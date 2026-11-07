package com.example.roomkit.Date.Ui.Tenancy

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.databinding.FragmentMoveInBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Calendar
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.example.roomkit.R


class MoveInFragment : Fragment() {

    private var _binding: FragmentMoveInBinding? = null
    private val binding get() = _binding!!

    private var isBindingState = false
    private var tenantPicker: TenantPickerDialog? = null
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

        setupDropdownListeners()
        setupClickListeners()
        setupInputWatchers()
        observeUiState()
    }

    private fun setupClickListeners() {

        binding.actvTenant.setOnClickListener { showTenantPicker() }
        binding.tilTenant.setEndIconOnClickListener { showTenantPicker() }

        binding.btnBackMoveIn.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.etMoveInDate.setOnClickListener { showMoveInDatePicker() }
        binding.tilMoveInDate.setEndIconOnClickListener { showMoveInDatePicker() }

        binding.btnConfirmMoveIn.setOnClickListener {
            showConfirmationDialog()
        }
    }

    private fun setupInputWatchers() {

        binding.etMonthlyRent.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onRentChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etDeposit.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onDepositChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etBillingDay.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onBillingDayChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etDueDay.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            val value = it?.toString().orEmpty()
            viewModel.onDueDayChanged(value)
            // Do not show an error on an untouched empty field.
            if (value.isNotBlank() || binding.tilDueDay.error != null) {
                validateDueDay(value)
            }
        }

        binding.etElectricityRate.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onElectricityRateChanged(
                it?.toString().orEmpty()
            )
        }

        // Fixed water fee
        binding.etWaterFee.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            if (!viewModel.uiState.value.isWaterMetered) {
                viewModel.onWaterFeeOrRateChanged(
                    it?.toString().orEmpty()
                )
            }
        }

        // Metered water rate
        binding.etWaterRate.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            if (viewModel.uiState.value.isWaterMetered) {
                viewModel.onWaterFeeOrRateChanged(
                    it?.toString().orEmpty()
                )
            }
        }

        binding.etRubbishFee.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onRubbishFeeChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etInitialElectricity.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onInitialElecReadingChanged(
                it?.toString().orEmpty()
            )
        }

        binding.etInitialWater.doAfterTextChanged {
            if (isBindingState) return@doAfterTextChanged
            viewModel.onInitialWaterReadingChanged(
                it?.toString().orEmpty()
            )
        }

        binding.rgWaterMethod.setOnCheckedChangeListener { _, checkedId ->

            if (isBindingState || checkedId == View.NO_ID) {
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

                launch { viewModel.uiState.collect { state -> bindState(state) } }
                launch {
                    viewModel.tenantPickerState.collect { picker ->
                        bindTenantSelector(viewModel.uiState.value)
                        tenantPicker?.render(picker, viewModel.uiState.value.tenantId)
                    }
                }
            }
        }
    }

    private fun bindState(
        state: MoveInUiState
    ) {

        isBindingState = true
        try {
            bindDate(state)
            bindError(state)
            bindTenantSelector(state)
            bindRoomSelector(state)
            bindWaterMethod(state)
            bindInitialValues(state)
            bindSavingState(state)
        } finally {
            isBindingState = false
        }

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

        binding.cardError.isVisible = message != null
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

    private fun bindTenantSelector(state: MoveInUiState) {
        setInputText(binding.actvTenant, state.tenantName)
        val picker = viewModel.tenantPickerState.value
        val option = picker.options.firstOrNull { it.tenantId == state.tenantId }
        binding.tilTenant.helperText = when {
            picker.isLoading -> "Checking tenant availability…"
            picker.errorMessage != null -> "Availability unavailable. Open the picker to retry."
            option != null -> option.assignmentLabel
            picker.options.isEmpty() -> "No tenants found. Add a tenant first."
            else -> "Search by name or phone · Available tenants first"
        }
    }

    private fun showTenantPicker() {
        val state = viewModel.uiState.value
        if (state.isTenantLocked || state.isSaving || state.isSuccess) return
        if (tenantPicker?.isShowing == true) return
        val picker = TenantPickerDialog(requireContext(),
            onSelected = viewModel::selectTenantForMoveIn,
            onRetry = viewModel::reloadTenantOptions)
        tenantPicker = picker
        picker.setOnDismissListener { if (tenantPicker === picker) tenantPicker = null }
        picker.render(viewModel.tenantPickerState.value, state.tenantId)
        picker.show()
    }

    private fun bindRoomSelector(state: MoveInUiState) {
        setupBuildingSelector(state)
        updateOptions(binding.actvRoom, state.availableRooms.map {
            MoveInOption(it.roomId, roomLabel(it.roomNumber, it.monthlyRent))
        })
        val room = state.availableRooms.firstOrNull { it.roomId == state.roomId }
        val label = when {
            room != null -> roomLabel(room.roomNumber, room.monthlyRent)
            state.isRoomLocked -> "Room ${state.roomNumber}"
            else -> ""
        }
        setDropdownText(binding.actvRoom, label)
        binding.tilRoom.helperText = when {
            state.isRoomLocked -> "Room selected from its details"
            state.buildingId == null -> "Select a property first"
            state.availableRooms.isEmpty() -> "No vacant rooms available in this property"
            else -> "${state.availableRooms.size} vacant room(s) available"
        }
    }

    private fun setupBuildingSelector(state: MoveInUiState) {
        updateOptions(binding.actvBuilding, state.allBuildings.map {
            MoveInOption(it.buildingId, it.name)
        })
        setDropdownText(binding.actvBuilding, state.buildingName)
    }

    private fun bindWaterMethod(state: MoveInUiState) {
        val checkedId = if (state.isWaterMetered) binding.rbWaterMetered.id else binding.rbWaterFixed.id
        if (binding.rgWaterMethod.checkedRadioButtonId != checkedId) binding.rgWaterMethod.check(checkedId)
        binding.tilWaterFee.isVisible = !state.isWaterMetered
        binding.tilWaterRate.isVisible = state.isWaterMetered
        binding.tilInitialWater.isVisible = state.isWaterMetered
        val field = if (state.isWaterMetered) binding.etWaterRate else binding.etWaterFee
        setInputText(field, state.waterFeeOrRateKhrInput)
    }

    private fun bindInitialValues(state: MoveInUiState) {
        setInputText(binding.etMonthlyRent, state.monthlyRentKhrInput)
        setInputText(binding.etDeposit, state.depositKhrInput)
        setInputText(binding.etBillingDay, state.billingDayInput)
        setInputText(binding.etDueDay, state.dueDayInput)
        setInputText(binding.etElectricityRate, state.electricityRateKhrInput)
        setInputText(binding.etRubbishFee, state.rubbishFeeKhrInput)
        setInputText(binding.etInitialElectricity, state.initialElecReadingInput)
        setInputText(binding.etInitialWater, state.initialWaterReadingInput)
    }

    private fun setInputText(field: android.widget.EditText, value: String) {
        if (field.text.toString() != value) {
            field.setText(value)
            field.setSelection(value.length)
        }
    }

    private fun setDropdownText(field: AutoCompleteTextView, value: String) {
        if (field.text.toString() != value) field.setText(value, false)
    }

    private fun bindSavingState(state: MoveInUiState) {
        val canEdit = !state.isSaving && !state.isSuccess

        binding.btnConfirmMoveIn.isEnabled = canEdit
        binding.btnConfirmMoveIn.text =
            if (state.isSaving) "Saving…" else "Review move-in"

        binding.tilTenant.isEnabled =
            canEdit && !state.isTenantLocked

        binding.tilBuilding.isEnabled =
            canEdit && !state.isRoomLocked && state.allBuildings.size != 1

        binding.tilRoom.isEnabled =
            canEdit &&
                    !state.isRoomLocked &&
                    state.buildingId != null && state.availableRooms.isNotEmpty()

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

    private fun setupDropdownListeners() {
        binding.actvBuilding.setOnItemClickListener { parent, _, position, _ ->
            val option = parent.getItemAtPosition(position) as? MoveInOption
                ?: return@setOnItemClickListener
            viewModel.uiState.value.allBuildings.firstOrNull { it.buildingId == option.id }
                ?.let(viewModel::onBuildingSelected)
        }
        binding.actvRoom.setOnItemClickListener { parent, _, position, _ ->
            val option = parent.getItemAtPosition(position) as? MoveInOption
                ?: return@setOnItemClickListener
            viewModel.uiState.value.availableRooms.firstOrNull { it.roomId == option.id }
                ?.let(viewModel::onRoomSelected)
        }
        listOf(binding.actvBuilding, binding.actvRoom).forEach { field ->
            field.threshold = 0
            field.setDropDownBackgroundDrawable(GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.roomkit_surface))
                cornerRadius = 16f * resources.displayMetrics.density
            })
        }
    }

    private fun updateOptions(field: AutoCompleteTextView, options: List<MoveInOption>) {
        val existing = field.adapter as? MoveInOptionsAdapter
        if (existing?.options != options) {
            field.setAdapter(MoveInOptionsAdapter(requireContext(), options))
        }
    }

    private fun roomLabel(number: String, rent: Long): String =
        "Room $number · ${CurrencyFormatter.formatKhr(rent)} / month"

    private data class MoveInOption(val id: Long, val label: String) {
        override fun toString(): String = label
    }

    /** Non-editable selectors always expose the entire current list, including after a selection. */
    private class MoveInOptionsAdapter(
        context: Context,
        val options: List<MoveInOption>
    ) : ArrayAdapter<MoveInOption>(context, android.R.layout.simple_list_item_1, options) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            row(position, convertView)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            row(position, convertView)

        private fun row(position: Int, convertView: View?): TextView {
            val density = context.resources.displayMetrics.density
            return ((convertView as? TextView) ?: TextView(context)).apply {
                layoutParams = android.widget.AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                minHeight = (56 * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (12 * density).toInt(),
                    (16 * density).toInt(), (12 * density).toInt())
                setTextColor(ContextCompat.getColor(context, R.color.roomkit_text_primary))
                setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_surface))
                textSize = 15f
                maxLines = 3
                text = getItem(position)?.label.orEmpty()
            }
        }

        private val completeListFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().apply { values = options; count = options.size }
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
            override fun convertResultToString(resultValue: Any?): CharSequence =
                (resultValue as? MoveInOption)?.label.orEmpty()
        }
        override fun getFilter(): Filter = completeListFilter
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
        tenantPicker?.dismiss()
        tenantPicker = null
        moveInErrorDialog?.dismiss()
        moveInErrorDialog = null
        lastShownError = null

        confirmationDialog?.dismiss()
        confirmationDialog = null

        isBindingState = false
        _binding = null

        super.onDestroyView()
    }
}
