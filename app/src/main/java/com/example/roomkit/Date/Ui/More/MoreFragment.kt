@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.More

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.Date.Ui.Reminders.RemindersViewModel
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentMoreBinding
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {
    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!
    private val remindersViewModel: RemindersViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardTenants.setOnClickListener { open(R.id.action_moreFragment_to_tenantListFragment) }
        binding.cardReports.setOnClickListener { open(R.id.roomkitReportsFragment) }
        binding.cardReminders.setOnClickListener { open(R.id.action_moreFragment_to_remindersFragment) }
        binding.cardSettings.setOnClickListener { open(R.id.roomkitSettingsFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                remindersViewModel.uiState.collect { state ->
                    if (!state.loading) {
                        renderReminderBadge(state.overdueCount + state.todayCount)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        remindersViewModel.refresh()
    }

    private fun renderReminderBadge(urgentCount: Int) {
        if (_binding == null) return
        binding.tvRemindersBadge.isVisible = urgentCount > 0
        if (urgentCount > 0) {
            binding.tvRemindersBadge.text = if (urgentCount > 99) "99+" else urgentCount.toString()
        }
    }

    private fun open(destination: Int) {
        val nav = findNavController()
        if (!parentFragmentManager.isStateSaved && nav.currentDestination?.id == R.id.moreFragment) {
            nav.navigate(destination)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}