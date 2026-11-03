package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Model.InvoiceWithDetails
import com.example.roomkit.Date.Repositories.ReplacementInvoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReplacementUiState(
    val detail: InvoiceWithDetails? = null,
    val values: Map<String, String> = emptyMap(),
    val busy: Boolean = true,
    val dirty: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
    val openInvoiceId: Long? = null
)

class ReplacementInvoiceViewModel(
    private val repo: ReplacementInvoiceRepository,
    originalId: Long,
    draftId: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(ReplacementUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val result = if (draftId > 0L) repo.load(draftId) else repo.createOrOpen(originalId)
            result.onSuccess { detail ->
                mutable.value = ReplacementUiState(
                    detail = detail, values = valuesFor(detail.invoice), busy = false,
                    openInvoiceId = detail.invoice.invoiceId.takeIf { detail.invoice.status != "DRAFT" }
                )
            }.onFailure { e ->
                mutable.value = ReplacementUiState(busy = false, error = e.message ?: "Cannot open replacement.")
            }
        }
    }

    private fun valuesFor(i: InvoiceEntity) = mapOf(
        "rent" to i.rentKhr.toString(),
        "electricityPrevious" to (i.electricityPrevious ?: 0L).toString(),
        "electricityCurrent" to (i.electricityCurrent ?: 0L).toString(),
        "electricityRate" to i.electricityRateKhrSnapshot.toString(),
        "waterPrevious" to (i.waterPrevious ?: 0L).toString(),
        "waterCurrent" to (i.waterCurrent ?: 0L).toString(),
        "waterRate" to (if (i.waterBillingMethod.equals("FIXED", true))
            i.fixedWaterFeeKhrSnapshot ?: i.waterChargeKhr else i.waterRateKhrSnapshot ?: 0L).toString(),
        "rubbish" to i.rubbishFeeKhrSnapshot.toString(),
        "other" to i.otherChargesKhr.toString(), "discount" to i.discountKhr.toString(),
        "discountReason" to i.discountReason.orEmpty()
    )

    fun edit(key: String, value: String) {
        if (mutable.value.busy) return
        mutable.update { it.copy(values = it.values + (key to value), dirty = true, error = null, message = null) }
    }

    fun deleteDraft() {
        val snapshot = state.value
        val invoice = snapshot.detail?.invoice ?: return
        if (snapshot.busy || snapshot.deleted || invoice.replacesInvoiceId != null) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.deleteRegularDraft(invoice).onSuccess {
                mutable.update { it.copy(busy = false, deleted = true, dirty = false) }
            }.onFailure { e -> mutable.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun save(issue: Boolean = false) {
        val snapshot = mutable.value
        val invoice = snapshot.detail?.invoice ?: return
        if (snapshot.busy || invoice.status != "DRAFT") return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            if (issue) {
                repo.issue(invoice, snapshot.values).onSuccess { id ->
                    mutable.update { it.copy(busy = false, dirty = false, openInvoiceId = id) }
                }.onFailure { e ->
                    mutable.update { it.copy(busy = false, error = e.message ?: "Cannot issue draft.") }
                }
            } else {
                repo.save(invoice, snapshot.values).onSuccess { saved ->
                    // The details model need not support copy(): reload joins after save.
                    repo.load(saved.invoiceId).onSuccess { detail ->
                        mutable.update { it.copy(detail = detail, values = valuesFor(saved), busy = false,
                            dirty = false, message = "Draft saved.") }
                    }.onFailure { e ->
                        mutable.update { it.copy(busy = false, error = e.message ?: "Saved; reopen draft.") }
                    }
                }.onFailure { e ->
                    mutable.update { it.copy(busy = false, error = e.message ?: "Cannot save draft.") }
                }
            }
        }
    }
}
