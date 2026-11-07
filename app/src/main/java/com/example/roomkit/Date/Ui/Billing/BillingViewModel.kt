@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Billing.DocumentStatus
import com.example.roomkit.Date.Billing.InvoiceStatusResolver
import com.example.roomkit.Date.Billing.PaymentStatus
import com.example.roomkit.Date.Billing.invoiceStatusClock
import com.example.roomkit.Date.Repositories.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

// Additional screen state avoids changing your existing BillingUiState or enums.
enum class BillingDocumentFilter { VOID, DRAFT }
private data class BillingSelection(
    val payment: BillingFilter = BillingFilter.ALL,
    val document: BillingDocumentFilter? = null
)

data class BillingReferenceState(
    val content: BillingUiState = BillingUiState(),
    val outstandingKhr: Long = 0L,
    val voidCount: Int = 0,
    val draftCount: Int = 0,
    val documentFilter: BillingDocumentFilter? = null
)

class BillingViewModel(private val repository: BillingRepository) : ViewModel() {
    // A single period avoids transient year/month mismatches across December/January.
    private val periodFlow = MutableStateFlow(YearMonth.now())
    private val buildingFlow = MutableStateFlow<Long?>(null)
    private val selectionFlow = MutableStateFlow(BillingSelection())
    private val dueDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val paymentDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    val screenState: StateFlow<BillingReferenceState> = combine(
        combine(repository.observeBillingData(), invoiceStatusClock()) { raw, now -> raw to now },
        periodFlow,
        buildingFlow,
        selectionFlow
    ) { timedData, period, buildingId, selection ->
        val (raw, now) = timedData
        val monthText = period.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        val buildings = raw.buildings.associateBy { it.buildingId }
        val rooms = raw.rooms.associateBy { it.roomId }
        val tenancies = raw.tenancies.associateBy { it.tenancyId }
        val tenants = raw.tenants.associateBy { it.tenantId }
        val payments = raw.payments.filter {
            it.status.trim().equals("ACTIVE", ignoreCase = true)
        }.groupBy { it.invoiceId }
        val validStatuses = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE", "VOID", "VOIDED", "DRAFT")
        val invoices = raw.invoices.filter { invoice ->
            val room = tenancies[invoice.tenancyId]?.let { rooms[it.roomId] }
            (buildingId == null || room?.buildingId == buildingId) &&
                    invoice.billingMonth == period.monthValue && invoice.billingYear == period.year &&
                    invoice.status.trim().uppercase(Locale.ROOT) in validStatuses
        }
        var outstanding = 0L
        var paidCount = 0
        var partialCount = 0
        var unpaidCount = 0
        var overdueCount = 0
        var voidCount = 0
        var draftCount = 0
        val items = invoices.map { invoice ->
            val invoicePayments = payments[invoice.invoiceId].orEmpty()
            val paid = invoicePayments.sumOf { it.amountKhr }
            val status = InvoiceStatusResolver.resolve(invoice.status, invoice.totalKhr, paid, invoice.dueDate, now)
            val display = when (status.documentStatus) {
                DocumentStatus.VOID -> { voidCount++; BillingDisplayStatus.VOID }
                DocumentStatus.DRAFT -> { draftCount++; BillingDisplayStatus.DRAFT }
                else -> {
                    outstanding += status.remainingKhr.coerceAtLeast(0L)
                    if (status.isOverdue) overdueCount++
                    when (status.paymentStatus) {
                        PaymentStatus.PAID -> { paidCount++; BillingDisplayStatus.PAID }
                        PaymentStatus.PARTIALLY_PAID -> { partialCount++; BillingDisplayStatus.PARTIAL }
                        else -> { unpaidCount++; BillingDisplayStatus.UNPAID }
                    }
                }
            }
            val tenancy = tenancies[invoice.tenancyId]
            val room = tenancy?.let { rooms[it.roomId] }
            val tenant = tenancy?.let { tenants[it.tenantId] }
            val lastPayment = invoicePayments.maxByOrNull { it.paymentDate }
            val dueDate = invoice.dueDate
            BillingInvoiceItem(
                invoiceId = invoice.invoiceId,
                roomNumber = room?.roomNumber ?: "—",
                buildingName = room?.let { buildings[it.buildingId]?.name }.orEmpty(),
                tenantName = tenant?.fullName ?: "Tenant",
                billingPeriod = monthText,
                dueDateText = dueDate?.let { "Due ${dueDateFormat.format(Date(it))}" }.orEmpty(),
                invoiceTotalKhr = invoice.totalKhr,
                totalPaidKhr = paid,
                remainingBalanceKhr = status.remainingKhr,
                status = display,
                documentStatus = status.documentStatus,
                paymentStatus = status.paymentStatus,
                isOverdue = status.isOverdue,
                lastPaymentDateText = lastPayment?.let { paymentDateFormat.format(Date(it.paymentDate)) },
                lastPaymentMethodText = lastPayment?.paymentMethod,
                paymentCount = invoicePayments.size
            )
        }
        // Document precedence keeps voids/drafts out of payment-status filters.
        val filtered = when (selection.document) {
            BillingDocumentFilter.VOID -> items.filter { it.status == BillingDisplayStatus.VOID }
            BillingDocumentFilter.DRAFT -> items.filter { it.status == BillingDisplayStatus.DRAFT }
            null -> when (selection.payment) {
                BillingFilter.ALL -> items
                BillingFilter.PAID -> items.filter { it.status == BillingDisplayStatus.PAID }
                BillingFilter.PARTIAL -> items.filter { it.status == BillingDisplayStatus.PARTIAL }
                BillingFilter.UNPAID -> items.filter { it.status == BillingDisplayStatus.UNPAID }
                BillingFilter.OVERDUE -> items.filter {
                    it.status != BillingDisplayStatus.VOID && it.status != BillingDisplayStatus.DRAFT && it.isOverdue
                }
            }
        }
        BillingReferenceState(
            content = BillingUiState(
                isLoading = false,
                selectedMonth = period.monthValue,
                selectedYear = period.year,
                monthYearFormatted = monthText,
                selectedBuildingId = buildingId,
                selectedFilter = selection.payment,
                totalCount = invoices.size,
                paidCount = paidCount,
                partialCount = partialCount,
                unpaidCount = unpaidCount,
                overdueCount = overdueCount,
                invoices = filtered
            ),
            outstandingKhr = outstanding,
            voidCount = voidCount,
            draftCount = draftCount,
            documentFilter = selection.document
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillingReferenceState())

    // Retained for compatibility with existing consumers.
    val uiState: StateFlow<BillingUiState> = screenState.map { it.content }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillingUiState())

    fun onPreviousMonthClicked() { periodFlow.value = periodFlow.value.minusMonths(1) }
    fun onNextMonthClicked() { periodFlow.value = periodFlow.value.plusMonths(1) }
    fun onFilterSelected(filter: BillingFilter) { selectionFlow.value = BillingSelection(payment = filter) }
    fun onDocumentFilterSelected(filter: BillingDocumentFilter) { selectionFlow.value = BillingSelection(document = filter) }
    fun onBuildingSelected(buildingId: Long?) { buildingFlow.value = buildingId }
}
