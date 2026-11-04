package com.metech.lunch.backend.payments

import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.PaymentEvents
import com.metech.lunch.backend.db.Payments
import com.metech.lunch.backend.menu.MenuService
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.RefundStatus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Clock

enum class EventOutcome { DUPLICATE, UNKNOWN_PAYMENT, ALREADY_FINAL, CONFIRMED, FAILED, REFUND_REQUESTED }

/**
 * Applies verified gateway callbacks. An order is confirmed only here, never from the
 * app's redirect. Replayed callbacks are recorded once and otherwise ignored.
 */
class PaymentService(
    private val db: Db,
    private val clock: Clock,
    private val menu: MenuService,
    val gateways: Map<String, PaymentGateway>,
) {
    private val log = LoggerFactory.getLogger(PaymentService::class.java)

    suspend fun handleEvent(gateway: String, event: GatewayEvent): EventOutcome = db.tx {
        val now = clock.nowUtc()
        val inserted = PaymentEvents.insertIgnore {
            it[PaymentEvents.gateway] = gateway
            it[eventId] = event.eventId
            it[gatewayRef] = event.gatewayRef
            it[payload] = event.payload
            it[receivedAt] = now
        }.insertedCount
        if (inserted == 0) return@tx EventOutcome.DUPLICATE

        val payment = Payments.selectAll()
            .where { Payments.gatewayRef eq event.gatewayRef }
            .forUpdate()
            .singleOrNull()
        if (payment == null) {
            log.warn("Payment event {} for unknown gateway ref {}", event.eventId, event.gatewayRef)
            return@tx EventOutcome.UNKNOWN_PAYMENT
        }
        val paymentStatus = wireEnum<PaymentStatus>(payment[Payments.status])
        if (paymentStatus == PaymentStatus.VERIFIED || paymentStatus == PaymentStatus.REFUNDED) {
            return@tx EventOutcome.ALREADY_FINAL
        }

        val orderId = payment[Payments.orderId]
        val order = Orders.selectAll().where { Orders.id eq orderId }.forUpdate().single()
        val orderStatus = wireEnum<OrderStatus>(order[Orders.status])

        if (!event.success) {
            Payments.update({ Payments.id eq payment[Payments.id] }) {
                it[status] = PaymentStatus.FAILED.wire()
                it[updatedAt] = now
            }
            if (orderStatus == OrderStatus.PENDING_PAYMENT) setOrderStatus(orderId, OrderStatus.PAYMENT_FAILED)
            audit(clock, null, "payment_failed", "order", orderId)
            return@tx EventOutcome.FAILED
        }

        // Money has been taken from here on.
        Payments.update({ Payments.id eq payment[Payments.id] }) {
            it[status] = PaymentStatus.VERIFIED.wire()
            it[updatedAt] = now
        }

        val canConfirm = (orderStatus == OrderStatus.PENDING_PAYMENT || orderStatus == OrderStatus.PAYMENT_FAILED) &&
            capacityStillAvailable(orderId)

        if (canConfirm) {
            // An order created before the cutoff is honoured even if the payment lands after it.
            setOrderStatus(orderId, OrderStatus.CONFIRMED)
            audit(clock, null, "order_confirmed", "order", orderId)
            EventOutcome.CONFIRMED
        } else {
            // Paid but cannot be fulfilled (cancelled meanwhile, or sold out after the hold expired).
            if (orderStatus == OrderStatus.PENDING_PAYMENT || orderStatus == OrderStatus.PAYMENT_FAILED) {
                setOrderStatus(orderId, OrderStatus.CANCELLED)
            }
            Payments.update({ Payments.id eq payment[Payments.id] }) {
                it[refundStatus] = RefundStatus.REQUESTED.wire()
            }
            audit(clock, null, "refund_requested", "order", orderId, "Paid but could not be fulfilled ($orderStatus)")
            EventOutcome.REFUND_REQUESTED
        }
    }

    /** Re-checks capacity for an order, excluding its own (possibly expired) hold. */
    private fun capacityStillAvailable(orderId: String): Boolean {
        val lines = OrderLines.selectAll().where { OrderLines.orderId eq orderId }.toList()
        val dailyIds = lines.map { it[OrderLines.dailyMenuItemId] }.distinct().sorted()
        val capacities = DailyMenuItems.selectAll()
            .where { DailyMenuItems.id inList dailyIds }
            .orderBy(DailyMenuItems.id, SortOrder.ASC)
            .forUpdate()
            .associate { it[DailyMenuItems.id] to it[DailyMenuItems.capacity] }
        val reserved = menu.reservedQuantities(dailyIds, excludeOrderId = orderId)
        return lines.groupBy { it[OrderLines.dailyMenuItemId] }.all { (dailyId, ls) ->
            val capacity = capacities[dailyId] ?: return@all true
            (reserved[dailyId] ?: 0) + ls.sumOf { it[OrderLines.quantity] } <= capacity
        }
    }

    private fun setOrderStatus(orderId: String, status: OrderStatus) {
        Orders.update({ Orders.id eq orderId }) {
            it[Orders.status] = status.wire()
            it[updatedAt] = clock.nowUtc()
        }
    }
}
