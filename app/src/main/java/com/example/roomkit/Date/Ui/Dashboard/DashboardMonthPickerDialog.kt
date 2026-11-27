@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Dashboard

import com.example.roomkit.Date.Ui.Common.setRoomKitTextSize

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.annotation.StringRes
import com.example.roomkit.R
import com.example.roomkit.databinding.DialogDashboardMonthBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

open class DashboardMonthPickerDialog : BottomSheetDialogFragment() {

    private var _binding: DialogDashboardMonthBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val monthButtons = mutableListOf<MaterialButton>()

    private var selectedMonth = 1
    private var selectedYear = 2026
    private var resultSent = false

    private val displayLocale: Locale
        get() = ConfigurationCompat.getLocales(
            resources.configuration
        )[0] ?: Locale.getDefault()

    /** Override only caller-specific context; the layout and selection logic stay shared. */
    protected open val resultKey: String get() = RESULT
    @get:StringRes
    protected open val descriptionRes: Int? get() = null
    protected open fun currentPeriod(): YearMonth = YearMonth.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val now = currentPeriod()

        selectedMonth = (
                savedInstanceState?.getInt(KEY_MONTH)
                    ?: arguments?.getInt(KEY_MONTH, now.monthValue)
                    ?: now.monthValue
                ).coerceIn(1, 12)

        selectedYear = (
                savedInstanceState?.getInt(KEY_YEAR)
                    ?: arguments?.getInt(KEY_YEAR, now.year)
                    ?: now.year
                ).coerceIn(MIN_YEAR, MAX_YEAR)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDashboardMonthBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val description = descriptionRes
        binding.tvMonthHint.isVisible = description != null
        description?.let { binding.tvMonthHint.setText(it) }

        createMonthButtons()

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnPreviousYear.setOnClickListener {
            if (selectedYear > MIN_YEAR) {
                selectedYear--
                renderSelection()
            }
        }

        binding.btnNextYear.setOnClickListener {
            if (selectedYear < MAX_YEAR) {
                selectedYear++
                renderSelection()
            }
        }

        binding.btnThisMonth.setOnClickListener {
            val now = currentPeriod()

            selectedMonth = now.monthValue
            selectedYear = now.year.coerceIn(MIN_YEAR, MAX_YEAR)

            // Update the selection; Apply confirms it.
            renderSelection()
        }

        binding.btnApply.setOnClickListener {
            if (resultSent) return@setOnClickListener
            if (parentFragmentManager.isStateSaved) {
                return@setOnClickListener
            }

            resultSent = true

            parentFragmentManager.setFragmentResult(
                resultKey,
                bundleOf(
                    KEY_MONTH to selectedMonth,
                    KEY_YEAR to selectedYear
                )
            )

            dismiss()
        }

        renderSelection()
    }

    override fun onStart() {
        super.onStart()

        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val sheet = sheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // Round only the top corners.
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(dp(24).toFloat())
            .setTopRightCornerSize(dp(24).toFloat())
            .build()

        sheet.background = MaterialShapeDrawable(shape).apply {
            fillColor = ColorStateList.valueOf(
                color(R.color.roomkit_white)
            )
        }

        sheetDialog.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            this.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun createMonthButtons() {
        binding.monthGrid.removeAllViews()
        monthButtons.clear()

        for (rowIndex in 0 until 4) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) {
                        topMargin = dp(8)
                    }
                }
            }

            for (columnIndex in 0 until 3) {
                val monthNumber = rowIndex * 3 + columnIndex + 1

                val button = MaterialButton(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        if (columnIndex < 2) {
                            marginEnd = dp(8)
                        }
                    }

                    minWidth = 0
                    minimumWidth = 0
                    minHeight = dp(52)
                    minimumHeight = dp(52)

                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(14)
                    strokeWidth = dp(1)

                    setPadding(dp(4), dp(12), dp(4), dp(12))

                    isAllCaps = false
                    letterSpacing = 0f
                    setRoomKitTextSize(R.dimen.roomkit_text_14)
                    typeface = Typeface.create(
                        "sans-serif-medium",
                        Typeface.NORMAL
                    )

                    // Avoid different elevation on press.
                    stateListAnimator = null
                    elevation = 0f

                    isCheckable = true

                    text = Month.of(monthNumber).getDisplayName(
                        TextStyle.SHORT_STANDALONE,
                        displayLocale
                    )

                    setOnClickListener {
                        selectedMonth = monthNumber
                        renderSelection()
                    }
                }

                monthButtons += button
                row.addView(button)
            }

            binding.monthGrid.addView(row)
        }
    }

    private fun renderSelection() {
        val now = currentPeriod()
        val selectedPeriod = YearMonth.of(
            selectedYear,
            selectedMonth
        )

        binding.tvYear.text = selectedYear.toString()

        binding.tvSelectedPeriod.text = selectedPeriod.format(
            DateTimeFormatter.ofPattern(
                "MMMM yyyy",
                displayLocale
            )
        )

        binding.btnPreviousYear.isEnabled = selectedYear > MIN_YEAR
        binding.btnNextYear.isEnabled = selectedYear < MAX_YEAR

        binding.btnPreviousYear.alpha =
            if (binding.btnPreviousYear.isEnabled) 1f else 0.35f

        binding.btnNextYear.alpha =
            if (binding.btnNextYear.isEnabled) 1f else 0.35f

        // Speak the target year instead of the arrow symbol.
        binding.btnPreviousYear.contentDescription =
            (selectedYear - 1).toString()

        binding.btnNextYear.contentDescription =
            (selectedYear + 1).toString()

        monthButtons.forEachIndexed { index, button ->
            val monthNumber = index + 1
            val selected = monthNumber == selectedMonth
            val current = selectedYear == now.year &&
                    monthNumber == now.monthValue

            button.isChecked = selected

            button.backgroundTintList = ColorStateList.valueOf(
                color(
                    if (selected) R.color.roomkit_primary
                    else R.color.roomkit_white
                )
            )

            button.setTextColor(
                color(
                    if (selected) R.color.roomkit_white
                    else R.color.roomkit_text_primary
                )
            )

            button.strokeColor = ColorStateList.valueOf(
                color(
                    if (selected || current) R.color.roomkit_primary
                    else R.color.roomkit_border
                )
            )

            button.rippleColor = ColorStateList.valueOf(
                color(R.color.roomkit_text_muted)
            )

            val fullMonth = Month.of(monthNumber).getDisplayName(
                TextStyle.FULL_STANDALONE,
                displayLocale
            )

            button.contentDescription = if (current) {
                "$fullMonth $selectedYear, " +
                        getString(R.string.dm_this_month)
            } else {
                "$fullMonth $selectedYear"
            }
        }
    }

    private fun color(resourceId: Int): Int {
        return ContextCompat.getColor(requireContext(), resourceId)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_MONTH, selectedMonth)
        outState.putInt(KEY_YEAR, selectedYear)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        monthButtons.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "dashboard-month-picker"
        const val RESULT = "dashboard-month-result"

        private const val KEY_MONTH = "month"
        private const val KEY_YEAR = "year"

        private const val MIN_YEAR = 1900
        private const val MAX_YEAR = 9999

        fun newInstance(
            month: Int,
            year: Int
        ): DashboardMonthPickerDialog {
            return DashboardMonthPickerDialog().apply {
                arguments = bundleOf(
                    KEY_MONTH to month,
                    KEY_YEAR to year
                )
            }
        }
    }
}