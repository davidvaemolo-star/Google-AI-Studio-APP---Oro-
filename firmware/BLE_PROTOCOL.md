# Oro Haptic Paddle - BLE Protocol Specification

## Overview
This document specifies the BLE communication protocol between the Oro Haptic Paddle firmware (nRF52840) and the Android application.

---

## Device Information

**Device Name Format:** `Oro-XXXX`
- Where `XXXX` = last 4 characters of BLE MAC address (hex)
- Example: `Oro-A4F3`

**Advertising:**
- Primary Service: Oro Haptic Service UUID
- Secondary Service: Battery Service (standard 0x180F)
- Connection Interval: 7.5ms - 20ms (low latency)

---

## BLE Services

### 1. Oro Haptic Service
**Service UUID:** `12340000-1234-5678-1234-56789abcdef0`

This is the primary custom service for haptic training control.

#### Characteristics:

##### 1.1 Haptic Control (Write Only)
**UUID:** `12340001-1234-5678-1234-56789abcdef0`
**Properties:** `BLEWrite`
**Size:** 5 bytes

**Data Format:**
```
Byte 0: Command (uint8)
Byte 1: Intensity (uint8, 0-100)
Byte 2: Duration LSB (uint16, milliseconds)
Byte 3: Duration MSB
Byte 4: Pattern (uint8, DRV2605L effect ID)
```

**Commands:**
| Value | Name | Description |
|-------|------|-------------|
| 0x00 | CMD_STOP | Stop all training/haptic activity |
| 0x01 | CMD_SINGLE_PULSE | Trigger single haptic pulse |
| 0x02 | CMD_START_TRAINING | Begin training session |
| 0x03 | CMD_PAUSE_TRAINING | Pause active training |
| 0x04 | CMD_RESUME_TRAINING | Resume paused training |
| 0x05 | CMD_COMPLETE_TRAINING | Mark training as complete |
| 0x06 | CMD_TEST_PATTERN | Test haptic pattern |

**Haptic Patterns (DRV2605L Effects):**
| Value | Name | Description |
|-------|------|-------------|
| 1 | PATTERN_STRONG_CLICK | Sharp single click (default training) |
| 2 | PATTERN_SHARP_CLICK | Medium click |
| 3 | PATTERN_SOFT_CLICK | Gentle click |
| 12 | PATTERN_DOUBLE_CLICK | Two quick clicks |
| 13 | PATTERN_TRIPLE_CLICK | Three quick clicks |
| 24 | PATTERN_ALERT_750MS | Long alert (completion) |
| 47 | PATTERN_PULSING | Continuous pulse |
| 51 | PATTERN_TRANSITION | Smooth transition |

**Example: Start Training**
```kotlin
val data = byteArrayOf(
    0x02,  // CMD_START_TRAINING
    100,   // Intensity 100%
    0, 0,  // Duration (not used for this command)
    1      // PATTERN_STRONG_CLICK
)
hapticControlChar.write(data)
```

**Example: Test Haptic**
```kotlin
val data = byteArrayOf(
    0x06,  // CMD_TEST_PATTERN
    80,    // Intensity 80%
    0, 0,  // Duration (not used)
    12     // PATTERN_DOUBLE_CLICK
)
hapticControlChar.write(data)
```

---

##### 1.2 Zone Settings (Write Only)
**UUID:** `12340002-1234-5678-1234-56789abcdef0`
**Properties:** `BLEWrite`
**Size:** 6 bytes

**Data Format:**
```
Byte 0: Total Strokes LSB (uint16)
Byte 1: Total Strokes MSB
Byte 2: Total Sets (uint8)
Byte 3: Strokes Per Minute LSB (uint16)
Byte 4: Strokes Per Minute MSB
Byte 5: Intensity (uint8)
```

**Intensity Codes:**
| Value | Intensity |
|-------|-----------|
| 0x01 | Low |
| 0x02 | Medium |
| 0x03 | High |
| 0x04–0x06 | Reserved |

