@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Meter

import android.app.Dialog
import android.os.Bundle
import android.widget.NumberPicker
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.roomkit.R
import com.example.roomkit.databinding.DialogDashboardMonthBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.YearMonth
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

class MeterMonthPickerDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val now = YearMonth.now(java.time.ZoneId.of("Asia/Phnom_Penh"))
        val selectedMonth = savedInstanceState?.getInt("month") ?: arguments?.getInt("month") ?: now.monthValue
        val selectedYear = savedInstanceState?.getInt("year") ?: arguments?.getInt("year") ?: now.year
        val b = DialogDashboardMonthBinding.inflate(layoutInflater)
        b.monthPicker.apply {
            minValue = 1
            maxValue = 12
            displayedValues = (1..12).map { Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) }.toTypedArray()
            wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            value = selectedMonth.coerceIn(1, 12)
        }
        b.yearPicker.apply {
            minValue = 1900
            maxValue = 9999
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            value = selectedYear.coerceIn(1900, 9999)
        }
        monthPicker = b.monthPicker
        yearPicker = b.yearPicker
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dm_choose_month)
            .setMessage(R.string.qa_meter_month_warning)
            .setView(b.root)
            .setNegativeButton(R.string.dm_cancel, null)
            .setNeutralButton(R.string.dm_this_month) { _, _ -> send(now.monthValue, now.year) }
            .setPositiveButton(R.string.dm_apply) { _, _ -> send(b.monthPicker.value, b.yearPicker.value) }
            .create()
    }
    private var monthPicker: NumberPicker? = null
    private var yearPicker: NumberPicker? = null
    override fun onSaveInstanceState(outState: Bundle) {
        monthPicker?.let { outState.putInt("month", it.value) }
        yearPicker?.let { outState.putInt("year", it.value) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        monthPicker = null
        yearPicker = null
        super.onDestroyView()
    }
    private fun send(month: Int, year: Int) {
        parentFragmentManager.setFragmentResult(RESULT, bundleOf("month" to month, "year" to year))
    }
    companion object {
        const val TAG = "meter-month-picker"
        const val RESULT = "meter-month-result"
        fun newInstance(month: Int, year: Int) = MeterMonthPickerDialog().apply {
            arguments = bundleOf("month" to month, "year" to year)
        }
    }
}
