package com.roomkit.data.repository

import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.dao.BuildingDao

import com.roomkit.data.local.model.BuildingWithStats
import kotlinx.coroutines.flow.Flow

class BuildingRepository(private val buildingDao: BuildingDao) {

    fun getBuildings(query: String = ""): Flow<List<BuildingWithStats>> =
        buildingDao.observeBuildingsWithStats(query)

    fun getBuildingById(buildingId: Long): Flow<BuildingEntity?> =
        buildingDao.observeBuildingById(buildingId)

    suspend fun insertBuilding(building: BuildingEntity): Long {
        return buildingDao.insertSingleBuilding(building)
    }

    suspend fun addBuilding(
        name: String,
        address: String?,
        description: String?,
        totalRooms: Int
    ): Long {
        require(name.isNotBlank()) {
            "Building name cannot be blank."
        }
        require(totalRooms > 0) {
            "Room capacity must be greater than 0."
        }

        return buildingDao.insertSingleBuilding(
            BuildingEntity(
                name = name.trim(),
                address = address?.trim(),
                description = description?.trim(),
                totalRooms = totalRooms
            )
        )
    }


    suspend fun updateBuilding(building: BuildingEntity){
        require(building.name.isNotBlank()) { "Building name cannot be blank." }
        buildingDao.updateBuilding(building.copy(name = building.name.trim()))
    }

    suspend fun deleteBuilding(building: BuildingEntity): Result<Unit> {
        val roomCount = buildingDao.getRoomCountForBuilding(building.buildingId)
        if (roomCount > 0) {
            return Result.failure(IllegalStateException("Cannot delete building containing $roomCount room(s)."))
        }
        buildingDao.deleteBuilding(building)
        return Result.success(Unit)
    }
}