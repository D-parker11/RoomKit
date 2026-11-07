package com.example.roomkit.Date.Repositories

import androidx.room.withTransaction
import com.example.roomkit.Date.Local.Dao.MeterReadingDao
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.TenancyHistoryItem
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.example.roomkit.data.local.dao.TenantDao
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.local.dao.BuildingDao
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class TenancyRepository(
    private val db: AppDatabase,
    private val tenancyDao: TenancyDao,
    private val roomDao: RoomDao,
    private val tenantDao: TenantDao,
    private val buildingDao: BuildingDao,
    private val meterReadingDao: MeterReadingDao
) {

    fun observeAllTenants(): Flow<List<TenantEntity>> =
        tenantDao.getAllTenants()

    fun observeTenantAssignmentOptions(): Flow<List<com.example.roomkit.Date.Local.Model.TenantAssignmentOption>> =
        tenantDao.observeTenantAssignmentOptions()

    fun observeAllBuildings(): Flow<List<BuildingEntity>> =
        buildingDao.getAllBuildings()

    fun observeVacantRooms(
        buildingId: Long
    ): Flow<List<RoomEntity>> =
        roomDao.observeVacantRoomsForBuilding(buildingId)

    suspend fun getRoomById(roomId: Long): RoomEntity? =
        roomDao.getRoomById(roomId)

    suspend fun getBuildingById(
        buildingId: Long
    ): BuildingEntity? =
        buildingDao.getBuildingById(buildingId)

    fun observeActiveTenancyForTenant(
        tenantId: Long
    ): Flow<TenancyEntity?> =
        tenancyDao.observeActiveTenancyForTenant(tenantId)

    fun observeTenancyHistoryForTenant(
        tenantId: Long
    ): Flow<List<TenancyHistoryItem>> =
        tenancyDao.observeTenancyHistoryForTenant(tenantId)

    suspend fun moveInTenant(
        tenantId: Long,
        buildingId: Long,
        roomId: Long,
        moveInDate: Long,
        monthlyRentKhr: Long,
        depositKhr: Long,
        billingDay: Int,
        dueDay: Int,
        electricityRateKhr: Long,
        waterBillingMethod: String,
        waterRateKhr: Long,
        rubbishFeeKhr: Long,
        initialElecReading: Long,
        initialWaterReading: Long?
    ): Result<Long> {
        return try {
            require(tenantId > 0L) {
                "Please select a tenant."
            }

            require(buildingId > 0L) {
                "Please select a building."
            }

            require(roomId > 0L) {
                "Please select a room."
            }

            require(monthlyRentKhr > 0L) {
                "Monthly rent must be greater than 0៛."
            }

            require(depositKhr >= 0L) {
                "Deposit cannot be negative."
            }

            require(billingDay in 1..31) {
                "Billing day must be between 1 and 31."
            }

            require(dueDay in 1..28) {
                "Due day must be between 1 and 28."
            }

            require(electricityRateKhr >= 0L) {
                "Electricity rate cannot be negative."
            }

            require(waterRateKhr >= 0L) {
                "Water rate or fixed fee cannot be negative."
            }

            require(rubbishFeeKhr >= 0L) {
                "Rubbish fee cannot be negative."
            }

            require(initialElecReading >= 0L) {
                "Initial electricity reading cannot be negative."
            }

            val normalizedWaterMethod = when (
                waterBillingMethod.trim().uppercase(Locale.ROOT)
            ) {
                "METER", "METERED" -> "METER"
                "FIXED" -> "FIXED"
                else -> throw IllegalArgumentException(
                    "Please select a valid water billing method."
                )
            }

            val isMeteredWater = normalizedWaterMethod == "METER"

            if (isMeteredWater) {
                require(
                    initialWaterReading != null &&
                            initialWaterReading >= 0L
                ) {
                    "A non-negative initial water reading is required " +
                            "for metered water."
                }
            }

            val newTenancyId = db.withTransaction {
                // Verify the selected tenant exists.
                val tenant = tenantDao.getTenantById(tenantId)
                    ?: throw IllegalStateException(
                        "Selected tenant does not exist."
                    )

                // One active tenancy per tenant across all buildings.
                val currentTenancy =
                    tenancyDao.getActiveTenancyForTenant(tenantId)

                if (currentTenancy != null) {
                    val currentRoom = roomDao.getRoomById(
                        currentTenancy.roomId
                    )

                    val currentBuilding = currentRoom?.let {
                        buildingDao.getBuildingById(it.buildingId)
                    }

                    val currentRental = when {
                        currentRoom != null && currentBuilding != null ->
                            "Room ${currentRoom.roomNumber} " +
                                    "in ${currentBuilding.name}"

                        currentRoom != null ->
                            "Room ${currentRoom.roomNumber}"

                        else ->
                            "another room"
                    }

                    throw IllegalStateException(
                        "${tenant.fullName} already rents $currentRental. " +
                                "Complete their current move-out before " +
                                "assigning another room."
                    )
                }

                // Verify the room and selected building.
                val room = roomDao.getRoomById(roomId)
                    ?: throw IllegalStateException(
                        "Selected room does not exist."
                    )

                check(room.buildingId == buildingId) {
                    "Room does not belong to the selected building."
                }

                check(buildingDao.getBuildingById(buildingId) != null) {
                    "Selected building does not exist."
                }

                check(
                    !room.status.equals(
                        "ARCHIVED",
                        ignoreCase = true
                    )
                ) {
                    "This room is archived and cannot be assigned."
                }

                check(
                    !room.status.equals(
                        "MAINTENANCE",
                        ignoreCase = true
                    )
                ) {
                    "This room is under maintenance and cannot be assigned."
                }

                // One active tenancy per room.
                check(
                    tenancyDao.getActiveTenancyForRoom(roomId) == null
                ) {
                    "Room ${room.roomNumber} already has an active tenant. " +
                            "Please select a vacant room."
                }

                val tenancy = TenancyEntity(
                    tenantId = tenantId,
                    roomId = roomId,
                    moveInDate = moveInDate,
                    moveOutDate = null,
                    currency = "KHR",
                    monthlyRent = monthlyRentKhr,
                    deposit = depositKhr,
                    billingDay = billingDay,
                    dueDay = dueDay,
                    electricityRate = electricityRateKhr,
                    waterBillingMethod = normalizedWaterMethod,
                    waterRate = waterRateKhr,
                    rubbishFee = rubbishFeeKhr,
                    status = "ACTIVE"
                )

                val handover = db.moveOutDao().latestForRoom(roomId)
                if (handover != null) {
                    require(moveInDate >= handover.moveOutDate) { "Move-in cannot precede the previous move-out." }
                    require(initialElecReading >= handover.electricityFinal) { "Electricity start cannot be below the handover reading." }
                    if (isMeteredWater && handover.waterFinal != null) {
                        require(initialWaterReading != null && initialWaterReading >= handover.waterFinal) { "Water start cannot be below the handover reading." }
                    }
                }
                val savedId = tenancyDao.insertTenancy(tenancy)
                val startDate = java.time.Instant.ofEpochMilli(moveInDate).atZone(java.time.ZoneId.of("Asia/Phnom_Penh")).toLocalDate()
                fun initial(type: String, reading: Long) = MeterReadingEntity(
                    tenancyId=savedId, roomId=roomId, meterType=type,
                    previousReading=reading, currentReading=reading, readingDate=moveInDate,
                    billingMonth=startDate.monthValue, billingYear=startDate.year, readingType="INITIAL")
                meterReadingDao.insertReading(initial("ELECTRICITY", initialElecReading))
                if (isMeteredWater) meterReadingDao.insertReading(initial("WATER", initialWaterReading!!))

                roomDao.updateRoomStatus(
                    roomId = roomId,
                    status = "OCCUPIED"
                )

                roomDao.updateRoomInitialReadings(
                    roomId,
                    initialElecReading.toDouble(),
                    if (isMeteredWater) {
                        initialWaterReading?.toDouble()
                    } else {
                        0.0
                    }
                )

                savedId
            }

            Result.success(newTenancyId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun moveOutTenant(
        tenancyId: Long,
        moveOutTimestamp: Long
    ): Result<Unit> {
        return try {
            db.withTransaction {
                val tenancy = tenancyDao.getTenancyById(tenancyId)
                    ?: throw IllegalStateException(
                        "Tenancy record not found."
                    )

                check(tenancy.status == "ACTIVE") {
                    "This tenancy has already ended or is not active."
                }

                require(moveOutTimestamp >= tenancy.moveInDate) {
                    "Move-out date cannot be earlier than the move-in date."
                }

                check(db.moveOutDao().get(tenancyId) != null) { "Complete the Move-Out settlement screen first." }
                val updatedRows = tenancyDao.markTenancyEnded(
                    tenancyId = tenancyId,
                    moveOutDate = moveOutTimestamp
                )

                check(updatedRows == 1) {
                    "Unable to end this tenancy. Please refresh and try again."
                }

                // Keep occupancy correct if older data contains
                // another active tenancy for the same room.
                val stillOccupied = tenancyDao.hasActiveTenancy(
                    tenancy.roomId
                )

                roomDao.updateRoomStatus(
                    roomId = tenancy.roomId,
                    status = if (stillOccupied) {
                        "OCCUPIED"
                    } else {
                        "VACANT"
                    }
                )
            }

            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun observeInvoicesForTenancy(tenancyId: Long): Flow<List<com.example.roomkit.Date.Local.Entity.InvoiceEntity>> =
        db.invoiceDao().observeInvoicesForTenancy(tenancyId)
}
