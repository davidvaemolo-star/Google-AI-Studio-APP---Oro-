# Android App BLE Integration Guide

## Overview

This Android app now has full BLE connectivity to communicate with Oro Haptic Paddle firmware running on the Seeed XIAO nRF52840 Sense.

## What's Been Implemented

### 1. BLE Permissions
- **File:** `app/src/main/AndroidManifest.xml`
- Added Android 12+ permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
- Added legacy permissions for older Android versions
- Declared BLE hardware feature requirement

### 2. BLE Manager
- **File:** `app/src/main/java/com/orotrain/oro/ble/BleManager.kt`
- Comprehensive BLE manager with:
  - Device scanning (filters for "Oro-XXXX" devices)
  - Connection/disconnection management
  - Battery level reading and notifications
  - Haptic control commands
  - Training zone configuration
  - Device status monitoring

### 3. Updated ViewModel
- **File:** `app/src/main/java/com/orotrain/oro/MainViewModel.kt`
- Integrated BleManager
- Exposes haptic control functions to UI
- Maintains fallback to simulated devices for preview mode

### 4. Updated MainActivity
- **File:** `app/src/main/java/com/orotrain/oro/MainActivity.kt`
- Creates BleManager instance
- Handles runtime permission requests
- Prompts for Bluetooth enablement
- Proper cleanup on destroy

## BLE Protocol Specification

### Service UUIDs
```kotlin
ORO_HAPTIC_SERVICE_UUID  = "12340000-1234-5678-1234-56789abcdef0"
BATTERY_SERVICE_UUID     = "0000180F-0000-1000-8000-00805F9B34FB"
```

### Characteristic UUIDs
```kotlin
// Oro Haptic Service
HAPTIC_CONTROL_UUID      = "12340001-1234-5678-1234-56789abcdef0" (Write)
ZONE_SETTINGS_UUID       = "12340002-1234-5678-1234-56789abcdef0" (Write)
DEVICE_STATUS_UUID       = "12340003-1234-5678-1234-56789abcdef0" (Read/Notify)
CONNECTION_STATUS_UUID   = "12340004-1234-5678-1234-56789abcdef0" (Read/Notify)

// Battery Service
BATTERY_LEVEL_UUID       = "00002A19-0000-1000-8000-00805F9B34FB" (Read/Notify)
```

## Available Functions

### MainViewModel Public API

```kotlin
// Scanning
fun startScan()  // Start BLE scan for Oro devices

// Connection Management
fun toggleDeviceConnection(deviceId: String)  // Connect/disconnect device

// Zone Configuration
fun configureZone(deviceId: String, zone: Zone)  // Configure training zone

// Training Control
fun startDeviceTraining(deviceId: String)   // Start training session
fun pauseDeviceTraining(deviceId: String)   // Pause training
fun resumeDeviceTraining(deviceId: String)  // Resume training
fun stopDeviceTraining(deviceId: String)    // Stop training

// Testing
fun testHaptic(deviceId: String, pattern: Byte)  // Test haptic pattern

// Device Management
fun reorderConnectedDevices(fromIndex: Int, toIndex: Int)  // Reorder seats
```

### BleManager Public API

```kotlin
// Scanning
fun startScan()
fun stopScan()
fun isBluetoothEnabled(): Boolean

// Connection
fun connectDevice(deviceId: String)
fun disconnectDevice(deviceId: String)

// Training Configuration
fun configureTrainingZone(
    deviceId: String,
    strokes: Int,
    sets: Int,
    spm: Int,
    zoneColor: Byte = ZONE_ENDURANCE
): Boolean

// Training Control
fun startTraining(deviceId: String, intensity: Int = 100, pattern: Byte = PATTERN_STRONG_CLICK): Boolean
fun pauseTraining(deviceId: String): Boolean
fun resumeTraining(deviceId: String): Boolean
fun stopTraining(deviceId: String): Boolean
fun testHapticPattern(deviceId: String, pattern: Byte, intensity: Int = 80): Boolean

// State Access
val discoveredDevices: StateFlow<List<HapticDevice>>
val isScanning: StateFlow<Boolean>
```

## Haptic Commands

```kotlin
// Commands (BleManager.kt constants)
CMD_STOP             = 0x00
CMD_SINGLE_PULSE     = 0x01
CMD_START_TRAINING   = 0x02
CMD_PAUSE_TRAINING   = 0x03
CMD_RESUME_TRAINING  = 0x04
CMD_COMPLETE_TRAINING = 0x05
CMD_TEST_PATTERN     = 0x06

// Patterns
PATTERN_STRONG_CLICK  = 1
PATTERN_SHARP_CLICK   = 2
PATTERN_SOFT_CLICK    = 3
PATTERN_DOUBLE_CLICK  = 12
PATTERN_TRIPLE_CLICK  = 13
PATTERN_LONG_ALERT    = 24

// Zone Colors
ZONE_RECOVERY    = 0x01  // Light Blue
ZONE_ENDURANCE   = 0x02  // Green
ZONE_TEMPO       = 0x03  // Yellow
ZONE_THRESHOLD   = 0x04  // Orange
ZONE_VO2_MAX     = 0x05  // Red
ZONE_ANAEROBIC   = 0x06  // Dark Red
```

