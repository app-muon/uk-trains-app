package com.example.transport_app.ui.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.transport_app.data.model.BusStop
import com.example.transport_app.data.model.Station
import com.example.transport_app.data.model.StationEntry
import com.example.transport_app.data.model.TflDirection
import com.example.transport_app.data.model.TflLine
import com.example.transport_app.data.model.TflRailStation
import com.example.transport_app.data.model.TransportDataSource
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
    val tflRailSearchResults: List<TflRailStation> = emptyList(),
    val isTflRailSearching: Boolean = false,
    val busSearchResults: List<BusStop> = emptyList(),
    val busSearchGroups: Map<String, List<BusStop>> = emptyMap(),
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
    val isLoadingDirections: Boolean = false,
    val directionPickerHasAllOption: Boolean = true,
    val tflOptionStation: TflRailStation? = null,
    val tflOptionType: String = TransportType.TRAIN,
    val tflOptionModes: List<String> = emptyList(),
    val tflOptionLines: List<TflLine> = emptyList(),
    val tflOptionDirections: List<TflDirection> = emptyList(),
    val isLoadingTflOptions: Boolean = false,
    val isLoadingTflDirections: Boolean = false
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
    private var tflRailSearchJob: Job? = null

    private val trainTflModes = listOf("overground", "elizabeth-line")
    private val tubeTflModes = listOf("tube", "dlr")

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
        tflRailSearchJob?.cancel()
        _uiState.update {
            it.copy(
                searchMode = mode,
                searchQuery = "",
                searchResults = emptyList(),
                tflRailSearchResults = emptyList(),
                isTflRailSearching = false,
                busSearchResults = emptyList(),
                busSearchGroups = emptyMap(),
                isBusSearching = false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        when (_uiState.value.searchMode) {
            TransportType.TRAIN -> {
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        searchResults = stationRepository.search(query),
                        tflRailSearchResults = emptyList()
                    )
                }
                searchTflRail(query, trainTflModes)
            }
            TransportType.TUBE -> {
                _uiState.update {
                    it.copy(
                        searchQuery = query,
                        searchResults = emptyList(),
                        tflRailSearchResults = emptyList()
                    )
                }
                searchTflRail(query, tubeTflModes)
            }
            else -> {
                _uiState.update { it.copy(searchQuery = query) }
                searchBusStops(query)
            }
        }
    }

    private fun searchBusStops(query: String) {
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
                    // Group by name. For display, show one entry per name:
                    // prefer a group stop (490G/HUB) if present, otherwise the first individual stop.
                    val grouped = results.groupBy { it.name }
                    val display = grouped.map { (_, stops) ->
                        stops.firstOrNull { TflApiClient.isGroupStop(it.naptanId) } ?: stops.first()
                    }
                    _uiState.update { it.copy(busSearchResults = display, busSearchGroups = grouped, isBusSearching = false) }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w(TAG, "busSearch: query='$query' failed: ${e.message}")
                    _uiState.update { it.copy(busSearchResults = emptyList(), busSearchGroups = emptyMap(), isBusSearching = false) }
                }
            }
    }

    private fun searchTflRail(query: String, modes: List<String>) {
        tflRailSearchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(tflRailSearchResults = emptyList(), isTflRailSearching = false) }
            return
        }
        tflRailSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isTflRailSearching = true) }
            try {
                val results = tflApiClient.searchRailStations(query, modes)
                _uiState.update {
                    it.copy(tflRailSearchResults = results, isTflRailSearching = false)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w(TAG, "tflRailSearch: query='$query' failed: ${e.message}")
                _uiState.update { it.copy(tflRailSearchResults = emptyList(), isTflRailSearching = false) }
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

    fun selectTflRailStation(station: TflRailStation) {
        val displayType = if (_uiState.value.searchMode == TransportType.TUBE) TransportType.TUBE else TransportType.TRAIN
        val modes = if (displayType == TransportType.TUBE) tubeTflModes else trainTflModes
        _uiState.update {
            it.copy(
                tflOptionStation = station,
                tflOptionType = displayType,
                tflOptionModes = modes,
                tflOptionLines = station.lines,
                tflOptionDirections = emptyList(),
                isLoadingTflOptions = true,
                searchQuery = "",
                searchResults = emptyList(),
                tflRailSearchResults = emptyList()
            )
        }
        viewModelScope.launch {
            val enriched = try {
                tflApiClient.getRailStation(station.id, modes)
            } catch (_: Exception) {
                station
            }
            _uiState.update {
                it.copy(
                    tflOptionStation = enriched,
                    tflOptionLines = enriched.lines,
                    isLoadingTflOptions = false
                )
            }
            loadTflDirections(null)
        }
    }

    fun loadTflDirections(line: TflLine?) {
        val station = _uiState.value.tflOptionStation ?: return
        val arrivalStopIds = line?.stopIds?.takeIf { ids -> ids.isNotEmpty() }
            ?: station.arrivalStopIds.takeIf { ids -> ids.isNotEmpty() }
            ?: listOf(station.id)
        _uiState.update { it.copy(isLoadingTflDirections = true, tflOptionDirections = emptyList()) }
        viewModelScope.launch {
            val directions = try {
                tflApiClient.getRailDirections(arrivalStopIds.joinToString(","), line?.id)
            } catch (_: Exception) {
                emptyList()
            }
            _uiState.update { it.copy(tflOptionDirections = directions, isLoadingTflDirections = false) }
        }
    }

    fun addTflRailStation(line: TflLine?, direction: TflDirection?) {
        val station = _uiState.value.tflOptionStation ?: return
        _uiState.update {
            val arrivalStopIds = line?.stopIds?.takeIf { ids -> ids.isNotEmpty() }
                ?: station.arrivalStopIds.takeIf { ids -> ids.isNotEmpty() }
                ?: listOf(station.id)
            val newEntry = StationEntry(
                groupId = groupId ?: 0L,
                stationName = station.name,
                crsCode = arrivalStopIds.joinToString(","),
                displayOrder = it.stations.size,
                filterCrs = line?.id,
                filterName = line?.name,
                type = it.tflOptionType,
                dataSource = TransportDataSource.TFL,
                tflMode = line?.mode ?: station.modes.firstOrNull(),
                direction = direction?.id,
                directionName = direction?.label
            )
            it.copy(
                stations = it.stations + newEntry,
                tflOptionStation = null,
                tflOptionLines = emptyList(),
                tflOptionDirections = emptyList(),
                isLoadingTflOptions = false,
                isLoadingTflDirections = false
            )
        }
    }

    fun dismissTflRailOptions() {
        _uiState.update {
            it.copy(
                tflOptionStation = null,
                tflOptionLines = emptyList(),
                tflOptionDirections = emptyList(),
                isLoadingTflOptions = false,
                isLoadingTflDirections = false
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
                    directionPickerHasAllOption = true,
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
            // Check if there are multiple individual stops with this name from the search results.
            // If so, look up the parent group stop and show the direction picker with proper labels.
            val alternatives = _uiState.value.busSearchGroups[busStop.name] ?: emptyList()
            if (alternatives.size > 1) {
                // Find the common parentId across all alternatives (all should share one).
                // If it's a bus group (490G/HUB), we can expand the full hub to get ALL stops
                // with proper stop letters, not just the subset returned by search.
                val commonParentId = alternatives
                    .mapNotNull { it.parentId }
                    .groupBy { it }
                    .maxByOrNull { it.value.size }
                    ?.key
                val parentIsBusGroup = commonParentId != null && TflApiClient.isGroupStop(commonParentId)

                _uiState.update {
                    it.copy(
                        directionPickerStop = if (parentIsBusGroup)
                            BusStop(name = busStop.name, naptanId = commonParentId!!)
                        else
                            busStop,
                        directionOptions = alternatives,
                        isLoadingDirections = true,
                        directionPickerHasAllOption = parentIsBusGroup,
                        searchQuery = "",
                        busSearchResults = emptyList()
                    )
                }
                viewModelScope.launch {
                    val children = try {
                        if (commonParentId != null)
                            tflApiClient.getChildStops(commonParentId)
                        else
                            tflApiClient.enrichStops(alternatives)
                    } catch (_: Exception) {
                        tflApiClient.enrichStops(alternatives)
                    }
                    _uiState.update { it.copy(directionOptions = children, isLoadingDirections = false) }
                }
            } else {
                addBusStopDirectly(busStop.name, busStop.naptanId)
            }
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
                type = TransportType.BUS,
                dataSource = TransportDataSource.TFL
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
                isLoadingDirections = false,
                directionPickerHasAllOption = true
            )
        }
    }

    fun dismissDirectionPicker() {
        _uiState.update {
            it.copy(
                directionPickerStop = null,
                directionOptions = emptyList(),
                isLoadingDirections = false,
                directionPickerHasAllOption = true
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
                if (i == index) s.copy(filterCrs = null, filterName = null, direction = null, directionName = null)
                else s
            })
        }
    }

    fun removeStation(index: Int) {
        _uiState.update {
            it.copy(stations = it.stations.filterIndexed { i, _ -> i != index })
        }
    }

    fun moveStation(fromIndex: Int, toIndex: Int) {
        _uiState.update {
            if (fromIndex == toIndex || fromIndex !in it.stations.indices || toIndex !in it.stations.indices) {
                it
            } else {
                it.copy(
                    stations = it.stations.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                )
            }
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
