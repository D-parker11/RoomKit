
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardViewModel(
    private val repository: DashboardRepository
) : ViewModel() {

    private val selectedBuildingIdFlow = MutableStateFlow<Long?>(null)

    private val currentCal = Calendar.getInstance()
    private val selectedMonthFlow = MutableStateFlow(currentCal.get(Calendar.MONTH) + 1)
    private val selectedYearFlow = MutableStateFlow(currentCal.get(Calendar.YEAR))

    private val monthYearFormatter = SimpleDateFormat("MMM yyyy", Locale.US)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeDashboardData(),
        selectedBuildingIdFlow,
        selectedMonthFlow,
        selectedYearFlow
    ) { raw, buildingFilterId, month, year ->

        // 1. Build Filter Chips
        val chipList = mutableListOf<BuildingFilterItem>()
        chipList.add(
            BuildingFilterItem(
                buildingId = null,
                name = "All Buildings",
                isSelected = (buildingFilterId == null)
            )
        )
        raw.buildings.forEach { b ->
            chipList.add(
                BuildingFilterItem(
                    buildingId = b.buildingId,
                    name = b.name,
                    isSelected = (buildingFilterId == b.buildingId)
                )
            )
        }

        // 2. Filter Rooms by Building (null = All Buildings)
        val scopedRooms = if (buildingFilterId == null) {
            raw.rooms
        } else {
            raw.rooms.filter { it.buildingId == buildingFilterId }
        }
        val scopedRoomIds = scopedRooms.map { it.roomId }.toSet()

        // 3. Calculate Room Occupancy (Room is OCCUPIED if it has ACTIVE tenancy)
        val activeTenancyRoomIds = raw.tenancies
            .filter { it.status.equals("ACTIVE", ignoreCase = true) && scopedRoomIds.contains(it.roomId) }
            .map { it.roomId }
            .toSet()

        val totalRooms = scopedRooms.size
        val occupiedRooms = activeTenancyRoomIds.size
        val vacantRooms = (totalRooms - occupiedRooms).coerceAtLeast(0)
        val occupancyPercent = if (totalRooms > 0) {
            ((occupiedRooms.toDouble() / totalRooms.toDouble()) * 100).toInt()
        } else 0

        // Map tenancyId -> TenancyEntity to bridge Invoice -> Tenancy -> Room
        val tenancyMap = raw.tenancies.associateBy { it.tenancyId }

        // 4. Filter Invoices by Building & Selected Month/Year
        val validOfficialStatuses = setOf("ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE")
        val scopedInvoices = raw.invoices.filter { inv ->
            val invoiceRoomId = tenancyMap[inv.tenancyId]?.roomId
            invoiceRoomId != null &&
                    scopedRoomIds.contains(invoiceRoomId) &&
                    inv.billingMonth == month &&
                    inv.billingYear == year &&
                    validOfficialStatuses.contains(inv.status.uppercase(Locale.US))
        }

        val scopedInvoiceIds = scopedInvoices.map { it.invoiceId }.toSet()

        // Group actual payments by invoice
        val paymentsByInvoice = raw.payments
            .filter { it.status == "ACTIVE" && scopedInvoiceIds.contains(it.invoiceId) }
            .groupBy { it.invoiceId }

        // 5. Calculate Financials & Statuses
        var billedKhr = 0L
        var collectedKhr = 0L

        var paidCount = 0
        var partiallyPaidCount = 0
        var unpaidCount = 0
        var overdueCount = 0

        val attentionItems = mutableListOf<DashboardAttentionItem>()
        val today = Date()

        val roomMap = raw.rooms.associateBy { it.roomId }
        val buildingMap = raw.buildings.associateBy { it.buildingId }

        for (inv in scopedInvoices) {
            val total = inv.totalKhr
            billedKhr += total

            val invoicePayments = paymentsByInvoice[inv.invoiceId] ?: emptyList()
            val paid = invoicePayments.sumOf { it.amountKhr }
            collectedKhr += paid

            val remaining = (total - paid).coerceAtLeast(0L)
            val isDueDatePassed = inv.dueDate != null && inv.dueDate < today.time

            val tenancy = tenancyMap[inv.tenancyId]
            val invoiceRoomId = tenancy?.roomId ?: -1L
            val room = roomMap[invoiceRoomId]
            val roomNum = room?.roomNumber ?: "Room"
            val buildingName = room?.let { buildingMap[it.buildingId]?.name } ?: ""

            when {
                remaining == 0L -> {
                    paidCount++
                }
                isDueDatePassed -> {
                    overdueCount++
                    attentionItems.add(
                        DashboardAttentionItem(
                            type = DashboardAttentionType.OVERDUE,
                            invoiceId = inv.invoiceId,
                            roomId = invoiceRoomId,
                            roomNumber = roomNum,
                            buildingName = buildingName,
                            title = "Room $roomNum · $buildingName",
                            subtitle = "Payment overdue",
                            statusLabel = "Overdue"
                        )
                    )
                }
                paid > 0L -> {
                    partiallyPaidCount++
                    attentionItems.add(
                        DashboardAttentionItem(
                            type = DashboardAttentionType.UNPAID,
                            invoiceId = inv.invoiceId,
                            roomId = invoiceRoomId,
                            roomNumber = roomNum,
                            buildingName = buildingName,
                            title = "Room $roomNum · $buildingName",
                            subtitle = "Payment partially unpaid",
                            statusLabel = "Partial"
                        )
                    )
                }
                else -> {
                    unpaidCount++
                    attentionItems.add(
                        DashboardAttentionItem(
                            type = DashboardAttentionType.UNPAID,
                            invoiceId = inv.invoiceId,
                            roomId = invoiceRoomId,
                            roomNumber = roomNum,
                            buildingName = buildingName,
                            title = "Room $roomNum · $buildingName",
                            subtitle = "Payment unpaid",
                            statusLabel = "Unpaid"
                        )
                    )
                }
            }
        }

        val outstandingKhr = (billedKhr - collectedKhr).coerceAtLeast(0L)

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, month - 1)
            set(Calendar.YEAR, year)
        }
        val monthYearFormatted = monthYearFormatter.format(cal.time)

        DashboardUiState(
            isLoading = false,
            selectedBuildingId = buildingFilterId,
            selectedMonth = month,
            selectedYear = year,
            monthYearFormatted = monthYearFormatted,
            buildings = chipList,
            totalRooms = totalRooms,
            occupiedRooms = occupiedRooms,
            vacantRooms = vacantRooms,
            occupancyPercent = occupancyPercent,
            billedKhr = billedKhr,
            collectedKhr = collectedKhr,
            outstandingKhr = outstandingKhr,
            paidCount = paidCount,
            partiallyPaidCount = partiallyPaidCount,
            unpaidCount = unpaidCount,
            overdueCount = overdueCount,
            attentionItems = attentionItems
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardUiState()
    )

    fun onBuildingFilterSelected(buildingId: Long?) {
        selectedBuildingIdFlow.value = buildingId
    }

    fun onMonthChanged(month: Int, year: Int) {
        selectedMonthFlow.value = month
        selectedYearFlow.value = year
    }
}