package com.orotrain.oro.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OroUiStateLockTest {

    private fun stateWith(status: TrainingStatus) =
        OroUiState(trainingSession = TrainingSessionState(status = status))

    @Test
    fun `not locked when idle`() {
        assertFalse(stateWith(TrainingStatus.Idle).isNavigationLocked)
    }

    @Test
    fun `not locked when completed`() {
        assertFalse(stateWith(TrainingStatus.Completed).isNavigationLocked)
    }

    @Test
    fun `locked while a session is live`() {
        assertTrue(stateWith(TrainingStatus.Starting).isNavigationLocked)
        assertTrue(stateWith(TrainingStatus.Standby).isNavigationLocked)
        assertTrue(stateWith(TrainingStatus.Active).isNavigationLocked)
        assertTrue(stateWith(TrainingStatus.Paused).isNavigationLocked)
    }
}
