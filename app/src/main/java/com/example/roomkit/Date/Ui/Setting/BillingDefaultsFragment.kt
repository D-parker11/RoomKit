package com.example.roomkit.Date.Settings

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentBillingDefaultsBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/** Reuses the existing settings validation and preference storage. */
class BillingDefaultsFragment : Fragment(R.layout.fragment_billing_defaults) {
    private val vm: SettingsViewModel by viewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentBillingDefaultsBinding.bind(view)
        val fields: List<Pair<TextInputEditText, String>> = listOf(
            b.settingsDue to "due", b.settingsElectricity to "electricity",
            b.settingsFixedWater to "fixedWater", b.settingsWaterRate to "waterRate", b.settingsRubbish to "rubbish"
        )
        fields.forEach { (field, key) ->
            field.isSaveEnabled = false
            field.setText(vm.text(key))
            field.doAfterTextChanged { vm.edit(key, it?.toString().orEmpty()) }
        }
        b.settingsMetered.isChecked = vm.metered()
        b.settingsMetered.setOnCheckedChangeListener { _, checked -> vm.waterMethod(checked) }
        b.settingsBack.setOnClickListener { findNavController().popBackStack() }
        b.settingsSave.setOnClickListener { vm.save() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    fields.forEach { (field, _) -> field.isEnabled = !state.saving }
                    b.settingsMetered.isEnabled = !state.saving; b.settingsSave.isEnabled = !state.saving
                    b.settingsSave.setText(if (state.saving) R.string.rks_saving else R.string.rks_save)
                    b.settingsMessage.text = state.message; b.settingsMessage.isVisible = state.message.isNotBlank()
                    b.settingsMessage.setTextColor(ContextCompat.getColor(requireContext(),
                        if (state.error) R.color.rks2_error else R.color.rks2_success))
                }
            }
        }
    }
}
