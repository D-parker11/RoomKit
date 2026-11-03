//@file:Suppress("PackageNaming")
//
//package com.example.roomkit.Date.Ui.Room
//
//import android.content.DialogInterface
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.appcompat.app.AlertDialog
//import androidx.core.content.ContextCompat
//import androidx.core.os.ConfigurationCompat
//import androidx.core.os.bundleOf
//import androidx.core.view.isVisible
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.repeatOnLifecycle
//import androidx.navigation.fragment.findNavController
//import com.example.roomkit.Date.Billing.CurrencyFormatter
//import com.example.roomkit.Date.Local.Model.RoomDetailUiState
//import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
//import com.example.roomkit.R
//import com.example.roomkit.databinding.FragmentRoomDetailBinding
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import java.text.NumberFormat
//import java.text.SimpleDateFormat
//import java.util.Calendar
//import java.util.Date
//import java.util.Locale
//import kotlinx.coroutines.launch
//
//class RoomDetailFragment : Fragment() {
//
//    private var _binding: FragmentRoomDetailBinding? = null
//    private val binding get() = _binding!!
//
//    private val roomId: Long by lazy {
//        arguments?.getLong("roomId") ?: -1L
//    }
//
//    private val viewModel: RoomDetailViewModel by viewModels {
//        ViewModelFactory(requireContext(), targetId = roomId)
//    }
//
//    private var latestState: RoomDetailUiState? = null
//    private var tenancyDialog: AlertDialog? = null
//    private var moveOutInProgress = false
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentRoomDetailBinding.inflate(
//            inflater,
//            container,
//            false
//        )
//        return binding.root
//    }
//
//    override fun onViewCreated(
//        view: View,
//        savedInstanceState: Bundle?
//    ) {
//        super.onViewCreated(view, savedInstanceState)
//
//        binding.btnBack.setOnClickListener {
//            findNavController().popBackStack()
//        }
//
//        if (roomId <= 0L) {
//            showMessage(getString(R.string.rk_room_detail_room_not_found))
//            findNavController().popBackStack()
//            return
//        }
//
//        setupListeners()
//        observeRoom()
//    }
//
//    private fun setupListeners() {
//        binding.btnEditRoom.setOnClickListener {
//            val state = latestState ?: return@setOnClickListener
//
//            findNavController().navigate(
//                R.id.editRoomFragment,
//                bundleOf("roomId" to state.roomId)
//            )
//        }
//
//        binding.btnAssignTenantQuick.setOnClickListener {
//            val state = latestState ?: return@setOnClickListener
//
//            findNavController().navigate(
//                R.id.moveInFragment,
//                bundleOf(
//                    "presetRoomId" to state.roomId,
//                    "presetBuildingId" to state.buildingId
//                )
//            )
//        }
//
//        binding.btnManageTenancy.setOnClickListener {
//            if (moveOutInProgress) return@setOnClickListener
//
//            val state = latestState ?: return@setOnClickListener
//            showManageTenancyDialog(state)
//        }
//
//        // This button opens only the meter-reading screen.
//        binding.btnRecordMeter.setOnClickListener {
//            if (moveOutInProgress) return@setOnClickListener
//
//            val state = latestState ?: return@setOnClickListener
//            openMeterReadings(state)
//        }
//
//        // This button opens only the existing invoice-preview screen.
//        binding.btnViewInvoice.setOnClickListener {
//            if (moveOutInProgress) return@setOnClickListener
//
//            val state = latestState ?: return@setOnClickListener
//            openInvoicePreview(state)
//        }
//    }
//
//    private fun observeRoom() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                viewModel.uiState.collect { state ->
//                    if (state != null) {
//                        renderUi(state)
//                    }
//                }
//            }
//        }
//    }
//
//    private fun renderUi(state: RoomDetailUiState) {
//        latestState = state
//
//        renderHeader(state)
//        renderOccupancy(state)
//        renderReadings(state)
//        renderInvoice(state)
//        renderActions(state)
//    }
//
//    private fun renderHeader(state: RoomDetailUiState) {
//        binding.tvHeaderRoomNumber.text = getString(
//            R.string.rk_room_detail_room_number,
//            state.roomNumber
//        )
//
//        binding.tvHeaderBuildingName.text = state.buildingName.ifBlank {
//            getString(R.string.rk_room_detail_building_fallback)
//        }
//
//        binding.tvMonthlyRent.text = getString(
//            R.string.rk_room_detail_monthly_rent,
//            CurrencyFormatter.formatKhr(state.monthlyRent)
//        )
//    }
//
//    private fun renderOccupancy(state: RoomDetailUiState) {
//        val occupied = state.isOccupied
//
//        binding.layoutOccupiedDetails.isVisible = occupied
//        binding.layoutVacantDetails.isVisible = !occupied
//
//        binding.tvOccupancyBadge.apply {
//            setText(
//                if (occupied) {
//                    R.string.rk_room_detail_occupied
//                } else {
//                    R.string.rk_room_detail_vacant
//                }
//            )
//
//            setBackgroundResource(
//                if (occupied) {
//                    R.drawable.bg_badge_paid_pill
//                } else {
//                    R.drawable.bg_badge_unpaid_pill
//                }
//            )
//
//            backgroundTintList = ContextCompat.getColorStateList(
//                requireContext(),
//                if (occupied) {
//                    R.color.roomkit_primary
//                } else {
//                    R.color.roomkit_surface
//                }
//            )
//
//            setTextColor(
//                ContextCompat.getColor(
//                    requireContext(),
//                    if (occupied) {
//                        R.color.roomkit_on_primary
//                    } else {
//                        R.color.roomkit_text_secondary
//                    }
//                )
//            )
//        }
//
//        binding.tvTenantName.text =
//            state.tenantName?.takeIf { it.isNotBlank() }
//                ?: getString(R.string.rk_room_detail_tenant_fallback)
//
//        binding.tvTenantPhone.text =
//            state.tenantPhone?.takeIf { it.isNotBlank() }
//                ?: getString(R.string.rk_room_detail_phone_missing)
//
//        binding.tvMoveInDate.text = state.moveInDate?.let {
//            getString(
//                R.string.rk_room_detail_moved_in,
//                formatDate(it)
//            )
//        } ?: getString(R.string.rk_room_detail_move_in_missing)
//
//        binding.tvDepositValue.text = state.deposit?.let {
//            CurrencyFormatter.formatKhr(it)
//        } ?: getString(R.string.rk_room_detail_unavailable)
//    }
//
//    private fun renderReadings(state: RoomDetailUiState) {
//        val electricityUnit = getString(
//            R.string.rk_room_detail_unit_electricity
//        )
//        val waterUnit = getString(R.string.rk_room_detail_unit_water)
//
//        binding.tvMeterReadingsSectionTitle.text = getString(
//            R.string.rk_room_detail_readings_heading,
//            formatPeriod(state.billingMonth, state.billingYear)
//        )
//
//        binding.tvElecPreviousVal.text = formatReading(
//            state.electricityPrevious,
//            electricityUnit
//        )
//
//        binding.tvElecCurrentVal.text = formatReading(
//            state.electricityCurrent,
//            electricityUnit
//        )
//
//        binding.tvElecUsageVal.text = formatReading(
//            validUsage(
//                state.electricityPrevious,
//                state.electricityCurrent,
//                state.electricityUsage
//            ),
//            electricityUnit
//        )
//
//        binding.tvWaterHeader.setText(R.string.rk_room_detail_water)
//        binding.layoutFixedWaterRow.isVisible = state.waterIsFixed
//        binding.layoutMeteredWaterGrid.isVisible = !state.waterIsFixed
//
//        if (state.waterIsFixed) {
//            binding.tvFixedWaterFee.text = formatKhr(state.waterCharge)
//        } else {
//            binding.tvWaterPreviousVal.text = formatReading(
//                state.waterPrevious,
//                waterUnit
//            )
//
//            binding.tvWaterCurrentVal.text = formatReading(
//                state.waterCurrent,
//                waterUnit
//            )
//
//            binding.tvWaterUsageVal.text = formatReading(
//                validUsage(
//                    state.waterPrevious,
//                    state.waterCurrent,
//                    state.waterUsage
//                ),
//                waterUnit
//            )
//        }
//    }
//
//    private fun renderInvoice(state: RoomDetailUiState) {
//        val period = formatPeriod(state.billingMonth, state.billingYear)
//        val hasInvoice = state.invoiceId != null
//
//        binding.tvInvoiceSectionTitle.text = getString(
//            R.string.rk_room_detail_invoice_heading,
//            period
//        )
//
//        binding.tvInvoiceCardTitle.text = getString(
//            R.string.rk_room_detail_invoice_title,
//            period
//        )
//
//        binding.layoutInvoiceContent.isVisible = hasInvoice
//        binding.layoutInvoiceEmpty.isVisible = !hasInvoice
//        binding.tvInvoiceStatusBadge.isVisible = hasInvoice
//
//        // Button visibility is managed independently in renderActions().
//        if (!hasInvoice) {
//            binding.tvEmptyInvoiceTitle.text =
//                if (state.isOccupied) {
//                    getString(
//                        R.string.rk_room_detail_no_invoice,
//                        period
//                    )
//                } else {
//                    getString(R.string.rk_room_detail_no_tenancy_invoice)
//                }
//            return
//        }
//
//        binding.tvInvoiceRentVal.text = CurrencyFormatter.formatKhr(
//            state.rentCharge ?: state.monthlyRent
//        )
//
//        binding.tvInvoiceElecVal.text = formatKhr(
//            state.electricityCharge
//        )
//
//        binding.tvInvoiceElecSubtext.setText(
//            R.string.rk_room_detail_usage_hint
//        )
//
//        binding.tvInvoiceWaterLabel.setText(
//            if (state.waterIsFixed) {
//                R.string.rk_room_detail_water_fixed
//            } else {
//                R.string.rk_room_detail_water_metered
//            }
//        )
//
//        binding.tvInvoiceWaterVal.text = formatKhr(state.waterCharge)
//
//        val rubbish = state.rubbishCharge
//        binding.rowRubbish.isVisible = rubbish != null && rubbish > 0L
//        binding.tvInvoiceRubbishVal.text = formatKhr(rubbish)
//
//        // These saved snapshot values are not exposed by the current state.
//        binding.tvInvoiceUtilitySubtotalKhr.setText(
//            R.string.rk_room_detail_invoice_value_hint
//        )
//
//
//
//        binding.tvInvoiceTotalKhr.text =
//            state.totalInvoiceAmount?.let {
//                CurrencyFormatter.formatKhr(it)
//            } ?: getString(R.string.rk_room_detail_unavailable)
//
//        binding.tvInvoiceDueDate.text = state.invoiceDueDate?.let {
//            getString(
//                R.string.rk_room_detail_due,
//                formatDate(it)
//            )
//        } ?: getString(R.string.rk_room_detail_due_missing)
//
//        renderInvoiceStatus(state.invoiceStatus)
//    }
//
//    private fun renderActions(state: RoomDetailUiState) {
//        val enabled = !moveOutInProgress
//
//        // Available before an invoice is issued.
//        // Missing readings do not redirect this button to Record Meter.
//        binding.btnViewInvoice.apply {
//            setText(R.string.rk_room_detail_view_invoice)
//            isVisible = state.isOccupied
//            isEnabled = enabled
//        }
//
//        binding.btnRecordMeter.apply {
//            setText(R.string.rk_room_detail_record_meter)
//            isVisible = state.isOccupied &&
//                    (
//                            state.invoiceId == null ||
//                                    state.invoiceStatus.equals("DRAFT", ignoreCase = true)
//                            )
//            isEnabled = enabled
//        }
//
//        binding.btnManageTenancy.isVisible = state.isOccupied
//        binding.btnManageTenancy.isEnabled = enabled
//        binding.btnEditRoom.isEnabled = enabled
//        binding.btnAssignTenantQuick.isEnabled = enabled
//    }
//
//    private fun openInvoicePreview(state: RoomDetailUiState) {
//        val invoiceId = state.invoiceId
//        val isDraft = state.invoiceStatus.equals(
//        "DRAFT",
//        ignoreCase = true
//    )
//
//    // Open the saved invoice after it has been issued.
//        if (invoiceId != null && invoiceId > 0L && !isDraft) {
//        findNavController().navigate(
//            R.id.invoiceDetailFragment,
//            bundleOf("invoiceId" to invoiceId)
//        )
//        return
//    }
//
//        if (!state.isOccupied) {
//        showMessage(getString(R.string.rk_room_detail_no_tenancy))
//        return
//        }
//
//    // No issued invoice: open the draft preview.
//        findNavController().navigate(
//        R.id.invoicePreviewFragment,
//        bundleOf("roomId" to state.roomId)
//        )
//    }
//
//    private fun openMeterReadings(state: RoomDetailUiState) {
//        findNavController().navigate(
//            R.id.action_roomDetailFragment_to_meterReadingFragment,
//            bundleOf(
//                "roomId" to state.roomId,
//                "preselectedBuildingId" to state.buildingId
//            )
//        )
//    }
//
//    private fun renderInvoiceStatus(status: String?) {
//        val normalized = status?.uppercase(Locale.US)
//
//        val labelResource: Int
//        val backgroundResource: Int
//        val colorResource: Int
//
//        when (normalized) {
//            "PAID" -> {
//                labelResource = R.string.rk_room_detail_paid
//                backgroundResource = R.drawable.bg_badge_paid_pill
//                colorResource = R.color.roomkit_success_dark
//            }
//
//            "PARTIAL", "PARTIALLY_PAID" -> {
//                labelResource = R.string.rk_room_detail_partial
//                backgroundResource = R.drawable.bg_badge_partial_pill
//                colorResource = R.color.roomkit_warning_dark
//            }
//
//            "OVERDUE" -> {
//                labelResource = R.string.rk_room_detail_overdue
//                backgroundResource = R.drawable.bg_badge_overdue_pill
//                colorResource = R.color.roomkit_error_dark
//            }
//
//            else -> {
//                labelResource = when (normalized) {
//                    "DRAFT" -> R.string.rk_room_detail_draft
//                    "ISSUED" -> R.string.rk_room_detail_issued
//                    "VOID" -> R.string.rk_room_detail_void
//                    "UNPAID" -> R.string.rk_room_detail_unpaid
//                    else -> R.string.rk_room_detail_unknown
//                }
//
//                backgroundResource = R.drawable.bg_badge_unpaid_pill
//                colorResource = R.color.roomkit_text_secondary
//            }
//        }
//
//        binding.tvInvoiceStatusBadge.apply {
//            setText(labelResource)
//            setBackgroundResource(backgroundResource)
//            setTextColor(
//                ContextCompat.getColor(requireContext(), colorResource)
//            )
//        }
//    }
//
//    private fun showManageTenancyDialog(state: RoomDetailUiState) {
//        if (tenancyDialog?.isShowing == true) return
//
//        val dialog = MaterialAlertDialogBuilder(requireContext())
//            .setTitle(R.string.rk_room_detail_manage)
//            .setItems(
//                arrayOf(
//                    getString(R.string.rk_room_detail_profile),
//                    getString(R.string.rk_room_detail_move_out)
//                )
//            ) { _, selected ->
//                when (selected) {
//                    0 -> openTenantProfile(state)
//                    1 -> confirmMoveOut(state)
//                }
//            }
//            .setNegativeButton(R.string.rk_room_detail_cancel, null)
//            .create()
//
//        tenancyDialog = dialog
//
//        dialog.setOnDismissListener {
//            if (tenancyDialog === dialog) {
//                tenancyDialog = null
//            }
//        }
//
//        dialog.show()
//    }
//
//    private fun openTenantProfile(state: RoomDetailUiState) {
//        val tenantId = state.tenantId
//
//        if (tenantId == null || tenantId <= 0L) {
//            showMessage(
//                getString(R.string.rk_room_detail_profile_missing)
//            )
//            return
//        }
//
//        findNavController().navigate(
//            R.id.tenantDetailsFragment,
//            bundleOf("tenantId" to tenantId)
//        )
//    }
//
//    private fun confirmMoveOut(state: RoomDetailUiState) {
//        val id = state.tenancyId ?: return
//        findNavController().navigate(R.id.moveOutFragment, Bundle().apply { putLong("tenancyId", id) })
//    }
//
//    private fun displayLocale(): Locale =
//        ConfigurationCompat.getLocales(resources.configuration)[0]
//            ?: Locale.getDefault()
//
//    private fun formatPeriod(month: Int, year: Int): String {
//        if (month !in 1..12 || year <= 0) {
//            return getString(R.string.rk_room_detail_selected_period)
//        }
//
//        val calendar = Calendar.getInstance().apply {
//            clear()
//            set(year, month - 1, 1)
//        }
//
//        val locale = displayLocale()
//        val pattern = android.text.format.DateFormat
//            .getBestDateTimePattern(locale, "MMMyyyy")
//
//        return SimpleDateFormat(pattern, locale).format(calendar.time)
//    }
//
//    private fun validUsage(
//        previous: Long?,
//        current: Long?,
//        storedUsage: Long?
//    ): Long? {
//        if (previous != null && current != null) {
//            return if (previous >= 0L && current >= previous) {
//                current - previous
//            } else {
//                null
//            }
//        }
//
//        return storedUsage?.takeIf { it >= 0L }
//    }
//
//    private fun formatReading(value: Long?, unit: String): String =
//        value?.let {
//            getString(
//                R.string.rk_room_detail_reading_value,
//                NumberFormat.getIntegerInstance(displayLocale()).format(it),
//                unit
//            )
//        } ?: getString(R.string.rk_room_detail_unavailable)
//
//    private fun formatKhr(value: Long?): String =
//        value?.let {
//            getString(
//                R.string.rk_room_detail_khr_value,
//                NumberFormat.getIntegerInstance(displayLocale()).format(it)
//            )
//        } ?: getString(R.string.rk_room_detail_unavailable)
//
//    private fun formatDate(timestamp: Long): String {
//        val locale = displayLocale()
//        val pattern = android.text.format.DateFormat
//            .getBestDateTimePattern(locale, "yMMMd")
//
//        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
//    }
//
//    private fun showMessage(message: String) {
//        val currentContext = context ?: return
//
//        Toast.makeText(
//            currentContext,
//            message,
//            Toast.LENGTH_LONG
//        ).show()
//    }
//
//    override fun onDestroyView() {
//        tenancyDialog?.dismiss()
//        tenancyDialog = null
//        latestState = null
//        _binding = null
//        super.onDestroyView()
//    }
//}
@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Room

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Model.RoomDetailUiState
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentRoomDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

class RoomDetailFragment : Fragment() {

    private var _binding: FragmentRoomDetailBinding? = null
    private val binding get() = _binding!!

    private val roomId: Long by lazy {
        arguments?.getLong("roomId") ?: -1L
    }

    private val viewModel: RoomDetailViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = roomId)
    }

    private var latestState: RoomDetailUiState? = null
    private var tenancyDialog: AlertDialog? = null
    private var moveOutInProgress = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomDetailBinding.inflate(
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

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        if (roomId <= 0L) {
            showMessage(getString(R.string.rk_room_detail_room_not_found))
            findNavController().popBackStack()
            return
        }

        setupListeners()
        observeRoom()
    }

    private fun setupListeners() {
        binding.btnEditRoom.setOnClickListener {
            val state = latestState ?: return@setOnClickListener

            findNavController().navigate(
                R.id.editRoomFragment,
                bundleOf("roomId" to state.roomId)
            )
        }

        binding.btnAssignTenantQuick.setOnClickListener {
            val state = latestState ?: return@setOnClickListener

            findNavController().navigate(
                R.id.moveInFragment,
                bundleOf(
                    "presetRoomId" to state.roomId,
                    "presetBuildingId" to state.buildingId
                )
            )
        }

        binding.btnManageTenancy.setOnClickListener {
            if (moveOutInProgress) return@setOnClickListener

            val state = latestState ?: return@setOnClickListener
            showManageTenancyDialog(state)
        }

        // This button opens only the meter-reading screen.
        binding.btnRecordMeter.setOnClickListener {
            if (moveOutInProgress) return@setOnClickListener

            val state = latestState ?: return@setOnClickListener
            openMeterReadings(state)
        }

        // This button opens only the existing invoice-preview screen.
        binding.btnViewInvoice.setOnClickListener {
            if (moveOutInProgress) return@setOnClickListener

            val state = latestState ?: return@setOnClickListener
            openInvoicePreview(state)
        }
    }

    private fun observeRoom() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state != null) {
                        renderUi(state)
                    }
                }
            }
        }
    }

    private fun renderUi(state: RoomDetailUiState) {
        latestState = state

        renderHeader(state)
        renderOccupancy(state)
        renderReadings(state)
        renderInvoice(state)
        renderActions(state)
    }

    private fun renderHeader(state: RoomDetailUiState) {
        binding.tvHeaderRoomNumber.text = getString(
            R.string.rk_room_detail_room_number,
            state.roomNumber
        )

        binding.tvHeaderBuildingName.text = state.buildingName.ifBlank {
            getString(R.string.rk_room_detail_building_fallback)
        }

        binding.tvMonthlyRent.text = getString(
            R.string.rk_room_detail_monthly_rent,
            CurrencyFormatter.formatKhr(state.monthlyRent)
        )
    }

    private fun renderOccupancy(state: RoomDetailUiState) {
        val occupied = state.isOccupied

        binding.layoutOccupiedDetails.isVisible = occupied
        binding.layoutVacantDetails.isVisible = !occupied

        binding.tvOccupancyBadge.apply {
            setText(
                if (occupied) {
                    R.string.rk_room_detail_occupied
                } else {
                    R.string.rk_room_detail_vacant
                }
            )

            setBackgroundResource(
                if (occupied) {
                    R.drawable.bg_badge_paid_pill
                } else {
                    R.drawable.bg_badge_unpaid_pill
                }
            )

            backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (occupied) {
                    R.color.roomkit_primary
                } else {
                    R.color.roomkit_surface
                }
            )

            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (occupied) {
                        R.color.roomkit_on_primary
                    } else {
                        R.color.roomkit_text_secondary
                    }
                )
            )
        }

        binding.tvTenantName.text =
            state.tenantName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.rk_room_detail_tenant_fallback)

        binding.tvTenantPhone.text =
            state.tenantPhone?.takeIf { it.isNotBlank() }
                ?: getString(R.string.rk_room_detail_phone_missing)

        binding.tvMoveInDate.text = state.moveInDate?.let {
            getString(
                R.string.rk_room_detail_moved_in,
                formatDate(it)
            )
        } ?: getString(R.string.rk_room_detail_move_in_missing)

        binding.tvDepositValue.text = state.deposit?.let {
            CurrencyFormatter.formatKhr(it)
        } ?: getString(R.string.rk_room_detail_unavailable)
    }

    private fun renderReadings(state: RoomDetailUiState) {
        val electricityUnit = getString(
            R.string.rk_room_detail_unit_electricity
        )
        val waterUnit = getString(R.string.rk_room_detail_unit_water)

        binding.tvMeterReadingsSectionTitle.text = getString(
            R.string.rk_room_detail_readings_heading,
            formatPeriod(state.billingMonth, state.billingYear)
        )

        binding.tvElecPreviousVal.text = formatReading(
            state.electricityPrevious,
            electricityUnit
        )

        binding.tvElecCurrentVal.text = formatReading(
            state.electricityCurrent,
            electricityUnit
        )

        binding.tvElecUsageVal.text = formatReading(
            validUsage(
                state.electricityPrevious,
                state.electricityCurrent,
                state.electricityUsage
            ),
            electricityUnit
        )

        binding.tvWaterHeader.setText(R.string.rk_room_detail_water)
        binding.layoutFixedWaterRow.isVisible = state.waterIsFixed
        binding.layoutMeteredWaterGrid.isVisible = !state.waterIsFixed

        if (state.waterIsFixed) {
            binding.tvFixedWaterFee.text = formatKhr(state.waterCharge)
        } else {
            binding.tvWaterPreviousVal.text = formatReading(
                state.waterPrevious,
                waterUnit
            )

            binding.tvWaterCurrentVal.text = formatReading(
                state.waterCurrent,
                waterUnit
            )

            binding.tvWaterUsageVal.text = formatReading(
                validUsage(
                    state.waterPrevious,
                    state.waterCurrent,
                    state.waterUsage
                ),
                waterUnit
            )
        }
    }

    private fun renderInvoice(state: RoomDetailUiState) {
        val period = formatPeriod(state.billingMonth, state.billingYear)
        val hasInvoice = state.invoiceId != null

        binding.tvInvoiceSectionTitle.text = getString(
            R.string.rk_room_detail_invoice_heading,
            period
        )

        binding.tvInvoiceCardTitle.text = getString(
            R.string.rk_room_detail_invoice_title,
            period
        )

        binding.layoutInvoiceContent.isVisible = hasInvoice
        binding.layoutInvoiceEmpty.isVisible = !hasInvoice
        binding.tvInvoiceStatusBadge.isVisible = hasInvoice

        // Button visibility is managed independently in renderActions().
        if (!hasInvoice) {
            binding.tvEmptyInvoiceTitle.text =
                if (state.isOccupied) {
                    getString(
                        R.string.rk_room_detail_no_invoice,
                        period
                    )
                } else {
                    getString(R.string.rk_room_detail_no_tenancy_invoice)
                }
            return
        }

        binding.tvInvoiceRentVal.text = CurrencyFormatter.formatKhr(
            state.rentCharge ?: state.monthlyRent
        )

        binding.tvInvoiceElecVal.text = formatKhr(
            state.electricityCharge
        )

        binding.tvInvoiceElecSubtext.setText(
            R.string.rk_room_detail_usage_hint
        )

        binding.tvInvoiceWaterLabel.setText(
            if (state.waterIsFixed) {
                R.string.rk_room_detail_water_fixed
            } else {
                R.string.rk_room_detail_water_metered
            }
        )

        binding.tvInvoiceWaterVal.text = formatKhr(state.waterCharge)

        val rubbish = state.rubbishCharge
        binding.rowRubbish.isVisible = rubbish != null && rubbish > 0L
        binding.tvInvoiceRubbishVal.text = formatKhr(rubbish)

        // These saved snapshot values are not exposed by the current state.
        binding.tvInvoiceUtilitySubtotalKhr.setText(
            R.string.rk_room_detail_invoice_value_hint
        )



        binding.tvInvoiceTotalKhr.text =
            state.totalInvoiceAmount?.let {
                CurrencyFormatter.formatKhr(it)
            } ?: getString(R.string.rk_room_detail_unavailable)

        binding.tvInvoiceDueDate.text = state.invoiceDueDate?.let {
            getString(
                R.string.rk_room_detail_due,
                formatDate(it)
            )
        } ?: getString(R.string.rk_room_detail_due_missing)

        renderInvoiceStatus(state.invoiceStatus)
    }

    private fun renderActions(state: RoomDetailUiState) {
        val enabled = !moveOutInProgress

        // Available before an invoice is issued.
        // Missing readings do not redirect this button to Record Meter.
        binding.btnViewInvoice.apply {
            setText(R.string.rk_room_detail_view_invoice)
            isVisible = state.isOccupied
            isEnabled = enabled
        }

        binding.btnRecordMeter.apply {
            setText(R.string.rk_room_detail_record_meter)
            isVisible = state.isOccupied && state.invoiceId == null
            isEnabled = enabled
        }

        binding.btnManageTenancy.isVisible = state.isOccupied
        binding.btnManageTenancy.isEnabled = enabled
        binding.btnEditRoom.isEnabled = enabled
        binding.btnAssignTenantQuick.isEnabled = enabled
    }

    private fun openInvoicePreview(state: RoomDetailUiState) {
        val id = state.invoiceId ?: 0L
        if (id > 0L) {
            if (state.invoiceStatus?.trim()?.equals("DRAFT", true) == true) {
                findNavController().navigate(R.id.replacementInvoiceFragment,
                    androidx.core.os.bundleOf("draftInvoiceId" to id))
            } else {
                findNavController().navigate(R.id.invoiceDetailFragment,
                    androidx.core.os.bundleOf("invoiceId" to id))
            }
        } else {
            findNavController().navigate(R.id.invoicePreviewFragment,
                androidx.core.os.bundleOf("roomId" to state.roomId))
        }
    }

    private fun openMeterReadings(state: RoomDetailUiState) {
        findNavController().navigate(
            R.id.action_roomDetailFragment_to_meterReadingFragment,
            bundleOf(
                "roomId" to state.roomId,
                "preselectedBuildingId" to state.buildingId
            )
        )
    }

    private fun renderInvoiceStatus(status: String?) {
        val normalized = status?.uppercase(Locale.US)

        val labelResource: Int
        val backgroundResource: Int
        val colorResource: Int

        when (normalized) {
            "PAID" -> {
                labelResource = R.string.rk_room_detail_paid
                backgroundResource = R.drawable.bg_badge_paid_pill
                colorResource = R.color.roomkit_success_dark
            }

            "PARTIAL", "PARTIALLY_PAID" -> {
                labelResource = R.string.rk_room_detail_partial
                backgroundResource = R.drawable.bg_badge_partial_pill
                colorResource = R.color.roomkit_warning_dark
            }

            "OVERDUE" -> {
                labelResource = R.string.rk_room_detail_overdue
                backgroundResource = R.drawable.bg_badge_overdue_pill
                colorResource = R.color.roomkit_error_dark
            }

            else -> {
                labelResource = when (normalized) {
                    "DRAFT" -> R.string.rk_room_detail_draft
                    "ISSUED" -> R.string.rk_room_detail_issued
                    "VOID" -> R.string.rk_room_detail_void
                    "UNPAID" -> R.string.rk_room_detail_unpaid
                    else -> R.string.rk_room_detail_unknown
                }

                backgroundResource = R.drawable.bg_badge_unpaid_pill
                colorResource = R.color.roomkit_text_secondary
            }
        }

        binding.tvInvoiceStatusBadge.apply {
            setText(labelResource)
            setBackgroundResource(backgroundResource)
            setTextColor(
                ContextCompat.getColor(requireContext(), colorResource)
            )
        }
    }

    private fun showManageTenancyDialog(state: RoomDetailUiState) {
        if (tenancyDialog?.isShowing == true) return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rk_room_detail_manage)
            .setItems(
                arrayOf(
                    getString(R.string.rk_room_detail_profile),
                    getString(R.string.rk_room_detail_move_out)
                )
            ) { _, selected ->
                when (selected) {
                    0 -> openTenantProfile(state)
                    1 -> confirmMoveOut(state)
                }
            }
            .setNegativeButton(R.string.rk_room_detail_cancel, null)
            .create()

        tenancyDialog = dialog

        dialog.setOnDismissListener {
            if (tenancyDialog === dialog) {
                tenancyDialog = null
            }
        }

        dialog.show()
    }

    private fun openTenantProfile(state: RoomDetailUiState) {
        val tenantId = state.tenantId

        if (tenantId == null || tenantId <= 0L) {
            showMessage(
                getString(R.string.rk_room_detail_profile_missing)
            )
            return
        }

        findNavController().navigate(
            R.id.tenantDetailsFragment,
            bundleOf("tenantId" to tenantId)
        )
    }

    private fun confirmMoveOut(state: RoomDetailUiState) {
        val id = state.tenancyId ?: return
        findNavController().navigate(R.id.moveOutFragment, Bundle().apply { putLong("tenancyId", id) })
    }

    private fun displayLocale(): Locale =
        ConfigurationCompat.getLocales(resources.configuration)[0]
            ?: Locale.getDefault()

    private fun formatPeriod(month: Int, year: Int): String {
        if (month !in 1..12 || year <= 0) {
            return getString(R.string.rk_room_detail_selected_period)
        }

        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }

        val locale = displayLocale()
        val pattern = android.text.format.DateFormat
            .getBestDateTimePattern(locale, "MMMyyyy")

        return SimpleDateFormat(pattern, locale).format(calendar.time)
    }

    private fun validUsage(
        previous: Long?,
        current: Long?,
        storedUsage: Long?
    ): Long? {
        if (previous != null && current != null) {
            return if (previous >= 0L && current >= previous) {
                current - previous
            } else {
                null
            }
        }

        return storedUsage?.takeIf { it >= 0L }
    }

    private fun formatReading(value: Long?, unit: String): String =
        value?.let {
            getString(
                R.string.rk_room_detail_reading_value,
                NumberFormat.getIntegerInstance(displayLocale()).format(it),
                unit
            )
        } ?: getString(R.string.rk_room_detail_unavailable)

    private fun formatKhr(value: Long?): String =
        value?.let {
            getString(
                R.string.rk_room_detail_khr_value,
                NumberFormat.getIntegerInstance(displayLocale()).format(it)
            )
        } ?: getString(R.string.rk_room_detail_unavailable)

    private fun formatDate(timestamp: Long): String {
        val locale = displayLocale()
        val pattern = android.text.format.DateFormat
            .getBestDateTimePattern(locale, "yMMMd")

        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
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
        tenancyDialog?.dismiss()
        tenancyDialog = null
        latestState = null
        _binding = null
        super.onDestroyView()
    }
}
