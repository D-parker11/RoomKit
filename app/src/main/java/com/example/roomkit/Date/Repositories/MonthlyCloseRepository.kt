package com.example.roomkit.Date.Repositories

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import com.example.roomkit.Date.Local.Entity.BillingPeriodEntity
import com.example.roomkit.Date.Local.Entity.BillingPeriodEventEntity
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.roomkit.data.local.AppDatabase
import java.time.YearMonth
import java.time.ZoneId

fun phase3Billing(context: Context, db: AppDatabase) = BillingRepository(
    context.applicationContext, db, db.buildingDao(), db.invoiceDao(), db.paymentDao(),
    db.tenancyDao(), db.tenantDao(), db.roomDao(), db.meterReadingDao()
)

/** Close prepares/ issues ongoing tenancies. Ended tenancies use their saved move-out settlement. */
class MonthlyCloseRepository(private val db: AppDatabase, private val billing: BillingRepository) {
    private val zone = ZoneId.of("Asia/Phnom_Penh")
    suspend fun state(month: YearMonth) = db.billingPeriodDao().get(month.year, month.monthValue)
    suspend fun history(month: YearMonth) = db.billingPeriodDao().history(month.year, month.monthValue)

    suspend fun close(month: YearMonth): List<Long> {
        require(month < YearMonth.now(zone)) { "Close a completed calendar month, not this month or a future month." }
        val ids = db.withTransaction {
            val dao = db.billingPeriodDao()
            val old = dao.get(month.year, month.monthValue)
            if (old?.status == "CLOSED") return@withTransaction old.invoiceIds.ids()
            if (old == null) dao.insert(BillingPeriodEntity(month.year, month.monthValue))
            val until = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val tenants = mutableListOf<Pair<Long, Long>>()
            db.openHelper.writableDatabase.query(
                "SELECT tenancyId,roomId FROM tenancies WHERE status='ACTIVE' AND moveInDate < ? ORDER BY tenancyId",
                arrayOf<Any>(until)
            ).use { c -> while (c.moveToNext()) tenants += c.getLong(0) to c.getLong(1) }
            val editor = ReplacementInvoiceRepository(db, billing)
            for ((tenancyId, roomId) in tenants) {
                var invoice = db.invoiceDao().getInvoiceForMonth(tenancyId, month.monthValue, month.year)
                if (invoice == null) {
                    val preview = billing.prepareBillingDraft(roomId, month.monthValue, month.year).getOrThrow()
                    val id = billing.persistRegularDraft(preview, preview.result.otherChargesKhr, preview.result.discountKhr, false).getOrThrow()
                    invoice = db.invoiceDao().getInvoiceById(id) ?: error("Draft was not saved.")
                }
                check(invoice.status.uppercase() !in setOf("VOID", "VOIDED")) {
                    "Tenancy #$tenancyId has a void invoice. Create its replacement draft before closing."
                }
                if (invoice.status.uppercase() == "DRAFT") editor.issueStoredDraftWithinTransaction(invoice)
            }
            // Refuse drafts left on ended tenancies; require an explicit correction/settlement decision.
            db.openHelper.writableDatabase.query(
                "SELECT COUNT(*) FROM invoices WHERE billingYear=? AND billingMonth=? AND UPPER(TRIM(status))='DRAFT'",
                arrayOf<Any>(month.year, month.monthValue)
            ).use { it.moveToFirst(); check(it.getLong(0) == 0L) { "Resolve all remaining drafts before closing this month." } }
            val all = mutableListOf<Long>()
            db.openHelper.writableDatabase.query(
                "SELECT invoiceId FROM invoices WHERE billingYear=? AND billingMonth=? AND UPPER(TRIM(status)) NOT IN ('VOID','VOIDED','DRAFT') ORDER BY invoiceId",
                arrayOf<Any>(month.year, month.monthValue)
            ).use { while (it.moveToNext()) all += it.getLong(0) }
            val now = System.currentTimeMillis()
            dao.update(BillingPeriodEntity(month.year, month.monthValue, "CLOSED", all.joinToString(","), now))
            dao.event(BillingPeriodEventEntity(billingYear=month.year, billingMonth=month.monthValue,
                action="CLOSE", reason="Reviewed monthly close", createdAt=now))
            all
        }
        // All notification side effects occur after the outer transaction has committed.
        for (id in ids) try {
            val i = db.invoiceDao().getInvoiceById(id)
            if (i != null && i.balanceKhr > 0) billing.scheduleInvoiceReminders(id, "", i.dueDate)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // The close already committed. Startup reconciliation retries reminder scheduling.
            android.util.Log.w("MonthlyClose", "Reminder reconciliation needed for invoice $id", e)
        }
        return ids
    }

    suspend fun reopen(month: YearMonth, reason: String) = db.withTransaction {
        require(reason.trim().length in 3..500) { "Enter a reason between 3 and 500 characters." }
        val old = db.billingPeriodDao().get(month.year, month.monthValue) ?: error("This month was never closed.")
        check(old.status == "CLOSED") { "This month is already open." }
        db.billingPeriodDao().update(old.copy(status="OPEN"))
        db.billingPeriodDao().event(BillingPeriodEventEntity(billingYear=month.year, billingMonth=month.monthValue,
            action="REOPEN", reason=reason.trim(), createdAt=System.currentTimeMillis()))
    }
    private fun String.ids() = if (isBlank()) emptyList() else split(',').map(String::toLong)
}
