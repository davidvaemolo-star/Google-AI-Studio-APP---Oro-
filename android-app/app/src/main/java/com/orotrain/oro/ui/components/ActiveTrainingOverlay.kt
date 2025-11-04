package com.orotrain.oro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.model.TrainingStatus
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneLevel
import com.orotrain.oro.ui.theme.ZoneGreenEnd
import com.orotrain.oro.ui.theme.ZoneGreenStart
import com.orotrain.oro.ui.theme.ZoneRedEnd
import com.orotrain.oro.ui.theme.ZoneRedStart
import com.orotrain.oro.ui.theme.ZoneYellowEnd
import com.orotrain.oro.ui.theme.ZoneYellowStart

@Composable
fun ActiveTrainingOverlay(
    state: OroUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trainingSession = state.trainingSession
    val currentZone = state.currentZone

    AnimatedVisibility(
        visible = trainingSession.isActive,
        enter = fadeIn() + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zone indicator header
                    currentZone?.let { zone ->
                        ZoneLevelIndicator(zone = zone)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Large stroke counter
                    Text(
                        text = "${trainingSession.currentStroke}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentZone?.let { getZoneColor(it) } ?: MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "of ${currentZone?.strokes ?: 0} strokes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar for current set
                    currentZone?.let { zone ->
                        val progress by animateFloatAsState(
                            targetValue = trainingSession.currentStroke.toFloat() / zone.strokes.toFloat(),
                            animationSpec = spring(),
                            label = "stroke_progress"
                        )

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = getZoneColor(zone),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Set and Zone info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InfoCard(
                            label = "Set",
                            value = "${trainingSession.currentSet} / ${currentZone?.sets ?: 0}"
                        )

                        InfoCard(
                            label = "Zone",
                            value = "${trainingSession.currentZoneIndex + 1} / ${state.zones.size}"
                        )

                        InfoCard(
                            label = "SPM",
                            value = "${trainingSession.currentSpm}"
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause/Resume button
                        IconButton(
                            onClick = {
                                if (trainingSession.status == TrainingStatus.Active) {
                                    onPause()
                                } else {
                                    onResume()
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = if (trainingSession.status == TrainingStatus.Active) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = if (trainingSession.status == TrainingStatus.Active) {
                                    "Pause training"
                                } else {
                                    "Resume training"
                                },
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Stop button
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Stop training"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }

                    // Status message (if paused)
                    if (trainingSession.status == TrainingStatus.Paused) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Training Paused",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneLevelIndicator(zone: Zone) {
    val (gradientStart, gradientEnd) = when (zone.level) {
        ZoneLevel.Low -> ZoneGreenStart to ZoneGreenEnd
        ZoneLevel.Medium -> ZoneYellowStart to ZoneYellowEnd
        ZoneLevel.High -> ZoneRedStart to ZoneRedEnd
    }

    val levelText = when (zone.level) {
        ZoneLevel.Low -> "Low Intensity"
        ZoneLevel.Medium -> "Medium Intensity"
        ZoneLevel.High -> "High Intensity"
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(gradientStart, gradientEnd)
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = levelText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun getZoneColor(zone: Zone): Color {
    return when (zone.level) {
        ZoneLevel.Low -> ZoneGreenStart
        ZoneLevel.Medium -> ZoneYellowStart
        ZoneLevel.High -> ZoneRedStart
    }
}
