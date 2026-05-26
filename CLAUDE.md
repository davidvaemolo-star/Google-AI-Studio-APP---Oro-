# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Oro Haptic Paddle

Outrigger canoe (OC6) training system. Each device is built into a paddle's t-handle and vibrates in sync with the pacer's stroke to improve crew synchronization. Three components: Configuration Planner (web UI), Training Controller (Android app), and nRF52840 firmware.

## Domain Language

**Always use the canonical terms from `CONTEXT.md`**. Key distinctions that are easy to get wrong:

- **Programme** vs **Session**: a Programme is the template (list of Zones); a Session is one run through it.
- **Zone** (not interval/block): defined by strokes, sets, and intensity (Low/Medium/High, never "Green/Red").
- **Catch** / **Finish**: Catch = blade enters water (haptic fires here); Finish = blade exits (stroke count advances here). These are intentionally decoupled — see `docs/adr/0001-haptic-on-catch-count-on-finish.md`.
- **Pacer** (not leader/master) / **Follower** (not slave/non-pacer).
- **Top Hand Pressure** (not grip force/FSR pressure); **Peak Pressure** → **Power Range**.
- **Training Controller** = Android app; **Configuration Planner** = web UI.

## Project Structure

```
/                       → Configuration Planner (React/Vite, post-MVP)
/components/            → React components (ZoneBlock, ConnectionScreen, etc.)
/android-app/           → Training Controller (Jetpack Compose, Kotlin)
/firmware/              → Arduino firmware for Seeed XIAO nRF52840 Sense
/firmware/OroHapticFirmware/ → Main firmware source
/docs/adr/              → Architecture Decision Records
CONTEXT.md              → Canonical domain vocabulary (read before writing UI copy)
```

## Commands

### Web (Configuration Planner)
```bash
npm install
npm run dev        # Dev server on port 3000
npm run build
```

### Android (Training Controller)
```bash
cd android-app
./gradlew assembleDebug    # Build APK
./gradlew installDebug     # Install on connected device
./gradlew test             # Run unit tests
./gradlew testDebugUnitTest --tests "com.orotrain.oro.SomeTest"  # Single test
```

### Firmware
Built with Arduino IDE. See `firmware/COMPILE_INSTRUCTIONS.md`. Board: Seeed XIAO nRF52840 Sense. Serial monitor at 115200 baud.

## Architecture

### BLE Communication (Fixed Constraint)

The phone mediates all haptic coordination. When the Pacer's device detects a Catch, it sends a BLE notification to the phone; the phone then writes `CMD_SINGLE_PULSE` to every Follower. **Devices never signal each other directly** — the nRF52840 cannot act as both BLE central and peripheral simultaneously. This is a hardware constraint, not a trade-off (ADR-0002). The 50ms "perfect sync" threshold in Sync Score is calibrated to this round-trip budget.

BLE service UUID: `12340000-1234-5678-1234-56789abcdef0`. Full characteristic spec in `firmware/BLE_PROTOCOL.md`.

### Android Architecture

Single `MainViewModel` owns all state as `OroUiState` (a single `StateFlow`). `BleManager`, `AudioManager`, `SessionRepository`, and `ProgrammeRepository` are injected as nullable constructor parameters — when null, the ViewModel falls back to simulated data (scan/connect mocks). Never add real logic to the simulated fallback paths.

```
BleManager (BLE I/O)
  └─ strokeEvents / calibrationUpdates / fsrUpdates (StateFlow)
       └─ MainViewModel.handleStrokeEvent() / handleCalibrationUpdate() / handleFsrUpdate()
            ├─ updates OroUiState
            ├─ StrokeAnalyzer.onStrokeEvent()  ← accumulates StrokeRecord per stroke
            │    └─ emits CoachingEvent
            │         └─ CoachingEngine.onCoachingEvent() ← throttled haptic/audio feedback
            └─ triggerFollowerHaptics() ← BleManager.broadcastHaptic() to all Followers
```

### Pacer Designation

**Seat 1 = Pacer. There is no separate "Set as Pacer" action.** (ADR-0010)

Seats are auto-assigned by device name order when devices connect. The coach can override via a per-device seat dropdown; assigning a device to a seat swaps it with whoever held that seat before (`swapSeats()` in `model/SeatAssignment.kt`). Whenever seat assignments change, `renumberSeats()` automatically calls `setPacerDevice()` for the device in Seat 1.

