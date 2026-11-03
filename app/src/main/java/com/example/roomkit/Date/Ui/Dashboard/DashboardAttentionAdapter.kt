package com.example.roomkit.Date.Ui.Dashboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.databinding.ItemDashboardAttentionBinding

class DashboardAttentionAdapter(
    private val onItemClick: (Long) -> Unit
) : ListAdapter<DashboardAttentionItem, DashboardAttentionAdapter.AttentionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttentionViewHolder {
        val binding = ItemDashboardAttentionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AttentionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AttentionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AttentionViewHolder(
        private val binding: ItemDashboardAttentionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardAttentionItem) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.tvBadge.text = item.statusLabel

            val isOverdue = item.type == DashboardAttentionType.OVERDUE

            // Colors: Soft red for Overdue, soft amber for Unpaid/Partial
            val badgeBgColor = if (isOverdue) Color.parseColor("#FEE2E2") else Color.parseColor("#FEF3C7")
            val primaryTextColor = if (isOverdue) Color.parseColor("#DC2626") else Color.parseColor("#D97706")

            val badgeDrawable = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(badgeBgColor)
            }
            binding.tvBadge.background = badgeDrawable
            binding.tvBadge.setTextColor(primaryTextColor)

            val iconDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeBgColor)
            }
            binding.layoutIconContainer.background = iconDrawable
            binding.ivIcon.setColorFilter(primaryTextColor)

            binding.root.setOnClickListener {
                onItemClick(item.invoiceId)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DashboardAttentionItem>() {
        override fun areItemsTheSame(old: DashboardAttentionItem, new: DashboardAttentionItem) =
            old.invoiceId == new.invoiceId

        override fun areContentsTheSame(old: DashboardAttentionItem, new: DashboardAttentionItem) =
            old == new
    }
}