package com.orotrain.oro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.orotrain.oro.data.entities.SessionEntity
import com.orotrain.oro.data.entities.StrokeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity)

    @Insert
    suspend fun insertStrokes(strokes: List<StrokeEntity>)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :n")
    fun getRecentSessions(n: Int = 10): Flow<List<SessionEntity>>

    @Query("SELECT * FROM strokes WHERE sessionId = :sessionId ORDER BY strokeNumber")
    fun getStrokesForSession(sessionId: String): Flow<List<StrokeEntity>>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getSessionCount(): Int
}
