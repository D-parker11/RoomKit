package com.example.roomkit.Date.Settings

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.example.roomkit.databinding.FragmentSettingBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_setting) {

    private var _binding: FragmentSettingBinding? = null

    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentSettingBinding.bind(view)
        _binding = binding

        val fields: List<Pair<TextInputEditText, String>> = listOf(
            binding.settingsDue to "due",
            binding.settingsElectricity to "electricity",
            binding.settingsFixedWater to "fixedWater",
            binding.settingsWaterRate to "waterRate",
            binding.settingsRubbish to "rubbish"
        )

        fields.forEach { (field, key) ->
            // SavedStateHandle owns the input values.
            field.isSaveEnabled = false
            field.setText(viewModel.text(key))

            field.doAfterTextChanged { text ->
                viewModel.edit(key, text?.toString().orEmpty())
            }
        }

        binding.settingsMetered.isSaveEnabled = false
        binding.settingsMetered.isChecked = viewModel.metered()

        binding.settingsMetered.setOnCheckedChangeListener { _, checked ->
            viewModel.waterMethod(checked)
        }

        binding.settingsBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.settingsSave.setOnClickListener {
            viewModel.save()
        }

        binding.settingsNotifications.setOnClickListener {
            openNotificationSettings()
        }

        binding.settingsVersion.text = getString(
            R.string.rks_version,
            installedVersion()
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    fields.forEach { (field, _) ->
                        field.isEnabled = !state.saving
                    }

                    binding.settingsMetered.isEnabled = !state.saving
                    binding.settingsSave.isEnabled = !state.saving

                    binding.settingsSave.setText(
                        if (state.saving) {
                            R.string.rks_saving
                        } else {
                            R.string.rks_save
                        }
                    )

                    binding.settingsMessage.text = state.message
                    binding.settingsMessage.isVisible =
                        state.message.isNotEmpty()

                    binding.settingsMessage.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (state.error) {
                                R.color.rks_error
                            } else {
                                R.color.rks_success
                            }
                        )
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationStatus()
    }

    private fun updateNotificationStatus() {
        val binding = _binding ?: return

        val manager = requireContext().getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channel = manager.getNotificationChannel(
            "roomkit_invoice_reminders"
        )

        val enabled = manager.areNotificationsEnabled() &&
                (
                        channel == null ||
                                channel.importance != NotificationManager.IMPORTANCE_NONE
                        )

        binding.settingsNotificationStatus.setText(
            if (enabled) {
                R.string.rks_enabled
            } else {
                R.string.rks_disabled
            }
        )
    }

    private fun openNotificationSettings() {
        val packageName = requireContext().packageName

        val notificationIntent = Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

        try {
            startActivity(notificationIntent)
        } catch (_: ActivityNotFoundException) {
            val appDetailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )

            try {
                startActivity(appDetailsIntent)
            } catch (_: ActivityNotFoundException) {
                _binding?.let { binding ->
                    Snackbar.make(
                        binding.root,
                        R.string.rks_cannot_open,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(): String {
        return runCatching {
            val context = requireContext()

            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull()
            .orEmpty()
            .ifBlank { "—" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}