## Usage Example

### 1. Scan for Devices
```kotlin
viewModel.startScan()
```

### 2. Connect to Device
```kotlin
val deviceId = "AA:BB:CC:DD:EE:FF"  // BLE MAC address
viewModel.toggleDeviceConnection(deviceId)
```

### 3. Configure Training Zone
```kotlin
val zone = Zone(
    strokes = 10,
    sets = 3,
    spm = 20
)
viewModel.configureZone(deviceId, zone)
```

### 4. Start Training
```kotlin
viewModel.startDeviceTraining(deviceId)
```

### 5. Control Training
```kotlin
viewModel.pauseDeviceTraining(deviceId)
viewModel.resumeDeviceTraining(deviceId)
viewModel.stopDeviceTraining(deviceId)
```

### 6. Test Haptic Feedback
```kotlin
viewModel.testHaptic(deviceId, BleManager.PATTERN_DOUBLE_CLICK)
```

## Data Formats

### Zone Settings (6 bytes, Write to ZONE_SETTINGS_UUID)
```
[0-1]: Strokes (uint16, little-endian)
[2]:   Sets (uint8)
[3-4]: SPM (uint16, little-endian)
[5]:   Zone Color (uint8)

Example: [0x0A, 0x00, 0x03, 0x14, 0x00, 0x02]
         = 10 strokes, 3 sets, 20 SPM, Endurance zone
```

### Haptic Control (5 bytes, Write to HAPTIC_CONTROL_UUID)
```
[0]: Command (0x00-0x06)
[1]: Intensity (0-100)
[2-3]: Duration (unused, set to 0)
[4]: Pattern (1-51)

Example: [0x02, 100, 0, 0, 1]
         = Start training, 100% intensity, strong click pattern
```

### Device Status (5 bytes, Read/Notify from DEVICE_STATUS_UUID)
```
[0]:   State (0x00-0xFF)
[1-2]: Current Stroke (uint16, little-endian)
[3]:   Current Set (uint8)
[4]:   Battery Level (0-100)

Example: [0x02, 0x05, 0x00, 0x01, 0x64]
         = Training state, stroke 5, set 1, 100% battery
```

## Testing Checklist

- [ ] Build and install app on physical Android device
- [ ] Grant Bluetooth permissions when prompted
- [ ] Ensure Bluetooth is enabled
- [ ] Upload firmware to XIAO nRF52840 device
- [ ] Tap "Scan" button in app
- [ ] Verify Oro device appears in list (e.g., "Oro-A4F3")
- [ ] Tap device to connect
- [ ] Verify connection status changes to "Connected"
- [ ] Verify battery level displays
- [ ] Configure a training zone in the app
- [ ] Start training session
- [ ] Verify haptic pulses occur at correct intervals
- [ ] Test pause/resume functionality
- [ ] Test stop functionality

## Troubleshooting

### Device Not Found
- Check BLE permissions are granted
- Check location services are enabled (required on Android <12)
- Verify device is powered on and advertising
- Check battery charge level
- Ensure firmware is uploaded correctly

### Services Not Discovered
- Add delay before discoverServices() (already implemented)
- Check connection status in logs
- Verify firmware is running correctly
- Check Serial Monitor for firmware errors

### Haptic Not Working
- Test with HapticTest.ino first to verify hardware
- Verify DRV2605L is powered with 3.3V (NOT 5V)
- Check motor connections
- Check I2C connections (SDA=D4, SCL=D5)
- Monitor Serial output for errors

### Notifications Not Received
- Notifications are enabled automatically on connection
- Check logs for descriptor write status
- Verify firmware is sending notifications

## Related Documentation

For complete firmware documentation, see:
- `../firmware/BLE_PROTOCOL.md` - Complete BLE protocol spec
- `../firmware/INTEGRATION_GUIDE.md` - Android integration guide
- `../firmware/QUICK_REFERENCE.txt` - Quick reference card
- `../firmware/README.md` - Firmware overview

## Next Steps

1. **Enhance UI Integration**
   - Add training progress display in TrainingScreen
   - Show device status updates in real-time
   - Add zone color selection in UI

2. **Add Error Handling**
   - Display connection errors to user
   - Handle GATT errors gracefully
   - Implement reconnection logic

3. **Implement Advanced Features**
   - Multi-device training sessions
   - Training history and analytics
   - Custom haptic patterns
   - Firmware update over BLE (optional)

## Architecture Notes

The BLE integration follows a clean architecture pattern:

```
UI (Composables)
    ↓
MainViewModel (State Management)
    ↓
BleManager (BLE Operations)
    ↓
Android BLE Stack
    ↓
Oro Device Firmware
```

State flows upward through StateFlow, while commands flow downward through function calls.

## License

This integration is part of the Oro Haptic Paddle project.

---

**Last Updated:** 2025-11-02
**Firmware Version:** v1.0
**Android App Version:** Current
