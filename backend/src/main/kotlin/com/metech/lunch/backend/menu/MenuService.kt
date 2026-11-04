package com.metech.lunch.backend.menu

import com.metech.lunch.backend.AppConfig
import com.metech.lunch.backend.common.decodeStringList
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.Kitchens
import com.metech.lunch.backend.db.MenuItems
import com.metech.lunch.backend.db.OrderLines
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.Orders
import com.metech.lunch.backend.common.rejected
import com.metech.lunch.core.model.DailyMenu
import com.metech.lunch.core.model.DropPoint
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.model.Office
import com.metech.lunch.core.model.OrderStatus
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** A kitchen's local "today" and cutoff, computed from the injected clock. */
data class KitchenDay(val kitchenId: String, val zone: ZoneId, val today: LocalDate, val cutoffAt: ZonedDateTime, val now: ZonedDateTime) {
    val beforeCutoff: Boolean get() = now.isBefore(cutoffAt)
}

class MenuService(
    private val config: AppConfig,
    private val db: Db,
    private val clock: Clock,
) {

    suspend fun offices(): List<Office> = db.tx {
        val dropPoints = DropPoints.selectAll().where { DropPoints.active eq true }
            .map { DropPoint(it[DropPoints.id], it[DropPoints.officeId], it[DropPoints.name], it[DropPoints.instructions]) }
            .groupBy { it.officeId }
        Offices.selectAll().where { Offices.active eq true }.orderBy(Offices.name).map {
            Office(
                id = it[Offices.id],
                kitchenId = it[Offices.kitchenId],
                name = it[Offices.name],
                address = it[Offices.address],
                dropPoints = dropPoints[it[Offices.id]].orEmpty(),
            )
        }
    }

    suspend fun todaysMenu(officeId: String): DailyMenu = db.tx {
        val office = activeOffice(officeId)
        val day = kitchenDay(office[Offices.kitchenId])
        val rows = (DailyMenuItems.join(MenuItems, JoinType.INNER, DailyMenuItems.menuItemId, MenuItems.id))
            .selectAll()
            .where { (DailyMenuItems.kitchenId eq day.kitchenId) and (DailyMenuItems.menuDate eq day.today) }
            .orderBy(MenuItems.name)
            .toList()
        val reserved = reservedQuantities(rows.map { it[DailyMenuItems.id] }, excludeOrderId = null)
        val items = rows.map { row ->
            val capacity = row[DailyMenuItems.capacity]
            val remaining = capacity?.let { (it - (reserved[row[DailyMenuItems.id]] ?: 0)).coerceAtLeast(0) }
            MenuItem(
                id = row[MenuItems.id],
                name = row[MenuItems.name],
                description = row[MenuItems.description],
                imageUrl = row[MenuItems.imageUrl],
                pricePaise = row[DailyMenuItems.pricePaise],
                available = row[DailyMenuItems.available] && row[MenuItems.active] && (remaining == null || remaining > 0),
                remainingCapacity = remaining,
                dietaryTags = decodeStringList(row[MenuItems.dietaryTags]),
                allergens = decodeStringList(row[MenuItems.allergens]),
            )
        }
        DailyMenu(
            kitchenId = day.kitchenId,
            date = day.today.toString(),
            orderingOpen = day.beforeCutoff && items.any { it.available },
            cutoffAt = day.cutoffAt.toOffsetDateTime().toString(),
            items = items,
        )
    }

    // ---- Helpers used inside other services' transactions ----

    fun activeOffice(officeId: String): ResultRow =
        Offices.selectAll().where { (Offices.id eq officeId) and (Offices.active eq true) }.singleOrNull()
            ?: rejected(ErrorCodes.OFFICE_NOT_SERVICEABLE, "This office is not serviceable")

    fun kitchenDay(kitchenId: String): KitchenDay {
        val kitchen = Kitchens.selectAll().where { Kitchens.id eq kitchenId }.single()
        val zone = ZoneId.of(kitchen[Kitchens.timezone])
        val now = clock.instant().atZone(zone)
        val today = now.toLocalDate()
        return KitchenDay(kitchenId, zone, today, today.atTime(kitchen[Kitchens.cutoffTime]).atZone(zone), now)
    }

    /**
     * Meals already taken per daily menu item: every order that is paid or in progress,
     * plus unpaid orders still inside the payment hold window.
     */
    fun reservedQuantities(dailyMenuItemIds: List<String>, excludeOrderId: String?): Map<String, Int> {
        if (dailyMenuItemIds.isEmpty()) return emptyMap()
        val holdStart = clock.nowUtc().minus(config.paymentHold)
        val total = OrderLines.quantity.sum()
        return OrderLines.join(Orders, JoinType.INNER, OrderLines.orderId, Orders.id)
            .select(OrderLines.dailyMenuItemId, total)
            .where {
                val holding = (Orders.status inList HOLDING_STATUSES) or
                    ((Orders.status eq OrderStatus.PENDING_PAYMENT.wire()) and (Orders.createdAt greaterEq holdStart))
                var cond = (OrderLines.dailyMenuItemId inList dailyMenuItemIds) and holding
                if (excludeOrderId != null) cond = cond and (Orders.id neq excludeOrderId)
                cond
            }
            .groupBy(OrderLines.dailyMenuItemId)
            .associate { it[OrderLines.dailyMenuItemId] to (it[total] ?: 0) }
    }

    companion object {
        /** Statuses that keep their meals regardless of age. */
        val HOLDING_STATUSES = listOf(
            OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED, OrderStatus.PARTIALLY_DELIVERED, OrderStatus.DELIVERY_FAILED,
            OrderStatus.CANCEL_REQUESTED,
        ).map { it.wire() }
    }
}
