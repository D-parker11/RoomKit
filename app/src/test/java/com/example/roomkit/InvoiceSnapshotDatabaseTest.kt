package com.example.roomkit

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.roomkit.Date.Local.Entity.*
import com.example.roomkit.Date.Repositories.phase3Billing
import com.roomkit.Date.Local.Scheme.MeterHistorySchema
import com.roomkit.Date.Local.Scheme.Phase3Schema
import com.roomkit.Date.Local.Scheme.PropertyConstraintSchema
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[30], manifest=org.robolectric.annotation.Config.NONE)
class InvoiceSnapshotDatabaseTest {
    private lateinit var db: AppDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val month = YearMonth.of(2026, 11)
    private val at = month.atDay(10).atStartOfDay(ZoneId.of("Asia/Phnom_Penh")).toInstant().toEpochMilli()
    private var tenancyId=0L
    private var roomId=0L
    private var tenantId=0L
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
            previousReading=100,currentReading=115,readingDate=at+1000,billingMonth=month.monthValue,billingYear=month.year))
    }
    @After fun teardown() = db.close()

    @Test fun futureRateChangeAndRestorePreserveIssuedInvoiceAndItems() = runBlocking {
        val billing = phase3Billing(context, db)
        val id = billing.issueInvoice(billing.prepareBillingDraft(roomId, 11, 2026).getOrThrow()).getOrThrow()
        val invoice = requireNotNull(db.invoiceDao().getInvoiceById(id))
        val items = db.invoiceItemDao().getForInvoice(id)
        val history = db.meterReadingDao().getForTenancy(tenancyId)
        assertEquals("ISSUED", invoice.status)
        assertEquals(445_000L, invoice.totalKhr)
        assertEquals(1_000L, invoice.electricityRateKhrSnapshot)
        assertEquals(15_000L, invoice.electricityChargeKhr)
        assertEquals(0L, invoice.amountPaidKhr)
        assertEquals(445_000L, invoice.balanceKhr)
        for (rate in listOf(1_200L, 1_000L)) {
            // Only this fresh in-memory fixture is mutated, never an emulator database.
            db.openHelper.writableDatabase.execSQL(
                "UPDATE tenancies SET electricityRate=? WHERE tenancyId=?", arrayOf(rate, tenancyId))
            assertEquals(rate, db.tenancyDao().getActiveTenancyForRoom(roomId)!!.electricityRate)
            assertEquals(invoice, db.invoiceDao().getInvoiceById(id))
            assertEquals(items, db.invoiceItemDao().getForInvoice(id))
            assertEquals(history, db.meterReadingDao().getForTenancy(tenancyId))
            assertTrue(billing.prepareBillingDraft(roomId, 11, 2026).isFailure)
            assertEquals(listOf(invoice), db.invoiceDao().getForTenancy(tenancyId))
        }
    }
}
