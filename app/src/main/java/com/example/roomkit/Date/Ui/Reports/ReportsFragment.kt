package com.example.roomkit.Date.Reports

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentReportsBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ReportsFragment : Fragment(R.layout.fragment_reports) {
    private val vm: ReportsViewModel by viewModels()
    private var detailsExpanded = false
    private var binding: FragmentReportsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentReportsBinding.bind(view)
        binding = b
        detailsExpanded = savedInstanceState?.getBoolean("reportDetails") ?: false
        fun showDetails() {
            b.rptDetails.isVisible = detailsExpanded
            b.rptDetailsToggle.text = if (detailsExpanded) "Hide detailed breakdown" else "View detailed breakdown"
        }
        showDetails()
        b.rptDetailsToggle.setOnClickListener { detailsExpanded = !detailsExpanded; showDetails() }
        b.rptBack.setOnClickListener { findNavController().popBackStack() }
        b.rptPrevious.setOnClickListener { vm.select(-1) }
        b.rptNext.setOnClickListener { vm.select(1) }
        b.rptRefresh.setOnClickListener { vm.refresh() }
        b.rptRetry.setOnClickListener { vm.refresh() }
        b.rptShare.setOnClickListener {
            val state = vm.state.value
            val totals = state.totals ?: return@setOnClickListener
            if (state.loading || state.error != null) return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "RoomKit report · " + ReportFormatter.month(state.month))
                putExtra(Intent.EXTRA_TEXT, ReportFormatter.summary(state.month, totals, state.updatedAt) +
                        "\n\nBILLED UTILITY USAGE\nElectricity: " + units(totals.electricityUsage, "kWh") +
                        "\nMetered water: " + units(totals.waterUsage, "m³") +
                        "\n\nPAYMENT TREND\n" + state.dashboard?.trend.orEmpty().joinToString("\n") {
                    ReportFormatter.month(it.month) + ": " + ReportFormatter.money(it.amount)
                })
            }
            try {
                startActivity(Intent.createChooser(intent, getString(R.string.rpt_share)))
            } catch (_: ActivityNotFoundException) {
                Snackbar.make(b.root, R.string.rpt_share_error, Snackbar.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    b.rptPeriod.text = ReportFormatter.month(state.month)
                    b.rptNext.isEnabled = state.month < vm.currentMonth()
                    b.rptPrevious.isEnabled = state.month > java.time.YearMonth.of(1, 6)
                    b.rptRefresh.isEnabled = !state.loading
                    b.rptLoading.isVisible = state.loading
                    b.rptErrorPanel.isVisible = state.error != null
                    b.rptError.text = state.error
                    b.rptContent.isVisible = !state.loading && state.totals != null
                    state.totals?.let { render(b, it, state.updatedAt) }
                    state.dashboard?.let { dashboard ->
                        b.rptTrend.submit(dashboard.trend)
                        b.rptTrendRange.text = ReportFormatter.month(dashboard.trend.first().month) +
                                " – " + ReportFormatter.month(state.month)
                        b.rptSummaryMonth.text = ReportFormatter.month(state.month)
                        b.rptElectricityUsage.text = units(dashboard.totals.electricityUsage, "kWh")
                        b.rptWaterUsage.text = units(dashboard.totals.waterUsage, "m³")
                        b.rptElectricityChange.text = comparison(dashboard.totals.electricityUsage, dashboard.previous.electricityUsage, "kWh")
                        b.rptWaterChange.text = comparison(dashboard.totals.waterUsage, dashboard.previous.waterUsage, "m³")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun render(b: FragmentReportsBinding, t: ReportTotals, updated: Long) {
        val amounts = listOf(
            b.rptReceived to t.received, b.rptBilled to t.billed,
            b.rptRemaining to t.remaining, b.rptRent to t.rent,
            b.rptElectricity to t.electricity, b.rptWater to t.water,
            b.rptRubbish to t.rubbish, b.rptOtherCharges to t.otherCharges,
            b.rptCash to t.cash, b.rptBank to t.bank, b.rptKhqr to t.khqr,
            b.rptOtherPayments to t.otherPayments, b.rptDepositApplied to t.depositApplied,
            b.rptAllOutstanding to t.allOutstanding, b.rptAllOverdue to t.allOverdue,
            b.rptPendingRefund to t.pendingRefund
        )
        amounts.forEach { (view, value) -> view.text = ReportFormatter.money(value) }
        b.rptDiscount.text = if (t.discount > 0) "−" + ReportFormatter.money(t.discount) else ReportFormatter.money(0)
        val percent = ReportFormatter.settledPercent(t)
        b.rptCollection.text = if (t.billed == 0L) "No billed amount to settle" else
            "$percent% · Includes payments and deposit allocations"
        b.rptProgress.progress = percent
        b.rptCounts.text = "${t.invoiceCount} invoices · ${t.paidCount} paid · ${t.partialCount} partially paid"
        b.rptOverdue.text = "${t.overdueCount} overdue · ${ReportFormatter.money(t.overdueAmount)}\nPartial and overdue counts can overlap."
        b.rptSettled.text = ReportFormatter.money((t.billed - t.remaining).coerceAtLeast(0))
        b.rptRing.submit(t.occupied, t.rooms)
        b.rptOccupied.text = t.occupied.toString()
        b.rptVacant.text = (t.rooms - t.occupied).coerceAtLeast(0).toString()
        b.rptRooms.text = t.rooms.toString()
        b.rptOccupancy.text = ReportFormatter.occupancy(t)
        b.rptRefundCount.text = "${t.pendingRefundCount} settlements awaiting refund"
        b.rptUpdated.text = "Updated ${ReportFormatter.time(updated)} · Cambodia time"
        b.rptEmpty.isVisible = t.invoiceCount == 0L && t.received == 0L && t.depositApplied == 0L
    }

    private fun units(value: Long, unit: String): String =
        java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(value) + " " + unit

    private fun comparison(current: Long, previous: Long, unit: String): String {
        val delta = current - previous
        val change = when {
            delta > 0 -> "+" + units(delta, unit)
            delta < 0 -> "−" + units(-delta, unit)
            else -> "No change"
        }
        return "$change vs previous billing month"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("reportDetails", detailsExpanded)
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
