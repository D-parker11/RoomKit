package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "billing_draft_adjustments",
    primaryKeys = ["tenancyId", "billingMonth", "billingYear"],
    foreignKeys = [ForeignKey(entity = TenancyEntity::class,
        parentColumns = ["tenancyId"], childColumns = ["tenancyId"],
        onDelete = ForeignKey.RESTRICT)])
data class BillingDraftAdjustmentEntity(
    val tenancyId: Long,
    val billingMonth: Int,
    val billingYear: Int,
    val otherChargesKhr: Long,
    val discountKhr: Long,
    val billingPeriodStart: String,
    val billingPeriodEnd: String
)
