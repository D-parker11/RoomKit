package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object InvoiceReplacementSchema {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE invoices ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE invoices ADD COLUMN replacesInvoiceId INTEGER REFERENCES invoices(invoiceId) ON UPDATE NO ACTION ON DELETE RESTRICT")
            db.execSQL("DROP INDEX index_invoices_tenancyId_billingMonth_billingYear_invoiceKind")
            db.execSQL("CREATE UNIQUE INDEX index_invoices_tenancyId_billingMonth_billingYear_invoiceKind_revision ON invoices(tenancyId,billingMonth,billingYear,invoiceKind,revision)")
            db.execSQL("CREATE UNIQUE INDEX index_invoices_replacesInvoiceId ON invoices(replacesInvoiceId)")
            installGuards(db)
        }
    }

    fun installGuards(db: SupportSQLiteDatabase) {
        // Room's entity index represents revision uniqueness. Triggers separately enforce
        // a single non-void row, including DRAFT, on both fresh installs and upgrades.
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_single_current_insert
            BEFORE INSERT ON invoices
            WHEN UPPER(TRIM(NEW.status)) NOT IN ('VOID','VOIDED') AND EXISTS(
                SELECT 1 FROM invoices i WHERE i.tenancyId=NEW.tenancyId
                AND i.billingMonth=NEW.billingMonth AND i.billingYear=NEW.billingYear
                AND i.invoiceKind=NEW.invoiceKind AND UPPER(TRIM(i.status)) NOT IN ('VOID','VOIDED'))
            BEGIN SELECT RAISE(ABORT,'Another current invoice exists for this period'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_single_current_update
            BEFORE UPDATE ON invoices
            WHEN UPPER(TRIM(NEW.status)) NOT IN ('VOID','VOIDED') AND EXISTS(
                SELECT 1 FROM invoices i WHERE i.invoiceId<>OLD.invoiceId
                AND i.tenancyId=NEW.tenancyId AND i.billingMonth=NEW.billingMonth
                AND i.billingYear=NEW.billingYear AND i.invoiceKind=NEW.invoiceKind
                AND UPPER(TRIM(i.status)) NOT IN ('VOID','VOIDED'))
            BEGIN SELECT RAISE(ABORT,'Another current invoice exists for this period'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_replacement_link
            BEFORE INSERT ON invoices WHEN NEW.replacesInvoiceId IS NOT NULL
            AND (NEW.status<>'DRAFT' OR NEW.amountPaidKhr<>0 OR NEW.balanceKhr<>0
              OR NOT EXISTS(SELECT 1 FROM invoices o WHERE o.invoiceId=NEW.replacesInvoiceId
                AND UPPER(TRIM(o.status)) IN ('VOID','VOIDED') AND o.amountPaidKhr=0
                AND o.invoiceKind='REGULAR' AND NEW.invoiceKind=o.invoiceKind
                AND NEW.tenancyId=o.tenancyId AND NEW.billingMonth=o.billingMonth
                AND NEW.billingYear=o.billingYear AND NEW.revision>o.revision)
              OR EXISTS(SELECT 1 FROM payments p WHERE p.invoiceId=NEW.replacesInvoiceId
                AND UPPER(TRIM(p.status))='ACTIVE')
              OR EXISTS(SELECT 1 FROM move_out_settlements s WHERE s.finalInvoiceId=NEW.replacesInvoiceId))
            BEGIN SELECT RAISE(ABORT,'Invalid replacement invoice'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_identity_locked
            BEFORE UPDATE ON invoices WHEN NEW.invoiceId<>OLD.invoiceId
              OR NEW.replacesInvoiceId IS NOT OLD.replacesInvoiceId OR NEW.revision<>OLD.revision
              OR NEW.tenancyId<>OLD.tenancyId OR NEW.billingMonth<>OLD.billingMonth
              OR NEW.billingYear<>OLD.billingYear OR NEW.invoiceKind<>OLD.invoiceKind
            BEGIN SELECT RAISE(ABORT,'Invoice identity and replacement link cannot change'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_void_stays_void
            BEFORE UPDATE ON invoices WHEN UPPER(TRIM(OLD.status)) IN ('VOID','VOIDED')
              AND (NEW.status IS NOT OLD.status OR NEW.balanceKhr<>0
                OR NEW.amountPaidKhr<>OLD.amountPaidKhr
                OR NEW.voidedAt IS NOT OLD.voidedAt OR NEW.voidReason IS NOT OLD.voidReason)
            BEGIN SELECT RAISE(ABORT,'Voided invoices must remain in history unchanged'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_snapshot_locked
            BEFORE UPDATE ON invoices WHEN UPPER(TRIM(OLD.status))<>'DRAFT' AND (
                NEW.rentKhr IS NOT OLD.rentKhr
                OR NEW.electricityPrevious IS NOT OLD.electricityPrevious
                OR NEW.electricityCurrent IS NOT OLD.electricityCurrent
                OR NEW.electricityUsage IS NOT OLD.electricityUsage
                OR NEW.electricityRateKhrSnapshot IS NOT OLD.electricityRateKhrSnapshot
                OR NEW.electricityChargeKhr IS NOT OLD.electricityChargeKhr
                OR NEW.waterBillingMethod IS NOT OLD.waterBillingMethod
                OR NEW.waterPrevious IS NOT OLD.waterPrevious OR NEW.waterCurrent IS NOT OLD.waterCurrent
                OR NEW.waterUsage IS NOT OLD.waterUsage
                OR NEW.waterRateKhrSnapshot IS NOT OLD.waterRateKhrSnapshot
                OR NEW.fixedWaterFeeKhrSnapshot IS NOT OLD.fixedWaterFeeKhrSnapshot
                OR NEW.waterChargeKhr IS NOT OLD.waterChargeKhr
                OR NEW.rubbishFeeKhrSnapshot IS NOT OLD.rubbishFeeKhrSnapshot
                OR NEW.otherChargesKhr IS NOT OLD.otherChargesKhr
                OR NEW.utilitySubtotalKhr IS NOT OLD.utilitySubtotalKhr
                OR NEW.discountKhr IS NOT OLD.discountKhr OR NEW.totalKhr IS NOT OLD.totalKhr
                OR NEW.issueDate IS NOT OLD.issueDate OR NEW.dueDate IS NOT OLD.dueDate
                OR NEW.createdAt IS NOT OLD.createdAt)
            BEGIN SELECT RAISE(ABORT,'Issued invoice amounts are locked; create a replacement'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_no_draft_payment_insert
            BEFORE INSERT ON payments WHEN UPPER(TRIM(NEW.status))='ACTIVE' AND EXISTS(
                SELECT 1 FROM invoices i WHERE i.invoiceId=NEW.invoiceId
                AND UPPER(TRIM(i.status)) IN ('DRAFT','VOID','VOIDED'))
            BEGIN SELECT RAISE(ABORT,'Cannot pay a draft or voided invoice'); END
        """.trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS invoice_no_draft_payment_update
            BEFORE UPDATE ON payments WHEN UPPER(TRIM(NEW.status))='ACTIVE' AND EXISTS(
                SELECT 1 FROM invoices i WHERE i.invoiceId=NEW.invoiceId
                AND UPPER(TRIM(i.status)) IN ('DRAFT','VOID','VOIDED'))
            BEGIN SELECT RAISE(ABORT,'Cannot pay a draft or voided invoice'); END
        """.trimIndent())
    }
}
