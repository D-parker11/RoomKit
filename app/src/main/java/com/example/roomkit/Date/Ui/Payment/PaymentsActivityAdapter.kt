//@file:Suppress("PackageNaming")
//package com.example.roomkit.Date.Ui.Payment
//
//import android.graphics.drawable.GradientDrawable
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.TextView
//import androidx.core.graphics.toColorInt
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.example.roomkit.Date.Billing.CurrencyFormatter
//import com.example.roomkit.databinding.ItemPaymentOutstandingBinding
//import com.example.roomkit.databinding.ItemPaymentRecordBinding
//
//class PaymentsActivityAdapter(
//    private val onInvoiceClick: (Long) -> Unit,
//    private val onRecordPaymentClick: (Long) -> Unit
//) : ListAdapter<PaymentActivityItem, RecyclerView.ViewHolder>(DiffCallback) {
//
//    companion object {
//        private const val TYPE_PAYMENT = 1
//        private const val TYPE_OUTSTANDING = 2
//
//        private val DiffCallback = object : DiffUtil.ItemCallback<PaymentActivityItem>() {
//            override fun areItemsTheSame(oldItem: PaymentActivityItem, newItem: PaymentActivityItem): Boolean {
//                return oldItem.id == newItem.id
//            }
//
//            override fun areContentsTheSame(oldItem: PaymentActivityItem, newItem: PaymentActivityItem): Boolean {
//                return oldItem == newItem
//            }
//        }
//    }
//
//    override fun getItemViewType(position: Int): Int {
//        return when (getItem(position)) {
//            is PaymentActivityItem.PaymentRecord -> TYPE_PAYMENT
//            is PaymentActivityItem.OutstandingInvoice -> TYPE_OUTSTANDING
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
//        val inflater = LayoutInflater.from(parent.context)
//        return if (viewType == TYPE_PAYMENT) {
//            val binding = ItemPaymentRecordBinding.inflate(inflater, parent, false)
//            PaymentRecordViewHolder(binding)
//        } else {
//            val binding = ItemPaymentOutstandingBinding.inflate(inflater, parent, false)
//            OutstandingViewHolder(binding)
//        }
//    }
//
//    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
//        when (val item = getItem(position)) {
//            is PaymentActivityItem.PaymentRecord -> (holder as PaymentRecordViewHolder).bind(item)
//            is PaymentActivityItem.OutstandingInvoice -> (holder as OutstandingViewHolder).bind(item)
//        }
//    }
//
//    inner class PaymentRecordViewHolder(private val b: ItemPaymentRecordBinding) : RecyclerView.ViewHolder(b.root) {
//        fun bind(item: PaymentActivityItem.PaymentRecord) {
//            b.tvPaymentDate.text = item.paymentDateFormatted
//            b.tvTenantName.text = item.tenantName
//            b.tvRoomBuilding.text = "Room ${item.roomNumber} · ${item.buildingName}"
//            b.tvPaymentAmount.text = CurrencyFormatter.formatKhr(item.paymentAmountKhr)
//
//            val methodLabel = when (item.paymentMethod.uppercase()) {
//                "DEPOSIT" -> "Deposit applied"
//                "KHQR" -> "KHQR"
//                "BANK_TRANSFER", "BANK" -> "Bank Transfer"
//                else -> "Cash"
//            }
//            b.tvPaymentMethod.text = methodLabel
//
//            if (item.isFullyPaid) {
//                applyBadge(b.tvStatusBadge, "Paid", "#ECFDF5".toColorInt(), "#059669".toColorInt())
//                b.layoutPartialFooter.visibility = View.GONE
//            } else {
//                applyBadge(b.tvStatusBadge, "Partial", "#EFF6FF".toColorInt(), "#2563EB".toColorInt())
//                b.layoutPartialFooter.visibility = View.VISIBLE
//                val invTotal = CurrencyFormatter.formatKhr(item.invoiceTotalKhr)
//                val bal = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)
//                b.tvPartialBreakdown.text = "Invoice $invTotal · Balance $bal"
//            }
//
//            b.root.setOnClickListener { onInvoiceClick(item.invoiceId) }
//        }
//    }
//
//    inner class OutstandingViewHolder(private val b: ItemPaymentOutstandingBinding) : RecyclerView.ViewHolder(b.root) {
//        fun bind(item: PaymentActivityItem.OutstandingInvoice) {
//            b.tvDueDate.text = item.dueDateFormatted
//            b.tvTenantName.text = item.tenantName
//            b.tvRoomBuilding.text = "Room ${item.roomNumber} · ${item.buildingName}"
//            b.tvRemainingBalance.text = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)
//
//            if (item.isOverdue) {
//                applyBadge(b.tvStatusBadge, "Overdue", "#FEE2E2".toColorInt(), "#DC2626".toColorInt())
//                b.tvRemainingBalance.setTextColor("#DC2626".toColorInt())
//            } else {
//                applyBadge(b.tvStatusBadge, "Unpaid", "#FEF3C7".toColorInt(), "#D97706".toColorInt())
//                b.tvRemainingBalance.setTextColor("#D97706".toColorInt())
//            }
//
//            b.btnViewInvoice.setOnClickListener { onInvoiceClick(item.invoiceId) }
//            b.btnRecordPayment.setOnClickListener { onRecordPaymentClick(item.invoiceId) }
//            b.root.setOnClickListener { onInvoiceClick(item.invoiceId) }
//        }
//    }
//
//    private fun applyBadge(tv: TextView, text: String, bgColor: Int, textColor: Int) {
//        tv.text = text
//        tv.setTextColor(textColor)
//        tv.background = GradientDrawable().apply {
//            cornerRadius = 24f
//            setColor(bgColor)
//        }
//    }
//}

