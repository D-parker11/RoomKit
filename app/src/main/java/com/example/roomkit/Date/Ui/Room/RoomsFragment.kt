@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Room

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
import com.example.roomkit.databinding.FragmentRoomsBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.core.widget.doAfterTextChanged

class RoomsFragment : Fragment() {

    private var _binding: FragmentRoomsBinding? = null
    private val binding: FragmentRoomsBinding get() = _binding!!

    private val buildingId: Long by lazy { arguments?.getLong("buildingId") ?: -1L }

    private val viewModel: RoomsViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = buildingId)
    }

    private lateinit var roomsAdapter: RoomsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.etSearchRooms.setText(viewModel.searchQuery.value)

        binding.etSearchRooms.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }
        binding.btnAddRoom.setOnClickListener { navigateToAddRoom() }
        binding.btnEmptyAddRoom.setOnClickListener { navigateToAddRoom() }
        if (savedInstanceState == null) {
            val initialFilter = arguments?.getString("initialFilter")

            viewModel.selectTab(
                if (initialFilter == "VACANT") {
                    RoomTabFilter.VACANT
                } else {
                    RoomTabFilter.ALL
                }
            )
        }

        setupRecyclerView()
        setupTabs()
        observeViewModel()
    }

    private fun navigateToAddRoom() {
        findNavController().navigate(
            R.id.action_roomsFragment_to_addRoomFragment,
            bundleOf("buildingId" to buildingId)
        )
    }

    private fun setupRecyclerView() {
        roomsAdapter = RoomsAdapter(
            onRoomClick = { roomId ->
                findNavController().navigate(
                    R.id.action_roomsFragment_to_roomDetailFragment,
                    bundleOf("roomId" to roomId)
                )
            },
            onAssignTenantClick = { roomId ->
                findNavController().navigate(
                    R.id.action_roomsFragment_to_moveInFragment,
                    bundleOf(
                        "presetRoomId" to roomId,
                        "presetBuildingId" to buildingId
                    )
                )
            }
        )

        binding.rvRooms.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = roomsAdapter
            setHasFixedSize(true)
        }
    }
    private fun setupTabs() {
        binding.tabLayoutRoomFilter.apply {
            clearOnTabSelectedListeners()
            removeAllTabs()

            // Do not automatically select All when adding it.
            addTab(newTab().setText("All (0)"), false)
            addTab(newTab().setText("Occupied (0)"), false)
            addTab(newTab().setText("Vacant (0)"), false)

            val selectedPosition = when (viewModel.selectedTab.value) {
                RoomTabFilter.ALL -> 0
                RoomTabFilter.OCCUPIED -> 1
                RoomTabFilter.VACANT -> 2
            }

            getTabAt(selectedPosition)?.select()

            addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        viewModel.selectTab(
                            when (tab?.position) {
                                1 -> RoomTabFilter.OCCUPIED
                                2 -> RoomTabFilter.VACANT
                                else -> RoomTabFilter.ALL
                            }
                        )
                    }

                    override fun onTabUnselected(tab: TabLayout.Tab?) {}

                    override fun onTabReselected(tab: TabLayout.Tab?) {}
                }
            )
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Building Header Info
                launch {
                    viewModel.building.collect { building ->
                        binding.tvBuildingName.text = building?.name ?: "Building Rooms"
                        binding.tvBuildingAddress.text = building?.address?.ifBlank { "No address recorded" } ?: "No address recorded"
                    }
                }

                // 2. Summary Counts & Occupancy Percentage
                launch {
                    combine(
                        viewModel.allCount,
                        viewModel.occupiedCount,
                        viewModel.vacantCount,
                        viewModel.occupancyPercentage
                    ) { all, occupied, vacant, occupancyPct ->
                        SummaryData(all, occupied, vacant, occupancyPct)
                    }.collect { summary ->
                        binding.tvSummaryTotalRooms.text = summary.all.toString()
                        binding.tvSummaryOccupied.text = summary.occupied.toString()
                        binding.tvSummaryVacant.text = summary.vacant.toString()
                        binding.tvSummaryOccupancyPercent.text = "${summary.occupancyPct}%"

                        val tabLayout = binding.tabLayoutRoomFilter
                        tabLayout.getTabAt(0)?.text = "All (${summary.all})"
                        tabLayout.getTabAt(1)?.text = "Occupied (${summary.occupied})"
                        tabLayout.getTabAt(2)?.text = "Vacant (${summary.vacant})"
                    }
                }

                // 3. Displayed Rooms List & Empty States
                launch {
                    combine(viewModel.displayedRooms, viewModel.selectedTab) { rooms, tab ->
                        rooms to tab
                    }.collect { (rooms, tab) ->
                        if (rooms.isEmpty()) {
                            binding.rvRooms.visibility = View.GONE
                            binding.layoutEmptyRooms.visibility = View.VISIBLE

                            when (tab) {
                                RoomTabFilter.OCCUPIED -> {
                                    binding.tvEmptyTitle.text = "No occupied rooms"
                                    binding.tvEmptySubtitle.text = "Assigned tenants will appear here."
                                    binding.btnEmptyAddRoom.visibility = View.GONE
                                }
                                RoomTabFilter.VACANT -> {
                                    binding.tvEmptyTitle.text = "No vacant rooms"
                                    binding.tvEmptySubtitle.text = "All rooms are currently occupied."
                                    binding.btnEmptyAddRoom.visibility = View.GONE
                                }
                                RoomTabFilter.ALL -> {
                                    binding.tvEmptyTitle.text = "No rooms yet"
                                    binding.tvEmptySubtitle.text = "Add your first room to this building."
                                    binding.btnEmptyAddRoom.visibility = View.VISIBLE
                                }
                            }
                        } else {
                            binding.rvRooms.visibility = View.VISIBLE
                            binding.layoutEmptyRooms.visibility = View.GONE
                            roomsAdapter.submitList(rooms)
                        }
                    }
                }
            }
        }
    }

    private data class SummaryData(
        val all: Int,
        val occupied: Int,
        val vacant: Int,
        val occupancyPct: Int
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}