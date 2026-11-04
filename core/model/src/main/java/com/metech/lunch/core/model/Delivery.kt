package com.metech.lunch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A group of orders for one office/drop point, assigned to one delivery partner. */
@Serializable
data class DeliveryBatch(
    val id: String,
    val kitchenId: String,
    val deliveryDate: String,
    val pickupLocation: String,
    val office: Office,
    val dropPoint: DropPoint?,
    val contact: RecipientContact?,
    val orders: List<BatchOrder>,
    val totalMeals: Int,
    val status: BatchStatus,
)

@Serializable
data class BatchOrder(
    val orderId: String,
    val customerName: String,
    val mealCount: Int,
    val status: OrderStatus,
)

@Serializable
data class RecipientContact(val name: String, val phone: String)

@Serializable
enum class BatchStatus {
    /** Created by the owner but no partner yet; partners never see these. */
    @SerialName("unassigned") UNASSIGNED,
    @SerialName("assigned") ASSIGNED,
    @SerialName("picked_up") PICKED_UP,
    @SerialName("in_transit") IN_TRANSIT,
    @SerialName("delivered") DELIVERED,
    @SerialName("partially_delivered") PARTIALLY_DELIVERED,
    @SerialName("failed") FAILED,
}

@Serializable
data class PickupConfirmation(val confirmedMealCount: Int)

@Serializable
data class HandoverConfirmation(
    val orderIds: List<String>,
    val otp: String?,
)

@Serializable
data class DeliveryIssueReport(
    val orderIds: List<String>,
    val reason: String,
    val partial: Boolean,
)
