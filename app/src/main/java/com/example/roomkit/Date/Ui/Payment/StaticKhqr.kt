@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Payment

/** Supported offline subset only; format validation does NOT verify account ownership. */
data class StaticKhqr(
    val payload: String,
    val receiver: String,
    val accountId: String,
    val bank: String
)

object StaticKhqrParser {
    fun parse(payload: String): StaticKhqr {
        require(payload.length in 30..4096 && payload.all { it.code in 32..126 }) {
            "Unsupported QR text. Import a reusable KHR receiving QR from your bank."
        }
        val fields = tlv(payload)
        require(
            fields.keys.last() == "63" && fields["63"]?.length == 4 && payload.takeLast(8)
                .startsWith("6304")
        ) {
            "Invalid KHQR checksum field."
        }
        require(
            fields.getValue("63").equals(crc(payload.dropLast(4)), true)
        ) { "Damaged QR code. Please import it again." }
        require(fields["00"] == "01" && fields["58"] == "KH") { "This is not a supported Cambodian receiving QR." }
        require(fields["01"] == "11") { "Use a reusable static QR, not a transaction or expiring QR." }
        require(fields["53"] == "116") { "Please import your KHR receiving QR. USD QR codes are not supported." }
        require(fields["52"]?.matches(Regex("[0-9]{4}")) == true) { "Missing merchant category." }
        require(!fields["60"].isNullOrBlank()) { "Missing merchant city." }
        require(fields["54"] == null || fields["54"]!!.matches(Regex("0+(\\.0+)?"))) {
            "This QR contains an amount. Import a receiving QR without an amount."
        }
        require((55..57).none { fields.containsKey(it.toString()) }) { "QR codes with payment fees or tips are not supported." }
        val accountTags = listOf("29", "30").filter { fields.containsKey(it) }
        require(accountTags.size == 1) { "Unsupported receiving account format." }
        val account = tlv(fields.getValue(accountTags.single()))
        val id = account["00"].orEmpty()
        require(id.isNotBlank() && id.contains('@')) { "Missing Bakong receiving account ID." }
        if (accountTags.single() == "30") {
            require(!account["01"].isNullOrBlank() && !account["02"].isNullOrBlank()) { "Incomplete merchant account." }
        }
        fields["99"]?.let { require(!tlv(it).containsKey("01")) { "Expiring QR codes cannot be saved as your reusable QR." } }
        fields["62"]?.let {
            val extra = tlv(it)
            require(listOf("01", "05").none { key -> !extra[key].isNullOrBlank() }) {
                "This QR includes a bill or transaction reference. Import your general receiving QR."
            }
        }
        val name = fields["59"].orEmpty()
        require(name.isNotBlank()) { "Missing receiver name." }
        return StaticKhqr(payload, name, id, account["02"].orEmpty())
    }

    private fun tlv(text: String): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        var offset = 0
        while (offset < text.length) {
            require(offset + 4 <= text.length) { "Incomplete KHQR field." }
            val tag = text.substring(offset, offset + 2)
            val lengthText = text.substring(offset + 2, offset + 4)
            require(tag.all { it in '0'..'9' } && lengthText.all { it in '0'..'9' }) { "Invalid KHQR field." }
            val length = lengthText.toInt()
            offset += 4
            require(length > 0 && offset + length <= text.length && !result.containsKey(tag)) { "Invalid or duplicate KHQR field." }
            result[tag] = text.substring(offset, offset + length)
            offset += length
        }
        return result
    }

    internal fun crc(text: String): String {
        var value = 0xFFFF
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            value = value xor ((byte.toInt() and 255) shl 8)
            repeat(8) {
                value = if (value and 0x8000 != 0) (value shl 1) xor 0x1021 else value shl 1
            }
            value = value and 0xFFFF
        }
        return value.toString(16).uppercase(java.util.Locale.ROOT).padStart(4, '0')
    }
}
