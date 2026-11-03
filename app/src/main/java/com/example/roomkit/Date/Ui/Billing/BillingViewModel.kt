//@file:Suppress("PackageNaming")
//package com.example.roomkit.Date.Ui.Billing
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.roomkit.Date.Repositories.BillingRepository
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.SharingStarted
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.combine
//import kotlinx.coroutines.flow.stateIn
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Date
//import java.util.Locale
//
//class BillingViewModel(
//    private val repository: BillingRepository
//) : ViewModel() {
//
//    private val currentCal = Calendar.getInstance()
//    private val selectedMonthFlow = MutableStateFlow(currentCal.get(Calendar.MONTH) + 1)
//    private val selectedYearFlow = MutableStateFlow(currentCal.get(Calendar.YEAR))
//
//    private val selectedBuildingIdFlow = MutableStateFlow<Long?>(null)
//    private val selectedFilterFlow = MutableStateFlow(BillingFilter.ALL)
//
//    private val monthYearFormatter = SimpleDateFormat("MMM yyyy", Locale.US)
//    private val dueDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
//    private val paymentDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
//
//    val uiState: StateFlow<BillingUiState> = combine(
//        repository.observeBillingData(),
//        selectedMonthFlow,
//        selectedYearFlow,
//        selectedBuildingIdFlow,
//        selectedFilterFlow
//    ) { raw, month, year, buildingId, filter ->
//
//        val cal = Calendar.getInstance().apply {
//            set(Calendar.DAY_OF_MONTH, 1)
//            set(Calendar.MONTH, month - 1)
//            set(Calendar.YEAR, year)
//        }
//        val monthYearFormatted = monthYearFormatter.format(cal.time)
//
//        // Lookup maps for fast relational queries
//        val buildingMap = raw.buildings.associateBy { it.buildingId }
//        val roomMap = raw.rooms.associateBy { it.roomId }
//        val tenancyMap = raw.tenancies.associateBy { it.tenancyId }
//        val tenantMap = raw.tenants.associateBy { it.tenantId }
//        val paymentsByInvoice = raw.payments.filter { it.status.trim().equals("ACTIVE", true) }.groupBy { it.invoiceId }
//
//        // Filter valid official invoices for this month & year
//        val validStatuses = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE", "VOID", "VOIDED", "DRAFT")
//
//        val scopedInvoices = raw.invoices.filter { inv ->
//            val tenancy = tenancyMap[inv.tenancyId]
//            val room = tenancy?.let { roomMap[it.roomId] }
//
//            val matchesBuilding = (buildingId == null || room?.buildingId == buildingId)
//            val matchesMonth = (inv.billingMonth == month && inv.billingYear == year)
//            val isOfficial = validStatuses.contains(inv.status.uppercase(Locale.US))
//
//            matchesBuilding && matchesMonth && isOfficial
//        }
//
//        var paidCount = 0
//        var partialCount = 0
//        var unpaidCount = 0
//        var overdueCount = 0
//
//        val todayTime = Date().time
//        val allProcessedItems = mutableListOf<BillingInvoiceItem>()
//
//        for (inv in scopedInvoices) {
//            val storedStatus = inv.status.trim().uppercase(Locale.ROOT)
//            val isVoided = storedStatus in setOf("VOID", "VOIDED")
//            val isDraft = storedStatus == "DRAFT"
//            val totalKhr = inv.totalKhr
//            val invoicePayments = paymentsByInvoice[inv.invoiceId] ?: emptyList()
//            val paidKhr = invoicePayments.sumOf { it.amountKhr }
//            val remainingKhr = if (isVoided || isDraft) 0L else
//                (totalKhr - paidKhr).coerceAtLeast(0L)
//            val isDuePassed = inv.dueDate < todayTime
//            val displayStatus = when {
//                isVoided -> BillingDisplayStatus.VOID
//                isDraft -> BillingDisplayStatus.DRAFT
//                remainingKhr == 0L -> { paidCount++; BillingDisplayStatus.PAID }
//                isDuePassed -> { overdueCount++; BillingDisplayStatus.OVERDUE }
//                paidKhr > 0L -> { partialCount++; BillingDisplayStatus.PARTIAL }
//                else -> { unpaidCount++; BillingDisplayStatus.UNPAID }
//            }
//
//            // Relationship resolution
//            val tenancy = tenancyMap[inv.tenancyId]
//            val room = tenancy?.let { roomMap[it.roomId] }
//            val building = room?.let { buildingMap[it.buildingId] }
//            val tenant = tenancy?.let { tenantMap[it.tenantId] }
//
//            val roomNum = room?.roomNumber ?: "Room"
//            val buildingName = building?.name ?: ""
//            val tenantName = tenant?.fullName ?: "Tenant"
//
//            val dueDateText = if (inv.dueDate != null) {
//                "Due ${dueDateFormat.format(Date(inv.dueDate))}"
//            } else ""
//
//            // Last payment details
//            val sortedPayments = invoicePayments.sortedByDescending { it.paymentDate }
//            val lastPayment = sortedPayments.firstOrNull()
//            val lastPaymentDateText = lastPayment?.let { paymentDateFormat.format(Date(it.paymentDate)) }
//            val lastPaymentMethod = lastPayment?.paymentMethod
//
//            allProcessedItems.add(
//                BillingInvoiceItem(
//                    invoiceId = inv.invoiceId,
//                    roomNumber = roomNum,
//                    buildingName = buildingName,
//                    tenantName = tenantName,
//                    billingPeriod = monthYearFormatted,
//                    dueDateText = dueDateText,
//                    invoiceTotalKhr = totalKhr,
//                    totalPaidKhr = paidKhr,
//                    remainingBalanceKhr = remainingKhr,
//                    status = displayStatus,
//                    lastPaymentDateText = lastPaymentDateText,
//                    lastPaymentMethodText = lastPaymentMethod,
//                    paymentCount = invoicePayments.size
//                )
//            )
//        }
//
//        // Apply Tab Filter to the list
//        val filteredList = when (filter) {
//            BillingFilter.ALL -> allProcessedItems
//            BillingFilter.PAID -> allProcessedItems.filter { it.status == BillingDisplayStatus.PAID }
//            BillingFilter.PARTIAL -> allProcessedItems.filter { it.status == BillingDisplayStatus.PARTIAL }
//            BillingFilter.UNPAID -> allProcessedItems.filter { it.status == BillingDisplayStatus.UNPAID }
//            BillingFilter.OVERDUE -> allProcessedItems.filter { it.status == BillingDisplayStatus.OVERDUE }
//        }
//
//        BillingUiState(
//            isLoading = false,
//            selectedMonth = month,
//            selectedYear = year,
//            monthYearFormatted = monthYearFormatted,
//            selectedBuildingId = buildingId,
//            selectedFilter = filter,
//            totalCount = scopedInvoices.size,
//            paidCount = paidCount,
//            partialCount = partialCount,
//            unpaidCount = unpaidCount,
//            overdueCount = overdueCount,
//            invoices = filteredList
//        )
//    }.stateIn(
//        viewModelScope,
//        SharingStarted.WhileSubscribed(5000),
//        BillingUiState()
//    )
//
//    //    fun onPreviousMonthClicked() {
////        val cal = Calendar.getInstance().apply {
////            set(Calendar.MONTH, selectedMonthFlow.value - 1)
////            set(Calendar.YEAR, selectedYearFlow.value)
////            add(Calendar.MONTH, -1)
////        }
////        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
////        selectedYearFlow.value = cal.get(Calendar.YEAR)
////    }
////
////    fun onNextMonthClicked() {
////        val cal = Calendar.getInstance().apply {
////            set(Calendar.MONTH, selectedMonthFlow.value - 1)
////            set(Calendar.YEAR, selectedYearFlow.value)
////            add(Calendar.MONTH, 1)
////        }
////        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
////        selectedYearFlow.value = cal.get(Calendar.YEAR)
////    }
//    fun onPreviousMonthClicked() {
//        changeMonth(-1)
//    }
//
//    fun onNextMonthClicked() {
//        changeMonth(1)
//    }
//
//    private fun changeMonth(offset: Int) {
//        val calendar = Calendar.getInstance().apply {
//            clear()
//            set(
//                selectedYearFlow.value,
//                selectedMonthFlow.value - 1,
//                1
//            )
//            add(Calendar.MONTH, offset)
//        }
//
//        selectedYearFlow.value = calendar.get(Calendar.YEAR)
//        selectedMonthFlow.value = calendar.get(Calendar.MONTH) + 1
//    }
//
//    fun onFilterSelected(filter: BillingFilter) {
//        selectedFilterFlow.value = filter
//    }
//
//    fun onBuildingSelected(buildingId: Long?) {
//        selectedBuildingIdFlow.value = buildingId
//    }
//}
@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.DocumentStatus
import com.example.roomkit.Date.Billing.PaymentStatus
import com.example.roomkit.Date.Billing.invoiceStatusClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BillingViewModel(
    private val repository: BillingRepository
) : ViewModel() {

    private val currentCal = Calendar.getInstance()
    private val selectedMonthFlow = MutableStateFlow(currentCal.get(Calendar.MONTH) + 1)
    private val selectedYearFlow = MutableStateFlow(currentCal.get(Calendar.YEAR))

    private val selectedBuildingIdFlow = MutableStateFlow<Long?>(null)
    private val selectedFilterFlow = MutableStateFlow(BillingFilter.ALL)

    private val monthYearFormatter = SimpleDateFormat("MMM yyyy", Locale.US)
    private val dueDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val paymentDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    val uiState: StateFlow<BillingUiState> = combine(
        combine(repository.observeBillingData(), invoiceStatusClock()) { raw, now -> raw to now },
        selectedMonthFlow,
        selectedYearFlow,
        selectedBuildingIdFlow,
        selectedFilterFlow
    ) { timedData, month, year, buildingId, filter ->
        val (raw, now) = timedData

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, month - 1)
            set(Calendar.YEAR, year)
        }
        val monthYearFormatted = monthYearFormatter.format(cal.time)

        // Lookup maps for fast relational queries
        val buildingMap = raw.buildings.associateBy { it.buildingId }
        val roomMap = raw.rooms.associateBy { it.roomId }
        val tenancyMap = raw.tenancies.associateBy { it.tenancyId }
        val tenantMap = raw.tenants.associateBy { it.tenantId }
        val paymentsByInvoice = raw.payments.filter { it.status.trim().equals("ACTIVE", true) }.groupBy { it.invoiceId }

        // Filter valid official invoices for this month & year
        val validStatuses = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE", "VOID", "VOIDED", "DRAFT")

        val scopedInvoices = raw.invoices.filter { inv ->
            val tenancy = tenancyMap[inv.tenancyId]
            val room = tenancy?.let { roomMap[it.roomId] }

            val matchesBuilding = (buildingId == null || room?.buildingId == buildingId)
            val matchesMonth = (inv.billingMonth == month && inv.billingYear == year)
            val isOfficial = validStatuses.contains(inv.status.trim().uppercase(Locale.ROOT))

            matchesBuilding && matchesMonth && isOfficial
        }

        var paidCount = 0
        var partialCount = 0
        var unpaidCount = 0
        var overdueCount = 0

        val allProcessedItems = mutableListOf<BillingInvoiceItem>()

        for (inv in scopedInvoices) {
            val totalKhr = inv.totalKhr
            val invoicePayments = paymentsByInvoice[inv.invoiceId] ?: emptyList()
            val paidKhr = invoicePayments.sumOf { it.amountKhr }
            val status = InvoiceStatusResolver.resolve(inv.status, totalKhr, paidKhr, inv.dueDate, now)
            val remainingKhr = status.remainingKhr
            if (status.isOverdue) overdueCount++
            val displayStatus = when (status.documentStatus) {
                DocumentStatus.VOID -> BillingDisplayStatus.VOID
                DocumentStatus.DRAFT -> BillingDisplayStatus.DRAFT
                else -> when (status.paymentStatus) {
                    PaymentStatus.PAID -> { paidCount++; BillingDisplayStatus.PAID }
                    PaymentStatus.PARTIALLY_PAID -> { partialCount++; BillingDisplayStatus.PARTIAL }
                    else -> { unpaidCount++; BillingDisplayStatus.UNPAID }
                }
            }

            // Relationship resolution
            val tenancy = tenancyMap[inv.tenancyId]
            val room = tenancy?.let { roomMap[it.roomId] }
            val building = room?.let { buildingMap[it.buildingId] }
            val tenant = tenancy?.let { tenantMap[it.tenantId] }

            val roomNum = room?.roomNumber ?: "Room"
            val buildingName = building?.name ?: ""
            val tenantName = tenant?.fullName ?: "Tenant"

            val dueDateText = if (inv.dueDate != null) {
                "Due ${dueDateFormat.format(Date(inv.dueDate))}"
            } else ""

            // Last payment details
            val sortedPayments = invoicePayments.sortedByDescending { it.paymentDate }
            val lastPayment = sortedPayments.firstOrNull()
            val lastPaymentDateText = lastPayment?.let { paymentDateFormat.format(Date(it.paymentDate)) }
            val lastPaymentMethod = lastPayment?.paymentMethod

            allProcessedItems.add(
                BillingInvoiceItem(
                    invoiceId = inv.invoiceId,
                    roomNumber = roomNum,
                    buildingName = buildingName,
                    tenantName = tenantName,
                    billingPeriod = monthYearFormatted,
                    dueDateText = dueDateText,
                    invoiceTotalKhr = totalKhr,
                    totalPaidKhr = paidKhr,
                    remainingBalanceKhr = remainingKhr,
                    status = displayStatus,
                    documentStatus = status.documentStatus,
                    paymentStatus = status.paymentStatus,
                    isOverdue = status.isOverdue,
                    lastPaymentDateText = lastPaymentDateText,
                    lastPaymentMethodText = lastPaymentMethod,
                    paymentCount = invoicePayments.size
                )
            )
        }

        // Apply Tab Filter to the list
        val filteredList = when (filter) {
            BillingFilter.ALL -> allProcessedItems
            BillingFilter.PAID -> allProcessedItems.filter { it.paymentStatus == PaymentStatus.PAID }
            BillingFilter.PARTIAL -> allProcessedItems.filter { it.paymentStatus == PaymentStatus.PARTIALLY_PAID }
            BillingFilter.UNPAID -> allProcessedItems.filter { it.paymentStatus == PaymentStatus.UNPAID }
            BillingFilter.OVERDUE -> allProcessedItems.filter { it.isOverdue }
        }

        BillingUiState(
            isLoading = false,
            selectedMonth = month,
            selectedYear = year,
            monthYearFormatted = monthYearFormatted,
            selectedBuildingId = buildingId,
            selectedFilter = filter,
            totalCount = scopedInvoices.size,
            paidCount = paidCount,
            partialCount = partialCount,
            unpaidCount = unpaidCount,
            overdueCount = overdueCount,
            invoices = filteredList
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BillingUiState()
    )

    //    fun onPreviousMonthClicked() {
