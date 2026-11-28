@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Payment

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
import com.example.roomkit.databinding.ItemPaymentOutstandingBinding
import com.example.roomkit.databinding.ItemPaymentRecordBinding
import java.util.Locale
import kotlin.math.roundToInt

class PaymentsActivityAdapter(
    private val onInvoiceClick: (Long) -> Unit,
    private val onRecordPaymentClick: (Long) -> Unit
) : ListAdapter<PaymentActivityItem, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) =
        if (getItem(position) is PaymentActivityItem.PaymentRecord) 1 else 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            ReceiptHolder(ItemPaymentRecordBinding.inflate(inflater, parent, false))
        } else {
            DueHolder(ItemPaymentOutstandingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PaymentActivityItem.PaymentRecord -> (holder as ReceiptHolder).bind(item)
            is PaymentActivityItem.OutstandingInvoice -> (holder as DueHolder).bind(item)
        }
    }

    private fun money(value: Long) = String.format(Locale.US, "%,d KHR", value)

    private fun initials(name: String): String = name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1) }
        .uppercase(Locale.ROOT)
        .ifEmpty { "?" }

    private fun room(number: String, building: String) =
        "Room $number" + if (building.isBlank()) "" else " · $building"

    private fun badge(
        view: TextView,
        label: String,
        @DrawableRes backgroundRes: Int,
        @ColorRes foregroundRes: Int
    ) {
        val color = ContextCompat.getColor(view.context, foregroundRes)
        val density = view.resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (5 * density).roundToInt()

        view.text = label
        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.setRoomKitTextSize(R.dimen.roomkit_text_12)
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setTextColor(color)
        view.backgroundTintList = null
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

    inner class DueHolder(private val b: ItemPaymentOutstandingBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: PaymentActivityItem.OutstandingInvoice) = with(b) {
            tvAvatar.text = initials(item.tenantName)
            tvTenantName.text = item.tenantName
            tvRoomBuilding.text = room(item.roomNumber, item.buildingName)
            tvRemainingBalance.text = money(item.remainingBalanceKhr)
            tvDueDate.text = item.dueDateFormatted
            tvPartialBreakdown.isVisible = item.totalPaidKhr > 0L
            tvPartialBreakdown.text =
                "Paid ${money(item.totalPaidKhr)} of ${money(item.invoiceTotalKhr)}"
            tvDueDate.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    if (item.isOverdue) R.color.roomkit_error else R.color.roomkit_text_secondary
                )
            )

            when {
                item.isOverdue -> badge(
                    tvStatusBadge,
                    "Overdue",
                    R.drawable.bg_rd_badge_red,
                    R.color.roomkit_error
                )
                item.totalPaidKhr > 0L -> badge(
                    tvStatusBadge,
                    "Partial",
                    R.drawable.bg_rd_badge_blue,
                    R.color.roomkit_partial
                )
                else -> badge(
                    tvStatusBadge,
                    "Unpaid",
                    R.drawable.bg_rd_badge_amber,
                    R.color.roomkit_warning
                )
            }

            btnRecordPayment.isEnabled = item.remainingBalanceKhr > 0L
            btnViewInvoice.setOnClickListener { onInvoiceClick(item.invoiceId) }
            btnRecordPayment.setOnClickListener { onRecordPaymentClick(item.invoiceId) }
        }
    }

    inner class ReceiptHolder(private val b: ItemPaymentRecordBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: PaymentActivityItem.PaymentRecord) = with(b) {
            tvAvatar.text = initials(item.tenantName)
            tvTenantName.text = item.tenantName
            tvRoomBuilding.text = room(item.roomNumber, item.buildingName)
            tvPaymentAmount.text = money(item.paymentAmountKhr)
            tvPaymentDate.text = item.paymentDateFormatted
            tvPaymentAmount.setTextColor(
                ContextCompat.getColor(
                    root.context,
                    if (item.isVoided) R.color.roomkit_text_secondary else R.color.roomkit_success
                )
            )

            val method = when (item.paymentMethod.trim().uppercase(Locale.ROOT)) {
                "DEPOSIT" -> "Deposit applied"
                "KHQR" -> "KHQR"
                "BANK", "BANK_TRANSFER" -> "Bank transfer"
                "CASH" -> "Cash"
                else -> item.paymentMethod.ifBlank { "Payment" }
            }
            tvPaymentMethod.text = method

            if (item.isVoided) {
                badge(
                    tvStatusBadge,
                    "Voided",
                    R.drawable.bg_rd_badge_red,
                    R.color.roomkit_error
                )
                tvReceiptDetail.text = "Voided payment · Excluded from collected total"
            } else {
                badge(
                    tvStatusBadge,
                    "Recorded",
                    R.drawable.bg_rd_badge_green,
                    R.color.roomkit_success
                )
                tvReceiptDetail.text = if (item.isFullyPaid) {
                    "Invoice fully paid"
                } else {
                    "Invoice balance · ${money(item.remainingBalanceKhr)}"
                }
            }

            btnViewInvoice.setOnClickListener { onInvoiceClick(item.invoiceId) }
        }
    }

    private companion object {
        val Diff = object : DiffUtil.ItemCallback<PaymentActivityItem>() {
            override fun areItemsTheSame(
                oldItem: PaymentActivityItem,
                newItem: PaymentActivityItem
            ): Boolean = when {
                oldItem is PaymentActivityItem.PaymentRecord &&
                        newItem is PaymentActivityItem.PaymentRecord ->
                    oldItem.paymentId == newItem.paymentId

                oldItem is PaymentActivityItem.OutstandingInvoice &&
                        newItem is PaymentActivityItem.OutstandingInvoice ->
                    oldItem.invoiceId == newItem.invoiceId

                else -> false
            }

            override fun areContentsTheSame(
                oldItem: PaymentActivityItem,
                newItem: PaymentActivityItem
            ) = oldItem == newItem
        }
    }
}