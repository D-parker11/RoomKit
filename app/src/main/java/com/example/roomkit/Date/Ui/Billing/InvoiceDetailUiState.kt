package com.example.roomkit.Date.Ui.Billing

import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.DocumentStatus
import com.example.roomkit.Date.Billing.PaymentStatus
import com.example.roomkit.Date.Local.Entity.PaymentEntity

data class InvoiceDetailUiState(
    val items: List<com.example.roomkit.Date.Local.Entity.InvoiceItemEntity> = emptyList(),
    val replacesInvoiceId: Long? = null,
    val isLoading: Boolean = true,
    val invoiceId: Long = 0L,
    val currency: String = "KHR",
    val discountReason: String? = null,
    val invoiceNumberFormatted: String = "#0001",
    val billingPeriodFormatted: String = "Sep 2026",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val roomNumber: String = "",
    val buildingName: String = "",

    // Line items
    val rentKhr: Long = 0L,
    val electricityUsage: Long = 0L,
    val electricityRateKhr: Long = 0L,
    val electricityChargeKhr: Long = 0L,

    val isWaterMetered: Boolean = true,
    val waterUsage: Long = 0L,
    val waterRateKhr: Long = 0L,
    val waterChargeKhr: Long = 0L,

    val rubbishFeeKhr: Long = 0L,
    val utilitySubtotalKhr: Long = 0L,

    // Totals
    val totalKhr: Long = 0L,
    val amountPaidKhr: Long = 0L,
    val balanceKhr: Long = 0L, // Current invoice only; payment and KHQR actions use this.
    val previousUnpaidKhr: Long = 0L,
    val laterUnpaidKhr: Long = 0L,
    val totalAccountBalanceKhr: Long = 0L,
    val dueDateFormatted: String = "",
    val dueDateTimestamp: Long = 0L,
    val status: String = "ISSUED",

    val payments: List<PaymentEntity> = emptyList(),
    val errorMessage: String? = null,
    val voidReason: String? = null,
    val voidedAt: Long? = null,
    val documentStatus: DocumentStatus = DocumentStatus.UNKNOWN,
    val paymentStatus: PaymentStatus = PaymentStatus.NOT_APPLICABLE,
    val isOverdue: Boolean = false,
    val otherChargesKhr: Long = 0L,
    val discountKhr: Long = 0L,
    val billingPeriodDates: String? = null
)
