package com.example.roomkit.Date.Ui.Billing

import android.net.Uri
import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.DocumentStatus
import com.example.roomkit.Date.Billing.PaymentStatus
import com.example.roomkit.Date.Billing.invoiceStatusClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Billing.BillingCalculator
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InvoiceDetailViewModel(
    private val invoiceId: Long,
    private val repository: BillingRepository
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)

    // Export PDF State Handling
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _lastExportedUri = MutableStateFlow<Uri?>(null)
    val lastExportedUri: StateFlow<Uri?> = _lastExportedUri.asStateFlow()

    val uiState: StateFlow<InvoiceDetailUiState> = combine(
        repository.observeInvoiceDetailWithItems(invoiceId),
        invoiceStatusClock()
    ) { saved, now ->
        val detail = saved?.detail
        if (detail == null) {
            InvoiceDetailUiState(isLoading = false, errorMessage = "Invoice not found.")
        } else {
            val inv = detail.invoice
            if (inv.currency != "KHR") return@combine InvoiceDetailUiState(isLoading = false,
                errorMessage = "Unsupported invoice currency.")
            val items = requireNotNull(saved).items
            try {
                com.example.roomkit.Date.Billing.InvoiceItems.validate(inv, items)
            } catch (e: IllegalArgumentException) {
                return@combine InvoiceDetailUiState(isLoading = false, errorMessage = e.message)
            } catch (e: ArithmeticException) {
                return@combine InvoiceDetailUiState(isLoading = false, errorMessage = "Invoice item total is too large.")
            }
            val lines = items.associateBy { it.itemCode }
            fun amount(code: String) = lines.getValue(code).amountKhr

            val account = try {
                com.example.roomkit.Date.Billing.InvoiceAccountBalances.calculate(inv, saved.accountInvoices)
            } catch (e: Exception) {
                return@combine InvoiceDetailUiState(isLoading = false,
                    errorMessage = e.message ?: "Unable to calculate account balance.")
            }
            val payments = saved.accountInvoices.single { it.invoice.invoiceId == inv.invoiceId }
                .payments.sortedWith(compareByDescending<com.example.roomkit.Date.Local.Entity.PaymentEntity> { it.paymentDate }
                    .thenByDescending { it.paymentId })
            val activePaid = account.currentPaidKhr
            val separated = InvoiceStatusResolver.resolve(inv.status, inv.totalKhr, activePaid, inv.dueDate, now)
            // Compatibility string for existing action guards. Overdue is independent.
            val resolvedStatus = when (separated.documentStatus) {
                DocumentStatus.DRAFT -> "DRAFT"
                DocumentStatus.VOID -> "VOID"
                DocumentStatus.UNKNOWN -> inv.status.trim().uppercase(Locale.ROOT)
                DocumentStatus.ISSUED -> when (separated.paymentStatus) {
                    PaymentStatus.PAID -> "PAID"
                    PaymentStatus.PARTIALLY_PAID -> "PARTIALLY_PAID"
                    else -> "ISSUED"
                }
            }

            val periodCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.MONTH, inv.billingMonth - 1)
                set(Calendar.YEAR, inv.billingYear)
            }

            val isWaterMeter = lines.getValue("WATER").unit != "FIXED"

            InvoiceDetailUiState(
                isLoading = false,
                invoiceId = inv.invoiceId,
                currency = inv.currency,
                discountReason = inv.discountReason,
                items = items,
                replacesInvoiceId = inv.replacesInvoiceId,
                invoiceNumberFormatted = "#%04d".format(inv.invoiceId),
                billingPeriodFormatted = monthYearFormat.format(periodCal.time),
                tenantName = detail.tenantName,
                tenantPhone = detail.tenantPhone,
                roomNumber = detail.roomNumber,
                buildingName = detail.buildingName,
                rentKhr = amount("RENT"),
                electricityUsage = lines.getValue("ELECTRICITY").quantity ?: 0L,
                electricityRateKhr = lines.getValue("ELECTRICITY").unitPriceKhr ?: 0L,
                electricityChargeKhr = amount("ELECTRICITY"),
                isWaterMetered = isWaterMeter,
                waterUsage = lines.getValue("WATER").quantity ?: 0L,
                waterRateKhr = lines.getValue("WATER").unitPriceKhr ?: 0L,
                waterChargeKhr = amount("WATER"),
                rubbishFeeKhr = amount("RUBBISH"),
                utilitySubtotalKhr = com.example.roomkit.Date.Billing.InvoiceItems.utilitySubtotal(items),
                otherChargesKhr = amount("OTHER"),
                discountKhr = Math.negateExact(amount("DISCOUNT")),
                billingPeriodDates = com.example.roomkit.Date.Billing.BillingPeriod.label(inv.billingPeriodStart, inv.billingPeriodEnd),
                totalKhr = inv.totalKhr,
                amountPaidKhr = activePaid,
                balanceKhr = account.currentInvoiceBalanceKhr,
                previousUnpaidKhr = account.previousUnpaidKhr,
                laterUnpaidKhr = account.laterUnpaidKhr,
                totalAccountBalanceKhr = account.totalAccountBalanceKhr,
                dueDateFormatted = dateFormat.format(Date(inv.dueDate)),
                dueDateTimestamp = inv.dueDate,
                status = resolvedStatus,
                documentStatus = separated.documentStatus,
                paymentStatus = separated.paymentStatus,
                isOverdue = separated.isOverdue,
                voidReason = inv.voidReason,
                voidedAt = inv.voidedAt,
                payments = payments
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvoiceDetailUiState()
    )

    fun recordPayment(
        amountKhr: Long,
        method: String,
        date: Long,
        note: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.recordPayment(
                invoiceId = invoiceId,
                amountKhr = amountKhr,
                paymentDate = date,
                method = method,
                note = note
            )
            onResult(result)
        }
    }

    fun voidInvoice(invoiceId: Long, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.voidInvoice(invoiceId, reason)
            onResult(result)
        }
    }

    fun voidPayment(paymentId: Long, reason: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.voidPayment(paymentId, reason)
            onResult(result)
        }
    }

    fun exportPdf(onComplete: (Result<Uri>) -> Unit) {
        if (_isExporting.value) return
        _isExporting.value = true

        viewModelScope.launch {
            val result = repository.exportInvoicePdf(invoiceId)
            result.onSuccess { uri ->
                _lastExportedUri.value = uri
            }
            _isExporting.value = false
            onComplete(result)
        }
    }
}
