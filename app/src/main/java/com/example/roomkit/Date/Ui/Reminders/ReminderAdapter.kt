@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Reminders

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Reminders.InvoiceReminderPolicy
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemReminderInvoiceBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

class RemindersAdapter(private val onOpen: (Long) -> Unit) :
    ListAdapter<ReminderCard, RemindersAdapter.Holder>(Diff) {

    private var notificationsEnabled = false

    fun setNotificationsEnabled(value: Boolean) {
        if (notificationsEnabled == value) return
        notificationsEnabled = value
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemReminderInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemReminderInvoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: ReminderCard) {
            val context = binding.root.context
            val invoice = card.invoice
            val zone = TimeZone.getTimeZone(InvoiceReminderPolicy.TIME_ZONE)
            val date = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = zone }
            val dateTime = SimpleDateFormat("MMM d · h:mm a", Locale.US).apply { timeZone = zone }

            binding.tvReminderRoom.text = context.getString(R.string.rr_room, invoice.roomNumber) +
                    if (invoice.buildingName.isBlank()) "" else " · ${invoice.buildingName}"
            binding.tvReminderTenant.text =
                context.getString(R.string.rr_identity, invoice.tenantName, invoice.invoiceId)
            binding.tvReminderBalance.text = CurrencyFormatter.formatKhr(card.balanceKhr)
            binding.tvReminderDue.text = date.format(Date(invoice.dueDate))
            binding.tvReminderNext.text = when {
                !notificationsEnabled -> context.getString(R.string.rr_paused_short)
                card.reminderPending -> context.getString(R.string.rr_pending_short)
                else -> dateTime.format(Date(card.nextReminderAt))
            }

            styleStatusBadge(
                view = binding.tvReminderPaymentBadge,
                textRes = if (card.isPartial) R.string.rr_partial else R.string.rr_unpaid,
                backgroundRes = if (card.isPartial) R.drawable.bg_rd_badge_blue else R.drawable.bg_rd_badge_amber,
                foregroundRes = if (card.isPartial) R.color.rr_blue else R.color.rr_amber
            )

            when (card.filter) {
                ReminderFilter.OVERDUE -> styleStatusBadge(
                    view = binding.tvReminderDueBadge,
                    textRes = R.string.rr_overdue,
                    backgroundRes = R.drawable.bg_rd_badge_red,
                    foregroundRes = R.color.rr_red
                )
                ReminderFilter.TODAY -> styleStatusBadge(
                    view = binding.tvReminderDueBadge,
                    textRes = R.string.rr_today,
                    backgroundRes = R.drawable.bg_rd_badge_blue,
                    foregroundRes = R.color.rr_blue
                )
                ReminderFilter.UPCOMING -> styleStatusBadge(
                    view = binding.tvReminderDueBadge,
                    textRes = R.string.rr_upcoming,
                    backgroundRes = R.drawable.bg_rd_badge_neutral,
                    foregroundRes = R.color.rr_muted
                )
            }

            binding.btnOpenReminderInvoice.setOnClickListener { onOpen(invoice.invoiceId) }
        }
    }

    private fun styleStatusBadge(
        view: TextView,
        @StringRes textRes: Int,
        @DrawableRes backgroundRes: Int,
        @ColorRes foregroundRes: Int
    ) {
        val color = ContextCompat.getColor(view.context, foregroundRes)
        val density = view.resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (5 * density).roundToInt()

        view.setText(textRes)
        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.textSize = 12f
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

    private object Diff : DiffUtil.ItemCallback<ReminderCard>() {
        override fun areItemsTheSame(oldItem: ReminderCard, newItem: ReminderCard) =
            oldItem.invoice.invoiceId == newItem.invoice.invoiceId

        override fun areContentsTheSame(oldItem: ReminderCard, newItem: ReminderCard) =
            oldItem == newItem
    }
}