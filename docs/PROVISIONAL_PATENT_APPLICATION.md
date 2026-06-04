# PROVISIONAL PATENT APPLICATION

**Title of Invention:**
HAPTIC PADDLE-INTEGRATED CREW SYNCHRONISATION SYSTEM FOR OUTRIGGER CANOE TRAINING

**Applicant:** David Vaemolo
**Filing Date:** 2026-06-04
**Status:** PROVISIONAL — NOT FOR PUBLIC DISCLOSURE

---

> **Note to Patent Attorney:** This document is a technical disclosure prepared from the engineering record (architecture decisions, firmware specification, and domain model) of the Oro Haptic Paddle system. It is intended as the basis for a formal USPTO provisional patent application under 35 U.S.C. § 111(b). The claims section is included for completeness of disclosure but should be reviewed and refined by counsel before the non-provisional is filed.

---

## FIELD OF THE INVENTION

The present invention relates to wearable sport training devices and, more particularly, to a wireless, multi-device haptic feedback system integrated into the handle of an outrigger canoe paddle to improve intra-crew stroke synchronisation during on-water training sessions.

---

## BACKGROUND OF THE INVENTION

Outrigger canoe racing — particularly the six-person OC6 discipline — depends critically on stroke synchronisation among crew members. When all six paddlers catch (entry of the blade into the water) and drive simultaneously, the hull moves with maximum efficiency. Even small timing offsets of 50–300 milliseconds generate opposing hydrodynamic forces, slowing the canoe and increasing hull instability. Experienced crews spend considerable practice time achieving and maintaining synchronisation, typically by following an audio metronome, a drum cadence, or the visual cue of the stroke-1 (bow) paddler.

Existing approaches to synchronisation training suffer from several limitations:

1. **Audio cues**: Metronomes and drum tracks suffer from wind, water noise, and safety concerns on open water. They do not adapt to the pacer's actual stroke and give no individual feedback on whether each paddler is catching early or late.

2. **Video analysis**: Post-session frame-by-frame analysis is expensive, time-delayed, and unavailable during a session.

3. **Coach observation**: Even skilled observers cannot reliably detect 50–100 ms timing gaps across six paddlers simultaneously.

4. **Wrist-worn devices**: Existing fitness trackers placed on the wrist introduce latency from wrist-to-blade mechanics that is inconsistent across paddlers. The paddle grip, not the wrist, is the primary interface between athlete and water.

5. **No individual accountability**: Crew-level feedback (e.g. "you're out of sync") does not identify which seat is responsible, making targeted coaching difficult.

What is needed is a system that: (a) detects the actual stroke event at the point of blade entry, (b) propagates a synchronised cue to every crew member within the human perception threshold of approximately 50 ms, (c) measures each individual's timing error relative to the designated pacer, and (d) delivers per-seat performance feedback at session end without requiring crew members to look at a display.

The present invention addresses each of these needs.

---

## SUMMARY OF THE INVENTION

The invention is a haptic training system for multi-person paddle sports crews, comprising:

- A plurality of **haptic paddle devices**, each embedded in the T-handle of a single paddler's paddle, containing an inertial measurement unit (IMU), a force-sensitive resistor (FSR) positioned at the top-hand grip, a vibrotactile actuator, a wireless transceiver, a microcontroller, and a power source;
- A **mobile controller** (e.g. an Android smartphone) carried by the coach, which mediates wireless communication between all devices and manages session state;
- A **configuration interface** (e.g. a web application) for designing and storing reusable training programmes;
- A **firmware architecture** on each device that detects stroke events, vibrates in response to coach-mediated commands, and delivers spoken audio from pre-loaded voice clips;
- A **synchronisation scoring engine** on the mobile controller that computes, per paddler, the absolute timing gap between that paddler's blade entry and the designated pacer's blade entry, using clock-aligned device timestamps; and
- A **Crew Roll-Call** protocol by which every device, simultaneously, speaks the end-of-session per-seat performance summary aloud on the water without any audio from the mobile controller itself.

In one embodiment, a single pacer device is always the device assigned to seat number 1 in the lead canoe, and the mobile controller designates all other devices as followers. No separate "Set as Pacer" user action exists — the seat assignment is the pacer designation. The system supports up to twelve devices across two OC6 canoes simultaneously, mediated by a single mobile controller.

---

## BRIEF DESCRIPTION OF THE DRAWINGS

*(To be prepared by patent draftsman; the following enumerated figures are referenced in the Detailed Description.)*

