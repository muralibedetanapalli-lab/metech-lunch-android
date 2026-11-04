package com.metech.lunch.delivery.batches

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.BatchOrder
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.ui.label

private enum class Dialog { PICKUP, HANDOVER, ISSUE }

@Composable
fun BatchDetailScreen(
    state: BatchDetailState,
    onConfirmPickup: (count: Int, onDone: () -> Unit) -> Unit,
    onHandOver: (orderIds: List<String>, otp: String, onDone: () -> Unit) -> Unit,
    onReportIssue: (orderIds: List<String>, reason: String, partial: Boolean, onDone: () -> Unit) -> Unit,
    onRetry: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    val batch = state.batch
    if (batch == null) {
        Box(Modifier.fillMaxSize()) {
            if (state.error != null) {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        return
    }

    val context = LocalContext.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val open: (Dialog) -> Unit = {
        onDismissMessages() // start each dialog without a stale error
        dialog = it
    }
    // Orders the partner has ticked for handover or an issue report.
    var selected by remember(batch.id) { mutableStateOf(setOf<String>()) }
    val deliverable = batch.orders.filter { it.status == OrderStatus.OUT_FOR_DELIVERY }
    LaunchedEffect(batch) { selected = selected.filter { id -> deliverable.any { it.orderId == id } }.toSet() }
    val inTransit = batch.status != BatchStatus.ASSIGNED && batch.status != BatchStatus.UNASSIGNED && deliverable.isNotEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(batch.office.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            BatchStatusText(batch.status)
        }

        state.message?.let { Notice(it, ok = true, onDismiss = onDismissMessages) }
        if (dialog == null) state.error?.let { Notice(it, ok = false, onDismiss = onDismissMessages) }

        // Where to go.
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (batch.status == BatchStatus.ASSIGNED) {
                    Text("Pick up from", style = MaterialTheme.typography.labelMedium)
                    Text(batch.pickupLocation)
                    OutlinedButton(onClick = { openMaps(context, batch.pickupLocation) }) { Text("Navigate to kitchen") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                }
                Text("Deliver to", style = MaterialTheme.typography.labelMedium)
                Text(batch.office.address)
                batch.dropPoint?.let { dp ->
                    Text("Drop point: ${dp.name}", fontWeight = FontWeight.SemiBold)
                    dp.instructions?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openMaps(context, "${batch.office.name}, ${batch.office.address}") }) { Text("Navigate") }
                    batch.contact?.let { c -> OutlinedButton(onClick = { dial(context, c.phone) }) { Text("Call ${c.name}") } }
                }
            }
        }

        // What to do now.
        when {
            batch.status == BatchStatus.ASSIGNED -> {
                Text("Collect ${meals(batch.totalMeals)} from the kitchen, count them, then confirm.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { open(Dialog.PICKUP) }, modifier = Modifier.fillMaxWidth()) { Text("Confirm pickup") }
            }
            inTransit -> {
                Text(
                    "Tick the orders you are handing over, then enter the customer's 4-digit code (one order) " +
                        "or the office coordinator's code (any number of orders).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selected = deliverable.map { it.orderId }.toSet() }) { Text("Select all") }
                    if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                }
            }
        }

        Text("Orders", style = MaterialTheme.typography.titleMedium)
        batch.orders.forEach { order ->
            OrderRow(
                order = order,
                selectable = inTransit && order.status == OrderStatus.OUT_FOR_DELIVERY,
                checked = order.orderId in selected,
                onToggle = { selected = if (order.orderId in selected) selected - order.orderId else selected + order.orderId },
            )
        }

        if (inTransit) {
            Button(onClick = { open(Dialog.HANDOVER) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (selected.isEmpty()) "Hand over" else "Hand over ${selected.size} order${if (selected.size == 1) "" else "s"}")
            }
            OutlinedButton(onClick = { open(Dialog.ISSUE) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Report a problem with selected")
            }
        }
    }

    val close = { dialog = null }
    when (dialog) {
        Dialog.PICKUP -> PickupDialog(batch, state, onDismiss = close) { count -> onConfirmPickup(count, close) }
        Dialog.HANDOVER -> HandoverDialog(selected.size, state, onDismiss = close) { otp ->
            onHandOver(selected.toList(), otp) { close(); selected = emptySet() }
        }
        Dialog.ISSUE -> IssueDialog(selected.size, state, onDismiss = close) { reason, partial ->
            onReportIssue(selected.toList(), reason, partial) { close(); selected = emptySet() }
        }
        null -> Unit
    }
}

@Composable
private fun OrderRow(order: BatchOrder, selectable: Boolean, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (selectable) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(order.customerName, style = MaterialTheme.typography.bodyLarge)
            Text("${order.mealCount} meal${if (order.mealCount == 1) "" else "s"} · #${order.orderId.take(8)}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            order.status.label(),
            style = MaterialTheme.typography.labelMedium,
            color = when (order.status) {
                OrderStatus.DELIVERED -> MaterialTheme.colorScheme.primary
                OrderStatus.DELIVERY_FAILED, OrderStatus.CANCELLED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun PickupDialog(batch: DeliveryBatch, state: BatchDetailState, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var count by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm pickup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This batch should have ${meals(batch.totalMeals)}. Enter how many you counted.")
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it.filter(Char::isDigit).take(4) },
                    label = { Text("Meals collected") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { count.toIntOrNull()?.let(onConfirm) }, enabled = count.isNotEmpty() && !state.busy) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HandoverDialog(orderCount: Int, state: BatchDetailState, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var otp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hand over $orderCount order${if (orderCount == 1) "" else "s"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (orderCount == 1) "Ask the customer (or office coordinator) for their 4-digit code."
                    else "Ask the office coordinator for the batch code.",
                )
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                    label = { Text("Handover code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(otp) }, enabled = otp.length >= 4 && !state.busy) { Text("Confirm handover") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IssueDialog(orderCount: Int, state: BatchDetailState, onDismiss: () -> Unit, onConfirm: (String, Boolean) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf(false) }
    val reasons = listOf("Customer not available", "Office closed / no access", "Meal damaged", "Wrong or missing meal")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a problem ($orderCount order${if (orderCount == 1) "" else "s"})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Text(r)
                    }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it.take(300) }, label = { Text("Reason") })
                Row(Modifier.clickable { partial = !partial }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = partial, onCheckedChange = { partial = it })
                    Text("Part of the order was handed over")
                }
                Text("The kitchen is notified and will follow up.", style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(reason, partial) }, enabled = reason.isNotBlank() && !state.busy) { Text("Report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Notice(text: String, ok: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss),
    ) { Text(text, Modifier.padding(12.dp)) }
}

/** Opens the address in whichever maps / navigation app the partner uses. */
private fun openMaps(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(address))))
    }
}

private fun dial(context: Context, phone: String) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
}

private fun meals(n: Int) = "$n meal${if (n == 1) "" else "s"}"
