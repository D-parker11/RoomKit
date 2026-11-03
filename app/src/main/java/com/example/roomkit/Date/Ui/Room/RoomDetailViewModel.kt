package com.example.roomkit.Date.Ui.Room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Model.RoomDetailUiState
import com.example.roomkit.Date.Repositories.RoomDetailRepository
import com.example.roomkit.Date.Repositories.TenancyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoomDetailViewModel(
    private val roomId: Long,
    private val repository: RoomDetailRepository,
    private val tenancyRepository: TenancyRepository
) : ViewModel() {

    val uiState: StateFlow<RoomDetailUiState?> = repository.observeRoomDetail(roomId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun recordMeters(month: Int, year: Int, elec: Long, water: Long, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.recordMeterReadings(roomId, month, year, elec, water)
            onResult(res)
        }
    }

    fun endTenancy(tenancyId: Long, moveOutTimestamp: Long, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = tenancyRepository.moveOutTenant(tenancyId, moveOutTimestamp)
            onResult(res)
        }
    }
}