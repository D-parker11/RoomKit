package com.example.roomkit.Date.Ui.Room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.model.RoomItemDetails
import com.roomkit.data.repository.BuildingRepository
import com.roomkit.data.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

enum class RoomTabFilter { ALL, OCCUPIED, VACANT }

class RoomsViewModel(
    val buildingId: Long,
    private val buildingRepository: BuildingRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {

    // 1. Building details (Name & Address)
    // Directly stateIn the repository's Flow without wrapping in flow { emit(...) }
    val building: StateFlow<BuildingEntity?> = buildingRepository
        .getBuildingById(buildingId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 2. Tab filter state
    private val _selectedTab = MutableStateFlow(RoomTabFilter.ALL)
    val selectedTab: StateFlow<RoomTabFilter> = _selectedTab

    // 3. Rooms stream
    private val allRoomsFlow: Flow<List<RoomItemDetails>> = roomRepository.getRoomsForBuilding(buildingId)

    // Dynamic counts
    val allCount: StateFlow<Int> = allRoomsFlow
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val occupiedCount: StateFlow<Int> = allRoomsFlow
        .map { list -> list.count { it.isOccupied } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val vacantCount: StateFlow<Int> = allRoomsFlow
        .map { list -> list.count { !it.isOccupied } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Dynamic Occupancy % calculation: (Occupied / Total) * 100
    val occupancyPercentage: StateFlow<Int> = allRoomsFlow
        .map { list ->
            if (list.isEmpty()) 0
            else {
                val occupied = list.count { it.isOccupied }
                ((occupied.toDouble() / list.size.toDouble()) * 100.0).roundToInt()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Filtered rooms list
    val displayedRooms: StateFlow<List<RoomItemDetails>> = combine(allRoomsFlow, _selectedTab) { list, tab ->
        when (tab) {
            RoomTabFilter.ALL -> list
            RoomTabFilter.OCCUPIED -> list.filter { it.isOccupied }
            RoomTabFilter.VACANT -> list.filter { !it.isOccupied }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectTab(tab: RoomTabFilter) {
        _selectedTab.value = tab
    }
}