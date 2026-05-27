package com.orotrain.oro.model

/**
 * Named bucket for Peak Pressure averaged across a session. See CONTEXT.md "Power Range".
 */
enum class PowerRange {
    Light, Moderate, Strong, Maximum;

    companion object {
        fun fromPeakPercent(percent: Int): PowerRange = when {
            percent >= 76 -> Maximum
            percent >= 51 -> Strong
            percent >= 26 -> Moderate
            else -> Light
        }
    }
}
