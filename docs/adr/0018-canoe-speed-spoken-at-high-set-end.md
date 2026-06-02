---
status: accepted
---

# Canoe Speed is spoken on every device at the end of each High-zone set

The phone tracks the hull's GPS speed (**Canoe Speed**, km/h to one decimal) and, **only during High intensity zones**, has every device speak it aloud — as a bare number, e.g. "fourteen point three" — **once per set**, fired on the **Pacer's 3rd-to-last Finish** so the speech finishes before the set ends. The spoken value is the **instantaneous, lightly smoothed** (~1–2 s rolling average) speed at that instant. The phone stays silent (ADR-0016); the devices compose the number from stored digit clips and speak it in unison, reusing the Crew Roll-Call delivery pattern (BLE_PROTOCOL §1.10 → new §1.11, characteristic `…000B`).

## Why these choices

- **Only High zones, only at set end.** High is where the crew pushes for top speed; the end of a set is when the loaded-up hull is moving fastest, so it's the most motivating moment to hear the number. Low/Medium zones and mid-set call-outs would just add noise.
- **3rd-to-last Finish as the trigger.** Stroke count advances on the Pacer's Finish (ADR-0001). Firing 3 strokes from the end gives the ~1.5–2 s spoken number room to land *before* the set-changeover beep and "next set" voice, instead of stepping on them. Tight at 80 SPM — the runway is a placeholder.
- **Instantaneous + smoothed, not peak-of-set.** Simplest thing matching "end ≈ fastest"; the ~1–2 s average kills GPS jitter and a single bad fix without the bookkeeping of peak-tracking. Revisit peak-of-set if the field number feels like it undersells the push.
- **One value for the whole crew.** The two canoes lash into a single rigid V12, so there is one hull speed; the phone rides on it and broadcasts that one number to every device regardless of seat or canoe.

## What we gave up / guards

- **No GPS fix or denied location permission → silent skip.** That set simply doesn't announce; the crew hears nothing unusual. The coach (who can reposition the phone or grant permission) sees a GPS-status indicator on the phone. A stale last-known speed is never spoken; a spoken "no signal" bark was rejected as alarming and useless mid-push.
- **Purely live.** Canoe Speed is not recorded — not in the Session Summary, the spoken Crew Roll-Call, or the Room DB. Top-speed-on-the-summary was considered and deferred to keep this change out of the end-of-session pipeline.
- **Always on (no toggle yet).** Active during every High zone whenever GPS is available.

## Consequences

- New BLE characteristic `1234000B-…` (BLE_PROTOCOL §1.11): phone writes the speed (km/h ×10) plus a unison start-delay byte and volume; the device composes and speaks. `…000B` is now reserved — do not reuse.
- ~22 new voice clips (number words "zero"–"twenty" plus "point") generated and flashed via the same QSPI pipeline as the Roll-Call clips; an unflashed paddle falls back to the existing tone fallback rather than speaking the number.
- Android requires runtime location permission (FINE) and an active GPS/location stream while a session is running; denial degrades gracefully to silent-skip.
- Interval/threshold placeholders pending field data: the smoothing window, the 3-strokes-from-end runway, and the realistic speed cap for clip composition.
