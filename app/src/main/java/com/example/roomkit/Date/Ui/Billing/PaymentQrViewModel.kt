//package com.example.roomkit.Date.Ui.Billing
//
//import android.content.Context
//import android.graphics.Bitmap
//import android.net.Uri
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.roomkit.Date.Billing.PaymentQrImageGenerator
//import com.example.roomkit.Date.Repositories.BillingRepository
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.combine
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Locale
//
//class PaymentQrViewModel(
//    private val invoiceId: Long,
//    private val repository: BillingRepository
//) : ViewModel() {
//
//    private val _uiState = MutableStateFlow(PaymentQrUiState())
//    val uiState: StateFlow<PaymentQrUiState> = _uiState.asStateFlow()
//
//    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)
//    private var cachedCardBitmap: Bitmap? = null
//
//    init {
//        loadPaymentQrData()
//    }
//
//    private fun loadPaymentQrData() {
//        viewModelScope.launch {
//            combine(
//                repository.observeInvoiceDetail(invoiceId),
//                repository.observePayments(invoiceId)
//            ) { detail, payments ->
//                Pair(detail, payments)
//            }.collect { (detail, payments) ->
//                if (detail == null) {
//                    _uiState.value = PaymentQrUiState(
//                        isLoading = false,
//                        errorMessage = "Invoice not found."
//                    )
//                    return@collect
//                }
//
//                val inv = detail.invoice
//
//                // Source of truth: Total paid = sum of recorded PaymentEntities
//                val totalPaidKhr = payments.sumOf { it.amountKhr }
//                val remainingBalanceKhr = (inv.totalKhr - totalPaidKhr).coerceAtLeast(0L)
//
//                val cal = Calendar.getInstance().apply {
//                    set(Calendar.DAY_OF_MONTH, 1)
//                    set(Calendar.MONTH, inv.billingMonth - 1)
//                    set(Calendar.YEAR, inv.billingYear)
//                }
//                val periodFormatted = monthYearFormat.format(cal.time)
//                val invoiceNumFormatted = String.format(Locale.US, "#INV-%04d", inv.invoiceId)
//
//                // Guard checks
//                if (inv.status.uppercase() == "VOID") {
//                    _uiState.value = PaymentQrUiState(
//                        isLoading = false,
//                        errorMessage = "Payment QR is unavailable because this invoice is VOID.",
//                        isPayable = false
//                    )
//                    return@collect
//                }
//
//                if (inv.status.uppercase() == "DRAFT") {
//                    _uiState.value = PaymentQrUiState(
//                        isLoading = false,
//                        errorMessage = "Payment QR cannot be generated for DRAFT invoices. Please issue the invoice first.",
//                        isPayable = false
//                    )
//                    return@collect
//                }
//
//                if (remainingBalanceKhr <= 0L || inv.status.uppercase() == "PAID") {
//                    _uiState.value = PaymentQrUiState(
//                        isLoading = false,
//                        invoiceId = inv.invoiceId,
//                        invoiceNumberFormatted = invoiceNumFormatted,
//                        billingPeriodFormatted = periodFormatted,
//                        tenantName = detail.tenantName,
//                        roomNumber = detail.roomNumber,
//                        invoiceTotalKhr = inv.totalKhr,
//                        totalPaidKhr = totalPaidKhr,
//                        remainingBalanceKhr = 0L,
//                        status = "PAID",
//                        isPayable = false,
//                        errorMessage = "This invoice is already paid in full."
//                    )
//                    return@collect
//                }
//
//                // Invalidate cached card when balance changes
//                if (_uiState.value.remainingBalanceKhr != remainingBalanceKhr) {
//                    cachedCardBitmap = null
//                }
//
//                // Preserve previously set custom bank QR bitmap
//                val currentQrBitmap = _uiState.value.qrBitmap
//
//                _uiState.value = PaymentQrUiState(
//                    isLoading = false,
//                    invoiceId = inv.invoiceId,
//                    invoiceNumberFormatted = invoiceNumFormatted,
//                    billingPeriodFormatted = periodFormatted,
//                    tenantName = detail.tenantName,
//                    tenantPhone = detail.tenantPhone,
//                    roomNumber = detail.roomNumber,
//                    buildingName = detail.buildingName,
//                    invoiceTotalKhr = inv.totalKhr,
//                    totalPaidKhr = totalPaidKhr,
//                    remainingBalanceKhr = remainingBalanceKhr,
//                    status = inv.status,
//                    qrBitmap = currentQrBitmap,
//                    isPayable = true
//                )
//            }
//        }
//    }
//
//    fun setCustomBankQrBitmap(bitmap: Bitmap) {
//        cachedCardBitmap = null
//        _uiState.value = _uiState.value.copy(qrBitmap = bitmap)
//    }
//
//    private fun getOrCreateCardBitmap(): Bitmap? {
//        val s = _uiState.value
//        val qr = s.qrBitmap ?: return null
//        if (cachedCardBitmap == null) {
//            cachedCardBitmap = PaymentQrImageGenerator.createPaymentCardBitmap(
//                qrBitmap = qr,
//                roomNumber = s.roomNumber,
//                tenantName = s.tenantName,
//                periodFormatted = s.billingPeriodFormatted,
//                invoiceNumberFormatted = s.invoiceNumberFormatted,
//                remainingBalanceKhr = s.remainingBalanceKhr
//            )
//        }
//        return cachedCardBitmap
//    }
//
//    fun prepareShareImageUri(context: Context): Uri? {
//        val cardBitmap = getOrCreateCardBitmap() ?: return null
//        val fileName = "RoomKit_QR_${_uiState.value.roomNumber}_${_uiState.value.billingPeriodFormatted.replace(" ", "_")}"
//        return PaymentQrImageGenerator.saveToCacheForSharing(context, cardBitmap, fileName)
//    }
//
//    fun saveImageToGallery(context: Context, onResult: (Result<Uri>) -> Unit) {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val cardBitmap = getOrCreateCardBitmap()
//                    ?: throw IllegalStateException("QR image not ready")
//                val fileName = "RoomKit_QR_Invoice_${_uiState.value.invoiceId}_${_uiState.value.billingPeriodFormatted.replace(" ", "_")}"
//                val uri = PaymentQrImageGenerator.saveToPicturesGallery(context, cardBitmap, fileName)
//                withContext(Dispatchers.Main) { onResult(Result.success(uri)) }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
//            }
//        }
//    }
//}

