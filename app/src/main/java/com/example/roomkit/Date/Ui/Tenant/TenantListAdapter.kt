package com.example.roomkit.Date.Ui.Tenant

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Model.TenantWithCurrentTenancy
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemTenantCardBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TenantListAdapter(
    private val onTenantClicked: (TenantWithCurrentTenancy) -> Unit,
    private val onViewRoomClicked: (TenantWithCurrentTenancy) -> Unit
) : ListAdapter<TenantWithCurrentTenancy, TenantListAdapter.TenantViewHolder>(DiffCallback) {

    private val moveInDateFormat = SimpleDateFormat("MMM dd", Locale.ENGLISH)
    private val numberFormat = DecimalFormat("#,###")

    inner class TenantViewHolder(private val binding: ItemTenantCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TenantWithCurrentTenancy) {
            binding.fullName.text = item.fullName
            binding.tvTenantPhone.text = item.phoneNumber.ifBlank { "—" }

            val isActive = item.activeTenancyId != null

            if (isActive) {
                // Active status pill
                binding.tvTenantStatusBadge.text = "Active"
                binding.tvTenantStatusBadge.setBackgroundResource(R.drawable.bg_badge_tenant_active)
                binding.tvTenantStatusBadge.setTextColor(Color.parseColor("#2E7D32"))

                // Room & Building
                val rNum = item.roomNumber ?: item.currentRoomNumber ?: "—"
                val bName = item.buildingName ?: item.currentBuildingName ?: "—"
                binding.tvRoomAndBuilding.text = "Room $rNum · $bName"

                // Deposit handling
                val isKhr = item.currency?.equals("KHR", ignoreCase = true) != false
                binding.tvDepositValue.text = item.deposit?.let { dep ->
                    if (isKhr) {
                        // If stored in riel (>10,000 for standard deposits) or using CurrencyFormatter:
                        if (dep >= 1000L && dep % 100L == 0L && dep >= 10000L) {
                            CurrencyFormatter.formatKhr(dep).removeSuffix(".00")
                        } else {
                            "$${numberFormat.format(dep)}"
                        }
                    } else {
                        "${numberFormat.format(dep)} ៛"
                    }
                } ?: "—"

                // Moved in date
                binding.tvMoveInDateValue.text = item.moveInDate?.let { moveInDateFormat.format(Date(it)) } ?: "—"

                // View Room button
                binding.btnViewRoomAction.visibility = View.VISIBLE
                binding.btnViewRoomAction.setOnClickListener { onViewRoomClicked(item) }

            } else {
                // Previous vs Unassigned logic
                val hasPastHistory = item.totalTenanciesCount > 0

                if (hasPastHistory) {
                    binding.tvTenantStatusBadge.text = "Previous"
                    binding.tvTenantStatusBadge.setBackgroundResource(R.drawable.bg_badge_tenant_previous)
                    binding.tvTenantStatusBadge.setTextColor(Color.parseColor("#4B5563"))
                } else {
                    binding.tvTenantStatusBadge.text = "Unassigned"
                    binding.tvTenantStatusBadge.setBackgroundResource(R.drawable.bg_badge_tenant_previous)
                    binding.tvTenantStatusBadge.setTextColor(Color.parseColor("#6B7280"))
                }

                binding.tvRoomAndBuilding.text = "No active room"
                binding.tvDepositValue.text = "—"
                binding.tvMoveInDateValue.text = "—"
                binding.btnViewRoomAction.visibility = View.GONE
            }

            binding.root.setOnClickListener { onTenantClicked(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TenantViewHolder {
        val binding = ItemTenantCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TenantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TenantViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TenantWithCurrentTenancy>() {
        override fun areItemsTheSame(oldItem: TenantWithCurrentTenancy, newItem: TenantWithCurrentTenancy) =
            oldItem.tenantId == newItem.tenantId

        override fun areContentsTheSame(oldItem: TenantWithCurrentTenancy, newItem: TenantWithCurrentTenancy) =
            oldItem == newItem
    }
}
