package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object BillingDetailsSchema {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN billingPeriodStart TEXT")
            db.execSQL("ALTER TABLE invoices ADD COLUMN billingPeriodEnd TEXT")
            // Preserve historical facts: no guessed backfill for old invoices.
            db.execSQL("""CREATE TABLE IF NOT EXISTS billing_draft_adjustments (
                tenancyId INTEGER NOT NULL, billingMonth INTEGER NOT NULL, billingYear INTEGER NOT NULL,
                otherChargesKhr INTEGER NOT NULL, discountKhr INTEGER NOT NULL,
                billingPeriodStart TEXT NOT NULL, billingPeriodEnd TEXT NOT NULL,
                PRIMARY KEY(tenancyId,billingMonth,billingYear),
                FOREIGN KEY(tenancyId) REFERENCES tenancies(tenancyId) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""".trimIndent())
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        // Also repairs missing guards on databases originally created at version 5.
        InvoiceReplacementSchema.installGuards(db)
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_period_locked
            BEFORE UPDATE ON invoices
            WHEN NEW.billingPeriodStart IS NOT OLD.billingPeriodStart
              OR NEW.billingPeriodEnd IS NOT OLD.billingPeriodEnd
            BEGIN SELECT RAISE(ABORT,'Billing period is fixed for this invoice'); END
        """.trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_replacement_period
            BEFORE INSERT ON invoices WHEN NEW.replacesInvoiceId IS NOT NULL AND EXISTS (
                SELECT 1 FROM invoices o WHERE o.invoiceId=NEW.replacesInvoiceId
                AND (NEW.billingPeriodStart IS NOT o.billingPeriodStart
                     OR NEW.billingPeriodEnd IS NOT o.billingPeriodEnd))
            BEGIN SELECT RAISE(ABORT,'Replacement must retain the original billing period'); END
        """.trimIndent())
    }
}
