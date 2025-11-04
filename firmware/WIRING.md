# Oro Haptic Paddle - Hardware Wiring Diagram

## Component List

### Required Components
- **Microcontroller:** Seeed XIAO nRF52840 Sense
- **Haptic Driver:** Adafruit DRV2605L Breakout Board
- **Motor:** ERM Coin Vibration Motor (3.3V, ~70mA)
- **Battery:** 3.7V LiPo Battery (recommended: 500-1000mAh)
- **Connector:** JST PH 2.0mm connector (for battery)

### Optional Components
- **Audio Amplifier:** Adafruit MAX98357 I2S (future feature)
- **Speaker:** 1W 8Ω speaker (future feature)
- **Switch:** SPST power switch (recommended)

---

## Pin Connection Table

### DRV2605L Haptic Driver → XIAO nRF52840

| DRV2605L Pin | XIAO Pin | Description |
|--------------|----------|-------------|
| VIN | 3.3V | Power supply (⚠️ NOT 5V!) |
| GND | GND | Ground |
| SDA | D4 (pin 4) | I2C Data |
| SCL | D5 (pin 5) | I2C Clock |
| OUT+ | Motor Red | Motor positive terminal |
| OUT- | Motor Black | Motor negative terminal |

### Battery → XIAO nRF52840

| Battery Wire | XIAO Pin | Description |
|--------------|----------|-------------|
| Red (+) | BAT+ | Battery positive (3.7V) |
| Black (-) | BAT- (GND) | Battery negative/ground |

### Optional: MAX98357 I2S Amplifier → XIAO nRF52840

| MAX98357 Pin | XIAO Pin | Description |
|--------------|----------|-------------|
| VIN | 3.3V | Power supply |
| GND | GND | Ground |
| BCLK | D1 (pin 1) | I2S Bit Clock |
| LRC | D0 (pin 0) | I2S Left/Right Clock |
| DIN | D2 (pin 2) | I2S Data Input |
| SD | D6 (pin 6) | Shutdown (active-low) |
| GAIN | GND | Gain setting (15dB at GND) |
| Speaker + | Speaker Red | Speaker positive |
| Speaker - | Speaker Black | Speaker negative |

---

## Wiring Diagram (ASCII)

```
                    ┌─────────────────────────────┐
                    │  SEEED XIAO nRF52840 SENSE  │
                    │                             │
     Battery ────── │ BAT+                        │
        3.7V        │ BAT-                    3.3V│ ────────┬─── DRV2605L VIN
        LiPo        │ GND                          │         │
                    │                              │         │
                    │                          D0  │ ────────┼─── (Reserved: MAX98357 LRC)
                    │                          D1  │ ────────┼─── (Reserved: MAX98357 BCLK)
                    │                          D2  │ ────────┼─── (Reserved: MAX98357 DIN)
                    │                          D3  │         │
                    │                          D4  │ ────────┼─── DRV2605L SDA (I2C Data)
                    │                          D5  │ ────────┼─── DRV2605L SCL (I2C Clock)
                    │                          D6  │ ────────┼─── (Reserved: MAX98357 SD)
                    │                              │         │
                    │                          GND │ ────────┴─── Common Ground
                    │                              │
                    │                           A0 │ ────────┬─── Battery Voltage Monitor
                    │                              │         │    (Internal connection)
                    └──────────────────────────────┘         │
                                                              │
     ┌────────────────────────────────────────────────────────┘
     │
     │    ┌─────────────────────────┐
     ├─── │ DRV2605L VIN            │
     │    │                         │
     ├─── │ GND                     │
     │    │                         │
     ├─── │ SDA                     │
     │    │                         │
     ├─── │ SCL                     │
     │    │                         │
     │    │ OUT+ ─────────────────┐ │
     │    │                       │ │
     │    │ OUT- ───────────────┐ │ │
     │    └─────────────────────┼─┼─┘
     │                          │ │
     │         ┌────────────────┘ │
     │         │  ┌───────────────┘
     │         │  │
     │         │  │   ┌─────────────────┐
     │         │  └─→ │ ERM Motor (Red) │
     │         │      │                 │
     │         └────→ │ ERM Motor (Blk) │
     │                └─────────────────┘
     │
    GND (Common Ground)
```

---

## Detailed Connection Instructions

### Step 1: DRV2605L Haptic Driver

1. **Power Connections:**
   - Connect DRV2605L **VIN** to XIAO **3.3V** pin
   - ⚠️ **CRITICAL:** Do NOT connect to 5V! DRV2605L is 3.3V only
   - Connect DRV2605L **GND** to XIAO **GND**

2. **I2C Connections:**
   - Connect DRV2605L **SDA** to XIAO **D4** (pin 4)
   - Connect DRV2605L **SCL** to XIAO **D5** (pin 5)
   - Note: No external pullup resistors needed (internal pullups enabled)

