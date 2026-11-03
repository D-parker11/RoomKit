package com.example.roomkit.Date.Settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("roomkit_settings", Context.MODE_PRIVATE)

    fun load(): BillingDefaults = BillingDefaults(
        dueDay = prefs.getInt("due_day", 5),
        electricityRate = prefs.getLong("electricity_rate", 1000),
        waterMetered = prefs.getBoolean("water_metered", false),
        fixedWaterFee = prefs.getLong("fixed_water_fee", 20000),
        waterRate = prefs.getLong("water_rate", 1500),
        rubbishFee = prefs.getLong("rubbish_fee", 10000)
    ).validated()

    suspend fun save(value: BillingDefaults) = withContext(Dispatchers.IO) {
        value.validated()
        check(prefs.edit()
            .putInt("due_day", value.dueDay)
            .putLong("electricity_rate", value.electricityRate)
            .putBoolean("water_metered", value.waterMetered)
            .putLong("fixed_water_fee", value.fixedWaterFee)
            .putLong("water_rate", value.waterRate)
            .putLong("rubbish_fee", value.rubbishFee)
            .commit()) { "Settings could not be saved. Please try again." }
    }
}
