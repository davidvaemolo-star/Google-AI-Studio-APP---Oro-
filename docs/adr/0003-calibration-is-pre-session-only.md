# Calibration is a pre-session device state, not a session-time operation

Calibration always happens before a session starts. A device is not considered Ready until calibration is complete. The correct device lifecycle is: Connected → Calibrating → Ready → (session starts).

Calibration must not be modelled as a boolean flag alongside connection status — it is a distinct device state that gates session participation. The firmware already emits `STATE_CALIBRATING (0x05)` as a first-class state; the Android model should reflect this rather than tracking it as `isCalibrating: Boolean` on `HapticDevice`.
