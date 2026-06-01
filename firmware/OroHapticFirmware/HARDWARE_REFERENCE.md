# Oro Haptic Paddle — Hardware Reference

## Microcontroller

| Component | Details |
|-----------|---------|
| Board | Seeed XIAO nRF52840 Sense |
| SoC | Nordic nRF52840 (ARM Cortex-M4, 64 MHz) |
| BLE | Bluetooth 5.0 LE (via Bluefruit library) |

---

## Peripherals

| Component | Part | Interface | Address / Notes |
|-----------|------|-----------|-----------------|
| Haptic Driver | DRV2605L | I2C | 0x5A |
| IMU | LSM6DS3 | I2C | 0x6A (built-in on XIAO Sense) |
| Audio Amplifier | MAX98357A | I2S | Class-D mono, 1W |
| Speaker | 8 Ω, 1 W | — | Connected to MAX98357A output |
| Force Sensor | FSR (Force Sensitive Resistor) | Analog | Voltage divider to ADC |
| LRA Motor | Linear Resonant Actuator | — | Driven by DRV2605L |

---

## Pin Map

### I2C Bus (DRV2605L Haptic + LSM6DS3 IMU)

| Signal | Arduino Label | nRF52840 GPIO | Notes |
|--------|--------------|---------------|-------|
| SDA | D4 | P0.04 | Shared bus for DRV2605L & LSM6DS3 |
| SCL | D5 | P0.05 | Shared bus for DRV2605L & LSM6DS3 |

### I2S Audio (MAX98357A Amplifier)

The nRF52840 I2S peripheral uses dedicated castellated pads on the back of the XIAO board. The `SD_MODE` enable line uses a standard GPIO.

| Signal | Function | nRF52840 GPIO | Arduino Label | Notes |
|--------|----------|---------------|--------------|-------|
| BCLK | Bit Clock | P0.19 (GPIO 19) | — | Castellated pad |
| LRCLK / WS | Word Select | P1.01 (GPIO 33) | — | Castellated pad |
| DIN | Data Input to amp | P0.15 (GPIO 15) | — | Castellated pad |
| SD_MODE | Shutdown / Enable | P1.11 (GPIO 43) | D6 | HIGH = enabled, LOW = mute |

> **Note:** The `.ino` header comment lists I2S on D0/D1/D2 (legacy labels). The actual hardware driver in `audio_i2s.h` uses the nRF52840 chip-level GPIO numbers above (castellated pads).

### Analog Inputs

| Signal | Arduino Label | nRF52840 GPIO | Notes |
|--------|--------------|---------------|-------|
| Battery Monitor | A0 | P0.02 | Voltage divider; read every 30 s |
| FSR Force Sensor | A3 / D3 | P0.29 | Polled every 50 ms (20 Hz); threshold 50% |

### Digital I/O

| Signal | Arduino Label | nRF52840 GPIO | Direction | Notes |
|--------|--------------|---------------|-----------|-------|
| Power Switch | D10 | P1.15 | Input (PULLUP) | Tactile switch; 2 s hold → System OFF (`sd_power_system_off`). Same pin configured as GPIO sense wake source — press wakes via chip reset. 50 ms debounce on press detection. See ADR-0011. |
| LED Red | D9 | P1.14 | Output | Common-cathode RGB — Active HIGH |
| LED Green | D8 | P1.13 | Output | Common-cathode RGB — Active HIGH |
| LED Blue | D7 | P1.12 | Output | Common-cathode RGB — Active HIGH |

---

## MAX98357A Gain Configuration

| GAIN Pin Connection | Gain | Perceived Volume |
|---------------------|------|-----------------|
| GND | 9 dB | Quiet (25%) |
| Floating | 12 dB | Moderate (50%) |
| VDD (3.3 V) | 15 dB | Loud (100%) — recommended |

> GAIN is latched at power-up. A power cycle is required after any wiring change.

---

## BLE Services & Characteristics

### Oro Haptic Service `12340000-1234-5678-1234-56789abcdef0`

| Characteristic | UUID Suffix | Permissions | Format | Purpose |
|---------------|-------------|-------------|--------|---------|
| Haptic Control | `…0001` | Write | `[cmd][intensity][duration_ms×2][pattern]` | Trigger haptic patterns |
| Zone Settings | `…0002` | Write | `[strokes×2][sets][spm×2][zone_color]` | Training zone config |
| Device Status | `…0003` | Read / Notify | `[state][power_state][stroke×2][set][battery]` | Device state |
| Connection Status | `…0004` | Read / Notify | `[connected][rssi]` | BLE RSSI |
| Stroke Event | `…0005` | Notify | `[phase][timestamp_ms×4][accel_float16×2]` | Stroke detection events |
| Calibration | `…0006` | Write / Notify | `[cmd][threshold_float16×2][status]` | Calibration control |
| Audio Control | `…0007` | Write | `[audio_event][volume]` | Trigger audio prompts |
| FSR Data | `…0008` | Read / Notify | `[force_pct][raw_adc_lsb][raw_adc_msb][threshold_flag]` | Force sensor data |

> Note: The LED is firmware-driven from `DeviceState` — there is no BLE characteristic for LED control. See ADR-0009.

### Battery Service `0x180F` (Standard)

| Characteristic | UUID | Permissions | Format |
|---------------|------|-------------|--------|
| Battery Level | `0x2A19` | Read / Notify | `[level 0–100%]` |

---

## IMU Stroke Detection

| Parameter | Value |
|-----------|-------|
| Sample Rate | 104 Hz |
| Detection Threshold | 1.0 g (default; configurable via BLE calibration) |
| Min Stroke Interval | 200 ms |
| Calibration Samples | 10 |

Stroke phases: `CATCH (0x01)` → `DRIVE (0x02)` → `FINISH (0x03)` → `RECOVERY (0x04)`

---

## Audio Events

| Event ID | Name | Description |
|----------|------|-------------|
| 0x01 | `AUDIO_TRAINING_START` | Session start beep |
| 0x02 | `AUDIO_HALFWAY` | Halfway point chime |
| 0x03 | `AUDIO_SET_COMPLETE` | Set complete tone |
| 0x04 | `AUDIO_LAST_SET` | Last set alert |
| 0x05 | `AUDIO_ZONE_TRANSITION` | Zone transition sound |
| 0x06 | `AUDIO_SESSION_COMPLETE` | Session complete fanfare |
| 0x07 | `AUDIO_PAUSE` | Pause beep |
| 0x08 | `AUDIO_RESUME` | Resume beep |
| 0x09 | `AUDIO_POWER_ON` | Power-on "Oro" prompt |

Audio sample rate: 32 kHz, 16-bit, stereo (mono source duplicated).

---

## Haptic Patterns (DRV2605L Effect Library)

| Effect ID | Name | Description |
|-----------|------|-------------|
| 1 | `PATTERN_STRONG_CLICK` | Sharp single click |
| 2 | `PATTERN_SHARP_CLICK` | Medium click |
| 3 | `PATTERN_SOFT_CLICK` | Gentle click |
| 12 | `PATTERN_DOUBLE_CLICK` | Two quick clicks |
| 13 | `PATTERN_TRIPLE_CLICK` | Three quick clicks |
| 47 | `PATTERN_PULSING` | Continuous pulse |
| 51 | `PATTERN_TRANSITION` | Smooth transition |
| 24 | `PATTERN_ALERT_750MS` | Long alert |

---

*Source: `OroHapticFirmware.ino`, `audio_i2s.h`, `GAIN_PIN_WIRING.txt`*
