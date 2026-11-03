package com.example.roomkit.Date.Repositories

import com.example.roomkit.Date.Billing.InvoiceItems
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.roomkit.data.local.AppDatabase

/** Caller must be in the same database transaction as saving/issuing the header. */
internal suspend fun AppDatabase.writeDraftItems(invoice: InvoiceEntity) {
    check(invoice.status.trim().equals("DRAFT", true)) { "Only draft items can be changed." }
    check(invoice.invoiceId > 0L)
    val rows = InvoiceItems.fromInvoice(invoice)
    invoiceItemDao().deleteForDraft(invoice.invoiceId)
    invoiceItemDao().insertAll(rows)
}