@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Payment

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.databinding.ItemPaymentOutstandingBinding
import com.example.roomkit.databinding.ItemPaymentRecordBinding

class PaymentsActivityAdapter(
    private val onInvoiceClick: (Long) -> Unit,
    private val onRecordPaymentClick: (Long) -> Unit
) : ListAdapter<PaymentActivityItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_PAYMENT = 1
        private const val TYPE_OUTSTANDING = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<PaymentActivityItem>() {
            override fun areItemsTheSame(oldItem: PaymentActivityItem, newItem: PaymentActivityItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PaymentActivityItem, newItem: PaymentActivityItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PaymentActivityItem.PaymentRecord -> TYPE_PAYMENT
            is PaymentActivityItem.OutstandingInvoice -> TYPE_OUTSTANDING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_PAYMENT) {
            val binding = ItemPaymentRecordBinding.inflate(inflater, parent, false)
            PaymentRecordViewHolder(binding)
        } else {
            val binding = ItemPaymentOutstandingBinding.inflate(inflater, parent, false)
            OutstandingViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PaymentActivityItem.PaymentRecord -> (holder as PaymentRecordViewHolder).bind(item)
            is PaymentActivityItem.OutstandingInvoice -> (holder as OutstandingViewHolder).bind(item)
        }
    }

    inner class PaymentRecordViewHolder(private val b: ItemPaymentRecordBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: PaymentActivityItem.PaymentRecord) {
            b.tvPaymentDate.text = item.paymentDateFormatted
            b.tvTenantName.text = item.tenantName
            b.tvRoomBuilding.text = "Room ${item.roomNumber} · ${item.buildingName}"
            b.tvPaymentAmount.text = CurrencyFormatter.formatKhr(item.paymentAmountKhr)

            val methodLabel = when (item.paymentMethod.uppercase()) {
                "DEPOSIT" -> "Deposit applied"
                "KHQR" -> "KHQR"
                "BANK_TRANSFER", "BANK" -> "Bank Transfer"
                else -> "Cash"
            }
            b.tvPaymentMethod.text = methodLabel

            if (item.isVoided) {
                applyBadge(b.tvStatusBadge, b.root.context.getString(com.example.roomkit.R.string.pv_voided), "#FEE2E2".toColorInt(), "#DC2626".toColorInt())
                b.layoutPartialFooter.visibility = View.GONE
            } else if (item.isFullyPaid) {
                applyBadge(b.tvStatusBadge, "Paid", "#ECFDF5".toColorInt(), "#059669".toColorInt())
                b.layoutPartialFooter.visibility = View.GONE
            } else {
                applyBadge(b.tvStatusBadge, "Partial", "#EFF6FF".toColorInt(), "#2563EB".toColorInt())
                b.layoutPartialFooter.visibility = View.VISIBLE
                val invTotal = CurrencyFormatter.formatKhr(item.invoiceTotalKhr)
                val bal = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)
                b.tvPartialBreakdown.text = "Invoice $invTotal · Balance $bal"
            }

            b.root.setOnClickListener { onInvoiceClick(item.invoiceId) }
        }
    }

    inner class OutstandingViewHolder(private val b: ItemPaymentOutstandingBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: PaymentActivityItem.OutstandingInvoice) {
            b.tvDueDate.text = item.dueDateFormatted
            b.tvTenantName.text = item.tenantName
            b.tvRoomBuilding.text = "Room ${item.roomNumber} · ${item.buildingName}"
            b.tvRemainingBalance.text = CurrencyFormatter.formatKhr(item.remainingBalanceKhr)

            if (item.isOverdue) {
                applyBadge(b.tvStatusBadge, "Overdue", "#FEE2E2".toColorInt(), "#DC2626".toColorInt())
                b.tvRemainingBalance.setTextColor("#DC2626".toColorInt())
            } else {
                applyBadge(b.tvStatusBadge, "Unpaid", "#FEF3C7".toColorInt(), "#D97706".toColorInt())
                b.tvRemainingBalance.setTextColor("#D97706".toColorInt())
            }

            b.btnViewInvoice.setOnClickListener { onInvoiceClick(item.invoiceId) }
            b.btnRecordPayment.setOnClickListener { onRecordPaymentClick(item.invoiceId) }
            b.root.setOnClickListener { onInvoiceClick(item.invoiceId) }
        }
    }

    private fun applyBadge(tv: TextView, text: String, bgColor: Int, textColor: Int) {
        tv.text = text
        tv.setTextColor(textColor)
        tv.background = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(bgColor)
        }
    }
}