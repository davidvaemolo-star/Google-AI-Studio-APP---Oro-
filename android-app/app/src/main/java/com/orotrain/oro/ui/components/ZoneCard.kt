package com.orotrain.oro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneField
import com.orotrain.oro.ui.theme.AccentCyan
import com.orotrain.oro.ui.theme.Charcoal
import com.orotrain.oro.ui.theme.ZoneGreenEnd
import com.orotrain.oro.ui.theme.ZoneGreenStart
import com.orotrain.oro.ui.theme.ZoneRedEnd
import com.orotrain.oro.ui.theme.ZoneRedStart
import com.orotrain.oro.ui.theme.ZoneYellowEnd
import com.orotrain.oro.ui.theme.ZoneYellowStart

@Composable
fun ZoneCard(
    zone: Zone,
    index: Int,
    isLast: Boolean,
    onAdjust: (ZoneField, Int) -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onAddAfter: () -> Unit,
    onStart: () -> Unit = {},
    canStartTraining: Boolean = false,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    isAnyDragging: Boolean = false
) {
    val gradient = when (zone.level) {
        com.orotrain.oro.model.ZoneLevel.Low -> Brush.linearGradient(listOf(ZoneGreenStart, ZoneGreenEnd))
        com.orotrain.oro.model.ZoneLevel.Medium -> Brush.linearGradient(listOf(ZoneYellowStart, ZoneYellowEnd))
        com.orotrain.oro.model.ZoneLevel.High -> Brush.linearGradient(listOf(ZoneRedStart, ZoneRedEnd))
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = if (isDragging) 12.dp else 4.dp,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .then(
                when {
                    isDragging -> Modifier.border(
                        width = 4.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp)
                    )
                    isAnyDragging -> Modifier.border(
                        width = 3.dp,
                        color = AccentCyan.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    else -> Modifier
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zone ${index + 1}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onAddAfter, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add zone after",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Duplicate zone",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove zone",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        ZoneControl(
            label = "Strokes",
            value = zone.strokes,
            onIncrement = { onAdjust(ZoneField.Strokes, 1) },
            onDecrement = { onAdjust(ZoneField.Strokes, -1) }
        )

        ZoneControl(
            label = "Sets",
            value = zone.sets,
            onIncrement = { onAdjust(ZoneField.Sets, 1) },
            onDecrement = { onAdjust(ZoneField.Sets, -1) }
        )

        ZoneLevelSelector(
            currentLevel = zone.level,
            onSelectLevel = { level ->
                // Calculate delta to reach the desired level
                val levels = com.orotrain.oro.model.ZoneLevel.values()
                val currentIndex = levels.indexOf(zone.level)
                val targetIndex = levels.indexOf(level)
                val delta = targetIndex - currentIndex
                if (delta != 0) {
                    onAdjust(com.orotrain.oro.model.ZoneField.Level, delta)
                }
            }
        )

        if (isLast) {
            OutlinedButton(
                onClick = onStart,
                enabled = canStartTraining,
                modifier = Modifier.fillMaxWidth(),
                border = null,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Start Training",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = if (canStartTraining) Charcoal else Charcoal.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ZoneControl(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onDecrement) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Decrease $label",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            )
            IconButton(onClick = onIncrement) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $label",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun ZoneLevelSelector(
    currentLevel: com.orotrain.oro.model.ZoneLevel,
    onSelectLevel: (com.orotrain.oro.model.ZoneLevel) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "INTENSITY",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Low Zone Button
            ZoneLevelButton(
                level = com.orotrain.oro.model.ZoneLevel.Low,
                label = "Low",
                spmRange = "30-45",
                isSelected = currentLevel == com.orotrain.oro.model.ZoneLevel.Low,
                gradient = Brush.linearGradient(listOf(ZoneGreenStart, ZoneGreenEnd)),
                onClick = { onSelectLevel(com.orotrain.oro.model.ZoneLevel.Low) },
                modifier = Modifier.weight(1f)
            )

            // Medium Zone Button
            ZoneLevelButton(
                level = com.orotrain.oro.model.ZoneLevel.Medium,
                label = "Medium",
                spmRange = "46-60",
                isSelected = currentLevel == com.orotrain.oro.model.ZoneLevel.Medium,
                gradient = Brush.linearGradient(listOf(ZoneYellowStart, ZoneYellowEnd)),
                onClick = { onSelectLevel(com.orotrain.oro.model.ZoneLevel.Medium) },
                modifier = Modifier.weight(1f)
            )

            // High Zone Button
            ZoneLevelButton(
                level = com.orotrain.oro.model.ZoneLevel.High,
                label = "High",
                spmRange = "61-80",
                isSelected = currentLevel == com.orotrain.oro.model.ZoneLevel.High,
                gradient = Brush.linearGradient(listOf(ZoneRedStart, ZoneRedEnd)),
                onClick = { onSelectLevel(com.orotrain.oro.model.ZoneLevel.High) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun ZoneLevelButton(
    level: com.orotrain.oro.model.ZoneLevel,
    label: String,
    spmRange: String,
    isSelected: Boolean,
    gradient: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(64.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(3.dp, Color.White)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = spmRange,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            )
        }
    }
}

