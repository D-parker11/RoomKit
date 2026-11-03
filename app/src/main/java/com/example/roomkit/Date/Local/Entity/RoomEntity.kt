package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = BuildingEntity::class,
            parentColumns = ["buildingId"],
            childColumns = ["buildingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["buildingId", "roomNumber"], unique = true),
//        Index(value = ["buildingId"])
    ]
)

data class RoomEntity(
    @PrimaryKey(autoGenerate = true)
    val roomId: Long = 0L,
    val buildingId: Long,
    val roomNumber: String,
    val floor: String?,
    val monthlyRent: Long,
    val description: String?,
    val roomType: String? = null,
    val initialElectricityReading: Double? = 0.0,
    val initialWaterReading: Double? = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "VACANT"

)