**Example: Configure Zone**
```kotlin
// Zone: 10 strokes, 3 sets, 20 SPM, Medium intensity
val strokes: UShort = 10u
val sets: UByte = 3u
val spm: UShort = 20u
val intensity: UByte = 0x02u  // Medium

val data = byteArrayOf(
    (strokes.toInt() and 0xFF).toByte(),
    (strokes.toInt() shr 8).toByte(),
    sets.toByte(),
    (spm.toInt() and 0xFF).toByte(),
    (spm.toInt() shr 8).toByte(),
    intensity.toByte()
)
zoneSettingsChar.write(data)
```

---

##### 1.3 Device Status (Read + Notify)
**UUID:** `12340003-1234-5678-1234-56789abcdef0`
**Properties:** `BLERead | BLENotify`
**Size:** 5 bytes

**Data Format:**
```
Byte 0: Device State (uint8)
Byte 1: Current Stroke LSB (uint16)
Byte 2: Current Stroke MSB
Byte 3: Current Set (uint8)
Byte 4: Battery Level (uint8, 0-100%)
```

**Device States:**
| Value | Name | Description |
|-------|------|-------------|
| 0x00 | STATE_IDLE | Initial state, not configured |
| 0x01 | STATE_READY | Configured and ready to train |
| 0x02 | STATE_TRAINING | Actively running training |
| 0x03 | STATE_PAUSED | Training paused |
| 0x04 | STATE_COMPLETE | Training session completed |
| 0x05 | STATE_CALIBRATING | Calibration in progress |
| 0xFF | STATE_ERROR | Hardware error |

**Example: Parse Status**
```kotlin
override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
) {
    if (characteristic.uuid == DEVICE_STATUS_UUID) {
        val state = value[0].toInt()
        val currentStroke = (value[1].toInt() and 0xFF) or
                           ((value[2].toInt() and 0xFF) shl 8)
        val currentSet = value[3].toInt() and 0xFF
        val battery = value[4].toInt() and 0xFF

        // Update UI
        updateDeviceState(state, currentStroke, currentSet, battery)
    }
}
```

---

##### 1.4 Connection Status (Read + Notify)
**UUID:** `12340004-1234-5678-1234-56789abcdef0`
**Properties:** `BLERead | BLENotify`
**Size:** 2 bytes

**Data Format:**
```
Byte 0: Connection State (uint8, 0=disconnected, 1=connected)
Byte 1: RSSI (int8, reserved - currently 0)
```

---

##### 1.5 Stroke Event (Notify Only)
**UUID:** `12340005-1234-5678-1234-56789abcdef0`
**Properties:** `BLENotify`
**Size:** 16 bytes

Emitted by the firmware on every stroke phase transition (Catch, Drive, Finish, Recovery). The Training Controller uses the Catch notification to trigger haptics on all Followers.

**Data Format:**
```
Byte  0:     Stroke Phase (uint8)
Bytes 1–4:   Timestamp ms (uint32 LE)
Bytes 5–6:   Current acceleration × 100 (int16 LE, e.g. 183 = 1.83 g)
Bytes 7–8:   Peak acceleration this stroke × 100 (int16 LE)
Bytes 9–10:  Min acceleration this stroke × 100 (int16 LE)
Bytes 11–12: Phase duration ms (uint16 LE)
Byte  13:    Top Hand Pressure % at this moment (uint8, 0–100)
Byte  14:    Stroke flags (uint8, bit 0 = top-hand-pressure threshold triggered)
Byte  15:    Reserved (0x00)
```

**Stroke Phases:**
| Value | Name | Description |
|-------|------|-------------|
| 0x01 | CATCH | Paddle entry — haptic trigger point |
| 0x02 | DRIVE | Power phase, after Catch |
| 0x03 | FINISH | Paddle exit — stroke count advances |
| 0x04 | RECOVERY | Return phase until next Catch |

**Phase Duration Field:**
- On DRIVE notification: duration since Catch (ms)
- On FINISH notification: duration since Drive (ms)
- On RECOVERY notification: duration since Finish (ms)
- On CATCH notification: 0

---

##### 1.6 Calibration (Write + Notify)
**UUID:** `12340006-1234-5678-1234-56789abcdef0`
**Properties:** `BLEWrite | BLENotify`

A device must complete calibration before it is Ready to join a session.

**Write Format (phone → device): 4 bytes**
```
Byte 0: Calibration Command (uint8)
Bytes 1–2: Threshold × 100 (int16 LE) — used by CAL_CMD_SET_THRESHOLD only
Byte 3: Reserved (0x00)
```

