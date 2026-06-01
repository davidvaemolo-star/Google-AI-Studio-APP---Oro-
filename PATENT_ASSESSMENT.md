# Patent Assessment: Oro Haptic Paddle

**Date:** 2026-05-24
**Prepared by:** Claude Code (lead-research-assistant skill)
**Based on:** Full codebase analysis of firmware, Android app, web UI, ADRs, and documentation

---

## Executive Summary

The Oro Haptic Paddle system contains several potentially patentable inventions. There is a **solid foundation for at least 2-3 defensible utility patent claims**, and possibly a broader portfolio covering the system as a whole.

---

## Product Overview

The **Oro Haptic Paddle** is a training system for OC6 (6-person outrigger canoe) crews that improves stroke synchronization through haptic feedback and audio cues. Each device is built into a paddle's t-handle and vibrates in sync with the Pacer's (pace-setter's) stroke cycle, cueing Followers to synchronize their paddle entry (Catch) with the pacer.

**Three-component system:**
- **Firmware** — nRF52840 (Seeed XIAO Sense) microcontroller in each paddle handle
- **Android App** — Training controller (Jetpack Compose, Kotlin), manages up to 6+ devices via BLE
- **Web UI** — React/Vite configuration planner (scaffold; future MVP)

---

## Patent Eligibility Framework

For each candidate invention, three criteria are assessed:
- **Novelty** — Is it new? (35 U.S.C. § 102)
- **Non-obviousness** — Would it be non-obvious to a skilled engineer? (35 U.S.C. § 103)
- **Utility** — Does it solve a real problem? (35 U.S.C. § 101)

---

## Candidate Claims, Ranked by Strength

### 1. Dual-Gate Stroke Detection — STRONGEST

**Source:** ADR-0004, `handleStrokeDetection()`, `firmware/OroHapticFirmware/OroHapticFirmware.ino` lines 1519–1700

**What it is:**
IMU acceleration spike (Gate 1) must be *confirmed* by a Force Sensitive Resistor pressure rise within a time window (Gate 2) to declare a valid paddle Catch. Gracefully degrades to IMU-only if FSR fails.

**Technical detail:**
- Gate 1 (IMU): LSM6DS3 Y-axis acceleration exceeds threshold (default 1.0g, auto-calibrated to 55% of paddler's observed peak)
- Gate 2 (FSR): Top hand pressure (pin A3) rises within a short confirmation window after Gate 1
- If both gates fire: Catch confirmed → haptic trigger + Catch timestamp sent via BLE
- If FSR absent/failed: falls back to IMU-only (no code change required)

**Patent argument:**
- **Novel:** IMU-only stroke detection exists in wearables (swimming, rowing); FSR-only grip detection exists in racket sports. The *combination* as an AND-gate for confirming a water-entry event in paddling is not established prior art.
- **Non-obvious:** The insight that paddle blade entry correlates with *top hand loading* (not bottom hand, not blade force) is biomechanically specific to outrigger/OC technique. Not derivable from generic sensor fusion literature.
- **Utility:** Solves the false-positive problem (hand vibration, catches from rough water) that plagues IMU-only detection in marine environments.

**Suggested claim language:**
> "A method for detecting a paddle stroke event comprising: detecting an acceleration threshold crossing via an inertial measurement unit embedded in a paddle handle; confirming the stroke event by detecting a concurrent rise in grip force above a pressure threshold via a force-sensitive resistor within a predefined time window; and triggering a haptic actuator upon confirmed stroke detection."

---

### 2. Phone-Mediated Multi-Paddle Haptic Synchronization — STRONG

**Source:** ADR-0002, `BleManager.kt:broadcastHapticPulse()`, `firmware/BLE_PROTOCOL.md`

**What it is:**
Pacer paddle detects stroke Catch → notifies central Android device via BLE → Android immediately broadcasts `CMD_SINGLE_PULSE` to all Follower paddles → Followers fire haptic actuators, cueing synchronized entry. Sync Score calculated from Pacer–Follower latency.

**Technical detail:**
- BLE connection interval: 7.5–20ms (low latency)
- Pacer sends 16-byte enriched Stroke Event packet (phase + timestamp + accel peaks + FSR + phase duration)
- Phone receives Pacer Catch timestamp, broadcasts haptic command to all Followers (<50ms round-trip)
- Sync Score: `50ms = 100 (perfect), 300ms = 0 (no sync)`, rolling 10-stroke window
- Supports up to 12 simultaneous paddlers across 2 OC6 canoes (firmware limit currently 6; expansion planned)

**Patent argument:**
- **Novel:** Crew sports synchronization systems exist (rowing ergs, metronomes, visual cues) but using embedded haptic paddles with phone-mediated BLE relay is not established prior art.
- **Non-obvious:** The phone-mediated topology (rather than mesh BLE between paddles) is a non-obvious architectural choice that also *enables* Sync Score calculation by having a single timestamping authority.
- **Utility:** Enables 12 simultaneous paddlers to receive synchronized haptic cues with <100ms latency without requiring direct device-to-device communication (which nRF52840 cannot support in standard configuration).

**Suggested claim language:**
> "A crew training system comprising: a first haptic device designated as a Pacer, detecting a stroke event and transmitting a timestamp to a central computing device via Bluetooth Low Energy; a central computing device receiving the Pacer stroke event and transmitting a haptic trigger command to a plurality of Follower devices; wherein each Follower device actuates a vibration motor upon receiving the haptic trigger; and wherein the central device calculates a synchronization score for each Follower based on the latency between the Pacer stroke event and the Follower's detected stroke event."

---

### 3. Decoupled Haptic Timing vs. Stroke Counting — MODERATE-STRONG

**Source:** ADR-0001, firmware training state machine

**What it is:**
Haptic fires at **Catch** (blade enters water) for synchronization cueing, but the stroke counter increments at **Finish** (blade exits water). These are deliberately decoupled events.

**Patent argument:**
- **Novel:** Most stroke-counting wearables increment on a single event. Deliberate decoupling of the *feedback trigger* from the *counting event* for biomechanical accuracy is likely novel in paddle-sport applications.
- **Non-obvious:** Requires domain insight that counting on Catch double-counts incomplete strokes; cueing at Finish is too late for crew synchronization.
- **Utility:** Produces accurate stroke counts while preserving maximum haptic cue lead time.

---

### 4. Crew-Wide Session Summary via Cross-Dimensional Voice Prompts — MODERATE

**Source:** ADR-0008, `audio_prompts.h`, `firmware/BLE_PROTOCOL.md` (prompt IDs 0x08–0x13)

**What it is:**
12 pre-recorded voice prompts indexed by a 2D matrix (Sync Rating × Power Range), broadcast simultaneously to all paddles at session end.

**Matrix:**
```
             Light    Moderate   Strong    Maximum
Poor         0x08      0x09      0x0A      0x0B
Good         0x0C      0x0D      0x0E      0x0F
Excellent    0x10      0x11      0x12      0x13
```

Example output: *"Excellent sync, Strong power."*

**Patent argument:**
- **Novel:** Post-session multi-dimensional performance feedback delivered as synchronized audio across a wireless crew is not established prior art.
- **Non-obvious:** Combining *crew synchronization quality* with *individual power output* into a single vocal prompt reduces cognitive load relative to numerical data.
- **Lower confidence** — the prompt delivery mechanism is less inventive than the detection/synchronization claims.

---

### 5. Adaptive Per-Paddler Calibration via Acceleration Extremes — MODERATE

**Source:** ADR-0003, `completeCalibration()`, calibration state machine

**What it is:**
50-stroke baseline session samples each paddler's acceleration range; threshold auto-set to 55% of observed peak. Per-paddler, not global.

**Technical detail:**
- Command: `CAL_CMD_START` (0x01) initiates 50-stroke sampling
- Firmware tracks `maxAccelSeen` across all 50 strokes
- On completion: `threshold = maxAccelSeen * 0.55`
- Device notifies Android of progress (stroke count, current max/min accel)
- Device transitions to `STATE_READY` on completion

**Patent argument:**
- **Novel:** Per-paddler stroke threshold auto-calibration using acceleration percentile as the threshold function, in a water-sport context, may be defensible.
- **Non-obvious:** The 55% formula and 50-stroke sample size are empirically tuned to this domain.
- **Lower confidence** — prior art risk from sports wearables (Catapult, Garmin, etc. have calibration claims).

---

## What is Likely NOT Patentable

| Feature | Reason |
|---------|--------|
| BLE service/characteristic UUID design | Protocol design, not patentable process |
| RGB LED state indicator | Entirely conventional |
| I2S audio playback | Well-established hardware pattern |
| Android MVVM architecture | Industry standard pattern |
| FSR Power Range bucketing (Light/Moderate/Strong/Maximum) | Likely not novel; similar to gym/sports wearables |
| Battery monitoring algorithm | Fully conventional |

---

## Recommended Patent Strategy

### Option A: Single Broad Utility Patent *(Best starting point)*

File a **single utility patent** with the system claim as the primary claim, and the dual-gate detection + phone-mediated sync as dependent claims. Captures the broadest protection at lowest cost.

**Primary system claim:** A paddle-sport crew synchronization training system comprising embedded haptic devices in paddle handles with IMU + FSR stroke detection, a central coordinator device, and real-time multi-dimensional performance feedback.

### Option B: Patent Portfolio *(If funded)*

File 2–3 separate patents:
1. Dual-gate detection method (method claim, hardware-independent)
2. Multi-device BLE haptic synchronization architecture (system claim)
3. Adaptive calibration method (method claim)

---

## Key Risks & Due Diligence Required

### 1. Prior Art Search
Run a professional prior art search before filing. Key areas to cover:
- Garmin, Catapult, STATSports sports wearables patents (IMU stroke detection)
- Rowing/dragon boat training devices (e.g., OttoMüller Sport patents)
- Paddle-sports haptic patents (US Patent Class 473/415–473/450)
- Apple/Google IMU gesture recognition patents (false positive filtering)

### 2. Architecture Framing Risk
The phone-mediated architecture is partly driven by nRF52840 hardware limitations (cannot be BLE peripheral + central simultaneously). A patent examiner may argue this is a workaround to hardware constraints rather than an invention. The attorney must frame the *functional benefits* (centralized timestamping, Sync Score calculation, multi-canoe scalability) as the inventive contribution.

### 3. Threshold Values Are Not Patentable
The 50ms sync threshold and 55% calibration constant are not patentable numbers — but the *methods* that use them are.

### 4. Public Disclosure Bar
**Do not publicly disclose the invention** (publish papers, demo at conferences, sell units) before filing at least a provisional patent. Public disclosure starts a 1-year bar on US filing and **immediately bars international patents** in most jurisdictions.

### 5. Git History as Evidence
Creation dates in the git commit log serve as evidence of invention date. Do not rewrite or amend history (no force-push, no rebase of published commits).

---

## Technical Evidence Summary

Key source files supporting patent claims:

| File | Claim Supported |
|------|----------------|
| `firmware/OroHapticFirmware/OroHapticFirmware.ino` lines 1519–1700 | Dual-gate detection, decoupled haptic/counting timing |
| `firmware/BLE_PROTOCOL.md` | Multi-device sync architecture, Sync Score formula, audio prompt matrix |
| `android-app/…/ble/BleManager.kt` | Phone-mediated BLE relay, Sync Score calculation |
| `android-app/…/analysis/StrokeAnalyzer.kt` | FSR power analysis, coaching issue detection |
| `firmware/OroHapticFirmware/audio_prompts.h` | Cross-dimensional voice prompt library |
| `docs/adr/` | ADR-0001 through ADR-0009 (architectural decisions as invention documentation) |

---

## Practical Next Steps

1. **Consult a patent attorney** with IP experience in sports technology or wearables. The technical depth here is sufficient for a productive first meeting. Bring this document.

2. **Prepare a formal invention disclosure document** — this assessment is a strong starting point. The attorney will convert it into formal claim language.

3. **Run a freedom-to-operate (FTO) search** before filing, specifically on the dual-gate detection claim.

4. **File a provisional patent application** (~$1,500–$3,000 USD) to establish a priority date while refining claims. You have 12 months from provisional filing to file the full utility patent.

5. **Do not publicly demo or sell** before at least a provisional is filed (see Public Disclosure Bar above).

---

## Bottom Line

**Yes, there is a solid patent case**, particularly around:
- The **dual-gate stroke detection method** (IMU + FSR AND-gate)
- The **phone-mediated multi-paddle haptic synchronization architecture**

These two claims are most likely to survive examination. The system as a whole — the combination of these elements for crew paddle-sport training — is novel enough to warrant filing. Engage a patent attorney, prioritizing a provisional application before any public demonstration or commercialization.
