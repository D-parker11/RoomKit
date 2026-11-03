package com.example.roomkit.Date.Local.Entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meter_readings",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["roomId"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["roomId", "tenancyId", "meterType", "billingMonth", "billingYear", "readingType", "eventOrdinal"], unique = true),
        Index(value = ["roomId"])
    ]
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val readingId: Long = 0L,
    @ColumnInfo(defaultValue = "0") val tenancyId: Long = 0L,
    val roomId: Long,
    val meterType: String, // "ELECTRICITY" or "WATER"
    val previousReading: Long,
    val currentReading: Long,
    val readingDate: Long = System.currentTimeMillis(),
    val billingMonth: Int, // 1 to 12
    val billingYear: Int,
    @ColumnInfo(defaultValue = "'MONTHLY'") val readingType: String = "MONTHLY",
    @ColumnInfo(defaultValue = "0") val eventOrdinal: Int = 0,
    @ColumnInfo(defaultValue = "0") val meterSequence: Int = 0,
    @ColumnInfo(defaultValue = "0") val carriedUsage: Long = 0L,
    val reason: String? = null
) {
    val usage: Long get() = if (readingType == "METERREPLACEMENT") 0L else Math.addExact(carriedUsage, Math.subtractExact(currentReading, previousReading))
}