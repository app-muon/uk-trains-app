package com.example.transport_app.ui.departures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.transport_app.data.model.Departure
import com.example.transport_app.data.model.TransportType
import com.example.transport_app.ui.theme.BusHeaderBg
import com.example.transport_app.ui.theme.BusHeaderFg
import com.example.transport_app.ui.theme.StatusGreen
import com.example.transport_app.ui.theme.StatusOrange
import com.example.transport_app.ui.theme.TrainHeaderBg
import com.example.transport_app.ui.theme.TrainHeaderFg
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    onBack: () -> Unit,
    onServiceClick: (serviceId: String) -> Unit,
    onBusClick: (vehicleId: String) -> Unit = {},
    viewModel: DeparturesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val lastFetched = uiState.lastFetchedAt
    val isStale = lastFetched != null && (nowMs - lastFetched) > 2 * 60 * 1_000L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.groupName.ifEmpty { "Departures" }) },
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Offline / cached data warning
            if (uiState.hasAnyFromCache) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Offline — showing cached data",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Stale data warning
            if (isStale && !uiState.hasAnyFromCache) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Data may be stale — tap Refresh",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Last fetched timestamp
            uiState.lastFetchedAt?.let { ts ->
                item {
                    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.UK) }
                    Text(
                        "Last updated ${fmt.format(Date(ts))}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Per-station errors
            if (uiState.stationErrors.isNotEmpty()) {
                items(uiState.stationErrors) { error ->
                    Text(
                        text = "⚠ $error",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Empty state
            if (uiState.sections.isEmpty() && uiState.stationErrors.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No departures found.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Station sections
            uiState.sections.forEach { section ->
                item {
                    StationHeader(section.headerText, section.type)
                }
                if (section.type == TransportType.BUS) {
                    item {
                        BusStopCodeRow(section.crsCode)
                    }
                }
                if (section.messages.isNotEmpty()) {
                    items(section.messages) { msg ->
                        Text(
                            text = msg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (section.type == TransportType.BUS) {
                    item { BusDepartureHeader() }
                    items(section.departures) { departure ->
                        val hasRealVehicleId = !departure.serviceId.contains("-")
                        BusDepartureRow(
                            departure,
                            onClick = if (hasRealVehicleId) {{ onBusClick(departure.serviceId) }} else null
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else {
                    item { DepartureHeader() }
                    items(section.departures) { departure ->
                        DepartureRow(departure, onClick = { onServiceClick(departure.serviceId) })
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            // More times button (only for train sections — bus arrivals don't support time offset)
            if (uiState.sections.any { it.type == TransportType.TRAIN }) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoadingMore) {
                            CircularProgressIndicator()
                        } else {
                            OutlinedButton(onClick = viewModel::loadMore) {
                                Text("More times")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusStopCodeRow(naptanId: String) {
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Stop code: $naptanId",
            style = MaterialTheme.typography.labelSmall,
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
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StationHeader(name: String, type: String) {
    val isBus = type == TransportType.BUS
    val bg = if (isBus) BusHeaderBg else TrainHeaderBg
    val fg = if (isBus) BusHeaderFg else TrainHeaderFg
    val icon = if (isBus) Icons.Outlined.DirectionsBus else Icons.Outlined.Train

    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
private fun DepartureHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Time", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.15f), fontWeight = FontWeight.Bold)
        Text("Destination", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.45f), fontWeight = FontWeight.Bold)
        Text("Plat", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.1f), fontWeight = FontWeight.Bold)
        Text("Status", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BusDepartureHeader() {
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
private fun BusDepartureRow(departure: Departure, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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

@Composable
private fun DepartureRow(departure: Departure, onClick: () -> Unit) {
    val statusColor = when {
        departure.isCancelled -> MaterialTheme.colorScheme.error
        departure.estimatedTime == "On time" -> StatusGreen
        departure.estimatedTime == "Delayed" -> MaterialTheme.colorScheme.error
        else -> StatusOrange
    }
    val statusText = when {
        departure.isCancelled -> "Cancelled"
        departure.estimatedTime == "On time" -> "On time"
        departure.estimatedTime == "Delayed" -> "Delayed"
        departure.estimatedTime.matches(Regex("\\d{2}:\\d{2}")) ->
            "Exp ${departure.estimatedTime}"
        else -> departure.estimatedTime
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            departure.scheduledTime,
            modifier = Modifier.weight(0.15f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            departure.destination,
            modifier = Modifier.weight(0.45f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            departure.platform ?: "–",
            modifier = Modifier.weight(0.1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            statusText,
            modifier = Modifier.weight(0.3f),
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
    }
}
