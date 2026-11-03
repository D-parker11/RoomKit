package com.example.roomkit.Date.Ui.Building

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.databinding.BottomSheetAddBuildingBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.repository.BuildingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddBuildingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBuildingBinding? = null
    private val binding get() = _binding!!

    private val buildingId: Long
        get() = arguments?.getLong(ARG_BUILDING_ID) ?: 0L

    private val isEditing: Boolean
        get() = buildingId > 0L

    private var loadedBuilding: BuildingEntity? = null
    private var isSaving = false

    private val districts = listOf(
        "Chamkarmon",
        "Sen Sok",
        "Daun Penh",
        "Toul Kork",
        "BKK (Boeung Keng Kang)",
        "Mean Chey",
        "Chroy Changvar",
        "Porsenchey"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddBuildingBinding.inflate(
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

        setupDistrictDropdown()

        binding.tvBuildingTitle.text =
            if (isEditing) "Edit Building" else "Add Building"

        binding.btnCreateBuilding.text =
            if (isEditing) "Save Changes" else "Create Building"

        binding.btnCloseAddBuilding.setOnClickListener {
            if (!isSaving) dismiss()
        }

        binding.btnHeaderSave.setOnClickListener {
            validateAndSave()
        }

        binding.btnCreateBuilding.setOnClickListener {
            validateAndSave()
        }

        if (isEditing) {
            loadBuilding(savedInstanceState == null)
        }
    }

    private fun setupDistrictDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            districts
        )

        binding.actvDistrict.setAdapter(adapter)
        binding.actvDistrict.setText(districts.first(), false)
    }

    private fun loadBuilding(populateFields: Boolean) {
        val db = AppDatabase.getInstance(requireContext())
        setSaveEnabled(false)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val building = db.buildingDao()
                    .observeBuildingById(buildingId)
                    .first()
                    ?: throw IllegalStateException("Building not found.")

                loadedBuilding = building

                if (populateFields) {
                    binding.etBuildingName.setText(building.name)

                    // The existing app stores:
                    // "street, district, city" in one address column.
                    val addressParts = building.address
                        .orEmpty()
                        .split(",")
                        .map { it.trim() }

                    if (addressParts.size >= 3) {
                        binding.etStreetAddress.setText(
                            addressParts.dropLast(2).joinToString(", ")
                        )
                        binding.actvDistrict.setText(
                            addressParts[addressParts.lastIndex - 1],
                            false
                        )
                        binding.etCity.setText(addressParts.last())
                    } else {
                        binding.etStreetAddress.setText(building.address)
                    }

                    val floors = Regex(
                        """Floors:\s*(\d+)""",
                        RegexOption.IGNORE_CASE
                    ).find(building.description.orEmpty())
                        ?.groupValues
                        ?.getOrNull(1)

                    binding.etFloors.setText(floors.orEmpty())

                    // Capacity comes from the real database column.
                    binding.etTotalRooms.setText(
                        building.totalRooms
                            .takeIf { it > 0 }
                            ?.toString()
                            .orEmpty()
                    )
                }

                setSaveEnabled(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Unable to load building.")
                dismiss()
            }
        }
    }

    private fun validateAndSave() {
        if (isSaving) return
        if (isEditing && loadedBuilding == null) return

        val name = binding.etBuildingName.text
            ?.toString()?.trim().orEmpty()

        val street = binding.etStreetAddress.text
            ?.toString()?.trim().orEmpty()

        val district = binding.actvDistrict.text
            ?.toString()?.trim().orEmpty()

        val city = binding.etCity.text
            ?.toString()?.trim().orEmpty()
            .ifBlank { "Phnom Penh" }

        val floorsInput = binding.etFloors.text
            ?.toString()?.trim().orEmpty()

        val floors = if (floorsInput.isBlank()) {
            1
        } else {
            floorsInput.toIntOrNull()
        }

        val totalRooms = binding.etTotalRooms.text
            ?.toString()?.trim()?.toIntOrNull()

        binding.tilBuildingName.error = null
        binding.tilStreetAddress.error = null
        binding.tilDistrict.error = null
        binding.tilFloors.error = null
        binding.tilTotalRooms.error = null

        when {
            name.isBlank() -> {
                binding.tilBuildingName.error =
                    "Building name is required."
                binding.etBuildingName.requestFocus()
                return
            }

            street.isBlank() -> {
                binding.tilStreetAddress.error =
                    "Street address is required."
                binding.etStreetAddress.requestFocus()
                return
            }

            district.isBlank() -> {
                binding.tilDistrict.error = "Select a district."
                binding.actvDistrict.requestFocus()
                return
            }

            floors == null || floors <= 0 -> {
                binding.tilFloors.error =
                    "Enter a floor count greater than 0."
                binding.etFloors.requestFocus()
                return
            }

            totalRooms == null || totalRooms <= 0 -> {
                binding.tilTotalRooms.error =
                    "Enter a room capacity greater than 0."
                binding.etTotalRooms.requestFocus()
                return
            }
        }

        val fullAddress = "$street, $district, $city"
        val db = AppDatabase.getInstance(requireContext())
        val repository = BuildingRepository(db.buildingDao())

        isSaving = true
        isCancelable = false
        setSaveEnabled(false)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                db.withTransaction {
                    if (isEditing) {
                        val current = db.buildingDao()
                            .observeBuildingById(buildingId)
                            .first()
                            ?: throw IllegalStateException(
                                "Building not found."
                            )

                        val roomCount = db.buildingDao()
                            .getRoomCountForBuilding(buildingId)

                        require(totalRooms >= roomCount) {
                            "This building already has $roomCount rooms. " +
                                    "Capacity must be at least $roomCount."
                        }

                        // Preserve other description text.
                        val description = updateDescription(
                            current.description,
                            floors,
                            totalRooms
                        )

                        repository.updateBuilding(
                            current.copy(
                                name = name,
                                address = fullAddress,
                                description = description,
                                totalRooms = totalRooms
                            )
                        )
                    } else {
                        repository.insertBuilding(
                            BuildingEntity(
                                name = name,
                                address = fullAddress,
                                description =
                                    "Floors: $floors | Total Rooms: $totalRooms",
                                totalRooms = totalRooms
                            )
                        )
                    }
                }

                showMessage(
                    if (isEditing) {
                        "Building updated successfully."
                    } else {
                        "$name added successfully."
                    }
                )

                dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Unable to save building.")
            } finally {
                isSaving = false
                isCancelable = true

                if (_binding != null) {
                    setSaveEnabled(!isEditing || loadedBuilding != null)
                }
            }
        }
    }

    private fun updateDescription(
        existing: String?,
        floors: Int,
        totalRooms: Int
    ): String {
        val floorsPattern = Regex(
            """Floors:\s*\d+""",
            RegexOption.IGNORE_CASE
        )
        val roomsPattern = Regex(
            """Total Rooms:\s*\d+""",
            RegexOption.IGNORE_CASE
        )

        var text = existing.orEmpty()

        text = if (floorsPattern.containsMatchIn(text)) {
            floorsPattern.replace(text, "Floors: $floors")
        } else {
            listOf(text, "Floors: $floors")
                .filter { it.isNotBlank() }
                .joinToString(" | ")
        }

        return if (roomsPattern.containsMatchIn(text)) {
            roomsPattern.replace(text, "Total Rooms: $totalRooms")
        } else {
            "$text | Total Rooms: $totalRooms"
        }
    }

    private fun setSaveEnabled(enabled: Boolean) {
        binding.btnHeaderSave.isEnabled = enabled
        binding.btnCreateBuilding.isEnabled = enabled
    }

    private fun showMessage(message: String) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AddBuildingBottomSheet"
        private const val ARG_BUILDING_ID = "buildingId"

        fun newInstance(
            buildingId: Long = 0L
        ): AddBuildingBottomSheetDialogFragment {
            return AddBuildingBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_BUILDING_ID, buildingId)
                }
            }
        }
    }
}