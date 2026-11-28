package com.example.roomkit

import android.app.Activity
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.roomkit.databinding.FragmentInvoiceDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RoomKitDialogInflationTest {
    @Test
    fun explicitReviewChoiceAndInputDialogsInflate() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_RoomKit)
        val activity = controller.setup().get()
        try {
            val builders = listOf(
                MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_RoomKit_Dialog)
                    .setTitle("Review").setMessage("Review details"),
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Choose tenant").setSingleChoiceItems(arrayOf("Tenant"), 0, null),
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Invoice history").setItems(arrayOf("Invoice #1"), null),
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Reason").setView(EditText(activity))
            )
            builders.forEach { builder ->
                val dialog = builder.setNegativeButton("Cancel", null).create()
                try {
                    dialog.show()
                    assertNotNull(dialog.getButton(AlertDialog.BUTTON_NEGATIVE))
                } finally {
                    dialog.dismiss()
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun issueAlertInflatesWithDefaultThemeAndConfirmsOnce() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_RoomKit)
        val activity = controller.setup().get()
        var confirmations = 0
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Issue this invoice?")
            .setMessage("Review this invoice before issuing.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Issue invoice") { _, _ -> confirmations++ }
            .create()
        try {
            // Regression: the title view previously had no layout_width/height
            // because a TextAppearance was supplied as its complete view style.
            dialog.show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, confirmations)
            assertFalse(dialog.isShowing)
        } finally {
            dialog.dismiss()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun invoiceDetailLayoutInflatesAfterIssue() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_RoomKit)
        val activity = controller.setup().get()
        try {
            val binding = FragmentInvoiceDetailBinding.inflate(activity.layoutInflater)
            assertNotNull(binding.rvPaymentHistory)
            assertNotNull(binding.btnRecordPayment)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
