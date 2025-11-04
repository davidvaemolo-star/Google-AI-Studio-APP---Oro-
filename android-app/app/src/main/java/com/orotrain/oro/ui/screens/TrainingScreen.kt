package com.orotrain.oro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import com.orotrain.oro.model.MAX_DEVICES
import com.orotrain.oro.model.MAX_ZONES
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.model.ZoneField
import com.orotrain.oro.ui.components.ZoneCard
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyGridState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrainingScreen(
    state: OroUiState,
    onAddZone: () -> Unit,
    onRemoveZone: (String) -> Unit,
    onDuplicateZone: (String) -> Unit,
    onAddAfterZone: (String) -> Unit,
    onAdjustZone: (String, ZoneField, Int) -> Unit,
    onReorderZones: (Int, Int) -> Unit,
    onStartTraining: () -> Unit = {},
    onPauseTraining: () -> Unit = {},
    onResumeTraining: () -> Unit = {},
    onStopTraining: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val reorderState = rememberReorderableLazyGridState(onMove = { from, to ->
        onReorderZones(from.index, to.index)
    })

    var isAnyDragging by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 240.dp),
            state = reorderState.gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .reorderable(reorderState),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onAddZone,
                    enabled = state.zones.size < MAX_ZONES
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add training zone"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Add Training Zone")
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${state.connectedDevicesCount} of $MAX_DEVICES devices connected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        // Show pre-training checklist if zones exist but training not active
        if (state.zones.isNotEmpty() && !state.trainingSession.isActive) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                com.orotrain.oro.ui.components.PreTrainingChecklist(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.zones.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "Tap Add Training Zone to start building your plan.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = state.zones,
                key = { _, zone -> zone.id }
            ) { index, zone ->
                ReorderableItem(reorderState, key = zone.id) { isDragging ->
                    // Update global drag state when this item starts dragging
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                            isAnyDragging = true
                        } else {
                            // Small delay to allow recomposition before resetting
                            delay(100)
                            isAnyDragging = false
                        }
                    }

                    ZoneCard(
                        zone = zone,
                        index = index,
                        isLast = index == state.zones.lastIndex,
                        onAdjust = { field, delta -> onAdjustZone(zone.id, field, delta) },
                        onRemove = { onRemoveZone(zone.id) },
                        onDuplicate = { onDuplicateZone(zone.id) },
                        onAddAfter = { onAddAfterZone(zone.id) },
                        onStart = onStartTraining,
                        canStartTraining = state.canStartTraining,
                        isDragging = isDragging,
                        isAnyDragging = isAnyDragging && !isDragging,
                        modifier = Modifier
                            .detectReorderAfterLongPress(reorderState)
                    )
                }
            }
        }
    }

        // Active Training Overlay
        com.orotrain.oro.ui.components.ActiveTrainingOverlay(
            state = state,
            onPause = onPauseTraining,
            onResume = onResumeTraining,
            onStop = onStopTraining
        )
    }
}
