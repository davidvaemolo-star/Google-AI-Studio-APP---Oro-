# Loading the Session Summary Voice Prompts

This is a **one-time setup** done per device, before the device ships. It copies the
12 pre-recorded Session Summary voice prompts onto the paddle's external flash chip.
After this is done once, the prompts stay on the chip permanently — re-flashing the
main firmware does **not** erase them.

If a device can't read its prompts for any reason, the firmware falls back to a short
three-note chime so a session always ends with an audible cue. This setup is what makes
the device play the real voice instead of the fallback chime.

---

## The hardware

- **Board:** Seeed XIAO nRF52840 Sense **Plus**.
- **Flash chip:** on-board Puya **P25Q16H**, 2 MB, QSPI. Its JEDEC id is `0x856015`.
- **Audio format:** 16 kHz, mono, 16-bit PCM.

### Two things that will bite you if you get them wrong

**1. Pick the "Sense Plus" board in the Arduino IDE — not plain "Sense".**

The two boards look identical but wire the flash chip up differently in software.
If "Sense" is selected, every flash read and write returns garbage. The IDE's Serial
Monitor title bar shows which board is selected (e.g.
`Seeed XIAO nRF52840 Sense Plus on COM16`) — check it before you start.

**2. The chip's "quad-enable" flag must be set correctly in the code.**

The Puya P25Q16H keeps a setting called the Quad-Enable bit in its **Status Register-2**.
Some chips keep it in Register-1, and the Adafruit flash library has a flag
(`single_status_byte`) that tells it where to look. For this chip that flag must be
**`false`**. If it's wrong, the library writes the quad-enable bit to the wrong place,
it never actually gets set, and then:

- writing to the chip hangs forever, and
- reading from the chip returns garbage (a repeating `0x88` pattern), which shows up
  in the firmware as `[extAudio] bad magic`.

This flag is already set correctly (`single_status_byte = false`) in both
`OroAudioFlasher/OroAudioFlasher.ino` and `OroHapticFirmware/external_audio.cpp`. Don't
change it back. The loader also writes the chip one lane at a time (quad writes left
off) because single-lane writing is reliable for a one-time load.

---

## How the prompts are stored

`build_audio_blob.py` packs the 12 prompts into a single file, `audio_blob.bin`, with a
small header at the very start:

- bytes 0–3: the magic word `OROA` (this is what the firmware checks first)
- then a version number, a prompt count, and an index of where each prompt lives
- then the raw audio samples

The firmware reads that header at startup; if the magic word `OROA` is there and the
count looks sane, it knows the prompts are present and ready.

---

## The procedure

You need two sketches and one Python script:

- `firmware/OroAudioFlasher/OroAudioFlasher.ino` — a temporary loader sketch
- `firmware/flash_audio.py` — the PC side that streams the audio over USB
- `firmware/OroHapticFirmware/` — the real firmware (loaded last)

### Step 0 — Build the audio file (if it isn't built already)

```
python firmware/build_audio_blob.py
```

This produces `firmware/audio_blob.bin`.

### Step 1 — Load the temporary loader sketch

In the Arduino IDE, **select the "Seeed XIAO nRF52840 Sense Plus" board**, then upload
`OroAudioFlasher/OroAudioFlasher.ino` to the device. When it boots it prints a version
line (e.g. `OROFLASHER v7`) and the chip's JEDEC id. Seeing `JEDEC ID 0x856015`
confirms the board can talk to the flash chip.

### Step 2 — Stream the audio onto the chip

Close the Arduino Serial Monitor first (only one program can hold the USB port), then run:

```
python firmware/flash_audio.py --port COMx
```

(use the port the device shows up as — e.g. `COM16`). It erases the chip, sends the
audio in 4 KB blocks, waiting for the device to confirm each block before sending the
next, then verifies the `OROA` magic word is in place. A good run ends with:

```
WROTE 727862
MAGIC OROA
BYE
Flash complete. Re-upload OroHapticFirmware.ino to the device.
```

Note: pressing the device's reset button re-enumerates the USB port, which can change
the COM number (e.g. COM5 ↔ COM16). If a command can't open the port, check the new
number in the IDE.

### Step 3 — Load the real firmware

Upload `OroHapticFirmware/` to the device (Sense Plus still selected). The prompts you
just loaded stay on the chip — this step does not touch them.

### Step 4 — Confirm it worked

With the main firmware running, the startup log should report the prompts were found.
After a full training session, the device plays the real Session Summary voice prompt
instead of the chime.

---

## If something goes wrong

The symptoms of the two gotchas above are distinctive:

- **Wrong board ("Sense" instead of "Sense Plus"):** flash reads come back as a
  repeating `88` pattern (e.g. `MAGIC BAD 88888888`), or `JEDEC ID` reads as `0x0` /
  garbage. Re-select "Sense Plus" and re-upload.
- **`single_status_byte` set wrong:** writes hang and the firmware logs
  `[extAudio] bad magic`. This flag is already correct in the code; if you've touched
  the descriptor, set it back to `false`.

(During development the loader had a built-in `TEST` command that erased the chip and
ran a read/write round-trip to isolate exactly this. It was removed once the setup was
proven working — check the git history if you need it back.)

---

## Why the loader is a separate sketch

The loader streams a 727 KB file over USB, which needs careful pacing: it sends one
4 KB block, waits for the device to write it and reply `OK`, then sends the next. An
earlier "just send everything" approach deadlocked when the device fell behind — the PC
blocked forever waiting to send. The handshake (plus a write timeout on the PC side)
means a stuck device reports an error in seconds instead of hanging. None of this
machinery is needed in the day-to-day firmware, so it lives in its own throwaway sketch.