- **FIG. 1** — System overview: two OC6 canoes, twelve haptic paddle devices, one mobile controller, BLE topology.
- **FIG. 2** — Haptic paddle device hardware block diagram: nRF52840 SoC, DRV2605L haptic driver, LRA motor, IMU (6-axis), FSR, I2S amplifier, RGB LED, LiPo cell.
- **FIG. 3** — T-handle cross-section showing placement of device enclosure, FSR pad under top-hand grip surface, and motor mount.
- **FIG. 4** — BLE service and characteristic map.
- **FIG. 5** — Stroke phase state machine: CATCH → DRIVE → FINISH → RECOVERY → (CATCH).
- **FIG. 6** — Session lifecycle state diagram: IDLE → CONNECTING → CALIBRATING → READY → STARTING → STANDBY → ACTIVE → COMPLETE.
- **FIG. 7** — Sync score computation: clock-offset estimation, pairing of follower catch to nearest pacer catch, absolute gap calculation, scoring function.
- **FIG. 8** — Crew Roll-Call delivery sequence: ROLLCALL_LOAD broadcast → ROLLCALL_PLAY compensated-delay broadcast → simultaneous on-device audio composition and playback.
- **FIG. 9** — Calibration sequence: tare window → stroke-counting phase → threshold computation at 55% of peak relative acceleration.
- **FIG. 10** — Training zone and programme data model.

---

## DETAILED DESCRIPTION OF THE PREFERRED EMBODIMENTS

### 1. Hardware — Haptic Paddle Device

Each haptic paddle device (hereinafter "the Device") is housed in a waterproof enclosure mounted within or replacing the T-handle cap of a standard outrigger canoe paddle. The T-handle is the portion gripped by the paddler's upper (inboard) hand during the stroke; it is therefore the natural mechanical point at which blade-entry forces are transmitted to the paddler's body.

**1.1 Microcontroller.** In a preferred embodiment the Device uses the Seeed XIAO nRF52840 Sense system-on-chip, incorporating a 64 MHz ARM Cortex-M4 processor, a Bluetooth Low Energy (BLE) 5.0 radio, and an integrated 6-axis IMU (3-axis accelerometer + 3-axis gyroscope). The nRF52840 BLE stack is configured as a BLE peripheral (GATT server); it cannot simultaneously act as a BLE central, a hardware constraint that is fundamental to the system topology described in §4.

**1.2 Haptic Actuator.** The vibrotactile actuator is a Linear Resonant Actuator (LRA) motor driven by a Texas Instruments DRV2605L haptic driver via the I2C bus. The DRV2605L provides a library of pre-programmed haptic waveforms (including, in a preferred embodiment, a sharp single-click pattern used for the stroke-synchronisation cue) and allows software-selectable intensity (0–100%).

**1.3 Force-Sensitive Resistor (FSR).** A polymeric FSR sensing element is laminated beneath the top surface of the T-handle, spanning the area contacted by the paddler's upper-hand palm when applying downward force during the power phase of the stroke. The FSR is sampled at 20 Hz via an ADC channel and its output is normalised to a 0–100% scale. This signal, termed **Top Hand Pressure**, measures the downward force exerted by the top hand — the primary power-transfer mechanism in the outrigger stroke — and is distinct from grip force. The FSR is used for two independent purposes: (a) confirmation of genuine blade-entry events (§5.2), and (b) measurement of per-stroke power output (§8.2).

**1.4 Audio.** A MAX98357A I2S amplifier drives a small speaker mounted in the handle enclosure. Pre-recorded voice clips are stored in external QSPI flash and streamed to the amplifier during playback. Tones (single beeps for set-changeover cues) are generated directly by the firmware. The Device is the exclusive source of athlete-facing audio; the mobile controller produces no audio during training (§9).

**1.5 Indicator LED.** An RGB LED provides visual device-state feedback. Its state machine is owned entirely by the firmware and transitions with device state (e.g. pulsing blue during BLE advertising, solid green when Ready, red during an error). No BLE characteristic exists for LED control; the LED is not remotely actuated.

**1.6 Power.** A single-cell LiPo battery powers the Device. Battery voltage is mapped to a 0–100% state-of-charge estimate (4.2 V = 100%, 3.0 V = 0%) and reported to the mobile controller via the standard BLE Battery Service (UUID 0x180F). Low battery warns the coach on-screen but never blocks session start.

**1.7 Physical power control.** A tactile switch (D10) implements a 2-second hold to enter System OFF (ultra-low-power sleep, ~2 µA) and the same pin is configured as a wake-up source so pressing it again reboots the firmware.

---

