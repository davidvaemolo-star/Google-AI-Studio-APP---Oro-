# Voice Prompts Setup Guide

This guide walks you through replacing beep tones with voice prompts ("Training start", "Halfway", etc.) in your Oro Haptic Paddle firmware.

## Overview

Instead of beeps, your device will say:
- "Training start" (instead of 3 ascending beeps)
- "Halfway" (instead of single beep)
- "Set complete" (instead of 2 quick beeps)
- "Last set" (instead of long tone)
- "Zone transition" (instead of sweep)
- "Session complete" (instead of fanfare)
- "Pause" (instead of descending beep)
- "Resume" (instead of ascending beep)

**Memory usage:** ~200-250 KB (plenty of space available)

---

## Step 1: Install Python Dependencies

You need Python 3.7+ and these libraries:

```bash
pip install gtts pydub numpy
```

**Note:** You also need ffmpeg installed on your system:
- **Windows:** Download from https://ffmpeg.org/download.html
- **Linux:** `sudo apt-get install ffmpeg`
- **Mac:** `brew install ffmpeg`

---

## Step 2: Generate Voice Prompts

Run the generation script:

```bash
cd firmware/OroHapticFirmware
python generate_voice_simple.py
```

This will:
- Generate 8 voice prompts using Google TTS (free, no API key needed)
- Female US voice at fast speech pace
- Output 16kHz, 16-bit mono WAV files
- Save to `voice_prompts_raw/` directory

**Expected output:**
```
Generating: 'Training start'
  ✓ Duration: 950ms
  ✓ Size: 30,400 bytes (29.7 KB)
  ✓ Saved: voice_prompts_raw/training_start.wav
...
```

---

## Step 3: Convert to C Arrays

Run the conversion script:

```bash
python convert_to_c_arrays.py
```

This will:
- Read WAV files from `voice_prompts_raw/`
- Convert to C arrays (int16_t samples)
- Generate `audio_prompts.h` header file
- Show total memory usage

**Expected output:**
```
✓ Generated: audio_prompts.h
✓ Total size: 208,640 bytes (203.8 KB)
✓ Prompt count: 8
```

---

## Step 4: Update Firmware

### 4.1 Add Include Statement

Open `OroHapticFirmware.ino` and add this line near the top (around line 25):

```cpp
#include "audio_prompts.h"  // Voice prompt audio data
```

**Before:**
```cpp
#include "LSM6DS3.h"  // Use Seeed_Arduino_LSM6DS3 library
#include "audio_i2s.h"  // I2S audio playback for MAX98357A

// ============================================================================
```

**After:**
```cpp
#include "LSM6DS3.h"  // Use Seeed_Arduino_LSM6DS3 library
#include "audio_i2s.h"  // I2S audio playback for MAX98357A
#include "audio_prompts.h"  // Voice prompt audio data

// ============================================================================
```

### 4.2 Replace Tone Playback with Voice

Find the `playAudioEvent()` function (around line 1017) and replace the switch statement:

**Find this code:**
```cpp
void playAudioEvent(uint8_t audioEvent, uint8_t volume) {
  Serial.print("Audio event: 0x");
  Serial.print(audioEvent, HEX);
  Serial.print(" (");

  // Log and play the audio event
  switch (audioEvent) {
    case AUDIO_TRAINING_START:
      Serial.print("Training Start");
      // Three ascending beeps
      audioPlayer.playTone(800, 100, volume);
      delay(50);
      audioPlayer.playTone(1000, 100, volume);
      delay(50);
      audioPlayer.playTone(1200, 100, volume);
      break;
    // ... more cases ...
  }
}
```

**Replace with this code:**
```cpp
void playAudioEvent(uint8_t audioEvent, uint8_t volume) {
  Serial.print("Audio event: 0x");
  Serial.print(audioEvent, HEX);
  Serial.print(" (");

  // Validate audio event range
  if (audioEvent == 0 || audioEvent > AUDIO_PROMPT_COUNT) {
    Serial.println("Unknown)");
    Serial.println("Invalid audio event ID");
    return;
  }

  // Get prompt info from lookup table
  AudioPromptInfo promptInfo;
  memcpy_P(&promptInfo, &AUDIO_PROMPT_TABLE[audioEvent], sizeof(AudioPromptInfo));

  // Log event name
  switch (audioEvent) {
    case AUDIO_TRAINING_START: Serial.print("Training Start"); break;
    case AUDIO_HALFWAY: Serial.print("Halfway"); break;
    case AUDIO_SET_COMPLETE: Serial.print("Set Complete"); break;
    case AUDIO_LAST_SET: Serial.print("Last Set"); break;
    case AUDIO_ZONE_TRANSITION: Serial.print("Zone Transition"); break;
    case AUDIO_SESSION_COMPLETE: Serial.print("Session Complete"); break;
    case AUDIO_PAUSE: Serial.print("Pause"); break;
    case AUDIO_RESUME: Serial.print("Resume"); break;
    default: Serial.print("Unknown"); break;
  }

  Serial.print(") at volume ");
  Serial.println(volume);

  // Play voice prompt from flash memory
  audioPlayer.playBuffer(promptInfo.data, promptInfo.size, volume);
}
```

