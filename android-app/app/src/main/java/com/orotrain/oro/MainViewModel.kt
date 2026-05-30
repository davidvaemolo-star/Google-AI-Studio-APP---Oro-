package com.orotrain.oro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orotrain.oro.analysis.StrokeAnalyzer
import com.orotrain.oro.audio.sessionSummaryAudioPromptFor
import com.orotrain.oro.coaching.CoachingEngine
import com.orotrain.oro.ble.BleManager
import com.orotrain.oro.data.SessionRepository
import com.orotrain.oro.model.AppDestination
import com.orotrain.oro.model.Programme
import com.orotrain.oro.model.CalibrationState
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.model.autoAssignSeats
import com.orotrain.oro.model.swapSeats
import com.orotrain.oro.model.OroUiState
import com.orotrain.oro.model.SessionAudioCue
import com.orotrain.oro.model.SessionOutcome
import com.orotrain.oro.model.StrokeProgression
import com.orotrain.oro.model.TrainingSessionState
import com.orotrain.oro.model.computeStrokeProgression
import com.orotrain.oro.model.Zone
import com.orotrain.oro.model.ZoneField
import com.orotrain.oro.model.ZoneLevel
import com.orotrain.oro.model.withAddedZone
import com.orotrain.oro.model.withZoneAddedAfter
import com.orotrain.oro.model.withZoneAdjusted
import com.orotrain.oro.model.withZoneDuplicated
import com.orotrain.oro.model.withZoneLevel
import com.orotrain.oro.model.withZoneMoved
import com.orotrain.oro.model.withZoneRemoved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val bleManager: BleManager? = null,
    private val audioManager: com.orotrain.oro.audio.AudioManager? = null,
    private val sessionRepository: SessionRepository? = null,
    private val programmeRepository: com.orotrain.oro.data.ProgrammeRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableStateFlow(OroUiState())
    val uiState: StateFlow<OroUiState> = _uiState.asStateFlow()

    // Stroke analytics engine
    val strokeAnalyzer = StrokeAnalyzer()

    // Real-time coaching feedback engine
    private val coachingEngine = CoachingEngine(bleManager, audioManager)

    // Tracks Android-side receive time of pacer's last CATCH event for sync latency calculation
    @Volatile private var pacerLastCatchReceivedMs: Long? = null
    // Running (sum, count) per follower device for session-average sync latency
    private val syncLatencyAccumulator = mutableMapOf<String, Pair<Long, Int>>()

    init {
        // Observe BLE manager state if available
        bleManager?.let {
            viewModelScope.launch {
                it.discoveredDevices.collect { devices ->
                    _uiState.update { state ->
                        val mergedDevices = devices.map { device ->
                            val existing = state.devices.find { it.id == device.id }
                            // Note: calibrationState is intentionally NOT preserved — on every
                            // reconnect we trust the BLE-layer default (NotStarted). The firmware-side
                            // calibration state is reset on every cold boot anyway (RAM clears on
                            // System OFF), and preserving a stale InProgress/Complete across reconnects
                            // strands the dialog in the wrong view.
                            device.copy(
                                seat = existing?.seat,
                                batteryLevel = device.batteryLevel ?: existing?.batteryLevel,
                                strokeThreshold = existing?.strokeThreshold ?: device.strokeThreshold,
                                strokeCount = existing?.strokeCount ?: device.strokeCount,
                                lastStrokePhase = existing?.lastStrokePhase ?: device.lastStrokePhase,
                                topHandPressurePercent = existing?.topHandPressurePercent ?: device.topHandPressurePercent,
                                topHandPressureThresholdTriggered = existing?.topHandPressureThresholdTriggered ?: device.topHandPressureThresholdTriggered
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
            viewModelScope.launch {
                it.calibrationUpdates.collect { calibrationUpdate ->
                    calibrationUpdate?.let { update ->
                        handleCalibrationUpdate(update)
                    }
                }
            }
            viewModelScope.launch {
                it.fsrUpdates.collect { fsrUpdate ->
                    fsrUpdate?.let { update ->
                        handleFsrUpdate(update)
                    }
                }
            }
        }

        // Observe coaching events from StrokeAnalyzer and forward to CoachingEngine
        viewModelScope.launch {
            strokeAnalyzer.coachingEvents.collect { event ->
                event?.let {
                    val deviceIds = _uiState.value.devices
                        .filter { d -> d.status == DeviceStatus.Connected }
                        .map { d -> d.id }
                    coachingEngine.onCoachingEvent(it, deviceIds)
                }
            }
        }

        // Load saved programmes on startup
        programmeRepository?.let { repo ->
            _uiState.update { it.copy(programmes = repo.loadAll()) }
        }
    }

    private fun handleStrokeEvent(event: BleManager.StrokeEvent) {
        // Forward to analytics engine only during active training
        if (_uiState.value.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active) {
            strokeAnalyzer.onStrokeEvent(event)
        }

        // Record pacer CATCH receive time and compute follower sync latency before state update.
        // Both mutations (pacerLastCatchReceivedMs, syncLatencyAccumulator) must live outside
        // _uiState.update because StateFlow.update retries its lambda under CAS contention.
        val nowMs = System.currentTimeMillis()
        val syncQualityUpdate: Pair<String, Int>? = run {
            if (_uiState.value.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active &&
                event.phase == BleManager.STROKE_PHASE_CATCH) {
                val pacer = _uiState.value.devices.find { it.seat == 1 }
                if (event.deviceId == pacer?.id) {
                    pacerLastCatchReceivedMs = nowMs
                    null
                } else {
                    val pacerTs = pacerLastCatchReceivedMs
                    if (pacerTs != null) {
                        val latencyMs = (nowMs - pacerTs).toInt().coerceIn(0, 2000)
                        val (prevSum, prevCount) = syncLatencyAccumulator.getOrDefault(event.deviceId, Pair(0L, 0))
                        val newSum = prevSum + latencyMs
                        val newCount = prevCount + 1
                        syncLatencyAccumulator[event.deviceId] = Pair(newSum, newCount)
                        event.deviceId to (newSum / newCount).toInt()
                    } else null
                }
            } else null
        }

        // Compute training progression as a pure value BEFORE the state update, mirroring the
        // syncQualityUpdate pattern above. Its side effects (audio, zone reconfigure, session
        // stop) are executed exactly once after the update commits — never inside the update
        // lambda, which StateFlow.update may re-run under CAS contention.
        val pacerId = _uiState.value.devices.find { it.seat == 1 }?.id
        val isPacerFinish = _uiState.value.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active &&
            event.deviceId == pacerId &&
            event.phase == BleManager.STROKE_PHASE_FINISH
        val progression: StrokeProgression? = if (isPacerFinish) {
            _uiState.value.currentZone?.let { zone ->
                computeStrokeProgression(
                    session = _uiState.value.trainingSession,
                    currentZone = zone,
                    zones = _uiState.value.zones,
                    strokeTimestamp = event.timestamp
                )
            }
        } else null

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

            // Apply pre-computed progression and sync quality update
            val sessionAfterProgress = progression?.session ?: state.trainingSession
            val updatedSession = if (syncQualityUpdate != null) {
                sessionAfterProgress.copy(
                    syncQuality = sessionAfterProgress.syncQuality + syncQualityUpdate
                )
            } else {
                sessionAfterProgress
            }

            state.copy(
                devices = updatedDevices,
                trainingSession = updatedSession
            )
        }

        // Run progression side effects once, now that the state update has committed
        progression?.let { runStrokeProgressionEffects(it) }

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

    private fun handleCalibrationUpdate(update: BleManager.CalibrationUpdate) {
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == update.deviceId) {
                    device.copy(
                        calibrationProgress = update.strokeCount,
                        calibrationMaxAccel = update.maxAccel,
                        calibrationMinAccel = update.minAccel,
                        strokeThreshold = update.suggestedThreshold,
                        calibrationState = if (update.isComplete) {
                            CalibrationState.Complete
                        } else {
                            CalibrationState.InProgress
                        }
                    )
                } else {
                    device
                }
            }
            state.copy(devices = updatedDevices)
        }

        Log.d(TAG, "Calibration update applied for ${update.deviceId}: " +
                "${update.strokeCount}/50 strokes, threshold=${update.suggestedThreshold}g")
    }

    private fun handleFsrUpdate(update: BleManager.FsrUpdate) {
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == update.deviceId) {
                    device.copy(
                        topHandPressurePercent = update.forcePercent,
                        topHandPressureThresholdTriggered = update.topHandPressureThresholdTriggered
                    )
                } else {
                    device
                }
            }
            state.copy(devices = updatedDevices)
        }

        // Drive LED feedback based on grip force (during active training)
        if (_uiState.value.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active) {
            coachingEngine.onFsrUpdate(update.deviceId, update.forcePercent)
        }
    }

    /** Maps a model-level [SessionAudioCue] to its BLE audio-event byte. */
    private fun audioEventFor(cue: SessionAudioCue): Byte = when (cue) {
        SessionAudioCue.SetChangeoverBeep -> BleManager.AUDIO_SET_CHANGEOVER_BEEP
        SessionAudioCue.NextSetLow        -> BleManager.AUDIO_NEXT_SET_LOW
        SessionAudioCue.NextSetMedium     -> BleManager.AUDIO_NEXT_SET_MEDIUM
        SessionAudioCue.NextSetHigh       -> BleManager.AUDIO_NEXT_SET_HIGH
        SessionAudioCue.LastSet           -> BleManager.AUDIO_LAST_SET
    }

    /** Executes the side effects of a [StrokeProgression] exactly once, after the state commit. */
    private fun runStrokeProgressionEffects(progression: StrokeProgression) {
        progression.checkPaceTargetSpm?.let { strokeAnalyzer.checkPaceDeviation(it) }

        progression.audioCues.forEach { broadcastAudioPrompt(audioEventFor(it), 100) }

        if (progression.reconfigureZone) {
            viewModelScope.launch { configureCurrentZone() }
        }

        if (progression.sessionComplete) {
            val outcome = SessionOutcome.compute(
                followerLatenciesMs = _uiState.value.trainingSession.syncQuality.values,
                strokeFsrPeakPercents = strokeAnalyzer.getAllStrokes().map { it.fsrPeakPercent }
            )
            broadcastAudioPrompt(sessionSummaryAudioPromptFor(outcome), 100)
            stopTrainingSession()
        }
    }

    private fun triggerFollowerHaptics(currentZone: Zone?) {
        val (pattern, intensity) = when (currentZone?.level) {
            com.orotrain.oro.model.ZoneLevel.Low -> Pair(BleManager.PATTERN_SOFT_CLICK, 60)
            com.orotrain.oro.model.ZoneLevel.Medium -> Pair(BleManager.PATTERN_STRONG_CLICK, 80)
            com.orotrain.oro.model.ZoneLevel.High -> Pair(BleManager.PATTERN_DOUBLE_CLICK, 100)
            null -> Pair(BleManager.PATTERN_STRONG_CLICK, 80)
        }

        bleManager?.broadcastHaptic(
            command = BleManager.CMD_SINGLE_PULSE,
            pattern = pattern,
            intensity = intensity,
            includePacer = true  // Include pacer so all devices pulse together
        )
    }

    private fun broadcastAudioPrompt(audioEvent: Byte, volume: Int = 90) {
        // Send audio command to all devices (including pacer)
        bleManager?.broadcastAudio(
            audioEvent = audioEvent,
            volume = volume,
            includePacer = true
        )
    }

    fun setDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination) }
    }

    /** Applies a pure zone-list [transform] to the active session's zones. */
    private fun editZones(transform: (List<Zone>) -> List<Zone>) {
        _uiState.update { state -> state.copy(zones = transform(state.zones)) }
    }

    fun addZone() = editZones { it.withAddedZone() }

    fun addZoneAfter(zoneId: String) = editZones { it.withZoneAddedAfter(zoneId) }

    fun duplicateZone(zoneId: String) = editZones { it.withZoneDuplicated(zoneId) }

    fun removeZone(zoneId: String) = editZones { it.withZoneRemoved(zoneId) }

    fun adjustZone(zoneId: String, field: ZoneField, delta: Int) =
        editZones { it.withZoneAdjusted(zoneId, field, delta) }

    fun setZoneLevel(zoneId: String, level: ZoneLevel) =
        editZones { it.withZoneLevel(zoneId, level) }

    fun reorderZones(fromIndex: Int, toIndex: Int) =
        editZones { it.withZoneMoved(fromIndex, toIndex) }

    // ── Programme library ────────────────────────────────────────────────────

    fun createProgramme(name: String) {
        val programme = Programme(name = name.trim())
        _uiState.update { state ->
            state.copy(programmes = state.programmes + programme)
        }
        persistProgrammes()
    }

    fun renameProgramme(id: String, name: String) {
        _uiState.update { state ->
            state.copy(
                programmes = state.programmes.map {
                    if (it.id == id) it.copy(name = name.trim()) else it
                },
                activeProgramme = state.activeProgramme?.let {
                    if (it.id == id) it.copy(name = name.trim()) else it
                }
            )
        }
        persistProgrammes()
    }

    fun deleteProgramme(id: String) {
        _uiState.update { state ->
            val isActive = state.activeProgramme?.id == id
            state.copy(
                programmes = state.programmes.filterNot { it.id == id },
                activeProgramme = if (isActive) null else state.activeProgramme,
                zones = if (isActive) emptyList() else state.zones,
                editingProgrammeId = if (state.editingProgrammeId == id) null else state.editingProgrammeId
            )
        }
        persistProgrammes()
    }

    fun duplicateProgramme(id: String) {
        _uiState.update { state ->
            val source = state.programmes.find { it.id == id } ?: return@update state
            val copy = source.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${source.name} (copy)",
                zones = source.zones.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
            )
            val index = state.programmes.indexOfFirst { it.id == id }
            val updated = state.programmes.toMutableList().also { it.add(index + 1, copy) }
            state.copy(programmes = updated)
        }
        persistProgrammes()
    }

    fun loadProgramme(id: String) {
        _uiState.update { state ->
            val programme = state.programmes.find { it.id == id } ?: return@update state
            state.copy(
                activeProgramme = programme,
                zones = programme.zones.map { it.copy() },
                destination = AppDestination.Training
            )
        }
    }

    fun openProgrammeEditor(id: String) {
        _uiState.update { it.copy(editingProgrammeId = id, destination = AppDestination.Programmes) }
    }

    fun closeProgrammeEditor() {
        _uiState.update { it.copy(editingProgrammeId = null) }
    }

    /** Applies a pure zone-list [transform] to the editing programme's zones, then persists. */
    private fun editProgrammeZones(transform: (List<Zone>) -> List<Zone>) {
        _uiState.update { state ->
            val id = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == id } ?: return@update state
            val updated = programme.copy(zones = transform(programme.zones))
            state.copy(programmes = state.programmes.map { if (it.id == id) updated else it })
        }
        persistProgrammes()
    }

    fun addZoneToEditingProgramme() = editProgrammeZones { it.withAddedZone() }

    fun addZoneAfterInProgramme(zoneId: String) = editProgrammeZones { it.withZoneAddedAfter(zoneId) }

    fun duplicateZoneInProgramme(zoneId: String) = editProgrammeZones { it.withZoneDuplicated(zoneId) }

    fun removeZoneFromProgramme(zoneId: String) = editProgrammeZones { it.withZoneRemoved(zoneId) }

    fun adjustZoneInProgramme(zoneId: String, field: ZoneField, delta: Int) =
        editProgrammeZones { it.withZoneAdjusted(zoneId, field, delta) }

    fun reorderZonesInProgramme(fromIndex: Int, toIndex: Int) =
        editProgrammeZones { it.withZoneMoved(fromIndex, toIndex) }

    private fun persistProgrammes() {
        viewModelScope.launch {
            programmeRepository?.saveAll(_uiState.value.programmes)
        }
    }

    fun startScan() {
        Log.d(TAG, "=== startScan() CALLED ===")
        Log.d(TAG, "  Currently scanning: ${_uiState.value.isScanning}")
        Log.d(TAG, "  bleManager is null: ${bleManager == null}")

        if (_uiState.value.isScanning) {
            Log.d(TAG, "  Scan already in progress, ignoring")
            return
        }

        if (bleManager != null) {
            // Use real BLE scanning
            Log.d(TAG, "  Starting real BLE scan via bleManager")
            bleManager.startScan()
        } else {
            // Fallback to simulated scan for preview/testing
            Log.w(TAG, "  BleManager is NULL - using simulated scan (THIS SHOULD NOT HAPPEN IN PRODUCTION!)")
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
        Log.d(TAG, "=== toggleDeviceConnection CALLED ===")
        Log.d(TAG, "  Device ID: $deviceId")

        val device = _uiState.value.devices.find { it.id == deviceId }
        if (device == null) {
            Log.e(TAG, "  Device NOT FOUND in state!")
            return
        }

        Log.d(TAG, "  Device: ${device.name}, Status: ${device.status}")

        when (device.status) {
            DeviceStatus.Connected -> {
                Log.d(TAG, "  Action: Disconnecting")
                disconnect(deviceId)
            }
            DeviceStatus.Disconnected -> {
                Log.d(TAG, "  Action: Connecting")
                connect(deviceId)
            }
            DeviceStatus.Connecting -> {
                Log.d(TAG, "  Action: Ignored (already connecting)")
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
        Log.d(TAG, "=== connect() CALLED ===")
        Log.d(TAG, "  Device ID: $deviceId")
        Log.d(TAG, "  bleManager is null: ${bleManager == null}")

        if (bleManager != null) {
            // Use real BLE connection
            Log.d(TAG, "  Calling bleManager.connectDevice($deviceId)")
            bleManager.connectDevice(deviceId)
        } else {
            // Fallback to simulated connection for preview/testing
            Log.w(TAG, "  BleManager is NULL - using simulated connection (THIS SHOULD NOT HAPPEN IN PRODUCTION!)")
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

    private fun renumberSeats(devices: List<HapticDevice>): List<HapticDevice> {
        val result = autoAssignSeats(devices)
        result.find { it.seat == 1 && it.status == DeviceStatus.Connected }
            ?.let { setPacerDevice(it.id) }
        return result
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

    fun testAudioBroadcast(audioEvent: Byte = BleManager.AUDIO_SESSION_START_BEEP, volume: Int = 90) {
        Log.d(TAG, "=== TEST AUDIO BROADCAST ===")
        Log.d(TAG, "Audio Event: 0x${String.format("%02X", audioEvent)}, Volume: $volume")
        Log.d(TAG, "Connected devices: ${_uiState.value.devices.filter { it.status == DeviceStatus.Connected }.size}")

        val result = bleManager?.broadcastAudio(
            audioEvent = audioEvent,
            volume = volume,
            includePacer = true
        )

        if (result != null) {
            Log.d(TAG, "Broadcast result: ${result.succeeded}/${result.attempted} devices")
        } else {
            Log.w(TAG, "BleManager is null - cannot test audio")
        }
    }

    // Calibration and stroke detection functions

    fun startCalibration(deviceId: String) {
        bleManager?.startCalibration(deviceId)
        _uiState.update { state ->
            val updatedDevices = state.devices.map { device ->
                if (device.id == deviceId) {
                    device.copy(calibrationState = CalibrationState.InProgress, strokeCount = 0)
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
                    device.copy(calibrationState = CalibrationState.NotStarted)
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

    fun assignSeat(deviceId: String, newSeat: Int) {
        _uiState.update { state ->
            val updated = swapSeats(state.devices, deviceId, newSeat)
            val pacer = updated.find { it.seat == 1 && it.status == DeviceStatus.Connected }
            pacer?.let { setPacerDevice(it.id) }
            state.copy(devices = updated)
        }
    }

    fun enableStrokeDetection(deviceId: String) {
        bleManager?.enableStrokeDetection(deviceId)
    }

    fun disableStrokeDetection(deviceId: String) {
        bleManager?.disableStrokeDetection(deviceId)
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

        // Reset analytics and coaching for new session
        strokeAnalyzer.reset()
        coachingEngine.reset()

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
            broadcastAudioPrompt(BleManager.AUDIO_SESSION_START_BEEP, 100)
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
                zoneColor = currentZone.zoneColor,
                isPacer = device.seat == 1
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
    }

    fun stopTrainingSession() {
        val state = _uiState.value
        if (!state.trainingSession.isActive) return
        pacerLastCatchReceivedMs = null
        syncLatencyAccumulator.clear()

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

        // Reset coaching engine so post-session stray events don't trigger feedback
        coachingEngine.reset()

        // Save session data to database
        viewModelScope.launch {
            try {
                sessionRepository?.saveSession(strokeAnalyzer, state.trainingSession)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save session: ${e.message}")
            }
        }

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

