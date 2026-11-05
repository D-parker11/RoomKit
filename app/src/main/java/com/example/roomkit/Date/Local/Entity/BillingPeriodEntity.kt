package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "billing_periods", primaryKeys = ["billingYear", "billingMonth"])
data class BillingPeriodEntity(
    val billingYear: Int,
    val billingMonth: Int,
    val status: String = "OPEN",
    val invoiceIds: String = "",
    val closedAt: Long? = null
)

@Entity(tableName = "billing_period_events")
data class BillingPeriodEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val billingYear: Int,
    val billingMonth: Int,
    val action: String,
    val reason: String,
    val createdAt: Long
)
