@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.roomkit.databinding.ItemBuildingFilterBinding

class BuildingFilterAdapter(
    private val onBuildingSelected: (Long?) -> Unit
) : ListAdapter<BuildingFilterItem, BuildingFilterAdapter.FilterViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemBuildingFilterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FilterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FilterViewHolder(
        private val binding: ItemBuildingFilterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BuildingFilterItem) {
            binding.tvFilterName.text = item.name

            if (item.isSelected) {
                // Selected: Dark background, dark border, white text
                binding.cardFilter.setCardBackgroundColor("#111827".toColorInt())
                binding.cardFilter.strokeColor = "#111827".toColorInt()
                binding.tvFilterName.setTextColor(Color.WHITE)
            } else {
                // Unselected: White background, light gray border, dark gray text
                binding.cardFilter.setCardBackgroundColor(Color.WHITE)
                binding.cardFilter.strokeColor = "#E5E7EB".toColorInt()
                binding.tvFilterName.setTextColor("#374151".toColorInt())
            }

            binding.root.setOnClickListener {
                if (!item.isSelected) {
                    onBuildingSelected(item.buildingId)
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BuildingFilterItem>() {
        override fun areItemsTheSame(oldItem: BuildingFilterItem, newItem: BuildingFilterItem): Boolean {
            return oldItem.buildingId == newItem.buildingId
        }

        override fun areContentsTheSame(oldItem: BuildingFilterItem, newItem: BuildingFilterItem): Boolean {
            return oldItem == newItem
        }
    }
}