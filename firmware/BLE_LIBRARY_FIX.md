# BLE Library Migration - ArduinoBLE to Bluefruit nRF52

## Problem Summary

The firmware was originally written using `ArduinoBLE` library, which **does not support nRF52 architecture**. This caused compilation errors:

```
WARNING: library ArduinoBLE claims to run on samd, megaavr, mbed, apollo3...
and may be incompatible with your current board which runs on nrf52 architecture(s).

error: #error "Unsupported board selected!"
```

## Solution

The firmware has been migrated to use **Adafruit Bluefruit nRF52 Libraries** which are the native BLE libraries for nRF52840 and come bundled with the Seeed nRF52 board support package.

---

## Key Changes Made

### 1. Library Include Change

**Before (ArduinoBLE):**
```cpp
#include <ArduinoBLE.h>
```

**After (Bluefruit nRF52):**
```cpp
#include <bluefruit.h>
```

---

### 2. Service and Characteristic Declaration

**Before:**
```cpp
BLEService oroHapticService(ORO_HAPTIC_SERVICE_UUID);
BLECharacteristic hapticControlChar(HAPTIC_CONTROL_CHAR_UUID, BLEWrite, 5);
```

**After:**
```cpp
BLEService oroHapticService = BLEService(ORO_HAPTIC_SERVICE_UUID);
BLECharacteristic hapticControlChar = BLECharacteristic(HAPTIC_CONTROL_CHAR_UUID);
```

---

### 3. BLE Initialization

**Before (ArduinoBLE):**
```cpp
BLE.begin();
BLE.setLocalName(deviceName.c_str());
BLE.setAdvertisedService(oroHapticService);
oroHapticService.addCharacteristic(hapticControlChar);
BLE.addService(oroHapticService);
BLE.advertise();
```

**After (Bluefruit):**
```cpp
Bluefruit.begin();
Bluefruit.setName(deviceName.c_str());

// Configure service
oroHapticService.begin();

// Configure characteristic with properties
hapticControlChar.setProperties(CHR_PROPS_WRITE);
hapticControlChar.setPermission(SECMODE_OPEN, SECMODE_OPEN);
hapticControlChar.setFixedLen(5);
hapticControlChar.setWriteCallback(onHapticControlWrite);
hapticControlChar.begin();

// Start advertising
Bluefruit.Advertising.addService(oroHapticService);
Bluefruit.Advertising.start(0);
```

---

### 4. Event Handler Signatures

**Before (ArduinoBLE):**
```cpp
void onBLEConnected(BLEDevice central) {
  // ...
}

void onHapticControlWrite(BLEDevice central, BLECharacteristic characteristic) {
  const uint8_t* data = characteristic.value();
  int length = characteristic.valueLength();
}
```

**After (Bluefruit):**
```cpp
void onBLEConnected(uint16_t conn_handle) {
  BLEConnection* connection = Bluefruit.Connection(conn_handle);
  // ...
}

void onHapticControlWrite(uint16_t conn_hdl, BLECharacteristic* chr,
                          uint8_t* data, uint16_t len) {
  // Data is passed directly as parameters
}
```

---

### 5. Characteristic Value Updates

**Before (ArduinoBLE):**
```cpp
deviceStatusChar.writeValue(status, 5);
batteryLevelChar.writeValue(batteryLevel);
```

**After (Bluefruit):**
```cpp
deviceStatusChar.write(status, 5);
if (Bluefruit.connected()) {
  deviceStatusChar.notify(status, 5);
}

batteryLevelChar.write8(batteryLevel);
if (Bluefruit.connected()) {
  batteryLevelChar.notify8(batteryLevel);
}
```

---

### 6. Main Loop

**Before (ArduinoBLE):**
```cpp
void loop() {
  BLE.poll();  // Required for ArduinoBLE
  // ...
}
```

**After (Bluefruit):**
```cpp
void loop() {
  // Bluefruit handles BLE automatically, no polling needed
  // ...
}
```

---

### 7. Connection State Check

**Before (ArduinoBLE):**
```cpp
if (BLE.connected()) {
  // ...
}
```

**After (Bluefruit):**
```cpp
if (Bluefruit.connected()) {
  // ...
}
```

---

## Installation Steps

### Step 1: Verify Board Support Package
1. Open Arduino IDE
2. Go to Tools → Board → Boards Manager
3. Search for "Seeed nRF52"
4. Verify "Seeed nRF52 Boards" is installed (v1.1.8 or newer)
5. Select: Tools → Board → Seeed nRF52 Boards → **Seeed XIAO nRF52840 Sense**

### Step 2: Remove ArduinoBLE (if installed)
1. Go to Sketch → Include Library → Manage Libraries
2. Search for "ArduinoBLE"
3. If installed, click "Uninstall"
4. Close and restart Arduino IDE

