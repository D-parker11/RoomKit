package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["invoiceId"],
            childColumns = ["replacesInvoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(
            entity = TenancyEntity::class,
            parentColumns = ["tenancyId"],
            childColumns = ["tenancyId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["tenancyId"]),
        Index(value = ["tenancyId", "billingMonth", "billingYear", "invoiceKind", "revision"], unique = true),
        Index(value = ["replacesInvoiceId"], unique = true)
    ]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val invoiceId: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "'REGULAR'") val invoiceKind: String = "REGULAR",
    val tenancyId: Long,
    val billingMonth: Int,
    val billingYear: Int,

    // Rent in whole KHR
    val rentKhr: Long,

    // Electricity
    val electricityPrevious: Long?,
    val electricityCurrent: Long?,
    val electricityUsage: Long,
    val electricityRateKhrSnapshot: Long,
    val electricityChargeKhr: Long,

    // Water
    val waterBillingMethod: String, // "METER" or "FIXED"
    val waterPrevious: Long?,
    val waterCurrent: Long?,
    val waterUsage: Long?,
    val waterRateKhrSnapshot: Long?,
    val fixedWaterFeeKhrSnapshot: Long?,
    val waterChargeKhr: Long,

    // Rubbish & Other Charges
    val rubbishFeeKhrSnapshot: Long,
    val otherChargesKhr: Long = 0L,

    // Utility Subtotal in KHR
    val utilitySubtotalKhr: Long,



    // Discount in whole KHR
    val discountKhr: Long = 0L,

    // Final Total in whole KHR
    val totalKhr: Long,

    // Balances in whole KHR
    val amountPaidKhr: Long = 0L,
    val balanceKhr: Long,

    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val status: String, // DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, VOID
    val createdAt: Long = System.currentTimeMillis(),
    val voidedAt: Long? = null,
    val voidReason: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val revision: Int = 0,
    val replacesInvoiceId: Long? = null,
    // ISO local calendar dates, inclusive. Null for legacy invoices with unknown exact dates.
    val billingPeriodStart: String? = null,
    val billingPeriodEnd: String? = null,
    val discountReason: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'KHR'")
    val currency: String = "KHR",
    @androidx.room.ColumnInfo(defaultValue = "0") val electricityCarriedUsage: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val waterCarriedUsage: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "0") val electricityMeterSequence: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val waterMeterSequence: Int = 0
)
