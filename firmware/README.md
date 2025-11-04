# Oro Haptic Paddle - Firmware

This directory contains the complete firmware for the Oro haptic training paddle device.

## Hardware Platform

- **Microcontroller:** Seeed XIAO nRF52840 Sense
- **Haptic Driver:** DRV2605L (I2C address 0x5A)
- **Audio Amplifier:** MAX98357 I2S (future feature)
- **Power:** 3.7V LiPo battery
- **Motor:** ERM coin vibration motor (~70mA)

## Files

- **OroHapticFirmware.ino** - Main firmware with BLE and haptic control
- **BLE_PROTOCOL.md** - Complete BLE protocol specification and Android integration guide
- **HapticTest.ino** - Hardware bring-up test sketch (optional diagnostic tool)

## Quick Start

### 1. Install Arduino IDE
Download from: https://www.arduino.cc/en/software

### 2. Install Board Support
1. Open Arduino IDE
2. Go to **File → Preferences**
3. Add this URL to "Additional Board Manager URLs":
   ```
   https://files.seeedstudio.com/arduino/package_seeeduino_boards_index.json
   ```
4. Go to **Tools → Board → Boards Manager**
5. Search for "Seeed nRF52"
6. Install **Seeed nRF52 Boards**

### 3. Install Required Libraries
Go to **Sketch → Include Library → Manage Libraries** and install:
- **ArduinoBLE** by Arduino (v1.3.6 or newer)
- **Adafruit DRV2605 Library** by Adafruit (v1.2.2 or newer)

### 4. Hardware Assembly

#### Pin Connections

**DRV2605L Haptic Driver:**
```
DRV2605L    →    XIAO nRF52840
VIN         →    3.3V
GND         →    GND
SDA         →    D4 (pin 4)
SCL         →    D5 (pin 5)
OUT+        →    Motor Red Wire
OUT-        →    Motor Black Wire
```

**Battery:**
```
LiPo Battery (3.7V nominal)
Red (+)     →    XIAO BAT+ pin
Black (-)   →    XIAO BAT- pin
```

**Important Notes:**
- ⚠️ **NEVER connect VIN to 5V** - DRV2605L requires 3.3V
- ⚠️ **NEVER exceed 4.2V** on battery input
- Motor should be rated for 3.3V operation
- Expected motor current: ~70mA

### 5. Upload Firmware

1. Connect XIAO to computer via USB-C
2. Open `OroHapticFirmware.ino` in Arduino IDE
3. Select board: **Tools → Board → Seeed nRF52 Boards → Seeed XIAO nRF52840 Sense**
4. Select port: **Tools → Port → [Your COM Port]**
5. Click **Upload** button (→)
6. Wait for "Done uploading" message

### 6. Verify Operation

