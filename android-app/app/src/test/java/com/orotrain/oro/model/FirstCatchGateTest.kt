package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Standby->Active gate (ADR-0017): the Pacer's first Catch only starts the session once a
 * Top Hand Pressure rise confirms it within a short window, so a stray bump during line-up
 * doesn't start training.
 */
class FirstCatchGateTest {

    private val window = 400L
    private val threshold = 30

    @Test
    fun `pressure before any catch never confirms`() {
        val gate = FirstCatchGate()
        val result = gate.onPacerPressure(pressurePercent = 90, phoneNowMs = 1000, windowMs = window, thresholdPercent = threshold)

        assertTrue(result is FirstCatchResult.Pending)
        assertNull(result.gate.candidatePhoneMs)
    }

    @Test
    fun `catch then a strong pressure rise within the window confirms, backdated to the catch`() {
        val armed = FirstCatchGate().onPacerCatch(phoneNowMs = 1000)
        val result = armed.onPacerPressure(pressurePercent = 45, phoneNowMs = 1250, windowMs = window, thresholdPercent = threshold)

        assertTrue(result is FirstCatchResult.Confirmed)
        assertEquals(1000L, (result as FirstCatchResult.Confirmed).startTimePhoneMs)
    }

    @Test
    fun `catch then weak pressure within the window keeps waiting`() {
        val armed = FirstCatchGate().onPacerCatch(phoneNowMs = 1000)
        val result = armed.onPacerPressure(pressurePercent = 10, phoneNowMs = 1100, windowMs = window, thresholdPercent = threshold)

        assertTrue(result is FirstCatchResult.Pending)
        // candidate still armed so a later rise can still confirm
        assertEquals(1000L, result.gate.candidatePhoneMs)
    }

    @Test
    fun `a strong pressure rise after the window lapses does not confirm and drops the stale candidate`() {
        val armed = FirstCatchGate().onPacerCatch(phoneNowMs = 1000)
        val result = armed.onPacerPressure(pressurePercent = 90, phoneNowMs = 1500, windowMs = window, thresholdPercent = threshold)

        assertTrue(result is FirstCatchResult.Pending)
        assertNull(result.gate.candidatePhoneMs)
    }

    @Test
    fun `a second catch refreshes the candidate to the newer one`() {
        val armed = FirstCatchGate().onPacerCatch(phoneNowMs = 1000).onPacerCatch(phoneNowMs = 5000)
        // the 400ms window is measured from the newer catch
        val result = armed.onPacerPressure(pressurePercent = 50, phoneNowMs = 5300, windowMs = window, thresholdPercent = threshold)

        assertTrue(result is FirstCatchResult.Confirmed)
        assertEquals(5000L, (result as FirstCatchResult.Confirmed).startTimePhoneMs)
    }

    @Test
    fun `confirming clears the candidate so it cannot confirm twice`() {
        val armed = FirstCatchGate().onPacerCatch(phoneNowMs = 1000)
        val confirmed = armed.onPacerPressure(pressurePercent = 50, phoneNowMs = 1100, windowMs = window, thresholdPercent = threshold)

        assertNull(confirmed.gate.candidatePhoneMs)
        // a further pressure sample on the post-confirm gate just keeps waiting, no re-confirm
        val again = confirmed.gate.onPacerPressure(pressurePercent = 90, phoneNowMs = 1150, windowMs = window, thresholdPercent = threshold)
        assertTrue(again is FirstCatchResult.Pending)
    }
}
