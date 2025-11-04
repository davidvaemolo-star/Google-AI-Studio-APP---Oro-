package com.orotrain.oro.model

import java.util.UUID

enum class DeviceStatus {
    Disconnected,
    Connecting,
    Connected
}

data class Zone(
    val id: String = UUID.randomUUID().toString(),
    val strokes: Int = 10,
    val sets: Int = 1,
    val level: ZoneLevel = ZoneLevel.Low
) {
    val zoneColor: Byte
        get() = when (level) {
            ZoneLevel.Low -> 0x01    // Green
            ZoneLevel.Medium -> 0x02 // Orange
            ZoneLevel.High -> 0x03   // Red
        }

    // Get representative SPM for the zone level (used for display and device config)
    val targetSpm: Int
        get() = when (level) {
            ZoneLevel.Low -> 38      // Mid-point of 30-45
            ZoneLevel.Medium -> 53   // Mid-point of 46-60
            ZoneLevel.High -> 70     // Mid-point of 61-80
        }

    val spmRange: String
        get() = when (level) {
            ZoneLevel.Low -> "30-45 SPM"
            ZoneLevel.Medium -> "46-60 SPM"
            ZoneLevel.High -> "61-80 SPM"
        }

    // For backward compatibility with existing code
    val spm: Int get() = targetSpm
}

enum class ZoneField {
    Strokes,
    Sets,
    Level
}

data class HapticDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val status: DeviceStatus = DeviceStatus.Disconnected,
    val seat: Int? = null,
    val batteryLevel: Int? = null,
    val isCalibrating: Boolean = false,
    val strokeThreshold: Float? = null,
    val strokeCount: Int = 0,
    val lastStrokePhase: Byte? = null
)

enum class AppDestination {
    Connection,
    Training
}

enum class TrainingStatus {
    Idle,
    Starting,
    Active,
    Paused,
    Completed
}

enum class ZoneLevel {
    Low,    // Green: 30-45 SPM (Recovery/Endurance)
    Medium, // Orange: 46-60 SPM (Tempo)
    High    // Red: 61-80 SPM (Threshold/VO2Max)
}

data class TrainingSessionState(
    val status: TrainingStatus = TrainingStatus.Idle,
    val currentZoneIndex: Int = 0,
    val currentStroke: Int = 0,
    val currentSet: Int = 0,
    val startTimeMillis: Long? = null,
    val pausedTimeMillis: Long? = null,
    val totalPausedDuration: Long = 0,
    val recentStrokeTimestamps: List<Long> = emptyList(), // For SPM calculation
    val currentSpm: Int = 0,
    val syncQuality: Map<String, Int> = emptyMap(), // deviceId -> latency in ms
    val errorMessage: String? = null
) {
    val elapsedTimeMillis: Long
        get() {
            val start = startTimeMillis ?: return 0
            val now = if (status == TrainingStatus.Paused) {
                pausedTimeMillis ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
            return (now - start) - totalPausedDuration
        }

    val isActive: Boolean
        get() = status == TrainingStatus.Active || status == TrainingStatus.Paused
}
