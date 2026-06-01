#!/usr/bin/env python3
"""
Test WAV file to see actual sample values
"""

import wave
import struct
import sys

wav_file = "voice_prompts_raw/halfway.wav"

print(f"Testing WAV file: {wav_file}\n")

with wave.open(wav_file, 'rb') as wav:
    # Get info
    channels = wav.getnchannels()
    sample_width = wav.getsampwidth()
    framerate = wav.getframerate()
    n_frames = wav.getnframes()

    print(f"Format: {framerate}Hz, {channels}ch, {sample_width*8}-bit")
    print(f"Frames: {n_frames} ({n_frames/framerate:.2f}s)\n")

    # Read all audio data
    audio_data = wav.readframes(n_frames)

    # Convert to int16 samples
    samples = struct.unpack(f'{n_frames}h', audio_data)

    # Print first 20 samples
    print("First 20 samples:")
    for i in range(20):
        print(f"  Sample[{i}]: {samples[i]}")

    print("\nSamples at offset 500:")
    for i in range(500, 520):
        print(f"  Sample[{i}]: {samples[i]}")

    print("\nSamples at offset 2000:")
    for i in range(2000, 2020):
        print(f"  Sample[{i}]: {samples[i]}")

    # Find first non-zero sample
    first_nonzero = None
    for i, sample in enumerate(samples):
        if sample != 0:
            first_nonzero = i
            break

    if first_nonzero:
        print(f"\nFirst non-zero sample at index: {first_nonzero} ({first_nonzero/framerate*1000:.1f}ms)")
        print(f"Value: {samples[first_nonzero]}")
    else:
        print("\n⚠️ WARNING: ALL SAMPLES ARE ZERO!")

    # Calculate statistics
    max_val = max(samples)
    min_val = min(samples)
    non_zero_count = sum(1 for s in samples if s != 0)

    print(f"\nStatistics:")
    print(f"  Max value: {max_val}")
    print(f"  Min value: {min_val}")
    print(f"  Non-zero samples: {non_zero_count}/{n_frames} ({non_zero_count/n_frames*100:.1f}%)")
