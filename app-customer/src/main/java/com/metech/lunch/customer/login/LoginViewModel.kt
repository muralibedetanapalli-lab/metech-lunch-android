package com.metech.lunch.customer.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun requestOtp(phone: String) {
        viewModelScope.launch {
            _error.value = auth.requestOtp(phone).exceptionOrNull()?.let { "Could not send OTP" }
        }
    }

    fun verifyOtp(phone: String, otp: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            auth.verifyOtp(phone, otp)
                .onSuccess { _error.value = null; onSuccess() }
                .onFailure { _error.value = "Invalid OTP" }
        }
    }
}
