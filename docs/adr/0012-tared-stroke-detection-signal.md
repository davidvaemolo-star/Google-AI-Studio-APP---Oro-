# Stroke detection uses a tared, relative-to-rest signal

Stroke detection compares accelerometer movement against the device's own **Resting Baseline**, not against a raw absolute reading. At the start of Calibration the device samples ~1 second while held still, averages those readings into `restBaseline`, and from then on subtracts that baseline from every reading. The single detection input becomes `strokeAccel = accelY - restBaseline` — "how far the device has moved from rest." Because the entire stroke state machine (calibration peak tracking, the calibration trigger, the 55%-of-peak threshold, and the drive/finish/recovery transitions) reads from that one value, this makes the whole detector orientation- and offset-corrected in one place.

## Why

Two devices with identical hardware and identical firmware were observed to have different sensitivity during Calibration — one fired haptics on smaller movements than the other. The cause was that detection watched the raw `accelY` axis, which carries (a) the static pull of gravity projected onto that axis, which varies with how the device seats in the t-handle, and (b) each accelerometer's built-in zero-point offset, which varies unit to unit. Comparing that gravity-and-offset-loaded value against a fixed absolute threshold meant "same firmware" never implied "same sensitivity." Subtracting a per-device resting baseline removes both terms, so units that are mounted and held the same way now agree.

## Alternatives considered

- **Tilt-independent magnitude** (`√(x²+y²+z²) − 1g`): fully orientation-proof, but always positive. The stroke state machine relies on *signed* values — it recognises recovery and finish by the signal going negative — so magnitude would force a full rewrite of the detector and a re-tune of every threshold. Rejected as disproportionate to the problem; the tare fixes the observed symptom while preserving the working signed model. Magnitude remains a future option if orientation *drift during the stroke* (which a static baseline does not correct) proves significant in the field.
- **Explicit "hold still" → "now paddle" UI step in the Training Controller**: more robust if the coach is already moving when calibration begins, but it changes the Android calibration flow and the BLE handshake. Rejected in favour of a firmware-only window, because the coach already taps "calibrate" before paddling, so the paddle is normally at rest at `CAL_CMD_START`. A jumpiness check rejects the baseline and re-prompts if it was not.

## Caveats

- A static baseline corrects for resting orientation and sensor offset, not for the changing gravity projection *during* a stroke as the paddle rotates. The stroke's own motion dominates, so this is accepted; magnitude (above) is the escape hatch if it is not.
- The absolute constants in firmware (the `0.25g` calibration trigger and the `1.0g` default threshold) now mean "movement from rest" rather than an absolute reading, so they may want field re-tuning. The self-referential 55%-of-peak threshold adapts automatically.
- `restBaseline` lives in RAM and is recomputed each Calibration, consistent with the detection threshold not being persisted across power cycles.
