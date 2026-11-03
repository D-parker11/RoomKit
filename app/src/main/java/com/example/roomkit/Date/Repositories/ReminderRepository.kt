package com.example.roomkit.Date.Ui.Reminders

import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class RemindersRepository(private val db: AppDatabase) {
    fun observe(): Flow<List<ReminderInvoice>> = combine(
        db.invoiceDao().observeAllInvoices(), db.paymentDao().observeAllPayments()
    ) { invoices, payments ->
        val active = payments.filter { it.status.trim().equals("ACTIVE", true) }
            .groupBy { it.invoiceId }
        invoices.map { invoice ->
            val detail = db.invoiceDao().getInvoiceDetailById(invoice.invoiceId)
            ReminderInvoice(
                invoice.invoiceId, detail?.roomNumber ?: "—", detail?.buildingName.orEmpty(),
                detail?.tenantName ?: "Tenant", invoice.status, invoice.totalKhr,
                active[invoice.invoiceId].orEmpty().fold(0L) { sum, p -> Math.addExact(sum, p.amountKhr) },
                invoice.dueDate
            )
        }
    }.flowOn(Dispatchers.IO)
}
