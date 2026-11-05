package com.roomkit.Date.Local.Scheme

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Phase3Schema {
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TABLE billing_periods (
                billingYear INTEGER NOT NULL, billingMonth INTEGER NOT NULL,
                status TEXT NOT NULL, invoiceIds TEXT NOT NULL, closedAt INTEGER,
                PRIMARY KEY(billingYear,billingMonth))""")
            db.execSQL("""CREATE TABLE billing_period_events (
                eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                billingYear INTEGER NOT NULL, billingMonth INTEGER NOT NULL,
                action TEXT NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
            installGuards(db)
        }
    }

    fun installGuards(db: SupportSQLiteDatabase) {
        // Reads once recorded are facts. Invoice edits never rewrite meter history.
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_monthly_immutable
            BEFORE UPDATE ON meter_readings WHEN OLD.readingType='MONTHLY'
            BEGIN SELECT RAISE(ABORT,'Recorded monthly readings are immutable'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_monthly_no_delete
            BEFORE DELETE ON meter_readings WHEN OLD.readingType='MONTHLY'
            BEGIN SELECT RAISE(ABORT,'Recorded monthly readings are immutable'); END""")
        for (table in listOf("invoices", "billing_draft_adjustments", "meter_readings")) {
            val filter = if (table == "meter_readings") "NEW.readingType='MONTHLY' AND " else ""
            val draftFilter = if (table == "invoices") "UPPER(TRIM(OLD.status))='DRAFT' AND " else ""
            for (operation in listOf("INSERT", "UPDATE")) {
                // Issued invoice payment/void updates remain available in closed periods.
                val condition = if (operation == "UPDATE") draftFilter else ""
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS closed_${table}_${operation.lowercase()}
                    BEFORE $operation ON $table WHEN $filter$condition EXISTS(
                        SELECT 1 FROM billing_periods p WHERE p.billingYear=NEW.billingYear
                        AND p.billingMonth=NEW.billingMonth AND p.status='CLOSED')
                    BEGIN SELECT RAISE(ABORT,'Reopen the billing month before changing its preparation'); END""")
            }
        }
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS billing_period_valid_insert
            BEFORE INSERT ON billing_periods WHEN NEW.billingYear NOT BETWEEN 1 AND 9999
            OR NEW.billingMonth NOT BETWEEN 1 AND 12 OR NEW.status NOT IN ('OPEN','CLOSED')
            BEGIN SELECT RAISE(ABORT,'Invalid billing period'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS billing_period_valid_update
            BEFORE UPDATE ON billing_periods WHEN NEW.billingYear<>OLD.billingYear OR NEW.billingMonth<>OLD.billingMonth
            OR NEW.status NOT IN ('OPEN','CLOSED')
            BEGIN SELECT RAISE(ABORT,'Invalid billing period change'); END""")
        for (op in listOf("UPDATE", "DELETE")) db.execSQL("""CREATE TRIGGER IF NOT EXISTS period_event_no_${op.lowercase()}
            BEFORE $op ON billing_period_events
            BEGIN SELECT RAISE(ABORT,'Month close and reopen history is immutable'); END""")
    }
}
