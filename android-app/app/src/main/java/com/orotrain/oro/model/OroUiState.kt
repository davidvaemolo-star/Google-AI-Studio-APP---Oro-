package com.orotrain.oro.model

data class OroUiState(
    val destination: AppDestination = AppDestination.Connection,
    val devices: List<HapticDevice> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val isScanning: Boolean = false,
    val isSeatOrderLocked: Boolean = true,
    val trainingSession: TrainingSessionState = TrainingSessionState()
) {
    val connectedDevicesCount: Int = devices.count { it.status == DeviceStatus.Connected }

    val currentZone: Zone?
        get() = zones.getOrNull(trainingSession.currentZoneIndex)

    val canStartTraining: Boolean
        get() = connectedDevicesCount > 0 &&
                zones.isNotEmpty() &&
                !trainingSession.isActive &&
                devices.filter { it.status == DeviceStatus.Connected }
                    .all { it.batteryLevel != null && it.batteryLevel > 20 }
}
