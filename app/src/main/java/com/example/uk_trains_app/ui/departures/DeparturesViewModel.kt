package com.example.uk_trains_app.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uk_trains_app.data.model.Departure
import com.example.uk_trains_app.data.model.StationEntry
import com.example.uk_trains_app.data.model.TransportType
import com.example.uk_trains_app.data.repository.DeparturesRepository
import com.example.uk_trains_app.data.repository.GroupRepository
import com.example.uk_trains_app.data.repository.StationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class StationSection(
    val stationName: String,
    val crsCode: String,
    val filterCrs: String? = null,
    val filterName: String? = null,
    val departures: List<Departure>,
    val messages: List<String> = emptyList(),
    val fromCache: Boolean = false,
    val type: String = TransportType.TRAIN
) {
    val headerText: String
        get() = when {
            type == TransportType.BUS && filterName != null -> "$stationName \u2014 $filterName"
            type == TransportType.BUS -> stationName
            filterName != null -> "$stationName \u2192 $filterName"
            else -> stationName
        }

    val sectionKey: String
        get() = buildSectionKey(type, crsCode, filterCrs)
}

private fun buildSectionKey(type: String, crsCode: String, filterCrs: String?): String =
    if (filterCrs != null) "$type:$crsCode->$filterCrs" else "$type:$crsCode"

private fun stationSectionKey(station: StationEntry): String =
    buildSectionKey(station.type, station.crsCode, station.filterCrs)

data class DeparturesUiState(
    val groupName: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val sections: List<StationSection> = emptyList(),
    val stationErrors: List<String> = emptyList(),
    val lastFetchedAt: Long? = null,
    val hasAnyFromCache: Boolean = false
)

@HiltViewModel
class DeparturesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val departuresRepository: DeparturesRepository
) : ViewModel() {

    private val groupId: Long = checkNotNull(savedStateHandle["groupId"])
    private var activeJob: Job? = null

    private val _uiState = MutableStateFlow(DeparturesUiState())
    val uiState: StateFlow<DeparturesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val group = groupRepository.getGroup(groupId)
            _uiState.update { it.copy(groupName = group?.name ?: "") }
        }
        refresh()
    }

    fun refresh() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, stationErrors = emptyList(), sections = emptyList()) }
            val stations = groupRepository.getStations(groupId)
            val results = departuresRepository.fetchAll(stations, groupId, timeOffset = 0)
            val sections = buildSections(stations, results)
            val anyFromCache = sections.any { it.fromCache }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sections = sections,
                    stationErrors = buildErrors(results),
                    lastFetchedAt = if (anyFromCache) {
                        departuresRepository.getCachedTimestamp(groupId) ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis()
                    },
                    hasAnyFromCache = anyFromCache
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore) return
        activeJob?.cancel()
        val offset = computeNextOffset(_uiState.value.sections)
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            // Only fetch more trains — buses don't support time offset pagination
            val trainStations = groupRepository.getStations(groupId)
                .filter { it.type == TransportType.TRAIN }
            val results = departuresRepository.fetchAll(trainStations, groupId, timeOffset = offset)
            _uiState.update {
                it.copy(
                    isLoadingMore = false,
                    sections = appendSections(it.sections, trainStations, results),
                    lastFetchedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private fun computeNextOffset(sections: List<StationSection>): Int {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        var latestMinutes = nowMinutes
        for (section in sections.filter { it.type == TransportType.TRAIN }) {
            for (dep in section.departures) {
                val parts = dep.scheduledTime.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull() ?: continue
                    val m = parts[1].toIntOrNull() ?: continue
                    val depMinutes = h * 60 + m
                    if (depMinutes > latestMinutes) {
                        latestMinutes = depMinutes
                    }
                }
            }
        }

        // Start 1 minute after the latest departure we already have
        return (latestMinutes - nowMinutes + 1).coerceAtLeast(0)
    }

    private fun buildSections(stations: List<StationEntry>, results: List<StationResult>): List<StationSection> =
        stations.zip(results).mapNotNull { (station, result) ->
            (result as? StationResult.Success)?.let {
                StationSection(station.stationName, station.crsCode, station.filterCrs, station.filterName, it.departures, it.messages, it.fromCache, station.type)
            }
        }

    private fun appendSections(
        existing: List<StationSection>,
        stations: List<StationEntry>,
        results: List<StationResult>
    ): List<StationSection> {
        val existingMap = existing.associateBy { it.sectionKey }
        return stations.zip(results).mapNotNull { (station, result) ->
            val key = stationSectionKey(station)
            val prev = existingMap[key]
            when (result) {
                is StationResult.Success -> {
                    val existingIds = prev?.departures?.map { it.serviceId }?.toSet() ?: emptySet()
                    val newDepartures = result.departures.filter { it.serviceId !in existingIds }
                    StationSection(
                        station.stationName,
                        station.crsCode,
                        station.filterCrs,
                        station.filterName,
                        (prev?.departures ?: emptyList()) + newDepartures,
                        result.messages,
                        type = station.type
                    )
                }
                is StationResult.Error -> prev
            }
        }
    }

    private fun buildErrors(results: List<StationResult>): List<String> =
        results.filterIsInstance<StationResult.Error>().map { "${it.crs}: ${it.message}" }
}
