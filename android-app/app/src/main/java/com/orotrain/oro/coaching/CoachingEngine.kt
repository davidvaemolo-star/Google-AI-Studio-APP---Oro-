package com.orotrain.oro.coaching

import android.util.Log
import com.orotrain.oro.analysis.CoachingEvent
import com.orotrain.oro.analysis.CoachingIssue
import com.orotrain.oro.ble.BleManager

/**
 * Drives real-time haptic coaching feedback from StrokeAnalyzer output, throttled to avoid
 * overwhelming the paddler. The phone is silent (ADR-0016), so coaching is haptic-only — cues are
 * buzzed on the devices, never spoken.
 */
class CoachingEngine(
    private val bleManager: BleManager?
) {
    companion object {
        private const val TAG = "CoachingEngine"

        // Minimum time between haptic coaching cues
        private const val HAPTIC_MIN_INTERVAL_MS = 5000L
    }

    var hapticFeedbackEnabled = true

    private var lastHapticTime = 0L

    fun onFsrUpdate(deviceId: String, forcePercent: Int) {
        // LED feedback removed — LED is firmware-driven (see ADR-0009)
    }

    /**
     * Called when StrokeAnalyzer emits a coaching event. Buzzes the matching haptic pattern on the
     * devices, throttled so cues don't pile up.
     */
    fun onCoachingEvent(event: CoachingEvent, allDeviceIds: List<String>) {
        val now = System.currentTimeMillis()

        if (hapticFeedbackEnabled && bleManager != null &&
            now - lastHapticTime > HAPTIC_MIN_INTERVAL_MS) {

            val pattern = when (event.issue) {
                CoachingIssue.OVER_GRIPPING -> BleManager.PATTERN_DOUBLE_CLICK
                CoachingIssue.POWER_DROPPING -> BleManager.PATTERN_TRIPLE_CLICK
                CoachingIssue.INCONSISTENT_TIMING -> BleManager.PATTERN_ALERT_750MS
                CoachingIssue.DRIVE_RATIO_LOW -> BleManager.PATTERN_SHARP_CLICK
                CoachingIssue.DRIVE_RATIO_HIGH -> BleManager.PATTERN_SOFT_CLICK
                CoachingIssue.PACE_TOO_SLOW -> BleManager.PATTERN_DOUBLE_CLICK
                CoachingIssue.PACE_TOO_FAST -> BleManager.PATTERN_SOFT_CLICK
                CoachingIssue.GRIP_FADING -> BleManager.PATTERN_SHARP_CLICK
            }

            val intensity = (event.severity * 60 + 40).toInt().coerceIn(40, 100)

            bleManager.broadcastHaptic(
                command = BleManager.CMD_SINGLE_PULSE,
                pattern = pattern,
                intensity = intensity,
                includePacer = true
            )
            lastHapticTime = now

            Log.d(TAG, "Haptic coaching: ${event.issue.name} pattern=$pattern intensity=$intensity")
        }
    }

    fun reset() {
        lastHapticTime = 0L
        Log.d(TAG, "CoachingEngine reset")
    }
}
