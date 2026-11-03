package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity

@Entity(
    tableName = "tenancies",
    foreignKeys = [
        ForeignKey(
            entity = TenantEntity::class,
            parentColumns = ["tenantId"],
            childColumns = ["tenantId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["roomId"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["roomId"]),
        Index(value = ["status"])
    ]
)
data class TenancyEntity(
    @PrimaryKey(autoGenerate = true)
    val tenancyId: Long = 0L,
    val tenantId: Long,
    val roomId: Long,
    val moveInDate: Long,
    val moveOutDate: Long? = null,
    val currency: String = "KHR",
    val monthlyRent: Long,
    val deposit: Long,
    val billingDay: Int,
    val dueDay: Int,
    val electricityRate: Long,
    val waterBillingMethod: String, // "FIXED" or "METER"
    val waterRate: Long,
    val rubbishFee: Long,
    val status: String // "ACTIVE" or "ENDED"
)