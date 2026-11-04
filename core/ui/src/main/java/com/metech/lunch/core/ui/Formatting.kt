package com.metech.lunch.core.ui

import com.metech.lunch.core.model.OrderStatus

fun formatPaise(paise: Long): String = "₹%d.%02d".format(paise / 100, paise % 100)

fun OrderStatus.label(): String = when (this) {
    OrderStatus.PENDING_PAYMENT -> "Awaiting payment"
    OrderStatus.CONFIRMED -> "Confirmed"
    OrderStatus.PREPARING -> "Preparing"
    OrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
    OrderStatus.DELIVERED -> "Delivered"
    OrderStatus.PARTIALLY_DELIVERED -> "Partially delivered"
    OrderStatus.DELIVERY_FAILED -> "Delivery failed"
    OrderStatus.CANCEL_REQUESTED -> "Cancellation requested"
    OrderStatus.CANCELLED -> "Cancelled"
    OrderStatus.PAYMENT_FAILED -> "Payment failed"
}
