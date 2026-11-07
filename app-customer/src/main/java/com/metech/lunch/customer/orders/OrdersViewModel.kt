package com.metech.lunch.customer.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.model.CustomerTracking
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
    /** The delivery partner's live position, while the order is on the way. */
    val tracking: CustomerTracking? = null,
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

    /**
     * Keeps the screen current while it's visible: quickly while waiting for payment, the
     * rider's position every few seconds while on the way, and slowly while waiting for pickup.
     */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var paymentChecks = 0
            while (isActive) {
                fetch()
                when (_state.value.order?.status) {
                    OrderStatus.PENDING_PAYMENT -> {
                        if (paymentChecks++ > MAX_POLLS) break
                        delay(POLL_MS)
                    }
                    OrderStatus.OUT_FOR_DELIVERY -> repeat(TRACKING_ROUNDS) {
                        fetchTracking()
                        delay(TRACKING_MS)
                    }
                    OrderStatus.CONFIRMED, OrderStatus.PREPARING -> delay(WAITING_MS)
                    else -> {
                        _state.update { it.copy(tracking = null) }
                        break
                    }
                }
            }
        }
    }

    private suspend fun fetchTracking() {
        when (val r = apiCall { api.orderTracking(orderId) }) {
            is ApiResult.Success -> _state.update { it.copy(tracking = r.value) }
            is ApiResult.Failure -> Unit // keep the last position; the next round tries again
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
        const val TRACKING_MS = 5_000L
        const val TRACKING_ROUNDS = 3 // refresh the order itself every ~15 s
        const val WAITING_MS = 30_000L
    }
}
