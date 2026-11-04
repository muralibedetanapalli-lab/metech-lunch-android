package com.metech.lunch.backend.admin

import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.decodeStringList
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.randomDigits
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.BatchOrders
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DeliveryBatches
import com.metech.lunch.backend.db.DeliveryIssues
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.Payments
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.menu.MenuService
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.model.UserRole
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.LocalDate

/** Orders, kitchen preparation, packing, dispatch and delivery follow-up. */
class OperationsAdmin(private val db: Db, private val clock: Clock, private val menu: MenuService) {

    // ---- Overview ----

    suspend fun overview(staff: Staff, date: LocalDate?): OverviewView = db.tx {
        val day = menu.kitchenDay(staff.kitchenId)
        val d = date ?: day.today
        val orders = Orders.selectAll().where { (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate eq d) }.toList()
        val statusCounts = orders.groupingBy { it[Orders.status] }.eachCount()
        val prepared = orders.filter { it[Orders.status] in PREPARED }
        val meals = mealsByOrder(prepared.map { it[Orders.id] }).values.sum()
        val sales = if (staff.isOwner) {
            Payments.selectAll()
                .where { (Payments.orderId inList orders.map { it[Orders.id] }) and (Payments.status eq PaymentStatus.VERIFIED.wire()) }
                .sumOf { it[Payments.amountPaise] }
        } else null
        val pendingRefunds = if (staff.isOwner) pendingRefundCount(staff.kitchenId) else null
        val batchIds = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.kitchenId eq staff.kitchenId) and (DeliveryBatches.deliveryDate eq d) }
            .toList()
        val openIssues = DeliveryIssues.selectAll().where { DeliveryIssues.batchId inList batchIds.map { it[DeliveryBatches.id] } }.count().toInt()
        OverviewView(
            date = d.toString(),
            orderingOpen = d == day.today && day.beforeCutoff,
            cutoffTime = day.cutoffAt.toLocalTime().toString(),
            statusCounts = statusCounts,
            meals = meals,
            salesPaise = sales,
            pendingRefunds = pendingRefunds,
            openIssues = openIssues,
            unassignedBatches = batchIds.count { it[DeliveryBatches.status] == BatchStatus.UNASSIGNED.wire() },
        )
    }

    // ---- Orders ----

    suspend fun orders(staff: Staff, date: LocalDate?, status: OrderStatus?, officeId: String?): List<AdminOrder> = db.tx {
        val d = date ?: menu.kitchenDay(staff.kitchenId).today
        val rows = Orders.selectAll()
            .where {
                var cond = (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate eq d)
                if (status != null) cond = cond and (Orders.status eq status.wire())
                if (officeId != null) cond = cond and (Orders.officeId eq officeId)
                cond
            }
            .orderBy(Orders.createdAt, SortOrder.ASC)
            .toList()
        toAdminOrders(rows)
    }

    fun toAdminOrders(rows: List<ResultRow>): List<AdminOrder> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[Orders.id] }
        val lines = OrderLines.selectAll().where { OrderLines.orderId inList ids }.orderBy(OrderLines.id).toList()
            .groupBy { it[OrderLines.orderId] }
        val payments = Payments.selectAll().where { Payments.orderId inList ids }.associateBy { it[Payments.orderId] }
        val users = Users.selectAll().where { Users.id inList rows.map { it[Orders.customerId] }.distinct() }.associateBy { it[Users.id] }
        val offices = Offices.selectAll().associateBy { it[Offices.id] }
        val dropPoints = DropPoints.selectAll().associateBy { it[DropPoints.id] }
        val batches = BatchOrders.selectAll().where { BatchOrders.orderId inList ids }.associate { it[BatchOrders.orderId] to it[BatchOrders.batchId] }
        return rows.map { o ->
            val id = o[Orders.id]
            val payment = payments[id]
            val user = users[o[Orders.customerId]]
            AdminOrder(
                id = id,
                deliveryDate = o[Orders.deliveryDate].toString(),
                createdAt = o[Orders.createdAt].toString(),
                status = wireEnum(o[Orders.status]),
                customerName = user?.get(Users.name)?.ifBlank { null } ?: "(no name)",
                customerPhone = user?.get(Users.phone).orEmpty(),
                officeId = o[Orders.officeId],
                officeName = offices[o[Orders.officeId]]?.get(Offices.name).orEmpty(),
                dropPointName = o[Orders.dropPointId]?.let { dropPoints[it]?.get(DropPoints.name) },
                lines = lines[id].orEmpty().map { AdminOrderLine(it[OrderLines.name], it[OrderLines.quantity]) },
                totalPaise = o[Orders.totalPaise],
                paymentStatus = payment?.let { wireEnum<PaymentStatus>(it[Payments.status]) },
                paymentReference = payment?.get(Payments.gatewayRef),
                refundStatus = payment?.get(Payments.refundStatus)?.let { wireEnum<RefundStatus>(it) },
                batchId = batches[id],
            )
        }
    }

    // ---- Kitchen preparation and packing ----

    suspend fun prep(staff: Staff, date: LocalDate?): PrepView = db.tx {
        val day = menu.kitchenDay(staff.kitchenId)
        val d = date ?: day.today
        val all = Orders.selectAll().where { (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate eq d) }.toList()
        val prepared = all.filter { it[Orders.status] in PREPARED }
        val ids = prepared.map { it[Orders.id] }
        val lines = if (ids.isEmpty()) emptyList() else OrderLines.selectAll().where { OrderLines.orderId inList ids }.toList()
        val linesByOrder = lines.groupBy { it[OrderLines.orderId] }
        val users = if (prepared.isEmpty()) emptyMap() else
            Users.selectAll().where { Users.id inList prepared.map { it[Orders.customerId] }.distinct() }.associateBy { it[Users.id] }
        val offices = Offices.selectAll().associateBy { it[Offices.id] }
        val dropPoints = DropPoints.selectAll().associateBy { it[DropPoints.id] }

        val groups = prepared
            .groupBy { it[Orders.officeId] to it[Orders.dropPointId] }
            .map { (key, orders) ->
                val (officeId, dropPointId) = key
                val groupLines = orders.flatMap { linesByOrder[it[Orders.id]].orEmpty() }
                PackingGroup(
                    officeId = officeId,
                    officeName = offices[officeId]?.get(Offices.name).orEmpty(),
                    officeAddress = offices[officeId]?.get(Offices.address).orEmpty(),
                    dropPointId = dropPointId,
                    dropPointName = dropPointId?.let { dropPoints[it]?.get(DropPoints.name) },
                    mealTotals = mealTotals(groupLines),
                    orders = orders.map { o ->
                        PackingOrder(
                            orderId = o[Orders.id],
                            customerName = users[o[Orders.customerId]]?.get(Users.name)?.ifBlank { null } ?: "(no name)",
                            lines = linesByOrder[o[Orders.id]].orEmpty().map { AdminOrderLine(it[OrderLines.name], it[OrderLines.quantity]) },
                            status = wireEnum(o[Orders.status]),
                        )
                    }.sortedBy { it.customerName.lowercase() },
                )
            }
            .sortedWith(compareBy({ it.officeName }, { it.dropPointName ?: "" }))

        PrepView(
            date = d.toString(),
            cutoffPassed = d.isBefore(day.today) || (d == day.today && !day.beforeCutoff),
            mealTotals = mealTotals(lines),
            totalMeals = lines.sumOf { it[OrderLines.quantity] },
            confirmedOrders = prepared.size,
            awaitingPrep = prepared.count { it[Orders.status] == OrderStatus.CONFIRMED.wire() },
            pendingCancellations = all.count { it[Orders.status] == OrderStatus.CANCEL_REQUESTED.wire() },
            groups = groups,
        )
    }

    /** Moves the day's confirmed orders to "preparing". Only after the cutoff, when the list is final. */
    suspend fun startPreparation(staff: Staff, date: LocalDate): CountResult = db.tx {
        val day = menu.kitchenDay(staff.kitchenId)
        if (date == day.today && day.beforeCutoff) {
            rejected(ErrorCodes.INVALID_STATE, "Wait until the ${day.cutoffAt.toLocalTime()} cutoff; customers can still order or cancel")
        }
        if (date.isAfter(day.today)) rejected(ErrorCodes.INVALID_STATE, "That day has not started yet")
        val now = clock.nowUtc()
        val n = Orders.update({
            (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate eq date) and (Orders.status eq OrderStatus.CONFIRMED.wire())
        }) {
            it[status] = OrderStatus.PREPARING.wire()
            it[updatedAt] = now
        }
        audit(clock, staff.userId, "preparation_started", "day", date.toString(), "$n orders")
        CountResult(n)
    }

    // ---- Dispatch ----

    suspend fun batches(staff: Staff, date: LocalDate?): List<AdminBatch> = db.tx {
        val d = date ?: menu.kitchenDay(staff.kitchenId).today
        val rows = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.kitchenId eq staff.kitchenId) and (DeliveryBatches.deliveryDate eq d) }
            .orderBy(DeliveryBatches.createdAt)
            .toList()
        toAdminBatches(rows)
    }

    /**
     * Groups the day's paid, not-yet-batched orders by office and drop point. Orders join an
     * existing batch for the same place if it has not been picked up yet.
     */
    suspend fun generateBatches(staff: Staff, date: LocalDate): List<AdminBatch> {
        db.tx {
            val batched = BatchOrders.selectAll().map { it[BatchOrders.orderId] }.toSet()
            val candidates = Orders.selectAll()
                .where {
                    (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate eq date) and
                        (Orders.status inList listOf(OrderStatus.CONFIRMED.wire(), OrderStatus.PREPARING.wire()))
                }
                .forUpdate()
                .filter { it[Orders.id] !in batched }
            val open = DeliveryBatches.selectAll()
                .where {
                    (DeliveryBatches.kitchenId eq staff.kitchenId) and (DeliveryBatches.deliveryDate eq date) and
                        (DeliveryBatches.status inList listOf(BatchStatus.UNASSIGNED.wire(), BatchStatus.ASSIGNED.wire()))
                }
                .toList()
            val now = clock.nowUtc()
            for ((key, orders) in candidates.groupBy { it[Orders.officeId] to it[Orders.dropPointId] }) {
                val (officeId, dropPointId) = key
                val batchId = open.firstOrNull { it[DeliveryBatches.officeId] == officeId && it[DeliveryBatches.dropPointId] == dropPointId }
                    ?.get(DeliveryBatches.id)
                    ?: newId().also { id ->
                        DeliveryBatches.insert {
                            it[DeliveryBatches.id] = id
                            it[kitchenId] = staff.kitchenId
                            it[deliveryDate] = date
                            it[DeliveryBatches.officeId] = officeId
                            it[DeliveryBatches.dropPointId] = dropPointId
                            it[status] = BatchStatus.UNASSIGNED.wire()
                            it[createdAt] = now
                            it[updatedAt] = now
                        }
                    }
                orders.forEach { o ->
                    BatchOrders.insert {
                        it[BatchOrders.batchId] = batchId
                        it[orderId] = o[Orders.id]
                    }
                }
            }
            audit(clock, staff.userId, "batches_generated", "day", date.toString(), "${candidates.size} orders")
        }
        return batches(staff, date)
    }

    suspend fun assignBatch(staff: Staff, batchId: String, req: AssignBatchRequest): AdminBatch = db.tx {
        val batch = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.id eq batchId) and (DeliveryBatches.kitchenId eq staff.kitchenId) }
            .forUpdate().singleOrNull() ?: notFound("Batch")
        val status = wireEnum<BatchStatus>(batch[DeliveryBatches.status])
        if (status != BatchStatus.UNASSIGNED && status != BatchStatus.ASSIGNED) {
            rejected(ErrorCodes.INVALID_STATE, "A batch cannot be reassigned after pickup")
        }
        Users.selectAll()
            .where { (Users.id eq req.partnerId) and (Users.role eq UserRole.DELIVERY_PARTNER.wire()) and (Users.active eq true) }
            .singleOrNull() ?: badRequest("Choose an active delivery partner")
        DeliveryBatches.update({ DeliveryBatches.id eq batchId }) {
            it[partnerId] = req.partnerId
            it[DeliveryBatches.status] = BatchStatus.ASSIGNED.wire()
            it[coordinatorName] = req.coordinatorName?.trim()?.ifEmpty { null }
            it[coordinatorPhone] = req.coordinatorPhone?.trim()?.ifEmpty { null }
            it[coordinatorOtp] = if (req.coordinatorOtp) batch[DeliveryBatches.coordinatorOtp] ?: randomDigits(4) else null
            it[updatedAt] = clock.nowUtc()
        }
        audit(clock, staff.userId, "batch_assigned", "batch", batchId, "partner=${req.partnerId}")
        toAdminBatches(DeliveryBatches.selectAll().where { DeliveryBatches.id eq batchId }.toList()).single()
    }

    private fun toAdminBatches(rows: List<ResultRow>): List<AdminBatch> {
        if (rows.isEmpty()) return emptyList()
        val batchIds = rows.map { it[DeliveryBatches.id] }
        val links = BatchOrders.selectAll().where { BatchOrders.batchId inList batchIds }.toList()
        val orderRows = if (links.isEmpty()) emptyList() else
            Orders.selectAll().where { Orders.id inList links.map { it[BatchOrders.orderId] } }.toList()
        val orders = toAdminOrders(orderRows).associateBy { it.id }
        val offices = Offices.selectAll().associateBy { it[Offices.id] }
        val dropPoints = DropPoints.selectAll().associateBy { it[DropPoints.id] }
        val partners = Users.selectAll().where { Users.role eq UserRole.DELIVERY_PARTNER.wire() }.associateBy { it[Users.id] }
        val byBatch = links.groupBy({ it[BatchOrders.batchId] }, { it[BatchOrders.orderId] })
        return rows.map { b ->
            val batchOrders = byBatch[b[DeliveryBatches.id]].orEmpty().mapNotNull { orders[it] }
            val counted = batchOrders.filter { it.status != OrderStatus.CANCELLED && it.status != OrderStatus.CANCEL_REQUESTED }
            AdminBatch(
                id = b[DeliveryBatches.id],
                deliveryDate = b[DeliveryBatches.deliveryDate].toString(),
                officeName = offices[b[DeliveryBatches.officeId]]?.get(Offices.name).orEmpty(),
                dropPointName = b[DeliveryBatches.dropPointId]?.let { dropPoints[it]?.get(DropPoints.name) },
                status = wireEnum(b[DeliveryBatches.status]),
                partnerId = b[DeliveryBatches.partnerId],
                partnerName = b[DeliveryBatches.partnerId]?.let { partners[it]?.get(Users.name) },
                coordinatorName = b[DeliveryBatches.coordinatorName],
                coordinatorPhone = b[DeliveryBatches.coordinatorPhone],
                coordinatorOtp = b[DeliveryBatches.coordinatorOtp],
                orderCount = counted.size,
                totalMeals = counted.sumOf { o -> o.lines.sumOf { it.quantity } },
                pickupCount = b[DeliveryBatches.pickupCount],
                orders = batchOrders.map { PackingOrder(it.id, it.customerName, it.lines, it.status) }.sortedBy { it.customerName.lowercase() },
            )
        }
    }

    // ---- Delivery issues ----

    suspend fun issues(staff: Staff, date: LocalDate?): List<AdminIssue> = db.tx {
        val d = date ?: menu.kitchenDay(staff.kitchenId).today
        val batches = DeliveryBatches.selectAll()
            .where { (DeliveryBatches.kitchenId eq staff.kitchenId) and (DeliveryBatches.deliveryDate eq d) }
            .associateBy { it[DeliveryBatches.id] }
        if (batches.isEmpty()) return@tx emptyList()
        val offices = Offices.selectAll().associateBy { it[Offices.id] }
        val users = Users.selectAll().where { Users.role eq UserRole.DELIVERY_PARTNER.wire() }.associateBy { it[Users.id] }
        DeliveryIssues.selectAll().where { DeliveryIssues.batchId inList batches.keys.toList() }
            .orderBy(DeliveryIssues.createdAt, SortOrder.DESC)
            .map {
                val batch = batches.getValue(it[DeliveryIssues.batchId])
                AdminIssue(
                    id = it[DeliveryIssues.id],
                    batchId = it[DeliveryIssues.batchId],
                    officeName = offices[batch[DeliveryBatches.officeId]]?.get(Offices.name).orEmpty(),
                    partnerName = users[it[DeliveryIssues.partnerId]]?.get(Users.name).orEmpty(),
                    orderIds = decodeStringList(it[DeliveryIssues.orderIds]),
                    reason = it[DeliveryIssues.reason],
                    partial = it[DeliveryIssues.partial],
                    createdAt = it[DeliveryIssues.createdAt].toString(),
                )
            }
    }

    // ---- helpers ----

    private fun mealsByOrder(orderIds: List<String>): Map<String, Int> =
        if (orderIds.isEmpty()) emptyMap() else
            OrderLines.selectAll().where { OrderLines.orderId inList orderIds }
                .groupBy { it[OrderLines.orderId] }
                .mapValues { (_, l) -> l.sumOf { it[OrderLines.quantity] } }

    private fun mealTotals(lines: List<ResultRow>): List<MealTotal> =
        lines.groupBy { it[OrderLines.menuItemId] }
            .map { (id, ls) -> MealTotal(id, ls.first()[OrderLines.name], ls.sumOf { it[OrderLines.quantity] }) }
            .sortedByDescending { it.quantity }

    companion object {
        /** Orders the kitchen cooks for: paid and not cancelled. */
        val PREPARED = listOf(
            OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED, OrderStatus.PARTIALLY_DELIVERED, OrderStatus.DELIVERY_FAILED,
        ).map { it.wire() }

        fun pendingRefundCount(kitchenId: String): Int =
            Payments.join(Orders, JoinType.INNER, Payments.orderId, Orders.id)
                .selectAll()
                .where { (Orders.kitchenId eq kitchenId) and (Payments.refundStatus eq RefundStatus.REQUESTED.wire()) }
                .count().toInt()
    }
}
