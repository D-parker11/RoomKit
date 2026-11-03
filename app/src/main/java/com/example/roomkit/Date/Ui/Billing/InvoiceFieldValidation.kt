package com.example.roomkit.Date.Billing

object InvoiceFieldValidation {
    const val DISCOUNT_REASON_LIMIT = 500
    const val PAYMENT_REFERENCE_LIMIT = 100
    fun discountReason(value: String?): String? = optional(value, DISCOUNT_REASON_LIMIT, "Discount reason")
    fun paymentReference(value: String?): String? = optional(value, PAYMENT_REFERENCE_LIMIT, "Payment reference")
    private fun optional(value: String?, limit: Int, label: String): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(clean.length <= limit) { "$label must be $limit characters or fewer." }
        return clean
    }
}
