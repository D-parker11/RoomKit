package com.example.roomkit.Date.Repositories

import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.TenantWithCurrentTenancy
import com.example.roomkit.data.local.dao.TenantDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class TenantRepository(private val tenantDao: TenantDao) {

    fun getTenants(query: String = ""): Flow<List<TenantWithCurrentTenancy>> =
        tenantDao.observeTenantsWithCurrentRoom(query)

    fun getTenantById(tenantId: Long): Flow<TenantEntity?> =
        tenantDao.observeTenantById(tenantId)

    // Fixed: collect the first emitted list from the Flow
    suspend fun getAllTenants(): List<TenantEntity> =
        tenantDao.getAllTenants().firstOrNull() ?: emptyList()

    // Optional: expose the live Flow directly if UI or ViewModels need continuous updates
    fun observeAllTenants(): Flow<List<TenantEntity>> =
        tenantDao.getAllTenants()

    suspend fun addTenant(fullName: String, phoneNumber: String, idOrPassport: String?, note: String?): Result<Long> {
        val cleanName = fullName.trim()
        val cleanPhone = phoneNumber.trim()

        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Tenant name cannot be empty."))
        if (cleanPhone.isBlank()) return Result.failure(IllegalArgumentException("Phone number cannot be empty."))

        return try {
            val id = tenantDao.insertTenantCheckingIdentity(
                TenantEntity(
                    fullName = cleanName,
                    phoneNumber = cleanPhone,
                    idOrPassport = idOrPassport?.trim()?.ifBlank { null },
                    note = note?.trim()?.ifBlank { null }
                )
            )
            Result.success(id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun updateTenant(tenantId: Long, fullName: String, phone: String, identity: String?, note: String?) {
        tenantDao.updateTenantProfile(tenantId, fullName, phone, identity, note)
    }
}
