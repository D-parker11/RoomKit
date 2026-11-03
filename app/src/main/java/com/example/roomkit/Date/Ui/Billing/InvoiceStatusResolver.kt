package com.example.roomkit.Date.Billing

import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

enum class DocumentStatus { DRAFT, ISSUED, VOID, UNKNOWN }
enum class PaymentStatus { NOT_APPLICABLE, UNPAID, PARTIALLY_PAID, PAID }

data class InvoiceStatusSummary(
    val documentStatus: DocumentStatus,
    val paymentStatus: PaymentStatus,
    val isOverdue: Boolean,
    val remainingKhr: Long
)

object InvoiceStatusResolver {
    fun resolve(
        storedStatus: String,
        totalKhr: Long,
        amountPaidKhr: Long,
        dueDateTimestamp: Long,
        currentTimestamp: Long = System.currentTimeMillis()
    ): InvoiceStatusSummary {
        require(totalKhr >= 0 && amountPaidKhr >= 0) { "Amounts cannot be negative." }
        val document = when (storedStatus.trim().uppercase(Locale.ROOT)) {
            "DRAFT" -> DocumentStatus.DRAFT
            "VOID", "VOIDED" -> DocumentStatus.VOID
            "ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE" -> DocumentStatus.ISSUED
            else -> DocumentStatus.UNKNOWN
        }
        if (document != DocumentStatus.ISSUED) {
            return InvoiceStatusSummary(document, PaymentStatus.NOT_APPLICABLE, false, 0L)
        }
        val remaining = if (amountPaidKhr >= totalKhr) 0L else totalKhr - amountPaidKhr
        val payment = when {
            remaining == 0L -> PaymentStatus.PAID
            amountPaidKhr > 0L -> PaymentStatus.PARTIALLY_PAID
            else -> PaymentStatus.UNPAID
        }
        return InvoiceStatusSummary(document, payment,
            remaining > 0L && currentTimestamp > dueDateTimestamp, remaining)
    }
}

// Immediate refresh on subscription, then once per minute while collected.
// Preserve the app's existing exact-timestamp overdue boundary.
fun invoiceStatusClock() = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000L)
    }
}
