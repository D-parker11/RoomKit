package com.example.roomkit.Date.Reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.roomkit.Date.Workers.InvoiceReminderWorker
import java.util.concurrent.TimeUnit

object InvoiceReminderScheduler {
    private const val SWEEP = "roomkit_invoice_reminder_sweep_v2"
    private const val RECONCILE = "roomkit_invoice_reminder_reconcile_v2"

    /** Covers existing invoices, upgrade recovery, and later payment reversals. */
    fun start(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(SWEEP, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InvoiceReminderWorker>(6, TimeUnit.HOURS).build())
        wm.enqueueUniqueWork(RECONCILE, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<InvoiceReminderWorker>().build())
    }

    fun schedule(context: Context, invoiceId: Long, dueDate: Long) {
        require(invoiceId > 0L)
        cancelLegacy(context, invoiceId)
        start(context)
        enqueueNext(context, invoiceId, dueDate)
    }

    fun cancelLegacy(context: Context, invoiceId: Long) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("reminder_early_$invoiceId")
        wm.cancelUniqueWork("reminder_due_$invoiceId")
    }

    fun enqueueNext(context: Context, invoiceId: Long, dueDate: Long): Operation {
        val now = System.currentTimeMillis()
        val slot = InvoiceReminderPolicy.next(dueDate, now)
        val request = OneTimeWorkRequestBuilder<InvoiceReminderWorker>()
            .setInitialDelay((slot.at - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("invoiceId" to invoiceId))
            .addTag("invoice_reminder_$invoiceId")
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork(
            "invoice_reminder_v2_${invoiceId}_${dueDate}_${slot.key}",
            ExistingWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context, invoiceId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("invoice_reminder_$invoiceId")
        cancelLegacy(context, invoiceId)
        InvoiceReminderNotifications.cancel(context, invoiceId)
        // Keep the ledger: a payment reversal must not repeat an already-delivered slot.
    }
}
