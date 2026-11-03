package com.example.roomkit.Date.Ui.Reminders

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import com.example.roomkit.databinding.FragmentReminderBinding
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {
    private var _binding: FragmentReminderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by viewModels()
    private var adapter: RemindersAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RemindersAdapter { id ->
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.remindersFragment) {
                nav.navigate(R.id.action_remindersFragment_to_invoiceDetailFragment, bundleOf("invoiceId" to id))
            }
        }
        binding.listReminders.layoutManager = LinearLayoutManager(requireContext())
        binding.listReminders.adapter = adapter
        binding.btnRemindersBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnReminderSettings.setOnClickListener { openNotificationSettings() }
        binding.btnReminderRetry.setOnClickListener { viewModel.refresh() }
        binding.chipOverdue.setOnClickListener { viewModel.select(ReminderFilter.OVERDUE) }
        binding.chipToday.setOnClickListener { viewModel.select(ReminderFilter.TODAY) }
        binding.chipUpcoming.setOnClickListener { viewModel.select(ReminderFilter.UPCOMING) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
            ?.menu?.findItem(R.id.moreFragment)?.isChecked = true
        try {
            InvoiceReminderScheduler.start(requireContext().applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("RemindersScreen", "Could not refresh schedule", e)
            Toast.makeText(requireContext(), R.string.rr_schedule_error, Toast.LENGTH_LONG).show()
        }
        viewModel.refresh()
    }

    private fun render(state: RemindersUiState) {
        binding.progressReminders.isVisible = state.loading
        binding.listReminders.isVisible = !state.loading && state.error == null && state.cards.isNotEmpty()
        binding.layoutReminderEmpty.isVisible = !state.loading && (state.error != null || state.cards.isEmpty())
        binding.btnReminderRetry.isVisible = state.error != null
        val emptyTitle = when (state.filter) {
            ReminderFilter.OVERDUE -> R.string.rr_empty_overdue
            ReminderFilter.TODAY -> R.string.rr_empty_today
            ReminderFilter.UPCOMING -> R.string.rr_empty_upcoming
        }
        binding.tvReminderEmptyTitle.setText(if (state.error != null) R.string.rr_error_title else emptyTitle)
        binding.tvReminderEmptyBody.text = state.error ?: getString(R.string.rr_empty_body)
        binding.tvReminderPermission.setText(if (state.notificationsEnabled) R.string.rr_enabled else R.string.rr_disabled)
        binding.tvReminderPermissionHint.setText(if (state.notificationsEnabled) R.string.rr_time else R.string.rr_disabled_help)
        binding.btnReminderSettings.contentDescription = getString(if (state.notificationsEnabled) R.string.rr_settings else R.string.rr_enable)
        binding.tvReminderPermission.setTextColor(ContextCompat.getColor(requireContext(),
            if (state.notificationsEnabled) R.color.rr_navy else R.color.rr_red))
        filter(binding.chipOverdue, binding.tvFilterOverdue, binding.tvCountOverdue, state.overdueCount, state.filter == ReminderFilter.OVERDUE, true)
        filter(binding.chipToday, binding.tvFilterToday, binding.tvCountToday, state.todayCount, state.filter == ReminderFilter.TODAY)
        filter(binding.chipUpcoming, binding.tvFilterUpcoming, binding.tvCountUpcoming, state.upcomingCount, state.filter == ReminderFilter.UPCOMING)
        adapter?.setNotificationsEnabled(state.notificationsEnabled)
        adapter?.submitList(state.cards)
    }

    private fun filter(card: MaterialCardView, label: TextView, count: TextView,
                       number: Int, selected: Boolean, overdue: Boolean = false) {
        val bg = if (!selected) R.color.rr_surface else if (overdue) R.color.rr_red_light else R.color.rr_blue_light
        val fg = if (!selected) R.color.rr_navy else if (overdue) R.color.rr_red else R.color.rr_blue
        val color = ContextCompat.getColor(requireContext(), fg)
        card.isSelected = selected
        card.contentDescription = "${label.text}, $number"
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), bg))
        card.strokeColor = ContextCompat.getColor(requireContext(), if (selected) fg else R.color.rr_border)
        label.setTextColor(color)
        count.text = number.toString()
        count.setTextColor(color)
        count.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) (color and 0x00FFFFFF) or 0x22000000
            else ContextCompat.getColor(requireContext(), R.color.rr_background))
        }
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, "roomkit_invoice_reminders")
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        try { startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, R.string.rr_settings_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        binding.listReminders.adapter = null
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
