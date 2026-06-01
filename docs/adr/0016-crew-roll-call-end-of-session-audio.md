---
status: accepted (supersedes ADR-0005)
---

# End-of-session audio is a per-seat Crew Roll-Call, and the phone is silent

The end-of-session summary is reworked from a single crew-wide voice prompt into a **Crew Roll-Call**: every device, in unison, speaks the crew's overall Sync Rating and then reads each occupied Seat's Sync Rating and Power Range (the Pacer's entry gives Power Range only, since it has no Sync Score). The Training Controller (phone) no longer speaks at all — its text-to-speech is removed and all athlete-facing audio comes from the devices. The synchronised start signal also gains a single haptic buzz on "go". This supersedes ADR-0005's 12-prompt crew-only summary.

## Why

A single crew-wide rating can't tell the crew *who* synced and who didn't. Reading every paddler's result aloud on every paddle makes individual accountability audible on the water, with nobody looking at the phone. Removing the phone's voice keeps one consistent audio source — the paddles — so the phone never competes with them, and matches the paddles already owning all athlete-facing audio.

## How it stays intelligible

The phone sends the full roster (each Seat's Sync Rating + Power Range) to every device in one message, then triggers them together; each device composes and speaks the whole read-out locally from its stored clips.

- **Streaming clips one-at-a-time from the phone was rejected.** Across up to 12 devices, Bluetooth timing drift makes the overlapping voices garble.
- **Having only one device speak was rejected.** Paddlers far down a 6 m canoe may not hear it; the roll-call is meant to reach the whole crew.

## Consequences

- New voice assets must be recorded and flashed to every device: seat numbers (and canoe numbers, for two canoes), "pacer", the connective words, and the Sync Rating / Power Range words. The old 12-prompt Sync×Power summary grid is retired.
- **Per-seat scoring is now required.** Today only the Pacer's strokes feed Power Range, and Sync Score is computed crew-wide only (ADR-0015). Both must now be computed per device — each Follower's own absolute catch gap, and each device's own session pressure.
- A new BLE message (the roster plus a synchronised trigger) and firmware that speaks a composed sequence replace the single-event summary write. See `firmware/BLE_PROTOCOL.md`.
- A Follower with no matched Catches stays **Not Measured** (ADR-0015); its roll-call entry says so rather than inventing a rating.
- The start signal stays tones-and-buzz (no spoken "go"), honouring ADR-0008; the phone's spoken "ready, go" is removed.
