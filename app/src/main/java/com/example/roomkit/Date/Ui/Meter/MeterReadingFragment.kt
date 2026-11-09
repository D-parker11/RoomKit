package com.example.roomkit.Date.Ui.Meter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Model.MeterReadingUiState
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentMeterReadingBinding
import com.example.roomkit.databinding.ItemMeterHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** UI-only update for the version-9 meter-history ViewModel. */
class MeterReadingFragment : Fragment() {
    private var _binding: FragmentMeterReadingBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val roomId: Long by lazy { arguments?.getLong("roomId") ?: 0L }
    private val viewModel: MeterReadingViewModel by viewModels { ViewModelFactory(requireContext(), roomId) }
    private val numbers = DecimalFormat("#,###")
    private val dates = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.getDefault())
        .withZone(ZoneId.of("Asia/Phnom_Penh"))
    private var rendering = false
    private var replacementExpanded = false
    private var allHistory = false
    private var replacementPending = false
    private var renderedHistory: List<MeterReadingEntity>? = null
    private var renderedAllHistory = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMeterReadingBinding.inflate(inflater, container, false)
        renderedHistory = null
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        replacementExpanded = savedInstanceState?.getBoolean("replacementExpanded") ?: replacementExpanded
        allHistory = savedInstanceState?.getBoolean("allHistory") ?: allHistory
        replacementPending = savedInstanceState?.getBoolean("replacementPending") ?: replacementPending
