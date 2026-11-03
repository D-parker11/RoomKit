package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey(autoGenerate = true)
    val tenantId: Long = 0L,
    val fullName: String,
    val phoneNumber: String,
    val idOrPassport: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)