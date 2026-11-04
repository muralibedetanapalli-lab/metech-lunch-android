package com.metech.lunch.customer.ordering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.ui.formatPaise
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun MenuScreen(
    state: OrderingState,
    onChangeQuantity: (MenuItem, Int) -> Unit,
    onChangeOffice: () -> Unit,
    onReview: () -> Unit,
    onRetry: () -> Unit,
) {
    val menu = state.menu
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Delivering to", style = MaterialTheme.typography.labelMedium)
                    Text(
                        listOfNotNull(state.office?.name, state.dropPointName).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = onChangeOffice) { Text("Change") }
            }
            if (menu != null) CutoffBanner(menu.orderingOpen, menu.cutoffAt, hasItems = menu.items.isNotEmpty())
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && menu == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                menu == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Could not load the menu")
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                menu.items.isEmpty() -> Text("No menu has been published for today.", Modifier.align(Alignment.Center).padding(24.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(menu.items, key = { it.id }) { item ->
                        MenuItemCard(item, state.cart[item.id] ?: 0, menu.orderingOpen, onChangeQuantity)
                    }
                }
            }
        }

        if (state.cartCount > 0) {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.cartCount} meal${if (state.cartCount > 1) "s" else ""}")
                        Text(formatPaise(state.cartTotalPaise), fontWeight = FontWeight.SemiBold)
                    }
                    Button(onClick = onReview, enabled = menu?.orderingOpen == true) { Text("Review order") }
                }
            }
        }
    }
}

@Composable
private fun CutoffBanner(open: Boolean, cutoffAt: String, hasItems: Boolean) {
    val time = runCatching {
        OffsetDateTime.parse(cutoffAt).atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }.getOrDefault("10:00 AM")
    val (text, color) = when {
        open -> "Order before $time for today's delivery" to MaterialTheme.colorScheme.primary
        hasItems -> "Ordering for today closed at $time" to MaterialTheme.colorScheme.error
        else -> "Ordering is closed today" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun MenuItemCard(item: MenuItem, quantity: Int, orderingOpen: Boolean, onChangeQuantity: (MenuItem, Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                val tags = (item.dietaryTags + item.allergens.map { "contains $it" }).joinToString(" · ")
                if (tags.isNotBlank()) Text(tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(formatPaise(item.pricePaise), fontWeight = FontWeight.SemiBold)
                when {
                    !item.available -> Text("Sold out", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    item.remainingCapacity != null && item.remainingCapacity!! <= 10 ->
                        Text("Only ${item.remainingCapacity} left", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (item.available && orderingOpen) {
                QuantityStepper(quantity, onMinus = { onChangeQuantity(item, -1) }, onPlus = { onChangeQuantity(item, 1) })
            }
        }
    }
}

@Composable
fun QuantityStepper(quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (quantity > 0) {
            FilledTonalIconButton(onClick = onMinus) { Text("−") }
            Text("$quantity", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalIconButton(onClick = onPlus) { Text("+") }
    }
}
