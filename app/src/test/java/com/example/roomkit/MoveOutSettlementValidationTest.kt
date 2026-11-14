package com.example.roomkit

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.roomkit.Date.Local.Entity.*
import com.example.roomkit.Date.Repositories.MoveOutInput
import com.example.roomkit.Date.Repositories.MoveOutRepository
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class MoveOutSettlementValidationTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: MoveOutRepository
    private var tenancyId = 0L
    private var roomId = 0L
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(sql: SupportSQLiteDatabase) {
                    com.roomkit.Date.Local.Scheme.MeterHistorySchema.installGuards(sql)
                    com.roomkit.Date.Local.Scheme.PropertyConstraintSchema.installGuards(sql)
                    com.roomkit.Date.Local.Scheme.Phase3Schema.installGuards(sql)
                }
            }).build()
        val now = System.currentTimeMillis()
        val buildingId = db.buildingDao().insertSingleBuilding(BuildingEntity(name = "Test Property", address = null, description = null, totalRooms = 1))
        roomId = db.roomDao().insertRoomWithinCapacity(RoomEntity(buildingId = buildingId, roomNumber = "QA-MO", floor = null, monthlyRent = 400_000, description = null))
        val tenantId = db.tenantDao().insertTenantCheckingIdentity(TenantEntity(fullName = "Settlement Test", phoneNumber = "test-settlement", idOrPassport = "SETTLEMENT-TEST"))
        tenancyId = db.tenancyDao().insertTenancy(TenancyEntity(
            tenantId = tenantId, roomId = roomId, moveInDate = now - 86_400_000,
            monthlyRent = 400_000, deposit = 400_000, billingDay = 1, dueDay = 5,
            electricityRate = 1_000, waterBillingMethod = "FIXED", waterRate = 20_000,
            rubbishFee = 10_000, status = "ACTIVE"
        ))
        db.roomDao().updateRoomStatus(roomId, "OCCUPIED")
        db.meterReadingDao().insertReading(MeterReadingEntity(
            tenancyId = tenancyId, roomId = roomId, meterType = "ELECTRICITY",
            previousReading = 100, currentReading = 100, readingDate = now - 86_400_000,
            billingMonth = 11, billingYear = 2026, readingType = "INITIAL"
        ))
        repository = MoveOutRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun input(deductions: Long = 0, reason: String = "") = MoveOutInput(
        date = System.currentTimeMillis(), electricity = 100, water = null,
        additionalRent = 0, otherCharges = 0, chargeReason = "",
        applyDeposit = 0, deductions = deductions, deductionReason = reason
    )

    @Test fun zeroDeductionDiscardsStaleReasonAtPersistenceBoundary() = runBlocking {
        val snapshot = repository.snapshot(tenancyId)
        val zeroRefundInput = input(reason = "stale -1 test text").copy(
            additionalRent = 400_000, chargeReason = "Agreed test rent", applyDeposit = 400_000
        )
        val reviewed = repository.review(snapshot, zeroRefundInput)
        repository.complete(reviewed)
        val settlement = requireNotNull(db.moveOutDao().get(tenancyId))
        assertEquals(0L, settlement.deductionsKhr)
        assertEquals("", settlement.deductionReason)
        assertEquals(0L, settlement.refundKhr)
        assertFalse(settlement.refundPaid)
        assertNull(settlement.refundPaidAt)
    }

    @Test fun positiveDeductionRejectsBlankReasonAtPersistenceBoundary() = runBlocking {
        val valid = repository.review(repository.snapshot(tenancyId), input())
        val invalid = valid.copy(input = input(deductions = 50_000, reason = "   "))
        val failure = runCatching { repository.complete(invalid) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("meaningful reason"))
        assertNull(db.moveOutDao().get(tenancyId))
        assertEquals("ACTIVE", db.tenancyDao().getTenancyById(tenancyId)?.status)
        assertTrue(db.invoiceDao().getForTenancy(tenancyId).isEmpty())
        assertTrue(db.meterReadingDao().getForTenancy(tenancyId).none { it.readingType == "FINAL" })
    }

    @Test fun positiveDeductionRequiresAndPersistsTrimmedReason() = runBlocking {
        val reviewed = repository.review(repository.snapshot(tenancyId), input(100_000, "  Window repair  "))
        repository.complete(reviewed)
        val settlement = requireNotNull(db.moveOutDao().get(tenancyId))
        assertEquals(100_000L, settlement.deductionsKhr)
        assertEquals("Window repair", settlement.deductionReason)
        assertEquals(300_000L, settlement.refundKhr)
        assertFalse(settlement.refundPaid)
    }

    @Test fun positivePendingRefundCanBeRecordedExactlyOnceWithDateAndReference() = runBlocking {
        repository.complete(repository.review(repository.snapshot(tenancyId), input(100_000, "Window repair")))
        val paidAt = System.currentTimeMillis()
        assertEquals(1, db.moveOutDao().markRefundPaid(tenancyId, paidAt, "REFUND-TEST-001"))
        assertEquals(0, db.moveOutDao().markRefundPaid(tenancyId, paidAt + 1, "DUPLICATE"))
        val settlement = requireNotNull(db.moveOutDao().get(tenancyId))
        assertEquals(300_000L, settlement.refundKhr)
        assertTrue(settlement.refundPaid)
        assertEquals(paidAt, settlement.refundPaidAt)
        assertEquals("REFUND-TEST-001", settlement.refundReference)
    }
}
