package com.example.roomkit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class OccupiedRoomCardRentTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun occupiedCardUsesActiveTenancyRentWhileVacantCardKeepsRoomDefault() = runBlocking {
        val buildingId = database.buildingDao().insertBuilding(
            BuildingEntity(name = "Test Building", address = null, description = null)
        )
        val occupiedRoomId = database.roomDao().insertRoom(
            RoomEntity(buildingId = buildingId, roomNumber = "QA-901", floor = null,
                monthlyRent = 140_000L, description = null)
        )
        val vacantRoomId = database.roomDao().insertRoom(
            RoomEntity(buildingId = buildingId, roomNumber = "QA-902", floor = null,
                monthlyRent = 180_000L, description = null)
        )
        val tenantId = database.tenantDao().insertTenant(
            TenantEntity(fullName = "Test Tenant", phoneNumber = "012345678")
        )
        database.tenancyDao().insertTenancy(
            TenancyEntity(tenantId = tenantId, roomId = occupiedRoomId,
                moveInDate = 1_799_000_000_000L, currency = "KHR", monthlyRent = 200_000L,
                deposit = 50_000L, billingDay = 1, dueDay = 5, electricityRate = 2_000L,
                waterBillingMethod = "FIXED", waterRate = 30_000L, rubbishFee = 5_000L,
                status = "ACTIVE")
        )

        val rooms = database.roomDao().observeRoomsForBuilding(buildingId).first()
            .associateBy { it.roomId }

        assertEquals(200_000L, rooms.getValue(occupiedRoomId).monthlyRent)
        assertEquals(180_000L, rooms.getValue(vacantRoomId).monthlyRent)
    }
}
