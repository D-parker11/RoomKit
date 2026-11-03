package com.example.roomkit.data.local.dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Model.TenancyHistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TenancyDao {

    @Query("SELECT * FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTenancyForRoom(roomId: Long): TenancyEntity?

    @Query("SELECT * FROM tenancies WHERE tenantId = :tenantId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTenancyForTenant(tenantId: Long): TenancyEntity?

    @Query("SELECT * FROM tenancies WHERE tenantId = :tenantId AND status = 'ACTIVE' LIMIT 1")
    fun observeActiveTenancyForTenant(tenantId: Long): Flow<TenancyEntity?>

    @Query("""
    SELECT 
        t.tenancyId,
        t.roomId,
        r.roomNumber,
        b.name AS buildingName,
        t.moveInDate,
        t.moveOutDate,
        t.monthlyRent,
        t.deposit,
        t.status
    FROM tenancies t
    INNER JOIN rooms r ON t.roomId = r.roomId
    INNER JOIN buildings b ON r.buildingId = b.buildingId
    WHERE t.tenantId = :tenantId
    ORDER BY t.moveInDate DESC
""")
    fun observeTenancyHistoryForTenant(tenantId: Long): Flow<List<TenancyHistoryItem>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTenancy(tenancy: TenancyEntity): Long

    @Query("UPDATE tenancies SET moveOutDate = :moveOutDate, status = 'ENDED' WHERE tenancyId = :tenancyId AND status = 'ACTIVE'")
    suspend fun markTenancyEnded(tenancyId: Long, moveOutDate: Long): Int

    @Query("SELECT COUNT(*) > 0 FROM tenancies WHERE roomId = :roomId AND status = 'ACTIVE'")
    suspend fun hasActiveTenancy(roomId: Long): Boolean
    @Query("SELECT * FROM tenancies WHERE tenancyId = :tenancyId LIMIT 1")
    suspend fun getTenancyById(tenancyId: Long): TenancyEntity?
    @Query("UPDATE tenancies SET status = :status, moveOutDate = :moveOutDate WHERE tenancyId = :tenancyId")
    suspend fun updateTenancyStatus(tenancyId: Long, status: String, moveOutDate: Long?)

    @Query("SELECT COUNT(*) > 0 FROM tenancies WHERE roomId = :roomId")
    suspend fun hasAnyTenancyHistory(roomId: Long): Boolean
    @Query("SELECT * FROM tenancies")
    fun observeAllTenancies(): Flow<List<TenancyEntity>>
    @Query("""
    SELECT * FROM tenancies
    WHERE roomId = :roomId
      AND status = 'ACTIVE'
    LIMIT 1
""")
    fun observeActiveTenancyForRoom(
        roomId: Long
    ): Flow<TenancyEntity?>
}
