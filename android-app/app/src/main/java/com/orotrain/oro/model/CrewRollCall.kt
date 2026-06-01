package com.orotrain.oro.model

/**
 * One device's place in the read-out order: which Canoe + Seat it occupies (canoe 0 = single-canoe
 * session). Supplied by the caller because seat assignment lives outside the scoring.
 */
data class RosterSeat(
    val deviceId: String,
    val canoe: Int,
    val seat: Int
)

/**
 * One seat's line in the Crew Roll-Call (ADR-0016). [syncScore]/[syncRating] are null when the
 * seat's sync is Not Measured — the Pacer (its own Catch is the reference everyone is measured
 * against) or a Follower with no comparable Catch (ADR-0015). [powerRange] is always present.
 */
data class SeatScore(
    val deviceId: String,
    val canoe: Int,
    val seat: Int,
    val isPacer: Boolean,
    val syncScore: Int?,
    val syncRating: SyncRating?,
    val powerRange: PowerRange
)

/**
 * The computed end-of-session Crew Roll-Call (ADR-0016): the crew-wide Sync Rating plus one
 * [SeatScore] per device, in the order they should be read out. Pure data — knows nothing about
 * audio prompts, BLE, or screens.
 */
data class CrewRollCall(
    val crewSyncScore: Int?,
    val crewSyncRating: SyncRating?,
    val seats: List<SeatScore>
) {
    companion object {
        /**
         * Builds the Roll-Call from the session's recorded Catches and per-device pressure.
         *
         * @param roster devices in the order they should be read out (by Canoe + Seat), with the
         *        [pacerId] device expected first.
         * @param devicePressurePercents each device's recorded Top Hand Pressure %s over the
         *        session; an absent or empty entry yields the lowest Power Range.
         */
        fun compute(
            catchSamples: List<CatchSample>,
            pacerId: String?,
            roster: List<RosterSeat>,
            devicePressurePercents: Map<String, List<Int>>
        ): CrewRollCall {
            val crewScore = SessionOutcome.scoreForGap(SyncComputer.crewAverageGapMs(catchSamples, pacerId))
            val perDeviceGap = SyncComputer.perDeviceAverageGapMs(catchSamples, pacerId)

            val seats = roster.map { rosterSeat ->
                val id = rosterSeat.deviceId
                val isPacer = id == pacerId
                // The Pacer is the reference, so it has no Sync Score. A Follower scores from its
                // own gap, or is Not Measured (null) when it has no gap (ADR-0015).
                val syncScore = if (isPacer) null else SessionOutcome.scoreForGap(perDeviceGap[id])
                val pressures = devicePressurePercents[id].orEmpty()
                val avgPeak = if (pressures.isEmpty()) 0 else pressures.average().toInt()
                SeatScore(
                    deviceId = id,
                    canoe = rosterSeat.canoe,
                    seat = rosterSeat.seat,
                    isPacer = isPacer,
                    syncScore = syncScore,
                    syncRating = syncScore?.let { SyncRating.fromScore(it) },
                    powerRange = PowerRange.fromPeakPercent(avgPeak)
                )
            }
            return CrewRollCall(
                crewSyncScore = crewScore,
                crewSyncRating = crewScore?.let { SyncRating.fromScore(it) },
                seats = seats
            )
        }
    }
}
