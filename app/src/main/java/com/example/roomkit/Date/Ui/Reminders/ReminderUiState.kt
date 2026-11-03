package com.example.roomkit.Date.Ui.Reminders

import com.example.roomkit.Date.Reminders.InvoiceReminderPolicy
import java.util.Calendar
import java.util.TimeZone

enum class ReminderFilter { OVERDUE, TODAY, UPCOMING }

data class ReminderInvoice(
    val invoiceId: Long, val roomNumber: String, val buildingName: String,
    val tenantName: String, val status: String, val totalKhr: Long,
    val paidKhr: Long, val dueDate: Long
)

data class ReminderCard(
    val invoice: ReminderInvoice,
    val balanceKhr: Long,
    val isPartial: Boolean,
    val filter: ReminderFilter,
    val reminderPending: Boolean,
    val nextReminderAt: Long
)

data class RemindersUiState(
    val loading: Boolean = true,
    val filter: ReminderFilter = ReminderFilter.OVERDUE,
    val overdueCount: Int = 0, val todayCount: Int = 0, val upcomingCount: Int = 0,
    val notificationsEnabled: Boolean = false,
    val cards: List<ReminderCard> = emptyList(),
    val error: String? = null
)

object ReminderScreenLogic {
    private fun day(timestamp: Long): Long = Calendar.getInstance(
        TimeZone.getTimeZone(InvoiceReminderPolicy.TIME_ZONE)
    ).apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun card(invoice: ReminderInvoice, now: Long, alreadyClaimed: (String) -> Boolean): ReminderCard? {
        val balance = (invoice.totalKhr - invoice.paidKhr).coerceAtLeast(0L)
        if (!InvoiceReminderPolicy.eligible(invoice.status, balance)) return null
        val filter = when {
            now > invoice.dueDate -> ReminderFilter.OVERDUE
            day(now) == day(invoice.dueDate) -> ReminderFilter.TODAY
            else -> ReminderFilter.UPCOMING
        }
        val current = InvoiceReminderPolicy.current(invoice.dueDate, now)
        val pending = current != null && !alreadyClaimed(current.key)
        return ReminderCard(invoice, balance, invoice.paidKhr > 0L, filter, pending,
            if (pending) current!!.at else InvoiceReminderPolicy.next(invoice.dueDate, now).at)
    }
}
