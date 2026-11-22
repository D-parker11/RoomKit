@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Welcome

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.roomkit.MainActivity
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentStartupBinding
import kotlinx.coroutines.launch

class StartupFragment : Fragment(R.layout.fragment_startup) {
    private val vm: StartupViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyOpeningInsets(view)
        val b = FragmentStartupBinding.bind(view)
        b.startupRetry.setOnClickListener { vm.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.state.collect { state ->
                    b.startupProgress.isVisible = state.loading
                    b.startupRetry.isVisible = state.failed
                    b.startupError.isVisible = state.failed
                    state.hasProperty?.let { (requireActivity() as MainActivity).finishStartup(it) }
                }
            }
        }
    }
}