**Calibration Commands (Write):**
| Value | Name | Description |
|-------|------|-------------|
| 0x01 | CAL_CMD_START | Begin calibration (50-stroke session) |
| 0x02 | CAL_CMD_STOP | Abort calibration |
| 0x03 | CAL_CMD_SET_THRESHOLD | Override threshold (bytes 1–2 = threshold × 100) |
| 0x04 | CAL_CMD_GET_STATUS | Request current status notification |

**Notify Format — Status Update (device → phone): 8 bytes**

Sent periodically during calibration and on completion.
```
Byte 0:    Command echo (CAL_CMD_GET_STATUS = 0x04)
Byte 1:    Stroke count so far (uint8, target = 50)
Bytes 2–3: Max acceleration seen × 100 (int16 LE) — measured relative to resting baseline (ADR-0012)
Bytes 4–5: Min acceleration seen × 100 (int16 LE) — measured relative to resting baseline (ADR-0012)
Byte 6:    Taring (uint8, 1 = capturing resting baseline, hold paddle still; 0 = stroke-counting phase) — ADR-0012
Byte 7:    Baseline rejected (uint8, 1 = paddle wasn't still, hold still and calibrate again; 0 = OK) — ADR-0012
```

Calibration now begins with a ~1 s resting-baseline (tare) window: the device must be held still while it learns its zero. During that window byte 6 = 1 and the stroke count stays 0. If the paddle was moving, byte 7 = 1 and the device enters the Error state — re-send `CAL_CMD_START` to retry. Bytes 6–7 were previously reserved (0x00), so older app builds that ignore them remain compatible.

**Notify Format — Threshold Acknowledged: 4 bytes**

Sent in response to CAL_CMD_SET_THRESHOLD.
```
Byte 0:    CAL_CMD_SET_THRESHOLD (0x03)
Bytes 1–2: Threshold × 100 echoed back (int16 LE)
Byte 3:    0x01 (success)
```

Calibration completes automatically after 50 strokes. The firmware sets the detection threshold to 55% of the maximum acceleration observed and transitions the device to STATE_READY.

---

##### 1.7 Audio Control (Write Only)
**UUID:** `12340007-1234-5678-1234-56789abcdef0`
**Properties:** `BLEWrite`
**Size:** 2 bytes

Triggers audio playback on the device. Tones are firmware-generated; voice prompts are pre-recorded audio played via the MAX98357A I2S amplifier.

**Data Format:**
```
Byte 0: Audio Event (uint8)
Byte 1: Volume (uint8, 0–100)
```

**Audio Events:**
| Value | Name | Type | Description |
|-------|------|------|-------------|
| 0x01 | AUDIO_POWER_ON | Voice | "Oro" — played on boot |
| 0x02 | AUDIO_SESSION_START_BEEP | Tone + Haptic | 3 short beeps + 1 long go-beep, plus a synchronized buzz on the go-beep so the crew's first Catch is together (the Countdown — ADR-0016) |
| 0x03 | AUDIO_SET_CHANGEOVER_BEEP | Tone | Single beep: set complete |
| 0x04 | AUDIO_LAST_SET | Voice | "last set" |
| 0x05 | AUDIO_NEXT_SET_LOW | Voice | "next set low" |
| 0x06 | AUDIO_NEXT_SET_MEDIUM | Voice | "next set medium" |
| 0x07 | AUDIO_NEXT_SET_HIGH | Voice | "next set high" |
| 0x08–0x13 | AUDIO_SUMMARY_* | — | **RETIRED** (ADR-0016). The 12-prompt crew summary is replaced by the per-seat Crew Roll-Call (§1.10). These IDs remain reserved; do not reuse. |

The end-of-session summary is no longer a single crew-wide clip. It is delivered by the **Roll-Call Control** characteristic (§1.10): the phone loads every device with the full per-seat roster, then triggers all devices to speak it in unison (ADR-0016).

---

##### 1.8 FSR Data (Notify Only)
**UUID:** `12340008-1234-5678-1234-56789abcdef0`
**Properties:** `BLENotify`
**Size:** 4 bytes

