# Oro Haptic Paddle

A haptic training system for OC6 outrigger canoe crews. Each device is built into the paddle's t-handle; it vibrates in sync with the pacer's stroke to improve crew synchronization.

## Language

### Training structure

**Zone**:
A training block defined by a stroke count, a set count, and an intensity level.
_Avoid_: interval, block, round

**Set**:
One repetition of the stroke count within a zone.
_Avoid_: rep, repetition, round

**Strokes** (training unit):
The number of paddle strokes to complete in each set.
_Avoid_: stroke count (ambiguous — see Stroke Event)

**Intensity**:
The effort level of a zone: Low, Medium, or High.
_Avoid_: zone color, zone level, zone name, Recovery, Endurance, Tempo, Threshold, VO2 Max, Anaerobic

**Programme**:
A named, reusable ordered list of zones designed by a coach. Created in the Configuration Planner and applied to one or more Sessions. Exists independently of any live session.
_Avoid_: workout plan, training plan, schedule

**Session**:
A complete training run through an ordered list of Zones. Those Zones are usually copied from a loaded **Programme**, but may also be assembled directly on the Training screen without saving a Programme first — an **ad-hoc Session** (ADR-0013). A Session always runs the Zones currently loaded, whatever their source.
_Avoid_: workout, training run

**SPM**:
Strokes Per Minute — the pacing rate for a zone (30–80 range).

### Devices and roles

**Device**:
A single nRF52840 hardware unit built into the t-handle of one paddler's paddle.
_Avoid_: node, unit, peripheral, wrist device

**Canoe**:
One OC6 outrigger canoe with up to 6 seats. The system supports up to 2 canoes simultaneously on one Training Controller. The two canoes are not independent hulls on the water: they are lashed together with cross-beams into a single rigid **V12** (12-seat) hull that moves as one — so the crew always travels at a single speed, whether paddling a lone OC6 or a joined V12.
_Avoid_: boat, crew (crew refers to all paddlers across all canoes)

**Seat**:
A paddler's physical position within their canoe, numbered 1–6 front to back. A device is identified by canoe + seat together — seat number alone is not unique across two canoes.
_Avoid_: position, slot, index

**Pacer**:
The single device whose Catch sets the timing reference for all Followers across all canoes. There is exactly one Pacer per session. The Pacer is always the device assigned to Seat 1 — the coach designates the Pacer by assigning a device to that seat.
_Avoid_: leader, master

**Follower**:
Any device that is not the Pacer. Receives haptic cues triggered by the Pacer's Catch. All non-Pacer devices are Followers regardless of their seat number or canoe.
_Avoid_: slave, non-pacer

### Stroke detection

**Catch**:
The moment the paddle blade enters the water. The primary synchronisation event — haptics fire at this point.
_Avoid_: entry, blade entry, water entry

**Drive**:
The power phase of a stroke, after Catch and before Finish.

**Finish**:
The moment the paddle exits the water. Training progress (stroke count) advances at this point.
_Avoid_: exit, release

**Recovery**:
The idle phase between Finish and the next Catch.
_Avoid_: rest phase, inter-stroke phase

**Stroke Event**:
A BLE notification emitted by a device for each phase transition: Catch, Drive, Finish, or Recovery.
_Avoid_: stroke (alone — ambiguous with training unit), IMU event

### Paddle technique

**Top Hand Pressure**:
The downward force applied by the paddler's top hand on the paddle grip during a stroke. Measured by the FSR sensor. The paddler pushes down, not grips — this is the primary power transfer mechanism.
_Avoid_: grip force, grip pressure, handle pressure, FSR force

**Peak Pressure**:
The maximum Top Hand Pressure recorded during the Drive phase of a stroke. The primary stroke quality metric — reported as a Power Range in the session summary.
_Avoid_: max force, grip peak, FSR peak

**Power Range**:
A named bucket for Peak Pressure: Light (0–25%), Moderate (26–50%), Strong (51–75%), Maximum (76–100%). Boundaries are placeholders pending field validation.
_Avoid_: pressure zone, force level, intensity (reserved for Zone intensity)

### Device lifecycle

**Calibration**:
A pre-session setup step where a device first establishes its **Resting Baseline** (its reading while held still) and then samples the paddler's stroke motion to set its detection threshold. Detection measures movement *relative to rest*, not absolute tilt — so two devices with identical firmware can still be made to agree (ADR-0012). A device must complete calibration before it is ready for training. Calibration progresses through three mutually-exclusive states — **Not Started**, **In Progress**, **Complete** — mirroring the firmware's calibration lifecycle (ADR-0003). This is a single state, not a pair of booleans.
_Avoid_: tuning, threshold setup; isCalibrating/isCalibrationComplete flag pair

