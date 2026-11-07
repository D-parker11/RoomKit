@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.invoiceStatusClock
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class PaymentsScreenFilter { ALL, UNPAID, PAID, PARTIAL, OVERDUE, HISTORY }

data class PaymentsReferenceState(
    val content: PaymentsUiState = PaymentsUiState(),
    val filter: PaymentsScreenFilter = PaymentsScreenFilter.ALL,
    val invoiceCount: Int = 0,
    val unpaidCount: Int = 0,
    val paidCount: Int = 0,
    val partialCount: Int = 0,
    val overdueCount: Int = 0,
    val historyCount: Int = 0
)

class PaymentsViewModel(private val repository: BillingRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val filter = MutableStateFlow(PaymentsScreenFilter.ALL)
    private val query = MutableStateFlow("")
    val currentSearchQuery: String get() = query.value
    @OptIn(FlowPreview::class)
    private val debouncedQuery = query.debounce(250)
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    val screenState: StateFlow<PaymentsReferenceState> = combine(
        combine(repository.observeBillingData(), invoiceStatusClock()) { raw, now -> raw to now },
        month, filter, debouncedQuery
    ) { timed, selectedMonth, selectedFilter, search ->
        val (raw, now) = timed
        val buildings = raw.buildings.associateBy { it.buildingId }
        val rooms = raw.rooms.associateBy { it.roomId }
        val tenancies = raw.tenancies.associateBy { it.tenancyId }
        val tenants = raw.tenants.associateBy { it.tenantId }
        val payments = raw.payments.groupBy { it.invoiceId }
        val official = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE")
        val invoices = raw.invoices.filter {
            it.billingYear == selectedMonth.year && it.billingMonth == selectedMonth.monthValue &&
                    it.status.trim().uppercase(Locale.ROOT) in official
        }
        val outstanding = mutableListOf<PaymentActivityItem.OutstandingInvoice>()
        val receipts = mutableListOf<PaymentActivityItem.PaymentRecord>()
        val unpaidIds = mutableSetOf<Long>()
        val paidIds = mutableSetOf<Long>()
        val partialIds = mutableSetOf<Long>()
        val overdueIds = mutableSetOf<Long>()
        var collected = 0L
        var balance = 0L
        var overdue = 0L
        for (invoice in invoices) {
            val records = payments[invoice.invoiceId].orEmpty()
            val active = records.filter { it.status.trim().equals("ACTIVE", true) }
            val paid = active.sumOf { it.amountKhr }
            val resolved = InvoiceStatusResolver.resolve(invoice.status, invoice.totalKhr, paid, invoice.dueDate, now)
            val remaining = resolved.remainingKhr.coerceAtLeast(0L)
            collected += paid
            balance += remaining
            if (remaining == 0L) paidIds.add(invoice.invoiceId)
            else if (paid > 0L) partialIds.add(invoice.invoiceId)
            else unpaidIds.add(invoice.invoiceId)
            if (resolved.isOverdue && remaining > 0L) {
                overdue += remaining
                overdueIds.add(invoice.invoiceId)
            }
            val tenancy = tenancies[invoice.tenancyId]
            val room = tenancy?.let { rooms[it.roomId] }
            val name = tenancy?.let { tenants[it.tenantId]?.fullName } ?: "Tenant"
            val number = room?.roomNumber ?: "—"
            val property = room?.let { buildings[it.buildingId]?.name }.orEmpty()
            if (remaining > 0L) outstanding.add(PaymentActivityItem.OutstandingInvoice(
                invoiceId = invoice.invoiceId, tenantName = name, roomNumber = number,
                buildingName = property,
                dueDateFormatted = invoice.dueDate?.let { "Due ${dateFormat.format(Date(it))}" } ?: "No due date",
                timestamp = invoice.dueDate ?: invoice.issueDate,
                invoiceTotalKhr = invoice.totalKhr, totalPaidKhr = paid,
                remainingBalanceKhr = remaining, isOverdue = resolved.isOverdue
            ))
            // Keep voided receipts visible in history, but never count them as collected money.
            for (p in records) {
                val status = p.status.trim().uppercase(Locale.ROOT)
                if (status !in setOf("ACTIVE", "VOID", "VOIDED")) continue
                receipts.add(PaymentActivityItem.PaymentRecord(
                    paymentId = p.paymentId, invoiceId = invoice.invoiceId, tenantName = name,
                    roomNumber = number, buildingName = property,
                    paymentDateFormatted = dateFormat.format(Date(p.paymentDate)), timestamp = p.paymentDate,
                    paymentAmountKhr = p.amountKhr, paymentMethod = p.paymentMethod,
                    invoiceTotalKhr = invoice.totalKhr, remainingBalanceKhr = remaining,
                    isFullyPaid = remaining == 0L, isVoided = status != "ACTIVE"
                ))
            }
        }
        val candidates: List<PaymentActivityItem> = when (selectedFilter) {
            PaymentsScreenFilter.ALL -> outstanding + receipts
            PaymentsScreenFilter.UNPAID -> outstanding.filter { it.invoiceId in unpaidIds }
            PaymentsScreenFilter.PAID -> receipts.filter { !it.isVoided && it.invoiceId in paidIds }
            PaymentsScreenFilter.PARTIAL -> outstanding.filter { it.invoiceId in partialIds }
            PaymentsScreenFilter.OVERDUE -> outstanding.filter { it.invoiceId in overdueIds }
            PaymentsScreenFilter.HISTORY -> receipts
        }
        val q = search.trim()
        val visible = candidates.filter { item ->
            val fields = when (item) {
                is PaymentActivityItem.OutstandingInvoice -> listOf(item.tenantName, item.roomNumber, item.buildingName, item.invoiceId.toString(), "Room ${item.roomNumber}")
                is PaymentActivityItem.PaymentRecord -> listOf(item.tenantName, item.roomNumber, item.buildingName, item.invoiceId.toString(), "Room ${item.roomNumber}")
            }
            q.isEmpty() || fields.any { it.contains(q, ignoreCase = true) }
        }.sortedByDescending { it.timestamp }
        val legacyFilter = when (selectedFilter) {
            PaymentsScreenFilter.PAID -> PaymentFilter.PAID
            PaymentsScreenFilter.PARTIAL -> PaymentFilter.PARTIAL
            PaymentsScreenFilter.OVERDUE -> PaymentFilter.OVERDUE
            else -> PaymentFilter.ALL
        }
        PaymentsReferenceState(
            content = PaymentsUiState(
                isLoading = false, selectedMonth = selectedMonth.monthValue, selectedYear = selectedMonth.year,
                monthYearFormatted = selectedMonth.format(monthFormat), selectedFilter = legacyFilter,
                searchQuery = search, collectedKhr = collected, outstandingKhr = balance, overdueKhr = overdue,
                items = visible
            ), filter = selectedFilter, invoiceCount = invoices.size, unpaidCount = unpaidIds.size,
            paidCount = paidIds.size, partialCount = partialIds.size, overdueCount = overdueIds.size,
            historyCount = receipts.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentsReferenceState())

    // Existing consumers can continue using the original UI-state type.
    val uiState: StateFlow<PaymentsUiState> = screenState.map { it.content }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentsUiState())
    fun onPreviousMonthClicked() { month.value = month.value.minusMonths(1) }
    fun onNextMonthClicked() { month.value = month.value.plusMonths(1) }
    fun onSearchQueryChanged(value: String) { query.value = value }
    fun onScreenFilterSelected(value: PaymentsScreenFilter) { filter.value = value }
    fun onFilterSelected(value: PaymentFilter) {
        filter.value = when (value) {
            PaymentFilter.PAID -> PaymentsScreenFilter.PAID
            PaymentFilter.PARTIAL -> PaymentsScreenFilter.PARTIAL
            PaymentFilter.OVERDUE -> PaymentsScreenFilter.OVERDUE
            else -> PaymentsScreenFilter.ALL
        }
    }
}
