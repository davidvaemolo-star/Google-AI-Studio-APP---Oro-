package com.orotrain.oro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orotrain.oro.model.CalibrationState
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.ui.theme.AccentCyan
import com.orotrain.oro.ui.theme.Charcoal
import com.orotrain.oro.ui.theme.SurfaceOverlay

@Composable
fun CalibrationDialog(
    device: HapticDevice,
    onDismiss: () -> Unit,
    onStartCalibration: () -> Unit,
    onStopCalibration: () -> Unit,
    onSetThreshold: (Float) -> Unit,
    previewMode: Boolean = false  // Add preview mode for testing
) {
    var manualThreshold by remember { mutableStateOf(device.strokeThreshold ?: 1.0f) }
    var previewState by remember { mutableStateOf(0) } // 0=IDLE, 1=ACTIVE, 2=COMPLETE

    // Create demo device with test data for preview
    val displayDevice = if (previewMode) {
        when (previewState) {
            0 -> device.copy(calibrationState = CalibrationState.NotStarted, strokeThreshold = 1.2f)
            1 -> device.copy(
                calibrationState = CalibrationState.InProgress,
                calibrationProgress = 32,
                calibrationMaxAccel = 1.85f,
                calibrationMinAccel = -2.3f,
                strokeThreshold = 1.02f
            )
            2 -> device.copy(
                calibrationState = CalibrationState.Complete,
                calibrationProgress = 50,
                calibrationMaxAccel = 1.92f,
                calibrationMinAccel = -2.45f,
                strokeThreshold = 1.06f
            )
            else -> device
        }
    } else {
        device
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(Charcoal)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calibrate ${displayDevice.name}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentCyan
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                // Preview mode controls
                if (previewMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { previewState = 0 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("IDLE", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { previewState = 1 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ACTIVE", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { previewState = 2 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("COMPLETE", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Calibration state
                when (displayDevice.calibrationState) {
                    CalibrationState.NotStarted -> {
                        IdleCalibrationView(
                            currentThreshold = displayDevice.strokeThreshold,
                            onStartCalibration = onStartCalibration
                        )
                    }
                    CalibrationState.InProgress -> {
                        when {
                            displayDevice.baselineRejected -> {
                                BaselineRejectedView(
                                    onRetryCalibration = onStartCalibration,
                                    onStopCalibration = onStopCalibration
                                )
                            }
                            displayDevice.isCapturingBaseline -> {
                                CapturingBaselineView(
                                    onStopCalibration = onStopCalibration
                                )
                            }
                            else -> {
                                ActiveCalibrationView(
                                    device = displayDevice,
                                    onStopCalibration = onStopCalibration
                                )
                            }
                        }
                    }
                    CalibrationState.Complete -> {
                        CompleteCalibrationView(
                            device = displayDevice,
                            manualThreshold = manualThreshold,
                            onManualThresholdChanged = { manualThreshold = it },
                            onApplyThreshold = {
                                onSetThreshold(manualThreshold)
                                onDismiss()
                            },
                            onRetryCalibration = onStartCalibration
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleCalibrationView(
    currentThreshold: Float?,
    onStartCalibration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Stroke Detection Calibration",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )

        Text(
            text = "Calibration helps the device detect your paddle strokes accurately by measuring your stroke characteristics.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(SurfaceOverlay)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Instructions:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentCyan
                )
                Text(
                    text = "1. Perform 50 strokes with varying intensities",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "2. Include both gentle and powerful strokes",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "3. Maintain your normal paddling form",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        if (currentThreshold != null) {
            Text(
                text = "Current threshold: ${String.format("%.2f", currentThreshold)}g",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onStartCalibration,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan
            )
        ) {
            Text(
                text = "Start Calibration",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun CapturingBaselineView(
    onStopCalibration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = AccentCyan)

        Text(
            text = "Hold the paddle still",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = AccentCyan,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Keep the paddle resting in place for a moment while the device finds its zero point. Stroke counting starts on its own.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        OutlinedButton(
            onClick = onStopCalibration,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Cancel",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BaselineRejectedView(
    onRetryCalibration: () -> Unit,
    onStopCalibration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color(0xFFFFC24B),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Couldn't get a steady reading",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFFFC24B)
            )
        }
        Text(
            text = "The paddle was moving while the device tried to find its zero point. Rest it still, then tap Retry.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onStopCalibration,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onRetryCalibration,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun ActiveCalibrationView(
    device: HapticDevice,
    onStopCalibration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Calibrating...",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = AccentCyan,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        // Progress bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = device.calibrationProgress / 50f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = AccentCyan,
                trackColor = SurfaceOverlay
            )
            Text(
                text = "${device.calibrationProgress} / 50 strokes",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Real-time measurements
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(SurfaceOverlay)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MeasurementRow(
                    label = "Max Acceleration",
                    value = "${String.format("%.2f", device.calibrationMaxAccel)}g",
                    color = Color(0xFF3ADE8A)
                )
                MeasurementRow(
                    label = "Min Acceleration",
                    value = "${String.format("%.2f", device.calibrationMinAccel)}g",
                    color = Color(0xFFFF6E6E)
                )
                if (device.strokeThreshold != null) {
                    MeasurementRow(
                        label = "Suggested Threshold",
                        value = "${String.format("%.2f", device.strokeThreshold)}g",
                        color = AccentCyan
                    )
                }
            }
        }

        Text(
            text = "Perform varied-intensity strokes to calibrate accurately",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        OutlinedButton(
            onClick = onStopCalibration,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Stop Calibration",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CompleteCalibrationView(
    device: HapticDevice,
    manualThreshold: Float,
    onManualThresholdChanged: (Float) -> Unit,
    onApplyThreshold: () -> Unit,
    onRetryCalibration: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Success header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF3ADE8A),
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Calibration Complete!",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF3ADE8A)
            )
        }

        // Results
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(SurfaceOverlay)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Calibration Results:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentCyan
                )
                MeasurementRow(
                    label = "Strokes Analyzed",
                    value = "${device.calibrationProgress}",
                    color = Color.White
                )
                MeasurementRow(
                    label = "Max Acceleration",
                    value = "${String.format("%.2f", device.calibrationMaxAccel)}g",
                    color = Color(0xFF3ADE8A)
                )
                MeasurementRow(
                    label = "Min Acceleration",
                    value = "${String.format("%.2f", device.calibrationMinAccel)}g",
                    color = Color(0xFFFF6E6E)
                )
                MeasurementRow(
                    label = "Suggested Threshold",
                    value = "${String.format("%.2f", device.strokeThreshold ?: 0f)}g",
                    color = AccentCyan
                )
            }
        }

        // Manual threshold adjustment
        Text(
            text = "Adjust Threshold (Optional)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = manualThreshold,
                onValueChange = onManualThresholdChanged,
                valueRange = 0.5f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${String.format("%.2f", manualThreshold)}g",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetryCalibration,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
            Button(
                onClick = onApplyThreshold,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan
                )
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun MeasurementRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
