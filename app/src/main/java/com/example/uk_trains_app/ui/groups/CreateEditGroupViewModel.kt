package com.example.uk_trains_app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uk_trains_app.data.model.Station
import com.example.uk_trains_app.data.model.StationEntry
import com.example.uk_trains_app.data.repository.GroupRepository
import com.example.uk_trains_app.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateEditGroupUiState(
    val groupName: String = "",
    val stations: List<StationEntry> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<Station> = emptyList(),
    val isSaved: Boolean = false,
    val savedGroupId: Long? = null,
    val isDeleted: Boolean = false,
    val canDelete: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class CreateEditGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val stationRepository: StationRepository
) : ViewModel() {

    private val groupId: Long? = savedStateHandle.get<Long>("groupId")

    private val _uiState = MutableStateFlow(CreateEditGroupUiState())
    val uiState: StateFlow<CreateEditGroupUiState> = _uiState.asStateFlow()

    init {
        if (groupId != null) {
            _uiState.update { it.copy(isLoading = true, canDelete = true) }
            viewModelScope.launch {
                val group = groupRepository.getGroup(groupId)
                val stations = groupRepository.getStations(groupId)
                _uiState.update {
                    it.copy(
                        groupName = group?.name ?: "",
                        stations = stations,
                        isLoading = false
                    )
                }
            }
        } else {
            _uiState.update { it.copy(groupName = "New Board") }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(groupName = name) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchResults = stationRepository.search(query)
            )
        }
    }

    fun searchDestinations(query: String): List<Station> =
        stationRepository.search(query)

    fun addStation(station: Station) {
        _uiState.update {
            if (it.stations.any { s -> s.crsCode == station.crs }) return@update it
            val newEntry = StationEntry(
                groupId = groupId ?: 0L,
                stationName = station.name,
                crsCode = station.crs,
                displayOrder = it.stations.size
            )
            it.copy(
                stations = it.stations + newEntry,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun setDestination(entry: StationEntry, destination: Station) {
        _uiState.update {
            it.copy(stations = it.stations.map { s ->
                if (s.crsCode == entry.crsCode) s.copy(filterCrs = destination.crs, filterName = destination.name)
                else s
            })
        }
    }

    fun clearDestination(entry: StationEntry) {
        _uiState.update {
            it.copy(stations = it.stations.map { s ->
                if (s.crsCode == entry.crsCode) s.copy(filterCrs = null, filterName = null)
                else s
            })
        }
    }

    fun removeStation(entry: StationEntry) {
        _uiState.update { it.copy(stations = it.stations.filter { s -> s != entry }) }
    }

    fun deleteGroup() {
        if (groupId == null) return
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun save() {
        val name = _uiState.value.groupName.trim()
        if (name.isEmpty() || _uiState.value.isSaved) return
        viewModelScope.launch {
            val targetGroupId = if (groupId != null) {
                groupRepository.renameGroup(groupId, name)
                groupId
            } else {
                groupRepository.createGroup(name)
            }
            groupRepository.saveStations(targetGroupId, _uiState.value.stations)
            _uiState.update { it.copy(isSaved = true, savedGroupId = targetGroupId) }
        }
    }
}