---

## Step 5: Upload to Device

1. Open Arduino IDE
2. Open `OroHapticFirmware.ino`
3. Verify the code compiles (click "Verify" button)
4. Upload to your Seeed XIAO nRF52840 Sense
5. Open Serial Monitor (115200 baud)

**Expected Serial Output:**
```
Audio event: 0x2 (Halfway) at volume 80
Playing buffer: 7520 samples (470.0 ms) at volume 80
Buffer playback complete
```

---

## Step 6: Test Voice Prompts

Use your Android app to trigger audio events:

1. Start a training session (should hear "Training start")
2. Complete half the strokes (should hear "Halfway")
3. Complete a set (should hear "Set complete")
4. Complete session (should hear "Session complete")

---

## Troubleshooting

### Problem: "audio_prompts.h: No such file or directory"

**Solution:** Make sure `audio_prompts.h` is in the same folder as `OroHapticFirmware.ino`

```
OroHapticFirmware/
├── OroHapticFirmware.ino
├── audio_i2s.h
├── audio_i2s.cpp
├── audio_prompts.h          ← Must be here!
└── ...
```

### Problem: Compilation error "not enough memory"

**Solution:** The voice prompts are too large. You can:
1. Reduce audio quality in `generate_voice_simple.py` (lower sample rate to 12kHz or 8kHz)
2. Shorten the voice prompts (use single words instead of phrases)
3. Remove unused prompts from `audio_prompts.h`

### Problem: Voice sounds distorted or too quiet

**Solution:** Adjust volume in your app (should be 70-100 for clear audio)

Check GAIN pin on MAX98357A:
- Connect GAIN to 3.3V (VDD) for maximum volume
- Connect GAIN to GND for quieter output

### Problem: Voice is too slow or too fast

**Solution:** Edit `generate_voice_simple.py` and change the speedup value:

```python
# Speed up by 1.3x for "fast" speech
audio = speedup(audio, playback_speed=1.3)  # Change 1.3 to 1.0 (normal) or 1.5 (faster)
```

Then re-run both Python scripts.

---

## Memory Usage

Current firmware with voice prompts:

| Component | Size |
|-----------|------|
| Firmware code | ~65 KB |
| Libraries | ~120 KB |
| Voice prompts | ~210 KB |
| **Total** | **~395 KB** |
| **Available** | ~600 KB |

---

## Customizing Voice Prompts

### Change Voice Gender/Accent

Edit `generate_voice_simple.py`:

```python
# For male voice, use different language code
tts = gTTS(text=text, lang='en', tld='com.au', slow=False)  # Australian male
tts = gTTS(text=text, lang='en', tld='co.uk', slow=False)   # British
```

### Change Prompt Text

Edit the `PROMPTS` dictionary in `generate_voice_simple.py`:

```python
PROMPTS = {
    "training_start": "Let's go!",          # Instead of "Training start"
    "halfway": "You're halfway there!",     # Instead of "Halfway"
    "set_complete": "Nice work!",           # Instead of "Set complete"
    # ...
}
```

Then re-run both scripts.

### Add Your Own Voice

1. Record your own voice as WAV files (16kHz, mono, 16-bit)
2. Name them: `training_start.wav`, `halfway.wav`, etc.
3. Place in `voice_prompts_raw/` folder
4. Run `convert_to_c_arrays.py` only (skip `generate_voice_simple.py`)

---

## Reverting to Beep Tones

To go back to beep tones:

1. Remove `#include "audio_prompts.h"` from `OroHapticFirmware.ino`
2. Restore original `playAudioEvent()` function (see git history)
3. Delete `audio_prompts.h` file
4. Upload firmware

---

## Files Created

After running the setup scripts:

```
firmware/OroHapticFirmware/
├── generate_voice_simple.py       ← Python script to generate TTS
├── convert_to_c_arrays.py         ← Python script to convert WAV to C
├── audio_prompts.h                ← Generated header (include in firmware)
├── voice_prompts_raw/             ← WAV files (not needed after conversion)
│   ├── training_start.wav
│   ├── halfway.wav
│   ├── set_complete.wav
│   ├── last_set.wav
│   ├── zone_transition.wav
│   ├── session_complete.wav
│   ├── pause.wav
│   └── resume.wav
└── VOICE_PROMPTS_SETUP.md         ← This file
```

---

## Next Steps

1. **Test all audio events** to verify voice quality
2. **Adjust volume** in app if needed (recommended: 80-100%)
3. **Customize prompts** if desired (see "Customizing" section above)
4. **Share feedback** on voice quality and clarity

Enjoy your voice-enabled Oro Haptic Paddle! 🎤

---

## Session Summary Prompts (External QSPI Flash)

The 12 session-summary voice prompts (Sync Rating × Power Range) live on the
on-board 2 MB QSPI flash chip, not in the firmware image. This frees ~600 KB of
internal flash and lets us keep all 12 voices at 16 kHz mono.

