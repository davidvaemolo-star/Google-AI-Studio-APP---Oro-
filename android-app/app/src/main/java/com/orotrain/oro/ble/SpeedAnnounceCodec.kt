package com.orotrain.oro.ble

import com.orotrain.oro.model.CanoeSpeedConfig
import kotlin.math.roundToInt

/**
 * Encodes a single Canoe Speed call-out into the Speed Announce wire format
 * (BLE_PROTOCOL §1.11, ADR-0018): [speed x10 LE (2 bytes), startDelay/10, volume].
 * Pure byte-packing — no Android deps, mirrors [RollCallCodec].
 */
object SpeedAnnounceCodec {
    /** [speedKmh] is clamped to the speakable range; [startDelayMs] is sent in tens of ms. */
    fun encode(speedKmh: Float, startDelayMs: Int, volume: Int): ByteArray {
        val tenths = (speedKmh.coerceIn(0f, CanoeSpeedConfig.MAX_SPEAKABLE_KMH) * 10f)
            .roundToInt().coerceIn(0, 0xFFFF)
        val delayUnits = (startDelayMs / 10).coerceIn(0, 255)
        return byteArrayOf(
            (tenths and 0xFF).toByte(),
            (tenths shr 8).toByte(),
            delayUnits.toByte(),
            volume.coerceIn(0, 100).toByte()
        )
    }
}
