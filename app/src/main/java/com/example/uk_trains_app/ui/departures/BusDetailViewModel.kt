package com.example.uk_trains_app.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uk_trains_app.data.model.BusStopArrival
import com.example.uk_trains_app.data.network.TflApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BusDetailUiState(
    val isLoading: Boolean = true,
    val lineName: String = "",
    val towards: String = "",
    val stops: List<BusStopArrival> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class BusDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tflApiClient: TflApiClient
) : ViewModel() {

    private val vehicleId: String = checkNotNull(savedStateHandle["vehicleId"])

    private val _uiState = MutableStateFlow(BusDetailUiState())
    val uiState: StateFlow<BusDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val stops = tflApiClient.getVehicleArrivals(vehicleId)
                val lineName = stops.firstOrNull()?.lineName ?: ""
                val towards = stops.lastOrNull()?.stopName ?: ""
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lineName = lineName,
                        towards = towards,
                        stops = stops
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }
}
