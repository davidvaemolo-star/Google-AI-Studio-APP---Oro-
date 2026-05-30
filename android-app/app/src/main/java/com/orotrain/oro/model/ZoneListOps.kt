package com.orotrain.oro.model

/**
 * Pure transformations on a list of [Zone]s.
 *
 * These are the single source of truth for zone editing. Both the active session's
 * zones ([OroUiState.zones]) and a saved Programme's zones are edited through these
 * functions, so the MAX_ZONES guard, the index math, and the value clamps live in
 * exactly one place. Each function returns a new list and leaves the input unchanged
 * if the operation cannot apply (list full, zone id not found, indices out of range).
 */

/** Appends a new empty zone, unless the list is already at [MAX_ZONES]. */
fun List<Zone>.withAddedZone(): List<Zone> =
    if (size >= MAX_ZONES) this else this + Zone()

/** Inserts a new empty zone directly after [zoneId]. */
fun List<Zone>.withZoneAddedAfter(zoneId: String): List<Zone> {
    if (size >= MAX_ZONES) return this
    val index = indexOfFirst { it.id == zoneId }
    if (index == -1) return this
    return toMutableList().also { it.add(index + 1, Zone()) }
}

/** Inserts a copy of [zoneId] (with a fresh id) directly after it. */
fun List<Zone>.withZoneDuplicated(zoneId: String): List<Zone> {
    if (size >= MAX_ZONES) return this
    val index = indexOfFirst { it.id == zoneId }
    if (index == -1) return this
    val copy = this[index].copy(id = Zone().id)
    return toMutableList().also { it.add(index + 1, copy) }
}

/** Removes [zoneId] if present. */
fun List<Zone>.withZoneRemoved(zoneId: String): List<Zone> =
    filterNot { it.id == zoneId }

/** Applies a clamped [delta] to [field] of [zoneId]. */
fun List<Zone>.withZoneAdjusted(zoneId: String, field: ZoneField, delta: Int): List<Zone> =
    map { zone ->
        if (zone.id != zoneId) return@map zone
        when (field) {
            ZoneField.Strokes -> zone.copy(strokes = (zone.strokes + delta).coerceIn(MIN_VALUE, MAX_STROKES))
            ZoneField.Sets -> zone.copy(sets = (zone.sets + delta).coerceIn(MIN_VALUE, MAX_SETS))
            ZoneField.Level -> {
                val levels = ZoneLevel.values()
                val newIndex = (levels.indexOf(zone.level) + delta).coerceIn(0, levels.size - 1)
                zone.copy(level = levels[newIndex])
            }
        }
    }

/** Sets the intensity [level] of [zoneId]. */
fun List<Zone>.withZoneLevel(zoneId: String, level: ZoneLevel): List<Zone> =
    map { if (it.id == zoneId) it.copy(level = level) else it }

/** Moves the zone at [fromIndex] to [toIndex]. */
fun List<Zone>.withZoneMoved(fromIndex: Int, toIndex: Int): List<Zone> {
    if (fromIndex == toIndex) return this
    if (fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().also { it.add(toIndex, it.removeAt(fromIndex)) }
}
