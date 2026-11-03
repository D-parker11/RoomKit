package com.example.roomkit.Date.Local.Entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buildings")
data class BuildingEntity(
    @PrimaryKey(autoGenerate = true)
    val buildingId: Long = 0L,
    val name: String,
    val address: String?,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(defaultValue = "0")
    val totalRooms: Int = 0
)