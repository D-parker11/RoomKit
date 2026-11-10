package com.example.roomkit.Date.Ui.Room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.repository.BuildingRepository
import com.roomkit.data.repository.RoomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddRoomUiState(
    val buildingName: String = "",
    val roomNumber: String = "",
    val floor: String = "",
    val roomType: String = "",
    val monthlyRentInput: String = "",
    val notes: String = "",
    val roomNumberError: String? = null,
    val rentError: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedRoomId: Long? = null,
    val generalError: String? = null
) {
    val isSaveEnabled: Boolean
        get() = roomNumber.isNotBlank() &&
                monthlyRentInput.isNotBlank() &&
                roomNumberError == null &&
                rentError == null &&
                !isSaving
}

class AddRoomViewModel(
    val buildingId: Long,
    private val buildingRepository: BuildingRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRoomUiState())
    val uiState: StateFlow<AddRoomUiState> = _uiState.asStateFlow()

    val building: StateFlow<BuildingEntity?> = buildingRepository
        .getBuildingById(buildingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            building.collect { b ->
                if (b != null) {
                    _uiState.update { it.copy(buildingName = b.name) }
                }
            }
        }
    }

    fun onRoomNumberChanged(number: String) {
        val trimmed = number.trim()
        val error = if (number.isNotEmpty() && trimmed.isEmpty()) "Room number is required." else null
        _uiState.update {
            it.copy(
                roomNumber = number,
                roomNumberError = error,
                generalError = null
            )
        }
    }

    fun onFloorChanged(floor: String) {
        _uiState.update { it.copy(floor = floor) }
    }

    fun onRoomTypeChanged(type: String) {
        _uiState.update { it.copy(roomType = type) }
    }

    fun onMonthlyRentChanged(rentStr: String) {
        val cleaned = rentStr.trim().removeSuffix("៛").trim()
        val error = when {
            cleaned.isEmpty() -> null
            else -> {
                val parsed = parseKhrInput(cleaned)
                if (parsed == null) "Enter a valid monthly rent."
                else if (parsed <= 0L) "Monthly rent must be greater than 0៛."
                else null
            }
        }
        _uiState.update {
            it.copy(
                monthlyRentInput = cleaned,
                rentError = error,
                generalError = null
            )
        }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun saveRoom() {
        val state = _uiState.value
        if (state.isSaving || state.saveSuccess) return

        val cleanRoomNum = state.roomNumber.trim()
        if (cleanRoomNum.isEmpty()) {
            _uiState.update { it.copy(roomNumberError = "Room number is required.") }
            return
        }

        val rentKhr = parseKhrInput(state.monthlyRentInput)
        if (rentKhr == null || rentKhr <= 0L) {
            _uiState.update { it.copy(rentError = "Monthly rent must be greater than 0៛.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, generalError = null) }

        viewModelScope.launch {
            val result = roomRepository.addRoom(
                buildingId = buildingId,
                roomNumber = cleanRoomNum,
                floor = state.floor.trim().ifBlank { null },
                roomType = state.roomType.trim().ifBlank { null },
                monthlyRentKhr = rentKhr,
                note = state.notes.trim().ifBlank { null }
            )

            result.onSuccess { roomId ->
                _uiState.update { it.copy(isSaving = false, saveSuccess = true, savedRoomId = roomId) }
            }.onFailure { err ->
                val msg = when {
                    err.message?.contains("already exists", ignoreCase = true) == true ->
                        "Room $cleanRoomNum already exists in this building."
                    else -> err.message ?: "Unable to add room. Please try again."
                }
                _uiState.update { it.copy(isSaving = false, generalError = msg) }
            }
        }
    }

    companion object {
        fun parseKhrInput(amount: String): Long? = com.example.roomkit.Date.Billing.CurrencyFormatter.parseKhrInput(amount)
    }
}