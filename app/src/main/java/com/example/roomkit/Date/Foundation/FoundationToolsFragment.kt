package com.example.roomkit.Date.Foundation

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentFoundationToolsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** UI replacement only. Keep the existing Phase 3 ViewModel and repositories. */
class FoundationToolsFragment : Fragment(R.layout.fragment_foundation_tools) {
    private val vm: FoundationToolsViewModel by viewModels()
    private var restoreDialog: AlertDialog? = null
    private var inputDialog: AlertDialog? = null
    private val create = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.export(it) }
    private val open = registerForActivityResult(ActivityResultContracts.OpenDocument()) { vm.restore(it) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentFoundationToolsBinding.bind(view)
        val section = arguments?.getString("section") ?: "ALL"
        val monthOnly = section == "MONTH"; val backupOnly = section == "BACKUP"
        b.toolsMonthlySection.isVisible = !backupOnly; b.toolsBackupSection.isVisible = !monthOnly
        b.pageTitle.setText(when { backupOnly -> R.string.rks2_backup; monthOnly -> R.string.rks2_month; else -> R.string.rks2_tools })
        b.pageSubtitle.setText(if (backupOnly) R.string.rks2_backup_subtitle else R.string.rks2_month_subtitle)
        b.settingsBack.setOnClickListener { findNavController().popBackStack() }
        b.toolsMonth.setText(vm.month)
        b.toolsMonth.doAfterTextChanged { vm.month = it?.toString().orEmpty() }
        b.toolsReview.setOnClickListener { vm.refresh() }
        b.toolsClose.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.rks2_close_confirm, vm.month))
                .setMessage(R.string.rks2_close_confirm_body).setNegativeButton(R.string.rks2_cancel, null)
                .setPositiveButton(R.string.rks2_close) { _, _ -> vm.close() }.show()
        }
        b.toolsReopen.setOnClickListener { reopenDialog() }
        b.toolsExport.setOnClickListener { passwordDialog(false) }
        b.toolsRestore.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rks2_restore_confirm)
                .setMessage(R.string.rks2_restore_confirm_body).setNegativeButton(R.string.rks2_cancel, null)
                .setPositiveButton(R.string.rks2_choose_backup) { _, _ -> passwordDialog(true) }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    listOf(b.toolsReview, b.toolsClose, b.toolsReopen, b.toolsExport, b.toolsRestore,
                        b.toolsMonth, b.settingsBack).forEach { it.isEnabled = !state.busy }
                    b.toolsProgress.isVisible = state.busy
                    b.toolsStatus.isVisible = state.busy || state.message.isNotBlank()
                    b.toolsStatus.text = if (state.busy) getString(R.string.rks2_working) else state.message
                    if (state.restored && restoreDialog?.isShowing != true) {
                        restoreDialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.rks2_restore_complete)
                            .setMessage(state.message).setCancelable(false)
                            .setPositiveButton(R.string.rks2_dashboard) { _, _ ->
                                vm.acknowledgeRestore()
                                findNavController().navigate(R.id.dashboardFragment, null,
                                    navOptions { popUpTo(R.id.nav_graph) { inclusive = true } })
                            }.show()
                    }
                }
            }
        }
    }
    private fun reopenDialog() {
        val (box, field) = inputBox(getString(R.string.rks2_reason), false)
        field.filters = arrayOf(InputFilter.LengthFilter(500))
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.rks2_reopen_confirm, vm.month))
            .setMessage(R.string.rks2_reopen_note).setView(box).setNegativeButton(R.string.rks2_cancel, null)
            .setPositiveButton(R.string.rks2_reopen, null).create()
        inputDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = field.text?.toString().orEmpty().trim()
                if (reason.length !in 3..500) field.error = getString(R.string.rks2_reason)
                else { dialog.dismiss(); vm.reopen(reason) }
            }
        }
        dialog.show()
    }
    private fun passwordDialog(restoring: Boolean) {
        val (box, pass) = inputBox(getString(R.string.rks2_password), true)
        val confirm = if (restoring) null else addInput(box, getString(R.string.rks2_password_confirm), true)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (restoring) R.string.rks2_unlock else R.string.rks2_protect).setView(box)
            .setNegativeButton(R.string.rks2_cancel) { _, _ -> vm.clearPassword() }
            .setPositiveButton(R.string.rks2_continue, null).create()
        inputDialog = dialog
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = pass.text?.toString().orEmpty()
                if (value.length < 10 || (confirm != null && value != confirm.text?.toString())) {
                    pass.error = getString(R.string.rks2_password_error)
                } else {
                    vm.keepPassword(value.toCharArray())
                    pass.text?.clear(); confirm?.text?.clear(); dialog.dismiss()
                    if (restoring) open.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    else create.launch("RoomKit-backup-${java.time.LocalDate.now()}.json")
                }
            }
        }
        dialog.setOnCancelListener { vm.clearPassword() }
        dialog.show()
    }
    private fun inputBox(label: String, password: Boolean): Pair<LinearLayout, TextInputEditText> {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        return box to addInput(box, label, password)
    }
    private fun addInput(box: LinearLayout, label: String, password: Boolean): TextInputEditText {
        val layout = TextInputLayout(requireContext(), null, com.google.android.material.R.attr.textInputStyle).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = label; box.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        return TextInputEditText(layout.context).apply {
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSaveEnabled = false; layout.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    override fun onDestroyView() {
        restoreDialog?.dismiss(); restoreDialog = null
        inputDialog?.dismiss(); inputDialog = null
        super.onDestroyView()
    }
}
