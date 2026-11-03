package com.example.roomkit.data.local.dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.TenantWithCurrentTenancy
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {

    @Query("""
        SELECT 
            t.tenantId,
            t.fullName,
            t.phoneNumber,
            t.idOrPassport,
            t.note,
            act.tenancyId AS activeTenancyId,
            r.buildingId AS currentBuildingId,
            r.roomId AS currentRoomId,
            r.roomNumber AS roomNumber,
            r.roomNumber AS currentRoomNumber,
            b.name AS buildingName,
            b.name AS currentBuildingName,
            act.currency AS currency,
            act.deposit AS deposit,
            act.moveInDate AS moveInDate,
            (SELECT COUNT(*) FROM tenancies hist WHERE hist.tenantId = t.tenantId) AS totalTenanciesCount
        FROM tenants t
        LEFT JOIN tenancies act ON t.tenantId = act.tenantId AND act.status = 'ACTIVE'
        LEFT JOIN rooms r ON act.roomId = r.roomId
        LEFT JOIN buildings b ON r.buildingId = b.buildingId
        WHERE (:query = '' 
           OR LOWER(t.fullName) LIKE '%' || LOWER(:query) || '%' 
           OR t.phoneNumber LIKE '%' || :query || '%')
        ORDER BY act.tenancyId IS NOT NULL DESC, t.fullName ASC
    """)
    fun observeTenantsWithCurrentRoom(query: String = ""): Flow<List<TenantWithCurrentTenancy>>

    @Query("SELECT * FROM tenants WHERE tenantId = :tenantId LIMIT 1")
    fun observeTenantById(tenantId: Long): Flow<TenantEntity?>

    @Query("SELECT * FROM tenants ORDER BY fullName ASC")
    fun getAllTenants(): Flow<List<TenantEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTenant(tenant: TenantEntity): Long

    @Update
    suspend fun updateTenant(tenant: TenantEntity)

    @Delete
    suspend fun deleteTenant(tenant: TenantEntity)

    @Query("SELECT * FROM tenants WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getTenantById(tenantId: Long): TenantEntity?
    @Query("SELECT * FROM tenants")
    fun observeAllTenants(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants ORDER BY fullName ASC")
    suspend fun getAllTenantsOnce(): List<TenantEntity>

    @Transaction
    suspend fun insertTenantCheckingIdentity(tenant: TenantEntity): Long {
        val identity = tenant.idOrPassport
            ?.trim()
            ?.uppercase(java.util.Locale.ROOT)
            .orEmpty()

        if (identity.isNotEmpty()) {
            val existingTenant = getAllTenantsOnce().firstOrNull {
                it.idOrPassport
                    ?.trim()
                    ?.uppercase(java.util.Locale.ROOT) == identity
            }

            require(existingTenant == null) {
                "A tenant with this ID/passport already exists. Use the existing tenant profile."
            }
        }

        return insertTenant(tenant)
    }
}