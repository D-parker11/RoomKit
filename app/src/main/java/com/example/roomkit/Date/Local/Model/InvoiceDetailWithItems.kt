package com.example.roomkit.Date.Local.Model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.roomkit.Date.Local.Entity.InvoiceItemEntity

/** Loaded by a Room @Transaction: header and items belong to the same snapshot. */
data class InvoiceDetailWithItems(
    @Embedded val detail: InvoiceWithDetails,
    @Relation(parentColumn = "invoiceId", entityColumn = "invoiceId")
    val items: List<InvoiceItemEntity>,
    @Relation(
        entity = com.example.roomkit.Date.Local.Entity.InvoiceEntity::class,
        parentColumn = "tenancyId", entityColumn = "tenancyId"
    )
    val accountInvoices: List<AccountInvoiceWithPayments>
)
