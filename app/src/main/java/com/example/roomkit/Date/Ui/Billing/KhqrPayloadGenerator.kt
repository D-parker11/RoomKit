package com.example.roomkit.Date.Billing

import java.util.Locale

object KhqrPayloadGenerator {

    /**
     * Generates a standard EMVCo / KHQR dynamic payment string for KHR.
     * @param merchantBakongId The landlord's Bakong account (e.g., "landlord@aclb" or merchant ID)
     * @param merchantName Property or Landlord Name
     * @param billNumber Invoice reference number (e.g. "INV-0002")
     * @param amountKhr Amount strictly in whole KHR
     */
    fun generateDynamicKhqrPayload(
        merchantBakongId: String = "roomkit_landlord@bakong",
        merchantName: String,
        billNumber: String,
        amountKhr: Long
    ): String {
        val amountStr = amountKhr.toString()

        fun formatTag(tag: String, value: String): String {
            val length = String.format(Locale.US, "%02d", value.length)
            return "$tag$length$value"
        }

        // Tag 29: Merchant Account Information
        val subTagBakongId = formatTag("00", merchantBakongId)
        val tag29 = formatTag("29", subTagBakongId)

        // Tag 62: Additional Data (Bill Number / Reference)
        val subTagBill = formatTag("01", billNumber)
        val tag62 = formatTag("62", subTagBill)

        val rawPayload = StringBuilder().apply {
            append(formatTag("00", "01")) // Payload Format Indicator
            append(formatTag("01", "12")) // Point of Initiation: 12 = Dynamic QR (with amount)
            append(tag29)
            append(formatTag("52", "0000")) // Merchant Category Code
            append(formatTag("53", "116"))  // Currency: 116 = KHR
            append(formatTag("54", amountStr)) // Exact Amount Due
            append(formatTag("58", "KH"))   // Country Code: Cambodia
            append(formatTag("59", merchantName.take(25))) // Merchant / Landlord Name
            append(formatTag("60", "Phnom Penh")) // City
            append(tag62)
            append("6304") // Tag 63 (CRC) with length 04
        }.toString()

        val crc = calculateCrc16Ccitt(rawPayload)
        return rawPayload + crc
    }

    private fun calculateCrc16Ccitt(data: String): String {
        var crc = 0xFFFF
        val polynomial = 0x1021
        val bytes = data.toByteArray(Charsets.ISO_8859_1)

        for (b in bytes) {
            for (i in 0 until 8) {
                val bit = ((b.toInt() shr (7 - i)) and 1) == 1
                val c15 = ((crc shr 15) and 1) == 1
                crc = crc shl 1
                if (c15 xor bit) crc = crc xor polynomial
            }
        }
        crc = crc and 0xFFFF
        return String.format(Locale.US, "%04X", crc)
    }
}