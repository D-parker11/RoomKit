package com.example.roomkit.Date.Local.Entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Six saved charge snapshots. Discount is the only negative line. Whole KHR. */
@Entity(tableName = "invoice_items", primaryKeys = ["invoiceId", "itemCode"],
    foreignKeys = [ForeignKey(entity = InvoiceEntity::class,
        parentColumns = ["invoiceId"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["invoiceId"])])
data class InvoiceItemEntity(
    val invoiceId: Long,
    val itemCode: String,
    val description: String,
    val quantity: Long?,
    val unit: String,
    val unitPriceKhr: Long?,
    val amountKhr: Long,
    val sortOrder: Int
)
