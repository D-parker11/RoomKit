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

        // Month close and backups now live under More → Settings.
        view.findViewById<View>(R.id.cardFoundation)?.visibility = View.GONE

        // Navigate to Tenants List
        binding.cardTenants.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_tenantListFragment)
        }

        // Placeholders for future modules
        binding.cardReports.setOnClickListener {
            findNavController().navigate(R.id.roomkitReportsFragment)
        }

        binding.cardReminders.setOnClickListener {
            findNavController().navigate(R.id.action_moreFragment_to_remindersFragment)
        }

        binding.cardSettings.setOnClickListener {
            findNavController().navigate(R.id.roomkitSettingsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
