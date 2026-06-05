#!/usr/bin/env python3
"""
'Goku powering up' boot-sound candidates for the Oro paddle.

Two paths:
  * RICH (GOKU_rich_charge / GOKU_short_charge): layered oscillators + harmonics + aura noise +
    pulsing ki tremolo + climax burst. This CANNOT be made live by the paddle's sine-tone engine —
    it would have to be baked in as a recorded clip on the QSPI flash (like the voice prompts).
  * SINE (GOKU_paddle_sine): rising stepped sine sweep with a pulsing amplitude throb + climax.
    This is reproducible LIVE on the device (no clip needed) — what the tone engine can actually do.

    python make_goku.py
"""
import os
import wave
import numpy as np

SR = 32000
HERE = os.path.dirname(os.path.abspath(__file__))
np.random.seed(7)


def save(name, x):
    x = np.asarray(x, dtype=float)
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.95
    pcm = (x * 32767).astype("<i2").tobytes()
    with wave.open(os.path.join(HERE, name), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm)
    print(f"  {name:26s} {len(x)/SR:.2f}s")


def goku_rich(dur):
    n = int(SR * dur); t = np.arange(n) / SR; p = t / dur
    # Rising fundamental with a surge near the climax.
    f0 = 70 + 120 * p + 70 * (p ** 4)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    body = sum(np.sin(k * phase) / k for k in range(1, 7))      # sawtooth-ish, rich harmonics
    # Pulsing "ki" tremolo that speeds up as power builds.
    trem_rate = 6 + 26 * p
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(trem_rate) / SR)
    body *= trem
    # Aura crackle: high-passed noise, rising level, same pulsing.
    noise = np.random.randn(n)
    noise = np.diff(np.concatenate([[0.0], noise]))            # crude high-pass
    aura = noise * (0.12 + 0.5 * p) * trem
    # Climax burst in the last 0.5s — a bright rising sine that decays.
    burst = np.zeros(n)
    cs = max(0, n - int(SR * 0.5)); m = n - cs
    if m > 0:
        bt = np.arange(m) / SR
        bf = np.linspace(500, 1300, m)
        burst[cs:] = np.sin(2 * np.pi * np.cumsum(bf) / SR) * np.exp(-3 * bt) * 0.7
    mix = body * 0.7 + aura * 0.5 + burst
    # Overall swell: quiet -> loud into the climax, then a quick release.
    env = p ** 1.3
    rel = int(SR * 0.12); env[-rel:] *= np.linspace(1, 0, rel)
    return mix * env


def tone(f, ms, amp):
    n = int(SR * ms / 1000); fade = max(1, int(SR * 0.004))
    i = np.arange(n)
    e = np.ones(n)
    e[:fade] = 0.5 - 0.5 * np.cos(np.pi * i[:fade] / fade)
    e[-fade:] = 0.5 - 0.5 * np.cos(np.pi * (n - i[-fade:]) / fade)
    return amp * e * np.sin(2 * np.pi * f * i / SR)


def goku_sine(dur):
    """Device-reproducible: rising stepped sine + pulsing volume throb, then a sustained climax."""
    step_ms = 30
    steps = int((dur - 0.45) * 1000 / step_ms)
    out = []
    for i in range(steps):
        p = i / steps
        f = 180 + 720 * p + 220 * (p ** 3)                    # rising pitch (carrying midrange)
        throb = 0.45 + 0.55 * (0.5 + 0.5 * np.sin(2 * np.pi * (6 + 22 * p) * (i * step_ms / 1000)))
        out.append(tone(f, step_ms, throb))
    out.append(tone(1100, 450, 1.0))                          # climax: bright sustained surge
    return np.concatenate(out)


print("Goku power-up previews (32 kHz mono):")
save("GOKU_rich_charge.wav", goku_rich(2.8))
save("GOKU_short_charge.wav", goku_rich(1.4))
save("GOKU_paddle_sine.wav", goku_sine(2.2))
print("Done.")