3. **Motor Connections:**
   - Connect DRV2605L **OUT+** to motor **red wire** (positive)
   - Connect DRV2605L **OUT-** to motor **black wire** (negative)
   - Motor polarity matters - reversing will work but haptic feel may differ

### Step 2: Battery

1. **Battery Connection:**
   - Connect battery **red (+)** to XIAO **BAT+** pad/pin
   - Connect battery **black (-)** to XIAO **BAT-** pad/pin
   - Use JST PH 2.0mm connector for easy disconnection

2. **Battery Specifications:**
   - Voltage: 3.7V nominal (4.2V max when charged)
   - Capacity: 500-1000mAh recommended
   - Discharge rate: 1C minimum (to handle motor current spikes)
   - DO NOT exceed 4.2V (risk of damaging XIAO)

3. **Optional Power Switch:**
   - Add SPST switch between battery + and XIAO BAT+
   - Allows easy on/off without unplugging battery

### Step 3: Motor Selection

**Recommended Motor Specifications:**
- Type: ERM (Eccentric Rotating Mass) coin vibration motor
- Voltage: 3.3V or 3V rated
- Current: 60-80mA typical
- Diameter: 8-12mm
- Examples:
  - Adafruit Product #1201 (8mm pancake motor)
  - Precision Microdrives 308-100
  - Similar motors from Pololu, SparkFun

**Motor Mounting:**
- Secure motor to prevent movement during vibration
- Use hot glue, double-sided foam tape, or mechanical mounting
- Ensure wires have strain relief

---

## Power Distribution

```
                LiPo Battery (3.7V)
                      │
                      ├─────→ XIAO BAT+ ──→ Internal Regulator ──→ 3.3V Rail
                      │                                                │
                      │                                                ├──→ XIAO Logic
                      │                                                │
                      │                                                ├──→ BLE Radio
                      │                                                │
                      │                                                ├──→ DRV2605L VIN
                      │                                                │
                      └─────→ XIAO BAT- (GND) ←──────────────────────┴──→ Common Ground
```

**Current Budget:**
- XIAO idle (BLE advertising): ~5mA
- XIAO connected (BLE active): ~8mA
- DRV2605L idle: ~3mA
- Motor active (haptic pulse): ~70mA
- Peak current during haptic: ~80mA total
- Average during training (20 SPM): ~12mA

**Battery Life Estimate (1000mAh battery):**
- Idle: ~125 hours
- Active training (20 SPM): ~80 hours
- Active training (40 SPM): ~60 hours

---

## Physical Assembly Tips

### Recommended Layout

```
┌────────────────────────────────────┐
│                                    │
│  ┌──────────────────┐              │
│  │  XIAO nRF52840   │              │
│  │      (USB up)    │              │
│  └──────────────────┘              │
│           │                        │
│           │ (I2C wires)            │
│           │                        │
│  ┌──────────────────┐              │
│  │    DRV2605L      │  ┌────────┐  │
│  │    Breakout      │  │ Motor  │  │
│  └──────────────────┘  └────────┘  │
│                                    │
│  ┌──────────────────┐              │
│  │   LiPo Battery   │              │
│  │   (underneath)   │              │
│  └──────────────────┘              │
│                                    │
└────────────────────────────────────┘
```

### Assembly Steps

1. **Mount XIAO:**
   - Mount XIAO on top surface for easy USB access
   - Orient with USB port accessible for programming
   - Use standoffs or double-sided tape

2. **Mount DRV2605L:**
   - Place near motor mounting location
   - Keep I2C wires short (< 10cm recommended)
   - Secure with adhesive or standoffs

3. **Route Wires:**
   - Keep I2C wires (SDA/SCL) away from motor wires
   - Use twisted pairs for I2C if possible
   - Keep power wires (3.3V, GND) thick enough (24-26 AWG)

4. **Mount Motor:**
   - Secure motor to vibrate the entire device/paddle
   - Test vibration direction and intensity
   - Add padding if vibration is too harsh

5. **Battery Placement:**
   - Place battery underneath or in separate compartment
   - Ensure battery can't shift during use
   - Use foam padding to protect battery

6. **Wire Management:**
   - Use heat shrink tubing for protection
   - Add strain relief on all connections
   - Test all connections before closing enclosure

---

## Testing Procedure

### Initial Power-On Test

1. **Before Connecting Battery:**
   - Double-check all connections against wiring table
   - Verify DRV2605L VIN is connected to 3.3V (NOT 5V!)
   - Check for any short circuits with multimeter

