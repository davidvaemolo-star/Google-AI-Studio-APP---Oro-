#!/usr/bin/env python3
"""
Generate the 20 Crew Roll-Call voice clips (ADR-0016) for the Oro Haptic Paddle.

These short clips are stitched at runtime into the spoken end-of-session roll-call
("team sync good. Seat one, pacer, power strong. ..."). They live on the device's
external QSPI flash, packed by build_audio_blob.py — NOT in the firmware image.

Pipeline: gTTS (free Google TTS, female US voice) -> ffmpeg -> 16 kHz mono 16-bit WAV,
sped up 1.3x and loudness-normalised, written to OroHapticFirmware/voice_prompts_raw/.

16 kHz mono 16-bit is the external-flash native format, so build_audio_blob.py can pack
the WAVs directly without re-encoding.

Requires: pip install gtts ; and an ffmpeg binary (local firmware/ffmpeg.exe, or the
`imageio-ffmpeg` pip package, or ffmpeg on PATH).

    cd firmware
    python generate_roll_call_clips.py
"""
import os
import subprocess
import sys
import wave
from gtts import gTTS

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
    """MP3 -> 16 kHz mono 16-bit WAV, sped up 1.3x and loudness-normalised."""
    cmd = [
        FFMPEG, "-y", "-i", mp3_path,
        "-ar", "16000", "-ac", "1", "-sample_fmt", "s16",
        "-af", "atempo=1.3,loudnorm=I=-16:TP=-3:LRA=11",
        wav_path,
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"ffmpeg failed for {mp3_path}:\n{r.stderr[-400:]}")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    print("Crew Roll-Call clip generator (ADR-0016)")
    print(f"  Voice : gTTS female, US accent, 1.3x")
    print(f"  Format: 16 kHz mono 16-bit WAV")
    print(f"  ffmpeg: {FFMPEG}")
    print(f"  Output: {OUT_DIR}\n")

    total_bytes = 0
    for stem, text in CLIPS:
        tts = gTTS(text=text, lang="en", tld="us", slow=False)  # gTTS defaults to a female voice
        tmp_mp3 = os.path.join(OUT_DIR, f"{stem}_temp.mp3")
        wav_path = os.path.join(OUT_DIR, f"{stem}.wav")
        tts.save(tmp_mp3)
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
    main()
