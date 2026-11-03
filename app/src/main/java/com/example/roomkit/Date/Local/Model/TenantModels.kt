package com.example.roomkit.Date.Local.Model

data class TenantWithCurrentTenancy(
    val tenantId: Long,
    val fullName: String,
    val phoneNumber: String,
    val idOrPassport: String?,
    val note: String?,
    val activeTenancyId: Long?,
    val currentBuildingId: Long?,
    val currentRoomId: Long?,
    val currentRoomNumber: String?,
    val currentBuildingName: String?,
    val currency: String?,
    val deposit: Long?,
    val moveInDate: Long?,
    val totalTenanciesCount: Int = 0
) {
    val isActive: Boolean get() = activeTenancyId != null
    val roomNumber: String? get() = currentRoomNumber
    val buildingName: String? get() = currentBuildingName
}

data class TenancyHistoryItem(
    val tenancyId: Long,
    val roomNumber: String,
    val buildingName: String,
    val moveInDate: Long,
    val moveOutDate: Long?,
    val monthlyRent: Long,
    val status: String
)

data class VacantRoomOption(
    val roomId: Long,
    val buildingId: Long,
    val roomNumber: String,
    val floor: String?,
    val monthlyRent: Long
)