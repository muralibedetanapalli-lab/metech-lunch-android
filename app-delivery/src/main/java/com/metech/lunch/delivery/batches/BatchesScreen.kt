package com.metech.lunch.delivery.batches

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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch

@Composable
fun BatchesScreen(state: BatchesState, onOpen: (String) -> Unit, onRefresh: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("To deliver (${state.open.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        val list = if (tab == 0) state.open else state.done
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.batches.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.batches.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error)
                    TextButton(onClick = onRefresh) { Text("Retry") }
                }
                list.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (tab == 0) "No deliveries assigned to you right now." else "No past deliveries yet.")
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list, key = { it.id }) { batch -> BatchCard(batch) { onOpen(batch.id) } }
                }
            }
        }
    }
}

@Composable
private fun BatchCard(batch: DeliveryBatch, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(batch.office.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                BatchStatusText(batch.status)
            }
            batch.dropPoint?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "${batch.totalMeals} meal${if (batch.totalMeals == 1) "" else "s"} · ${batch.orders.size} order${if (batch.orders.size == 1) "" else "s"} · ${batch.deliveryDate}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun BatchStatusText(status: BatchStatus) {
    val (text, color) = when (status) {
        BatchStatus.UNASSIGNED, BatchStatus.ASSIGNED -> "Ready for pickup" to MaterialTheme.colorScheme.primary
        BatchStatus.PICKED_UP, BatchStatus.IN_TRANSIT -> "On the way" to Color(0xFF1565C0)
        BatchStatus.DELIVERED -> "Delivered" to MaterialTheme.colorScheme.primary
        BatchStatus.PARTIALLY_DELIVERED -> "Partly delivered" to Color(0xFFB26A00)
        BatchStatus.FAILED -> "Not delivered" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
}
