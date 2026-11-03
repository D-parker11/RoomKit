@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentPaymentsBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaymentsViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private lateinit var activityAdapter: PaymentsActivityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupMonthSelector()
        setupSearch()
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
        activityAdapter = PaymentsActivityAdapter(
            onInvoiceClick = { invoiceId ->
                findNavController().navigate(
                    R.id.action_paymentsFragment_to_invoiceDetailFragment,
                    bundleOf("invoiceId" to invoiceId)
                )
            },
            onRecordPaymentClick = { invoiceId ->
                // Use the action ID declared in your nav_graph.xml
                findNavController().navigate(
                    R.id.action_paymentsFragment_to_recordPaymentBottomSheet,
                    bundleOf("invoiceId" to invoiceId)
                )
            }
        )
        binding.rvPaymentActivity.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = activityAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupMonthSelector() {
        binding.btnPrevMonth.setOnClickListener { viewModel.onPreviousMonthClicked() }
        binding.btnNextMonth.setOnClickListener { viewModel.onNextMonthClicked() }
    }

    private fun setupSearch() {
        binding.etSearchPayments.addTextChangedListener { editable ->
            viewModel.onSearchQueryChanged(editable?.toString().orEmpty())
        }
    }

    private fun setupTabs() {
        binding.tabLayoutPaymentFilter.apply {
            removeAllTabs()
            addTab(newTab().setText("All"))
            addTab(newTab().setText("Paid"))
            addTab(newTab().setText("Partial"))
            addTab(newTab().setText("Overdue"))

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val filter = when (tab?.position) {
                        1 -> PaymentFilter.PAID
                        2 -> PaymentFilter.PARTIAL
                        3 -> PaymentFilter.OVERDUE
                        else -> PaymentFilter.ALL
                    }
                    viewModel.onFilterSelected(filter)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun renderUi(s: PaymentsUiState) {
        binding.tvSelectedMonth.text = s.monthYearFormatted

        // Top 3 Metrics
        binding.tvSummaryCollected.text = CurrencyFormatter.formatKhr(s.collectedKhr)
        binding.tvSummaryOutstanding.text = CurrencyFormatter.formatKhr(s.outstandingKhr)
        binding.tvSummaryOverdue.text = CurrencyFormatter.formatKhr(s.overdueKhr)

        // List vs Empty
        if (s.items.isEmpty()) {
            binding.rvPaymentActivity.visibility = View.GONE
            binding.layoutEmptyPayments.visibility = View.VISIBLE
            if (s.searchQuery.isNotEmpty()) {
                binding.tvEmptyTitle.text = "No matching payments or invoices"
                binding.tvEmptySubtitle.text = "Try searching with another tenant name or room number."
            } else {
                binding.tvEmptyTitle.text = "No payment activity for ${s.monthYearFormatted}"
                binding.tvEmptySubtitle.text = "Recorded payments and due invoices will appear here."
            }
        } else {
            binding.rvPaymentActivity.visibility = View.VISIBLE
            binding.layoutEmptyPayments.visibility = View.GONE
            activityAdapter.submitList(s.items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}