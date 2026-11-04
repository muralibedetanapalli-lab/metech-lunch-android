package com.metech.lunch.customer.ordering

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metech.lunch.core.ui.formatPaise

@Composable
fun CheckoutScreen(
    state: OrderingState,
    onChangeQuantity: (com.metech.lunch.core.model.MenuItem, Int) -> Unit,
    onPay: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Order summary", style = MaterialTheme.typography.headlineSmall)
        if (state.cartLines.isEmpty()) {
            Text("Your order is empty.")
            return@Column
        }
        state.cartLines.forEach { (item, qty) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(formatPaise(item.pricePaise * qty), style = MaterialTheme.typography.bodyMedium)
                }
                QuantityStepper(qty, onMinus = { onChangeQuantity(item, -1) }, onPlus = { onChangeQuantity(item, 1) })
            }
        }
        HorizontalDivider()
        Row {
            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(formatPaise(state.cartTotalPaise), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        Text("Delivery", style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(state.office?.name, state.dropPointName).joinToString(" · "))
        state.office?.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Your order is confirmed once the payment is verified. You can cancel before the cutoff for a refund.",
            style = MaterialTheme.typography.bodySmall,
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = onPay,
            enabled = !state.placing && state.menu?.orderingOpen == true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.placing) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
            else Text("Pay ${formatPaise(state.cartTotalPaise)}")
        }
    }
}
