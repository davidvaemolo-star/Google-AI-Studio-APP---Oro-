package com.orotrain.oro.ble

import com.orotrain.oro.model.CrewRollCall
import com.orotrain.oro.model.PowerRange
import com.orotrain.oro.model.SeatScore
import com.orotrain.oro.model.SyncRating

/**
 * Encodes a [CrewRollCall] into the Roll-Call Control wire format (BLE_PROTOCOL §1.10, ADR-0016):
 * a LOAD payload carrying the whole roster, and a PLAY trigger. Pure byte-packing — no Android deps.
 */
object RollCallCodec {
    const val CMD_CLEAR: Byte = 0x00
    const val CMD_LOAD: Byte = 0x01
    const val CMD_PLAY: Byte = 0x02

    private const val PACER_BIT = 0x80

    /** LOAD: [CMD_LOAD, crewRating, seatCount, (canoe, seat|pacerBit, power<<4|sync) × N]. */
    fun encodeLoad(rollCall: CrewRollCall): ByteArray {
        val out = ArrayList<Byte>(3 + rollCall.seats.size * 3)
        out.add(CMD_LOAD)
        out.add(ratingCode(rollCall.crewSyncRating).toByte())
        out.add(rollCall.seats.size.toByte())
        for (seat in rollCall.seats) {
            out.add(seat.canoe.toByte())
            out.add(seatByte(seat))
            out.add(scoreByte(seat))
        }
        return out.toByteArray()
    }

    /** PLAY: [CMD_PLAY, startDelay in tens of ms (0–255), volume (0–100)]. */
    fun encodePlay(startDelayMs: Int, volume: Int): ByteArray = byteArrayOf(
        CMD_PLAY,
        (startDelayMs / 10).coerceIn(0, 255).toByte(),
        volume.coerceIn(0, 100).toByte()
    )

    private fun seatByte(seat: SeatScore): Byte {
        val pacer = if (seat.isPacer) PACER_BIT else 0
        return ((seat.seat and 0x7F) or pacer).toByte()
    }

    private fun scoreByte(seat: SeatScore): Byte =
        ((powerCode(seat.powerRange) shl 4) or ratingCode(seat.syncRating)).toByte()

    /** Sync Rating wire code; null (Not Measured) → 0. */
    private fun ratingCode(rating: SyncRating?): Int = when (rating) {
        null -> 0
        SyncRating.Poor -> 1
        SyncRating.Good -> 2
        SyncRating.Excellent -> 3
    }

    private fun powerCode(power: PowerRange): Int = when (power) {
        PowerRange.Light -> 1
        PowerRange.Moderate -> 2
        PowerRange.Strong -> 3
        PowerRange.Maximum -> 4
    }
}
