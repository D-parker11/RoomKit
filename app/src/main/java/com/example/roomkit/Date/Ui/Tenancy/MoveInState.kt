package com.example.roomkit.Date.Ui.Tenancy

import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity

data class MoveInUiState(
    // Entry Mode
    val isRoomLocked: Boolean = false,
    val isTenantLocked: Boolean = false,

    // Selections
    val tenantId: Long? = null,
    val tenantName: String = "",
    val buildingId: Long? = null,
    val buildingName: String = "",
    val roomId: Long? = null,
    val roomNumber: String = "",

    // Form inputs
    val moveInDate: Long = System.currentTimeMillis(),
    val moveInDateFormatted: String = "",
    val monthlyRentKhrInput: String = "",
    val depositKhrInput: String = "",
    val billingDayInput: String = "1",
    val dueDayInput: String = "5",
    val electricityRateKhrInput: String = "1000",
    val isWaterMetered: Boolean = false, // false = Fixed, true = Metered
    val waterFeeOrRateKhrInput: String = "20000",
    val rubbishFeeKhrInput: String = "10000",
    val initialElecReadingInput: String = "0",
    val initialWaterReadingInput: String = "0",

    // Options for dropdowns
    val allTenants: List<TenantEntity> = emptyList(),
    val allBuildings: List<BuildingEntity> = emptyList(),
    val availableRooms: List<RoomEntity> = emptyList(),

    // UI Feedback
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val createdTenancyId: Long? = null
)