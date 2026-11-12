#!/usr/bin/env python3
"""Generate the project-original sound effects (stdlib only, deterministic).

Writes plain PCM 16-bit mono WAV files (the only format libGDX's WAV decoder reads, see docs/DEVELOPMENT.md,
"Assets"). Every random source uses a fixed seed, so two runs produce identical bytes.

    python3 tools/audio/generate_sounds.py            # write into assets/audio
    python3 tools/audio/generate_sounds.py --out DIR  # write into DIR instead (used by the unit test)

Outputs: jump.wav (breathy throaty "ha!"), dust_puff.wav, wall_rumble.wav, grit_fall.wav. The crude, buzzy
character is deliberate (see docs/SOUND_DIRECTION.md).
"""
from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path

# ---------------------------------------------------------------- global format
SAMPLE_RATE = 44100          # Hz; matches most existing effects (the legacy jump.wav was 44100 Hz)
FADE_MS = 4.0                # linear fade in/out on every file, removes edge clicks
PEAK_LIMIT_DB = -1.0         # hard ceiling asserted by the test (nothing may exceed this)

# ---------------------------------------------------------------- target peaks (dBFS)
DUST_PEAK_DB = -14.0         # plays together with the jump, must stay under it
RUMBLE_PEAK_DB = -9.0        # long low sound, sits under the music
GRIT_PEAK_DB = -12.0         # dry patter, quiet

# ---------------------------------------------------------------- jump: throaty effort "ha!"
# 'h' + 'a': a short glottal aspiration (band-passed noise with a smooth ramp, so it is breath and not a click)
# overlapping into a voiced open vowel /a/ (glottal pulse train with jitter, irregular pulse amplitude and
# vocal fry in the last third, through three wide formants and a dark low-pass). No nasal notch, no murmur.
# Fields: (seed, duration s, f0 start Hz, f0 end Hz, period jitter, aspiration length s, aspiration level relative to
#          the vowel peak, roughness in the last third, voiced attack s, vowel decay tau s, final cutoff s,
#          peak dBFS; tuned so the RMS lands near -16.5 dBFS)
JUMP_PARAMS = (1302, 0.15, 190.0, 155.0, 0.025, 0.030, 1.10, 0.15, 0.016, 0.065, 0.012, -8.0)
HA_FORMANTS = ((760.0, 190.0, 1.0), (1250.0, 260.0, 0.55), (2550.0, 420.0, 0.2))  # (Hz, bandwidth Hz, gain): open /a/
HA_LOWPASS_HZ = 3000.0       # dark, "from the throat"
HA_SOURCE_LOWPASS_HZ = 3500.0  # softens the sawtooth pulses before the formants
HA_ASPIRATION_BAND = (1500.0, 2500.0)  # centre, bandwidth: roughly 500..3000 Hz breath
HA_ASPIRATION_RAMP = 0.010   # s, smooth rise of the breath (no click)
HA_ASPIRATION_FADE = 0.012   # s, the breath tails off under the vowel
HA_VOICE_DELAY = 0.35        # the vowel starts at this fraction of the aspiration length (overlap)
HA_DRIVE = 3.0              # soft saturation (tanh) after the filters: lowers the crest factor so the RMS matches other effects
HA_PULSE_AMP_JITTER = 0.10   # irregular pulse amplitude over the whole sound (relative)
HA_FRY_START = 2.0 / 3.0     # last third: vocal-fry-like extra period jitter and amplitude roughness
HA_FRY_JITTER = 0.4          # extra relative period jitter at full fry, scaled by the roughness field

# ---------------------------------------------------------------- dust puff
DUST_SEED = 2201
DUST_DURATION = 0.10
DUST_BAND = (1400.0, 1800.0)  # centre, bandwidth of the scuff noise
DUST_ATTACK = 0.004
DUST_DECAY_TAU = 0.025

# ---------------------------------------------------------------- wall rumble
RUMBLE_SEED = 3301
RUMBLE_DURATION = 1.0
RUMBLE_LOWPASS_HZ = 180.0    # noise body
RUMBLE_SUB_HZ = 55.0         # sub sine, centre
RUMBLE_SUB_FM_HZ = 3.0       # slow frequency modulation of the sub
RUMBLE_SUB_FM_DEPTH_HZ = 8.0 # +-8 Hz, keeps it inside 40..70 Hz
RUMBLE_SUB_GAIN = 0.8        # relative to the noise body
RUMBLE_WOBBLE_HZ = 7.0       # amplitude wobble
RUMBLE_WOBBLE_DEPTH = 0.25   # fraction of the level
RUMBLE_ATTACK = 0.25         # s, slow attack
RUMBLE_RELEASE = 0.30        # s, smooth release (raised-cosine)

