package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests for the pure Canoe Speed smoother (ADR-0018). window = 1500 ms. */
class CanoeSpeedTest {

    @Test fun `no samples means no fix`() {
        assertNull(SpeedSmoother(windowMs = 1500).smoothed(nowMs = 1000))
    }

    @Test fun `a single fresh sample returns itself`() {
        val s = SpeedSmoother(windowMs = 1500)
        s.addSample(timeMs = 1000, kmh = 14.0f)
        assertEquals(14.0f, s.smoothed(nowMs = 1200)!!, 0.001f)
    }

    @Test fun `averages samples inside the window`() {
        val s = SpeedSmoother(windowMs = 1500)
        s.addSample(timeMs = 1000, kmh = 12.0f)
        s.addSample(timeMs = 1500, kmh = 14.0f)
        s.addSample(timeMs = 2000, kmh = 16.0f)
        assertEquals(14.0f, s.smoothed(nowMs = 2000)!!, 0.001f)
    }

    @Test fun `samples older than the window are dropped`() {
        val s = SpeedSmoother(windowMs = 1500)
        s.addSample(timeMs = 1000, kmh = 2.0f)   // 1600 ms before now -> dropped
        s.addSample(timeMs = 2500, kmh = 18.0f)
        assertEquals(18.0f, s.smoothed(nowMs = 2600)!!, 0.001f)
    }

    @Test fun `a stale latest sample reads as no fix`() {
        val s = SpeedSmoother(windowMs = 1500)
        s.addSample(timeMs = 1000, kmh = 15.0f)
        assertNull(s.smoothed(nowMs = 5000))     // 4 s later, GPS lost -> no fix
    }

    @Test fun `a sample exactly at the window edge is kept`() {
        val s = SpeedSmoother(windowMs = 1500)
        s.addSample(timeMs = 1000, kmh = 13.0f)
        assertEquals(13.0f, s.smoothed(nowMs = 2500)!!, 0.001f)  // gap == windowMs, not older
    }
}
