#!/usr/bin/env python3
"""
Generate the Oro Haptic Paddle voice clips: the 20 Crew Roll-Call clips (ADR-0016), the 3
session prompts (ADR-0017), and the 22 Canoe Speed number words (ADR-0018).

These short clips are stitched at runtime on the device (e.g. "team sync good. Seat one ...",
or "fourteen point three"). They live on the external QSPI flash, packed by build_audio_blob.py
— NOT in the firmware image.

Voice: Microsoft **edge-tts** neural voice (en-US-AriaNeural, female, ~225 Hz). Chosen over gTTS
(no voice control) and over the lower JennyNeural (~168 Hz, which read as male on the small paddle
speaker). A higher-pitched female voice reads unmistakably female and carries better over wind
on the water (ADR-0018).

Pipeline: edge-tts -> ffmpeg -> 16 kHz mono 16-bit WAV, **wind-optimized** (high-pass at 110 Hz to
drop sub-bass rumble without clipping the female fundamental, +5 dB presence lift at 3 kHz for
consonant clarity, loudness-maximized),
written to OroHapticFirmware/voice_prompts_raw/. 16 kHz mono 16-bit is the flash's native format.

Requires: pip install edge-tts ; and an ffmpeg binary (local firmware/ffmpeg.exe, the
`imageio-ffmpeg` pip package, or ffmpeg on PATH). edge-tts needs network access to Microsoft.

    cd firmware
    python generate_roll_call_clips.py

If your network intercepts TLS (corporate proxy) and edge-tts fails cert verification, run with
ORO_TTS_INSECURE_SSL=1 to relax verification for the TTS fetch only.
"""
import asyncio
import os
import ssl
import subprocess
import sys
import wave

import certifi
import edge_tts

# edge-tts (aiohttp) uses the system CA store. Point it at certifi's bundle so it verifies on most
# machines. On a TLS-intercepting proxy that still fails, set ORO_TTS_INSECURE_SSL=1 to relax
# verification for the TTS fetch only (the clips are public audio; low risk).
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

VOICE = "en-US-AriaNeural"  # higher-pitched female neural voice (~225 Hz); clearly female + cuts wind (ADR-0018)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(SCRIPT_DIR, "OroHapticFirmware", "voice_prompts_raw")

# (filename stem, spoken text). Order/ids are documented in build_audio_blob.py and must match the
# RollCallClip enum in OroHapticFirmware.ino (ext id = 0x30 + index).
CLIPS = [
    ("rc_team_sync",       "team sync"),
    ("rc_sync",            "sync"),
    ("rc_power",           "power"),
    ("rc_pacer",           "pacer"),
    ("rc_not_measured",    "not measured"),
    ("rc_rating_poor",     "poor"),
    ("rc_rating_good",     "good"),
    ("rc_rating_excellent","excellent"),
    ("rc_power_light",     "light"),
    ("rc_power_moderate",  "moderate"),
    ("rc_power_strong",    "strong"),
    ("rc_power_maximum",   "maximum"),
    ("rc_seat_1",          "seat one"),
    ("rc_seat_2",          "seat two"),
    ("rc_seat_3",          "seat three"),
    ("rc_seat_4",          "seat four"),
    ("rc_seat_5",          "seat five"),
    ("rc_seat_6",          "seat six"),
    ("rc_canoe_1",         "canoe one"),
    ("rc_canoe_2",         "canoe two"),
    # Session start/end voice prompts (ADR-0017). Played directly by audio-event id (see
    # build_audio_blob.py), not stitched into the roll-call, but packed in the same QSPI blob.
    ("session_standby",              "stand by"),
    ("session_complete",             "session complete"),
    ("session_standby_for_results",  "stand by for results"),
    # Canoe Speed number words (ADR-0018). Composed at runtime into "<whole> point <decimal>" km/h.
    # Order is load-bearing: it must match the SpeedClip enum in OroHapticFirmware.ino and the
    # 0x50+ ids in build_audio_blob.py.
    ("spd_0",     "zero"),
    ("spd_1",     "one"),
    ("spd_2",     "two"),
    ("spd_3",     "three"),
    ("spd_4",     "four"),
    ("spd_5",     "five"),
    ("spd_6",     "six"),
    ("spd_7",     "seven"),
    ("spd_8",     "eight"),
    ("spd_9",     "nine"),
    ("spd_10",    "ten"),
    ("spd_11",    "eleven"),
    ("spd_12",    "twelve"),
    ("spd_13",    "thirteen"),
    ("spd_14",    "fourteen"),
    ("spd_15",    "fifteen"),
    ("spd_16",    "sixteen"),
    ("spd_17",    "seventeen"),
    ("spd_18",    "eighteen"),
    ("spd_19",    "nineteen"),
    ("spd_20",    "twenty"),
    ("spd_point", "point"),
]


