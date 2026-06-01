package com.orotrain.oro.analysis

import android.util.Log
import com.orotrain.oro.ble.BleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-stroke record with all computed metrics.
 */
data class StrokeRecord(
    val strokeNumber: Int,
    val timestamp: Long,              // System time when FINISH received
    val catchTimestamp: Long,          // Firmware millis at CATCH
    val finishTimestamp: Long,         // Firmware millis at FINISH
    val peakAccelG: Float,            // Peak acceleration during drive (g)
    val minAccelG: Float,             // Min acceleration during recovery (g)
    val driveDurationMs: Int,         // CATCH-to-FINISH duration
    val recoveryDurationMs: Int,      // FINISH-to-next-CATCH (0 if not yet known)
    val totalStrokeDurationMs: Int,   // CATCH-to-next-CATCH (set on next stroke)
    val driveRatio: Float,            // driveDuration / totalStrokeDuration (0.0-1.0)
    val fsrPeakPercent: Int,          // Peak grip force during this stroke
    val fsrAtFinish: Int,             // FSR reading at finish
    val powerScore: Float             // peakAccel * driveDuration (proxy for work)
)

/**
 * Rolling metrics computed over a sliding window of recent strokes.
 */
data class StrokeMetrics(
    val windowSize: Int = 0,
    val avgPeakAccel: Float = 0f,
    val avgDriveDuration: Int = 0,
    val avgRecoveryDuration: Int = 0,
    val avgDriveRatio: Float = 0f,
    val avgFsrPercent: Int = 0,
    val consistencyScore: Float = 0f,   // 0-100, based on timing CV
    val powerScore: Float = 0f,         // Average power proxy
    val fatigueIndex: Float = 1f,       // Ratio of recent power to early power
    val currentSpm: Int = 0
)

/**
 * Coaching issues detected from the data.
 */
enum class CoachingIssue {
    OVER_GRIPPING,          // FSR consistently above threshold
    GRIP_FADING,            // FSR decreasing over time (losing grip)
    POWER_DROPPING,         // Peak accel trending down (fatigue)
    INCONSISTENT_TIMING,    // High CV in stroke timing
    DRIVE_RATIO_LOW,        // Too little time in drive phase
    DRIVE_RATIO_HIGH,       // Too much time in drive (not enough recovery)
    PACE_TOO_SLOW,          // SPM below target zone
    PACE_TOO_FAST           // SPM above target zone
}

data class CoachingEvent(
    val issue: CoachingIssue,
    val severity: Float,      // 0.0-1.0
    val message: String,
    val timestamp: Long
)

/**
 * Core analytics engine that processes enriched stroke events and FSR data,
 * computes rolling quality metrics, and detects coaching-worthy issues.
 *
 * Dragon boat paddling metrics:
 * - Drive ratio: ideal ~0.40-0.50 (40-50% of stroke in drive phase)
 * - Consistency: CV of stroke intervals < 8% is excellent, > 20% is poor
 * - Fatigue: power ratio < 0.75 indicates significant fatigue
 */
class StrokeAnalyzer {
    companion object {
        private const val TAG = "StrokeAnalyzer"
        private const val ROLLING_WINDOW = 10        // Strokes for rolling metrics
        private const val FATIGUE_EARLY_WINDOW = 10  // First N strokes as baseline
        private const val MIN_STROKES_FOR_METRICS = 3
        private const val COACHING_COOLDOWN_MS = 15000L // Min time between same coaching tip
        private const val OVER_GRIP_THRESHOLD = 70   // FSR % considered over-gripping
        private const val CONSISTENCY_CV_BAD = 0.20f  // CV > 20% = poor consistency
    }

    private val allStrokes = mutableListOf<StrokeRecord>()

    private val _metrics = MutableStateFlow(StrokeMetrics())
    val metrics: StateFlow<StrokeMetrics> = _metrics.asStateFlow()

