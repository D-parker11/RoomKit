package com.example.roomkit.Date.Ui.Dashboard

import java.util.Calendar

data class BuildingFilterItem(
    val buildingId: Long?,
    val name: String,
    val isSelected: Boolean
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val selectedBuildingId: Long? = null,
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val monthYearFormatted: String = "",

    val buildings: List<BuildingFilterItem> = emptyList(),

    // Room metrics
    val totalRooms: Int = 0,
    val occupiedRooms: Int = 0,
    val vacantRooms: Int = 0,
    val occupancyPercent: Int = 0,

    // Financial totals (in whole KHR)
    val billedKhr: Long = 0L,
    val collectedKhr: Long = 0L,
    val outstandingKhr: Long = 0L,

    // Status counts
    val paidCount: Int = 0,
    val partiallyPaidCount: Int = 0,
    val unpaidCount: Int = 0,
    val overdueCount: Int = 0,

    // Requires Attention items
    val attentionItems: List<DashboardAttentionItem> = emptyList(),
    val errorMessage: String? = null
)