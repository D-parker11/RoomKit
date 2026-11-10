package com.example.roomkit.Date.Ui.Tenant

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Model.TenancyHistoryItem
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentTenantDetailsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TenantDetailsFragment : Fragment() {

    private var _binding: FragmentTenantDetailsBinding? = null
    private val binding get() = _binding!!

    private val tenantId: Long by lazy {
        arguments?.getLong("tenantId") ?: 0L
    }

    private val viewModel: TenantDetailsViewModel by viewModels {
        ViewModelFactory(requireContext(), tenantId)
    }

    private val dateFormat = SimpleDateFormat(
        "dd MMM yyyy",
        Locale.US
    )

    private lateinit var historyAdapter: TenancyHistoryAdapter

    private var invoiceLoadJob: Job? = null
    private var invoiceDialog: AlertDialog? = null
    private var moveOutDialog: AlertDialog? = null
    private var moveOutDateDialog: DatePickerDialog? = null

    private var isMovingOut = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTenantDetailsBinding.inflate(
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

        historyAdapter = TenancyHistoryAdapter(
            onViewInvoices = { item -> showTenancyInvoices(item) },
            onViewSettlement = { item -> findNavController().navigate(R.id.moveOutFragment, bundleOf("tenancyId" to item.tenancyId)) }
        )

        binding.rvTenancyHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }

        binding.btnBackTenantDetails.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnAssignRoom.setOnClickListener {
            if (tenantId > 0L &&
                viewModel.activeTenancy.value == null
            ) {
                findNavController().navigate(
                    R.id.moveInFragment,
                    bundleOf("presetTenantId" to tenantId)
                )
            }
        }

        binding.btnEditTenant.setOnClickListener {
            findNavController().navigate(R.id.editTenantFragment, bundleOf("tenantId" to tenantId))
        }
        observeDetails()
    }

    private fun observeDetails() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.tenant.collect { tenant ->
                        val views = _binding ?: return@collect

                        views.tvDetailsFullName.text =
                            tenant?.fullName.orEmpty()

                        views.tvDetailsPhone.text =
                            tenant?.phoneNumber.orEmpty()

                        views.tvDetailsIdOrPassport.text =
                            tenant?.idOrPassport
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    getString(R.string.td_identity, it)
                                }
                                ?: getString(R.string.td_no_identity)
                    }
                }

                launch {
                    viewModel.activeTenancy.collect { tenancy ->
                        bindActiveTenancy(tenancy)
                    }
                }

                launch {
                    viewModel.tenancyHistory.collect { history ->
                        historyAdapter.submitList(history)

                        // Refresh the room label when its details arrive.
                        bindActiveTenancy(
                            viewModel.activeTenancy.value
                        )
                    }
                }
            }
        }
    }

    private fun bindActiveTenancy(tenancy: TenancyEntity?) {
        val views = _binding ?: return
        val hasTenancy = tenancy != null

        views.layoutActiveTenancyCard.isVisible = hasTenancy
        views.tvNoActiveTenancy.isVisible = !hasTenancy
        views.btnAssignRoom.isVisible = !hasTenancy

        views.btnAssignRoom.isEnabled =
            tenantId > 0L && !isMovingOut

        views.btnMoveOutTenant.isEnabled = !isMovingOut

        if (tenancy == null) {
            views.btnMoveOutTenant.setOnClickListener(null)
            views.btnViewAssignedRoom.setOnClickListener(null)
            return
        }

        val historyItem = viewModel.tenancyHistory.value
            .firstOrNull {
                it.tenancyId == tenancy.tenancyId
            }

        views.tvActiveBuildingRoom.text =
            historyItem?.let {
                getString(
                    R.string.td_property_room,
                    it.buildingName,
                    it.roomNumber
                )
            } ?: getString(R.string.td_current_rental)

        views.tvActiveRentDeposit.text = getString(
            R.string.td_rent_deposit,
            CurrencyFormatter.formatKhr(tenancy.monthlyRent),
            CurrencyFormatter.formatKhr(tenancy.deposit)
        )

        views.tvActiveMoveInDate.text = getString(
            R.string.td_move_in_date,
            dateFormat.format(Date(tenancy.moveInDate))
        )

        views.btnViewAssignedRoom.setOnClickListener {
            findNavController().navigate(
                R.id.roomDetailFragment,
                bundleOf("roomId" to tenancy.roomId)
            )
        }

        views.btnMoveOutTenant.setOnClickListener {
            findNavController().navigate(R.id.moveOutFragment, bundleOf("tenancyId" to tenancy.tenancyId))
        }
    }

    private fun showTenancyInvoices(item: TenancyHistoryItem) {
        if (invoiceLoadJob?.isActive == true) return
        if (invoiceDialog?.isShowing == true) return

        val owner = viewLifecycleOwner

        invoiceLoadJob = owner.lifecycleScope.launch {
            try {
                val invoices = viewModel.getInvoicesForTenancy(
                    item.tenancyId
                )

                if (_binding == null ||
                    !owner.lifecycle.currentState.isAtLeast(
                        Lifecycle.State.RESUMED
                    )
                ) {
                    return@launch
                }

                val builder = MaterialAlertDialogBuilder(
                    requireContext()
                ).setTitle(
                    getString(
                        R.string.tenant_history_invoices_title,
                        item.roomNumber
                    )
                )

                if (invoices.isEmpty()) {
                    builder.setMessage(
                        R.string.tenant_history_no_invoices
                    )
                } else {
                    val labels = invoices.map { invoice ->
                        getString(
                            R.string.tenant_history_invoice_item,
                            invoice.invoiceId,
                            invoice.billingMonth,
                            invoice.billingYear,
                            CurrencyFormatter.formatKhr(
                                invoice.totalKhr
                            ),
                            invoice.status,
                            CurrencyFormatter.formatKhr(
                                invoice.balanceKhr
                            )
                        )
                    }.toTypedArray()

                    builder.setItems(labels) { _, position ->
                        val selected = invoices[position]

                        findNavController().navigate(
                            R.id.invoiceDetailFragment,
                            bundleOf("invoiceId" to selected.invoiceId)
                        )
                    }
                }

                val dialog = builder
                    .setNegativeButton(
                        R.string.tenant_history_close,
                        null
                    )
                    .create()

                invoiceDialog = dialog

                dialog.setOnDismissListener {
                    if (invoiceDialog === dialog) {
                        invoiceDialog = null
                    }
                }

                dialog.show()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (_binding != null) {
                    showMessage(
                        getString(
                            R.string.tenant_history_invoice_load_failed
                        )
                    )
                }
            }
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
        invoiceLoadJob?.cancel()
        invoiceLoadJob = null

        invoiceDialog?.dismiss()
        invoiceDialog = null

        moveOutDateDialog?.dismiss()
        moveOutDateDialog = null

        moveOutDialog?.dismiss()
        moveOutDialog = null

        _binding?.rvTenancyHistory?.adapter = null
        _binding = null

        super.onDestroyView()
    }
}