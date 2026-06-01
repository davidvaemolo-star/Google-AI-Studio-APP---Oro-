package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for the redesigned Sync Score computation (ADR-0015): clock alignment, nearest-catch
 * pairing, and absolute-gap scoring.
 *
 * CatchSample fields: deviceTimestampMs = device's own clock (real time + a per-device bias),
 * phoneReceiveMs = phone clock (real time + Bluetooth latency).
 */
class SyncComputerTest {

    @Test fun `perfectly synced followers score ~0 gap despite different device clocks`() {
        // Pacer clock unbiased; follower clock 100000ms ahead. Both catch at the same real times
        // (1000/2000/3000) with constant 10ms BLE latency. Clock alignment must cancel the bias.
        val samples = listOf(
            CatchSample("P", 1000, 1010), CatchSample("P", 2000, 2010), CatchSample("P", 3000, 3010),
            CatchSample("F", 101000, 1010), CatchSample("F", 102000, 2010), CatchSample("F", 103000, 3010)
        )
        val gap = SyncComputer.crewAverageGapMs(samples, pacerId = "P")
        assertNotNull(gap)
        assertEquals(0.0, gap!!, 0.001)
    }

    @Test fun `follower rushing 150ms ahead is penalized by the absolute gap`() {
        // Follower catches 150ms BEFORE the pacer each stroke. Old code clamped this to "perfect";
        // now it must score as 150ms out of sync.
        val samples = listOf(
            CatchSample("P", 1000, 1010), CatchSample("P", 2000, 2010), CatchSample("P", 3000, 3010),
            CatchSample("F", 850, 860), CatchSample("F", 1850, 1860), CatchSample("F", 2850, 2860)
        )
        val gap = SyncComputer.crewAverageGapMs(samples, pacerId = "P")
        assertNotNull(gap)
        assertEquals(150.0, gap!!, 0.001)
    }

    @Test fun `follower lagging 100ms behind scores 100ms gap`() {
        val samples = listOf(
            CatchSample("P", 1000, 1010), CatchSample("P", 2000, 2010), CatchSample("P", 3000, 3010),
            CatchSample("F", 1100, 1110), CatchSample("F", 2100, 2110), CatchSample("F", 3100, 3110)
        )
        val gap = SyncComputer.crewAverageGapMs(samples, pacerId = "P")
        assertNotNull(gap)
        assertEquals(100.0, gap!!, 0.001)
    }

    @Test fun `follower catch with no pacer in window counts as max gap`() {
        // Follower catch is 500ms from the only pacer catch — outside the +-400ms window.
        val samples = listOf(
            CatchSample("P", 1000, 1010),
            CatchSample("F", 1500, 1510)
        )
        val gap = SyncComputer.crewAverageGapMs(samples, pacerId = "P")
        assertNotNull(gap)
        assertEquals(SyncComputer.MAX_GAP_MS, gap!!, 0.001)
    }

    @Test fun `single device with no followers is Not Measured`() {
        val samples = listOf(
            CatchSample("P", 1000, 1010), CatchSample("P", 2000, 2010)
        )
        assertNull(SyncComputer.crewAverageGapMs(samples, pacerId = "P"))
    }

    @Test fun `null pacer id is Not Measured`() {
        val samples = listOf(CatchSample("F", 1000, 1010))
        assertNull(SyncComputer.crewAverageGapMs(samples, pacerId = null))
    }
}
