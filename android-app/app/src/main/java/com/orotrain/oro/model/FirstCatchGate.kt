package com.orotrain.oro.model

/**
 * Confirmation thresholds for the Standby->Active first-Catch gate (ADR-0017). Placeholders pending
 * field validation (see CONTEXT.md open questions).
 */
object FirstCatchGateConfig {
    /** How long after the Pacer's Catch a Top Hand Pressure rise still counts as confirmation. */
    const val CONFIRM_WINDOW_MS: Long = 400
    /** The Top Hand Pressure % a real stroke's Drive must reach to confirm the Catch. */
    const val CONFIRM_PRESSURE_PERCENT: Int = 30
}

/**
 * Pure state for the Standby->Active gate (ADR-0017). [candidatePhoneMs] is the phone-clock time the
 * Pacer's most recent unconfirmed Catch arrived; null means no Catch is awaiting confirmation. The
 * Catch only starts the session once a Top Hand Pressure rise confirms it within the window, so a
 * stray bump during line-up can't start training.
 */
data class FirstCatchGate(
    val candidatePhoneMs: Long? = null
)

/** Outcome of feeding a pressure sample to the gate. */
sealed interface FirstCatchResult {
    val gate: FirstCatchGate

    /** No confirmation: either no candidate, the rise was too weak, or the window had lapsed. */
    data class Pending(override val gate: FirstCatchGate) : FirstCatchResult

    /** The Catch is confirmed: start the session, backdated to [startTimePhoneMs]. */
    data class Confirmed(
        override val gate: FirstCatchGate,
        val startTimePhoneMs: Long
    ) : FirstCatchResult
}

/**
 * The Pacer's Catch arms (or refreshes, if one was already pending) the candidate at [phoneNowMs].
 */
fun FirstCatchGate.onPacerCatch(phoneNowMs: Long): FirstCatchGate =
    copy(candidatePhoneMs = phoneNowMs)

/**
 * A Pacer Top Hand Pressure sample at [phoneNowMs]. Confirms the candidate iff the pressure has
 * risen to [thresholdPercent] within [windowMs] of the Catch; a sample after the window drops the
 * stale candidate so only a genuinely fresh Catch+rise pair can start the session.
 */
fun FirstCatchGate.onPacerPressure(
    pressurePercent: Int,
    phoneNowMs: Long,
    windowMs: Long = FirstCatchGateConfig.CONFIRM_WINDOW_MS,
    thresholdPercent: Int = FirstCatchGateConfig.CONFIRM_PRESSURE_PERCENT
): FirstCatchResult {
    val armed = candidatePhoneMs ?: return FirstCatchResult.Pending(this)
    if (phoneNowMs - armed > windowMs) {
        return FirstCatchResult.Pending(FirstCatchGate())
    }
    return if (pressurePercent >= thresholdPercent) {
        FirstCatchResult.Confirmed(FirstCatchGate(), startTimePhoneMs = armed)
    } else {
        FirstCatchResult.Pending(this)
    }
}
