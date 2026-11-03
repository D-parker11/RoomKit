package com.example.roomkit.Date.Billing

import java.util.Locale

/** All monetary values are whole Cambodian riel, never cents. */
object CurrencyFormatter {
    fun formatKhr(riel: Long): String = String.format(Locale.US, "%,d ៛", riel)

    fun parseKhrInput(input: String): Long? {
        val cleaned = input.trim().removeSuffix("៛").trim()
        if (cleaned.isEmpty() || cleaned.any { it !in '0'..'9' }) return null
        return cleaned.toLongOrNull()?.takeIf { it >= 0L }
    }
}
