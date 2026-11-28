@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Billing

import com.example.roomkit.Date.Ui.Common.setRoomKitTextSize

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Billing.InvoicePaperSize
import com.example.roomkit.Date.Billing.InvoicePrintAdapter
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.BottomSheetPaperSizeBinding
import com.example.roomkit.databinding.BottomSheetPdfExportedBinding
import com.example.roomkit.databinding.BottomSheetVoidPaymentBinding
import com.example.roomkit.databinding.DialogVoidInvoiceBinding
import com.example.roomkit.databinding.FragmentInvoiceDetailBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class InvoiceDetailFragment : Fragment() {

    private var _binding: FragmentInvoiceDetailBinding? = null
    private val binding: FragmentInvoiceDetailBinding
        get() = requireNotNull(_binding)

    private var voidDialog: android.app.Dialog? = null
    private var pdfExportDialog: BottomSheetDialog? = null
    private var paperDialog: BottomSheetDialog? = null
    private var exportedPaper = InvoicePaperSize.A4

    private val invoiceId: Long by lazy {
        arguments?.getLong("invoiceId") ?: 0L
    }

    private val viewModel: InvoiceDetailViewModel by viewModels {
        ViewModelFactory(requireContext(), targetId = invoiceId)
    }

    private lateinit var paymentAdapter: PaymentHistoryAdapter

    private val khrFormat = DecimalFormat("#,###")
    private var exportInProgress = false
    private var lastErrorMessage: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceDetailBinding.inflate(
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

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        if (invoiceId <= 0L) {
            showMessage(getString(R.string.invoice_detail_not_found))
            findNavController().popBackStack()
            return
        }

        setupPaymentHistory()
        setupListeners()
        observeState()
    }

    private fun setupHeader() {
        binding.tvInvoiceHeaderTitle.apply {
            setText(R.string.invoice_screen_title)
            visibility = View.VISIBLE
            alpha = 1f

            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.roomkit_text_primary
                )
            )
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

    private fun showVoidPaymentDialog(payment: PaymentEntity) {
        if (voidDialog?.isShowing == true) return

        val dateFormat = SimpleDateFormat(
            "dd MMM yyyy HH:mm",
            Locale.getDefault()
        )

        // Existing voided records and deposit allocations are read-only here.
        if (payment.status != "ACTIVE" || payment.paymentMethod == "DEPOSIT") {
            val message = when {
                payment.status == "VOIDED" -> {
                    val voidDate = payment.voidedAt?.let {
                        dateFormat.format(Date(it))
                    } ?: getString(R.string.pvu_date_unknown)

                    getString(
                        R.string.pvu_details,
                        payment.voidReason.orEmpty(),
                        voidDate
                    )
                }

                payment.paymentMethod == "DEPOSIT" ->
                    getString(R.string.pvu_protected)

                else ->
                    getString(R.string.pvu_inactive)
            }

            val historyDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pvu_history)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .create()

            voidDialog = historyDialog

            historyDialog.setOnDismissListener {
                if (voidDialog === historyDialog) {
                    voidDialog = null
                }
            }

            historyDialog.show()
            return
        }

        val sheet = BottomSheetVoidPaymentBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())

        dialog.setContentView(sheet.root)
        voidDialog = dialog

        sheet.tvVoidAmount.text = CurrencyFormatter.formatKhr(payment.amountKhr)

        sheet.tvVoidPaymentInfo.text = getString(
            R.string.pvu_payment_info,
            payment.paymentId,
            dateFormat.format(Date(payment.paymentDate))
        )

        var saving = false

        fun setSaving(value: Boolean) {
            saving = value

            sheet.btnConfirmVoid.isEnabled = !value
            sheet.btnCancelVoid.isEnabled = !value
            sheet.etVoidReason.isEnabled = !value

            sheet.btnConfirmVoid.setText(
                if (value) R.string.pvu_saving else R.string.pvu_confirm
            )

            dialog.setCancelable(!value)
            dialog.setCanceledOnTouchOutside(!value)
            dialog.behavior.isDraggable = !value
        }

        sheet.etVoidReason.doAfterTextChanged {
            sheet.tilVoidReason.error = null
            sheet.tvVoidError.isVisible = false
        }

        sheet.btnCancelVoid.setOnClickListener {
            if (!saving) {
                dialog.dismiss()
            }
        }

        sheet.btnConfirmVoid.setOnClickListener {
            if (saving) return@setOnClickListener

            val reason = sheet.etVoidReason.text
                ?.toString()
                ?.trim()
                .orEmpty()

            if (reason.isBlank()) {
                sheet.tilVoidReason.error =
                    getString(R.string.pvu_reason_required)

                sheet.etVoidReason.requestFocus()
                return@setOnClickListener
            }

            sheet.tvVoidError.isVisible = false
            setSaving(true)

            dialog.window?.let { window ->
                WindowInsetsControllerCompat(window, sheet.root)
                    .hide(WindowInsetsCompat.Type.ime())
            }

            viewModel.voidPayment(payment.paymentId, reason) { result ->
                // Do not update a sheet belonging to a destroyed Fragment view.
                if (_binding != null && voidDialog === dialog) {
                    result.onSuccess {
                        dialog.dismiss()

                        Toast.makeText(
                            requireContext(),
                            R.string.pvu_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        setSaving(false)

                        sheet.tvVoidError.text =
                            error.message ?: getString(R.string.pvu_failed)

                        sheet.tvVoidError.isVisible = true
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            if (voidDialog === dialog) {
                voidDialog = null
            }
        }

        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.setBackgroundColor(Color.TRANSPARENT)

            dialog.behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }

            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        dialog.show()
    }

    private fun setupPaymentHistory() {
        paymentAdapter = PaymentHistoryAdapter { payment -> showVoidPaymentDialog(payment) }

        binding.rvPaymentHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = paymentAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnRecordPayment.setOnClickListener {
            val state = payableState(includePrevious = true) ?: return@setOnClickListener

            findNavController().navigate(
                R.id.action_invoiceDetailFragment_to_recordPaymentBottomSheet,
                bundleOf("invoiceId" to state.invoiceId)
            )
        }

        binding.btnGenerateKhqr.setOnClickListener {
            val state = payableState() ?: return@setOnClickListener

            findNavController().navigate(
                R.id.paymentQrFragment,
                bundleOf(
                    "invoiceId" to state.invoiceId,
                    "amountKhr" to state.balanceKhr
                )
            )
        }

        binding.btnVoidInvoice.setOnClickListener {
            showVoidInvoiceDialog()
        }

        binding.btnExportPdf.setOnClickListener {
            exportPdf(shareAfterExport = false)
        }

        binding.btnSendTelegram.setOnClickListener {
            exportPdf(shareAfterExport = true)
        }
    }

    private fun payableState(includePrevious: Boolean = false): InvoiceDetailUiState? {
        val state = viewModel.uiState.value

        if (state.invoiceId <= 0L || state.errorMessage != null) {
            showMessage(getString(R.string.invoice_detail_not_ready))
            return null
        }

        if (state.status.trim().uppercase(Locale.ROOT) !in setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE")) {
            showMessage(getString(R.string.invoice_detail_void_payment))
            return null
        }

        if (
            (if (includePrevious) state.collectibleBalanceKhr else state.balanceKhr) <= 0L
        ) {
            showMessage(getString(R.string.invoice_detail_already_paid))
            return null
        }

        return state
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: InvoiceDetailUiState) {
        val showOverdueBadge = !state.isLoading &&
                state.errorMessage == null &&
                state.invoiceId > 0L &&
                state.isOverdue
        binding.tvInvoiceOverdueBadge.isVisible = showOverdueBadge
        if (showOverdueBadge) {
            styleStatusBadge(
                view = binding.tvInvoiceOverdueBadge,
                color = ContextCompat.getColor(requireContext(), R.color.roomkit_error_dark),
                backgroundRes = R.drawable.bg_rd_badge_red
            )
        }

        renderVoidHistory(state)
        renderReplacementAction(state)

        binding.btnVoidInvoice.isVisible =
            state.invoiceId > 0L &&
                    state.errorMessage == null &&
                    state.amountPaidKhr == 0L &&
                    state.payments.none {
                        it.status.equals("ACTIVE", ignoreCase = true)
                    } &&
                    state.status.uppercase(Locale.ROOT) in setOf(
                "ISSUED",
                "UNPAID",
                "OVERDUE"
            )
        updateInvoiceManagementVisibility()
        val error = state.errorMessage

        if (error != null) {
            binding.containerPayableActions.isVisible = false
            binding.btnExportPdf.isEnabled = false
            binding.btnSendTelegram.isEnabled = false

            if (error != lastErrorMessage) {
                showMessage(error)
            }

            lastErrorMessage = error
            return
        }

        lastErrorMessage = null

        val ready = state.invoiceId > 0L

        binding.btnExportPdf.isEnabled = ready && !exportInProgress
        binding.btnSendTelegram.isEnabled = ready && !exportInProgress

        if (!ready) {
            binding.containerPayableActions.isVisible = false
            return
        }

        binding.tvHeaderBillingPeriod.text = state.billingPeriodFormatted

        // Normalize the prefix to prevent "Invoice ##0001".
        val invoiceNumber = state.invoiceNumberFormatted
            .trim()
            .trimStart('#')

        binding.tvInvoiceNumber.text = getString(
            R.string.invoice_detail_number,
            invoiceNumber
        )

        binding.tvInvoiceDueDateHeader.text = getString(
            R.string.invoice_detail_due,
            state.dueDateFormatted
        )

        binding.tvTenantName.text = state.tenantName
        binding.tvTenantPhone.text = state.tenantPhone

        binding.tvRoomNumber.text = getString(
            R.string.invoice_detail_room,
            state.roomNumber
        )

        binding.tvBuildingName.text = state.buildingName

        renderCharges(state)
        renderPaymentSummary(state)
        renderStatus(state)

        paymentAdapter.submitList(state.payments)
        binding.tvEmptyPaymentHistory.isVisible = state.payments.isEmpty()
    }

    private fun renderVoidHistory(state: InvoiceDetailUiState) {
        val show = !state.isLoading && state.errorMessage == null &&
                state.invoiceId > 0L &&
                state.status.trim().uppercase(Locale.ROOT) in setOf("VOID", "VOIDED")
        binding.cardInvoiceVoidHistory.isVisible = show
        if (!show) {
            binding.tvInvoiceVoidReason.text = ""
            binding.tvInvoiceVoidedAt.text = ""
            return
        }
        binding.tvInvoiceVoidReason.text = state.voidReason
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.invoice_void_history_reason_missing)
        binding.tvInvoiceVoidedAt.text = state.voidedAt?.let { timestamp ->
            val date = Date(timestamp)
            val dateText = android.text.format.DateFormat.getMediumDateFormat(requireContext()).format(date)
            val timeText = android.text.format.DateFormat.getTimeFormat(requireContext()).format(date)
            getString(R.string.invoice_void_history_datetime, dateText, timeText)
        } ?: getString(R.string.invoice_void_history_date_missing)
    }

    private fun renderCharges(state: InvoiceDetailUiState) {
        val items = state.items.associateBy { it.itemCode }
        binding.tvRentLabel.text = items.getValue("RENT").description
        binding.tvElectricityLabel.text = items.getValue("ELECTRICITY").description
        binding.tvRubbishLabel.text = items.getValue("RUBBISH").description
        binding.tvOtherChargesLabel.text = items.getValue("OTHER").description
        binding.tvDiscountLabel.text = items.getValue("DISCOUNT").description
        binding.rowOtherCharges.isVisible = state.otherChargesKhr != 0L
        binding.tvOtherChargesAmount.text = CurrencyFormatter.formatKhr(state.otherChargesKhr)
        binding.rowDiscount.isVisible = state.discountKhr != 0L
        binding.tvDiscountAmount.text = getString(R.string.bd_discount_amount, CurrencyFormatter.formatKhr(state.discountKhr))
        binding.tvInvoicePeriodDates.isVisible = state.billingPeriodDates != null
        binding.tvInvoicePeriodDates.text = state.billingPeriodDates?.let { getString(R.string.bd_period, it) }.orEmpty()
        binding.tvRentAmount.text = CurrencyFormatter.formatKhr(
            state.rentKhr
        )

        binding.tvElectricitySubtext.isVisible = items.getValue("ELECTRICITY").quantity != null &&
                items.getValue("ELECTRICITY").unitPriceKhr != null
        binding.tvElectricitySubtext.text = getString(
            R.string.invoice_detail_electricity_usage,
            khrFormat.format(state.electricityUsage),
            khrFormat.format(state.electricityRateKhr)
        )

        binding.tvElectricityAmount.text = CurrencyFormatter.formatKhr(
            state.electricityChargeKhr
        )

        binding.tvWaterLabel.text =
            if (state.isWaterMetered && items.getValue("WATER").quantity != null &&
                items.getValue("WATER").unitPriceKhr != null
            ) {
                getString(
                    R.string.invoice_detail_water_metered,
                    khrFormat.format(state.waterUsage),
                    khrFormat.format(state.waterRateKhr)
                )
            } else {
                items.getValue("WATER").description
            }

        binding.tvWaterAmount.text = CurrencyFormatter.formatKhr(
            state.waterChargeKhr
        )

        binding.rowRubbish.isVisible = state.rubbishFeeKhr > 0L
        binding.tvRubbishAmount.text = CurrencyFormatter.formatKhr(
            state.rubbishFeeKhr
        )

        binding.tvUtilitySubtotalKhr.text = CurrencyFormatter.formatKhr(
            state.utilitySubtotalKhr
        )

        binding.tvInvoiceTotalKhr.text = CurrencyFormatter.formatKhr(
            state.totalKhr
        )
    }

    private fun renderPaymentSummary(state: InvoiceDetailUiState) {
        binding.tvInvoiceCurrency.text = getString(R.string.if_currency_value, state.currency)
        binding.tvInvoiceDiscountReason.text = state.discountReason?.let { getString(R.string.if_discount_reason_value, it) }
        binding.tvInvoiceDiscountReason.isVisible = !state.discountReason.isNullOrBlank()
        binding.tvPreviousUnpaidBalance.text = CurrencyFormatter.formatKhr(state.previousUnpaidKhr)
        binding.tvAccountCurrentBalance.text = CurrencyFormatter.formatKhr(state.balanceKhr)
        binding.tvLaterUnpaidBalance.text = CurrencyFormatter.formatKhr(state.laterUnpaidKhr)
        binding.layoutLaterUnpaidBalance.isVisible = state.laterUnpaidKhr > 0L
        binding.tvTotalAccountBalance.text = CurrencyFormatter.formatKhr(state.totalAccountBalanceKhr)
        binding.tvSummaryInvoiceTotal.text = CurrencyFormatter.formatKhr(
            state.totalKhr
        )

        binding.tvSummaryTotalPaid.text = CurrencyFormatter.formatKhr(
            state.amountPaidKhr
        )

        binding.tvSummaryRemainingBalance.text = CurrencyFormatter.formatKhr(
            state.collectibleBalanceKhr
        )
        binding.tvSummaryRemainingBalance.contentDescription =
            "Total to pay including previous unpaid: ${CurrencyFormatter.formatKhr(state.collectibleBalanceKhr)}"
        binding.btnRecordPayment.text = "Record payment · includes previous unpaid"

        val color = when {
            state.collectibleBalanceKhr <= 0L ->
                R.color.roomkit_text_secondary

            state.isOverdue ->
                R.color.roomkit_error_dark

            else ->
                R.color.roomkit_warning_dark
        }

        binding.tvSummaryRemainingBalance.setTextColor(
            ContextCompat.getColor(requireContext(), color)
        )
    }

    private fun renderStatus(state: InvoiceDetailUiState) {
        val status = state.status.uppercase(Locale.US)

        val label: Int
        val background: Int
        val colorValue: Int

        when (status) {
            "PAID" -> {
                label = R.string.invoice_detail_paid
                background = R.drawable.bg_rd_badge_green
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_success_dark)
            }

            "PARTIAL", "PARTIALLY_PAID" -> {
                label = R.string.invoice_detail_partial
                background = R.drawable.bg_rd_badge_blue
                colorValue = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.roomkit_blue_700)
            }

            "OVERDUE" -> {
                label = R.string.invoice_detail_overdue
                background = R.drawable.bg_rd_badge_red
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_error_dark)
            }

            "VOID" -> {
                label = R.string.invoice_detail_void
                background = R.drawable.bg_rd_badge_neutral
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_text_muted)
            }

            "DRAFT" -> {
                label = R.string.invoice_detail_draft
                background = R.drawable.bg_rd_badge_neutral
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_text_muted)
            }

            else -> {
                label = R.string.invoice_detail_unpaid
                background = R.drawable.bg_rd_badge_amber
                colorValue = ContextCompat.getColor(requireContext(), R.color.roomkit_warning_dark)
            }
        }

        binding.tvInvoiceStatusBadge.setText(label)
        styleStatusBadge(
            view = binding.tvInvoiceStatusBadge,
            color = colorValue,
            backgroundRes = background
        )

        binding.containerPayableActions.isVisible =
            state.collectibleBalanceKhr > 0L &&
                    status in setOf(
                "ISSUED",
                "UNPAID",
                "PARTIAL",
                "PARTIALLY_PAID",
                "OVERDUE", "PAID"
            )
        binding.btnGenerateKhqr.text = "KHQR · this invoice only"
        // The imported static KHQR screen still explicitly covers the current invoice.
        binding.btnGenerateKhqr.isVisible = state.balanceKhr > 0L &&
                status in setOf("ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "OVERDUE")
    }

    private fun exportPdf(shareAfterExport: Boolean) {
        if (exportInProgress || paperDialog?.isShowing == true) return
        if (_binding == null || !isAdded || parentFragmentManager.isStateSaved) return

        val state = viewModel.uiState.value
        if (state.isLoading || state.invoiceId <= 0L || state.errorMessage != null) {
            showMessage(getString(R.string.invoice_detail_not_ready))
            return
        }

        val sheet = BottomSheetPaperSizeBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheet.root)
        dialog.setCanceledOnTouchOutside(true)

        sheet.tvPaperSizeContext.text =
            "Room ${state.roomNumber} · ${state.billingPeriodFormatted}"

        var selected = InvoicePaperSize.selected(requireContext())
        fun updateSelection(choice: InvoicePaperSize) {
            selected = choice
            sheet.ivCheckA4.visibility =
                if (choice == InvoicePaperSize.A4) View.VISIBLE else View.INVISIBLE
            sheet.ivCheckA5.visibility =
                if (choice == InvoicePaperSize.A5) View.VISIBLE else View.INVISIBLE
        }
        updateSelection(selected)

        sheet.optionA4.setOnClickListener { updateSelection(InvoicePaperSize.A4) }
        sheet.optionA5.setOnClickListener { updateSelection(InvoicePaperSize.A5) }
        sheet.btnCancelPaperSize.setOnClickListener { dialog.dismiss() }

        sheet.btnConfirmPaperSize.setOnClickListener {
            if (!dialog.isShowing) return@setOnClickListener
            selected.remember(requireContext())
            exportedPaper = selected
            dialog.dismiss()
            if (isAdded && _binding != null) {
                createPdf(shareAfterExport)
            }
        }

        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.background = ColorDrawable(Color.TRANSPARENT)

            dialog.behavior.apply {
                isFitToContents = true
                skipCollapsed = true
                this.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.setOnDismissListener {
            if (paperDialog === dialog) {
                paperDialog = null
            }
        }

        paperDialog = dialog
        dialog.show()
    }

    private fun createPdf(shareAfterExport: Boolean) {
        if (exportInProgress) return

        val state = viewModel.uiState.value

        if (state.invoiceId <= 0L || state.errorMessage != null) {
            showMessage(getString(R.string.invoice_detail_not_ready))
            return
        }

        val viewBinding = binding

        exportInProgress = true
        viewBinding.btnExportPdf.isEnabled = false
        viewBinding.btnSendTelegram.isEnabled = false

        showMessage(getString(R.string.invoice_detail_preparing_pdf))

        viewModel.exportPdf { result ->
            exportInProgress = false

            // The callback may arrive after the user leaves this screen.
            if (!isAdded || _binding !== viewBinding) {
                return@exportPdf
            }

            renderUi(viewModel.uiState.value)

            if (
                !viewLifecycleOwner.lifecycle.currentState
                    .isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return@exportPdf
            }

            result.onSuccess { uri ->
                if (shareAfterExport) {
                    sharePdf(uri)
                } else {
                    showExportSuccess(uri)
                }
            }.onFailure { error ->
                showMessage(
                    getString(
                        R.string.invoice_detail_export_error,
                        error.localizedMessage
                            ?: getString(R.string.invoice_detail_unknown_error)
                    )
                )
            }
        }
    }

    private fun showExportSuccess(uri: Uri) {
        if (_binding == null || !isAdded) return
        if (parentFragmentManager.isStateSaved) return

        pdfExportDialog?.dismiss()

        val invoiceState = viewModel.uiState.value

        val documentName = exportedDocumentName(uri)

        val sheetBinding = BottomSheetPdfExportedBinding.inflate(
            layoutInflater
        )

        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)
        dialog.setCanceledOnTouchOutside(true)

        sheetBinding.tvInvoiceContext.text =
            "${if (documentName.contains("_Receipt_")) "Receipt" else "Invoice"} · ${exportedPaper.name}\n" +
                    "Room ${invoiceState.roomNumber} · ${invoiceState.billingPeriodFormatted}"

        var actionHandled = false

        sheetBinding.btnOpenPdf.setOnClickListener {
            if (actionHandled || !dialog.isShowing) {
                return@setOnClickListener
            }

            actionHandled = true
            dialog.dismiss()

            if (isAdded && _binding != null) {
                openPdf(uri)
            }
        }

        sheetBinding.btnPrintPdf.setOnClickListener {
            if (actionHandled || !dialog.isShowing) {
                return@setOnClickListener
            }

            actionHandled = true
            dialog.dismiss()

            if (isAdded && _binding != null) {
                printPdf(uri, documentName)
            }
        }

        sheetBinding.btnDone.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )?.background = ColorDrawable(Color.TRANSPARENT)

            dialog.behavior.apply {
                isFitToContents = true
                skipCollapsed = true
                this.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.setOnDismissListener {
            if (pdfExportDialog === dialog) {
                pdfExportDialog = null
            }
        }

        pdfExportDialog = dialog
        dialog.show()
    }

    private fun openPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(getString(R.string.invoice_detail_no_pdf_app))
        }
    }

    private fun printPdf(uri: Uri, documentName: String) {
        val printManager = requireContext()
            .getSystemService(Context.PRINT_SERVICE) as? PrintManager

        if (printManager == null) {
            showMessage(getString(R.string.invoice_detail_no_print_service))
            return
        }

        val adapter = InvoicePrintAdapter(
            requireContext(),
            uri,
            documentName
        )

        val media = if (exportedPaper == InvoicePaperSize.A5)
            PrintAttributes.MediaSize.ISO_A5 else PrintAttributes.MediaSize.ISO_A4
        printManager.print(
            documentName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(media)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
    }

    private fun sharePdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"

            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, exportedDocumentName(uri))

            clipData = ClipData.newRawUri(
                getString(R.string.invoice_detail_pdf_label),
                uri
            )

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(
                Intent.createChooser(
                    intent,
                    getString(R.string.invoice_detail_share_title)
                )
            )
        } catch (_: ActivityNotFoundException) {
            showMessage(getString(R.string.invoice_detail_no_share_app))
        }
    }

    private fun exportedDocumentName(uri: Uri): String {
        return runCatching {
            requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: "RoomKit document"
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
        paperDialog?.dismiss()
        paperDialog = null
        pdfExportDialog?.dismiss()
        pdfExportDialog = null

        voidDialog?.dismiss()
        voidDialog = null

        _binding?.rvPaymentHistory?.adapter = null
        _binding = null

        super.onDestroyView()
    }

    private fun showVoidInvoiceDialog() {
        if (voidDialog?.isShowing == true) return
        val state = viewModel.uiState.value
        if (state.invoiceId <= 0L || state.errorMessage != null || state.isLoading) return

        val content = DialogVoidInvoiceBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(content.root)
            .setBackground(GradientDrawable().apply {
                setColor(ContextCompat.getColor(requireContext(), R.color.roomkit_white))
                cornerRadius = 24f * resources.displayMetrics.density
            })
            .create()
        voidDialog = dialog
        var saving = false

        fun updateConfirm() {
            content.btnVoidInvoice.isEnabled = !saving && !content.etVoidReason.text.isNullOrBlank()
        }

        fun setSaving(value: Boolean) {
            saving = value
            content.btnKeepInvoice.isEnabled = !value
            content.etVoidReason.isEnabled = !value
            content.btnVoidInvoice.setText(if (value) R.string.rvi_saving else R.string.rvi_confirm)
            dialog.setCancelable(!value)
            dialog.setCanceledOnTouchOutside(!value)
            updateConfirm()
        }

        content.etVoidReason.doAfterTextChanged {
            content.tilVoidReason.error = null
            content.tvVoidError.isVisible = false
            updateConfirm()
        }
        content.btnKeepInvoice.setOnClickListener { if (!saving) dialog.dismiss() }
        content.btnVoidInvoice.setOnClickListener {
            if (saving) return@setOnClickListener
            val reason = content.etVoidReason.text?.toString()?.trim().orEmpty()
            if (reason.isBlank()) {
                content.tilVoidReason.error = getString(R.string.iv_reason_required)
                content.etVoidReason.requestFocus()
                return@setOnClickListener
            }
            content.tvVoidError.isVisible = false
            setSaving(true)
            viewModel.voidInvoice(invoiceId = state.invoiceId, reason = reason) { result ->
                if (_binding != null && voidDialog === dialog) {
                    result.onSuccess {
                        dialog.dismiss()
                        showMessage(getString(R.string.iv_success))
                    }.onFailure { error ->
                        setSaving(false)
                        content.tvVoidError.text = error.message ?: getString(R.string.iv_failed)
                        content.tvVoidError.isVisible = true
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            if (voidDialog === dialog) voidDialog = null
        }
        updateConfirm()
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun updateInvoiceManagementVisibility() {
        val showOriginal = binding.btnOriginalInvoice.isVisible
        val showVoid = binding.btnVoidInvoice.isVisible

        binding.cardInvoiceManagement.isVisible = showOriginal || showVoid
        binding.dividerInvoiceManagement.isVisible = showOriginal && showVoid
    }

    private fun renderReplacementAction(state: InvoiceDetailUiState) {
        val status = state.status.trim().uppercase(Locale.ROOT)
        val ready = !state.isLoading && state.errorMessage == null && state.invoiceId > 0L
        binding.btnReplacementAction.isVisible = ready && status in setOf("VOID", "VOIDED", "DRAFT")
        binding.btnReplacementAction.setText(
            if (status == "DRAFT") R.string.ia_edit else R.string.ia_replacement
        )
        binding.btnReplacementAction.setOnClickListener {
            val latest = viewModel.uiState.value
            val currentStatus = latest.status.trim().uppercase(Locale.ROOT)
            if (!latest.isLoading && latest.invoiceId > 0L && latest.errorMessage == null &&
                currentStatus in setOf("DRAFT", "VOID", "VOIDED")
            ) {
                val draft = currentStatus == "DRAFT"
                findNavController().navigate(
                    R.id.replacementInvoiceFragment,
                    bundleOf(
                        "originalInvoiceId" to if (draft) 0L else latest.invoiceId,
                        "draftInvoiceId" to if (draft) latest.invoiceId else 0L
                    )
                )
            }
        }
        val originalId = state.replacesInvoiceId
        binding.btnOriginalInvoice.isVisible = ready && originalId != null
        if (originalId != null) {
            binding.btnOriginalInvoice.text = getString(R.string.ia_original, originalId)
        }
        binding.btnOriginalInvoice.setOnClickListener {
            viewModel.uiState.value.replacesInvoiceId?.let { id ->
                findNavController().navigate(R.id.invoiceDetailFragment, bundleOf("invoiceId" to id))
            }
        }
    }
}
