@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.viewModels
import kotlinx.coroutines.CancellationException
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Repositories.BillingRepository
import com.example.roomkit.databinding.BottomSheetRecordPaymentBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordPaymentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetRecordPaymentBinding? = null
    private val binding get() = _binding!!

    private var loaded = false
    private val paymentVm: RecordPaymentViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RecordPaymentViewModel(repository) as T
        }
    }
    private var invoiceId: Long = -1L
    private var remainingBalanceKhr: Long = 0L
    private var selectedDateTimestamp = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    private val repository: BillingRepository by lazy {
        val db = AppDatabase.getInstance(requireContext())
        BillingRepository(
            context = requireContext().applicationContext,
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceId = arguments?.getLong("invoiceId") ?: -1L
        selectedDateTimestamp = savedInstanceState?.getLong("paymentDate") ?: selectedDateTimestamp
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRecordPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPaymentMethodDropdown()
        setupDatePicker()
        loadInvoiceDetails()
        setupSaveButton()
        savedInstanceState?.let { saved ->
            binding.etPaymentAmount.setText(saved.getString("paymentAmount"))
            binding.actvPaymentMethod.setText(saved.getString("paymentMethod") ?: "Cash", false)
            binding.etPaymentReference.setText(saved.getString("paymentReference"))
            binding.etPaymentNote.setText(saved.getString("paymentNote"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                paymentVm.state.collect { state ->
                    binding.btnSavePayment.isEnabled = loaded && !state.saving && !state.saved
                    isCancelable = !state.saving
                    if (state.saved) {
                        Toast.makeText(requireContext(), "Payment recorded successfully", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } else if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
                        paymentVm.clearError()
                    }
                }
            }
        }
    }

    private fun setupPaymentMethodDropdown() {
        val methods = listOf("Cash", "Bank Transfer", "KHQR")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methods)
        binding.actvPaymentMethod.setAdapter(adapter)
        binding.actvPaymentMethod.setText("Cash", false)
    }

    private fun setupDatePicker() {
        binding.etPaymentDate.setText(dateFormat.format(Date(selectedDateTimestamp)))
        binding.etPaymentDate.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDateTimestamp = cal.timeInMillis
                    binding.etPaymentDate.setText(dateFormat.format(Date(selectedDateTimestamp)))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadInvoiceDetails() {
        binding.btnSavePayment.isEnabled = false
        if (invoiceId <= 0L) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val saved = repository.observeInvoiceDetailWithItems(invoiceId).firstOrNull() ?: error("Invoice not found.")
                val detail = saved.detail
                require(detail.invoice.currency == "KHR") { "Unsupported invoice currency." }
                val payments = saved.accountInvoices.single { it.invoice.invoiceId == invoiceId }.payments

                val totalPaid = payments.filter { it.status == "ACTIVE" }.sumOf { it.amountKhr }
                val invoiceTotal = detail.invoice.totalKhr
                remainingBalanceKhr = (invoiceTotal - totalPaid).coerceAtLeast(0L)

                val periodCal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.MONTH, detail.invoice.billingMonth - 1)
                    set(Calendar.YEAR, detail.invoice.billingYear)
                }
                val periodText = SimpleDateFormat("MMM yyyy", Locale.US).format(periodCal.time)

                binding.tvPaymentHeaderSubtitle.text = "Room ${detail.roomNumber} · $periodText"
                binding.tvPaymentTotalAmount.text = CurrencyFormatter.formatKhr(invoiceTotal)
                binding.tvPaymentAlreadyPaid.text = CurrencyFormatter.formatKhr(totalPaid)
                binding.tvPaymentRemaining.text = CurrencyFormatter.formatKhr(remainingBalanceKhr)

                // Pre-fill amount input with remaining balance
                val defaultAmountText = remainingBalanceKhr.toString()
                if (binding.etPaymentAmount.text.isNullOrBlank()) binding.etPaymentAmount.setText(defaultAmountText)
                loaded = true
                binding.btnSavePayment.isEnabled = !paymentVm.state.value.saving && !paymentVm.state.value.saved
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Toast.makeText(requireContext(), e.message ?: "Unable to load invoice. Reopen this form.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSavePayment.setOnClickListener {
            if (!loaded || paymentVm.state.value.saving || paymentVm.state.value.saved) return@setOnClickListener
            val amountInput = binding.etPaymentAmount.text?.toString().orEmpty()
            val enteredKhr = CurrencyFormatter.parseKhrInput(amountInput)

            if (enteredKhr == null || enteredKhr <= 0L) {
                binding.tilPaymentAmount.error = "Enter a valid payment amount"
                return@setOnClickListener
            }

            if (enteredKhr > remainingBalanceKhr) {
                binding.tilPaymentAmount.error =
                    "Amount cannot exceed balance of ${CurrencyFormatter.formatKhr(remainingBalanceKhr)}"
                return@setOnClickListener
            }

            binding.tilPaymentAmount.error = null

            val rawMethod = binding.actvPaymentMethod.text.toString().trim()
            val methodEnum = when (rawMethod.uppercase(Locale.US)) {
                "KHQR" -> "KHQR"
                "BANK TRANSFER", "BANK" -> "BANK_TRANSFER"
                else -> "CASH"
            }

            val note = binding.etPaymentNote.text?.toString()?.trim()

            val reference = binding.etPaymentReference.text?.toString()
            paymentVm.save(invoiceId, enteredKhr, selectedDateTimestamp, methodEnum,
                note?.ifEmpty { null }, reference)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        _binding?.let { b ->
            outState.putString("paymentAmount", b.etPaymentAmount.text?.toString())
            outState.putString("paymentMethod", b.actvPaymentMethod.text?.toString())
            outState.putString("paymentReference", b.etPaymentReference.text?.toString())
            outState.putString("paymentNote", b.etPaymentNote.text?.toString())
        }
        outState.putLong("paymentDate", selectedDateTimestamp)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loaded = false
        _binding = null
    }
}
