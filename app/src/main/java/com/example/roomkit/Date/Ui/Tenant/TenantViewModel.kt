@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Tenant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Model.TenantWithCurrentTenancy
import com.example.roomkit.Date.Repositories.TenantRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TenantFilter {
    ALL,
    ACTIVE,
    MOVED_OUT
}

data class TenantTabCounts(
    val allCount: Int = 0,
    val activeCount: Int = 0,
    val movedOutCount: Int = 0
)

data class TenantListState(
    val tenants: List<TenantWithCurrentTenancy> = emptyList(),
    val noTenants: Boolean = true,
    val query: String = "",
    val filter: TenantFilter = TenantFilter.ALL
)

class TenantsViewModel(
    private val repository: TenantRepository
) : ViewModel() {

    enum class MatchReason {
        SAME_NAME_PHONE_AND_ID,
        SAME_NAME_AND_PHONE,
        SAME_PHONE,
        SAME_ID
    }

    data class TenantDuplicateMatch(
        val tenantId: Long,
        val fullName: String,
        val phoneNumber: String,
        val idOrPassport: String?,
        val matchReason: MatchReason,
        val sameIdentityDocument: Boolean
    )

    data class TenantSaveUiState(
        val isSaving: Boolean = false,
        val isSuccess: Boolean = false,
        val savedTenantId: Long? = null,
        val duplicateMatches: List<TenantDuplicateMatch> = emptyList(),
        val showDuplicateWarning: Boolean = false,
        val canCreateSeparateTenant: Boolean = false,
        val errorMessage: String? = null
    )

    val searchQuery = MutableStateFlow("")
    val selectedFilter = MutableStateFlow(TenantFilter.ALL)

    private val _uiState = MutableStateFlow(TenantSaveUiState())
    val uiState: StateFlow<TenantSaveUiState> = _uiState.asStateFlow()

    private val allTenants: StateFlow<List<TenantWithCurrentTenancy>> =
        repository.getTenants("")
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val tabCounts: StateFlow<TenantTabCounts> =
        allTenants.map { tenants ->
            TenantTabCounts(
                allCount = tenants.size,
                activeCount = tenants.count {
                    it.activeTenancyId != null
                },
                movedOutCount = tenants.count {
                    it.activeTenancyId == null &&
                            it.totalTenanciesCount > 0
                }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TenantTabCounts()
        )

    val listState: StateFlow<TenantListState> =
        combine(
            allTenants,
            searchQuery,
            selectedFilter
        ) { tenants, query, filter ->
            val cleanQuery = query.trim()

            val filtered = tenants.filter { tenant ->
                val matchesQuery =
                    cleanQuery.isBlank() ||
                            tenant.fullName.contains(
                                cleanQuery,
                                ignoreCase = true
                            ) ||
                            tenant.phoneNumber.contains(cleanQuery) ||
                            tenant.idOrPassport.orEmpty().contains(cleanQuery, ignoreCase = true)

                val matchesFilter = when (filter) {
                    TenantFilter.ALL -> true

                    TenantFilter.ACTIVE ->
                        tenant.activeTenancyId != null

                    TenantFilter.MOVED_OUT ->
                        tenant.activeTenancyId == null &&
                                tenant.totalTenanciesCount > 0
                }

                matchesQuery && matchesFilter
            }
            TenantListState(filtered, tenants.isEmpty(), cleanQuery, filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TenantListState()
        )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: TenantFilter) {
        selectedFilter.value = filter
    }

    fun checkForDuplicatesAndSave(
        fullName: String,
        phone: String,
        idOrPassport: String,
        notes: String
    ) {
        if (_uiState.value.isSaving) return

        val cleanName = fullName.trim()
        val cleanPhone = phone.trim()

        if (cleanName.isBlank()) {
            _uiState.value = TenantSaveUiState(
                errorMessage = "Tenant name cannot be empty."
            )
            return
        }

        if (cleanPhone.isBlank()) {
            _uiState.value = TenantSaveUiState(
                errorMessage = "Phone number cannot be empty."
            )
            return
        }

        _uiState.value = TenantSaveUiState(isSaving = true)

        viewModelScope.launch {
            try {
                val inputName = normalizeName(cleanName)
                val inputPhone = normalizePhone(cleanPhone)
                val inputIdentity = normalizeIdentity(idOrPassport)

                val existingTenants = repository.getAllTenants()

                val matches = existingTenants.mapNotNull { tenant ->
                    val sameName =
                        normalizeName(tenant.fullName) == inputName

                    val samePhone =
                        inputPhone.isNotEmpty() &&
                                normalizePhone(tenant.phoneNumber) == inputPhone

                    val sameIdentity =
                        inputIdentity.isNotEmpty() &&
                                normalizeIdentity(
                                    tenant.idOrPassport.orEmpty()
                                ) == inputIdentity

                    val reason = when {
                        sameName && samePhone && sameIdentity ->
                            MatchReason.SAME_NAME_PHONE_AND_ID

                        sameIdentity ->
                            MatchReason.SAME_ID

                        sameName && samePhone ->
                            MatchReason.SAME_NAME_AND_PHONE

                        samePhone ->
                            MatchReason.SAME_PHONE

                        else -> null
                    }

                    if (reason == null) {
                        null
                    } else {
                        TenantDuplicateMatch(
                            tenantId = tenant.tenantId,
                            fullName = tenant.fullName,
                            phoneNumber = tenant.phoneNumber,
                            idOrPassport = tenant.idOrPassport,
                            matchReason = reason,
                            sameIdentityDocument = sameIdentity
                        )
                    }
                }

                val needsReview = matches.isNotEmpty()

                if (needsReview) {
                    _uiState.value = TenantSaveUiState(
                        duplicateMatches = matches,
                        showDuplicateWarning = true,
                        canCreateSeparateTenant = false
                    )
                    return@launch
                }

                val savedId = repository.addTenant(
                    fullName = cleanName,
                    phoneNumber = cleanPhone,
                    idOrPassport = idOrPassport.trim().ifBlank { null },
                    note = notes.trim().ifBlank { null }
                ).getOrThrow()

                _uiState.value = TenantSaveUiState(
                    isSuccess = true,
                    savedTenantId = savedId
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = TenantSaveUiState(
                    errorMessage = error.message
                        ?: "Unable to save tenant. Please try again."
                )
            }
        }
    }

    fun dismissDuplicateWarning() {
        _uiState.update { state ->
            state.copy(
                duplicateMatches = emptyList(),
                showDuplicateWarning = false,
                canCreateSeparateTenant = false
            )
        }
    }

    fun resetSaveState() {
        if (!_uiState.value.isSaving) {
            _uiState.value = TenantSaveUiState()
        }
    }

    private fun normalizeName(value: String): String =
        value.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

    private fun normalizeIdentity(value: String): String =
        com.example.roomkit.Date.Local.Model.TenantIdentity.document(value)

    private fun normalizePhone(value: String): String =
        com.example.roomkit.Date.Local.Model.TenantIdentity.phone(value)
}
