package com.example.roomkit.Date.Ui.Room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.databinding.FragmentEditRoomBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class EditRoomFragment : Fragment() {

    private var _binding: FragmentEditRoomBinding? = null
    private val binding get() = _binding!!

    private val roomId: Long by lazy { arguments?.getLong("roomId") ?: 0L }
    private val viewModel: EditRoomViewModel by viewModels {
        ViewModelFactory(requireContext(), roomId)
    }

    private val roomTypes = listOf("Studio", "1 Bedroom", "2 Bedroom", "Shared Room", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditRoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBackEditRoom.setOnClickListener {
            findNavController().popBackStack()
        }

        setupRoomTypeDropdown()
        setupListeners()
        observeState()
    }

    private fun setupRoomTypeDropdown() {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, roomTypes)
        binding.actvRoomType.setAdapter(adapter)
        binding.actvRoomType.setOnItemClickListener { _, _, position, _ ->
            viewModel.onRoomTypeChanged(roomTypes[position])
        }
    }

    private fun setupListeners() {
        binding.etRoomNumber.doAfterTextChanged { viewModel.onRoomNumberChanged(it?.toString().orEmpty()) }
        binding.etFloor.doAfterTextChanged { viewModel.onFloorChanged(it?.toString().orEmpty()) }
        binding.etNotes.doAfterTextChanged { viewModel.onNotesChanged(it?.toString().orEmpty()) }
        binding.etMonthlyRent.doAfterTextChanged { viewModel.onRentChanged(it?.toString().orEmpty()) }

        binding.btnSaveChanges.setOnClickListener {
            viewModel.saveChanges()
        }

        binding.btnArchiveRoom.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Archive Room?")
                .setMessage("Archiving removes this room from vacant assignment lists while keeping all history, invoices, and tenant records intact.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Archive") { _, _ -> viewModel.archiveRoom() }
                .show()
        }

        binding.btnDeleteRoom.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Room permanently?")
                .setMessage("Are you sure? Rooms with active tenants or historical billing records cannot be deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ -> viewModel.deleteRoom() }
                .show()
        }
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
        binding.tvHeaderTitle.text = "Edit Room ${state.originalRoomNumber.ifBlank { "" }}"
        binding.etBuilding.setText(state.buildingName)

        if (binding.etRoomNumber.text.isNullOrBlank() && state.roomNumber.isNotBlank()) {
            binding.etRoomNumber.setText(state.roomNumber)
        }
        if (binding.etFloor.text.isNullOrBlank() && state.floor.isNotBlank()) {
            binding.etFloor.setText(state.floor)
        }
        if (binding.etMonthlyRent.text.isNullOrBlank() && state.defaultRentKhrInput.isNotBlank()) {
            binding.etMonthlyRent.setText(state.defaultRentKhrInput)
        }
        if (binding.etNotes.text.isNullOrBlank() && state.notes.isNotBlank()) {
            binding.etNotes.setText(state.notes)
        }

        binding.etOccupancy.setText(if (state.isOccupied) "Occupied" else "Vacant")
        binding.layoutOccupiedNotice.visibility = if (state.isOccupied) View.VISIBLE else View.GONE

        // Errors
        if (state.errorMessage != null) {
            binding.tvErrorMessage.visibility = View.VISIBLE
            binding.tvErrorMessage.text = state.errorMessage
        } else {
            binding.tvErrorMessage.visibility = View.GONE
        }

        binding.btnSaveChanges.isEnabled = !state.isSaving

        if (state.isSuccess) {
            Toast.makeText(requireContext(), "Room updated successfully", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        if (state.isDeleted) {
            Toast.makeText(requireContext(), "Room deleted", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}