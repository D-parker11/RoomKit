package com.example.roomkit.Date.Ui.Billing
import com.example.roomkit.Date.Repositories.BillingRepository

data class InvoicePreviewUiState(
    val isLoading: Boolean = true,
    val draft: BillingRepository.BillingDraft? = null,
    val isIssuing: Boolean = false,
    val savedDraftId: Long? = null,
    val issuedInvoiceId: Long? = null,
    val errorMessage: String? = null,
    val otherText: String = "0",
    val discountText: String = "0",
    val validationMessage: String? = null,
    val dirty: Boolean = false,
    val isSaving: Boolean = false
)
