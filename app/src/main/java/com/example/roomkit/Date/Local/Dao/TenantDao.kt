package com.example.roomkit.data.local.dao

import androidx.room.*
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.TenantWithCurrentTenancy
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {

    @Query("""
        SELECT t.tenantId, t.fullName, t.phoneNumber,
               act.tenancyId AS activeTenancyId,
               r.roomNumber AS currentRoomNumber,
               b.name AS currentBuildingName
        FROM tenants t
        LEFT JOIN tenancies act ON act.tenancyId = (
            SELECT MIN(active.tenancyId) FROM tenancies active
            WHERE active.tenantId = t.tenantId AND active.status = 'ACTIVE'
        )
        LEFT JOIN rooms r ON r.roomId = act.roomId
        LEFT JOIN buildings b ON b.buildingId = r.buildingId
        ORDER BY (act.tenancyId IS NOT NULL) ASC, t.fullName COLLATE NOCASE ASC, t.tenantId ASC
    """)
    fun observeTenantAssignmentOptions(): Flow<List<com.example.roomkit.Date.Local.Model.TenantAssignmentOption>>



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
           OR t.phoneNumber LIKE '%' || :query || '%'
           OR LOWER(t.idOrPassport) LIKE '%' || LOWER(:query) || '%')
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
        validateIdentity(tenant)
        return insertTenant(tenant)
    }

    @Transaction
    suspend fun updateTenantProfile(
        tenantId: Long, fullName: String, phone: String, identity: String?, note: String?
    ) {
        val existing = getTenantById(tenantId) ?: error("Tenant no longer exists.")
        val updated = existing.copy(fullName = fullName.trim(), phoneNumber = phone.trim(),
            idOrPassport = identity?.trim()?.ifBlank { null }, note = note?.trim()?.ifBlank { null })
        validateIdentity(updated)
        // UPDATE preserves primary key, creation date, tenancies and invoice links.
        updateTenant(updated)
    }

    suspend fun validateIdentity(tenant: TenantEntity) {
        require(tenant.fullName.isNotBlank()) { "Tenant name cannot be empty." }
        require(tenant.phoneNumber.isNotBlank()) { "Phone number cannot be empty." }
        val phone = com.example.roomkit.Date.Local.Model.TenantIdentity.phone(tenant.phoneNumber)
        val identity = com.example.roomkit.Date.Local.Model.TenantIdentity.document(tenant.idOrPassport)
        val others = getAllTenantsOnce().filter { it.tenantId != tenant.tenantId }
        require(others.none { com.example.roomkit.Date.Local.Model.TenantIdentity.phone(it.phoneNumber) == phone }) {
            "A tenant with this phone number already exists. Use the existing tenant profile."
        }
        require(identity.isEmpty() || others.none {
            com.example.roomkit.Date.Local.Model.TenantIdentity.document(it.idOrPassport) == identity
        }) { "A tenant with this ID/passport already exists. Use the existing tenant profile." }
    }
}
