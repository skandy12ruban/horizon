"""Synthesises a soft ambient bed (pads + plucked arpeggio) as music_raw.wav. Pure Python, no deps.
D major: Dmaj7 - Bm7 - Gmaj7 - A6, 4 s per chord, ~100 bpm feel."""
import math, struct, wave, random, sys

SR = 44100
DUR = float(sys.argv[1]) if len(sys.argv) > 1 else 34.0
N = int(SR * DUR)
A4 = 440.0
def f(n): return A4 * 2 ** ((n - 69) / 12)  # midi -> Hz

CHORDS = [  # midi notes
    [50, 54, 57, 61],  # Dmaj7: D F# A C#
    [47, 50, 54, 57],  # Bm7:   B D F# A
    [43, 47, 50, 54],  # Gmaj7: G B D F#
    [45, 49, 52, 54],  # A6:    A C# E F#
]
CH = 4.0
L = [0.0] * N
R = [0.0] * N

# pads: detuned sines, slow attack/release, one octave up, slight stereo spread
for ci in range(int(DUR / CH) + 1):
    t0 = ci * CH
    notes = CHORDS[ci % 4]
    for k, n in enumerate(notes):
        for det, pan in ((-0.12, 0.3), (0.12, 0.7)):
            fr = f(n + 12) * (2 ** (det / 12))
            s0, s1 = int(t0 * SR), min(N, int((t0 + CH + 1.5) * SR))
            ph = random.random() * 6.28
            for i in range(s0, s1):
                t = (i - s0) / SR
                env = min(1, t / 1.2) * (1 if t < CH else max(0, 1 - (t - CH) / 1.5))
                v = 0.035 * env * (math.sin(2 * math.pi * fr * t + ph) + 0.25 * math.sin(4 * math.pi * fr * t + ph))
                L[i] += v * (1 - pan); R[i] += v * pan

# bass: root note, soft
for ci in range(int(DUR / CH) + 1):
    t0 = ci * CH
    fr = f(CHORDS[ci % 4][0] - 12)
    s0, s1 = int(t0 * SR), min(N, int((t0 + CH) * SR))
    for i in range(s0, s1):
        t = (i - s0) / SR
        env = min(1, t / 0.3) * max(0, 1 - t / (CH + 0.2))
        v = 0.06 * env * math.sin(2 * math.pi * fr * t)
        L[i] += v; R[i] += v

# plucked arpeggio: eighth notes at 100 bpm (0.3 s), chord tones two octaves up, enters after 3.5 s
step = 0.3
pattern = [0, 1, 2, 3, 2, 1, 3, 2]
t = 3.6
k = 0
while t < DUR - 1.5:
    ci = int(t / CH)
    n = CHORDS[ci % 4][pattern[k % 8]] + 24
    fr = f(n)
    s0, s1 = int(t * SR), min(N, int((t + 1.4) * SR))
    pan = 0.35 + 0.3 * ((k % 4) / 3)
    vel = 0.05 if k % 2 == 0 else 0.035
    for i in range(s0, s1):
        tt = (i - s0) / SR
        env = (1 - math.exp(-tt * 300)) * math.exp(-tt * 4.5)
        v = vel * env * (math.sin(2 * math.pi * fr * tt) + 0.3 * math.sin(4 * math.pi * fr * tt) * math.exp(-tt * 8))
        L[i] += v * (1 - pan); R[i] += v * pan
    t += step
    k += 1

# master fade in/out and normalise
peak = max(max(abs(x) for x in L), max(abs(x) for x in R)) or 1
with wave.open("music_raw.wav", "w") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    frames = bytearray()
    for i in range(N):
        tt = i / SR
        g = min(1, tt / 1.5) * min(1, (DUR - tt) / 2.5) * 0.85 / peak
        frames += struct.pack("<hh", int(32767 * L[i] * g), int(32767 * R[i] * g))
    w.writeframes(bytes(frames))
print("music_raw.wav", DUR, "s")
