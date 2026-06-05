#!/usr/bin/env python3
"""
WAV to C Array Converter for Oro Haptic Paddle
Converts 32kHz 16-bit mono WAV files to C arrays for embedding in firmware
Requires: pip install wave numpy
"""

import wave
import struct
import os
import numpy as np

# Input/output directories
INPUT_DIR = "voice_prompts_raw"
OUTPUT_FILE = "audio_prompts.h"

# Prompt file names (must match generate_embedded_prompts.py).
# ONLY the 5 firmware-embedded prompts live here. The 12 Session Summary clips were moved to the
# external QSPI flash (ADR-0016, Crew Roll-Call) — embedding them too would overflow the nRF52840's
# 1 MB internal flash. Their table slots below are nullptr (played via externalAudio.playClip).
PROMPT_FILES = [
    # power_on dropped: the boot greeting is now the synthesized playPowerOnChime() (no clip).
    ("last_set",                  "AUDIO_LAST_SET"),
    ("next_set_low",              "AUDIO_NEXT_SET_LOW"),
    ("next_set_medium",           "AUDIO_NEXT_SET_MEDIUM"),
    ("next_set_high",             "AUDIO_NEXT_SET_HIGH"),
]

def wav_to_c_array(wav_file, array_name):
    """Convert a WAV file to a C array of int16_t samples"""

    with wave.open(wav_file, 'rb') as wav:
        # Verify format
        channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        framerate = wav.getframerate()
        n_frames = wav.getnframes()

        print(f"\n  Reading: {os.path.basename(wav_file)}")
        print(f"    Format: {framerate}Hz, {channels}ch, {sample_width*8}-bit")
        print(f"    Frames: {n_frames} ({n_frames/framerate:.2f}s)")

        if channels != 1:
            print(f"    WARNING: Expected mono (1 channel), got {channels}")
        if sample_width != 2:
            print(f"    WARNING: Expected 16-bit (2 bytes), got {sample_width}")
        if framerate != 32000:
            print(f"    WARNING: Expected 32kHz, got {framerate}Hz")

        # Read all audio data
        audio_data = wav.readframes(n_frames)

        # Convert to int16 samples
        samples = struct.unpack(f'{n_frames}h', audio_data)

        # Calculate statistics
        samples_array = np.array(samples)
        max_amplitude = np.max(np.abs(samples_array))
        rms = np.sqrt(np.mean(samples_array**2))

        print(f"    Peak amplitude: {max_amplitude} ({max_amplitude/32768*100:.1f}%)")
        print(f"    RMS level: {rms:.0f} ({rms/32768*100:.1f}%)")

        # Generate C array (no PROGMEM - nRF52 doesn't need it)
        array_declaration = f"const int16_t {array_name}[] = {{\n"

        # Format samples in rows of 12 for readability
        for i in range(0, len(samples), 12):
            row = samples[i:i+12]
            array_declaration += "  " + ", ".join(f"{s:6d}" for s in row) + ",\n"

        # Remove trailing comma and close array
        array_declaration = array_declaration.rstrip(",\n") + "\n};\n"

        # Add size constant
        size_constant = f"const uint32_t {array_name}_SIZE = {len(samples)};\n"

        return array_declaration + size_constant, len(samples)

