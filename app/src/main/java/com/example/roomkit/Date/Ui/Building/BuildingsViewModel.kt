package com.roomkit.ui.buildings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.model.BuildingWithStats
import com.roomkit.data.repository.BuildingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class OverallSummary(
    val totalBuildings: Int = 0,
    val totalRooms: Int = 0,
    val totalOccupied: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class BuildingsViewModel(private val repository: BuildingRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val buildings: StateFlow<List<BuildingWithStats>> = searchQuery
        .flatMapLatest { query -> repository.getBuildings(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<OverallSummary> = buildings.map { list ->
        OverallSummary(
            totalBuildings = list.size,
            totalRooms = list.sumOf { it.totalRooms },
            totalOccupied = list.sumOf { it.occupiedRooms }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverallSummary())

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query.trim()
    }

    fun saveBuilding(
        name: String,
        address: String?,
        description: String?,
        totalRooms: Int,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.addBuilding(
                    name = name,
                    address = address,
                    description = description,
                    totalRooms = totalRooms
                )
                onResult(Result.success(Unit))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun updateBuilding(building: BuildingEntity, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                repository.updateBuilding(building)
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun deleteBuilding(building: BuildingEntity, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteBuilding(building)
            onResult(res)
        }
    }
}