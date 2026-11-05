package com.example.roomkit.Date.Ui.Meter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Model.MeterReadingUiState
import com.example.roomkit.Date.Repositories.MeterReadingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MeterReadingViewModel(private val roomId: Long, private val repository: MeterReadingRepository) : ViewModel() {
    private val date = Instant.now().atZone(ZoneId.of("Asia/Phnom_Penh"))
    private val _uiState = MutableStateFlow(MeterReadingUiState(roomId=roomId,
        billingMonth=date.monthValue, billingYear=date.year,
        billingMonthText=date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))))
    val uiState = _uiState.asStateFlow()
    private var loadedContext: MeterReadingRepository.InitialMeterContext? = null
    init { reload() }

    fun reload() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isLoading=true, hasActiveTenancy=false, globalError=null) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val context = repository.loadContext(roomId,state.billingMonth,state.billingYear)
                val history = repository.history(roomId)
                loadedContext = context.getOrNull()
                val ctx = loadedContext
                if (ctx == null) {
                    _uiState.update { it.copy(isLoading=false, hasActiveTenancy=false, history=history,
                        globalError=context.exceptionOrNull()?.message ?: "Unable to load meter details.") }
                } else {
                    _uiState.update { it.copy(isLoading=false, hasActiveTenancy=true,
                        roomNumber=ctx.room.roomNumber, tenantName=ctx.tenantName,
                        previousElectricity=ctx.previousElectricity,
                        previousWater=ctx.previousWater,
                        currentElectricityInput=ctx.existingCurrentElectricity?.toString().orEmpty(),
                        currentWaterInput=ctx.existingCurrentWater?.toString().orEmpty(),
                        electricityUsage=ctx.existingCurrentElectricity?.let(ctx.electricityBasis::usage),
                        waterUsage=ctx.existingCurrentWater?.let(ctx.waterBasis::usage),
                        electricityCarriedUsage=ctx.electricityBasis.carried, waterCarriedUsage=ctx.waterBasis.carried,
                        isWaterFixed=ctx.tenancy.waterBillingMethod.equals("FIXED",true),
                        fixedWaterFee=ctx.tenancy.waterRate, rubbishFee=ctx.tenancy.rubbishFee,
                        currency=ctx.tenancy.currency, isLockedByInvoice=ctx.isLockedByInvoice,
                        electricityError=null, waterError=null, history=history,
                        globalError=if(ctx.isLockedByInvoice) "Monthly readings are read-only after saving. Closed months must be reopened for new preparation." else null) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { _uiState.update { it.copy(isLoading=false, globalError=e.message) } }
        }
    }
    fun selectPeriod(value: String) {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        try {
            val period=java.time.YearMonth.parse(value.trim())
            require(period.year in 1..9999 && period <= java.time.YearMonth.now(ZoneId.of("Asia/Phnom_Penh"))) {
                "Choose the current month or a past month."
            }
            loadedContext=null
            _uiState.value=MeterReadingUiState(roomId=roomId,billingMonth=period.monthValue,billingYear=period.year,
                billingMonthText=period.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.US)))
            reload()
        } catch(e: Exception) { _uiState.update { it.copy(globalError=e.message ?: "Use YYYY-MM.") } }
    }

    fun onElectricityChanged(input: String) = change(input, true)
    fun onWaterChanged(input: String) = change(input, false)
    private fun change(input: String, electricity: Boolean) {
        val ctx = loadedContext ?: return
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        val text = input.trim()
        val basis = if(electricity) ctx.electricityBasis else ctx.waterBasis
        var error: String? = null
        val usage = if(text.isEmpty()) null else try {
            basis.usage(text.toLongOrNull() ?: error("Enter a whole number."))
        } catch(e: Exception) { error=e.message ?: "Invalid reading."; null }
        _uiState.update { if(electricity) it.copy(currentElectricityInput=text,electricityUsage=usage,electricityError=error)
        else it.copy(currentWaterInput=text,waterUsage=usage,waterError=error) }
    }
    fun replacementInput(type: String, closing: String, opening: String, reason: String) {
        if(!_uiState.value.isSaving) _uiState.update { it.copy(replacementType=type,
            replacementClosing=closing, replacementOpening=opening, replacementReason=reason) }
    }
    fun replaceMeter() {
        val state = _uiState.value
        val expected = loadedContext ?: return
        if(state.isSaving || state.isLoading) return
        val closing = state.replacementClosing.trim().toLongOrNull()
        val opening = state.replacementOpening.trim().toLongOrNull()
        if(closing == null || opening == null) {
            _uiState.update { it.copy(globalError="Enter whole numbers for both replacement readings.") }; return
        }
        _uiState.update { it.copy(isSaving=true,globalError=null) }
        viewModelScope.launch {
            val result = repository.replaceMeter(roomId,state.billingMonth,state.billingYear,state.replacementType,
                closing,opening,state.replacementReason,expected)
            _uiState.update { it.copy(isSaving=false,globalError=result.exceptionOrNull()?.message) }
            if(result.isSuccess) {
                _uiState.update { it.copy(replacementClosing="",replacementOpening="",replacementReason="") }
                reload()
            }
        }
    }
    fun saveReadings() {
        val state = _uiState.value
        val expected = loadedContext ?: return
        if(!state.canSave || state.isSaveSuccessful) return
        val electricity = state.currentElectricityInput.toLongOrNull() ?: return
        val water = if(state.isWaterFixed) 0L else state.currentWaterInput.toLongOrNull() ?: return
        _uiState.update { it.copy(isSaving=true,globalError=null) }
        viewModelScope.launch {
            repository.saveReadings(roomId,state.billingMonth,state.billingYear,state.previousElectricity,
                electricity,state.isWaterFixed,state.previousWater,water,expected)
                .onSuccess { _uiState.update { it.copy(isSaving=false,isSaveSuccessful=true) } }
                .onFailure { error -> _uiState.update { it.copy(isSaving=false,globalError=error.message) } }
        }
    }
    fun consumeSuccess() { _uiState.update { it.copy(isSaveSuccessful=false) } }
}
