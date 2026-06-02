---
status: accepted (supersedes the synchronised-start half of ADR-0008)
---

# A Session begins on the Pacer's first Catch, not on a synchronised countdown

Pressing **Start** no longer makes the Session **Active** immediately. The Session enters a new **Standby** state: every device is configured and detecting, each paddle speaks **"Stand by"**, but nothing is measured. The Session becomes **Active** the instant the **Pacer** takes its first **Catch** — at which point the session clock, stroke 1 + Follower haptics, Sync Score recording, and zone progression all begin, backdated to that Catch's timestamp. The armed waiting time is excluded from session duration. This **retires the Countdown** (the 3-beeps-plus-go-buzz synchronised start from ADR-0008).

## Why

Coaches press Start while the boat is still being lined up at the start line — but the old model began timing, counting strokes, and scoring sync from the button press, polluting every session with the line-up period. Letting the Pacer's first stroke be the true start makes the recorded session match the actual paddling effort, and gives the crew a natural, athlete-driven "go" instead of a fixed phone-driven countdown.

## What we gave up, and the guard

- **Synchronised first stroke.** The Countdown's whole purpose was a simultaneous "go" so the crew's first Catch landed together. Without it, Followers learn of the Pacer's first Catch only via the phone-mediated haptic (~50–100 ms later, ADR-0002), so the first stroke is ragged by design; the rhythm self-corrects from stroke 2. We accepted this — a deliberate, athlete-led start is worth more than a synchronised cold-start.
- **Accidental starts.** With no deliberate "go", a stray bump while lining up could start the session. The first Catch is therefore **pressure-confirmed**: after the Pacer's first Catch the phone watches the FSR stream for a Top Hand Pressure rise within a short window (~400 ms); only a confirmed rise commits the start (a partial, start-only realisation of ADR-0004). The gate lives on the **phone**, for the first Catch only — putting it in firmware for every Catch would delay every catch report and eat the 50 ms haptic/sync budget. Window and threshold are placeholders pending field data.

## End-of-session cue

Symmetrically, session end gains a spoken cue before the **Crew Roll-Call** (ADR-0016): on completion every paddle speaks **"Session complete"**, then **"Stand by for results"** 2 s later, then the Roll-Call plays ~30 s after that (roster loaded immediately, played after the delay — the load/play split of BLE_PROTOCOL §1.10). The 30 s is a placeholder. The phone stays silent throughout (ADR-0016).

## Consequences

- New `TrainingStatus.Standby` between `Starting` (the brief configuring transient) and `Active`.
- Three new voice clips must be generated and flashed to every device: **"Stand by"**, **"Session complete"**, **"Stand by for results"**. New Audio Event IDs in BLE_PROTOCOL §1.7; the Countdown's `0x02` becomes unused-but-reserved (do not reuse).
- "Stand by" is heard at both ends of a session (start = armed/waiting; "Stand by for results" = end). Deliberate, contextually distinct.
- The firmware-driven LED (ADR-0009) shows its training colour during Standby, since the device is in `STATE_TRAINING` while the phone withholds measurement.
