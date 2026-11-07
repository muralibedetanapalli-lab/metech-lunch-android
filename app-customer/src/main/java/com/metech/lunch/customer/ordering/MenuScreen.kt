package com.metech.lunch.customer.ordering

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.ui.EmptyState
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.core.ui.PulseDot
import com.metech.lunch.core.ui.VegMark
import com.metech.lunch.core.ui.formatPaise
import com.metech.lunch.core.ui.greeting
import com.metech.lunch.core.ui.pressScale
import com.metech.lunch.core.ui.shimmer
import com.metech.lunch.customer.ui.Avatar
import com.metech.lunch.customer.ui.MealArt
import com.metech.lunch.customer.ui.isVeg
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    state: OrderingState,
    onChangeQuantity: (MenuItem, Int) -> Unit,
    onChangeOffice: () -> Unit,
    onReview: () -> Unit,
    onRetry: () -> Unit,
) {
    val menu = state.menu
    var vegOnly by rememberSaveable { mutableStateOf(false) }
    val items = menu?.items.orEmpty().filter { !vegOnly || it.isVeg() }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.loading && menu != null,
            onRefresh = onRetry,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (state.cartCount > 0) 112.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(state, onChangeOffice)
                }
                if (menu != null) {
                    item(key = "cutoff") { CutoffCard(menu.orderingOpen, menu.cutoffAt, hasItems = menu.items.isNotEmpty()) }
                    if (menu.items.isNotEmpty()) {
                        item(key = "filters") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !vegOnly, onClick = { vegOnly = false },
                                    label = { Text("All meals · ${menu.items.size}") },
                                    shape = CircleShape,
                                    colors = chipColors(),
                                )
                                FilterChip(
                                    selected = vegOnly, onClick = { vegOnly = !vegOnly },
                                    label = { Text("Veg only") },
                                    leadingIcon = { VegMark(true, size = 14.dp) },
                                    shape = CircleShape,
                                    colors = chipColors(),
                                )
                            }
                        }
                    }
                }
                when {
                    state.loading && menu == null -> items(3, key = { "skeleton$it" }) { MealSkeleton() }
                    menu == null -> item(key = "error") {
                        EmptyState("😕", "Couldn't load the menu", state.error ?: "Check your connection and try again.") {
                            PrimaryButton("Try again", onClick = onRetry)
                        }
                    }
                    menu.items.isEmpty() -> item(key = "empty") {
                        EmptyState("🧑‍🍳", "No menu today", "The kitchen hasn't published today's menu yet. Pull down to check again.")
                    }
                    items.isEmpty() -> item(key = "noveg") {
                        EmptyState("🥦", "No veg meals today", "Switch the filter off to see everything on the menu.")
                    }
                    else -> items(items, key = { it.id }) { item ->
                        MealCard(
                            item = item,
                            quantity = state.cart[item.id] ?: 0,
                            orderingOpen = menu.orderingOpen,
                            onChangeQuantity = onChangeQuantity,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        // Keep scrolled content from running under the status bar icons.
        Box(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)),
        )

        CartBar(
            visible = state.cartCount > 0,
            count = state.cartCount,
            totalPaise = state.cartTotalPaise,
            enabled = menu?.orderingOpen == true,
            onClick = onReview,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.surface,
)

@Composable
private fun Header(state: OrderingState, onChangeOffice: () -> Unit) {
    val name = state.profile?.name?.trim()?.substringBefore(' ').orEmpty()
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${greeting(LocalTime.now().hour)}${if (name.isNotEmpty()) "," else ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (name.isNotEmpty()) "$name 👋" else "What's for lunch?",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Avatar(state.profile?.name.orEmpty(), onClick = onChangeOffice)
        }
        Spacer(Modifier.height(12.dp))
        val place = listOfNotNull(state.office?.name, state.dropPointName).joinToString(" · ")
        Row(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onChangeOffice)
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp), tint = LunchTheme.extras.saffron)
            Spacer(Modifier.width(6.dp))
            Text(
                if (place.isNotEmpty()) place else "Choose your office",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Live countdown to the cutoff, with a bar that empties and turns amber in the last half hour. */
@Composable
private fun CutoffCard(open: Boolean, cutoffAt: String, hasItems: Boolean) {
    val cutoff = remember(cutoffAt) { runCatching { OffsetDateTime.parse(cutoffAt) }.getOrNull() }
    val cutoffLabel = cutoff?.atZoneSameInstant(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "10:00 AM"
    val now by produceState(OffsetDateTime.now()) {
        while (true) { delay(1_000); value = OffsetDateTime.now() }
    }
    // The server decides whether ordering is open. The countdown uses this phone's clock,
    // so it is left out when that clock disagrees (cutoff already "passed" while still open).
    val left = cutoff?.let { Duration.between(now, it) }?.takeIf { !it.isNegative }
    val x = LunchTheme.extras
    val isOpen = open
    val urgent = isOpen && left != null && left.toMinutes() < 30

    val bg = when {
        !isOpen -> MaterialTheme.colorScheme.surfaceVariant
        urgent -> x.warningSoft
        else -> x.saffronSoft
    }
    Surface(shape = MaterialTheme.shapes.large, color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) { Text(if (isOpen) "⏰" else "🌙", style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (isOpen && left == null) {
                    Text("Ordering is open", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Order by $cutoffLabel for delivery today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isOpen && left != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (urgent) { PulseDot(x.warning, pulse = true, size = 8.dp); Spacer(Modifier.width(6.dp)) }
                        Text(
                            "${formatLeft(left)} left to order",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (urgent) x.warning else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        "Order by $cutoffLabel for delivery today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // The bar covers the last four hours before the cutoff.
                    val fraction = (left.seconds / (4 * 3600f)).coerceIn(0f, 1f)
                    val animated by animateFloatAsState(fraction, tween(600), label = "left")
                    LinearProgressIndicator(
                        progress = { animated },
                        color = if (urgent) x.warning else x.saffron,
                        trackColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )
                } else {
                    Text(
                        if (hasItems) "Ordering closed at $cutoffLabel" else "No ordering today",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Today's orders are with the kitchen. See you tomorrow!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatLeft(d: Duration): String {
    // Duration's toMinutesPart/toSecondsPart need Android 12; minSdk is 8.0.
    val total = d.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m >= 10 -> "${m}m"
        else -> "%d:%02d".format(m, s)
    }
}

@Composable
private fun MealCard(
    item: MenuItem,
    quantity: Int,
    orderingOpen: Boolean,
    onChangeQuantity: (MenuItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOut = !item.available
    val selected = quantity > 0
    val border by animateFloatAsState(if (selected) 2f else 1f, label = "border")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(border.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 6.dp else 0.dp,
    ) {
        Column {
            Box {
                MealArt(
                    item.name, item.imageUrl,
                    Modifier.fillMaxWidth().height(150.dp).graphicsLayer { alpha = if (soldOut) 0.45f else 1f },
                )
                val left = item.remainingCapacity
                when {
                    soldOut -> Badge("Sold out", Color(0xFF2A2118), Color.White, Modifier.align(Alignment.TopStart))
                    left != null && left <= 10 -> Badge("🔥 Only $left left", LunchTheme.extras.saffron, Color.White, Modifier.align(Alignment.TopStart))
                }
                androidx.compose.animation.AnimatedVisibility(
                    selected,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Text("$quantity", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegMark(item.isVeg())
                    Spacer(Modifier.width(8.dp))
                    Text(item.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.allergens.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.allergens.forEach { a ->
                            Text(
                                "Contains $a",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPaise(item.pricePaise), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (!soldOut && orderingOpen) {
                        AddStepper(quantity, onMinus = { onChangeQuantity(item, -1) }, onPlus = { onChangeQuantity(item, 1) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier) {
    Text(
        text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(12.dp).clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** "Add" pill that turns into − n + once something is in the cart. */
@Composable
fun AddStepper(quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit, compact: Boolean = false) {
    val haptics = LocalHapticFeedback.current
    val height = if (compact) 36.dp else 44.dp
    AnimatedContent(quantity > 0, label = "stepper", transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith fadeOut() }) { inCart ->
        if (!inCart) {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier.height(height).pressScale(interaction).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(interaction, null) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlus() }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Add", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Row(
                Modifier.height(height).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton("−") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMinus() }
                AnimatedContent(
                    quantity,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "qty",
                ) { q ->
                    Text("$q", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                StepButton("+") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlus() }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp).pressScale(interaction, 0.85f).clip(CircleShape).clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun CartBar(visible: Boolean, count: Int, totalPaise: Long, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
        modifier = modifier,
    ) {
        // Bump the badge whenever the count changes.
        var bump by remember { mutableStateOf(false) }
        LaunchedEffect(count) { bump = true; delay(160); bump = false }
        val badgeScale by animateFloatAsState(if (bump) 1.25f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "bump")
        val interaction = remember { MutableInteractionSource() }

        Row(
            Modifier.padding(16.dp).fillMaxWidth().height(64.dp).pressScale(interaction, 0.98f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                .clickable(interaction, null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).scale(badgeScale).clip(CircleShape).background(LunchTheme.extras.saffron),
                contentAlignment = Alignment.Center,
            ) { Text("$count", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (count == 1) "1 meal" else "$count meals", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(formatPaise(totalPaise), color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            Text(if (enabled) "View cart" else "Closed", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun MealSkeleton() {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).shimmer(RoundedCornerShape(0.dp)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(180.dp).height(20.dp).shimmer())
            Box(Modifier.fillMaxWidth().height(14.dp).shimmer())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(70.dp).height(24.dp).shimmer())
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(96.dp).height(44.dp).shimmer(CircleShape))
            }
        }
    }
}
