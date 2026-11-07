package com.roomkit.ui.buildings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.roomkit.data.local.model.BuildingWithStats
import com.roomkit.data.repository.BuildingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverallSummary(
    val totalBuildings: Int = 0,
    val totalRooms: Int = 0,
    val totalOccupied: Int = 0
) {
    val totalVacant: Int
        get() = (totalRooms - totalOccupied).coerceAtLeast(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BuildingsViewModel(
    private val repository: BuildingRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val buildings: StateFlow<List<BuildingWithStats>> =
        searchQuery
            .flatMapLatest { query ->
                repository.getBuildings(query)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val summary: StateFlow<OverallSummary> =
        buildings
            .map { properties ->
                OverallSummary(
                    totalBuildings = properties.size,
                    totalRooms = properties.sumOf {
                        it.totalRooms
                    },
                    totalOccupied = properties.sumOf {
                        it.occupiedRooms
                    }
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OverallSummary()
            )

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
        performOperation(onResult) {
            repository.addBuilding(
                name = name,
                address = address,
                description = description,
                totalRooms = totalRooms
            )
        }
    }

    fun updateBuilding(
        building: BuildingEntity,
        onResult: (Result<Unit>) -> Unit
    ) {
        performOperation(onResult) {
            repository.updateBuilding(building)
        }
    }

    fun deleteBuilding(
        building: BuildingEntity,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result: Result<Unit> = try {
                repository.deleteBuilding(building)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }

            onResult(result)
        }
    }

    private fun performOperation(
        onResult: (Result<Unit>) -> Unit,
        operation: suspend () -> Unit
    ) {
        viewModelScope.launch {
            val result: Result<Unit> = try {
                operation()
                Result.success(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }

            onResult(result)
        }
    }
}