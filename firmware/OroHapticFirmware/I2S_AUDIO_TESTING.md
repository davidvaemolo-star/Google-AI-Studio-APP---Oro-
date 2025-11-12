# I2S Audio Testing Guide for Oro Haptic Paddle

## Hardware Setup

### Required Components
- **nRF52840**: Seeed XIAO nRF52840 Sense
- **Audio Amp**: MAX98357A I2S Audio Amplifier breakout
- **Speaker**: 1W 8Ω speaker
- **Power**: Ensure adequate power supply (LiPo battery or USB)

### Wiring Connections

```
nRF52840 XIAO          MAX98357A
─────────────────      ──────────
D1 (GPIO 1)     ────→  BCLK (Bit Clock)
D2 (GPIO 2)     ────→  LRC (Word Select)
D0 (GPIO 0)     ────→  DIN (Data Input)
D6 (GPIO 6)     ────→  SD (Shutdown Control)
GND             ────→  GND
VDD (3.3V)      ────→  VIN

MAX98357A Configuration:
─────────────────────────
GAIN pin → GND (9dB gain - recommended)
         or VDD (15dB gain - louder but may distort)
         or FLOAT (12dB gain)

SD pin → D6 (GPIO controlled - HIGH=enabled, LOW=shutdown)
```

### Power Control
The firmware includes automatic power control:
- SD_MODE pin (D6) is connected to MAX98357A SD pin
- Amplifier is enabled on startup (SD pin set HIGH)
- Use `audioPlayer.suspend()` to mute/power down
- Use `audioPlayer.resume()` to re-enable audio

---

## Quick Test Procedure

### Step 1: Flash Firmware
1. Open `OroHapticFirmware.ino` in Arduino IDE
2. Select **Board**: "Seeed XIAO nRF52840 Sense"
3. Select correct **Port**
4. Click **Upload**

### Step 2: Monitor Serial Output
1. Open **Serial Monitor** (115200 baud)
2. Look for initialization messages:
```
=== Oro Haptic Paddle Firmware ===
Hardware: XIAO nRF52840 Sense + DRV2605L
...
Configuring I2S peripheral...
I2S initialized successfully
Sample Rate: 16000 Hz
```

### Step 3: Test Audio via BLE
1. Connect to device from Android app
2. Start a training session
3. Listen for audio prompts:
   - **Training Start**: 3 ascending beeps (800→1000→1200 Hz)
   - **Halfway Point**: Single 1000 Hz beep
   - **Set Complete**: Two quick 1200 Hz beeps
   - **Zone Transition**: Sweep from 800→1200 Hz

---

## Troubleshooting

### Problem: No Sound at All

**Check 1: Serial Monitor**
```
Look for: "I2S initialized successfully"
If you see: "WARNING: Failed to initialize I2S"
→ There's an initialization problem
```

**Check 2: Wiring**
- Verify connections with multimeter
- Check for loose wires
- Ensure speaker is connected to MAX98357A output

**Check 3: MAX98357A Power**
```
Measure voltage at MAX98357A VIN pin
Should be: 3.3V (from XIAO)
If 0V: Check power connection
```

**Check 4: SD_MODE Pin**
```
MAX98357A is DISABLED if SD_MODE is LOW
→ Tie SD_MODE to VDD (3.3V) to enable
```

**Check 5: Speaker**
- Try a different speaker
- Check speaker impedance (4Ω or 8Ω)
- Verify speaker polarity doesn't matter but check connections

---

### Problem: Very Faint Audio (Even at Max Volume)

**Cause 1: I2S Alignment Mismatch (Common with Clone Boards)**
```
Some clone MAX98357A boards expect different I2S data alignment

Solution:
1. Type 'f' in Serial Monitor to toggle alignment
2. Test with 't' command
3. Toggle again with 'f' if still quiet
4. One alignment should work much better

Technical: MAX98357A can accept LEFT or RIGHT aligned I2S data
Some clones are pickier about which format they accept
```

**Cause 2: GAIN Pin at Minimum**
```
Check GAIN pin configuration:
- GAIN → GND: 9dB (quietest)
- GAIN → FLOAT: 12dB
- GAIN → VDD: 15dB (loudest)

Connect GAIN to VDD/3.3V for maximum volume
```

**Cause 3: Defective Speaker or Board**
```
Try a different 4Ω or 8Ω speaker
Test with a known-good MAX98357A board
Check speaker for damage (torn cone, loose connections)
```

---

### Problem: Distorted or Crackling Sound

**Cause 1: Volume Too High**
```cpp
// Reduce volume in Android app, or modify firmware:
audioPlayer.playTone(1000, 150, 50);  // Reduce from 80-100 to 50
```

**Cause 2: GAIN Setting Too High**
```
MAX98357A GAIN pin configuration:
- Connected to GND: 9dB (recommended - clean sound)
- Floating: 12dB (moderate)
- Connected to VDD: 15dB (loudest, may distort)
```

**Cause 3: Power Supply Issues**
```
Speaker draws current peaks during loud sounds
→ Use higher capacity power source
→ Add 100µF capacitor across MAX98357A VIN/GND
```

---

### Problem: High-Pitched Whine or Hiss

**Cause 1: I2S Clock Noise**
```
Normal for MAX98357A - slight hiss when enabled
Solutions:
1. Reduce GAIN (use GND setting)
2. Use SD_MODE to power down when not playing
3. Add ferrite bead on VIN line
```