    private val _coachingEvents = MutableStateFlow<CoachingEvent?>(null)
    val coachingEvents: StateFlow<CoachingEvent?> = _coachingEvents.asStateFlow()

    private val lastCoachingTime = mutableMapOf<CoachingIssue, Long>()

    // Intermediate state for building a stroke record across phase events
    private var pendingCatchTimestamp: Long = 0L
    private var pendingPeakAccel: Float = 0f
    private var pendingFsrPeak: Int = 0
    private var lastFinishTimestamp: Long = 0L

    fun onStrokeEvent(event: BleManager.StrokeEvent) {
        when (event.phase) {
            BleManager.STROKE_PHASE_CATCH -> {
                pendingCatchTimestamp = event.timestamp
                pendingPeakAccel = event.peakAccel
                pendingFsrPeak = event.topHandPressurePercent

                // Update previous stroke's recovery duration and drive ratio
                if (allStrokes.isNotEmpty() && lastFinishTimestamp > 0) {
                    val lastIdx = allStrokes.lastIndex
                    val last = allStrokes[lastIdx]
                    val recoveryMs = (event.timestamp - lastFinishTimestamp).toInt()
                        .coerceIn(0, 10000) // Sanity cap
                    val totalMs = (event.timestamp - last.catchTimestamp).toInt()
                        .coerceIn(1, 15000)
                    val driveRatio = if (totalMs > 0) {
                        last.driveDurationMs / totalMs.toFloat()
                    } else 0f

                    allStrokes[lastIdx] = last.copy(
                        recoveryDurationMs = recoveryMs,
                        totalStrokeDurationMs = totalMs,
                        driveRatio = driveRatio
                    )
                }
            }

            BleManager.STROKE_PHASE_DRIVE -> {
                // Track peak values across phases
                if (event.peakAccel > pendingPeakAccel) {
                    pendingPeakAccel = event.peakAccel
                }
                if (event.topHandPressurePercent > pendingFsrPeak) {
                    pendingFsrPeak = event.topHandPressurePercent
                }
            }

            BleManager.STROKE_PHASE_FINISH -> {
                // Finalize peak accel from firmware's accumulated value
                if (event.peakAccel > pendingPeakAccel) {
                    pendingPeakAccel = event.peakAccel
                }
                lastFinishTimestamp = event.timestamp

                // Compute drive duration: use firmware's phase duration if available,
                // otherwise compute from catch-to-finish timestamps
                val driveDuration = if (event.phaseDurationMs > 0) {
                    event.phaseDurationMs
                } else if (pendingCatchTimestamp > 0) {
                    (event.timestamp - pendingCatchTimestamp).toInt().coerceIn(0, 10000)
                } else {
                    0
                }

                val strokeNum = allStrokes.size + 1
                val powerScore = pendingPeakAccel * driveDuration / 1000f

                val record = StrokeRecord(
                    strokeNumber = strokeNum,
                    timestamp = System.currentTimeMillis(),
                    catchTimestamp = pendingCatchTimestamp,
                    finishTimestamp = event.timestamp,
                    peakAccelG = pendingPeakAccel,
                    minAccelG = event.minAccel,
                    driveDurationMs = driveDuration,
                    recoveryDurationMs = 0,        // Filled on next CATCH
                    totalStrokeDurationMs = 0,      // Filled on next CATCH
                    driveRatio = 0f,                // Filled on next CATCH
                    fsrPeakPercent = pendingFsrPeak,
                    fsrAtFinish = event.topHandPressurePercent,
                    powerScore = powerScore
                )
                allStrokes.add(record)

                Log.d(TAG, "Stroke #$strokeNum: peak=${pendingPeakAccel}g, drive=${driveDuration}ms, " +
                    "fsr=$pendingFsrPeak%, power=${"%.2f".format(powerScore)}")

                recomputeMetrics()
                detectCoachingIssues()
            }

            BleManager.STROKE_PHASE_RECOVERY -> {
                // Recovery duration computed on next CATCH
            }
        }
    }

