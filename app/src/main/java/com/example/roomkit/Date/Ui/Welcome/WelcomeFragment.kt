@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Welcome

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.roomkit.Date.Ui.Building.AddBuildingBottomSheetDialogFragment
import com.example.roomkit.MainActivity
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentWelcomeBinding
import com.google.android.material.snackbar.Snackbar
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyOpeningInsets(view)
        val b = FragmentWelcomeBinding.bind(view)
        b.welcomeSetup.setOnClickListener {
            val manager = childFragmentManager
            if (!manager.isStateSaved &&
                manager.findFragmentByTag(AddBuildingBottomSheetDialogFragment.TAG) == null) {
                AddBuildingBottomSheetDialogFragment.newInstance()
                    .show(manager, AddBuildingBottomSheetDialogFragment.TAG)
            }
        }
        val retry = MutableStateFlow(0)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                retry.collectLatest {
                    try {
                        // Also handles a property restored into Room while welcome is visible.
                        AppDatabase.getInstance(requireContext()).buildingDao()
                            .observeAllBuildings().collect { buildings ->
                                if (buildings.isNotEmpty()) {
                                    // The sheet normally dismisses itself after save. Dismiss a
                                    // restored sheet as well before leaving this destination.
                                    (childFragmentManager.findFragmentByTag(
                                        AddBuildingBottomSheetDialogFragment.TAG
                                    ) as? AddBuildingBottomSheetDialogFragment)?.dismiss()
                                    (requireActivity() as MainActivity).finishPropertySetup()
                                }
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Snackbar.make(b.root, R.string.rko_read_error, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.rko_retry) {
                                retry.value += 1
                            }.show()
                    }
                }
            }
        }
    }
}
