package com.metech.lunch.customer.ordering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Name plus office and drop point; required before the first order. */
@Composable
fun ProfileScreen(state: OrderingState, onSave: (name: String, officeId: String, dropPointId: String?) -> Unit) {
    var name by remember(state.profile?.id) { mutableStateOf(state.profile?.name.orEmpty()) }
    var officeId by remember(state.profile?.id) { mutableStateOf(state.profile?.defaultOfficeId) }
    var dropPointId by remember(state.profile?.id) { mutableStateOf(state.profile?.defaultDropPointId) }
    val office = state.offices.firstOrNull { it.id == officeId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Your details", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Office", style = MaterialTheme.typography.titleMedium)
        state.offices.forEach { o ->
            FilterChip(
                selected = o.id == officeId,
                onClick = { officeId = o.id; dropPointId = o.dropPoints.firstOrNull()?.id },
                label = { Text("${o.name} — ${o.address}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (office != null && office.dropPoints.isNotEmpty()) {
            Text("Drop point", style = MaterialTheme.typography.titleMedium)
            office.dropPoints.forEach { dp ->
                FilterChip(
                    selected = dp.id == dropPointId,
                    onClick = { dropPointId = dp.id },
                    label = { Text(dp.name) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { officeId?.let { onSave(name, it, dropPointId) } },
            enabled = name.isNotBlank() && officeId != null && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}