**Cause 2: Ground Loop**
```
- Ensure common ground between nRF52840 and MAX98357A
- Keep wires short and twisted together
- Avoid running near power lines
```

---

### Problem: Audio Events Received but No Playback

**Check Serial Monitor:**
```
Should see:
  Audio event: 0x01 (Training Start) at volume 80
  Playing tone: 800 Hz for 100 ms at volume 80

If you only see first line:
→ playTone() is not being called
→ Check audio_i2s.cpp compilation
```

**Debug Code:**
Add to `playAudioEvent()` before switch statement:
```cpp
Serial.print("audioPlayer initialized: ");
Serial.println(audioPlayer.isPlaying() ? "YES" : "NO");
```

---

### Problem: Tones Play but Wrong Pitch

**Cause: Sample Rate Mismatch**
```cpp
// In audio_i2s.cpp, check I2S configuration:
NRF_I2S->CONFIG.RATIO = 7;     // Should be 256x
NRF_I2S->CONFIG.MCKFREQ = 0x10000000;  // 4.096 MHz MCK

If tones too high: Increase MCKFREQ
If tones too low: Decrease MCKFREQ
```

**Calibration Test:**
```cpp
// In setup(), add after audioPlayer.begin():
Serial.println("Playing 1000 Hz calibration tone...");
audioPlayer.playTone(1000, 1000, 50);  // 1 second at 1kHz
delay(2000);
// Use frequency counter app to verify actual frequency
```

---

## Oscilloscope Debug (Advanced)

### Pin Probing Points

**D1 (BCLK - Bit Clock):**
```
Expected: ~512 kHz square wave
Formula: Sample Rate × 32 bits/sample = 16000 × 32 = 512 kHz
```

**D2 (LRCLK - Word Select):**
```
Expected: 16 kHz square wave (sample rate)
Duty cycle: 50% (50% left channel, 50% right channel)
```

**D0 (DIN - Data):**
```
Expected: I2S data stream
Should show varying amplitude during tone playback
Should be silent (flat) when not playing
```

### Signal Analysis
```
Trigger on LRCLK rising edge
Observe 32 BCLK cycles per LRCLK period
DIN should change on BCLK edges
```

---

## Performance Optimization

### Reduce Latency
```cpp
// In audio_i2s.h, reduce buffer size:
#define AUDIO_BUFFER_SIZE 64  // From 256 (faster response, more CPU)
```

### Reduce Memory Usage
```cpp
#define AUDIO_BUFFER_SIZE 128  // From 256 (saves 256 bytes RAM)
```

### Non-Blocking Playback (Future Enhancement)
Current implementation blocks during playback. To enable concurrent haptics:
1. Implement interrupt-based buffer swapping
2. Use double buffering
3. See `audio_i2s.h` comments for IRQ handler structure

---

## Quick Reference Commands

### Test Audio from Serial Monitor
Add this function to firmware for manual testing:
```cpp
void testAudio() {
  Serial.println("Audio Test Menu:");
  Serial.println("1: 1kHz tone");
  Serial.println("2: Training start");
  Serial.println("3: Zone transition");

  if (Serial.available()) {
    char cmd = Serial.read();
    switch(cmd) {
      case '1': audioPlayer.playTone(1000, 500, 80); break;
      case '2': playAudioEvent(AUDIO_TRAINING_START, 80); break;
      case '3': playAudioEvent(AUDIO_ZONE_TRANSITION, 80); break;
    }
  }
}

// Call in loop():
testAudio();
```

---

## Known Limitations

1. **Blocking Playback**: Audio playback blocks code execution
   - Haptics may be delayed during long audio events
   - BLE notifications may be delayed
   - **Fix**: Implement interrupt-based playback

2. **Mono to Stereo Duplication**: Same audio on both L/R channels
   - Not an issue for single speaker use
   - Could be optimized for true stereo if needed

3. **Simple Tones Only**: No complex waveforms or WAV playback
   - Sine waves only
   - **Enhancement**: Add square/triangle waves, or WAV file support

4. **Power Consumption**: I2S peripheral always active
   - Use `suspend()`/`resume()` to save power
   - Consider SD_MODE pin control

---

## Success Criteria

✅ **I2S Initialized**: Serial shows "I2S initialized successfully"
✅ **Tones Play**: Clear beeps on training events
✅ **Correct Pitch**: 1000 Hz tone sounds like 1000 Hz
✅ **No Distortion**: Clean sound at 50-80% volume
✅ **All Events Work**: Training start, halfway, set complete, etc.

---

## Next Steps After Working Audio

1. **Tune Volume Levels**: Adjust per-event volume for best experience
2. **Create Custom Tones**: Design unique melodies for each event
3. **Add Polyphony**: Play chords or harmonies
4. **WAV Playback**: Store pre-recorded sounds in flash
5. **Dynamic BPM Sync**: Play metronome beeps at stroke rate

---

## Support Resources

- **MAX98357A Datasheet**: https://datasheets.maximintegrated.com/en/ds/MAX98357A-MAX98357B.pdf
- **nRF52840 I2S Spec**: Section 6.34 of nRF52840_PS_v1.1.pdf
- **Nordic DevZone**: https://devzone.nordicsemi.com/
- **Adafruit nRF52 Forums**: https://forums.adafruit.com/

---

**Happy Testing!** 🎵
