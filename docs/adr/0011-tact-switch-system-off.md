# Tact Switch Triggers System OFF; Wake Reboots the Firmware

The paddle's tactile power switch (D10 / P1.15, configured input-pullup) puts the nRF52840 into deep-sleep via `sd_power_system_off()` after a 2-second hold. Wake is by the same button, configured as a `GPIO sense` source, which causes a full chip reset — firmware boots from scratch, re-advertises BLE, and plays the `AUDIO_POWER_ON` ("Oro") voice prompt at training volume. During the hold, the active LED state ramps brightness to zero over 2 s; releasing the button before 2 s snaps brightness back to the current state's normal value and cancels shutdown. A press shorter than the hold threshold does nothing.

System OFF is the only mode that delivers true "off" behaviour matching user expectation when a paddle is stowed between sessions — measured standby current is ~1.5 µA, giving a stored paddle effectively indefinite shelf life on a charged LiPo. The trade-off is that wake is not instantaneous: the firmware reboot adds ~1–2 s before BLE advertising restarts and the device can be re-connected from the Training Controller. The "Oro" prompt fills this gap with an unambiguous audio confirmation that the button press worked.

## Considered Options

- **Low-power idle loop (BLE disconnect, IMU off, MCU running)** — rejected. Standby current ~mA rather than µA; a charged paddle would drain in days. Instant wake doesn't justify the battery cost for a device that spends most of its life stowed.
- **Soft "park" (keep advertising, just stop sampling)** — rejected. Solves no real problem: the LED still draws, BLE still draws, and the coach has no way to know the device is "off" from across the canoe.
- **Single-press to power off** — rejected. Too easy to trigger accidentally mid-session through the paddle's t-handle grip. 2-s hold with visual ramp-down feedback eliminates this class of mistake.

## Consequences

- Pin D10 / P1.15 is reserved for the power switch and configured as a wake source; no other firmware feature may claim it.
- The phone-side BLE connection drops on power-off. The Training Controller must treat this as a normal disconnect (not an error) and accept re-connection on wake without requiring a re-pair.
- Session state (current zone, stroke count, calibration) does **not** survive power-off — System OFF clears RAM. Calibration thresholds are re-computed each session; ADR-0003 already requires a fresh calibration pre-session, so this aligns with existing behaviour.
- The "Oro" boot prompt fires on every wake, not just first power-up. This is intentional: it is the confirmation signal for the button press.
- A future hardware revision considering a separate "reset" button (vs. power button) would need its own ADR; this decision binds D10 to the power-switch semantics only.
