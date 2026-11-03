package com.roomkit.data.local.model

import androidx.room.Embedded
import androidx.room.Relation

data class BuildingWithStats(
    val buildingId: Long,
    val name: String,
    val address: String?,
    val description: String?,
    val totalRooms: Int,
    val occupiedRooms: Int
) {
    val vacantRooms: Int get() = (totalRooms - occupiedRooms).coerceAtLeast(0)
    val occupancyPercentage: Int get() = if (totalRooms == 0) 0 else ((occupiedRooms * 100) / totalRooms)
}

data class RoomItemDetails(
    val roomId: Long,
    val buildingId: Long,
    val roomNumber: String,
    val floor: String?,
    val monthlyRent: Long,
    val currency: String = "KHR",
    val isOccupied: Boolean,
    val tenantName: String?,
    val electricityReading: Long?,
    val waterReading: Long?,
    val dueDateText: String?,
    val waterBillingMethod: String? = null,
    val paymentStatus: String?
)
