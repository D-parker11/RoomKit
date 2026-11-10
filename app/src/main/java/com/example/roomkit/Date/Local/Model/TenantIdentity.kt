package com.example.roomkit.Date.Local.Model

import java.util.Locale

object TenantIdentity {
    fun phone(value: String): String {
        val digits = value.filter { it in '0'..'9' }
        return when {
            digits.startsWith("00855") -> "0" + digits.removePrefix("00855").removePrefix("0")
            digits.startsWith("855") -> "0" + digits.removePrefix("855").removePrefix("0")
            else -> digits.ifEmpty { value.trim().lowercase(Locale.ROOT) }
        }
    }
    fun document(value: String?): String = value.orEmpty().trim().uppercase(Locale.ROOT)
}
