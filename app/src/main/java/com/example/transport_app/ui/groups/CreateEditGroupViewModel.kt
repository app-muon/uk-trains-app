package com.example.transport_app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.transport_app.data.model.BusStop
import com.example.transport_app.data.model.Station
import com.example.transport_app.data.model.StationEntry
import com.example.transport_app.data.model.TransportType
import android.util.Log
import com.example.transport_app.BuildConfig
import com.example.transport_app.data.network.TflApiClient
import com.example.transport_app.data.repository.BusStopRepository
import com.example.transport_app.data.repository.GroupRepository
import com.example.transport_app.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val busSearchResults: List<BusStop> = emptyList(),
    val isBusSearching: Boolean = false,
    val searchMode: String = TransportType.TRAIN,
    val isSaved: Boolean = false,
    val savedGroupId: Long? = null,
    val isDeleted: Boolean = false,
    val canDelete: Boolean = false,
    val isLoading: Boolean = false,
    val availableRoutes: List<String> = emptyList(),
    val isLoadingRoutes: Boolean = false,
    val directionPickerStop: BusStop? = null,
    val directionOptions: List<BusStop> = emptyList(),
    val isLoadingDirections: Boolean = false
)

@HiltViewModel
class CreateEditGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val stationRepository: StationRepository,
    private val busStopRepository: BusStopRepository,
    private val tflApiClient: TflApiClient
) : ViewModel() {

    private val groupId: Long? = savedStateHandle.get<Long>("groupId")
    companion object {
        private const val TAG = "CreateEditGroupVM"
    }
    private var busSearchJob: Job? = null

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

    fun onSearchModeChange(mode: String) {
        busSearchJob?.cancel()
        _uiState.update {
            it.copy(
                searchMode = mode,
                searchQuery = "",
                searchResults = emptyList(),
                busSearchResults = emptyList(),
                isBusSearching = false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        if (_uiState.value.searchMode == TransportType.TRAIN) {
            _uiState.update {
                it.copy(
                    searchQuery = query,
                    searchResults = stationRepository.search(query)
                )
            }
        } else {
            _uiState.update { it.copy(searchQuery = query) }
            busSearchJob?.cancel()
            if (query.length < 2) {
                _uiState.update { it.copy(busSearchResults = emptyList(), isBusSearching = false) }
                return
            }
            busSearchJob = viewModelScope.launch {
                _uiState.update { it.copy(isBusSearching = true) }
                try {
                    val results = busStopRepository.search(query)
                    if (BuildConfig.DEBUG) Log.d(TAG, "busSearch: query='$query' -> ${results.size} results: ${results.map { "${it.naptanId} (${it.name})" }}")
                    _uiState.update { it.copy(busSearchResults = results, isBusSearching = false) }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "busSearch: query='$query' failed: ${e.message}")
                    _uiState.update { it.copy(busSearchResults = emptyList(), isBusSearching = false) }
                }
            }
        }
    }

    fun searchDestinations(query: String): List<Station> =
        stationRepository.search(query)

    fun addStation(station: Station) {
        _uiState.update {
            val newEntry = StationEntry(
                groupId = groupId ?: 0L,
                stationName = station.name,
                crsCode = station.crs,
                displayOrder = it.stations.size,
                type = TransportType.TRAIN
            )
            it.copy(
                stations = it.stations + newEntry,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun addBusStop(busStop: BusStop) {
        val isGroup = TflApiClient.isGroupStop(busStop.naptanId)
        if (BuildConfig.DEBUG) Log.d(TAG, "addBusStop: ${busStop.naptanId} name=${busStop.name} isGroup=$isGroup")
        if (isGroup) {
            _uiState.update {
                it.copy(
                    directionPickerStop = busStop,
                    directionOptions = emptyList(),
                    isLoadingDirections = true,
                    searchQuery = "",
                    busSearchResults = emptyList()
                )
            }
            viewModelScope.launch {
                try {
                    val children = tflApiClient.getChildStops(busStop.naptanId)
                    if (BuildConfig.DEBUG) Log.d(TAG, "addBusStop: got ${children.size} children for ${busStop.naptanId}: ${children.map { "${it.naptanId} (${it.indicator} -> ${it.towards})" }}")
                    _uiState.update {
                        it.copy(directionOptions = children, isLoadingDirections = false)
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "addBusStop: getChildStops failed for ${busStop.naptanId}, falling back", e)
                    addBusStopDirectly(busStop.name, busStop.naptanId)
                    _uiState.update {
                        it.copy(
                            directionPickerStop = null,
                            directionOptions = emptyList(),
                            isLoadingDirections = false
                        )
                    }
                }
            }
        } else {
            addBusStopDirectly(busStop.name, busStop.naptanId)
        }
    }

    private fun addBusStopDirectly(name: String, naptanId: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "addBusStopDirectly: name=$name naptanId=$naptanId currentStationCount=${_uiState.value.stations.size}")
        _uiState.update {
            val newEntry = StationEntry(
                groupId = groupId ?: 0L,
                stationName = name,
                crsCode = naptanId,
                displayOrder = it.stations.size,
                type = TransportType.BUS
            )
            it.copy(
                stations = it.stations + newEntry,
                searchQuery = "",
                busSearchResults = emptyList()
            )
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "addBusStopDirectly: newStationCount=${_uiState.value.stations.size} entries=${_uiState.value.stations.map { "${it.crsCode}(${it.filterCrs})" }}")
    }

    fun selectDirection(child: BusStop?) {
        val groupStop = _uiState.value.directionPickerStop ?: return
        if (BuildConfig.DEBUG) Log.d(TAG, "selectDirection: groupStop=${groupStop.naptanId} child=${child?.naptanId} indicator=${child?.indicator} towards=${child?.towards}")
        if (child == null) {
            // "All directions" — use group ID
            addBusStopDirectly(groupStop.name, groupStop.naptanId)
        } else {
            // Build display name with direction info
            val directionSuffix = buildList {
                child.indicator?.let { add(it) }
                child.towards?.let { add("towards $it") }
            }.joinToString(" ")
            val displayName = if (directionSuffix.isNotEmpty()) {
                "${groupStop.name} ($directionSuffix)"
            } else {
                groupStop.name
            }
            addBusStopDirectly(displayName, child.naptanId)
        }
        _uiState.update {
            it.copy(
                directionPickerStop = null,
                directionOptions = emptyList(),
                isLoadingDirections = false
            )
        }
    }

    fun dismissDirectionPicker() {
        _uiState.update {
            it.copy(
                directionPickerStop = null,
                directionOptions = emptyList(),
                isLoadingDirections = false
            )
        }
    }

    fun setDestination(index: Int, destination: Station) {
        _uiState.update {
            it.copy(stations = it.stations.mapIndexed { i, s ->
                if (i == index) s.copy(filterCrs = destination.crs, filterName = destination.name)
                else s
            })
        }
    }

    fun setRouteFilter(index: Int, routeNumber: String) {
        _uiState.update {
            it.copy(stations = it.stations.mapIndexed { i, s ->
                if (i == index) s.copy(filterCrs = routeNumber, filterName = "Route $routeNumber")
                else s
            })
        }
    }

    fun clearDestination(index: Int) {
        _uiState.update {
            it.copy(stations = it.stations.mapIndexed { i, s ->
                if (i == index) s.copy(filterCrs = null, filterName = null)
                else s
            })
        }
    }

    fun removeStation(index: Int) {
        _uiState.update {
            it.copy(stations = it.stations.filterIndexed { i, _ -> i != index })
        }
    }

    fun deleteGroup() {
        if (groupId == null) return
        viewModelScope.launch {
            groupRepository.deleteGroup(groupId)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun loadRoutesForStop(naptanId: String) {
        _uiState.update { it.copy(isLoadingRoutes = true, availableRoutes = emptyList()) }
        viewModelScope.launch {
            try {
                val routes = tflApiClient.getRoutes(naptanId)
                _uiState.update { it.copy(availableRoutes = routes, isLoadingRoutes = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(availableRoutes = emptyList(), isLoadingRoutes = false) }
            }
        }
    }

    fun clearRoutes() {
        _uiState.update { it.copy(availableRoutes = emptyList(), isLoadingRoutes = false) }
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
