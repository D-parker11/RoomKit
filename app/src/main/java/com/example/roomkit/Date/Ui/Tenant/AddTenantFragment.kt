@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Tenant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentAddTenantBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import com.example.roomkit.Date.Ui.Tenant.TenantsViewModel.TenantDuplicateMatch as TenantMatch

class AddTenantFragment : Fragment() {

    private var _binding: FragmentAddTenantBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TenantsViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private var duplicateDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTenantBinding.inflate(
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
        observeSaveState()
    }

    private fun setupClickListeners() {

        binding.btnBackAddTenant.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveTenant.setOnClickListener {
            validateAndSave()
        }
    }

    private fun validateAndSave(
        forceCreate: Boolean = false
    ) {

        clearErrors()

        val fullName =
            binding.etFullName.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val phone =
            binding.etPhoneNumber.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val idPassport =
            binding.etIdPassport.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val notes =
            binding.etNote.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (fullName.isBlank()) {

            binding.tilFullName.error =
                getString(
                    R.string.add_tenant_name_required
                )

            binding.etFullName.requestFocus()

            return
        }

        if (phone.isBlank()) {

            binding.tilPhoneNumber.error =
                getString(
                    R.string.add_tenant_phone_required
                )

            binding.etPhoneNumber.requestFocus()

            return
        }

        viewModel.checkForDuplicatesAndSave(
            fullName = fullName,
            phone = phone,
            idOrPassport = idPassport,
            notes = notes,
            forceCreate = forceCreate
        )
    }

    private fun clearErrors() {

        binding.tilFullName.error = null
        binding.tilPhoneNumber.error = null
    }

    private fun observeSaveState() {

        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {

                viewModel.uiState.collect { state ->

                    binding.btnSaveTenant.isEnabled =
                        !state.isSaving

                    binding.btnSaveTenant.text =
                        if (state.isSaving) {
                            "Saving..."
                        } else {
                            "Save Tenant"
                        }

                    when {

                        state.isSuccess -> {

                            viewModel.resetSaveState()

                            showMessage(
                                getString(
                                    R.string.add_tenant_success
                                )
                            )

                            findNavController()
                                .popBackStack()
                        }

                        state.errorMessage != null -> {

                            showMessage(
                                state.errorMessage
                            )

                            viewModel.resetSaveState()
                        }

                        state.showDuplicateWarning -> {

                            showDuplicateWarningDialog(
                                matches = state.duplicateMatches,
                                canCreateSeparateTenant =
                                    state.canCreateSeparateTenant
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showDuplicateWarningDialog(
        matches: List<TenantMatch>,
        canCreateSeparateTenant: Boolean
    ) {

        if (matches.isEmpty()) {
            return
        }

        if (duplicateDialog?.isShowing == true) {
            return
        }

        /*
         * Prefer identity-document matches when they exist,
         * because that is a stronger duplicate signal.
         */
        val identityMatches =
            matches.filter {
                it.sameIdentityDocument
            }

        val candidates =
            identityMatches.ifEmpty {
                matches
            }

        var selectedIndex = 0

        val labels =
            candidates.map { match ->

                getString(
                    R.string.add_tenant_match_item,
                    match.fullName,
                    match.phoneNumber
                )

            }.toTypedArray()

        val title =
            if (identityMatches.isNotEmpty()) {
                R.string.add_tenant_identity_exists
            } else {
                R.string.add_tenant_possible_match
            }

        val builder =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(title)
                .setSingleChoiceItems(
                    labels,
                    selectedIndex
                ) { _, position ->

                    selectedIndex = position
                }
                .setPositiveButton(
                    R.string.add_tenant_use_existing
                ) { _, _ ->

                    val selectedTenant =
                        candidates.getOrNull(
                            selectedIndex
                        ) ?: return@setPositiveButton

                    viewModel
                        .dismissDuplicateWarning()

                    openExistingTenant(
                        selectedTenant.tenantId
                    )
                }
                .setNegativeButton(
                    R.string.add_tenant_cancel
                ) { _, _ ->

                    viewModel
                        .dismissDuplicateWarning()
                }

        /*
         * Allow a separate record when the ViewModel says it is safe.
         */
        if (canCreateSeparateTenant) {

            builder.setNeutralButton(
                R.string.add_tenant_create_separate
            ) { _, _ ->

                confirmCreateSeparateTenant()
            }
        }

        val dialog = builder.create()

        duplicateDialog = dialog

        dialog.setOnCancelListener {

            viewModel
                .dismissDuplicateWarning()
        }

        dialog.setOnDismissListener {

            if (duplicateDialog === dialog) {
                duplicateDialog = null
            }
        }

        dialog.show()
    }

    private fun openExistingTenant(
        tenantId: Long
    ) {

        findNavController().navigate(
            R.id.tenantDetailsFragment,
            bundleOf(
                "tenantId" to tenantId
            )
        )
    }

    private fun confirmCreateSeparateTenant() {

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setTitle(
                    R.string.add_tenant_create_separate
                )
                .setMessage(
                    R.string.add_tenant_separate_confirmation
                )
                .setPositiveButton(
                    R.string.add_tenant_create
                ) { _, _ ->

                    /*
                     * ViewModel performs duplicate checks again
                     * before creating the tenant.
                     */
                    validateAndSave(
                        forceCreate = true
                    )
                }
                .setNegativeButton(
                    R.string.add_tenant_cancel
                ) { _, _ ->

                    viewModel
                        .dismissDuplicateWarning()
                }
                .create()

        duplicateDialog = dialog

        dialog.setOnCancelListener {

            viewModel
                .dismissDuplicateWarning()
        }

        dialog.setOnDismissListener {

            if (duplicateDialog === dialog) {
                duplicateDialog = null
            }
        }

        dialog.show()
    }

    private fun showMessage(
        message: String
    ) {

        if (!isAdded) {
            return
        }

        Toast.makeText(
            requireContext(),
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {

        duplicateDialog?.dismiss()
        duplicateDialog = null

        _binding = null

        super.onDestroyView()
    }
}