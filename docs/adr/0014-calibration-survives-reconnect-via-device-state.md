# Calibration survives a reconnect; the firmware's DeviceState is the source of truth

A device's **Calibration** state (ADR-0003) is no longer wiped every time the phone's BLE device list re-emits. A *completed* calibration is preserved across reconnects, and the firmware's own reported **DeviceState** is treated as the authority for whether a device is still calibrated:

- The phone preserves `CalibrationState.Complete` across reconnects (an in-progress calibration is not preserved, to avoid stranding the calibration dialog).
- The firmware broadcasts its `DeviceState` on a ~3-second heartbeat while connected. The phone maps it back to calibration state: `IDLE → NotStarted`, `READY/TRAINING/PAUSED/COMPLETE → Complete`, `CALIBRATING/ERROR → ignored` (left to the live calibration flow). The mapping never overrides an in-progress calibration, so the heartbeat cannot flicker the dialog.
- Cancelling calibration now leaves the firmware in `IDLE`, not `READY`, so `READY` reliably means "calibrated."

## Why

The previous code deliberately reset calibration to `NotStarted` on every reconnect, reasoning that a power-cycle clears the firmware's RAM anyway. But a **Bluetooth blip is not a power-cycle** — the device stays powered, its calibration (threshold + resting baseline) is intact, yet the coach was forced to recalibrate. With flaky BLE this happened often and was a major source of "I can't start a session" confusion.

The firmware already knows the truth: it holds its `DeviceState` and writes it to a readable, notifiable characteristic. The phone was parsing that byte and throwing it away. So the authoritative signal existed; we just weren't using it.

## Alternatives considered

- **Read the DeviceState on connect** instead of a heartbeat. Rejected: the firmware does not push status on connect (notifications aren't subscribed yet at that point), so the phone would have to issue a GATT read during connection — and all GATT operations must go through the single serialized queue (bypassing it silently drops operations). A periodic heartbeat avoids touching that fragile path entirely and self-heals if a packet is missed.
- **Just preserve calibration across reconnects, unconditionally.** Rejected: after a genuine reboot the device is uncalibrated, and the phone would show a stale "Complete," letting a session start with the firmware on its default (uncalibrated) threshold. Trusting the reported DeviceState corrects that within a few seconds.

## Trade-off

Cost: a small status notification every ~3s per connected device (negligible), and the phone briefly (≤ one heartbeat) shows a stale "Complete" after a real reboot before correcting. Benefit: calibration no longer evaporates on routine BLE blips, which was the dominant pre-session frustration.
