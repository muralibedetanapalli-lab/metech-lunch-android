package com.metech.lunch.delivery.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.auth.AuthRepository
import com.metech.lunch.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun requestOtp(phone: String) {
        viewModelScope.launch {
            _error.value = (auth.requestOtp(phone) as? ApiResult.Failure)?.message
        }
    }

    fun verifyOtp(phone: String, otp: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            when (val result = auth.verifyOtp(phone, otp)) {
                is ApiResult.Success -> { _error.value = null; onSuccess() }
                is ApiResult.Failure -> _error.value = result.message
            }
        }
    }
}
