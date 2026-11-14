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
class PaymentProtectionDatabaseTest {
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

    @Test fun voidAndInvoiceProtectionRetainHistoryAndBalances() = runBlocking {
        val billing = phase3Billing(context, db)
        val id = billing.issueInvoice(billing.prepareBillingDraft(roomId, 11, 2026).getOrThrow()).getOrThrow()
        val items = db.invoiceItemDao().getForInvoice(id)
        val readings = db.meterReadingDao().getForTenancy(tenancyId)
        billing.recordPayment(id, 100_000, method="CASH").getOrThrow()
        billing.recordPayment(id, 45_000, method="BANK_TRANSFER", referenceNumber="TEST-PAY-002").getOrThrow()
        billing.recordPayment(id, 300_000, method="CASH").getOrThrow()
        val payment = db.paymentDao().getPaymentsForInvoice(id).single { it.amountKhr == 300_000L }
        billing.voidPayment(payment.paymentId, "Test payment entered incorrectly.").getOrThrow()
        val invoice = requireNotNull(db.invoiceDao().getInvoiceById(id))
        val payments = db.paymentDao().getPaymentsForInvoice(id)
        assertEquals(145_000L, invoice.amountPaidKhr)
        assertEquals(300_000L, invoice.balanceKhr)
        assertEquals(445_000L, invoice.totalKhr)
        assertEquals(2, payments.count { it.status == "ACTIVE" })
        assertEquals(1, payments.count { it.status == "VOIDED" })
        val repeat = billing.voidPayment(payment.paymentId, "Repeated test void")
        assertTrue(repeat.isFailure)
        assertEquals("This payment has already been voided.", repeat.exceptionOrNull()?.message)
        val voidInvoice = billing.voidInvoice(id, "Isolated active payment protection test")
        assertTrue(voidInvoice.isFailure)
        assertEquals(invoice, db.invoiceDao().getInvoiceById(id))
        assertEquals(payments, db.paymentDao().getPaymentsForInvoice(id))
        assertEquals(items, db.invoiceItemDao().getForInvoice(id))
        assertEquals(readings, db.meterReadingDao().getForTenancy(tenancyId))
        val state = com.example.roomkit.Date.Billing.InvoiceStatusResolver.resolve(
            invoice.status, invoice.totalKhr, invoice.amountPaidKhr, invoice.dueDate)
        assertEquals(com.example.roomkit.Date.Billing.DocumentStatus.ISSUED, state.documentStatus)
        assertEquals(com.example.roomkit.Date.Billing.PaymentStatus.PARTIALLY_PAID, state.paymentStatus)
    }
}
