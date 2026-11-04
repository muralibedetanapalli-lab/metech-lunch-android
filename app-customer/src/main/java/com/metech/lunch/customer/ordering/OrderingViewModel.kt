package com.metech.lunch.customer.ordering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.model.DailyMenu
import com.metech.lunch.core.model.ErrorCodes
import com.metech.lunch.core.model.MenuItem
import com.metech.lunch.core.model.Office
import com.metech.lunch.core.model.PlaceOrderLine
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.UpdateProfileRequest
import com.metech.lunch.core.model.UserProfile
import com.metech.lunch.core.network.ApiResult
import com.metech.lunch.core.network.LunchApi
import com.metech.lunch.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OrderingState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val offices: List<Office> = emptyList(),
    val menu: DailyMenu? = null,
    /** menuItemId -> quantity */
    val cart: Map<String, Int> = emptyMap(),
    val placing: Boolean = false,
    val error: String? = null,
) {
    val needsProfile: Boolean get() = profile != null && (profile.name.isBlank() || profile.defaultOfficeId == null)
    val office: Office? get() = offices.firstOrNull { it.id == profile?.defaultOfficeId }
    val dropPointName: String? get() = office?.dropPoints?.firstOrNull { it.id == profile?.defaultDropPointId }?.name
    val cartLines: List<Pair<MenuItem, Int>>
        get() = menu?.items.orEmpty().mapNotNull { item -> cart[item.id]?.let { item to it } }
    val cartTotalPaise: Long get() = cartLines.sumOf { (item, qty) -> item.pricePaise * qty }
    val cartCount: Int get() = cart.values.sum()
}

/** Activity-scoped: the menu, cart and checkout screens share one instance. */
class OrderingViewModel(private val api: LunchApi) : ViewModel() {

    private val _state = MutableStateFlow(OrderingState())
    val state: StateFlow<OrderingState> = _state.asStateFlow()

    /** One key per cart, so a double tap or a retry cannot create two orders. */
    private var idempotencyKey = UUID.randomUUID().toString()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val profile = apiCall { api.me() }
            val offices = apiCall { api.offices() }
            if (profile is ApiResult.Failure || offices is ApiResult.Failure) {
                val msg = (profile as? ApiResult.Failure ?: offices as ApiResult.Failure).message
                _state.update { it.copy(loading = false, error = msg) }
                return@launch
            }
            _state.update {
                it.copy(
                    profile = (profile as ApiResult.Success).value,
                    offices = (offices as ApiResult.Success).value,
                )
            }
            loadMenu()
        }
    }

    private suspend fun loadMenu() {
        val officeId = _state.value.profile?.defaultOfficeId
        if (officeId == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        when (val menu = apiCall { api.todaysMenu(officeId) }) {
            is ApiResult.Success -> _state.update { s ->
                // Drop cart lines that are no longer orderable.
                val orderable = menu.value.items.filter { it.available }.associateBy { it.id }
                val cart = s.cart.filterKeys { it in orderable }
                    .mapValues { (id, qty) -> orderable.getValue(id).remainingCapacity?.let { minOf(qty, it) } ?: qty }
                    .filterValues { it > 0 }
                s.copy(loading = false, menu = menu.value, cart = cart)
            }
            is ApiResult.Failure -> _state.update { it.copy(loading = false, error = menu.message) }
        }
    }

    fun saveProfile(name: String, officeId: String, dropPointId: String?, onSaved: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val officeChanged = officeId != _state.value.profile?.defaultOfficeId
            when (val result = apiCall { api.updateProfile(UpdateProfileRequest(name.trim(), _state.value.profile?.email, officeId, dropPointId)) }) {
                is ApiResult.Success -> {
                    _state.update { it.copy(profile = result.value, cart = if (officeChanged) emptyMap() else it.cart) }
                    loadMenu()
                    onSaved()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }

    fun changeQuantity(item: MenuItem, delta: Int) {
        _state.update { s ->
            val current = s.cart[item.id] ?: 0
            val max = minOf(MAX_PER_ITEM, item.remainingCapacity ?: MAX_PER_ITEM)
            val next = (current + delta).coerceIn(0, max)
            s.copy(cart = if (next == 0) s.cart - item.id else s.cart + (item.id to next))
        }
    }

    /** Creates the order on the server and returns the checkout URL to open. */
    fun placeOrder(onCreated: (orderId: String, paymentUrl: String?) -> Unit) {
        val s = _state.value
        val profile = s.profile ?: return
        val officeId = profile.defaultOfficeId ?: return
        if (s.cart.isEmpty() || s.placing) return
        viewModelScope.launch {
            _state.update { it.copy(placing = true, error = null) }
            val request = PlaceOrderRequest(
                officeId = officeId,
                dropPointId = profile.defaultDropPointId,
                lines = s.cart.map { (id, qty) -> PlaceOrderLine(id, qty) },
                idempotencyKey = idempotencyKey,
            )
            when (val result = apiCall { api.placeOrder(request) }) {
                is ApiResult.Success -> {
                    idempotencyKey = UUID.randomUUID().toString()
                    _state.update { it.copy(placing = false, cart = emptyMap()) }
                    onCreated(result.value.order.id, result.value.paymentUrl)
                }
                is ApiResult.Failure -> {
                    // A rule rejected the order (closed, sold out...): the next attempt is a new order.
                    if (result.httpStatus == 422) idempotencyKey = UUID.randomUUID().toString()
                    _state.update { it.copy(placing = false, error = result.message) }
                    if (result.code in REFRESH_ON) loadMenu()
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private companion object {
        const val MAX_PER_ITEM = 20
        val REFRESH_ON = setOf(ErrorCodes.ORDERING_CLOSED, ErrorCodes.OUT_OF_CAPACITY, ErrorCodes.ITEM_UNAVAILABLE)
    }
}
