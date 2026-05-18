#!/usr/bin/env python3
"""
Simple Voice Prompt Generator for Oro Haptic Paddle
Uses gTTS (free, no API key required) for text-to-speech
Requires: pip install gtts

This script generates voice prompts and automatically converts them to the correct format.
"""

import os
import sys
import subprocess
import wave
from gtts import gTTS

# Locate ffmpeg binary (local copy takes priority over system PATH)
_script_dir = os.path.dirname(os.path.abspath(__file__))
_ffmpeg_local = os.path.join(_script_dir, "ffmpeg.exe")
FFMPEG = _ffmpeg_local if os.path.exists(_ffmpeg_local) else "ffmpeg"


def _mp3_to_wav(mp3_path, wav_path):
    """Convert MP3 to 32kHz mono 16-bit WAV via ffmpeg, speed up 1.3x, normalize."""
    cmd = [
        FFMPEG, "-y",
        "-i", mp3_path,
        "-ar", "32000",
        "-ac", "1",
        "-sample_fmt", "s16",
        "-af", "atempo=1.3,loudnorm=I=-16:TP=-3:LRA=11",
        wav_path,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"ffmpeg failed: {result.stderr[-400:]}")

# Audio prompts to generate
PROMPTS = {
    # Power-on
    "power_on": "Oro",

    # Zone cues
    "last_set": "Last set",
    "next_set_low": "Next set low",
    "next_set_medium": "Next set medium",
    "next_set_high": "Next set high",

    # Session summary: Sync Rating × Power Range (12 prompts)
    "summary_poor_light":         "Poor sync, light power",
    "summary_poor_moderate":      "Poor sync, moderate power",
    "summary_poor_strong":        "Poor sync, strong power",
    "summary_poor_maximum":       "Poor sync, maximum power",
    "summary_good_light":         "Good sync, light power",
    "summary_good_moderate":      "Good sync, moderate power",
    "summary_good_strong":        "Good sync, strong power",
    "summary_good_maximum":       "Good sync, maximum power",
    "summary_excellent_light":    "Excellent sync, light power",
    "summary_excellent_moderate": "Excellent sync, moderate power",
    "summary_excellent_strong":   "Excellent sync, strong power",
    "summary_excellent_maximum":  "Excellent sync, maximum power",
}

def generate_prompts():
    """Generate all voice prompts using gTTS"""

    output_dir = "voice_prompts_raw"
    os.makedirs(output_dir, exist_ok=True)

    print("="*60)
    print("Voice Prompt Generator - Oro Haptic Paddle")
    print("Using gTTS (free Google Text-to-Speech)")
    print("="*60)
    print("\nSettings:")
    print("  Voice: Female (US accent)")
    print("  Speed: Fast (1.3x)")
    print("  Output: 32kHz, 16-bit, Mono\n")

    for filename, text in PROMPTS.items():
        print(f"Generating: '{text}'")

        # Generate TTS audio with gTTS
        # tld='us' gives US accent, lang='en' for English
        # gTTS defaults to female voice
        tts = gTTS(text=text, lang='en', tld='us', slow=False)

        # Save to temporary MP3 file (gTTS outputs MP3)
        temp_mp3 = os.path.join(output_dir, f"{filename}_temp.mp3")
        tts.save(temp_mp3)

        # Convert MP3 -> 32kHz mono 16-bit WAV, speed 1.3x, normalised
        output_path = os.path.join(output_dir, f"{filename}.wav")
        _mp3_to_wav(temp_mp3, output_path)

        # Remove temporary MP3
        os.remove(temp_mp3)

        # Show info from the generated WAV
        with wave.open(output_path, "rb") as wf:
            n_frames = wf.getnframes()
            framerate = wf.getframerate()
        duration_ms = int(n_frames / framerate * 1000)
        size_bytes = n_frames * 2  # 16-bit = 2 bytes per sample

        print(f"  Duration: {duration_ms}ms")
        print(f"  Size: {size_bytes:,} bytes ({size_bytes/1024:.1f} KB)")
        print(f"  Saved: {output_path}\n")

    print("="*60)
    print(f"All {len(PROMPTS)} prompts generated successfully!")
    print(f"Output directory: {output_dir}/")
    print("="*60)
    print("\nNext step: Run convert_to_c_arrays.py to generate firmware header")

if __name__ == "__main__":
    try:
        generate_prompts()
    except ImportError as e:
        print("\nERROR: Missing required libraries!")
        print("\nPlease install dependencies:")
        print("  pip install gtts")
        print("\nNote: You may also need ffmpeg or libav for audio conversion:")
        print("  Windows: Download from https://ffmpeg.org/download.html")
        print("  Linux: sudo apt-get install ffmpeg")
        print("  Mac: brew install ffmpeg")
        sys.exit(1)
    except Exception as e:
        print(f"\nERROR: {e}")
        sys.exit(1)
