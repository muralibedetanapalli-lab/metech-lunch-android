package com.metech.lunch.backend.orders

import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.randomDigits
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.MenuItems
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.Payments
import com.metech.lunch.backend.menu.MenuService
import com.metech.lunch.backend.payments.PaymentGateway
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderLine
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentInfo
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.RefundStatus
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock

class OrderService(
    private val db: Db,
    private val clock: Clock,
    private val menu: MenuService,
    private val gateway: PaymentGateway,
) {

    suspend fun placeOrder(customerId: String, req: PlaceOrderRequest): PlaceOrderResponse {
        validate(req)
        return try {
            db.tx { existingResponse(customerId, req.idempotencyKey) ?: createOrder(customerId, req) }
        } catch (e: ExposedSQLException) {
            // Two concurrent requests with the same idempotency key: the loser hits the
            // unique constraint, so return the order the winner created.
            if (e.sqlState == UNIQUE_VIOLATION) {
                db.tx { existingResponse(customerId, req.idempotencyKey) } ?: throw e
            } else {
                throw e
            }
        }
    }

    suspend fun myOrders(customerId: String): List<Order> = db.tx {
        Orders.selectAll().where { Orders.customerId eq customerId }
            .orderBy(Orders.createdAt, SortOrder.DESC)
            .limit(50)
            .map { toOrder(it) }
    }

    suspend fun order(customerId: String, orderId: String): Order = db.tx {
        val row = Orders.selectAll().where { (Orders.id eq orderId) and (Orders.customerId eq customerId) }
            .singleOrNull() ?: notFound("Order")
        toOrder(row)
    }

    /**
     * Unpaid orders are cancelled immediately. Paid orders can be cancelled until the
     * cutoff on their delivery day; the refund then waits for owner approval.
     */
    suspend fun requestCancellation(customerId: String, orderId: String): Order = db.tx {
        val row = Orders.selectAll().where { (Orders.id eq orderId) and (Orders.customerId eq customerId) }
            .forUpdate().singleOrNull() ?: notFound("Order")
        val day = menu.kitchenDay(row[Orders.kitchenId])
        val beforeCutoff = row[Orders.deliveryDate].isAfter(day.today) ||
            (row[Orders.deliveryDate] == day.today && day.beforeCutoff)

        when (wireEnum<OrderStatus>(row[Orders.status])) {
            OrderStatus.PENDING_PAYMENT -> setStatus(orderId, OrderStatus.CANCELLED)
            OrderStatus.CONFIRMED -> {
                if (!beforeCutoff) rejected(ErrorCodes.CANNOT_CANCEL, "Orders can only be cancelled before the cutoff")
                setStatus(orderId, OrderStatus.CANCEL_REQUESTED)
                Payments.update({ Payments.orderId eq orderId }) {
                    it[refundStatus] = RefundStatus.REQUESTED.wire()
                    it[updatedAt] = clock.nowUtc()
                }
            }
            else -> rejected(ErrorCodes.CANNOT_CANCEL, "This order can no longer be cancelled")
        }
        audit(clock, customerId, "cancel_requested", "order", orderId)
        toOrder(Orders.selectAll().where { Orders.id eq orderId }.single())
    }

    // ---- internals ----

    private fun validate(req: PlaceOrderRequest) {
        if (req.idempotencyKey.isBlank() || req.idempotencyKey.length > 100) badRequest("idempotencyKey is required")
        if (req.lines.isEmpty()) badRequest("Add at least one meal")
        if (req.lines.any { it.quantity !in 1..MAX_QTY_PER_LINE }) badRequest("Quantity must be between 1 and $MAX_QTY_PER_LINE")
        if (req.lines.map { it.menuItemId }.toSet().size != req.lines.size) badRequest("Each meal may appear only once")
    }

    private fun org.jetbrains.exposed.sql.Transaction.existingResponse(customerId: String, key: String): PlaceOrderResponse? {
        val row = Orders.selectAll()
            .where { (Orders.customerId eq customerId) and (Orders.idempotencyKey eq key) }
            .singleOrNull() ?: return null
        val url = Payments.selectAll().where { Payments.orderId eq row[Orders.id] }.singleOrNull()
            ?.takeIf { row[Orders.status] == OrderStatus.PENDING_PAYMENT.wire() }
            ?.get(Payments.checkoutUrl)
        return PlaceOrderResponse(toOrder(row), url)
    }

    private fun createOrder(customerId: String, req: PlaceOrderRequest): PlaceOrderResponse {
        val office = menu.activeOffice(req.officeId)
        val kitchenId = office[com.metech.lunch.backend.db.Offices.kitchenId]
        if (req.dropPointId != null) {
            DropPoints.selectAll()
                .where { (DropPoints.id eq req.dropPointId!!) and (DropPoints.officeId eq req.officeId) and (DropPoints.active eq true) }
                .singleOrNull() ?: rejected(ErrorCodes.OFFICE_NOT_SERVICEABLE, "This drop point is not available")
        }

        val day = menu.kitchenDay(kitchenId)
        if (!day.beforeCutoff) {
            rejected(ErrorCodes.ORDERING_CLOSED, "Ordering for today closed at ${day.cutoffAt.toLocalTime()}")
        }

        // Lock today's rows for the requested meals so concurrent orders cannot oversell.
        val requested = req.lines.associate { it.menuItemId to it.quantity }
        val dailyRows = DailyMenuItems.join(MenuItems, JoinType.INNER, DailyMenuItems.menuItemId, MenuItems.id)
            .selectAll()
            .where {
                (DailyMenuItems.kitchenId eq kitchenId) and (DailyMenuItems.menuDate eq day.today) and
                    (DailyMenuItems.menuItemId inList requested.keys.toList())
            }
            .orderBy(DailyMenuItems.id, SortOrder.ASC)
            .forUpdate()
            .toList()
        val byMenuItem = dailyRows.associateBy { it[DailyMenuItems.menuItemId] }

        for ((menuItemId, _) in requested) {
            val row = byMenuItem[menuItemId]
            if (row == null || !row[DailyMenuItems.available] || !row[MenuItems.active]) {
                rejected(ErrorCodes.ITEM_UNAVAILABLE, "${row?.get(MenuItems.name) ?: "A meal"} is not available today")
            }
        }
        val reserved = menu.reservedQuantities(dailyRows.map { it[DailyMenuItems.id] }, excludeOrderId = null)
        for ((menuItemId, qty) in requested) {
            val row = byMenuItem.getValue(menuItemId)
            val capacity = row[DailyMenuItems.capacity] ?: continue
            val left = capacity - (reserved[row[DailyMenuItems.id]] ?: 0)
            if (qty > left) {
                rejected(
                    ErrorCodes.OUT_OF_CAPACITY,
                    if (left <= 0) "${row[MenuItems.name]} is sold out" else "Only $left ${row[MenuItems.name]} left",
                )
            }
        }

        val now = clock.nowUtc()
        val orderId = newId()
        val total = requested.entries.sumOf { (id, qty) -> byMenuItem.getValue(id)[DailyMenuItems.pricePaise] * qty }
        Orders.insert {
            it[id] = orderId
            it[Orders.kitchenId] = kitchenId
            it[Orders.customerId] = customerId
            it[officeId] = req.officeId
            it[dropPointId] = req.dropPointId
            it[deliveryDate] = day.today
            it[status] = OrderStatus.PENDING_PAYMENT.wire()
            it[totalPaise] = total
            it[idempotencyKey] = req.idempotencyKey
            it[deliveryOtp] = randomDigits(4)
            it[createdAt] = now
            it[updatedAt] = now
        }
        for ((menuItemId, qty) in requested) {
            val row = byMenuItem.getValue(menuItemId)
            OrderLines.insert {
                it[OrderLines.orderId] = orderId
                it[dailyMenuItemId] = row[DailyMenuItems.id]
                it[OrderLines.menuItemId] = menuItemId
                it[name] = row[MenuItems.name]
                it[quantity] = qty
                it[unitPricePaise] = row[DailyMenuItems.pricePaise]
            }
        }

        val paymentId = newId()
        val checkout = gateway.createCheckout(paymentId, orderId, total)
        Payments.insert {
            it[id] = paymentId
            it[Payments.orderId] = orderId
            it[gateway] = this@OrderService.gateway.name
            it[gatewayRef] = checkout.gatewayRef
            it[checkoutUrl] = checkout.url
            it[status] = PaymentStatus.PENDING.wire()
            it[amountPaise] = total
            it[createdAt] = now
            it[updatedAt] = now
        }
        audit(clock, customerId, "order_created", "order", orderId)
        return PlaceOrderResponse(toOrder(Orders.selectAll().where { Orders.id eq orderId }.single()), checkout.url)
    }

    private fun setStatus(orderId: String, status: OrderStatus) {
        Orders.update({ Orders.id eq orderId }) {
            it[Orders.status] = status.wire()
            it[updatedAt] = clock.nowUtc()
        }
    }

    private fun toOrder(row: ResultRow): Order {
        val orderId = row[Orders.id]
        val lines = OrderLines.selectAll().where { OrderLines.orderId eq orderId }.orderBy(OrderLines.id).map {
            OrderLine(it[OrderLines.menuItemId], it[OrderLines.name], it[OrderLines.quantity], it[OrderLines.unitPricePaise])
        }
        val payment = Payments.selectAll().where { Payments.orderId eq orderId }.singleOrNull()
        return Order(
            id = orderId,
            kitchenId = row[Orders.kitchenId],
            customerId = row[Orders.customerId],
            officeId = row[Orders.officeId],
            dropPointId = row[Orders.dropPointId],
            deliveryDate = row[Orders.deliveryDate].toString(),
            lines = lines,
            totalPaise = row[Orders.totalPaise],
            status = wireEnum(row[Orders.status]),
            payment = PaymentInfo(
                status = payment?.let { wireEnum<PaymentStatus>(it[Payments.status]) } ?: PaymentStatus.PENDING,
                reference = payment?.get(Payments.gatewayRef),
                refundStatus = payment?.get(Payments.refundStatus)?.let { wireEnum<RefundStatus>(it) },
            ),
            createdAt = row[Orders.createdAt].toString(),
            deliveryOtp = row[Orders.deliveryOtp],
        )
    }

    private companion object {
        const val MAX_QTY_PER_LINE = 20
        const val UNIQUE_VIOLATION = "23505"
    }
}
