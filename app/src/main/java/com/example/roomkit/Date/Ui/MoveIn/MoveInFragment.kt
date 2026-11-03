package com.example.roomkit.Date.Ui.Tenancy

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Model.VacantRoomOption
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.databinding.FragmentMoveInBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.example.roomkit.R


class MoveInFragment : Fragment() {

    private var _binding: FragmentMoveInBinding? = null
    private val binding get() = _binding!!

    private var roomAdapter: RoomOptionAdapter? = null
    private var confirmationDialog: AlertDialog? = null

    private var lastShownError: String? = null
    private var moveInErrorDialog: AlertDialog? = null

    private val presetRoomId: Long by lazy {
        arguments
            ?.getLong("presetRoomId", -1L)
            ?.takeIf { it > 0L }
            ?: arguments?.getLong("roomId", 0L)
            ?: 0L
    }

    private val presetTenantId: Long by lazy {
        arguments
            ?.getLong("presetTenantId", -1L)
            ?.takeIf { it > 0L }
            ?: arguments?.getLong("tenantId", 0L)
            ?: 0L
    }

    private val viewModel: MoveInViewModel by viewModels {
        ViewModelFactory(
            requireContext(),
            presetRoomId,
            presetTenantId
        )
    }

    private val khrFormat = DecimalFormat("#,###")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoveInBinding.inflate(
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

        setupClickListeners()
        setupInputWatchers()
        observeUiState()
    }

