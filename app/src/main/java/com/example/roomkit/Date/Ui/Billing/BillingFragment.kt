@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentBillingBinding
import kotlinx.coroutines.launch
import java.util.Locale

class BillingFragment : Fragment() {
    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BillingViewModel by viewModels { ViewModelFactory(requireContext()) }
    private lateinit var invoiceAdapter: BillingInvoiceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        invoiceAdapter = BillingInvoiceAdapter { invoiceId ->
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.billingFragment) {
                nav.navigate(R.id.action_billingFragment_to_invoiceDetailFragment, bundleOf("invoiceId" to invoiceId))
            }
        }
        binding.rvInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
            isNestedScrollingEnabled = false
            itemAnimator = null
        }
        binding.billingPage.requestFocus()
        binding.btnPrevMonth.setOnClickListener { viewModel.onPreviousMonthClicked() }
        binding.btnNextMonth.setOnClickListener { viewModel.onNextMonthClicked() }
        binding.chipAll.setOnClickListener {
            viewModel.onFilterSelected(BillingFilter.ALL)
            binding.chipAll.isChecked = true
        }
        binding.chipUnpaid.setOnClickListener {
            viewModel.onFilterSelected(BillingFilter.UNPAID)
            binding.chipUnpaid.isChecked = true
        }
        binding.chipPaid.setOnClickListener {
            viewModel.onFilterSelected(BillingFilter.PAID)
            binding.chipPaid.isChecked = true
        }
        binding.chipMore.setOnClickListener { showMoreFilters() }
        binding.chipMore.closeIconContentDescription = getString(R.string.rb_more)
        binding.chipMore.setOnCloseIconClickListener { showMoreFilters() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.screenState.collect { renderUi(it) }
            }
        }
    }

    private fun showMoreFilters() {
        val state = viewModel.screenState.value
        val s = state.content
        binding.chipMore.isChecked = state.documentFilter != null ||
                s.selectedFilter == BillingFilter.PARTIAL || s.selectedFilter == BillingFilter.OVERDUE
        PopupMenu(requireContext(), binding.chipMore).apply {
            menu.add(0, 1, 0, getString(R.string.rb_filter_count, getString(R.string.rb_partial), s.partialCount))
            menu.add(0, 2, 1, getString(R.string.rb_filter_count, getString(R.string.rb_overdue), s.overdueCount))
            menu.add(0, 3, 2, getString(R.string.rb_filter_count, getString(R.string.rb_void), state.voidCount))
            menu.add(0, 4, 3, getString(R.string.rb_filter_count, getString(R.string.rb_draft), state.draftCount))
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> viewModel.onFilterSelected(BillingFilter.PARTIAL)
                    2 -> viewModel.onFilterSelected(BillingFilter.OVERDUE)
                    3 -> viewModel.onDocumentFilterSelected(BillingDocumentFilter.VOID)
                    4 -> viewModel.onDocumentFilterSelected(BillingDocumentFilter.DRAFT)
                }
                true
            }
            show()
        }
    }

    private fun renderUi(state: BillingReferenceState) {
        val s = state.content
        binding.progressBilling.isVisible = s.isLoading
        binding.billingContent.isVisible = !s.isLoading
        if (s.isLoading) return
        binding.tvSelectedMonth.text = s.monthYearFormatted
        binding.tvOutstandingAmount.text = String.format(Locale.US, "%,d KHR", state.outstandingKhr)
        binding.tvSummaryUnpaidCount.text = s.unpaidCount.toString()
        binding.tvSummaryPaidCount.text = s.paidCount.toString()
        binding.tvSummaryVoidCount.text = state.voidCount.toString()
        binding.tvSummaryNote.setText(if (state.draftCount > 0) R.string.rb_exclusions_with_drafts else R.string.rb_exclusions)
        binding.chipAll.text = getString(R.string.rb_filter_count, getString(R.string.rb_all), s.totalCount)
        binding.chipUnpaid.text = getString(R.string.rb_filter_count, getString(R.string.rb_unpaid), s.unpaidCount)
        binding.chipPaid.text = getString(R.string.rb_filter_count, getString(R.string.rb_paid), s.paidCount)
        val moreLabel = when (state.documentFilter) {
            BillingDocumentFilter.VOID -> getString(R.string.rb_void)
            BillingDocumentFilter.DRAFT -> getString(R.string.rb_draft)
            null -> when (s.selectedFilter) {
                BillingFilter.PARTIAL -> getString(R.string.rb_partial)
                BillingFilter.OVERDUE -> getString(R.string.rb_overdue)
                else -> getString(R.string.rb_more)
            }
        }
        binding.chipMore.text = moreLabel
        val extra = state.documentFilter != null || s.selectedFilter == BillingFilter.PARTIAL || s.selectedFilter == BillingFilter.OVERDUE
        binding.chipAll.isChecked = !extra && s.selectedFilter == BillingFilter.ALL
        binding.chipUnpaid.isChecked = !extra && s.selectedFilter == BillingFilter.UNPAID
        binding.chipPaid.isChecked = !extra && s.selectedFilter == BillingFilter.PAID
        binding.chipMore.isChecked = extra
        binding.tvRecordCount.text = resources.getQuantityString(R.plurals.rb_records, s.invoices.size, s.invoices.size)
        binding.layoutEmptyState.isVisible = s.invoices.isEmpty()
        binding.rvInvoices.isVisible = s.invoices.isNotEmpty()
        binding.tvEmptyTitle.text = getString(R.string.rb_empty_title, s.monthYearFormatted)
        invoiceAdapter.submitList(s.invoices)
    }

    override fun onDestroyView() {
        binding.rvInvoices.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
