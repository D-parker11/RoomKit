package com.roomkit.ui.buildings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.databinding.ItemBuildingCardBinding
import com.roomkit.data.local.model.BuildingWithStats

class BuildingsAdapter(
    private val onViewRoomsClicked: (BuildingWithStats) -> Unit,
    private val onEditClicked: (BuildingWithStats) -> Unit
) : ListAdapter<BuildingWithStats, BuildingsAdapter.BuildingViewHolder>(DiffCallback) {

    inner class BuildingViewHolder(private val binding: ItemBuildingCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BuildingWithStats) {
            binding.tvBuildingName.text = item.name
            binding.tvBuildingAddress.text = item.address ?: "No address provided"
            binding.tvCardRoomsCount.text = item.totalRooms.toString()
            binding.tvCardOccupiedCount.text = item.occupiedRooms.toString()
            binding.tvCardVacantCount.text = item.vacantRooms.toString()
            binding.tvOccupancyPct.text = "${item.occupancyPercentage}%"
            binding.progressOccupancy.progress = item.occupancyPercentage

            binding.btnViewRooms.setOnClickListener { onViewRoomsClicked(item) }
            binding.headerContainer.setOnClickListener { onViewRoomsClicked(item) }
            binding.btnEditBuilding.setOnClickListener { onEditClicked(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuildingViewHolder {
        val binding = ItemBuildingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BuildingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BuildingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BuildingWithStats>() {
        override fun areItemsTheSame(oldItem: BuildingWithStats, newItem: BuildingWithStats) =
            oldItem.buildingId == newItem.buildingId

        override fun areContentsTheSame(oldItem: BuildingWithStats, newItem: BuildingWithStats) =
            oldItem == newItem
    }
}