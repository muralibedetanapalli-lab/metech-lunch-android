package com.metech.lunch.backend.dev

import com.metech.lunch.backend.common.encodeStringList
import com.metech.lunch.backend.common.newId
import com.metech.lunch.backend.common.nowUtc
import com.metech.lunch.backend.common.wire
import com.metech.lunch.backend.db.DailyMenuItems
import com.metech.lunch.backend.db.Db
import com.metech.lunch.backend.db.DropPoints
import com.metech.lunch.backend.db.Kitchens
import com.metech.lunch.backend.db.MenuItems
import com.metech.lunch.backend.db.Offices
import com.metech.lunch.backend.db.Users
import com.metech.lunch.core.model.UserRole
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId

/**
 * Dev-only demo data: one kitchen, two offices, a small menu published for the next week,
 * an owner and a delivery partner. Safe to run on every start.
 */
class DemoSeed(private val db: Db, private val clock: Clock) {
    private val log = LoggerFactory.getLogger(DemoSeed::class.java)

    fun run() = db.txBlocking {
        val now = clock.nowUtc()
        if (Kitchens.selectAll().where { Kitchens.id eq KITCHEN_ID }.empty()) {
            Kitchens.insert {
                it[id] = KITCHEN_ID
                it[name] = "Metech Central Kitchen"
                it[address] = "Plot 12, Madhapur, Hyderabad"
                it[timezone] = "Asia/Kolkata"
                it[cutoffTime] = LocalTime.of(10, 0)
                it[createdAt] = now
            }
            office("office-tower-a", "Tech Park Tower A", "Hitec City, Hyderabad", listOf("Reception", "Floor 5 pantry"))
            office("office-cyber", "Cyber Towers", "Hitec City, Hyderabad", listOf("Main lobby"))

            menuItem("meal-veg-thali", "Veg thali", "Rice, dal, two curries, roti, curd", 12000, listOf("veg"), listOf("dairy", "gluten"))
            menuItem("meal-paneer-bowl", "Paneer rice bowl", "Paneer tikka on jeera rice", 14000, listOf("veg"), listOf("dairy"))
            menuItem("meal-chicken-biryani", "Chicken biryani", "Hyderabadi dum biryani with raita", 16000, listOf("non-veg"), listOf("dairy"))
            menuItem("meal-curd-rice", "Curd rice", "Tempered curd rice with pickle", 8000, listOf("veg"), listOf("dairy"))

            user("+919999900000", UserRole.OWNER, "Demo Owner")
            user("+919999900001", UserRole.DELIVERY_PARTNER, "Ravi (partner)")
            log.info("Demo data created")
        }

        // Keep a menu published for today and the next 6 days.
        val today = clock.instant().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
        val items = MenuItems.selectAll().where { MenuItems.kitchenId eq KITCHEN_ID }.toList()
        for (offset in 0L..6L) {
            val date = today.plusDays(offset)
            for (item in items) {
                val exists = DailyMenuItems.selectAll().where {
                    (DailyMenuItems.kitchenId eq KITCHEN_ID) and (DailyMenuItems.menuDate eq date) and
                        (DailyMenuItems.menuItemId eq item[MenuItems.id])
                }.any()
                if (!exists) {
                    DailyMenuItems.insert {
                        it[id] = newId()
                        it[kitchenId] = KITCHEN_ID
                        it[menuDate] = date
                        it[menuItemId] = item[MenuItems.id]
                        it[pricePaise] = item[MenuItems.pricePaise]
                        it[capacity] = 50
                        it[available] = true
                    }
                }
            }
        }
    }

    private fun office(id: String, name: String, address: String, dropPoints: List<String>) {
        Offices.insert {
            it[Offices.id] = id
            it[kitchenId] = KITCHEN_ID
            it[Offices.name] = name
            it[Offices.address] = address
            it[active] = true
        }
        dropPoints.forEachIndexed { i, dp ->
            DropPoints.insert {
                it[DropPoints.id] = "$id-dp${i + 1}"
                it[officeId] = id
                it[DropPoints.name] = dp
                it[active] = true
            }
        }
    }

    private fun menuItem(id: String, name: String, description: String, price: Long, tags: List<String>, allergens: List<String>) {
        MenuItems.insert {
            it[MenuItems.id] = id
            it[kitchenId] = KITCHEN_ID
            it[MenuItems.name] = name
            it[MenuItems.description] = description
            it[pricePaise] = price
            it[dietaryTags] = encodeStringList(tags)
            it[MenuItems.allergens] = encodeStringList(allergens)
            it[active] = true
        }
    }

    private fun user(phone: String, role: UserRole, name: String) {
        Users.insert {
            it[id] = newId()
            it[Users.phone] = phone
            it[Users.role] = role.wire()
            it[Users.name] = name
            it[kitchenId] = KITCHEN_ID
            it[active] = true
            it[createdAt] = clock.nowUtc()
        }
    }

    companion object {
        const val KITCHEN_ID = "kitchen-main"
    }
}
