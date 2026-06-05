---
status: accepted (constraint on the firmware audio path; relates to ADR-0002, ADR-0018)
---

# Audio playback must not block stroke detection

The firmware `loop()` is cooperative: it samples the IMU and runs the Catch→Finish state machine (`handleStrokeDetection()`) once per iteration. Audio playback (`playTone`, `playBuffer`, `speakCanoeSpeed`, `speakRollCall`) is **fully blocking** — it busy-waits until the whole clip finishes — so while a paddle makes any sound it stops watching for strokes, and strokes during that window are lost. The fix: playback **services stroke detection in the gaps between audio chunks** (~8 ms cadence) so detection keeps running on time during a prompt. This applies on every device.

## Why this is a real problem, not cosmetic

The Pacer plays the same prompts as everyone else, and the **Canoe Speed call-out fires mid-set, at the 3rd-to-last stroke of a High Zone** (ADR-0018) — precisely while the crew is stroking hard. A ~1–2 s blocking clip there makes the Pacer miss the strokes that *count* (the Pacer's Catch/Finish is what advances the stroke/set/zone), so sets can mis-count or fail to advance. On Followers the same starvation drops Sync Score samples.

## Considered options

- **A — keep watching while talking (chosen).** Service detection between audio chunks. The only option that keeps both the **count** correct and the **timing** honest — and timing is load-bearing because the whole Sync Score rides on a ~50 ms phone-round-trip budget (ADR-0002).
- **B — catch up afterwards.** Buffer IMU samples (the LSM6DS3 has an onboard FIFO) and process them when the clip ends. Counting stays accurate, but the haptic for those strokes lands up to a clip-length (~1–2 s) late, denting felt sync. Rejected.
- **C — quiet the Pacer.** Skip mid-stroke prompts on Seat 1 only. Lowest effort, but breaks "every paddle speaks in unison" (ADR-0016/0017) and the Pacer paddler wouldn't hear the speed. Rejected.

## Consequences

- The audio module must expose a way to call back into stroke detection during its wait loops (a service hook), introducing a deliberate, documented coupling from audio → detection. The hook is a no-op unless stroke detection is enabled, so end-of-session audio (Roll-Call) is unaffected.
- A future "simplification" back to plain blocking playback would silently reintroduce missed strokes — this ADR exists to stop that.
- Verify on-device with serial logging of the gap between successive detection passes during a prompt, before and after.
