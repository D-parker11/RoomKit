package com.example.roomkit.Date.Ui.Tenant

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Model.TenancyHistoryItem
import com.example.roomkit.databinding.ItemTenancyHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TenancyHistoryAdapter(
    private val onViewInvoices: (TenancyHistoryItem) -> Unit = {},
    private val onViewSettlement: (TenancyHistoryItem) -> Unit = {}
) : ListAdapter<
        TenancyHistoryItem,
        TenancyHistoryAdapter.HistoryViewHolder
        >(DiffCallback) {

    private val dateFormat = SimpleDateFormat(
        "dd MMM yyyy",
        Locale.US
    )

    inner class HistoryViewHolder(
        private val binding: ItemTenancyHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TenancyHistoryItem) {
            binding.tvHistoryRoomBuilding.text =
                "Room ${item.roomNumber} • ${item.buildingName}"

            binding.tvHistoryStatus.text = item.status

            val moveIn = dateFormat.format(Date(item.moveInDate))
            val moveOut = item.moveOutDate?.let {
                dateFormat.format(Date(it))
            } ?: "Present"

            binding.tvHistoryDates.text = "$moveIn – $moveOut"

            binding.tvHistoryRent.text =
                "${CurrencyFormatter.formatKhr(item.monthlyRent)} / month"

            binding.btnHistorySettlement.visibility = if (item.status == "ENDED") android.view.View.VISIBLE else android.view.View.GONE
            binding.btnHistorySettlement.setOnClickListener { onViewSettlement(item) }
            binding.root.setOnClickListener {
                onViewInvoices(item)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HistoryViewHolder {
        val binding = ItemTenancyHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: HistoryViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback :
        DiffUtil.ItemCallback<TenancyHistoryItem>() {

        override fun areItemsTheSame(
            oldItem: TenancyHistoryItem,
            newItem: TenancyHistoryItem
        ): Boolean = oldItem.tenancyId == newItem.tenancyId

        override fun areContentsTheSame(
            oldItem: TenancyHistoryItem,
            newItem: TenancyHistoryItem
        ): Boolean = oldItem == newItem
    }
}