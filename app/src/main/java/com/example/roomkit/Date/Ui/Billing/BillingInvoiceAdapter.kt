@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Billing

import com.example.roomkit.Date.Ui.Common.setRoomKitTextSize

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemBillingInvoiceBinding
import java.util.Locale
import kotlin.math.roundToInt

class BillingInvoiceAdapter(private val onItemClick: (Long) -> Unit) :
    ListAdapter<BillingInvoiceItem, BillingInvoiceAdapter.InvoiceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = InvoiceViewHolder(
        ItemBillingInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) = holder.bind(getItem(position))

    private fun styleStatusBadge(
        view: TextView,
        @ColorRes colorRes: Int,
        @DrawableRes backgroundRes: Int
    ) {
        val color = ContextCompat.getColor(view.context, colorRes)
        val density = view.resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (5 * density).roundToInt()

        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.setRoomKitTextSize(R.dimen.roomkit_text_12)
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setTextColor(color)
        view.setBackgroundResource(backgroundRes)
        view.setPadding(padH, padV, padH, padV)
        view.compoundDrawablePadding = (6 * density).roundToInt()
        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
            view,
            R.drawable.ic_rd_status_dot,
            0,
            0,
            0
        )
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color))
    }

    inner class InvoiceViewHolder(private val binding: ItemBillingInvoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BillingInvoiceItem) {
            val context = binding.root.context
            fun string(id: Int, vararg args: Any) = context.getString(id, *args)
            fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)

            val isVoid = item.status == BillingDisplayStatus.VOID
            val isDraft = item.status == BillingDisplayStatus.DRAFT
            val overdue = !isVoid && !isDraft && item.isOverdue

            binding.tvInvoiceRoomBuilding.text = string(R.string.rb_room_number, item.roomNumber)
            binding.tvInvoiceTenantName.text = item.tenantName
            binding.tvInvoiceTotal.text = money(item.invoiceTotalKhr)
            binding.tvInvoiceTotal.setTextColor(
                ContextCompat.getColor(context, if (isVoid) R.color.roomkit_text_secondary else R.color.roomkit_text_primary)
            )
            binding.tvInvoicePeriodDueDate.text = item.dueDateText
            binding.tvInvoicePeriodDueDate.isVisible = !isVoid && !isDraft && item.dueDateText.isNotBlank()

            // Reset recycled views before applying each status.
            binding.tvInvoicePaymentDetail.text = ""
            binding.tvInvoicePaymentDetail.isVisible = false
            binding.tvInvoicePaymentDetail.setTextColor(ContextCompat.getColor(context, R.color.roomkit_text_secondary))

            val label: Int
            val color: Int
            val badgeBg: Int
            when {
                isVoid -> {
                    label = R.string.rb_void
                    color = R.color.roomkit_text_secondary
                    badgeBg = R.drawable.bg_rd_badge_neutral
                    binding.tvInvoicePaymentDetail.text = string(R.string.rb_cancelled)
                }
                isDraft -> {
                    label = R.string.rb_draft
                    color = R.color.roomkit_text_secondary
                    badgeBg = R.drawable.bg_rd_badge_neutral
                    binding.tvInvoicePaymentDetail.text = string(R.string.rb_not_issued)
                }
                overdue || item.status == BillingDisplayStatus.OVERDUE -> {
                    label = R.string.rb_overdue
                    color = R.color.roomkit_error
                    badgeBg = R.drawable.bg_rd_badge_red
                }
                item.status == BillingDisplayStatus.PAID -> {
                    label = R.string.rb_paid
                    color = R.color.roomkit_success
                    badgeBg = R.drawable.bg_rd_badge_green
                    binding.tvInvoicePaymentDetail.text = listOfNotNull(
                        string(R.string.rb_paid),
                        item.lastPaymentDateText?.takeIf { it.isNotBlank() },
                        item.lastPaymentMethodText?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                }
                item.status == BillingDisplayStatus.PARTIAL -> {
                    label = R.string.rb_partial
                    color = R.color.roomkit_partial
                    badgeBg = R.drawable.bg_rd_badge_blue
                }
                else -> {
                    label = R.string.rb_unpaid
                    color = R.color.roomkit_warning
                    badgeBg = R.drawable.bg_rd_badge_amber
                }
            }

            if (!isVoid && !isDraft && item.totalPaidKhr > 0L && item.remainingBalanceKhr > 0L) {
                binding.tvInvoicePaymentDetail.text =
                    string(R.string.rb_payment_balance, money(item.totalPaidKhr), money(item.remainingBalanceKhr))
            }
            binding.tvInvoicePaymentDetail.isVisible = binding.tvInvoicePaymentDetail.text.isNotEmpty()
            if (!isVoid && !isDraft) {
                binding.tvInvoicePaymentDetail.setTextColor(ContextCompat.getColor(context, color))
            }

            binding.tvInvoiceStatusBadge.setText(label)
            styleStatusBadge(
                view = binding.tvInvoiceStatusBadge,
                colorRes = color,
                backgroundRes = badgeBg
            )

            binding.root.setOnClickListener { onItemClick(item.invoiceId) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<BillingInvoiceItem>() {
        override fun areItemsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem) =
            oldItem.invoiceId == newItem.invoiceId

        override fun areContentsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem) =
            oldItem == newItem
    }
}