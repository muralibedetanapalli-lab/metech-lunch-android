package com.metech.lunch.backend.admin

import com.metech.lunch.backend.common.audit
import com.metech.lunch.backend.common.badRequest
import com.metech.lunch.backend.common.decodeStringList
import com.metech.lunch.backend.common.encodeStringList
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.notFound
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.Kitchens
import com.metech.lunch.backend.db.MenuItems
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.menu.MenuService
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Who is calling the admin API, and which kitchen they work for. */
data class Staff(val userId: String, val isOwner: Boolean, val kitchenId: String)

fun parseDate(value: String?): LocalDate? = value?.let {
    try { LocalDate.parse(it) } catch (e: DateTimeParseException) { badRequest("Dates must look like 2026-11-04") }
}

/** Kitchen settings, offices and drop points, meal catalogue and the daily menu. */
class CatalogAdmin(private val db: Db, private val clock: Clock, private val menu: MenuService) {

    // ---- Kitchen ----

    suspend fun kitchen(staff: Staff): KitchenSettings = db.tx { kitchenSettings(staff.kitchenId) }

    suspend fun updateKitchen(staff: Staff, req: UpdateKitchenRequest): KitchenSettings = db.tx {
        if (req.name.isBlank() || req.address.isBlank()) badRequest("Name and address are required")
        val cutoff = try { LocalTime.parse(req.cutoffTime) } catch (e: DateTimeParseException) { badRequest("Cutoff must look like 10:00") }
        Kitchens.update({ Kitchens.id eq staff.kitchenId }) {
            it[name] = req.name.trim()
            it[address] = req.address.trim()
            it[cutoffTime] = cutoff
        }
        audit(clock, staff.userId, "kitchen_updated", "kitchen", staff.kitchenId, "cutoff=$cutoff")
        kitchenSettings(staff.kitchenId)
    }

    private fun kitchenSettings(kitchenId: String): KitchenSettings {
        val k = Kitchens.selectAll().where { Kitchens.id eq kitchenId }.single()
        return KitchenSettings(
            id = k[Kitchens.id],
            name = k[Kitchens.name],
            address = k[Kitchens.address],
            timezone = k[Kitchens.timezone],
            cutoffTime = k[Kitchens.cutoffTime].toString(),
            today = menu.kitchenDay(kitchenId).today.toString(),
        )
    }

    // ---- Offices ----

    suspend fun offices(staff: Staff): List<AdminOffice> = db.tx {
        val dps = DropPoints.selectAll().toList().groupBy { it[DropPoints.officeId] }
        Offices.selectAll().where { Offices.kitchenId eq staff.kitchenId }.orderBy(Offices.name).map { o ->
            AdminOffice(
                id = o[Offices.id],
                name = o[Offices.name],
                address = o[Offices.address],
                active = o[Offices.active],
                dropPoints = dps[o[Offices.id]].orEmpty().sortedBy { it[DropPoints.name] }.map {
                    AdminDropPoint(it[DropPoints.id], it[DropPoints.name], it[DropPoints.instructions], it[DropPoints.active])
                },
            )
        }
    }

    suspend fun createOffice(staff: Staff, req: SaveOfficeRequest): List<AdminOffice> {
        validateOffice(req)
        db.tx {
            val id = newId()
            Offices.insert {
                it[Offices.id] = id
                it[kitchenId] = staff.kitchenId
                it[name] = req.name.trim()
                it[address] = req.address.trim()
                it[active] = req.active
            }
            audit(clock, staff.userId, "office_created", "office", id, req.name)
        }
        return offices(staff)
    }

    suspend fun updateOffice(staff: Staff, officeId: String, req: SaveOfficeRequest): List<AdminOffice> {
        validateOffice(req)
        db.tx {
            ownOffice(staff, officeId)
            Offices.update({ Offices.id eq officeId }) {
                it[name] = req.name.trim()
                it[address] = req.address.trim()
                it[active] = req.active
            }
            audit(clock, staff.userId, "office_updated", "office", officeId, "active=${req.active}")
        }
        return offices(staff)
    }

