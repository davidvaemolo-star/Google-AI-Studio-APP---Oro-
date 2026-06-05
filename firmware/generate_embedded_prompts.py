#!/usr/bin/env python3
"""
Generate the 4 EMBEDDED (compiled-into-firmware) voice prompts for the Oro Haptic Paddle:
"Last set" and "Next set low/medium/high". (The boot greeting is no longer a clip — it's the
synthesized playPowerOnChime() in OroHapticFirmware.ino.)

These are NOT on the QSPI flash blob — they are baked into the firmware image via
audio_prompts.h (see convert_to_c_arrays.py). Because the embedded playback path
(AudioI2S::playBuffer) feeds samples straight to the 32 kHz I2S WITHOUT the 16->32 kHz
sample-doubling the QSPI streaming path uses, these clips must be rendered at **32 kHz**
(not 16 kHz). Render them at 16 kHz and they play an octave low / dragging — which is
exactly the "sounds like a man" symptom.

Voice + filter chain are kept IDENTICAL to generate_roll_call_clips.py (edge-tts
en-US-AriaNeural, female, with the wind/small-speaker treble lift + pitch lift) so every
prompt on the paddle — boot, zone changes, roll-call, speed — uses one consistent female
voice. The ONLY difference here is the 32 kHz output sample rate.

After running this, regenerate the header:
    cd firmware
    python generate_embedded_prompts.py
    python convert_to_c_arrays.py        # rebuilds audio_prompts.h
Then re-upload OroHapticFirmware.ino to each paddle (no QSPI flashing needed for these).

If your network intercepts TLS, run with ORO_TTS_INSECURE_SSL=1 (same as the other script).
"""
import asyncio
import os
import ssl
import subprocess
import sys
import wave

import certifi
import edge_tts

os.environ.setdefault("SSL_CERT_FILE", certifi.where())
if os.environ.get("ORO_TTS_INSECURE_SSL"):
    _orig_ctx = ssl.create_default_context
    def _unverified_ctx(*a, **k):
        c = _orig_ctx(*a, **k)
        c.check_hostname = False
        c.verify_mode = ssl.CERT_NONE
        return c
    ssl.create_default_context = _unverified_ctx
    print("WARNING: ORO_TTS_INSECURE_SSL set — TLS verification disabled for edge-tts fetch.")

VOICE = "en-US-AriaNeural"  # MUST match generate_roll_call_clips.py

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, "OroHapticFirmware", "voice_prompts_raw")

# (filename stem, spoken text). Stems/texts must match convert_to_c_arrays.py PROMPT_FILES and
# generate_voice_simple.py.
CLIPS = [
    # No "Oro" power-on clip: the boot greeting is the synthesized playPowerOnChime() in firmware.
    ("last_set",        "Last set"),
    ("next_set_low",    "Next set low"),
    ("next_set_medium", "Next set medium"),
    ("next_set_high",   "Next set high"),
]


def find_ffmpeg():
    local = os.path.join(SCRIPT_DIR, "ffmpeg.exe")
    if os.path.exists(local):
        return local
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


FFMPEG = find_ffmpeg()


def mp3_to_wav_32k(mp3_path, wav_path):
    """MP3 -> 32 kHz mono 16-bit WAV. Same wind/small-speaker filter chain as the QSPI clips
    (generate_roll_call_clips.py) so the timbre matches — only the sample rate differs (32 kHz,
    because the embedded playBuffer path does not sample-double)."""
    af = (
        "highpass=f=110,"
        "equalizer=f=3500:width_type=q:w=1.0:g=6,"   # presence peak for consonant clarity
        "treble=g=8:f=3500,"                          # high-shelf: counter speaker treble roll-off
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05:detection=peak,"
        "areverse,"
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05:detection=peak,"
        "areverse,"
        "rubberband=pitch=1.26,"                      # +~4 semitones (formant-aware) -> clearly female
        "atempo=1.10,"
        "loudnorm=I=-13:TP=-1.5:LRA=11"
    )
    cmd = [
        FFMPEG, "-y", "-i", mp3_path,
        "-ar", "32000", "-ac", "1", "-sample_fmt", "s16",
        "-af", af,
        wav_path,
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {mp3_path}:\n{r.stderr[-400:]}")


async def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print("Oro EMBEDDED voice prompt generator (firmware-baked, 32 kHz)")
    print(f"  Voice : edge-tts {VOICE} (female), wind-optimized")
    print(f"  Format: 32 kHz mono 16-bit WAV  (NOT 16 kHz — embedded playBuffer has no doubling)")
    print(f"  ffmpeg: {FFMPEG}")
    print(f"  Output: {OUT_DIR}\n")

    for stem, text in CLIPS:
        tmp_mp3 = os.path.join(OUT_DIR, f"{stem}_temp.mp3")
        wav_path = os.path.join(OUT_DIR, f"{stem}.wav")
        await edge_tts.Communicate(text, VOICE).save(tmp_mp3)
        try:
            mp3_to_wav_32k(tmp_mp3, wav_path)
        finally:
            if os.path.exists(tmp_mp3):
                os.remove(tmp_mp3)
        with wave.open(wav_path, "rb") as w:
            n, sr = w.getnframes(), w.getframerate()
        print(f"  {stem:18s} \"{text}\"  {n/sr:.2f}s  {sr} Hz  {n*2/1024:.1f} KB")

    print("\nNext: python convert_to_c_arrays.py   (rebuilds audio_prompts.h)")
    print("Then re-upload OroHapticFirmware.ino to each paddle.")


if __name__ == "__main__":
    asyncio.run(main())
