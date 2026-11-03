package com.example.roomkit.Date.Billing

import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Local.Model.AccountInvoiceWithPayments
import java.util.Locale

/** Live account information, never an invoice charge or a stored invoice total. */
data class InvoiceAccountBalance(
    val previousUnpaidKhr: Long,
    val currentInvoiceBalanceKhr: Long,
    val laterUnpaidKhr: Long,
    val totalAccountBalanceKhr: Long,
    val currentPaidKhr: Long
)

object InvoiceAccountBalances {
    private fun String.normalized() = trim().uppercase(Locale.ROOT)
    private val issued = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "OVERDUE", "PAID")

    fun activePaid(payments: List<PaymentEntity>): Long = payments
        .filter { it.status.normalized() == "ACTIVE" }
        .fold(0L) { sum, payment ->
            require(payment.amountKhr >= 0L) { "A payment has an invalid negative amount." }
            Math.addExact(sum, payment.amountKhr)
        }

    fun calculate(current: InvoiceEntity, rows: List<AccountInvoiceWithPayments>): InvoiceAccountBalance {
        require(rows.all { it.invoice.tenancyId == current.tenancyId }) { "Account contains a different tenancy." }
        val byId = rows.associateBy { it.invoice.invoiceId }
        require(byId.size == rows.size) { "Duplicate account invoice." }
        require(byId[current.invoiceId]?.invoice == current) { "Current invoice is missing or changed." }
        // A replacement retains the original's position within its billing month.
        fun rootId(invoice: InvoiceEntity): Long {
            var row = invoice
            val visited = mutableSetOf<Long>()
            while (true) {
                require(visited.add(row.invoiceId)) { "Invalid replacement chain." }
                val parentId = row.replacesInvoiceId ?: return row.invoiceId
                val parent = byId[parentId]?.invoice ?: error("Original invoice is missing.")
                require(parent.billingYear == row.billingYear && parent.billingMonth == row.billingMonth) {
                    "Replacement billing period changed."
                }
                row = parent
            }
        }
        val order = compareBy<InvoiceEntity> { it.billingYear }
            .thenBy { it.billingMonth }.thenBy { rootId(it) }.thenBy { it.invoiceId }
        var previous = 0L
        var later = 0L
        var currentBalance = 0L
        var currentPaid = 0L
        for (row in rows) {
            val invoice = row.invoice
            require(row.payments.all { it.invoiceId == invoice.invoiceId }) { "Payment belongs to another invoice." }
            val status = invoice.status.normalized()
            require(status in issued || status in setOf("DRAFT", "VOID", "VOIDED")) { "Unknown invoice status." }
            val paid = activePaid(row.payments)
            require(invoice.totalKhr >= 0L) { "Invalid invoice total." }
            val remaining = if (status in issued) (invoice.totalKhr - paid).coerceAtLeast(0L) else 0L
            when {
                invoice.invoiceId == current.invoiceId -> { currentBalance = remaining; currentPaid = paid }
                order.compare(invoice, current) < 0 -> previous = Math.addExact(previous, remaining)
                else -> later = Math.addExact(later, remaining)
            }
        }
        return InvoiceAccountBalance(previous, currentBalance, later,
            Math.addExact(Math.addExact(previous, currentBalance), later), currentPaid)
    }
}
