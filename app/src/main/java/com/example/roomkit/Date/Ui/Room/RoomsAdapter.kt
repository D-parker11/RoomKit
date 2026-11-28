@file:Suppress("PackageNaming", "PackageName")

package com.example.roomkit.Date.Ui.Room

import com.example.roomkit.Date.Ui.Common.setRoomKitTextSize

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.R
import com.example.roomkit.databinding.ItemRoomOccupiedBinding
import com.example.roomkit.databinding.ItemRoomVacantBinding
import com.roomkit.data.local.model.RoomItemDetails
import java.util.Locale
import kotlin.math.roundToInt

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

    private fun styleStatusBadge(
        view: TextView,
        @androidx.annotation.ColorRes textColorRes: Int,
        @DrawableRes backgroundRes: Int
    ) {
        val color = androidx.core.content.ContextCompat.getColor(view.context, textColorRes)
        val density = view.resources.displayMetrics.density
        val padH = (10 * density).roundToInt()
        val padV = (5 * density).roundToInt()

        view.gravity = Gravity.CENTER_VERTICAL
        view.includeFontPadding = false
        view.setRoomKitTextSize(R.dimen.roomkit_text_12)
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setTextColor(color)
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

    inner class OccupiedViewHolder(private val b: ItemRoomOccupiedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: RoomItemDetails) {
            b.tvRoomNumber.text = "ROOM ${item.roomNumber.uppercase(Locale.US)}"
            b.tvTenantName.text = item.tenantName ?: "Active Tenant"

            val rentKhr = item.monthlyRent
            b.tvMonthlyRent.text = "${CurrencyFormatter.formatKhr(rentKhr)} / month"

            b.tvElectricityUsage.text = item.electricityReading?.let { "Reading: $it kWh" } ?: "—"
            b.tvWaterUsage.text = if (item.waterBillingMethod.equals("FIXED", true)) {
                "Fixed fee"
            } else {
                item.waterReading?.let { "Reading: $it m³" } ?: "—"
            }
            b.tvDueDay.text = item.dueDateText ?: "No due date"

            b.tvInvoiceStatusBadge.visibility = View.VISIBLE
            b.tvInvoiceStatusBadge.text = "Occupied"
            styleStatusBadge(
                view = b.tvInvoiceStatusBadge,
                textColorRes = R.color.roomkit_green_700,
                backgroundRes = R.drawable.bg_rd_badge_green
            )

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