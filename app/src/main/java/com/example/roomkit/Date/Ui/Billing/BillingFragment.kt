@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class BillingFragment : Fragment() {

    private var _binding: FragmentBillingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BillingViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private lateinit var invoiceAdapter: BillingInvoiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupMonthSelector()
        setupTabs()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        invoiceAdapter = BillingInvoiceAdapter { invoiceId ->
            findNavController().navigate(
                R.id.action_billingFragment_to_invoiceDetailFragment,
                bundleOf("invoiceId" to invoiceId)
            )
        }
        binding.rvInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupMonthSelector() {
        binding.btnPrevMonth.setOnClickListener {
            viewModel.onPreviousMonthClicked()
        }
        binding.btnNextMonth.setOnClickListener {
            viewModel.onNextMonthClicked()
        }
    }

    private fun setupTabs() {
        binding.tabLayoutFilter.apply {
            removeAllTabs()
            addTab(newTab().setText("All"))
            addTab(newTab().setText("Paid"))
            addTab(newTab().setText("Unpaid"))
            addTab(newTab().setText("Overdue"))
            addTab(newTab().setText("Partial"))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val filter = when (tab?.position) {
                        1 -> BillingFilter.PAID
                        2 -> BillingFilter.UNPAID
                        3 -> BillingFilter.OVERDUE
                        4 -> BillingFilter.PARTIAL
                        else -> BillingFilter.ALL
                    }
                    viewModel.onFilterSelected(filter)
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun renderUi(s: BillingUiState) {
        // Month Selector
        binding.tvSelectedMonth.text = s.monthYearFormatted

        // Top 4 Summary Cards
        binding.tvSummaryTotalCount.text = s.totalCount.toString()
        binding.tvSummaryPaidCount.text = s.paidCount.toString()
        binding.tvSummaryUnpaidCount.text = s.unpaidCount.toString()
        binding.tvSummaryOverdueCount.text = s.overdueCount.toString()

        // List & Empty State
        if (s.invoices.isEmpty()) {
            binding.rvInvoices.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = "No invoices for ${s.monthYearFormatted}"
        } else {
            binding.rvInvoices.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            invoiceAdapter.submitList(s.invoices)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}