You only need to do this **once per device** (or after re-recording the prompts).

### 1. Build the audio blob

```bash
cd firmware
python build_audio_blob.py
```

This resamples the 12 source WAVs in `OroHapticFirmware/voice_prompts_raw/` to
16 kHz mono PCM and packs them into `firmware/audio_blob.bin` with a small index.

### 2. Upload the flasher sketch

In Arduino IDE, open `firmware/OroAudioFlasher/OroAudioFlasher.ino`. Make sure
the **Adafruit SPIFlash** library is installed (Library Manager → search
"Adafruit SPIFlash"). Board: **Seeed XIAO nRF52840 Sense**. Click **Upload**.

When the upload finishes, open Serial Monitor at 115200 baud. You should see:

```
OROFLASHER v1
JEDEC ID 0x...
Size bytes 2097152
```

Close Serial Monitor before the next step (it holds the port).

### 3. Stream the blob to the device

```bash
pip install pyserial          # one-time
python flash_audio.py --port COMx
```

(Replace `COMx` with whichever port the XIAO is on — check Arduino IDE → Tools → Port.)

Expected output (lines starting with `<-` are replies from the device):

```
Blob: .../audio_blob.bin  (727862 bytes)
-> PING
  <- OROFLASHER v1
-> ERASE
  <- ERASED
-> WRITE 727862
  <- READY
  <- WROTE 727862
-> VERIFY 4
  <- MAGIC OROA
-> DONE
  <- BYE

Flash complete. Re-upload OroHapticFirmware.ino to the device.
```

### 4. Re-upload the main firmware

Open `firmware/OroHapticFirmware/OroHapticFirmware.ino`, **Upload**. On boot you
should see in Serial Monitor:

```
External audio ready (Crew Roll-Call clips on QSPI).
[extAudio] JEDEC 0x...
[extAudio] ready, prompts=20
  0x30  off=0xFC  samples=...
  ...
```

Run a full session from the Android app. When it finishes, the device should
**speak** the roll-call ("team sync good. Seat one, pacer, power strong. ...")
instead of beeping it.

If you see `External audio NOT ready -- roll-call will use tone fallback` you
need to redo steps 1-3 (the QSPI either wasn't flashed or the blob got
corrupted). The device will beep the roll-call safely in the meantime.

---

## Crew Roll-Call Clips (ADR-0016)

The end-of-session audio is the **Crew Roll-Call** — the device speaks the crew's sync, then each
seat's sync and power (BLE_PROTOCOL §1.10). This replaces the 12 Sync×Power summary prompts. The
composer in `OroHapticFirmware.ino` (`speakRollCall`) stitches the read-out from 20 short clips that
live on the **external QSPI flash** (same blob mechanism as the old summary prompts, not PROGMEM).

**If the blob isn't flashed, each clip plays a placeholder tone** — so the roll-call already works as
a sequence of beeps; flashing the clips turns the beeps into words.

### The 20 clips

Generated by `generate_roll_call_clips.py` (female US voice, gTTS). Order is load-bearing: the
firmware reads clip *i* from external-flash id `0x30 + i`, matching the `RollCallClip` enum and the
`PROMPTS` list in `build_audio_blob.py`.

| id | clip | spoken |
|----|------|--------|
| 0x30 | `rc_team_sync` | "team sync" |
| 0x31–0x34 | `rc_sync`, `rc_power`, `rc_pacer`, `rc_not_measured` | "sync", "power", "pacer", "not measured" |
| 0x35–0x37 | `rc_rating_poor/good/excellent` | "poor", "good", "excellent" |
| 0x38–0x3B | `rc_power_light/moderate/strong/maximum` | "light", "moderate", "strong", "maximum" |
| 0x3C–0x41 | `rc_seat_1` … `rc_seat_6` | "seat one" … "seat six" |
| 0x42–0x43 | `rc_canoe_1`, `rc_canoe_2` | "canoe one", "canoe two" |

A follower line is stitched as `seat three` · `sync` · `poor` · `power` · `light`; the pacer as
`seat one` · `pacer` · `power` · `strong`; the opener as `team sync` · `good`.

### Generate, pack, and flash

The roll-call clips ride the **same external-QSPI flow** as the (now-retired) summary prompts above —
they just replace the contents of `audio_blob.bin`.

```bash
cd firmware
python generate_roll_call_clips.py   # gTTS → 16 kHz mono WAVs in OroHapticFirmware/voice_prompts_raw/
python build_audio_blob.py           # packs the 20 clips (ids 0x30–0x43) into audio_blob.bin (~518 KB)
```

Then flash `audio_blob.bin` to the device's QSPI exactly as in **steps 2–4** of the "Session Summary
Prompts" section above (upload `OroAudioFlasher`, run `python flash_audio.py --port COMx`, re-upload
the main firmware). On the next session end the device speaks the roll-call instead of beeping.

To change the voice or wording, edit the `CLIPS` list in `generate_roll_call_clips.py` and re-run both
scripts. Any clip the firmware can't read from flash falls back to its tone, so you can flash
incrementally.