Streams Top Hand Pressure readings at 20 Hz. The Training Controller uses this data to compute Peak Pressure per stroke (the maximum value during the Drive phase) and derive Power Range. Every device streams its own FSR during a Session, so the phone derives each Seat's own Power Range for the Crew Roll-Call (ADR-0016), not just the Pacer's.

**Data Format:**
```
Byte 0:    Top Hand Pressure % (uint8, 0–100)
Bytes 1–2: Raw ADC value (uint16 LE, 0–4095)
Byte 3:    Threshold triggered (uint8, 0x00 = below, 0x01 = above)
```

Note: The field name in firmware is `forcePercent` / `thresholdTriggered`. The canonical domain term is **Top Hand Pressure**. Android model fields should use `topHandPressurePercent` / `topHandPressureThresholdTriggered`.

---

##### 1.9 LED Control — REMOVED

The LED is driven entirely by firmware from `DeviceState` (see ADR-0009). No BLE characteristic exists at `…0009`; the slot is reserved and must not be reused.

---

##### 1.10 Roll-Call Control (Write Only)
**UUID:** `1234000A-1234-5678-1234-56789abcdef0`
**Properties:** `BLEWrite`

Delivers the end-of-session **Crew Roll-Call** (ADR-0016): the crew Sync Rating, then every occupied Seat's own Sync Rating and Power Range. The phone first **loads** the full roster onto every device, then **plays** it on all devices in unison; each device composes and speaks the whole read-out locally from its stored clips. Load and play are separate so the larger roster transfer never delays the synchronized start.

**Commands (Byte 0):**
| Value | Name | Description |
|-------|------|-------------|
| 0x00 | CMD_ROLLCALL_CLEAR | Discard any stored roster (optional) |
| 0x01 | CMD_ROLLCALL_LOAD | Store the roster for later playback |
| 0x02 | CMD_ROLLCALL_PLAY | Speak the stored roster after a short delay |

**`CMD_ROLLCALL_LOAD` payload (phone → each device):**
```
Byte 0:    0x01
Byte 1:    Crew Sync Rating (uint8) — see Rating codes
Byte 2:    Seat count N (uint8, 1–12)
Bytes 3…:  N seat entries, 3 bytes each, in read-out order (Canoe + Seat ascending, Pacer first):
  Entry Byte 0: Canoe (uint8) — 0 = single-canoe session (say no "Canoe N"); else 1–2
  Entry Byte 1: Seat  (uint8) — Seat number 1–6; bit 7 set = this Seat is the Pacer
  Entry Byte 2: Score (uint8) — low nibble = Sync Rating, high nibble = Power Range
```
Total length = `3 + 3N` bytes (≤ 39 for a full 12-seat crew). The roster must fit in one write, so the Training Controller requests an ATT MTU of 247 on connect. If a roster ever exceeds the negotiated MTU, `LOAD` may be split across successive writes that the device appends until it has received `Seat count` entries.

