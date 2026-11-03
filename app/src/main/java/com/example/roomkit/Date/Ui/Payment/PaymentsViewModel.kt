@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PaymentsViewModel(
    private val repository: BillingRepository
) : ViewModel() {

    private val currentCal = Calendar.getInstance()
    private val selectedMonthFlow = MutableStateFlow(currentCal.get(Calendar.MONTH) + 1)
    private val selectedYearFlow = MutableStateFlow(currentCal.get(Calendar.YEAR))
    private val selectedFilterFlow = MutableStateFlow(PaymentFilter.ALL)
    private val searchQueryFlow = MutableStateFlow("")

    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val itemDateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // Debounce search query so user typing doesn't stutter the UI
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedSearchFlow = searchQueryFlow
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val uiState: StateFlow<PaymentsUiState> = combine(
        repository.observeBillingData(),
        selectedMonthFlow,
        selectedYearFlow,
        selectedFilterFlow,
        debouncedSearchFlow
    ) { raw, month, year, filter, query ->

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, month - 1)
            set(Calendar.YEAR, year)
        }
        val monthYearFormatted = monthYearFormatter.format(cal.time)

        val buildingMap = raw.buildings.associateBy { it.buildingId }
        val roomMap = raw.rooms.associateBy { it.roomId }
        val tenancyMap = raw.tenancies.associateBy { it.tenancyId }
        val tenantMap = raw.tenants.associateBy { it.tenantId }
        val paymentsByInvoice = raw.payments.filter { it.status == "ACTIVE" }.groupBy { it.invoiceId }

        val todayTime = Date().time

        // 1. Filter official Invoices scoped to selected month
        val validStatuses = setOf("ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE")
        val scopedInvoices = raw.invoices.filter { inv ->
            inv.billingMonth == month && inv.billingYear == year && validStatuses.contains(inv.status.uppercase(Locale.US))
        }

        // 2. Compute Summary Metrics
        var collectedKhr = 0L
        var outstandingKhr = 0L
        var overdueKhr = 0L

        // Collected = sum of all payment records belonging to invoices of this month
        scopedInvoices.forEach { inv ->
            val invoicePayments = paymentsByInvoice[inv.invoiceId] ?: emptyList()
            val totalPaid = invoicePayments.sumOf { it.amountKhr }
            val remaining = (inv.totalKhr - totalPaid).coerceAtLeast(0L)
            val isOverdue = inv.dueDate != null && inv.dueDate < todayTime

            collectedKhr += totalPaid
            outstandingKhr += remaining
            if (isOverdue && remaining > 0L) {
                overdueKhr += remaining
            }
        }

        // 3. Build Activity Items: Payment receipts + Unpaid/Overdue invoices
        val activityList = mutableListOf<PaymentActivityItem>()

        // A. Add Payment Records
        for (inv in scopedInvoices) {
            val tenancy = tenancyMap[inv.tenancyId]
            val room = tenancy?.let { roomMap[it.roomId] }
            val building = room?.let { buildingMap[it.buildingId] }
            val tenant = tenancy?.let { tenantMap[it.tenantId] }

            val roomNum = room?.roomNumber ?: "Room"
            val bName = building?.name ?: ""
            val tName = tenant?.fullName ?: "Tenant"

            val invoicePayments = paymentsByInvoice[inv.invoiceId] ?: emptyList()
            val totalPaid = invoicePayments.sumOf { it.amountKhr }
            val remaining = (inv.totalKhr - totalPaid).coerceAtLeast(0L)

            raw.payments.filter { it.invoiceId == inv.invoiceId }.forEach { p ->
                activityList.add(
                    PaymentActivityItem.PaymentRecord(
                        paymentId = p.paymentId,
                        invoiceId = inv.invoiceId,
                        tenantName = tName,
                        roomNumber = roomNum,
                        buildingName = bName,
                        paymentDateFormatted = itemDateFormatter.format(Date(p.paymentDate)),
                        timestamp = p.paymentDate,
                        paymentAmountKhr = p.amountKhr,
                        paymentMethod = p.paymentMethod,
                        invoiceTotalKhr = inv.totalKhr,
                        remainingBalanceKhr = remaining,
                        isFullyPaid = remaining == 0L,
                        isVoided = p.status == "VOIDED"
                    )
                )
            }

            // B. Add Outstanding / Overdue invoices still needing collection
            if (remaining > 0L) {
                val isDuePassed = inv.dueDate != null && inv.dueDate < todayTime
                activityList.add(
                    PaymentActivityItem.OutstandingInvoice(
                        invoiceId = inv.invoiceId,
                        tenantName = tName,
                        roomNumber = roomNum,
                        buildingName = bName,
                        dueDateFormatted = if (inv.dueDate != null) "Due ${itemDateFormatter.format(Date(inv.dueDate))}" else "",
                        timestamp = inv.dueDate ?: inv.issueDate,
                        invoiceTotalKhr = inv.totalKhr,
                        totalPaidKhr = totalPaid,
                        remainingBalanceKhr = remaining,
                        isOverdue = isDuePassed
                    )
                )
            }
        }

        // 4. Filter by Tab selection
        val tabFiltered = when (filter) {
            PaymentFilter.ALL -> activityList
            PaymentFilter.PAID -> activityList.filter { it is PaymentActivityItem.PaymentRecord && !it.isVoided && it.isFullyPaid }
            PaymentFilter.PARTIAL -> activityList.filter {
                (it is PaymentActivityItem.PaymentRecord && !it.isVoided && !it.isFullyPaid) ||
                        (it is PaymentActivityItem.OutstandingInvoice && it.totalPaidKhr > 0L)
            }
            PaymentFilter.OVERDUE -> activityList.filter { it is PaymentActivityItem.OutstandingInvoice && it.isOverdue }
        }

        // 5. Filter by Search Query
        val q = query.trim().lowercase(Locale.US)
        val finalItems = if (q.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter { item ->
                when (item) {
                    is PaymentActivityItem.PaymentRecord -> {
                        item.tenantName.lowercase(Locale.US).contains(q) ||
                                item.roomNumber.lowercase(Locale.US).contains(q) ||
                                item.buildingName.lowercase(Locale.US).contains(q) ||
                                item.invoiceId.toString().contains(q)
                    }
                    is PaymentActivityItem.OutstandingInvoice -> {
                        item.tenantName.lowercase(Locale.US).contains(q) ||
                                item.roomNumber.lowercase(Locale.US).contains(q) ||
                                item.buildingName.lowercase(Locale.US).contains(q) ||
                                item.invoiceId.toString().contains(q)
                    }
                }
            }
        }.sortedByDescending { it.timestamp }

        PaymentsUiState(
            isLoading = false,
            selectedMonth = month,
            selectedYear = year,
            monthYearFormatted = monthYearFormatted,
            selectedFilter = filter,
            searchQuery = query,
            collectedKhr = collectedKhr,
            outstandingKhr = outstandingKhr,
            overdueKhr = overdueKhr,
            items = finalItems
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PaymentsUiState()
    )

    fun onPreviousMonthClicked() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, selectedMonthFlow.value - 1)
            set(Calendar.YEAR, selectedYearFlow.value)
            add(Calendar.MONTH, -1)
        }
        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
        selectedYearFlow.value = cal.get(Calendar.YEAR)
    }

    fun onNextMonthClicked() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, selectedMonthFlow.value - 1)
            set(Calendar.YEAR, selectedYearFlow.value)
            add(Calendar.MONTH, 1)
        }
        selectedMonthFlow.value = cal.get(Calendar.MONTH) + 1
        selectedYearFlow.value = cal.get(Calendar.YEAR)
    }

    fun onFilterSelected(filter: PaymentFilter) {
        selectedFilterFlow.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }
}