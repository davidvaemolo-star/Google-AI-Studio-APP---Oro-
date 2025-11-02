package com.orotrain.oro.model

data class OroUiState(
    val destination: AppDestination = AppDestination.Connection,
    val devices: List<HapticDevice> = emptyList(),
    val zones: List<Zone> = emptyList(),
    val isScanning: Boolean = false
) {
    val connectedDevicesCount: Int = devices.count { it.status == DeviceStatus.Connected }
}

