package com.orotrain.oro.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val startTime: Long,
    val endTime: Long,
    val totalStrokes: Int,
    val avgSpm: Int,
    val avgPeakAccel: Float,
    val avgDriveRatio: Float,
    val avgFsrPercent: Int,
    val avgConsistencyScore: Float,
    val avgPowerScore: Float,
    val fatigueIndex: Float,
    val zoneCount: Int,
    val durationMs: Long
)
