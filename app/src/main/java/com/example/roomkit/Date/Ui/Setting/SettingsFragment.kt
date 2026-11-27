@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Settings

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Payment.PaymentSettingsRepository
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentSettingBinding
import com.example.roomkit.databinding.ItemSettingsRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SettingsFragment : Fragment(R.layout.fragment_setting) {

    private var binding: FragmentSettingBinding? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentSettingBinding.bind(view)
        binding = b

        b.settingsBack.setOnClickListener {
            findNavController().popBackStack()
        }

        row(
            b.settingsProperty,
            R.string.rks2_property,
            R.string.rks2_property_body,
            R.drawable.ic_building
        ) {
            openDestination(R.id.buildingsFragment)
        }

        row(
            b.settingsDefaults,
            R.string.rks2_defaults,
            R.string.rks2_defaults_body,
            R.drawable.ic_settings
        ) {
            openDestination(R.id.roomkitBillingDefaultsFragment)
        }

        configureRow(
            b = b.settingsPayment,
            title = "Payment setup",
            subtitle = "Manage your receiving KHQR and invoice QR settings",
            icon = R.drawable.ic_payment
        ) {
            openDestination(R.id.paymentSetupFragment)
        }

        row(
            b.settingsMonthClose,
            R.string.rks2_month,
            R.string.rks2_month_body,
            R.drawable.ic_calender
        ) {
            openTools("MONTH")
        }

        row(
            b.settingsCurrency,
            R.string.rks2_currency,
            R.string.rks2_currency_body,
            R.drawable.ic_payment
        )

        b.settingsCurrency.rowValue.text = "KHR"

        row(
            b.settingsNotifications,
            R.string.rks2_notifications,
            R.string.rks2_notifications_body,
            R.drawable.ic_bell
        ) {
            openNotificationSettings()
        }

        row(
            b.settingsBackup,
            R.string.rks2_backup,
            R.string.rks2_backup_body,
            R.drawable.ic_download
        ) {
            openTools("BACKUP")
        }

        row(
            b.settingsPrivacy,
            R.string.rks2_privacy,
            R.string.rks2_privacy_body,
            R.drawable.ic_alert_circle
        ) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rks2_privacy)
                .setMessage(R.string.rks2_privacy_detail)
                .setPositiveButton(R.string.rks2_ok, null)
                .show()
        }

        @Suppress("DEPRECATION")
        val version = runCatching {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
        }
            .getOrNull()
            .orEmpty()
            .ifBlank { "—" }

        b.settingsVersion.text = getString(
            R.string.rks2_version,
            version
        )
    }

    private fun row(
        b: ItemSettingsRowBinding,
        @StringRes title: Int,
        @StringRes subtitle: Int,
        @DrawableRes icon: Int,
        action: (() -> Unit)? = null
    ) {
        configureRow(
            b = b,
            title = getString(title),
            subtitle = getString(subtitle),
            icon = icon,
            action = action
        )
    }

    private fun configureRow(
        b: ItemSettingsRowBinding,
        title: String,
        subtitle: String,
        @DrawableRes icon: Int,
        action: (() -> Unit)? = null
    ) {
        b.rowTitle.text = title
        b.rowSubtitle.text = subtitle
        b.rowIcon.setImageResource(icon)
        b.rowValue.text = ""
        b.rowChevron.isVisible = action != null

        b.root.setOnClickListener(null)

        if (action != null) {
            b.root.setOnClickListener {
                action()
            }
        }

        b.root.isClickable = action != null
        b.root.isFocusable = action != null
    }

    private fun openDestination(
        destinationId: Int,
        arguments: Bundle? = null
    ) {
        if (parentFragmentManager.isStateSaved) return

        val nav = findNavController()

        // Prevent duplicate navigation after a rapid second tap.
        if (nav.currentDestination?.id != R.id.roomkitSettingsFragment) {
            return
        }

        nav.navigate(destinationId, arguments)
    }

    private fun openTools(section: String) {
        openDestination(
            R.id.foundationToolsFragment,
            bundleOf("section" to section)
        )
    }

    override fun onResume() {
        super.onResume()

        val b = binding ?: return
        val context = requireContext()

        val defaults = runCatching {
            SettingsRepository(context).load()
        }.getOrNull()

        b.settingsDefaults.rowValue.text = defaults?.let {
            getString(R.string.rks2_due_summary, it.dueDay)
        }.orEmpty()

        // Refresh after returning from Payment setup.
        val paymentQr = PaymentSettingsRepository(
            context.applicationContext
        ).load()

        b.settingsPayment.rowValue.text =
            if (paymentQr != null) {
                "Ready"
            } else {
                "Not set up"
            }

        b.settingsPayment.rowValue.setTextColor(
            ContextCompat.getColor(
                context,
                if (paymentQr != null) {
                    R.color.roomkit_success
                } else {
                    R.color.roomkit_text_secondary
                }
            )
        )

        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        val channel = notificationManager.getNotificationChannel(
            "roomkit_invoice_reminders"
        )

        val notificationsEnabled =
            notificationManager.areNotificationsEnabled() &&
                    (
                            channel == null ||
                                    channel.importance != NotificationManager.IMPORTANCE_NONE
                            )

        b.settingsNotifications.rowValue.setText(
            if (notificationsEnabled) {
                R.string.rks2_enabled
            } else {
                R.string.rks2_disabled
            }
        )

        b.settingsNotifications.rowValue.setTextColor(
            ContextCompat.getColor(
                context,
                if (notificationsEnabled) {
                    R.color.roomkit_success
                } else {
                    R.color.roomkit_error
                }
            )
        )
    }

    private fun openNotificationSettings() {
        val packageName = requireContext().packageName

        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(
                        Settings.EXTRA_APP_PACKAGE,
                        packageName
                    )
            )
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                binding?.let { b ->
                    Snackbar.make(
                        b.root,
                        R.string.rks_cannot_open,
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}