package com.metech.lunch.backend.delivery

import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.constantTimeEquals
import com.metech.lunch.backend.common.encodeStringList
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.BatchOrders
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DeliveryBatches
import com.metech.lunch.backend.db.DeliveryIssues
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.Kitchens
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.PartnerLocations
import com.metech.lunch.backend.db.Users
import com.metech.lunch.core.model.BatchOrder
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.DeliveryIssueReport
import com.metech.lunch.core.model.DropPoint
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.LocationAck
import com.metech.lunch.core.model.LocationUpdate
import com.metech.lunch.core.model.Office
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.RecipientContact
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.OffsetDateTime

/**
 * Delivery partner operations. A partner only ever sees batches assigned to them, and
 * only the customer details needed to hand meals over.
 */
/** Batch statuses where the partner is carrying meals. */
val ON_THE_ROAD = listOf(BatchStatus.PICKED_UP.wire(), BatchStatus.IN_TRANSIT.wire())

class DeliveryService(private val db: Db, private val clock: Clock) {

    suspend fun assignedBatches(partnerId: String, status: BatchStatus?): List<DeliveryBatch> = db.tx {
        DeliveryBatches.selectAll()
            .where {
                var cond = DeliveryBatches.partnerId eq partnerId
                if (status != null) cond = cond and (DeliveryBatches.status eq status.wire())
                cond
            }
            .orderBy(DeliveryBatches.deliveryDate to SortOrder.DESC, DeliveryBatches.createdAt to SortOrder.ASC)
            .limit(100)
            .map { toBatch(it) }
    }

    suspend fun batch(partnerId: String, batchId: String): DeliveryBatch = db.tx {
        toBatch(ownBatch(partnerId, batchId, lock = false))
    }

    /** The partner confirms they collected exactly the assigned number of meals. */
    suspend fun confirmPickup(partnerId: String, batchId: String, confirmedMealCount: Int): DeliveryBatch = db.tx {
        val batch = ownBatch(partnerId, batchId, lock = true)
        if (wireEnum<BatchStatus>(batch[DeliveryBatches.status]) != BatchStatus.ASSIGNED) {
            rejected(ErrorCodes.INVALID_STATE, "This batch has already been picked up")
        }
        val expected = mealCount(batchId)
        if (confirmedMealCount != expected) {
            rejected(ErrorCodes.PICKUP_COUNT_MISMATCH, "This batch has $expected meal${if (expected == 1) "" else "s"} but you counted $confirmedMealCount. Recount with the kitchen.")
        }
        val now = clock.nowUtc()
        DeliveryBatches.update({ DeliveryBatches.id eq batchId }) {
            it[status] = BatchStatus.PICKED_UP.wire()
            it[pickupCount] = confirmedMealCount
            it[pickedUpAt] = now
            it[updatedAt] = now
        }
        for (orderId in activeOrderIds(batchId)) setOrderStatus(orderId, OrderStatus.OUT_FOR_DELIVERY)
        audit(clock, partnerId, "batch_picked_up", "batch", batchId, "meals=$confirmedMealCount")
        toBatch(DeliveryBatches.selectAll().where { DeliveryBatches.id eq batchId }.single())
    }

