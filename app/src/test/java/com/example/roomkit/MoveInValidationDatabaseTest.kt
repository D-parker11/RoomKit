package com.example.roomkit

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Repositories.TenancyRepository
import com.roomkit.Date.Local.Scheme.MeterHistorySchema
import com.roomkit.Date.Local.Scheme.Phase3Schema
import com.roomkit.Date.Local.Scheme.PropertyConstraintSchema
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
class MoveInValidationDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TenancyRepository
    private lateinit var originalRoom: RoomEntity
    private var buildingId = 0L
    private var roomId = 0L
    private var tenantId = 0L

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    MeterHistorySchema.installGuards(db)
                    PropertyConstraintSchema.installGuards(db)
                    Phase3Schema.installGuards(db)
                }
            }).build()
        buildingId = db.buildingDao().insertSingleBuilding(BuildingEntity(
            name = "Isolated move-in fixture", address = null, description = null, totalRooms = 1))
        roomId = db.roomDao().insertRoomWithinCapacity(RoomEntity(
            buildingId = buildingId, roomNumber = "TEST-ONLY", floor = null,
            monthlyRent = 400_000, description = null))
        tenantId = db.tenantDao().insertTenantCheckingIdentity(TenantEntity(
            fullName = "Isolated fixture tenant", phoneNumber = "012345670"))
        originalRoom = requireNotNull(db.roomDao().getRoomById(roomId))
        repository = TenancyRepository(db, db.tenancyDao(), db.roomDao(),
            db.tenantDao(), db.buildingDao(), db.meterReadingDao())
    }

    @After
    fun teardown() = db.close()

    private suspend fun moveIn(deposit: Long = 400_000, electricity: Long = 100) =
        repository.moveInTenant(
            tenantId = tenantId, buildingId = buildingId, roomId = roomId,
            moveInDate = 1_794_312_000_000, monthlyRentKhr = 400_000,
            depositKhr = deposit, billingDay = 1, dueDay = 5,
            electricityRateKhr = 1_000, waterBillingMethod = "FIXED",
            waterRateKhr = 20_000, rubbishFeeKhr = 10_000,
            initialElecReading = electricity, initialWaterReading = null)

    private fun count(table: String): Int = db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private suspend fun assertRejectedWithoutWrites(result: Result<Long>, message: String) {
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(message, result.exceptionOrNull()?.message)
        // Count every row, including any partial or non-active tenancy/reading.
        assertEquals(0, count("tenancies"))
        assertEquals(0, count("meter_readings"))
        assertNull(db.tenancyDao().getActiveTenancyForTenant(tenantId))
        assertNull(db.tenancyDao().getActiveTenancyForRoom(roomId))
        assertEquals(originalRoom, db.roomDao().getRoomById(roomId))
        assertEquals("VACANT", db.roomDao().getRoomById(roomId)?.status)
        assertEquals(1, count("rooms"))
    }

    @Test
    fun negativeDepositRejectsWithoutTenancyReadingsOrOccupancyChange() = runBlocking {
        assertRejectedWithoutWrites(moveIn(deposit = -1), "Deposit cannot be negative.")
    }

    @Test
    fun negativeInitialElectricityRejectsWithoutTenancyReadingsOrOccupancyChange() = runBlocking {
        assertRejectedWithoutWrites(moveIn(electricity = -1),
            "Initial electricity reading cannot be negative.")
    }

    @Test
    fun validControlCreatesTenancyAndInitialReadingAndOccupiesRoom() = runBlocking {
        val tenancyId = moveIn().getOrThrow()
        assertEquals(1, count("tenancies"))
        assertEquals(1, count("meter_readings"))
        assertEquals("OCCUPIED", db.roomDao().getRoomById(roomId)?.status)
        val initial = db.meterReadingDao().getForTenancy(tenancyId).single()
        assertEquals("INITIAL", initial.readingType)
        assertEquals("ELECTRICITY", initial.meterType)
        assertEquals(100L, initial.previousReading)
        assertEquals(100L, initial.currentReading)
    }
}
