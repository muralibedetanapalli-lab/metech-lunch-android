package com.metech.lunch.backend.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

// Mirrors db/migration/V1__init.sql. Flyway owns the schema; these are mappings only.

object Kitchens : Table("kitchens") {
    val id = varchar("id", 36)
    val name = text("name")
    val address = text("address")
    val timezone = text("timezone")
    val cutoffTime = time("cutoff_time")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Offices : Table("offices") {
    val id = varchar("id", 36)
    val kitchenId = varchar("kitchen_id", 36)
    val name = text("name")
    val address = text("address")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

object DropPoints : Table("drop_points") {
    val id = varchar("id", 36)
    val officeId = varchar("office_id", 36)
    val name = text("name")
    val instructions = text("instructions").nullable()
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

object Users : Table("users") {
    val id = varchar("id", 36)
    val phone = text("phone")
    val role = text("role")
    val name = text("name")
    val email = text("email").nullable()
    val kitchenId = varchar("kitchen_id", 36).nullable()
    val defaultOfficeId = varchar("default_office_id", 36).nullable()
    val defaultDropPointId = varchar("default_drop_point_id", 36).nullable()
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object OtpCodes : Table("otp_codes") {
    val id = long("id").autoIncrement()
    val phone = text("phone")
    val role = text("role")
    val codeHash = text("code_hash")
    val expiresAt = timestampWithTimeZone("expires_at")
    val attempts = integer("attempts")
    val consumed = bool("consumed")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val tokenHash = text("token_hash")
    val userId = varchar("user_id", 36)
    val expiresAt = timestampWithTimeZone("expires_at")
    val revoked = bool("revoked")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(tokenHash)
}

object MenuItems : Table("menu_items") {
    val id = varchar("id", 36)
    val kitchenId = varchar("kitchen_id", 36)
    val name = text("name")
    val description = text("description")
    val imageUrl = text("image_url").nullable()
    val pricePaise = long("price_paise")
    val dietaryTags = text("dietary_tags")
    val allergens = text("allergens")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

object DailyMenuItems : Table("daily_menu_items") {
    val id = varchar("id", 36)
    val kitchenId = varchar("kitchen_id", 36)
    val menuDate = date("menu_date")
    val menuItemId = varchar("menu_item_id", 36)
    val pricePaise = long("price_paise")
    val capacity = integer("capacity").nullable()
    val available = bool("available")
    override val primaryKey = PrimaryKey(id)
}

object Orders : Table("orders") {
    val id = varchar("id", 36)
    val kitchenId = varchar("kitchen_id", 36)
    val customerId = varchar("customer_id", 36)
    val officeId = varchar("office_id", 36)
    val dropPointId = varchar("drop_point_id", 36).nullable()
    val deliveryDate = date("delivery_date")
    val status = text("status")
    val totalPaise = long("total_paise")
    val idempotencyKey = text("idempotency_key")
    val deliveryOtp = text("delivery_otp")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object OrderLines : Table("order_lines") {
    val id = long("id").autoIncrement()
    val orderId = varchar("order_id", 36)
    val dailyMenuItemId = varchar("daily_menu_item_id", 36)
    val menuItemId = varchar("menu_item_id", 36)
    val name = text("name")
    val quantity = integer("quantity")
    val unitPricePaise = long("unit_price_paise")
    override val primaryKey = PrimaryKey(id)
}

object Payments : Table("payments") {
    val id = varchar("id", 36)
    val orderId = varchar("order_id", 36)
    val gateway = text("gateway")
    val gatewayRef = text("gateway_ref")
    val checkoutUrl = text("checkout_url").nullable()
    val status = text("status")
    val refundStatus = text("refund_status").nullable()
    val amountPaise = long("amount_paise")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PaymentEvents : Table("payment_events") {
    val id = long("id").autoIncrement()
    val gateway = text("gateway")
    val eventId = text("event_id")
    val gatewayRef = text("gateway_ref")
    val payload = text("payload")
    val receivedAt = timestampWithTimeZone("received_at")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(gateway, eventId)
    }
}

object DeliveryBatches : Table("delivery_batches") {
    val id = varchar("id", 36)
    val kitchenId = varchar("kitchen_id", 36)
    val deliveryDate = date("delivery_date")
    val officeId = varchar("office_id", 36)
    val dropPointId = varchar("drop_point_id", 36).nullable()
    val partnerId = varchar("partner_id", 36).nullable()
    val status = text("status")
    val coordinatorName = text("coordinator_name").nullable()
    val coordinatorPhone = text("coordinator_phone").nullable()
    val coordinatorOtp = text("coordinator_otp").nullable()
    val pickupCount = integer("pickup_count").nullable()
    val pickedUpAt = timestampWithTimeZone("picked_up_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** GPS fixes from a delivery partner's phone, recorded only while they have a batch on the road. */
object PartnerLocations : Table("partner_locations") {
    val id = long("id").autoIncrement()
    val partnerId = varchar("partner_id", 36)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val accuracyM = float("accuracy_m").nullable()
    val speedMps = float("speed_mps").nullable()
    val headingDeg = float("heading_deg").nullable()
    val recordedAt = timestampWithTimeZone("recorded_at")
    val receivedAt = timestampWithTimeZone("received_at")
    override val primaryKey = PrimaryKey(id)
}

object BatchOrders : Table("batch_orders") {
    val batchId = varchar("batch_id", 36)
    val orderId = varchar("order_id", 36)
    override val primaryKey = PrimaryKey(batchId, orderId)
}

object DeliveryIssues : Table("delivery_issues") {
    val id = long("id").autoIncrement()
    val batchId = varchar("batch_id", 36)
    val partnerId = varchar("partner_id", 36)
    val orderIds = text("order_ids")
    val reason = text("reason")
    val partial = bool("partial")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AuditLog : Table("audit_log") {
    val id = long("id").autoIncrement()
    val actorId = varchar("actor_id", 36).nullable()
    val action = text("action")
    val entity = text("entity")
    val entityId = text("entity_id")
    val details = text("details").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}
