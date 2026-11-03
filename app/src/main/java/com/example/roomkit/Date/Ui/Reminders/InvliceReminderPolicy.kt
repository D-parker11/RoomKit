package com.example.roomkit.Date.Reminders

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Calendar-day reminders at 09:00 in the property's Cambodia time zone. */
object InvoiceReminderPolicy {
    const val TIME_ZONE = "Asia/Phnom_Penh"
    private const val DAY = 86_400_000L // Cambodia has no daylight-saving transitions.
    data class Slot(val key: String, val at: Long, val overdue: Boolean)

    private fun morning(dueDate: Long): Long = Calendar.getInstance(
        TimeZone.getTimeZone(TIME_ZONE)
    ).apply {
        timeInMillis = dueDate
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun eligible(status: String, remaining: Long): Boolean = remaining > 0L &&
            status.trim().uppercase(Locale.ROOT) in setOf(
        "ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "OVERDUE", "PAID"
    ) // Actual active payments, rather than a stale PAID label, determine debt.

    /** At most one current reminder. Missed pre-due alerts are never replayed late. */
    fun current(dueDate: Long, now: Long): Slot? {
        val due = morning(dueDate)
        val firstOverdue = due + DAY
        if (now >= firstOverdue) {
            val week = (now - firstOverdue) / (7L * DAY)
            return Slot("OVERDUE_$week", firstOverdue + week * 7L * DAY, true)
        }
        val midnightAfterDue = due + 15L * 60L * 60L * 1000L
        if (now >= due && now < midnightAfterDue) return Slot("DUE_TODAY", due, false)
        val early = due - 3L * DAY
        val midnightAfterEarly = early + 15L * 60L * 60L * 1000L
        if (now >= early && now < midnightAfterEarly) return Slot("DUE_SOON", early, false)
        return null
    }

    fun next(dueDate: Long, now: Long): Slot {
        val due = morning(dueDate)
        val early = due - 3L * DAY
        val firstOverdue = due + DAY
        return when {
            now < early -> Slot("DUE_SOON", early, false)
            now < due -> Slot("DUE_TODAY", due, false)
            now < firstOverdue -> Slot("OVERDUE_0", firstOverdue, true)
            else -> {
                val week = (now - firstOverdue) / (7L * DAY) + 1L
                Slot("OVERDUE_$week", firstOverdue + week * 7L * DAY, true)
            }
        }
    }
}
