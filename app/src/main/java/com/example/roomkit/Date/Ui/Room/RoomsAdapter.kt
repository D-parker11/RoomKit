@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Room

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemRoomOccupiedBinding
import com.example.roomkit.databinding.ItemRoomVacantBinding
import com.roomkit.data.local.model.RoomItemDetails
import java.util.Locale

class RoomsAdapter(
    private val onRoomClick: (Long) -> Unit,
    private val onAssignTenantClick: (Long) -> Unit
) : ListAdapter<RoomItemDetails, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_OCCUPIED = 1
        private const val TYPE_VACANT = 2

        private val DiffCallback = object : DiffUtil.ItemCallback<RoomItemDetails>() {
            override fun areItemsTheSame(oldItem: RoomItemDetails, newItem: RoomItemDetails): Boolean =
                oldItem.roomId == newItem.roomId

            override fun areContentsTheSame(oldItem: RoomItemDetails, newItem: RoomItemDetails): Boolean =
                oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isOccupied) TYPE_OCCUPIED else TYPE_VACANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_OCCUPIED) {
            val binding = ItemRoomOccupiedBinding.inflate(inflater, parent, false)
            OccupiedViewHolder(binding)
        } else {
            val binding = ItemRoomVacantBinding.inflate(inflater, parent, false)
            VacantViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is OccupiedViewHolder) {
            holder.bind(item)
        } else if (holder is VacantViewHolder) {
            holder.bind(item)
        }
    }

    inner class OccupiedViewHolder(private val b: ItemRoomOccupiedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RoomItemDetails) {
            val context = b.root.context
            b.tvRoomNumber.text = "ROOM ${item.roomNumber.uppercase(Locale.US)}"
            b.tvTenantName.text = item.tenantName ?: "Active Tenant"

            val rentKhr = item.monthlyRent
            b.tvMonthlyRent.text = "${CurrencyFormatter.formatKhr(rentKhr)} / month"

            b.tvElectricityUsage.text = item.electricityReading?.let { "Reading: $it kWh" } ?: "—"
            b.tvWaterUsage.text = if (item.waterBillingMethod.equals("FIXED", true)) {
                "Fixed fee"
            } else item.waterReading?.let { "Reading: $it m³" } ?: "—"
            b.tvDueDay.text = item.dueDateText ?: "No due date"

            b.tvInvoiceStatusBadge.visibility = View.VISIBLE
            b.tvInvoiceStatusBadge.text = "Occupied"
            b.tvInvoiceStatusBadge.setBackgroundResource(R.drawable.bg_badge_paid_pill)
            b.tvInvoiceStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.roomkit_success_dark))

            b.root.setOnClickListener { onRoomClick(item.roomId) }
        }
    }

    inner class VacantViewHolder(private val b: ItemRoomVacantBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RoomItemDetails) {
            b.tvRoomNumber.text = "ROOM ${item.roomNumber.uppercase(Locale.US)}"

            val rentKhr = item.monthlyRent
            b.tvMonthlyRent.text = "${CurrencyFormatter.formatKhr(rentKhr)} / month"

            b.btnAssignTenant.setOnClickListener { onAssignTenantClick(item.roomId) }
            b.root.setOnClickListener { onRoomClick(item.roomId) }
        }
    }

}
