package com.orotrain.oro.ui.components

import androidx.compose.foundation.background
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
import com.orotrain.oro.model.SPM_GREEN_THRESHOLD
import com.orotrain.oro.model.SPM_YELLOW_THRESHOLD
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
    modifier: Modifier = Modifier
) {
    val gradient = when {
        zone.spm <= SPM_GREEN_THRESHOLD -> Brush.linearGradient(listOf(ZoneGreenStart, ZoneGreenEnd))
        zone.spm <= SPM_YELLOW_THRESHOLD -> Brush.linearGradient(listOf(ZoneYellowStart, ZoneYellowEnd))
        else -> Brush.linearGradient(listOf(ZoneRedStart, ZoneRedEnd))
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zone ${index + 1}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onAddAfter) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add zone after",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDuplicate) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Duplicate zone",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove zone",
                        tint = Color.White
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

        ZoneControl(
            label = "SPM",
            value = zone.spm,
            onIncrement = { onAdjust(ZoneField.Spm, 1) },
            onDecrement = { onAdjust(ZoneField.Spm, -1) }
        )

        if (isLast) {
            OutlinedButton(
                onClick = { /* TODO: hook up start action */ },
                modifier = Modifier.fillMaxWidth(),
                border = null,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = Charcoal
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
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
                    tint = Color.White
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            )
            IconButton(onClick = onIncrement) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Increase $label",
                    tint = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

