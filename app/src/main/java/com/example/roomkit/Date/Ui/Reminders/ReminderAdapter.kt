package com.example.roomkit.Date.Ui.Reminders

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    inner class Holder(private val binding: ItemReminderInvoiceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: ReminderCard) {
            val context = binding.root.context
            val invoice = card.invoice
            val zone = TimeZone.getTimeZone(InvoiceReminderPolicy.TIME_ZONE)
            val date = SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = zone }
            val dateTime = SimpleDateFormat("MMM d · h:mm a", Locale.US).apply { timeZone = zone }
            binding.tvReminderRoom.text = context.getString(R.string.rr_room, invoice.roomNumber) +
                    if (invoice.buildingName.isBlank()) "" else " · ${invoice.buildingName}"
            binding.tvReminderTenant.text = context.getString(R.string.rr_identity, invoice.tenantName, invoice.invoiceId)
            binding.tvReminderBalance.text = CurrencyFormatter.formatKhr(card.balanceKhr)
            binding.tvReminderDue.text = date.format(Date(invoice.dueDate))
            binding.tvReminderNext.text = when {
                !notificationsEnabled -> context.getString(R.string.rr_paused_short)
                card.reminderPending -> context.getString(R.string.rr_pending_short)
                else -> dateTime.format(Date(card.nextReminderAt))
            }
            badge(binding.tvReminderPaymentBadge,
                if (card.isPartial) R.string.rr_partial else R.string.rr_unpaid,
                if (card.isPartial) R.color.rr_blue_light else R.color.rr_amber_light,
                if (card.isPartial) R.color.rr_blue else R.color.rr_amber)
            when (card.filter) {
                ReminderFilter.OVERDUE -> badge(binding.tvReminderDueBadge, R.string.rr_overdue, R.color.rr_red_light, R.color.rr_red)
                ReminderFilter.TODAY -> badge(binding.tvReminderDueBadge, R.string.rr_today, R.color.rr_blue_light, R.color.rr_blue)
                ReminderFilter.UPCOMING -> badge(binding.tvReminderDueBadge, R.string.rr_upcoming, R.color.rr_background, R.color.rr_muted)
            }
            binding.btnOpenReminderInvoice.setOnClickListener { onOpen(invoice.invoiceId) }
        }
    }

    private fun badge(view: TextView, text: Int, background: Int, foreground: Int) {
        view.setText(text)
        view.setTextColor(ContextCompat.getColor(view.context, foreground))
        view.background = GradientDrawable().apply {
            cornerRadius = 8f * view.resources.displayMetrics.density
            setColor(ContextCompat.getColor(view.context, background))
        }
    }
    private object Diff : DiffUtil.ItemCallback<ReminderCard>() {
        override fun areItemsTheSame(oldItem: ReminderCard, newItem: ReminderCard) = oldItem.invoice.invoiceId == newItem.invoice.invoiceId
        override fun areContentsTheSame(oldItem: ReminderCard, newItem: ReminderCard) = oldItem == newItem
    }
}
