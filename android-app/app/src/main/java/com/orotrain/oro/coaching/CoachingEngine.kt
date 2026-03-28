package com.orotrain.oro.coaching

import android.util.Log
import com.orotrain.oro.analysis.CoachingEvent
import com.orotrain.oro.analysis.CoachingIssue
import com.orotrain.oro.analysis.StrokeMetrics
import com.orotrain.oro.audio.AudioManager
import com.orotrain.oro.ble.BleManager

/**
 * Drives real-time LED, haptic, and audio coaching feedback based on
 * StrokeAnalyzer output. Implements intelligent throttling to avoid
 * overwhelming the paddler.
 */
class CoachingEngine(
    private val bleManager: BleManager?,
    private val audioManager: AudioManager?
) {
    companion object {
        private const val TAG = "CoachingEngine"

        // Feedback intervals (minimum time between feedback of each type)
        private const val LED_UPDATE_INTERVAL_MS = 500L     // LED color update rate (2Hz)
        private const val HAPTIC_MIN_INTERVAL_MS = 5000L    // Min 5s between haptic coaching cues
        private const val AUDIO_MIN_INTERVAL_MS = 20000L    // Min 20s between audio coaching (speech is intrusive)
    }

    var ledFeedbackEnabled = true
    var hapticFeedbackEnabled = true
    var audioFeedbackEnabled = true

    private var lastLedUpdateTime = 0L
    private var lastHapticTime = 0L
    private var lastAudioTime = 0L

    /**
     * Called on every FSR update to drive LED color feedback.
     * LED reflects grip force in real-time:
     *   0-40%  = green (relaxed, efficient grip)
     *   40-70% = yellow gradient (moderate, watch it)
     *   70-100% = red (over-gripping, fatigue/injury risk)
     */
    fun onFsrUpdate(deviceId: String, forcePercent: Int) {
        if (!ledFeedbackEnabled || bleManager == null) return

        val now = System.currentTimeMillis()
        if (now - lastLedUpdateTime < LED_UPDATE_INTERVAL_MS) return
        lastLedUpdateTime = now

        val (r, g, b) = when {
            forcePercent < 40 -> Triple(0, 255, 0)       // Green — good
            forcePercent < 70 -> {
                // Smooth gradient: green → yellow → orange
                val t = (forcePercent - 40) / 30f
                Triple(
                    (255 * t).toInt().coerceIn(0, 255),
                    (255 * (1f - t * 0.5f)).toInt().coerceIn(0, 255),
                    0
                )
            }
            else -> Triple(255, 0, 0)                      // Red — over-gripping
        }

        bleManager.sendLedColor(deviceId, r, g, b)
    }

    /**
     * Called when StrokeAnalyzer emits a coaching event.
     * Routes to appropriate feedback channels with throttling.
     */
    fun onCoachingEvent(event: CoachingEvent, allDeviceIds: List<String>) {
        val now = System.currentTimeMillis()

        // Haptic feedback (quick, non-verbal cue)
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

        // Audio feedback (verbal coaching tip)
        if (audioFeedbackEnabled && audioManager != null &&
            now - lastAudioTime > AUDIO_MIN_INTERVAL_MS) {

            audioManager.speakCoachingTip(event.message)
            lastAudioTime = now

            Log.d(TAG, "Audio coaching: ${event.message}")
        }
    }

    /**
     * Periodic metrics-based feedback. Call on significant metrics updates
     * (e.g., every 10 strokes or on zone transitions).
     */
    fun onMetricsUpdate(metrics: StrokeMetrics, targetSpm: Int) {
        val now = System.currentTimeMillis()

        // Pace deviation audio feedback
        if (audioFeedbackEnabled && audioManager != null &&
            now - lastAudioTime > AUDIO_MIN_INTERVAL_MS &&
            metrics.currentSpm > 0 && targetSpm > 0) {

            val deviation = metrics.currentSpm - targetSpm
            if (kotlin.math.abs(deviation) > 5) {
                val msg = if (deviation > 0) {
                    "Ease up, you're $deviation strokes above target pace."
                } else {
                    "Pick it up, you're ${-deviation} strokes below target pace."
                }
                audioManager.speakCoachingTip(msg)
                lastAudioTime = now
            }
        }
    }

    fun reset() {
        lastLedUpdateTime = 0L
        lastHapticTime = 0L
        lastAudioTime = 0L
        Log.d(TAG, "CoachingEngine reset")
    }
}
