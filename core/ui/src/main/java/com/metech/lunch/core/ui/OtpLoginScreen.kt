package com.metech.lunch.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val PHONE_DIGITS = 10
private const val OTP_DIGITS = 6
private const val RESEND_SECONDS = 30

/**
 * Shared phone + code sign-in for both apps: a green hero with the logo, then a cream sheet
 * that slides from "your number" to "enter the code". The caller moves to the code step by
 * setting [codeSent] once the server has accepted the number.
 */
@Composable
fun OtpLoginScreen(
    appName: String,
    tagline: String,
    heroEmoji: List<String>,
    codeSent: Boolean,
    busy: Boolean,
    errorMessage: String?,
    onRequestOtp: (phone: String) -> Unit,
    onVerifyOtp: (phone: String, otp: String) -> Unit,
    onEditPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var digits by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val phone = "+91$digits"
    val x = LunchTheme.extras

    Column(
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(x.heroStart, x.heroEnd)))
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        // Hero
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 28.dp, bottom = 32.dp)) {
            FloatingFood(heroEmoji, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LunchLogo(size = 76.dp, tile = Palette.Cream, bowl = Palette.Saffron)
                Spacer(Modifier.height(16.dp))
                Text(appName, style = MaterialTheme.typography.headlineLarge, color = Palette.Cream)
                Spacer(Modifier.height(6.dp))
                Text(tagline, style = MaterialTheme.typography.bodyLarge, color = Palette.Cream.copy(alpha = 0.8f))
            }
        }

        // Sheet
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = true)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
                .padding(24.dp),
        ) {
            AnimatedContent(
                codeSent,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
                },
                label = "step",
            ) { onCodeStep ->
                if (!onCodeStep) {
                    PhoneStep(
                        digits = digits,
                        onDigits = { digits = it },
                        busy = busy,
                        error = errorMessage,
                        onContinue = { code = ""; onRequestOtp(phone) },
                    )
                } else {
                    CodeStep(
                        phoneLabel = "+91 ${digits.take(5)} ${digits.drop(5)}",
                        code = code,
                        onCode = { new ->
                            code = new
                            if (new.length == OTP_DIGITS && !busy) onVerifyOtp(phone, new)
                        },
                        busy = busy,
                        error = errorMessage,
                        onVerify = { onVerifyOtp(phone, code) },
                        onResend = { code = ""; onRequestOtp(phone) },
                        onEdit = { code = ""; onEditPhone() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneStep(digits: String, onDigits: (String) -> Unit, busy: Boolean, error: String?, onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sign in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We'll send a 6-digit code to your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val valid = digits.length == PHONE_DIGITS
        Row(
            Modifier.fillMaxWidth().height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, if (error != null) LunchTheme.extras.danger else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🇮🇳  +91", style = MaterialTheme.typography.titleMedium)
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Box(Modifier.weight(1f)) {
                if (digits.isEmpty()) {
                    Text("98765 43210", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                }
                BasicTextField(
                    value = digits,
                    onValueChange = { onDigits(it.filter(Char::isDigit).take(PHONE_DIGITS)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (valid && !busy) onContinue() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(valid, enter = fadeIn(), exit = fadeOut()) {
                Text("✓", color = LunchTheme.extras.success, style = MaterialTheme.typography.titleLarge)
            }
        }
        ErrorLine(error)
        PrimaryButton(
            text = "Send code",
            onClick = onContinue,
            enabled = valid,
            loading = busy,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("Your number is only used to sign you in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CodeStep(
    phoneLabel: String,
    code: String,
    onCode: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onEdit: () -> Unit,
) {
    var secondsLeft by rememberSaveable { mutableIntStateOf(RESEND_SECONDS) }
    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) { delay(1000); secondsLeft-- }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Enter the code", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sent to $phoneLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Change")
            }
        }
        CodeInput(value = code, onValueChange = onCode, length = OTP_DIGITS, isError = error != null)
        ErrorLine(error)
        PrimaryButton(
            text = "Verify & continue",
            onClick = onVerify,
            enabled = code.length == OTP_DIGITS,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (secondsLeft > 0) {
                Text("Resend code in 0:%02d".format(secondsLeft), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = { secondsLeft = RESEND_SECONDS; onResend() }, enabled = !busy) {
                    Text("Resend code", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(error: String?) {
    AnimatedVisibility(error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LunchTheme.extras.dangerSoft).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠", color = LunchTheme.extras.danger)
            Spacer(Modifier.width(8.dp))
            Text(error.orEmpty(), color = LunchTheme.extras.danger, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Food emoji drifting gently around the logo, clear of the title below it. */
@Composable
private fun FloatingFood(emoji: List<String>, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "d")
    // Offsets from the top centre; the logo is 76dp wide and the title starts ~120dp down.
    val spots = listOf(
        Triple((-150).dp, 0.dp, -12f),
        Triple(150.dp, 6.dp, 14f),
        Triple((-100).dp, 44.dp, 8f),
        Triple(100.dp, 46.dp, -10f),
    )
    Box(modifier) {
        spots.forEachIndexed { i, (dx, dy, tilt) ->
            val e = emoji.getOrNull(i % emoji.size) ?: return@forEachIndexed
            val phase = if (i % 2 == 0) drift else 1f - drift
            Box(
                Modifier.align(Alignment.TopCenter)
                    .offset(x = dx, y = dy)
                    .graphicsLayer { translationY = (phase * 8f).dp.toPx() }
                    .rotate(tilt + phase * 6f)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) { Text(e, style = MaterialTheme.typography.titleLarge) }
        }
    }
}
