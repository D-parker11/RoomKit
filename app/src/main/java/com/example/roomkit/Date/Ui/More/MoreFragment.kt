@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.More

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {
    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardTenants.setOnClickListener { open(R.id.action_moreFragment_to_tenantListFragment) }
        binding.cardReports.setOnClickListener { open(R.id.roomkitReportsFragment) }
        binding.cardReminders.setOnClickListener { open(R.id.action_moreFragment_to_remindersFragment) }
        binding.cardSettings.setOnClickListener { open(R.id.roomkitSettingsFragment) }
        @Suppress("DEPRECATION")
        val version = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName.orEmpty()
        binding.tvAppVersion.text = getString(R.string.rm_version, version)
    }
    private fun open(destination: Int) {
        val nav = findNavController()
        // Prevent a second rapid tap from navigating again after leaving More.
        if (nav.currentDestination?.id == R.id.moreFragment) nav.navigate(destination)
    }
    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
