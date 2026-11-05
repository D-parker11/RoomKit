package com.example.roomkit.Date.Foundation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Backup.JsonBackupRepository
import com.example.roomkit.Date.Repositories.MonthlyCloseRepository
import com.example.roomkit.Date.Repositories.phase3Billing
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

data class FoundationState(val busy: Boolean = false, val message: String = "", val restored: Boolean = false)

class FoundationToolsViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val db = AppDatabase.getInstance(app)
    private val months = MonthlyCloseRepository(db, phase3Billing(app, db))
    private val backups = JsonBackupRepository(app, db)
    private val mutable = MutableStateFlow(FoundationState())
    val state = mutable.asStateFlow()
    var month: String
        get() = saved["month"] ?: YearMonth.now(ZoneId.of("Asia/Phnom_Penh")).minusMonths(1).toString()
        set(value) { saved["month"] = value }
    private var password: CharArray? = null
    fun keepPassword(value: CharArray) { clearPassword(); password = value }
    fun clearPassword() { password?.fill('\u0000'); password = null }
    private fun run(block: suspend () -> FoundationState) {
        if (mutable.value.busy) return
        mutable.value = FoundationState(busy=true)
        viewModelScope.launch(Dispatchers.IO) {
            try { mutable.value = block() }
            catch (e: CancellationException) { mutable.value = FoundationState(); throw e }
            catch (e: Exception) { mutable.value = FoundationState(message=e.message ?: "Operation failed. No successful result was reported.") }
        }
    }
    private fun period(): YearMonth = try { YearMonth.parse(month.trim()).also { require(it.year in 1..9999) } }
        catch (e: Exception) { throw IllegalArgumentException("Enter a valid month as YYYY-MM.") }
    fun refresh() = run {
        val p = period(); val s = months.state(p)
        val events = months.history(p).joinToString("\n") { "${it.action}: ${it.reason} (${java.util.Date(it.createdAt)})" }
        FoundationState(message="$p: ${s?.status ?: "OPEN"}\n${events.ifBlank { "No close/reopen events." }}")
    }
    fun close() = run { val p=period(); val ids=months.close(p); FoundationState(message="$p is CLOSED. Invoice IDs: ${ids.joinToString().ifBlank { "none" }}. Repeating close reuses this result.") }
    fun reopen(reason: String) = run { val p=period(); months.reopen(p, reason); FoundationState(message="$p is OPEN. Issued invoices and recorded readings remain locked.") }
    fun export(uri: Uri?) {
        val pass=password; password=null
        if (uri == null || pass == null) { pass?.fill('\u0000'); mutable.value=FoundationState(message="Backup cancelled or password expired. Choose Backup again."); return }
        if (mutable.value.busy) { pass.fill('\u0000'); return }
        run { try { backups.export(uri, pass); FoundationState(message="Protected JSON backup saved. Keep its password separately; lost passwords cannot be recovered.") } finally { pass.fill('\u0000') } }
    }
    fun restore(uri: Uri?) {
        val pass=password; password=null
        if (uri == null || pass == null) { pass?.fill('\u0000'); mutable.value=FoundationState(message="Restore cancelled or password expired. Choose Restore again."); return }
        if (mutable.value.busy) { pass.fill('\u0000'); return }
        run { try { backups.restore(uri, pass); FoundationState(message="Backup restored. Rental and financial records were replaced.", restored=true) } finally { pass.fill('\u0000') } }
    }
    fun acknowledgeRestore() { mutable.value = FoundationState(message="Restore completed.") }
    override fun onCleared() { clearPassword() }
}
