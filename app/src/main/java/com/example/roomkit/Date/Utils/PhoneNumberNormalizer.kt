package com.example.roomkit.Date.Utils

object PhoneNumberNormalizer {
    fun normalize(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        return phone.replace(Regex("[^0-9+]"), "")
    }
}