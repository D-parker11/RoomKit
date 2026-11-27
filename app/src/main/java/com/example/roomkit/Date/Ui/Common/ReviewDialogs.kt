@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Common

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.example.roomkit.R
import com.example.roomkit.databinding.DialogReviewBinding
import com.example.roomkit.databinding.ItemDialogReviewSectionBinding
import com.example.roomkit.databinding.ItemSummaryRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Display-only data: callers retain validation and the operation being confirmed. */
data class ReviewSection(
    val title: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val body: String? = null
)

/** Shared compact review with scrolling details and actions that remain visible. */
object ReviewDialogs {
    fun create(
        context: Context,
        title: String,
        description: String,
        sections: List<ReviewSection>,
        confirmLabel: String,
        cancelLabel: String = context.getString(android.R.string.cancel),
        onConfirm: () -> Unit
    ): AlertDialog {
        val builder = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_RoomKit_Dialog)
        val inflater = LayoutInflater.from(builder.context)
        val binding = DialogReviewBinding.inflate(inflater)
        binding.reviewTitle.text = title
        binding.reviewDescription.text = description
        binding.reviewDescription.isVisible = description.isNotBlank()
        sections.forEach { section ->
            val card = ItemDialogReviewSectionBinding.inflate(inflater, binding.reviewSections, false)
            card.sectionTitle.text = section.title
            card.sectionTitle.isVisible = !section.title.isNullOrBlank()
            section.rows.forEach { (label, value) ->
                val row = ItemSummaryRowBinding.inflate(inflater, card.sectionRows, false)
                row.tvSummaryRowLabel.text = label
                row.tvSummaryRowValue.text = value
                card.sectionRows.addView(row.root)
            }
            card.sectionBody.text = section.body
            card.sectionBody.isVisible = !section.body.isNullOrBlank()
            binding.reviewSections.addView(card.root)
        }
        val dialog = builder.setView(binding.root).create()
        binding.reviewConfirm.text = confirmLabel
        binding.reviewCancel.text = cancelLabel
        binding.reviewCancel.setOnClickListener { dialog.dismiss() }
        binding.reviewConfirm.setOnClickListener {
            if (!dialog.isShowing || !binding.reviewConfirm.isEnabled) return@setOnClickListener
            binding.reviewConfirm.isEnabled = false
            binding.reviewCancel.isEnabled = false
            dialog.dismiss()
            onConfirm()
        }
        return dialog
    }
}
