@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentEditRoomBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class EditRoomFragment : Fragment() {

    private var _binding: FragmentEditRoomBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val roomId: Long by lazy {
        arguments?.getLong("roomId") ?: 0L
    }

    private val viewModel: EditRoomViewModel by viewModels {
        ViewModelFactory(requireContext(), roomId)
    }

    private val roomTypes = listOf(
        "Studio",
        "1 Bedroom",
        "2 Bedroom",
        "Shared Room",
        "Other"
    )

    private var renderingState = false
    private var navigationHandled = false
    private var confirmationDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditRoomBinding.inflate(
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

        setupRoomTypeDropdown()
        setupListeners()
        observeState()
    }

    private fun setupRoomTypeDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            roomTypes
        )

        binding.actvRoomType.setAdapter(adapter)

        binding.actvRoomType.setOnItemClickListener { parent, _, position, _ ->
            if (!renderingState) {
                val selectedType = parent.getItemAtPosition(position)
                    ?.toString()
                    .orEmpty()

                viewModel.onRoomTypeChanged(selectedType)
            }
        }
    }

    private fun setupListeners() {
        binding.btnBackEditRoom.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.etRoomNumber.doAfterTextChanged {
            if (!renderingState) {
                viewModel.onRoomNumberChanged(it?.toString().orEmpty())
            }
        }

        binding.etFloor.doAfterTextChanged {
            if (!renderingState) {
                viewModel.onFloorChanged(it?.toString().orEmpty())
            }
        }

        binding.etNotes.doAfterTextChanged {
            if (!renderingState) {
                viewModel.onNotesChanged(it?.toString().orEmpty())
            }
        }

        binding.etMonthlyRent.doAfterTextChanged {
            if (!renderingState) {
                viewModel.onRentChanged(it?.toString().orEmpty())
            }
        }

        binding.btnSaveChanges.setOnClickListener {
            if (!isBusy()) {
                viewModel.saveChanges()
            }
        }

        binding.btnArchiveRoom.setOnClickListener {
            showArchiveConfirmation()
        }

        binding.btnDeleteRoom.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun isBusy(): Boolean {
        val state = viewModel.uiState.value
        return state.isSaving || state.isDeleting
    }

    private fun showArchiveConfirmation() {
        if (isBusy() || confirmationDialog?.isShowing == true) return

        confirmationDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Archive room?")
            .setMessage(
                "This room will be removed from vacant assignment lists. " +
                        "Its history, invoices and tenant records will be kept."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Archive") { _, _ ->
                if (!isBusy()) {
                    viewModel.archiveRoom()
                }
            }
            .create()

        confirmationDialog?.show()
    }

    private fun showDeleteConfirmation() {
        if (isBusy() || confirmationDialog?.isShowing == true) return

        confirmationDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete room permanently?")
            .setMessage(
                "This cannot be undone. Rooms with active tenants or " +
                        "historical billing records cannot be deleted."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete room") { _, _ ->
                if (!isBusy()) {
                    viewModel.deleteRoom()
                }
            }
            .create()

        confirmationDialog?.show()

        confirmationDialog
            ?.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            ?.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.roomkit_error_dark
                )
            )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    private fun bindState(state: EditRoomUiState) {
        val currentBinding = _binding ?: return

        renderingState = true

        try {
            with(currentBinding) {
                tvHeaderTitle.text = state.originalRoomNumber
                    .takeIf { it.isNotBlank() }
                    ?.let { "Edit room $it" }
                    ?: "Edit room"

                // These are read-only TextViews in the redesigned layout.
                etBuilding.text = state.buildingName

                etRoomNumber.setTextIfDifferent(state.roomNumber)
                etFloor.setTextIfDifferent(state.floor)
                etNotes.setTextIfDifferent(state.notes)
                etMonthlyRent.setTextIfDifferent(state.defaultRentKhrInput)

                if (actvRoomType.text.toString() != state.roomType) {
                    actvRoomType.setText(state.roomType, false)
                }

                etOccupancy.text = if (state.isOccupied) {
                    "Occupied"
                } else {
                    "Vacant"
                }

                etOccupancy.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (state.isOccupied) {
                            R.color.roomkit_success_dark
                        } else {
                            R.color.roomkit_text_secondary
                        }
                    )
                )

                layoutOccupiedNotice.isVisible = state.isOccupied

                tvErrorMessage.text = state.errorMessage.orEmpty()
                tvErrorMessage.isVisible = !state.errorMessage.isNullOrBlank()

                val busy = state.isSaving || state.isDeleting

                etRoomNumber.isEnabled = !busy
                etFloor.isEnabled = !busy
                etNotes.isEnabled = !busy
                etMonthlyRent.isEnabled = !busy
                actvRoomType.isEnabled = !busy

                btnSaveChanges.isEnabled = !busy
                btnArchiveRoom.isEnabled = !busy
                btnDeleteRoom.isEnabled = !busy

                btnSaveChanges.alpha = if (busy) 0.55f else 1f
                btnSaveChanges.text = when {
                    state.isDeleting -> "Deleting…"
                    state.isSaving -> "Saving…"
                    else -> "Save changes"
                }
            }
        } finally {
            renderingState = false
        }

        if (navigationHandled) return

        val completionMessage = when {
            state.isDeleted -> "Room deleted"
            state.isSuccess -> "Room changes saved"
            else -> null
        } ?: return

        navigationHandled = true

        Toast.makeText(
            requireContext(),
            completionMessage,
            Toast.LENGTH_SHORT
        ).show()

        findNavController().popBackStack()
    }

    private fun EditText.setTextIfDifferent(value: String) {
        if (text.toString() == value) return

        val previousSelection = selectionStart
        setText(value)

        if (hasFocus()) {
            setSelection(
                previousSelection.coerceIn(0, value.length)
            )
        }
    }

    override fun onDestroyView() {
        confirmationDialog?.dismiss()
        confirmationDialog = null
        _binding = null
        super.onDestroyView()
    }
}