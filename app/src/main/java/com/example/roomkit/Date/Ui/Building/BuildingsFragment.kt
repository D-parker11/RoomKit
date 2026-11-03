package com.roomkit.ui.buildings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Ui.Building.AddBuildingBottomSheetDialogFragment
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentBuildingsBinding
import kotlinx.coroutines.launch

class BuildingsFragment : Fragment() {

    private var _binding: FragmentBuildingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BuildingsViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private lateinit var buildingsAdapter: BuildingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBuildingsBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        buildingsAdapter = BuildingsAdapter(
            onViewRoomsClicked = { building ->
                val args = Bundle().apply {
                    putLong("buildingId", building.buildingId)
                }

                findNavController().navigate(
                    R.id.action_buildingsFragment_to_roomsFragment,
                    args
                )
            },
            onEditClicked = { building ->
                openBuildingSheet(building.buildingId)
            }
        )

        binding.rvBuildings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = buildingsAdapter
        }
    }

    private fun setupListeners() {
        binding.btnAddBuilding.setOnClickListener {
            openBuildingSheet()
        }

        binding.etSearchBuildings.visibility = View.GONE
        binding.etSearchBuildings.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(
                text?.toString().orEmpty()
            )
        }
    }

    private fun openBuildingSheet(buildingId: Long = 0L) {
        val tag = AddBuildingBottomSheetDialogFragment.TAG

        if (childFragmentManager.isStateSaved) return
        if (childFragmentManager.findFragmentByTag(tag) != null) return

        AddBuildingBottomSheetDialogFragment
            .newInstance(buildingId)
            .show(childFragmentManager, tag)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.buildings.collect { buildings ->
                        buildingsAdapter.submitList(buildings)
                        binding.btnAddBuilding.visibility =
                            if (buildings.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.summary.collect { summary ->
                        binding.tvTotalBuildingsVal.text =
                            summary.totalBuildings.toString()

                        binding.tvTotalRoomsVal.text =
                            summary.totalRooms.toString()

                        binding.tvTotalOccupiedVal.text =
                            summary.totalOccupied.toString()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.rvBuildings.adapter = null
        _binding = null
        super.onDestroyView()
    }
}