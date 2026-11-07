package com.metech.lunch.delivery.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.auth.AuthRepository
import com.metech.lunch.core.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val codeSent: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** Shown on the sign-in screen when the server has no SMS and returns the code instead. */
    val shownCode: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** Moves to the code step only once the server has accepted the number. */
    fun requestOtp(phone: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val r = auth.requestOtp(phone)) {
                is ApiResult.Success -> _state.update { it.copy(busy = false, codeSent = true, shownCode = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = r.message) }
            }
        }
    }

    fun verifyOtp(phone: String, otp: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val r = auth.verifyOtp(phone, otp)) {
                is ApiResult.Success -> { _state.update { it.copy(busy = false) }; onSuccess() }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = r.message) }
            }
        }
    }

    fun editPhone() = _state.update { LoginState() }
}
