@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private lateinit var buildingFilterAdapter: BuildingFilterAdapter
    private lateinit var attentionAdapter: DashboardAttentionAdapter

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

        updateDynamicGreeting()
        setupBuildingFilterRecycler()
        setupAttentionRecycler()
//        setupQuickActions()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh greeting whenever user returns to dashboard
        updateDynamicGreeting()
    }

    private fun updateDynamicGreeting() {
        val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalTime.now().hour
        } else {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }

        val greetingText = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }

        binding.tvGreeting.text = greetingText
    }

    private fun setupBuildingFilterRecycler() {
        buildingFilterAdapter = BuildingFilterAdapter { selectedBuildingId ->
            viewModel.onBuildingFilterSelected(selectedBuildingId)
        }

        binding.rvBuildingFilters.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = buildingFilterAdapter

            // Prevents NestedScrollView from intercepting horizontal swipes
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Ask the parent NestedScrollView not to intercept touch events
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Restore parent interception when touch ends
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }
            })
        }
    }

    private fun setupAttentionRecycler() {
        attentionAdapter = DashboardAttentionAdapter { invoiceId ->
            findNavController().navigate(
                R.id.action_dashboardFragment_to_invoiceDetailFragment,
                bundleOf("invoiceId" to invoiceId)
            )
        }
        binding.rvRequiresAttention.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = attentionAdapter
            isNestedScrollingEnabled = false
        }
    }

//    private fun setupQuickActions() {
//        binding.btnAddMeterReading.setOnClickListener {
//            val buildingId = viewModel.uiState.value.selectedBuildingId ?: -1L
//            findNavController().navigate(
//                R.id.action_dashboardFragment_to_meterReadingFragment,
//                bundleOf("preselectedBuildingId" to buildingId)
//            )
//        }
//
//        binding.btnRecordPayment.setOnClickListener {
//            findNavController().navigate(R.id.action_dashboardFragment_to_recordPaymentBottomSheet)
//        }
//    }

    private fun renderUi(s: DashboardUiState) {
        // 1. Submit Building Filter list to horizontal RecyclerView
        buildingFilterAdapter.submitList(s.buildings)
        binding.rvBuildingFilters.visibility = View.GONE

        // 2. Room Statistics
        binding.tvTotalRoomsCount.text = s.totalRooms.toString()
        binding.tvOccupiedRoomsCount.text = s.occupiedRooms.toString()
        binding.tvVacantRoomsCount.text = s.vacantRooms.toString()
        binding.tvOccupancyPercent.text = "${s.occupancyPercent}%"

        binding.tvTotalRoomsSub.text = if (s.selectedBuildingId == null) {
            "in your property"
        } else {
            "in your property"
        }

        // 3. Financial Summary
        binding.tvMonthYear.text = s.monthYearFormatted
        binding.tvBilledAmount.text = CurrencyFormatter.formatKhr(s.billedKhr)
        binding.tvCollectedAmount.text = CurrencyFormatter.formatKhr(s.collectedKhr)
        binding.tvOutstandingAmount.text = CurrencyFormatter.formatKhr(s.outstandingKhr)

        // 4. Status Dot Counters
        binding.tvPaidCountLabel.text = "${s.paidCount} Paid"
        binding.tvPartialCountLabel.text = "${s.partiallyPaidCount} Partial"
        binding.tvUnpaidCountLabel.text = "${s.unpaidCount} Unpaid"
        binding.tvOverdueCountLabel.text = "${s.overdueCount} Overdue"

        // 5. Requires Attention Section
        if (s.attentionItems.isEmpty()) {
            binding.rvRequiresAttention.visibility = View.GONE
            binding.tvEmptyAttention.visibility = View.VISIBLE
        } else {
            binding.rvRequiresAttention.visibility = View.VISIBLE
            binding.tvEmptyAttention.visibility = View.GONE
            attentionAdapter.submitList(s.attentionItems)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}