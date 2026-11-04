package com.metech.lunch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val kitchenId: String,
    val customerId: String,
    val officeId: String,
    val dropPointId: String?,
    val deliveryDate: String,
    val lines: List<OrderLine>,
    val totalPaise: Long,
    val status: OrderStatus,
    val payment: PaymentInfo,
    val createdAt: String,
)

@Serializable
data class OrderLine(
    val menuItemId: String,
    val name: String,
    val quantity: Int,
    val unitPricePaise: Long,
)

@Serializable
enum class OrderStatus {
    @SerialName("pending_payment") PENDING_PAYMENT,
    @SerialName("confirmed") CONFIRMED,
    @SerialName("preparing") PREPARING,
    @SerialName("out_for_delivery") OUT_FOR_DELIVERY,
    @SerialName("delivered") DELIVERED,
    @SerialName("partially_delivered") PARTIALLY_DELIVERED,
    @SerialName("delivery_failed") DELIVERY_FAILED,
    @SerialName("cancel_requested") CANCEL_REQUESTED,
    @SerialName("cancelled") CANCELLED,
    @SerialName("payment_failed") PAYMENT_FAILED,
}

/** Payment state as verified by the backend — never inferred from the gateway redirect. */
@Serializable
data class PaymentInfo(
    val status: PaymentStatus,
    val reference: String? = null,
    val refundStatus: RefundStatus? = null,
)

@Serializable
enum class PaymentStatus {
    @SerialName("pending") PENDING,
    @SerialName("verified") VERIFIED,
    @SerialName("failed") FAILED,
    @SerialName("refunded") REFUNDED,
}

@Serializable
enum class RefundStatus {
    @SerialName("requested") REQUESTED,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("completed") COMPLETED,
}

/** Request body for placing an order. The server validates cutoff, capacity and eligibility. */
@Serializable
data class PlaceOrderRequest(
    val officeId: String,
    val dropPointId: String?,
    val lines: List<PlaceOrderLine>,
    val idempotencyKey: String,
)

@Serializable
data class PlaceOrderLine(val menuItemId: String, val quantity: Int)

/** Server response: the created order and where to send the user to pay. */
@Serializable
data class PlaceOrderResponse(
    val order: Order,
    val paymentUrl: String?,
)
