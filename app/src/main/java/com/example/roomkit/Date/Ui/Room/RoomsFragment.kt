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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ConcatAdapter
import com.example.roomkit.dataheader.ItemRoomsHeaderBinding
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.dataheader.FragmentRoomsBinding
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
    private var _header: ItemRoomsHeaderBinding? = null
    private val header get() = requireNotNull(_header)
    private var pendingRoomId: Long? = null
    private var initialFilterApplied = false

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
        _header = ItemRoomsHeaderBinding.inflate(layoutInflater, binding.root, false)
        initialFilterApplied = savedInstanceState?.getBoolean("initialFilterApplied") ?: initialFilterApplied
        pendingRoomId = savedInstanceState?.getLong("pendingRoomId")?.takeIf { it > 0 } ?: pendingRoomId
        parentFragmentManager.setFragmentResultListener("room-added", viewLifecycleOwner) { _, result ->
            pendingRoomId = result.getLong("roomId")
            viewModel.onSearchQueryChanged("")
            header.etSearchRooms.setText("")
            viewModel.selectTab(RoomTabFilter.ALL)
            header.tabLayoutRoomFilter.getTabAt(0)?.select()
            scrollToSavedRoom()
        }

        header.btnBack.setOnClickListener { findNavController().popBackStack() }
        header.etSearchRooms.setText(viewModel.searchQuery.value)

        header.etSearchRooms.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }
        header.btnAddRoom.setOnClickListener { navigateToAddRoom() }
        header.btnEmptyAddRoom.setOnClickListener { navigateToAddRoom() }
        if (!initialFilterApplied) {
            initialFilterApplied = true
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

        binding.root.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 1
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(header.root) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }, roomsAdapter)
            setHasFixedSize(true)
            // Cards retain the same screen margins while the header owns its own padding.
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View,
                    parent: RecyclerView, state: RecyclerView.State) {
                    if (parent.getChildAdapterPosition(view) > 0) {
                        val padding = resources.getDimensionPixelSize(R.dimen.roomkit_screen_padding_horizontal)
                        outRect.left = padding
                        outRect.right = padding
                    }
                }
            })
        }
    }
    private fun setupTabs() {
        header.tabLayoutRoomFilter.apply {
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
                        header.tvBuildingName.text = building?.name ?: "Building Rooms"
                        header.tvBuildingAddress.text = building?.address?.ifBlank { "No address recorded" } ?: "No address recorded"
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
                        header.tvSummaryTotalRooms.text = summary.all.toString()
                        header.tvSummaryOccupied.text = summary.occupied.toString()
                        header.tvSummaryVacant.text = summary.vacant.toString()
                        header.tvSummaryOccupancyPercent.text = "${summary.occupancyPct}%"

                        val tabLayout = header.tabLayoutRoomFilter
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
                        roomsAdapter.submitList(rooms) {
                            scrollToSavedRoom()
                        }
                        if (rooms.isEmpty()) {
                            binding.root.visibility = View.VISIBLE
                            header.layoutEmptyRooms.visibility = View.VISIBLE

                            when (tab) {
                                RoomTabFilter.OCCUPIED -> {
                                    header.tvEmptyTitle.text = "No occupied rooms"
                                    header.tvEmptySubtitle.text = "Assigned tenants will appear here."
                                    header.btnEmptyAddRoom.visibility = View.GONE
                                }
                                RoomTabFilter.VACANT -> {
                                    header.tvEmptyTitle.text = "No vacant rooms"
                                    header.tvEmptySubtitle.text = "All rooms are currently occupied."
                                    header.btnEmptyAddRoom.visibility = View.GONE
                                }
                                RoomTabFilter.ALL -> {
                                    header.tvEmptyTitle.text = "No rooms yet"
                                    header.tvEmptySubtitle.text = "Add your first room to this building."
                                    header.btnEmptyAddRoom.visibility = View.VISIBLE
                                }
                            }
                        } else {
                            binding.root.visibility = View.VISIBLE
                            header.layoutEmptyRooms.visibility = View.GONE

                        }
                    }
                }
            }
        }
    }

    private fun scrollToSavedRoom() {
        if (!::roomsAdapter.isInitialized || _binding == null) return
        val id = pendingRoomId ?: return
        val index = roomsAdapter.currentList.indexOfFirst { it.roomId == id }
        if (index >= 0) {
            binding.root.scrollToPosition(index + 1)
            pendingRoomId = null
        }
    }

    private data class SummaryData(
        val all: Int,
        val occupied: Int,
        val vacant: Int,
        val occupancyPct: Int
    )

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("initialFilterApplied", initialFilterApplied)
        pendingRoomId?.let { outState.putLong("pendingRoomId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding?.root?.adapter = null
        _header = null
        super.onDestroyView()
        _binding = null
    }
}