### Step 3: Verify Bluefruit Library
- The Bluefruit library (`bluefruit.h`) is **automatically included** with the Seeed nRF52 board package
- No separate installation is needed
- Verify by checking: Arduino/packages/Seeeduino/hardware/nrf52/[version]/libraries/Bluefruit52Lib/

### Step 4: Install DRV2605 Library
1. Go to Sketch → Include Library → Manage Libraries
2. Search for "Adafruit DRV2605"
3. Install "Adafruit DRV2605 Library" (v1.2.2 or newer)

### Step 5: Compile Firmware
1. Open `OroHapticFirmware.ino`
2. Click Verify/Compile
3. Should compile without errors

---

## Testing the Updated Firmware

### Serial Monitor Output (Expected)
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

### BLE Scanning Test
1. Use nRF Connect app (Android/iOS)
2. Scan for devices
3. Should see device named `Oro-XXXX` (XXXX = last 4 hex digits of MAC)
4. Connect to device
5. Verify services:
   - Oro Haptic Service: `12340000-1234-5678-1234-56789abcdef0`
   - Battery Service: `0000180F-0000-1000-8000-00805F9B34FB`

---

## Migration Checklist

- [x] Replace `#include <ArduinoBLE.h>` with `#include <bluefruit.h>`
- [x] Update service/characteristic declarations
- [x] Rewrite `initializeBLE()` using Bluefruit API
- [x] Update event handler signatures
- [x] Replace `writeValue()` with `write()` and `notify()`
- [x] Remove `BLE.poll()` from main loop
- [x] Update connection state checks
- [x] Update `libraries.txt` documentation
- [x] Create migration guide

---

## API Reference Quick Guide

### Bluefruit Core Functions
| Function | Description |
|----------|-------------|
| `Bluefruit.begin()` | Initialize BLE stack |
| `Bluefruit.setName(name)` | Set device name |
| `Bluefruit.setTxPower(power)` | Set TX power (-40 to +4 dBm) |
| `Bluefruit.connected()` | Check connection status |
| `Bluefruit.getAddr(mac)` | Get device MAC address |

### Service Functions
| Function | Description |
|----------|-------------|
| `BLEService(uuid)` | Create service object |
| `service.begin()` | Initialize service |

### Characteristic Functions
| Function | Description |
|----------|-------------|
| `BLECharacteristic(uuid)` | Create characteristic |
| `char.setProperties(props)` | Set properties (READ/WRITE/NOTIFY) |
| `char.setPermission(read, write)` | Set security permissions |
| `char.setFixedLen(len)` | Set fixed data length |
| `char.setWriteCallback(func)` | Set write callback |
| `char.begin()` | Initialize characteristic |
| `char.write(data, len)` | Write value |
| `char.notify(data, len)` | Send notification |
| `char.write8(val)` | Write 8-bit value |
| `char.notify8(val)` | Notify 8-bit value |

### Advertising Functions
| Function | Description |
|----------|-------------|
| `Advertising.addService(service)` | Add service to advertisement |
| `Advertising.addName()` | Include device name |
| `Advertising.setInterval(min, max)` | Set advertising interval |
| `Advertising.start(timeout)` | Start advertising (0=forever) |

---

## Troubleshooting

### Compilation Error: "bluefruit.h: No such file"
- **Cause:** Seeed nRF52 board package not installed
- **Fix:** Install "Seeed nRF52 Boards" via Boards Manager

### Compilation Error: "Unsupported board selected"
- **Cause:** ArduinoBLE still installed or wrong board selected
- **Fix:** Uninstall ArduinoBLE, select XIAO nRF52840 Sense board

### Upload Error: "No DFU capable USB device"
- **Cause:** Board not in bootloader mode
- **Fix:** Double-press RESET button, wait for LED to pulse, then upload

### Device Not Advertising
- **Cause:** BLE initialization failed
- **Fix:** Check serial output for errors, verify I2C wiring isn't shorting

---

## Performance Comparison

| Feature | ArduinoBLE | Bluefruit nRF52 |
|---------|------------|-----------------|
| Architecture Support | SAMD, ESP32, etc. | **nRF52 only** |
| nRF52 Compatible | **NO** | **YES** |
| Connection Interval | 7.5-20ms | **6-16 units (7.5-20ms)** |
| TX Power Control | Limited | **-40 to +4 dBm** |
| Event Handling | Polling required | **Interrupt-based** |
| Memory Footprint | Higher | **Optimized for nRF52** |

---

## Additional Resources

- [Adafruit Bluefruit nRF52 Library Documentation](https://github.com/adafruit/Adafruit_nRF52_Arduino)
- [Seeed XIAO nRF52840 Wiki](https://wiki.seeedstudio.com/XIAO_BLE/)
- [Nordic nRF52840 Product Specification](https://infocenter.nordicsemi.com/pdf/nRF52840_PS_v1.7.pdf)

---

**Document Version:** 1.0
**Date:** 2025-11-02
**Firmware Compatibility:** OroHapticFirmware v1.0 (Bluefruit nRF52)
