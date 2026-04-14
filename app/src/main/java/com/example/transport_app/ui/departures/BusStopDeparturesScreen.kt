package com.example.transport_app.ui.departures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.transport_app.data.model.Departure
import com.example.transport_app.ui.theme.StatusGreen
import com.example.transport_app.ui.theme.StatusOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusStopDeparturesScreen(
    onBack: () -> Unit,
    onBusClick: (vehicleId: String) -> Unit,
    viewModel: BusStopDeparturesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.stopName.ifEmpty { "Bus stop" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StopInfoCard(
                naptanId = uiState.naptanId,
                indicator = uiState.indicator,
                towards = uiState.towards
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(uiState.error!!, color = MaterialTheme.colorScheme.error) }
                }
                uiState.departures.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text("No departures found.") }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item { BusStopDepartureHeader() }
                        items(uiState.departures) { departure ->
                            BusStopDepartureRow(departure, onBusClick)
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopInfoCard(naptanId: String, indicator: String?, towards: String?) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Stop code: $naptanId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(naptanId)) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy stop code",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (indicator != null) {
                Text(
                    indicator,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (towards != null) {
                Text(
                    "Towards $towards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BusStopDepartureHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Route", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.15f), fontWeight = FontWeight.Bold)
        Text("Destination", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.55f), fontWeight = FontWeight.Bold)
        Text("Due", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BusStopDepartureRow(departure: Departure, onBusClick: (String) -> Unit) {
    val hasRealVehicleId = !departure.serviceId.contains("-")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (hasRealVehicleId) Modifier.clickable { onBusClick(departure.serviceId) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            departure.routeNumber ?: "",
            modifier = Modifier.weight(0.15f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            departure.destination,
            modifier = Modifier.weight(0.55f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            departure.estimatedTime,
            modifier = Modifier.weight(0.3f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (departure.estimatedTime == "Due") StatusGreen else StatusOrange
        )
    }
}