### 2. Hardware — Mobile Controller

The mobile controller is a consumer Android smartphone running the **Training Controller** application (Kotlin, Jetpack Compose). The Training Controller acts as the sole BLE central in the network: it initiates and maintains simultaneous BLE connections to up to twelve Devices across two canoes, relays stroke-synchronisation commands, and runs the session-management and analytics software described in subsequent sections.

---

### 3. Training Programme Structure

A **Programme** is a named, ordered list of one or more **Zones**, created by the coach in the Configuration Planner (a web-based interface) or directly in the Training Controller. A Zone is defined by three parameters:

- **Strokes**: the number of paddle strokes in each Set (repetition).
- **Sets**: the number of repetitions of the stroke count.
- **Intensity**: a three-level categorical variable (Low, Medium, or High) encoding both the target effort level and a corresponding target Strokes Per Minute (SPM) range. In a preferred embodiment: Low → 30–45 SPM (target midpoint 38), Medium → 46–60 SPM (target midpoint 53), High → 61–80 SPM (target midpoint 70).

A **Session** is one run through an ordered list of Zones. Sessions may be loaded from a saved Programme (copying the Zone list at the time of loading, so later edits to the Programme do not affect an in-progress Session) or assembled ad-hoc on the Training Controller without saving a Programme first. Zone settings are transmitted to every Device via a 6-byte Zone Settings BLE packet before the Session starts and on each Zone transition.

---

### 4. BLE Network Topology and Phone-Mediated Haptic Relay

The system uses a star topology in which the mobile controller is the sole BLE central. All Devices are BLE peripherals. Devices do not communicate with each other.

**4.1 Architectural constraint.** The nRF52840 SoC cannot act simultaneously as a BLE peripheral (maintaining an advertising, connectable state for the mobile controller to connect to) and a BLE central (scanning for and connecting to other Devices). A mesh or peer-to-peer topology among Devices is therefore not achievable in the current hardware configuration without adding a separate BLE central radio to each Device. The phone-mediated topology is a fixed hardware constraint, not a design preference.

**4.2 Haptic relay path.** When the Pacer Device's firmware detects a valid Catch event, it transmits a BLE Stroke Event notification (UUID `…0005`) to the mobile controller. The mobile controller immediately writes a `CMD_SINGLE_PULSE` command (5 bytes, Haptic Control characteristic, UUID `…0001`) to every connected Follower Device. The Follower Devices trigger their haptic actuators upon receiving this write. The end-to-end latency from Pacer Catch detection to Follower haptic onset is approximately 50–100 ms under typical BLE connection intervals of 7.5–20 ms, and the Sync Score's "perfect" threshold (§8.1) is calibrated to this round-trip budget.

**4.3 BLE Service.** All training-related communication occurs over the Oro Haptic Service (UUID `12340000-1234-5678-1234-56789abcdef0`), a custom GATT service with the following characteristics:

| Characteristic | UUID Suffix | Direction | Purpose |
|---|---|---|---|
| Haptic Control | …0001 | Write | Start, stop, pulse, test haptics |
| Zone Settings | …0002 | Write | Configure zone parameters |
| Device Status | …0003 | Read+Notify | Device state, stroke/set progress, battery |
| Connection Status | …0004 | Read+Notify | BLE link state |
| Stroke Event | …0005 | Notify | Per-phase stroke events with timestamps |
| Calibration | …0006 | Write+Notify | Calibration commands and status |
| Audio Control | …0007 | Write | Trigger pre-recorded voice or tone prompts |
| FSR Data | …0008 | Notify | 20 Hz Top Hand Pressure stream |
| Roll-Call Control | …000A | Write | Deliver end-of-session per-seat roster |

---

### 5. Stroke Detection — Tared IMU with FSR Confirmation

#### 5.1 Tared (Baseline-Relative) Accelerometer Signal

Stroke detection is performed by the Device firmware using the IMU's accelerometer. A critical aspect of the invention is that detection operates on a **baseline-relative signal**, not on a raw absolute accelerometer reading.

At the start of each Calibration sequence (§6), the firmware samples the accelerometer for approximately one second while the Device is held still. It averages these samples to produce a per-device **Resting Baseline** — the accelerometer's reading under gravity and any manufacturing offset when at rest in the T-handle. The firmware then computes `strokeAccel = accelY − restBaseline` for every subsequent sample. The entire stroke state machine — threshold comparisons, phase transitions, calibration peak tracking — operates on this single derived value, which represents "how far the Device has moved from its resting orientation," not an absolute tilt angle or acceleration magnitude.

