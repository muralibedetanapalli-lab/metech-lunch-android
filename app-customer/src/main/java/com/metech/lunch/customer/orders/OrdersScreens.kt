package com.metech.lunch.customer.orders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.RefundStatus
import com.metech.lunch.core.ui.ConfettiBurst
import com.metech.lunch.core.ui.EmptyState
import com.metech.lunch.core.ui.LunchCard
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.Palette
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.core.ui.SecondaryButton
import com.metech.lunch.core.ui.StatusPill
import com.metech.lunch.core.ui.formatPaise
import com.metech.lunch.core.ui.isLive
import com.metech.lunch.core.ui.label
import com.metech.lunch.core.ui.shimmer
import com.metech.lunch.core.ui.tone
import com.metech.lunch.customer.ui.ScreenTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(state: OrdersState, onOpen: (String) -> Unit, onRetry: () -> Unit, onBrowse: () -> Unit) {
    val today = LocalDate.now().toString()
    val (current, past) = state.orders.partition { it.deliveryDate >= today }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Your orders",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
        PullToRefreshBox(isRefreshing = state.loading && state.orders.isNotEmpty(), onRefresh = onRetry, modifier = Modifier.weight(1f)) {
            when {
                state.loading && state.orders.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Box(Modifier.fillMaxWidth().height(96.dp).shimmer(MaterialTheme.shapes.large)) }
                }
                state.error != null && state.orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("😕", "Couldn't load your orders", state.error) { PrimaryButton("Try again", onClick = onRetry) }
                }
                state.orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("🥡", "No orders yet", "Your lunch orders will show up here.") {
                        PrimaryButton("Browse today's menu", onClick = onBrowse)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (current.isNotEmpty()) {
                        item(key = "h-current") { SectionLabel("Today & upcoming") }
                        items(current, key = { it.id }) { OrderRow(it, highlight = true, onOpen = onOpen, modifier = Modifier.animateItem()) }
                    }
                    if (past.isNotEmpty()) {
                        item(key = "h-past") { SectionLabel("Earlier") }
                        items(past, key = { it.id }) { OrderRow(it, highlight = false, onOpen = onOpen, modifier = Modifier.animateItem()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun OrderRow(order: Order, highlight: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (highlight) 3.dp else 0.dp,
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onOpen(order.id) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(LunchTheme.extras.saffronSoft),
                contentAlignment = Alignment.Center,
            ) { Text(statusEmoji(order.status), style = MaterialTheme.typography.headlineSmall) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(prettyDate(order.deliveryDate), style = MaterialTheme.typography.titleSmall)
                Text(
                    order.lines.joinToString { "${it.quantity} × ${it.name}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(order.status.label(), order.status.tone(), pulse = order.status.isLive)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPaise(order.totalPaise), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ---------------------------------------------------------------- detail

@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    onPay: (() -> Unit)?,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    val order = state.order
    var confirmCancel by remember { mutableStateOf(false) }
    // Celebrate when this screen sees the payment go through.
    var lastStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var celebrate by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(order?.status) {
        val now = order?.status
        if (lastStatus == OrderStatus.PENDING_PAYMENT && now == OrderStatus.CONFIRMED) celebrate = System.currentTimeMillis()
        lastStatus = now
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("Order details", onBack = onBack)
            if (order == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.error != null) EmptyState("😕", "Couldn't load this order", state.error)
                    else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(180.dp).shimmer(MaterialTheme.shapes.large))
                        Box(Modifier.fillMaxWidth().height(120.dp).shimmer(MaterialTheme.shapes.large))
                    }
                }
                return@Column
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusHero(order)
                if (order.status in TRACKED) Tracker(order.status)
                if (order.status == OrderStatus.OUT_FOR_DELIVERY) LiveDelivery(state.tracking)
                if (order.deliveryOtp != null && order.status in OTP_VISIBLE) HandoverCode(order.deliveryOtp!!)

                LunchCard {
                    Text("Meals", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    order.lines.forEach { line ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("${line.quantity} ×", style = MaterialTheme.typography.bodyMedium, color = LunchTheme.extras.saffron, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Text(line.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(formatPaise(line.unitPricePaise * line.quantity), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (order.status in UNPAID) "Total" else "Total paid", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatPaise(order.totalPaise), style = MaterialTheme.typography.titleLarge)
                    }
                    order.payment.reference?.let { ref ->
                        val clipboard = LocalClipboardManager.current
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Payment ref  $ref", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clipboard.setText(AnnotatedString(ref)) }) { Text("Copy") }
                        }
                    }
                    order.payment.refundStatus?.let {
                        Spacer(Modifier.height(4.dp))
                        StatusPill("Refund ${refundLabel(it)}", if (it == RefundStatus.REJECTED) com.metech.lunch.core.ui.Tone.Danger else com.metech.lunch.core.ui.Tone.Accent)
                    }
                }

                state.error?.let { Text(it, color = LunchTheme.extras.danger, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(4.dp))
            }

            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (order.status == OrderStatus.PENDING_PAYMENT && onPay != null) {
                    PrimaryButton(
                        "Complete payment",
                        onClick = onPay,
                        icon = Icons.Default.Lock,
                        containerColor = LunchTheme.extras.saffron,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (order.status == OrderStatus.CONFIRMED || order.status == OrderStatus.PENDING_PAYMENT) {
                    SecondaryButton(
                        if (order.status == OrderStatus.CONFIRMED) "Cancel & get a refund" else "Cancel order",
                        onClick = { confirmCancel = true },
                        enabled = !state.cancelling,
                        danger = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (order.status == OrderStatus.PENDING_PAYMENT && onPay != null) {
                    TextButton(onClick = onBackToMenu, modifier = Modifier.fillMaxWidth()) { Text("Back to menu") }
                } else {
                    PrimaryButton("Back to menu", onClick = onBackToMenu, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        ConfettiBurst(celebrate, Modifier.fillMaxSize())
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this order?") },
            text = {
                Text(
                    if (order?.status == OrderStatus.CONFIRMED) "Your refund will be processed once the kitchen approves it."
                    else "The payment for this order will be abandoned.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmCancel = false; onCancel() }) { Text("Yes, cancel", color = LunchTheme.extras.danger) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep my order") } },
        )
    }
}

@Composable
private fun StatusHero(order: Order) {
    val x = LunchTheme.extras
    val good = order.status.tone() == com.metech.lunch.core.ui.Tone.Success
    val colors = if (good) listOf(x.heroStart, x.heroEnd) else listOf(Color(0xFF7A6A58), Color(0xFF4E4236))
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(Brush.linearGradient(colors)).padding(24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                order.status,
                transitionSpec = { (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith fadeOut() },
                label = "emoji",
            ) { s ->
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Text(statusEmoji(s), style = MaterialTheme.typography.displaySmall) }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(order.status, label = "title") { s ->
                Text(statusTitle(s), style = MaterialTheme.typography.headlineSmall, color = Palette.Cream)
            }
            Spacer(Modifier.height(4.dp))
            Text(statusHelp(order), style = MaterialTheme.typography.bodyMedium, color = Palette.Cream.copy(alpha = 0.85f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "${prettyDate(order.deliveryDate)} · ${order.lines.sumOf { it.quantity }} meal${if (order.lines.sumOf { it.quantity } == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Cream,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private val STEPS = listOf("Paid", "Cooking", "On the way", "Delivered")
private val TRACKED = setOf(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)

@Composable
private fun Tracker(status: OrderStatus) {
    val current = when (status) {
        OrderStatus.CONFIRMED -> 0
        OrderStatus.PREPARING -> 1
        OrderStatus.OUT_FOR_DELIVERY -> 2
        else -> 3
    }
    LunchCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            STEPS.forEachIndexed { i, _ ->
                val done = i <= current
                val color by animateColorAsState(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, tween(500), label = "c$i",
                )
                val scale by animateFloatAsState(if (i == current) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s$i")
                Box(Modifier.size(28.dp).scale(scale).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                if (i < STEPS.lastIndex) {
                    val fill by animateFloatAsState(if (i < current) 1f else 0f, tween(700), label = "f$i")
                    Box(Modifier.weight(1f).height(4.dp).padding(horizontal = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                        Box(Modifier.fillMaxWidth(fill).height(4.dp).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            STEPS.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (i <= current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (i) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        STEPS.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HandoverCode(code: String) {
    val x = LunchTheme.extras
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(x.saffronSoft, Color(0xFFFFD9B3))))
            .padding(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("🔐 Handover code", style = MaterialTheme.typography.titleSmall, color = Color(0xFF5A2E06))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                code.forEach { c ->
                    Box(
                        Modifier.size(width = 54.dp, height = 64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.toString(), style = MaterialTheme.typography.headlineLarge, color = Color(0xFF2A2118)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Show this to the delivery partner when you collect your meal.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFF5A2E06),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private val UNPAID = setOf(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED)
private val OTP_VISIBLE = setOf(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY)

private fun statusEmoji(s: OrderStatus) = when (s) {
    OrderStatus.PENDING_PAYMENT -> "⏳"
    OrderStatus.CONFIRMED -> "✅"
    OrderStatus.PREPARING -> "👩‍🍳"
    OrderStatus.OUT_FOR_DELIVERY -> "🛵"
    OrderStatus.DELIVERED -> "🎉"
    OrderStatus.PARTIALLY_DELIVERED -> "📦"
    OrderStatus.DELIVERY_FAILED -> "⚠️"
    OrderStatus.CANCEL_REQUESTED -> "↩️"
    OrderStatus.CANCELLED -> "✖️"
    OrderStatus.PAYMENT_FAILED -> "💳"
}

private fun statusTitle(s: OrderStatus) = when (s) {
    OrderStatus.CONFIRMED -> "Lunch is booked!"
    OrderStatus.PREPARING -> "The kitchen is cooking"
    OrderStatus.OUT_FOR_DELIVERY -> "On its way to you"
    OrderStatus.DELIVERED -> "Enjoy your lunch!"
    else -> s.label()
}

private fun statusHelp(order: Order): String = when (order.status) {
    OrderStatus.PENDING_PAYMENT -> "Waiting for the payment to be verified. This updates by itself."
    OrderStatus.PAYMENT_FAILED -> "The payment didn't go through and nothing was charged. You can order again."
    OrderStatus.CONFIRMED -> "Payment received. We'll start cooking after the cutoff."
    OrderStatus.PREPARING -> "Your meal is being freshly prepared."
    OrderStatus.OUT_FOR_DELIVERY -> "Keep your handover code ready."
    OrderStatus.DELIVERED -> "Delivered. Thanks for ordering!"
    OrderStatus.PARTIALLY_DELIVERED -> "Part of this order was delivered. Support will follow up."
    OrderStatus.DELIVERY_FAILED -> "We couldn't deliver this order. Support will follow up."
    OrderStatus.CANCEL_REQUESTED -> "Cancellation requested. Your refund follows once it's approved."
    OrderStatus.CANCELLED -> if (order.payment.refundStatus != null) "Cancelled. A refund has been requested." else "This order was cancelled."
}

private fun refundLabel(status: RefundStatus) = when (status) {
    RefundStatus.REQUESTED -> "requested"
    RefundStatus.APPROVED -> "approved"
    RefundStatus.REJECTED -> "rejected"
    RefundStatus.COMPLETED -> "completed"
}

private fun prettyDate(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    val today = LocalDate.now()
    when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}.getOrDefault(iso)

/** The rider on a map while the meals are on the way, with how fresh the position is. */
@Composable
private fun LiveDelivery(tracking: com.metech.lunch.core.model.CustomerTracking?) {
    val name = tracking?.partnerFirstName ?: "Your delivery partner"
    val location = tracking?.location
    LunchCard(padding = PaddingValues(0.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.metech.lunch.core.ui.PulseDot(LunchTheme.extras.saffron, pulse = true, size = 10.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$name is on the way", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (location != null) "Live location · updated ${secondsAgo(location.recordedAt)}"
                    else "Waiting for $name's location…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("🛵", style = MaterialTheme.typography.headlineSmall)
        }
        if (location != null) {
            RiderMap(
                location = location,
                trail = tracking.trail,
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(120.dp).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(MaterialTheme.shapes.medium).shimmer(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) { Text("📡", style = MaterialTheme.typography.headlineMedium) }
        }
    }
}

/** "just now", "25 s ago", "3 min ago": re-read on every poll, which is often enough. */
private fun secondsAgo(iso: String): String {
    val seconds = runCatching { java.time.Duration.between(java.time.OffsetDateTime.parse(iso), java.time.OffsetDateTime.now()).seconds }
        .getOrDefault(0L).coerceAtLeast(0)
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds s ago"
        seconds < 3600 -> "${seconds / 60} min ago"
        else -> "${seconds / 3600} h ago"
    }
}
