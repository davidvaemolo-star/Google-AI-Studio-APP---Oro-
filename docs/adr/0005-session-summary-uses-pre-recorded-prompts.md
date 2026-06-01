---
status: superseded by ADR-0016
---

# Session summary delivered as pre-recorded voice prompts, not dynamic TTS

> **Superseded by [ADR-0016](0016-crew-roll-call-end-of-session-audio.md).** The single crew-wide
> 12-prompt summary described below is replaced by a per-seat Crew Roll-Call, and the phone is
> silenced. The "pre-recorded clips, not dynamic TTS" principle still holds; the crew-only grid does not.


At the end of a session, the Android app sends a crew-wide summary to all devices as a pre-recorded audio prompt via the Audio Control BLE characteristic. The prompt is selected based on which Sync Score and Power Range bracket the crew achieved.

Dynamic TTS was rejected for MVP: the device audio system plays pre-compiled PCM arrays baked into firmware — streaming synthesised audio to 6 devices simultaneously over BLE is not feasible at this stage. The Android phone's TTS engine could generate dynamic text, but transmitting it as audio in real time is out of scope. Pre-recorded prompts keyed to outcome brackets are sufficient for MVP and leverage the existing audio infrastructure with no firmware changes.
