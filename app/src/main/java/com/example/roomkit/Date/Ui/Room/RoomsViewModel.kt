@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.model.RoomItemDetails
import com.roomkit.data.repository.BuildingRepository
import com.roomkit.data.repository.RoomRepository
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

enum class RoomTabFilter { ALL, OCCUPIED, VACANT }

class RoomsViewModel(
    val buildingId: Long,
    private val buildingRepository: BuildingRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {
    val building: StateFlow<BuildingEntity?> = buildingRepository.getBuildingById(buildingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _selectedTab = MutableStateFlow(RoomTabFilter.ALL)
    val selectedTab = _selectedTab.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val allRooms = roomRepository.getRoomsForBuilding(buildingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allCount = allRooms.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val occupiedCount = allRooms.map { list -> list.count { it.isOccupied } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val vacantCount = allRooms.map { list -> list.count { !it.isOccupied } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val occupancyPercentage = allRooms.map { list ->
        if (list.isEmpty()) 0 else (list.count { it.isOccupied } * 100.0 / list.size).roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val displayedRooms: StateFlow<List<RoomItemDetails>> = combine(allRooms, _selectedTab, _searchQuery) { list, tab, query ->
        val q = query.trim()
        list.filter { room ->
            val matchesTab = when (tab) {
                RoomTabFilter.ALL -> true
                RoomTabFilter.OCCUPIED -> room.isOccupied
                RoomTabFilter.VACANT -> !room.isOccupied
            }
            matchesTab && (q.isEmpty() || room.roomNumber.contains(q, true) ||
                    "Room ${room.roomNumber}".contains(q, true) || room.tenantName.orEmpty().contains(q, true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun selectTab(tab: RoomTabFilter) { _selectedTab.value = tab }
    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }
}
