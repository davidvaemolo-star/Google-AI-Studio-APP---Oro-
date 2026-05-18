package com.orotrain.oro.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.orotrain.oro.model.MAX_ZONES
import com.orotrain.oro.model.Programme
import com.orotrain.oro.model.ZoneField
import com.orotrain.oro.ui.components.ZoneCard
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyGridState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgrammeEditorScreen(
    programme: Programme,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onAddZone: () -> Unit,
    onRemoveZone: (String) -> Unit,
    onDuplicateZone: (String) -> Unit,
    onAddAfterZone: (String) -> Unit,
    onAdjustZone: (String, ZoneField, Int) -> Unit,
    onReorderZones: (Int, Int) -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = programme.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = onLoad) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load into Session")
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onAddZone,
                        enabled = programme.zones.size < MAX_ZONES
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add zone")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Zone")
                    }
                }
            }

            if (programme.zones.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "Tap Add Zone to build your programme.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = programme.zones,
                    key = { _, zone -> zone.id }
                ) { index, zone ->
                    ReorderableItem(reorderState, key = zone.id) { isDragging ->
                        LaunchedEffect(isDragging) {
                            if (isDragging) {
                                isAnyDragging = true
                            } else {
                                delay(100)
                                isAnyDragging = false
                            }
                        }

                        ZoneCard(
                            zone = zone,
                            index = index,
                            isLast = index == programme.zones.lastIndex,
                            onAdjust = { field, delta -> onAdjustZone(zone.id, field, delta) },
                            onRemove = { onRemoveZone(zone.id) },
                            onDuplicate = { onDuplicateZone(zone.id) },
                            onAddAfter = { onAddAfterZone(zone.id) },
                            onStart = {},
                            canStartTraining = false,
                            isDragging = isDragging,
                            isAnyDragging = isAnyDragging && !isDragging,
                            modifier = Modifier.detectReorderAfterLongPress(reorderState)
                        )
                    }
                }
            }
        }
    }
}
