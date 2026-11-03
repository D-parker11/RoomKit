package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object InvoicePaymentFieldsSchema {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN discountReason TEXT")
            // Existing v7 invoices contain whole KHR amounts, without conversion.
            db.execSQL("ALTER TABLE invoices ADD COLUMN currency TEXT NOT NULL DEFAULT 'KHR'")
            db.execSQL("ALTER TABLE payments ADD COLUMN referenceNumber TEXT")
            installGuards(db)
        }
    }

    fun installGuards(db: SupportSQLiteDatabase) {
        InvoiceItemsSchema.installGuards(db)
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_fields_locked
            BEFORE UPDATE ON invoices
            WHEN UPPER(TRIM(OLD.status)) <> 'DRAFT'
              AND (NEW.discountReason IS NOT OLD.discountReason OR NEW.currency IS NOT OLD.currency)
            BEGIN SELECT RAISE(ABORT, 'Issued invoice reason and currency are locked'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_currency_insert
            BEFORE INSERT ON invoices WHEN NEW.currency <> 'KHR'
            BEGIN SELECT RAISE(ABORT, 'Only KHR invoice currency is supported'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_currency_update
            BEFORE UPDATE ON invoices WHEN NEW.currency <> 'KHR'
            BEGIN SELECT RAISE(ABORT, 'Only KHR invoice currency is supported'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS payment_reference_locked
            BEFORE UPDATE ON payments WHEN NEW.referenceNumber IS NOT OLD.referenceNumber
            BEGIN SELECT RAISE(ABORT, 'Payment reference is part of the saved payment history'); END
        """.trimIndent())
    }
}
