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
A complete training run through all zones in a Programme, in order.
_Avoid_: workout, training run

**SPM**:
Strokes Per Minute — the pacing rate for a zone (30–80 range).

### Devices and roles

**Device**:
A single nRF52840 hardware unit built into the t-handle of one paddler's paddle.
_Avoid_: node, unit, peripheral, wrist device

**Canoe**:
One OC6 outrigger canoe with up to 6 seats. The system supports up to 2 canoes simultaneously on one Training Controller.
_Avoid_: boat, crew (crew refers to all paddlers across all canoes)

**Seat**:
A paddler's physical position within their canoe, numbered 1–6 front to back. A device is identified by canoe + seat together — seat number alone is not unique across two canoes.
_Avoid_: position, slot, index

**Pacer**:
The single device whose Catch sets the timing reference for all Followers across all canoes. There is exactly one Pacer per session. Designated by the coach, not assumed to be any specific seat.
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
A pre-session setup step where a device samples the paddler's stroke motion to set its detection threshold. A device must complete calibration before it is ready for training.
_Avoid_: tuning, threshold setup

**Ready**:
The state of a device that has completed calibration and can participate in a session.
_Avoid_: calibrated, connected (connected means BLE link only, not readiness for training)

### Synchronisation

**Sync Score**:
A 0–100 per-device score measuring how closely a follower's Catch aligns with the pacer's Catch. 100 = within 50ms; 0 = 300ms or more behind.
_Avoid_: sync percentage, match score

**Crew Sync**:
The aggregate synchronisation state across all connected followers in a session.

**Sync Rating**:
A named bracket for the crew's overall Sync Score: Poor (0–49), Good (50–79), Excellent (80–100).
_Avoid_: sync level, sync tier, sync category

**Session Summary**:
An end-of-session report covering Sync Rating and Power Range. The Training Controller displays both a crew-wide aggregate and a per-canoe breakdown. All devices receive the same crew-wide voice prompt — one of 12 pre-recorded prompts selected by Sync Rating (Poor/Good/Excellent) × Power Range (Light/Moderate/Strong/Maximum).
_Avoid_: results, stats, report

### Tools

**Configuration Planner**:
The web UI. Post-MVP tool for designing programmes on a laptop. Not used in MVP — programme creation happens in the Training Controller.
_Avoid_: web app, frontend, dashboard

**Training Controller**:
The Android app. Manages BLE connections, runs sessions, and displays live sync data.
_Avoid_: Android app, mobile app

## Relationships

- A **Programme** is a named ordered list of one or more **Zones**, created in the **Configuration Planner**
- A **Session** runs through the **Zones** of a **Programme** in order
- A **Zone** has one **Intensity** (Low, Medium, or High)
- A **Zone** runs for N **Sets**, each of N **Strokes**
- A **Device** occupies exactly one **Seat** within one **Canoe**; its identity is **Canoe + Seat**
- There is exactly one **Pacer** per **Session**, designated by the coach; all other devices are **Followers**
- A **Device** must complete **Calibration** before it is **Ready** to join a **Session**
- The **Pacer** emits **Stroke Events**; the **Training Controller** uses the **Catch** event to trigger haptics on all **Followers** (phone-mediated — devices do not signal each other directly)
- **Catch** detection requires an IMU trigger confirmed by a **Top Hand Pressure** rise within a short window
- A **Follower**'s **Sync Score** is calculated from the latency between the **Pacer**'s Catch and the **Follower**'s Catch
- A stroke's **Peak Pressure** is the maximum **Top Hand Pressure** during the **Drive** phase; it maps to a **Power Range**
- A **Session** produces a **Session Summary** with a **Sync Rating** and a crew **Power Range**; the summary is displayed in the **Training Controller** and broadcast to all devices as a pre-recorded voice prompt

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

## Known constraints to resolve

- **Firmware 6-device limit**: firmware and Android BLE code caps at 6 simultaneous connections. Two OC6 canoes requires 12. This limit must be lifted.
- **Seat 1 = Pacer assumption**: Android code hardcodes seat 1 as the Pacer. Must become coach-designated.
