package com.example.roomkit.Date.Ui.Billing

import android.graphics.Bitmap

data class PaymentQrUiState(
    val isLoading: Boolean = true,
    val invoiceId: Long = 0L,
    val invoiceNumberFormatted: String = "",
    val billingPeriodFormatted: String = "",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val roomNumber: String = "",
    val buildingName: String = "",
    val invoiceTotalKhr: Long = 0L,
    val totalPaidKhr: Long = 0L,
    val remainingBalanceKhr: Long = 0L,
    val status: String = "ISSUED",
    val qrBitmap: Bitmap? = null,
    val isPayable: Boolean = true,
    val errorMessage: String? = null
)