package com.example.roomkit.Date.Ui.Billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Repositories.BillingRepository
import com.example.roomkit.Date.Repositories.ReplacementInvoiceRepository
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentReplacementInvoiceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** XML presentation for the saved replacement-draft workflow. */
class ReplacementInvoiceFragment : Fragment() {
    private var _binding: FragmentReplacementInvoiceBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var rendering = false
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private val fields = linkedMapOf<String, Pair<TextInputEditText, TextInputLayout>>()
    private val numbers = NumberFormat.getIntegerInstance(Locale.US)
    private val dueFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    private val replacementRepository: ReplacementInvoiceRepository by lazy {
        val context = requireContext().applicationContext
        val db = AppDatabase.getInstance(context)
        val billing = BillingRepository(context, db, db.buildingDao(), db.invoiceDao(),
            db.paymentDao(), db.tenancyDao(), db.tenantDao(), db.roomDao(), db.meterReadingDao())
        ReplacementInvoiceRepository(db, billing)
    }
    private val vm: ReplacementInvoiceViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReplacementInvoiceViewModel(replacementRepository,
                    arguments?.getLong("originalInvoiceId") ?: 0L,
                    arguments?.getLong("draftInvoiceId") ?: 0L) as T
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReplacementInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            fields["rent"] = etRent to tilRent
            fields["electricityPrevious"] = etElectricityPrevious to tilElectricityPrevious
            fields["electricityCurrent"] = etElectricityCurrent to tilElectricityCurrent
            fields["electricityRate"] = etElectricityRate to tilElectricityRate
            fields["waterPrevious"] = etWaterPrevious to tilWaterPrevious
            fields["waterCurrent"] = etWaterCurrent to tilWaterCurrent
            fields["waterRate"] = etWaterRate to tilWaterRate
            fields["rubbish"] = etRubbish to tilRubbish
            fields["other"] = etOther to tilOther
            fields["discount"] = etDiscount to tilDiscount
            fields["discountReason"] = etDiscountReason to tilDiscountReason
            btnBack.setOnClickListener { leave() }
            btnDeleteDraft.setOnClickListener { confirmDelete() }
            btnSave.setOnClickListener { vm.save() }
            btnIssue.setOnClickListener { confirmIssue() }
            btnOriginal.setOnClickListener {
                vm.state.value.detail?.invoice?.replacesInvoiceId?.let { id ->
                    findNavController().navigate(R.id.invoiceDetailFragment, bundleOf("invoiceId" to id))
                }
            }
            btnAbout.setOnClickListener {
                dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(if (vm.state.value.detail?.invoice?.replacesInvoiceId == null) R.string.rd_about else R.string.replacement_details)
                    .setMessage(if (vm.state.value.detail?.invoice?.replacesInvoiceId == null) R.string.rd_details else R.string.replacement_details_message)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
        for ((key, pair) in fields) {
            pair.first.doAfterTextChanged {
                if (!rendering) vm.edit(key, it?.toString().orEmpty())
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { leave() }
            })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    if (state.deleted && findNavController().currentDestination?.id == R.id.replacementInvoiceFragment) {
                        findNavController().navigate(R.id.billingFragment, null,
                            NavOptions.Builder().setPopUpTo(R.id.dashboardFragment, false).setLaunchSingleTop(true).build())
                        return@collect
                    }
                    val id = state.openInvoiceId
                    if (id != null && findNavController().currentDestination?.id == R.id.replacementInvoiceFragment) {
                        findNavController().navigate(R.id.invoiceDetailFragment,
                            bundleOf("invoiceId" to id),
                            NavOptions.Builder().setPopUpTo(R.id.replacementInvoiceFragment, true).build())
                    } else {
                        render(state)
                    }
                }
            }
        }
    }

    private data class Preview(
        val invoice: InvoiceEntity? = null,
        val errors: Map<String, String> = emptyMap(),
        val message: String? = null
    )

    /** Preview uses the same calculator as save/issue; no database writes on typing. */
    private fun preview(state: ReplacementUiState): Preview {
        val invoice = state.detail?.invoice ?: return Preview()
        val metered = !invoice.waterBillingMethod.equals("FIXED", true)
        val keys = fields.keys.filter { it != "discountReason" && (metered || it !in setOf("waterPrevious", "waterCurrent")) }
        val errors = linkedMapOf<String, String>()
        for (key in keys) {
            val value = state.values[key]?.trim()?.toLongOrNull()
            if (value == null || value < 0L) errors[key] = getString(R.string.replacement_whole_number)
        }
        fun checkReadings(previous: String, current: String) {
            val p = state.values[previous]?.trim()?.toLongOrNull()
            val c = state.values[current]?.trim()?.toLongOrNull()
            if (p != null && c != null && c < p) errors[current] = getString(R.string.replacement_reading_low)
        }
        checkReadings("electricityPrevious", "electricityCurrent")
        if (metered) checkReadings("waterPrevious", "waterCurrent")
        if (state.values["discountReason"].orEmpty().trim().length > 500) {
            errors["discountReason"] = getString(R.string.if_discount_reason_long)
        }
        if (errors.isNotEmpty()) return Preview(errors = errors, message = getString(R.string.replacement_invalid))
        return try {
            Preview(invoice = replacementRepository.calculateDraft(invoice, state.values))
        } catch (_: ArithmeticException) {
            Preview(message = getString(R.string.replacement_amount_large))
        } catch (e: IllegalArgumentException) {
            // BillingCalculator rejects a discount greater than rent + utilities + fees.
            if (e.message?.contains("discount", ignoreCase = true) == true) {
                Preview(errors = mapOf("discount" to getString(R.string.replacement_discount_high)),
                    message = getString(R.string.replacement_invalid))
            } else Preview(message = e.message ?: getString(R.string.replacement_invalid))
        } catch (e: IllegalStateException) {
            Preview(message = e.message ?: getString(R.string.replacement_invalid))
        }
    }

    private fun render(state: ReplacementUiState) {
        val b = _binding ?: return
        val detail = state.detail
        val invoice = detail?.invoice
        val metered = invoice != null && !invoice.waterBillingMethod.equals("FIXED", true)
        val editable = invoice?.status == "DRAFT" && !state.busy
        val calculation = preview(state)
        b.progressLoading.isVisible = state.busy
        b.btnBack.isEnabled = !state.busy
        val regular = invoice != null && invoice.replacesInvoiceId == null
        b.tvEditorTitle.setText(if (regular) R.string.rd_title else R.string.replacement_title)
        b.tvEditorNote.setText(if (regular) R.string.rd_note else R.string.replacement_note)
        if (invoice != null && (invoice.electricityCarriedUsage > 0 || invoice.waterCarriedUsage > 0)) {
            b.tvEditorNote.text = "${b.tvEditorNote.text}\nUsage includes old-meter consumption: electricity ${invoice.electricityCarriedUsage}, water ${invoice.waterCarriedUsage}. Previous/current fields refer to the new meter."
        }
        b.btnAbout.setText(if (regular) R.string.rd_about else R.string.replacement_details)
        b.btnDeleteDraft.isVisible = regular && invoice?.status == "DRAFT"
        b.btnDeleteDraft.isEnabled = editable
        b.btnOriginal.isVisible = invoice?.replacesInvoiceId != null
        b.btnOriginal.isEnabled = invoice?.replacesInvoiceId != null && !state.busy
        b.btnAbout.isEnabled = !state.busy
        if (invoice != null) {
            val period = Calendar.getInstance().apply { clear(); set(invoice.billingYear, invoice.billingMonth - 1, 1) }
            b.tvRoom.text = getString(R.string.replacement_room, detail?.roomNumber.orEmpty())
            b.tvTenant.text = detail?.tenantName.orEmpty()
            b.tvPeriod.text = monthFormat.format(period.time)
            b.tvDue.text = getString(R.string.replacement_due, dueFormat.format(Date(invoice.dueDate)))
            b.tvDraftNumber.text = getString(R.string.replacement_draft_number, invoice.invoiceId)
            b.btnOriginal.text = getString(R.string.replacement_original, invoice.replacesInvoiceId ?: 0L)
        } else {
            b.tvRoom.setText(R.string.replacement_loading)
        }
        rendering = true
        for ((key, pair) in fields) {
            val value = state.values[key].orEmpty()
            if (pair.first.text.toString() != value) pair.first.setText(value)
            pair.first.isEnabled = editable
            pair.second.error = calculation.errors[key]
        }
        rendering = false
        b.rowWaterReadings.isVisible = metered
        b.tvWaterUsage.isVisible = metered
        b.tvWaterMethod.setText(if (metered) R.string.replacement_water_metered else R.string.replacement_water_fixed)
        b.tilWaterRate.hint = getString(if (metered) R.string.replacement_rate else R.string.replacement_fixed_fee)
        b.tilWaterRate.suffixText = getString(if (metered) R.string.replacement_rate_khr else R.string.replacement_khr)
        val calculated = calculation.invoice
        val dash = getString(R.string.replacement_unavailable)
        b.tvTotal.text = calculated?.let { CurrencyFormatter.formatKhr(it.totalKhr) } ?: dash
        b.tvElectricityCharge.text = calculated?.let { CurrencyFormatter.formatKhr(it.electricityChargeKhr) } ?: dash
        b.tvWaterCharge.text = calculated?.let { CurrencyFormatter.formatKhr(it.waterChargeKhr) } ?: dash
        b.tvElectricityUsage.text = calculated?.let {
            getString(R.string.replacement_usage, numbers.format(it.electricityUsage), getString(R.string.replacement_kwh))
        } ?: dash
        b.tvWaterUsage.text = calculated?.let {
            getString(R.string.replacement_usage, numbers.format(it.waterUsage ?: 0L), getString(R.string.replacement_m3))
        } ?: dash
        val notice = state.error ?: calculation.message
        b.tvNotice.text = notice.orEmpty()
        b.tvNotice.isVisible = !notice.isNullOrBlank()
        b.tvSaveState.setText(when {
            state.busy && invoice == null -> R.string.replacement_loading
            state.busy -> R.string.replacement_saving
            state.dirty -> R.string.replacement_unsaved
            invoice != null -> R.string.replacement_saved
            else -> R.string.replacement_ready
        })
        b.tvSaveState.setTextColor(ContextCompat.getColor(requireContext(),
            if (state.dirty) R.color.replacement_amber else R.color.replacement_success))
        b.btnSave.isEnabled = editable && calculated != null && state.dirty
        b.btnIssue.isEnabled = editable && calculated != null
        b.btnSave.alpha = if (b.btnSave.isEnabled) 1f else 0.45f
        b.btnIssue.alpha = if (b.btnIssue.isEnabled) 1f else 0.45f
    }

    private fun confirmIssue() {
        val state = vm.state.value
        if (state.busy || dialog?.isShowing == true) return
        val total = preview(state).invoice?.totalKhr ?: return
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (state.detail?.invoice?.replacesInvoiceId == null) R.string.rd_issue_title else R.string.replacement_issue_title)
            .setMessage(getString(if (state.detail?.invoice?.replacesInvoiceId == null) R.string.rd_issue_message else R.string.replacement_issue_message, CurrencyFormatter.formatKhr(total)))
            .setNegativeButton(R.string.replacement_keep_draft, null)
            .setPositiveButton(R.string.replacement_issue) { _, _ -> vm.save(issue = true) }
            .show()
    }

    private fun confirmDelete() {
        val state = vm.state.value
        if (state.busy || state.detail?.invoice?.replacesInvoiceId != null || dialog?.isShowing == true) return
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rd_delete_title).setMessage(R.string.rd_delete_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.rd_delete) { _, _ -> vm.deleteDraft() }.show()
    }

    private fun leave() {
        if (vm.state.value.busy || dialog?.isShowing == true) return
        if (!vm.state.value.dirty) { findNavController().popBackStack(); return }
        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.replacement_leave_title)
            .setMessage(R.string.replacement_leave_message)
            .setNegativeButton(R.string.replacement_keep_editing, null)
            .setPositiveButton(R.string.replacement_leave) { _, _ -> findNavController().popBackStack() }
            .show()
    }

    override fun onDestroyView() {
        dialog?.dismiss(); dialog = null
        fields.clear()
        _binding = null
        super.onDestroyView()
    }
}
