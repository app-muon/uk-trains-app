package com.example.transport_app.ui.departures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.transport_app.data.model.BusStopArrival
import com.example.transport_app.ui.theme.StatusGreen
import com.example.transport_app.ui.theme.StatusOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusDetailScreen(
    onBack: () -> Unit,
    viewModel: BusDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.lineName.isNotEmpty()) {
                        Text("Route ${uiState.lineName}")
                    } else {
                        Text("Bus details")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { Text(uiState.error!!, color = MaterialTheme.colorScheme.error) }
            }
            uiState.stops.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { Text("No stop information available.") }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (uiState.towards.isNotEmpty()) {
                        item {
                            Text(
                                "Towards ${uiState.towards}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    item { SectionHeader("Upcoming stops") }
                    item { StopColumnHeader() }

                    itemsIndexed(uiState.stops) { index, stop ->
                        StopRow(stop)
                        if (index < uiState.stops.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun StopColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Stop", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.55f), fontWeight = FontWeight.Bold)
        Text("Time", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold)
        Text("Due", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.25f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StopRow(stop: BusStopArrival) {
    val dueText = if (stop.minutesAway <= 0) "Due" else "${stop.minutesAway} min"
    val dueColor = if (stop.minutesAway <= 0) StatusGreen else StatusOrange

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stop.stopName,
            modifier = Modifier.weight(0.55f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stop.expectedTime,
            modifier = Modifier.weight(0.2f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            dueText,
            modifier = Modifier.weight(0.25f),
            style = MaterialTheme.typography.bodyMedium,
            color = dueColor
        )
    }
}
