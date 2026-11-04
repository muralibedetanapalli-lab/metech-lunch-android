package com.metech.lunch.customer.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.network.ApiResult
import com.metech.lunch.core.network.LunchApi
import com.metech.lunch.core.network.apiCall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OrdersState(
    val loading: Boolean = false,
    val orders: List<Order> = emptyList(),
    val error: String? = null,
)

class OrdersViewModel(private val api: LunchApi) : ViewModel() {
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val r = apiCall { api.myOrders() }) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, orders = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}

data class OrderDetailState(
    val order: Order? = null,
    val cancelling: Boolean = false,
    val error: String? = null,
)

/**
 * Shows one order. While it waits for payment, it polls: the server confirms the order
 * only after the gateway callback, which can arrive after the user returns to the app.
 */
class OrderDetailViewModel(private val api: LunchApi, private val orderId: String) : ViewModel() {
    private val _state = MutableStateFlow(OrderDetailState())
    val state: StateFlow<OrderDetailState> = _state.asStateFlow()
    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var attempts = 0
            while (isActive) {
                fetch()
                val pending = _state.value.order?.status == OrderStatus.PENDING_PAYMENT
                if (!pending || attempts++ > MAX_POLLS) break
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    private suspend fun fetch() {
        when (val r = apiCall { api.order(orderId) }) {
            is ApiResult.Success -> _state.update { it.copy(order = r.value, error = null) }
            is ApiResult.Failure -> _state.update { it.copy(error = r.message) }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            _state.update { it.copy(cancelling = true, error = null) }
            when (val r = apiCall { api.requestCancellation(orderId) }) {
                is ApiResult.Success -> _state.update { it.copy(cancelling = false, order = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(cancelling = false, error = r.message) }
            }
        }
    }

    private companion object {
        const val POLL_MS = 3_000L
        const val MAX_POLLS = 100 // ~5 minutes
    }
}
