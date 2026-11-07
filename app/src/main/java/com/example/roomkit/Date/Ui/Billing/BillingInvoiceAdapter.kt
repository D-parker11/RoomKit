@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemBillingInvoiceBinding
import java.util.Locale

class BillingInvoiceAdapter(private val onItemClick: (Long) -> Unit) :
    ListAdapter<BillingInvoiceItem, BillingInvoiceAdapter.InvoiceViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = InvoiceViewHolder(
        ItemBillingInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) = holder.bind(getItem(position))

    inner class InvoiceViewHolder(private val binding: ItemBillingInvoiceBinding) : RecyclerView.ViewHolder(binding.root) {
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
            binding.tvInvoiceTotal.setTextColor(ContextCompat.getColor(context, if (isVoid) R.color.rb_muted else R.color.rb_ink))
            binding.tvInvoicePeriodDueDate.text = item.dueDateText
            binding.tvInvoicePeriodDueDate.isVisible = !isVoid && !isDraft && item.dueDateText.isNotBlank()
            // Reset recycled views before applying each status.
            binding.tvInvoicePaymentDetail.text = ""
            binding.tvInvoicePaymentDetail.isVisible = false
            binding.tvInvoicePaymentDetail.setTextColor(ContextCompat.getColor(context, R.color.rb_muted))
            val label: Int
            val color: Int
            val background: Int
            when {
                isVoid -> {
                    label = R.string.rb_void; color = R.color.rb_muted; background = R.color.rb_badge_neutral
                    binding.tvInvoicePaymentDetail.text = string(R.string.rb_cancelled)
                }
                isDraft -> {
                    label = R.string.rb_draft; color = R.color.rb_muted; background = R.color.rb_badge_neutral
                    binding.tvInvoicePaymentDetail.text = string(R.string.rb_not_issued)
                }
                overdue || item.status == BillingDisplayStatus.OVERDUE -> {
                    label = R.string.rb_overdue; color = R.color.rb_red; background = R.color.rb_badge_red
                }
                item.status == BillingDisplayStatus.PAID -> {
                    label = R.string.rb_paid; color = R.color.rb_green; background = R.color.rb_badge_green
                    binding.tvInvoicePaymentDetail.text = listOfNotNull(
                        string(R.string.rb_paid),
                        item.lastPaymentDateText?.takeIf { it.isNotBlank() },
                        item.lastPaymentMethodText?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                }
                item.status == BillingDisplayStatus.PARTIAL -> {
                    label = R.string.rb_partial; color = R.color.rb_blue; background = R.color.rb_badge_blue
                }
                else -> {
                    label = R.string.rb_unpaid; color = R.color.rb_amber; background = R.color.rb_badge_amber
                }
            }
            if (!isVoid && !isDraft && item.totalPaidKhr > 0L && item.remainingBalanceKhr > 0L) {
                binding.tvInvoicePaymentDetail.text = string(R.string.rb_payment_balance, money(item.totalPaidKhr), money(item.remainingBalanceKhr))
            }
            binding.tvInvoicePaymentDetail.isVisible = binding.tvInvoicePaymentDetail.text.isNotEmpty()
            if (!isVoid && !isDraft) binding.tvInvoicePaymentDetail.setTextColor(ContextCompat.getColor(context, color))
            binding.tvInvoiceStatusBadge.setText(label)
            binding.tvInvoiceStatusBadge.setTextColor(ContextCompat.getColor(context, color))
            binding.tvInvoiceStatusBadge.background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, background))
                cornerRadius = 20f * context.resources.displayMetrics.density
            }
            binding.root.setOnClickListener { onItemClick(item.invoiceId) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<BillingInvoiceItem>() {
        override fun areItemsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem) = oldItem.invoiceId == newItem.invoiceId
        override fun areContentsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem) = oldItem == newItem
    }
}
