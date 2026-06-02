package com.orotrain.oro.model

/**
 * Tunables for the spoken Canoe Speed call-out (ADR-0018). Placeholders pending field validation
 * (see CONTEXT.md open questions).
 */
object CanoeSpeedConfig {
    /** Rolling-average window used to smooth raw GPS speed before speaking it. */
    const val SMOOTHING_WINDOW_MS: Long = 1_500
    /**
     * Highest km/h the firmware can speak; the codec clamps to this. 29.9 (not 30.0) so the device
     * never needs a "thirty" clip — 21–29 are composed as "twenty" + ones (ADR-0018).
     */
    const val MAX_SPEAKABLE_KMH: Float = 29.9f
}

/**
 * Pure rolling-average smoother for GPS-derived Canoe Speed (ADR-0018). Feed it timestamped km/h
 * samples; [smoothed] averages the samples inside the trailing [windowMs] and returns null when no
 * sample falls inside the window — which the caller treats as "no GPS fix" and skips the call-out.
 *
 * Not thread-safe: the ViewModel feeds and reads it on a single coroutine (the main dispatcher).
 */
class SpeedSmoother(private val windowMs: Long = CanoeSpeedConfig.SMOOTHING_WINDOW_MS) {
    private data class Sample(val timeMs: Long, val kmh: Float)
    private val samples = ArrayDeque<Sample>()

    fun addSample(timeMs: Long, kmh: Float) {
        samples.addLast(Sample(timeMs, kmh))
        prune(timeMs)
    }

    /** Average km/h over the trailing window at [nowMs], or null if no sample falls inside it. */
    fun smoothed(nowMs: Long): Float? {
        prune(nowMs)
        if (samples.isEmpty()) return null
        return samples.map { it.kmh }.average().toFloat()
    }

    private fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().timeMs > windowMs) {
            samples.removeFirst()
        }
    }
}