This approach solves a fundamental hardware consistency problem: two Devices with identical firmware but mounted in slightly different orientations within their respective T-handles, or with different accelerometer zero-point offsets (a common characteristic of MEMS sensors at unit level), will both present the same `strokeAccel = 0` at rest and identical sensitivity to motion, because each subtracts its own gravity-and-offset-loaded baseline. Without this tare step, "same firmware" does not imply "same sensitivity," causing some Devices to trigger on lighter strokes than others.

If the accelerometer reading during the tare window is non-stationary (i.e. the paddle is moving), the firmware sets a `baselineRejected` flag in the Calibration notification and enters an error state; the Training Controller prompts the coach to hold the paddle still and retry.

#### 5.2 Stroke Phase State Machine

The firmware implements a four-phase stroke state machine: **CATCH** → **DRIVE** → **FINISH** → **RECOVERY** → (next CATCH). Phase transitions are detected by comparing `strokeAccel` against thresholds derived from the device's Calibration (§6). On each phase transition the firmware emits a Stroke Event BLE notification (UUID `…0005`) containing: the phase identifier, a firmware-local millisecond timestamp, current and peak acceleration (relative to baseline), phase duration, instantaneous Top Hand Pressure percentage, and a threshold-triggered flag.

**Catch detection.** A Catch candidate is declared when `strokeAccel` exceeds the detection threshold for three consecutive IMU samples. This debounce requirement rejects electrical noise and brief mechanical bumps.

**FSR Confirmation Gate.** Following a Catch candidate, the firmware opens a short confirmation window (approximately 400 ms in a preferred embodiment). If Top Hand Pressure rises above a confirmation threshold within this window, the Catch is confirmed as valid. If pressure does not rise, the candidate is discarded as a false positive. This two-sensor AND-gate matches the biomechanics of the outrigger stroke: blade entry (IMU spike) is followed milliseconds later by the paddler loading their top hand (pressure rise). The gate degrades gracefully: if the FSR hardware malfunctions, the firmware can fall back to IMU-only detection by treating all IMU candidates as confirmed, without a code change.

**Decoupled haptic trigger and stroke counter.** The haptic synchronisation cue fires at the **Catch** phase, because that is the moment paddlers must synchronise — it cues the entry movement. The training stroke counter advances at the **Finish** phase (blade exit), because a stroke is not complete until the blade has left the water. These two events are deliberately decoupled and independently configurable. Triggering the haptic at Finish would cue too late; counting on Catch would count incomplete strokes if a paddler aborts mid-stroke.

---

### 6. Per-Device Calibration

Before a Device may participate in a Session, it must complete a Calibration sequence. Calibration is a device-level lifecycle state, not a boolean flag, and progresses through three mutually exclusive states: **Not Started → In Progress → Complete**. The firmware reports the current calibration state in the Device Status characteristic (State `0x05 = STATE_CALIBRATING`), and the Training Controller reflects this state accurately rather than inferring it from secondary flags.

Calibration proceeds in two phases:

1. **Tare phase.** The firmware samples the accelerometer for ~1 second while the Device is held still, computes the Resting Baseline (§5.1), and validates that the baseline is stable.

2. **Stroke-counting phase.** The paddler performs 50 strokes. The firmware records the peak `strokeAccel` observed across all strokes. On the 50th stroke completion, the detection threshold is set to **55% of the observed peak** relative to the Resting Baseline. This self-referential threshold means that the Calibration adapts to each individual paddler's stroke intensity, so a stronger paddler and a lighter paddler using identical hardware will both have consistent detection at their own natural stroke level.

The Calibration state survives a BLE reconnect: if a Device disconnects and reconnects during the pre-session phase, the firmware's reported Device State is the source of truth. The Training Controller does not reset Calibration status on a reconnect; it reads the Device State from the Calibration characteristic and updates accordingly. This means a transient BLE blip — common in outdoor environments — does not force a complete redo of the 50-stroke calibration sequence.

A Device is marked **Ready** (eligible to join a Session) when Calibration is Complete. "Ready" is a superset of "Connected" — a connected Device that has not yet calibrated is not Ready.

---

### 7. Pacer Designation and Session Configuration

**7.1 Seat-1-as-Pacer rule.** There is exactly one Pacer per Session. The Pacer is always the Device assigned to Seat 1 of the lead canoe. No separate "Set as Pacer" user action exists. The coach designates the Pacer by assigning a Device to Seat 1 — a single operational decision that simultaneously defines both the crew position and the synchronisation reference. If the coach reassigns seats (e.g. to correct an automatic assignment), the Training Controller automatically promotes the Device newly occupying Seat 1 to Pacer and demotes the previous occupant to Follower.

