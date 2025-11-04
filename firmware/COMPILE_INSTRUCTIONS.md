# Oro Haptic Firmware - Compilation Instructions

## Quick Start (After BLE Library Fix)

The firmware has been updated to use the correct BLE library for nRF52840. Follow these steps to compile and upload.

---

## Prerequisites

### 1. Arduino IDE
- Version 2.0 or newer recommended
- Download from: https://www.arduino.cc/en/software

### 2. Board Support Package
**CRITICAL:** Install Seeed nRF52 board package FIRST before anything else.

1. Open Arduino IDE
2. File → Preferences
3. In "Additional Board Manager URLs", add:
   ```
   https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json
   ```
4. Click OK
5. Tools → Board → Boards Manager
6. Search for "Seeed nRF52"
7. Install "Seeed nRF52 Boards" (v1.1.8 or newer)
8. Wait for installation to complete

### 3. Select Board
1. Tools → Board → Seeed nRF52 Boards → **Seeed XIAO nRF52840 Sense**
2. Tools → Port → Select the COM port for your XIAO

### 4. Install Required Libraries

**IMPORTANT:** Do NOT install ArduinoBLE - it is incompatible!

Only install this library via Library Manager:
1. Sketch → Include Library → Manage Libraries
2. Search "Adafruit DRV2605"
3. Install "Adafruit DRV2605 Library" by Adafruit (v1.2.2+)
   - This will auto-install dependencies (Adafruit BusIO)

**Note:** The Bluefruit BLE library (`bluefruit.h`) is already included with the Seeed nRF52 board package. No separate installation needed!

---

## Compilation Steps

### Step 1: Open Firmware
1. Navigate to: `firmware/OroHapticFirmware/`
2. Open `OroHapticFirmware.ino` in Arduino IDE

### Step 2: Verify Settings
Check these settings in the IDE:

- **Board:** Seeed XIAO nRF52840 Sense
- **Port:** COM port where XIAO is connected
- **Programmer:** Default (no change needed)

### Step 3: Compile
1. Click the **Verify** button (checkmark icon) or press Ctrl+R
2. Wait for compilation to complete

**Expected Output:**
```
Sketch uses XXXXX bytes (XX%) of program storage space.
Global variables use XXXXX bytes (XX%) of dynamic memory.
```

**If you see errors**, check the Troubleshooting section below.

### Step 4: Upload
1. Connect XIAO nRF52840 via USB-C cable
2. Click the **Upload** button (right arrow icon) or press Ctrl+U
3. Wait for upload to complete

**If upload fails:**
1. Press the **RESET button** on XIAO **twice quickly**
2. Wait for the LED to pulse (bootloader mode)
3. Click Upload again immediately

---

## Verification

### Serial Monitor Check
1. Tools → Serial Monitor (or Ctrl+Shift+M)
2. Set baud rate to **115200**
3. Press RESET button on XIAO once

**Expected Output:**
```
=== Oro Haptic Paddle Firmware ===
Hardware: XIAO nRF52840 Sense + DRV2605L

Initializing DRV2605L haptic driver...
Scanning I2C bus... FOUND at 0x5A
DRV2605L initialized successfully
Initializing BLE...
BLE initialized successfully
Advertising as: Oro-XXXX
System initialized successfully
Device name: Oro-XXXX
Ready for BLE connections
```

### BLE Advertising Check
1. Install "nRF Connect" app on phone (Android/iOS)
2. Open app and scan for devices
3. Look for device named `Oro-XXXX` (XXXX = last 4 MAC address digits)
4. Connect to verify services are present:
   - Oro Haptic Service: `12340000-1234-5678-1234-56789abcdef0`
   - Battery Service: `0000180F-...`

---

## Troubleshooting

### ERROR: "bluefruit.h: No such file or directory"

**Cause:** Seeed nRF52 board package not installed or wrong board selected

**Fix:**
1. Verify board package installation: Tools → Board → Boards Manager → Search "Seeed nRF52"
2. Ensure "Seeed nRF52 Boards" is installed
3. Select correct board: Tools → Board → Seeed nRF52 Boards → Seeed XIAO nRF52840 Sense
4. Restart Arduino IDE

---

### WARNING: "ArduinoBLE library incompatible with nRF52 architecture"

**Cause:** ArduinoBLE library is installed (should NOT be used)

**Fix:**
1. Sketch → Include Library → Manage Libraries
2. Search "ArduinoBLE"
3. If installed, click "Uninstall"
4. Restart Arduino IDE
5. Recompile

---

### ERROR: "Adafruit_DRV2605.h: No such file or directory"

