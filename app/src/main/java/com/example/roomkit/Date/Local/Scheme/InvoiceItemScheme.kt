package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object InvoiceItemsSchema {
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Preserve every original header, payment, status, date and total.
            // Copy historical amounts; never recalculate from today's tenancy rates.
            db.execSQL("""CREATE TABLE IF NOT EXISTS invoice_items (
    invoiceId INTEGER NOT NULL, itemCode TEXT NOT NULL, description TEXT NOT NULL,
    quantity INTEGER, unit TEXT NOT NULL, unitPriceKhr INTEGER, amountKhr INTEGER NOT NULL,
    sortOrder INTEGER NOT NULL, PRIMARY KEY(invoiceId, itemCode),
    FOREIGN KEY(invoiceId) REFERENCES invoices(invoiceId) ON UPDATE NO ACTION ON DELETE RESTRICT)""".trimIndent())
            db.execSQL("""CREATE INDEX IF NOT EXISTS index_invoice_items_invoiceId ON invoice_items(invoiceId)""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'RENT',CASE WHEN invoiceKind='FINAL' THEN 'Additional rent' ELSE 'Monthly Rent' END,1,'FIXED',rentKhr,rentKhr,0 FROM invoices""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'ELECTRICITY','Electricity',electricityUsage,'kWh',electricityRateKhrSnapshot,electricityChargeKhr,1 FROM invoices""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'WATER',CASE WHEN UPPER(TRIM(waterBillingMethod))='FIXED' THEN 'Water — Fixed' ELSE 'Water — Metered' END,CASE WHEN UPPER(TRIM(waterBillingMethod))='FIXED' THEN 1 ELSE waterUsage END,CASE WHEN UPPER(TRIM(waterBillingMethod))='FIXED' THEN 'FIXED' ELSE 'm³' END,CASE WHEN UPPER(TRIM(waterBillingMethod))='FIXED' THEN waterChargeKhr ELSE waterRateKhrSnapshot END,waterChargeKhr,2 FROM invoices""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'RUBBISH','Rubbish / Waste',1,'FIXED',rubbishFeeKhrSnapshot,rubbishFeeKhrSnapshot,3 FROM invoices""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'OTHER','Other charges',1,'FIXED',otherChargesKhr,otherChargesKhr,4 FROM invoices""".trimIndent())
            db.execSQL("""INSERT INTO invoice_items (invoiceId,itemCode,description,quantity,unit,unitPriceKhr,amountKhr,sortOrder) SELECT invoiceId,'DISCOUNT','Discount',1,'FIXED',discountKhr,-discountKhr,5 FROM invoices""".trimIndent())
            installGuards(db)
        }
    }

    fun installGuards(db: SupportSQLiteDatabase) {
        BillingDetailsSchema.installGuards(db)
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_item_lock_insert BEFORE INSERT ON invoice_items WHEN NOT EXISTS(SELECT 1 FROM invoices WHERE invoiceId=NEW.invoiceId AND UPPER(TRIM(status))='DRAFT') BEGIN SELECT RAISE(ABORT,'Only draft invoice items can change'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_item_lock_update BEFORE UPDATE ON invoice_items WHEN NOT EXISTS(SELECT 1 FROM invoices WHERE invoiceId=OLD.invoiceId AND UPPER(TRIM(status))='DRAFT') OR NOT EXISTS(SELECT 1 FROM invoices WHERE invoiceId=NEW.invoiceId AND UPPER(TRIM(status))='DRAFT') BEGIN SELECT RAISE(ABORT,'Only draft invoice items can change'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_item_lock_delete BEFORE DELETE ON invoice_items WHEN NOT EXISTS(SELECT 1 FROM invoices WHERE invoiceId=OLD.invoiceId AND UPPER(TRIM(status))='DRAFT') BEGIN SELECT RAISE(ABORT,'Only draft invoice items can change'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_item_validate_insert BEFORE INSERT ON invoice_items WHEN NEW.itemCode NOT IN ('RENT','ELECTRICITY','WATER','RUBBISH','OTHER','DISCOUNT') OR (NEW.itemCode='DISCOUNT' AND NEW.amountKhr>0) OR (NEW.itemCode<>'DISCOUNT' AND NEW.amountKhr<0) OR NEW.quantity<0 OR NEW.unitPriceKhr<0 OR NEW.sortOrder<0 OR LENGTH(TRIM(NEW.description))=0 BEGIN SELECT RAISE(ABORT,'Invalid invoice item'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_item_validate_update BEFORE UPDATE ON invoice_items WHEN NEW.itemCode NOT IN ('RENT','ELECTRICITY','WATER','RUBBISH','OTHER','DISCOUNT') OR (NEW.itemCode='DISCOUNT' AND NEW.amountKhr>0) OR (NEW.itemCode<>'DISCOUNT' AND NEW.amountKhr<0) OR NEW.quantity<0 OR NEW.unitPriceKhr<0 OR NEW.sortOrder<0 OR LENGTH(TRIM(NEW.description))=0 BEGIN SELECT RAISE(ABORT,'Invalid invoice item'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_insert_as_draft BEFORE INSERT ON invoices WHEN UPPER(TRIM(NEW.status))<>'DRAFT' BEGIN SELECT RAISE(ABORT,'Create a draft with items before issuing'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_cannot_return_to_draft BEFORE UPDATE ON invoices WHEN UPPER(TRIM(OLD.status))<>'DRAFT' AND UPPER(TRIM(NEW.status))='DRAFT' BEGIN SELECT RAISE(ABORT,'Issued invoices cannot return to draft'); END""".trimIndent())
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_items_required_on_issue BEFORE UPDATE ON invoices WHEN UPPER(TRIM(OLD.status))='DRAFT' AND UPPER(TRIM(NEW.status))<>'DRAFT' AND ((SELECT COUNT(*) FROM invoice_items WHERE invoiceId=NEW.invoiceId)<>6 OR (SELECT SUM(amountKhr) FROM invoice_items WHERE invoiceId=NEW.invoiceId) IS NOT NEW.totalKhr OR (SELECT SUM(amountKhr) FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode IN ('ELECTRICITY','WATER','RUBBISH','OTHER')) IS NOT NEW.utilitySubtotalKhr OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='RENT') IS NOT NEW.rentKhr OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='ELECTRICITY') IS NOT NEW.electricityChargeKhr OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='WATER') IS NOT NEW.waterChargeKhr OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='RUBBISH') IS NOT NEW.rubbishFeeKhrSnapshot OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='OTHER') IS NOT NEW.otherChargesKhr OR (SELECT amountKhr FROM invoice_items WHERE invoiceId=NEW.invoiceId AND itemCode='DISCOUNT') IS NOT -NEW.discountKhr) BEGIN SELECT RAISE(ABORT,'Invoice items must match the saved total before issue'); END""".trimIndent())
    }
}
