package com.roomkit.data.repository

import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.roomkit.data.local.model.RoomItemDetails
import kotlinx.coroutines.flow.Flow

class RoomRepository(private val roomDao: RoomDao) {

    fun getRoomsForBuilding(buildingId: Long): Flow<List<RoomItemDetails>> =
        roomDao.observeRoomsForBuilding(buildingId)

    suspend fun isRoomNumberTaken(buildingId: Long, roomNumber: String, excludeRoomId: Long): Boolean =
        roomDao.isRoomNumberTaken(buildingId, roomNumber, excludeRoomId)
    suspend fun getRoomById(roomId: Long): RoomEntity? = roomDao.getRoomById(roomId)
    suspend fun addRoom(
        buildingId: Long,
        roomNumber: String,
        floor: String?,
        roomType: String?,
        monthlyRentKhr: Long,
        note: String?
    ): Result<Long> {
        val cleanRoomNum = roomNumber.trim()
        if (cleanRoomNum.isBlank()) {
            return Result.failure(IllegalArgumentException("Room number is required."))
        }
        if (monthlyRentKhr <= 0L) {
            return Result.failure(IllegalArgumentException("Monthly rent must be greater than 0៛."))
        }

//        if (roomDao.existsRoomNumber(buildingId, cleanRoomNum)) {
//            return Result.failure(IllegalArgumentException("Room $cleanRoomNum already exists in this building."))
//        }

        return try {
            val id = roomDao.insertRoomWithinCapacity(
                RoomEntity(
                    buildingId = buildingId,
                    roomNumber = cleanRoomNum,
                    floor = floor?.trim()?.ifBlank { null },
                    roomType = roomType?.trim()?.ifBlank { null },
                    monthlyRent = monthlyRentKhr,
                    description = note?.trim()?.ifBlank { null },
                    initialElectricityReading = 0.0,
                    initialWaterReading = 0.0,
                    status = "VACANT"
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun updateRoomPhysicalDetails(
        roomId: Long,
        roomNumber: String,
        floor: String?,
        defaultRentKhr: Long,
        notes: String?
    ): Result<Unit> {
        return try {
            roomDao.updateRoomPhysicalDetails(
                roomId = roomId,
                roomNumber = roomNumber.trim(),
                floor = floor?.trim()?.ifBlank { null },
                defaultRentKhr = defaultRentKhr,
                notes = notes?.trim()?.ifBlank { null }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun archiveRoom(roomId: Long): Result<Unit> {
        return try {
            roomDao.archiveRoom(roomId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRoomSafely(roomId: Long, tenancyDao: TenancyDao): Result<Unit> {
        return try {
            if (tenancyDao.hasActiveTenancy(roomId)) {
                return Result.failure(IllegalStateException("This room currently has an active tenant. Move the tenant out before removing the room."))
            }
            if (tenancyDao.hasAnyTenancyHistory(roomId)) {
                return Result.failure(IllegalStateException("This room has rental history and cannot be permanently deleted. Archive it instead."))
            }
            roomDao.deleteRoom(roomId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}