**7.2 Seat assignment.** On each BLE connection, the Training Controller auto-assigns Devices to seats in device-name lexicographic order (`Oro-XXXX` where XXXX is the BLE MAC suffix). The coach may override this via a per-Device seat selector, which swaps the newly assigned Device with whichever Device previously held the target seat.

**7.3 Pre-session start checks.** A Session cannot start unless: at least one Device is connected, a Pacer is in Seat 1, all connected Devices have completed Calibration, and at least one Zone is loaded. A loaded Programme is not required — ad-hoc Sessions composed directly on the Training Controller are permitted.

**7.4 Zone Settings packet.** Before starting, the Training Controller writes a 7-byte Zone Settings packet (UUID `…0002`) to every Device, containing: total strokes (uint16), total sets (uint8), target SPM (uint16), intensity code (uint8: 1=Low, 2=Medium, 3=High), and an `isPacer` flag (1 bit indicating whether this Device is the Pacer). The Pacer Device uses the `isPacer` flag to enable stroke detection notifications toward the phone; Follower Devices configure their haptic response mode.

---

### 8. Session Lifecycle — Standby and Athlete-Driven Start

**8.1 The Standby state.** When the coach presses Start, the Session enters a **Standby** state rather than immediately becoming Active. In Standby: all Devices are configured with Zone Settings and are actively detecting strokes; each paddle speaks the "Stand by" audio prompt; no stroke counts, Sync Scores, or timing measurements are recorded; and the session clock has not yet started. The crew may be still being positioned at the start line during this period.

**8.2 First-Catch activation.** The Session transitions from Standby to **Active** on the Pacer's first confirmed Catch. "Confirmed" means the pressure-confirmation gate (§5.2) has been satisfied for that specific Catch: the phone receives the Pacer's first Catch Stroke Event notification and then observes a Top Hand Pressure rise in the FSR data stream within the confirmation window. Only a pressure-confirmed first Catch commits the transition. An accidental bump that generates an IMU-only candidate — without a corresponding Top Hand Pressure rise — is rejected, preventing accidental session starts while lining up.

Upon activation: the session clock is backdated to the timestamp of that first Catch; the first Catch is replayed as stroke 1; haptic cues are sent to Followers; and Sync Score recording begins. The Standby period is excluded from session duration.

**8.3 Active session.** During an Active Session:
- Every Device detects its own Catches and reports them to the Training Controller via Stroke Event notifications (§4.3, UUID `…0005`).
- Only the **Pacer's** Catch triggers haptic pulses on all Devices (including the Pacer itself) and advances the zone stroke counter.
- Each **Follower's** own Catch is used solely to compute that Follower's Sync Score (§9).
- The Pacer's Finish events advance the stroke/set/zone progress.
- Zone transitions trigger audio prompts on all Devices (set-changeover beep, "last set", "next set low/medium/high" voice clips).
- The FSR data stream (UUID `…0008`) from every Device is recorded at 20 Hz for per-seat power analysis (§10.2).

**8.4 Stroke analytics.** A `StrokeAnalyzer` component on the mobile controller processes the Pacer's Stroke Event stream, assembling per-stroke records across Catch→Drive→Finish phases. After each Finish it recomputes rolling metrics including: drive ratio (time in Drive / total stroke time), stroke-to-stroke consistency (coefficient of variation), fatigue index, and SPM. A coaching engine maps anomalies to haptic patterns sent to the coach's own device, with a 15-second per-issue cooldown. Coaching cues are haptic-only — the phone produces no audio.

---

### 9. Synchronisation Score Computation

**9.1 Architecture.** Because all Devices report their own Catches to the mobile controller (§8.3), the mobile controller accumulates, for each Follower Device, a time series of Follower Catch timestamps and Pacer Catch timestamps, expressed in a common clock reference aligned via per-device clock-offset estimation.

**9.2 Clock alignment.** Each Device's firmware timestamps events using its own local millisecond counter. The mobile controller estimates each Device's clock offset as a running minimum of `(phone_receive_time − device_timestamp)` over a sliding window of recent messages, reset on reconnect. Using the minimum — rather than the mean — removes systematic bias introduced by variable BLE transmission latency: the fastest packets in the window are those with the least queuing and scheduling delay, and their round-trip is closest to the true hardware latency. This clock alignment removes the "which device is Pacer" dependency that would otherwise cause Sync Scores to vary with role assignment rather than with actual crew timing.