    /**
     * Stores a GPS fix, but only while the partner has a batch on the road (picked up and not
     * yet finished). Otherwise nothing is kept and the app is told to stop sharing.
     */
    suspend fun recordLocation(partnerId: String, update: LocationUpdate): LocationAck = db.tx {
        if (update.latitude !in -90.0..90.0 || update.longitude !in -180.0..180.0 ||
            update.latitude.isNaN() || update.longitude.isNaN()
        ) badRequest("Invalid coordinates")
        val onTheRoad = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.partnerId eq partnerId) and (DeliveryBatches.status inList ON_THE_ROAD) }
            .limit(1)
            .any()
        if (!onTheRoad) return@tx LocationAck(tracking = false)

        val now = clock.nowUtc()
        // Trust the phone's timestamp only if it is recent and not in the future.
        val recorded = update.recordedAt
            ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            ?.takeIf { !it.isAfter(now.plusMinutes(1)) && it.isAfter(now.minusMinutes(15)) }
            ?: now
        PartnerLocations.insert {
            it[PartnerLocations.partnerId] = partnerId
            it[latitude] = update.latitude
            it[longitude] = update.longitude
            it[accuracyM] = update.accuracyMeters?.takeIf { a -> a.isFinite() && a >= 0 }
            it[speedMps] = update.speedMps?.takeIf { v -> v.isFinite() && v >= 0 }
            it[headingDeg] = update.headingDegrees?.takeIf { h -> h.isFinite() }
            it[recordedAt] = recorded
            it[receivedAt] = now
        }
        LocationAck(tracking = true)
    }

    /**
     * Marks orders as handed over. The OTP is either the batch's office-coordinator OTP
     * (valid for any orders in the batch) or, for a single order, that customer's OTP.
     */
    suspend fun confirmHandover(partnerId: String, batchId: String, body: HandoverConfirmation): DeliveryBatch = db.tx {
        val batch = ownBatch(partnerId, batchId, lock = true)
        requireInTransit(batch)
        val orders = outForDeliveryOrders(batchId, body.orderIds)

        val otp = body.otp?.trim().orEmpty()
        val coordinatorOtp = batch[DeliveryBatches.coordinatorOtp]
        val valid = otp.isNotEmpty() && (
            (coordinatorOtp != null && constantTimeEquals(otp, coordinatorOtp)) ||
                (orders.size == 1 && constantTimeEquals(otp, orders.single()[Orders.deliveryOtp]))
            )
        if (!valid) rejected(ErrorCodes.INVALID_HANDOVER_OTP, "The handover OTP is incorrect")

        for (order in orders) setOrderStatus(order[Orders.id], OrderStatus.DELIVERED)
        recomputeBatchStatus(batchId)
        audit(clock, partnerId, "handover_confirmed", "batch", batchId, encodeStringList(body.orderIds))
        toBatch(DeliveryBatches.selectAll().where { DeliveryBatches.id eq batchId }.single())
    }

    /** Failed or partial delivery. The owner sees these in the dashboard for follow-up. */
    suspend fun reportIssue(partnerId: String, batchId: String, body: DeliveryIssueReport): DeliveryBatch = db.tx {
        if (body.reason.isBlank()) badRequest("A reason is required")
        val batch = ownBatch(partnerId, batchId, lock = true)
        requireInTransit(batch)
        val orders = outForDeliveryOrders(batchId, body.orderIds)

        DeliveryIssues.insert {
            it[DeliveryIssues.batchId] = batchId
            it[DeliveryIssues.partnerId] = partnerId
            it[orderIds] = encodeStringList(body.orderIds)
            it[reason] = body.reason.trim()
            it[partial] = body.partial
            it[createdAt] = clock.nowUtc()
        }
        val newStatus = if (body.partial) OrderStatus.PARTIALLY_DELIVERED else OrderStatus.DELIVERY_FAILED
        for (order in orders) setOrderStatus(order[Orders.id], newStatus)
        recomputeBatchStatus(batchId)
        audit(clock, partnerId, "delivery_issue", "batch", batchId, body.reason.trim())
        toBatch(DeliveryBatches.selectAll().where { DeliveryBatches.id eq batchId }.single())
    }

    // ---- internals ----

    private fun ownBatch(partnerId: String, batchId: String, lock: Boolean): ResultRow {
        val query = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.id eq batchId) and (DeliveryBatches.partnerId eq partnerId) }
        return (if (lock) query.forUpdate() else query).singleOrNull() ?: notFound("Batch")
    }

    private fun requireInTransit(batch: ResultRow) {
        val status = wireEnum<BatchStatus>(batch[DeliveryBatches.status])
        if (status == BatchStatus.ASSIGNED) rejected(ErrorCodes.INVALID_STATE, "Confirm pickup first")
        if (status == BatchStatus.DELIVERED || status == BatchStatus.FAILED) {
            rejected(ErrorCodes.INVALID_STATE, "This batch is already closed")
        }
    }

    private fun outForDeliveryOrders(batchId: String, orderIds: List<String>): List<ResultRow> {
        if (orderIds.isEmpty()) badRequest("Select at least one order")
        val rows = BatchOrders.join(Orders, JoinType.INNER, BatchOrders.orderId, Orders.id)
            .selectAll()
            .where { (BatchOrders.batchId eq batchId) and (Orders.id inList orderIds.distinct()) }
            .forUpdate()
            .toList()
        if (rows.size != orderIds.distinct().size) rejected(ErrorCodes.INVALID_STATE, "Some orders are not in this batch")
        if (rows.any { it[Orders.status] != OrderStatus.OUT_FOR_DELIVERY.wire() }) {
            rejected(ErrorCodes.INVALID_STATE, "Some orders are not out for delivery")
        }
        return rows
    }

    /** Orders in the batch that are still to be delivered (not cancelled/refunded). */
    private fun activeOrderIds(batchId: String): List<String> =
        BatchOrders.join(Orders, JoinType.INNER, BatchOrders.orderId, Orders.id)
            .selectAll()
            .where { (BatchOrders.batchId eq batchId) and (Orders.status inList listOf(OrderStatus.CONFIRMED.wire(), OrderStatus.PREPARING.wire())) }
            .map { it[Orders.id] }

    private fun mealCount(batchId: String): Int {
        val ids = activeOrderIds(batchId)
        if (ids.isEmpty()) return 0
        return OrderLines.selectAll().where { OrderLines.orderId inList ids }.sumOf { it[OrderLines.quantity] }
    }

    private fun recomputeBatchStatus(batchId: String) {
        val statuses = BatchOrders.join(Orders, JoinType.INNER, BatchOrders.orderId, Orders.id)
            .select(Orders.status)
            .where { BatchOrders.batchId eq batchId }
            .map { wireEnum<OrderStatus>(it[Orders.status]) }
            .filter { it != OrderStatus.CANCELLED && it != OrderStatus.CANCEL_REQUESTED }
        val open = statuses.any { it == OrderStatus.OUT_FOR_DELIVERY }
        val newStatus = when {
            open -> BatchStatus.IN_TRANSIT
            statuses.all { it == OrderStatus.DELIVERED } -> BatchStatus.DELIVERED
            statuses.none { it == OrderStatus.DELIVERED || it == OrderStatus.PARTIALLY_DELIVERED } -> BatchStatus.FAILED
            else -> BatchStatus.PARTIALLY_DELIVERED
        }
        DeliveryBatches.update({ DeliveryBatches.id eq batchId }) {
            it[status] = newStatus.wire()
            it[updatedAt] = clock.nowUtc()
        }
    }

    private fun setOrderStatus(orderId: String, status: OrderStatus) {
        Orders.update({ Orders.id eq orderId }) {
            it[Orders.status] = status.wire()
            it[updatedAt] = clock.nowUtc()
        }
    }

    private fun toBatch(row: ResultRow): DeliveryBatch {
        val batchId = row[DeliveryBatches.id]
        val kitchen = Kitchens.selectAll().where { Kitchens.id eq row[DeliveryBatches.kitchenId] }.single()
        val officeRow = Offices.selectAll().where { Offices.id eq row[DeliveryBatches.officeId] }.single()
        val dropPoint = row[DeliveryBatches.dropPointId]?.let { id ->
            DropPoints.selectAll().where { DropPoints.id eq id }.singleOrNull()?.let {
                DropPoint(it[DropPoints.id], it[DropPoints.officeId], it[DropPoints.name], it[DropPoints.instructions])
            }
        }
        val orderRows = BatchOrders.join(Orders, JoinType.INNER, BatchOrders.orderId, Orders.id)
            .join(Users, JoinType.INNER, Orders.customerId, Users.id)
            .selectAll()
            .where { BatchOrders.batchId eq batchId }
            .orderBy(Users.name)
            .toList()
        val qtyByOrder = if (orderRows.isEmpty()) emptyMap() else
            OrderLines.selectAll().where { OrderLines.orderId inList orderRows.map { it[Orders.id] } }
                .groupBy { it[OrderLines.orderId] }
                .mapValues { (_, lines) -> lines.sumOf { it[OrderLines.quantity] } }
        val orders = orderRows.map {
            BatchOrder(
                orderId = it[Orders.id],
                customerName = it[Users.name].ifBlank { "Customer" },
                mealCount = qtyByOrder[it[Orders.id]] ?: 0,
                status = wireEnum(it[Orders.status]),
            )
        }
        val contact = row[DeliveryBatches.coordinatorPhone]?.let {
            RecipientContact(row[DeliveryBatches.coordinatorName] ?: "Office coordinator", it)
        }
        return DeliveryBatch(
            id = batchId,
            kitchenId = row[DeliveryBatches.kitchenId],
            deliveryDate = row[DeliveryBatches.deliveryDate].toString(),
            pickupLocation = "${kitchen[Kitchens.name]}, ${kitchen[Kitchens.address]}",
            office = Office(officeRow[Offices.id], officeRow[Offices.kitchenId], officeRow[Offices.name], officeRow[Offices.address]),
            dropPoint = dropPoint,
            contact = contact,
            orders = orders,
            totalMeals = orders.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.CANCEL_REQUESTED }
                .sumOf { it.mealCount },
            status = wireEnum(row[DeliveryBatches.status]),
        )
    }
}
