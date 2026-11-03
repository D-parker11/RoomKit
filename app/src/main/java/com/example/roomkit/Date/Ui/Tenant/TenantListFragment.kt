@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Tenant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roomkit.Date.Ui.Factory.ViewModelFactory
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentTenantListBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class TenantListFragment : Fragment() {

    private var _binding: FragmentTenantListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TenantsViewModel by viewModels {
        ViewModelFactory(requireContext())
    }

    private lateinit var adapter: TenantListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTenantListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnAddTenant.setOnClickListener {
            findNavController().navigate(R.id.action_tenantListFragment_to_addTenantFragment)
        }
        binding.btnEmptyAddTenant.setOnClickListener {
            findNavController().navigate(R.id.action_tenantListFragment_to_addTenantFragment)
        }

        setupRecyclerView()
        setupTabs()
        setupSearch()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = TenantListAdapter(
            onTenantClicked = { tenant ->
                val bundle = Bundle().apply { putLong("tenantId", tenant.tenantId) }
                findNavController().navigate(R.id.tenantDetailsFragment, bundle)
            },
            onViewRoomClicked = { tenant ->
                val roomId = tenant.currentRoomId
                if (roomId != null && roomId > 0) {
                    val bundle = Bundle().apply { putLong("roomId", roomId) }
                    findNavController().navigate(R.id.roomDetailFragment, bundle)
                } else {
                    Toast.makeText(requireContext(), "No active room assigned", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvTenants.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTenants.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayoutTenantFilter.removeAllTabs()
        binding.tabLayoutTenantFilter.addTab(binding.tabLayoutTenantFilter.newTab().setText("All (0)"))
        binding.tabLayoutTenantFilter.addTab(binding.tabLayoutTenantFilter.newTab().setText("Active (0)"))
        binding.tabLayoutTenantFilter.addTab(binding.tabLayoutTenantFilter.newTab().setText("Moved Out"))

        binding.tabLayoutTenantFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.setFilter(TenantFilter.ALL)
                    1 -> viewModel.setFilter(TenantFilter.ACTIVE)
                    2 -> viewModel.setFilter(TenantFilter.MOVED_OUT)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch() {
        binding.etSearchTenant.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Collect filtered list and toggle empty state visibility
                launch {
                    viewModel.filteredTenants.collect { list ->
                        adapter.submitList(list)
                        binding.layoutEmptyTenants.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvTenants.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.tabCounts.collect { counts ->
                        binding.tabLayoutTenantFilter.getTabAt(0)?.text = "All (${counts.allCount})"
                        binding.tabLayoutTenantFilter.getTabAt(1)?.text = "Active (${counts.activeCount})"
                        binding.tabLayoutTenantFilter.getTabAt(2)?.text = if (counts.movedOutCount > 0) {
                            "Moved Out (${counts.movedOutCount})"
                        } else {
                            "Moved Out"
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}