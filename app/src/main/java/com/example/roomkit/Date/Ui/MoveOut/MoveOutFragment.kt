package com.example.roomkit.Date.Ui.MoveOut

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Repositories.MoveOutInput
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentMoveOutBinding
import com.example.roomkit.databinding.ItemSummaryRowBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MoveOutFragment : Fragment() {
    private var _binding: FragmentMoveOutBinding? = null
    private val binding get() = _binding!!
    private var rendering = false
    private var dateDialog: DatePickerDialog? = null
    private val dates = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val viewModel: MoveOutViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(type: Class<T>): T {
                require(type == MoveOutViewModel::class.java)
                return MoveOutViewModel(requireArguments().getLong("tenancyId"), AppDatabase.getInstance(requireContext())) as T
            }
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoveOutBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.getLong("selectedMoveOutDate")?.let { viewModel.selectedDate = it }
        val b = binding
        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        b.btnReloadMoveOut.setOnClickListener { viewModel.load() }
        b.etMoveOutDate.setOnClickListener { showDatePicker() }
        val inputs = listOf(b.etFinalElectricity, b.etFinalWater, b.etAdditionalRent, b.etOtherFees, b.etDepositApplied, b.etDeductions, b.etChargeExplanation, b.etDeductionReason)
        inputs.forEach { input -> input.doAfterTextChanged {
            if (!rendering) {
                b.cbAcknowledge.isChecked = false
                viewModel.invalidateReview()
                updateConditionalFields()
            }
        } }
        b.btnPrimaryAction.setOnClickListener {
            val s = viewModel.state.value
            if (s.loading || s.saving) return@setOnClickListener
            if (s.settlement != null) {
                if (s.settlement.refundKhr > 0 && !s.settlement.refundPaid) showRefundRecordingDialog(s.settlement.refundKhr)
            } else if (s.review != null) {
                if (b.cbAcknowledge.isChecked) viewModel.complete()
                else showError(getString(R.string.mu_ack_required))
            } else reviewInputs()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }
    private fun showDatePicker() {
        if (viewModel.state.value.saving || dateDialog?.isShowing == true) return
        val cal = Calendar.getInstance().apply { timeInMillis = viewModel.selectedDate }
        dateDialog = DatePickerDialog(requireContext(), { _, y, m, d ->
            if (_binding != null) {
                cal.set(y, m, d)
                viewModel.selectedDate = cal.timeInMillis
                binding.cbAcknowledge.isChecked = false
                binding.etMoveOutDate.setText(dates.format(cal.time))
                viewModel.invalidateReview()
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).also { it.show() }
    }
    private fun parse(layout: TextInputLayout, optional: Boolean = false): Long? {
        layout.error = null
        val raw = layout.editText?.text?.toString()?.trim().orEmpty()
        val amount = if (optional && raw.isEmpty()) 0L else CurrencyFormatter.parseKhrInput(raw)
        if (amount == null) layout.error = getString(R.string.mu_whole_number)
        return amount
    }
    private fun reviewInputs() {
        val b = binding
        val elec = parse(b.tilFinalElectricity) ?: return
        val metered = viewModel.state.value.snapshot?.tenancy?.waterBillingMethod != "FIXED"
        val water = if (metered) parse(b.tilFinalWater) ?: return else null
        val rent = parse(b.tilAdditionalRent, true) ?: return
        val fees = parse(b.tilOtherFees, true) ?: return
        val applied = parse(b.tilDepositApplied, true) ?: return
        val deductions = parse(b.tilDeductions, true) ?: return
        viewModel.review(MoveOutInput(viewModel.selectedDate, elec, water, rent, fees,
            b.etChargeExplanation.text.toString(), applied, deductions,
            b.etDeductionReason.text.toString()))
    }
    private fun showRefundRecordingDialog(amount: Long) {
        val reference = EditText(requireContext()).apply {
            hint = getString(R.string.mu_refund_reference_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            maxLines = 1
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
            addView(reference, LinearLayout.LayoutParams(-1, -2))
        }
        val paidAt = System.currentTimeMillis()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mu_record_refund)
            .setMessage(getString(R.string.mu_refund_record_confirmation, money(amount), dates.format(Date(paidAt))))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.mu_record_refund) { _, _ ->
                viewModel.recordRefundPaid(paidAt, reference.text?.toString())
            }
            .show()
    }
    private fun updateConditionalFields() {
        val b = _binding ?: return
        fun positive(text: CharSequence?) = (text?.toString()?.toLongOrNull() ?: 0) > 0
        b.tilChargeExplanation.isVisible = positive(b.etAdditionalRent.text) || positive(b.etOtherFees.text)
        b.tilDeductionReason.isVisible = positive(b.etDeductions.text)
    }
    private fun render(s: MoveOutState) {
        val b = _binding ?: return
        rendering = true
        try {
            b.tvMoveOutMessage.text = s.error ?: if (s.loading) getString(R.string.mu_loading) else ""
            b.tvMoveOutMessage.isVisible = s.error != null || s.loading
            b.btnReloadMoveOut.isVisible = s.error != null
            b.btnReloadMoveOut.isEnabled = !s.saving
            val snap = s.snapshot
            val saved = s.settlement
            val history = saved != null || snap?.tenancy?.status == "ENDED"
            // Room initial readings belong to the current occupant, not this historical settlement.
            b.tvElecBilledBaseline.isVisible = !history
            val editable = !history && !s.loading && !s.saving && snap != null
            listOf(b.etFinalElectricity,b.etFinalWater,b.etAdditionalRent,b.etOtherFees,b.etDepositApplied,b.etDeductions,b.etChargeExplanation,b.etDeductionReason,b.etMoveOutDate).forEach { it.isEnabled = editable }
            b.layoutRefundPaidContainer.isVisible = false
            b.btnBack.isEnabled = !s.saving
            b.cbAcknowledge.isEnabled = !s.saving
            b.etMoveOutDate.setText(dates.format(Date(saved?.moveOutDate ?: viewModel.selectedDate)))
            if (snap != null) {
                b.tvSummaryTenantName.text = s.tenantName
                b.tvSummaryRoomBuilding.text = getString(R.string.mu_property_room, s.buildingName, snap.room.roomNumber)
                b.tvSummaryDeposit.text = money(snap.tenancy.deposit)
                val official = snap.invoices.filter { it.status !in listOf("DRAFT","VOID","VOIDED") }
                val debt = official.sumOf { inv -> (inv.totalKhr - snap.payments.filter { it.status == "ACTIVE" && it.invoiceId == inv.invoiceId }.sumOf { it.amountKhr }).coerceAtLeast(0) }
                b.tvSummaryUnpaidBalance.text = money(debt)
                b.tvExistingInvoices.isVisible = snap.invoices.isNotEmpty()
                b.tvExistingInvoices.text = snap.invoices.joinToString("\n\n") { inv ->
                    val paid = snap.payments.filter { it.status == "ACTIVE" && it.invoiceId == inv.invoiceId }.sumOf { it.amountKhr }
                    getString(R.string.mu_invoice,inv.invoiceId,inv.billingMonth,inv.billingYear,inv.status,money(inv.totalKhr),money(paid),money((inv.totalKhr-paid).coerceAtLeast(0)))
                }
                val eBasis = com.example.roomkit.Date.Repositories.MeterHistory.settlementBasis(snap, "ELECTRICITY")
                val wBasis = com.example.roomkit.Date.Repositories.MeterHistory.settlementBasis(snap, "WATER")
                val elecStart = eBasis.previous
                val waterStart = wBasis.previous
                b.tvElecBilledBaseline.text = getString(R.string.mu_elec_baseline,elecStart) +
                        if(eBasis.carried > 0) " · plus ${eBasis.carried} unbilled units from old meters" else ""
                val metered = snap.tenancy.waterBillingMethod != "FIXED"
                b.tilFinalWater.isVisible = metered
                b.tvWaterBilledBaseline.isVisible = metered && !history
                b.tvWaterFixedNote.isVisible = !metered
                b.tvWaterBilledBaseline.text = getString(R.string.mu_water_baseline,waterStart) + if(wBasis.carried > 0) " · plus ${wBasis.carried} unbilled units from old meters" else ""
                b.tvWaterFixedNote.text = getString(R.string.mu_fixed_water,money(snap.tenancy.waterRate))
            }
            val r = s.review
            b.cardReviewSummary.isVisible = r != null || saved != null
            if (r != null) renderRows(r.newCharges,r.existingDebt,r.input.applyDeposit,r.input.deductions,r.refund,r.debt)
            if (saved != null) {
                val finalAmount = snap?.invoices?.firstOrNull { it.invoiceId == saved.finalInvoiceId }?.totalKhr ?: 0
                renderRows(finalAmount,(saved.debtKhr+saved.appliedKhr-finalAmount).coerceAtLeast(0),saved.appliedKhr,saved.deductionsKhr,saved.refundKhr,saved.debtKhr)
                val refundStatus = when {
                    saved.refundKhr <= 0 -> getString(R.string.mu_no_refund_due)
                    saved.refundPaid -> getString(R.string.mu_paid)
                    else -> getString(R.string.mu_pending_amount, money(saved.refundKhr))
                }
                val paidDetails = if (saved.refundPaid) {
                    val paidDate = saved.refundPaidAt?.let { dates.format(Date(it)) } ?: getString(R.string.mu_date_unavailable)
                    getString(R.string.mu_refund_paid_details, paidDate, saved.refundReference.orEmpty())
                } else ""
                b.tvSavedSettlement.text = listOf(
                    getString(R.string.mu_saved,dates.format(Date(saved.moveOutDate)),saved.electricityFinal,saved.waterFinal?.toString() ?: "—",refundStatus,listOf(saved.chargeReason,saved.deductionReason).filter(String::isNotBlank).joinToString("\n")),
                    paidDetails
                ).filter(String::isNotBlank).joinToString("\n")
            } else if (history) b.tvSavedSettlement.text = getString(R.string.mu_legacy_history)
            b.tvSavedSettlement.isVisible = history
            // Keep checkbox available after a review is invalidated by checking it.
            val pendingRefund = saved != null && saved.refundKhr > 0 && !saved.refundPaid
            b.cbAcknowledge.isVisible = r != null
            b.cbAcknowledge.setText(R.string.mu_ack)
            b.btnPrimaryAction.isVisible = !history || pendingRefund
            b.btnPrimaryAction.isEnabled = !s.loading && !s.saving && snap != null && (!history || pendingRefund)
            b.btnPrimaryAction.setText(when {
                s.saving -> R.string.mu_saving
                pendingRefund -> R.string.mu_record_refund
                r != null -> R.string.mu_complete
                else -> R.string.mu_review
            })
            updateConditionalFields()
        } finally { rendering = false }
    }
    private fun renderRows(final: Long, existing: Long, applied: Long, deductions: Long, refund: Long, debt: Long) {
        val b = binding
        row(b.rowFinalInvoice,R.string.mu_final,final); row(b.rowExistingDebt,R.string.mu_existing,existing)
        row(b.rowDepositApplied,R.string.mu_applied,applied); row(b.rowDeductions,R.string.mu_deductions,deductions)
        row(b.rowRefundDue,R.string.mu_refund,refund); row(b.rowDebtRemaining,R.string.mu_debt,debt)
    }
    private fun row(b: ItemSummaryRowBinding, label: Int, amount: Long) {
        b.tvSummaryRowLabel.setText(label); b.tvSummaryRowValue.text = money(amount)
    }
    private fun money(value: Long) = CurrencyFormatter.formatKhr(value)
    private fun showError(message: String) { binding.tvMoveOutMessage.text = message; binding.tvMoveOutMessage.isVisible = true }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("selectedMoveOutDate",viewModel.selectedDate)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { dateDialog?.dismiss(); dateDialog=null; _binding=null; super.onDestroyView() }
}