    suspend fun createDropPoint(staff: Staff, officeId: String, req: SaveDropPointRequest): List<AdminOffice> {
        if (req.name.isBlank()) badRequest("Name is required")
        db.tx {
            ownOffice(staff, officeId)
            val id = newId()
            DropPoints.insert {
                it[DropPoints.id] = id
                it[DropPoints.officeId] = officeId
                it[name] = req.name.trim()
                it[instructions] = req.instructions?.trim()?.ifEmpty { null }
                it[active] = req.active
            }
            audit(clock, staff.userId, "drop_point_created", "drop_point", id, req.name)
        }
        return offices(staff)
    }

    suspend fun updateDropPoint(staff: Staff, dropPointId: String, req: SaveDropPointRequest): List<AdminOffice> {
        if (req.name.isBlank()) badRequest("Name is required")
        db.tx {
            val dp = DropPoints.selectAll().where { DropPoints.id eq dropPointId }.singleOrNull() ?: notFound("Drop point")
            ownOffice(staff, dp[DropPoints.officeId])
            DropPoints.update({ DropPoints.id eq dropPointId }) {
                it[name] = req.name.trim()
                it[instructions] = req.instructions?.trim()?.ifEmpty { null }
                it[active] = req.active
            }
            audit(clock, staff.userId, "drop_point_updated", "drop_point", dropPointId, "active=${req.active}")
        }
        return offices(staff)
    }

    private fun validateOffice(req: SaveOfficeRequest) {
        if (req.name.isBlank() || req.address.isBlank()) badRequest("Name and address are required")
    }

    private fun ownOffice(staff: Staff, officeId: String): ResultRow =
        Offices.selectAll().where { (Offices.id eq officeId) and (Offices.kitchenId eq staff.kitchenId) }.singleOrNull()
            ?: notFound("Office")

    // ---- Meal catalogue ----

    suspend fun menuItems(staff: Staff): List<AdminMenuItem> = db.tx {
        MenuItems.selectAll().where { MenuItems.kitchenId eq staff.kitchenId }.orderBy(MenuItems.name).map { it.toAdminMenuItem() }
    }

    suspend fun createMenuItem(staff: Staff, req: SaveMenuItemRequest): AdminMenuItem {
        validateMenuItem(req)
        return db.tx {
            val id = newId()
            MenuItems.insert {
                it[MenuItems.id] = id
                it[kitchenId] = staff.kitchenId
                writeMenuItem(it, req)
            }
            audit(clock, staff.userId, "menu_item_created", "menu_item", id, req.name)
            MenuItems.selectAll().where { MenuItems.id eq id }.single().toAdminMenuItem()
        }
    }

    suspend fun updateMenuItem(staff: Staff, id: String, req: SaveMenuItemRequest): AdminMenuItem {
        validateMenuItem(req)
        return db.tx {
            MenuItems.selectAll().where { (MenuItems.id eq id) and (MenuItems.kitchenId eq staff.kitchenId) }.singleOrNull()
                ?: notFound("Meal")
            MenuItems.update({ MenuItems.id eq id }) { writeMenuItem(it, req) }
            audit(clock, staff.userId, "menu_item_updated", "menu_item", id, "price=${req.pricePaise} active=${req.active}")
            MenuItems.selectAll().where { MenuItems.id eq id }.single().toAdminMenuItem()
        }
    }

    private fun validateMenuItem(req: SaveMenuItemRequest) {
        if (req.name.isBlank()) badRequest("Name is required")
        if (req.pricePaise < 0) badRequest("Price cannot be negative")
    }

    private fun writeMenuItem(it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, req: SaveMenuItemRequest) {
        it[MenuItems.name] = req.name.trim()
        it[MenuItems.description] = req.description.trim()
        it[MenuItems.imageUrl] = req.imageUrl?.trim()?.ifEmpty { null }
        it[MenuItems.pricePaise] = req.pricePaise
        it[MenuItems.dietaryTags] = encodeStringList(req.dietaryTags.map { t -> t.trim() }.filter { t -> t.isNotEmpty() })
        it[MenuItems.allergens] = encodeStringList(req.allergens.map { t -> t.trim() }.filter { t -> t.isNotEmpty() })
        it[MenuItems.active] = req.active
    }

    private fun ResultRow.toAdminMenuItem() = AdminMenuItem(
        id = this[MenuItems.id],
        name = this[MenuItems.name],
        description = this[MenuItems.description],
        imageUrl = this[MenuItems.imageUrl],
        pricePaise = this[MenuItems.pricePaise],
        dietaryTags = decodeStringList(this[MenuItems.dietaryTags]),
        allergens = decodeStringList(this[MenuItems.allergens]),
        active = this[MenuItems.active],
    )

