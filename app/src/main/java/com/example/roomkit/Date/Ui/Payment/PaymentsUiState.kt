//@file:Suppress("PackageNaming")
//package com.example.roomkit.Date.Ui.Payment
//
//import java.util.Calendar
//
//enum class PaymentFilter {
//    ALL,
//    PAID,
//    PARTIAL,
//    OVERDUE
//}
//
//sealed class PaymentActivityItem {
//    abstract val id: String
//    abstract val timestamp: Long
//
//    data class PaymentRecord(
//        val paymentId: Long,
//        val invoiceId: Long,
//        val tenantName: String,
//        val roomNumber: String,
//        val buildingName: String,
//        val paymentDateFormatted: String,
//        override val timestamp: Long,
//        val paymentAmountKhr: Long,
//        val paymentMethod: String,
//        val invoiceTotalKhr: Long,
//        val remainingBalanceKhr: Long,
//        val isFullyPaid: Boolean
//    ) : PaymentActivityItem() {
//        override val id: String = "payment_$paymentId"
//    }
//
//    data class OutstandingInvoice(
//        val invoiceId: Long,
//        val tenantName: String,
//        val roomNumber: String,
//        val buildingName: String,
//        val dueDateFormatted: String,
//        override val timestamp: Long,
//        val invoiceTotalKhr: Long,
//        val totalPaidKhr: Long,
//        val remainingBalanceKhr: Long,
//        val isOverdue: Boolean
//    ) : PaymentActivityItem() {
//        override val id: String = "invoice_$invoiceId"
//    }
//}
//
//data class PaymentsUiState(
//    val isLoading: Boolean = true,
//    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
//    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
//    val monthYearFormatted: String = "",
//    val selectedFilter: PaymentFilter = PaymentFilter.ALL,
//    val searchQuery: String = "",
//    val collectedKhr: Long = 0L,
//    val outstandingKhr: Long = 0L,
//    val overdueKhr: Long = 0L,
//    val items: List<PaymentActivityItem> = emptyList(),
//    val errorMessage: String? = null
//)

@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import java.util.Calendar

enum class PaymentFilter {
    ALL,
    PAID,
    PARTIAL,
    OVERDUE
}

sealed class PaymentActivityItem {
    abstract val id: String
    abstract val timestamp: Long

    data class PaymentRecord(
        val paymentId: Long,
        val invoiceId: Long,
        val tenantName: String,
        val roomNumber: String,
        val buildingName: String,
        val paymentDateFormatted: String,
        override val timestamp: Long,
        val paymentAmountKhr: Long,
        val paymentMethod: String,
        val invoiceTotalKhr: Long,
        val remainingBalanceKhr: Long,
        val isFullyPaid: Boolean,
        val isVoided: Boolean = false
    ) : PaymentActivityItem() {
        override val id: String = "payment_$paymentId"
    }

    data class OutstandingInvoice(
        val invoiceId: Long,
        val tenantName: String,
        val roomNumber: String,
        val buildingName: String,
        val dueDateFormatted: String,
        override val timestamp: Long,
        val invoiceTotalKhr: Long,
        val totalPaidKhr: Long,
        val remainingBalanceKhr: Long,
        val isOverdue: Boolean
    ) : PaymentActivityItem() {
        override val id: String = "invoice_$invoiceId"
    }
}

data class PaymentsUiState(
    val isLoading: Boolean = true,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthYearFormatted: String = "",
    val selectedFilter: PaymentFilter = PaymentFilter.ALL,
    val searchQuery: String = "",
    val collectedKhr: Long = 0L,
    val outstandingKhr: Long = 0L,
    val overdueKhr: Long = 0L,
    val items: List<PaymentActivityItem> = emptyList(),
    val errorMessage: String? = null
)