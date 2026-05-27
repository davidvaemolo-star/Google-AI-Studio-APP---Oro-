package com.orotrain.oro.audio

import com.orotrain.oro.ble.BleManager
import com.orotrain.oro.model.PowerRange
import com.orotrain.oro.model.SessionOutcome
import com.orotrain.oro.model.SyncRating

/**
 * Picks the pre-recorded audio prompt for a Session Outcome. One of 12 prompts
 * (Sync Rating × Power Range), per ADR-0005.
 */
fun sessionSummaryAudioPromptFor(outcome: SessionOutcome): Byte = when (outcome.syncRating) {
    SyncRating.Excellent -> when (outcome.powerRange) {
        PowerRange.Light    -> BleManager.AUDIO_SUMMARY_EXCELLENT_LIGHT
        PowerRange.Moderate -> BleManager.AUDIO_SUMMARY_EXCELLENT_MODERATE
        PowerRange.Strong   -> BleManager.AUDIO_SUMMARY_EXCELLENT_STRONG
        PowerRange.Maximum  -> BleManager.AUDIO_SUMMARY_EXCELLENT_MAXIMUM
    }
    SyncRating.Good -> when (outcome.powerRange) {
        PowerRange.Light    -> BleManager.AUDIO_SUMMARY_GOOD_LIGHT
        PowerRange.Moderate -> BleManager.AUDIO_SUMMARY_GOOD_MODERATE
        PowerRange.Strong   -> BleManager.AUDIO_SUMMARY_GOOD_STRONG
        PowerRange.Maximum  -> BleManager.AUDIO_SUMMARY_GOOD_MAXIMUM
    }
    SyncRating.Poor -> when (outcome.powerRange) {
        PowerRange.Light    -> BleManager.AUDIO_SUMMARY_POOR_LIGHT
        PowerRange.Moderate -> BleManager.AUDIO_SUMMARY_POOR_MODERATE
        PowerRange.Strong   -> BleManager.AUDIO_SUMMARY_POOR_STRONG
        PowerRange.Maximum  -> BleManager.AUDIO_SUMMARY_POOR_MAXIMUM
    }
}
