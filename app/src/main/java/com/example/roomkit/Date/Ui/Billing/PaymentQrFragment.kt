package com.example.roomkit.Date.Ui.Billing

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentPaymentQrBinding
import kotlinx.coroutines.launch

class PaymentQrFragment : Fragment() {

    private var _binding: FragmentPaymentQrBinding? = null
    private val binding get() = _binding!!

    private val invoiceId: Long by lazy { arguments?.getLong("invoiceId") ?: 0L }
    private val viewModel: PaymentQrViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = invoiceId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentQrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBackPaymentQr.setOnClickListener { findNavController().popBackStack() }

        // 1. DIRECTLY LOAD AND DISPLAY YOUR BANK QR CODE
        val bankQrBitmap: Bitmap? = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_qr_code)
        } catch (e: Exception) {
            null
        }

        if (bankQrBitmap != null) {
            binding.ivQrCode.setImageBitmap(bankQrBitmap)
            binding.ivQrCode.visibility = View.VISIBLE
            binding.progressQr.visibility = View.GONE
            // Send to viewModel so Share and Save Image include it
            viewModel.setCustomBankQrBitmap(bankQrBitmap)
        } else {
            // Fallback: Use whatever drawable is set in XML
            binding.ivQrCode.setImageResource(R.drawable.ic_qr_code)
            binding.ivQrCode.visibility = View.VISIBLE
            binding.progressQr.visibility = View.GONE
        }

        // 2. SHARE BUTTON
        binding.btnShareQr.setOnClickListener {
            val s = viewModel.uiState.value
            if (!s.isPayable) {
                Toast.makeText(requireContext(), "Payment QR is disabled for this invoice.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Ensure the ViewModel has the bitmap from the displayed ImageView
            val drawable = binding.ivQrCode.drawable
            if (viewModel.uiState.value.qrBitmap == null && drawable is android.graphics.drawable.BitmapDrawable) {
                viewModel.setCustomBankQrBitmap(drawable.bitmap)
            }

            val shareUri = viewModel.prepareShareImageUri(requireContext())
            if (shareUri != null) {
                val message = """
            RoomKit Payment
            
            Tenant: ${s.tenantName}
            Room: ${s.roomNumber}
            Invoice: ${s.invoiceNumberFormatted}
            Period: ${s.billingPeriodFormatted}
            
            Amount Due: ${CurrencyFormatter.formatKhr(s.remainingBalanceKhr)}
            
            Please scan the attached KHQR with any Cambodian banking app to pay.
        """.trimIndent()

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_TEXT, message)
                    clipData = ClipData.newRawUri("KHQR Payment", shareUri)
                    // Critical: Grant read permission on the intent
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Share Payment QR via").apply {
                    // Critical: Also pass read permission to the system chooser activity
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Grant explicit read URI permission to all potential matching receiver apps
                val resInfoList = requireContext().packageManager.queryIntentActivities(
                    chooser,
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                )
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    requireContext().grantUriPermission(
                        packageName,
                        shareUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                startActivity(chooser)
            } else {
                Toast.makeText(requireContext(), "Unable to prepare share image.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. SAVE BUTTON
        binding.btnSaveImage.setOnClickListener {
            val s = viewModel.uiState.value
            if (!s.isPayable) {
                Toast.makeText(requireContext(), "Payment QR is disabled for this invoice.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSaveImage.isEnabled = false
            viewModel.saveImageToGallery(requireContext()) { res ->
                binding.btnSaveImage.isEnabled = true
                res.onSuccess {
                    Toast.makeText(requireContext(), "Payment QR saved to Pictures/RoomKit", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(requireContext(), "Unable to save image: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. OBSERVE INVOICE STATE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(s: PaymentQrUiState) {
        if (s.errorMessage != null && !s.isPayable) {
            Toast.makeText(requireContext(), s.errorMessage, Toast.LENGTH_LONG).show()
        }

        // Keep QR visible; hide the progress spinner once invoice data finishes loading
        binding.progressQr.visibility = if (s.isLoading) View.VISIBLE else View.GONE

        binding.tvRoomTenantPill.text = "Room ${s.roomNumber} • ${s.tenantName}"
        binding.tvBillingPeriod.text = s.billingPeriodFormatted
        binding.tvAmountDue.text = CurrencyFormatter.formatKhr(s.remainingBalanceKhr)
        binding.tvPaymentNotice.text = "PAYMENT AMOUNT IS EXACT — ${CurrencyFormatter.formatKhr(s.remainingBalanceKhr)}"

        // Buttons state
        binding.btnShareQr.isEnabled = s.isPayable
        binding.btnSaveImage.isEnabled = s.isPayable
        val alpha = if (s.isPayable) 1.0f else 0.5f
        binding.btnShareQr.alpha = alpha
        binding.btnSaveImage.alpha = alpha
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}