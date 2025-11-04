# Oro Haptic Paddle - Android Integration Quick Reference

## Critical UUIDs for Android App

Copy these UUIDs into your Android `BleManager.kt`:

```kotlin
object BleConstants {
    // Oro Haptic Service
    val ORO_HAPTIC_SERVICE_UUID = UUID.fromString("12340000-1234-5678-1234-56789abcdef0")
    val HAPTIC_CONTROL_UUID = UUID.fromString("12340001-1234-5678-1234-56789abcdef0")
    val ZONE_SETTINGS_UUID = UUID.fromString("12340002-1234-5678-1234-56789abcdef0")
    val DEVICE_STATUS_UUID = UUID.fromString("12340003-1234-5678-1234-56789abcdef0")
    val CONNECTION_STATUS_UUID = UUID.fromString("12340004-1234-5678-1234-56789abcdef0")

    // Battery Service (Standard)
    val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
}
```

---

## Quick Command Reference

### 1. Configure Training Zone

```kotlin
fun configureZone(strokes: Int, sets: Int, spm: Int, color: Long) {
    val colorCode: Byte = when (color) {
        0xFF64B5F6L -> 0x01  // Recovery - Light Blue
        0xFF81C784L -> 0x02  // Endurance - Green
        0xFFFFD54FL -> 0x03  // Tempo - Yellow
        0xFFFFB74DL -> 0x04  // Threshold - Orange
        0xFFE57373L -> 0x05  // VO2 Max - Red
        0xFFF44336L -> 0x06  // Anaerobic - Dark Red
        else -> 0x02
    }

    val data = ByteBuffer.allocate(6).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        putShort(strokes.toShort())
        put(sets.toByte())
        putShort(spm.toShort())
        put(colorCode)
    }.array()

    zoneSettingsChar.value = data
    bluetoothGatt?.writeCharacteristic(zoneSettingsChar)
}
```

### 2. Start Training

