package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneListOpsTest {

    private fun zones(n: Int): List<Zone> = List(n) { Zone(strokes = it + 1) }

    // --- withAddedZone ---

    @Test
    fun `withAddedZone appends one zone`() {
        val result = zones(2).withAddedZone()
        assertEquals(3, result.size)
    }

    @Test
    fun `withAddedZone is a no-op at MAX_ZONES`() {
        val full = zones(MAX_ZONES)
        val result = full.withAddedZone()
        assertEquals(MAX_ZONES, result.size)
    }

    // --- withZoneAddedAfter ---

    @Test
    fun `withZoneAddedAfter inserts directly after the target`() {
        val list = zones(3)
        val result = list.withZoneAddedAfter(list[0].id)
        assertEquals(4, result.size)
        // original first zone stays at index 0; new (default) zone is at index 1
        assertEquals(list[0].id, result[0].id)
        assertEquals(list[1].id, result[2].id)
    }

    @Test
    fun `withZoneAddedAfter is a no-op for unknown id`() {
        val list = zones(2)
        assertEquals(list, list.withZoneAddedAfter("nope"))
    }

    @Test
    fun `withZoneAddedAfter is a no-op at MAX_ZONES`() {
        val full = zones(MAX_ZONES)
        assertEquals(MAX_ZONES, full.withZoneAddedAfter(full[0].id).size)
    }

    // --- withZoneDuplicated ---

    @Test
    fun `withZoneDuplicated inserts a copy with a fresh id after the target`() {
        val list = zones(2)
        val result = list.withZoneDuplicated(list[0].id)
        assertEquals(3, result.size)
        // copy sits at index 1, shares data but not id
        assertEquals(list[0].strokes, result[1].strokes)
        assertNotEquals(list[0].id, result[1].id)
    }

    @Test
    fun `withZoneDuplicated is a no-op at MAX_ZONES`() {
        val full = zones(MAX_ZONES)
        assertEquals(MAX_ZONES, full.withZoneDuplicated(full[0].id).size)
    }

    // --- withZoneRemoved ---

    @Test
    fun `withZoneRemoved drops the target`() {
        val list = zones(3)
        val result = list.withZoneRemoved(list[1].id)
        assertEquals(2, result.size)
        assertTrue(result.none { it.id == list[1].id })
    }

    // --- withZoneAdjusted ---

    @Test
    fun `withZoneAdjusted clamps strokes to MAX_STROKES`() {
        val list = listOf(Zone(strokes = MAX_STROKES))
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Strokes, delta = 5)
        assertEquals(MAX_STROKES, result[0].strokes)
    }

    @Test
    fun `withZoneAdjusted clamps strokes to MIN_VALUE`() {
        val list = listOf(Zone(strokes = MIN_VALUE))
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Strokes, delta = -5)
        assertEquals(MIN_VALUE, result[0].strokes)
    }

    @Test
    fun `withZoneAdjusted clamps sets to MAX_SETS`() {
        val list = listOf(Zone(sets = MAX_SETS))
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Sets, delta = 3)
        assertEquals(MAX_SETS, result[0].sets)
    }

    @Test
    fun `withZoneAdjusted steps level up without overrunning the enum`() {
        val list = listOf(Zone(level = ZoneLevel.High))
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Level, delta = 1)
        assertEquals(ZoneLevel.High, result[0].level)
    }

    @Test
    fun `withZoneAdjusted steps level down without underrunning the enum`() {
        val list = listOf(Zone(level = ZoneLevel.Low))
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Level, delta = -1)
        assertEquals(ZoneLevel.Low, result[0].level)
    }

    @Test
    fun `withZoneAdjusted leaves non-target zones untouched`() {
        val list = zones(2)
        val result = list.withZoneAdjusted(list[0].id, ZoneField.Strokes, delta = 1)
        assertEquals(list[1].strokes, result[1].strokes)
    }

    // --- withZoneLevel ---

    @Test
    fun `withZoneLevel sets the level of the target only`() {
        val list = listOf(Zone(level = ZoneLevel.Low), Zone(level = ZoneLevel.Low))
        val result = list.withZoneLevel(list[0].id, ZoneLevel.High)
        assertEquals(ZoneLevel.High, result[0].level)
        assertEquals(ZoneLevel.Low, result[1].level)
    }

    // --- withZoneMoved ---

    @Test
    fun `withZoneMoved reorders the list`() {
        val list = zones(3)
        val result = list.withZoneMoved(fromIndex = 0, toIndex = 2)
        assertEquals(list[0].id, result[2].id)
        assertEquals(list[1].id, result[0].id)
    }

    @Test
    fun `withZoneMoved returns same instance when indices match`() {
        val list = zones(3)
        assertSame(list, list.withZoneMoved(1, 1))
    }

    @Test
    fun `withZoneMoved is a no-op for out-of-range indices`() {
        val list = zones(2)
        assertEquals(list, list.withZoneMoved(0, 5))
    }
}
