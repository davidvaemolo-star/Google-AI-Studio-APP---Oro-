package com.orotrain.oro.model

/**
 * Assigns [newSeat] to the device with [deviceId].
 * If another connected device already holds [newSeat], it takes [deviceId]'s old seat (swap).
 * Returns the updated list unchanged if [deviceId] is not found.
 */
internal fun swapSeats(
    devices: List<HapticDevice>,
    deviceId: String,
    newSeat: Int
): List<HapticDevice> {
    val result = devices.toMutableList()
    val targetIdx = result.indexOfFirst { it.id == deviceId }
    if (targetIdx < 0) return devices

    val oldSeat = result[targetIdx].seat
    val conflictIdx = result.indexOfFirst { it.seat == newSeat && it.id != deviceId }
    if (conflictIdx >= 0) {
        result[conflictIdx] = result[conflictIdx].copy(seat = oldSeat)
    }
    result[targetIdx] = result[targetIdx].copy(seat = newSeat)
    return result
}

/**
 * Auto-assigns seats to connected devices that have none (seat == null).
 * Preserves existing seat assignments. Fills lowest available seat numbers,
 * ordered by device name numeric suffix (Oro-01 < Oro-02 etc.).
 * Updates are applied in-place; non-connected devices have their seat cleared.
 */
internal fun autoAssignSeats(devices: List<HapticDevice>): List<HapticDevice> {
    val connected = devices.filter { it.status == DeviceStatus.Connected }
    val seated = connected.filter { it.seat != null }
    val unseated = connected
        .filter { it.seat == null }
        .sortedBy { device ->
            val match = Regex("""(\d+)""").find(device.name)
            match?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
        }

    val usedSeats = seated.map { it.seat!! }.toSet()
    val available = (1..connected.size).filter { it !in usedSeats }

    val newlySeated = unseated.zip(available).map { (device, seat) ->
        device.copy(seat = seat)
    }

    val allSeated = (seated + newlySeated).sortedBy { it.seat }
    val connectedIds = allSeated.map { it.id }.toSet()
    val others = devices
        .filter { it.id !in connectedIds }
        .map { it.copy(seat = null) }
    return allSeated + others
}
