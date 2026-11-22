@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentSaveState(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)

class RecordPaymentViewModel(private val repository: BillingRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PaymentSaveState())
    val state = mutable.asStateFlow()

    // Kept for existing single-invoice callers, including other screens.
    fun save(invoiceId: Long, amount: Long, date: Long, method: String, note: String?, reference: String?) {
        perform { repository.recordPayment(invoiceId, amount, date, method, note, reference) }
    }
    fun saveCarryForward(invoiceId: Long, amount: Long, date: Long, method: String, note: String?, reference: String?, fingerprint: String) {
        perform { repository.recordCarryForwardPayment(invoiceId, amount, date, method, note, reference, fingerprint) }
    }
    private fun perform(save: suspend () -> Result<Unit>) {
        if (state.value.saving || state.value.saved) return
        mutable.value = PaymentSaveState(saving = true)
        viewModelScope.launch {
            try {
                val result = save()
                mutable.value = if (result.isSuccess) PaymentSaveState(saved = true)
                else PaymentSaveState(error = result.exceptionOrNull()?.message ?: "Failed to record payment")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = PaymentSaveState(error = e.message ?: "Failed to record payment") }
        }
    }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
}
