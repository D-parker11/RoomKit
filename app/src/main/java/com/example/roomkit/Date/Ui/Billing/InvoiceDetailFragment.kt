@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Billing.InvoicePrintAdapter
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentInvoiceDetailBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.roomkit.databinding.BottomSheetVoidPaymentBinding

class InvoiceDetailFragment : Fragment() {

    private var _binding: FragmentInvoiceDetailBinding? = null
    private val binding get() = _binding!!
    private var voidDialog: android.app.Dialog? = null
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
            val state = payableState() ?: return@setOnClickListener

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
            if (exportInProgress) return@setOnClickListener

            val uri = viewModel.lastExportedUri.value

            if (uri != null) {
                sharePdf(uri)
            } else {
                exportPdf(shareAfterExport = true)
            }
        }
    }

    private fun payableState(): InvoiceDetailUiState? {
        val state = viewModel.uiState.value

        if (state.invoiceId <= 0L || state.errorMessage != null) {
            showMessage(getString(R.string.invoice_detail_not_ready))
            return null
        }

        if (state.status.uppercase(Locale.US) == "VOID") {
            showMessage(getString(R.string.invoice_detail_void_payment))
            return null
        }

        if (
            state.status.uppercase(Locale.US) == "PAID" ||
            state.balanceKhr <= 0L
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
        binding.tvInvoiceOverdueBadge.isVisible = !state.isLoading &&
                state.errorMessage == null && state.invoiceId > 0L && state.isOverdue
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
            // Use the saved timestamp in milliseconds, never the current time.
            // Display in the device's local time zone and date/time preferences.
            val date = Date(timestamp)
            val dateText = android.text.format.DateFormat.getMediumDateFormat(requireContext()).format(date)
            val timeText = android.text.format.DateFormat.getTimeFormat(requireContext()).format(date)
            getString(R.string.invoice_void_history_datetime, dateText, timeText)
        } ?: getString(R.string.invoice_void_history_date_missing)
    }

    private fun renderCharges(state: InvoiceDetailUiState) {
        val items = state.items.associateBy { it.itemCode }
        // The ViewModel verifies all six records before exposing a ready state.
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
                items.getValue("WATER").unitPriceKhr != null) {
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
            state.balanceKhr
        )

        val color = when {
            state.balanceKhr <= 0L ->
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
        val color: Int

        when (status) {
            "PAID" -> {
                label = R.string.invoice_detail_paid
                background = R.drawable.bg_badge_paid_pill
                color = R.color.roomkit_success_dark
            }

            "PARTIAL", "PARTIALLY_PAID" -> {
                label = R.string.invoice_detail_partial
                background = R.drawable.bg_badge_partial_pill
                color = R.color.roomkit_warning_dark
            }

            "OVERDUE" -> {
                label = R.string.invoice_detail_overdue
                background = R.drawable.bg_badge_overdue_pill
                color = R.color.roomkit_error_dark
            }

            "VOID" -> {
                label = R.string.invoice_detail_void
                background = R.drawable.bg_badge_draft_pill
                color = R.color.roomkit_text_muted
            }

            "DRAFT" -> {
                label = R.string.invoice_detail_draft
                background = R.drawable.bg_badge_draft_pill
                color = R.color.roomkit_text_muted
            }

            else -> {
                label = R.string.invoice_detail_unpaid
                background = R.drawable.bg_badge_unpaid_pill
                color = R.color.roomkit_warning_dark
            }
        }

        binding.tvInvoiceStatusBadge.apply {
            setText(label)
            setBackgroundResource(background)

            setTextColor(
                ContextCompat.getColor(requireContext(), color)
            )
        }

        binding.containerPayableActions.isVisible =
            state.balanceKhr > 0L &&
                    status in setOf(
                "ISSUED",
                "UNPAID",
                "PARTIAL",
                "PARTIALLY_PAID",
                "OVERDUE"
            )
    }

    private fun exportPdf(shareAfterExport: Boolean) {
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
        val state = viewModel.uiState.value
        val documentName =
            "RoomKit_Invoice_${state.roomNumber}_${state.billingPeriodFormatted}"

        Snackbar.make(
            binding.root,
            R.string.invoice_detail_pdf_saved,
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.invoice_detail_open) {
                openPdf(uri)
            }
            .show()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.invoice_detail_pdf_exported)
            .setMessage(R.string.invoice_detail_pdf_export_message)
            .setPositiveButton(R.string.invoice_detail_print) { _, _ ->
                printPdf(uri, documentName)
            }
            .setNeutralButton(R.string.invoice_detail_open_pdf) { _, _ ->
                openPdf(uri)
            }
            .setNegativeButton(R.string.invoice_detail_done, null)
            .show()
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

        printManager.print(documentName, adapter, null)
    }

    private fun sharePdf(uri: Uri) {
        val state = viewModel.uiState.value
        val invoiceNumber = state.invoiceNumberFormatted
            .trim()
            .trimStart('#')

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"

            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(
                    R.string.invoice_detail_share_subject,
                    invoiceNumber,
                    state.roomNumber
                )
            )

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

    private fun showMessage(message: String) {
        val currentContext = context ?: return

        Toast.makeText(
            currentContext,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        voidDialog?.dismiss()
        voidDialog = null
        binding.rvPaymentHistory.adapter = null
        _binding = null
        super.onDestroyView()
    }
    private fun showVoidInvoiceDialog() {
        if (voidDialog?.isShowing == true) return

        val state = viewModel.uiState.value
        if (state.invoiceId <= 0L || state.errorMessage != null) return

        val reasonInput =
            com.google.android.material.textfield.TextInputEditText(
                requireContext()
            ).apply {
                hint = getString(R.string.iv_reason_hint)
                minLines = 2
                maxLines = 5
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }

        val padding = (24 * resources.displayMetrics.density).toInt()

        val inputContainer = android.widget.LinearLayout(
            requireContext()
        ).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(
                reasonInput,
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.iv_title)
            .setMessage(R.string.iv_explanation)
            .setView(inputContainer)
            .setNegativeButton(R.string.iv_keep, null)
            .setPositiveButton(R.string.iv_action, null)
            .create()

        voidDialog = dialog

        dialog.setOnDismissListener {
            if (voidDialog === dialog) {
                voidDialog = null
            }
        }

        dialog.setOnShowListener {
            val confirm = dialog.getButton(
                android.content.DialogInterface.BUTTON_POSITIVE
            )
            val cancel = dialog.getButton(
                android.content.DialogInterface.BUTTON_NEGATIVE
            )

            confirm.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.roomkit_error_dark
                )
            )

            confirm.setOnClickListener {
                val reason = reasonInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (reason.isBlank()) {
                    reasonInput.error =
                        getString(R.string.iv_reason_required)
                    reasonInput.requestFocus()
                    return@setOnClickListener
                }

                confirm.isEnabled = false
                cancel.isEnabled = false
                reasonInput.isEnabled = false
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)

                viewModel.voidInvoice(
                    invoiceId = state.invoiceId,
                    reason = reason
                ) { result ->
                    if (_binding != null && voidDialog === dialog) {
                        result.onSuccess {
                            dialog.dismiss()
                            showMessage(getString(R.string.iv_success))
                        }.onFailure { error ->
                            confirm.isEnabled = true
                            cancel.isEnabled = true
                            reasonInput.isEnabled = true
                            dialog.setCancelable(true)
                            dialog.setCanceledOnTouchOutside(true)

                            reasonInput.error = error.message
                                ?: getString(R.string.iv_failed)
                        }
                    }
                }
            }
        }

        dialog.show()
    }


    private fun renderReplacementAction(state: InvoiceDetailUiState) {
        val status = state.status.trim().uppercase(java.util.Locale.ROOT)
        val ready = !state.isLoading && state.errorMessage == null && state.invoiceId > 0L
        binding.btnReplacementAction.isVisible = ready && status in setOf("VOID", "VOIDED", "DRAFT")
        binding.btnReplacementAction.setText(
            if (status == "DRAFT") R.string.ia_edit else R.string.ia_replacement
        )
        binding.btnReplacementAction.setOnClickListener {
            val latest = viewModel.uiState.value
            val currentStatus = latest.status.trim().uppercase(java.util.Locale.ROOT)
            if (!latest.isLoading && latest.invoiceId > 0L && latest.errorMessage == null &&
                currentStatus in setOf("DRAFT", "VOID", "VOIDED")) {
                val draft = currentStatus == "DRAFT"
                findNavController().navigate(R.id.replacementInvoiceFragment, bundleOf(
                    "originalInvoiceId" to if (draft) 0L else latest.invoiceId,
                    "draftInvoiceId" to if (draft) latest.invoiceId else 0L
                ))
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
