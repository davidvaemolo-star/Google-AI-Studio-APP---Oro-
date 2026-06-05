#!/usr/bin/env python3
"""
Bird-like power-on (boot) sound candidates for the Oro paddle: chirps, tweets, trills and
warbles built from frequency glides + rapid tone alternation. 32 kHz mono 16-bit.

On the device these become stepped playTone() sweeps (a bit grainier than these smooth
previews) — same shape, slightly coarser. Birdsong sits ~2-4 kHz, right where the 1 W
paddle speaker is loudest, so it should carry well on the water.

    python make_previews_birds.py
"""
import math
import os
import struct
import wave

SR = 32000
AMP = 0.5


def _env(i, n, fade):
    if i < fade:
        return 0.5 - 0.5 * math.cos(math.pi * i / fade)
    if i > n - fade:
        return 0.5 - 0.5 * math.cos(math.pi * (n - i) / fade)
    return 1.0


def glide(f0, f1, ms, amp=AMP, curve=1.0):
    """Smooth pitch glide f0->f1 (curve>1 = ease, accelerates near the end)."""
    n = int(SR * ms / 1000)
    fade = max(1, int(SR * 0.004))
    out, phase = [], 0.0
    for i in range(n):
        t = (i / n) ** curve
        f = f0 + (f1 - f0) * t
        phase += 2 * math.pi * f / SR
        out.append(amp * _env(i, n, fade) * math.sin(phase))
    return out


def warble(center, depth, rate_hz, ms, amp=AMP):
    """A whistle whose pitch wobbles up/down (vibrato), like a warbling bird."""
    n = int(SR * ms / 1000)
    fade = max(1, int(SR * 0.004))
    out, phase = [], 0.0
    for i in range(n):
        f = center + depth * math.sin(2 * math.pi * rate_hz * i / SR)
        phase += 2 * math.pi * f / SR
        out.append(amp * _env(i, n, fade) * math.sin(phase))
    return out


def trill(fa, fb, step_ms, reps, amp=AMP):
    """Rapid alternation between two pitches (a trill)."""
    out = []
    for k in range(reps):
        out += glide(fa, fa, step_ms, amp) if k % 2 == 0 else glide(fb, fb, step_ms, amp)
    return out


def sil(ms):
    return [0.0] * int(SR * ms / 1000)


def save(name, s):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in s))
    print(f"  {name:32s} {len(s)/SR:.2f}s")


# 1: tweet-tweet — two quick rising chirps
BIRD1 = glide(2200, 3600, 70) + sil(60) + glide(2200, 3600, 70)
# 2: classic sweet tweet — rise then fall
BIRD2 = glide(2400, 3500, 60, curve=0.7) + glide(3500, 2300, 90, curve=1.4)
# 3: trill — fast alternation, like a little chatter
BIRD3 = trill(2800, 3300, 28, 8) + glide(3300, 2600, 80)
# 4: warble whistle, rising
BIRD4 = warble(2600, 250, 22, 360)
# 5: chirp into a short trill (chirp-trrr)
BIRD5 = glide(2000, 3200, 80) + sil(40) + trill(3000, 3500, 26, 6)
# 6: little morning phrase — three varied chirps
BIRD6 = (glide(2300, 3400, 70) + sil(50) +
         glide(2600, 3000, 55) + sil(45) +
         glide(2200, 3600, 110, curve=1.3))
# 7: gentle two-note whistle (softer, less busy)
BIRD7 = glide(2500, 2500, 90) + sil(35) + glide(3100, 3100, 160)

print("Bird-like boot previews (32 kHz mono):")
for name, s in [
    ("BIRD1_tweet_tweet.wav", BIRD1),
    ("BIRD2_sweet_tweet.wav", BIRD2),
    ("BIRD3_trill_chatter.wav", BIRD3),
    ("BIRD4_warble_whistle.wav", BIRD4),
    ("BIRD5_chirp_trill.wav", BIRD5),
    ("BIRD6_morning_phrase.wav", BIRD6),
    ("BIRD7_two_note_whistle.wav", BIRD7),
]:
    save(name, s)
print("Done.")
