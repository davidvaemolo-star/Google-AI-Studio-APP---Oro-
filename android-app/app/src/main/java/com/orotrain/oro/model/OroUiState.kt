package com.orotrain.oro.model

data class OroUiState(
    val destination: AppDestination = AppDestination.Programmes,
    val devices: List<HapticDevice> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val activeProgramme: Programme? = null,
    val editingProgrammeId: String? = null,
    val zones: List<Zone> = emptyList(),
    val isScanning: Boolean = false,
    val isSeatOrderLocked: Boolean = true,
    val trainingSession: TrainingSessionState = TrainingSessionState()
) {
    val connectedDevicesCount: Int = devices.count { it.status == DeviceStatus.Connected }

    val currentZone: Zone?
        get() = zones.getOrNull(trainingSession.currentZoneIndex)

    val editingProgramme: Programme?
        get() = programmes.find { it.id == editingProgrammeId }

    val canStartTraining: Boolean
        get() = activeProgramme != null &&
                connectedDevicesCount > 0 &&
                zones.isNotEmpty() &&
                !trainingSession.isActive &&
                devices.filter { it.status == DeviceStatus.Connected }
                    .all { it.batteryLevel != null && it.batteryLevel > 20 }
}
