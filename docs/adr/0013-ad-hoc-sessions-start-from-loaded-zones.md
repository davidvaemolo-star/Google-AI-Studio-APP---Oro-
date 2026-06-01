# A Session starts from the loaded Zones; a saved Programme is not required

The Training Controller lets a coach start a Session from whatever Zones are currently loaded on the Training screen. Those Zones usually come from loading a saved **Programme**, but they may also be built directly on the Training screen. Starting no longer requires a Programme to be the "active" one.

`canStartTraining` and the on-screen Pre-Training Checklist are both derived from a single shared list of checks (`OroUiState.startChecks`), so they can never disagree. The blocking checks are: at least one Device connected, a Pacer in Seat 1, all connected Devices calibrated, and at least one Zone. Low battery is a warning that is shown but never blocks.

## Why

ADR-0007 established that Programmes are a saved library and that loading one copies its Zones into the session. The UI, however, also lets a coach add Zones directly on the Training screen, and the old start gate additionally required `activeProgramme != null`. The result was an unreachable-feeling state: zones visible, devices ready, but Start silently disabled because no Programme had been *loaded* (as opposed to merely edited). Worse, the checklist and the gate were two independent lists — the checklist could report "Ready" while Start stayed greyed, because the checklist omitted the calibration and active-programme requirements. This repeatedly stranded coaches with no explanation.

The session machinery already runs purely off the loaded Zones (`state.zones`); nothing in starting, running, or saving a Session reads `activeProgramme`. So requiring a loaded Programme was a guard with no functional purpose — only a trap.

## Decision and trade-off

We chose **usability over strict model fidelity**: a Session may run Zones that are not backed by a saved Programme (an "ad-hoc Session"). This bends the ADR-0007 framing that a Session is "one run through a Programme." The glossary (CONTEXT.md) has been softened accordingly: a Session runs the Zones currently loaded, whatever their source.

The alternative — keeping the Programme requirement — would have forced the app to forbid editing Zones on the Training screen without a Programme (or to auto-create one), which is more machinery for no user benefit. We rejected it.

## Consequence

- Starting a Session is gated only by genuinely necessary conditions (connected, pacer, calibrated, has zones), all of which are visible in the checklist.
- Ad-hoc Sessions are not persisted as Programmes; if a coach wants to reuse the Zones, they must save them as a Programme separately.
- The checklist and the Start button share one definition; adding or changing a start requirement is done in one place (`OroUiState.startChecks`).