def find_ffmpeg():
    """Local copy first, then the imageio-ffmpeg bundle, then PATH."""
    local = os.path.join(SCRIPT_DIR, "ffmpeg.exe")
    if os.path.exists(local):
        return local
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"


FFMPEG = find_ffmpeg()


def mp3_to_wav_16k(mp3_path, wav_path):
    """MP3 -> 16 kHz mono 16-bit WAV, tuned for the paddle's small speaker (ADR-0018):
    high-pass at 110 Hz (drop sub-bass rumble without clipping the female fundamental),
    then an AGGRESSIVE high-frequency lift (+6 dB presence peak at 3.5 kHz plus a +8 dB
    high-shelf above 3.5 kHz) to counter the 1 W speaker's treble roll-off, which otherwise
    darkens the voice toward a male timbre. Leading/trailing silence trimmed (smaller blob —
    must fit the 2 MB QSPI), a slight 1.1x pace, then loudness-maximized. NB: clips will sound
    bright/sharp on a PC — that is the pre-compensation; judge them on the paddle speaker."""
    af = (
        "highpass=f=110,"
        "equalizer=f=3500:width_type=q:w=1.0:g=6,"   # presence peak for consonant clarity
        "treble=g=8:f=3500,"                          # high-shelf: counter speaker treble roll-off
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05:detection=peak,"
        "areverse,"
        "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05:detection=peak,"
        "areverse,"
        "atempo=1.10,"
        "loudnorm=I=-13:TP=-1.5:LRA=11"
    )
    cmd = [
        FFMPEG, "-y", "-i", mp3_path,
        "-ar", "16000", "-ac", "1", "-sample_fmt", "s16",
        "-af", af,
        wav_path,
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {mp3_path}:\n{r.stderr[-400:]}")


async def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print("Oro voice clip generator (ADR-0016 / 0017 / 0018)")
    print(f"  Voice : edge-tts {VOICE} (female), wind-optimized")
    print(f"  Format: 16 kHz mono 16-bit WAV")
    print(f"  ffmpeg: {FFMPEG}")
    print(f"  Output: {OUT_DIR}\n")

    total_bytes = 0
    for stem, text in CLIPS:
        tmp_mp3 = os.path.join(OUT_DIR, f"{stem}_temp.mp3")
        wav_path = os.path.join(OUT_DIR, f"{stem}.wav")
        await edge_tts.Communicate(text, VOICE).save(tmp_mp3)
        try:
            mp3_to_wav_16k(tmp_mp3, wav_path)
        finally:
            if os.path.exists(tmp_mp3):
                os.remove(tmp_mp3)

        with wave.open(wav_path, "rb") as w:
            n = w.getnframes()
        size = n * 2
        total_bytes += size
        print(f"  {stem:22s} \"{text}\"  {n/16000:.2f}s  {size/1024:.1f} KB")

    print(f"\nGenerated {len(CLIPS)} clips, {total_bytes/1024:.1f} KB raw PCM.")
    print("Next: python build_audio_blob.py  (packs these into audio_blob.bin)")


if __name__ == "__main__":
    asyncio.run(main())
