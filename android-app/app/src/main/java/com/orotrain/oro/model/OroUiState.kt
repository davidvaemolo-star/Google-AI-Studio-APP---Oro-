package com.orotrain.oro.model

/**
 * A single pre-session readiness check.
 *
 * This list is the SINGLE SOURCE OF TRUTH for "can a Session start": both the Start button
 * ([OroUiState.canStartTraining]) and the Pre-Training Checklist UI are derived from the exact
 * same [OroUiState.startChecks] list, so they can never disagree. Previously they were two
 * separate lists, which let the checklist show "Ready" while Start stayed disabled.
 *
 * - [isBlocking] true: failing this check disables Start.
 * - [isBlocking] false: a warning only (e.g. low battery) — shown, but never blocks Start.
 */
data class StartCheck(
    val label: String,
    val isPassed: Boolean,
    val isBlocking: Boolean,
    val isWarning: Boolean = false,
    val details: String? = null
)

data class OroUiState(
    val destination: AppDestination = AppDestination.Programmes,
    val devices: List<HapticDevice> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val activeProgramme: Programme? = null,
    val editingProgrammeId: String? = null,
    val zones: List<Zone> = emptyList(),
    val isScanning: Boolean = false,
    val trainingSession: TrainingSessionState = TrainingSessionState(),
    /**
     * The smoothed Canoe Speed in km/h for the coach-facing indicator (ADR-0018), or null when
     * there is no usable GPS fix. Display only — the spoken call-out is broadcast separately.
     */
    val canoeSpeedKmh: Float? = null
) {
    val connectedDevicesCount: Int = devices.count { it.status == DeviceStatus.Connected }

    val currentZone: Zone?
        get() = zones.getOrNull(trainingSession.currentZoneIndex)

    val editingProgramme: Programme?
        get() = programmes.find { it.id == editingProgrammeId }

    /**
     * Pre-session readiness, shown in the Pre-Training Checklist AND enforced by the Start button.
     *
     * A Session starts from the [zones] currently on the Training screen — a saved Programme does
     * NOT need to be loaded first (ADR-0013). Low battery warns but never blocks.
     */
    val startChecks: List<StartCheck>
        get() {
            val connected = devices.filter { it.status == DeviceStatus.Connected }
            val pacer = connected.find { it.seat == 1 }
            val calibratedCount = connected.count { it.calibrationState == CalibrationState.Complete }
            val lowBattery = connected.filter { (it.batteryLevel ?: Int.MAX_VALUE) <= 20 }
            val batteryReported = connected.all { it.batteryLevel != null }

            return listOf(
                StartCheck(
                    label = "Devices Connected",
                    isPassed = connected.isNotEmpty(),
                    isBlocking = true,
                    details = "${connected.size} of $MAX_DEVICES devices connected"
                ),
                StartCheck(
                    label = "Pacer Assigned",
                    isPassed = pacer != null,
                    isBlocking = true,
                    details = if (pacer != null) "Seat 1: ${pacer.name}" else "No device in Seat 1"
                ),
                StartCheck(
                    label = "Calibration Complete",
                    isPassed = connected.isNotEmpty() && calibratedCount == connected.size,
                    isBlocking = true,
                    details = when {
                        connected.isEmpty() -> "No devices to calibrate"
                        calibratedCount == connected.size -> "All devices calibrated"
                        else -> "$calibratedCount of ${connected.size} devices calibrated"
                    }
                ),
                StartCheck(
                    label = "Training Zones",
                    isPassed = zones.isNotEmpty(),
                    isBlocking = true,
                    details = if (zones.isEmpty()) "No zones added" else "${zones.size} zone(s) ready"
                ),
                StartCheck(
                    label = "Battery Levels",
                    isPassed = lowBattery.isEmpty(),
                    isBlocking = false,
                    isWarning = lowBattery.isNotEmpty(),
                    details = when {
                        connected.isEmpty() -> "No devices to check"
                        !batteryReported -> "Some devices not reporting battery"
                        lowBattery.isNotEmpty() -> "${lowBattery.size} device(s) at or below 20%"
                        else -> "All devices above 20%"
                    }
                )
            )
        }

    val canStartTraining: Boolean
        get() = !trainingSession.isActive &&
                startChecks.filter { it.isBlocking }.all { it.isPassed }
}
