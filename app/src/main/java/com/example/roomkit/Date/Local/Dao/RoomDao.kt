package com.example.roomkit.data.local.dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Model.VacantRoomOption
import com.roomkit.data.local.model.RoomItemDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Query("""
    SELECT 
        r.roomId,
        r.buildingId,
        r.roomNumber,
        r.floor,
        CASE WHEN t.tenancyId IS NOT NULL THEN t.monthlyRent ELSE r.monthlyRent END AS monthlyRent,
        COALESCE(t.currency, 'KHR') AS currency,
        t.waterBillingMethod AS waterBillingMethod,
        CASE WHEN t.tenancyId IS NOT NULL THEN 1 ELSE 0 END AS isOccupied,
        ten.fullName AS tenantName,
        COALESCE(elec.currentReading, CAST(r.initialElectricityReading AS INTEGER)) AS electricityReading,
        COALESCE(water.currentReading, CAST(r.initialWaterReading AS INTEGER)) AS waterReading,
        CASE 
            WHEN inv.dueDate IS NOT NULL THEN 'Due ' || strftime('%d %b', inv.dueDate / 1000, 'unixepoch')
            WHEN t.dueDay IS NOT NULL THEN 'Due day ' || t.dueDay
            ELSE NULL 
        END AS dueDateText,
        COALESCE(inv.status, CASE WHEN t.tenancyId IS NOT NULL THEN 'Unpaid' ELSE NULL END) AS paymentStatus
    FROM rooms r
    LEFT JOIN tenancies t ON r.roomId = t.roomId AND t.status = 'ACTIVE'
    LEFT JOIN tenants ten ON t.tenantId = ten.tenantId
    LEFT JOIN (
        SELECT m.roomId, m.currentReading FROM meter_readings m
        WHERE m.meterType = 'ELECTRICITY' AND m.tenancyId IN (SELECT tenancyId FROM tenancies WHERE status='ACTIVE')
        AND m.readingId = (SELECT x.readingId FROM meter_readings x WHERE x.tenancyId=m.tenancyId AND x.meterType=m.meterType
            ORDER BY x.meterSequence DESC,x.readingDate DESC,x.readingId DESC LIMIT 1)
    ) elec ON r.roomId = elec.roomId
    LEFT JOIN (
        SELECT m.roomId, m.currentReading FROM meter_readings m
        WHERE m.meterType = 'WATER' AND m.tenancyId IN (SELECT tenancyId FROM tenancies WHERE status='ACTIVE')
        AND m.readingId = (SELECT x.readingId FROM meter_readings x WHERE x.tenancyId=m.tenancyId AND x.meterType=m.meterType
            ORDER BY x.meterSequence DESC,x.readingDate DESC,x.readingId DESC LIMIT 1)
    ) water ON r.roomId = water.roomId
    LEFT JOIN (
        SELECT tenancyId, status, dueDate, MAX(billingYear * 100 + billingMonth)
        FROM invoices
        GROUP BY tenancyId
    ) inv ON t.tenancyId = inv.tenancyId
    WHERE r.buildingId = :buildingId
    ORDER BY CAST(r.roomNumber AS INTEGER) ASC, r.roomNumber ASC
""")
    fun observeRoomsForBuilding(buildingId: Long): Flow<List<RoomItemDetails>>

    @Query("SELECT * FROM rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: Long): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoom(room: RoomEntity): Long

    @Update
    suspend fun updateRoom(room: RoomEntity)

    @Delete
    suspend fun deleteRoom(room: RoomEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM rooms WHERE buildingId = :buildingId AND roomNumber = :roomNumber LIMIT 1)")
    suspend fun existsRoomNumber(buildingId: Long, roomNumber: String): Boolean

    @Query("""
    SELECT r.* FROM rooms r 
    WHERE r.buildingId = :buildingId 
      AND r.status != 'ARCHIVED'
      AND r.roomId NOT IN (SELECT t.roomId FROM tenancies t WHERE t.status = 'ACTIVE')
    ORDER BY r.roomNumber ASC
""")
    fun observeVacantRoomsForBuilding(buildingId: Long): Flow<List<RoomEntity>>

    @Query("""
    SELECT r.* FROM rooms r 
    WHERE r.buildingId = :buildingId 
      AND r.roomId NOT IN (SELECT t.roomId FROM tenancies t WHERE t.status = 'ACTIVE')
    ORDER BY r.roomNumber ASC
""")
    suspend fun getVacantRoomsForBuilding(buildingId: Long): List<RoomEntity>

    @Query("UPDATE rooms SET status = :status WHERE roomId = :roomId")
    suspend fun updateRoomStatus(roomId: Long, status: String)

    @Query("UPDATE rooms SET initialElectricityReading = :elec, initialWaterReading = :water WHERE roomId = :roomId")
    suspend fun updateRoomInitialReadings(roomId: Long, elec: Double?, water: Double?)

    @Query("""
    SELECT COUNT(*) > 0 FROM rooms 
    WHERE buildingId = :buildingId 
      AND LOWER(TRIM(roomNumber)) = LOWER(TRIM(:roomNumber)) 
      AND roomId != :excludeRoomId
""")
    suspend fun isRoomNumberTaken(buildingId: Long, roomNumber: String, excludeRoomId: Long): Boolean
    @Query("""
    UPDATE rooms 
    SET roomNumber = :roomNumber,
        floor = :floor,
        monthlyRent = :defaultRentKhr,
        description = :notes
    WHERE roomId = :roomId
""")
    suspend fun updateRoomPhysicalDetails(
        roomId: Long,
        roomNumber: String,
        floor: String?,
        defaultRentKhr: Long,
        notes: String?
    )

    @Query("UPDATE rooms SET status = 'ARCHIVED' WHERE roomId = :roomId")
    suspend fun archiveRoom(roomId: Long)
    @Query("DELETE FROM rooms WHERE roomId = :roomId")
    suspend fun deleteRoom(roomId: Long)

    @Query("SELECT * FROM rooms ORDER BY roomNumber ASC")
    fun observeAllRooms(): Flow<List<RoomEntity>>

    @Query("""
    SELECT totalRooms
    FROM buildings
    WHERE buildingId = :buildingId
    LIMIT 1
""")
    suspend fun getBuildingRoomCapacity(buildingId: Long): Int?

    @Query("""
    SELECT COUNT(*)
    FROM rooms
    WHERE buildingId = :buildingId
""")
    suspend fun countRoomsForBuilding(buildingId: Long): Int
    @Query("SELECT * FROM rooms WHERE roomId = :roomId LIMIT 1")
    fun observeRoomById(roomId: Long): Flow<RoomEntity?>

    @Transaction
    suspend fun insertRoomWithinCapacity(room: RoomEntity): Long {
        val capacity = getBuildingRoomCapacity(room.buildingId)
            ?: throw IllegalArgumentException("Building not found.")

        if (capacity <= 0) {
            throw IllegalStateException(
                "Set this building's room capacity before adding rooms."
            )
        }

        val currentCount = countRoomsForBuilding(room.buildingId)

        if (currentCount >= capacity) {
            throw IllegalStateException(
                "This building has reached its capacity of $capacity rooms. " +
                        "Edit the building capacity to add more."
            )
        }

        if (existsRoomNumber(room.buildingId, room.roomNumber)) {
            throw IllegalArgumentException(
                "Room ${room.roomNumber} already exists in this building."
            )
        }

        return insertRoom(room)
    }


}