//        binding.tvBillingMonthSubtitle.contentDescription="Choose billing month"
//        binding.tvBillingMonthSubtitle.setOnClickListener {
//            val state=viewModel.uiState.value
//            if (!state.isLoading && !state.isSaving) {
//                val field=EditText(requireContext()).apply {
//                    hint="YYYY-MM";setText("%04d-%02d".format(state.billingYear,state.billingMonth))
//                }
//                MaterialAlertDialogBuilder(requireContext()).setTitle("Choose billing month")
//                    .setMessage("Changing month discards unsaved input. Saved readings stay read-only. Meter replacement is available for the current month only.")
//                    .setView(field).setNegativeButton("Cancel",null).setPositiveButton("Load month") { _,_->
//                        replacementExpanded=false;viewModel.selectPeriod(field.text.toString())
//                    }.show()
//            }
//        }
        binding.btnBackMeterReading.setOnClickListener { findNavController().popBackStack() }
        binding.etCurrentElectricity.doAfterTextChanged { if (!rendering) viewModel.onElectricityChanged(it?.toString().orEmpty()) }
        binding.etCurrentWater.doAfterTextChanged { if (!rendering) viewModel.onWaterChanged(it?.toString().orEmpty()) }
        binding.btnSaveMeterReading.setOnClickListener { viewModel.saveReadings() }
        binding.btnReloadMeters.setOnClickListener {
            val state = viewModel.uiState.value
            val entries = listOf(state.currentElectricityInput, state.currentWaterInput,
                state.replacementClosing, state.replacementOpening, state.replacementReason)
            if (entries.any { it.isNotBlank() }) {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.mr_discard_title)
                    .setMessage(R.string.mr_discard_body).setNegativeButton(R.string.mr_cancel, null)
                    .setPositiveButton(R.string.mr_reload) { _, _ -> viewModel.reload() }.show()
            } else viewModel.reload()
        }
        binding.btnToggleReplacement.setOnClickListener {
            replacementExpanded = !replacementExpanded
            bindState(viewModel.uiState.value)
        }
        binding.btnHistoryMore.setOnClickListener {
            allHistory = !allHistory
            renderHistory(viewModel.uiState.value.history)
        }
        binding.rgReplacementType.setOnCheckedChangeListener { _, _ -> replacementChanged() }
        binding.etReplacementClosing.doAfterTextChanged { replacementChanged() }
        binding.etReplacementOpening.doAfterTextChanged { replacementChanged() }
        binding.etReplacementReason.doAfterTextChanged { replacementChanged() }
        binding.btnReplaceMeter.setOnClickListener {
            replacementChanged()
            val state = viewModel.uiState.value
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.mr_confirm_title)
                .setMessage(getString(R.string.mr_confirm_message, utility(state.replacementType),
                    state.replacementClosing, state.replacementOpening, state.replacementReason))
                .setNegativeButton(R.string.mr_cancel, null)
                .setPositiveButton(R.string.mr_confirm) { _, _ ->
                    replacementPending = true
                    viewModel.replaceMeter()
                }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { bindState(it) }
            }
        }
    }

    private fun replacementChanged() {
        if (rendering) return
        viewModel.replacementInput(if (binding.rbReplaceWater.isChecked) "WATER" else "ELECTRICITY",
            binding.etReplacementClosing.text?.toString().orEmpty(),
            binding.etReplacementOpening.text?.toString().orEmpty(), binding.etReplacementReason.text?.toString().orEmpty())
    }

    private fun bindState(state: MeterReadingUiState) {
        if (_binding == null) return
        if (replacementPending && !state.isSaving && !state.isLoading) {
            if (state.globalError == null && state.replacementClosing.isBlank() && state.replacementOpening.isBlank()) {
                replacementExpanded = false
                replacementPending = false
            } else if (state.globalError != null) {
                replacementPending = false
            }
        }
        rendering = true
        try {
            val busy = state.isLoading || state.isSaving
            val editable = state.hasActiveTenancy && !busy && !state.isLockedByInvoice && !replacementExpanded
            val today=java.time.YearMonth.now(ZoneId.of("Asia/Phnom_Penh"))
            val currentMonth=state.billingYear==today.year && state.billingMonth==today.monthValue
            val replacementEditable = state.hasActiveTenancy && !busy && currentMonth
//            binding.tvBillingMonthSubtitle.text = "${state.billingMonthText} · Change"
            binding.tvRoomTenantBanner.text = if (state.roomNumber.isBlank()) getString(R.string.mr_room_only)
            else getString(R.string.mr_room_tenant, state.roomNumber, state.tenantName)
            binding.progressMeters.isVisible = busy
            binding.tvGlobalError.isVisible = state.globalError != null
            binding.tvGlobalError.text = state.globalError
            binding.btnReloadMeters.isVisible = !busy && state.globalError != null && !state.isLockedByInvoice
            binding.btnReloadMeters.isEnabled = !busy
            binding.btnToggleReplacement.isEnabled = !busy && currentMonth
            binding.btnToggleReplacement.setText(if (replacementExpanded) R.string.mr_replace_close else R.string.mr_replace)
            binding.layoutReplacementBody.isVisible = replacementExpanded
            binding.tvReplacementError.isVisible = replacementExpanded && state.globalError != null && !state.isLockedByInvoice
            binding.tvReplacementError.text = state.globalError
            binding.layoutBottomAction.isVisible = !replacementExpanded
            binding.btnSaveMeterReading.isEnabled = state.canSave
            binding.btnSaveMeterReading.setText(if (state.isSaving) R.string.mr_saving else R.string.mr_save)
            binding.tvElecPreviousValue.text = numbers.format(state.previousElectricity)
            setInput(binding.etCurrentElectricity, state.currentElectricityInput, editable)
            binding.tvElecUsageLive.isVisible = state.electricityUsage != null
            binding.tvElecUsageLive.text = usage(state.electricityUsage, state.electricityCarriedUsage, "kWh")
            binding.tvElecError.isVisible = state.electricityError != null
            binding.tvElecError.text = state.electricityError
            binding.layoutWaterMeterBody.isVisible = !state.isWaterFixed
            binding.tvWaterFixedNotice.isVisible = state.isWaterFixed
            binding.tvWaterFixedNotice.text = getString(R.string.mr_fixed_fee, numbers.format(state.fixedWaterFee))
            binding.tvWaterPreviousValue.text = numbers.format(state.previousWater)
            setInput(binding.etCurrentWater, state.currentWaterInput, editable && !state.isWaterFixed)
            binding.tvWaterUsageLive.isVisible = state.waterUsage != null
            binding.tvWaterUsageLive.text = usage(state.waterUsage, state.waterCarriedUsage, "m³")
            binding.tvWaterError.isVisible = state.waterError != null
            binding.tvWaterError.text = state.waterError
            binding.tvRubbishFeeAmount.text = getString(R.string.mr_money, numbers.format(state.rubbishFee))
            binding.rbReplaceWater.isVisible = !state.isWaterFixed
            binding.rbReplaceElectricity.isEnabled = replacementEditable
            binding.rbReplaceWater.isEnabled = replacementEditable
            binding.rgReplacementType.check(if (state.replacementType == "WATER" && !state.isWaterFixed) R.id.rbReplaceWater else R.id.rbReplaceElectricity)
            setInput(binding.etReplacementClosing, state.replacementClosing, replacementEditable)
            setInput(binding.etReplacementOpening, state.replacementOpening, replacementEditable)
            setInput(binding.etReplacementReason, state.replacementReason, replacementEditable)
            binding.btnReplaceMeter.isEnabled = replacementEditable
            renderHistory(state.history)
        } finally { rendering = false }
        if (state.isSaveSuccessful) {
            viewModel.consumeSuccess()
            Toast.makeText(requireContext(), R.string.mr_saved, Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun setInput(field: EditText, value: String, enabled: Boolean) {
        field.isEnabled = enabled
        if (field.text.toString() != value) {
            field.setText(value)
            field.setSelection(value.length)
        }
    }
    private fun usage(value: Long?, carried: Long, unit: String): String {
        if (value == null) return ""
        return getString(R.string.mr_usage, numbers.format(value), unit) +
                if (carried > 0) "\n" + getString(R.string.mr_carried, numbers.format(carried), unit) else ""
    }
    private fun utility(type: String) = getString(if (type == "WATER") R.string.mr_water else R.string.mr_electricity)
    private fun event(type: String) = getString(when (type) {
        "INITIAL" -> R.string.mr_initial
        "FINAL" -> R.string.mr_final
        "METERREPLACEMENT" -> R.string.mr_replaced
        else -> R.string.mr_monthly_record
    })
    private fun renderHistory(rows: List<MeterReadingEntity>) {
        if (renderedHistory == rows && renderedAllHistory == allHistory) return
        renderedHistory = rows.toList()
        renderedAllHistory = allHistory
        binding.tvMeterHistory.isVisible = rows.isEmpty()
        binding.btnHistoryMore.isVisible = rows.size > 3
        binding.btnHistoryMore.text = if (allHistory) getString(R.string.mr_show_less) else getString(R.string.mr_view_all, rows.size)
        binding.layoutHistoryRows.removeAllViews()
        for (row in if (allHistory) rows else rows.take(3)) {
            val item = ItemMeterHistoryBinding.inflate(layoutInflater, binding.layoutHistoryRows, false)
            val unit = if (row.meterType == "WATER") "m³" else "kWh"
            val title = getString(R.string.mr_history_title, utility(row.meterType), event(row.readingType))
            val values = getString(if (row.readingType == "METERREPLACEMENT") R.string.mr_replacement_values else R.string.mr_reading_values,
                numbers.format(row.previousReading), numbers.format(row.currentReading))
            item.tvHistoryTitle.text = title
            item.tvHistoryDate.text = dates.format(Instant.ofEpochMilli(row.readingDate))
            item.tvHistoryValues.text = values
            item.tvHistoryUsage.isVisible = row.readingType != "METERREPLACEMENT"
            item.tvHistoryUsage.text = if (row.readingType == "INITIAL") getString(R.string.mr_initial_value, numbers.format(row.currentReading), unit)
            else getString(R.string.mr_history_usage, numbers.format(row.usage), unit)
            item.tvHistoryNote.isVisible = row.carriedUsage > 0
            item.tvHistoryNote.text = getString(R.string.mr_carried, numbers.format(row.carriedUsage), unit)
            item.root.contentDescription = "$title, ${item.tvHistoryDate.text}, $values. ${getString(R.string.mr_expand_details)}"
            item.root.setOnClickListener {
                val message = getString(R.string.mr_history_detail, title, item.tvHistoryDate.text.toString(),
                    values, row.tenancyId, row.meterSequence) + (row.reason?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: "")
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.mr_details).setMessage(message)
                    .setPositiveButton(R.string.mr_close, null).show()
            }
            binding.layoutHistoryRows.addView(item.root)
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("replacementExpanded", replacementExpanded)
        outState.putBoolean("allHistory", allHistory)
        outState.putBoolean("replacementPending", replacementPending)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        renderedHistory = null
        _binding = null
        super.onDestroyView()
    }
}
