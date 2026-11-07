package com.metech.lunch.delivery.batches

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.ui.EmptyState
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.Palette
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.core.ui.StatusPill
import com.metech.lunch.core.ui.Tone
import com.metech.lunch.core.ui.greeting
import com.metech.lunch.core.ui.shimmer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchesScreen(state: BatchesState, onOpen: (String) -> Unit, onRefresh: () -> Unit, onSignOut: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val list = if (tab == 0) state.open else state.done

    PullToRefreshBox(isRefreshing = state.loading && state.batches.isNotEmpty(), onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") { Hero(state, onSignOut = { confirmSignOut = true }) }
            item(key = "tabs") {
                Segmented(
                    options = listOf("To deliver · ${state.open.size}", "History · ${state.done.size}"),
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            when {
                state.loading && state.batches.isEmpty() -> items(3, key = { "s$it" }) {
                    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(150.dp).shimmer(MaterialTheme.shapes.large))
                }
                state.error != null && state.batches.isEmpty() -> item(key = "err") {
                    EmptyState("📡", "Couldn't load deliveries", state.error) { PrimaryButton("Try again", onClick = onRefresh) }
                }
                list.isEmpty() -> item(key = "empty") {
                    if (tab == 0) EmptyState("🛵", "All clear!", "No deliveries assigned right now. Pull down to check again.")
                    else EmptyState("📦", "No history yet", "Finished deliveries will show up here.")
                }
                else -> items(list, key = { it.id }) { batch ->
                    BatchCard(batch, onClick = { onOpen(batch.id) }, modifier = Modifier.padding(horizontal = 16.dp).animateItem())
                }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need a new code to sign back in.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out", color = LunchTheme.extras.danger) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun Hero(state: BatchesState, onSignOut: () -> Unit) {
    val x = LunchTheme.extras
    val open = state.open
    val mealsToGo = open.sumOf { b -> b.orders.filter { it.status == OrderStatus.OUT_FOR_DELIVERY || b.status == BatchStatus.ASSIGNED }.sumOf { it.mealCount } }
    val deliveredToday = state.batches.filter { it.deliveryDate == LocalDate.now().toString() }
        .sumOf { b -> b.orders.filter { it.status == OrderStatus.DELIVERED }.sumOf { it.mealCount } }
    val first = state.partnerName.trim().substringBefore(' ').substringBefore('(').trim()

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(x.heroStart, x.heroEnd)))
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(LocalTime.now().hour) + if (first.isNotEmpty()) "," else "", color = Palette.Cream.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
                Text(if (first.isNotEmpty()) "$first 🛵" else "Your deliveries", color = Palette.Cream, style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", tint = Palette.Cream) }
        }
        Text(
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            color = Palette.Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 12.dp)) {
            Stat("${open.size}", if (open.size == 1) "batch to go" else "batches to go", Modifier.weight(1f))
            Stat("$mealsToGo", if (mealsToGo == 1) "meal to deliver" else "meals to deliver", Modifier.weight(1f))
            Stat("$deliveredToday", if (deliveredToday == 1) "meal delivered" else "meals delivered", Modifier.weight(1f), accent = true)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier, accent: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (accent) LunchTheme.extras.saffron else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stat") { v ->
            Text(v, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BatchCard(batch: DeliveryBatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val delivered = batch.orders.count { it.status == OrderStatus.DELIVERED }
    val total = batch.orders.size
    val progress by animateFloatAsState(if (total == 0) 0f else delivered / total.toFloat(), tween(600), label = "p")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (batch.isOpen) 3.dp else 0.dp,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(LunchTheme.extras.saffronSoft),
                    contentAlignment = Alignment.Center,
                ) { Text(batchEmoji(batch.status), style = MaterialTheme.typography.headlineSmall) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(batch.office.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(batch.dropPoint?.name, prettyDate(batch.deliveryDate)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${batch.totalMeals}", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(6.dp))
                Text(
                    "meal${if (batch.totalMeals == 1) "" else "s"} · $total order${if (total == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp).weight(1f),
                )
                BatchStatusPill(batch.status)
            }
            if (batch.status != BatchStatus.ASSIGNED && batch.status != BatchStatus.UNASSIGNED) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(LunchTheme.extras.success))
                }
                Spacer(Modifier.height(6.dp))
                Text("$delivered of $total handed over", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BatchStatusPill(status: BatchStatus) {
    val (text, tone) = when (status) {
        BatchStatus.UNASSIGNED, BatchStatus.ASSIGNED -> "Ready for pickup" to Tone.Accent
        BatchStatus.PICKED_UP, BatchStatus.IN_TRANSIT -> "On the way" to Tone.Success
        BatchStatus.DELIVERED -> "Delivered" to Tone.Success
        BatchStatus.PARTIALLY_DELIVERED -> "Partly delivered" to Tone.Warning
        BatchStatus.FAILED -> "Not delivered" to Tone.Danger
    }
    StatusPill(text, tone, pulse = status == BatchStatus.PICKED_UP || status == BatchStatus.IN_TRANSIT)
}

fun batchEmoji(status: BatchStatus) = when (status) {
    BatchStatus.UNASSIGNED, BatchStatus.ASSIGNED -> "🍱"
    BatchStatus.PICKED_UP, BatchStatus.IN_TRANSIT -> "🛵"
    BatchStatus.DELIVERED -> "✅"
    BatchStatus.PARTIALLY_DELIVERED -> "📦"
    BatchStatus.FAILED -> "⚠️"
}

fun prettyDate(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}.getOrDefault(iso)

