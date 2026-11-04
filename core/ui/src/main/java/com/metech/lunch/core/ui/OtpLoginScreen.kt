package com.metech.lunch.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Shared phone + OTP login UI. Each app supplies its own title and callbacks. */
@Composable
fun OtpLoginScreen(
    title: String,
    onRequestOtp: (phone: String) -> Unit,
    onVerifyOtp: (phone: String, otp: String) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        if (otpSent) {
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it },
                label = { Text("OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                if (otpSent) {
                    onVerifyOtp(phone, otp)
                } else {
                    onRequestOtp(phone)
                    otpSent = true
                }
            },
            enabled = phone.isNotBlank() && (!otpSent || otp.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (otpSent) "Verify" else "Send OTP")
        }
    }
}
