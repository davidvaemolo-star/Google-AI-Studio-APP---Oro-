#!/usr/bin/env python3
"""
Generate PC-listenable previews of the 4 candidate power-on (boot) sounds for the Oro paddle.
These are pure sine tones — exactly what the firmware's playTone() synthesizes on the device.
A short cosine fade per note avoids clicks. Output: 32 kHz mono 16-bit WAV (the paddle's rate).

    python make_previews.py
"""
import math
import os
import struct
import wave

SR = 32000
AMP = 0.55  # 0..1


def tone(freq, ms):
    n = int(SR * ms / 1000)
    fade = max(1, int(SR * 0.005))  # 5 ms fade in/out
    out = []
    for i in range(n):
        env = 1.0
        if i < fade:
            env = 0.5 - 0.5 * math.cos(math.pi * i / fade)
        elif i > n - fade:
            env = 0.5 - 0.5 * math.cos(math.pi * (n - i) / fade)
        out.append(AMP * env * math.sin(2 * math.pi * freq * i / SR))
    return out


def silence(ms):
    return [0.0] * int(SR * ms / 1000)


def sweep(f0, f1, ms):
    """Continuous-ish rising glide built from a smooth phase integral."""
    n = int(SR * ms / 1000)
    fade = max(1, int(SR * 0.005))
    out, phase = [], 0.0
    for i in range(n):
        f = f0 + (f1 - f0) * (i / n)
        phase += 2 * math.pi * f / SR
        env = 1.0
        if i < fade:
            env = 0.5 - 0.5 * math.cos(math.pi * i / fade)
        elif i > n - fade:
            env = 0.5 - 0.5 * math.cos(math.pi * (n - i) / fade)
        out.append(AMP * env * math.sin(phase))
    return out


def save(name, samples):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))
    print(f"  {name:28s} {len(samples)/SR:.2f}s")


# A — Rising 3-note chime (C5, E5, G5)
A = tone(523, 120) + tone(659, 120) + tone(784, 200)

# B — Pulsing throb, rising last pulse (660, 660, 880)
B = tone(660, 90) + silence(70) + tone(660, 90) + silence(70) + tone(880, 260)

# C — Two-tone power up (G4 -> C6)
C = tone(392, 140) + tone(1047, 220)

# D — Pulsing sweep 500 -> 1200 Hz
D = sweep(500, 1200, 420)

print("Boot-sound previews (32 kHz mono):")
save("A_rising_3note_chime.wav", A)
save("B_pulsing_throb.wav", B)
save("C_two_tone_powerup.wav", C)
save("D_pulsing_sweep.wav", D)
print("Done.")
