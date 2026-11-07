package com.metech.lunch.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The Metech Lunch mark: a saffron bowl with three wisps of steam on a green tile. */
@Composable
fun LunchLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tile: Color = Palette.Green,
    bowl: Color = Palette.Saffron,
    steam: Color = if (tile == Palette.Cream) Palette.Green else Palette.Cream,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(tile, cornerRadius = CornerRadius(s * 0.28f))
        // Bowl: a flat-topped half disc with a small foot.
        val bowlTop = s * 0.52f
        drawArc(bowl, startAngle = 0f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(s * 0.18f, bowlTop - s * 0.32f), size = Size(s * 0.64f, s * 0.64f))
        drawRoundRect(bowl, topLeft = Offset(s * 0.14f, bowlTop - s * 0.03f), size = Size(s * 0.72f, s * 0.07f),
            cornerRadius = CornerRadius(s * 0.035f))
        drawRoundRect(bowl, topLeft = Offset(s * 0.40f, s * 0.80f), size = Size(s * 0.20f, s * 0.05f),
            cornerRadius = CornerRadius(s * 0.025f))
        // Steam.
        listOf(0.36f, 0.5f, 0.64f).forEachIndexed { i, x ->
            val path = Path().apply {
                val top = s * (if (i == 1) 0.16f else 0.22f)
                moveTo(s * x, bowlTop - s * 0.10f)
                cubicTo(s * (x - 0.06f), s * 0.36f, s * (x + 0.06f), s * 0.30f, s * x, top)
            }
            drawPath(path, steam, style = Stroke(width = s * 0.045f, cap = StrokeCap.Round))
        }
    }
}

/** Scales a little while pressed, for a tactile feel on any clickable. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press")
    this.scale(scale)
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.height(56.dp).pressScale(interaction),
    ) {
        AnimatedContent(loading, label = "button") { busy ->
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val color = if (danger) LunchTheme.extras.danger else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.height(52.dp).pressScale(interaction),
    ) { Text(text, style = MaterialTheme.typography.titleSmall) }
}

/** A white rounded card with a hairline border, the basic container on cream. */
@Composable
fun LunchCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(Modifier.padding(padding), content = content) }
}

enum class Tone { Success, Warning, Danger, Neutral, Accent }

@Composable
fun StatusPill(text: String, tone: Tone, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val x = LunchTheme.extras
    val (fg, bg) = when (tone) {
        Tone.Success -> x.success to x.successSoft
        Tone.Warning -> x.warning to x.warningSoft
        Tone.Danger -> x.danger to x.dangerSoft
        Tone.Accent -> MaterialTheme.colorScheme.onSecondaryContainer to x.saffronSoft
        Tone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseDot(fg, pulse)
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PulseDot(color: Color, pulse: Boolean, size: Dp = 7.dp) {
    val alpha = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/** India's food marking: a green square with a dot (veg) or a brown square with a triangle (non-veg). */
@Composable
fun VegMark(veg: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val color = if (veg) LunchTheme.extras.veg else LunchTheme.extras.nonVeg
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(color, style = Stroke(s * 0.1f), cornerRadius = CornerRadius(s * 0.12f))
        if (veg) {
            drawCircle(color, radius = s * 0.24f)
        } else {
            val path = Path().apply {
                moveTo(s * 0.5f, s * 0.24f); lineTo(s * 0.76f, s * 0.72f); lineTo(s * 0.24f, s * 0.72f); close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bob = rememberInfiniteTransition(label = "bob")
        val y by bob.animateFloat(0f, -8f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "y")
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(LunchTheme.extras.saffronSoft),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, style = MaterialTheme.typography.displaySmall, modifier = Modifier.graphicsLayer { translationY = y.dp.toPx() }) }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** A soft moving highlight for skeleton placeholders while content loads. */
fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerLow
    this.clip(shape).background(
        Brush.linearGradient(
            colors = listOf(base, light, base),
            start = Offset(x * 600f, 0f),
            end = Offset(x * 600f + 400f, 200f),
        ),
    )
}

/** One box per digit; the real input is an invisible text field so paste and the number keyboard work. */
@Composable
fun CodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
) {
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focusRequester.requestFocus() } }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            for (dx in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(dx, tween(45))
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter(Char::isDigit).take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.focusRequester(focusRequester).graphicsLayer { translationX = shake.value.dp.toPx() },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(length) { i ->
                    val char = value.getOrNull(i)?.toString().orEmpty()
                    val active = i == value.length
                    val borderColor = when {
                        isError -> LunchTheme.extras.danger
                        active -> MaterialTheme.colorScheme.secondary
                        char.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        Modifier.weight(1f).height(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(if (active) 2.dp else 1.5.dp, borderColor, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            char,
                            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                            label = "digit",
                        ) { c -> Text(c, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
            }
        },
    )
}

/** A short confetti burst, for moments worth celebrating (order confirmed, delivery done). */
@Composable
fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier) {
    if (trigger == null) return
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val colors = listOf(Palette.Saffron, Palette.Green, Color(0xFFF2C94C), Palette.SaffronDeep, Color(0xFF6FCF97))
    val pieces = remember(trigger) {
        val rnd = Random(trigger.hashCode())
        List(70) {
            val angle = rnd.nextDouble(-PI * 0.95, -PI * 0.05)
            val speed = rnd.nextDouble(0.55, 1.15).toFloat()
            Triple(Offset(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed), colors[it % colors.size], rnd.nextFloat() * 360f)
        }
    }
    if (progress.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val w = 10.dp.toPx()
        val h = 5.dp.toPx()
        val origin = Offset(size.width / 2, size.height * 0.35f)
        pieces.forEach { (v, color, spin) ->
            val x = origin.x + v.x * size.width * 0.7f * p
            val y = origin.y + v.y * size.height * 0.45f * p + size.height * 0.9f * p * p
            rotate(spin + p * 720f, Offset(x, y)) {
                drawRect(color.copy(alpha = 1f - p * p), topLeft = Offset(x - w / 2, y - h / 2), size = Size(w, h))
            }
        }
    }
}
