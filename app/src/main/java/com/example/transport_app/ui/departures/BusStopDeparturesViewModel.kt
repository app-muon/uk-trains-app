package com.example.transport_app.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.transport_app.data.model.Departure
import com.example.transport_app.data.network.TflApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BusStopDeparturesUiState(
    val isLoading: Boolean = true,
    val stopName: String = "",
    val naptanId: String = "",
    val indicator: String? = null,
    val towards: String? = null,
    val departures: List<Departure> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class BusStopDeparturesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tflApiClient: TflApiClient
) : ViewModel() {

    private val naptanId: String = checkNotNull(savedStateHandle["naptanId"])
    private val stopName: String = savedStateHandle["stopName"] ?: ""

    private val _uiState = MutableStateFlow(
        BusStopDeparturesUiState(stopName = stopName, naptanId = naptanId)
    )
    val uiState: StateFlow<BusStopDeparturesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val departuresDeferred = async { tflApiClient.getArrivals(naptanId) }
                val stopInfoDeferred = async {
                    try { tflApiClient.getStopInfo(naptanId) } catch (_: Exception) { null }
                }
                val departures = departuresDeferred.await()
                val stopInfo = stopInfoDeferred.await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        departures = departures,
                        indicator = stopInfo?.indicator,
                        towards = stopInfo?.towards,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }
}
