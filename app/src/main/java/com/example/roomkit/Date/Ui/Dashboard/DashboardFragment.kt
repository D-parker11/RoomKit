@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Dashboard

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentDashboardBinding
import com.example.roomkit.databinding.ItemDashboardAttentionModernBinding
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels { ViewModelFactory(requireContext()) }
    private var showAllAttention = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showAllAttention = savedInstanceState?.getBoolean("showAllAttention") ?: false

        childFragmentManager.setFragmentResultListener(
            DashboardMonthPickerDialog.RESULT,
            viewLifecycleOwner
        ) { _, result ->
            showAllAttention = false
            viewModel.onMonthChanged(result.getInt("month"), result.getInt("year"))
        }

        binding.tvMonthYear.setOnClickListener {
            val manager = childFragmentManager
            if (!manager.isStateSaved && manager.findFragmentByTag(DashboardMonthPickerDialog.TAG) == null) {
                val state = viewModel.uiState.value
                if (!state.isLoading) {
                    DashboardMonthPickerDialog.newInstance(state.selectedMonth, state.selectedYear)
                        .show(manager, DashboardMonthPickerDialog.TAG)
                }
            }
        }

        // There is one property; no hidden stale building filter should affect the totals.
        viewModel.onBuildingFilterSelected(null)

        binding.btnViewProperty.setOnClickListener {
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.dashboardFragment) {
                nav.navigate(R.id.buildingsFragment)
            }
        }

        binding.btnViewAllAttention.setOnClickListener {
            showAllAttention = !showAllAttention
            renderAttention(viewModel.uiState.value)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderUi(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hour = LocalTime.now().hour
        binding.tvGreeting.setText(
            when (hour) {
                in 5..11 -> R.string.rd_good_morning
                in 12..16 -> R.string.rd_good_afternoon
                else -> R.string.rd_good_evening
            }
        )
        viewModel.refreshDate()
    }

    private fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)

    private fun styleStatusBadge(
        view: TextView,
        @ColorRes colorRes: Int,
        @DrawableRes backgroundRes: Int
    ) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        val density = resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (6 * density).roundToInt()

        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.textSize = 12f
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setTextColor(color)
        view.setBackgroundResource(backgroundRes)
        view.setPadding(padH, padV, padH, padV)
        view.compoundDrawablePadding = (6 * density).roundToInt()
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
            view,
            R.drawable.ic_rd_status_dot,
            0,
            0,
            0
        )
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color))
    }

    private fun renderUi(state: DashboardUiState) {
        binding.loadingDashboard.isVisible = state.isLoading
        binding.dashboardContent.isVisible = !state.isLoading
        if (state.isLoading) return

        val propertyName = state.buildings.firstOrNull { it.buildingId != null }?.name
        binding.tvPropertyName.text = propertyName.orEmpty()
        binding.tvPropertyName.isVisible = !propertyName.isNullOrBlank()

        val period = YearMonth.of(state.selectedYear, state.selectedMonth)
        binding.tvPeriodTitle.text = if (period == YearMonth.now()) {
            getString(R.string.dm_this_month)
        } else {
            period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
        binding.tvMonthYear.text = getString(R.string.dm_month_button, state.monthYearFormatted)
        binding.tvAttentionPeriod.text = getString(R.string.dm_attention_period, state.monthYearFormatted)

        binding.tvOutstandingAmount.text = money(state.outstandingKhr)
        binding.tvBilledAmount.text = money(state.billedKhr)
        binding.tvCollectedAmount.text = money(state.collectedKhr)

        val collectedPercent = if (state.billedKhr <= 0L) {
            0
        } else {
            (state.collectedKhr.toDouble() / state.billedKhr * 100).roundToInt().coerceIn(0, 100)
        }
        binding.collectionProgress.setProgressCompat(collectedPercent, false)
        binding.tvCollectionPercent.text = getString(R.string.rd_collected_percent, collectedPercent)

        // Period summary status badges
        binding.tvUnpaidCountLabel.text = getString(R.string.rd_unpaid_count, state.unpaidCount)
        styleStatusBadge(
            view = binding.tvUnpaidCountLabel,
            colorRes = if (state.unpaidCount > 0) R.color.rd_amber else R.color.rd_muted,
            backgroundRes = if (state.unpaidCount > 0) R.drawable.bg_rd_badge_amber else R.drawable.bg_rd_badge_neutral
        )

        binding.tvOverdueCountLabel.text = getString(R.string.rd_overdue_count, state.overdueCount)
        styleStatusBadge(
            view = binding.tvOverdueCountLabel,
            colorRes = if (state.overdueCount > 0) R.color.rd_red else R.color.rd_muted,
            backgroundRes = if (state.overdueCount > 0) R.drawable.bg_rd_badge_red else R.drawable.bg_rd_badge_neutral
        )

        binding.tvPartialCountLabel.isVisible = state.partiallyPaidCount > 0
        binding.tvPartialCountLabel.text = getString(R.string.rd_partial_count, state.partiallyPaidCount)
        styleStatusBadge(
            view = binding.tvPartialCountLabel,
            colorRes = R.color.rd_amber,
            backgroundRes = R.drawable.bg_rd_badge_amber
        )

        binding.tvTotalRoomsCount.text = state.totalRooms.toString()
        binding.tvOccupiedRoomsCount.text = state.occupiedRooms.toString()
        binding.tvVacantRoomsCount.text = state.vacantRooms.toString()
        binding.tvOccupancyPercent.text = getString(R.string.rd_occupancy_percent, state.occupancyPercent)
        binding.occupancyProgress.setProgressCompat(state.occupancyPercent, false)

        renderAttention(state)
    }

    private fun renderAttention(state: DashboardUiState) {
        val items = if (showAllAttention) state.attentionItems else state.attentionItems.take(2)
        binding.attentionRows.removeAllViews()
        binding.attentionCard.isVisible = items.isNotEmpty()
        binding.tvEmptyAttention.isVisible = items.isEmpty()
        binding.btnViewAllAttention.isVisible = state.attentionItems.size > 2
        binding.btnViewAllAttention.setText(if (showAllAttention) R.string.rd_view_less else R.string.rd_view_all)

        items.forEachIndexed { index, item ->
            val row = ItemDashboardAttentionModernBinding.inflate(layoutInflater, binding.attentionRows, false)
            row.tvAttentionTitle.text = getString(R.string.rd_room_number, item.roomNumber)
            row.tvAttentionSubtitle.text = item.subtitle
            row.tvAttentionStatus.text = item.statusLabel

            val overdue = item.type == DashboardAttentionType.OVERDUE
            styleStatusBadge(
                view = row.tvAttentionStatus,
                colorRes = if (overdue) R.color.rd_red else R.color.rd_amber,
                backgroundRes = if (overdue) R.drawable.bg_rd_badge_red else R.drawable.bg_rd_badge_amber
            )

            row.rowDivider.isVisible = index < items.lastIndex
            row.root.setOnClickListener {
                val nav = findNavController()
                if (nav.currentDestination?.id == R.id.dashboardFragment) {
                    nav.navigate(
                        R.id.action_dashboardFragment_to_invoiceDetailFragment,
                        bundleOf("invoiceId" to item.invoiceId)
                    )
                }
            }
            binding.attentionRows.addView(row.root)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showAllAttention", showAllAttention)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}