# Oro Haptic Paddle - Canoeing Training System

An Android-based training system that uses haptic feedback and audio cues to improve crew synchronization in outrigger canoeing.

## System Architecture

**Oro Haptic Paddle** consists of two main components:

1. **Android App** (`android-app/`) - Mobile training application
2. **Firmware** (`firmware/`) - nRF52840-based hardware devices

## Hardware

- **MCU**: Seeed XIAO nRF52840 Sense
- **Haptic Driver**: DRV2605L (I2C)
- **Audio Output**: MAX98357A (I2S)
- **Sensors**: LSM6DS3 IMU (built-in)
- **Communication**: Bluetooth Low Energy (BLE 5.0)

## Android App Setup

**Prerequisites:** Android Studio, Android SDK 34+

1. Open `android-app/` in Android Studio
2. Build and run on physical Android device (BLE required)
3. Grant Bluetooth and location permissions when prompted

## Firmware Setup

**Prerequisites:** Arduino IDE, Adafruit nRF52 board support

1. Install board support via Arduino Board Manager:
   - Add URL: `https://adafruit.github.io/arduino-board-index/package_adafruit_index.json`
   - Install "Adafruit nRF52 by Adafruit"

2. Install required libraries:
   - Adafruit BusIO
   - Adafruit DRV2605 Library
   - Arduino LSM6DS3

3. Open `firmware/OroHapticFirmware/OroHapticFirmware.ino`
4. Select board: "Seeed XIAO nRF52840 Sense"
5. Upload to device

## Features

- **Stroke Detection**: Real-time paddle stroke analysis using IMU
- **Haptic Feedback**: Zone-specific vibration patterns for training intensity
- **Audio Cues**: I2S audio announcements for training milestones
- **Multi-Device Sync**: Supports up to 6 devices with pacer-follower architecture
- **Training Zones**: Configurable zones with custom stroke counts, sets, and SPM targets

## Development Status

This system is under active development. Recent improvements:
- ✅ Fixed I2S audio (mono left-channel mode)
- ✅ Enhanced stroke detection reliability
- ✅ Improved BLE device discovery
- 🚧 Calibration UI (in progress)
- 🚧 Command acknowledgments (in progress)

## License

Proprietary - All rights reserved