**Resting Baseline**:
A device's accelerometer reading while held still, captured at the start of Calibration and subtracted from every subsequent reading. Removes both gravity's pull on the measured axis and each unit's built-in sensor offset, so detection responds to movement rather than orientation (ADR-0012).
_Avoid_: zero point, offset, tare (use these only as internal/implementation terms)

**Ready**:
The state of a device whose Calibration is Complete and can participate in a session.
_Avoid_: calibrated, connected (connected means BLE link only, not readiness for training)

### Speed

**Canoe Speed**:
The GPS-measured ground speed of the canoe (a lone OC6 or a joined V12) carrying the Training Controller, expressed in km/h to one decimal place. Because the phone rides on the hull and the hull moves as one, this single value is the speed of the whole crew. During **High** intensity zones it is spoken aloud on every device, in unison, as a **bare number** with no unit word — e.g. "fourteen point three". The phone measures it but stays silent; the devices announce it (ADR-0016). It is not announced during Low or Medium zones.
_Avoid_: boat speed, crew speed (crew spans hulls, but this is one value for one hull), velocity, GPS speed (the term is Canoe Speed; GPS is how it is measured)

### Synchronisation

**Sync Score**:
A 0–100 per-device score measuring how closely a follower's Catch aligns in time with the pacer's Catch. It is the **absolute** timing gap, so catching early (rushing ahead of the pacer) counts against it exactly as much as catching late (lagging): 100 = within 50ms either way, 0 = 300ms or more off. The gap is measured in a common timeline built from each device's own Catch timestamp (the phone aligns the devices' clocks), and each follower Catch is paired to the nearest pacer Catch. When there are no follower Catches to compare — e.g. a single-device session — the Sync Score is **Not Measured**, which is distinct from a Poor score (ADR-0015).
_Avoid_: sync percentage, match score; "behind" (the gap is symmetric, not just lateness)

**Crew Sync**:
The aggregate synchronisation state across all connected followers in a session.

**Sync Rating**:
A named bracket for a Sync Score — a single Follower's or the crew's overall: Poor (0–49), Good (50–79), Excellent (80–100).
_Avoid_: sync level, sync tier, sync category

**Session Outcome**:
The computed end-of-session result: a Sync Rating, a Power Range, and the raw Sync Score. Produced once at session end from the recorded stroke latencies and the recorded strokes. Pure data — does not know about audio prompts or screens.
_Avoid_: result, outcome data, score

**Session Summary**:
The end-of-session report. Built from a Session Outcome. The Training Controller (the phone) shows a crew-wide aggregate and a per-canoe breakdown on screen but stays **silent** — all athlete-facing audio comes from the devices, delivered as the **Crew Roll-Call**.
_Avoid_: results, stats, report

**Crew Roll-Call**:
The spoken end-of-session read-out, played on every device in unison. It announces the crew's overall **Sync Rating**, then goes through every occupied **Seat** in order — identified by **Canoe + Seat** when more than one canoe is present — giving that paddler's **Sync Rating** and **Power Range**, so the whole crew hears who synced and who didn't. The **Pacer** (Seat 1) has no **Sync Score**, so its entry names it as the pacer and reads its **Power Range** only. A Follower with no measured sync is read as Not Measured rather than given a rating (ADR-0015).
_Avoid_: summary voice prompt, leaderboard, results read-out

**Standby**:
The state a **Session** is in after the coach presses Start but before it is **Active**: every device is configured and detecting, and each paddle has spoken the **"Stand by"** prompt, but nothing is yet measured. The Session leaves Standby and becomes Active the instant the **Pacer** takes its first **Catch** (pressure-confirmed). The armed waiting time is excluded from session duration (ADR-0017).
_Avoid_: armed, ready (Ready is a device-level term), waiting, starting (Starting is the brief configuring transient before Standby)