    // ---- Daily menu ----

    suspend fun dailyMenu(staff: Staff, date: LocalDate?): DailyMenuView = db.tx { dailyMenuView(staff, date) }

    /**
     * Replaces the published menu for a date. Meals that already have orders that day can
     * be marked unavailable but not removed, and their capacity cannot drop below what is sold.
     */
    suspend fun saveDailyMenu(staff: Staff, date: LocalDate, req: SaveDailyMenuRequest): DailyMenuView = db.tx {
        val day = menu.kitchenDay(staff.kitchenId)
        if (date.isBefore(day.today)) badRequest("Past menus cannot be changed")
        if (req.lines.map { it.menuItemId }.toSet().size != req.lines.size) badRequest("Each meal may appear only once")
        req.lines.forEach {
            if (it.pricePaise < 0) badRequest("Price cannot be negative")
            if (it.capacity != null && it.capacity < 0) badRequest("Capacity cannot be negative")
        }
        val validItems = MenuItems.selectAll().where { MenuItems.kitchenId eq staff.kitchenId }.map { it[MenuItems.id] }.toSet()
        req.lines.firstOrNull { it.menuItemId !in validItems }?.let { notFound("Meal ${it.menuItemId}") }

        val existing = DailyMenuItems.selectAll()
            .where { (DailyMenuItems.kitchenId eq staff.kitchenId) and (DailyMenuItems.menuDate eq date) }
            .forUpdate()
            .associateBy { it[DailyMenuItems.menuItemId] }
        val reserved = menu.reservedQuantities(existing.values.map { it[DailyMenuItems.id] }, excludeOrderId = null)
        val wanted = req.lines.associateBy { it.menuItemId }

        for ((menuItemId, row) in existing) {
            val sold = reserved[row[DailyMenuItems.id]] ?: 0
            val line = wanted[menuItemId]
            if (line == null) {
                if (sold > 0) badRequest("A meal with orders cannot be removed; mark it unavailable instead")
                DailyMenuItems.deleteWhere { DailyMenuItems.id eq row[DailyMenuItems.id] }
            } else {
                if (line.capacity != null && line.capacity < sold) badRequest("Capacity cannot be lower than the $sold already ordered")
                if (line.pricePaise != row[DailyMenuItems.pricePaise] && sold > 0) {
                    badRequest("The price cannot change after orders have been placed for that day")
                }
                DailyMenuItems.update({ DailyMenuItems.id eq row[DailyMenuItems.id] }) {
                    it[pricePaise] = line.pricePaise
                    it[capacity] = line.capacity
                    it[available] = line.available
                }
            }
        }
        for ((menuItemId, line) in wanted) {
            if (menuItemId in existing) continue
            DailyMenuItems.insert {
                it[id] = newId()
                it[kitchenId] = staff.kitchenId
                it[menuDate] = date
                it[DailyMenuItems.menuItemId] = menuItemId
                it[pricePaise] = line.pricePaise
                it[capacity] = line.capacity
                it[available] = line.available
            }
        }
        audit(clock, staff.userId, "daily_menu_saved", "daily_menu", date.toString(), "${req.lines.size} meals")
        dailyMenuView(staff, date)
    }

    private fun dailyMenuView(staff: Staff, date: LocalDate?): DailyMenuView {
        val d = date ?: menu.kitchenDay(staff.kitchenId).today
        val rows = DailyMenuItems.join(MenuItems, org.jetbrains.exposed.sql.JoinType.INNER, DailyMenuItems.menuItemId, MenuItems.id)
            .selectAll()
            .where { (DailyMenuItems.kitchenId eq staff.kitchenId) and (DailyMenuItems.menuDate eq d) }
            .orderBy(MenuItems.name)
            .toList()
        val reserved = menu.reservedQuantities(rows.map { it[DailyMenuItems.id] }, excludeOrderId = null)
        return DailyMenuView(
            date = d.toString(),
            published = rows.map {
                DailyMenuEntry(
                    menuItemId = it[MenuItems.id],
                    name = it[MenuItems.name],
                    pricePaise = it[DailyMenuItems.pricePaise],
                    capacity = it[DailyMenuItems.capacity],
                    available = it[DailyMenuItems.available],
                    reserved = reserved[it[DailyMenuItems.id]] ?: 0,
                )
            },
        )
    }
}