**9.3 Catch pairing.** For each Follower Catch, the mobile controller finds the nearest Pacer Catch within a ±400 ms window. A Follower Catch with no Pacer Catch in that window is scored as a maximum-gap stroke (fully out of sync), not silently dropped. This prevents a burst of misaligned strokes from being laundered as no-data.

**9.4 Absolute gap scoring.** The Sync Score for a given Follower stroke is:

```
gap_ms = |follower_catch_time_aligned − pacer_catch_time_aligned|

score = max(0, 100 − ((gap_ms − 50) / (300 − 50)) × 100)   [clamped 0–100]
```

Where:
- `gap_ms ≤ 50 ms` → score 100 (perfect)
- `gap_ms ≥ 300 ms` → score 0 (completely out of sync)
- Values between 50 and 300 ms are linearly interpolated.

The gap is **absolute** (unsigned): catching early (rushing) is penalised identically to catching late (lagging), because a synchronised crew catches together — being ahead of the pacer is as incorrect as being behind. This is a deliberate departure from earlier implementations that clamped negative gaps (early catches) to perfect scores, which falsely rewarded rushing.

**9.5 Session Sync Score.** The per-Follower Sync Score for a Session is the average of all per-stroke scores. If no Follower Catches were paired (e.g. a single-Device session), the Sync Score is **Not Measured** — a distinct state from a score of 0. "Not Measured" is reported truthfully rather than defaulting to a Poor rating.

**9.6 Sync Rating.** Sync Scores are mapped to a named bracket: Poor (0–49), Good (50–79), Excellent (80–100).

---

### 10. Session End — Crew Roll-Call

#### 10.1 Spoken end-of-session sequence

At Session completion, the mobile controller orchestrates a three-part spoken sequence on every Device:

1. **"Session complete"** — immediately on zone completion.
2. **"Stand by for results"** — 2 seconds later.
3. **Crew Roll-Call** — approximately 30 seconds after "Stand by for results" (configurable delay to allow the crew to come to rest and listen).

The mobile controller itself produces no audio at any point. All athlete-facing audio originates from the Devices.

#### 10.2 Per-seat result computation

Before the Crew Roll-Call, the mobile controller computes a **Session Outcome** comprising:

- **Crew Sync Rating**: the Sync Rating derived from the average Sync Score across all Followers.
- **Per-seat Sync Rating**: each Follower's individual Sync Rating (Pacer has none).
- **Per-seat Power Range**: each seat's power output tier for the session, derived from the per-stroke Peak Top Hand Pressure (maximum FSR reading during the Drive phase) recorded from that Device's own FSR stream. Power Range values: Light (0–25%), Moderate (26–50%), Strong (51–75%), Maximum (76–100%).

#### 10.3 Roll-Call roster encoding

The mobile controller encodes the full per-seat roster into a `CMD_ROLLCALL_LOAD` packet (Roll-Call Control characteristic, UUID `…000A`). The packet contains: the crew Sync Rating, the number of occupied seats, and for each seat: canoe number (1–2; 0 = single-canoe session), seat number (1–6), a Pacer flag, the seat's Sync Rating code, and the seat's Power Range code. The total packet length for a full 12-seat, 2-canoe crew is 39 bytes, fitting within a single ATT MTU write when the MTU is negotiated to 247 bytes on connect.

#### 10.4 Simultaneous playback delivery

To deliver the Crew Roll-Call with intelligible voice on all Devices simultaneously — rather than overlapping, temporally drifted voices that garble — the mobile controller uses a compensated-delay protocol:

1. The mobile controller broadcasts `CMD_ROLLCALL_LOAD` to all Devices, loading the roster onto each Device's local memory.
2. The mobile controller records a reference time `t0` immediately before the first `CMD_ROLLCALL_PLAY` write.
3. For each Device, the mobile controller computes `delay_ms = TARGET_MS − (now − t0)` (clamped ≥ 0), where `TARGET_MS` is set above the worst-case serialised write spread across all connected Devices (e.g. 300 ms for twelve Devices). The delay is encoded as a byte in units of 10 ms.
4. The mobile controller sends `CMD_ROLLCALL_PLAY` with this per-device delay value to each Device using BLE Write Without Response (to minimise per-write latency).
5. Each Device, on receiving PLAY, waits its assigned delay, then composes and speaks the entire roll-call read-out locally from its stored voice clips.

Because each Device composes speech locally from its own clip library (rather than streaming audio from the mobile controller), there is no ongoing Bluetooth audio synchronisation burden. The load/play separation ensures that the larger roster transfer (`CMD_ROLLCALL_LOAD`) completes well before the simultaneous trigger, eliminating any timing risk from the data transfer itself.

