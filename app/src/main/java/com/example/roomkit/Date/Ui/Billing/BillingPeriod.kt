package com.example.roomkit.Date.Billing

import java.util.Calendar
import java.util.Locale

object BillingPeriod {
    fun month(month: Int, year: Int): Pair<String, String> {
        require(month in 1..12 && year in 1..9999) { "Invalid billing period." }
        val calendar = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }
        val start = String.format(Locale.ROOT, "%04d-%02d-01", year, month)
        val end = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month,
            calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        return start to end
    }
    fun label(start: String?, end: String?): String? =
        if (start != null && end != null) "$start – $end" else null
}
