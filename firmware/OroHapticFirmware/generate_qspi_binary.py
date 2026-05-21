#!/usr/bin/env python3
"""
QSPI Flash Audio Image Builder – Oro Haptic Paddle
====================================================
Reads the 17 voice-prompt WAV files produced by generate_voice_simple.py and
packs them into a flat binary image (audio_qspi.bin) ready to flash onto the
Seeed XIAO nRF52840's onboard 2 MB P25Q16H QSPI flash chip.

Flash layout written by this script
------------------------------------
  0x000000  Header (16 bytes)
              uint32_t  magic      = 0x4F524F41  ("AORO", little-endian)
              uint16_t  version    = 0x0001
              uint8_t   count      = 17  (QSPI_PROMPT_COUNT)
              uint8_t   reserved[9]

  0x000010  Directory table (17 × 8 bytes)
              For each prompt, in the order listed in PROMPT_ORDER below:
              uint32_t  byte_offset   – absolute offset from start of image
              uint32_t  sample_count  – number of int16_t samples

  0x000100  Audio data (256-byte aligned start)
              Raw int16_t PCM, 32 kHz, mono, little-endian
              Prompts are stored contiguously in PROMPT_ORDER order.

Usage
------
  # 1. Generate WAV files (if you haven't already)
  python3 generate_voice_simple.py

  # 2. Build the QSPI binary
  python3 generate_qspi_binary.py           # reads voice_prompts_raw/*.wav

  # 3a. Flash with nrfjprog (Nordic command-line tools)
  nrfjprog --qspieraseall
  nrfjprog --qspiwrite audio_qspi.bin --qspiaddr 0

  # 3b. Flash with pyocd
  pyocd cmd -t nrf52840 -c "qspi erase 0 2097152"
  pyocd load --format bin audio_qspi.bin --base-address 0x12000000

Requirements
-------------
  pip install wave
  (numpy is optional – only used for RMS stats)
"""

import os
import struct
import sys
import wave

# ── Constants (must match audio_qspi.h) ────────────────────────────────────────

MAGIC            = 0x4F524F41   # "AORO" little-endian
VERSION          = 0x0001
HEADER_SIZE      = 16           # bytes
DIR_ENTRY_SIZE   = 8            # bytes per entry (offset u32 + count u32)
DATA_OFFSET      = 0x100        # audio data starts at byte 256

EXPECTED_RATE    = 32000        # Hz
EXPECTED_CHANNELS = 1
EXPECTED_WIDTH   = 2            # bytes per sample (int16)

WAV_DIR          = "voice_prompts_raw"
OUTPUT_FILE      = "audio_qspi.bin"

# Prompt order must match kEventToIndex[] in audio_qspi.cpp
PROMPT_ORDER = [
    "power_on",                   # index 0  (AudioEvent 0x01)
    "last_set",                   # index 1  (AudioEvent 0x04)
    "next_set_low",               # index 2  (AudioEvent 0x05)
    "next_set_medium",            # index 3  (AudioEvent 0x06)
    "next_set_high",              # index 4  (AudioEvent 0x07)
    "summary_poor_light",         # index 5  (AudioEvent 0x08)
    "summary_poor_moderate",      # index 6  (AudioEvent 0x09)
    "summary_poor_strong",        # index 7  (AudioEvent 0x0A)
    "summary_poor_maximum",       # index 8  (AudioEvent 0x0B)
    "summary_good_light",         # index 9  (AudioEvent 0x0C)
    "summary_good_moderate",      # index 10 (AudioEvent 0x0D)
    "summary_good_strong",        # index 11 (AudioEvent 0x0E)
    "summary_good_maximum",       # index 12 (AudioEvent 0x0F)
    "summary_excellent_light",    # index 13 (AudioEvent 0x10)
    "summary_excellent_moderate", # index 14 (AudioEvent 0x11)
    "summary_excellent_strong",   # index 15 (AudioEvent 0x12)
    "summary_excellent_maximum",  # index 16 (AudioEvent 0x13)
]

QSPI_SIZE = 2 * 1024 * 1024  # 2 MB


