package com.metech.lunch.customer.ordering

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.PrimaryButton
import com.metech.lunch.customer.ui.Avatar
import com.metech.lunch.customer.ui.ScreenTopBar

/** Name, office and drop point. Doubles as first-time onboarding (no back arrow, welcome copy). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    state: OrderingState,
    onBack: (() -> Unit)?,
    onSave: (name: String, officeId: String, dropPointId: String?) -> Unit,
    onSignOut: () -> Unit,
) {
    var name by remember(state.profile?.id) { mutableStateOf(state.profile?.name.orEmpty()) }
    var officeId by remember(state.profile?.id) { mutableStateOf(state.profile?.defaultOfficeId) }
    var dropPointId by remember(state.profile?.id) { mutableStateOf(state.profile?.defaultDropPointId) }
    var confirmSignOut by remember { mutableStateOf(false) }
    val office = state.offices.firstOrNull { it.id == officeId }
    val onboarding = onBack == null

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(if (onboarding) "Welcome!" else "Your profile", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name, size = 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (onboarding) "Let's get your lunch sorted" else name.ifBlank { "Your profile" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        prettyPhone(state.profile?.phone.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Where should we deliver?", style = MaterialTheme.typography.titleMedium)
            state.offices.forEach { o ->
                val selected = o.id == officeId
                val border by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "b",
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, border),
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable {
                        if (officeId != o.id) { officeId = o.id; dropPointId = o.dropPoints.firstOrNull()?.id }
                    },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.surface else LunchTheme.extras.saffronSoft),
                            contentAlignment = Alignment.Center,
                        ) { Text("🏢", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.name, style = MaterialTheme.typography.titleSmall)
                            Text(o.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedVisibility(selected, enter = fadeIn(), exit = fadeOut()) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                        }
                    }
                }
            }

            AnimatedVisibility(
                office != null && office.dropPoints.isNotEmpty(),
                enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pick-up spot", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        office?.dropPoints?.forEach { dp ->
                            val selected = dp.id == dropPointId
                            Text(
                                dp.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clip(CircleShape)
                                    .background(if (selected) LunchTheme.extras.saffron else MaterialTheme.colorScheme.surface)
                                    .clickable { dropPointId = dp.id }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = LunchTheme.extras.danger, style = MaterialTheme.typography.bodyMedium)
            }

            if (!onboarding) {
                TextButton(onClick = { confirmSignOut = true }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = LunchTheme.extras.danger)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", color = LunchTheme.extras.danger, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
            PrimaryButton(
                text = if (onboarding) "Show me the menu" else "Save",
                onClick = { officeId?.let { onSave(name, it, dropPointId) } },
                enabled = name.isNotBlank() && officeId != null,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            )
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

/** +919999900060 -> +91 99999 00060 */
private fun prettyPhone(p: String): String =
    if (p.startsWith("+91") && p.length == 13) "+91 ${p.substring(3, 8)} ${p.substring(8)}" else p
