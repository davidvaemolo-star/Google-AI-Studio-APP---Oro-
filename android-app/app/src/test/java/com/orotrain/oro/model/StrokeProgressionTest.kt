package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeProgressionTest {

    private fun zone(strokes: Int = 10, sets: Int = 1, level: ZoneLevel = ZoneLevel.Low) =
        Zone(strokes = strokes, sets = sets, level = level)

    private fun session(
        zoneIndex: Int = 0,
        stroke: Int = 0,
        set: Int = 1
    ) = TrainingSessionState(
        status = TrainingStatus.Active,
        currentZoneIndex = zoneIndex,
        currentStroke = stroke,
        currentSet = set
    )

    // --- mid-set ---

    @Test
    fun `mid-set stroke just advances the counter`() {
        val zones = listOf(zone(strokes = 10))
        val result = computeStrokeProgression(session(stroke = 3), zones[0], zones, strokeTimestamp = 0)

        assertEquals(4, result.session.currentStroke)
        assertEquals(1, result.session.currentSet)
        assertTrue(result.audioCues.isEmpty())
        assertFalse(result.reconfigureZone)
        assertFalse(result.sessionComplete)
    }

    // --- set complete, more sets remain ---

    @Test
    fun `completing a set with sets remaining beeps and resets stroke`() {
        val zones = listOf(zone(strokes = 10, sets = 3))
        // last stroke of set 1 of 3
        val result = computeStrokeProgression(session(stroke = 9, set = 1), zones[0], zones, strokeTimestamp = 0)

        assertEquals(0, result.session.currentStroke)
        assertEquals(2, result.session.currentSet)
        assertEquals(listOf(SessionAudioCue.SetChangeoverBeep), result.audioCues)
        assertFalse(result.sessionComplete)
    }

    @Test
    fun `entering the last set announces the upcoming zone level`() {
        val zones = listOf(zone(strokes = 10, sets = 2), zone(level = ZoneLevel.High))
        // finishing set 1 of 2 -> newSet becomes 2 == sets, so announce next zone (High)
        val result = computeStrokeProgression(session(zoneIndex = 0, stroke = 9, set = 1), zones[0], zones, strokeTimestamp = 0)

        assertEquals(
            listOf(SessionAudioCue.SetChangeoverBeep, SessionAudioCue.NextSetHigh),
            result.audioCues
        )
    }

    @Test
    fun `entering the last set of the final zone announces last set`() {
        val zones = listOf(zone(strokes = 10, sets = 2)) // no zone after this one
        val result = computeStrokeProgression(session(zoneIndex = 0, stroke = 9, set = 1), zones[0], zones, strokeTimestamp = 0)

        assertEquals(
            listOf(SessionAudioCue.SetChangeoverBeep, SessionAudioCue.LastSet),
            result.audioCues
        )
    }

    // --- zone complete ---

    @Test
    fun `completing the last set of a non-final zone advances zone and requests reconfigure`() {
        val zones = listOf(zone(strokes = 10, sets = 1), zone(level = ZoneLevel.Medium))
        // last stroke of the only set of zone 0
        val result = computeStrokeProgression(session(zoneIndex = 0, stroke = 9, set = 1), zones[0], zones, strokeTimestamp = 0)

        assertEquals(1, result.session.currentZoneIndex)
        assertEquals(0, result.session.currentStroke)
        assertEquals(1, result.session.currentSet)
        assertTrue(result.reconfigureZone)
        assertFalse(result.sessionComplete)
    }

    // --- session complete ---

    @Test
    fun `completing the final zone flags completion without forcing Completed status`() {
        val zones = listOf(zone(strokes = 10, sets = 1)) // single zone, single set
        val result = computeStrokeProgression(session(zoneIndex = 0, stroke = 9, set = 1), zones[0], zones, strokeTimestamp = 0)

        assertTrue(result.sessionComplete)
        assertFalse(result.reconfigureZone)
        // status transition is owned by the stop path, not this pure function
        assertEquals(TrainingStatus.Active, result.session.status)
    }

    // --- pace deviation cadence ---

    @Test
    fun `pace check requested on every tenth stroke`() {
        val zones = listOf(zone(strokes = 100))
        // stroke 9 -> newStroke 10
        val onTenth = computeStrokeProgression(session(stroke = 9), zones[0], zones, strokeTimestamp = 0)
        assertEquals(zones[0].targetSpm, onTenth.checkPaceTargetSpm)

        // stroke 3 -> newStroke 4
        val offTenth = computeStrokeProgression(session(stroke = 3), zones[0], zones, strokeTimestamp = 0)
        assertEquals(null, offTenth.checkPaceTargetSpm)
    }

    // --- Canoe Speed announce (ADR-0018) ---

    @Test fun `announces speed on the third-to-last finish of a High set`() {
        val zone = Zone(strokes = 10, sets = 2, level = ZoneLevel.High)
        // currentStroke 7 -> newStroke 8 == strokes(10) - 2
        val session = TrainingSessionState(currentStroke = 7, currentSet = 1)
        val result = computeStrokeProgression(session, zone, listOf(zone), strokeTimestamp = 1000)
        assertTrue(result.announceSpeed)
    }

    @Test fun `does not announce on other strokes of a High set`() {
        val zone = Zone(strokes = 10, sets = 2, level = ZoneLevel.High)
        val session = TrainingSessionState(currentStroke = 5, currentSet = 1) // newStroke 6
        assertFalse(computeStrokeProgression(session, zone, listOf(zone), 1000).announceSpeed)
    }

    @Test fun `does not announce in Low or Medium zones`() {
        val low = Zone(strokes = 10, sets = 1, level = ZoneLevel.Low)
        val med = Zone(strokes = 10, sets = 1, level = ZoneLevel.Medium)
        val session = TrainingSessionState(currentStroke = 7, currentSet = 1) // newStroke 8
        assertFalse(computeStrokeProgression(session, low, listOf(low), 1000).announceSpeed)
        assertFalse(computeStrokeProgression(session, med, listOf(med), 1000).announceSpeed)
    }

    @Test fun `skips the announce on a High set shorter than three strokes`() {
        val zone = Zone(strokes = 2, sets = 1, level = ZoneLevel.High)
        val s0 = TrainingSessionState(currentStroke = 0, currentSet = 1) // newStroke 1
        assertFalse(computeStrokeProgression(s0, zone, listOf(zone), 1000).announceSpeed)
    }

    @Test fun `does not announce on the second-to-last finish of a High set`() {
        val zone = Zone(strokes = 10, sets = 2, level = ZoneLevel.High)
        // currentStroke 8 -> newStroke 9 == strokes - 1 (one too late, still mid-set)
        val session = TrainingSessionState(currentStroke = 8, currentSet = 1)
        assertFalse(computeStrokeProgression(session, zone, listOf(zone), 1000).announceSpeed)
    }

    // --- SPM helper ---

    @Test
    fun `calculateSpm returns zero with fewer than two samples`() {
        assertEquals(0, calculateSpm(emptyList()))
        assertEquals(0, calculateSpm(listOf(1000L)))
    }

    @Test
    fun `calculateSpm converts a one-second cadence to 60 spm`() {
        assertEquals(60, calculateSpm(listOf(0L, 1000L, 2000L, 3000L)))
    }
}
