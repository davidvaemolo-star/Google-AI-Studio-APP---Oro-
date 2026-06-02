package com.orotrain.oro.model

/**
 * An audio prompt the session should play in response to a pacer FINISH. Kept as a model-level
 * concept (not a raw BLE byte) so the progression decision stays free of the BLE layer; the
 * ViewModel maps these cues to the wire constants when it runs the effects.
 */
enum class SessionAudioCue {
    SetChangeoverBeep,
    NextSetLow,
    NextSetMedium,
    NextSetHigh,
    LastSet
}

/**
 * The outcome of a pacer FINISH stroke: the next [session] state plus a description of the side
 * effects to run after it commits. Holding the effects as data (rather than performing them inline)
 * keeps [computeStrokeProgression] pure, so it can be computed before the state update and unit
 * tested directly.
 */
data class StrokeProgression(
    val session: TrainingSessionState,
    val audioCues: List<SessionAudioCue> = emptyList(),
    val checkPaceTargetSpm: Int? = null,
    val reconfigureZone: Boolean = false,
    val sessionComplete: Boolean = false,
    /** True on the Pacer's 3rd-to-last Finish of a High set — speak the Canoe Speed (ADR-0018). */
    val announceSpeed: Boolean = false
)

/**
 * Strokes per minute derived from recent FINISH [timestamps] (ms). Returns 0 with fewer than two
 * samples or a non-positive average interval.
 */
fun calculateSpm(timestamps: List<Long>): Int {
    if (timestamps.size < 2) return 0
    val avgInterval = timestamps.zipWithNext { a, b -> b - a }.average()
    return if (avgInterval > 0) (60000 / avgInterval).toInt() else 0
}

/**
 * Pure progression rule for a pacer FINISH. Given the current [session], its [currentZone], the full
 * [zones] list, and the stroke's [strokeTimestamp], returns the next session state and the effects
 * to run. Performs no I/O and reads no shared state.
 *
 * The terminal case flags [StrokeProgression.sessionComplete] but does NOT set status to Completed —
 * that transition has a single owner (the ViewModel's stop path), avoiding the double-write that
 * arises when this is computed inside a retried state-update lambda.
 */
fun computeStrokeProgression(
    session: TrainingSessionState,
    currentZone: Zone,
    zones: List<Zone>,
    strokeTimestamp: Long
): StrokeProgression {
    val newStroke = session.currentStroke + 1
    val updatedTimestamps = (session.recentStrokeTimestamps + strokeTimestamp).takeLast(10)
    val calculatedSpm = calculateSpm(updatedTimestamps)
    // Check pace deviation every 10 strokes
    val checkPace = if (newStroke % 10 == 0) currentZone.targetSpm else null

    // Still mid-set: just advance the stroke counter
    if (newStroke < currentZone.strokes) {
        // Canoe Speed call-out: only in High zones, on the 3rd-to-last Finish (2 strokes remain).
        // Sets < 3 strokes never match (strokes - 2 < 1), so they are skipped (ADR-0018).
        val announceSpeed = currentZone.level == ZoneLevel.High &&
            newStroke == currentZone.strokes - 2
        return StrokeProgression(
            session = session.copy(
                currentStroke = newStroke,
                recentStrokeTimestamps = updatedTimestamps,
                currentSpm = calculatedSpm
            ),
            checkPaceTargetSpm = checkPace,
            announceSpeed = announceSpeed
        )
    }

    val newSet = session.currentSet + 1

    // Set complete, more sets remain in this zone: move to next set
    if (newSet <= currentZone.sets) {
        val cues = buildList {
            add(SessionAudioCue.SetChangeoverBeep)
            // Voice: entering the last set of this zone — announce what comes after it
            if (newSet == currentZone.sets) {
                val nextZoneIndex = session.currentZoneIndex + 1
                add(
                    if (nextZoneIndex < zones.size) when (zones[nextZoneIndex].level) {
                        ZoneLevel.Low    -> SessionAudioCue.NextSetLow
                        ZoneLevel.Medium -> SessionAudioCue.NextSetMedium
                        ZoneLevel.High   -> SessionAudioCue.NextSetHigh
                    } else SessionAudioCue.LastSet
                )
            }
        }
        return StrokeProgression(
            session = session.copy(
                currentStroke = 0,
                currentSet = newSet,
                recentStrokeTimestamps = updatedTimestamps,
                currentSpm = calculatedSpm
            ),
            audioCues = cues,
            checkPaceTargetSpm = checkPace
        )
    }

    // Zone complete: advance to the next zone, or finish the session
    val nextZoneIndex = session.currentZoneIndex + 1
    return if (nextZoneIndex < zones.size) {
        StrokeProgression(
            session = session.copy(
                currentZoneIndex = nextZoneIndex,
                currentStroke = 0,
                currentSet = 1,
                recentStrokeTimestamps = updatedTimestamps,
                currentSpm = calculatedSpm
            ),
            checkPaceTargetSpm = checkPace,
            reconfigureZone = true
        )
    } else {
        StrokeProgression(
            session = session.copy(
                recentStrokeTimestamps = updatedTimestamps,
                currentSpm = calculatedSpm
            ),
            checkPaceTargetSpm = checkPace,
            sessionComplete = true
        )
    }
}
