#!/usr/bin/env python3
"""Generate the project-original sound effects (stdlib only, deterministic).

Writes plain PCM 16-bit mono WAV files (the only format libGDX's WAV decoder reads, see docs/DEVELOPMENT.md,
"Assets"). Every random source uses a fixed seed, so two runs produce identical bytes.

    python3 tools/audio/generate_sounds.py            # write into assets/audio
    python3 tools/audio/generate_sounds.py --out DIR  # write into DIR instead (used by the unit test)

Outputs: jump.wav (breathy throaty "ha!"), the hero's voice set (hit.wav, bounce.wav, collect.wav, disappear.wav,
dust_puff.wav; issue #313 batch 1), the boss and menu voices (boss_hit.wav, boss_restore.wav, button_click.wav; batch 2), the environment (wall_rumble.wav loop, grit_fall.wav; batch 3), explosion.wav (#324), keepalive.wav (near-silent loop). The crude, buzzy
character is deliberate (see docs/SOUND_DIRECTION.md).
"""
from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------- global format
SAMPLE_RATE = 44100          # Hz; matches most existing effects (the old jump.wav was 44100 Hz)
FADE_MS = 4.0                # linear fade in/out on every file, removes edge clicks
PEAK_LIMIT_DB = -1.0         # hard ceiling asserted by the test (nothing may exceed this)

# ---------------------------------------------------------------- target peaks (dBFS)
DUST_PEAK_DB = -14.0         # whispered breath, plays together with the jump, must stay under it
RUMBLE_PEAK_DB = -9.0        # long low sound, sits under the music
GRIT_PEAK_DB = -12.0         # soft patter, quiet

# ---------------------------------------------------------------- keep-alive (output pipeline wake-up, #316)
KEEPALIVE_SEED = 5501
KEEPALIVE_DURATION = 1.0     # s; a seamless loop, played for the whole time the game runs
KEEPALIVE_RMS_DB = -65.0     # RMS level: far below audibility on speakers or headphones at normal volume (the room noise of
                             # a quiet room is about -50 dBFS-equivalent), yet about 18 LSB of 16-bit RMS, so it is never
                             # digital silence and never truncates to zeros; devices that idle on digital silence stay awake
KEEPALIVE_LOWPASS_HZ = 4000.0  # circular low-pass: a soft hiss instead of a bright one, even less audible at this level


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

# ---------------------------------------------------------------- dust puff: a muffled, soft "pfff" of air
# Mostly dark noise (no vowel colour) with a soft attack; the author asked for duller and less "hah" after batch 1.
DUST_SEED = 2203
DUST_DURATION = 0.14
DUST_ATTACK = 0.025          # soft onset: a puff of air, never a click
DUST_DECAY_TAU = 0.045
DUST_LOWPASS_HZ = 1300.0     # two passes of the 12 dB/oct low-pass: muffled
DUST_VOWEL_MIX = 0.15        # share of the /a/ formant-filtered noise mixed in (almost none: it is not a "hah")
DUST_RMS_DB = -29.0          # same loudness as the previous dust_puff (-29.2), peak capped at DUST_PEAK_DB

# ---------------------------------------------------------------- hero voice set (batch 1 of #313)
# Voice-synth sounds in the jump's recipe, described by a Voice record (see render_voice). Levels are set by RMS so the
# set stays balanced against the jump (-17.0 dBFS RMS); the peak is capped at VOICE_PEAK_CAP_DB.
VOICE_PEAK_CAP_DB = -2.0
FORMANTS_A = HA_FORMANTS     # open /a/ (the jump)
FORMANTS_O = ((520.0, 180.0, 1.0), (900.0, 240.0, 0.5), (2400.0, 420.0, 0.15))
FORMANTS_E = ((550.0, 150.0, 1.0), (1850.0, 250.0, 0.5), (2500.0, 350.0, 0.2))
FORMANTS_I = ((300.0, 120.0, 1.0), (2300.0, 260.0, 0.5), (3000.0, 350.0, 0.25))
FORMANTS_U = ((320.0, 100.0, 1.0), (800.0, 150.0, 0.4), (2400.0, 300.0, 0.1))
FORMANTS_AA = ((800.0, 200.0, 1.0), (1300.0, 280.0, 0.55), (2600.0, 450.0, 0.2))  # a slightly higher, strained /a/
FORMANTS_UH = ((600.0, 180.0, 1.0), (1000.0, 240.0, 0.5), (2400.0, 400.0, 0.15))  # a low, gruff /uh/


