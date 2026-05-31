package com.example.transport_app.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsSubway
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.example.transport_app.data.model.BusStop
import com.example.transport_app.data.model.Station
import com.example.transport_app.data.model.StationEntry
import com.example.transport_app.data.model.TflDirection
import com.example.transport_app.data.model.TflLine
import com.example.transport_app.data.model.TflRailStation
import com.example.transport_app.data.model.TransportDataSource
import com.example.transport_app.data.model.TransportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditGroupScreen(
    onBack: () -> Unit,
    onSaveAndView: ((groupId: Long) -> Unit)? = null,
    viewModel: CreateEditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var destinationTargetIndex by remember { mutableStateOf<Int?>(null) }
    var routeFilterTargetIndex by remember { mutableStateOf<Int?>(null) }

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

    destinationTargetIndex?.let { index ->
        val entry = uiState.stations.getOrNull(index)
        if (entry != null) {
            DestinationSearchDialog(
                stationName = entry.stationName,
                onSearch = viewModel::searchDestinations,
                onSelect = { station ->
                    viewModel.setDestination(index, station)
                    destinationTargetIndex = null
                },
                onDismiss = { destinationTargetIndex = null }
            )
        } else {
            destinationTargetIndex = null
        }
    }

    uiState.directionPickerStop?.let { stop ->
        DirectionPickerDialog(
            stopName = stop.name,
            options = uiState.directionOptions,
            isLoading = uiState.isLoadingDirections,
            showAllOption = uiState.directionPickerHasAllOption,
            onSelectAll = { viewModel.selectDirection(null) },
            onSelect = { viewModel.selectDirection(it) },
            onDismiss = { viewModel.dismissDirectionPicker() }
        )
    }

    routeFilterTargetIndex?.let { index ->
        val entry = uiState.stations.getOrNull(index)
        if (entry != null) {
            LaunchedEffect(entry.crsCode) {
                viewModel.loadRoutesForStop(entry.crsCode)
            }
            RouteFilterDialog(
                stopName = entry.stationName,
                availableRoutes = uiState.availableRoutes,
                isLoadingRoutes = uiState.isLoadingRoutes,
                onConfirm = { route ->
                    viewModel.setRouteFilter(index, route)
                    viewModel.clearRoutes()
                    routeFilterTargetIndex = null
                },
                onDismiss = {
                    viewModel.clearRoutes()
                    routeFilterTargetIndex = null
                }
            )
        } else {
            routeFilterTargetIndex = null
        }
    }

    uiState.tflOptionStation?.let { station ->
        TflRailOptionsDialog(
            station = station,
            displayType = uiState.tflOptionType,
            lines = uiState.tflOptionLines,
            directions = uiState.tflOptionDirections,
            isLoadingOptions = uiState.isLoadingTflOptions,
            isLoadingDirections = uiState.isLoadingTflDirections,
            onLineChange = viewModel::loadTflDirections,
            onConfirm = viewModel::addTflRailStation,
            onDismiss = viewModel::dismissTflRailOptions
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
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.searchMode == TransportType.TRAIN,
                        onClick = { viewModel.onSearchModeChange(TransportType.TRAIN) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { Icon(Icons.Outlined.Train, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) { Text("Trains") }
                    SegmentedButton(
                        selected = uiState.searchMode == TransportType.BUS,
                        onClick = { viewModel.onSearchModeChange(TransportType.BUS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Outlined.DirectionsBus, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) { Text("Buses") }
                    SegmentedButton(
                        selected = uiState.searchMode == TransportType.TUBE,
                        onClick = { viewModel.onSearchModeChange(TransportType.TUBE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Outlined.DirectionsSubway, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    ) { Text("Tube") }
                }
            }

            item {
                when (uiState.searchMode) {
                    TransportType.TRAIN -> RailSearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        nationalResults = uiState.searchResults,
                        tflResults = uiState.tflRailSearchResults,
                        isSearchingTfl = uiState.isTflRailSearching,
                        label = "Search stations",
                        onSelectNational = viewModel::addStation,
                        onSelectTfl = viewModel::selectTflRailStation
                    )
                    TransportType.BUS -> BusStopSearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        results = uiState.busSearchResults,
                        isSearching = uiState.isBusSearching,
                        onSelect = { viewModel.addBusStop(it) }
                    )
                    TransportType.TUBE -> RailSearchField(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        nationalResults = emptyList(),
                        tflResults = uiState.tflRailSearchResults,
                        isSearchingTfl = uiState.isTflRailSearching,
                        label = "Search Tube or DLR stations",
                        onSelectNational = viewModel::addStation,
                        onSelectTfl = viewModel::selectTflRailStation
                    )
                }
            }

            if (uiState.stations.isEmpty()) {
                item {
                    Text(
                        "Add stations or stops to build your departure board.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                item {
                    Text(
                        "Stations (${uiState.stations.size})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            itemsIndexed(uiState.stations, key = { index, s -> "$index-${s.type}-${s.crsCode}-${s.filterCrs}" }) { index, station ->
                StationRow(
                    station = station,
                    onRemove = { viewModel.removeStation(index) },
                    onSetDestination = {
                        if (station.type == TransportType.BUS) {
                            routeFilterTargetIndex = index
                        } else if (station.dataSource == TransportDataSource.DARWIN) {
                            destinationTargetIndex = index
                        }
                    },
                    onClearDestination = { viewModel.clearDestination(index) }
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
    onSelect: (name: String, crs: String) -> Unit,
    label: String
) {
    val focusRequester = remember { FocusRequester() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
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
private fun RailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    nationalResults: List<Station>,
    tflResults: List<TflRailStation>,
    isSearchingTfl: Boolean,
    label: String,
    onSelectNational: (Station) -> Unit,
    onSelectTfl: (TflRailStation) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (isSearchingTfl) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        val hasResults = nationalResults.isNotEmpty() || tflResults.isNotEmpty()
        if (hasResults) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                nationalResults.forEach { station ->
                    Text(
                        "${station.name} (${station.crs})",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectNational(station)
                                focusRequester.requestFocus()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                }
                tflResults.forEach { station ->
                    val modes = station.modes.joinToString(", ") { mode -> tflModeLabel(mode) }
                    val suffix = if (modes.isBlank()) "" else " - $modes"
                    Text(
                        "${station.name}$suffix",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTfl(station)
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
private fun BusStopSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<BusStop>,
    isSearching: Boolean,
    onSelect: (BusStop) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Name or stop code (on the sign)") },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
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
                results.forEach { busStop ->
                    val displayName = if (busStop.indicator != null) "${busStop.name} — Stop ${busStop.indicator}"
                                      else busStop.name
                    Text(
                        displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(busStop)
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
private fun TflRailOptionsDialog(
    station: TflRailStation,
    displayType: String,
    lines: List<TflLine>,
    directions: List<TflDirection>,
    isLoadingOptions: Boolean,
    isLoadingDirections: Boolean,
    onLineChange: (TflLine?) -> Unit,
    onConfirm: (TflLine?, TflDirection?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLine by remember(station.id) { mutableStateOf<TflLine?>(null) }
    var selectedDirection by remember(station.id) { mutableStateOf<TflDirection?>(null) }
    val modeLabel = if (displayType == TransportType.TUBE) "Tube" else "train"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $modeLabel station") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    station.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoadingOptions) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                Text(
                    "Line",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OptionRow(
                    label = "All lines",
                    selected = selectedLine == null,
                    onClick = {
                        selectedLine = null
                        selectedDirection = null
                        onLineChange(null)
                    }
                )
                lines.forEach { line ->
                    OptionRow(
                        label = line.name,
                        selected = selectedLine?.id == line.id,
                        onClick = {
                            selectedLine = line
                            selectedDirection = null
                            onLineChange(line)
                        }
                    )
                }

                Text(
                    "Direction",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OptionRow(
                    label = "All directions",
                    selected = selectedDirection == null,
                    onClick = { selectedDirection = null }
                )
                if (isLoadingDirections) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    directions.forEach { direction ->
                        OptionRow(
                            label = direction.label,
                            selected = selectedDirection?.id == direction.id,
                            onClick = { selectedDirection = direction }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedLine, selectedDirection) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
    HorizontalDivider()
}

@Composable
private fun RouteFilterDialog(
    stopName: String,
    availableRoutes: List<String>,
    isLoadingRoutes: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var route by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by route") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Only show a specific bus route at $stopName.", style = MaterialTheme.typography.bodyMedium)

                if (isLoadingRoutes) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (availableRoutes.isNotEmpty()) {
                    Text(
                        "Available routes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    availableRoutes.forEach { routeName ->
                        Text(
                            routeName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(routeName) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider()
                    }
                }

                OutlinedTextField(
                    value = route,
                    onValueChange = { route = it },
                    label = { Text("Or type a route number") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (route.isNotBlank()) onConfirm(route.trim()) },
                enabled = route.isNotBlank()
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun tflModeLabel(mode: String): String = when (mode) {
    "tube" -> "Underground"
    "dlr" -> "DLR"
    "overground" -> "Overground"
    "elizabeth-line" -> "Elizabeth line"
    else -> mode
}

@Composable
private fun DirectionPickerDialog(
    stopName: String,
    options: List<BusStop>,
    isLoading: Boolean,
    showAllOption: Boolean = true,
    onSelectAll: () -> Unit,
    onSelect: (BusStop) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose direction") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stopName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    if (showAllOption) {
                        Text(
                            "All directions",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectAll() }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider()
                    }

                    options.forEach { child ->
                        val label = buildList {
                            child.indicator?.let { add(it) }
                            child.towards?.let { add("towards $it") }
                        }.joinToString(" ").ifEmpty { child.name }

                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(child) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider()
                    }
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
    val isBus = station.type == TransportType.BUS
    val isTube = station.type == TransportType.TUBE
    val isTflRail = station.dataSource == TransportDataSource.TFL && !isBus
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            isBus -> Icons.Outlined.DirectionsBus
                            isTube -> Icons.Outlined.DirectionsSubway
                            else -> Icons.Outlined.Train
                        },
                        contentDescription = when {
                            isBus -> "Bus"
                            isTube -> "Tube"
                            else -> "Train"
                        },
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(station.stationName, style = MaterialTheme.typography.bodyLarge)
                }
                if (!isBus && !isTflRail) {
                    Text(
                        station.crsCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val filterText = when {
                    isTflRail && station.filterName != null && station.directionName != null ->
                        "${station.filterName}, ${station.directionName}"
                    isTflRail && station.filterName != null -> station.filterName
                    isTflRail && station.directionName != null -> station.directionName
                    else -> station.filterName
                }
                if (filterText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                isBus || isTflRail -> filterText
                                else -> "\u2192 $filterText"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onClearDestination) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(0.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        when {
                            isBus -> "Filter by route"
                            isTflRail -> "All lines and directions"
                            else -> "Add destination"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTflRail) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = if (isTflRail) {
                            Modifier.padding(vertical = 2.dp)
                        } else {
                            Modifier
                                .clickable { onSetDestination() }
                                .padding(vertical = 2.dp)
                        }
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Clear, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
