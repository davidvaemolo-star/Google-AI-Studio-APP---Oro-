package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionStateTest {

    @Test
    fun `Standby counts as an underway session`() {
        val state = TrainingSessionState(status = TrainingStatus.Standby)
        assertTrue(state.isActive)
    }

    @Test
    fun `Idle is not underway`() {
        val state = TrainingSessionState(status = TrainingStatus.Idle)
        assertFalse(state.isActive)
    }

    @Test
    fun `Standby has zero elapsed time because the clock has not started`() {
        // In Standby the session clock is not set until the Pacer's first Catch (ADR-0017).
        val state = TrainingSessionState(status = TrainingStatus.Standby, startTimeMillis = null)
        assertEquals(0L, state.elapsedTimeMillis)
    }
}
