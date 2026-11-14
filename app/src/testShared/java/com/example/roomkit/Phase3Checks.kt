package com.example.roomkit

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.roomkit.Date.Backup.JsonBackupRepository
import com.example.roomkit.Date.Backup.BackupCrypto
import com.example.roomkit.Date.Local.Entity.*
import com.example.roomkit.Date.Repositories.MonthlyCloseRepository
import com.example.roomkit.Date.Repositories.phase3Billing
import com.roomkit.Date.Local.Scheme.MeterHistorySchema
import com.roomkit.Date.Local.Scheme.Phase3Schema
import com.roomkit.Date.Local.Scheme.PropertyConstraintSchema
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

abstract class Phase3Checks {
    private lateinit var db: AppDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val month = YearMonth.now(ZoneId.of("Asia/Phnom_Penh")).minusMonths(1)
    private val at = month.atDay(1).atStartOfDay(ZoneId.of("Asia/Phnom_Penh")).toInstant().toEpochMilli()
    private var tenancyId=0L
    private var roomId=0L
    private var tenantId=0L
    private val fixtureFile by lazy { File(context.cacheDir,"phase3-test-${System.nanoTime()}.json") }
    @Before fun setup(): Unit = runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).addCallback(object:RoomDatabase.Callback(){
            override fun onCreate(sql:SupportSQLiteDatabase) {
                MeterHistorySchema.installGuards(sql);PropertyConstraintSchema.installGuards(sql);Phase3Schema.installGuards(sql)
            }
        }).build()
        val building=db.buildingDao().insertSingleBuilding(BuildingEntity(name="Fictional test property",address=null,description=null,totalRooms=2))
        roomId=db.roomDao().insertRoomWithinCapacity(RoomEntity(buildingId=building,roomNumber="101",floor=null,monthlyRent=400_000,description=null))
        tenantId=db.tenantDao().insertTenantCheckingIdentity(TenantEntity(fullName="Demo Tenant A",phoneNumber="test-only-A",idOrPassport="PH3-DEMO-A"))
        tenancyId=db.tenancyDao().insertTenancy(TenancyEntity(tenantId=tenantId,roomId=roomId,moveInDate=at,
            monthlyRent=400_000,deposit=400_000,billingDay=1,dueDay=5,electricityRate=1_000,
            waterBillingMethod="FIXED",waterRate=20_000,rubbishFee=10_000,status="ACTIVE"))
        db.roomDao().updateRoomStatus(roomId,"OCCUPIED")
        db.meterReadingDao().insertReading(MeterReadingEntity(tenancyId=tenancyId,roomId=roomId,meterType="ELECTRICITY",
            previousReading=100,currentReading=100,readingDate=at,billingMonth=month.monthValue,billingYear=month.year,readingType="INITIAL"))
        db.meterReadingDao().insertReading(MeterReadingEntity(tenancyId=tenancyId,roomId=roomId,meterType="ELECTRICITY",
            previousReading=100,currentReading=130,readingDate=at+1000,billingMonth=month.monthValue,billingYear=month.year))
    }
    @After fun teardown(){db.close();fixtureFile.delete()}
    private fun closeRepo()=MonthlyCloseRepository(db,phase3Billing(context,db))
    @Test fun closeIsIdempotentAndRetainsIds()=runBlocking {
        val first=closeRepo().close(month);val second=closeRepo().close(month)
        assertEquals(first,second);assertEquals(1,first.size)
        assertEquals(460_000L,db.invoiceDao().getInvoiceById(first.single())!!.totalKhr)
        assertEquals(1,closeRepo().history(month).size)
    }
    @Test fun competingCloseCallsReturnOneResult()=runBlocking {
        val result=coroutineScope { val a=async{closeRepo().close(month)};val b=async{closeRepo().close(month)};a.await() to b.await() }
        assertEquals(result.first,result.second);assertEquals(1,db.invoiceDao().getForTenancy(tenancyId).size)
    }
    @Test fun missingReadingRollsBackEveryInvoiceAndPeriod()=runBlocking {
        val room=db.roomDao().insertRoomWithinCapacity(RoomEntity(buildingId=1,roomNumber="102",floor=null,monthlyRent=400_000,description=null))
        val tenant=db.tenantDao().insertTenantCheckingIdentity(TenantEntity(fullName="Demo Tenant B",phoneNumber="test-only-B"))
        db.tenancyDao().insertTenancy(TenancyEntity(tenantId=tenant,roomId=room,moveInDate=at,monthlyRent=400_000,deposit=0,
            billingDay=1,dueDay=5,electricityRate=1000,waterBillingMethod="FIXED",waterRate=20_000,rubbishFee=10_000,status="ACTIVE"))
        assertTrue(runCatching{closeRepo().close(month)}.isFailure)
        assertTrue(db.invoiceDao().getForTenancy(tenancyId).isEmpty());assertNull(closeRepo().state(month))
    }
    @Test fun reopenRequiresReasonAndPreservesIssuedSnapshots()=runBlocking {
        val id=closeRepo().close(month).single();val original=db.invoiceDao().getInvoiceById(id)
        assertTrue(runCatching{closeRepo().reopen(month,"")}.isFailure)
        assertEquals("CLOSED",closeRepo().state(month)!!.status)
        closeRepo().reopen(month,"Reviewed correction")
        assertEquals(original,db.invoiceDao().getInvoiceById(id));assertEquals(listOf(id),closeRepo().close(month))
        assertEquals(listOf("CLOSE","REOPEN","CLOSE"),closeRepo().history(month).map{it.action})
    }
    @Test fun monthlyReadingCannotChangeBeforeIssue()=runBlocking {
        val row=db.meterReadingDao().getForTenancy(tenancyId).first{it.readingType=="MONTHLY"}
        assertTrue(runCatching{db.meterReadingDao().updateReading(row.copy(currentReading=140))}.isFailure)
        assertEquals(130L,db.meterReadingDao().getForTenancy(tenancyId).first{it.readingType=="MONTHLY"}.currentReading)
    }
    @Test fun closedMonthStillAcceptsPayments()=runBlocking {
        val billing=phase3Billing(context,db);val id=closeRepo().close(month).single()
        billing.recordPayment(id,200_000,method="CASH").getOrThrow()
        assertEquals(260_000L,db.invoiceDao().getInvoiceById(id)!!.balanceKhr)
    }
    @Test fun backupRoundTripRetainsAuditAndAmounts()=runBlocking {
        val id=closeRepo().close(month).single();val repository=JsonBackupRepository(context,db)
        repository.export(Uri.fromFile(fixtureFile),"test-only backup password".toCharArray())
        val tenant=db.tenantDao().getTenantById(tenantId)!!
        db.tenantDao().updateTenant(tenant.copy(fullName="Changed after backup"))
        repository.restore(Uri.fromFile(fixtureFile),"test-only backup password".toCharArray())
        assertEquals(tenant,db.tenantDao().getTenantById(tenantId))
        assertEquals(460_000L,db.invoiceDao().getInvoiceById(id)!!.totalKhr)
        assertEquals("CLOSED",closeRepo().state(month)!!.status);assertEquals(1,closeRepo().history(month).size)
    }
    @Test fun schema11BackupRestoresIntoCurrentSchema12WithSettlementDefaults(): Unit = runBlocking {
        val moveOut = com.example.roomkit.Date.Repositories.MoveOutRepository(db)
        val snapshot = moveOut.snapshot(tenancyId)
        moveOut.complete(moveOut.review(snapshot, com.example.roomkit.Date.Repositories.MoveOutInput(
            date=at+2_000, electricity=130, water=null, additionalRent=0, otherCharges=0,
            chargeReason="", applyDeposit=0, deductions=0, deductionReason="")))
        val repository = JsonBackupRepository(context, db)
        val password = "test-only backup password".toCharArray()
        repository.export(Uri.fromFile(fixtureFile), password)
        val outer = org.json.JSONObject(fixtureFile.readText())
        val decoder = java.util.Base64.getDecoder()
        val plain = BackupCrypto.decrypt(BackupCrypto.Envelope(
            decoder.decode(outer.getString("salt")), decoder.decode(outer.getString("iv")),
            decoder.decode(outer.getString("data"))), password)
        val payload = org.json.JSONObject(plain.toString(Charsets.UTF_8)).put("schemaVersion", 11)
        val settlement = payload.getJSONObject("tables").getJSONArray("move_out_settlements").getJSONObject(0)
        settlement.remove("chargeReason"); settlement.remove("refundPaidAt"); settlement.remove("refundReference")
        val sealed = BackupCrypto.encrypt(payload.toString().toByteArray(Charsets.UTF_8), password)
        val encoder = java.util.Base64.getEncoder()
        outer.put("salt", encoder.encodeToString(sealed.salt)).put("iv", encoder.encodeToString(sealed.iv))
            .put("data", encoder.encodeToString(sealed.ciphertext))
        fixtureFile.writeText(outer.toString())

        db.tenantDao().updateTenant(db.tenantDao().getTenantById(tenantId)!!.copy(fullName="Changed after backup"))
        repository.restore(Uri.fromFile(fixtureFile), password)
        assertEquals("Demo Tenant A", db.tenantDao().getTenantById(tenantId)!!.fullName)
        val restored = db.moveOutDao().get(tenancyId)!!
        assertEquals("", restored.chargeReason)
        assertNull(restored.refundPaidAt)
        assertNull(restored.refundReference)
        password.fill('\u0000'); plain.fill(0)
    }
    @Test fun wrongPasswordDoesNotChangeExistingData()=runBlocking {
        val repository=JsonBackupRepository(context,db);val before=db.tenantDao().getTenantById(tenantId)
        repository.export(Uri.fromFile(fixtureFile),"test-only backup password".toCharArray())
        assertTrue(runCatching{repository.restore(Uri.fromFile(fixtureFile),"another wrong password".toCharArray())}.isFailure)
        assertEquals(before,db.tenantDao().getTenantById(tenantId))
    }
    @Test fun malformedJsonDoesNotChangeExistingData()=runBlocking {
        val before=db.tenantDao().getTenantById(tenantId);fixtureFile.writeText("{broken")
        assertTrue(runCatching{JsonBackupRepository(context,db).restore(Uri.fromFile(fixtureFile),"test-only password".toCharArray())}.isFailure)
        assertEquals(before,db.tenantDao().getTenantById(tenantId))
    }
    @Test fun migration10To11PreservesExistingRows()=runBlocking {
        val name="phase3-migration-${System.nanoTime()}.db"
        val file=context.getDatabasePath(name)
        var old:AppDatabase?=null
        var upgraded:AppDatabase?=null
        try {
            old=Room.databaseBuilder(context,AppDatabase::class.java,name).addCallback(object:RoomDatabase.Callback(){
                override fun onCreate(sql:SupportSQLiteDatabase) {
                    MeterHistorySchema.installGuards(sql);PropertyConstraintSchema.installGuards(sql)
                }
            }).build()
            val buildingId=old.buildingDao().insertSingleBuilding(BuildingEntity(name="Legacy test property",address=null,description=null,totalRooms=1))
            val roomId=old.roomDao().insertRoomWithinCapacity(RoomEntity(buildingId=buildingId,roomNumber="M-1",floor=null,monthlyRent=140_000,description=null))
            val tenantId=old.tenantDao().insertTenantCheckingIdentity(TenantEntity(
                fullName="Preserved legacy tenant",phoneNumber="migration-only",idOrPassport="LEGACY-001"))
            val tenancyId=old.tenancyDao().insertTenancy(TenancyEntity(tenantId=tenantId,roomId=roomId,
                moveInDate=at,monthlyRent=100_000,deposit=50_000,billingDay=1,dueDay=5,
                electricityRate=1_000,waterBillingMethod="FIXED",waterRate=20_000,rubbishFee=10_000,status="ACTIVE"))
            old.roomDao().updateRoomStatus(roomId,"OCCUPIED")
            old.meterReadingDao().insertReading(MeterReadingEntity(tenancyId=tenancyId,roomId=roomId,
                meterType="ELECTRICITY",previousReading=100,currentReading=100,readingDate=at,
                billingMonth=month.monthValue,billingYear=month.year,readingType="INITIAL"))
            old.meterReadingDao().insertReading(MeterReadingEntity(tenancyId=tenancyId,roomId=roomId,
                meterType="ELECTRICITY",previousReading=100,currentReading=110,readingDate=at+1_000,
                billingMonth=month.monthValue,billingYear=month.year))
            val regularId=MonthlyCloseRepository(old,phase3Billing(context,old)).close(month).single()
            assertEquals(140_000L,old.invoiceDao().getInvoiceById(regularId)!!.totalKhr)
            phase3Billing(context,old).recordPayment(regularId,45_000,method="CASH").getOrThrow()
            val moveOut=com.example.roomkit.Date.Repositories.MoveOutRepository(old)
            val settlementReview=moveOut.review(moveOut.snapshot(tenancyId),com.example.roomkit.Date.Repositories.MoveOutInput(
                date=at+2_000,electricity=115,water=null,additionalRent=0,otherCharges=0,
                chargeReason="",applyDeposit=0,deductions=0,deductionReason=""))
            moveOut.complete(settlementReview)
            val finalInvoiceId=old.invoiceDao().getForTenancy(tenancyId).single { it.invoiceKind=="FINAL" }.invoiceId
            val payment=old.paymentDao().getForTenancy(tenancyId).single()
            val history=old.meterReadingDao().getForTenancy(tenancyId)
            val settlement=old.moveOutDao().get(tenancyId)!!
            assertEquals(5_000L,old.invoiceDao().getInvoiceById(finalInvoiceId)!!.totalKhr)
            assertEquals(45_000L,payment.amountKhr)
            assertEquals(50_000L,settlement.refundKhr)
            val items=old.invoiceItemDao().getForInvoice(regularId)
            val amounts=items.associate { it.itemCode to it.amountKhr }
            old.close();old=null
            // Derive a v10-shaped fixture with representative financial and history rows.
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path,null,0).use { sql ->
                sql.execSQL("DROP TABLE billing_period_events")
                sql.execSQL("DROP TABLE billing_periods")
                sql.execSQL("ALTER TABLE move_out_settlements RENAME TO settlements_v12")
                sql.execSQL("DROP INDEX index_move_out_settlements_tenancyId")
                sql.execSQL("""CREATE TABLE move_out_settlements (
                    tenancyId INTEGER NOT NULL PRIMARY KEY, roomId INTEGER NOT NULL, moveOutDate INTEGER NOT NULL,
                    electricityFinal INTEGER NOT NULL, waterFinal INTEGER, depositHeldKhr INTEGER NOT NULL,
                    appliedKhr INTEGER NOT NULL, deductionsKhr INTEGER NOT NULL,
                    deductionReason TEXT NOT NULL, refundKhr INTEGER NOT NULL, refundPaid INTEGER NOT NULL,
                    debtKhr INTEGER NOT NULL, finalInvoiceId INTEGER, createdAt INTEGER NOT NULL,
                    FOREIGN KEY(tenancyId) REFERENCES tenancies(tenancyId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                sql.execSQL("CREATE UNIQUE INDEX index_move_out_settlements_tenancyId ON move_out_settlements(tenancyId)")
                sql.execSQL("""INSERT INTO move_out_settlements(tenancyId,roomId,moveOutDate,electricityFinal,waterFinal,
                    depositHeldKhr,appliedKhr,deductionsKhr,deductionReason,refundKhr,refundPaid,debtKhr,finalInvoiceId,createdAt)
                    SELECT tenancyId,roomId,moveOutDate,electricityFinal,waterFinal,depositHeldKhr,appliedKhr,
                    deductionsKhr,deductionReason,refundKhr,refundPaid,debtKhr,finalInvoiceId,createdAt FROM settlements_v12""")
                sql.execSQL("DROP TABLE settlements_v12")
                sql.version=10
            }
            upgraded=Room.databaseBuilder(context,AppDatabase::class.java,name)
                .addMigrations(AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12).build()
            assertEquals("Preserved legacy tenant",upgraded.tenantDao().getTenantById(tenantId)!!.fullName)
            assertEquals(tenancyId,upgraded.tenancyDao().getTenancyById(tenancyId)!!.tenancyId)
            assertEquals(regularId,upgraded.invoiceDao().getForTenancy(tenancyId).single { it.invoiceKind=="REGULAR" }.invoiceId)
            assertEquals(140_000L,upgraded.invoiceDao().getInvoiceById(regularId)!!.totalKhr)
            assertEquals(amounts,upgraded.invoiceItemDao().getForInvoice(regularId).associate { it.itemCode to it.amountKhr })
            assertEquals(payment,upgraded.paymentDao().getForTenancy(tenancyId).single())
            assertEquals(history,upgraded.meterReadingDao().getForTenancy(tenancyId))
            val migratedSettlement=upgraded.moveOutDao().get(tenancyId)!!
            assertEquals(settlement.copy(chargeReason="",refundPaidAt=null,refundReference=null),migratedSettlement)
            assertEquals(finalInvoiceId,migratedSettlement.finalInvoiceId)
            assertNull(upgraded.billingPeriodDao().get(month.year,month.monthValue))
            upgraded.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst();assertEquals(12,it.getInt(0)) }
            upgraded.openHelper.writableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='meter_monthly_immutable'").use {
                it.moveToFirst();assertEquals(1,it.getInt(0))
            }
            val postMigrationId=upgraded.tenantDao().insertTenantCheckingIdentity(TenantEntity(fullName="Created after migration",phoneNumber="migration-new"))
            assertEquals("Created after migration",upgraded.tenantDao().getTenantById(postMigrationId)!!.fullName)
        } finally { old?.close();upgraded?.close();context.deleteDatabase(name) }
    }
    @Test fun authenticatedButInconsistentBackupCannotReplaceData()=runBlocking {
        val repository=JsonBackupRepository(context,db)
        val password="test-only backup password".toCharArray()
        repository.export(Uri.fromFile(fixtureFile),password)
        val outer=org.json.JSONObject(fixtureFile.readText())
        val decoder=java.util.Base64.getDecoder()
        val plain=com.example.roomkit.Date.Backup.BackupCrypto.decrypt(
            com.example.roomkit.Date.Backup.BackupCrypto.Envelope(decoder.decode(outer.getString("salt")),decoder.decode(outer.getString("iv")),decoder.decode(outer.getString("data"))),password)
        val payload=org.json.JSONObject(plain.toString(Charsets.UTF_8))
        payload.getJSONObject("tables").getJSONArray("tenancies").getJSONObject(0).put("monthlyRent",-1)
        val sealed=com.example.roomkit.Date.Backup.BackupCrypto.encrypt(payload.toString().toByteArray(),password)
        val encoder=java.util.Base64.getEncoder()
        outer.put("salt",encoder.encodeToString(sealed.salt)).put("iv",encoder.encodeToString(sealed.iv)).put("data",encoder.encodeToString(sealed.ciphertext))
        fixtureFile.writeText(outer.toString())
        assertTrue(runCatching{repository.restore(Uri.fromFile(fixtureFile),password)}.isFailure)
        assertEquals(400_000L,db.tenancyDao().getTenancyById(tenancyId)!!.monthlyRent)
        password.fill('\u0000');plain.fill(0)
    }
    @Test fun replacementAfterSavedMonthlyCarriesOnlyUnbilledUsage()=runBlocking {
        val rows=db.meterReadingDao().getForTenancy(tenancyId)
        val replacement=MeterReadingEntity(tenancyId=tenancyId,roomId=roomId,meterType="ELECTRICITY",
            previousReading=140,currentReading=0,readingDate=at+2000,billingMonth=month.monthValue,billingYear=month.year,
            readingType="METERREPLACEMENT",eventOrdinal=1,meterSequence=1,reason="Damaged meter")
        db.meterReadingDao().insertReading(replacement)
        val next=month.plusMonths(1)
        val basis=com.example.roomkit.Date.Repositories.MeterHistory.monthly(rows+replacement,next.monthValue,next.year,0)
        assertEquals(0L,basis.previous);assertEquals(10L,basis.carried);assertEquals(15L,basis.usage(5))
        assertEquals(130L,db.meterReadingDao().getForTenancy(tenancyId).first{it.readingType=="MONTHLY"}.currentReading)
    }

    @Test fun tenantEditPreservesLinksAndAllowsUnchangedIdentity() = runBlocking {
        val invoiceId = closeRepo().close(month).single()
        val originalInvoice = db.invoiceDao().getInvoiceById(invoiceId)
        val originalTenancy = db.tenancyDao().getTenancyById(tenancyId)
        val original = db.tenantDao().getTenantById(tenantId)!!
        db.tenantDao().updateTenantProfile(tenantId, "Updated name", original.phoneNumber,
            original.idOrPassport, "Updated notes")
        val edited = db.tenantDao().getTenantById(tenantId)!!
        assertEquals(original.tenantId, edited.tenantId)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals("Updated name", edited.fullName)
        assertEquals("Updated notes", edited.note)
        assertEquals(originalTenancy, db.tenancyDao().getTenancyById(tenancyId))
        assertEquals(originalInvoice, db.invoiceDao().getInvoiceById(invoiceId))
    }

    @Test fun tenantCreateAndEditBlockNormalizedDuplicates() = runBlocking {
        val other = db.tenantDao().insertTenantCheckingIdentity(TenantEntity(
            fullName = "Second tenant", phoneNumber = "012 345 678", idOrPassport = "TEST-B"))
        assertTrue(runCatching { db.tenantDao().insertTenantCheckingIdentity(TenantEntity(
            fullName = "Duplicate", phoneNumber = "+85512345678")) }.isFailure)
        assertTrue(runCatching { db.tenantDao().updateTenantProfile(tenantId,
            "Changed", "00855 12 345 678", "UNIQUE", null) }.isFailure)
        assertTrue(runCatching { db.tenantDao().updateTenantProfile(tenantId,
            "Changed", "099111222", " test-b ", null) }.isFailure)
        assertTrue(runCatching { db.tenantDao().updateTenantProfile(tenantId,
            "", "099111222", null, null) }.isFailure)
        assertTrue(runCatching { db.tenantDao().updateTenantProfile(tenantId,
            "Changed", " ", null, null) }.isFailure)
        assertEquals("Demo Tenant A", db.tenantDao().getTenantById(tenantId)!!.fullName)
        assertEquals("Second tenant", db.tenantDao().getTenantById(other)!!.fullName)
    }

    @Test fun tenantSearchIncludesIdentity() = runBlocking {
        val matches = db.tenantDao().observeTenantsWithCurrentRoom("ph3-demo-a").first()
        assertEquals(listOf(tenantId), matches.map { it.tenantId })
    }

    @Test fun zeroChargeMoveOutCreatesFinalDocumentWithoutPayment() = runBlocking {
        // Regular invoice has already billed usage to 130 and this month's fixed water.
        val regularId = closeRepo().close(month).single()
        closeRepo().reopen(month, "Prepare final settlement")
        val repository = com.example.roomkit.Date.Repositories.MoveOutRepository(db)
        val snapshot = repository.snapshot(tenancyId)
        val input = com.example.roomkit.Date.Repositories.MoveOutInput(
            date = at + 2000, electricity = 130, water = null, additionalRent = 0,
            otherCharges = 0, chargeReason = "", applyDeposit = 0, deductions = 0,
            deductionReason = "")
        val review = repository.review(snapshot, input)
        assertEquals(0L, review.newCharges)
        repository.complete(review)
        val settlement = db.moveOutDao().get(tenancyId)!!
        assertNotNull(settlement.finalInvoiceId)
        val finalInvoice = db.invoiceDao().getInvoiceById(settlement.finalInvoiceId!!)!!
        assertEquals("FINAL", finalInvoice.invoiceKind)
        assertEquals("ISSUED", finalInvoice.status)
        assertEquals(0L, finalInvoice.totalKhr)
        assertEquals(0L, finalInvoice.balanceKhr)
        assertTrue(db.invoiceItemDao().getForInvoice(finalInvoice.invoiceId).isNotEmpty())
        assertTrue(db.paymentDao().getForTenancy(tenancyId).isEmpty())
        assertEquals(460_000L, db.invoiceDao().getInvoiceById(regularId)!!.balanceKhr)
        assertEquals("ENDED", db.tenancyDao().getTenancyById(tenancyId)!!.status)
        assertEquals("VACANT", db.roomDao().getRoomById(roomId)!!.status)
        assertTrue(runCatching { repository.complete(review) }.isFailure)
        assertEquals(1, db.invoiceDao().getForTenancy(tenancyId).count { it.invoiceKind == "FINAL" })
    }
}
