@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Repositories.BillingRepository
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentInvoicePreviewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InvoicePreviewFragment : Fragment() {

    private var _binding: FragmentInvoicePreviewBinding? = null
    private val binding get() = _binding!!

    private val roomId: Long by lazy { arguments?.getLong("roomId") ?: 0L }

    private val viewModel: InvoicePreviewViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = roomId)
    }

    private var renderingAdjustments = false

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMM yyyy", Locale.US)
    private val khrFormat = DecimalFormat("#,###")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnIssueInvoice.setOnClickListener {
            showIssueConfirmation()
        }

        binding.btnSaveDraft.setOnClickListener { viewModel.saveDraft() }
        binding.etOtherCharges.doAfterTextChanged {
            if (!renderingAdjustments) viewModel.editAdjustments(it.toString(), binding.etDiscount.text.toString())
        }
        binding.etDiscount.doAfterTextChanged {
            if (!renderingAdjustments) viewModel.editAdjustments(binding.etOtherCharges.text.toString(), it.toString())
        }
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: InvoicePreviewUiState) {
        state.savedDraftId?.let { id ->
            if (findNavController().currentDestination?.id == R.id.invoicePreviewFragment) {
                findNavController().navigate(R.id.replacementInvoiceFragment,
                    bundleOf("draftInvoiceId" to id),
                    androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.invoicePreviewFragment, true).build())
            }
            return
        }
        val busy = state.isLoading || state.isSaving || state.isIssuing
        val ready = state.draft != null && !busy && state.issuedInvoiceId == null
        binding.etOtherCharges.isEnabled = ready
        binding.etDiscount.isEnabled = ready
        binding.btnSaveDraft.isEnabled = ready && state.validationMessage == null
        binding.btnIssueInvoice.isEnabled = ready && state.validationMessage == null
        renderingAdjustments = true
        if (binding.etOtherCharges.text.toString() != state.otherText) binding.etOtherCharges.setText(state.otherText)
        if (binding.etDiscount.text.toString() != state.discountText) binding.etDiscount.setText(state.discountText)
        renderingAdjustments = false
        val error = state.validationMessage ?: state.errorMessage
        binding.tvAdjustmentError.isVisible = error != null
        binding.tvAdjustmentError.text = error.orEmpty()
        binding.tvDraftSaveStatus.setText(when {
            busy -> R.string.bd_saving
            state.validationMessage != null || state.dirty -> R.string.bd_unsaved
            else -> R.string.bd_ready
        })
        state.draft?.let { bindDraft(it) }
        if (state.validationMessage != null) binding.tvInvoiceTotal.text = "—"
        state.issuedInvoiceId?.let { id ->
            if (findNavController().currentDestination?.id == R.id.invoicePreviewFragment) {
                findNavController().navigate(R.id.action_invoicePreviewFragment_to_invoiceDetailFragment,
                    bundleOf("invoiceId" to id))
            }
        }
    }

    private fun bindDraft(draft: BillingRepository.BillingDraft) {
        // Room & Tenant Header Details
        binding.tvRoomNumber.text = "Room ${draft.room.roomNumber}"
        binding.tvTenantName.text = "Tenant: ${draft.tenantName}"

        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, draft.billingMonth - 1)
            set(Calendar.YEAR, draft.billingYear)
        }
        binding.tvBillingMonth.text = monthYearFormat.format(cal.time)
        binding.tvDueDate.text = "Due ${dateFormat.format(Date(draft.dueDate))}"

        binding.tvDraftPeriodDates.text = getString(R.string.bd_period,
            "${draft.billingPeriodStart} – ${draft.billingPeriodEnd}")
        // Rent Breakdown
        binding.tvRentAmount.text = CurrencyFormatter.formatKhr(draft.result.rentKhr)

        // Electricity
        val elecRateStr = khrFormat.format(draft.tenancy.electricityRate)
        binding.tvElectricityDesc.text = "Electricity — ${draft.result.electricityUsage} kWh × $elecRateStr ៛"
        binding.tvElectricityAmount.text = CurrencyFormatter.formatKhr(draft.result.electricityChargeKhr)

        // Water
        val isWaterMetered = !draft.tenancy.waterBillingMethod.equals("FIXED", ignoreCase = true)
        if (isWaterMetered) {
            val waterRateStr = khrFormat.format(draft.tenancy.waterRate)
            binding.tvWaterDesc.text = "Water — ${draft.result.waterUsage} m³ × $waterRateStr ៛"
        } else {
            binding.tvWaterDesc.text = "Water — Fixed Monthly"
        }
        binding.tvWaterAmount.text = CurrencyFormatter.formatKhr(draft.result.waterChargeKhr)

        // Rubbish Fee
        binding.tvRubbishAmount.text = CurrencyFormatter.formatKhr(draft.tenancy.rubbishFee)

        // Prominent Invoice Total
        binding.tvInvoiceTotal.text = CurrencyFormatter.formatKhr(draft.result.totalKhr)

        // Utility Conversion Summary Box
        binding.tvUtilitySubtotal.text = CurrencyFormatter.formatKhr(draft.result.utilitySubtotalKhr)
    }

    private fun showIssueConfirmation() {
        val draft = viewModel.uiState.value.draft ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Issue this invoice?")
            .setMessage("After issuing:\n• Meter readings for this billing period will be locked.\n• Billing values will be saved as invoice snapshots.\n• Payments can then be recorded.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Issue Invoice") { _, _ ->
                viewModel.issueInvoice { /* Navigation follows uiState while the view is started. */ }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}