package com.orotrain.oro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
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
import com.orotrain.oro.ui.components.LedControlDialog
import com.orotrain.oro.ui.theme.AccentCyan
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.burnoutcrew.reorderable.detectReorderAfterLongPress

@Composable
fun ConnectionScreen(
    state: OroUiState,
    onScan: () -> Unit,
    onToggleDevice: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    isSeatOrderLocked: Boolean,
    onToggleSeatLock: () -> Unit,
    onConnectAll: () -> Unit = {},
    modifier: Modifier = Modifier,
    onStartCalibration: ((String) -> Unit)? = null,
    onStopCalibration: ((String) -> Unit)? = null,
    onSetThreshold: ((String, Float) -> Unit)? = null,
    onSetLedColor: ((String, Int, Int, Int) -> Unit)? = null,
    onSetLedAutoMode: ((String) -> Unit)? = null
) {
    val connectedDevices = remember(state.devices) {
        state.devices.filter { it.status == DeviceStatus.Connected }
    }
    val otherDevices = remember(state.devices) {
        state.devices.filter { it.status != DeviceStatus.Connected }
    }

    val reorderableState = rememberReorderableLazyListState(onMove = { from, to ->
        if (!isSeatOrderLocked) {
            onReorder(from.index, to.index)
        }
    })

    val scrollState = rememberScrollState()

    // Calibration dialog state
    var calibratingDeviceId by remember { mutableStateOf<String?>(null) }
    val calibratingDevice = calibratingDeviceId?.let { deviceId ->
        state.devices.find { it.id == deviceId }
    }

    // LED control dialog state
    var ledControlDeviceId by remember { mutableStateOf<String?>(null) }
    val ledControlDevice = ledControlDeviceId?.let { deviceId ->
        state.devices.find { it.id == deviceId }
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

        // Connect All button (only show if there are disconnected devices)
        val disconnectedDevicesCount = otherDevices.count { it.status == DeviceStatus.Disconnected }
        if (disconnectedDevicesCount > 0) {
            OutlinedButton(
                onClick = onConnectAll,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Connect All ($disconnectedDevicesCount)")
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
                Text(
                    text = "Assign Seats",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AccentCyan
                )
                if (connectedDevices.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onToggleSeatLock,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = if (isSeatOrderLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(text = if (isSeatOrderLocked) "Unlock Seat Order" else "Lock Seat Order")
                    }
                    if (isSeatOrderLocked) {
                        Text(
                            text = "Seat order locked. Unlock to rearrange device positions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .then(if (isSeatOrderLocked) Modifier else Modifier.reorderable(reorderableState)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = connectedDevices,
                        key = { _, item -> item.id }
                    ) { index, device ->
                        ReorderableItem(reorderableState, key = device.id) { isDragging ->
                            val seatRole = when (index) {
                                0 -> "Pacer"
                                connectedDevices.lastIndex -> if (connectedDevices.size > 1) "Steerer" else null
                                else -> null
                            }
                            val itemModifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .then(
                                    if (isSeatOrderLocked) {
                                        Modifier
                                    } else {
                                        Modifier.detectReorderAfterLongPress(reorderableState)
                                    }
                                )
                            DeviceCard(
                                device = device,
                                onToggle = onToggleDevice,
                                seatNumber = index + 1,
                                seatRole = seatRole,
                                isDragging = if (isSeatOrderLocked) false else isDragging,
                                onStartCalibration = { deviceId ->
                                    calibratingDeviceId = deviceId
                                    onStartCalibration?.invoke(deviceId)
                                },
                                onStopCalibration = onStopCalibration,
                                onShowLedControl = if (onSetLedColor != null) { deviceId ->
                                    ledControlDeviceId = deviceId
                                } else null,
                                modifier = itemModifier
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

    // Show calibration dialog when a device is being calibrated
    if (calibratingDevice != null) {
        CalibrationDialog(
            device = calibratingDevice,
            onDismiss = { calibratingDeviceId = null },
            onStartCalibration = {
                onStartCalibration?.invoke(calibratingDevice.id)
            },
            onStopCalibration = {
                onStopCalibration?.invoke(calibratingDevice.id)
                calibratingDeviceId = null
            },
            onSetThreshold = { threshold ->
                onSetThreshold?.invoke(calibratingDevice.id, threshold)
            },
            previewMode = false  // Enable preview mode to demo all states
        )
    }

    // Show LED control dialog
    if (ledControlDevice != null) {
        LedControlDialog(
            deviceName = ledControlDevice.name,
            onDismiss = { ledControlDeviceId = null },
            onSetColor = { r, g, b ->
                onSetLedColor?.invoke(ledControlDevice.id, r, g, b)
            },
            onSetAutoMode = {
                onSetLedAutoMode?.invoke(ledControlDevice.id)
                ledControlDeviceId = null
            }
        )
    }
}