def read_wav_samples(path):
    """Return raw int16 sample bytes from a 32 kHz mono 16-bit WAV file."""
    with wave.open(path, "rb") as wf:
        ch  = wf.getnchannels()
        sw  = wf.getsampwidth()
        fr  = wf.getframerate()
        nf  = wf.getnframes()

        if ch != EXPECTED_CHANNELS:
            raise ValueError(f"{path}: expected mono, got {ch} channels")
        if sw != EXPECTED_WIDTH:
            raise ValueError(f"{path}: expected 16-bit, got {sw*8}-bit")
        if fr != EXPECTED_RATE:
            raise ValueError(f"{path}: expected {EXPECTED_RATE} Hz, got {fr} Hz")

        raw = wf.readframes(nf)          # nf × 2 bytes, little-endian int16
    return raw, nf                       # (bytes, sample_count)


def build_image(prompts):
    """
    Build the complete QSPI binary image and return it as bytes.

    prompts: list of (name, raw_bytes, sample_count) in PROMPT_ORDER order.
    """
    count = len(prompts)

    # Pre-compute byte offsets
    offsets = []
    cursor = DATA_OFFSET
    for _, raw, _ in prompts:
        offsets.append(cursor)
        cursor += len(raw)

    total_audio = cursor - DATA_OFFSET
    print(f"\nAudio data: {total_audio:,} bytes ({total_audio / 1024:.1f} KB"
          f" = {total_audio / 1024 / 1024:.2f} MB)")

    if cursor > QSPI_SIZE:
        raise OverflowError(
            f"Audio data ({cursor:,} bytes) exceeds 2 MB QSPI flash capacity!"
        )

    # ── Header (16 bytes) ────────────────────────────────────────────────────
    header = struct.pack(
        "<IHBB9s",
        MAGIC,           # uint32
        VERSION,         # uint16
        count,           # uint8  – prompt count
        0,               # uint8  – reserved
        b"\x00" * 9,     # 9 reserved bytes
    )
    assert len(header) == HEADER_SIZE

    # ── Directory table (count × 8 bytes) ───────────────────────────────────
    directory = b""
    for i, (_, _, sc) in enumerate(prompts):
        directory += struct.pack("<II", offsets[i], sc)

    # Pad directory to reach DATA_OFFSET
    dir_section = header + directory
    assert len(dir_section) <= DATA_OFFSET
    dir_section = dir_section.ljust(DATA_OFFSET, b"\x00")

    # ── Audio payload ────────────────────────────────────────────────────────
    audio_data = b"".join(raw for _, raw, _ in prompts)

    return dir_section + audio_data


def main():
    print("=" * 60)
    print("QSPI Audio Image Builder – Oro Haptic Paddle")
    print("=" * 60)

    if not os.path.isdir(WAV_DIR):
        print(f"\nERROR: WAV directory '{WAV_DIR}' not found.")
        print("Run generate_voice_simple.py first.")
        sys.exit(1)

    prompts = []
    total_bytes = 0

    for name in PROMPT_ORDER:
        wav_path = os.path.join(WAV_DIR, f"{name}.wav")
        if not os.path.exists(wav_path):
            print(f"ERROR: missing {wav_path}")
            sys.exit(1)

        raw, sample_count = read_wav_samples(wav_path)
        size_kb = len(raw) / 1024
        duration_ms = int(sample_count / EXPECTED_RATE * 1000)
        print(f"  {name:<35} {duration_ms:>5} ms  {size_kb:>6.1f} KB")

        prompts.append((name, raw, sample_count))
        total_bytes += len(raw)

    print(f"\n  {'TOTAL':<35} {total_bytes:>10,} bytes")

    image = build_image(prompts)

    with open(OUTPUT_FILE, "wb") as f:
        f.write(image)

    print(f"\nWrote {len(image):,} bytes → {OUTPUT_FILE}")
    print(f"Remaining QSPI space: {QSPI_SIZE - len(image):,} bytes"
          f" ({(QSPI_SIZE - len(image)) / 1024:.1f} KB)")

    print("\n" + "=" * 60)
    print("Next: flash audio_qspi.bin to the XIAO nRF52840 QSPI flash")
    print()
    print("  nrfjprog:")
    print("    nrfjprog --qspieraseall")
    print("    nrfjprog --qspiwrite audio_qspi.bin --qspiaddr 0")
    print()
    print("  pyocd:")
    print("    pyocd cmd -t nrf52840 -c 'qspi erase 0 2097152'")
    print("    pyocd load --format bin audio_qspi.bin --base-address 0x12000000")
    print("=" * 60)


if __name__ == "__main__":
    main()
