package com.example.uk_trains_app.ui.departures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.uk_trains_app.data.model.CallingPoint
import com.example.uk_trains_app.data.model.ServiceDetail

private val GreenOk = Color(0xFF2E7D32)
private val OrangeWarning = Color(0xFFE65100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(
    onBack: () -> Unit,
    viewModel: ServiceDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service details") },
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
            uiState.detail != null -> {
                ServiceDetailContent(
                    detail = uiState.detail!!,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ServiceDetailContent(detail: ServiceDetail, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Service summary
        item {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (detail.operator != null) {
                    Text(detail.operator, style = MaterialTheme.typography.titleMedium)
                }
                if (detail.isCancelled) {
                    Text("CANCELLED", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                if (detail.cancelReason != null) {
                    Text(detail.cancelReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (detail.delayReason != null) {
                    Text(detail.delayReason, style = MaterialTheme.typography.bodySmall, color = OrangeWarning)
                }
            }
        }

        // Previous calling points
        if (detail.previousCallingPoints.isNotEmpty()) {
            item { SectionHeader("Previous stops") }
            item { CallingPointColumnHeader() }
            items(detail.previousCallingPoints) { cp ->
                CallingPointRow(cp)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        // This station
        item {
            SectionHeader(detail.locationName)
        }
        item {
            ThisStationRow(detail)
        }

        // Subsequent calling points
        if (detail.subsequentCallingPoints.isNotEmpty()) {
            item { SectionHeader("Upcoming stops") }
            item { CallingPointColumnHeader() }
            items(detail.subsequentCallingPoints) { cp ->
                CallingPointRow(cp)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
private fun CallingPointColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Station", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
        Text("Sched", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.17f), fontWeight = FontWeight.Bold)
        Text("Actual", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.33f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CallingPointRow(cp: CallingPoint) {
    val timeText = cp.actualTime ?: cp.estimatedTime ?: ""
    val timeColor = when {
        cp.isCancelled -> MaterialTheme.colorScheme.error
        cp.actualTime != null -> GreenOk
        cp.estimatedTime == "On time" -> GreenOk
        cp.estimatedTime != null -> OrangeWarning
        else -> MaterialTheme.colorScheme.onSurface
    }
    val displayTime = when {
        cp.isCancelled -> "Cancelled"
        cp.actualTime != null -> cp.actualTime
        cp.estimatedTime == "On time" -> "On time"
        cp.estimatedTime != null -> "Exp ${cp.estimatedTime}"
        else -> ""
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cp.stationName, modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium)
        Text(cp.scheduledTime, modifier = Modifier.weight(0.17f), style = MaterialTheme.typography.bodyMedium)
        Text(displayTime, modifier = Modifier.weight(0.33f), style = MaterialTheme.typography.bodyMedium, color = timeColor)
    }
}

@Composable
private fun ThisStationRow(detail: ServiceDetail) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (detail.platform != null) {
                LabelValue("Platform", detail.platform)
            }
            if (detail.sta != null) {
                LabelValue("Arrives", detail.sta)
                if (detail.eta != null && detail.eta != "On time") {
                    LabelValue("Expected", detail.eta)
                }
            }
            if (detail.std != null) {
                LabelValue("Departs", detail.std)
                if (detail.etd != null && detail.etd != "On time") {
                    LabelValue("Expected", detail.etd)
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