**Rating codes** (used for both Crew Sync Rating in Byte 1 and each Seat's Sync Rating):
| Value | Meaning |
|-------|---------|
| 0 | Not Measured |
| 1 | Poor |
| 2 | Good |
| 3 | Excellent |

**Power Range codes** (high nibble of the Score byte):
| Value | Meaning |
|-------|---------|
| 0 | None / not available |
| 1 | Light |
| 2 | Moderate |
| 3 | Strong |
| 4 | Maximum |

A **Pacer** entry (Seat bit 7 set) always carries Sync Rating = 0 and is spoken as *"Seat N, pacer, power <range>"* — no sync word. A **Follower** with Sync Rating = 0 is spoken as *"Seat N, sync not measured, power <range>"* (ADR-0015). The opener uses Byte 1: *"Team sync <rating>"*.

**`CMD_ROLLCALL_PLAY` payload (phone → each device):**
```
Byte 0: 0x02
Byte 1: Start delay (uint8) — units of 10 ms; the device waits this long after receiving PLAY, then speaks
Byte 2: Volume (uint8, 0–100)
```

**Keeping playback in unison.** GATT writes are serialized, so the phone cannot reach all devices at the same instant — and overlapping, drifting voices would garble. To compensate, the Training Controller records a reference time `t0` just before the first `PLAY` write and gives each device a *decreasing* Start delay:

```
delay_ms = TARGET_MS − (now − t0)        // clamp ≥ 0, then divide by 10 for the byte
```

with `TARGET_MS` set above the worst-case send spread (e.g. 300 ms). A device written earlier waits longer, so every device's `receipt + delay` lands at ≈ the same wall-clock moment. `PLAY` is sent as **Write Without Response** to minimize per-write latency. No device-clock synchronization is needed — each device only times a relative delay from its own receipt.

**Firmware clip inventory.** Composing the spoken roster needs these clips stored on each device (factory-loaded, like the existing prompts): seat numbers *"Seat 1"…"Seat 6"*; canoe numbers *"Canoe 1","Canoe 2"*; the words *"pacer","sync","power","team sync","not measured"*; Sync Ratings *"poor","good","excellent"*; Power Ranges *"light","moderate","strong","maximum"*. The retired summary prompts (Audio Control 0x08–0x13) may be removed to reclaim space.

---

### 2. Battery Service (Standard)
**Service UUID:** `0000180F-0000-1000-8000-00805F9B34FB`

#### Battery Level Characteristic
**UUID:** `00002A19-0000-1000-8000-00805F9B34FB`
**Properties:** `BLERead | BLENotify`
**Size:** 1 byte

**Data Format:**
```
Byte 0: Battery percentage (uint8, 0-100)
```

**Battery Voltage Mapping:**
- 100% = 4.2V (fully charged LiPo)
- 0% = 3.0V (empty LiPo)
- Updates every 30 seconds or when change > 1%

---

## Android Integration Guide

### Required Dependencies
Add to `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    // BLE permissions already handled in manifest
}
```

### BLE Manager Updates

Update `BleManager.kt` with these UUID constants:

```kotlin
object BleConstants {
    // Oro Haptic Service
    val ORO_HAPTIC_SERVICE_UUID    = UUID.fromString("12340000-1234-5678-1234-56789abcdef0")
    val HAPTIC_CONTROL_UUID        = UUID.fromString("12340001-1234-5678-1234-56789abcdef0")
    val ZONE_SETTINGS_UUID         = UUID.fromString("12340002-1234-5678-1234-56789abcdef0")
    val DEVICE_STATUS_UUID         = UUID.fromString("12340003-1234-5678-1234-56789abcdef0")
    val CONNECTION_STATUS_UUID     = UUID.fromString("12340004-1234-5678-1234-56789abcdef0")
    val STROKE_EVENT_UUID          = UUID.fromString("12340005-1234-5678-1234-56789abcdef0")
    val CALIBRATION_UUID           = UUID.fromString("12340006-1234-5678-1234-56789abcdef0")
    val AUDIO_CONTROL_UUID         = UUID.fromString("12340007-1234-5678-1234-56789abcdef0")
    val FSR_DATA_UUID              = UUID.fromString("12340008-1234-5678-1234-56789abcdef0")
    // Note: 12340009 is reserved (LED control was removed — LED is firmware-driven; see ADR-0009)
    val ROLLCALL_CONTROL_UUID      = UUID.fromString("1234000A-1234-5678-1234-56789abcdef0")

    // Battery Service
    val BATTERY_SERVICE_UUID       = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID         = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
}
```

### Scanning for Devices

```kotlin
fun startScan() {
    val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(BleConstants.ORO_HAPTIC_SERVICE_UUID))
        .build()

    bluetoothAdapter.bluetoothLeScanner.startScan(
        listOf(scanFilter),
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build(),
        scanCallback
    )
}
```

### Connecting to Device

```kotlin
private val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val hapticService = gatt.getService(BleConstants.ORO_HAPTIC_SERVICE_UUID)
        hapticControlChar  = hapticService.getCharacteristic(BleConstants.HAPTIC_CONTROL_UUID)
        zoneSettingsChar   = hapticService.getCharacteristic(BleConstants.ZONE_SETTINGS_UUID)
        deviceStatusChar   = hapticService.getCharacteristic(BleConstants.DEVICE_STATUS_UUID)
        strokeEventChar    = hapticService.getCharacteristic(BleConstants.STROKE_EVENT_UUID)
        calibrationChar    = hapticService.getCharacteristic(BleConstants.CALIBRATION_UUID)
        audioControlChar   = hapticService.getCharacteristic(BleConstants.AUDIO_CONTROL_UUID)
        fsrDataChar        = hapticService.getCharacteristic(BleConstants.FSR_DATA_UUID)

        // Enable notifications
        enableNotifications(gatt, deviceStatusChar)
        enableNotifications(gatt, strokeEventChar)
        enableNotifications(gatt, calibrationChar)
        enableNotifications(gatt, fsrDataChar)

        val batteryService = gatt.getService(BleConstants.BATTERY_SERVICE_UUID)
        batteryLevelChar = batteryService.getCharacteristic(BleConstants.BATTERY_LEVEL_UUID)
        enableNotifications(gatt, batteryLevelChar)
    }
}

private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
    gatt.setCharacteristicNotification(char, true)
    val descriptor = char.getDescriptor(
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    )
    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    gatt.writeDescriptor(descriptor)
}
```

### Sending Zone Configuration

```kotlin
fun configureZone(zone: TrainingZone) {
    val data = ByteBuffer.allocate(6).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        putShort(zone.strokes.toShort())
        put(zone.sets.toByte())
        putShort(zone.spm.toShort())
        put(zone.intensityCode())  // 0x01=Low, 0x02=Medium, 0x03=High
    }.array()

    zoneSettingsChar.value = data
    bluetoothGatt?.writeCharacteristic(zoneSettingsChar)
}
```

### Starting Training

```kotlin
fun startTraining() {
    val data = byteArrayOf(
        0x02,  // CMD_START_TRAINING
        100,   // Intensity
        0, 0,  // Duration
        1      // PATTERN_STRONG_CLICK
    )

    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

### Receiving Status Updates

```kotlin
override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
) {
    when (characteristic.uuid) {
        BleConstants.DEVICE_STATUS_UUID -> {
            val state = value[0].toInt()
            val currentStroke = ByteBuffer.wrap(value, 1, 2)
                .order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val currentSet = value[3].toInt() and 0xFF
            val battery = value[4].toInt() and 0xFF

            _deviceState.value = DeviceState.fromByte(state)
            _currentProgress.value = Pair(currentStroke, currentSet)
            _batteryLevel.value = battery
        }

        BleConstants.BATTERY_LEVEL_UUID -> {
            val batteryLevel = value[0].toInt() and 0xFF
            _batteryLevel.value = batteryLevel
        }
    }
}
```

---

## Timing and Performance

### Stroke Timing Accuracy
- Target: ±2ms accuracy from programmed SPM
- Implementation: Uses `millis()` for interval calculation
- Formula: `strokeInterval = 60000 / SPM`

**Examples:**
- 20 SPM → 3000ms interval
- 30 SPM → 2000ms interval
- 40 SPM → 1500ms interval

### BLE Latency
- Connection interval: 7.5-20ms
- Command response: < 50ms typical
- Status notification rate: On change (training) + 30s (battery)

---

## Error Handling

### Firmware Error States
1. **I2C Communication Failure**: Device enters STATE_ERROR (0xFF)
2. **DRV2605L Not Detected**: Halts at startup, serial reports error
3. **BLE Initialization Failure**: Halts at startup

### Android Error Recovery
1. **Disconnection During Training**: Firmware auto-stops training
2. **Write Failure**: Retry with exponential backoff
3. **Service Discovery Timeout**: Disconnect and retry connection

---

## Testing Sequence

### Initial Device Test
1. Power on device → should see `Oro-XXXX` in BLE scan
2. Connect → verify services discovered
3. Send test haptic (CMD_TEST_PATTERN) → verify motor vibration
4. Read battery level → verify valid percentage (0-100)

### Training Session Test
1. Write zone settings → verify configuration accepted
2. Send CMD_START_TRAINING → verify haptic pulse starts
3. Monitor device status notifications → verify stroke/set count
4. Send CMD_PAUSE_TRAINING → verify haptic stops
5. Send CMD_RESUME_TRAINING → verify haptic resumes
6. Wait for completion → verify STATE_COMPLETE + completion haptic

---

## Firmware Upload Instructions

### Required Arduino Libraries
Install via Arduino Library Manager:
1. **ArduinoBLE** by Arduino (v1.3.6+)
2. **Adafruit DRV2605 Library** by Adafruit (v1.2.2+)
3. **Wire** (built-in, no install needed)

### Board Setup
1. Install **Seeed nRF52 Boards** via Boards Manager:
   - Add URL: `https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json`
   - Install "Seeed nRF52 Boards" package
2. Select: **Tools → Board → Seeed nRF52 Boards → Seeed XIAO nRF52840 Sense**
3. Select: **Tools → Port → [Your COM Port]**

### Upload Steps
1. Open `OroHapticFirmware.ino` in Arduino IDE
2. Verify libraries installed (sketch should compile)
3. Connect XIAO via USB-C
4. Click Upload
5. Open Serial Monitor (115200 baud) to verify initialization

### Expected Serial Output
```
=== Oro Haptic Paddle Firmware ===
Hardware: XIAO nRF52840 Sense + DRV2605L

