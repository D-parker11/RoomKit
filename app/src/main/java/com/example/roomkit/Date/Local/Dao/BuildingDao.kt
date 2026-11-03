package com.roomkit.data.local.dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.model.BuildingWithStats
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildingDao {

    @Query("""
    SELECT 
        b.buildingId,
        b.name,
        b.address,
        b.description,
        COUNT(DISTINCT r.roomId) AS totalRooms,
        COUNT(DISTINCT t.roomId) AS occupiedRooms
    FROM buildings b
    LEFT JOIN rooms r ON b.buildingId = r.buildingId
    LEFT JOIN tenancies t ON r.roomId = t.roomId AND t.status = 'ACTIVE'
    WHERE b.name LIKE '%' || :query || '%' OR (b.address IS NOT NULL AND b.address LIKE '%' || :query || '%')
    GROUP BY b.buildingId
    ORDER BY b.createdAt DESC
""")
    fun observeBuildingsWithStats(query: String = ""): Flow<List<BuildingWithStats>>

    @Query("SELECT * FROM buildings WHERE buildingId = :buildingId LIMIT 1")
    fun observeBuildingById(buildingId: Long): Flow<BuildingEntity?>

    @Query("SELECT COUNT(*) FROM rooms WHERE buildingId = :buildingId")
    suspend fun getRoomCountForBuilding(buildingId: Long): Int

    @Query("SELECT COUNT(*) FROM buildings")
    suspend fun getBuildingCount(): Int

    @Transaction
    suspend fun insertSingleBuilding(building: BuildingEntity): Long {
        check(getBuildingCount() == 0) {
            "Your property already exists. Edit it instead of adding another."
        }
        return insertBuilding(building)
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBuilding(building: BuildingEntity): Long

    @Update
    suspend fun updateBuilding(building: BuildingEntity)

    @Delete
    suspend fun deleteBuilding(building: BuildingEntity)
    @Query("SELECT * FROM buildings WHERE buildingId = :buildingId LIMIT 1")
    suspend fun getBuildingById(buildingId: Long): BuildingEntity?
    @Query("SELECT * FROM buildings ORDER BY name ASC")
    fun getAllBuildings(): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings ORDER BY name ASC")
    fun observeAllBuildings(): Flow<List<BuildingEntity>>
}
