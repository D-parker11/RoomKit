package com.example.roomkit.Date.Backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.roomkit.Date.Billing.InvoiceItems
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

/** Versioned encrypted JSON. Data is validated in a private temporary Room DB before replacement. */
class JsonBackupRepository(context: Context, private val db: AppDatabase) {
    private val context = context.applicationContext
    companion object {
        private const val CURRENT_SCHEMA_VERSION = 12
        private val SUPPORTED_SCHEMA_VERSIONS = setOf(11, CURRENT_SCHEMA_VERSION)
        private val SCHEMA_12_SETTLEMENT_COLUMNS = setOf("chargeReason", "refundPaidAt", "refundReference")
        val TABLES = listOf("buildings", "rooms", "tenants", "tenancies", "meter_readings", "invoices",
            "invoice_items", "payments", "move_out_settlements", "billing_draft_adjustments",
            "billing_periods", "billing_period_events")
    }
    suspend fun export(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val snapshot = db.withTransaction {
            val tables = JSONObject()
            for (table in TABLES) {
                val rows = JSONArray()
                db.openHelper.writableDatabase.query("SELECT * FROM `$table`").use { c ->
                    while (c.moveToNext()) {
                        val row = JSONObject()
                        for (i in 0 until c.columnCount) row.put(c.getColumnName(i), when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_STRING -> c.getString(i)
                            else -> error("Unexpected binary column in $table")
                        })
                        rows.put(row)
                    }
                }
                tables.put(table, rows)
            }
            JSONObject().put("format", "ROOMKIT_DATA").put("schemaVersion", CURRENT_SCHEMA_VERSION)
                .put("createdAt", System.currentTimeMillis()).put("tables", tables).toString().toByteArray(Charsets.UTF_8)
        }
        val encrypted = try { BackupCrypto.encrypt(snapshot, password) } finally { snapshot.fill(0) }
        val encoder = Base64.getEncoder()
        val outer = JSONObject().put("format", "ROOMKIT_ENCRYPTED_JSON").put("version", 1)
            .put("kdf", "PBKDF2-HMAC-SHA256").put("iterations", BackupCrypto.ITERATIONS)
            .put("cipher", "AES-256-GCM").put("salt", encoder.encodeToString(encrypted.salt))
            .put("iv", encoder.encodeToString(encrypted.iv)).put("data", encoder.encodeToString(encrypted.ciphertext))
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(outer.toString().toByteArray(Charsets.UTF_8)) }
            ?: error("The selected destination cannot be written.")
    }

    /** Confirmation must be obtained by the UI before calling this destructive replacement. */
    suspend fun restore(uri: Uri, password: CharArray) = withContext(Dispatchers.IO) {
        val outerBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(29 * 1024 * 1024) }
            ?: error("The selected backup cannot be opened.")
        val outer = JSONObject(outerBytes.toString(Charsets.UTF_8))
        require(outer.getString("format") == "ROOMKIT_ENCRYPTED_JSON" && outer.getInt("version") == 1 &&
            outer.getInt("iterations") == BackupCrypto.ITERATIONS && outer.getString("kdf") == "PBKDF2-HMAC-SHA256" &&
            outer.getString("cipher") == "AES-256-GCM") { "Unsupported backup format." }
        val decoder = Base64.getDecoder()
        val plain = try {
            BackupCrypto.decrypt(BackupCrypto.Envelope(decoder.decode(outer.getString("salt")),
                decoder.decode(outer.getString("iv")), decoder.decode(outer.getString("data"))), password)
        } catch (e: Exception) { throw IllegalArgumentException("Wrong password or damaged backup.", e) }
        val payload = try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        val schemaVersion = payload.getInt("schemaVersion")
        require(payload.getString("format") == "ROOMKIT_DATA" && schemaVersion in SUPPORTED_SCHEMA_VERSIONS) {
            "Only RoomKit schema 11 or 12 backups are supported. No data was changed."
        }
        val tables = payload.getJSONObject("tables")
        require(tables.keys().asSequence().toSet() == TABLES.toSet()) { "Backup tables are incomplete or unsupported." }
        val name = "restore_validation_${UUID.randomUUID()}.db"
        val probe = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val oldInvoiceIds = mutableListOf<Long>()
        try {
            probe.withTransaction { importRows(probe.openHelper.writableDatabase, tables, schemaVersion) }
            validate(probe)
            // Existing workers and UI writes serialize on this transaction. DDL and row changes
            // roll back together on any failure. No file swap or live-database deletion is used.
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                sql.query("SELECT invoiceId FROM invoices").use { while (it.moveToNext()) oldInvoiceIds += it.getLong(0) }
                val triggers = mutableListOf<Pair<String, String>>()
                sql.query("SELECT name,sql FROM sqlite_master WHERE type='trigger' AND sql IS NOT NULL").use {
                    while (it.moveToNext()) triggers += it.getString(0) to it.getString(1)
                }
                for ((n, _) in triggers) sql.execSQL("DROP TRIGGER `${n.replace("`", "``")}`")
                sql.execSQL("DELETE FROM billing_period_events")
                sql.execSQL("DELETE FROM billing_periods")
                for (t in listOf("move_out_settlements", "payments", "invoice_items", "billing_draft_adjustments")) sql.execSQL("DELETE FROM `$t`")
                // Replacement links use RESTRICT. Remove leaves first, preserving rollback on cycles.
                while (count(sql, "SELECT COUNT(*) FROM invoices") > 0) {
                    sql.execSQL("DELETE FROM invoices WHERE invoiceId NOT IN (SELECT replacesInvoiceId FROM invoices WHERE replacesInvoiceId IS NOT NULL)")
                    check(count(sql, "SELECT changes()") > 0) { "Existing invoice replacement links are inconsistent." }
                }
                for (t in listOf("meter_readings", "tenancies", "tenants", "rooms", "buildings")) sql.execSQL("DELETE FROM `$t`")
                importRows(sql, tables, schemaVersion)
                for ((_, definition) in triggers) sql.execSQL(definition)
                // Room's invalidation triggers are TEMP triggers; they are never removed above.
                sql.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Restored relationships are invalid." } }
            }
        } finally {
            probe.close()
            context.deleteDatabase(name)
        }
        // Do not erase this device's delivery ledger, which would duplicate already sent alerts.
        runCatching {
            for (id in oldInvoiceIds) {
                androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag("invoice_reminder_$id").result.get()
                InvoiceReminderNotifications.cancel(context, id)
            }
            InvoiceReminderScheduler.start(context)
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "roomkit_restore_reconcile", androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<com.example.roomkit.Date.Workers.InvoiceReminderWorker>().build()
            ).result.get()
        }
        Unit
    }

    private fun importRows(sql: SupportSQLiteDatabase, tables: JSONObject, schemaVersion: Int) {
        var total = 0
        for (t in TABLES) {
            val columns = linkedMapOf<String, String>()
            sql.query("PRAGMA table_info(`$t`)").use { while (it.moveToNext()) columns[it.getString(1)] = it.getString(2) }
            val rows = tables.getJSONArray(t)
            val v12Columns = if (t == "move_out_settlements" && schemaVersion == 11) SCHEMA_12_SETTLEMENT_COLUMNS else emptySet()
            val requiredKeys = columns.keys - v12Columns
            total = Math.addExact(total, rows.length())
            require(total <= 100_000) { "Backup contains too many records." }
            val ordered = (0 until rows.length()).map { rows.getJSONObject(it) }.let { list ->
                if (t == "invoices") list.sortedBy { it.getLong("revision") } else list
            }
            for (row in ordered) {
                require(row.keys().asSequence().toSet() == requiredKeys) { "Unsupported columns in $t." }
                val values = columns.map { (key, type) ->
                    val value = if (key in v12Columns && !row.has(key)) {
                        if (key == "chargeReason") "" else null
                    } else if (row.isNull(key)) null else row.get(key)
                    when {
                        value == null -> null
                        type == "INTEGER" -> { require(value is Int || value is Long) { "Invalid whole number in $t.$key" }; (value as Number).toLong() }
                        type == "REAL" -> { require(value is Number && value.toDouble().isFinite()); value.toDouble() }
                        type == "TEXT" -> { require(value is String && value.length <= 20_000); value }
                        else -> error("Unsupported column type.")
                    }
                }.toTypedArray<Any?>()
                sql.execSQL("INSERT INTO `$t` (${columns.keys.joinToString(",") { "`$it`" }}) VALUES (${columns.keys.joinToString(",") { "?" }})", values)
            }
        }
    }

    private suspend fun validate(candidate: AppDatabase) {
        val sql = candidate.openHelper.writableDatabase
        sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Backup has broken relationships." } }
        require(count(sql, "SELECT COUNT(*) FROM buildings") <= 1) { "Backup has multiple properties; review legacy data before restore." }
        for (query in listOf(
            "SELECT COUNT(*) FROM (SELECT roomId FROM tenancies WHERE status='ACTIVE' GROUP BY roomId HAVING COUNT(*)>1)",
            "SELECT COUNT(*) FROM (SELECT tenantId FROM tenancies WHERE status='ACTIVE' GROUP BY tenantId HAVING COUNT(*)>1)",
            "SELECT COUNT(*) FROM tenancies WHERE status NOT IN ('ACTIVE','ENDED') OR dueDay NOT BETWEEN 1 AND 28 OR monthlyRent<0 OR deposit<0 OR electricityRate<0 OR waterRate<0 OR rubbishFee<0 OR (moveOutDate IS NOT NULL AND moveOutDate<moveInDate)",
            "SELECT COUNT(*) FROM meter_readings m WHERE tenancyId<>0 AND NOT EXISTS(SELECT 1 FROM tenancies t WHERE t.tenancyId=m.tenancyId AND t.roomId=m.roomId)",
            "SELECT COUNT(*) FROM (SELECT tenancyId,roomId,meterType,readingType FROM meter_readings WHERE readingType IN ('INITIAL','FINAL') GROUP BY tenancyId,roomId,meterType,readingType HAVING COUNT(*)>1)",
            "SELECT COUNT(*) FROM payments WHERE amountKhr<=0 OR status NOT IN ('ACTIVE','VOIDED') OR (status='VOIDED' AND (voidedAt IS NULL OR LENGTH(TRIM(COALESCE(voidReason,'')))=0))",
            "SELECT COUNT(*) FROM (SELECT tenancyId,billingYear,billingMonth,invoiceKind FROM invoices WHERE status NOT IN ('VOID','VOIDED') GROUP BY tenancyId,billingYear,billingMonth,invoiceKind HAVING COUNT(*)>1)",
            "SELECT COUNT(*) FROM meter_readings WHERE readingType NOT IN ('INITIAL','MONTHLY','FINAL','METERREPLACEMENT') OR meterType NOT IN ('ELECTRICITY','WATER') OR previousReading<0 OR currentReading<0 OR carriedUsage<0 OR meterSequence<0 OR billingMonth NOT BETWEEN 1 AND 12 OR billingYear NOT BETWEEN 1 AND 9999 OR (readingType<>'METERREPLACEMENT' AND currentReading<previousReading)",
            "SELECT COUNT(*) FROM billing_periods WHERE status NOT IN ('OPEN','CLOSED') OR billingMonth NOT BETWEEN 1 AND 12 OR billingYear NOT BETWEEN 1 AND 9999",
            "SELECT COUNT(*) FROM billing_period_events e WHERE action NOT IN ('CLOSE','REOPEN') OR LENGTH(TRIM(reason))=0 OR NOT EXISTS(SELECT 1 FROM billing_periods p WHERE p.billingYear=e.billingYear AND p.billingMonth=e.billingMonth)",
            "SELECT COUNT(*) FROM billing_periods p JOIN invoices i ON i.billingYear=p.billingYear AND i.billingMonth=p.billingMonth WHERE p.status='CLOSED' AND i.status='DRAFT'",
            "SELECT COUNT(*) FROM meter_readings WHERE (readingType<>'METERREPLACEMENT' AND eventOrdinal<>0) OR (readingType='METERREPLACEMENT' AND (eventOrdinal<>meterSequence OR meterSequence<1 OR LENGTH(TRIM(COALESCE(reason,'')))=0))",
            "SELECT COUNT(*) FROM move_out_settlements s WHERE depositHeldKhr<0 OR appliedKhr<0 OR deductionsKhr<0 OR refundKhr<0 OR debtKhr<0 OR refundPaid NOT IN (0,1) OR NOT EXISTS(SELECT 1 FROM tenancies t WHERE t.tenancyId=s.tenancyId AND t.roomId=s.roomId AND t.status='ENDED') OR (finalInvoiceId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM invoices i WHERE i.invoiceId=s.finalInvoiceId AND i.tenancyId=s.tenancyId AND i.invoiceKind='FINAL'))",
            "SELECT COUNT(*) FROM move_out_settlements WHERE (refundPaid=1 AND refundKhr<=0) OR (refundPaidAt IS NOT NULL AND refundPaid=0)",
            "SELECT COUNT(*) FROM invoices i WHERE replacesInvoiceId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM invoices o WHERE o.invoiceId=i.replacesInvoiceId AND o.status IN ('VOID','VOIDED') AND o.tenancyId=i.tenancyId AND o.billingYear=i.billingYear AND o.billingMonth=i.billingMonth AND o.invoiceKind=i.invoiceKind AND o.revision<i.revision)"
        )) require(count(sql, query) == 0L) { "Backup contains inconsistent business records." }
        for (i in candidate.invoiceDao().getAllOnceForBackup()) {
            require(i.currency == "KHR" && i.totalKhr >= 0 && i.invoiceKind in setOf("REGULAR", "FINAL") && i.billingMonth in 1..12 && i.billingYear in 1..9999)
            InvoiceItems.validate(i, candidate.invoiceItemDao().getForInvoice(i.invoiceId))
            val paid = candidate.paymentDao().getTotalPaidForInvoice(i.invoiceId)
            require(paid in 0..i.totalKhr && paid == i.amountPaidKhr) { "Backup payment balances are inconsistent." }
            val uncollectible = i.status in setOf("DRAFT", "VOID", "VOIDED")
            require(i.balanceKhr == if (uncollectible) 0L else i.totalKhr - paid)
            require(i.status in setOf("DRAFT", "ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE", "VOID", "VOIDED"))
            if (i.status == "PAID") require(paid == i.totalKhr)
            if (i.status in setOf("UNPAID", "ISSUED")) require(paid == 0L)
            if (i.status in setOf("PARTIAL", "PARTIALLY_PAID")) require(paid > 0L && paid < i.totalKhr)
            if (uncollectible) require(paid == 0L)
            if (i.status in setOf("VOID", "VOIDED")) require(i.voidedAt != null && !i.voidReason.isNullOrBlank())
        }
        sql.query("SELECT depositHeldKhr,appliedKhr,deductionsKhr,refundKhr FROM move_out_settlements").use {
            while(it.moveToNext()) require(Math.addExact(Math.addExact(it.getLong(1),it.getLong(2)),it.getLong(3)) == it.getLong(0)) {
                "Backup deposit allocations are inconsistent."
            }
        }
        sql.query("SELECT billingYear,billingMonth,invoiceIds FROM billing_periods").use { c ->
            while(c.moveToNext()) {
                val year=c.getInt(0);val month=c.getInt(1);val ids=c.getString(2)
                if(ids.isNotBlank()) for(id in ids.split(',').map { it.toLong() }) {
                    val i=candidate.invoiceDao().getInvoiceById(id) ?: error("Month-close snapshot points to a missing invoice.")
                    require(i.billingYear == year && i.billingMonth == month)
                }
            }
        }
    }
    private fun count(sql: SupportSQLiteDatabase, query: String): Long = sql.query(query).use { it.moveToFirst(); it.getLong(0) }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; require(result.size() + n <= limit) { "Backup file is too large." }; result.write(buffer, 0, n) }
        return result.toByteArray()
    }
}
