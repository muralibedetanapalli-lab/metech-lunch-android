package com.metech.lunch.delivery.batches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metech.lunch.core.model.BatchStatus
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.DeliveryIssueReport
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.OrderStatus
import com.metech.lunch.core.model.PickupConfirmation
import com.metech.lunch.core.network.ApiResult
import com.metech.lunch.core.network.LunchApi
import com.metech.lunch.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val OPEN_STATUSES = setOf(BatchStatus.ASSIGNED, BatchStatus.PICKED_UP, BatchStatus.IN_TRANSIT)

/** A batch still needs work while it is assigned, picked up, or has orders out for delivery. */
val DeliveryBatch.isOpen: Boolean
    get() = status in OPEN_STATUSES ||
        (status == BatchStatus.PARTIALLY_DELIVERED && orders.any { it.status == OrderStatus.OUT_FOR_DELIVERY })

data class BatchesState(
    val loading: Boolean = false,
    val batches: List<DeliveryBatch> = emptyList(),
    val error: String? = null,
    val partnerName: String = "",
) {
    val open: List<DeliveryBatch> get() = batches.filter { it.isOpen }
    val done: List<DeliveryBatch> get() = batches.filterNot { it.isOpen }
}

class BatchesViewModel(private val api: LunchApi) : ViewModel() {
    private val _state = MutableStateFlow(BatchesState())
    val state: StateFlow<BatchesState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (_state.value.partnerName.isEmpty()) {
                (apiCall { api.me() } as? ApiResult.Success)?.let { me -> _state.update { it.copy(partnerName = me.value.name) } }
            }
            when (val r = apiCall { api.assignedBatches() }) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, batches = r.value) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }
}

data class BatchDetailState(
    val batch: DeliveryBatch? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** Short confirmation after a successful action, e.g. "2 orders handed over". */
    val message: String? = null,
)

class BatchDetailViewModel(private val api: LunchApi, private val batchId: String) : ViewModel() {
    private val _state = MutableStateFlow(BatchDetailState())
    val state: StateFlow<BatchDetailState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            when (val r = apiCall { api.batch(batchId) }) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, batch = r.value, error = null) }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun confirmPickup(count: Int, onDone: () -> Unit) = act(onDone, "Pickup confirmed: $count meal${if (count == 1) "" else "s"}") {
        api.confirmPickup(batchId, PickupConfirmation(count))
    }

    fun handOver(orderIds: List<String>, otp: String, onDone: () -> Unit) =
        act(onDone, if (orderIds.size == 1) "Order handed over" else "${orderIds.size} orders handed over") {
            api.confirmHandover(batchId, HandoverConfirmation(orderIds, otp.trim()))
        }

    fun reportIssue(orderIds: List<String>, reason: String, partial: Boolean, onDone: () -> Unit) =
        act(onDone, "Problem reported to the kitchen") {
            api.reportIssue(batchId, DeliveryIssueReport(orderIds, reason.trim(), partial))
        }

    fun clearMessages() = _state.update { it.copy(error = null, message = null) }

    /** Runs one action; on success replaces the batch and closes the dialog via [onDone]. */
    private fun act(onDone: () -> Unit, successMessage: String, call: suspend () -> DeliveryBatch) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            when (val r = apiCall { call() }) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, batch = r.value, message = successMessage) }
                    onDone()
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, error = r.message) }
            }
        }
    }
}