def generate_header_file():
    """Generate complete audio_prompts.h header file"""

    print("="*60)
    print("WAV to C Array Converter - Oro Haptic Paddle")
    print("="*60)

    if not os.path.exists(INPUT_DIR):
        print(f"\nERROR: Input directory '{INPUT_DIR}' not found!")
        print("Please run generate_voice_prompts.py first.")
        return

    # Start building header file
    header = """/*
 * Audio Prompts for Oro Haptic Paddle
 * Generated automatically - DO NOT EDIT BY HAND
 *
 * Format: 16-bit PCM, 32kHz, Mono
 * Storage: PROGMEM (flash memory)
 *
 * Voice: Female US Neural (Google Cloud TTS)
 * Speech Rate: Fast (1.2x)
 *
 * Generated with: convert_to_c_arrays.py
 */

#ifndef AUDIO_PROMPTS_H
#define AUDIO_PROMPTS_H

#include <Arduino.h>

// Audio prompt enumeration defined in OroHapticFirmware.ino (enum AudioEvent)
// No #defines needed here - using the existing enum values

"""

    total_size = 0

    # Process each prompt file
    for filename, define_name in PROMPT_FILES:
        wav_path = os.path.join(INPUT_DIR, f"{filename}.wav")

        if not os.path.exists(wav_path):
            print(f"\nWARNING: File not found: {wav_path}")
            continue

        array_name = f"audio_prompt_{filename}"
        c_array, sample_count = wav_to_c_array(wav_path, array_name)

        # Add comment separator
        header += f"\n// {define_name} - \"{filename.replace('_', ' ').title()}\"\n"
        header += c_array + "\n"

        size_bytes = sample_count * 2  # 2 bytes per sample
        total_size += size_bytes
        print(f"    Memory: {size_bytes:,} bytes ({size_bytes/1024:.1f} KB)")

    header += """
// Prompt lookup table
struct AudioPromptInfo {
  const int16_t* data;
  uint32_t size;
};

const AudioPromptInfo AUDIO_PROMPT_TABLE[] = {
  {nullptr, 0},                                                       // 0x00 unused
  {nullptr, 0},                                                       // 0x01 POWER_ON (accessed directly)
  {nullptr, 0},                                                       // 0x02 SESSION_START_BEEP (tone)
  {nullptr, 0},                                                       // 0x03 SET_CHANGEOVER_BEEP (tone)
  {audio_prompt_last_set,        audio_prompt_last_set_SIZE},         // 0x04
  {audio_prompt_next_set_low,    audio_prompt_next_set_low_SIZE},     // 0x05
  {audio_prompt_next_set_medium, audio_prompt_next_set_medium_SIZE},  // 0x06
  {audio_prompt_next_set_high,   audio_prompt_next_set_high_SIZE},    // 0x07
  {nullptr, 0},  // 0x08 SUMMARY_POOR_LIGHT (external flash)
  {nullptr, 0},  // 0x09 SUMMARY_POOR_MODERATE (external flash)
  {nullptr, 0},  // 0x0A SUMMARY_POOR_STRONG (external flash)
  {nullptr, 0},  // 0x0B SUMMARY_POOR_MAXIMUM (external flash)
  {nullptr, 0},  // 0x0C SUMMARY_GOOD_LIGHT (external flash)
  {nullptr, 0},  // 0x0D SUMMARY_GOOD_MODERATE (external flash)
  {nullptr, 0},  // 0x0E SUMMARY_GOOD_STRONG (external flash)
  {nullptr, 0},  // 0x0F SUMMARY_GOOD_MAXIMUM (external flash)
  {nullptr, 0},  // 0x10 SUMMARY_EXCELLENT_LIGHT (external flash)
  {nullptr, 0},  // 0x11 SUMMARY_EXCELLENT_MODERATE (external flash)
  {nullptr, 0},  // 0x12 SUMMARY_EXCELLENT_STRONG (external flash)
  {nullptr, 0},  // 0x13 SUMMARY_EXCELLENT_MAXIMUM (external flash)
};

#define AUDIO_PROMPT_COUNT 20

#endif // AUDIO_PROMPTS_H
"""

    # Write header file
    with open(OUTPUT_FILE, 'w') as f:
        f.write(header)

    print("\n" + "="*60)
    print(f"Generated: {OUTPUT_FILE}")
    print(f"Total size: {total_size:,} bytes ({total_size/1024:.1f} KB)")
    print(f"Prompt count: {len(PROMPT_FILES)}")
    print("="*60)
    print("\nNext steps:")
    print("  1. Copy audio_prompts.h to your Arduino sketch folder")
    print("  2. Update audio_i2s.h and audio_i2s.cpp (see instructions)")
    print("  3. Update OroHapticFirmware.ino to include audio_prompts.h")
    print("  4. Upload to your device and test!")

if __name__ == "__main__":
    generate_header_file()