**Cause:** DRV2605 library not installed

**Fix:**
1. Sketch → Include Library → Manage Libraries
2. Search "Adafruit DRV2605"
3. Install "Adafruit DRV2605 Library"
4. Wait for installation to complete
5. Recompile

---

### ERROR: "Upload failed" or "No DFU capable USB device available"

**Cause:** Board not in bootloader mode or wrong port

**Fix:**
1. Press RESET button on XIAO **twice quickly** (within 0.5 seconds)
2. LED should pulse orange/green (bootloader mode)
3. Verify COM port: Tools → Port → Select the port that appeared
4. Click Upload immediately
5. If still fails, try a different USB cable (must support data transfer)

---

### Serial Monitor shows "Scanning I2C bus... NOT FOUND at 0x5A"

**Cause:** DRV2605L haptic driver not connected or wrong I2C pins

**Fix:**
1. Verify I2C wiring:
   - DRV2605L SDA → XIAO D4 (pin 4)
   - DRV2605L SCL → XIAO D5 (pin 5)
   - DRV2605L VIN → XIAO 3.3V (NOT 5V!)
   - DRV2605L GND → XIAO GND
2. Check for loose connections
3. Verify DRV2605L is powered (measure 3.3V on VIN)

---

### Serial Monitor shows "Failed to initialize BLE"

**Cause:** BLE stack initialization error

**Fix:**
1. Verify correct board selected (Seeed XIAO nRF52840 Sense)
2. Press RESET button on XIAO
3. Check serial output for more specific error messages
4. Try re-uploading firmware

---

### Device Not Appearing in BLE Scan

**Cause:** BLE initialization failed or device already connected elsewhere

**Fix:**
1. Open Serial Monitor, verify "BLE initialized successfully" message
2. If phone was previously connected, disconnect/forget device in phone Bluetooth settings
3. Restart BLE scan on phone
4. Press RESET button on XIAO to restart advertising
5. Verify antenna connection (built-in on XIAO nRF52840)

---

## File Structure

```
firmware/
├── OroHapticFirmware/
│   └── OroHapticFirmware.ino    ← Main firmware file (compile this)
├── libraries.txt                 ← Library installation guide
├── BLE_PROTOCOL.md              ← BLE communication protocol
├── BLE_LIBRARY_FIX.md           ← Migration guide (ArduinoBLE → Bluefruit)
├── COMPILE_INSTRUCTIONS.md      ← This file
└── README.md                    ← Project overview
```

---

## Library Versions (Verified)

| Component | Version | Source |
|-----------|---------|--------|
| Arduino IDE | 2.3.2 | arduino.cc |
| Seeed nRF52 Boards | 1.1.8 | Seeed via Boards Manager |
| Bluefruit nRF52 | Built-in | Included with board package |
| Adafruit DRV2605 | 1.2.2 | Adafruit via Library Manager |
| Adafruit BusIO | 1.14.1+ | Auto-installed dependency |

---

## Hardware Pin Map (Reference)

```
XIAO nRF52840 Connections:
├─ I2C (DRV2605L)
│  ├─ D4 (pin 4)  → SDA
│  └─ D5 (pin 5)  → SCL
├─ Battery
│  └─ A0          → Voltage divider (optional)
└─ Power
   ├─ 3.3V        → DRV2605L VIN
   └─ GND         → Common ground

DRV2605L Connections:
├─ VIN  → XIAO 3.3V (NOT 5V!)
├─ GND  → XIAO GND
├─ SDA  → XIAO D4
├─ SCL  → XIAO D5
├─ OUT+ → Motor positive
└─ OUT- → Motor negative
```

---

## Next Steps

After successful compilation and upload:

1. **Test Haptic:** Motor should vibrate during startup (double-click pattern)
2. **Test BLE:** Device should appear in nRF Connect app scan
3. **Test Battery:** Serial monitor should show battery voltage/percentage
4. **Integrate with Android App:** Follow `INTEGRATION_GUIDE.md`

---

## Support Resources

- **Seeed XIAO nRF52840 Wiki:** https://wiki.seeedstudio.com/XIAO_BLE/
- **Bluefruit Library Docs:** https://github.com/adafruit/Adafruit_nRF52_Arduino
- **DRV2605L Datasheet:** https://www.ti.com/lit/ds/symlink/drv2605l.pdf
- **BLE Protocol Spec:** See `BLE_PROTOCOL.md` in this directory

---

**Last Updated:** 2025-11-02
**Firmware Version:** 1.0 (Bluefruit nRF52)
**Tested On:** Arduino IDE 2.3.2, Seeed nRF52 Boards 1.1.8