//        val cal = Calendar.getInstance().apply {
//            set(Calendar.MONTH, selectedMonthFlow.value - 1)
//            set(Calendar.YEAR, selectedYearFlow.value)
//            add(Calendar.MONTH, -1)
//        }
//        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
//        selectedYearFlow.value = cal.get(Calendar.YEAR)
//    }
//
//    fun onNextMonthClicked() {
//        val cal = Calendar.getInstance().apply {
//            set(Calendar.MONTH, selectedMonthFlow.value - 1)
//            set(Calendar.YEAR, selectedYearFlow.value)
//            add(Calendar.MONTH, 1)
//        }
//        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
//        selectedYearFlow.value = cal.get(Calendar.YEAR)
//    }
    fun onPreviousMonthClicked() {
        changeMonth(-1)
    }

    fun onNextMonthClicked() {
        changeMonth(1)
    }

    private fun changeMonth(offset: Int) {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(
                selectedYearFlow.value,
                selectedMonthFlow.value - 1,
                1
            )
            add(Calendar.MONTH, offset)
        }

        selectedYearFlow.value = calendar.get(Calendar.YEAR)
        selectedMonthFlow.value = calendar.get(Calendar.MONTH) + 1
    }

    fun onFilterSelected(filter: BillingFilter) {
        selectedFilterFlow.value = filter
    }

    fun onBuildingSelected(buildingId: Long?) {
        selectedBuildingIdFlow.value = buildingId
    }
}
