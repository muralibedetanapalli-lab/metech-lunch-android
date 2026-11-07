package com.metech.lunch.core.ui

import com.metech.lunch.core.model.OrderStatus
import java.text.NumberFormat
import java.util.Locale

private val rupees = NumberFormat.getNumberInstance(Locale("en", "IN"))

/** ₹120, ₹1,250 or ₹99.50: no ".00" on whole amounts, Indian digit grouping. */
fun formatPaise(paise: Long): String {
    val whole = rupees.format(paise / 100)
    val rest = paise % 100
    return if (rest == 0L) "₹$whole" else "₹$whole.%02d".format(rest)
}

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

fun OrderStatus.tone(): Tone = when (this) {
    OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED -> Tone.Success
    OrderStatus.PENDING_PAYMENT, OrderStatus.CANCEL_REQUESTED, OrderStatus.PARTIALLY_DELIVERED -> Tone.Warning
    OrderStatus.PAYMENT_FAILED, OrderStatus.DELIVERY_FAILED -> Tone.Danger
    OrderStatus.CANCELLED -> Tone.Neutral
}

/** Still moving: worth a pulsing dot. */
val OrderStatus.isLive: Boolean
    get() = this == OrderStatus.PENDING_PAYMENT || this == OrderStatus.PREPARING || this == OrderStatus.OUT_FOR_DELIVERY

fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
