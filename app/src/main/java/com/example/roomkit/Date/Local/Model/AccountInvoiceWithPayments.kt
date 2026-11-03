package com.example.roomkit.Date.Local.Model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.PaymentEntity

/** Read-only projection; not a new table. */
data class AccountInvoiceWithPayments(
    @Embedded val invoice: InvoiceEntity,
    @Relation(parentColumn = "invoiceId", entityColumn = "invoiceId")
    val payments: List<PaymentEntity>
)
