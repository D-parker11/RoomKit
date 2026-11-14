package com.example.roomkit

import com.example.roomkit.Date.Reminders.InvoiceReminderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class InvoiceReminderPolicyTest {
    private fun cambodiaDate(year: Int, month: Int, day: Int): Long = Calendar.getInstance(
        TimeZone.getTimeZone(InvoiceReminderPolicy.TIME_ZONE)
    ).apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis

    @Test
    fun changingDueDateMovesTheNextReminderSlot() {
        val now = cambodiaDate(2026, Calendar.NOVEMBER, 20)
        val oldDueDate = cambodiaDate(2026, Calendar.DECEMBER, 5)
        val changedDueDate = cambodiaDate(2026, Calendar.DECEMBER, 8)

        val oldSchedule = InvoiceReminderPolicy.next(oldDueDate, now)
        val changedSchedule = InvoiceReminderPolicy.next(changedDueDate, now)

        assertEquals("DUE_SOON", oldSchedule.key)
        assertEquals(cambodiaDate(2026, Calendar.DECEMBER, 2) + 9L * 60 * 60 * 1000, oldSchedule.at)
        assertEquals("DUE_SOON", changedSchedule.key)
        assertEquals(cambodiaDate(2026, Calendar.DECEMBER, 5) + 9L * 60 * 60 * 1000, changedSchedule.at)
        assertNotEquals(oldSchedule.at, changedSchedule.at)
    }
}
