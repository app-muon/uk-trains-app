package com.example.transport_app.ui.groups

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.transport_app.data.model.GroupWithCount
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    onGroupClick: (Long) -> Unit,
    onCreateGroup: () -> Unit,
    onEditGroup: (Long) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    var displayedGroups by remember { mutableStateOf(groups) }
    var draggedGroupId by remember { mutableStateOf<Long?>(null) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var draggedGroupOffset by remember { mutableStateOf(0f) }
    var groupItemStepPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val fallbackGroupStepPx = with(density) { 72.dp.toPx() }

    LaunchedEffect(groups) {
        if (draggedGroupId == null) displayedGroups = groups
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Boards") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateGroup) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
            }
        }
    ) { innerPadding ->
        if (displayedGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Train,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Welcome to Transport",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A board shows live departures from train stations and London bus stops. You can filter trains by destination or buses by route.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Tap + to create your first board",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayedGroups, key = { _, group -> group.id }) { index, group ->
                    GroupCard(
                        group = group,
                        index = index,
                        itemCount = displayedGroups.size,
                        visualOffset = groupVisualOffset(
                            index = index,
                            groupId = group.id,
                            draggedGroupId = draggedGroupId,
                            dragStartIndex = dragStartIndex,
                            dragTargetIndex = dragTargetIndex,
                            draggedOffset = draggedGroupOffset,
                            itemStepPx = groupItemStepPx.takeIf { it > 0f } ?: fallbackGroupStepPx
                        ),
                        isDragging = draggedGroupId == group.id,
                        onClick = { onGroupClick(group.id) },
                        onEdit = { onEditGroup(group.id) },
                        onItemStepMeasured = { groupItemStepPx = it },
                        onDragStart = {
                            draggedGroupId = group.id
                            dragStartIndex = index
                            dragTargetIndex = index
                            draggedGroupOffset = 0f
                        },
                        onDragOffset = { offset ->
                            draggedGroupOffset = offset
                            val start = dragStartIndex ?: index
                            val step = groupItemStepPx.takeIf { it > 0f } ?: fallbackGroupStepPx
                            dragTargetIndex = (start + (offset / step).roundToInt())
                                .coerceIn(0, displayedGroups.lastIndex)
                        },
                        onDragEnd = {
                            val from = dragStartIndex
                            val to = dragTargetIndex
                            if (from != null && to != null && from != to) {
                                val reordered = displayedGroups.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                                displayedGroups = reordered
                                viewModel.saveGroupOrder(reordered)
                            }
                            draggedGroupId = null
                            dragStartIndex = null
                            dragTargetIndex = null
                            draggedGroupOffset = 0f
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupWithCount,
    index: Int,
    itemCount: Int,
    visualOffset: Float,
    isDragging: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onItemStepMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragOffset: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val itemSpacingPx = with(density) { 8.dp.toPx() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onItemStepMeasured(it.height.toFloat() + itemSpacingPx) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = visualOffset
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
            }
            .padding(horizontal = if (isDragging) 2.dp else 0.dp)
            .padding(vertical = if (isDragging) 2.dp else 0.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReorderHandle(
                index = index,
                itemCount = itemCount,
                onDragStart = onDragStart,
                onDragOffset = onDragOffset,
                onDragEnd = onDragEnd
            )
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${group.stationCount} station${if (group.stationCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit board",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReorderHandle(
    index: Int,
    itemCount: Int,
    onDragStart: () -> Unit,
    onDragOffset: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .pointerInput(index, itemCount) {
                var totalDrag = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        totalDrag = 0f
                        onDragStart()
                    },
                    onDragEnd = {
                        totalDrag = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        onDragEnd()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount.y
                        onDragOffset(totalDrag)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = "Drag to reorder",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun groupVisualOffset(
    index: Int,
    groupId: Long,
    draggedGroupId: Long?,
    dragStartIndex: Int?,
    dragTargetIndex: Int?,
    draggedOffset: Float,
    itemStepPx: Float
): Float {
    val start = dragStartIndex ?: return 0f
    val target = dragTargetIndex ?: return 0f
    if (groupId == draggedGroupId) return draggedOffset
    return when {
        target > start && index in (start + 1)..target -> -itemStepPx
        target < start && index in target until start -> itemStepPx
        else -> 0f
    }
}
