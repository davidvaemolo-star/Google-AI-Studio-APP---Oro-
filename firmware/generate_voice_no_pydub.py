#!/usr/bin/env python3
"""
Simple Voice Prompt Generator for Oro Haptic Paddle
No pydub required - works with Python 3.13+
Requires: pip install gtts

This script generates voice prompts and converts them using only standard libraries.
"""

import os
import sys
import wave
import struct
import tempfile
from gtts import gTTS

# Audio prompts to generate
PROMPTS = {
    "training_start": "Training start",
    "halfway": "Halfway",
    "set_complete": "Set complete",
    "last_set": "Last set",
    "zone_transition": "Zone transition",
    "session_complete": "Session complete",
    "pause": "Pause",
    "resume": "Resume"
}

def convert_mp3_to_wav_32khz(mp3_path, wav_path):
    """
    Convert MP3 to 32kHz WAV using ffmpeg (must be installed)
    """
    import subprocess

    # Use ffmpeg to convert MP3 to 32kHz mono 16-bit WAV
    # -y: overwrite output file
    # -i: input file
    # -ar 32000: sample rate 32kHz
    # -ac 1: mono (1 channel)
    # -sample_fmt s16: 16-bit signed integer
    # -af "atempo=1.3": speed up by 1.3x for fast speech

    cmd = [
        'ffmpeg',
        '-y',  # Overwrite output
        '-i', mp3_path,  # Input MP3
        '-ar', '32000',  # 32kHz sample rate
        '-ac', '1',      # Mono
        '-sample_fmt', 's16',  # 16-bit
        '-af', 'atempo=1.3,volume=2.0',  # Speed up 1.3x and normalize
        wav_path
    ]

    try:
        # Run ffmpeg, suppress output
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return True
    except subprocess.CalledProcessError as e:
        print(f"    ⚠ ffmpeg error: {e.stderr}")
        return False
    except FileNotFoundError:
        print("    ❌ ffmpeg not found! Please install ffmpeg:")
        print("       Windows: https://ffmpeg.org/download.html")
        print("       Mac: brew install ffmpeg")
        print("       Linux: sudo apt-get install ffmpeg")
        return False

def get_wav_info(wav_path):
    """Get WAV file information"""
    with wave.open(wav_path, 'rb') as wav:
        n_channels = wav.getnchannels()
        sample_width = wav.getsampwidth()
        framerate = wav.getframerate()
        n_frames = wav.getnframes()
        duration_ms = int((n_frames / framerate) * 1000)
        size_bytes = n_frames * sample_width

        return {
            'channels': n_channels,
            'sample_width': sample_width,
            'framerate': framerate,
            'n_frames': n_frames,
            'duration_ms': duration_ms,
            'size_bytes': size_bytes
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

    total_size = 0
    success_count = 0

    for filename, text in PROMPTS.items():
        print(f"Generating: '{text}'")

        try:
            # Generate TTS audio with gTTS
            # tld='us' gives US accent, lang='en' for English
            tts = gTTS(text=text, lang='en', tld='us', slow=False)

            # Save to temporary MP3 file
            temp_mp3 = os.path.join(output_dir, f"{filename}_temp.mp3")
            tts.save(temp_mp3)

            # Convert to 32kHz WAV with ffmpeg
            output_path = os.path.join(output_dir, f"{filename}.wav")

            if not convert_mp3_to_wav_32khz(temp_mp3, output_path):
                print(f"  ❌ Failed to convert {filename}\n")
                os.remove(temp_mp3)
                continue

            # Remove temporary MP3
            os.remove(temp_mp3)

            # Get WAV info
            info = get_wav_info(output_path)

            print(f"  ✓ Duration: {info['duration_ms']}ms")
            print(f"  ✓ Size: {info['size_bytes']:,} bytes ({info['size_bytes']/1024:.1f} KB)")
            print(f"  ✓ Format: {info['framerate']}Hz, {info['channels']}ch, {info['sample_width']*8}-bit")
            print(f"  ✓ Saved: {output_path}\n")

            total_size += info['size_bytes']
            success_count += 1

        except Exception as e:
            print(f"  ❌ Error generating {filename}: {e}\n")
            continue

    print("="*60)
    if success_count == len(PROMPTS):
        print(f"✓ All {success_count} prompts generated successfully!")
    else:
        print(f"⚠ Generated {success_count}/{len(PROMPTS)} prompts")

    print(f"✓ Total size: {total_size:,} bytes ({total_size/1024:.1f} KB)")
    print(f"✓ Output directory: {output_dir}/")
    print("="*60)
    print("\nNext step: Run convert_to_c_arrays.py to generate firmware header")

if __name__ == "__main__":
    try:
        generate_prompts()
    except ImportError as e:
        print("\n❌ Error: Missing required libraries!")
        print("\nPlease install dependencies:")
        print("  pip install gtts")
        print("\nAlso ensure ffmpeg is installed:")
        print("  Windows: Download from https://ffmpeg.org/download.html")
        print("  Linux: sudo apt-get install ffmpeg")
        print("  Mac: brew install ffmpeg")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
