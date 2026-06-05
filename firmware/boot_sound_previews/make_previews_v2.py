#!/usr/bin/env python3
"""
Second batch of power-on (boot) sound candidates for the Oro paddle — variations on the two
favourites: A (rising chime) and B (pulsing throb), plus a couple of hybrids.
Pure sine tones (what playTone() makes on the device), 5 ms cosine fades, 32 kHz mono 16-bit.

    python make_previews_v2.py
"""
import math
import os
import struct
import wave

SR = 32000
AMP = 0.55


def tone(freq, ms, amp=AMP):
    n = int(SR * ms / 1000)
    fade = max(1, int(SR * 0.005))
    out = []
    for i in range(n):
        env = 1.0
        if i < fade:
            env = 0.5 - 0.5 * math.cos(math.pi * i / fade)
        elif i > n - fade:
            env = 0.5 - 0.5 * math.cos(math.pi * (n - i) / fade)
        out.append(amp * env * math.sin(2 * math.pi * freq * i / SR))
    return out


def sil(ms):
    return [0.0] * int(SR * ms / 1000)


def save(name, samples):
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))
    print(f"  {name:34s} {len(samples)/SR:.2f}s")


# ---- A-family: rising chimes ----
# A5: 4-note bright arpeggio C5-E5-G5-C6
A5 = tone(523,100)+tone(659,100)+tone(784,100)+tone(1047,240)
# A6: faster, sparklier, higher (E5-G5-B5-E6)
A6 = tone(659,80)+tone(784,80)+tone(988,80)+tone(1319,220)
# A7: warm wider rise (G4-B4-D5-G5)
A7 = tone(392,110)+tone(494,110)+tone(587,110)+tone(784,240)
# A8: two-note "ding-dong" up then a settle (C5-G5 ... E5)
A8 = tone(523,110)+tone(784,110)+sil(40)+tone(659,260)

# ---- B-family: pulsing throbs ----
# B5: three equal pulses then a higher long note
B5 = tone(660,80)+sil(70)+tone(660,80)+sil(70)+tone(660,80)+sil(70)+tone(880,280)
# B6: heartbeat — two quick low pulses then a bright sustain
B6 = tone(523,60)+sil(55)+tone(523,60)+sil(90)+tone(784,300)
# B7: ascending pulses, each steps up (D5-F5-A5) then settle
B7 = tone(587,90)+sil(60)+tone(698,90)+sil(60)+tone(880,300)
# B8: brighter double pulse into a high note
B8 = tone(698,100)+sil(70)+tone(698,100)+sil(70)+tone(1047,300)

# ---- Hybrids: pulse intro INTO a chime ----
# H1: two pulses then a 3-note rising chime
H1 = tone(660,80)+sil(70)+tone(660,80)+sil(90)+tone(784,100)+tone(988,100)+tone(1175,240)
# H2: single soft pulse then warm rising chime
H2 = tone(523,90)+sil(80)+tone(523,100)+tone(659,100)+tone(784,240)

print("Boot-sound previews v2 (32 kHz mono):")
for name, s in [
    ("A5_chime_4note_bright.wav", A5),
    ("A6_chime_fast_sparkle.wav", A6),
    ("A7_chime_warm_wide.wav", A7),
    ("A8_chime_dingdong_settle.wav", A8),
    ("B5_throb_triple_rise.wav", B5),
    ("B6_throb_heartbeat.wav", B6),
    ("B7_throb_ascending.wav", B7),
    ("B8_throb_bright_double.wav", B8),
    ("H1_pulse_into_chime.wav", H1),
    ("H2_soft_pulse_chime.wav", H2),
]:
    save(name, s)
print("Done.")
