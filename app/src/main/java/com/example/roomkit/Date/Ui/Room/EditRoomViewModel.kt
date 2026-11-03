package com.example.roomkit.Date.Ui.Room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.data.local.dao.TenancyDao
import com.roomkit.data.repository.BuildingRepository
import com.roomkit.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditRoomViewModel(
    private val roomId: Long,
    private val roomRepository: RoomRepository,
    private val buildingRepository: BuildingRepository,
    private val tenancyDao: TenancyDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditRoomUiState(roomId = roomId))
    val uiState: StateFlow<EditRoomUiState> = _uiState.asStateFlow()

    init {
        loadRoomData()
    }

    private fun loadRoomData() {
        viewModelScope.launch {
            val room = roomRepository.getRoomById(roomId) ?: return@launch

            // Fixed: Unwrap the Flow using firstOrNull()
            val building = buildingRepository.getBuildingById(room.buildingId).firstOrNull()

            val hasActive = tenancyDao.hasActiveTenancy(roomId)
            val rentFormatted = room.monthlyRent.toString().removeSuffix("៛")

            _uiState.update {
                it.copy(
                    buildingId = room.buildingId,
                    buildingName = building?.name ?: "Building",
                    originalRoomNumber = room.roomNumber,
                    roomNumber = room.roomNumber,
                    floor = room.floor.orEmpty(),
                    notes = room.description.orEmpty(),
                    defaultRentKhrInput = rentFormatted,
                    originalRentKhrInput = rentFormatted,
                    isOccupied = hasActive
                )
            }
        }
    }

    fun onRoomNumberChanged(value: String) = _uiState.update { it.copy(roomNumber = value, errorMessage = null) }
    fun onFloorChanged(value: String) = _uiState.update { it.copy(floor = value, errorMessage = null) }
    fun onRoomTypeChanged(value: String) = _uiState.update { it.copy(roomType = value, errorMessage = null) }
    fun onNotesChanged(value: String) = _uiState.update { it.copy(notes = value, errorMessage = null) }
    fun onRentChanged(value: String) = _uiState.update { it.copy(defaultRentKhrInput = value, errorMessage = null) }

    fun saveChanges() {
        val state = _uiState.value
        val cleanRoomNumber = state.roomNumber.trim()
        if (cleanRoomNumber.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Room Number cannot be empty.") }
            return
        }

        val rentKhr = parseKhrInput(state.defaultRentKhrInput)
        if (rentKhr == null || rentKhr <= 0L) {
            _uiState.update { it.copy(errorMessage = "Default Monthly Rent must be greater than 0៛.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            // Test 2 & 3: Exclude self when verifying duplicates
            val isDuplicate = roomRepository.isRoomNumberTaken(state.buildingId, cleanRoomNumber, roomId)
            if (isDuplicate) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Room '$cleanRoomNumber' already exists in ${state.buildingName}."
                    )
                }
                return@launch
            }

            val result = roomRepository.updateRoomPhysicalDetails(
                roomId = roomId,
                roomNumber = cleanRoomNumber,
                floor = state.floor,
                defaultRentKhr = rentKhr,
                notes = state.notes
            )

            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "Failed to save room.") }
            }
        }
    }

    fun archiveRoom() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            roomRepository.archiveRoom(roomId).onSuccess {
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "Failed to archive room.") }
            }
        }
    }

    fun deleteRoom() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
            val res = roomRepository.deleteRoomSafely(roomId, tenancyDao)
            res.onSuccess {
                _uiState.update { it.copy(isDeleting = false, isDeleted = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isDeleting = false, errorMessage = err.message) }
            }
        }
    }

    private fun parseKhrInput(amount: String): Long? = com.example.roomkit.Date.Billing.CurrencyFormatter.parseKhrInput(amount)
}