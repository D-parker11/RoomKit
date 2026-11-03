package com.example.roomkit.Date.Reminders

import com.example.roomkit.Date.Billing.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Presentation only: no scheduling, notification posting, or delivery-ledger changes. */
object InvoiceReminderContent {
    data class Content(val title: String, val message: String)
    private val dueDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        .withZone(ZoneId.of("Asia/Phnom_Penh"))

    private fun singleLine(value: String?, fallback: String): String =
        value?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

    fun build(
        invoiceId: Long,
        roomNumber: String?,
        tenantName: String?,
        dueDate: Long,
        remainingKhr: Long,
        slotKey: String
    ): Content {
        val room = singleLine(roomNumber, "?")
        val tenant = singleLine(tenantName, "Tenant name unavailable")
        val timing = when (slotKey) {
            "DUE_SOON" -> "Due in 3 days"
            "DUE_TODAY" -> "Due today"
            else -> "Payment overdue"
        }
        val due = dueDateFormat.format(Instant.ofEpochMilli(dueDate))
        return Content(
            title = "Invoice #$invoiceId · Room $room",
            message = "$timing\nTenant: $tenant\nDue: $due\nRemaining balance: ${CurrencyFormatter.formatKhr(remainingKhr)}"
        )
    }
}
