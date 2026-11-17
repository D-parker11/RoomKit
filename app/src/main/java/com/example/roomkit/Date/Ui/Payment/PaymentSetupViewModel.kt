@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Payment

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PaymentSetupState(
    val qr: StaticKhqr? = null, val bitmap: Bitmap? = null,
    val busy: Boolean = false, val pending: Boolean = false,
    val message: String = "", val confirmed: Boolean = false, val includePdf: Boolean = false
)

class PaymentSetupViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PaymentSettingsRepository(app)
    private val mutable = MutableStateFlow(PaymentSetupState())
    val state = mutable.asStateFlow()
    init { perform { display(repository.load(), false) } }
    fun importImage(uri: Uri) = perform {
        val qr = withContext(Dispatchers.IO) { KhqrImages.importImage(getApplication(), uri) }
        display(qr, true)
    }
    fun includePdf(value: Boolean) {
        if (!state.value.busy && value != state.value.includePdf) mutable.value = mutable.value.copy(includePdf = value, pending = true)
    }
    fun confirm(value: Boolean) { mutable.value = mutable.value.copy(confirmed = value) }
    fun save() {
        val s = state.value
        if (!s.pending || !s.confirmed || s.qr == null) return
        perform {
            repository.save(s.qr, s.includePdf)
            mutable.value = mutable.value.copy(pending = false, message = "Payment QR saved on this device.")
        }
    }
    fun remove() = perform { repository.remove(); display(null, false) }
    private suspend fun display(qr: StaticKhqr?, pending: Boolean) {
        val image = qr?.let { withContext(Dispatchers.Default) { KhqrImages.render(it.payload) } }
        mutable.value = PaymentSetupState(qr, image, busy = true, pending = pending, includePdf = repository.includeInPdf())
    }
    private fun perform(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "")
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(message = e.message ?: "Unable to update payment QR.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
