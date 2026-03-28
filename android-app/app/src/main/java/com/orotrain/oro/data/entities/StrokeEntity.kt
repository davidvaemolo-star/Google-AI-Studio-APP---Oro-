package com.orotrain.oro.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val strokeNumber: Int,
    val timestamp: Long,
    val peakAccelG: Float,
    val driveDurationMs: Int,
    val recoveryDurationMs: Int,
    val driveRatio: Float,
    val fsrPeakPercent: Int,
    val powerScore: Float
)
