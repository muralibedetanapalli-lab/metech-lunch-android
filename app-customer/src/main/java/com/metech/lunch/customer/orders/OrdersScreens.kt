package com.metech.lunch.customer.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.ui.formatPaise
import com.metech.lunch.core.ui.label

@Composable
fun OrdersScreen(state: OrdersState, onOpen: (String) -> Unit, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading && state.orders.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.error != null && state.orders.isEmpty() -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error)
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            state.orders.isEmpty() -> Text("No orders yet.", Modifier.align(Alignment.Center))
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.orders, key = { it.id }) { order ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(order.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row {
                                Text(order.deliveryDate, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                                StatusText(order.status)
                            }
                            Text(order.lines.joinToString { "${it.quantity} × ${it.name}" }, style = MaterialTheme.typography.bodyMedium)
                            Text(formatPaise(order.totalPaise), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderDetailScreen(state: OrderDetailState, onCancel: () -> Unit, onBackToMenu: () -> Unit) {
    val order = state.order
    if (order == null) {
        Box(Modifier.fillMaxSize()) {
            if (state.error != null) Text(state.error, Modifier.align(Alignment.Center).padding(24.dp))
            else CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Order for ${order.deliveryDate}", style = MaterialTheme.typography.headlineSmall)
        StatusText(order.status, big = true)
        Text(statusHelp(order), style = MaterialTheme.typography.bodyMedium)

        if (order.deliveryOtp != null && order.status in OTP_VISIBLE) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Handover code", style = MaterialTheme.typography.labelLarge)
                    Text(order.deliveryOtp!!, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("Share this with the delivery partner when you collect your meal.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()
        order.lines.forEach { line ->
            Row {
                Text("${line.quantity} × ${line.name}", Modifier.weight(1f))
                Text(formatPaise(line.unitPricePaise * line.quantity))
            }
        }
        Row {
            Text("Total", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(formatPaise(order.totalPaise), fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
        order.payment.reference?.let { Text("Payment reference: $it", style = MaterialTheme.typography.bodySmall) }
        order.payment.refundStatus?.let { Text("Refund: ${refundLabel(it)}", style = MaterialTheme.typography.bodySmall) }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (order.status == OrderStatus.CONFIRMED || order.status == OrderStatus.PENDING_PAYMENT) {
            OutlinedButton(onClick = onCancel, enabled = !state.cancelling, modifier = Modifier.fillMaxWidth()) {
                Text(if (order.status == OrderStatus.CONFIRMED) "Cancel and request refund" else "Cancel order")
            }
        }
        TextButton(onClick = onBackToMenu, modifier = Modifier.fillMaxWidth()) { Text("Back to menu") }
    }
}

private val OTP_VISIBLE = setOf(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY)

private fun statusHelp(order: Order): String = when (order.status) {
    OrderStatus.PENDING_PAYMENT -> "Waiting for the payment to be verified. This updates automatically."
    OrderStatus.PAYMENT_FAILED -> "The payment did not go through. Nothing was charged; you can order again."
    OrderStatus.CONFIRMED -> "Your meal is confirmed."
    OrderStatus.PREPARING -> "The kitchen is preparing your meal."
    OrderStatus.OUT_FOR_DELIVERY -> "Your meal is on its way."
    OrderStatus.DELIVERED -> "Delivered. Enjoy your lunch!"
    OrderStatus.PARTIALLY_DELIVERED -> "Part of this order was delivered. Support will follow up."
    OrderStatus.DELIVERY_FAILED -> "We could not deliver this order. Support will follow up."
    OrderStatus.CANCEL_REQUESTED -> "Cancellation requested. Your refund will be processed after approval."
    OrderStatus.CANCELLED -> if (order.payment.refundStatus != null) "Cancelled. A refund has been requested." else "Cancelled."
}

private fun refundLabel(status: RefundStatus) = when (status) {
    RefundStatus.REQUESTED -> "requested"
    RefundStatus.APPROVED -> "approved"
    RefundStatus.REJECTED -> "rejected"
    RefundStatus.COMPLETED -> "completed"
}

@Composable
private fun StatusText(status: OrderStatus, big: Boolean = false) {
    val color = when (status) {
        OrderStatus.PAYMENT_FAILED, OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error
        OrderStatus.PENDING_PAYMENT, OrderStatus.CANCEL_REQUESTED -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        status.label(),
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
    )
}