Initializing DRV2605L haptic driver...
Scanning I2C bus... FOUND at 0x5A
DRV2605L initialized successfully
Initializing BLE...
BLE initialized successfully
Advertising as: Oro-A4F3
System initialized successfully
Ready for BLE connections
```

---

## Hardware Verification Checklist

Before firmware upload:
- [ ] I2C connections: SDA=D4, SCL=D5
- [ ] DRV2605L VIN connected to 3.3V (not 5V!)
- [ ] DRV2605L GND connected to common ground
- [ ] Motor connected to DRV2605L OUT+ and OUT-
- [ ] Battery connected to XIAO BAT pins
- [ ] Battery voltage < 4.2V (safe LiPo range)

After firmware upload:
- [ ] Serial monitor shows successful initialization
- [ ] BLE device appears in scan (name: Oro-XXXX)
- [ ] Can connect from Android/nRF Connect app
- [ ] Services and characteristics visible
- [ ] Test haptic command triggers motor vibration
- [ ] Battery level reads reasonable value (0-100%)

---

## Troubleshooting

### Device Not Advertising
- Check serial output for BLE initialization errors
- Verify XIAO board selected correctly
- Try power cycle (disconnect/reconnect USB)

### DRV2605L Not Found
- Verify I2C wiring: SDA=D4, SCL=D5
- Check power: VIN must be 3.3V (XIAO 3.3V pin)
- Test with I2C scanner sketch first

### Motor Not Vibrating
- Check motor connections to DRV OUT+/OUT-
- Verify motor voltage rating (3.3V compatible)
- Try different haptic effect patterns
- Check motor current draw (should be ~70mA)

### Battery Reading Always 100% or 0%
- Verify battery connected to BAT pins
- Check battery voltage (3.0V - 4.2V range)
- May need calibration for specific battery chemistry

### BLE Disconnects Frequently
- Check battery level (low battery = unstable BLE)
- Reduce distance between phone and device
- Verify Android app has location permissions (required for BLE scan)

---

## Appendix: Complete UUID Reference

```
Oro Haptic Service:        12340000-1234-5678-1234-56789abcdef0
├─ Haptic Control:         12340001-1234-5678-1234-56789abcdef0
├─ Zone Settings:          12340002-1234-5678-1234-56789abcdef0
├─ Device Status:          12340003-1234-5678-1234-56789abcdef0
├─ Connection Status:      12340004-1234-5678-1234-56789abcdef0
├─ Stroke Event:           12340005-1234-5678-1234-56789abcdef0
├─ Calibration:            12340006-1234-5678-1234-56789abcdef0
├─ Audio Control:          12340007-1234-5678-1234-56789abcdef0
├─ FSR Data:               12340008-1234-5678-1234-56789abcdef0
│  (12340009 reserved — LED control removed, ADR-0009)
└─ Roll-Call Control:      1234000A-1234-5678-1234-56789abcdef0

Battery Service:           0000180F-0000-1000-8000-00805F9B34FB
└─ Battery Level:          00002A19-0000-1000-8000-00805F9B34FB
```

---

**Document Version:** 1.2
**Last Updated:** 2026-06-01
**Firmware Compatibility:** OroHapticFirmware v1.0+ (Roll-Call Control §1.10 requires updated firmware — ADR-0016)
