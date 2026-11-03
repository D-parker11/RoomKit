package com.roomkit.Date.Local.Scheme
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
object MeterHistorySchema {
    val MIGRATION_8_9 = object : Migration(8,9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""ALTER TABLE meter_readings ADD COLUMN readingType TEXT NOT NULL DEFAULT 'MONTHLY'""")
            db.execSQL("""ALTER TABLE meter_readings ADD COLUMN eventOrdinal INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE meter_readings ADD COLUMN meterSequence INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE meter_readings ADD COLUMN carriedUsage INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE meter_readings ADD COLUMN reason TEXT""")
            db.execSQL("""ALTER TABLE invoices ADD COLUMN electricityCarriedUsage INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE invoices ADD COLUMN waterCarriedUsage INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE invoices ADD COLUMN electricityMeterSequence INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""ALTER TABLE invoices ADD COLUMN waterMeterSequence INTEGER NOT NULL DEFAULT 0""")
            db.execSQL("""DROP INDEX index_meter_readings_roomId_tenancyId_meterType_billingMonth_billingYear""")
            db.execSQL("""CREATE UNIQUE INDEX index_meter_readings_roomId_tenancyId_meterType_billingMonth_billingYear_readingType_eventOrdinal ON meter_readings(roomId,tenancyId,meterType,billingMonth,billingYear,readingType,eventOrdinal)""")
            db.execSQL("""INSERT INTO meter_readings(tenancyId,roomId,meterType,previousReading,currentReading,readingDate,billingMonth,billingYear,readingType,eventOrdinal,meterSequence,carriedUsage,reason)
SELECT tenancyId,roomId,'ELECTRICITY',electricityFinal,electricityFinal,moveOutDate,
CAST(strftime('%m',moveOutDate/1000,'unixepoch','+7 hours') AS INTEGER),
CAST(strftime('%Y',moveOutDate/1000,'unixepoch','+7 hours') AS INTEGER),
'FINAL',0,0,0,'Historical closing value from move-out settlement; consumption was already settled.'
FROM move_out_settlements WHERE electricityFinal IS NOT NULL""")
            db.execSQL("""INSERT INTO meter_readings(tenancyId,roomId,meterType,previousReading,currentReading,readingDate,billingMonth,billingYear,readingType,eventOrdinal,meterSequence,carriedUsage,reason)
SELECT tenancyId,roomId,'WATER',waterFinal,waterFinal,moveOutDate,
CAST(strftime('%m',moveOutDate/1000,'unixepoch','+7 hours') AS INTEGER),
CAST(strftime('%Y',moveOutDate/1000,'unixepoch','+7 hours') AS INTEGER),
'FINAL',0,0,0,'Historical closing value from move-out settlement; consumption was already settled.'
FROM move_out_settlements WHERE waterFinal IS NOT NULL""")
            installGuards(db)
        }
    }
    fun installGuards(db: SupportSQLiteDatabase) {
        InvoicePaymentFieldsSchema.installGuards(db)
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_validate_insert BEFORE INSERT ON meter_readings
WHEN NEW.readingType NOT IN ('INITIAL','MONTHLY','FINAL','METERREPLACEMENT')
 OR NEW.meterType NOT IN ('ELECTRICITY','WATER') OR NEW.previousReading<0 OR NEW.currentReading<0
 OR NEW.carriedUsage<0 OR NEW.meterSequence<0 OR NEW.billingMonth NOT BETWEEN 1 AND 12
 OR NEW.billingYear NOT BETWEEN 1 AND 9999
 OR (NEW.readingType<>'METERREPLACEMENT' AND NEW.currentReading<NEW.previousReading)
 OR (NEW.readingType<>'METERREPLACEMENT' AND NEW.eventOrdinal<>0)
 OR (NEW.readingType='METERREPLACEMENT' AND (NEW.eventOrdinal<>NEW.meterSequence OR NEW.meterSequence<1 OR LENGTH(TRIM(COALESCE(NEW.reason,'')))=0))
BEGIN SELECT RAISE(ABORT,'Invalid meter history record'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_monthly_lock_insert BEFORE INSERT ON meter_readings
WHEN NEW.readingType='MONTHLY' AND EXISTS (
SELECT 1 FROM invoices i WHERE i.tenancyId=NEW.tenancyId AND i.billingMonth=NEW.billingMonth AND i.billingYear=NEW.billingYear
AND UPPER(TRIM(i.status)) NOT IN ('DRAFT','VOID','VOIDED'))
BEGIN SELECT RAISE(ABORT,'Issued invoice locks this meter period'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_validate_update BEFORE UPDATE ON meter_readings
WHEN NEW.readingType NOT IN ('INITIAL','MONTHLY','FINAL','METERREPLACEMENT')
 OR NEW.meterType NOT IN ('ELECTRICITY','WATER') OR NEW.previousReading<0 OR NEW.currentReading<0
 OR NEW.carriedUsage<0 OR NEW.meterSequence<0 OR NEW.billingMonth NOT BETWEEN 1 AND 12
 OR NEW.billingYear NOT BETWEEN 1 AND 9999
 OR (NEW.readingType<>'METERREPLACEMENT' AND NEW.currentReading<NEW.previousReading)
 OR (NEW.readingType<>'METERREPLACEMENT' AND NEW.eventOrdinal<>0)
 OR (NEW.readingType='METERREPLACEMENT' AND (NEW.eventOrdinal<>NEW.meterSequence OR NEW.meterSequence<1 OR LENGTH(TRIM(COALESCE(NEW.reason,'')))=0))
BEGIN SELECT RAISE(ABORT,'Invalid meter history record'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_monthly_lock_update BEFORE UPDATE ON meter_readings
WHEN NEW.readingType='MONTHLY' AND EXISTS (
SELECT 1 FROM invoices i WHERE i.tenancyId=NEW.tenancyId AND i.billingMonth=NEW.billingMonth AND i.billingYear=NEW.billingYear
AND UPPER(TRIM(i.status)) NOT IN ('DRAFT','VOID','VOIDED'))
BEGIN SELECT RAISE(ABORT,'Issued invoice locks this meter period'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_history_update_lock BEFORE UPDATE ON meter_readings
WHEN OLD.readingType<>'MONTHLY' OR NEW.readingId<>OLD.readingId OR NEW.tenancyId<>OLD.tenancyId
OR NEW.roomId<>OLD.roomId OR NEW.meterType<>OLD.meterType OR NEW.readingType<>OLD.readingType
OR NEW.billingMonth<>OLD.billingMonth OR NEW.billingYear<>OLD.billingYear
BEGIN SELECT RAISE(ABORT,'Meter history identity is immutable'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_history_no_delete BEFORE DELETE ON meter_readings
BEGIN SELECT RAISE(ABORT,'Meter history must be retained'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS meter_one_endpoint BEFORE INSERT ON meter_readings
WHEN NEW.readingType IN ('INITIAL','FINAL') AND EXISTS(SELECT 1 FROM meter_readings m WHERE m.tenancyId=NEW.tenancyId
AND m.roomId=NEW.roomId AND m.meterType=NEW.meterType AND m.readingType=NEW.readingType)
BEGIN SELECT RAISE(ABORT,'This tenancy already has that meter endpoint'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS invoice_meter_snapshot_locked BEFORE UPDATE ON invoices
WHEN UPPER(TRIM(OLD.status))<>'DRAFT' AND (NEW.electricityCarriedUsage IS NOT OLD.electricityCarriedUsage OR NEW.waterCarriedUsage IS NOT OLD.waterCarriedUsage OR NEW.electricityMeterSequence IS NOT OLD.electricityMeterSequence OR NEW.waterMeterSequence IS NOT OLD.waterMeterSequence)
BEGIN SELECT RAISE(ABORT,'Issued meter snapshots are locked'); END""")
    }
}
