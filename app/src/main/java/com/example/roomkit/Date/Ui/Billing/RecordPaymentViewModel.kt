package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentSaveState(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)

/** Retains an in-flight save during rotation; disables repeated Save taps. */
class RecordPaymentViewModel(private val repository: BillingRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PaymentSaveState())
    val state = mutable.asStateFlow()
    fun save(invoiceId: Long, amount: Long, date: Long, method: String, note: String?, reference: String?) {
        if (mutable.value.saving || mutable.value.saved) return
        mutable.value = PaymentSaveState(saving=true)
        viewModelScope.launch {
            val result = repository.recordPayment(invoiceId, amount, date, method, note, reference)
            mutable.value = if (result.isSuccess) PaymentSaveState(saved=true)
            else PaymentSaveState(error=result.exceptionOrNull()?.message ?: "Failed to record payment")
        }
    }
    fun clearError() { mutable.value = mutable.value.copy(error=null) }
}