2. **First Power-On (USB Only):**
   - Connect XIAO via USB (no battery yet)
   - Upload `HapticTest.ino` sketch
   - Open Serial Monitor (115200 baud)
   - Verify I2C scan finds DRV2605L at 0x5A
   - Verify motor vibrates during test patterns

3. **Battery Connection:**
   - Disconnect USB
   - Connect battery (observe polarity!)
   - XIAO should power on (LED may blink)
   - Motor should vibrate twice (startup pattern)

4. **Full System Test:**
   - Upload `OroHapticFirmware.ino`
   - Verify device advertises as "Oro-XXXX"
   - Connect from Android app or nRF Connect
   - Test haptic commands
   - Verify battery level reporting

### Troubleshooting

**No power from battery:**
- Check battery voltage (should be 3.7-4.2V)
- Check battery polarity (red=+, black=-)
- Check BAT+ and BAT- connections
- Try USB power first to isolate battery issue

**I2C scan doesn't find DRV2605L:**
- Check SDA/SCL connections (D4 and D5)
- Verify 3.3V power to DRV2605L
- Check ground connection
- Try adding 4.7kΩ pullup resistors on SDA/SCL

**Motor doesn't vibrate:**
- Check motor connections (OUT+, OUT-)
- Verify motor voltage rating (3.3V compatible)
- Test motor with external 3.3V power source
- Try different haptic patterns

**Weak vibration:**
- Increase intensity in code (100%)
- Check battery voltage (weak battery = weak motor)
- Verify motor current draw (~70mA expected)
- Try stronger haptic patterns (1, 12, 24)

---

## Safety Warnings

### ⚠️ CRITICAL WARNINGS

1. **Voltage Limits:**
   - NEVER connect DRV2605L VIN to 5V (will damage chip)
   - NEVER exceed 4.2V on battery input (will damage XIAO)
   - NEVER reverse battery polarity (will damage or destroy XIAO)

2. **Current Limits:**
   - Motor should draw < 100mA (higher = wrong motor or short)
   - Total system should draw < 150mA peak
   - If XIAO gets hot, disconnect immediately and check for shorts

3. **Battery Safety:**
   - ONLY use protected LiPo batteries
   - NEVER leave charging unattended
   - NEVER puncture or crush battery
   - Dispose of damaged batteries properly

4. **Soldering Safety:**
   - Work in ventilated area
   - Disconnect power before soldering
   - Don't overheat components (< 350°C tip, < 3 seconds contact)

### ✓ Best Practices

- Use insulated wire (silicone jacket recommended)
- Add strain relief to all connections
- Test continuity with multimeter before power-on
- Start with USB power, add battery only after verification
- Keep a schematic/photo of your wiring for reference
- Label wires with masking tape if needed

---

## Appendix: Alternative Configurations

### Configuration 1: No Battery (USB-Powered Only)
- Use for bench testing
- Connect XIAO via USB-C
- All features work except battery monitoring
- Battery level will read incorrectly (ignore it)

### Configuration 2: External Power (Not Recommended)
- Can power from 3.3V regulated supply
- Connect to XIAO 3.3V pin (NOT VIN or BAT+)
- Current capacity must be > 150mA
- No battery level monitoring

### Configuration 3: With Audio (Future)
- Add MAX98357 I2S amplifier
- Connect per optional pinout table above
- Requires firmware update to enable audio
- Speaker: 1W 8Ω, 20-40mm diameter

---

## Pin Reference Card

**Quick Reference - XIAO nRF52840 Pins:**
```
         USB-C Port
            ▼
    ┌───────────────┐
    │  [RST]   [GND]│
BAT+│   +      3.3V │ ← 3.3V out (regulated)
BAT-│   -       GND │
    │  NC        A0 │ ← Battery monitor (internal)
    │  NC        A1 │
    │  NC        A2 │
    │  NC        A3 │
    │  D0        A4 │ ← I2S LRC (future)
    │  D1        A5 │ ← I2S BCLK (future)
    │  D2       SCK │ ← I2S DIN (future)
    │  D3      MISO │
    │  D4      MOSI │ ← I2C SDA ★ USED
    │  D5        RX │ ← I2C SCL ★ USED
    │  D6        TX │ ← Amp SD (future)
    └───────────────┘
```

**★ Active Pins (Current Firmware):**
- D4 = I2C SDA (DRV2605L)
- D5 = I2C SCL (DRV2605L)
- A0 = Battery monitor (internal)
- 3.3V = Power out
- GND = Common ground
- BAT+/BAT- = Battery input

---

## Revision History

**v1.0 (2025-11-02)**
- Initial wiring documentation
- DRV2605L haptic driver
- Battery configuration
- Reserved pins for future I2S audio

---

**Document:** Hardware Wiring Guide
**Last Updated:** 2025-11-02
**Firmware Compatibility:** OroHapticFirmware v1.0
