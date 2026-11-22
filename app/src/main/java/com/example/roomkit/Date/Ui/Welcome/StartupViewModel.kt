@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Welcome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StartupState(
    val loading: Boolean = true,
    val hasProperty: Boolean? = null,
    val failed: Boolean = false
)

class StartupViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableStateFlow(StartupState())
    val state = mutable.asStateFlow()
    private var checking = false
    init { retry() }

    fun retry() {
        if (checking) return
        checking = true
        mutable.value = StartupState()
        viewModelScope.launch {
            try {
                val count = AppDatabase.getInstance(getApplication()).buildingDao().getBuildingCount()
                mutable.value = StartupState(loading = false, hasProperty = count > 0)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A read failure must not be mistaken for an empty database.
                mutable.value = StartupState(loading = false, failed = true)
            } finally {
                checking = false
            }
        }
    }
}
