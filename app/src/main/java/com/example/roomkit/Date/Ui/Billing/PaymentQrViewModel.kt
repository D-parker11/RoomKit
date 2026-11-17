package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Payment.KhqrImages
import com.example.roomkit.Date.Payment.PaymentSettingsRepository
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class PaymentQrViewModel(
    private val invoiceId: Long,
    private val repository: BillingRepository,
    private val paymentSettings: PaymentSettingsRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(PaymentQrUiState())
    val uiState = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                combine(repository.observeInvoiceDetail(invoiceId), repository.observePayments(invoiceId), paymentSettings.observe()) {
                        detail, payments, qr -> Triple(detail, payments, qr)
                }.collectLatest { (detail, payments, qr) ->
                    // Clear the previous image immediately while the next state is rendered.
                    mutable.value = PaymentQrUiState()
                    if (detail == null) {
                        mutable.value = PaymentQrUiState(isLoading = false, errorMessage = "Invoice not found.")
                        return@collectLatest
                    }
                    val inv = detail.invoice
                    val paid = payments.filter { it.status.trim().equals("ACTIVE", true) }
                        .fold(0L) { total, p -> Math.addExact(total, p.amountKhr) }
                    val balance = Math.subtractExact(inv.totalKhr, paid).coerceAtLeast(0L)
                    val status = inv.status.trim().uppercase(Locale.ROOT)
                    val allowed = status in setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE")
                    val error = when {
                        inv.currency != "KHR" -> "Only KHR invoices are supported."
                        !allowed -> "Payment QR is unavailable for this invoice status."
                        balance == 0L -> "This invoice is paid in full."
                        qr == null -> "Set up your receiving KHQR before sharing a payment request."
                        else -> null
                    }
                    val image = if (error == null && qr != null) withContext(Dispatchers.Default) {
                        KhqrImages.render(qr.payload)
                    } else null
                    mutable.value = PaymentQrUiState(
                        isLoading = false, invoiceId = inv.invoiceId,
                        invoiceNumberFormatted = String.format(Locale.US, "#INV-%04d", inv.invoiceId),
                        billingPeriodFormatted = YearMonth.of(inv.billingYear, inv.billingMonth)
                            .format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
                        tenantName = detail.tenantName, tenantPhone = detail.tenantPhone,
                        roomNumber = detail.roomNumber, buildingName = detail.buildingName,
                        invoiceTotalKhr = inv.totalKhr, totalPaidKhr = paid, remainingBalanceKhr = balance,
                        status = status, qrBitmap = image, receiverName = qr?.receiver.orEmpty(),
                        accountId = qr?.accountId.orEmpty(), isConfigured = qr != null,
                        isPayable = error == null, errorMessage = error
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = PaymentQrUiState(isLoading = false, errorMessage = e.message ?: "Unable to load payment QR.")
            }
        }
    }
}
