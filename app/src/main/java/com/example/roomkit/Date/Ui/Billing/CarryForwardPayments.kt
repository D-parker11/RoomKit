@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Billing

import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Model.AccountInvoiceWithPayments
import java.security.MessageDigest
import java.util.Locale

private val collectibleStatuses = setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "OVERDUE", "PAID")

data class CarryForwardScope(
    val balances: List<CarryForwardAllocator.Balance>,
    val previousUnpaidKhr: Long,
    val currentBalanceKhr: Long,
    val dueKhr: Long,
    val fingerprint: String
)
data class CarryForwardPaymentPreview(
    val scope: CarryForwardScope,
    val allocations: List<CarryForwardAllocator.Allocation>
)

object CarryForwardPayments {
    fun scope(current: InvoiceEntity, rows: List<AccountInvoiceWithPayments>): CarryForwardScope {
        require(current.status.trim().uppercase(Locale.ROOT) in collectibleStatuses) {
            "Payments require an issued invoice."
        }
        require(current.currency == "KHR") { "Unsupported invoice currency." }
        val account = InvoiceAccountBalances.calculate(current, rows)
        val byId = rows.associateBy { it.invoice.invoiceId }
        fun rootId(inv: InvoiceEntity): Long {
            var row = inv
            val seen = mutableSetOf<Long>()
            while (true) {
                require(seen.add(row.invoiceId)) { "Invalid replacement chain." }
                val id = row.replacesInvoiceId ?: return row.invoiceId
                val parent = byId[id]?.invoice ?: error("Original invoice is missing.")
                require(parent.tenancyId == current.tenancyId && parent.billingYear == row.billingYear && parent.billingMonth == row.billingMonth)
                row = parent
            }
        }
        val order = compareBy<InvoiceEntity> { it.billingYear }.thenBy { it.billingMonth }
            .thenBy { rootId(it) }.thenBy { it.invoiceId }
        val eligible = rows.filter {
            it.invoice.status.trim().uppercase(Locale.ROOT) in collectibleStatuses &&
                    order.compare(it.invoice, current) <= 0
        }.sortedWith { a, b -> order.compare(a.invoice, b.invoice) }
        val balances = eligible.map { row ->
            require(row.invoice.currency == "KHR") { "Account contains another currency." }
            val paid = InvoiceAccountBalances.activePaid(row.payments)
            require(paid <= row.invoice.totalKhr) { "Invoice payments exceed its charges. Check the payment history." }
            CarryForwardAllocator.Balance(row.invoice.invoiceId, Math.subtractExact(row.invoice.totalKhr, paid))
        }
        val due = CarryForwardAllocator.total(balances)
        check(due == Math.addExact(account.previousUnpaidKhr, account.currentInvoiceBalanceKhr))
        // Include statuses, totals, and payment identities. Voids/new payments force a fresh preview.
        val signature = buildString {
            append(current.invoiceId).append('|')
            eligible.forEach { row ->
                append(row.invoice.invoiceId).append(':').append(row.invoice.totalKhr)
                    .append(':').append(row.invoice.status).append('|')
                row.payments.sortedBy { it.paymentId }.forEach {
                    append(it.paymentId).append(':').append(it.amountKhr).append(':').append(it.status).append(';')
                }
            }
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return CarryForwardScope(balances, account.previousUnpaidKhr, account.currentInvoiceBalanceKhr, due, fingerprint)
    }
}
