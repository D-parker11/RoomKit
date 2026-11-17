@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Payment

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class PaymentSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("roomkit_payment_settings", Context.MODE_PRIVATE)
    fun load(): StaticKhqr? {
        if (!prefs.getBoolean("confirmed", false)) return null
        val raw = prefs.getString("payload", null) ?: return null
        return try { StaticKhqrParser.parse(raw) } catch (_: IllegalArgumentException) { null }
    }
    fun includeInPdf(): Boolean = prefs.getBoolean("include_pdf", false)
    fun observe() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(load()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    suspend fun save(qr: StaticKhqr, includePdf: Boolean = false) = withContext(Dispatchers.IO) {
        StaticKhqrParser.parse(qr.payload)
        check(prefs.edit().putString("payload", qr.payload).putBoolean("confirmed", true).putBoolean("include_pdf", includePdf).commit()) {
            "Payment settings could not be saved."
        }
    }
    suspend fun remove() = withContext(Dispatchers.IO) {
        check(prefs.edit().clear().commit()) { "Payment settings could not be removed." }
    }
}