The spoken read-out order is: crew aggregate ("Team sync: Excellent") followed by each occupied seat in canoe+seat ascending order, Pacer first: "Seat 1, pacer, power: Strong. Seat 2, sync: Good, power: Moderate. …". When two canoes are present, canoe number is announced: "Canoe 1, Seat 1, …". A Follower with no Sync Score is read as "sync: not measured."

---

### 11. Configuration Planner (Web Interface)

The Configuration Planner is a web application (React, served locally or from the cloud) through which coaches design and manage Programme libraries. Programmes created in the Configuration Planner are exported and loaded onto the Training Controller's local storage. The Configuration Planner is a post-MVP component; Programme creation is also available directly within the Training Controller for MVP use.

---

### 12. Multi-Canoe Support

The system architecture is designed to support two OC6 canoes (twelve Devices) connected to a single mobile controller. Seat identity is globally unique as a (canoe, seat) tuple, with canoe numbers 1–2 and seat numbers 1–6. The Pacer is always Seat 1 of Canoe 1 (the lead canoe). The Training Controller manages up to twelve simultaneous BLE connections, tracking each Device's (canoe, seat) identity, calibration state, stroke events, and FSR data independently. The Roll-Call roster encodes canoe membership for the two-canoe read-out variant.

---

## CLAIMS

*(The following claims are exemplary for provisional filing purposes. Formal independent and dependent claims should be drafted and reviewed by patent counsel for the non-provisional application.)*

**Claim 1.** A haptic synchronisation system for a multi-person paddle sports crew, the system comprising:
a plurality of haptic paddle devices, each device being integrated into a T-handle of a respective paddle and comprising: an inertial measurement unit (IMU) configured to detect stroke phase transitions based on paddle acceleration; a force-sensitive resistor (FSR) positioned at a top-hand grip surface of the T-handle configured to measure downward force applied by a paddler's upper hand; a vibrotactile actuator configured to generate haptic feedback; a wireless transceiver; and a processor executing firmware that detects a blade-entry event (Catch) based on the IMU signal, confirms the Catch by requiring a concurrent rise in FSR signal within a confirmation window, and transmits a timestamped Catch notification via the wireless transceiver;
a mobile controller configured to: maintain concurrent wireless connections to all haptic paddle devices; receive Catch notifications from a designated Pacer device; and transmit a haptic trigger command to all non-Pacer Follower devices upon receiving a Pacer Catch notification; wherein each Follower device triggers its vibrotactile actuator upon receiving the haptic trigger command.

**Claim 2.** The system of claim 1, wherein the haptic trigger fires at the blade-entry event (Catch) and a training stroke counter increments at a blade-exit event (Finish), and wherein these two events are independently detected and independently configurable.

**Claim 3.** The system of claim 1, wherein stroke detection by each haptic paddle device uses a tared, baseline-relative accelerometer signal computed by: sampling accelerometer output during a stationary resting window at calibration start; computing a per-device Resting Baseline as the average of the resting samples; and subtracting the Resting Baseline from all subsequent accelerometer readings to produce a motion-relative stroke signal.

**Claim 4.** The system of claim 3, wherein a detection threshold is set to 55% of the maximum baseline-relative acceleration observed during a calibration stroke-counting phase consisting of at least 50 strokes, such that the detection threshold adapts to each individual paddler's stroke intensity.

**Claim 5.** The system of claim 1, wherein the mobile controller computes a per-Follower Synchronisation Score as the session average of absolute timing gaps between each Follower Catch timestamp and the nearest Pacer Catch timestamp within a pairing window, wherein the absolute gap penalises catching before the Pacer equally to catching after the Pacer.

**Claim 6.** The system of claim 5, wherein Follower and Pacer Catch timestamps are each expressed in a device-local clock, and the mobile controller converts them to a common timeline by estimating each device's clock offset as a sliding-window minimum of the difference between mobile controller receive time and device-local timestamp, so that variable BLE transmission latency is removed as a systematic bias.

**Claim 7.** The system of claim 1, wherein the mobile controller designates the Pacer as the haptic paddle device assigned to a predetermined seat position (Seat 1) in a crew configuration, with no separate pacer-designation user action required, such that assigning a device to Seat 1 simultaneously constitutes Pacer designation.

**Claim 8.** The system of claim 1, further comprising a Standby state entered after a session-start command and before a session Active state, wherein in the Standby state all devices are configured and detecting strokes but no timing measurements are recorded, and wherein the session transitions from Standby to Active on the Pacer's first pressure-confirmed Catch.

