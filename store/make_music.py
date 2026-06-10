"""
Synthesize an original, royalty-free relaxing track for the app demo video.

  - warm evolving pad: Dmaj7 -> Gmaj7 -> Bm7 -> Asus (looped), soft attack/release,
    slow tremolo "breathing", gentle detune for stereo width
  - sparse bell/piano-like melody from the D-major pentatonic
  - soft sub bass for warmth
  - lush convolution reverb (warm, decorrelated stereo impulse)
  - 1.5 s fade-in, 3 s fade-out, peak-normalized

100% generated from scratch -> no copyright, no attribution required.

Out: store/screenshots/relaxing_pad.wav  (44.1 kHz, 16-bit stereo)
"""
import numpy as np, os, wave, struct

np.random.seed(7)
SR = 44100
DUR = 29.3
N = int(SR * DUR)
TAIL = int(2.5 * SR)            # extra room for note releases
BUF = N + TAIL
t_full = np.arange(BUF) / SR

def midi(m):                    # midi note -> frequency
    return 440.0 * 2 ** ((m - 69) / 12.0)

# ---- chord progression (midi notes) -----------------------------------------
CHORDS = [
    (38, [50, 54, 57, 61]),   # Dmaj7   (D2 bass; D3 F#3 A3 C#4)
    (43, [55, 59, 62, 66]),   # Gmaj7   (G2; G3 B3 D4 F#4)
    (47, [59, 62, 66, 69]),   # Bm7     (B2; B3 D4 F#4 A4)
    (45, [57, 62, 64, 67]),   # Asus    (A2; A3 D4 E4 G4)
]
SEQ = CHORDS + CHORDS                       # two loops
block = N / len(SEQ)                         # samples per chord
block_n = int(block)
rel_n = int(1.4 * SR)                        # release overlap
atk_n = int(0.9 * SR)                        # attack

padL = np.zeros(BUF); padR = np.zeros(BUF)
bass = np.zeros(BUF)

def pad_env(seg_n):
    env = np.ones(seg_n)
    env[:atk_n] = 0.5 * (1 - np.cos(np.linspace(0, np.pi, atk_n)))
    env[block_n:] = 0.5 * (1 + np.cos(np.linspace(0, np.pi, seg_n - block_n)))
    return env

PARTIALS = [(1, 1.0), (2, 0.42), (3, 0.15), (4, 0.06)]
for i, (broot, notes) in enumerate(SEQ):
    start = int(i * block)
    seg_n = block_n + rel_n
    tt = np.arange(seg_n) / SR
    env = pad_env(seg_n)
    L = np.zeros(seg_n); R = np.zeros(seg_n)
    for nm in notes:
        f = midi(nm)
        for h, amp in PARTIALS:
            phL = np.random.rand() * 2 * np.pi
            phR = np.random.rand() * 2 * np.pi
            L += amp * np.sin(2 * np.pi * f * h * 1.0025 * tt + phL)
            R += amp * np.sin(2 * np.pi * f * h * 0.9975 * tt + phR)
    L *= env / len(notes); R *= env / len(notes)
    end = min(start + seg_n, BUF)
    padL[start:end] += L[:end - start]
    padR[start:end] += R[:end - start]
    # sub bass: sine + soft 2nd harmonic
    fb = midi(broot)
    b = (np.sin(2 * np.pi * fb * tt) + 0.25 * np.sin(2 * np.pi * fb * 2 * tt)) * env
    bass[start:end] += b[:end - start]

# slow "breathing" tremolo on the pad
lfo = 0.85 + 0.15 * np.sin(2 * np.pi * 0.1 * t_full + 0.6)
padL *= lfo; padR *= lfo

# ---- sparse bell / piano-like melody (D-major pentatonic) --------------------
# (time_s, midi_note, velocity)
MEL = [
    (2.0, 78, 0.6), (4.6, 81, 0.65), (7.6, 76, 0.55), (10.4, 78, 0.6),
    (12.4, 74, 0.5), (14.8, 81, 0.65), (17.2, 83, 0.6), (19.6, 78, 0.6),
    (22.2, 81, 0.6), (24.4, 76, 0.5), (26.2, 74, 0.55),
]
mel = np.zeros(BUF)
BELL = [(1.0, 1.0), (2.0, 0.5), (3.01, 0.3), (4.2, 0.16)]
for ts, nm, vel in MEL:
    f = midi(nm)
    nlen = int(2.6 * SR)
    tt = np.arange(nlen) / SR
    s = np.zeros(nlen)
    for r, a in BELL:
        s += a * np.sin(2 * np.pi * f * r * tt)
    decay = np.exp(-tt / 1.05)
    at = int(0.004 * SR)
    decay[:at] *= np.linspace(0, 1, at)
    s *= decay * vel
    st = int(ts * SR)
    end = min(st + nlen, BUF)
    mel[st:end] += s[:end - st]

# ---- mix dry stereo ----------------------------------------------------------
dryL = 0.16 * padL + 0.20 * bass + 0.22 * mel
dryR = 0.16 * padR + 0.20 * bass + 0.22 * mel

# ---- warm convolution reverb -------------------------------------------------
def onepole_lp(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y

ir_n = int(1.6 * SR); pre = int(0.02 * SR)
tir = np.arange(ir_n) / SR
dec = np.exp(-tir / 0.42)
def make_ir():
    raw = np.random.randn(ir_n) * dec
    raw = onepole_lp(raw, 5500.0)            # tame hiss -> warm tail
    ir = np.concatenate([np.zeros(pre), raw])
    ir /= np.sqrt(np.sum(ir ** 2))
    return ir
irL = make_ir(); irR = make_ir()

def fftconv(x, h):
    n = len(x) + len(h) - 1
    y = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(h, n), n)
    return y[:len(x)]

wetL = fftconv(dryL, irL); wetR = fftconv(dryR, irR)
wpk = max(np.max(np.abs(wetL)), np.max(np.abs(wetR)), 1e-9)
dpk = max(np.max(np.abs(dryL)), np.max(np.abs(dryR)), 1e-9)
wetL *= dpk / wpk; wetR *= dpk / wpk
outL = 0.74 * dryL + 0.30 * wetL
outR = 0.74 * dryR + 0.30 * wetR

# ---- trim, fades, normalize --------------------------------------------------
outL = outL[:N]; outR = outR[:N]
fin = int(1.5 * SR); fout = int(3.0 * SR)
fade = np.ones(N)
fade[:fin] = 0.5 * (1 - np.cos(np.linspace(0, np.pi, fin)))
fade[N - fout:] = 0.5 * (1 + np.cos(np.linspace(0, np.pi, fout)))
outL *= fade; outR *= fade

pk = max(np.max(np.abs(outL)), np.max(np.abs(outR)), 1e-9)
g = 0.72 / pk
outL *= g; outR *= g

# ---- write 16-bit stereo WAV -------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "screenshots", "relaxing_pad.wav")
inter = np.empty(N * 2, dtype=np.float32)
inter[0::2] = outL; inter[1::2] = outR
pcm = np.clip(inter, -1, 1)
pcm16 = (pcm * 32767).astype('<i2')
with wave.open(OUT, 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(pcm16.tobytes())
print("wrote", OUT, "samples", N, "dur", round(N / SR, 2), "s")
