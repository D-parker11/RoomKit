package com.example.roomkit.Date.Ui.Tenant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Repositories.TenantRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditTenantState(
    val loading: Boolean = true, val saving: Boolean = false, val saved: Boolean = false,
    val name: String = "", val phone: String = "", val identity: String = "", val note: String = "",
    val error: String? = null, val exists: Boolean = false
)
class EditTenantViewModel(private val tenantId: Long, private val repository: TenantRepository) : ViewModel() {
    private val _state = MutableStateFlow(EditTenantState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val tenant = repository.getTenantById(tenantId).first() ?: error("Tenant no longer exists.")
                _state.value = EditTenantState(loading = false, exists = true, name = tenant.fullName,
                    phone = tenant.phoneNumber, identity = tenant.idOrPassport.orEmpty(), note = tenant.note.orEmpty())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = EditTenantState(loading = false, error = e.message) }
        }
    }
    fun edit(name: String, phone: String, identity: String, note: String) {
        if (_state.value.loading || _state.value.saving) return
        _state.update { it.copy(name = name, phone = phone, identity = identity, note = note, error = null) }
    }
    fun save() {
        val s = _state.value
        if (s.loading || s.saving || s.saved || !s.exists) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                repository.updateTenant(tenantId, s.name, s.phone, s.identity, s.note)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
    fun consumeSaved() { _state.update { it.copy(saved = false) } }
}
