@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Room

import com.example.roomkit.Date.Ui.Common.setRoomKitTextSize

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
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
import com.example.roomkit.databinding.BottomSheetManageTenancyBinding
import com.example.roomkit.databinding.FragmentRoomDetailBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class RoomDetailFragment : Fragment() {

    private var _binding: FragmentRoomDetailBinding? = null
    private val binding get() = _binding!!

    private var pdfExportDialog: BottomSheetDialog? = null

    private val roomId: Long by lazy {
        arguments?.getLong("roomId") ?: -1L
    }

    private val viewModel: RoomDetailViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = roomId)
    }

    private var latestState: RoomDetailUiState? = null
    private var tenancyDialog: BottomSheetDialog? = null
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

    private fun styleStatusBadge(
        view: TextView,
        color: Int,
        @DrawableRes backgroundRes: Int
    ) {
        val density = resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (5 * density).roundToInt()

        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.setRoomKitTextSize(R.dimen.roomkit_text_12)
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setTextColor(color)
        view.backgroundTintList = null
        view.setBackgroundResource(backgroundRes)
        view.setPadding(padH, padV, padH, padV)
        view.compoundDrawablePadding = (6 * density).roundToInt()
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
            view,
            R.drawable.ic_rd_status_dot,
            0,
            0,
            0
        )
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color))
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

        binding.tvOccupancyBadge.setText(
            if (occupied) {
                R.string.rk_room_detail_occupied
            } else {
                R.string.rk_room_detail_vacant
            }
        )
        styleStatusBadge(
            view = binding.tvOccupancyBadge,
            color = ContextCompat.getColor(
                requireContext(),
                if (occupied) {
                    R.color.roomkit_success_dark
                } else {
                    R.color.roomkit_text_secondary
                }
            ),
            backgroundRes = if (occupied) {
                R.drawable.bg_rd_badge_green
            } else {
                R.drawable.bg_rd_badge_neutral
            }
        )

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
                findNavController().navigate(
                    R.id.replacementInvoiceFragment,
                    bundleOf("draftInvoiceId" to id)
                )
            } else {
                findNavController().navigate(
                    R.id.invoiceDetailFragment,
                    bundleOf("invoiceId" to id)
                )
            }
        } else {
            findNavController().navigate(
                R.id.invoicePreviewFragment,
                bundleOf("roomId" to state.roomId)
            )
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
        val colorValue: Int

        when (normalized) {
            "PAID" -> {
                labelResource = R.string.rk_room_detail_paid
                backgroundResource = R.drawable.bg_rd_badge_green
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_success_dark)
            }

            "PARTIAL", "PARTIALLY_PAID" -> {
                labelResource = R.string.rk_room_detail_partial
                backgroundResource = R.drawable.bg_rd_badge_blue
                colorValue = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.roomkit_blue_700)
            }

            "OVERDUE" -> {
                labelResource = R.string.rk_room_detail_overdue
                backgroundResource = R.drawable.bg_rd_badge_red
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_error_dark)
            }

            "UNPAID", "ISSUED" -> {
                labelResource = if (normalized == "ISSUED") {
                    R.string.rk_room_detail_issued
                } else {
                    R.string.rk_room_detail_unpaid
                }
                backgroundResource = R.drawable.bg_rd_badge_amber
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_warning_dark)
            }

            else -> {
                labelResource = when (normalized) {
                    "DRAFT" -> R.string.rk_room_detail_draft
                    "VOID" -> R.string.rk_room_detail_void
                    else -> R.string.rk_room_detail_unknown
                }
                backgroundResource = R.drawable.bg_rd_badge_neutral
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_text_secondary)
            }
        }

        binding.tvInvoiceStatusBadge.setText(labelResource)
        styleStatusBadge(
            view = binding.tvInvoiceStatusBadge,
            color = colorValue,
            backgroundRes = backgroundResource
        )
    }

    private fun showManageTenancyDialog(state: RoomDetailUiState) {
        if (tenancyDialog?.isShowing == true) return
        if (childFragmentManager.isStateSaved) return

        val sheetBinding = BottomSheetManageTenancyBinding.inflate(
            layoutInflater
        )

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        dialog.setCanceledOnTouchOutside(true)

        sheetBinding.tvTenancyContext.text = getString(
            R.string.rk_manage_tenancy_context,
            state.roomNumber,
            state.tenantName
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.rk_room_detail_tenant_fallback)
        )

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Use the latest state so the actions target the current tenancy.
        sheetBinding.actionViewProfile.setOnClickListener {
            if (!dialog.isShowing) return@setOnClickListener

            val currentState = latestState ?: state
            dialog.dismiss()

            if (_binding != null && !parentFragmentManager.isStateSaved) {
                openTenantProfile(currentState)
            }
        }

        sheetBinding.actionMoveOut.setOnClickListener {
            if (!dialog.isShowing || moveOutInProgress) {
                return@setOnClickListener
            }

            val currentState = latestState ?: state
            dialog.dismiss()

            if (
                _binding != null &&
                !parentFragmentManager.isStateSaved &&
                currentState.isOccupied
            ) {
                confirmMoveOut(currentState)
            }
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )

            // Let our rounded layout provide the visible surface.
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)

            dialog.behavior.apply {
                isFitToContents = true
                skipCollapsed = true
                this.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.setOnDismissListener {
            if (tenancyDialog === dialog) {
                tenancyDialog = null
            }
        }

        tenancyDialog = dialog
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
        // Repository usage includes consumption carried from replaced meters.
        if (storedUsage != null) return storedUsage.takeIf { it >= 0L }

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