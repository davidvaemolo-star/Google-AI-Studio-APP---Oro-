package com.orotrain.oro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.MAX_DEVICES
import com.orotrain.oro.model.OroUiState

data class ChecklistItem(
    val label: String,
    val isPassed: Boolean,
    val isWarning: Boolean = false,
    val details: String? = null
)

@Composable
fun PreTrainingChecklist(
    state: OroUiState,
    modifier: Modifier = Modifier
) {
    val connectedDevices = state.devices.filter { it.status == DeviceStatus.Connected }
    val pacerDevice = connectedDevices.find { it.seat == 1 }

    val checklistItems = listOf(
        ChecklistItem(
            label = "Devices Connected",
            isPassed = state.connectedDevicesCount > 0,
            details = "${state.connectedDevicesCount} of $MAX_DEVICES devices connected"
        ),
        ChecklistItem(
            label = "Pacer Assigned",
            isPassed = pacerDevice != null,
            details = if (pacerDevice != null) "Seat 1: ${pacerDevice.name}" else "No device in Seat 1"
        ),
        ChecklistItem(
            label = "Battery Levels",
            isPassed = connectedDevices.all { it.batteryLevel != null && it.batteryLevel > 20 },
            isWarning = connectedDevices.any { it.batteryLevel != null && it.batteryLevel in 21..30 },
            details = when {
                connectedDevices.isEmpty() -> "No devices to check"
                connectedDevices.any { it.batteryLevel == null } -> "Some devices not reporting battery"
                connectedDevices.any { it.batteryLevel!! <= 20 } -> {
                    val lowDevices = connectedDevices.filter { it.batteryLevel!! <= 20 }
                    "${lowDevices.size} device(s) below 20%"
                }
                connectedDevices.any { it.batteryLevel!! in 21..30 } -> {
                    val warningDevices = connectedDevices.filter { it.batteryLevel!! in 21..30 }
                    "${warningDevices.size} device(s) at ${warningDevices.minOfOrNull { it.batteryLevel!! }}%"
                }
                else -> "All devices above 30%"
            }
        ),
        ChecklistItem(
            label = "Training Zones",
            isPassed = state.zones.isNotEmpty(),
            details = if (state.zones.isEmpty()) "No zones configured" else "${state.zones.size} zone(s) ready"
        )
    )

    val allPassed = checklistItems.all { it.isPassed }
    val passedCount = checklistItems.count { it.isPassed }
    val progress by animateFloatAsState(
        targetValue = passedCount.toFloat() / checklistItems.size.toFloat(),
        label = "checklist_progress"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPassed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pre-Training Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "$passedCount / ${checklistItems.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (allPassed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (allPassed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            // Checklist items
            checklistItems.forEach { item ->
                ChecklistItemRow(item = item)
            }

            // Status message
            AnimatedVisibility(
                visible = !allPassed,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Complete all checks before starting training",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            AnimatedVisibility(
                visible = allPassed,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Ready",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "All checks passed! Ready to start training",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(item: ChecklistItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Status icon
        val (icon, iconColor, backgroundColor) = when {
            item.isPassed && item.isWarning -> Triple(
                Icons.Rounded.Warning,
                MaterialTheme.colorScheme.tertiary,
                MaterialTheme.colorScheme.tertiaryContainer
            )
            item.isPassed -> Triple(
                Icons.Rounded.Check,
                Color.White,
                MaterialTheme.colorScheme.primary
            )
            else -> Triple(
                Icons.Rounded.Close,
                Color.White,
                MaterialTheme.colorScheme.error
            )
        }

        Icon(
            imageVector = icon,
            contentDescription = if (item.isPassed) "Passed" else "Failed",
            tint = iconColor,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .padding(4.dp)
        )

        // Label and details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            item.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