```kotlin
fun startTraining() {
    val data = byteArrayOf(
        0x02,  // CMD_START_TRAINING
        100,   // Intensity 100%
        0, 0,  // Duration (unused)
        1      // PATTERN_STRONG_CLICK
    )
    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

### 3. Pause Training

```kotlin
fun pauseTraining() {
    val data = byteArrayOf(0x03, 100, 0, 0, 1)  // CMD_PAUSE_TRAINING
    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

### 4. Resume Training

```kotlin
fun resumeTraining() {
    val data = byteArrayOf(0x04, 100, 0, 0, 1)  // CMD_RESUME_TRAINING
    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

### 5. Stop Training

```kotlin
fun stopTraining() {
    val data = byteArrayOf(0x00, 0, 0, 0, 0)  // CMD_STOP
    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

### 6. Test Haptic

```kotlin
fun testHaptic(pattern: Byte = 12, intensity: Int = 80) {
    val data = byteArrayOf(
        0x06,             // CMD_TEST_PATTERN
        intensity.toByte(),
        0, 0,
        pattern           // 12 = DOUBLE_CLICK
    )
    hapticControlChar.value = data
    bluetoothGatt?.writeCharacteristic(hapticControlChar)
}
```

---

## Receiving Status Updates

### Enable Notifications

```kotlin
override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        val hapticService = gatt.getService(BleConstants.ORO_HAPTIC_SERVICE_UUID)
        deviceStatusChar = hapticService.getCharacteristic(BleConstants.DEVICE_STATUS_UUID)

        // Enable notifications
        gatt.setCharacteristicNotification(deviceStatusChar, true)
        val descriptor = deviceStatusChar.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }
}
```

### Parse Device Status

```kotlin
override fun onCharacteristicChanged(
    gatt: BluetoothGatt,
    characteristic: BluetoothGattCharacteristic,
    value: ByteArray
) {
    if (characteristic.uuid == BleConstants.DEVICE_STATUS_UUID) {
        // Format: [state(1)][stroke_lsb(1)][stroke_msb(1)][set(1)][battery(1)]
        val state = value[0].toInt()  // 0x00=IDLE, 0x01=READY, 0x02=TRAINING, etc.
        val currentStroke = (value[1].toInt() and 0xFF) or
                           ((value[2].toInt() and 0xFF) shl 8)
        val currentSet = value[3].toInt() and 0xFF
        val battery = value[4].toInt() and 0xFF

        // Update UI
        updateTrainingProgress(currentStroke, currentSet)
        updateBatteryLevel(battery)
        updateDeviceState(state)
    }
}
```

---

## Data Format Reference

### Haptic Control (5 bytes)
| Byte | Name | Type | Range | Description |
|------|------|------|-------|-------------|
| 0 | Command | uint8 | 0x00-0x06 | See commands below |
| 1 | Intensity | uint8 | 0-100 | Haptic strength % |
| 2-3 | Duration | uint16 | 0-65535 | Milliseconds (unused) |
| 4 | Pattern | uint8 | 1-51 | DRV2605L effect ID |

**Commands:**
- 0x00 = STOP
- 0x01 = SINGLE_PULSE
- 0x02 = START_TRAINING
- 0x03 = PAUSE_TRAINING
- 0x04 = RESUME_TRAINING
- 0x05 = COMPLETE_TRAINING
- 0x06 = TEST_PATTERN

**Patterns:**
- 1 = Strong Click (default training)
- 2 = Sharp Click
- 3 = Soft Click
- 12 = Double Click
- 13 = Triple Click
- 24 = Long Alert (750ms)

### Zone Settings (6 bytes)
| Byte | Name | Type | Range | Description |
|------|------|------|-------|-------------|
| 0-1 | Strokes | uint16 LE | 1-65535 | Total strokes per set |
| 2 | Sets | uint8 | 1-255 | Number of sets |
| 3-4 | SPM | uint16 LE | 10-200 | Strokes per minute |
| 5 | Color | uint8 | 0x01-0x06 | Zone color code |

**Color Codes:**
- 0x01 = Recovery (Light Blue)
- 0x02 = Endurance (Green)
- 0x03 = Tempo (Yellow)
- 0x04 = Threshold (Orange)
- 0x05 = VO2 Max (Red)
- 0x06 = Anaerobic (Dark Red)

### Device Status (5 bytes) - READ/NOTIFY
| Byte | Name | Type | Range | Description |
|------|------|------|-------|-------------|
| 0 | State | uint8 | 0x00-0xFF | Device state code |
| 1-2 | Current Stroke | uint16 LE | 0-65535 | Progress in current set |
| 3 | Current Set | uint8 | 0-255 | Current set number |
| 4 | Battery | uint8 | 0-100 | Battery percentage |

**State Codes:**
- 0x00 = IDLE (not configured)
- 0x01 = READY (configured, ready to start)
- 0x02 = TRAINING (actively running)
- 0x03 = PAUSED (training paused)
- 0x04 = COMPLETE (training finished)
- 0xFF = ERROR (hardware error)

### Battery Level (1 byte) - READ/NOTIFY
| Byte | Name | Type | Range | Description |
|------|------|------|-------|-------------|
| 0 | Percentage | uint8 | 0-100 | Battery level % |

---

## Device Discovery

### Scan for Oro Devices

```kotlin
fun startScan() {
    val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid(BleConstants.ORO_HAPTIC_SERVICE_UUID))
        .build()

    val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    bluetoothLeScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
}

private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        if (device.name?.startsWith("Oro-") == true) {
            // Found Oro device
            onDeviceDiscovered(device)
        }
    }
}
```

**Device Naming:**
- Format: `Oro-XXXX`
- Where XXXX = last 4 hex characters of BLE MAC address
- Example: `Oro-A4F3`

---

## State Machine Flow

```
IDLE (0x00)
  ↓ [Zone configured]
READY (0x01)
  ↓ [START_TRAINING command]
TRAINING (0x02) ←→ PAUSED (0x03)
  ↓ [All sets complete OR STOP command]
COMPLETE (0x04) OR READY (0x01)
```

**State Transitions:**
1. Device starts in IDLE (no configuration)
2. Send zone settings → READY
3. Send START_TRAINING → TRAINING (haptic pulses begin)
4. Send PAUSE_TRAINING → PAUSED (haptic stops)
5. Send RESUME_TRAINING → TRAINING (haptic resumes)
6. Training completes → COMPLETE (automatic)
7. Send STOP → READY (can reconfigure)

---

## Example Training Session

```kotlin
// 1. Scan and connect
bleManager.startScan()
// ... user selects "Oro-A4F3"
bleManager.connect(selectedDevice)

// 2. Wait for services discovered
// In onServicesDiscovered callback:

// 3. Configure zone: 10 strokes, 3 sets, 20 SPM, Endurance (green)
val zoneData = ByteBuffer.allocate(6).apply {
    order(ByteOrder.LITTLE_ENDIAN)
    putShort(10)      // strokes
    put(3)            // sets
    putShort(20)      // SPM
    put(0x02)         // green color
}.array()
zoneSettingsChar.value = zoneData
bluetoothGatt?.writeCharacteristic(zoneSettingsChar)

// 4. Start training
val startCmd = byteArrayOf(0x02, 100, 0, 0, 1)
hapticControlChar.value = startCmd
bluetoothGatt?.writeCharacteristic(hapticControlChar)

// 5. Monitor progress via notifications
// onCharacteristicChanged will receive updates every stroke

// 6. Pause if needed
val pauseCmd = byteArrayOf(0x03, 100, 0, 0, 1)
hapticControlChar.value = pauseCmd
bluetoothGatt?.writeCharacteristic(hapticControlChar)

// 7. Resume
val resumeCmd = byteArrayOf(0x04, 100, 0, 0, 1)
hapticControlChar.value = resumeCmd
bluetoothGatt?.writeCharacteristic(hapticControlChar)

// 8. Training auto-completes after 3 sets × 10 strokes
// Device status will change to COMPLETE (0x04)
```

---

## Timing Specifications

**SPM to Interval Conversion:**
- Formula: `interval_ms = 60000 / SPM`
- Examples:
  - 20 SPM → 3000ms (3 seconds per stroke)
  - 30 SPM → 2000ms (2 seconds per stroke)
  - 40 SPM → 1500ms (1.5 seconds per stroke)
  - 60 SPM → 1000ms (1 second per stroke)

**Accuracy:**
- Target: ±2ms from programmed interval
- Typical: ±1ms at 60 BPM, ±2-3ms at 120 BPM

**BLE Latency:**
- Connection interval: 7.5-20ms
- Command response: <50ms typical
- Notification delivery: <30ms typical

---

## Troubleshooting Android Integration

### Device Not Found in Scan
**Causes:**
- BLE permissions not granted
- Location services disabled
- Device not powered on
- Device already connected to another app

**Solutions:**
```kotlin
// Check permissions
if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    != PackageManager.PERMISSION_GRANTED) {
    // Request permission
}

// Ensure location enabled (required for BLE scan on Android)
if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
    // Prompt user to enable location
}
```

### Services Not Discovered
**Cause:** Service discovery failed or device disconnected

**Solution:**
```kotlin
override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    when (newState) {
        BluetoothProfile.STATE_CONNECTED -> {
            // Add delay before service discovery (workaround for some devices)
            Handler(Looper.getMainLooper()).postDelayed({
                gatt.discoverServices()
            }, 600)
        }
    }
}
```

### Notifications Not Received
**Cause:** Descriptor not written correctly

**Solution:**
```kotlin
fun enableNotifications(char: BluetoothGattCharacteristic) {
    bluetoothGatt?.setCharacteristicNotification(char, true)

    val descriptor = char.getDescriptor(
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    )

    // Use ENABLE_NOTIFICATION_VALUE (not ENABLE_INDICATION_VALUE)
    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

    bluetoothGatt?.writeDescriptor(descriptor)
}
```

### Write Failures
**Cause:** Writing too quickly, characteristic not writable

**Solution:**
```kotlin
// Add delay between writes
fun writeWithDelay(char: BluetoothGattCharacteristic, data: ByteArray) {
    char.value = data
    bluetoothGatt?.writeCharacteristic(char)

    // Wait for write callback before next write
    // Or add fixed delay
    delay(100)
}

// Verify write property
if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
    // Characteristic is writable
}
```

---

## Testing Checklist

### Initial Connection Test
- [ ] Device appears in BLE scan with name "Oro-XXXX"
- [ ] Can connect to device
- [ ] Services discovered successfully
- [ ] All characteristics present
- [ ] Can enable notifications on Device Status
- [ ] Can enable notifications on Battery Level
- [ ] Can read initial battery level

### Haptic Test
- [ ] Send TEST_PATTERN command (0x06)
- [ ] Motor vibrates
- [ ] Different patterns produce different sensations
- [ ] Intensity adjustment works (50% vs 100%)

### Zone Configuration Test
- [ ] Send zone settings (10 strokes, 1 set, 20 SPM)
- [ ] Device status changes to READY (0x01)
- [ ] Device responds with haptic confirmation

### Training Session Test
- [ ] Send START_TRAINING command
- [ ] Device status changes to TRAINING (0x02)
- [ ] Haptic pulses occur at correct interval (3s for 20 SPM)
- [ ] Device status notifications show stroke progress
- [ ] Training completes after configured strokes
- [ ] Device status changes to COMPLETE (0x04)

### Pause/Resume Test
- [ ] Start training
- [ ] Send PAUSE command
- [ ] Haptic stops
- [ ] Device status = PAUSED (0x03)
- [ ] Send RESUME command
- [ ] Haptic resumes
- [ ] Device status = TRAINING (0x02)

### Battery Monitoring Test
- [ ] Battery notifications received
- [ ] Battery level is reasonable (0-100%)
- [ ] Battery updates when level changes

---

## Complete Code Example

See `AndroidIntegration.kt` for a complete BleManager implementation with:
- Device scanning
- Connection management
- Service discovery
- Characteristic read/write
- Notification handling
- All command functions
- State management with Kotlin Flows

---

## Firmware Upload Instructions

1. Install Arduino IDE and required libraries (see `libraries.txt`)
2. Select board: **Seeed XIAO nRF52840 Sense**
3. Upload `OroHapticFirmware.ino`
4. Open Serial Monitor (115200 baud) to verify initialization
5. Device should appear as "Oro-XXXX" in BLE scans

---

## Support Files

- `OroHapticFirmware.ino` - Main firmware
- `BLE_PROTOCOL.md` - Complete protocol specification
- `AndroidIntegration.kt` - Full Android BleManager example
- `HapticTest.ino` - Hardware diagnostic test
- `README.md` - Firmware documentation
- `libraries.txt` - Required Arduino libraries

---

**Last Updated:** 2025-11-02
**Firmware Version:** 1.0
**Protocol Version:** 1.0
