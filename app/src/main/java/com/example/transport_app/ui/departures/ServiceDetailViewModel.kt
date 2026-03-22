package com.example.transport_app.ui.departures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.transport_app.data.model.ServiceDetail
import com.example.transport_app.data.network.DarwinSoapClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceDetailUiState(
    val isLoading: Boolean = true,
    val detail: ServiceDetail? = null,
    val error: String? = null
)

@HiltViewModel
class ServiceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val soapClient: DarwinSoapClient
) : ViewModel() {

    private val serviceId: String = checkNotNull(savedStateHandle["serviceId"])

    private val _uiState = MutableStateFlow(ServiceDetailUiState())
    val uiState: StateFlow<ServiceDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val detail = soapClient.getServiceDetails(serviceId)
                _uiState.update { it.copy(isLoading = false, detail = detail) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }
}
