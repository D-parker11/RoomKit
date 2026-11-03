package com.example.roomkit.Date.Workers

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.roomkit.Date.Reminders.InvoiceReminderContent
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.example.roomkit.Date.Reminders.InvoiceReminderPolicy
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.Date.Reminders.InvoiceReminderStore
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class InvoiceReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val requestedId = inputData.getLong("invoiceId", -1L)
            val ids = if (requestedId > 0L) listOf(requestedId) else
                db.invoiceDao().observeAllInvoices().first().map { it.invoiceId }
            var failed = false
            for (id in ids) {
                if (isStopped) break
                try {
                    if (requestedId <= 0L) InvoiceReminderScheduler.cancelLegacy(applicationContext, id)
                    val nextDueDate = db.withTransaction {
                        val invoice = db.invoiceDao().getInvoiceById(id)
                            ?: return@withTransaction null
                        val paid = db.paymentDao().getTotalPaidForInvoice(id)
                        val remaining = (invoice.totalKhr - paid).coerceAtLeast(0L)
                        if (!InvoiceReminderPolicy.eligible(invoice.status, remaining)) {
                            InvoiceReminderNotifications.cancel(applicationContext, id)
                            return@withTransaction null
                        }
                        val now = System.currentTimeMillis()
                        val slot = InvoiceReminderPolicy.current(invoice.dueDate, now)
                        if (slot != null && InvoiceReminderNotifications.canNotify(applicationContext)) {
                            val detail = db.invoiceDao().getInvoiceDetailById(id)
                            val content = InvoiceReminderContent.build(
                                invoiceId = id,
                                roomNumber = detail?.roomNumber,
                                tenantName = detail?.tenantName,
                                dueDate = invoice.dueDate,
                                remainingKhr = remaining,
                                slotKey = slot.key
                            )
                            // Durable reservation BEFORE posting prevents duplicate alerts after retries/crashes.
                            // A process death between claim and notify can skip this occurrence (at-most-once).
                            if (InvoiceReminderStore.get(applicationContext).claim(
                                    id, invoice.dueDate, slot.key, now)) {
                                InvoiceReminderNotifications.show(applicationContext, id, content.title, content.message)
                            }
                        }
                        invoice.dueDate
                    }
                    if (nextDueDate != null) {
                        // Wait for persistence before reporting success; the next weekly link survives exit.
                        InvoiceReminderScheduler.enqueueNext(applicationContext, id, nextDueDate).result.get()
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    failed = true
                    android.util.Log.w("InvoiceReminder", "Reminder failed for invoice $id", e)
                }
            }
            if (failed) Result.retry() else Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("InvoiceReminder", "Reminder reconciliation failed", e)
            Result.retry()
        }
    }
}
