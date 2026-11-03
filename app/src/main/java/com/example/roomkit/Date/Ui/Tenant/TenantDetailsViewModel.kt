package com.example.roomkit.Date.Ui.Tenant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.TenancyHistoryItem
import com.example.roomkit.Date.Repositories.TenancyRepository
import com.example.roomkit.Date.Repositories.TenantRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TenantDetailsViewModel(
    private val tenantId: Long,
    private val tenantRepository: TenantRepository,
    private val tenancyRepository: TenancyRepository
) : ViewModel() {

    val tenant: StateFlow<TenantEntity?> =
        tenantRepository.getTenantById(tenantId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )

    val activeTenancy: StateFlow<TenancyEntity?> =
        tenancyRepository.observeActiveTenancyForTenant(tenantId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )

    val tenancyHistory: StateFlow<List<TenancyHistoryItem>> =
        tenancyRepository.observeTenancyHistoryForTenant(tenantId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    suspend fun getInvoicesForTenancy(
        tenancyId: Long
    ): List<InvoiceEntity> {
        val history = tenancyRepository
            .observeTenancyHistoryForTenant(tenantId)
            .first()

        require(history.any { it.tenancyId == tenancyId }) {
            "This rental record does not belong to this tenant."
        }

        return tenancyRepository
            .observeInvoicesForTenancy(tenancyId)
            .first()
    }

    fun moveOut(
        tenancyId: Long,
        moveOutDate: Long,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = tenancyRepository.moveOutTenant(
                tenancyId,
                moveOutDate
            )

            onResult(result)
        }
    }
}