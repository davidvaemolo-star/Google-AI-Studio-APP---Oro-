#!/usr/bin/env python3
"""
Pack the 12 Session Summary WAV files into a single binary blob for the
Oro Haptic Paddle's external QSPI flash.

Output layout (see docs/superpowers/plans/...external-flash.md):
  magic 'OROA' | version u16 | count u16 | reserved u32 | 12 * Entry | payloads

Each Entry (12 bytes):
  u8 eventId, u8 sampleRateCode (0 = 16 kHz), u16 reserved,
  u32 offset_in_blob, u32 sampleCount

Run from the firmware/ directory:
    python build_audio_blob.py
"""
import os
import struct
import subprocess
import sys
import wave

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
WAV_DIR = os.path.join(SCRIPT_DIR, "OroHapticFirmware", "voice_prompts_raw")
OUT_PATH = os.path.join(SCRIPT_DIR, "audio_blob.bin")
FFMPEG = os.path.join(SCRIPT_DIR, "ffmpeg.exe")
if not os.path.exists(FFMPEG):
    FFMPEG = "ffmpeg"

# (filename_stem, audioEventId)  -- ids must match enum AudioEvent in OroHapticFirmware.ino
PROMPTS = [
    ("summary_poor_light",         0x08),
    ("summary_poor_moderate",      0x09),
    ("summary_poor_strong",        0x0A),
    ("summary_poor_maximum",       0x0B),
    ("summary_good_light",         0x0C),
    ("summary_good_moderate",      0x0D),
    ("summary_good_strong",        0x0E),
    ("summary_good_maximum",       0x0F),
    ("summary_excellent_light",    0x10),
    ("summary_excellent_moderate", 0x11),
    ("summary_excellent_strong",   0x12),
    ("summary_excellent_maximum",  0x13),
]

HEADER_SIZE = 12              # magic+version+count+reserved
ENTRY_SIZE = 12
INDEX_END = HEADER_SIZE + ENTRY_SIZE * len(PROMPTS)  # 0x9C for 12 prompts

def resample_to_16k_mono_pcm(src_wav, dst_wav):
    cmd = [FFMPEG, "-y", "-i", src_wav,
           "-ar", "16000", "-ac", "1", "-sample_fmt", "s16", dst_wav]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {src_wav}:\n{r.stderr[-400:]}")

def read_pcm_samples(wav_path):
    with wave.open(wav_path, "rb") as w:
        if w.getnchannels() != 1:
            raise RuntimeError(f"{wav_path}: expected mono, got {w.getnchannels()} channels")
        if w.getsampwidth() != 2:
            raise RuntimeError(f"{wav_path}: expected 16-bit (2 bytes), got {w.getsampwidth()} bytes")
        if w.getframerate() != 16000:
            raise RuntimeError(f"{wav_path}: expected 16 kHz, got {w.getframerate()} Hz")
        n = w.getnframes()
        return w.readframes(n), n

def main():
    if not os.path.isdir(WAV_DIR):
        print(f"ERROR: WAV directory not found: {WAV_DIR}", file=sys.stderr)
        sys.exit(1)

    tmp_dir = os.path.join(SCRIPT_DIR, "audio_blob_tmp_16k")
    os.makedirs(tmp_dir, exist_ok=True)

    entries = []
    payloads = []
    cursor = INDEX_END

    for name, event_id in PROMPTS:
        src = os.path.join(WAV_DIR, f"{name}.wav")
        dst = os.path.join(tmp_dir, f"{name}_16k.wav")
        if not os.path.exists(src):
            print(f"ERROR: missing source {src}", file=sys.stderr)
            sys.exit(1)
        resample_to_16k_mono_pcm(src, dst)
        pcm_bytes, sample_count = read_pcm_samples(dst)
        entry = struct.pack("<BBHII", event_id, 0, 0, cursor, sample_count)
        entries.append(entry)
        payloads.append(pcm_bytes)
        print(f"  0x{event_id:02X} {name:30s} offset=0x{cursor:06X} samples={sample_count} "
              f"({sample_count/16000:.2f}s, {len(pcm_bytes)} B)")
        cursor += len(pcm_bytes)

    header = struct.pack("<4sHHI", b"OROA", 1, len(PROMPTS), 0)
    blob = header + b"".join(entries) + b"".join(payloads)

    with open(OUT_PATH, "wb") as f:
        f.write(blob)
    print(f"\nWrote {OUT_PATH}  total {len(blob)} bytes ({len(blob)/1024:.1f} KB)")

if __name__ == "__main__":
    main()