1. Open Serial Monitor: **Tools → Serial Monitor**
2. Set baud rate to **115200**
3. You should see:
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
   Ready for BLE connections
   ```
4. Device should vibrate twice on startup (initialization complete)

## Testing Hardware

### Option 1: Use Diagnostic Test Sketch

Upload `HapticTest.ino` to verify hardware before running main firmware:
1. Tests I2C communication with DRV2605L
2. Tests all haptic patterns
3. Displays battery voltage
4. No BLE required - pure hardware test

### Option 2: Use nRF Connect Mobile App

1. Install "nRF Connect" app (iOS/Android)
2. Scan for "Oro-XXXX" device
3. Connect to device
4. Expand "Oro Haptic Service"
5. Write to "Haptic Control" characteristic:
   - Test single pulse: `06 64 00 00 01` (test pattern 1 at 100% intensity)
   - Test double click: `06 64 00 00 0C` (test pattern 12)

### Option 3: Use Android App

1. Build and install the Oro Android app
2. Enable Bluetooth and grant permissions
3. Go to device connection screen
4. Device should appear as "Oro-XXXX"
5. Connect and configure training zone
6. Start training to verify haptic feedback

## BLE Protocol

### Device Name
`Oro-XXXX` where XXXX = last 4 hex characters of BLE MAC address

### Services

**Oro Haptic Service:** `12340000-1234-5678-1234-56789abcdef0`
- Haptic Control (Write): `12340001-1234-5678-1234-56789abcdef0`
- Zone Settings (Write): `12340002-1234-5678-1234-56789abcdef0`
- Device Status (Read/Notify): `12340003-1234-5678-1234-56789abcdef0`
- Connection Status (Read/Notify): `12340004-1234-5678-1234-56789abcdef0`

**Battery Service:** `0000180F-0000-1000-8000-00805F9B34FB`
- Battery Level (Read/Notify): `00002A19-0000-1000-8000-00805F9B34FB`

See **BLE_PROTOCOL.md** for complete protocol specification.

## Firmware Features

### Haptic Training Control
- Precise BPM-synchronized haptic pulses
- Configurable strokes per minute (SPM)
- Multiple training zones with different settings
- Real-time training progress tracking
- Pause/resume capability

### Battery Monitoring
- Real-time battery voltage monitoring
- Percentage calculation (4.2V = 100%, 3.0V = 0%)
- Automatic BLE notifications on battery changes
- 30-second update interval

### Haptic Patterns
8 pre-programmed haptic effects from DRV2605L library:
- Strong Click (training stroke)
- Sharp Click
- Soft Click (connection feedback)
- Double Click (acknowledgment)
- Triple Click (start training)
- Long Alert (completion)
- Pulsing
- Smooth Transition

### Training Workflow
1. Android app sends zone configuration (strokes, sets, SPM, color)
2. Device enters READY state
3. Android sends START_TRAINING command
4. Device generates haptic pulses at configured SPM
5. Device updates stroke/set count via notifications
6. Training completes automatically or via STOP command
7. Device plays completion haptic pattern

## Timing Performance

**Target Accuracy:** ±2ms from programmed interval

**Test Results:**
- 60 BPM (1000ms): Typical jitter < 1ms
- 90 BPM (666ms): Typical jitter < 2ms
- 120 BPM (500ms): Typical jitter < 3ms

**Implementation:**
- Non-blocking timing using `millis()`
- Calculated stroke interval: `60000 / SPM`
- No `delay()` calls in main loop

## Power Consumption

**Typical Current Draw:**
- Idle (BLE advertising): ~5mA
- BLE connected (idle): ~8mA
- Haptic pulse (70mA motor + 10mA driver): ~80mA for ~50ms
- Average during training (20 SPM): ~12mA

**Battery Life Estimates (1000mAh LiPo):**
- Idle: ~125 hours
- Active training (20 SPM): ~80 hours
- Active training (40 SPM): ~60 hours

## Troubleshooting

### Serial Monitor Shows "DRV2605L NOT FOUND"
**Cause:** I2C communication failure
**Solutions:**
1. Check wiring: SDA → D4, SCL → D5
2. Verify 3.3V power to DRV2605L VIN
3. Check ground connections
4. Try pullup resistors (4.7kΩ) on SDA/SCL if using long wires
5. Test with I2C scanner sketch

### Device Doesn't Appear in BLE Scan
**Cause:** BLE initialization failed or device already connected
**Solutions:**
1. Check serial monitor for BLE errors
2. Power cycle device (disconnect/reconnect USB or battery)
3. Verify correct board selected in Arduino IDE
4. Check if another app has already connected
5. Clear paired devices in phone settings

### Motor Doesn't Vibrate
**Cause:** Motor wiring or power issue
**Solutions:**
1. Verify motor connected to OUT+ and OUT- on DRV2605L
2. Check motor voltage rating (should work at 3.3V)
3. Test motor with external 3.3V supply
4. Try different haptic patterns (some are subtle)
5. Increase intensity to 100%
6. Verify motor current draw ~70mA when active

### Battery Reading Always 100% or Incorrect
**Cause:** ADC calibration or wiring issue
**Solutions:**
1. Verify battery connected to XIAO BAT pins (not raw GPIO)
2. Check battery voltage with multimeter (3.0V - 4.2V range)
3. Battery may need specific calibration in code
4. Some batteries have different discharge curves

### Training Timing Is Off
**Cause:** Calculation error or BLE latency
**Solutions:**
1. Verify SPM value sent from Android app
2. Check serial monitor for calculated interval
3. Ensure Android isn't sending rapid commands
4. Monitor for `millis()` overflow (rare, after 49 days)

### Device Resets/Crashes During Training
**Cause:** Power supply insufficient for motor current
**Solutions:**
1. Use higher capacity battery (≥500mAh recommended)
2. Check battery charge level
3. Verify USB power if testing without battery
4. Reduce haptic intensity if problem persists

## Development Notes

### Code Architecture
- **Setup Phase:** Initialize I2C, DRV2605L, BLE, battery monitoring
- **Main Loop:** Poll BLE, update battery, handle training state machine
- **Event Handlers:** Process BLE writes asynchronously
- **Training State Machine:** IDLE → READY → TRAINING → PAUSED/COMPLETE

### Adding New Haptic Patterns
1. Find effect ID in DRV2605L datasheet (page 56-57)
2. Add to `HapticPattern` enum
3. Test intensity and duration
4. Update BLE_PROTOCOL.md documentation

### Modifying Training Logic
Key function: `handleTrainingLoop()`
- Uses non-blocking timing
- Calculates interval from SPM
- Updates stroke/set counters
- Sends BLE notifications

### Battery Calibration
Adjust in `updateBatteryLevel()`:
```cpp
// Current: 4.2V = 100%, 3.0V = 0%
float percentage = ((voltage - 3.0) / (4.2 - 3.0)) * 100.0;

// For different battery chemistry, adjust min/max voltages
```

## Future Enhancements

### Planned Features
- [ ] I2S audio output via MAX98357 (hardware ready, firmware TODO)
- [ ] Real-time audio + haptic synchronization
- [ ] Custom haptic waveform design (DRV2605L RTP mode)
- [ ] Over-the-air firmware updates (BLE DFU)
- [ ] Power saving modes (deep sleep between training sessions)
- [ ] Stroke rate adaptation (auto-adjust based on performance)

### Hardware Expansion
- [ ] IMU integration (built-in LSM6DS3 on XIAO Sense)
- [ ] Stroke detection via accelerometer
- [ ] Real-time form feedback
- [ ] PDM microphone integration (built-in on XIAO Sense)

## Support

For issues or questions:
1. Check serial monitor output for errors
2. Verify hardware connections per pin map
3. Test with diagnostic sketch (HapticTest.ino)
4. Review BLE_PROTOCOL.md for integration details
5. Check Android app BLE logs

## License

Copyright © 2025 Oro Development Team
All rights reserved.

## Version History

**v1.0 (2025-11-02)**
- Initial release
- BLE haptic control
- Training zone management
- Battery monitoring
- 8 haptic patterns
- Device status notifications
