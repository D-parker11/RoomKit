@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Meter

import androidx.core.os.bundleOf
import com.example.roomkit.Date.Ui.Dashboard.DashboardMonthPickerDialog
import com.example.roomkit.R
import java.time.YearMonth
import java.time.ZoneId

/** Reuse the dashboard month grid with meter-specific messaging and result routing. */
class MeterMonthPickerDialog : DashboardMonthPickerDialog() {
    override val resultKey: String get() = RESULT
    override val descriptionRes: Int get() = R.string.qa_meter_month_warning
    override fun currentPeriod(): YearMonth = YearMonth.now(ZoneId.of("Asia/Phnom_Penh"))

    companion object {
        const val TAG = "meter-month-picker"
        const val RESULT = "meter-month-result"

        fun newInstance(month: Int, year: Int) = MeterMonthPickerDialog().apply {
            arguments = bundleOf("month" to month, "year" to year)
        }
    }
}
