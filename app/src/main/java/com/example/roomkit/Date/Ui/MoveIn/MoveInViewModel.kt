package com.example.roomkit.Date.Ui.Tenancy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Repositories.TenancyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job

class MoveInViewModel(
    private val presetRoomId: Long = 0L,
    private val presetTenantId: Long = 0L,
    private val tenancyRepository: TenancyRepository,
    private var availableRoomsJob: Job? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoveInUiState())
    val uiState: StateFlow<MoveInUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    init {
        val today = System.currentTimeMillis()
        _uiState.update { it.copy(moveInDate = today, moveInDateFormatted = dateFormat.format(Date(today))) }
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            // Load tenants list
            launch {
                tenancyRepository.observeAllTenants().collect { tenants ->
                    _uiState.update { it.copy(allTenants = tenants) }
                    if (presetTenantId > 0L) {
                        tenants.firstOrNull { it.tenantId == presetTenantId }?.let { t ->
                            onTenantSelected(t, isLocked = true)
                        }
                    }
                }
            }

            // Load buildings list
            launch {
                tenancyRepository.observeAllBuildings().collect { buildings ->
                    _uiState.update { it.copy(allBuildings = buildings) }
                    if (_uiState.value.buildingId == null && !_uiState.value.isRoomLocked) {
                        buildings.singleOrNull()?.let { onBuildingSelected(it) }
                    }
                }
            }

            // FLOW A: Room is known
            if (presetRoomId > 0L) {
                val room = tenancyRepository.getRoomById(presetRoomId)
                if (room != null) {
                    val building = tenancyRepository.getBuildingById(room.buildingId)
                    val rentKhrFormatted = room.monthlyRent.toString()
                    _uiState.update {
                        it.copy(
                            isRoomLocked = true,
                            roomId = room.roomId,
                            roomNumber = room.roomNumber,
                            buildingId = room.buildingId,
                            buildingName = building?.name ?: "",
                            monthlyRentKhrInput = rentKhrFormatted.removeSuffix("៛"),
                            depositKhrInput = rentKhrFormatted.removeSuffix("៛")
                        )
                    }
                }
            }
        }
    }

    fun onTenantSelected(tenant: TenantEntity, isLocked: Boolean = false) {
        _uiState.update {
            it.copy(
                tenantId = tenant.tenantId,
                tenantName = tenant.fullName,
                isTenantLocked = isLocked,
                errorMessage = null
            )
        }
    }

    fun onBuildingSelected(building: BuildingEntity) {
        if (_uiState.value.isRoomLocked || _uiState.value.isSaving) return

        availableRoomsJob?.cancel()

        _uiState.update {
            it.copy(
                buildingId = building.buildingId,
                buildingName = building.name,
                roomId = null,
                roomNumber = "",
                availableRooms = emptyList(),
                errorMessage = null
            )
        }

        availableRoomsJob = viewModelScope.launch {
            tenancyRepository
                .observeVacantRooms(building.buildingId)
                .collect { rooms ->
                    _uiState.update { current ->
                        if (current.buildingId == building.buildingId) {
                            current.copy(availableRooms = rooms)
                        } else {
                            current
                        }
                    }
                }
        }
    }

    fun onRoomSelected(room: RoomEntity) {
        if (_uiState.value.isRoomLocked) return
        val rentKhrFormatted = room.monthlyRent.toString()
        _uiState.update {
            it.copy(
                roomId = room.roomId,
                roomNumber = room.roomNumber,
                monthlyRentKhrInput = rentKhrFormatted.removeSuffix("៛"),
                depositKhrInput = rentKhrFormatted.removeSuffix("៛"),
                errorMessage = null
            )
        }
    }

    fun onMoveInDateChanged(timestamp: Long) {
        _uiState.update {
            it.copy(
                moveInDate = timestamp,
                moveInDateFormatted = dateFormat.format(Date(timestamp))
            )
        }
    }
    fun onMoveInSuccessHandled() {
        _uiState.update {
            it.copy(
                isSuccess = false,
                createdTenancyId = null
            )
        }
    }

    fun onRentChanged(value: String) = _uiState.update { it.copy(monthlyRentKhrInput = value) }
    fun onDepositChanged(value: String) = _uiState.update { it.copy(depositKhrInput = value) }
    fun onBillingDayChanged(value: String) = _uiState.update { it.copy(billingDayInput = value) }
    fun onDueDayChanged(value: String) = _uiState.update { it.copy(dueDayInput = value) }
    fun onElectricityRateChanged(value: String) = _uiState.update { it.copy(electricityRateKhrInput = value) }
    fun onRubbishFeeChanged(value: String) = _uiState.update { it.copy(rubbishFeeKhrInput = value) }
    fun onWaterMethodChanged(isMetered: Boolean) {
        _uiState.update {
            it.copy(
                isWaterMetered = isMetered,
                waterFeeOrRateKhrInput = if (isMetered) "1500" else "20000"
            )
        }
    }
    fun onWaterFeeOrRateChanged(value: String) = _uiState.update { it.copy(waterFeeOrRateKhrInput = value) }
    fun onInitialElecReadingChanged(value: String) = _uiState.update { it.copy(initialElecReadingInput = value) }
    fun onInitialWaterReadingChanged(value: String) = _uiState.update { it.copy(initialWaterReadingInput = value) }

    fun confirmMoveIn() {
        val state = _uiState.value

        if (state.isSaving || state.isSuccess) return

        val tenantId = state.tenantId
        if (tenantId == null) {
            _uiState.update { it.copy(errorMessage = "Please select a tenant.") }
            return
        }
        val buildingId = state.buildingId
        if (buildingId == null) {
            _uiState.update { it.copy(errorMessage = "Please select a building.") }
            return
        }
        val roomId = state.roomId
        if (roomId == null) {
            _uiState.update { it.copy(errorMessage = "Please select an available room.") }
            return
        }

        val rentKhr = parseKhrInput(state.monthlyRentKhrInput)
        if (rentKhr == null || rentKhr <= 0L) {
            _uiState.update { it.copy(errorMessage = "Monthly rent must be greater than 0៛.") }
            return
        }

        val depositKhr = parseKhrInput(state.depositKhrInput)
        if (depositKhr == null || depositKhr < 0L) {
            _uiState.update { it.copy(errorMessage = "Deposit cannot be negative.") }
            return
        }

        val billingDay = state.billingDayInput.toIntOrNull()
        if (billingDay == null || billingDay !in 1..31) {
            _uiState.update { it.copy(errorMessage = "Billing day must be between 1 and 31.") }
            return
        }

        val dueDay = state.dueDayInput.trim().toIntOrNull()
        if (dueDay == null || dueDay !in 1..28) {
            _uiState.update { it.copy(errorMessage = "Due day must be between 1 and 28.") }
            return
        }

        val elecRate = state.electricityRateKhrInput.toLongOrNull()
        if (elecRate == null || elecRate < 0L) {
            _uiState.update { it.copy(errorMessage = "Enter a valid electricity rate (KHR).") }
            return
        }

        val waterRateOrFee = state.waterFeeOrRateKhrInput.toLongOrNull()
        if (waterRateOrFee == null || waterRateOrFee < 0L) {
            _uiState.update { it.copy(errorMessage = "Enter a valid water charge (KHR).") }
            return
        }

        val rubbishFee = state.rubbishFeeKhrInput.toLongOrNull()
        if (rubbishFee == null || rubbishFee < 0L) {
            _uiState.update { it.copy(errorMessage = "Enter a valid rubbish fee (KHR).") }
            return
        }

        val initialElec = state.initialElecReadingInput.toLongOrNull()
        if (initialElec == null || initialElec < 0L) {
            _uiState.update { it.copy(errorMessage = "Enter a valid initial electricity reading.") }
            return
        }

        val initialWater = if (state.isWaterMetered) {
            val w = state.initialWaterReadingInput.toLongOrNull()
            if (w == null || w < 0L) {
                _uiState.update { it.copy(errorMessage = "Enter a valid initial water reading.") }
                return
            }
            w
        } else null

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val result = tenancyRepository.moveInTenant(
                tenantId = tenantId,
                buildingId = buildingId,
                roomId = roomId,
                moveInDate = state.moveInDate,
                monthlyRentKhr = rentKhr,
                depositKhr = depositKhr,
                billingDay = billingDay,
                dueDay = dueDay,
                electricityRateKhr = elecRate,
                waterBillingMethod = if (state.isWaterMetered) "METER" else "FIXED",
                waterRateKhr = waterRateOrFee,
                rubbishFeeKhr = rubbishFee,
                initialElecReading = initialElec,
                initialWaterReading = initialWater
            )

            result.onSuccess { tid ->
                _uiState.update { it.copy(isSaving = false, isSuccess = true, createdTenancyId = tid) }
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "Move-in failed.") }
            }
        }
    }

    private fun parseKhrInput(amount: String): Long? = com.example.roomkit.Date.Billing.CurrencyFormatter.parseKhrInput(amount)
}
