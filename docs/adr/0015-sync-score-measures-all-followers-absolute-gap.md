# Sync Score measures every follower's absolute catch gap, on an aligned clock

The Sync Score is reworked so it actually measures crew synchronisation:

1. **Every device detects its own Catch during a Session, and the phone subscribes to all of them** — not just the Pacer's. Only the Pacer's Catch still triggers haptics and advances the stroke count; a Follower's own Catch is used solely to score its sync.
2. **The score is the absolute timing gap** `|follower_catch − pacer_catch|`, on the existing scale (≤50 ms → 100, ≥300 ms → 0). Rushing ahead of the Pacer is penalised exactly as much as lagging behind, because a synchronised crew catches *together*.
3. **Each Follower Catch is paired to the nearest Pacer Catch within ~±400 ms.** A Follower Catch with no Pacer Catch in that window counts as a max-gap (out-of-sync) stroke, not silently dropped.
4. **Catch times come from each device's own firmware timestamp**, translated into a common timeline. The phone estimates each device's clock offset from the *fastest* recent messages (a sliding-window minimum of `phone_receive_time − device_timestamp`), reset on reconnect.
5. **No matched Follower Catches → "Not Measured" (N/A), not Poor.** A single-device session, or one where pairing never matched, is distinct from genuinely bad sync. The end-of-session voice summary (ADR-0005) **skips the spoken sync prompt** when sync is Not Measured (the 12-prompt grid has no sync-less entry and no power-only assets exist).
6. Stroke-event subscriptions and the latency accumulator are reset on session end and on Pacer change, so a former Pacer can never leak in as a phantom Follower.

## Why

The previous implementation could not measure sync at all: the phone only ever subscribed to the *Pacer's* stroke events, so there was never any genuine Follower data. A correctly-run session therefore fell through to the empty-data fallback and always reported **Poor**. Worse, subscriptions were never torn down when the Pacer changed, so reassigning the Pacer left the old one subscribed as a phantom Follower — and because the old math clamped "Follower caught before the Pacer" to a perfect 0, that phantom data often produced a misleading **Excellent**. The result: the Sync Score depended on *which device you picked as Pacer and in what order*, not on how synchronised the crew was.

## Alternatives considered

- **Keep Pacer-only detection.** Rejected: a per-Follower Sync Score is then impossible by construction.
- **Signed latency, clamp negatives to perfect** (the old behaviour). Rejected: rewards rushing; a paddler ahead of the beat is out of sync, not perfectly synced.
- **Pair to the most recent Pacer Catch** (old behaviour). Rejected: mis-pairs an early Follower Catch with the previous stroke, manufacturing huge false gaps once early catches are penalised.
- **Phone receive-time instead of device timestamps.** Simpler, and matches the documented 50 ms "round-trip" calibration. Rejected for now because per-device Bluetooth latency leaks in as a systematic bias — a milder echo of the very "which device is Pacer matters" bug being fixed. Device timestamps + min-filtered offset removes that bias; the cost is clock-alignment logic.
- **Keep reporting Poor when there's no data.** Rejected: mislabels single-device and matching-failure sessions as badly synced.

## Trade-offs and consequences

- Every device now notifies every Catch during a Session, increasing BLE traffic. This interacts with the existing 6-device cap; with two OC6s (12 devices) the notification load needs validation.
- Clock-offset estimation assumes Bluetooth one-way latency is roughly its minimum on the fastest packets; long-session clock drift is handled by using a *sliding* window rather than an all-time minimum.
- The 50 ms / 300 ms bounds remain unvalidated placeholders (see CONTEXT.md open questions).
- "Not Measured" adds a state outside the 12-prompt audio grid; rather than invent a prompt or mislabel it, the spoken sync summary is simply skipped when sync is Not Measured. Adding power-only voice assets is a possible later refinement.
