@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Billing

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Billing.PaymentQrImageGenerator
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentPaymentQrBinding
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaymentQrFragment : Fragment(R.layout.fragment_payment_qr) {

    private var binding: FragmentPaymentQrBinding? = null
    private var exporting = false

    private val vm: PaymentQrViewModel by viewModels {
        ViewModelFactory(
            requireContext(),
            targetId = arguments?.getLong("invoiceId") ?: 0L
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentPaymentQrBinding.bind(view)
        binding = b

        b.btnBackPaymentQr.setOnClickListener {
            findNavController().popBackStack()
        }

        b.btnSetup.setOnClickListener {
            val nav = findNavController()
            if (!parentFragmentManager.isStateSaved &&
                nav.currentDestination?.id == R.id.paymentQrFragment
            ) {
                nav.navigate(R.id.paymentSetupFragment)
            }
        }

        b.btnShareQr.setOnClickListener { export(share = true) }
        b.btnSaveImage.setOnClickListener { export(share = false) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { render(it) }
            }
        }
    }

    private fun render(s: PaymentQrUiState) {
        val b = binding ?: return

        val ready = !s.isLoading &&
                s.isPayable &&
                s.qrBitmap != null

        val hasInvoice = !s.isLoading && s.invoiceId > 0L

        val needsSetup = hasInvoice &&
                !s.isConfigured &&
                s.remainingBalanceKhr > 0L &&
                s.status.trim().uppercase(Locale.ROOT) in setOf(
            "ISSUED",
            "UNPAID",
            "PARTIAL",
            "PARTIALLY_PAID",
            "PAID",
            "OVERDUE"
        )

        b.progressQr.isVisible = s.isLoading

        // Hide empty invoice fields while loading or when unavailable.
        b.invoiceCard.isVisible = hasInvoice
        b.paymentCard.isVisible = !s.isLoading

        b.tvRoomTenantPill.text = "Room ${s.roomNumber} · ${s.tenantName}"
        b.tvBillingPeriod.text = "${s.billingPeriodFormatted} · ${s.invoiceNumberFormatted}"
        b.tvAmountDue.text = CurrencyFormatter.formatKhr(s.remainingBalanceKhr)

        b.btnSetup.isVisible = needsSetup

        b.ivQrCode.setImageBitmap(if (ready) s.qrBitmap else null)
        b.ivQrCode.isVisible = ready

        // Show only the recipient's display name (hide technical routing IDs).
        b.tvReceiver.isVisible = ready && s.receiverName.isNotBlank()
        b.tvReceiver.text = s.receiverName

        b.tvStateTitle.text = when {
            ready -> "Scan to pay"
            needsSetup -> "Add your payment QR first"
            hasInvoice && s.remainingBalanceKhr == 0L -> "No payment due"
            else -> "Payment QR unavailable"
        }

        b.tvPaymentNotice.isVisible = true
        b.tvPaymentNotice.text = when {
            ready ->
                "Enter ${CurrencyFormatter.formatKhr(s.remainingBalanceKhr)} " +
                        "in your banking app. Check the receiver before paying."

            needsSetup ->
                "Import your receiving KHQR image to display it here for this invoice."

            else ->
                s.errorMessage
                    ?: "Unable to load payment details. Go back and reopen this invoice."
        }

        b.shareActions.isVisible = ready
        b.btnShareQr.isEnabled = ready && !exporting
        b.btnSaveImage.isEnabled = ready && !exporting
        b.btnShareQr.text = if (exporting) "Preparing…" else "Share payment QR"

        b.manualNotice.isVisible = ready
    }

    private fun export(share: Boolean) {
        val s = vm.uiState.value
        val qr = s.qrBitmap ?: return
        if (exporting || !s.isPayable) return

        exporting = true
        render(s)

        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val card = PaymentQrImageGenerator.createPaymentCardBitmap(
                        qr,
                        s.roomNumber,
                        s.tenantName,
                        s.billingPeriodFormatted,
                        s.invoiceNumberFormatted,
                        s.remainingBalanceKhr,
                        s.receiverName,
                        "" // Keep technical routing ID out of exported card image
                    )
                    try {
                        val name = "RoomKit_QR_${s.invoiceId}_${System.currentTimeMillis()}"
                        if (share) {
                            PaymentQrImageGenerator.saveToCacheForSharing(app, card, name)
                        } else {
                            PaymentQrImageGenerator.saveToPicturesGallery(app, card, name)
                        }
                    } finally {
                        card.recycle()
                    }
                }

                // Do not share a card after the observed account/balance changed.
                if (vm.uiState.value != s) {
                    Toast.makeText(
                        app,
                        "Payment details changed. Please create a new payment card.",
                        Toast.LENGTH_LONG
                    ).show()
                } else if (share && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${s.invoiceNumberFormatted} · Enter ${CurrencyFormatter.formatKhr(s.remainingBalanceKhr)}. Receiver: ${s.receiverName}"
                        )
                        clipData = ClipData.newRawUri("Payment QR", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Share payment QR"))
                } else if (!share) {
                    Toast.makeText(app, "Saved to Pictures/RoomKit", Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(app, e.message ?: "Unable to export QR.", Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
                render(vm.uiState.value)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}