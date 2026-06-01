# ADR-0008: Audio prompt redesign — tones for timing cues, voice for content cues

## Status
Accepted

## Context

The original audio system had 8 voice prompts covering training lifecycle events: Training Start, Halfway, Set Complete, Last Set, Zone Transition, Session Complete, Pause, Resume. Field use revealed two problems:

1. **Sample rate mismatch**: audio files were encoded at 32kHz by `generate_voice_simple.py` but the I2S peripheral was configured at ~15.875kHz (MCKFREQ 32MDIV21, RATIO 96X). Playback was half speed, one octave low.

2. **Wrong prompts for the sport**: Several prompts (Halfway, Set Complete, Pause, Resume, generic Session Complete) added noise without useful information for paddlers who cannot see the Android screen during a session.

## Decision

### I2S sample rate fix

Change `audio_i2s.cpp` RATIO from `RATIO_96X` (÷96) to `RATIO_48X` (÷48), giving LRCLK ≈ 31,746Hz — a 0.79% deviation from 32kHz, imperceptible in voice. Update `SAMPLE_RATE` define in `audio_i2s.h` to 32000.

### Prompt taxonomy: tones vs voice

Two distinct prompt types serve different purposes:

**Tones** (firmware-generated, zero latency, no flash storage):
- **Session start**: 3 short identical beeps (880Hz, 100ms) + 1 long distinct "go" beep (1320Hz, 500ms). Replaces "Training Start" voice. Athletes in timed starts recognise this pattern immediately. No spoken words needed.
- **Set changeover**: single beep (660Hz, 80ms) on last stroke of every set. Sent by Android when stroke count completes a set. Replaces "Set Complete" voice. A beep is faster to process than speech mid-exertion.

**Voice** (pre-recorded, informational):
- **"Last set"**: fires at start of the last set of the final zone only. Tells paddlers to push hard through the finish.
- **"Next set low / medium / high"**: fires at start of the last set of a non-final zone. Replaces generic "Zone Transition". Names the upcoming intensity so paddlers can consciously adjust effort. Fires one set early — as a preparation cue, not a transition announcement.
- **12 session summary prompts** ("[Poor/Good/Excellent] sync, [Light/Moderate/Strong/Maximum] power"): replaces generic "Session Complete". Selected by Sync Rating × Power Range at session end. Gives crew specific, actionable feedback they can act on next session.
- **"Oro"** (power-on): retained unchanged.

### Dropped prompts

Halfway, Set Complete, Session Complete (generic), Pause, Resume. These fired too frequently, carried no actionable information for paddlers, or corresponded to workflows (pause/resume) not used in live OC6 sessions.

### Triggering responsibility

All audio commands are sent by Android over BLE. Firmware plays whatever event ID it receives — it has no knowledge of zone structure or session progress. This is consistent with the existing architecture and avoids duplicating training state in firmware.

## Consequences

- 16 pre-recorded voice prompts replace 8. Total flash usage increases modestly.
- `generate_voice_simple.py` and `audio_prompts.h` must be regenerated.
- Android must compute Sync Rating and Power Range at session end to select the correct summary prompt.
- `AudioEvent` enum in firmware and `BleManager` constants in Android must be kept in sync — a breaking change if firmware and Android versions diverge.
