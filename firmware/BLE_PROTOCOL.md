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
Byte 5: Zone Color (uint8)
```

**Zone Color Codes:**
| Value | Zone Name | Android Color |
|-------|-----------|---------------|
| 0x01 | Recovery | Light Blue (#64B5F6) |
| 0x02 | Endurance | Green (#81C784) |
| 0x03 | Tempo | Yellow (#FFD54F) |
| 0x04 | Threshold | Orange (#FFB74D) |
| 0x05 | VO2 Max | Red (#E57373) |
| 0x06 | Anaerobic | Dark Red (#F44336) |

**Example: Configure Zone**
```kotlin
// Zone: 10 strokes, 3 sets, 20 SPM, Endurance (Green)
val strokes: UShort = 10u
val sets: UByte = 3u
val spm: UShort = 20u
val color: UByte = 0x02u

val data = byteArrayOf(
    (strokes.toInt() and 0xFF).toByte(),
    (strokes.toInt() shr 8).toByte(),
    sets.toByte(),
    (spm.toInt() and 0xFF).toByte(),
    (spm.toInt() shr 8).toByte(),
    color.toByte()
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
    val ORO_HAPTIC_SERVICE_UUID = UUID.fromString("12340000-1234-5678-1234-56789abcdef0")
    val HAPTIC_CONTROL_UUID = UUID.fromString("12340001-1234-5678-1234-56789abcdef0")
    val ZONE_SETTINGS_UUID = UUID.fromString("12340002-1234-5678-1234-56789abcdef0")
    val DEVICE_STATUS_UUID = UUID.fromString("12340003-1234-5678-1234-56789abcdef0")
    val CONNECTION_STATUS_UUID = UUID.fromString("12340004-1234-5678-1234-56789abcdef0")

    // Battery Service
    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
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
        // Get characteristics
        val hapticService = gatt.getService(BleConstants.ORO_HAPTIC_SERVICE_UUID)
        hapticControlChar = hapticService.getCharacteristic(BleConstants.HAPTIC_CONTROL_UUID)
        zoneSettingsChar = hapticService.getCharacteristic(BleConstants.ZONE_SETTINGS_UUID)
        deviceStatusChar = hapticService.getCharacteristic(BleConstants.DEVICE_STATUS_UUID)

        // Enable notifications
        enableNotifications(gatt, deviceStatusChar)

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
        put(getZoneColorCode(zone.color))
    }.array()

    zoneSettingsChar.value = data
    bluetoothGatt?.writeCharacteristic(zoneSettingsChar)
}

private fun getZoneColorCode(color: Long): Byte {
    return when (color) {
        0xFF64B5F6 -> 0x01  // Recovery - Light Blue
        0xFF81C784 -> 0x02  // Endurance - Green
        0xFFFFD54F -> 0x03  // Tempo - Yellow
        0xFFFFB74D -> 0x04  // Threshold - Orange
        0xFFE57373 -> 0x05  // VO2 Max - Red
        0xFFF44336 -> 0x06  // Anaerobic - Dark Red
        else -> 0x02  // Default to Endurance
    }
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
└─ Connection Status:      12340004-1234-5678-1234-56789abcdef0

Battery Service:           0000180F-0000-1000-8000-00805F9B34FB
└─ Battery Level:          00002A19-0000-1000-8000-00805F9B34FB
```

---

**Document Version:** 1.0
**Last Updated:** 2025-11-02
**Firmware Compatibility:** OroHapticFirmware v1.0