@dataclass(frozen=True)
class Voice:
    seed: int
    duration: float
    f0: tuple                # Hz at start, Hz at end (linear)
    formants: tuple          # start formant set
    formants_end: tuple      # end formant set (same as start for a steady vowel)
    rms_db: float            # target RMS after saturation
    asp_len: float = 0.025   # aspiration (breath) onset length, s
    asp_level: float = 0.9   # relative to the vowel peak
    attack: float = 0.012    # vowel attack, s
    tau: float = 0.08        # exponential decay of the vowel, s
    release: float = 0.012   # linear cutoff at the very end, s
    jitter: float = 0.03     # relative period jitter
    rough: float = 0.0       # extra roughness (vocal fry) 0..1
    rough_start: float = 0.6  # fraction of the duration where the roughness begins
    vibrato: tuple = (0.0, 0.0)  # (Hz, relative depth)
    lowpass: float = 3200.0  # Hz, final darkening


# Hero death / hurt (was the inherited hit.wav): a strained, falling "ah-uh!", /a/ closing to /o/, rough at the end.
HIT_VOICE = Voice(1401, 0.28, (260.0, 120.0), FORMANTS_AA, FORMANTS_O, -16.5, asp_len=0.025, asp_level=0.9, attack=0.012,
                  tau=0.12, release=0.025, jitter=0.04, rough=0.5, rough_start=0.4)
# Stomp / enemy defeated (was bounce.wav): a bright rising "heh!". 0.20 s: the first version (0.12 s) was reported as
# inaudible; the shortest effects are the first to be swallowed by an output that is waking up (#316).
BOUNCE_VOICE = Voice(1501, 0.20, (210.0, 300.0), FORMANTS_E, FORMANTS_E, -17.5, asp_len=0.020, asp_level=0.8,
                     attack=0.010, tau=0.090, release=0.012, jitter=0.02, lowpass=3500.0)
# Pickup (was collect.wav): two quick rising /i/ syllables "hi-hi"; it plays on every coin, so it is the quietest of the set.
COLLECT_VOICES = (Voice(1601, 0.09, (330.0, 350.0), FORMANTS_I, FORMANTS_I, -19.0, asp_len=0.015, asp_level=0.5,
                        attack=0.010, tau=0.045, release=0.010, jitter=0.02, lowpass=3800.0),
                  Voice(1602, 0.12, (440.0, 470.0), FORMANTS_I, FORMANTS_I, -19.0, asp_len=0.010, asp_level=0.3,
                        attack=0.010, tau=0.060, release=0.012, jitter=0.02, lowpass=3800.0))
COLLECT_SECOND_OFFSET = 0.085  # s, start of the second syllable
COLLECT_RMS_DB = -19.0
# Checkpoint (was disappear.wav): a long rising glide /u/ to /a/ with a little vibrato, "oo-aah".
DISAPPEAR_VOICE = Voice(1701, 0.55, (170.0, 330.0), FORMANTS_U, FORMANTS_A, -18.0, asp_len=0.04, asp_level=0.6,
                        attack=0.080, tau=0.350, release=0.120, jitter=0.02, vibrato=(6.0, 0.03))


# ---------------------------------------------------------------- explosion (#324): a low, rough "BOOM / fwoomph"
# A very low, fried voice (a falling grunt in the /o/-/u/ range, the same render_voice recipe) under a dark noise burst
# that dies fast, plus a short sub tail. It plays only for a bomb and an explosive stump/root, so it is rare and may be
# a little fuller (longer, RMS 1.5 dB above the hero's hit) than the frequent effects; the peak cap is the usual one.
EXPLOSION_VOICE = Voice(3501, 0.45, (85.0, 38.0), FORMANTS_UH, FORMANTS_U, -15.0, asp_len=0.030, asp_level=0.6,
                        attack=0.012, tau=0.160, release=0.060, jitter=0.08, rough=1.0, rough_start=0.15, lowpass=600.0)
