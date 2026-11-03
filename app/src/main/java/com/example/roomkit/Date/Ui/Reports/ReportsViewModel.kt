package com.example.roomkit.Date.Reports

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

data class ReportsState(
    val month: YearMonth,
    val loading: Boolean = true,
    val totals: ReportTotals? = null,
    val error: String? = null,
    val updatedAt: Long = 0,
    val dashboard: ReportDashboard? = null
)

class ReportsViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val repository = ReportsRepository(db.ReportDao())
    private val initial = runCatching { YearMonth.parse(saved.get<String>("reportMonth")) }
        .getOrNull() ?: currentMonth()
    private val mutable = MutableStateFlow(ReportsState(initial))
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun currentMonth(): YearMonth = YearMonth.now(ZoneId.of("Asia/Phnom_Penh"))

    fun select(delta: Long) {
        val next = state.value.month.plusMonths(delta)
        if (next > currentMonth() || next < YearMonth.of(1, 6)) return
        saved["reportMonth"] = next.toString()
        mutable.value = ReportsState(next)
        refresh()
    }

    fun refresh() {
        job?.cancel()
        val month = state.value.month
        mutable.value = ReportsState(month)
        job = viewModelScope.launch {
            try {
                val result = db.withTransaction { repository.dashboard(month) }
                mutable.value = ReportsState(month, loading = false, totals = result.totals, dashboard = result,
                    updatedAt = System.currentTimeMillis())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = ReportsState(month, loading = false,
                    error = "Report could not be loaded. Please try again.")
            }
        }
    }
}
