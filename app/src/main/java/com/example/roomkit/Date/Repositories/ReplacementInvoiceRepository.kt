package com.example.roomkit.Date.Repositories

import androidx.room.withTransaction
import com.example.roomkit.Date.Billing.BillingCalculator
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Model.InvoiceWithDetails
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** Invoice corrections are saved snapshots; this editor never rewrites meter history. */
class ReplacementInvoiceRepository(
    private val db: AppDatabase,
    private val billing: BillingRepository
) {
    private val invoices = db.invoiceDao()
    private fun String.normalized() = trim().uppercase(Locale.ROOT)

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun createOrOpen(originalId: Long): Result<InvoiceWithDetails> = attempt {
        db.withTransaction {
            require(originalId > 0) { "Invalid original invoice." }
            val original = invoices.getInvoiceById(originalId) ?: error("Invoice not found.")
            check(original.status.normalized() in setOf("VOID", "VOIDED")) {
                "Only voided invoices can be replaced."
            }
            check(original.invoiceKind == "REGULAR") {
                "Final settlement invoices must be corrected through the move-out workflow."
            }
            check(!invoices.hasActivePayments(originalId) && original.amountPaidKhr == 0L) {
                "The original invoice has active payments or inconsistent balances."
            }
            check(!invoices.isLinkedToMoveOutSettlement(originalId)) {
                "An invoice linked to a move-out settlement cannot be replaced here."
            }
            // Idempotent: double taps and reopening always use the existing child.
            val existing = invoices.getReplacementForInvoice(originalId)
            val id = if (existing != null) existing.invoiceId else {
                check(!invoices.hasCurrentInvoiceForKind(
                    original.tenancyId, original.billingMonth, original.billingYear,
                    original.invoiceKind
                )) { "Another draft or issued invoice already exists for this period." }
                val revision = Math.addExact(invoices.getMaxInvoiceRevision(
                    original.tenancyId, original.billingMonth, original.billingYear,
                    original.invoiceKind
                ), 1)
                invoices.insertInvoice(original.copy(
                    invoiceId = 0L,
                    replacesInvoiceId = originalId,
                    revision = revision,
                    status = "DRAFT",
                    amountPaidKhr = 0L,
                    balanceKhr = 0L, // Drafts are not collectible debt.
                    issueDate = 0L,
                    createdAt = System.currentTimeMillis(),
                    voidedAt = null,
                    voidReason = null
                )).also { newId ->
                    db.writeDraftItems(invoices.getInvoiceById(newId) ?: error("Draft not found."))
                }
            }
            invoices.getInvoiceDetailById(id) ?: error("Replacement could not be loaded.")
        }
    }

    suspend fun load(id: Long): Result<InvoiceWithDetails> = attempt {
        val detail = invoices.getInvoiceDetailById(id) ?: error("Replacement not found.")
        check(detail.invoice.invoiceKind == "REGULAR") { "This editor supports regular invoices only." }
        detail
    }

    /** UI sends only editable numeric fields, never IDs, status, balances or link fields. */
    fun calculateDraft(old: InvoiceEntity, values: Map<String, String>): InvoiceEntity {
        fun number(key: String): Long = values[key]?.trim()?.toLongOrNull()
            ?.also { require(it >= 0L) { "$key must not be negative." } }
            ?: error("Enter a valid whole number for $key.")
        require(old.currency == "KHR") { "Unsupported invoice currency." }
        val metered = old.waterBillingMethod.normalized() != "FIXED"
        val ep = number("electricityPrevious")
        val ec = number("electricityCurrent")
        val wp = if (metered) number("waterPrevious") else 0L
        val wc = if (metered) number("waterCurrent") else 0L
        val er = number("electricityRate")
        val wr = number("waterRate")
        val rent = number("rent")
        val rubbish = number("rubbish")
        val other = number("other")
        val discount = number("discount")
        // Exact arithmetic rejects Long overflow, including enormous pasted values.
        val result = BillingCalculator.calculate(BillingCalculator.BillingInput(
            rentKhr = rent, previousElectricity = ep, currentElectricity = ec,
            electricityRateKhr = er, isWaterMeter = metered,
            previousWater = wp, currentWater = wc, waterRateKhr = wr,
            fixedWaterFeeKhr = wr, rubbishFeeKhr = rubbish,
            otherChargesKhr = other, discountKhr = discount,
            electricityCarriedUsage = old.electricityCarriedUsage, waterCarriedUsage = old.waterCarriedUsage
        ))
        return old.copy(
            discountReason = com.example.roomkit.Date.Billing.InvoiceFieldValidation.discountReason(values["discountReason"]),
            rentKhr = rent, electricityPrevious = ep, electricityCurrent = ec,
            electricityUsage = result.electricityUsage,
            electricityRateKhrSnapshot = er, electricityChargeKhr = result.electricityChargeKhr,
            waterPrevious = if (metered) wp else null,
            waterCurrent = if (metered) wc else null,
            waterUsage = if (metered) result.waterUsage else null,
            waterRateKhrSnapshot = if (metered) wr else null,
            fixedWaterFeeKhrSnapshot = if (metered) null else wr,
            waterChargeKhr = result.waterChargeKhr,
            rubbishFeeKhrSnapshot = rubbish, otherChargesKhr = other,
            discountKhr = discount, utilitySubtotalKhr = result.utilitySubtotalKhr,
            totalKhr = result.totalKhr, balanceKhr = 0L
        )
    }

    private suspend fun checkedDraft(expected: InvoiceEntity): InvoiceEntity {
        val current = invoices.getInvoiceById(expected.invoiceId) ?: error("Draft not found.")
        check(current.status.normalized() == "DRAFT" && current.invoiceKind == "REGULAR") {
            "This invoice is no longer editable. Reopen it to see its status."
        }
        check(current == expected) { "The draft changed elsewhere. Reopen it before saving." }
        check(!invoices.hasActivePayments(current.invoiceId)) { "Drafts cannot have payments." }
        check(current.amountPaidKhr == 0L && current.balanceKhr == 0L) { "Draft balances are inconsistent." }
        check(!invoices.isLinkedToMoveOutSettlement(current.invoiceId)) { "Settlement invoices cannot be edited here." }
        if (current.replacesInvoiceId != null) {
            val original = invoices.getInvoiceById(current.replacesInvoiceId) ?: error("Original invoice not found.")
            check(original.status.normalized() in setOf("VOID", "VOIDED")) { "The original invoice must remain voided." }
        } else {
            val tenancy = db.tenancyDao().getTenancyById(current.tenancyId) ?: error("Tenancy not found.")
            check(tenancy.status.normalized() == "ACTIVE") { "This tenancy ended. The regular draft cannot be saved or issued." }
        }
        return current
    }

    suspend fun deleteRegularDraft(expected: InvoiceEntity): Result<Unit> = attempt {
        db.withTransaction {
            val current = invoices.getInvoiceById(expected.invoiceId) ?: error("Draft no longer exists.")
            check(current == expected) { "Draft changed. Reopen it before deleting." }
            // If the guarded parent delete fails, the transaction restores these items too.
            db.invoiceItemDao().deleteForDraft(current.invoiceId)
            check(invoices.deleteUnusedRegularDraft(current.invoiceId) == 1) {
                "Only a regular draft without payments or history links can be deleted."
            }
            db.billingDraftAdjustmentDao().delete(current.tenancyId, current.billingMonth, current.billingYear)
        }
    }

    suspend fun save(expected: InvoiceEntity, values: Map<String, String>): Result<InvoiceEntity> = attempt {
        db.withTransaction {
            val edited = calculateDraft(checkedDraft(expected), values)
            invoices.updateInvoice(edited)
            db.writeDraftItems(edited)
            edited
        }
    }

    /** Used only by the outer all-or-nothing month-close transaction. No reminder side effects. */
    internal suspend fun issueStoredDraftWithinTransaction(expected: InvoiceEntity): InvoiceEntity {
        check(db.inTransaction())
        val old = checkedDraft(expected)
        val values = mapOf(
            "rent" to old.rentKhr.toString(), "electricityPrevious" to (old.electricityPrevious ?: 0L).toString(),
            "electricityCurrent" to (old.electricityCurrent ?: 0L).toString(),
            "electricityRate" to old.electricityRateKhrSnapshot.toString(),
            "waterPrevious" to (old.waterPrevious ?: 0L).toString(), "waterCurrent" to (old.waterCurrent ?: 0L).toString(),
            "waterRate" to (if (old.waterBillingMethod.equals("FIXED", true)) old.fixedWaterFeeKhrSnapshot ?: 0L else old.waterRateKhrSnapshot ?: 0L).toString(),
            "rubbish" to old.rubbishFeeKhrSnapshot.toString(), "other" to old.otherChargesKhr.toString(),
            "discount" to old.discountKhr.toString(), "discountReason" to old.discountReason.orEmpty()
        )
        val edited = calculateDraft(old, values)
        db.writeDraftItems(edited)
        val issued = edited.copy(status=if (edited.totalKhr == 0L) "PAID" else "ISSUED",
            balanceKhr=edited.totalKhr, issueDate=System.currentTimeMillis())
        invoices.updateInvoice(issued)
        return issued
    }

    suspend fun issue(expected: InvoiceEntity, values: Map<String, String>): Result<Long> = attempt {
        val detail = db.withTransaction {
            val edited = calculateDraft(checkedDraft(expected), values)
            invoices.updateInvoice(edited)
            db.writeDraftItems(edited)
            val issued = edited.copy(
                status = if (edited.totalKhr == 0L) "PAID" else "ISSUED",
                balanceKhr = edited.totalKhr,
                issueDate = System.currentTimeMillis()
            )
            invoices.updateInvoice(issued) // Same saved draft ID; never insert here.
            invoices.getInvoiceDetailById(issued.invoiceId) ?: error("Invoice not found.")
        }
        try {
            if (detail.invoice.balanceKhr > 0L) billing.scheduleInvoiceReminders(
                detail.invoice.invoiceId, detail.roomNumber, detail.invoice.dueDate
            )
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("ReplacementInvoice", "Invoice issued; reminder scheduling failed", e)
        }
        detail.invoice.invoiceId
    }
}