# ---------------------------------------------------------------- falling grit
GRIT_SEED = 4401
GRIT_DURATION = 0.5
GRIT_TICKS = 28              # number of grains
GRIT_DENSITY_TAU = 0.18      # s; tick times are drawn from an exponential, so density decays
GRIT_TICK_LEN = 0.006        # s, one grain
GRIT_TICK_BAND = (3200.0, 2400.0)   # centre, bandwidth of the grains (varied per tick by +-25 %)
GRIT_LEVEL_RANGE = (0.2, 1.0)       # random grain level


def n_samples(seconds: float) -> int:
    return int(round(seconds * SAMPLE_RATE))


def bandpass_coefs(fc: float, bw: float) -> tuple[float, float, float, float, float]:
    """RBJ constant 0 dB peak band-pass; returns (b0, b2, a1n, a2n) style tuple normalised by a0."""
    fc = min(max(fc, 20.0), SAMPLE_RATE * 0.45)
    q = max(fc / max(bw, 1.0), 0.2)
    w0 = 2.0 * math.pi * fc / SAMPLE_RATE
    alpha = math.sin(w0) / (2.0 * q)
    a0 = 1.0 + alpha
    return alpha / a0, 0.0, -alpha / a0, -2.0 * math.cos(w0) / a0, (1.0 - alpha) / a0


def bandpass(x: list[float], fc, bw) -> list[float]:
    """Band-pass x; fc and bw are constants or callables of the normalised position 0..1."""
    out = [0.0] * len(x)
    x1 = x2 = y1 = y2 = 0.0
    n = len(x)
    for i in range(n):
        t = i / max(n - 1, 1)
        c = fc(t) if callable(fc) else fc
        b = bw(t) if callable(bw) else bw
        b0, _, b2, a1, a2 = bandpass_coefs(c, b)
        y = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, x[i]
        y2, y1 = y1, y
        out[i] = y
    return out


def lowpass(x: list[float], fc: float) -> list[float]:
    """Two cascaded one-pole low-pass filters (12 dB/oct)."""
    a = 1.0 - math.exp(-2.0 * math.pi * fc / SAMPLE_RATE)
    y = [0.0] * len(x)
    s1 = s2 = 0.0
    for i, v in enumerate(x):
        s1 += a * (v - s1)
        s2 += a * (s1 - s2)
        y[i] = s2
    return y


def noise(rng: random.Random, n: int) -> list[float]:
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def envelope(n: int, attack: float, tau: float) -> list[float]:
    na = max(n_samples(attack), 1)
    return [min(i / na, 1.0) * math.exp(-max(i - na, 0) / SAMPLE_RATE / tau) for i in range(n)]