### Session Lifecycle

1. **Pre-session**: Devices connect → auto-assigned seats → each device completes Calibration (50 strokes, firmware sets threshold at 55% of peak acceleration) → device becomes `isCalibrationComplete = true`.
2. `canStartTraining` requires: active programme, ≥1 connected device, all connected devices calibrated.
3. **Session start**: `configureCurrentZone()` sends Zone Settings BLE packet (with `isPacer = seat == 1`) → `startTraining()` on all devices → `enableStrokeDetection()` on Pacer only → status = Active.
4. **During session**: Pacer Catch → phone triggers haptics on all (including Pacer). Pacer Finish → `processStrokeForTraining()` advances stroke/set/zone. Zone transitions broadcast audio prompts (beep, voice).
5. **Session end**: `selectSessionSummaryPrompt()` picks from 12 pre-recorded prompts (Sync Rating × Power Range) → broadcasts audio → saves to Room DB via `SessionRepository`.

### Stroke Analysis Pipeline

`StrokeAnalyzer` assembles `StrokeRecord`s across CATCH→DRIVE→FINISH phases from the Pacer's events. After each FINISH it recomputes rolling `StrokeMetrics` (drive ratio, consistency CV, fatigue index, SPM) and emits `CoachingEvent`s with a 15-second per-issue cooldown. `CoachingEngine` maps these events to haptic patterns and audio, throttled to 5s (haptic) and 20s (audio) minimums.

`sessionAverageFsrPeak()` on `StrokeAnalyzer` provides the Power Range input for the Session Summary prompt.

### Zone → BLE Mapping

`Zone` (Android model) maps to the Zone Settings BLE packet (6 bytes + role byte):
- `level` → `zoneColor`: Low=0x01, Medium=0x02, High=0x03
- `targetSpm`: Low=38, Medium=53, High=70 (midpoints; SPM ranges are 30–45 / 46–60 / 61–80)

### Persistence

- **Programmes**: JSON file via `ProgrammeRepository` (app internal storage), loaded at ViewModel init.
- **Sessions**: Room database (`SessionDatabase`) with `SessionEntity` and `StrokeEntity` tables, accessed via `SessionDao` → `SessionRepository`.

## Key Design Decisions (ADRs)

| ADR | Decision |
|-----|----------|
| 0001 | Haptic fires at Catch; stroke count advances at Finish (decoupled) |
| 0002 | Follower haptics are phone-mediated — devices cannot signal each other |
| 0003 | Calibration gates session participation; it is not a boolean flag |
| 0004 | Catch confirmation requires IMU trigger + Top Hand Pressure rise |
| 0005 | Session summary uses 12 pre-recorded voice prompts |
| 0006 | Single Pacer across all canoes |
| 0007 | Programmes live on Android as a local JSON library; loading copies zones into the session |
| 0008 | Audio: tones for timing cues, voice for content cues (I2S sample-rate fix in firmware) |
| 0009 | Firmware owns the RGB LED state machine; no BLE LED-control characteristic |
| 0010 | Seat 1 = Pacer — no separate pacer designation UI |
| 0011 | Tact switch (D10) → 2-s hold → System OFF; wake by same pin reboots firmware |

## Known Open Issues

- **6-device BLE cap**: firmware and Android BLE code currently limit connections to 6 devices. Two OC6 canoes needs 12.
- **Calibration model debt**: `HapticDevice` tracks calibration as `isCalibrating: Boolean` + `isCalibrationComplete: Boolean`. ADR-0003 says this should be a proper enum device state mirroring the firmware's `STATE_CALIBRATING`.
- **SPM is fixed per intensity level**: `Zone.targetSpm` is a hardcoded midpoint. The BLE protocol supports per-zone SPM; this is not yet surfaced in the UI.

## Key Documentation

- `CONTEXT.md` — Domain vocabulary and relationship model (read before naming anything)
- `firmware/BLE_PROTOCOL.md` — Complete BLE characteristic spec with packet formats
- `firmware/WIRING.md` — Hardware wiring
- `firmware/INTEGRATION_GUIDE.md` — Android-firmware integration notes
- `android-app/ANDROID_BLE_INTEGRATION.md` — BLE setup walkthrough
- `docs/adr/` — All architecture decisions