**Claim 9.** The system of claim 8, wherein the pressure confirmation of the first Pacer Catch is performed on the mobile controller by observing a rise in the FSR data stream from the Pacer device within a confirmation window following receipt of the first Catch notification, such that accidental bumps without Top Hand Pressure rise do not trigger session start.

**Claim 10.** The system of claim 1, wherein each haptic paddle device stores a library of pre-recorded voice clips in local non-volatile memory and an I2S audio amplifier, and wherein the mobile controller delivers an end-of-session performance roster to every haptic paddle device and triggers simultaneous on-device spoken playback of a Crew Roll-Call comprising per-seat synchronisation and power ratings, wherein the mobile controller produces no audio output.

**Claim 11.** The system of claim 10, wherein simultaneous playback is achieved by: the mobile controller transmitting a LOAD command containing the full per-seat roster to every device prior to playback; recording a reference time immediately before transmitting a PLAY command; computing for each device a start delay equal to a target delay minus elapsed time since the reference time; and transmitting the PLAY command with the per-device start delay value to each device using write-without-response, such that each device begins playback at approximately the same wall-clock instant.

**Claim 12.** The system of claim 10, wherein the Crew Roll-Call includes, for each occupied seat: a seat identifier; for the Pacer device, a power range only; and for each Follower device, a synchronisation rating and a power range; and wherein a Follower with no measured synchronisation data is announced as "not measured" rather than assigned a poor rating.

**Claim 13.** The system of claim 1, wherein each haptic paddle device reports its own Catch events to the mobile controller during a session, and wherein the mobile controller subscribes to Catch notifications from every device; wherein Pacer Catch notifications trigger Follower haptics; and wherein Follower Catch notifications are used exclusively to compute that Follower's Synchronisation Score, such that each Follower's score reflects its own timing relative to the Pacer rather than a crew average.

**Claim 14.** The system of claim 1, wherein a per-seat power range is derived from the peak FSR reading during the Drive phase of each stroke, recorded from each device's own FSR data stream independently, such that every seat's power output is measured from that seat's own paddle rather than inferred from the Pacer.

**Claim 15.** A method for delivering simultaneous spoken performance feedback to a plurality of wireless haptic paddle devices, the method comprising: computing, on a mobile controller, a per-seat performance roster comprising a synchronisation rating and a power range for each occupied seat; transmitting the roster to every haptic paddle device via a first wireless command (LOAD); recording a reference timestamp immediately before transmitting a second wireless command (PLAY) to each device; computing a per-device start delay as a target delay minus elapsed time since the reference timestamp; transmitting the PLAY command with the computed per-device start delay to each device; and each device, upon receiving PLAY, waiting its assigned delay then composing and speaking the entire performance read-out from locally stored audio clips.

---

## ABSTRACT

A haptic training system for outrigger canoe crews integrates a wireless haptic device into each paddle's T-handle. Each device contains an IMU, a force-sensitive resistor under the top-hand grip, a vibrotactile actuator, and a microcontroller with a BLE radio. A mobile controller (Android) maintains simultaneous BLE connections to up to twelve devices across two canoes, acting as the sole relay between devices. When the designated Pacer device detects a blade-entry (Catch) event — confirmed by a concurrent rise in Top Hand Pressure — the mobile controller broadcasts a haptic trigger to all Follower devices, which vibrate in sync. Stroke detection uses a per-device tared accelerometer baseline, making sensitivity consistent across devices regardless of mounting orientation or sensor offset. A calibration sequence sets each device's detection threshold at 55% of its own peak relative acceleration. Sessions enter a Standby state after the coach presses Start; the session becomes Active only on the Pacer's first pressure-confirmed Catch, with the session clock backdated to that moment. During a session every device reports its own Catches; per-Follower Synchronisation Scores are computed as the absolute timing gap between each Follower Catch and the nearest Pacer Catch, in a clock-offset-corrected common timeline. At session end the mobile controller computes per-seat Synchronisation Ratings and Power Ranges, loads the full per-seat roster onto every device, and triggers simultaneous on-device spoken delivery of the Crew Roll-Call using a compensated-delay protocol — every paddle speaks the full crew performance read-out in unison, while the mobile controller itself remains silent throughout.

---

*End of Provisional Patent Application*

*Prepared: 2026-06-04*
*Based on engineering records: CONTEXT.md, BLE_PROTOCOL.md, ADR-0001 through ADR-0017*
