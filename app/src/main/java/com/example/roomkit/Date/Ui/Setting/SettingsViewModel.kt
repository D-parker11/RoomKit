package com.example.roomkit.Date.Settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsSaveState(val saving: Boolean = false, val message: String = "", val error: Boolean = false)

class SettingsViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mutable = MutableStateFlow(SettingsSaveState())
    val state = mutable.asStateFlow()

    init {
        val value = repository.load()
        if (!saved.contains("due")) {
            saved["due"] = value.dueDay.toString()
            saved["electricity"] = value.electricityRate.toString()
            saved["fixedWater"] = value.fixedWaterFee.toString()
            saved["waterRate"] = value.waterRate.toString()
            saved["rubbish"] = value.rubbishFee.toString()
            saved["metered"] = value.waterMetered
        }
    }

    fun text(key: String): String = saved.get<String>(key).orEmpty()
    fun metered(): Boolean = saved["metered"] ?: false
    fun edit(key: String, value: String) {
        if (state.value.saving) return
        saved[key] = value
        mutable.value = SettingsSaveState()
    }
    fun waterMethod(value: Boolean) {
        if (state.value.saving) return
        saved["metered"] = value
        mutable.value = SettingsSaveState()
    }

    fun save() {
        if (state.value.saving) return
        val defaults = try {
            fun amount(key: String, label: String): Long =
                text(key).trim().toLongOrNull()?.takeIf { it >= 0 }
                    ?: error("Enter a valid whole KHR amount for $label.")
            BillingDefaults(
                dueDay = text("due").trim().toIntOrNull() ?: error("Enter a due day from 1 to 28."),
                electricityRate = amount("electricity", "electricity"),
                waterMetered = metered(),
                fixedWaterFee = amount("fixedWater", "fixed water"),
                waterRate = amount("waterRate", "metered water"),
                rubbishFee = amount("rubbish", "rubbish")
            ).validated()
        } catch (e: Exception) {
            mutable.value = SettingsSaveState(message = e.message ?: "Check your entries.", error = true)
            return
        }
        mutable.value = SettingsSaveState(saving = true)
        viewModelScope.launch {
            try {
                repository.save(defaults)
                mutable.value = SettingsSaveState(message = "Defaults saved for new tenancies.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = SettingsSaveState(message = e.message ?: "Could not save settings.", error = true)
            }
        }
    }
}