    private fun setupClickListeners() {

        binding.btnBackMoveIn.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.etMoveInDate.setOnClickListener {
            showMoveInDatePicker()
        }

        binding.btnConfirmMoveIn.setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun setupInputWatchers() {

        binding.etMonthlyRent.doAfterTextChanged {
            viewModel.onRentChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etDeposit.doAfterTextChanged {
            viewModel.onDepositChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etBillingDay.doAfterTextChanged {
            viewModel.onBillingDayChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etDueDay.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onDueDayChanged(value)
            // Do not show an error on an untouched empty field.
            if (value.isNotBlank() || binding.tilDueDay.error != null) {
                validateDueDay(value)
            }
        }

        binding.etElectricityRate.doAfterTextChanged {
            viewModel.onElectricityRateChanged(
                it?.toString().orEmpty()
            )
        }

        // Fixed water fee
        binding.etWaterFee.doAfterTextChanged {
            if (!viewModel.uiState.value.isWaterMetered) {
                viewModel.onWaterFeeOrRateChanged(
                    it?.toString().orEmpty()
                )
            }
        }

        // Metered water rate
        binding.etWaterRate.doAfterTextChanged {
            if (viewModel.uiState.value.isWaterMetered) {
                viewModel.onWaterFeeOrRateChanged(
                    it?.toString().orEmpty()
                )
            }
        }

        binding.etRubbishFee.doAfterTextChanged {
            viewModel.onRubbishFeeChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etInitialElectricity.doAfterTextChanged {
            viewModel.onInitialElecReadingChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etInitialWater.doAfterTextChanged {
            viewModel.onInitialWaterReadingChanged(
                it?.toString().orEmpty()
            )
        }

        binding.rgWaterMethod.setOnCheckedChangeListener { _, checkedId ->

            if (checkedId == View.NO_ID) {
                return@setOnCheckedChangeListener
            }

            val isMetered =
                checkedId == binding.rbWaterMetered.id

            if (viewModel.uiState.value.isWaterMetered != isMetered) {
                viewModel.onWaterMethodChanged(isMetered)
            }
        }
    }

    private fun showMoveInDatePicker() {

        val calendar = Calendar.getInstance().apply {
            timeInMillis = viewModel.uiState.value.moveInDate
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->

                calendar.set(
                    year,
                    month,
                    dayOfMonth
                )

                viewModel.onMoveInDateChanged(
                    calendar.timeInMillis
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeUiState() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.RESUMED
            ) {

                viewModel.uiState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    private fun bindState(
        state: MoveInUiState
    ) {

        bindDate(state)
        bindError(state)
        bindTenantSelector(state)
        bindRoomSelector(state)
        bindWaterMethod(state)
        bindInitialValues(state)
        bindSavingState(state)

        if (state.isSuccess) {
            handleMoveInSuccess(state)
        }
    }

    private fun bindDate(
        state: MoveInUiState
    ) {

        val current =
            binding.etMoveInDate.text
                ?.toString()
                .orEmpty()

        if (current != state.moveInDateFormatted) {
            binding.etMoveInDate.setText(
                state.moveInDateFormatted
            )
        }
    }

    private fun bindError(state: MoveInUiState) {
        val message = state.errorMessage
            ?.takeIf { it.isNotBlank() }

        binding.tvErrorMessage.isVisible = message != null
        binding.tvErrorMessage.text = message.orEmpty()

        if (message == null) {
            lastShownError = null

            moveInErrorDialog?.dismiss()
            moveInErrorDialog = null
            return
        }

        if (message == lastShownError) return

        moveInErrorDialog?.dismiss()
        lastShownError = message

        // Matches the active-tenancy error currently returned by your repository.
        val alreadyHasRoom = message.contains(
            "already rents",
            ignoreCase = true
        )

        val tenantId = state.tenantId

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (alreadyHasRoom) {
                    R.string.move_in_active_tenancy_title
                } else {
                    R.string.move_in_failed_title
                }
            )
            .setMessage(message)
            .setNegativeButton(R.string.move_in_dialog_cancel, null)

        if (alreadyHasRoom && tenantId != null && tenantId > 0L) {
            builder.setPositiveButton(
                R.string.move_in_view_current_tenancy
            ) { _, _ ->
                findNavController().navigate(
                    R.id.tenantDetailsFragment,
                    bundleOf("tenantId" to tenantId)
                )
            }
        }

        val dialog = builder.create()
        moveInErrorDialog = dialog

        dialog.setOnDismissListener {
            if (moveInErrorDialog === dialog) {
                moveInErrorDialog = null
            }
        }

        dialog.show()
    }

    private fun bindTenantSelector(
        state: MoveInUiState
    ) {

        if (state.isTenantLocked) {

            binding.actvTenant.setText(
                state.tenantName,
                false
            )

            binding.tilTenant.isEnabled = false

            return
        }

        binding.tilTenant.isEnabled = true

        val tenants = state.allTenants

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            tenants.map { it.fullName }
        )

        binding.actvTenant.setAdapter(adapter)

        binding.actvTenant.setOnItemClickListener {
                _,
                _,
                position,
                _ ->

            tenants
                .getOrNull(position)
                ?.let(viewModel::onTenantSelected)
        }
    }

    private fun bindRoomSelector(
        state: MoveInUiState
    ) {

        if (state.isRoomLocked) {

            binding.actvBuilding.setText(
                state.buildingName,
                false
            )

            binding.actvRoom.setText(
                state.roomNumber,
                false
            )

            binding.tilBuilding.isEnabled = false
            binding.tilRoom.isEnabled = false

            return
        }

        binding.tilBuilding.isEnabled = true

        setupBuildingSelector(state)
        if (state.allBuildings.size == 1) {
            binding.actvBuilding.setText(state.allBuildings.single().name, false)
        }

        binding.tilRoom.isEnabled =
            state.buildingId != null

        setupRoomOptions(
            state.availableRooms
        )
    }

    private fun setupBuildingSelector(
        state: MoveInUiState
    ) {

        val buildings = state.allBuildings

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            buildings.map { it.name }
        )

        binding.actvBuilding.setAdapter(adapter)

        binding.actvBuilding.setOnItemClickListener {
                _,
                _,
                position,
                _ ->

            buildings
                .getOrNull(position)
                ?.let(viewModel::onBuildingSelected)
        }
    }

    private fun bindWaterMethod(
        state: MoveInUiState
    ) {

        val checkedId =
            if (state.isWaterMetered) {
                binding.rbWaterMetered.id
            } else {
                binding.rbWaterFixed.id
            }

        if (binding.rgWaterMethod.checkedRadioButtonId != checkedId) {
            binding.rgWaterMethod.check(checkedId)
        }

        binding.tilWaterFee.isVisible =
            !state.isWaterMetered

        binding.tilWaterRate.isVisible =
            state.isWaterMetered

        binding.tilInitialWater.isVisible =
            state.isWaterMetered

        val waterValue =
            state.waterFeeOrRateKhrInput

        if (state.isWaterMetered) {

            val current =
                binding.etWaterRate.text
                    ?.toString()
                    .orEmpty()

            if (
                current.isBlank() &&
                waterValue.isNotBlank()
            ) {
                binding.etWaterRate.setText(
                    waterValue
                )
            }

        } else {

            val current =
                binding.etWaterFee.text
                    ?.toString()
                    .orEmpty()

            if (
                current.isBlank() &&
                waterValue.isNotBlank()
            ) {
                binding.etWaterFee.setText(
                    waterValue
                )
            }
        }
    }

    private fun bindInitialValues(
        state: MoveInUiState
    ) {

        if (
            binding.etMonthlyRent.text.isNullOrBlank() &&
            state.monthlyRentKhrInput.isNotBlank()
        ) {
            binding.etMonthlyRent.setText(
                state.monthlyRentKhrInput
            )
        }

        if (
            binding.etDeposit.text.isNullOrBlank() &&
            state.depositKhrInput.isNotBlank()
        ) {
            binding.etDeposit.setText(
                state.depositKhrInput
            )
        }
    }

    private fun bindSavingState(state: MoveInUiState) {
        val canEdit = !state.isSaving && !state.isSuccess

        binding.btnConfirmMoveIn.isEnabled = canEdit
        binding.btnConfirmMoveIn.text =
            if (state.isSaving) "Saving..." else "Confirm Move-In"

        binding.tilTenant.isEnabled =
            canEdit && !state.isTenantLocked

        binding.tilBuilding.isEnabled =
            canEdit && !state.isRoomLocked && state.allBuildings.size != 1

        binding.tilRoom.isEnabled =
            canEdit &&
                    !state.isRoomLocked &&
                    state.buildingId != null

        binding.etMoveInDate.isEnabled = canEdit
        binding.etMonthlyRent.isEnabled = canEdit
        binding.etDeposit.isEnabled = canEdit
        binding.etBillingDay.isEnabled = canEdit
        binding.etDueDay.isEnabled = canEdit
        binding.etElectricityRate.isEnabled = canEdit
        binding.etWaterFee.isEnabled = canEdit
        binding.etWaterRate.isEnabled = canEdit
        binding.etRubbishFee.isEnabled = canEdit
        binding.etInitialElectricity.isEnabled = canEdit
        binding.etInitialWater.isEnabled = canEdit
        binding.rbWaterFixed.isEnabled = canEdit
        binding.rbWaterMetered.isEnabled = canEdit
    }

    private fun setupRoomOptions(
        availableRooms: List<RoomEntity>
    ) {

        val roomOptions =
            availableRooms.map { room ->

                VacantRoomOption(
                    roomId = room.roomId,
                    buildingId = room.buildingId,
                    roomNumber = room.roomNumber,
                    floor = room.floor,
                    monthlyRent = room.monthlyRent
                )
            }

        roomAdapter = RoomOptionAdapter(
            requireContext(),
            roomOptions
        )

        binding.actvRoom.setAdapter(
            roomAdapter
        )

        binding.actvRoom.setOnItemClickListener {
                _,
                _,
                position,
                _ ->

            val selectedOption =
                roomOptions.getOrNull(position)
                    ?: return@setOnItemClickListener

            val room =
                availableRooms.getOrNull(position)
                    ?: return@setOnItemClickListener

            roomAdapter?.selectedRoomId =
                selectedOption.roomId

            roomAdapter?.notifyDataSetChanged()

            val formattedRent =
                CurrencyFormatter
                    .formatKhr(selectedOption.monthlyRent)

            binding.actvRoom.setText(
                "Room ${selectedOption.roomNumber} · $formattedRent/mo",
                false
            )

            val rentKhr =
                String.format(
                    Locale.US,
                    "%d",
                    selectedOption.monthlyRent
                )

            binding.etMonthlyRent.setText(
                rentKhr
            )

            binding.etDeposit.setText(
                rentKhr
            )

            viewModel.onRoomSelected(room)
        }
    }

    private fun validateDueDay(value: String): Boolean {
        val day = value.trim().toIntOrNull()
        val valid = day != null && day in 1..28
        binding.tilDueDay.error = if (valid) null else
            "Due day must be between 1 and 28."
        return valid
    }

    private fun showConfirmationDialog() {

        val state = viewModel.uiState.value

        if (state.isSaving || state.isSuccess) return

        if (!validateDueDay(state.dueDayInput)) {
            binding.etDueDay.requestFocus()
            return
        }

        if (confirmationDialog?.isShowing == true) return

        val waterSummary =
            if (state.isWaterMetered) {

                """
                Water Rate: ${
                    formatKhr(
                        state.waterFeeOrRateKhrInput
                    )
                } ៛/m³
                Initial Water: ${state.initialWaterReadingInput} m³
                """.trimIndent()

            } else {

                """
                Water: Fixed ${
                    formatKhr(
                        state.waterFeeOrRateKhrInput
                    )
                } ៛/month
                """.trimIndent()
            }

        val message =
            """
            Tenant: ${state.tenantName}
            Building: ${state.buildingName}
            Room: ${state.roomNumber}
            Move-In: ${state.moveInDateFormatted}

            Monthly Rent (KHR): ${state.monthlyRentKhrInput}
            Deposit (KHR): ${state.depositKhrInput}

            Billing Day: ${state.billingDayInput}
            Due Day: ${state.dueDayInput}

            Electricity: ${
                formatKhr(
                    state.electricityRateKhrInput
                )
            } ៛/kWh

            $waterSummary

            Rubbish: ${
                formatKhr(
                    state.rubbishFeeKhrInput
                )
            } ៛/month

            Initial Electricity: ${state.initialElecReadingInput} kWh
            """.trimIndent()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Move-In")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                viewModel.confirmMoveIn()
            }
            .create()

        confirmationDialog = dialog

        dialog.setOnDismissListener {
            if (confirmationDialog === dialog) {
                confirmationDialog = null
            }
        }

        dialog.show()
    }

    private fun formatKhr(
        value: String
    ): String {

        return khrFormat.format(
            value.toLongOrNull() ?: 0L
        )
    }

    private fun handleMoveInSuccess(state: MoveInUiState) {
        viewModel.onMoveInSuccessHandled()

        Toast.makeText(
            requireContext(),
            "Tenant moved into Room ${state.roomNumber}",
            Toast.LENGTH_SHORT
        ).show()

        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        moveInErrorDialog?.dismiss()
        moveInErrorDialog = null
        lastShownError = null

        confirmationDialog?.dismiss()
        confirmationDialog = null

        roomAdapter = null
        _binding = null

        super.onDestroyView()
    }
}
