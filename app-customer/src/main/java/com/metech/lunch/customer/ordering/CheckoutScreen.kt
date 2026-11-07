package com.metech.lunch.customer.ordering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.ui.EmptyState
import com.metech.lunch.core.ui.LunchCard
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.core.ui.VegMark
import com.metech.lunch.core.ui.formatPaise
import com.metech.lunch.customer.ui.MealArt
import com.metech.lunch.customer.ui.ScreenTopBar
import com.metech.lunch.customer.ui.isVeg

@Composable
fun CheckoutScreen(
    state: OrderingState,
    onChangeQuantity: (MenuItem, Int) -> Unit,
    onBack: () -> Unit,
    onChangeOffice: () -> Unit,
    onPay: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Your order", onBack = onBack)
        if (state.cartLines.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState("🛒", "Your cart is empty", "Add a meal from today's menu to get started.") {
                    PrimaryButton("Browse the menu", onClick = onBack)
                }
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LunchCard(Modifier.animateContentSize()) {
                Text("Meals", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                state.cartLines.forEachIndexed { i, (item, qty) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MealArt(item.name, item.imageUrl, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), emojiSize = MaterialTheme.typography.headlineSmall.fontSize)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VegMark(item.isVeg(), size = 12.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(formatPaise(item.pricePaise * qty), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        AddStepper(qty, onMinus = { onChangeQuantity(item, -1) }, onPlus = { onChangeQuantity(item, 1) }, compact = true)
                    }
                }
                TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("+ Add more meals", fontWeight = FontWeight.SemiBold)
                }
            }

            LunchCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(LunchTheme.extras.saffronSoft),
                        contentAlignment = Alignment.Center,
                    ) { Text("📍", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Deliver to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(state.office?.name, state.dropPointName).joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                        state.office?.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    TextButton(onClick = onChangeOffice) { Text("Change") }
                }
            }

            LunchCard {
                Text("Bill", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                BillRow("Meals (${state.cartCount})", formatPaise(state.cartTotalPaise))
                BillRow("Delivery", "Free", accent = true)
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("To pay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(formatPaise(state.cartTotalPaise), style = MaterialTheme.typography.headlineSmall)
                }
            }

            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your meal is confirmed once the payment is verified. You can cancel before the cutoff for a full refund.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                AnimatedVisibility(state.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Text(
                        state.error.orEmpty(),
                        color = LunchTheme.extras.danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp)).background(LunchTheme.extras.dangerSoft).padding(12.dp),
                    )
                }
                PrimaryButton(
                    text = if (state.menu?.orderingOpen == true) "Pay ${formatPaise(state.cartTotalPaise)}" else "Ordering is closed",
                    onClick = onPay,
                    enabled = state.menu?.orderingOpen == true,
                    loading = state.placing,
                    icon = Icons.Default.Lock,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BillRow(label: String, value: String, accent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (accent) LunchTheme.extras.success else MaterialTheme.colorScheme.onSurface,
        )
    }
}
