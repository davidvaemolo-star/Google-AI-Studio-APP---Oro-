package com.orotrain.oro.ble

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for the Speed Announce wire encoding (BLE_PROTOCOL §1.11, ADR-0018). */
class SpeedAnnounceCodecTest {

    @Test fun `encodes speed as km per h times ten little-endian`() {
        val bytes = SpeedAnnounceCodec.encode(speedKmh = 14.3f, startDelayMs = 300, volume = 90)
        assertEquals(4, bytes.size)
        assertEquals(143, (bytes[0].toInt() and 0xFF))   // LSB of 143
        assertEquals(0, (bytes[1].toInt() and 0xFF))     // MSB
        assertEquals(30, (bytes[2].toInt() and 0xFF))    // 300ms / 10
        assertEquals(90, (bytes[3].toInt() and 0xFF))
    }

    @Test fun `rounds to one decimal`() {
        // 14.35 km/h -> 143.5 tenths; roundToInt() -> 144 (truncation would give 143)
        val bytes = SpeedAnnounceCodec.encode(speedKmh = 14.35f, startDelayMs = 0, volume = 100)
        assertEquals(144, (bytes[0].toInt() and 0xFF))
    }

    @Test fun `clamps speed above the speakable maximum`() {
        // MAX_SPEAKABLE_KMH = 29.9 -> 299 tenths = 0x012B
        val bytes = SpeedAnnounceCodec.encode(speedKmh = 99f, startDelayMs = 0, volume = 100)
        assertEquals(0x2B, (bytes[0].toInt() and 0xFF))
        assertEquals(0x01, (bytes[1].toInt() and 0xFF))
    }

    @Test fun `floors negative delay and clamps loud volume`() {
        val bytes = SpeedAnnounceCodec.encode(speedKmh = 12f, startDelayMs = -100, volume = 150)
        assertEquals(0, (bytes[2].toInt() and 0xFF))
        assertEquals(100, (bytes[3].toInt() and 0xFF))
    }

    @Test fun `clamps an over-long delay to 255 units`() {
        val bytes = SpeedAnnounceCodec.encode(speedKmh = 12f, startDelayMs = 5000, volume = 50)
        assertEquals(255, (bytes[2].toInt() and 0xFF))   // 500 units clamped
    }
}
