@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
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
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels { ViewModelFactory(requireContext()) }
    private var showAllAttention = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showAllAttention = savedInstanceState?.getBoolean("showAllAttention") ?: false
        childFragmentManager.setFragmentResultListener(DashboardMonthPickerDialog.RESULT, viewLifecycleOwner) { _, result ->
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
            if (nav.currentDestination?.id == R.id.dashboardFragment) nav.navigate(R.id.buildingsFragment)
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
        binding.tvGreeting.setText(when (hour) {
            in 5..11 -> R.string.rd_good_morning
            in 12..16 -> R.string.rd_good_afternoon
            else -> R.string.rd_good_evening
        })
        viewModel.refreshDate()
    }

    private fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)

    private fun renderUi(state: DashboardUiState) {
        binding.loadingDashboard.isVisible = state.isLoading
        binding.dashboardContent.isVisible = !state.isLoading
        if (state.isLoading) return
        val propertyName = state.buildings.firstOrNull { it.buildingId != null }?.name
        binding.tvPropertyName.text = propertyName.orEmpty()
        binding.tvPropertyName.isVisible = !propertyName.isNullOrBlank()
        val period = YearMonth.of(state.selectedYear, state.selectedMonth)
        binding.tvPeriodTitle.text = if (period == YearMonth.now()) getString(R.string.dm_this_month)
        else period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        binding.tvMonthYear.text = getString(R.string.dm_month_button, state.monthYearFormatted)
        binding.tvAttentionPeriod.text = getString(R.string.dm_attention_period, state.monthYearFormatted)
        binding.tvOutstandingAmount.text = money(state.outstandingKhr)
        binding.tvBilledAmount.text = money(state.billedKhr)
        binding.tvCollectedAmount.text = money(state.collectedKhr)
        val collectedPercent = if (state.billedKhr <= 0L) 0 else
            (state.collectedKhr.toDouble() / state.billedKhr * 100).roundToInt().coerceIn(0, 100)
        binding.collectionProgress.setProgressCompat(collectedPercent, false)
        binding.tvCollectionPercent.text = getString(R.string.rd_collected_percent, collectedPercent)
        binding.tvUnpaidCountLabel.text = getString(R.string.rd_unpaid_count, state.unpaidCount)
        binding.tvOverdueCountLabel.text = getString(R.string.rd_overdue_count, state.overdueCount)
        val overdueColor = if (state.overdueCount > 0) R.color.rd_red else R.color.rd_green
        binding.tvOverdueCountLabel.setTextColor(ContextCompat.getColor(requireContext(), overdueColor))
        binding.tvOverdueCountLabel.setBackgroundResource(
            if (state.overdueCount > 0) R.drawable.bg_rd_red else R.drawable.bg_rd_green
        )
        // Reference shows two chips; reveal a Partial chip only when it carries data.
        binding.tvPartialCountLabel.isVisible = state.partiallyPaidCount > 0
        binding.tvPartialCountLabel.text = getString(R.string.rd_partial_count, state.partiallyPaidCount)
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
            row.tvAttentionStatus.setTextColor(ContextCompat.getColor(requireContext(), if (overdue) R.color.rd_red else R.color.rd_amber))
            row.tvAttentionStatus.setBackgroundResource(if (overdue) R.drawable.bg_rd_red else R.drawable.ic_rd_amber)
            row.rowDivider.isVisible = index < items.lastIndex
            row.root.setOnClickListener {
                val nav = findNavController()
                if (nav.currentDestination?.id == R.id.dashboardFragment) {
                    nav.navigate(R.id.action_dashboardFragment_to_invoiceDetailFragment, bundleOf("invoiceId" to item.invoiceId))
                }
            }
            binding.attentionRows.addView(row.root)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("showAllAttention", showAllAttention)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
