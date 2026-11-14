package com.example.roomkit

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.Date.Reminders.InvoiceReminderPolicy
import com.example.roomkit.Date.Workers.InvoiceReminderWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InvoiceReminderSchedulerTest {

    @Test
    fun schedulingChangedDueDateEnqueuesRequestKeyedToNewDate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val workManager = WorkManager.getInstance(context)
        val oldDueDate = futureCambodiaDate(30)
        val newDueDate = futureCambodiaDate(33)

        InvoiceReminderScheduler.schedule(context, invoiceId = 9001L, dueDate = oldDueDate)
        InvoiceReminderScheduler.schedule(context, invoiceId = 9001L, dueDate = newDueDate)

        val oldWork = workManager.getWorkInfosForUniqueWork(
            uniqueName(9001L, oldDueDate, "DUE_SOON")
        ).get()
        val newWork = workManager.getWorkInfosForUniqueWork(
            uniqueName(9001L, newDueDate, "DUE_SOON")
        ).get()

        assertEquals(1, oldWork.size)
        assertEquals(1, newWork.size)
        assertTrue(oldWork.single().tags.contains("invoice_reminder_9001"))
        assertTrue(newWork.single().tags.contains("invoice_reminder_9001"))
    }

    @Test
    fun clockJumpWithinDueSoonSlotKeepsTheOldUniqueWorkUnderKeep() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        val workManager = WorkManager.getInstance(context)
        val invoiceId = 9002L
        val dueDate = cambodiaDateTime(2026, Calendar.DECEMBER, 5, 0, 0)
        val timeBeforeClockJump = cambodiaDateTime(2026, Calendar.NOVEMBER, 28, 8, 10)
        val timeAfterClockJump = cambodiaDateTime(2026, Calendar.DECEMBER, 2, 8, 0)
        val oldSlot = InvoiceReminderPolicy.next(dueDate, timeBeforeClockJump)
        val reconciledSlot = InvoiceReminderPolicy.next(dueDate, timeAfterClockJump)

        assertEquals("DUE_SOON", oldSlot.key)
        assertEquals(oldSlot.key, reconciledSlot.key)
        assertEquals(oldSlot.at, reconciledSlot.at)

        val uniqueName = uniqueName(invoiceId, dueDate, oldSlot.key)
        val originalRequest = OneTimeWorkRequestBuilder<InvoiceReminderWorker>()
            .setInitialDelay(oldSlot.at - timeBeforeClockJump, TimeUnit.MILLISECONDS)
            .addTag("original-delay")
            .build()
        val shortenedRequest = OneTimeWorkRequestBuilder<InvoiceReminderWorker>()
            .setInitialDelay(reconciledSlot.at - timeAfterClockJump, TimeUnit.MILLISECONDS)
            .addTag("recomputed-delay")
            .build()

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, originalRequest)
            .result.get()
        val originalWork = workManager.getWorkInfosForUniqueWork(uniqueName).get().single()

        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, shortenedRequest)
            .result.get()
        val retainedWork = workManager.getWorkInfosForUniqueWork(uniqueName).get().single()

        assertEquals(originalWork.id, retainedWork.id)
        assertTrue(retainedWork.tags.contains("original-delay"))
        assertFalse(retainedWork.tags.contains("recomputed-delay"))
    }

    private fun uniqueName(invoiceId: Long, dueDate: Long, slot: String) =
        "invoice_reminder_v2_${invoiceId}_${dueDate}_${slot}"

    private fun futureCambodiaDate(daysAhead: Int): Long = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Phnom_Penh")
    ).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, daysAhead)
    }.timeInMillis

    private fun cambodiaDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Phnom_Penh")).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
