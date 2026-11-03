package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class InvoicePreviewViewModel(private val roomId: Long, private val repository: BillingRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(InvoicePreviewUiState())
    val uiState: StateFlow<InvoicePreviewUiState> = _uiState.asStateFlow()
    private var savedDraft: BillingRepository.BillingDraft? = null
    init { loadDraftForCurrentMonth() }
    fun loadDraftForCurrentMonth() {
        val c = Calendar.getInstance()
        loadDraft(c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
    }
    fun loadDraft(month: Int, year: Int) {
        if (_uiState.value.isSaving || _uiState.value.isIssuing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val existing = repository.findSavedDraft(roomId, month, year)
                if (existing != null) {
                    _uiState.value = InvoicePreviewUiState(isLoading = false, savedDraftId = existing)
                    return@launch
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                return@launch
            }
            repository.prepareBillingDraft(roomId, month, year).onSuccess { draft ->
                savedDraft = draft
                _uiState.value = InvoicePreviewUiState(isLoading = false, draft = draft,
                    otherText = draft.result.otherChargesKhr.toString(), discountText = draft.result.discountKhr.toString())
            }.onFailure { error ->
                savedDraft = null
                _uiState.update { it.copy(isLoading = false, draft = null, errorMessage = error.message ?: "Could not load draft.") }
            }
        }
    }
    fun editAdjustments(other: String, discount: String) {
        val base = savedDraft ?: return
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isIssuing || state.issuedInvoiceId != null || state.savedDraftId != null) return
        val edited = runCatching {
            val o = other.trim().toLongOrNull() ?: error("Enter a whole KHR amount for other charges.")
            val d = discount.trim().toLongOrNull() ?: error("Enter a whole KHR amount for discount.")
            repository.adjustDraft(base, o, d)
        }
        _uiState.update { it.copy(otherText = other, discountText = discount,
            draft = edited.getOrNull() ?: base,
            validationMessage = edited.exceptionOrNull()?.let { e ->
                if (e is ArithmeticException) "Amount is too large." else e.message
            }, errorMessage = null,
            dirty = edited.getOrNull()?.result != base.result) }
    }
    fun saveDraft() { persist(false) }
    fun issueInvoice(onSuccess: (Long) -> Unit) { persist(true, onSuccess) }
    private fun persist(issue: Boolean, onSuccess: (Long) -> Unit = {}) {
        val base = savedDraft ?: return
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isIssuing || state.validationMessage != null || state.issuedInvoiceId != null || state.savedDraftId != null) return
        val edited = state.draft ?: return
        _uiState.update { it.copy(isSaving = !issue, isIssuing = issue, errorMessage = null) }
        viewModelScope.launch {
            repository.persistRegularDraft(base, edited.result.otherChargesKhr, edited.result.discountKhr, issue)
                .onSuccess { id ->
                    _uiState.update { it.copy(isSaving = false, isIssuing = false, dirty = false,
                        savedDraftId = if (issue) null else id, issuedInvoiceId = if (issue) id else null) }
                    if (issue) onSuccess(id)
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, isIssuing = false, errorMessage = e.message ?: "Could not save invoice.") }
                }
        }
    }
}