def finish(x: list[float], peak_db: float) -> list[float]:
    """Remove DC, fade the edges, normalise the peak to peak_db."""
    mean = sum(x) / len(x)
    x = [v - mean for v in x]
    nf = min(n_samples(FADE_MS / 1000.0), len(x) // 2)
    for i in range(nf):
        g = i / nf
        x[i] *= g
        x[-1 - i] *= g
    peak = max(abs(v) for v in x) or 1.0
    k = 10.0 ** (peak_db / 20.0) / peak
    return [v * k for v in x]


def write_wav(path: Path, x: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = struct.pack("<%dh" % len(x), *[max(-32768, min(32767, int(round(v * 32767.0)))) for v in x])
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(data)


# ---------------------------------------------------------------- the sounds
def ha_grunt(params: tuple) -> list[float]:
    seed, dur, f0a, f0b, jitter, asp_len, asp_level, rough, attack, tau, release, peak_db = params
    rng = random.Random(seed)
    n = n_samples(dur)
    nd = n_samples(asp_len * HA_VOICE_DELAY)
    src = [0.0] * n
    phase, scale, amp = 0.0, 1.0, 1.0
    for i in range(n):
        t = i / (n - 1)
        fry = max(0.0, (t - HA_FRY_START) / (1.0 - HA_FRY_START))   # 0..1 over the last third
        phase += lerp(f0a, f0b, t) * scale / SAMPLE_RATE
        if phase >= 1.0:
            phase -= 1.0
            scale = 1.0 + rng.uniform(-jitter, jitter) + fry * rough * rng.uniform(-HA_FRY_JITTER, HA_FRY_JITTER)
            amp = 1.0 + rng.uniform(-HA_PULSE_AMP_JITTER, HA_PULSE_AMP_JITTER) - fry * rough * rng.uniform(0.0, 1.0)
        src[i] = (2.0 * phase - 1.0) * amp
    src = lowpass(src, HA_SOURCE_LOWPASS_HZ)
    voiced = [0.0] * n
    for fc, bw, gain in HA_FORMANTS:
        voiced = [v + gain * b for v, b in zip(voiced, bandpass(src, fc, bw))]
    env = [0.0] * nd + envelope(n - nd, attack, tau)
    nr = n_samples(release)
    for i in range(nr):                       # quick, slightly abrupt cutoff
        env[n - 1 - i] *= i / nr
    voiced = [v * e for v, e in zip(voiced, env)]
    peak = max(abs(v) for v in voiced) or 1.0
    na, nf = n_samples(asp_len), n_samples(HA_ASPIRATION_FADE)
    nr_asp = n_samples(HA_ASPIRATION_RAMP)
    hiss = bandpass(noise(rng, na), *HA_ASPIRATION_BAND)
    hp = max(abs(v) for v in hiss) or 1.0
    for i in range(na):
        g = min(i / nr_asp, 1.0) * min((na - 1 - i) / nf, 1.0)
        voiced[i] += asp_level * peak * g * hiss[i] / hp
    voiced = lowpass(voiced, HA_LOWPASS_HZ)
    peak = max(abs(v) for v in voiced) or 1.0
    voiced = [math.tanh(HA_DRIVE * v / peak) for v in voiced]
    return finish(voiced, peak_db)


def jump() -> list[float]:
    return ha_grunt(JUMP_PARAMS)


def dust_puff() -> list[float]:
    rng = random.Random(DUST_SEED)
    n = n_samples(DUST_DURATION)
    env = envelope(n, DUST_ATTACK, DUST_DECAY_TAU)
    band = bandpass(noise(rng, n), *DUST_BAND)
    return finish([b * e for b, e in zip(band, env)], DUST_PEAK_DB)


def wall_rumble() -> list[float]:
    rng = random.Random(RUMBLE_SEED)
    n = n_samples(RUMBLE_DURATION)
    body = lowpass(noise(rng, n), RUMBLE_LOWPASS_HZ)
    bp = max(abs(v) for v in body) or 1.0
    body = [v / bp for v in body]
    phase = 0.0
    out = []
    na, nr = n_samples(RUMBLE_ATTACK), n_samples(RUMBLE_RELEASE)
    for i in range(n):
        s = i / SAMPLE_RATE
        f = RUMBLE_SUB_HZ + RUMBLE_SUB_FM_DEPTH_HZ * math.sin(2.0 * math.pi * RUMBLE_SUB_FM_HZ * s)
        phase += f / SAMPLE_RATE
        sub = math.sin(2.0 * math.pi * phase)
        wob = 1.0 - RUMBLE_WOBBLE_DEPTH * 0.5 * (1.0 - math.sin(2.0 * math.pi * RUMBLE_WOBBLE_HZ * s))
        a = 0.5 - 0.5 * math.cos(math.pi * min(i / na, 1.0))            # raised-cosine attack
        r = 0.5 - 0.5 * math.cos(math.pi * min((n - 1 - i) / nr, 1.0))  # raised-cosine release
        out.append((body[i] + RUMBLE_SUB_GAIN * sub) * wob * a * r)
    return finish(out, RUMBLE_PEAK_DB)


def grit_fall() -> list[float]:
    rng = random.Random(GRIT_SEED)
    n = n_samples(GRIT_DURATION)
    out = [0.0] * n
    nt = n_samples(GRIT_TICK_LEN)
    times = sorted(min(rng.expovariate(1.0 / GRIT_DENSITY_TAU), GRIT_DURATION - GRIT_TICK_LEN * 2)
                   for _ in range(GRIT_TICKS))
    for t in times:
        start = n_samples(t)
        level = rng.uniform(*GRIT_LEVEL_RANGE)
        fc = GRIT_TICK_BAND[0] * rng.uniform(0.75, 1.25)
        tick = bandpass(noise(rng, nt), fc, GRIT_TICK_BAND[1])
        for j in range(nt):
            out[start + j] += tick[j] * level * math.exp(-5.0 * j / nt)
    return finish(out, GRIT_PEAK_DB)


GAME_SOUNDS = {"jump.wav": jump, "dust_puff.wav": dust_puff, "wall_rumble.wav": wall_rumble,
               "grit_fall.wav": grit_fall}
def generate(out_dir: Path) -> list[Path]:
    written = []
    for name, fn in GAME_SOUNDS.items():
        write_wav(out_dir / name, fn())
        written.append(out_dir / name)
    return written


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", type=Path, help="write into this directory instead of assets/audio")
    args = ap.parse_args()
    paths = generate(args.out or root / "assets" / "audio")
    for p in paths:
        print(p)


if __name__ == "__main__":
    main()