package com.example.roomkit.Date.Ui.Billing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Billing.PaymentQrImageGenerator
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PaymentQrViewModel(
    private val invoiceId: Long,
    private val repository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentQrUiState())
    val uiState: StateFlow<PaymentQrUiState> = _uiState.asStateFlow()

    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)
    private var cachedCardBitmap: Bitmap? = null

    init {
        loadPaymentQrData()
    }

    private fun loadPaymentQrData() {
        viewModelScope.launch {
            combine(
                repository.observeInvoiceDetail(invoiceId),
                repository.observePayments(invoiceId)
            ) { detail, payments ->
                Pair(detail, payments)
            }.collect { (detail, payments) ->
                if (detail == null) {
                    _uiState.value = PaymentQrUiState(
                        isLoading = false,
                        errorMessage = "Invoice not found."
                    )
                    return@collect
                }

                val inv = detail.invoice

                // Source of truth: Total paid = sum of recorded PaymentEntities
                val totalPaidKhr = payments.filter { it.status == "ACTIVE" }.sumOf { it.amountKhr }
                val remainingBalanceKhr = (inv.totalKhr - totalPaidKhr).coerceAtLeast(0L)

                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.MONTH, inv.billingMonth - 1)
                    set(Calendar.YEAR, inv.billingYear)
                }
                val periodFormatted = monthYearFormat.format(cal.time)
                val invoiceNumFormatted = String.format(Locale.US, "#INV-%04d", inv.invoiceId)

                // Guard checks
                if (inv.status.uppercase() == "VOID") {
                    _uiState.value = PaymentQrUiState(
                        isLoading = false,
                        errorMessage = "Payment QR is unavailable because this invoice is VOID.",
                        isPayable = false
                    )
                    return@collect
                }

                if (inv.status.uppercase() == "DRAFT") {
                    _uiState.value = PaymentQrUiState(
                        isLoading = false,
                        errorMessage = "Payment QR cannot be generated for DRAFT invoices. Please issue the invoice first.",
                        isPayable = false
                    )
                    return@collect
                }

                if (remainingBalanceKhr <= 0L || inv.status.uppercase() == "PAID") {
                    _uiState.value = PaymentQrUiState(
                        isLoading = false,
                        invoiceId = inv.invoiceId,
                        invoiceNumberFormatted = invoiceNumFormatted,
                        billingPeriodFormatted = periodFormatted,
                        tenantName = detail.tenantName,
                        roomNumber = detail.roomNumber,
                        invoiceTotalKhr = inv.totalKhr,
                        totalPaidKhr = totalPaidKhr,
                        remainingBalanceKhr = 0L,
                        status = "PAID",
                        isPayable = false,
                        errorMessage = "This invoice is already paid in full."
                    )
                    return@collect
                }

                // Invalidate cached card when balance changes
                if (_uiState.value.remainingBalanceKhr != remainingBalanceKhr) {
                    cachedCardBitmap = null
                }

                // Preserve previously set custom bank QR bitmap
                val currentQrBitmap = _uiState.value.qrBitmap

                _uiState.value = PaymentQrUiState(
                    isLoading = false,
                    invoiceId = inv.invoiceId,
                    invoiceNumberFormatted = invoiceNumFormatted,
                    billingPeriodFormatted = periodFormatted,
                    tenantName = detail.tenantName,
                    tenantPhone = detail.tenantPhone,
                    roomNumber = detail.roomNumber,
                    buildingName = detail.buildingName,
                    invoiceTotalKhr = inv.totalKhr,
                    totalPaidKhr = totalPaidKhr,
                    remainingBalanceKhr = remainingBalanceKhr,
                    status = inv.status,
                    qrBitmap = currentQrBitmap,
                    isPayable = true
                )
            }
        }
    }

    fun setCustomBankQrBitmap(bitmap: Bitmap) {
        cachedCardBitmap = null
        _uiState.value = _uiState.value.copy(qrBitmap = bitmap)
    }

    private fun getOrCreateCardBitmap(): Bitmap? {
        val s = _uiState.value
        val qr = s.qrBitmap ?: return null
        if (cachedCardBitmap == null) {
            cachedCardBitmap = PaymentQrImageGenerator.createPaymentCardBitmap(
                qrBitmap = qr,
                roomNumber = s.roomNumber,
                tenantName = s.tenantName,
                periodFormatted = s.billingPeriodFormatted,
                invoiceNumberFormatted = s.invoiceNumberFormatted,
                remainingBalanceKhr = s.remainingBalanceKhr
            )
        }
        return cachedCardBitmap
    }

    fun prepareShareImageUri(context: Context): Uri? {
        val cardBitmap = getOrCreateCardBitmap() ?: return null
        val fileName = "RoomKit_QR_${_uiState.value.roomNumber}_${_uiState.value.billingPeriodFormatted.replace(" ", "_")}"
        return PaymentQrImageGenerator.saveToCacheForSharing(context, cardBitmap, fileName)
    }

    fun saveImageToGallery(context: Context, onResult: (Result<Uri>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cardBitmap = getOrCreateCardBitmap()
                    ?: throw IllegalStateException("QR image not ready")
                val fileName = "RoomKit_QR_Invoice_${_uiState.value.invoiceId}_${_uiState.value.billingPeriodFormatted.replace(" ", "_")}"
                val uri = PaymentQrImageGenerator.saveToPicturesGallery(context, cardBitmap, fileName)
                withContext(Dispatchers.Main) { onResult(Result.success(uri)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }
}