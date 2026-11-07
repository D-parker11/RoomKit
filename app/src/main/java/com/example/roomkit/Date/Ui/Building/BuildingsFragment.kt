@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Building

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentBuildingsBinding
import com.roomkit.data.local.model.BuildingWithStats
import com.roomkit.ui.buildings.BuildingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class BuildingsFragment : Fragment() {

    private var _binding: FragmentBuildingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BuildingsViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private var currentPropertyId: Long? = null
    private var positionedAtTop = false

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

        viewModel.onSearchQueryChanged("")
        binding.propertyPage.requestFocus()

        setupListeners()
        observeProperty()
    }

    private fun setupListeners() {
        binding.btnSetupProperty.setOnClickListener {
            openBuildingSheet()
        }

        binding.btnEditProperty.setOnClickListener {
            currentPropertyId?.let { id ->
                openBuildingSheet(id)
            }
        }

        binding.btnViewRooms.setOnClickListener {
            openRooms(initialFilter = "ALL")
        }

        binding.btnViewVacantRooms.setOnClickListener {
            openRooms(initialFilter = "VACANT")
        }

        binding.btnAssignTenant.setOnClickListener {
            openAddTenant()
        }
    }

    private fun observeProperty() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.buildings.collect { properties ->
                    render(properties.firstOrNull())
                }
            }
        }
    }

    private fun render(property: BuildingWithStats?) {
        currentPropertyId = property?.buildingId

        binding.propertyContent.isVisible = property != null
        binding.emptyState.isVisible = property == null

        if (property == null) return

        positionAtTopOnce()

        val total = property.totalRooms.coerceAtLeast(0)
        val occupied = property.occupiedRooms.coerceAtLeast(0)
        val vacant = (total - occupied).coerceAtLeast(0)

        val percent = if (total == 0) {
            0
        } else {
            (occupied.toDouble() / total * 100)
                .roundToInt()
                .coerceIn(0, 100)
        }

        binding.tvPropertyName.text = property.name

        binding.tvPropertyAddress.text = property.address
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.rp_address_missing)

        binding.tvTotalRooms.text = total.toString()
        binding.tvOccupiedRooms.text = occupied.toString()
        binding.tvVacantRooms.text = vacant.toString()

        binding.tvOccupancyPercent.text = getString(
            R.string.rp_percent,
            percent
        )

        binding.tvOccupancyDetail.text = getString(
            R.string.rp_occupancy_detail,
            occupied,
            total
        )

        binding.occupancyProgress.setProgressCompat(
            percent,
            false
        )

        binding.occupancyProgress.contentDescription = getString(
            R.string.rp_occupancy_accessibility,
            percent,
            occupied,
            total
        )

        binding.tvAvailability.text = resources.getQuantityString(
            R.plurals.rp_available_rooms,
            vacant,
            vacant
        )

        // Creating a tenant profile does not require a vacant room.
        binding.btnAssignTenant.isEnabled = true
        binding.btnAssignTenant.alpha = 1f
//        binding.btnAssignTenant.text = "Add tenant"
        binding.tvAssignHint.text = "Create a new tenant profile"

        binding.btnViewVacantRooms.isEnabled = vacant > 0
        binding.btnViewVacantRooms.alpha =
            if (vacant > 0) 1f else 0.45f
    }

    private fun positionAtTopOnce() {
        if (positionedAtTop) return

        positionedAtTop = true
        binding.propertyPage.requestFocus()

        binding.scrollBuildings.post {
            _binding?.scrollBuildings?.scrollTo(0, 0)
        }
    }

    private fun openRooms(initialFilter: String = "ALL") {
        val propertyId = currentPropertyId ?: return
        val nav = findNavController()

        if (nav.currentDestination?.id != R.id.buildingsFragment) {
            return
        }

        val args = Bundle().apply {
            putLong("buildingId", propertyId)
            putString("initialFilter", initialFilter)
        }

        nav.navigate(
            R.id.action_buildingsFragment_to_roomsFragment,
            args
        )
    }

    private fun openAddTenant() {
        val nav = findNavController()

        if (nav.currentDestination?.id != R.id.buildingsFragment) {
            return
        }

        nav.navigate(R.id.addTenantFragment)
    }

    private fun openBuildingSheet(buildingId: Long = 0L) {
        val manager = childFragmentManager
        val tag = AddBuildingBottomSheetDialogFragment.TAG

        if (manager.isStateSaved) return
        if (manager.findFragmentByTag(tag) != null) return

        AddBuildingBottomSheetDialogFragment
            .newInstance(buildingId)
            .show(manager, tag)
    }

    override fun onDestroyView() {
        currentPropertyId = null
        positionedAtTop = false
        _binding = null
        super.onDestroyView()
    }
}