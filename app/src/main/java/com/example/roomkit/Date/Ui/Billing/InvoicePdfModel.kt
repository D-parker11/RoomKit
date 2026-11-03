package com.example.roomkit.Date.Billing

data class PaymentPdfItem(
    val paymentId: Long,
    val paymentDateFormatted: String,
    val method: String,
    val note: String?,
    val amountKhr: Long,
    val referenceNumber: String? = null
)

data class InvoicePdfModel(
    val invoiceId: Long,
    val currency: String = "KHR",
    val discountReason: String? = null,
    val invoiceNumberFormatted: String,
    val billingPeriodFormatted: String,
    val status: String,
    val issueDateFormatted: String,
    val dueDateFormatted: String,

    val tenantName: String,
    val tenantPhone: String,
    val buildingName: String,
    val roomNumber: String,

    // Financial Snapshots (From InvoiceEntity)
    val rentKhr: Long,
    val electricityUsage: Long,
    val electricityRateKhr: Long,
    val electricityChargeKhr: Long,

    val isWaterMetered: Boolean,
    val waterUsage: Long?,
    val waterRateKhr: Long?,
    val waterChargeKhr: Long,

    val rubbishFeeKhr: Long,
    val utilitySubtotalKhr: Long,

    val totalKhr: Long,
    val amountPaidKhr: Long,
    val balanceKhr: Long,
    val accountBalance: InvoiceAccountBalance,

    val otherChargesKhr: Long = 0L,
    val discountKhr: Long = 0L,
    val billingPeriodDates: String? = null,
    val items: List<com.example.roomkit.Date.Local.Entity.InvoiceItemEntity>,
    val payments: List<PaymentPdfItem>
)