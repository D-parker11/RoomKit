@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.DashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class DashboardViewModel(private val repository: DashboardRepository) : ViewModel() {
    private val selectedBuildingIdFlow = MutableStateFlow<Long?>(null)
    private val selectedPeriod = MutableStateFlow(YearMonth.now())
    private val todayFlow = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeDashboardData(),
        selectedBuildingIdFlow,
        selectedPeriod,
        todayFlow
    ) { raw, buildingFilterId, period, today ->
        val chips = listOf(
            BuildingFilterItem(buildingId = null, name = "All Buildings", isSelected = buildingFilterId == null)
        ) + raw.buildings.map { building ->
            BuildingFilterItem(
                buildingId = building.buildingId,
                name = building.name,
                isSelected = buildingFilterId == building.buildingId
            )
        }
        val rooms = raw.rooms.filter {
            buildingFilterId == null || it.buildingId == buildingFilterId
        }
        val roomIds = rooms.map { it.roomId }.toSet()
        val occupied = raw.tenancies.filter {
            it.status.equals("ACTIVE", ignoreCase = true) && it.roomId in roomIds
        }.map { it.roomId }.toSet().size
        val totalRooms = rooms.size
        val occupancy = if (totalRooms == 0) 0 else
            (occupied.toDouble() / totalRooms * 100).roundToInt().coerceIn(0, 100)
        val tenancies = raw.tenancies.associateBy { it.tenancyId }
        val roomMap = raw.rooms.associateBy { it.roomId }
        val buildingMap = raw.buildings.associateBy { it.buildingId }
        // Support both current and legacy official status names. Drafts and voids are excluded.
        val officialStatuses = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE")
        val invoices = raw.invoices.filter { invoice ->
            val invoiceRoomId = tenancies[invoice.tenancyId]?.roomId
            invoiceRoomId != null && invoiceRoomId in roomIds &&
                    invoice.billingMonth == period.monthValue &&
                    invoice.billingYear == period.year &&
                    invoice.status.uppercase(Locale.ROOT) in officialStatuses
        }
        val invoiceIds = invoices.map { it.invoiceId }.toSet()
        val payments = raw.payments.filter {
            it.status.equals("ACTIVE", ignoreCase = true) && it.invoiceId in invoiceIds
        }.groupBy { it.invoiceId }
        val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var billed = 0L
        var collected = 0L
        var outstanding = 0L
        var paidCount = 0
        var partialCount = 0
        var unpaidCount = 0
        var overdueCount = 0
        val attention = mutableListOf<DashboardAttentionItem>()
        for (invoice in invoices) {
            val total = invoice.totalKhr
            val paid = payments[invoice.invoiceId].orEmpty().sumOf { it.amountKhr }
            val remaining = (total - paid).coerceAtLeast(0L)
            val dueDate = invoice.dueDate
            // An invoice due today is not overdue until tomorrow.
            val overdue = dueDate != null && dueDate < startOfToday
            billed += total
            collected += paid
            outstanding += remaining
            when {
                remaining == 0L -> paidCount++
                overdue -> overdueCount++
                paid > 0L -> partialCount++
                else -> unpaidCount++
            }
            if (remaining > 0L) {
                val roomId = tenancies[invoice.tenancyId]?.roomId ?: continue
                val room = roomMap[roomId]
                val roomNumber = room?.roomNumber.orEmpty()
                val buildingName = room?.let { buildingMap[it.buildingId]?.name }.orEmpty()
                attention += DashboardAttentionItem(
                    type = if (overdue) DashboardAttentionType.OVERDUE else DashboardAttentionType.UNPAID,
                    invoiceId = invoice.invoiceId,
                    roomId = roomId,
                    roomNumber = roomNumber,
                    buildingName = buildingName,
                    title = "Room $roomNumber",
                    subtitle = when {
                        overdue -> "Payment overdue"
                        paid > 0L -> "Partially paid"
                        else -> "Payment unpaid"
                    },
                    statusLabel = when {
                        overdue -> "Overdue"
                        paid > 0L -> "Partial"
                        else -> "Unpaid"
                    }
                )
            }
        }
        DashboardUiState(
            isLoading = false,
            selectedBuildingId = buildingFilterId,
            selectedMonth = period.monthValue,
            selectedYear = period.year,
            monthYearFormatted = period.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
            buildings = chips,
            totalRooms = totalRooms,
            occupiedRooms = occupied,
            vacantRooms = (totalRooms - occupied).coerceAtLeast(0),
            occupancyPercent = occupancy,
            billedKhr = billed,
            collectedKhr = collected,
            outstandingKhr = outstanding,
            paidCount = paidCount,
            partiallyPaidCount = partialCount,
            unpaidCount = unpaidCount,
            overdueCount = overdueCount,
            attentionItems = attention.sortedWith(
                compareBy<DashboardAttentionItem> { it.type != DashboardAttentionType.OVERDUE }
                    .thenBy { it.roomNumber }
                    .thenBy { it.invoiceId }
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun onBuildingFilterSelected(buildingId: Long?) {
        selectedBuildingIdFlow.value = buildingId
    }

    fun onMonthChanged(month: Int, year: Int) {
        selectedPeriod.value = YearMonth.of(year, month)
    }

    fun refreshDate() {
        todayFlow.value = LocalDate.now()
    }
}
