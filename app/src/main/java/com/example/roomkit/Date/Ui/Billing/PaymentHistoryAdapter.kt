////@file:Suppress("PackageNaming")
////package com.example.roomkit.Date.Ui.Billing
////
////import android.view.LayoutInflater
////import android.view.ViewGroup
////import androidx.recyclerview.widget.DiffUtil
////import androidx.recyclerview.widget.ListAdapter
////import androidx.recyclerview.widget.RecyclerView
////import com.example.roomkit.Date.Billing.CurrencyFormatter
////import com.example.roomkit.Date.Local.Entity.PaymentEntity
////import com.example.roomkit.databinding.ItemPaymentHistoryBinding
////import java.text.SimpleDateFormat
////import java.util.Date
////import java.util.Locale
////
////class PaymentHistoryAdapter(
////    private val onItemClick: ((PaymentEntity) -> Unit)? = null
////) : ListAdapter<PaymentEntity, PaymentHistoryAdapter.PaymentViewHolder>(DiffCallback) {
////
////    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
////
////    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
////        val binding = ItemPaymentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
////        return PaymentViewHolder(binding)
////    }
////
////    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
////        holder.bind(getItem(position))
////    }
////
////    inner class PaymentViewHolder(private val binding: ItemPaymentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
////        fun bind(item: PaymentEntity) {
////            binding.tvPaymentDate.text = dateFormat.format(Date(item.paymentDate))
////
////            val formattedMethod = formatPaymentMethod(item.paymentMethod)
////            val detail = if (!item.note.isNullOrBlank()) "$formattedMethod · ${item.note}" else formattedMethod
////            binding.tvPaymentMethodAndNote.text = detail
////            binding.tvPaymentAmount.text = CurrencyFormatter.formatKhr(item.amountKhr)
////
////            binding.root.setOnClickListener {
////                onItemClick?.invoke(item)
////            }
////        }
////
////        private fun formatPaymentMethod(rawMethod: String?): String {
////            if (rawMethod.isNullOrBlank()) return "Cash"
////            return when (rawMethod.uppercase(Locale.US)) {
////                "BANK_TRANSFER", "BANK", "ABA", "WING" -> "Bank Transfer"
////                "DEPOSIT" -> "Deposit applied"
////                "KHQR", "QR" -> "KHQR"
////                "CASH" -> "Cash"
////                else -> rawMethod.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
////            }
////        }
////    }
////
////    companion object DiffCallback : DiffUtil.ItemCallback<PaymentEntity>() {
////        override fun areItemsTheSame(oldItem: PaymentEntity, newItem: PaymentEntity): Boolean =
////            oldItem.paymentId == newItem.paymentId
////
////        override fun areContentsTheSame(oldItem: PaymentEntity, newItem: PaymentEntity): Boolean =
////            oldItem == newItem
////    }
////}
//
//@file:Suppress("PackageNaming")
//
//package com.example.roomkit.Date.Ui.Billing
//
//import android.content.Context
//import android.graphics.Paint
//import android.view.LayoutInflater
//import android.view.ViewGroup
//import androidx.core.content.ContextCompat
//import androidx.core.view.isVisible
//import androidx.recyclerview.widget.DiffUtil
//import androidx.recyclerview.widget.ListAdapter
//import androidx.recyclerview.widget.RecyclerView
//import com.example.roomkit.Date.Billing.CurrencyFormatter
//import com.example.roomkit.Date.Local.Entity.PaymentEntity
//import com.example.roomkit.R
//import com.example.roomkit.databinding.ItemPaymentHistoryBinding
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//
//class PaymentHistoryAdapter(
//    private val onItemClick: ((PaymentEntity) -> Unit)? = null
//) : ListAdapter<PaymentEntity, PaymentHistoryAdapter.PaymentViewHolder>(
//    DiffCallback
//) {
//
//    private val paymentDateFormat = SimpleDateFormat(
//        "dd MMM yyyy",
//        Locale.getDefault()
//    )
//
//    private val voidDateFormat = SimpleDateFormat(
//        "dd MMM yyyy, HH:mm",
//        Locale.getDefault()
//    )
//
//    override fun onCreateViewHolder(
//        parent: ViewGroup,
//        viewType: Int
//    ): PaymentViewHolder {
//        val binding = ItemPaymentHistoryBinding.inflate(
//            LayoutInflater.from(parent.context),
//            parent,
//            false
//        )
//
//        return PaymentViewHolder(binding)
//    }
//
//    override fun onBindViewHolder(
//        holder: PaymentViewHolder,
//        position: Int
//    ) {
//        holder.bind(getItem(position))
//    }
//
//    inner class PaymentViewHolder(
//        private val binding: ItemPaymentHistoryBinding
//    ) : RecyclerView.ViewHolder(binding.root) {
//
//        fun bind(item: PaymentEntity) {
//            val context = binding.root.context
//            val isVoided = item.status.equals("VOIDED", ignoreCase = true)
//            val isDeposit = item.paymentMethod.equals(
//                "DEPOSIT",
//                ignoreCase = true
//            )
//
//            binding.tvPaymentDate.text =
//                paymentDateFormat.format(Date(item.paymentDate))
//
//            // Keep the method separate from notes and void details.
//            binding.tvPaymentMethodAndNote.text =
//                formatPaymentMethod(context, item.paymentMethod)
//
//            binding.tvPaymentAmount.text =
//                CurrencyFormatter.formatKhr(item.amountKhr)
//
//            binding.tvPaymentAmount.setTextColor(
//                ContextCompat.getColor(
//                    context,
//                    if (isVoided) {
//                        R.color.ph_secondary
//                    } else {
//                        R.color.ph_success
//                    }
//                )
//            )
//
//            // Reset both states because RecyclerView reuses its rows.
//            binding.tvPaymentAmount.paintFlags =
//                if (isVoided) {
//                    binding.tvPaymentAmount.paintFlags or
//                            Paint.STRIKE_THRU_TEXT_FLAG
//                } else {
//                    binding.tvPaymentAmount.paintFlags and
//                            Paint.STRIKE_THRU_TEXT_FLAG.inv()
//                }
//
//            binding.tvPaymentStatus.setText(
//                when {
//                    isVoided -> R.string.ph_voided
//                    isDeposit -> R.string.ph_applied
//                    else -> R.string.ph_received
//                }
//            )
//
//            binding.tvPaymentStatus.setTextColor(
//                ContextCompat.getColor(
//                    context,
//                    if (isVoided) {
//                        R.color.ph_void_text
//                    } else {
//                        R.color.ph_success
//                    }
//                )
//            )
//
//            val note = item.note?.trim().orEmpty()
//            binding.tvPaymentNote.text = note
//            binding.tvPaymentNote.isVisible = note.isNotEmpty()
//
//            binding.cardVoidDetails.isVisible = isVoided
//
//            if (isVoided) {
//                binding.tvVoidReason.text =
//                    item.voidReason
//                        ?.trim()
//                        ?.takeIf { it.isNotEmpty() }
//                        ?: context.getString(R.string.ph_reason_missing)
//
//                binding.tvVoidedAt.text = item.voidedAt?.let { timestamp ->
//                    context.getString(
//                        R.string.ph_void_date,
//                        voidDateFormat.format(Date(timestamp))
//                    )
//                } ?: context.getString(R.string.ph_void_date_missing)
//            } else {
//                binding.tvVoidReason.text = null
//                binding.tvVoidedAt.text = null
//            }
//
//            binding.root.setOnClickListener {
//                onItemClick?.invoke(item)
//            }
//        }
//    }
//
//    private fun formatPaymentMethod(
//        context: Context,
//        method: String
//    ): String {
//        val resource = when (method.uppercase(Locale.ROOT)) {
//            "CASH" -> R.string.ph_cash
//            "BANK", "BANK_TRANSFER", "ABA", "WING" -> R.string.ph_bank
//            "KHQR", "QR" -> R.string.ph_khqr
//            "DEPOSIT" -> R.string.ph_deposit
//            else -> null
//        }
//
//        return resource?.let { context.getString(it) }
//            ?: method.takeIf { it.isNotBlank() }
//            ?: context.getString(R.string.ph_method_unknown)
//    }
//
//    private companion object DiffCallback :
//        DiffUtil.ItemCallback<PaymentEntity>() {
//
//        override fun areItemsTheSame(
//            oldItem: PaymentEntity,
//            newItem: PaymentEntity
//        ): Boolean = oldItem.paymentId == newItem.paymentId
//
//        override fun areContentsTheSame(
//            oldItem: PaymentEntity,
//            newItem: PaymentEntity
//        ): Boolean = oldItem == newItem
//    }
//}
@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Billing