EXPLOSION_SEED = 3502
EXPLOSION_RMS_DB = -15.0     # fuller than the voice set (-16.5 .. -19.4) on purpose: rare, and an explosion
EXPLOSION_BURST_ATTACK = 0.010   # s, the burst swells in (no click)
EXPLOSION_BURST_TAU = 0.060      # s, decay of the noise burst
EXPLOSION_BURST_LOWPASS_HZ = 500.0   # dark, a whoomph and not a hiss
EXPLOSION_BURST_GAIN = 0.9   # burst peak relative to the voiced body peak
EXPLOSION_SUB_HZ = (60.0, 34.0)  # sub tail, falling
EXPLOSION_SUB_TAU = 0.140    # s
EXPLOSION_SUB_GAIN = 0.7     # relative to the voiced body peak
EXPLOSION_SUB_DELAY = 0.020  # s, the sub starts just after the burst


def explosion() -> list[float]:
    body = render_voice(EXPLOSION_VOICE)
    n = len(body)
    peak = max(abs(a) for a in body) or 1.0
    rng = random.Random(EXPLOSION_SEED)
    burst = lowpass(lowpass(noise(rng, n), EXPLOSION_BURST_LOWPASS_HZ), EXPLOSION_BURST_LOWPASS_HZ)
    env = envelope(n, EXPLOSION_BURST_ATTACK, EXPLOSION_BURST_TAU)
    burst = [b * e for b, e in zip(burst, env)]
    bp = max(abs(b) for b in burst) or 1.0
    nd = n_samples(EXPLOSION_SUB_DELAY)
    sub_env = envelope(n - nd, 0.010, EXPLOSION_SUB_TAU)
    sub, phase = [0.0] * n, 0.0
    for i in range(n - nd):
        phase += lerp(EXPLOSION_SUB_HZ[0], EXPLOSION_SUB_HZ[1], i / (n - nd)) / SAMPLE_RATE
        sub[nd + i] = math.sin(2.0 * math.pi * phase) * sub_env[i]
    mix = [a + EXPLOSION_BURST_GAIN * peak * b / bp + EXPLOSION_SUB_GAIN * peak * c for a, b, c in zip(body, burst, sub)]
    mp = max(abs(a) for a in mix) or 1.0
    mix = [math.tanh(HA_DRIVE * a / mp) for a in mix]
    return finish_rms(mix, EXPLOSION_RMS_DB)


# ---------------------------------------------------------------- wall rumble: a very low growling voice (batch 3 of #313)
# A seamless 1 s loop of a glottal pulse train at about 50 Hz (vocal fry: single slow pulses, with period and amplitude
# jitter), through wide, low /o/-ish formants, plus a little breath. Every stage is circular, so there is no seam.
RUMBLE_SEED = 3302
RUMBLE_DURATION = 1.0
RUMBLE_PULSES = 50           # pulses per loop = 50 Hz fry
RUMBLE_PERIOD_JITTER = 0.15  # relative jitter of each pulse interval (the sum is renormalised to exactly 1 s)
RUMBLE_AMP_JITTER = 0.25     # irregular pulse amplitude (the growl)
RUMBLE_FORMANTS = ((300.0, 140.0, 1.0), (520.0, 200.0, 0.6), (900.0, 300.0, 0.15))  # a dark, closed /o/ to /u/
RUMBLE_BREATH_LOWPASS_HZ = 500.0
RUMBLE_BREATH_GAIN = 0.35    # circular noise breath relative to the voice
RUMBLE_DRIVE = 4.0          # tanh saturation of the pulse train: lowers the crest factor so the RMS reaches the target
RUMBLE_LOWPASS_HZ = 500.0    # dark: a growl in the chest, not a buzz
RUMBLE_WOBBLE_HZ = 3.0       # slow amplitude swell (whole cycles per loop)
RUMBLE_WOBBLE_DEPTH = 0.30
RUMBLE_RMS_DB = -20.0        # lower than the effects (-17 .. -19.4): it can play for seconds
RUMBLE_PEAK_CAP_DB = -9.0

# ---------------------------------------------------------------- falling grit: a soft whispered patter
# Unvoiced like dust_puff: the grains are soft-attack whisper ticks (breath), not clicks, and darker than before.
GRIT_SEED = 4402
GRIT_DURATION = 0.5
GRIT_TICKS = 24              # number of grains
GRIT_DENSITY_TAU = 0.18      # s; tick times are drawn from an exponential, so density decays
GRIT_TICK_LEN = 0.018        # s, one grain
GRIT_TICK_ATTACK = 0.008     # s, grains swell in (a whisper, not a click)
GRIT_TICK_BAND = (1900.0, 1500.0)   # centre, bandwidth of the grains (varied per tick by +-25 %)
GRIT_LEVEL_RANGE = (0.2, 1.0)       # random grain level
GRIT_LOWPASS_HZ = 3000.0     # darkens the whole patter
GRIT_RMS_DB = -30.0          # quiet background patter (before: -36.7); its sparse grains make a high crest factor, so the
                             # peak cap (GRIT_PEAK_DB) is what limits it, not this target


