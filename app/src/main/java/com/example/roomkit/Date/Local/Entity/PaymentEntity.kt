package com.example.roomkit.Date.Local.Entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["invoiceId"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["invoiceId"])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val paymentId: Long = 0L,
    val invoiceId: Long,
    val amountKhr: Long,
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentMethod: String = "CASH", // CASH, BANK, KHQR
    val note: String? = null,
    @ColumnInfo(defaultValue = "'ACTIVE'")
    val status: String = "ACTIVE",
    val voidedAt: Long? = null,
    val voidReason: String? = null,
    val referenceNumber: String? = null
)