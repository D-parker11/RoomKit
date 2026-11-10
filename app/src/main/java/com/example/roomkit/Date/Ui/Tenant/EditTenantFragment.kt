package com.example.roomkit.Date.Ui.Tenant

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentAddTenantBinding
import kotlinx.coroutines.launch

class EditTenantFragment : Fragment(R.layout.fragment_add_tenant) {
    private val viewModel: EditTenantViewModel by viewModels {
        ViewModelFactory(requireContext(), requireArguments().getLong("tenantId"))
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentAddTenantBinding.bind(view)
        b.tvTenantFormTitle.setText(R.string.qa_edit_tenant)
        b.tvTenantFormSubtitle.setText(R.string.qa_edit_tenant_hint)
        // ViewModel owns draft fields across view recreation; do not restore stale XML state over them.
        val fields = listOf(b.etFullName, b.etPhoneNumber, b.etIdPassport, b.etNote)
        fields.forEach { it.isSaveEnabled = false }
        var rendering = false
        fields.forEach { field -> field.doAfterTextChanged {
            if (!rendering) viewModel.edit(b.etFullName.text.toString(), b.etPhoneNumber.text.toString(),
                b.etIdPassport.text.toString(), b.etNote.text.toString())
        } }
        b.btnBackAddTenant.setOnClickListener { findNavController().popBackStack() }
        b.btnSaveTenant.setOnClickListener {
            b.tilFullName.error = if (b.etFullName.text.isNullOrBlank()) getString(R.string.add_tenant_name_required) else null
            b.tilPhoneNumber.error = if (b.etPhoneNumber.text.isNullOrBlank()) getString(R.string.add_tenant_phone_required) else null
            if (b.tilFullName.error == null && b.tilPhoneNumber.error == null) viewModel.save()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    rendering = true
                    fields.zip(listOf(s.name, s.phone, s.identity, s.note)).forEach { (field, value) ->
                        if (field.text.toString() != value) field.setText(value)
                        field.isEnabled = s.exists && !s.loading && !s.saving
                    }
                    rendering = false
                    b.btnSaveTenant.isEnabled = s.exists && !s.loading && !s.saving
                    b.btnSaveTenant.setText(if (s.saving) R.string.qa_saving else R.string.qa_save_changes)
                    b.tilPhoneNumber.error = s.error
                    if (s.saved) {
                        viewModel.consumeSaved()
                        Toast.makeText(requireContext(), R.string.qa_tenant_updated, Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }
}
