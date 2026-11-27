@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Dashboard

import com.example.roomkit.R

import android.view.LayoutInflater
import android.view.ViewGroup
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
                binding.cardFilter.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_text_primary))
                binding.cardFilter.strokeColor = androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_text_primary)
                binding.tvFilterName.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_white))
            } else {
                // Unselected: White background, light gray border, dark gray text
                binding.cardFilter.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_white))
                binding.cardFilter.strokeColor = androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_gray_200)
                binding.tvFilterName.setTextColor(androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.roomkit_gray_750))
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