# ---------------------------------------------------------------- batch 2 of #313: boss and menu voices
# Boss hit (was boss_hit.wav): a deep, rough, falling grunt "uhh!" (the boss is big, so the pitch is low).
BOSS_HIT_VOICE = Voice(2401, 0.24, (115.0, 72.0), FORMANTS_UH, FORMANTS_O, -16.5, asp_len=0.030, asp_level=0.8,
                       attack=0.014, tau=0.10, release=0.030, jitter=0.05, rough=0.6, rough_start=0.3, lowpass=2600.0)
# Boss restored on a retry (was boss_restore.wav): a rising, warm "oh-ah" glide with vibrato, a recovery.
BOSS_RESTORE_VOICE = Voice(2501, 0.40, (120.0, 230.0), FORMANTS_O, FORMANTS_A, -19.0, asp_len=0.030, asp_level=0.5,
                           attack=0.060, tau=0.25, release=0.080, jitter=0.02, vibrato=(5.5, 0.025), lowpass=2800.0)
# Menu click (was button_click.wav), also the hover sound at 0.35 volume: a soft, short, closed "hi". It must stay
# pleasant when quiet and short enough that quick hovering does not stack into a smear.
CLICK_VOICE = Voice(2601, 0.11, (250.0, 280.0), FORMANTS_I, FORMANTS_E, -19.4, asp_len=0.020, asp_level=0.5,
                    attack=0.010, tau=0.040, release=0.015, jitter=0.02, lowpass=3000.0)


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


def render_voice(v: Voice) -> list[float]:
    """Voiced vowel (glottal sawtooth with jitter, vibrato and fry through gliding formants) with a breath onset, as in
    ha_grunt but for any vowel, pitch glide and length. Returns the samples before the final level and edge fade."""
    rng = random.Random(v.seed)
    n = n_samples(v.duration)
    nd = n_samples(v.asp_len * HA_VOICE_DELAY)
    src = [0.0] * n
    phase, scale, amp = 0.0, 1.0, 1.0
    for i in range(n):
        t = i / (n - 1)
        fry = max(0.0, (t - v.rough_start) / (1.0 - v.rough_start)) if v.rough else 0.0
        vib = 1.0 + v.vibrato[1] * math.sin(2.0 * math.pi * v.vibrato[0] * i / SAMPLE_RATE)
        phase += lerp(v.f0[0], v.f0[1], t) * vib * scale / SAMPLE_RATE
        if phase >= 1.0:
            phase -= 1.0
            scale = 1.0 + rng.uniform(-v.jitter, v.jitter) + fry * v.rough * rng.uniform(-HA_FRY_JITTER, HA_FRY_JITTER)
            amp = 1.0 + rng.uniform(-HA_PULSE_AMP_JITTER, HA_PULSE_AMP_JITTER) - fry * v.rough * rng.uniform(0.0, 1.0)
        src[i] = (2.0 * phase - 1.0) * amp
    src = lowpass(src, HA_SOURCE_LOWPASS_HZ)
    voiced = [0.0] * n
    for (fa, ba, ga), (fb, bb, gb) in zip(v.formants, v.formants_end):
        band = bandpass(src, lambda t, fa=fa, fb=fb: lerp(fa, fb, t), lambda t, ba=ba, bb=bb: lerp(ba, bb, t))
        voiced = [x + lerp(ga, gb, 0.5) * b for x, b in zip(voiced, band)]
    env = [0.0] * nd + envelope(n - nd, v.attack, v.tau)
    nr = n_samples(v.release)
    for i in range(nr):
        env[n - 1 - i] *= i / nr
    voiced = [x * e for x, e in zip(voiced, env)]
    peak = max(abs(x) for x in voiced) or 1.0
    na, nf, nramp = n_samples(v.asp_len), n_samples(HA_ASPIRATION_FADE), n_samples(HA_ASPIRATION_RAMP)
    hiss = bandpass(noise(rng, na), *HA_ASPIRATION_BAND)
    hp = max(abs(x) for x in hiss) or 1.0
    for i in range(na):
        voiced[i] += v.asp_level * peak * min(i / nramp, 1.0) * min((na - 1 - i) / nf, 1.0) * hiss[i] / hp
    voiced = lowpass(voiced, v.lowpass)
    peak = max(abs(x) for x in voiced) or 1.0
    return [math.tanh(HA_DRIVE * x / peak) for x in voiced]