**Countdown** _(retired — ADR-0017)_:
Formerly the synchronised start signal (tones + a "go" buzz so the crew's first **Catch** was together, ADR-0008). Replaced by **Standby**: the Session now begins on the Pacer's first Catch rather than on a synchronised go, so there is no pre-stroke countdown. Term kept only so older references resolve.
_Avoid_: using as a live concept — it no longer fires

### Tools

**Configuration Planner**:
The web UI. Post-MVP tool for designing programmes on a laptop. Not used in MVP — programme creation happens in the Training Controller.
_Avoid_: web app, frontend, dashboard

**Training Controller**:
The Android app. Manages BLE connections, runs sessions, and displays live sync data.
_Avoid_: Android app, mobile app

## Relationships

- A **Programme** is a named ordered list of one or more **Zones**, created in the **Configuration Planner**
- A **Session** runs through an ordered list of **Zones**; those Zones are usually copied from a loaded **Programme**, but may be built directly for an ad-hoc Session (ADR-0013)
- A **Zone** has one **Intensity** (Low, Medium, or High)
- A **Zone** runs for N **Sets**, each of N **Strokes**
- A **Device** occupies exactly one **Seat** within one **Canoe**; its identity is **Canoe + Seat**
- There is exactly one **Pacer** per **Session**, designated by the coach; all other devices are **Followers**
- A **Device** must complete **Calibration** before it is **Ready** to join a **Session**
- During a Session **every Device detects its own Catch** and reports it to the **Training Controller**; only the **Pacer**'s Catch triggers haptics on all **Followers** (phone-mediated — devices do not signal each other directly) and advances the stroke count, while each **Follower**'s own Catch is used only to score its synchronisation (ADR-0015)
- **Catch** detection is *intended* to require an IMU trigger confirmed by a **Top Hand Pressure** rise within a short window (ADR-0004); today detection is IMU-only — the pressure-confirmation gate is still an open item (see Open questions below)
- A **Follower**'s **Sync Score** is the absolute timing gap between its Catch and the nearest **Pacer** Catch (ADR-0015)
- A stroke's **Peak Pressure** is the maximum **Top Hand Pressure** during the **Drive** phase; it maps to a **Power Range**
- A **Session** produces a **Session Summary**: a crew **Sync Rating** plus, per **Seat**, that paddler's **Sync Rating** and **Power Range**. It is displayed in the **Training Controller** and spoken on the devices as the **Crew Roll-Call** (the phone itself stays silent)

## Example dialogue

> **Dev:** "Should we increment the stroke counter on Catch or Finish?"
> **Domain expert:** "Finish — the stroke isn't complete until the blade exits the water. The haptic fires at Catch, but the count advances at Finish."

> **Dev:** "The zone is set to High intensity — what SPM does that map to?"
> **Domain expert:** "61–80 SPM. Intensity is the user-facing concept; SPM is the derived value sent over BLE."

## Open questions

- **Sync Rating boundaries**: Poor/Good/Excellent split at 50/80 are placeholders — to be validated against field data.
- **Power Range boundaries**: Light/Moderate/Strong/Maximum split at 25%/50%/75% are placeholders — to be validated against field data.
- **Sync Score thresholds**: The 50ms (perfect) and 300ms (zero) latency bounds are placeholders — not validated against biomechanical perception thresholds or real BLE round-trip measurements.
- **FSR secondary Catch confirmation**: `sessionAverageFsrPeak()` now feeds Power Range in the Session Summary. Remaining: use Top Hand Pressure as a secondary confirmation gate for Catch detection.
- **First-Catch confirmation thresholds**: The Standby→Active gate uses a Top Hand Pressure rise within a window (~400ms) after the Pacer's first Catch to reject accidental bumps (ADR-0017). The window length and the confirmation pressure % are placeholders — to be validated against field data.
- **Roll-Call delay**: The 30s gap between "Stand by for results" and the Crew Roll-Call (ADR-0017) is a placeholder — to be validated against how long crews actually need to settle.
- **Canoe Speed timing**: The ~1–2 s GPS smoothing window, the "3 strokes from the end of the set" trigger point, and the realistic speed cap used for clip composition (ADR-0018) are placeholders — to be validated on the water. Open question whether the spoken value should later become the set's *peak* speed rather than the instantaneous reading.
- **Calibration removal**: Firmware currently requires a per-device calibration step to set the IMU stroke detection threshold (55% of the paddler's peak acceleration). The fixed default (1.0g, derived from real paddle data) may be sufficient for all crew members. To be validated against field data — if consistent, the Calibration step and Ready state could be removed from the pre-session flow.

## Known constraints to resolve

- **Firmware 6-device limit**: firmware and Android BLE code caps at 6 simultaneous connections. Two OC6 canoes requires 12. This limit must be lifted.
