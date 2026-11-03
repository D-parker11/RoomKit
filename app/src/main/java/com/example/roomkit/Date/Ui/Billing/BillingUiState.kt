@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.DocumentStatus
import com.example.roomkit.Date.Billing.PaymentStatus
import java.util.Calendar

enum class BillingDisplayStatus {
    PAID,
    PARTIAL,
    UNPAID,
    OVERDUE,
    VOID,
    DRAFT
}

enum class BillingFilter {
    ALL,
    PAID,
    PARTIAL,
    UNPAID,
    OVERDUE
}

data class BillingInvoiceItem(
    val invoiceId: Long,
    val roomNumber: String,
    val buildingName: String,
    val tenantName: String,
    val billingPeriod: String,
    val dueDateText: String,

    val invoiceTotalKhr: Long,
    val totalPaidKhr: Long,
    val remainingBalanceKhr: Long,

    val status: BillingDisplayStatus,

    val lastPaymentDateText: String?,
    val lastPaymentMethodText: String?,
    val documentStatus: DocumentStatus = DocumentStatus.UNKNOWN,
    val paymentStatus: PaymentStatus = PaymentStatus.NOT_APPLICABLE,
    val isOverdue: Boolean = false,
    val paymentCount: Int
)

data class BillingUiState(
    val isLoading: Boolean = true,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthYearFormatted: String = "",

    val selectedBuildingId: Long? = null,
    val selectedFilter: BillingFilter = BillingFilter.ALL,

    // Summary counts
    val totalCount: Int = 0,
    val paidCount: Int = 0,
    val partialCount: Int = 0,
    val unpaidCount: Int = 0,
    val overdueCount: Int = 0,

    val invoices: List<BillingInvoiceItem> = emptyList(),
    val errorMessage: String? = null
)
