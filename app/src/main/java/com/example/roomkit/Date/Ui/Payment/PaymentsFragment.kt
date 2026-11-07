@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentPaymentsBinding
import kotlinx.coroutines.launch
import java.util.Locale

class PaymentsFragment : Fragment() {
    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaymentsViewModel by viewModels { ViewModelFactory(requireContext()) }
    private lateinit var collectionAdapter: PaymentsActivityAdapter
    private lateinit var historyAdapter: PaymentsActivityAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectionAdapter = newAdapter()
        historyAdapter = newAdapter()
        binding.rvPaymentActivity.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = collectionAdapter
            isNestedScrollingEnabled = false
        }
        binding.rvPaymentHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
            isNestedScrollingEnabled = false
        }

        binding.btnPrevMonth.setOnClickListener { viewModel.onPreviousMonthClicked() }
        binding.btnNextMonth.setOnClickListener { viewModel.onNextMonthClicked() }
        binding.etSearchPayments.setText(viewModel.currentSearchQuery)
        binding.etSearchPayments.doAfterTextChanged { viewModel.onSearchQueryChanged(it?.toString().orEmpty()) }
        listOf(binding.chipAll to PaymentsScreenFilter.ALL,
            binding.chipUnpaid to PaymentsScreenFilter.UNPAID,
            binding.chipPaid to PaymentsScreenFilter.PAID).forEach { (chip, filter) ->
            chip.setOnClickListener { chip.isChecked = true; viewModel.onScreenFilterSelected(filter) }
        }
        binding.chipMore.closeIconContentDescription = "More filters"
        binding.chipMore.setOnClickListener { showMore() }
        binding.chipMore.setOnCloseIconClickListener { showMore() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.screenState.collect { render(it) }
            }
        }
    }
    private fun newAdapter() = PaymentsActivityAdapter(
        onInvoiceClick = { navigate(R.id.action_paymentsFragment_to_invoiceDetailFragment, it) },
        onRecordPaymentClick = { navigate(R.id.action_paymentsFragment_to_recordPaymentBottomSheet, it) }
    )
    private fun navigate(action: Int, invoiceId: Long) {
        val nav = findNavController()
        if (nav.currentDestination?.getAction(action) != null) nav.navigate(action, bundleOf("invoiceId" to invoiceId))
    }
    private fun showMore() {
        val s = viewModel.screenState.value
        updateChips(s)
        val choices = listOf(
            PaymentsScreenFilter.PARTIAL to "Partial (${s.partialCount})",
            PaymentsScreenFilter.OVERDUE to "Overdue (${s.overdueCount})",
            PaymentsScreenFilter.HISTORY to "Payment history (${s.historyCount})"
        )
        PopupMenu(requireContext(), binding.chipMore).apply {
            choices.forEachIndexed { index, pair -> menu.add(0, index, index, pair.second) }
            setOnMenuItemClickListener { viewModel.onScreenFilterSelected(choices[it.itemId].first); true }
            show()
        }
    }
    private fun updateChips(s: PaymentsReferenceState) = with(binding) {
        chipAll.text = "All ${s.invoiceCount}"
        chipUnpaid.text = "Unpaid ${s.unpaidCount}"
        chipPaid.text = "Paid ${s.paidCount}"
        chipAll.isChecked = s.filter == PaymentsScreenFilter.ALL
        chipUnpaid.isChecked = s.filter == PaymentsScreenFilter.UNPAID
        chipPaid.isChecked = s.filter == PaymentsScreenFilter.PAID
        chipMore.isChecked = s.filter in setOf(PaymentsScreenFilter.PARTIAL, PaymentsScreenFilter.OVERDUE, PaymentsScreenFilter.HISTORY)
        chipMore.text = when (s.filter) {
            PaymentsScreenFilter.PARTIAL -> "Partial ${s.partialCount}"
            PaymentsScreenFilter.OVERDUE -> "Overdue ${s.overdueCount}"
            PaymentsScreenFilter.HISTORY -> "History"
            else -> "More"
        }
    }
    private fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)
    private fun render(state: PaymentsReferenceState) = with(binding) {
        val s = state.content
        progressPayments.isVisible = s.isLoading
        paymentsContent.isVisible = !s.isLoading
        tvSelectedMonth.text = s.monthYearFormatted
        tvSummaryOutstanding.text = money(s.outstandingKhr)
        tvSummaryCollected.text = money(s.collectedKhr)
        tvSummaryOverdue.text = money(s.overdueKhr)
        tvSummaryOverdue.setTextColor(ContextCompat.getColor(requireContext(), if (s.overdueKhr > 0) R.color.rpay_red else R.color.rpay_muted))
        updateChips(state)
        val due = s.items.filterIsInstance<PaymentActivityItem.OutstandingInvoice>()
        val history = s.items.filterIsInstance<PaymentActivityItem.PaymentRecord>()
        collectionAdapter.submitList(due)
        historyAdapter.submitList(history)
        collectionHeader.isVisible = due.isNotEmpty()
        rvPaymentActivity.isVisible = due.isNotEmpty()
        historyHeader.isVisible = history.isNotEmpty()
        rvPaymentHistory.isVisible = history.isNotEmpty()
        tvCollectionCount.text = resources.getQuantityString(R.plurals.rpay_invoices, due.size, due.size)
        tvHistoryCount.text = resources.getQuantityString(R.plurals.rpay_receipts, history.size, history.size)
        layoutEmptyPayments.isVisible = !s.isLoading && s.items.isEmpty()
        tvEmptyTitle.text = if (s.searchQuery.isNotBlank()) "No matching records" else "No records in this view"
        tvEmptySubtitle.text = if (s.searchQuery.isNotBlank()) "Try another tenant, room, property or invoice number." else "Try another filter or month. Fully paid invoices have no balance to collect."
    }
    override fun onDestroyView() {
        binding.rvPaymentActivity.adapter = null
        binding.rvPaymentHistory.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
