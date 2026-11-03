@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.databinding.ItemBillingInvoiceBinding

class BillingInvoiceAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<BillingInvoiceItem, BillingInvoiceAdapter.InvoiceViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val binding = ItemBillingInvoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return InvoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InvoiceViewHolder(
        private val binding: ItemBillingInvoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BillingInvoiceItem) {
            val buildingSuffix = if (item.buildingName.isNotBlank()) " · ${item.buildingName}" else ""
            binding.tvInvoiceRoomBuilding.text = "Room ${item.roomNumber}$buildingSuffix"
            binding.tvInvoiceTenantName.text = item.tenantName
            binding.tvInvoicePeriodDueDate.text = "${item.billingPeriod} · ${item.dueDateText}"

            // Original full invoice amount on right
            binding.tvInvoiceTotal.text = CurrencyFormatter.formatKhr(item.invoiceTotalKhr)

            binding.tvInvoiceOverdueBadge.visibility = if (item.isOverdue) View.VISIBLE else View.GONE

            // Setup Badge & Payment Footer depending on status
            when (item.status) {
                BillingDisplayStatus.DRAFT -> {
                    bindBadge("Draft", "#F3F4F6".toColorInt(), "#6B7280".toColorInt())
                    binding.layoutPaidDetail.visibility = View.VISIBLE
                    binding.tvInvoicePaymentDetail.text = "Not issued · Tap to open"
                    binding.tvInvoicePaymentDetail.setTextColor("#6B7280".toColorInt())
                }

                BillingDisplayStatus.VOID -> {
                    bindBadge("Void", "#F3F4F6".toColorInt(), "#6B7280".toColorInt())
                    binding.layoutPaidDetail.visibility = View.VISIBLE
                    binding.tvInvoicePaymentDetail.text = "Cancelled · No payment due"
                    binding.tvInvoicePaymentDetail.setTextColor("#6B7280".toColorInt())
                }

                BillingDisplayStatus.PAID -> {
                    bindBadge("Paid", "#ECFDF5".toColorInt(), "#059669".toColorInt())
                    binding.layoutPaidDetail.visibility = View.VISIBLE

                    val method = item.lastPaymentMethodText ?: "Payment"
                    val dateText = item.lastPaymentDateText ?: ""
                    binding.tvInvoicePaymentDetail.text = if (dateText.isNotEmpty()) {
                        "Paid $dateText · $method"
                    } else {
                        "Paid · $method"
                    }
                    binding.tvInvoicePaymentDetail.setTextColor("#059669".toColorInt())
                }
                BillingDisplayStatus.PARTIAL -> {
                    bindBadge("Partial", "#EFF6FF".toColorInt(), "#2563EB".toColorInt())
                    binding.layoutPaidDetail.visibility = View.VISIBLE

                    val paidFormatted = CurrencyFormatter.formatKhr(item.totalPaidKhr)
                    val balanceFormatted = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)
                    binding.tvInvoicePaymentDetail.text = "Paid $paidFormatted · Balance $balanceFormatted"
                    binding.tvInvoicePaymentDetail.setTextColor("#2563EB".toColorInt())
                }
                BillingDisplayStatus.OVERDUE -> {
                    bindBadge("Overdue", "#FEE2E2".toColorInt(), "#DC2626".toColorInt())

                    if (item.totalPaidKhr > 0L) {
                        binding.layoutPaidDetail.visibility = View.VISIBLE
                        val paidFormatted = CurrencyFormatter.formatKhr(item.totalPaidKhr)
                        val balanceFormatted = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)
                        binding.tvInvoicePaymentDetail.text = "Paid $paidFormatted · Balance $balanceFormatted"
                        binding.tvInvoicePaymentDetail.setTextColor("#DC2626".toColorInt())
                    } else {
                        binding.layoutPaidDetail.visibility = View.GONE
                    }
                }
                BillingDisplayStatus.UNPAID -> {
                    bindBadge("Unpaid", "#FEF3C7".toColorInt(), "#D97706".toColorInt())
                    binding.layoutPaidDetail.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener {
                onItemClick(item.invoiceId)
            }
        }

        private fun bindBadge(text: String, bgColor: Int, textColor: Int) {
            binding.tvInvoiceStatusBadge.text = text
            binding.tvInvoiceStatusBadge.setTextColor(textColor)
            val drawable = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(bgColor)
            }
            binding.tvInvoiceStatusBadge.background = drawable
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BillingInvoiceItem>() {
        override fun areItemsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem): Boolean {
            return oldItem.invoiceId == newItem.invoiceId
        }

        override fun areContentsTheSame(oldItem: BillingInvoiceItem, newItem: BillingInvoiceItem): Boolean {
            return oldItem == newItem
        }
    }
}
