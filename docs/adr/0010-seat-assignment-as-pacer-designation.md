# Seat Assignment as Pacer Designation

The coach designates the Pacer by assigning a device to Seat 1 — there is no separate "Set as Pacer" action. Seat 1 = Pacer is a hard rule, not a default. The Training Controller auto-assigns seats by device name order on connect, with the coach able to override via a per-device seat dropdown (swapping on conflict). This makes the designation mechanism concrete and coachable ("put your best stroke in Seat 1") while keeping the UI to a single pre-session step. A dedicated Pacer button was rejected because it decouples role from position, requiring the coach to manage two independent concepts that are operationally the same decision.

## Considered Options

- **Explicit "Set as Pacer" button per DeviceCard** — rejected. Forces coach to think about "Pacer" as a separate concept from seating. In practice, assigning seat position and designating Pacer are the same decision: the coach picks who paddles in which position.
- **Seat 1 always Pacer, auto-only (no override)** — rejected. Different devices connect in different orders depending on battery state, BLE timing, etc. The coach needs to be able to correct auto-assignment without physically re-labelling devices.

## Consequences

When two-canoe support is added, this rule extends to: "Pacer is Seat 1 of the designated lead canoe." The Training Controller will need a canoe-level lead selection, but the per-device seat UI remains unchanged.
