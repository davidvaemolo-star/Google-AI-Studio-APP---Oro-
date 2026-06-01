package com.orotrain.oro.model

/**
 * Named bracket for the crew's overall Sync Score. See CONTEXT.md "Sync Rating".
 */
enum class SyncRating {
    Poor, Good, Excellent;

    companion object {
        fun fromScore(syncScore: Int): SyncRating = when {
            syncScore >= 80 -> Excellent
            syncScore >= 50 -> Good
            else -> Poor
        }
    }
}
