package com.example.roomkit.Date.Reminders

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Separate local delivery ledger; no migration of the billing database is needed. */
class InvoiceReminderStore private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "roomkit_reminder_delivery.db", null, 1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE reminder_delivery (
                invoice_id INTEGER NOT NULL,
                due_date INTEGER NOT NULL,
                slot TEXT NOT NULL,
                claimed_at INTEGER NOT NULL,
                PRIMARY KEY(invoice_id, due_date, slot)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun claim(invoiceId: Long, dueDate: Long, slot: String, now: Long): Boolean {
        val values = ContentValues().apply {
            put("invoice_id", invoiceId)
            put("due_date", dueDate)
            put("slot", slot)
            put("claimed_at", now)
        }
        return writableDatabase.insertWithOnConflict(
            "reminder_delivery", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    /** Read-only lookup for the reminders screen; opening the screen never claims an alert. */
    fun hasClaim(invoiceId: Long, dueDate: Long, slot: String): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM reminder_delivery WHERE invoice_id=? AND due_date=? AND slot=? LIMIT 1",
            arrayOf(invoiceId.toString(), dueDate.toString(), slot)
        ).use { it.moveToFirst() }

    companion object {
        @Volatile private var instance: InvoiceReminderStore? = null
        fun get(context: Context): InvoiceReminderStore = instance ?: synchronized(this) {
            instance ?: InvoiceReminderStore(context).also { instance = it }
        }
    }
}
