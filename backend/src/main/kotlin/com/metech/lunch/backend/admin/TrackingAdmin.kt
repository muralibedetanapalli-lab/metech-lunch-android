package com.metech.lunch.backend.admin

import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.BatchOrders
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DeliveryBatches
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.PartnerLocations
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.delivery.ON_THE_ROAD
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.OrderStatus
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

/** Where an order's delivery partner is right now, for the owner's live map. */
class TrackingAdmin(private val db: Db, private val ops: OperationsAdmin) {

    suspend fun orderTracking(staff: Staff, orderId: String): OrderTracking = db.tx {
        val row = Orders.selectAll()
            .where { (Orders.id eq orderId) and (Orders.kitchenId eq staff.kitchenId) }
            .singleOrNull() ?: notFound("Order")
        val order = ops.toAdminOrders(listOf(row)).single()

        val batchRow = order.batchId?.let { id -> DeliveryBatches.selectAll().where { DeliveryBatches.id eq id }.singleOrNull() }
        val batch = batchRow?.let { toBatch(it) }
        val live = batchRow != null && order.status == OrderStatus.OUT_FOR_DELIVERY &&
            batchRow[DeliveryBatches.status] in ON_THE_ROAD

        val partnerId = batchRow?.get(DeliveryBatches.partnerId)
        val since = batchRow?.get(DeliveryBatches.pickedUpAt)
        val trail = if (live && partnerId != null && since != null) {
            PartnerLocations.selectAll()
                .where { (PartnerLocations.partnerId eq partnerId) and (PartnerLocations.recordedAt greaterEq since) }
                .orderBy(PartnerLocations.recordedAt, SortOrder.DESC)
                .limit(MAX_TRAIL)
                .map { toLocation(it) }
                .reversed()
        } else emptyList()

        OrderTracking(order = order, batch = batch, live = live, location = trail.lastOrNull(), trail = trail)
    }

    private fun toBatch(b: ResultRow): TrackingBatch {
        val partner = b[DeliveryBatches.partnerId]?.let { id -> Users.selectAll().where { Users.id eq id }.singleOrNull() }
        val statuses = BatchOrders.join(Orders, JoinType.INNER, BatchOrders.orderId, Orders.id)
            .select(Orders.status)
            .where { BatchOrders.batchId eq b[DeliveryBatches.id] }
            .map { it[Orders.status] }
        return TrackingBatch(
            id = b[DeliveryBatches.id],
            status = wireEnum<BatchStatus>(b[DeliveryBatches.status]),
            partnerName = partner?.get(Users.name)?.ifBlank { null },
            partnerPhone = partner?.get(Users.phone),
            pickedUpAt = b[DeliveryBatches.pickedUpAt]?.toString(),
            totalOrders = statuses.size,
            deliveredOrders = statuses.count { it == "delivered" },
        )
    }

    private fun toLocation(r: ResultRow) = TrackedLocation(
        latitude = r[PartnerLocations.latitude],
        longitude = r[PartnerLocations.longitude],
        accuracyMeters = r[PartnerLocations.accuracyM],
        speedMps = r[PartnerLocations.speedMps],
        recordedAt = r[PartnerLocations.recordedAt].toString(),
    )

    private companion object {
        const val MAX_TRAIL = 400
    }
}
