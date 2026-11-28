@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Billing

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Repositories.BillingRepository
import com.example.roomkit.databinding.BottomSheetRecordPaymentBinding
import com.example.roomkit.R
import com.example.roomkit.Date.Ui.Common.ReviewDialogs
import com.example.roomkit.Date.Ui.Common.ReviewSection
import androidx.appcompat.app.AlertDialog
import com.example.roomkit.Date.Billing.CarryForwardPayments
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordPaymentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecordPaymentBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var invoiceId = -1L
    private var loaded = false
    private var remainingBalanceKhr = 0L
    private var selectedDateTimestamp = System.currentTimeMillis()
    private var datePicker: DatePickerDialog? = null
    private var allocationDialog: AlertDialog? = null
    private var previewBusy = false
    private var loadedFingerprint = ""

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    private val repository: BillingRepository by lazy {
        val appContext = requireContext().applicationContext
        val db = AppDatabase.getInstance(appContext)

        BillingRepository(
            context = appContext,
            db = db,
            buildingDao = db.buildingDao(),
            invoiceDao = db.invoiceDao(),
            paymentDao = db.paymentDao(),
            tenancyDao = db.tenancyDao(),
            tenantDao = db.tenantDao(),
            roomDao = db.roomDao(),
            meterReadingDao = db.meterReadingDao()
        )
    }

    private val paymentVm: RecordPaymentViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(
                    modelClass.isAssignableFrom(RecordPaymentViewModel::class.java)
                ) {
                    "Unknown ViewModel: ${modelClass.name}"
                }

                return RecordPaymentViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        invoiceId = arguments?.getLong("invoiceId") ?: -1L
        selectedDateTimestamp =
            savedInstanceState?.getLong(KEY_DATE)
                ?: System.currentTimeMillis()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRecordPaymentBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loaded = false

        setupPaymentMethod()
        restoreForm(savedInstanceState)
        setupDatePicker()
        setupAmountInput()
        setupAdditionalDetails(savedInstanceState)
        setupSaveButton()
        observePaymentState()

        updateMethodNotice()
        updateBalancePreview()
        updateControls()

        // Preserve a deliberately empty amount after rotation.
        val prefillAmount =
            savedInstanceState?.containsKey(KEY_AMOUNT) != true

        loadInvoiceDetails(prefillAmount)
    }

    override fun onStart() {
        super.onStart()

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        updateControls()
    }

    private fun setupPaymentMethod() {
        val methods = listOf("Cash" , "KHQR")

        binding.actvPaymentMethod.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                methods
            )
        )

        binding.actvPaymentMethod.setText("Cash", false)

        binding.actvPaymentMethod.doAfterTextChanged {
            binding.tilPaymentMethod.error = null
            updateMethodNotice()
        }
    }

    private fun restoreForm(saved: Bundle?) {
        if (saved == null) return

        binding.etPaymentAmount.setText(saved.getString(KEY_AMOUNT))
        binding.actvPaymentMethod.setText(
            saved.getString(KEY_METHOD) ?: "Cash",
            false
        )
        binding.etPaymentReference.setText(saved.getString(KEY_REFERENCE))
        binding.etPaymentNote.setText(saved.getString(KEY_NOTE))
    }

    private fun setupDatePicker() {
        updateDateLabel()

        binding.etPaymentDate.setOnClickListener {
            showDatePicker()
        }

        binding.tilPaymentDate.setEndIconOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        if (!canEdit() || datePicker?.isShowing == true) return

        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedDateTimestamp
        }

        datePicker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                if (_binding != null && canEdit()) {
                    calendar.set(year, month, day)
                    selectedDateTimestamp = calendar.timeInMillis
                    updateDateLabel()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).also { picker ->
            picker.setOnDismissListener {
                datePicker = null
            }
            picker.show()
        }
    }

    private fun updateDateLabel() {
        _binding?.etPaymentDate?.setText(
            dateFormat.format(Date(selectedDateTimestamp))
        )
    }

    private fun setupAmountInput() {
        binding.etPaymentAmount.doAfterTextChanged {
            binding.tilPaymentAmount.error = null
            updateBalancePreview()
        }

        binding.btnFullBalance.setOnClickListener {
            if (!canEdit()) return@setOnClickListener

            binding.etPaymentAmount.setText(
                remainingBalanceKhr.toString()
            )
            binding.etPaymentAmount.setSelection(
                binding.etPaymentAmount.text?.length ?: 0
            )
        }
    }

    private fun setupAdditionalDetails(saved: Bundle?) {
        val expanded =
            saved?.getBoolean(KEY_DETAILS_EXPANDED) == true ||
                    !binding.etPaymentReference.text.isNullOrBlank() ||
                    !binding.etPaymentNote.text.isNullOrBlank()

        showAdditionalDetails(expanded)

        binding.btnAdditionalDetails.setOnClickListener {
            showAdditionalDetails(
                !binding.additionalDetails.isVisible
            )
        }
    }

    private fun showAdditionalDetails(expanded: Boolean) {
        binding.additionalDetails.isVisible = expanded
        binding.btnAdditionalDetails.text =
            if (expanded) {
                "Hide additional details"
            } else {
                "Add reference or note"
            }
    }

    private fun updateMethodNotice() {
        val b = _binding ?: return
        val method = b.actvPaymentMethod.text.toString()

        b.tvBankNotice.isVisible =
            method == "Bank Transfer" || method == "KHQR"
    }

    private fun updateBalancePreview() {
        val b = _binding ?: return

        if (!loaded) {
            b.tvBalanceAfterPayment.text = ""
            return
        }

        val amount = CurrencyFormatter.parseKhrInput(
            b.etPaymentAmount.text?.toString().orEmpty()
        )

        b.tvBalanceAfterPayment.text = when {
            amount == null || amount <= 0L ->
                "Enter the amount you received."

            amount > remainingBalanceKhr ->
                "Payment exceeds total outstanding through this invoice."

            amount == remainingBalanceKhr ->
                "This payment will clear the balance through this invoice."

            else -> {
                val balanceAfter = remainingBalanceKhr - amount
                "Outstanding after payment: ${
                    CurrencyFormatter.formatKhr(balanceAfter)
                }"
            }
        }
    }

    private fun loadInvoiceDetails(prefillAmount: Boolean) {
        if (invoiceId <= 0L) {
            showLoadError("Unable to open payment: invalid invoice.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val saved =
                    repository.observeInvoiceDetailWithItems(invoiceId)
                        .firstOrNull()
                        ?: error("Invoice not found.")

                val detail = saved.detail
                val invoice = detail.invoice

                require(invoice.currency == "KHR") {
                    "Unsupported invoice currency."
                }

                val status = invoice.status.trim().uppercase(Locale.ROOT)

                require(
                    status in setOf(
                        "ISSUED",
                        "UNPAID",
                        "PARTIAL",
                        "PARTIALLY_PAID",
                        "PAID",
                        "OVERDUE"
                    )
                ) {
                    "Payments cannot be recorded for this invoice status."
                }

                val scope = CarryForwardPayments.scope(invoice, saved.accountInvoices)
                val account = com.example.roomkit.Date.Billing.InvoiceAccountBalances.calculate(invoice, saved.accountInvoices)
                val totalPaid = account.currentPaidKhr
                val invoiceTotal = invoice.totalKhr
                remainingBalanceKhr = scope.dueKhr
                loadedFingerprint = scope.fingerprint
                binding.tvCarryForwardBreakdown.text =
                    "Previous unpaid: ${CurrencyFormatter.formatKhr(scope.previousUnpaidKhr)}\n" +
                            "This invoice remaining: ${CurrencyFormatter.formatKhr(scope.currentBalanceKhr)}"

                val periodText = YearMonth.of(
                    invoice.billingYear,
                    invoice.billingMonth
                ).format(
                    DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
                )

                binding.tvPaymentHeaderSubtitle.text =
                    "Room ${detail.roomNumber} · $periodText"

                binding.tvPaymentTotalAmount.text =
                    CurrencyFormatter.formatKhr(invoiceTotal)

                binding.tvPaymentAlreadyPaid.text =
                    CurrencyFormatter.formatKhr(totalPaid)

                binding.tvPaymentRemaining.text =
                    CurrencyFormatter.formatKhr(remainingBalanceKhr)

                require(remainingBalanceKhr > 0L) {
                    "No outstanding balance through this invoice."
                }

                if (
                    prefillAmount &&
                    binding.etPaymentAmount.text.isNullOrBlank()
                ) {
                    binding.etPaymentAmount.setText(
                        remainingBalanceKhr.toString()
                    )
                }

                loaded = true
                updateBalancePreview()
                updateControls()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showLoadError(
                    e.message ?: "Unable to load invoice. Reopen this form."
                )
            }
        }
    }

    private fun showLoadError(message: String) {
        loaded = false

        _binding?.apply {
            tvBalanceAfterPayment.text = message
            tvBalanceAfterPayment.isVisible = true
        }

        updateControls()
    }

    private fun setupSaveButton() {
        binding.btnSavePayment.setOnClickListener {
            savePayment()
        }
    }

    private fun savePayment() {
        if (!canEdit()) return

        val amount = CurrencyFormatter.parseKhrInput(
            binding.etPaymentAmount.text?.toString().orEmpty()
        )

        when {
            amount == null || amount <= 0L -> {
                binding.tilPaymentAmount.error =
                    "Enter a valid payment amount."
                binding.etPaymentAmount.requestFocus()
                return
            }

            amount > remainingBalanceKhr -> {
                binding.tilPaymentAmount.error =
                    "Amount cannot exceed ${
                        CurrencyFormatter.formatKhr(remainingBalanceKhr)
                    }."
                binding.etPaymentAmount.requestFocus()
                return
            }
        }

        binding.tilPaymentAmount.error = null

        val method = when (
            binding.actvPaymentMethod.text
                .toString()
                .trim()
                .uppercase(Locale.ROOT)
        ) {
            "CASH" -> "CASH"
            "BANK", "BANK TRANSFER" -> "BANK_TRANSFER"
            "KHQR" -> "KHQR"

            else -> {
                binding.tilPaymentMethod.error =
                    "Choose a payment method."
                return
            }
        }

        binding.tilPaymentMethod.error = null

        val note = binding.etPaymentNote.text
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val reference = binding.etPaymentReference.text
            ?.toString()
            ?.trim()

        previewBusy = true
        updateControls()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val preview = repository.previewCarryForwardPayment(invoiceId, amount).getOrThrow()
                check(preview.scope.fingerprint == loadedFingerprint) {
                    "Balances changed. Reopen this form to refresh the amount due."
                }
                if (_binding == null) return@launch
                allocationDialog = ReviewDialogs.create(
                    context = requireContext(),
                    title = getString(R.string.payment_review_title),
                    description = getString(R.string.payment_review_description),
                    sections = listOf(
                        ReviewSection(rows = listOf(
                            getString(R.string.payment_review_received) to CurrencyFormatter.formatKhr(amount),
                            getString(R.string.payment_review_remaining) to
                                CurrencyFormatter.formatKhr(preview.scope.dueKhr - amount)
                        )),
                        ReviewSection(
                            title = getString(R.string.payment_review_allocations),
                            rows = preview.allocations.map {
                                getString(R.string.roomkit_invoice_number, it.invoiceId) to
                                    CurrencyFormatter.formatKhr(it.amountKhr)
                            }
                        )
                    ),
                    confirmLabel = getString(R.string.ia_record),
                    onConfirm = {
                        paymentVm.saveCarryForward(invoiceId, amount, selectedDateTimestamp,
                            method, note, reference, preview.scope.fingerprint)
                        updateControls()
                    }
                ).also { d ->
                    d.setOnDismissListener { allocationDialog = null; updateControls() }
                    d.show()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                _binding?.tvBalanceAfterPayment?.text = e.message ?: "Unable to prepare payment."
            } finally {
                previewBusy = false
                updateControls()
            }
        }
    }

    private fun observePaymentState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paymentVm.state.collect { state ->
                    updateControls()

                    when {
                        state.saved -> {
                            Toast.makeText(
                                requireContext(),
                                "Payment recorded successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            dismiss()
                        }

                        state.error != null -> {
                            Toast.makeText(
                                requireContext(),
                                state.error,
                                Toast.LENGTH_LONG
                            ).show()

                            paymentVm.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun canEdit(): Boolean {
        val state = paymentVm.state.value
        return loaded && !state.saving && !state.saved && !previewBusy && allocationDialog == null
    }

    private fun updateControls() {
        val b = _binding ?: return
        val state = paymentVm.state.value
        val editable = canEdit()

        b.etPaymentAmount.isEnabled = editable
        b.actvPaymentMethod.isEnabled = editable
        b.tilPaymentMethod.isEnabled = editable
        b.etPaymentDate.isEnabled = editable
        b.tilPaymentDate.isEnabled = editable
        b.etPaymentReference.isEnabled = editable
        b.etPaymentNote.isEnabled = editable
        b.btnFullBalance.isEnabled = editable
        b.btnAdditionalDetails.isEnabled = editable
        b.btnSavePayment.isEnabled = editable

        b.btnSavePayment.text =
            if (state.saving) "Saving…" else "Save payment"

        isCancelable = !state.saving

        (dialog as? BottomSheetDialog)?.behavior?.isDraggable =
            !state.saving
    }

    override fun onSaveInstanceState(outState: Bundle) {
        _binding?.let { b ->
            outState.putString(
                KEY_AMOUNT,
                b.etPaymentAmount.text?.toString()
            )
            outState.putString(
                KEY_METHOD,
                b.actvPaymentMethod.text?.toString()
            )
            outState.putString(
                KEY_REFERENCE,
                b.etPaymentReference.text?.toString()
            )
            outState.putString(
                KEY_NOTE,
                b.etPaymentNote.text?.toString()
            )
            outState.putBoolean(
                KEY_DETAILS_EXPANDED,
                b.additionalDetails.isVisible
            )
        }

        outState.putLong(KEY_DATE, selectedDateTimestamp)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        allocationDialog?.dismiss()
        allocationDialog = null
        previewBusy = false
        datePicker?.dismiss()
        datePicker = null
        loaded = false
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_AMOUNT = "paymentAmount"
        const val KEY_METHOD = "paymentMethod"
        const val KEY_REFERENCE = "paymentReference"
        const val KEY_NOTE = "paymentNote"
        const val KEY_DATE = "paymentDate"
        const val KEY_DETAILS_EXPANDED = "detailsExpanded"
    }
}
