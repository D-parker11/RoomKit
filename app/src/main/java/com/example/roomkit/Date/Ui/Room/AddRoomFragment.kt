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
import com.example.roomkit.databinding.FragmentAddRoomBinding
import kotlinx.coroutines.launch

class AddRoomFragment : Fragment() {

    private var _binding: FragmentAddRoomBinding? = null
    private val binding get() = _binding!!

    private val buildingId: Long by lazy { arguments?.getLong("buildingId") ?: 0L }
    private val viewModel: AddRoomViewModel by viewModels {
        ViewModelFactory(requireContext(), buildingId)
    }

    private val roomTypes = listOf("Studio", "1 Bedroom", "2 Bedroom", "Shared Room", "Other")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddRoomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        binding.btnCancelAddRoom.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnHeaderSave.setOnClickListener {
            viewModel.saveRoom()
        }

        binding.btnSaveAndAddRoom.setOnClickListener {
            viewModel.saveRoom()
        }

        binding.etRoomNumber.doAfterTextChanged {
            viewModel.onRoomNumberChanged(it?.toString().orEmpty())
        }

        binding.etFloor.doAfterTextChanged {
            viewModel.onFloorChanged(it?.toString().orEmpty())
        }

        binding.etMonthlyRent.doAfterTextChanged {
            viewModel.onMonthlyRentChanged(it?.toString().orEmpty())
        }

        binding.etNotes.doAfterTextChanged {
            viewModel.onNotesChanged(it?.toString().orEmpty())
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Building Name
                    binding.tvHeaderBuildingName.text = state.buildingName

                    // Errors
                    binding.tilRoomNumber.error = state.roomNumberError
                    binding.tilMonthlyRent.error = state.rentError

                    if (state.generalError != null) {
                        binding.tvGeneralError.visibility = View.VISIBLE
                        binding.tvGeneralError.text = state.generalError
                    } else {
                        binding.tvGeneralError.visibility = View.GONE
                    }

                    // Button States
                    binding.btnHeaderSave.isEnabled = state.isSaveEnabled
                    binding.btnSaveAndAddRoom.isEnabled = state.isSaveEnabled

                    // Navigation on success
                    if (state.saveSuccess && findNavController().currentDestination?.id == com.example.roomkit.R.id.addRoomFragment) {
                        state.savedRoomId?.let { id ->
                            parentFragmentManager.setFragmentResult("room-added", androidx.core.os.bundleOf("roomId" to id))
                        }
                        Toast.makeText(
                            requireContext(),
                            "Room ${state.roomNumber.trim()} added successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}