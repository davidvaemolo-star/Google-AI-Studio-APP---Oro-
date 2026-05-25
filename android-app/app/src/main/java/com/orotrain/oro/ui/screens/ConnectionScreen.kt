package com.orotrain.oro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.ui.components.CalibrationDialog
import com.orotrain.oro.ui.components.DeviceCard
import com.orotrain.oro.ui.theme.AccentCyan

@Composable
fun ConnectionScreen(
    state: OroUiState,
    onScan: () -> Unit,
    onToggleDevice: (String) -> Unit,
    onSeatChange: (String, Int) -> Unit,
    onConnectAll: () -> Unit = {},
    modifier: Modifier = Modifier,
    onStartCalibration: ((String) -> Unit)? = null,
    onStopCalibration: ((String) -> Unit)? = null,
    onSetThreshold: ((String, Float) -> Unit)? = null
) {
    val connectedDevices = remember(state.devices) {
        state.devices
            .filter { it.status == DeviceStatus.Connected }
            .sortedBy { it.seat ?: Int.MAX_VALUE }
    }
    val otherDevices = remember(state.devices) {
        state.devices.filter { it.status != DeviceStatus.Connected }
    }

    val scrollState = rememberScrollState()

    var calibratingDeviceId by remember { mutableStateOf<String?>(null) }
    val calibratingDevice = calibratingDeviceId?.let { id ->
        state.devices.find { it.id == id }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connect Oro Devices",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = AccentCyan
        )

        Button(
            onClick = onScan,
            enabled = !state.isScanning,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            if (state.isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
                )
                Text("Scanning...")
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text("Scan")
            }
        }

        val disconnectedCount = otherDevices.count { it.status == DeviceStatus.Disconnected }
        if (disconnectedCount > 0) {
            OutlinedButton(
                onClick = onConnectAll,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Connect All ($disconnectedCount)")
            }
        }

        if (state.devices.isEmpty()) {
            Text(
                text = "Tap Scan to discover haptic devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (connectedDevices.isNotEmpty()) {
                    Text(
                        text = "Assign Seats",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentCyan
                    )
                    Text(
                        text = "Tap a seat number to reassign. Seat 1 is always the Pacer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        connectedDevices.forEach { device ->
                            val seatRole = when (device.seat) {
                                1 -> "Pacer"
                                connectedDevices.lastOrNull()?.seat -> if (connectedDevices.size > 1) "Steerer" else null
                                else -> null
                            }
                            DeviceCard(
                                device = device,
                                onToggle = onToggleDevice,
                                seatNumber = device.seat,
                                seatRole = seatRole,
                                seatCount = connectedDevices.size,
                                onSeatChange = { newSeat -> onSeatChange(device.id, newSeat) },
                                onStartCalibration = { deviceId ->
                                    calibratingDeviceId = deviceId
                                    onStartCalibration?.invoke(deviceId)
                                },
                                onStopCalibration = onStopCalibration,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                if (otherDevices.isNotEmpty()) {
                    Text(
                        text = "Other Devices",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        otherDevices.forEach { device ->
                            DeviceCard(
                                device = device,
                                onToggle = onToggleDevice,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (calibratingDevice != null) {
        CalibrationDialog(
            device = calibratingDevice,
            onDismiss = { calibratingDeviceId = null },
            onStartCalibration = { onStartCalibration?.invoke(calibratingDevice.id) },
            onStopCalibration = {
                onStopCalibration?.invoke(calibratingDevice.id)
                calibratingDeviceId = null
            },
            onSetThreshold = { threshold ->
                onSetThreshold?.invoke(calibratingDevice.id, threshold)
            },
            previewMode = false
        )
    }
}