import android.content.Context
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemPaymentHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentHistoryAdapter(private val onItemClick: ((PaymentEntity) -> Unit)? = null) :
    ListAdapter<PaymentEntity, PaymentHistoryAdapter.PaymentViewHolder>(DiffCallback) {
    private val paymentDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val voidDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PaymentViewHolder(
        ItemPaymentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) = holder.bind(getItem(position))

    inner class PaymentViewHolder(private val binding: ItemPaymentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PaymentEntity) {
            val context = binding.root.context
            val isVoided = item.status.trim().uppercase(Locale.ROOT) in setOf("VOID", "VOIDED")
            val isDeposit = item.paymentMethod.equals("DEPOSIT", true)
            binding.tvPaymentDate.text = paymentDateFormat.format(Date(item.paymentDate))
            binding.tvPaymentMethodAndNote.text = formatPaymentMethod(context, item.paymentMethod)
            binding.tvPaymentAmount.text = CurrencyFormatter.formatKhr(item.amountKhr)
            binding.tvPaymentAmount.setTextColor(ContextCompat.getColor(context,
                if (isVoided) R.color.ph_secondary else R.color.ph_success))
            binding.tvPaymentAmount.paintFlags = if (isVoided)
                binding.tvPaymentAmount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else binding.tvPaymentAmount.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.tvPaymentStatus.setText(when {
                isVoided -> R.string.ph_voided
                isDeposit -> R.string.ph_applied
                else -> R.string.ph_received
            })
            binding.tvPaymentStatus.setTextColor(ContextCompat.getColor(context,
                if (isVoided) R.color.ph_void_text else R.color.ph_success))
            val note = item.note?.trim().orEmpty()
            binding.tvPaymentNote.text = note
            binding.tvPaymentNote.isVisible = note.isNotEmpty()
            val reference = item.referenceNumber?.trim().orEmpty()
            binding.tvPaymentReference.text = if (reference.isNotEmpty())
                context.getString(R.string.if_payment_reference_value, reference) else null
            binding.tvPaymentReference.isVisible = reference.isNotEmpty()
            binding.cardVoidDetails.isVisible = isVoided
            if (isVoided) {
                binding.tvVoidReason.text = item.voidReason?.trim()?.takeIf { it.isNotEmpty() }
                    ?: context.getString(R.string.ph_reason_missing)
                binding.tvVoidedAt.text = item.voidedAt?.let {
                    context.getString(R.string.ph_void_date, voidDateFormat.format(Date(it)))
                } ?: context.getString(R.string.ph_void_date_missing)
            } else {
                binding.tvVoidReason.text = null
                binding.tvVoidedAt.text = null
            }
            binding.root.setOnClickListener { onItemClick?.invoke(item) }
        }
    }
    private fun formatPaymentMethod(context: Context, method: String): String {
        val resource = when (method.uppercase(Locale.ROOT)) {
            "CASH" -> R.string.ph_cash
            "BANK", "BANK_TRANSFER", "ABA", "WING" -> R.string.ph_bank
            "KHQR", "QR" -> R.string.ph_khqr
            "DEPOSIT" -> R.string.ph_deposit
            else -> null
        }
        return resource?.let { context.getString(it) } ?: method.takeIf { it.isNotBlank() }
        ?: context.getString(R.string.ph_method_unknown)
    }
    private companion object DiffCallback : DiffUtil.ItemCallback<PaymentEntity>() {
        override fun areItemsTheSame(oldItem: PaymentEntity, newItem: PaymentEntity) = oldItem.paymentId == newItem.paymentId
        override fun areContentsTheSame(oldItem: PaymentEntity, newItem: PaymentEntity) = oldItem == newItem
    }
}
