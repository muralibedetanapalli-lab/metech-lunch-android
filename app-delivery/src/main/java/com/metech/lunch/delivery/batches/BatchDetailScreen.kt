package com.metech.lunch.delivery.batches

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.model.BatchOrder
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.ui.CodeInput
import com.metech.lunch.core.ui.ConfettiBurst
import com.metech.lunch.core.ui.EmptyState
import com.metech.lunch.core.ui.LunchCard
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.Palette
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.core.ui.SecondaryButton
import com.metech.lunch.core.ui.StatusPill
import com.metech.lunch.core.ui.label
import com.metech.lunch.core.ui.pressScale
import com.metech.lunch.core.ui.shimmer
import com.metech.lunch.core.ui.tone
import com.metech.lunch.delivery.tracking.LocationSharingService

private enum class Sheet { PICKUP, HANDOVER, ISSUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDetailScreen(
    state: BatchDetailState,
    onBack: () -> Unit,
    onConfirmPickup: (count: Int, onDone: () -> Unit) -> Unit,
    onHandOver: (orderIds: List<String>, otp: String, onDone: () -> Unit) -> Unit,
    onReportIssue: (orderIds: List<String>, reason: String, partial: Boolean, onDone: () -> Unit) -> Unit,
    onRetry: () -> Unit,
    onDismissMessages: () -> Unit,
) {
    val batch = state.batch
    Column(Modifier.fillMaxSize()) {
        TopBar(batch?.office?.name ?: "Delivery", onBack)
        if (batch == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    EmptyState("📡", "Couldn't load this batch", state.error) { PrimaryButton("Try again", onClick = onRetry) }
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(170.dp).shimmer(MaterialTheme.shapes.large))
                        Box(Modifier.fillMaxWidth().height(140.dp).shimmer(MaterialTheme.shapes.large))
                    }
                }
            }
            return@Column
        }
        Content(batch, state, onConfirmPickup, onHandOver, onReportIssue, onDismissMessages)
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Content(
    batch: DeliveryBatch,
    state: BatchDetailState,
    onConfirmPickup: (count: Int, onDone: () -> Unit) -> Unit,
    onHandOver: (orderIds: List<String>, otp: String, onDone: () -> Unit) -> Unit,
    onReportIssue: (orderIds: List<String>, reason: String, partial: Boolean, onDone: () -> Unit) -> Unit,
    onDismissMessages: () -> Unit,
) {
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val open: (Sheet) -> Unit = { onDismissMessages(); sheet = it }
    var selected by remember(batch.id) { mutableStateOf(setOf<String>()) }
    val deliverable = batch.orders.filter { it.status == OrderStatus.OUT_FOR_DELIVERY }
    LaunchedEffect(batch) { selected = selected.filter { id -> deliverable.any { it.orderId == id } }.toSet() }
    val pickupStage = batch.status == BatchStatus.ASSIGNED || batch.status == BatchStatus.UNASSIGNED
    val inTransit = !pickupStage && deliverable.isNotEmpty()
    val onRoad = batch.status == BatchStatus.PICKED_UP || batch.status == BatchStatus.IN_TRANSIT

    // Live tracking: ask for location at pickup; share while the meals are on the road.
    var hasLocation by remember { mutableStateOf(LocationSharingService.hasLocationPermission(context)) }
    var openPickupAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = LocationSharingService.hasLocationPermission(context)
        if (openPickupAfterPermission) { openPickupAfterPermission = false; open(Sheet.PICKUP) }
    }
    val askForLocation = {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(perms.toTypedArray())
    }
    LaunchedEffect(onRoad, hasLocation) { if (onRoad && hasLocation) LocationSharingService.start(context) }

    // Celebrate the moment the last order is handed over.
    var lastStatus by remember { mutableStateOf<BatchStatus?>(null) }
    var celebrate by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(batch.status) {
        if (lastStatus != null && lastStatus != BatchStatus.DELIVERED && batch.status == BatchStatus.DELIVERED) celebrate = System.currentTimeMillis()
        lastStatus = batch.status
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(batch, sharing = onRoad && hasLocation)

            AnimatedVisibility(onRoad && !hasLocation, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(LunchTheme.extras.warningSoft).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "📍  Turn on location so the kitchen can follow this delivery on the map.",
                        color = LunchTheme.extras.warning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = askForLocation) { Text("Turn on", fontWeight = FontWeight.Bold) }
                }
            }

            AnimatedVisibility(state.message != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Banner("🎉  ${state.message.orEmpty()}", LunchTheme.extras.successSoft, LunchTheme.extras.success, onDismissMessages)
            }
            AnimatedVisibility(sheet == null && state.error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Banner("⚠  ${state.error.orEmpty()}", LunchTheme.extras.dangerSoft, LunchTheme.extras.danger, onDismissMessages)
            }

            // Route
            LunchCard {
                Text("Route", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Stop(
                    emoji = "🍳", title = "Kitchen", subtitle = batch.pickupLocation,
                    done = !pickupStage, active = pickupStage, showLine = true,
                    actions = { if (pickupStage) RoundAction(Icons.Default.Place, "Navigate") { openMaps(context, batch.pickupLocation) } },
                )
                Stop(
                    emoji = "🏢", title = batch.office.name,
                    subtitle = listOfNotNull(batch.office.address, batch.dropPoint?.let { "Drop at ${it.name}" }, batch.dropPoint?.instructions).joinToString("\n"),
                    done = batch.status == BatchStatus.DELIVERED, active = !pickupStage && batch.status != BatchStatus.DELIVERED, showLine = false,
                    actions = {
                        RoundAction(Icons.Default.Place, "Navigate") { openMaps(context, "${batch.office.name}, ${batch.office.address}") }
                        batch.contact?.let { c ->
                            Spacer(Modifier.width(8.dp))
                            RoundAction(Icons.Default.Call, "Call ${c.name}") { dial(context, c.phone) }
                        }
                    },
                )
                batch.contact?.let { c ->
                    Text("Contact: ${c.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 56.dp))
                }
            }

            // Orders
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Orders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (inTransit) {
                    val all = selected.size == deliverable.size
                    TextButton(onClick = { selected = if (all) emptySet() else deliverable.map { it.orderId }.toSet() }) {
                        Text(if (all) "Clear" else "Select all", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (inTransit) {
                Text(
                    "Tap the orders you're handing over, then enter the customer's or coordinator's 4-digit code.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            batch.orders.forEach { order ->
                OrderCard(
                    order = order,
                    selectable = inTransit && order.status == OrderStatus.OUT_FOR_DELIVERY,
                    checked = order.orderId in selected,
                    onToggle = { selected = if (order.orderId in selected) selected - order.orderId else selected + order.orderId },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        ConfettiBurst(celebrate, Modifier.fillMaxSize())
    }

    // Sticky actions
    if (pickupStage || inTransit) {
        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                if (pickupStage) {
                    PrimaryButton(
                        "Confirm pickup · ${meals(batch.totalMeals)}",
                        onClick = {
                            if (hasLocation) open(Sheet.PICKUP) else { openPickupAfterPermission = true; askForLocation() }
                        },
                        icon = Icons.Default.Check,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Problem", onClick = { open(Sheet.ISSUE) }, enabled = selected.isNotEmpty(), danger = true, modifier = Modifier.weight(0.4f))
                        PrimaryButton(
                            if (selected.isEmpty()) "Select orders" else "Hand over ${selected.size}",
                            onClick = { open(Sheet.HANDOVER) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = { sheet = null }
    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = close, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                when (sheet) {
                    Sheet.PICKUP -> PickupSheet(batch, state) { count -> onConfirmPickup(count, close) }
                    Sheet.HANDOVER -> HandoverSheet(selected.size, state) { otp ->
                        onHandOver(selected.toList(), otp) { close(); selected = emptySet() }
                    }
                    Sheet.ISSUE -> IssueSheet(selected.size, state) { reason, partial ->
                        onReportIssue(selected.toList(), reason, partial) { close(); selected = emptySet() }
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Hero(batch: DeliveryBatch, sharing: Boolean) {
    val x = LunchTheme.extras
    val step = when (batch.status) {
        BatchStatus.UNASSIGNED, BatchStatus.ASSIGNED -> 0
        BatchStatus.PICKED_UP, BatchStatus.IN_TRANSIT -> 1
        else -> 2
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(x.heroStart, x.heroEnd))).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                AnimatedContent(batchEmoji(batch.status), label = "e") { Text(it, style = MaterialTheme.typography.headlineMedium) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${batch.totalMeals} meal${if (batch.totalMeals == 1) "" else "s"}",
                    style = MaterialTheme.typography.headlineMedium, color = Palette.Cream,
                )
                Text(
                    "${batch.orders.size} order${if (batch.orders.size == 1) "" else "s"} · ${prettyDate(batch.deliveryDate)}",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Cream.copy(alpha = 0.8f),
                )
            }
        }
        if (sharing) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.metech.lunch.core.ui.PulseDot(x.saffron, pulse = true, size = 8.dp)
                Spacer(Modifier.width(8.dp))
                Text("Sharing live location with the kitchen", color = Palette.Cream, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Pick up", "Deliver", "Done").forEachIndexed { i, label ->
                val reached = i <= step
                val bg by animateColorAsState(if (reached) x.saffron else Color.White.copy(alpha = 0.18f), tween(500), label = "b$i")
                val sc by animateFloatAsState(if (i == step) 1.1f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s$i")
                Row(
                    Modifier.scale(sc).clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i < step) { Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White); Spacer(Modifier.width(4.dp)) }
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                if (i < 2) {
                    Box(Modifier.weight(1f).height(2.dp).padding(horizontal = 4.dp).background(if (i < step) x.saffron else Color.White.copy(alpha = 0.25f)))
                }
            }
        }
    }
}

@Composable
private fun Stop(
    emoji: String,
    title: String,
    subtitle: String,
    done: Boolean,
    active: Boolean,
    showLine: Boolean,
    actions: @Composable () -> Unit,
) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (done) LunchTheme.extras.successSoft else if (active) LunchTheme.extras.saffronSoft else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Default.Check, null, tint = LunchTheme.extras.success)
                else Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            if (showLine) Box(Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(top = 2.dp)) { actions() }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(42.dp).pressScale(interaction, 0.9f).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun OrderCard(order: BatchOrder, selectable: Boolean, checked: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val border by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "b")
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (checked) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .then(if (selectable) Modifier.clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle() } else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                selectable -> {
                    val sc by animateFloatAsState(if (checked) 1f else 0.8f, spring(Spring.DampingRatioMediumBouncy), label = "c")
                    Box(
                        Modifier.size(28.dp).clip(CircleShape)
                            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (checked) Icon(Icons.Default.Check, null, Modifier.size(18.dp).scale(sc), tint = MaterialTheme.colorScheme.onPrimary) }
                }
                else -> Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text(order.customerName.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.customerName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${meals(order.mealCount)} · #${order.orderId.take(6).uppercase()}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(order.status.label(), order.status.tone())
        }
    }
}

@Composable
private fun Banner(text: String, bg: Color, fg: Color, onDismiss: () -> Unit) {
    Text(
        text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg).clickable(onClick = onDismiss).padding(14.dp),
    )
}

// ---------------------------------------------------------------- sheets

@Composable
private fun SheetTitle(emoji: String, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(LunchTheme.extras.saffronSoft), contentAlignment = Alignment.Center) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SheetError(error: String?) {
    AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Text(
            "⚠  ${error.orEmpty()}", color = LunchTheme.extras.danger, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(MaterialTheme.shapes.medium)
                .background(LunchTheme.extras.dangerSoft).padding(12.dp),
        )
    }
}

/** Count the meals: big number with − / + around it, typed or tapped. */
@Composable
private fun PickupSheet(batch: DeliveryBatch, state: BatchDetailState, onConfirm: (Int) -> Unit) {
    var count by remember { mutableStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val matches = count == batch.totalMeals
    SheetTitle("🍱", "Count the meals", "This batch should have ${meals(batch.totalMeals)}. Count what you collected.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CountButton("−") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); count = (count - 1).coerceAtLeast(0) }
        Box(Modifier.width(150.dp), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = if (count == 0) "" else count.toString(),
                onValueChange = { v -> count = v.filter(Char::isDigit).take(4).toIntOrNull() ?: 0 },
                textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(LunchTheme.extras.saffron),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (count == 0) Text("0", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.outline)
                        inner()
                    }
                },
            )
        }
        CountButton("+") { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); count += 1 }
    }
    Spacer(Modifier.height(12.dp))
    val (hint, color) = when {
        count == 0 -> "Tap + for each meal, or type the number" to MaterialTheme.colorScheme.onSurfaceVariant
        matches -> "✓ Matches the batch" to LunchTheme.extras.success
        else -> "Expected ${batch.totalMeals} — recount before you leave" to LunchTheme.extras.warning
    }
    AnimatedContent(hint, label = "hint", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }) { h ->
        Text(h, color = color, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
    SheetError(state.error)
    PrimaryButton(
        "Confirm ${if (count > 0) meals(count) else "pickup"}",
        onClick = { onConfirm(count) },
        enabled = count > 0,
        loading = state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CountButton(symbol: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(64.dp).pressScale(interaction, 0.88f).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun HandoverSheet(orderCount: Int, state: BatchDetailState, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    SheetTitle(
        "🔐",
        "Hand over ${orderCount} order${if (orderCount == 1) "" else "s"}",
        if (orderCount == 1) "Ask the customer for the 4-digit code in their app, or use the office coordinator's code."
        else "Enter the office coordinator's 4-digit batch code.",
    )
    CodeInput(
        value = code,
        onValueChange = { new ->
            code = new
            if (new.length == 4 && !state.busy) onConfirm(new)
        },
        length = 4,
        isError = state.error != null,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Spacer(Modifier.height(20.dp))
    SheetError(state.error)
    PrimaryButton("Confirm handover", onClick = { onConfirm(code) }, enabled = code.length == 4, loading = state.busy, modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueSheet(orderCount: Int, state: BatchDetailState, onConfirm: (String, Boolean) -> Unit) {
    var reason by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf(false) }
    val reasons = listOf("🙋 Customer not available", "🚪 Office closed / no access", "💥 Meal damaged", "❓ Wrong or missing meal")
    SheetTitle("⚠️", "Report a problem", "For ${orderCount} order${if (orderCount == 1) "" else "s"}. The kitchen is told straight away.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reasons.forEach { r ->
            val text = r.substringAfter(' ')
            val active = reason == text
            Text(
                r,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape)
                    .background(if (active) LunchTheme.extras.danger else MaterialTheme.colorScheme.surface)
                    .clickable { reason = text }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = reason, onValueChange = { reason = it.take(300) },
        label = { Text("What happened?") },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
            .clickable { partial = !partial }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Part of the order was handed over", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = partial, onCheckedChange = { partial = it })
    }
    Spacer(Modifier.height(20.dp))
    SheetError(state.error)
    PrimaryButton(
        "Report problem",
        onClick = { onConfirm(reason, partial) },
        enabled = reason.isNotBlank(),
        loading = state.busy,
        icon = Icons.Default.Warning,
        containerColor = LunchTheme.extras.danger,
        modifier = Modifier.fillMaxWidth(),
    )
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
