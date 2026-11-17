@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Payment

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.roomkit.R
import com.example.roomkit.databinding.FragmentPaymentSetupBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class PaymentSetupFragment : Fragment(R.layout.fragment_payment_setup) {

    private val vm: PaymentSetupViewModel by viewModels()

    private var leaveDialogShowing = false

    private val picker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                vm.importImage(uri)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val b = FragmentPaymentSetupBinding.bind(view)
        var rendering = false

        fun requestBack() {
            if (vm.state.value.busy || leaveDialogShowing) return

            if (!vm.state.value.pending) {
                findNavController().popBackStack()
                return
            }

            leaveDialogShowing = true

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Discard unsaved changes?")
                .setMessage(
                    "Your changes have not been saved. " +
                            "Stay here to confirm the recipient and save your payment setup."
                )
                .setNegativeButton("Keep editing", null)
                .setPositiveButton("Discard") { _, _ ->
                    findNavController().popBackStack()
                }
                .setOnDismissListener {
                    leaveDialogShowing = false
                }
                .show()
        }

        b.btnBack.setOnClickListener {
            requestBack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requestBack()
                }
            }
        )

        b.btnImport.setOnClickListener {
            picker.launch("image/*")
        }

        // Ignore changes made while rendering ViewModel state.
        b.confirmReceiver.setOnCheckedChangeListener { _, checked ->
            if (!rendering) {
                vm.confirm(checked)
            }
        }

        b.includePdf.setOnCheckedChangeListener { _, checked ->
            if (!rendering) {
                vm.includePdf(checked)
            }
        }

        b.btnSave.setOnClickListener {
            vm.save()
        }

        b.btnRemove.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove payment QR?")
                .setMessage(
                    "This removes your saved receiving QR and any unsaved preview. " +
                            "Previously shared images and PDFs remain unchanged."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove") { _, _ ->
                    vm.remove()
                }
                .show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    rendering = true

                    try {
                        b.accountStatus.text = when {
                            s.busy -> "Updating…"
                            s.pending -> "Unsaved changes"
                            s.qr != null -> "Saved on this device"
                            else -> "Not set up"
                        }

                        b.progress.isVisible = s.busy
                        b.btnBack.isEnabled = !s.busy

                        b.qrPreview.isVisible = s.bitmap != null
                        b.qrPreview.setImageBitmap(s.bitmap)

                        // Keep technical routing identifiers out of
                        // the main recipient summary.
                        b.receiverDetails.text = s.qr?.let { qr ->
                            val bank = qr.bank.ifBlank { "Receiving bank" }
                            "${qr.receiver}\n$bank · KHR"
                        } ?: "Import the receiving QR image saved from your banking app."

                        b.btnImport.text =
                            if (s.qr == null) {
                                "Import KHQR image"
                            } else {
                                "Replace QR image"
                            }

                        b.btnImport.isEnabled = !s.busy

                        b.includePdf.isChecked = s.includePdf
                        b.includePdf.isEnabled = !s.busy && s.qr != null

                        b.pdfHint.text = when {
                            s.qr == null ->
                                "Add a receiving QR to enable this option."

                            s.pending ->
                                "Save your changes to apply this preference."

                            s.includePdf ->
                                "Your QR will appear on new unpaid invoice PDFs."

                            else ->
                                "Your QR will not appear on invoice PDFs."
                        }

                        // Required: show the parent containing
                        // the confirmation checkbox and Save button.
                        b.confirmationGroup.isVisible = s.pending

                        b.confirmReceiver.isVisible = s.pending
                        b.confirmReceiver.isChecked = s.confirmed
                        b.confirmReceiver.isEnabled = !s.busy

                        b.btnSave.isVisible = s.pending
                        b.btnSave.isEnabled =
                            !s.busy && s.confirmed && s.qr != null

                        b.btnSave.text =
                            if (s.busy) {
                                "Saving…"
                            } else {
                                "Save payment setup"
                            }

                        b.message.text = s.message
                        b.message.isVisible = s.message.isNotBlank()

                        b.btnRemove.isVisible = s.qr != null
                        b.btnRemove.isEnabled = !s.busy
                    } finally {
                        rendering = false
                    }
                }
            }
        }
    }
}