    private fun recomputeMetrics() {
        if (allStrokes.size < MIN_STROKES_FOR_METRICS) return

        val window = allStrokes.takeLast(ROLLING_WINDOW)
        val completedWindow = window.filter { it.totalStrokeDurationMs > 0 }

        val avgPeak = window.map { it.peakAccelG }.average().toFloat()
        val avgDrive = window.map { it.driveDurationMs }.average().toInt()
        val avgRecovery = if (completedWindow.isNotEmpty()) {
            completedWindow.map { it.recoveryDurationMs }.average().toInt()
        } else 0
        val avgDriveRatio = if (completedWindow.isNotEmpty()) {
            completedWindow.map { it.driveRatio }.average().toFloat()
        } else 0f
        val avgFsr = window.map { it.fsrPeakPercent }.average().toInt()
        val avgPower = window.map { it.powerScore }.average().toFloat()

        // Consistency: CV of total stroke durations (lower CV = better consistency)
        val consistencyScore = if (completedWindow.size >= 2) {
            val durations = completedWindow.map { it.totalStrokeDurationMs.toFloat() }
            val mean = durations.average().toFloat()
            val variance = durations.map { (it - mean) * (it - mean) }.average().toFloat()
            val stdDev = kotlin.math.sqrt(variance)
            val cv = if (mean > 0) stdDev / mean else 1f
            // Map CV to 0-100 score (lower CV = higher score)
            ((1f - (cv / CONSISTENCY_CV_BAD).coerceAtMost(1f)) * 100).coerceIn(0f, 100f)
        } else 0f

        // Fatigue index: recent avg power vs first N strokes
        val fatigueIndex = if (allStrokes.size > FATIGUE_EARLY_WINDOW) {
            val earlyPower = allStrokes.take(FATIGUE_EARLY_WINDOW)
                .map { it.powerScore }.average().toFloat()
            if (earlyPower > 0) avgPower / earlyPower else 1f
        } else 1f

        // SPM from system timestamps of recent strokes
        val timestamps = window.map { it.timestamp }
        val spm = if (timestamps.size >= 2) {
            val intervals = timestamps.zipWithNext { a, b -> b - a }
            val avgInterval = intervals.average()
            if (avgInterval > 0) (60000 / avgInterval).toInt() else 0
        } else 0

        val newMetrics = StrokeMetrics(
            windowSize = window.size,
            avgPeakAccel = avgPeak,
            avgDriveDuration = avgDrive,
            avgRecoveryDuration = avgRecovery,
            avgDriveRatio = avgDriveRatio,
            avgFsrPercent = avgFsr,
            consistencyScore = consistencyScore,
            powerScore = avgPower,
            fatigueIndex = fatigueIndex,
            currentSpm = spm
        )
        _metrics.value = newMetrics

        Log.d(TAG, "Metrics: SPM=$spm, consistency=${"%.0f".format(consistencyScore)}, " +
            "power=${"%.2f".format(avgPower)}, fatigue=${"%.2f".format(fatigueIndex)}, " +
            "driveRatio=${"%.2f".format(avgDriveRatio)}, avgFsr=$avgFsr%")
    }

