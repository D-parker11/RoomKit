@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Building

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.R
import com.example.roomkit.databinding.BottomSheetAddBuildingBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.repository.BuildingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddBuildingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddBuildingBinding? = null
    private val binding get() = requireNotNull(_binding)

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

    // Required: inflate the form and initialize ViewBinding.
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

        setupHeader()
        setupDistrictDropdown()
        setupListeners()
        setupInsets(view)

        // Phone storage is not implemented in the existing save logic.
        binding.tilContactPhone.visibility = View.GONE

        if (isEditing) {
            loadBuilding(populateFields = savedInstanceState == null)
        } else {
            setSaveEnabled(true)
        }
    }

    override fun onStart() {
        super.onStart()

        val sheetDialog = dialog as? BottomSheetDialog ?: return

        sheetDialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        val sheet = sheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        sheet.post {
            val currentBinding = _binding ?: return@post
            val container = sheet.parent as? View ?: return@post
            val topGap = (24 * resources.displayMetrics.density).toInt()
            val availableHeight = container.height

            if (availableHeight <= topGap) return@post

            sheet.layoutParams = sheet.layoutParams.apply {
                height = availableHeight - topGap
            }

            currentBinding.root.layoutParams =
                currentBinding.root.layoutParams.apply {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

            sheet.background = ColorDrawable(Color.TRANSPARENT)

            sheetDialog.behavior.apply {
                isFitToContents = true
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }

            sheet.requestLayout()
            ViewCompat.requestApplyInsets(currentBinding.root)
        }
    }

    private fun setupHeader() {
        binding.tvBuildingTitle.setText(
            if (isEditing) {
                R.string.rk_property_edit
            } else {
                R.string.rk_property_setup
            }
        )

        updateSaveButtonText()
    }

    private fun setupDistrictDropdown() {
        binding.actvDistrict.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                districts
            )
        )

        // Android restores the previous selection after view creation.
        binding.actvDistrict.setText(districts.first(), false)
    }

    private fun setupListeners() {
        binding.btnCloseAddBuilding.setOnClickListener {
            if (!isSaving) dismiss()
        }

        binding.btnCreateBuilding.setOnClickListener {
            validateAndSave()
        }
    }

    private fun setupInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val navigation = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            )
            val keyboard = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            target.setPadding(
                initialLeft + navigation.left,
                initialTop,
                initialRight + navigation.right,
                initialBottom + maxOf(
                    navigation.bottom,
                    keyboard.bottom
                )
            )

            insets
        }
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
                    populateBuildingFields(building)
                }

                setSaveEnabled(true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showMessage(
                    exception.message ?: "Unable to load building."
                )
                dismiss()
            }
        }
    }

    private fun populateBuildingFields(building: BuildingEntity) {
        binding.etBuildingName.setText(building.name)

        // Preserve the existing "street, district, city" storage format.
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

        val floors = FLOORS_PATTERN
            .find(building.description.orEmpty())
            ?.groupValues
            ?.getOrNull(1)

        binding.etFloors.setText(floors.orEmpty())

        binding.etTotalRooms.setText(
            building.totalRooms
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty()
        )
    }

    private fun clearErrors() {
        binding.tilBuildingName.error = null
        binding.tilStreetAddress.error = null
        binding.tilDistrict.error = null
        binding.tilFloors.error = null
        binding.tilTotalRooms.error = null
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
            .ifBlank {
                getString(R.string.rk_property_default_city)
            }

        val floorsInput = binding.etFloors.text
            ?.toString()?.trim().orEmpty()

        val floors = if (floorsInput.isBlank()) {
            1
        } else {
            floorsInput.toIntOrNull()
        }

        val totalRooms = binding.etTotalRooms.text
            ?.toString()?.trim()?.toIntOrNull()

        clearErrors()

        when {
            name.isBlank() -> {
                binding.tilBuildingName.error =
                    "Property name is required."
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

        saveBuilding(
            name = name,
            address = "$street, $district, $city",
            floors = floors,
            totalRooms = totalRooms
        )
    }

    private fun saveBuilding(
        name: String,
        address: String,
        floors: Int,
        totalRooms: Int
    ) {
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
                            "This property already has $roomCount rooms. " +
                                    "Capacity must be at least $roomCount."
                        }

                        repository.updateBuilding(
                            current.copy(
                                name = name,
                                address = address,
                                description = updateDescription(
                                    existing = current.description,
                                    floors = floors,
                                    totalRooms = totalRooms
                                ),
                                totalRooms = totalRooms
                            )
                        )
                    } else {
                        repository.insertBuilding(
                            BuildingEntity(
                                name = name,
                                address = address,
                                description =
                                    "Floors: $floors | Total Rooms: $totalRooms",
                                totalRooms = totalRooms
                            )
                        )
                    }
                }

                showMessage(
                    if (isEditing) {
                        "Property updated successfully."
                    } else {
                        "$name added successfully."
                    }
                )

                dismiss()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showMessage(
                    exception.message ?: "Unable to save property."
                )
            } finally {
                isSaving = false
                isCancelable = true

                if (_binding != null) {
                    setSaveEnabled(
                        !isEditing || loadedBuilding != null
                    )
                }
            }
        }
    }

    private fun updateDescription(
        existing: String?,
        floors: Int,
        totalRooms: Int
    ): String {
        var description = existing.orEmpty()

        description = if (
            FLOORS_PATTERN.containsMatchIn(description)
        ) {
            FLOORS_PATTERN.replace(
                description,
                "Floors: $floors"
            )
        } else {
            listOf(description, "Floors: $floors")
                .filter { it.isNotBlank() }
                .joinToString(" | ")
        }

        return if (
            ROOMS_PATTERN.containsMatchIn(description)
        ) {
            ROOMS_PATTERN.replace(
                description,
                "Total Rooms: $totalRooms"
            )
        } else {
            "$description | Total Rooms: $totalRooms"
        }
    }

    private fun setSaveEnabled(enabled: Boolean) {
        binding.btnCreateBuilding.isEnabled = enabled
        binding.btnCloseAddBuilding.isEnabled = !isSaving
        updateSaveButtonText()
    }

    private fun updateSaveButtonText() {
        binding.btnCreateBuilding.setText(
            when {
                isSaving -> R.string.rk_property_saving
                isEditing -> R.string.rk_property_save
                else -> R.string.rk_property_create
            }
        )
    }

    private fun showMessage(message: String) {
        val currentContext = context ?: return

        Toast.makeText(
            currentContext,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        _binding?.root?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it, null)
        }

        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AddBuildingBottomSheet"

        private const val ARG_BUILDING_ID = "buildingId"

        private val FLOORS_PATTERN = Regex(
            """Floors:\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )

        private val ROOMS_PATTERN = Regex(
            """Total Rooms:\s*\d+""",
            RegexOption.IGNORE_CASE
        )

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