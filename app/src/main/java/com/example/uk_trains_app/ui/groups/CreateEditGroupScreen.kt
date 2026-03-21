package com.example.uk_trains_app.ui.groups

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.uk_trains_app.data.model.Station
import com.example.uk_trains_app.data.model.StationEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditGroupScreen(
    onBack: () -> Unit,
    onSaveAndView: ((groupId: Long) -> Unit)? = null,
    viewModel: CreateEditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var destinationTarget by remember { mutableStateOf<StationEntry?>(null) }

    var wantsView by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved, uiState.savedGroupId) {
        val groupId = uiState.savedGroupId
        if (uiState.isSaved && groupId != null) {
            if (wantsView && onSaveAndView != null) {
                onSaveAndView(groupId)
            } else {
                onBack()
            }
        }
    }
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBack()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${uiState.groupName}\"?") },
            text = { Text("This will remove the board and all its stations.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteGroup()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    destinationTarget?.let { entry ->
        DestinationSearchDialog(
            stationName = entry.stationName,
            onSearch = viewModel::searchDestinations,
            onSelect = { station ->
                viewModel.setDestination(entry, station)
                destinationTarget = null
            },
            onDismiss = { destinationTarget = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.groupName.isEmpty()) "New Board" else uiState.groupName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete board",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = uiState.groupName,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Board name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                StationSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    results = uiState.searchResults.map { it.name to it.crs },
                    onSelect = { name, crs ->
                        viewModel.addStation(Station(name, crs))
                    }
                )
            }

            if (uiState.stations.isNotEmpty()) {
                item {
                    Text(
                        "Stations (${uiState.stations.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            items(uiState.stations, key = { it.crsCode }) { station ->
                StationRow(
                    station = station,
                    onRemove = { viewModel.removeStation(station) },
                    onSetDestination = { destinationTarget = station },
                    onClearDestination = { viewModel.clearDestination(station) }
                )
            }

            item {
                Button(
                    onClick = {
                        wantsView = true
                        viewModel.save()
                    },
                    enabled = uiState.groupName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text("Save and view")
                }
                TextButton(
                    onClick = {
                        wantsView = false
                        viewModel.save()
                    },
                    enabled = uiState.groupName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("Just save")
                }
            }
        }
    }
}

@Composable
private fun StationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Pair<String, String>>,
    onSelect: (name: String, crs: String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search stations") },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        if (results.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                results.forEach { (name, crs) ->
                    Text(
                        "$name ($crs)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(name, crs)
                                focusRequester.requestFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DestinationSearchDialog(
    stationName: String,
    onSearch: (String) -> List<Station>,
    onSelect: (Station) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Station>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Destination from $stationName") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        results = onSearch(it)
                    },
                    label = { Text("Search destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                results.take(8).forEach { station ->
                    Text(
                        "${station.name} (${station.crs})",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(station) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StationRow(
    station: StationEntry,
    onRemove: () -> Unit,
    onSetDestination: () -> Unit,
    onClearDestination: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(station.stationName, style = MaterialTheme.typography.bodyLarge)
                Text(station.crsCode, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (station.filterName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "\u2192 ${station.filterName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onClearDestination) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear destination",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(0.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        "Add destination",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onSetDestination() }
                            .padding(vertical = 2.dp)
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Clear, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
