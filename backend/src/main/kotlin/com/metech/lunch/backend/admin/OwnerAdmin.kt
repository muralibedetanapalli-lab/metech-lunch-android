package com.metech.lunch.backend.admin

import com.metech.lunch.backend.auth.AuthService
import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.common.wireEnum
import com.metech.lunch.backend.db.AuditLog
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.db.Payments
import com.metech.lunch.backend.db.Users
import com.metech.lunch.backend.menu.MenuService
import com.metech.lunch.backend.payments.PaymentGateway
import com.metech.lunch.backend.payments.RefundResult
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.model.UserRole
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.LocalDate

/** Owner-only: people, refunds, reports, exports and the audit trail. */
class OwnerAdmin(
    private val db: Db,
    private val clock: Clock,
    private val menu: MenuService,
    private val ops: OperationsAdmin,
    private val gateways: Map<String, PaymentGateway>,
) {

    // ---- People ----

    suspend fun users(staff: Staff, role: UserRole): List<AdminUser> = db.tx {
        val rows = Users.selectAll()
            .where {
                // Customers are not tied to a kitchen; staff and partners are.
                if (role == UserRole.CUSTOMER) Users.role eq role.wire()
                else (Users.role eq role.wire()) and (Users.kitchenId eq staff.kitchenId)
            }
            .orderBy(Users.name)
            .toList()
        val orderCounts = if (role == UserRole.CUSTOMER && rows.isNotEmpty()) {
            Orders.selectAll().where { (Orders.customerId inList rows.map { it[Users.id] }) and (Orders.kitchenId eq staff.kitchenId) }
                .groupingBy { it[Orders.customerId] }.eachCount()
        } else emptyMap()
        rows.map {
            AdminUser(
                id = it[Users.id],
                name = it[Users.name],
                phone = it[Users.phone],
                email = it[Users.email],
                role = wireEnum(it[Users.role]),
                active = it[Users.active],
                createdAt = it[Users.createdAt].toString(),
                orderCount = if (role == UserRole.CUSTOMER) orderCounts[it[Users.id]] ?: 0 else null,
            )
        }
    }

    suspend fun createUser(staff: Staff, req: CreateUserRequest): AdminUser {
        if (req.role == UserRole.CUSTOMER) badRequest("Customers register themselves in the app")
        if (req.name.isBlank()) badRequest("Name is required")
        val phone = AuthService.normalizePhone(req.phone)
        val id = newId()
        try {
            db.tx {
                Users.insert {
                    it[Users.id] = id
                    it[Users.phone] = phone
                    it[role] = req.role.wire()
                    it[name] = req.name.trim()
                    it[kitchenId] = staff.kitchenId
                    it[active] = true
                    it[createdAt] = clock.nowUtc()
                }
                audit(clock, staff.userId, "user_created", "user", id, "${req.role.wire()} $phone")
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") rejected(ErrorCodes.INVALID_STATE, "That phone number already has this role")
            throw e
        }
        return users(staff, req.role).single { it.id == id }
    }

    suspend fun updateUser(staff: Staff, userId: String, req: UpdateUserRequest): AdminUser {
        if (req.name.isBlank()) badRequest("Name is required")
        val role = db.tx {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: notFound("User")
            val role = wireEnum<UserRole>(user[Users.role])
            if (role != UserRole.CUSTOMER && user[Users.kitchenId] != staff.kitchenId) notFound("User")
            if (userId == staff.userId && !req.active) badRequest("You cannot deactivate your own account")
            Users.update({ Users.id eq userId }) {
                it[name] = req.name.trim()
                it[active] = req.active
            }
            audit(clock, staff.userId, "user_updated", "user", userId, "active=${req.active}")
            role
        }
        return users(staff, role).single { it.id == userId }
    }

    // ---- Refunds ----

    suspend fun refunds(staff: Staff, includeClosed: Boolean): List<AdminOrder> = db.tx {
        val orderIds = Payments.join(Orders, JoinType.INNER, Payments.orderId, Orders.id)
            .select(Orders.id)
            .where {
                val base = (Orders.kitchenId eq staff.kitchenId)
                if (includeClosed) base and (Payments.refundStatus.isNotNull())
                else base and (Payments.refundStatus eq RefundStatus.REQUESTED.wire())
            }
            .map { it[Orders.id] }
        if (orderIds.isEmpty()) return@tx emptyList()
        ops.toAdminOrders(Orders.selectAll().where { Orders.id inList orderIds }.orderBy(Orders.updatedAt, SortOrder.DESC).toList())
    }

    /** Owner starts a refund, e.g. for a failed delivery. */
    suspend fun requestRefund(staff: Staff, orderId: String): AdminOrder = db.tx {
        val (order, payment) = orderWithPayment(staff, orderId)
        if (payment[Payments.status] != PaymentStatus.VERIFIED.wire()) rejected(ErrorCodes.INVALID_STATE, "Only verified payments can be refunded")
        if (payment[Payments.refundStatus] != null) rejected(ErrorCodes.INVALID_STATE, "A refund already exists for this order")
        Payments.update({ Payments.id eq payment[Payments.id] }) {
            it[refundStatus] = RefundStatus.REQUESTED.wire()
            it[updatedAt] = clock.nowUtc()
        }
        audit(clock, staff.userId, "refund_requested", "order", orderId)
        ops.toAdminOrders(listOf(Orders.selectAll().where { Orders.id eq order[Orders.id] }.single())).single()
    }

    /** Approves a requested refund and sends it to the gateway. */
    suspend fun approveRefund(staff: Staff, orderId: String, note: String?): AdminOrder = db.tx {
        val (order, payment) = orderWithPayment(staff, orderId)
        if (payment[Payments.refundStatus] != RefundStatus.REQUESTED.wire()) rejected(ErrorCodes.INVALID_STATE, "No pending refund for this order")
        val gateway = gateways[payment[Payments.gateway]] ?: rejected(ErrorCodes.INVALID_STATE, "Unknown payment gateway")
        when (val result = gateway.refund(payment[Payments.gatewayRef], payment[Payments.amountPaise])) {
            is RefundResult.Rejected -> rejected(ErrorCodes.INVALID_STATE, "The gateway refused the refund: ${result.reason}")
            is RefundResult.Accepted -> {
                val now = clock.nowUtc()
                Payments.update({ Payments.id eq payment[Payments.id] }) {
                    it[refundStatus] = RefundStatus.COMPLETED.wire()
                    it[status] = PaymentStatus.REFUNDED.wire()
                    it[updatedAt] = now
                }
                if (order[Orders.status] == OrderStatus.CANCEL_REQUESTED.wire()) {
                    Orders.update({ Orders.id eq orderId }) {
                        it[status] = OrderStatus.CANCELLED.wire()
                        it[updatedAt] = now
                    }
                }
                audit(clock, staff.userId, "refund_approved", "order", orderId, listOfNotNull(result.refundRef, note).joinToString(" | "))
            }
        }
        ops.toAdminOrders(listOf(Orders.selectAll().where { Orders.id eq orderId }.single())).single()
    }

    /** Rejects a refund. A cancellation request goes back to a confirmed order. */
    suspend fun rejectRefund(staff: Staff, orderId: String, note: String?): AdminOrder = db.tx {
        val (order, payment) = orderWithPayment(staff, orderId)
        if (payment[Payments.refundStatus] != RefundStatus.REQUESTED.wire()) rejected(ErrorCodes.INVALID_STATE, "No pending refund for this order")
        val now = clock.nowUtc()
        Payments.update({ Payments.id eq payment[Payments.id] }) {
            it[refundStatus] = RefundStatus.REJECTED.wire()
            it[updatedAt] = now
        }
        if (order[Orders.status] == OrderStatus.CANCEL_REQUESTED.wire()) {
            Orders.update({ Orders.id eq orderId }) {
                it[status] = OrderStatus.CONFIRMED.wire()
                it[updatedAt] = now
            }
        }
        audit(clock, staff.userId, "refund_rejected", "order", orderId, note)
        ops.toAdminOrders(listOf(Orders.selectAll().where { Orders.id eq orderId }.single())).single()
    }

    private fun orderWithPayment(staff: Staff, orderId: String) =
        Payments.join(Orders, JoinType.INNER, Payments.orderId, Orders.id)
            .selectAll()
            .where { (Orders.id eq orderId) and (Orders.kitchenId eq staff.kitchenId) }
            .forUpdate()
            .singleOrNull()
            ?.let { it to it }
            ?: notFound("Order")

    // ---- Reports and exports ----

    suspend fun report(staff: Staff, from: LocalDate?, to: LocalDate?): ReportView = db.tx {
        val today = menu.kitchenDay(staff.kitchenId).today
        val end = to ?: today
        val start = from ?: end.minusDays(6)
        if (start.isAfter(end)) badRequest("The start date is after the end date")
        if (start.plusDays(366).isBefore(end)) badRequest("Choose a range of at most a year")

        val orders = Orders.selectAll()
            .where { (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate greaterEq start) and (Orders.deliveryDate lessEq end) }
            .toList()
        val ids = orders.map { it[Orders.id] }
        val payments = if (ids.isEmpty()) emptyMap() else
            Payments.selectAll().where { Payments.orderId inList ids }.associateBy { it[Payments.orderId] }
        val prepared = orders.filter { it[Orders.status] in OperationsAdmin.PREPARED }
        val lines = if (prepared.isEmpty()) emptyList() else
            OrderLines.selectAll().where { OrderLines.orderId inList prepared.map { it[Orders.id] } }.toList()
        val mealsByOrder = lines.groupBy { it[OrderLines.orderId] }.mapValues { (_, l) -> l.sumOf { it[OrderLines.quantity] } }
        val offices = Offices.selectAll().associateBy { it[Offices.id] }

        fun paid(orderId: String) = payments[orderId]?.let {
            it[Payments.status] == PaymentStatus.VERIFIED.wire() || it[Payments.status] == PaymentStatus.REFUNDED.wire()
        } == true
        fun refunded(orderId: String) = payments[orderId]?.get(Payments.status) == PaymentStatus.REFUNDED.wire()

        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { d ->
            val dayOrders = orders.filter { it[Orders.deliveryDate] == d }
            DaySummary(
                date = d.toString(),
                orders = dayOrders.count { it[Orders.status] in OperationsAdmin.PREPARED },
                meals = dayOrders.sumOf { mealsByOrder[it[Orders.id]] ?: 0 },
                salesPaise = dayOrders.filter { paid(it[Orders.id]) }.sumOf { payments.getValue(it[Orders.id])[Payments.amountPaise] },
                refundedPaise = dayOrders.filter { refunded(it[Orders.id]) }.sumOf { payments.getValue(it[Orders.id])[Payments.amountPaise] },
            )
        }.toList()

        ReportView(
            from = start.toString(),
            to = end.toString(),
            days = days,
            statusCounts = orders.groupingBy { it[Orders.status] }.eachCount(),
            mealTotals = lines.groupBy { it[OrderLines.menuItemId] }
                .map { (id, ls) -> MealTotal(id, ls.first()[OrderLines.name], ls.sumOf { it[OrderLines.quantity] }) }
                .sortedByDescending { it.quantity },
            officeTotals = prepared.groupBy { offices[it[Orders.officeId]]?.get(Offices.name) ?: it[Orders.officeId] }
                .mapValues { (_, os) -> os.sumOf { mealsByOrder[it[Orders.id]] ?: 0 } },
            totalSalesPaise = days.sumOf { it.salesPaise },
            totalRefundedPaise = days.sumOf { it.refundedPaise },
            pendingRefunds = OperationsAdmin.pendingRefundCount(staff.kitchenId),
        )
    }

    /** Orders in a date range as CSV, one row per order. */
    suspend fun ordersCsv(staff: Staff, from: LocalDate?, to: LocalDate?): String {
        val today = db.tx { menu.kitchenDay(staff.kitchenId).today }
        val end = to ?: today
        val start = from ?: end
        if (start.isAfter(end)) badRequest("The start date is after the end date")
        val orders = db.tx {
            ops.toAdminOrders(
                Orders.selectAll()
                    .where { (Orders.kitchenId eq staff.kitchenId) and (Orders.deliveryDate greaterEq start) and (Orders.deliveryDate lessEq end) }
                    .orderBy(Orders.deliveryDate to SortOrder.ASC, Orders.createdAt to SortOrder.ASC)
                    .toList(),
            )
        }
        val header = listOf(
            "order_id", "delivery_date", "created_at", "status", "customer", "phone", "office", "drop_point",
            "items", "meals", "total_inr", "payment_status", "payment_ref", "refund_status",
        )
        val rows = orders.map { o ->
            listOf(
                o.id, o.deliveryDate, o.createdAt, o.status.wire(), o.customerName, o.customerPhone, o.officeName,
                o.dropPointName.orEmpty(), o.lines.joinToString("; ") { "${it.quantity} x ${it.name}" },
                o.lines.sumOf { it.quantity }.toString(), "%d.%02d".format(o.totalPaise / 100, o.totalPaise % 100),
                o.paymentStatus?.wire().orEmpty(), o.paymentReference.orEmpty(), o.refundStatus?.wire().orEmpty(),
            )
        }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csvCell(it) } } + "\r\n"
    }

    private fun csvCell(value: String): String {
        // Neutralise spreadsheet formulas, then quote.
        val safe = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    // ---- Audit ----

    suspend fun audit(staff: Staff, limit: Int): List<AuditEntry> = db.tx {
        val kitchenUsers = Users.selectAll().where { (Users.kitchenId eq staff.kitchenId) or (Users.role eq UserRole.CUSTOMER.wire()) }
            .associate { it[Users.id] to it[Users.name] }
        AuditLog.selectAll()
            .orderBy(AuditLog.id, SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map {
                AuditEntry(
                    id = it[AuditLog.id],
                    actorName = it[AuditLog.actorId]?.let { a -> kitchenUsers[a]?.ifBlank { "(customer)" } ?: "(unknown)" },
                    action = it[AuditLog.action],
                    entity = it[AuditLog.entity],
                    entityId = it[AuditLog.entityId],
                    details = it[AuditLog.details],
                    createdAt = it[AuditLog.createdAt].toString(),
                )
            }
    }
}