def finish_rms(x: list[float], rms_db: float, peak_cap_db: float = VOICE_PEAK_CAP_DB) -> list[float]:
    """Remove DC, fade the edges, scale to a target RMS, but never above the peak cap."""
    mean = sum(x) / len(x)
    x = [a - mean for a in x]
    nf = min(n_samples(FADE_MS / 1000.0), len(x) // 2)
    for i in range(nf):
        x[i] *= i / nf
        x[-1 - i] *= i / nf
    rms = math.sqrt(sum(a * a for a in x) / len(x)) or 1.0
    peak = max(abs(a) for a in x) or 1.0
    k = min(10.0 ** (rms_db / 20.0) / rms, 10.0 ** (peak_cap_db / 20.0) / peak)
    return [a * k for a in x]


def hit() -> list[float]:
    return finish_rms(render_voice(HIT_VOICE), HIT_VOICE.rms_db)


def bounce() -> list[float]:
    return finish_rms(render_voice(BOUNCE_VOICE), BOUNCE_VOICE.rms_db)


def collect() -> list[float]:
    first, second = (render_voice(v) for v in COLLECT_VOICES)
    out = [0.0] * (n_samples(COLLECT_SECOND_OFFSET) + len(second))
    for i, a in enumerate(first):
        out[i] += a
    for i, a in enumerate(second):
        out[n_samples(COLLECT_SECOND_OFFSET) + i] += a
    return finish_rms(out, COLLECT_RMS_DB)


def disappear() -> list[float]:
    return finish_rms(render_voice(DISAPPEAR_VOICE), DISAPPEAR_VOICE.rms_db)


def boss_hit() -> list[float]:
    return finish_rms(render_voice(BOSS_HIT_VOICE), BOSS_HIT_VOICE.rms_db)


def boss_restore() -> list[float]:
    return finish_rms(render_voice(BOSS_RESTORE_VOICE), BOSS_RESTORE_VOICE.rms_db)


def button_click() -> list[float]:
    return finish_rms(render_voice(CLICK_VOICE), CLICK_VOICE.rms_db)


def dust_puff() -> list[float]:
    """Muffled "pfff": low-passed noise with a soft attack and a little vowel colour."""
    rng = random.Random(DUST_SEED)
    n = n_samples(DUST_DURATION)
    env = envelope(n, DUST_ATTACK, DUST_DECAY_TAU)
    raw = noise(rng, n)
    dark = lowpass(lowpass(raw, DUST_LOWPASS_HZ), DUST_LOWPASS_HZ)
    vowel = [0.0] * n
    for fc, bw, gain in HA_FORMANTS:
        vowel = [m + gain * b for m, b in zip(vowel, bandpass(raw, fc, bw))]
    dk, vw = max(abs(v) for v in dark) or 1.0, max(abs(v) for v in vowel) or 1.0
    mix = [d / dk + DUST_VOWEL_MIX * v / vw for d, v in zip(dark, vowel)]
    return finish_rms([m * e for m, e in zip(mix, env)], DUST_RMS_DB, DUST_PEAK_DB)


def wall_rumble() -> list[float]:
    """A seamless one-second loop (the game loops it for the wall's whole move time): the pulse train, the breath and the
    wobble are periodic in the file length, every filter runs over three copies and keeps the middle one, and there is no
    edge fade or envelope."""
    rng = random.Random(RUMBLE_SEED)
    n = n_samples(RUMBLE_DURATION)
    gaps = [1.0 + rng.uniform(-RUMBLE_PERIOD_JITTER, RUMBLE_PERIOD_JITTER) for _ in range(RUMBLE_PULSES)]
    k = n / sum(gaps)
    pulses = [0.0] * n
    pos = 0.0
    for g in gaps:                               # sawtooth-ish pulses: a decaying ramp after each glottal closure
        amp = 1.0 + rng.uniform(-RUMBLE_AMP_JITTER, RUMBLE_AMP_JITTER)
        start, length = int(pos), max(int(g * k), 1)
        for j in range(length):
            pulses[(start + j) % n] += amp * (1.0 - j / length)
        pos += g * k
    def circular(f, x):
        return f(x * 3)[n:2 * n]
    voice = [0.0] * n
    for fc, bw, gain in RUMBLE_FORMANTS:
        voice = [v + gain * b for v, b in zip(voice, circular(lambda y, fc=fc, bw=bw: bandpass(y, fc, bw), pulses))]
    vp = max(abs(v) for v in voice) or 1.0
    voice = [v / vp for v in voice]
    breath = circular(lambda y: lowpass(y, RUMBLE_BREATH_LOWPASS_HZ), noise(rng, n))
    bp = max(abs(v) for v in breath) or 1.0
    mix = [v + RUMBLE_BREATH_GAIN * b / bp for v, b in zip(voice, breath)]
    mp = max(abs(v) for v in mix) or 1.0
    mix = [math.tanh(RUMBLE_DRIVE * v / mp) for v in mix]          # memoryless, so still periodic
    mix = circular(lambda y: lowpass(y, RUMBLE_LOWPASS_HZ), mix)
    out = [m * (1.0 - RUMBLE_WOBBLE_DEPTH * 0.5 * (1.0 - math.cos(2.0 * math.pi * RUMBLE_WOBBLE_HZ * i / n)))
           for i, m in enumerate(mix)]
    mean = sum(out) / n
    out = [v - mean for v in out]
    rms = math.sqrt(sum(v * v for v in out) / n) or 1.0
    peak = max(abs(v) for v in out) or 1.0
    g = min(10.0 ** (RUMBLE_RMS_DB / 20.0) / rms, 10.0 ** (RUMBLE_PEAK_CAP_DB / 20.0) / peak)
    return [v * g for v in out]


def keepalive() -> list[float]:
    """Very quiet, non-silent noise loop. Circular low-pass (three copies, keep the middle one) so the wrap is as smooth
    as any other step; no fade, no envelope. Normalised to an RMS of KEEPALIVE_RMS_DB."""
    rng = random.Random(KEEPALIVE_SEED)
    n = n_samples(KEEPALIVE_DURATION)
    raw = noise(rng, n)
    body = lowpass(raw * 3, KEEPALIVE_LOWPASS_HZ)[n:2 * n]
    mean = sum(body) / n
    body = [v - mean for v in body]
    rms = math.sqrt(sum(v * v for v in body) / n) or 1.0
    k = 10.0 ** (KEEPALIVE_RMS_DB / 20.0) / rms
    return [v * k for v in body]


def grit_fall() -> list[float]:
    rng = random.Random(GRIT_SEED)
    n = n_samples(GRIT_DURATION)
    out = [0.0] * n
    nt = n_samples(GRIT_TICK_LEN)
    na = n_samples(GRIT_TICK_ATTACK)
    times = sorted(min(rng.expovariate(1.0 / GRIT_DENSITY_TAU), GRIT_DURATION - GRIT_TICK_LEN * 2)
                   for _ in range(GRIT_TICKS))
    for t in times:
        start = n_samples(t)
        level = rng.uniform(*GRIT_LEVEL_RANGE)
        fc = GRIT_TICK_BAND[0] * rng.uniform(0.75, 1.25)
        tick = bandpass(noise(rng, nt), fc, GRIT_TICK_BAND[1])
        for j in range(nt):
            out[start + j] += tick[j] * level * min(j / na, 1.0) * math.exp(-4.0 * j / nt)
    return finish_rms(lowpass(out, GRIT_LOWPASS_HZ), GRIT_RMS_DB, GRIT_PEAK_DB)


GAME_SOUNDS = {"jump.wav": jump, "hit.wav": hit, "bounce.wav": bounce, "collect.wav": collect,
               "disappear.wav": disappear, "boss_hit.wav": boss_hit, "boss_restore.wav": boss_restore,
               "button_click.wav": button_click, "dust_puff.wav": dust_puff, "wall_rumble.wav": wall_rumble,
               "grit_fall.wav": grit_fall, "explosion.wav": explosion, "keepalive.wav": keepalive}


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