    private fun detectCoachingIssues() {
        val now = System.currentTimeMillis()
        val m = _metrics.value
        if (m.windowSize < MIN_STROKES_FOR_METRICS) return

        // Over-gripping: FSR consistently above threshold
        if (m.avgFsrPercent > OVER_GRIP_THRESHOLD) {
            emitCoaching(
                CoachingIssue.OVER_GRIPPING,
                ((m.avgFsrPercent - OVER_GRIP_THRESHOLD) / 30f).coerceIn(0f, 1f),
                "Relax your grip. You're holding too tight.",
                now
            )
        }

        // Power dropping (fatigue)
        if (m.fatigueIndex < 0.75f && allStrokes.size > 15) {
            emitCoaching(
                CoachingIssue.POWER_DROPPING,
                (1f - m.fatigueIndex).coerceIn(0f, 1f),
                "Power is dropping. Focus on strong catches.",
                now
            )
        }

        // Inconsistent timing
        if (m.consistencyScore in 1f..60f && m.windowSize >= 5) {
            emitCoaching(
                CoachingIssue.INCONSISTENT_TIMING,
                ((60f - m.consistencyScore) / 60f).coerceIn(0f, 1f),
                "Timing is inconsistent. Find your rhythm.",
                now
            )
        }

        // Drive ratio too low (not enough time in drive)
        if (m.avgDriveRatio in 0.01f..0.35f) {
            emitCoaching(
                CoachingIssue.DRIVE_RATIO_LOW,
                ((0.35f - m.avgDriveRatio) / 0.35f).coerceIn(0f, 1f),
                "Your drive phase is short. Pull through the water longer.",
                now
            )
        }

        // Drive ratio too high (not enough recovery)
        if (m.avgDriveRatio > 0.55f) {
            emitCoaching(
                CoachingIssue.DRIVE_RATIO_HIGH,
                ((m.avgDriveRatio - 0.55f) / 0.25f).coerceIn(0f, 1f),
                "Slow your recovery. Let the boat run.",
                now
            )
        }
    }

    private fun emitCoaching(issue: CoachingIssue, severity: Float, message: String, now: Long) {
        val lastTime = lastCoachingTime[issue] ?: 0L
        if (now - lastTime < COACHING_COOLDOWN_MS) return

        lastCoachingTime[issue] = now
        _coachingEvents.value = CoachingEvent(issue, severity.coerceIn(0f, 1f), message, now)
        Log.d(TAG, "Coaching: ${issue.name} (severity=${"%.2f".format(severity)}) - $message")
    }

    /**
     * Check if current SPM deviates significantly from target.
     * Call periodically (e.g., every 10 strokes) with the current zone's target SPM.
     */
    fun checkPaceDeviation(targetSpm: Int): CoachingEvent? {
        val m = _metrics.value
        if (m.currentSpm <= 0 || targetSpm <= 0) return null

        val deviation = m.currentSpm - targetSpm
        val now = System.currentTimeMillis()

        return if (deviation > 5) {
            val event = CoachingEvent(
                CoachingIssue.PACE_TOO_FAST,
                (deviation / 15f).coerceIn(0f, 1f),
                "Ease up, you're $deviation strokes above target pace.",
                now
            )
            val lastTime = lastCoachingTime[CoachingIssue.PACE_TOO_FAST] ?: 0L
            if (now - lastTime >= COACHING_COOLDOWN_MS) {
                lastCoachingTime[CoachingIssue.PACE_TOO_FAST] = now
                _coachingEvents.value = event
                event
            } else null
        } else if (deviation < -5) {
            val event = CoachingEvent(
                CoachingIssue.PACE_TOO_SLOW,
                (-deviation / 15f).coerceIn(0f, 1f),
                "Pick it up, you're ${-deviation} strokes below target pace.",
                now
            )
            val lastTime = lastCoachingTime[CoachingIssue.PACE_TOO_SLOW] ?: 0L
            if (now - lastTime >= COACHING_COOLDOWN_MS) {
                lastCoachingTime[CoachingIssue.PACE_TOO_SLOW] = now
                _coachingEvents.value = event
                event
            } else null
        } else null
    }

    fun reset() {
        allStrokes.clear()
        lastCoachingTime.clear()
        _metrics.value = StrokeMetrics()
        _coachingEvents.value = null
        pendingCatchTimestamp = 0L
        pendingPeakAccel = 0f
        pendingFsrPeak = 0
        lastFinishTimestamp = 0L
        Log.d(TAG, "StrokeAnalyzer reset")
    }

    fun getAllStrokes(): List<StrokeRecord> = allStrokes.toList()

    fun getTotalStrokeCount(): Int = allStrokes.size
}
