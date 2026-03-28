package com.orotrain.oro.data

import android.util.Log
import com.orotrain.oro.analysis.StrokeAnalyzer
import com.orotrain.oro.data.entities.SessionEntity
import com.orotrain.oro.data.entities.StrokeEntity
import com.orotrain.oro.model.TrainingSessionState
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepository(private val dao: SessionDao) {

    companion object {
        private const val TAG = "SessionRepository"
    }

    /**
     * Save a completed training session with all stroke data.
     * Returns the session ID.
     */
    suspend fun saveSession(
        analyzer: StrokeAnalyzer,
        sessionState: TrainingSessionState
    ): String {
        val metrics = analyzer.metrics.value
        val allStrokes = analyzer.getAllStrokes()

        if (allStrokes.isEmpty()) {
            Log.w(TAG, "No strokes to save")
            return ""
        }

        val sessionId = UUID.randomUUID().toString()

        val session = SessionEntity(
            id = sessionId,
            startTime = sessionState.startTimeMillis ?: System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            totalStrokes = allStrokes.size,
            avgSpm = metrics.currentSpm,
            avgPeakAccel = metrics.avgPeakAccel,
            avgDriveRatio = metrics.avgDriveRatio,
            avgFsrPercent = metrics.avgFsrPercent,
            avgConsistencyScore = metrics.consistencyScore,
            avgPowerScore = metrics.powerScore,
            fatigueIndex = metrics.fatigueIndex,
            zoneCount = sessionState.currentZoneIndex + 1,
            durationMs = sessionState.elapsedTimeMillis
        )

        val strokeEntities = allStrokes.map { s ->
            StrokeEntity(
                sessionId = sessionId,
                strokeNumber = s.strokeNumber,
                timestamp = s.timestamp,
                peakAccelG = s.peakAccelG,
                driveDurationMs = s.driveDurationMs,
                recoveryDurationMs = s.recoveryDurationMs,
                driveRatio = s.driveRatio,
                fsrPeakPercent = s.fsrPeakPercent,
                powerScore = s.powerScore
            )
        }

        dao.insertSession(session)
        dao.insertStrokes(strokeEntities)

        Log.d(TAG, "Saved session $sessionId: ${allStrokes.size} strokes, " +
            "SPM=${metrics.currentSpm}, consistency=${"%.0f".format(metrics.consistencyScore)}")

        return sessionId
    }

    fun getSessionHistory(): Flow<List<SessionEntity>> = dao.getAllSessions()

    fun getRecentSessions(count: Int = 10): Flow<List<SessionEntity>> = dao.getRecentSessions(count)

    fun getSessionDetail(sessionId: String): Flow<List<StrokeEntity>> =
        dao.getStrokesForSession(sessionId)

    suspend fun getSessionCount(): Int = dao.getSessionCount()
}
