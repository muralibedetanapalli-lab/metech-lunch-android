package com.metech.lunch.backend.admin

import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PaymentStatus
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.model.UserRole
import kotlinx.serialization.Serializable

// Request/response bodies for the owner and kitchen dashboard (/api/admin/*).
// The dashboard mirrors these in dashboard/src/api/types.ts.

// ---- Kitchen, offices ----

@Serializable
data class KitchenSettings(val id: String, val name: String, val address: String, val timezone: String, val cutoffTime: String, val today: String)

@Serializable
data class UpdateKitchenRequest(val name: String, val address: String, val cutoffTime: String)

@Serializable
data class AdminDropPoint(val id: String, val name: String, val instructions: String?, val active: Boolean)

@Serializable
data class AdminOffice(val id: String, val name: String, val address: String, val active: Boolean, val dropPoints: List<AdminDropPoint>)

@Serializable
data class SaveOfficeRequest(val name: String, val address: String, val active: Boolean = true)

@Serializable
data class SaveDropPointRequest(val name: String, val instructions: String? = null, val active: Boolean = true)

// ---- Menu ----

@Serializable
data class AdminMenuItem(
    val id: String,
    val name: String,
    val description: String,
    val imageUrl: String?,
    val pricePaise: Long,
    val dietaryTags: List<String>,
    val allergens: List<String>,
    val active: Boolean,
)

@Serializable
data class SaveMenuItemRequest(
    val name: String,
    val description: String = "",
    val imageUrl: String? = null,
    val pricePaise: Long,
    val dietaryTags: List<String> = emptyList(),
    val allergens: List<String> = emptyList(),
    val active: Boolean = true,
)

@Serializable
data class DailyMenuEntry(
    val menuItemId: String,
    val name: String,
    val pricePaise: Long,
    val capacity: Int?,
    val available: Boolean,
    val reserved: Int,
)

@Serializable
data class DailyMenuView(val date: String, val published: List<DailyMenuEntry>)

@Serializable
data class DailyMenuLine(val menuItemId: String, val pricePaise: Long, val capacity: Int?, val available: Boolean)

@Serializable
data class SaveDailyMenuRequest(val lines: List<DailyMenuLine>)

// ---- Orders, prep, packing ----

@Serializable
data class AdminOrderLine(val name: String, val quantity: Int)

@Serializable
data class AdminOrder(
    val id: String,
    val deliveryDate: String,
    val createdAt: String,
    val status: OrderStatus,
    val customerName: String,
    val customerPhone: String,
    val officeId: String,
    val officeName: String,
    val dropPointName: String?,
    val lines: List<AdminOrderLine>,
    val totalPaise: Long,
    val paymentStatus: PaymentStatus?,
    val paymentReference: String?,
    val refundStatus: RefundStatus?,
    val batchId: String?,
)

@Serializable
data class MealTotal(val menuItemId: String, val name: String, val quantity: Int)

@Serializable
data class PackingOrder(val orderId: String, val customerName: String, val lines: List<AdminOrderLine>, val status: OrderStatus)

@Serializable
data class PackingGroup(
    val officeId: String,
    val officeName: String,
    val officeAddress: String,
    val dropPointId: String?,
    val dropPointName: String?,
    val mealTotals: List<MealTotal>,
    val orders: List<PackingOrder>,
)

@Serializable
data class PrepView(
    val date: String,
    val cutoffPassed: Boolean,
    val mealTotals: List<MealTotal>,
    val totalMeals: Int,
    val confirmedOrders: Int,
    val awaitingPrep: Int,
    val pendingCancellations: Int,
    val groups: List<PackingGroup>,
)

@Serializable
data class DateRequest(val date: String)

@Serializable
data class CountResult(val updated: Int)

// ---- Batches ----

@Serializable
data class AdminBatch(
    val id: String,
    val deliveryDate: String,
    val officeName: String,
    val dropPointName: String?,
    val status: BatchStatus,
    val partnerId: String?,
    val partnerName: String?,
    val coordinatorName: String?,
    val coordinatorPhone: String?,
    val coordinatorOtp: String?,
    val orderCount: Int,
    val totalMeals: Int,
    val pickupCount: Int?,
    val orders: List<PackingOrder>,
)

@Serializable
data class AssignBatchRequest(
    val partnerId: String,
    val coordinatorName: String? = null,
    val coordinatorPhone: String? = null,
    /** Generate an office-coordinator OTP that covers the whole batch. */
    val coordinatorOtp: Boolean = false,
)

@Serializable
data class AdminIssue(
    val id: Long,
    val batchId: String,
    val officeName: String,
    val partnerName: String,
    val orderIds: List<String>,
    val reason: String,
    val partial: Boolean,
    val createdAt: String,
)

// ---- People ----

@Serializable
data class AdminUser(
    val id: String,
    val name: String,
    val phone: String,
    val email: String?,
    val role: UserRole,
    val active: Boolean,
    val createdAt: String,
    val orderCount: Int? = null,
)

@Serializable
data class CreateUserRequest(val phone: String, val name: String, val role: UserRole)

@Serializable
data class UpdateUserRequest(val name: String, val active: Boolean)

// ---- Money, reports, audit ----

@Serializable
data class RefundDecision(val note: String? = null)

@Serializable
data class DaySummary(
    val date: String,
    val orders: Int,
    val meals: Int,
    val salesPaise: Long,
    val refundedPaise: Long,
)

@Serializable
data class ReportView(
    val from: String,
    val to: String,
    val days: List<DaySummary>,
    val statusCounts: Map<String, Int>,
    val mealTotals: List<MealTotal>,
    val officeTotals: Map<String, Int>,
    val totalSalesPaise: Long,
    val totalRefundedPaise: Long,
    val pendingRefunds: Int,
)

@Serializable
data class OverviewView(
    val date: String,
    val orderingOpen: Boolean,
    val cutoffTime: String,
    val statusCounts: Map<String, Int>,
    val meals: Int,
    /** Null for kitchen staff, who do not see financials. */
    val salesPaise: Long?,
    val pendingRefunds: Int?,
    val openIssues: Int,
    val unassignedBatches: Int,
)

@Serializable
data class AuditEntry(val id: Long, val actorName: String?, val action: String, val entity: String, val entityId: String, val details: String?, val createdAt: String)

@Serializable
data class TrackedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val speedMps: Float? = null,
    val recordedAt: String,
)

@Serializable
data class TrackingBatch(
    val id: String,
    val status: BatchStatus,
    val partnerName: String?,
    val partnerPhone: String?,
    val pickedUpAt: String?,
    val totalOrders: Int,
    val deliveredOrders: Int,
)

/** One order's delivery state for the live map. [live] is true while its meals are on the road. */
@Serializable
data class OrderTracking(
    val order: AdminOrder,
    val batch: TrackingBatch?,
    val live: Boolean,
    val location: TrackedLocation?,
    /** Fixes since pickup, oldest first (at most a few hundred). */
    val trail: List<TrackedLocation>,
)
