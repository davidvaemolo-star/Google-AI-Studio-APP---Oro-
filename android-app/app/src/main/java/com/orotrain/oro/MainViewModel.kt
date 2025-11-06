package com.orotrain.oro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orotrain.oro.ble.BleManager
import com.orotrain.oro.model.AppDestination
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.model.MAX_SETS
import com.orotrain.oro.model.MAX_SPM
import com.orotrain.oro.model.MAX_STROKES
import com.orotrain.oro.model.MAX_ZONES
import com.orotrain.oro.model.MIN_SPM
import com.orotrain.oro.model.MIN_VALUE
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.model.TrainingSessionState
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val bleManager: BleManager? = null,
    private val audioManager: com.orotrain.oro.audio.AudioManager? = null
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableStateFlow(OroUiState())
    val uiState: StateFlow<OroUiState> = _uiState.asStateFlow()

    init {
        // Observe BLE manager state if available
        bleManager?.let {
            viewModelScope.launch {
                it.discoveredDevices.collect { devices ->
                    _uiState.update { state ->
                        val mergedDevices = devices.map { device ->
                            val existing = state.devices.find { it.id == device.id }
                            device.copy(
                                seat = existing?.seat,
                                batteryLevel = device.batteryLevel ?: existing?.batteryLevel,
                                isCalibrating = existing?.isCalibrating ?: device.isCalibrating,
                                strokeThreshold = existing?.strokeThreshold ?: device.strokeThreshold,
                                strokeCount = existing?.strokeCount ?: device.strokeCount,
                                lastStrokePhase = existing?.lastStrokePhase ?: device.lastStrokePhase
                            )
                        }
                        state.copy(devices = renumberSeats(mergedDevices))
                    }
                }
            }
            viewModelScope.launch {
                it.isScanning.collect { isScanning ->
                    _uiState.update { state ->
                        state.copy(isScanning = isScanning)
                    }
                }
            }
            viewModelScope.launch {
                it.strokeEvents.collect { strokeEvent ->
                    strokeEvent?.let { event ->
                        handleStrokeEvent(event)
                    }
                }
            }
        }
    }

    private fun handleStrokeEvent(event: BleManager.StrokeEvent) {
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == event.deviceId) {
                    // Update stroke count on FINISH phase
                    val newCount = if (event.phase == BleManager.STROKE_PHASE_FINISH) {
                        device.strokeCount + 1
                    } else {
                        device.strokeCount
                    }
                    device.copy(
                        strokeCount = newCount,
                        lastStrokePhase = event.phase
                    )
                } else {
                    device
                }
            }

            // Update training session progress if active and event is from pacer
            val pacer = state.devices.find { it.seat == 1 }
            val updatedSession = if (state.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active &&
                event.deviceId == pacer?.id &&
                event.phase == BleManager.STROKE_PHASE_FINISH) {

                val currentZone = state.currentZone
                if (currentZone != null) {
                    processStrokeForTraining(state.trainingSession, currentZone, event.timestamp)
                } else {
                    state.trainingSession
                }
            } else {
                state.trainingSession
            }

            state.copy(
                devices = updatedDevices,
                trainingSession = updatedSession
            )
        }

        // Trigger follower haptics if pacer completed a stroke during active training
        val state = _uiState.value
        val pacer = state.devices.find { it.seat == 1 }

        // Debug logging for stroke detection
        val device = state.devices.find { it.id == event.deviceId }
        Log.d(TAG, "Stroke event received - Device: ${device?.name ?: event.deviceId}, " +
                "Seat: ${device?.seat}, Phase: ${event.phase}, " +
                "IsPacer: ${event.deviceId == pacer?.id}, " +
                "TrainingActive: ${state.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active}")

        if (state.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active &&
            event.deviceId == pacer?.id &&
            event.phase == BleManager.STROKE_PHASE_FINISH) {
            Log.d(TAG, "Triggering haptics for all devices (including pacer)")
            triggerFollowerHaptics(state.currentZone)
        }
    }

    private fun processStrokeForTraining(
        session: TrainingSessionState,
        currentZone: Zone,
        strokeTimestamp: Long
    ): TrainingSessionState {
        val newStroke = session.currentStroke + 1

        // Update recent stroke timestamps for SPM calculation (keep last 10)
        val updatedTimestamps = (session.recentStrokeTimestamps + strokeTimestamp).takeLast(10)
        val calculatedSpm = calculateSpm(updatedTimestamps)

        // Audio: Halfway announcement
        if (newStroke == currentZone.strokes / 2) {
            audioManager?.announceHalfway(newStroke, currentZone.strokes)
            broadcastAudioPrompt(BleManager.PATTERN_SOFT_CLICK, 80)
        }

        // Audio: Warning before zone transition (5 strokes remaining)
        if (newStroke == currentZone.strokes && session.currentSet == currentZone.sets) {
            val state = _uiState.value
            val nextZoneIndex = session.currentZoneIndex + 1
            if (nextZoneIndex < state.zones.size) {
                val nextZone = state.zones[nextZoneIndex]
                audioManager?.announceZoneTransition(nextZone, nextZoneIndex + 1, state.zones.size, 5)
                broadcastAudioPrompt(BleManager.PATTERN_TRANSITION, 90)
            }
        }

        // Check if set is complete
        return if (newStroke >= currentZone.strokes) {
            val newSet = session.currentSet + 1

            // Check if all sets in zone are complete
            if (newSet > currentZone.sets) {
                advanceToNextZone(session, updatedTimestamps, calculatedSpm)
            } else {
                // Move to next set
                // Audio: Set complete announcement
                audioManager?.announceSetComplete(session.currentSet, currentZone.sets)
                audioManager?.playSetCompleteSound()
                broadcastAudioPrompt(BleManager.PATTERN_DOUBLE_CLICK, 100)

                // Audio: Last set announcement
                if (newSet == currentZone.sets) {
                    audioManager?.announceLastSet()
                    broadcastAudioPrompt(BleManager.PATTERN_TRIPLE_CLICK, 100)
                }

                session.copy(
                    currentStroke = 0,
                    currentSet = newSet,
                    recentStrokeTimestamps = updatedTimestamps,
                    currentSpm = calculatedSpm
                )
            }
        } else {
            // Continue current set
            session.copy(
                currentStroke = newStroke,
                recentStrokeTimestamps = updatedTimestamps,
                currentSpm = calculatedSpm
            )
        }
    }

    private fun advanceToNextZone(
        session: TrainingSessionState,
        timestamps: List<Long>,
        spm: Int
    ): TrainingSessionState {
        val state = _uiState.value
        val nextZoneIndex = session.currentZoneIndex + 1

        return if (nextZoneIndex < state.zones.size) {
            // Move to next zone
            val nextZone = state.zones[nextZoneIndex]

            // Audio: Zone transition announcement
            audioManager?.playZoneTransitionSound()
            audioManager?.announceZoneTransition(nextZone, nextZoneIndex + 1, state.zones.size, 0)
            broadcastAudioPrompt(BleManager.PATTERN_ALERT_750MS, 100)

            viewModelScope.launch {
                configureCurrentZone()
            }

            session.copy(
                currentZoneIndex = nextZoneIndex,
                currentStroke = 0,
                currentSet = 1,
                recentStrokeTimestamps = timestamps,
                currentSpm = spm
            )
        } else {
            // All zones complete - finish training
            // Audio: Session complete announcement
            val totalStrokes = state.zones.sumOf { it.strokes * it.sets }
            audioManager?.playSessionCompleteSound()
            audioManager?.announceSessionComplete(totalStrokes, spm)
            broadcastAudioPrompt(BleManager.PATTERN_ALERT_750MS, 100)

            stopTrainingSession()
            session.copy(
                status = com.orotrain.oro.model.TrainingStatus.Completed
            )
        }
    }

    private fun calculateSpm(timestamps: List<Long>): Int {
        if (timestamps.size < 2) return 0

        // Calculate average time between strokes in milliseconds
        val intervals = timestamps.zipWithNext { a, b -> b - a }
        val avgInterval = intervals.average()

        // Convert to strokes per minute
        return if (avgInterval > 0) {
            (60000 / avgInterval).toInt()
        } else {
            0
        }
    }

    private fun triggerFollowerHaptics(currentZone: Zone?) {
        val pattern = when (currentZone?.level) {
            com.orotrain.oro.model.ZoneLevel.Low -> BleManager.PATTERN_SOFT_CLICK
            com.orotrain.oro.model.ZoneLevel.Medium -> BleManager.PATTERN_STRONG_CLICK
            com.orotrain.oro.model.ZoneLevel.High -> BleManager.PATTERN_DOUBLE_CLICK
            null -> BleManager.PATTERN_STRONG_CLICK
        }

        bleManager?.broadcastHaptic(
            command = BleManager.CMD_SINGLE_PULSE,
            pattern = pattern,
            intensity = 100,
            includePacer = true  // Include pacer so all devices pulse together
        )
    }

    private fun broadcastAudioPrompt(pattern: Byte, intensity: Int = 90) {
        bleManager?.broadcastHaptic(
            command = BleManager.CMD_TEST_PATTERN,
            pattern = pattern,
            intensity = intensity,
            includePacer = true
        )
    }

    fun setDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    fun addZone() {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) state
            else state.copy(zones = state.zones + Zone())
        }
    }

    fun addZoneAfter(zoneId: String) {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) return@update state
            val index = state.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) state
            else {
                val zones = state.zones.toMutableList()
                zones.add(index + 1, Zone())
                state.copy(zones = zones)
            }
        }
    }

    fun duplicateZone(zoneId: String) {
        _uiState.update { state ->
            if (state.zones.size >= MAX_ZONES) return@update state
            val index = state.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) state
            else {
                val copy = state.zones[index].copy(id = Zone().id)
                val zones = state.zones.toMutableList()
                zones.add(index + 1, copy)
                state.copy(zones = zones)
            }
        }
    }

    fun removeZone(zoneId: String) {
        _uiState.update { state ->
            state.copy(zones = state.zones.filterNot { it.id == zoneId })
        }
    }

    fun adjustZone(zoneId: String, field: ZoneField, delta: Int) {
        _uiState.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id != zoneId) return@map zone

                when (field) {
                    ZoneField.Strokes -> zone.copy(
                        strokes = (zone.strokes + delta).coerceIn(MIN_VALUE, MAX_STROKES)
                    )

                    ZoneField.Sets -> zone.copy(
                        sets = (zone.sets + delta).coerceIn(MIN_VALUE, MAX_SETS)
                    )

                    ZoneField.Level -> {
                        val levels = com.orotrain.oro.model.ZoneLevel.values()
                        val currentIndex = levels.indexOf(zone.level)
                        val newIndex = (currentIndex + delta).coerceIn(0, levels.size - 1)
                        zone.copy(level = levels[newIndex])
                    }
                }
            }
            state.copy(zones = zones)
        }
    }

    fun setZoneLevel(zoneId: String, level: com.orotrain.oro.model.ZoneLevel) {
        _uiState.update { state ->
            val zones = state.zones.map { zone ->
                if (zone.id == zoneId) zone.copy(level = level) else zone
            }
            state.copy(zones = zones)
        }
    }

    fun reorderZones(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            if (fromIndex !in state.zones.indices || toIndex !in state.zones.indices) return@update state
            val zones = state.zones.toMutableList()
            val zone = zones.removeAt(fromIndex)
            zones.add(toIndex, zone)
            state.copy(zones = zones)
        }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        if (bleManager != null) {
            // Use real BLE scanning
            bleManager.startScan()
        } else {
            // Fallback to simulated scan for preview/testing
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = true, devices = emptyList()) }
                kotlinx.coroutines.delay(2000)
                val mockDevices = listOf(
                    HapticDevice(name = "Oro Device 1"),
                    HapticDevice(name = "Oro Device 2")
                )
                _uiState.update { it.copy(isScanning = false, devices = mockDevices) }
            }
        }
    }

    fun toggleDeviceConnection(deviceId: String) {
        val device = _uiState.value.devices.find { it.id == deviceId } ?: return
        when (device.status) {
            DeviceStatus.Connected -> disconnect(deviceId)
            DeviceStatus.Disconnected -> connect(deviceId)
            DeviceStatus.Connecting -> {
                // Ignore taps while connecting
            }
        }
    }

    fun connectAllDevices() {
        val disconnectedDevices = _uiState.value.devices.filter { it.status == DeviceStatus.Disconnected }
        disconnectedDevices.forEach { device ->
            connect(device.id)
        }
    }

    private fun connect(deviceId: String) {
        if (bleManager != null) {
            // Use real BLE connection
            bleManager.connectDevice(deviceId)
        } else {
            // Fallback to simulated connection for preview/testing
            _uiState.update { state ->
                val updated = state.devices.map { device ->
                    if (device.id == deviceId) device.copy(status = DeviceStatus.Connecting)
                    else device
                }
                state.copy(devices = updated)
            }

            viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                val batteryLevel = kotlin.random.Random.nextInt(from = 20, until = 101)
                _uiState.update { state ->
                    val updated = state.devices.map { device ->
                        if (device.id == deviceId) {
                            device.copy(
                                status = DeviceStatus.Connected,
                                batteryLevel = batteryLevel
                            )
                        } else device
                    }
                    state.copy(devices = renumberSeats(updated))
                }
            }
        }
    }

    private fun disconnect(deviceId: String) {
        if (bleManager != null) {
            // Use real BLE disconnection
            bleManager.disconnectDevice(deviceId)
            // Update seat assignment after disconnect
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(devices = renumberSeats(state.devices))
                }
            }
        } else {
            // Fallback to simulated disconnection for preview/testing
            _uiState.update { state ->
                val updated = state.devices.map { device ->
                    if (device.id == deviceId) {
                        device.copy(
                            status = DeviceStatus.Disconnected,
                            batteryLevel = null,
                            seat = null
                        )
                    } else device
                }
                state.copy(devices = renumberSeats(updated))
            }
        }
    }

    fun reorderConnectedDevices(fromIndex: Int, toIndex: Int) {
        if (_uiState.value.isSeatOrderLocked) return
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            val connected = state.devices.filter { it.status == DeviceStatus.Connected }
            if (fromIndex !in connected.indices || toIndex !in connected.indices) return@update state
            val reordered = connected.toMutableList().apply {
                val moved = removeAt(fromIndex)
                add(toIndex, moved)
            }
            val reassigned = reordered.mapIndexed { index, device ->
                device.copy(seat = index + 1)
            }

            reassigned.firstOrNull()?.let { newPacer ->
                setPacerDevice(newPacer.id)
            }

            val connectedIds = reassigned.map { it.id }.toSet()
            val others = state.devices.filter { it.id !in connectedIds }.map {
                if (it.status == DeviceStatus.Connected) it.copy(seat = null) else it
            }
            state.copy(devices = reassigned + others)
        }
    }

    private fun renumberSeats(devices: List<HapticDevice>): List<HapticDevice> {
        val connected = devices
            .filter { it.status == DeviceStatus.Connected }
            .sortedBy { it.seat ?: Int.MAX_VALUE }

        val reassigned = connected.mapIndexed { index, device ->
            device.copy(seat = index + 1)
        }

        // Set first connected device (Seat 1) as pacer
        if (reassigned.isNotEmpty()) {
            setPacerDevice(reassigned[0].id)
        }

        val connectedIds = reassigned.map { it.id }.toSet()
        val others = devices.filter { it.id !in connectedIds }.map {
            if (it.status == DeviceStatus.Connected) it.copy(seat = null) else it
        }
        return reassigned + others
    }

    // Haptic training functions

    fun configureZone(deviceId: String, zone: Zone) {
        bleManager?.configureTrainingZone(
            deviceId = deviceId,
            strokes = zone.strokes,
            sets = zone.sets,
            spm = zone.spm,
            zoneColor = BleManager.ZONE_ENDURANCE
        )
    }

    fun startDeviceTraining(deviceId: String) {
        bleManager?.startTraining(deviceId)
    }

    fun pauseDeviceTraining(deviceId: String) {
        bleManager?.pauseTraining(deviceId)
    }

    fun resumeDeviceTraining(deviceId: String) {
        bleManager?.resumeTraining(deviceId)
    }

    fun stopDeviceTraining(deviceId: String) {
        bleManager?.stopTraining(deviceId)
    }

    fun testHaptic(deviceId: String, pattern: Byte = BleManager.PATTERN_STRONG_CLICK) {
        bleManager?.testHapticPattern(deviceId, pattern)
    }

    // Calibration and stroke detection functions

    fun startCalibration(deviceId: String) {
        bleManager?.startCalibration(deviceId)
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == deviceId) {
                    device.copy(isCalibrating = true, strokeCount = 0)
                } else {
                    device
                }
            }
            state.copy(devices = updatedDevices)
        }
    }

    fun stopCalibration(deviceId: String) {
        bleManager?.stopCalibration(deviceId)
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == deviceId) {
                    device.copy(isCalibrating = false)
                } else {
                    device
                }
            }
            state.copy(devices = updatedDevices)
        }
    }

    fun setStrokeThreshold(deviceId: String, threshold: Float) {
        bleManager?.setStrokeThreshold(deviceId, threshold)
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == deviceId) {
                    device.copy(strokeThreshold = threshold)
                } else {
                    device
                }
            }
            state.copy(devices = updatedDevices)
        }
    }

    fun setPacerDevice(deviceId: String) {
        bleManager?.setPacerDevice(deviceId)
    }

    fun enableStrokeDetection(deviceId: String) {
        bleManager?.enableStrokeDetection(deviceId)
    }

    fun disableStrokeDetection(deviceId: String) {
        bleManager?.disableStrokeDetection(deviceId)
    }

    fun toggleSeatOrderLock() {
        _uiState.update { state ->
            state.copy(isSeatOrderLocked = !state.isSeatOrderLocked)
        }
    }

    // Training Session Controller

    fun startTrainingSession() {
        val state = _uiState.value

        // Validation
        if (!state.canStartTraining) {
            _uiState.update {
                it.copy(
                    trainingSession = it.trainingSession.copy(
                        errorMessage = "Cannot start training: Check device connections and battery levels"
                    )
                )
            }
            return
        }

        if (state.zones.isEmpty()) {
            _uiState.update {
                it.copy(
                    trainingSession = it.trainingSession.copy(
                        errorMessage = "Cannot start training: No zones configured"
                    )
                )
            }
            return
        }

        // Set status to Starting
        _uiState.update {
            it.copy(
                trainingSession = TrainingSessionState(
                    status = com.orotrain.oro.model.TrainingStatus.Starting,
                    currentZoneIndex = 0,
                    currentStroke = 0,
                    currentSet = 1,
                    startTimeMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
            )
        }

        // Configure all devices with first zone
        viewModelScope.launch {
            configureCurrentZone()

            // Start training on all connected devices
            val connectedDevices = state.devices.filter { it.status == DeviceStatus.Connected }
            connectedDevices.forEach { device ->
                startDeviceTraining(device.id)
            }

            // Enable stroke detection on pacer (Seat 1)
            val pacer = connectedDevices.find { it.seat == 1 }
            pacer?.let {
                Log.d(TAG, "Enabling stroke detection on pacer: ${it.name} (Seat ${it.seat})")
                enableStrokeDetection(it.id)
            } ?: Log.w(TAG, "No pacer device found (Seat 1) - stroke detection not enabled!")

            // Set status to Active
            _uiState.update {
                it.copy(
                    trainingSession = it.trainingSession.copy(
                        status = com.orotrain.oro.model.TrainingStatus.Active
                    )
                )
            }

            // Audio: Training start announcement
            audioManager?.announceTrainingStart(state.zones.size)
            broadcastAudioPrompt(BleManager.PATTERN_TRIPLE_CLICK, 100)
        }
    }

    private fun configureCurrentZone() {
        val state = _uiState.value
        val currentZone = state.currentZone ?: return

        val connectedDevices = state.devices.filter { it.status == DeviceStatus.Connected }
        connectedDevices.forEach { device ->
            bleManager?.configureTrainingZone(
                deviceId = device.id,
                strokes = currentZone.strokes,
                sets = currentZone.sets,
                spm = currentZone.spm,
                zoneColor = currentZone.zoneColor
            )
        }
    }

    fun pauseTrainingSession() {
        val state = _uiState.value
        if (state.trainingSession.status != com.orotrain.oro.model.TrainingStatus.Active) return

        _uiState.update {
            it.copy(
                trainingSession = it.trainingSession.copy(
                    status = com.orotrain.oro.model.TrainingStatus.Paused,
                    pausedTimeMillis = System.currentTimeMillis()
                )
            )
        }

        // Pause all devices
        state.devices
            .filter { it.status == DeviceStatus.Connected }
            .forEach { device ->
                pauseDeviceTraining(device.id)
            }

        // Audio: Pause announcement
        audioManager?.announceTrainingPaused()
        broadcastAudioPrompt(BleManager.PATTERN_SOFT_CLICK, 70)
    }

    fun resumeTrainingSession() {
        val state = _uiState.value
        if (state.trainingSession.status != com.orotrain.oro.model.TrainingStatus.Paused) return

        val pausedTime = state.trainingSession.pausedTimeMillis ?: System.currentTimeMillis()
        val pauseDuration = System.currentTimeMillis() - pausedTime

        _uiState.update {
            it.copy(
                trainingSession = it.trainingSession.copy(
                    status = com.orotrain.oro.model.TrainingStatus.Active,
                    totalPausedDuration = it.trainingSession.totalPausedDuration + pauseDuration,
                    pausedTimeMillis = null
                )
            )
        }

        // Resume all devices
        state.devices
            .filter { it.status == DeviceStatus.Connected }
            .forEach { device ->
                resumeDeviceTraining(device.id)
            }

        // Audio: Resume announcement
        audioManager?.announceTrainingResumed()
        broadcastAudioPrompt(BleManager.PATTERN_DOUBLE_CLICK, 90)
    }

    fun stopTrainingSession() {
        val state = _uiState.value
        if (!state.trainingSession.isActive) return

        _uiState.update {
            it.copy(
                trainingSession = it.trainingSession.copy(
                    status = com.orotrain.oro.model.TrainingStatus.Completed
                )
            )
        }

        // Stop all devices
        state.devices
            .filter { it.status == DeviceStatus.Connected }
            .forEach { device ->
                stopDeviceTraining(device.id)
            }

        // Disable stroke detection on pacer
        val pacer = state.devices.find { it.seat == 1 && it.status == DeviceStatus.Connected }
        pacer?.let { disableStrokeDetection(it.id) }

        // Reset to idle after delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update {
                it.copy(trainingSession = TrainingSessionState())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager?.cleanup()
        audioManager?.cleanup()
    }
}

