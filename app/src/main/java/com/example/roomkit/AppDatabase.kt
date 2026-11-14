package com.roomkit.data.local

import android.content.Context
import com.example.roomkit.Date.Local.Entity.InvoiceItemEntity
import com.example.roomkit.Date.Local.Dao.InvoiceItemDao
import com.roomkit.Date.Local.Scheme.InvoiceItemsSchema
import com.example.roomkit.Date.Local.Entity.BillingDraftAdjustmentEntity
import com.example.roomkit.Date.Local.Dao.BillingDraftAdjustmentDao
import com.roomkit.Date.Local.Scheme.InvoiceReplacementSchema
import com.roomkit.Date.Local.Scheme.BillingDetailsSchema
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.roomkit.Date.Dao.ReportDao
import com.example.roomkit.Date.Local.Dao.InvoiceDao
import com.example.roomkit.Date.Local.Dao.MeterReadingDao
import com.example.roomkit.Date.Local.Dao.MoveOutDao
import com.example.roomkit.Date.Local.Dao.PaymentDao
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Entity.MoveOutSettlementEntity
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity

import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.example.roomkit.data.local.dao.TenantDao
import com.roomkit.data.local.dao.BuildingDao

@Database(
    entities = [
        com.example.roomkit.Date.Local.Entity.BillingPeriodEntity::class,
        com.example.roomkit.Date.Local.Entity.BillingPeriodEventEntity::class,
        InvoiceItemEntity::class,
        BillingDraftAdjustmentEntity::class,
        BuildingEntity::class,
        RoomEntity::class,
        TenantEntity::class,
        TenancyEntity::class,
        MeterReadingEntity::class,
        InvoiceEntity::class,
        PaymentEntity::class,
        MoveOutSettlementEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun billingPeriodDao(): com.example.roomkit.Date.Local.Dao.BillingPeriodDao
    abstract fun invoiceItemDao(): InvoiceItemDao
    abstract fun billingDraftAdjustmentDao(): BillingDraftAdjustmentDao
    abstract fun moveOutDao(): MoveOutDao
    abstract fun buildingDao(): BuildingDao
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun tenancyDao(): TenancyDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ReportDao(): ReportDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN invoiceKind TEXT NOT NULL DEFAULT 'REGULAR'")
                db.execSQL("DROP INDEX IF EXISTS index_invoices_tenancyId_billingMonth_billingYear")
                db.execSQL("CREATE UNIQUE INDEX index_invoices_tenancyId_billingMonth_billingYear_invoiceKind ON invoices(tenancyId,billingMonth,billingYear,invoiceKind)")
                db.execSQL("ALTER TABLE meter_readings ADD COLUMN tenancyId INTEGER NOT NULL DEFAULT 0")
                // Assign legacy readings only if exactly one tenancy matches their timestamp.
                db.execSQL("""
                    UPDATE meter_readings SET tenancyId = COALESCE((
                        SELECT MIN(t.tenancyId) FROM tenancies t WHERE t.roomId = meter_readings.roomId
                        AND t.moveInDate <= meter_readings.readingDate
                        AND (t.moveOutDate IS NULL OR t.moveOutDate >= meter_readings.readingDate)
                        HAVING COUNT(*) = 1), 0)
                """.trimIndent())
                db.execSQL("DROP INDEX IF EXISTS index_meter_readings_roomId_meterType_billingMonth_billingYear")
                db.execSQL("CREATE UNIQUE INDEX index_meter_readings_roomId_tenancyId_meterType_billingMonth_billingYear ON meter_readings(roomId,tenancyId,meterType,billingMonth,billingYear)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS move_out_settlements (
                        tenancyId INTEGER NOT NULL PRIMARY KEY, roomId INTEGER NOT NULL, moveOutDate INTEGER NOT NULL,
                        electricityFinal INTEGER NOT NULL, waterFinal INTEGER, depositHeldKhr INTEGER NOT NULL,
                        appliedKhr INTEGER NOT NULL, deductionsKhr INTEGER NOT NULL, deductionReason TEXT NOT NULL,
                        refundKhr INTEGER NOT NULL, refundPaid INTEGER NOT NULL, debtKhr INTEGER NOT NULL,
                        finalInvoiceId INTEGER, createdAt INTEGER NOT NULL,
                        FOREIGN KEY(tenancyId) REFERENCES tenancies(tenancyId) ON UPDATE NO ACTION ON DELETE RESTRICT)
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX index_move_out_settlements_tenancyId ON move_out_settlements(tenancyId)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE payments ADD COLUMN voidedAt INTEGER")
                db.execSQL("ALTER TABLE payments ADD COLUMN voidReason TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN voidedAt INTEGER")
                db.execSQL("ALTER TABLE invoices ADD COLUMN voidReason TEXT")
            }
        }

        val MIGRATION_10_11 = com.roomkit.Date.Local.Scheme.Phase3Schema.MIGRATION_10_11
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE move_out_settlements ADD COLUMN chargeReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE move_out_settlements ADD COLUMN refundPaidAt INTEGER")
                db.execSQL("ALTER TABLE move_out_settlements ADD COLUMN refundReference TEXT")
            }
        }
        val MIGRATION_9_10 = com.roomkit.Date.Local.Scheme.PropertyConstraintSchema.MIGRATION_9_10
        val MIGRATION_8_9 = com.roomkit.Date.Local.Scheme.MeterHistorySchema.MIGRATION_8_9
        val MIGRATION_7_8 = com.roomkit.Date.Local.Scheme.InvoicePaymentFieldsSchema.MIGRATION_7_8
        val MIGRATION_6_7 = InvoiceItemsSchema.MIGRATION_6_7
        val MIGRATION_5_6 = BillingDetailsSchema.MIGRATION_5_6
        val MIGRATION_4_5 = InvoiceReplacementSchema.MIGRATION_4_5

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "roomkit_khr_mvp.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            com.roomkit.Date.Local.Scheme.MeterHistorySchema.installGuards(db)
                            com.roomkit.Date.Local.Scheme.PropertyConstraintSchema.installGuards(db)
                            com.roomkit.Date.Local.Scheme.Phase3Schema.installGuards(db)
                        }
                    }).build().also { INSTANCE = it }
            }
    }
}
