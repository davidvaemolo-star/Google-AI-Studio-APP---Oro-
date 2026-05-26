package com.orotrain.oro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orotrain.oro.analysis.StrokeAnalyzer
import com.orotrain.oro.coaching.CoachingEngine
import com.orotrain.oro.ble.BleManager
import com.orotrain.oro.data.SessionRepository
import com.orotrain.oro.model.AppDestination
import com.orotrain.oro.model.Programme
import com.orotrain.oro.model.DeviceStatus
import com.orotrain.oro.model.HapticDevice
import com.orotrain.oro.model.autoAssignSeats
import com.orotrain.oro.model.swapSeats
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
                            // Note: isCalibrating is intentionally NOT preserved — on every reconnect
                            // we trust the BLE-layer default (false). The firmware-side calibration
                            // state is reset on every cold boot anyway (RAM clears on System OFF),
                            // and preserving stale true values across reconnects strands the dialog
                            // in the ACTIVE view with no Start button.
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

            val pacer = state.devices.find { it.seat == 1 }

            // Update training session progress if active and event is from pacer
            val sessionAfterProgress = if (state.trainingSession.status == com.orotrain.oro.model.TrainingStatus.Active &&
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

            // Apply pre-computed sync quality update (accumulator already mutated above)
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
                        isCalibrationComplete = update.isComplete,
                        // Clear isCalibrating on completion so the dialog can reach the COMPLETE view —
                        // the when-cascade in CalibrationDialog tests isCalibrating before isCalibrationComplete.
                        isCalibrating = if (update.isComplete) false else device.isCalibrating
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

    private fun processStrokeForTraining(
        session: TrainingSessionState,
        currentZone: Zone,
        strokeTimestamp: Long
    ): TrainingSessionState {
        val newStroke = session.currentStroke + 1

        // Update recent stroke timestamps for SPM calculation (keep last 10)
        val updatedTimestamps = (session.recentStrokeTimestamps + strokeTimestamp).takeLast(10)
        val calculatedSpm = calculateSpm(updatedTimestamps)

        // Check pace deviation every 10 strokes
        if (newStroke % 10 == 0) {
            strokeAnalyzer.checkPaceDeviation(currentZone.targetSpm)
        }

        // Check if set is complete
        return if (newStroke >= currentZone.strokes) {
            val newSet = session.currentSet + 1

            // Check if all sets in zone are complete
            if (newSet > currentZone.sets) {
                advanceToNextZone(session, updatedTimestamps, calculatedSpm)
            } else {
                // Move to next set
                // Beep: set complete changeover
                broadcastAudioPrompt(BleManager.AUDIO_SET_CHANGEOVER_BEEP, 100)

                // Voice: entering last set of this zone
                if (newSet == currentZone.sets) {
                    val state = _uiState.value
                    val nextZoneIndex = session.currentZoneIndex + 1
                    if (nextZoneIndex < state.zones.size) {
                        val nextZoneEvent = when (state.zones[nextZoneIndex].level) {
                            com.orotrain.oro.model.ZoneLevel.Low    -> BleManager.AUDIO_NEXT_SET_LOW
                            com.orotrain.oro.model.ZoneLevel.Medium -> BleManager.AUDIO_NEXT_SET_MEDIUM
                            com.orotrain.oro.model.ZoneLevel.High   -> BleManager.AUDIO_NEXT_SET_HIGH
                        }
                        broadcastAudioPrompt(nextZoneEvent, 100)
                    } else {
                        broadcastAudioPrompt(BleManager.AUDIO_LAST_SET, 100)
                    }
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
            val summaryEvent = selectSessionSummaryPrompt(_uiState.value.trainingSession, strokeAnalyzer)
            broadcastAudioPrompt(summaryEvent, 100)

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

    private fun selectSessionSummaryPrompt(
        trainingSession: TrainingSessionState,
        strokeAnalyzer: StrokeAnalyzer
    ): Byte {
        val avgLatencyMs = if (trainingSession.syncQuality.isEmpty()) 300.0
                           else trainingSession.syncQuality.values.average()
        val syncScore = ((300.0 - avgLatencyMs) / 250.0 * 100.0).coerceIn(0.0, 100.0).toInt()

        val avgFsrPeak = strokeAnalyzer.sessionAverageFsrPeak()

        val powerEvent: (Byte, Byte, Byte, Byte) -> Byte = { light, moderate, strong, maximum ->
            when {
                avgFsrPeak >= 76 -> maximum
                avgFsrPeak >= 51 -> strong
                avgFsrPeak >= 26 -> moderate
                else             -> light
            }
        }

        return when {
            syncScore >= 80 -> powerEvent(
                BleManager.AUDIO_SUMMARY_EXCELLENT_LIGHT,
                BleManager.AUDIO_SUMMARY_EXCELLENT_MODERATE,
                BleManager.AUDIO_SUMMARY_EXCELLENT_STRONG,
                BleManager.AUDIO_SUMMARY_EXCELLENT_MAXIMUM
            )
            syncScore >= 50 -> powerEvent(
                BleManager.AUDIO_SUMMARY_GOOD_LIGHT,
                BleManager.AUDIO_SUMMARY_GOOD_MODERATE,
                BleManager.AUDIO_SUMMARY_GOOD_STRONG,
                BleManager.AUDIO_SUMMARY_GOOD_MAXIMUM
            )
            else -> powerEvent(
                BleManager.AUDIO_SUMMARY_POOR_LIGHT,
                BleManager.AUDIO_SUMMARY_POOR_MODERATE,
                BleManager.AUDIO_SUMMARY_POOR_STRONG,
                BleManager.AUDIO_SUMMARY_POOR_MAXIMUM
            )
        }
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

    fun addZoneToEditingProgramme() {
        _uiState.update { state ->
            val id = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == id } ?: return@update state
            if (programme.zones.size >= MAX_ZONES) return@update state
            val updated = programme.copy(zones = programme.zones + Zone())
            state.copy(programmes = state.programmes.map { if (it.id == id) updated else it })
        }
        persistProgrammes()
    }

    fun addZoneAfterInProgramme(zoneId: String) {
        _uiState.update { state ->
            val programmeId = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == programmeId } ?: return@update state
            if (programme.zones.size >= MAX_ZONES) return@update state
            val index = programme.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) return@update state
            val zones = programme.zones.toMutableList().also { it.add(index + 1, Zone()) }
            val updated = programme.copy(zones = zones)
            state.copy(programmes = state.programmes.map { if (it.id == programmeId) updated else it })
        }
        persistProgrammes()
    }

    fun duplicateZoneInProgramme(zoneId: String) {
        _uiState.update { state ->
            val programmeId = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == programmeId } ?: return@update state
            if (programme.zones.size >= MAX_ZONES) return@update state
            val index = programme.zones.indexOfFirst { it.id == zoneId }
            if (index == -1) return@update state
            val copy = programme.zones[index].copy(id = java.util.UUID.randomUUID().toString())
            val zones = programme.zones.toMutableList().also { it.add(index + 1, copy) }
            val updated = programme.copy(zones = zones)
            state.copy(programmes = state.programmes.map { if (it.id == programmeId) updated else it })
        }
        persistProgrammes()
    }

    fun removeZoneFromProgramme(zoneId: String) {
        _uiState.update { state ->
            val programmeId = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == programmeId } ?: return@update state
            val updated = programme.copy(zones = programme.zones.filterNot { it.id == zoneId })
            state.copy(programmes = state.programmes.map { if (it.id == programmeId) updated else it })
        }
        persistProgrammes()
    }

    fun adjustZoneInProgramme(zoneId: String, field: ZoneField, delta: Int) {
        _uiState.update { state ->
            val programmeId = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == programmeId } ?: return@update state
            val zones = programme.zones.map { zone ->
                if (zone.id != zoneId) return@map zone
                when (field) {
                    ZoneField.Strokes -> zone.copy(strokes = (zone.strokes + delta).coerceIn(MIN_VALUE, MAX_STROKES))
                    ZoneField.Sets -> zone.copy(sets = (zone.sets + delta).coerceIn(MIN_VALUE, MAX_SETS))
                    ZoneField.Level -> {
                        val levels = com.orotrain.oro.model.ZoneLevel.values()
                        val newIndex = (levels.indexOf(zone.level) + delta).coerceIn(0, levels.size - 1)
                        zone.copy(level = levels[newIndex])
                    }
                }
            }
            val updated = programme.copy(zones = zones)
            state.copy(programmes = state.programmes.map { if (it.id == programmeId) updated else it })
        }
        persistProgrammes()
    }

    fun reorderZonesInProgramme(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        _uiState.update { state ->
            val programmeId = state.editingProgrammeId ?: return@update state
            val programme = state.programmes.find { it.id == programmeId } ?: return@update state
            if (fromIndex !in programme.zones.indices || toIndex !in programme.zones.indices) return@update state
            val zones = programme.zones.toMutableList()
            zones.add(toIndex, zones.removeAt(fromIndex))
            val updated = programme.copy(zones = zones)
            state.copy(programmes = state.programmes.map { if (it.id == programmeId) updated else it })
        }
        persistProgrammes()
    }

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

