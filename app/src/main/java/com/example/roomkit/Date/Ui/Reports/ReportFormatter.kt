package com.example.roomkit.Date.Reports

import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReportFormatter {
    fun money(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value) + " ៛"
    fun month(value: YearMonth): String = value.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    fun time(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.of("Asia/Phnom_Penh"))
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.US))
    fun settledPercent(t: ReportTotals): Int = if (t.billed <= 0L) 0 else
        (((t.billed - t.remaining).coerceAtLeast(0L).toDouble() / t.billed) * 100).toInt().coerceIn(0, 100)
    fun occupancy(t: ReportTotals): String {
        val percent = if (t.rooms == 0L) 0 else (t.occupied.toDouble() / t.rooms * 100).toInt()
        return "$percent% occupied · ${t.occupied} / ${t.rooms} rooms"
    }
    fun summary(month: YearMonth, t: ReportTotals, updated: Long): String = """
        ROOMKIT · ${month(month)}
        Generated ${time(updated)} (Cambodia time)

        PAYMENTS DATED IN THIS MONTH
        Received: ${money(t.received)}
        Cash: ${money(t.cash)}
        Bank transfer: ${money(t.bank)}
        KHQR: ${money(t.khqr)}
        Other methods: ${money(t.otherPayments)}
        Deposit applied (not new cash): ${money(t.depositApplied)}

        INVOICES FOR THIS BILLING MONTH
        Billed: ${money(t.billed)}
        Remaining now: ${money(t.remaining)}
        Issued documents: ${t.invoiceCount}
        Fully paid: ${t.paidCount}; partially paid: ${t.partialCount}
        Overdue now: ${t.overdueCount} · ${money(t.overdueAmount)}
        Rent: ${money(t.rent)}
        Electricity: ${money(t.electricity)}
        Water: ${money(t.water)}
        Rubbish: ${money(t.rubbish)}
        Other charges: ${money(t.otherCharges)}
        Discounts deducted: ${money(t.discount)}

        PROPERTY NOW · ALL PERIODS
        ${occupancy(t)}
        Outstanding: ${money(t.allOutstanding)}
        Overdue: ${money(t.allOverdue)}
        Pending refunds: ${money(t.pendingRefund)} (${t.pendingRefundCount})

        Draft/void invoices and voided payments are excluded.
        Partial and overdue counts may overlap.
        Balances, statuses, occupancy and refunds are current, not historical month-end snapshots.
        Received payments are not profit and do not deduct expenses or deposit refunds.
    """.trimIndent()
}
