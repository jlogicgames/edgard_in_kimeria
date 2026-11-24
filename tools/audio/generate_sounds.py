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


# ---------------------------------------------------------------- stone impact (#347): a short "thud-crack" and pebbles
# A low, gruff voiced thump (the same render_voice recipe, a falling /uh/), a very short band-passed noise crack on the
# hit and a few soft pebble ticks that settle after it. It plays once per falling stone, a little above the hero's hit.
STONE_IMPACT_VOICE = Voice(3601, 0.20, (105.0, 55.0), FORMANTS_UH, FORMANTS_U, -16.5, asp_len=0.015, asp_level=0.5,
                           attack=0.006, tau=0.070, release=0.040, jitter=0.05, rough=0.5, rough_start=0.4, lowpass=1500.0)
STONE_IMPACT_SEED = 3602
STONE_IMPACT_RMS_DB = -16.5
STONE_IMPACT_DURATION = 0.30
STONE_CRACK_BAND = (1400.0, 1800.0)  # Hz centre, bandwidth of the crack
STONE_CRACK_ATTACK = 0.010   # s, the crack swells in (no click; the shared soft-start test)
STONE_CRACK_TAU = 0.018      # s, decay of the crack
STONE_CRACK_GAIN = 0.55      # relative to the voiced thump peak
STONE_PEBBLES = 5            # soft ticks after the hit
STONE_PEBBLE_START = 0.05    # s, earliest pebble
STONE_PEBBLE_SPAN = 0.17     # s, the pebbles fall within this window after the first
STONE_PEBBLE_LEN = 0.014     # s, one pebble
STONE_PEBBLE_GAIN = 0.22     # relative to the thump peak, times a random 0.4..1 level


def stone_impact() -> list[float]:
    body = render_voice(STONE_IMPACT_VOICE)
    n = n_samples(STONE_IMPACT_DURATION)
    body = body + [0.0] * (n - len(body))
    peak = max(abs(a) for a in body) or 1.0
    rng = random.Random(STONE_IMPACT_SEED)
    crack = bandpass(noise(rng, n), *STONE_CRACK_BAND)
    cp = max(abs(c) for c in crack) or 1.0
    env = envelope(n, STONE_CRACK_ATTACK, STONE_CRACK_TAU)
    mix = [b + STONE_CRACK_GAIN * peak * c * e / cp for b, c, e in zip(body, crack, env)]
    nt = n_samples(STONE_PEBBLE_LEN)
    times = sorted(STONE_PEBBLE_START + rng.random() * STONE_PEBBLE_SPAN for _ in range(STONE_PEBBLES))
    for t in times:
        start = n_samples(t)
        level = STONE_PEBBLE_GAIN * peak * rng.uniform(0.4, 1.0)
        tick = bandpass(noise(rng, nt), 2200.0 * rng.uniform(0.8, 1.2), 1500.0)
        tp = max(abs(v) for v in tick) or 1.0
        for j in range(nt):
            mix[start + j] += level * tick[j] / tp * min(j / (nt * 0.3), 1.0) * math.exp(-4.0 * j / nt)
    mp = max(abs(a) for a in mix) or 1.0
    mix = [math.tanh(HA_DRIVE * a / mp) for a in mix]
    return finish_rms(mix, STONE_IMPACT_RMS_DB)


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
# ---------------------------------------------------------------- creeping bramble (#364, D89): the warning and the grow
# The rustle is the bramble's D7 warning tell: a dry crackle of brittle canes over a whispered "hhh" bed, quiet like the
# grit but brighter. The lash is the arm shooting out: a short rising "shk", a breath whose band sweeps up.
BRAMBLE_RUSTLE_SEED = 6401
BRAMBLE_RUSTLE_DURATION = 0.36
BRAMBLE_RUSTLE_TICKS = 18           # dry cane snaps
BRAMBLE_RUSTLE_TICK_LEN = 0.012     # s, one snap
BRAMBLE_RUSTLE_TICK_ATTACK = 0.004  # s, a whisper-soft onset, not a click
BRAMBLE_RUSTLE_TICK_BAND = (3200.0, 1800.0)  # centre, bandwidth (varied per tick by +-25 %)
BRAMBLE_RUSTLE_BED_GAIN = 0.35      # the breathy bed under the snaps
BRAMBLE_RUSTLE_ATTACK = 0.03        # s, the bed swells in
BRAMBLE_RUSTLE_TAU = 0.16           # s, the bed decays
BRAMBLE_RUSTLE_LOWPASS_HZ = 5500.0
BRAMBLE_RUSTLE_RMS_DB = -27.0       # quieter than the voices, a little louder than the grit: it is a warning
BRAMBLE_RUSTLE_PEAK_DB = -9.0 
BRAMBLE_LASH_SEED = 6402
BRAMBLE_LASH_DURATION = 0.16
BRAMBLE_LASH_SWEEP = (1200.0, 4200.0)  # band centre from, to (Hz): the rising "shk"
BRAMBLE_LASH_BANDWIDTH = 900.0
BRAMBLE_LASH_ATTACK = 0.012         # s
BRAMBLE_LASH_TAU = 0.05             # s
BRAMBLE_LASH_DRIVE = 3.0            # a little tanh grit
BRAMBLE_LASH_RMS_DB = -21.0
BRAMBLE_LASH_PEAK_DB = -9.0


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
# The silver thread goes taut on death (#341, D81): a plucked "tinn", a high, almost breathless /i/ with a hard-ish
# attack, a slight downward bend like a string settling, and a short ring with a little vibrato. A consequence sound
# (after the hit's "ah-uh"), so it stays at the quiet end of the voice set.
THREAD_VOICE = Voice(2701, 0.22, (560.0, 520.0), FORMANTS_I, FORMANTS_I, -19.0, asp_len=0.006, asp_level=0.2,
                     attack=0.004, tau=0.090, release=0.030, jitter=0.01, vibrato=(9.0, 0.015), lowpass=4200.0)

# ---------------------------------------------------------------- trip-line deadfall (#363, D88)
# The warning, at the trip: a strained rope-and-wood creak, a very low voice in constant vocal fry (a creak is fry) that
# rises slowly from /e/ to a strained /a/. 0.6 s, so it ends before the base 0.8 s warning releases the log and never
# masks the swing; it is not stretched by the longer-warnings assist.
DEADFALL_CREAK_VOICE = Voice(3701, 0.60, (72.0, 112.0), FORMANTS_E, FORMANTS_AA, -18.5, asp_len=0.020, asp_level=0.3,
                             attack=0.050, tau=0.60, release=0.090, jitter=0.12, rough=1.0, rough_start=0.0, lowpass=2200.0)
# The consequence, when the ropes hit the snag: a short, dull wooden "dok", a closed /o/ falling to /u/, dark and quick.
DEADFALL_KNOCK_VOICE = Voice(3801, 0.18, (150.0, 95.0), FORMANTS_O, FORMANTS_U, -17.0, asp_len=0.008, asp_level=0.3,
                             attack=0.004, tau=0.050, release=0.030, jitter=0.04, rough=0.3, rough_start=0.3, lowpass=1400.0)



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


def thread() -> list[float]:
    return finish_rms(render_voice(THREAD_VOICE), THREAD_VOICE.rms_db)


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


def deadfall_creak() -> list[float]:
    return finish_rms(render_voice(DEADFALL_CREAK_VOICE), DEADFALL_CREAK_VOICE.rms_db)


def deadfall_knock() -> list[float]:
    return finish_rms(render_voice(DEADFALL_KNOCK_VOICE), DEADFALL_KNOCK_VOICE.rms_db)


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


def bramble_rustle() -> list[float]:
    """Dry cane snaps (soft-attack band-passed ticks) over a whispered "hhh" bed shaped by the /a/ formants."""
    rng = random.Random(BRAMBLE_RUSTLE_SEED)
    n = n_samples(BRAMBLE_RUSTLE_DURATION)
    out = [0.0] * n
    nt, na = n_samples(BRAMBLE_RUSTLE_TICK_LEN), n_samples(BRAMBLE_RUSTLE_TICK_ATTACK)
    for _ in range(BRAMBLE_RUSTLE_TICKS):
        start = rng.randrange(n_samples(0.01), n - nt - n_samples(0.02))
        level = rng.uniform(0.3, 1.0)
        fc = BRAMBLE_RUSTLE_TICK_BAND[0] * rng.uniform(0.75, 1.25)
        tick = bandpass(noise(rng, nt), fc, BRAMBLE_RUSTLE_TICK_BAND[1])
        for j in range(nt):
            out[start + j] += tick[j] * level * min(j / na, 1.0) * math.exp(-4.0 * j / nt)
    raw, bed = noise(rng, n), [0.0] * n
    for fc, bw, gain in HA_FORMANTS:
        bed = [b + gain * v for b, v in zip(bed, bandpass(raw, fc, bw))]
    tp, bp = max(abs(v) for v in out) or 1.0, max(abs(v) for v in bed) or 1.0
    env = envelope(n, BRAMBLE_RUSTLE_ATTACK, BRAMBLE_RUSTLE_TAU)
    mix = [t / tp + BRAMBLE_RUSTLE_BED_GAIN * b / bp * e for t, b, e in zip(out, bed, env)]
    return finish_rms(lowpass(mix, BRAMBLE_RUSTLE_LOWPASS_HZ), BRAMBLE_RUSTLE_RMS_DB, BRAMBLE_RUSTLE_PEAK_DB)


def bramble_lash() -> list[float]:
    """A rising "shk": noise through a band-pass whose centre sweeps up, a quick swell and decay, lightly driven."""
    rng = random.Random(BRAMBLE_LASH_SEED)
    n = n_samples(BRAMBLE_LASH_DURATION)
    lo, hi = BRAMBLE_LASH_SWEEP
    swept = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, BRAMBLE_LASH_BANDWIDTH)
    peak = max(abs(v) for v in swept) or 1.0
    env = envelope(n, BRAMBLE_LASH_ATTACK, BRAMBLE_LASH_TAU)
    return finish_rms([math.tanh(BRAMBLE_LASH_DRIVE * v / peak) * e for v, e in zip(swept, env)],
                      BRAMBLE_LASH_RMS_DB, BRAMBLE_LASH_PEAK_DB)
# ---------------------------------------------------------------- hanging hive (#365, D90)
# Warning (hive_buzz): a nasal, fluttering "nnzzz" rising a little over the D7 shake; plays once at the start of each warning,
# at the quiet end of the voice set (-19.4, like the menu click) so it does not mask another warning. The flutter is a fast vibrato, the buzz the roughness.
HIVE_BUZZ_VOICE = Voice(6501, 0.70, (150.0, 190.0), FORMANTS_I, FORMANTS_E, -19.4, asp_len=0.030, asp_level=0.4,
                        attack=0.060, tau=0.500, release=0.080, jitter=0.05, rough=0.8, rough_start=0.0,
                        vibrato=(24.0, 0.06), lowpass=3000.0)
# Consequence (stinger_rain): a dry, even "tsk-tsk-tsk" patter for the 1 s curtain; grains like grit_fall's, but evenly
# spread, brighter and a little louder.
STINGER_SEED = 6502
STINGER_DURATION = 1.0
STINGER_TICKS = 70
STINGER_TICK_LEN = 0.014     # s, one dart
STINGER_TICK_ATTACK = 0.004  # s, darts swell in (no click)
STINGER_TICK_BAND = (3200.0, 1800.0)   # centre, bandwidth (varied per tick by +-20 %)
STINGER_LEVEL_RANGE = (0.4, 1.0)
STINGER_SPREAD = 0.4         # each tick lands within this fraction of its even slot
STINGER_LOWPASS_HZ = 5000.0
STINGER_RMS_DB = -28.0         # under the voice set, above grit_fall; its sparse darts make it peak-limited near -27.5
STINGER_PEAK_DB = -10.0
# Consequence (hive_fall): a papery crunch over a small dull thud, then a few scattered buzz grains (the swarm leaving).
HIVE_FALL_VOICE = Voice(6503, 0.30, (130.0, 80.0), FORMANTS_UH, FORMANTS_U, -19.0, asp_len=0.015, asp_level=0.5,
                        attack=0.010, tau=0.080, release=0.040, jitter=0.05, rough=0.6, rough_start=0.2, lowpass=2600.0)
HIVE_FALL_SEED = 6504
HIVE_FALL_DURATION = 0.42
HIVE_CRUNCH_BAND = (2400.0, 2600.0)  # Hz centre, bandwidth: dry paper
HIVE_CRUNCH_ATTACK = 0.008
HIVE_CRUNCH_TAU = 0.045
HIVE_CRUNCH_GAIN = 0.7       # relative to the thud's peak
HIVE_SCATTER_GRAINS = 6
HIVE_SCATTER_START = 0.12    # s
HIVE_SCATTER_SPAN = 0.24     # s
HIVE_SCATTER_LEN = 0.03      # s, one buzz grain
HIVE_SCATTER_HZ = (180.0, 260.0)  # grain pitch range (a sawtooth buzz)
HIVE_SCATTER_GAIN = 0.18     # relative to the thud's peak
HIVE_FALL_RMS_DB = -19.0


def hive_buzz() -> list[float]:
    return finish_rms(render_voice(HIVE_BUZZ_VOICE), HIVE_BUZZ_VOICE.rms_db)


def stinger_rain() -> list[float]:
    rng = random.Random(STINGER_SEED)
    n = n_samples(STINGER_DURATION)
    out = [0.0] * n
    nt, na = n_samples(STINGER_TICK_LEN), n_samples(STINGER_TICK_ATTACK)
    slot = (STINGER_DURATION - 2 * STINGER_TICK_LEN) / STINGER_TICKS
    for k in range(STINGER_TICKS):
        start = n_samples(STINGER_TICK_LEN + slot * (k + rng.uniform(0.0, STINGER_SPREAD)))
        level = rng.uniform(*STINGER_LEVEL_RANGE)
        tick = bandpass(noise(rng, nt), STINGER_TICK_BAND[0] * rng.uniform(0.8, 1.2), STINGER_TICK_BAND[1])
        for j in range(nt):
            out[start + j] += tick[j] * level * min(j / na, 1.0) * math.exp(-4.0 * j / nt)
    return finish_rms(lowpass(out, STINGER_LOWPASS_HZ), STINGER_RMS_DB, STINGER_PEAK_DB)


def hive_fall() -> list[float]:
    body = render_voice(HIVE_FALL_VOICE)
    n = n_samples(HIVE_FALL_DURATION)
    body = body[:n] + [0.0] * max(0, n - len(body))
    peak = max(abs(v) for v in body) or 1.0
    rng = random.Random(HIVE_FALL_SEED)
    crunch = bandpass(noise(rng, n), *HIVE_CRUNCH_BAND)
    cp = max(abs(v) for v in crunch) or 1.0
    env = envelope(n, HIVE_CRUNCH_ATTACK, HIVE_CRUNCH_TAU)
    mix = [b + HIVE_CRUNCH_GAIN * peak * c * e / cp for b, c, e in zip(body, crunch, env)]
    ng = n_samples(HIVE_SCATTER_LEN)
    for _ in range(HIVE_SCATTER_GRAINS):
        start = n_samples(HIVE_SCATTER_START + rng.random() * HIVE_SCATTER_SPAN)
        hz, level = rng.uniform(*HIVE_SCATTER_HZ), HIVE_SCATTER_GAIN * peak * rng.uniform(0.5, 1.0)
        for j in range(min(ng, n - start)):
            saw = 2.0 * ((j * hz / SAMPLE_RATE) % 1.0) - 1.0
            mix[start + j] += level * saw * math.sin(math.pi * j / ng)
    return finish_rms(mix, HIVE_FALL_RMS_DB)



# ---------------------------------------------------------------- ember fissure (#366, D91)
# Warning (fissure_rumble): a low, bubbling throat "hrrrm-bl-bl" swelling under the D7 glow; 0.6 s so it ends before the
# base 0.8 s jet, fixed length. Rough and darker than hive_buzz and bramble_rustle so the warnings never mask each other;
# the slow vibrato is the bubbling. Quiet end of the voice set.
FISSURE_RUMBLE_VOICE = Voice(6601, 0.60, (85.0, 110.0), FORMANTS_UH, FORMANTS_U, -19.4, asp_len=0.040, asp_level=0.3,
                             attack=0.150, tau=0.600, release=0.100, jitter=0.08, rough=1.0, rough_start=0.0,
                             vibrato=(11.0, 0.10), lowpass=1400.0)
# Consequence (ember_jet): a breathy roaring "FFSHHHH" at the jet's start: noise through a band-pass whose centre climbs
# with the flame, a fast swell and a decay over the hold, with dry crackle ticks on top; not a voice (fire, like the
# stinger patter), so it sits with the environment sounds, louder than them because it is the trap's act.
EMBER_JET_SEED = 6602
EMBER_JET_DURATION = 0.8
EMBER_JET_SWEEP = (700.0, 1800.0)    # Hz, band-pass centre from start to end
EMBER_JET_BANDWIDTH = 1400.0
EMBER_JET_ATTACK = 0.025             # s, the roar swells in (no click)
EMBER_JET_TAU = 0.35                 # s, decay over the hold
EMBER_JET_DRIVE = 1.6                # soft saturation of the roar
EMBER_CRACKLES = 14
EMBER_CRACKLE_LEN = 0.006            # s, one crackle
EMBER_CRACKLE_BAND = (4200.0, 2000.0)
EMBER_CRACKLE_GAIN = (0.15, 0.4)     # relative to the roar's peak
EMBER_JET_LOWPASS_HZ = 6000.0
EMBER_JET_RMS_DB = -23.0               # peak-limited by the -9 dBFS cap: the decaying roar has a high crest
EMBER_JET_PEAK_DB = -9.0             # the environment cap (test_ambient_levels)


def fissure_rumble() -> list[float]:
    return finish_rms(render_voice(FISSURE_RUMBLE_VOICE), FISSURE_RUMBLE_VOICE.rms_db)


def ember_jet() -> list[float]:
    rng = random.Random(EMBER_JET_SEED)
    n = n_samples(EMBER_JET_DURATION)
    lo, hi = EMBER_JET_SWEEP
    roar = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, EMBER_JET_BANDWIDTH)
    peak = max(abs(v) for v in roar) or 1.0
    env = envelope(n, EMBER_JET_ATTACK, EMBER_JET_TAU)
    out = [math.tanh(EMBER_JET_DRIVE * v / peak) * e for v, e in zip(roar, env)]
    nc = n_samples(EMBER_CRACKLE_LEN)
    for _ in range(EMBER_CRACKLES):
        start = rng.randrange(0, n - nc)
        tick = bandpass(noise(rng, nc), EMBER_CRACKLE_BAND[0] * rng.uniform(0.8, 1.2), EMBER_CRACKLE_BAND[1])
        tp = max(abs(v) for v in tick) or 1.0
        level = rng.uniform(*EMBER_CRACKLE_GAIN)
        for j in range(nc):
            out[start + j] += level * tick[j] / tp * math.sin(math.pi * j / nc)
    return finish_rms(lowpass(out, EMBER_JET_LOWPASS_HZ), EMBER_JET_RMS_DB, EMBER_JET_PEAK_DB)

# The fireball going out (#397): on terrain at full volume, at the end of its life at 0.6 (Level.fireballFizzle). A short
# "pfsss": noise through a band-pass whose centre falls as the flame cools, a quick swell and a fast decay, with a few dry
# crackles; not a voice (fire, like ember_jet), so it sits with the environment sounds, quieter than the fissure's jet.
FIREBALL_FIZZLE_SEED = 6701
FIREBALL_FIZZLE_DURATION = 0.35
FIREBALL_FIZZLE_SWEEP = (2600.0, 900.0)   # Hz, band-pass centre from start to end: the hiss cools
FIREBALL_FIZZLE_BANDWIDTH = 1600.0
FIREBALL_FIZZLE_ATTACK = 0.015            # s, swells in (no click)
FIREBALL_FIZZLE_TAU = 0.11                # s, dies out fast
FIREBALL_FIZZLE_DRIVE = 1.4               # soft saturation of the hiss
FIREBALL_FIZZLE_CRACKLES = 5
FIREBALL_FIZZLE_CRACKLE_SPAN = 0.5        # crackles fall in the first half, where the flame still burns
FIREBALL_FIZZLE_LOWPASS_HZ = 6000.0
FIREBALL_FIZZLE_RMS_DB = -25.0            # peak-limited by the -9 dBFS cap: the fast decay has a high crest
FIREBALL_FIZZLE_PEAK_DB = -9.0            # the environment cap (test_ambient_levels)


def fireball_fizzle() -> list[float]:
    rng = random.Random(FIREBALL_FIZZLE_SEED)
    n = n_samples(FIREBALL_FIZZLE_DURATION)
    hi, lo = FIREBALL_FIZZLE_SWEEP
    hiss = bandpass(noise(rng, n), lambda t: hi + (lo - hi) * t, FIREBALL_FIZZLE_BANDWIDTH)
    peak = max(abs(v) for v in hiss) or 1.0
    env = envelope(n, FIREBALL_FIZZLE_ATTACK, FIREBALL_FIZZLE_TAU)
    out = [math.tanh(FIREBALL_FIZZLE_DRIVE * v / peak) * e for v, e in zip(hiss, env)]
    nc = n_samples(EMBER_CRACKLE_LEN)
    for _ in range(FIREBALL_FIZZLE_CRACKLES):
        start = rng.randrange(n_samples(FIREBALL_FIZZLE_ATTACK), int(n * FIREBALL_FIZZLE_CRACKLE_SPAN))
        tick = bandpass(noise(rng, nc), EMBER_CRACKLE_BAND[0] * rng.uniform(0.8, 1.2), EMBER_CRACKLE_BAND[1])
        tp = max(abs(v) for v in tick) or 1.0
        level = rng.uniform(*EMBER_CRACKLE_GAIN)
        for j in range(nc):
            out[start + j] += level * tick[j] / tp * math.sin(math.pi * j / nc)
    return finish_rms(lowpass(out, FIREBALL_FIZZLE_LOWPASS_HZ), FIREBALL_FIZZLE_RMS_DB, FIREBALL_FIZZLE_PEAK_DB)


# ---------------------------------------------------------------- forest beetle (#327, D100)
# Notice (beetle_notice): the beetle's own call as it notices the hero, no shared alert sound (D100): two dry mandible
# clicks and a short rising chirp, a voice like the others, at the quiet end of the voice set.
BEETLE_NOTICE_SEED = 6801
BEETLE_NOTICE_DURATION = 0.22
BEETLE_CLICK_TIMES = (0.012, 0.050)       # s, the two clicks
BEETLE_CLICK_LEN = 0.008                  # s, one click
BEETLE_CLICK_BAND = (3800.0, 2200.0)      # centre, bandwidth: dry and bright
BEETLE_CLICK_GAIN = 0.9                   # relative to the chirp's peak
BEETLE_CHIRP_VOICE = Voice(6802, 0.13, (300.0, 560.0), FORMANTS_E, FORMANTS_I, -19.0, asp_len=0.010, asp_level=0.4,
                           attack=0.008, tau=0.070, release=0.020, jitter=0.03, rough=0.3, rough_start=0.5, lowpass=3600.0)
BEETLE_CHIRP_START = 0.085                # s, the chirp follows the clicks
BEETLE_NOTICE_RMS_DB = -19.0
# Wind-up (beetle_windup 0.8 s, beetle_windup_long 1.2 s with the assist): a rising buzzing whine whose ticks quicken with
# the blink (0.2 s down to 0.05 s, as Beetle draws it), ending in a short intake before the pop. A voice like hive_buzz,
# lower and rougher at the start, so the two warnings never sound alike. Played once, as long as the wind-up, so it never
# wraps; it runs to its end even if the beetle is killed in the wind-up (D100).
BEETLE_WINDUP_VOICES = {0.8: Voice(6803, 0.80, (110.0, 330.0), FORMANTS_UH, FORMANTS_I, -19.4, asp_len=0.020, asp_level=0.3,
                                   attack=0.120, tau=3.0, release=0.030, jitter=0.06, rough=0.7, rough_start=0.0,
                                   vibrato=(16.0, 0.08), lowpass=2800.0),
                        1.2: Voice(6804, 1.20, (110.0, 330.0), FORMANTS_UH, FORMANTS_I, -19.4, asp_len=0.020, asp_level=0.3,
                                   attack=0.150, tau=4.5, release=0.030, jitter=0.06, rough=0.7, rough_start=0.0,
                                   vibrato=(16.0, 0.08), lowpass=2800.0)}
BEETLE_BLINK = (0.2, 0.05)                # s, blink period at the start and at the end of the wind-up
BEETLE_TICK_LEN = 0.006
BEETLE_TICK_BAND = (3000.0, 1800.0)
BEETLE_TICK_GAIN = 0.35                   # relative to the buzz's peak
BEETLE_INTAKE_LEN = 0.07                  # s, the breath in at the very end
BEETLE_INTAKE_BAND = (1800.0, 1600.0)
BEETLE_INTAKE_GAIN = 0.5
BEETLE_WINDUP_RMS_DB = -19.4
# Burst (beetle_pop): a voiced plosive "pok!" and a short steam hiss after it; smaller than the explosion (no sub, no boom).
BEETLE_POP_SEED = 6805
BEETLE_POP_DURATION = 0.36
BEETLE_POP_VOICE = Voice(6806, 0.12, (300.0, 170.0), FORMANTS_O, FORMANTS_UH, -18.0, asp_len=0.006, asp_level=0.9,
                         attack=0.003, tau=0.040, release=0.020, jitter=0.05, rough=0.5, rough_start=0.3, lowpass=2400.0)
BEETLE_HISS_START = 0.03                  # s
BEETLE_HISS_BAND = (4200.0, 3000.0)       # steam
BEETLE_HISS_ATTACK = 0.012
BEETLE_HISS_TAU = 0.10
BEETLE_HISS_GAIN = 0.45                   # relative to the pop's peak
BEETLE_POP_RMS_DB = -18.0


def _add_tick(rng, out, start, length, band, level):
    tick = bandpass(noise(rng, length), band[0] * rng.uniform(0.85, 1.15), band[1])
    tp = max(abs(v) for v in tick) or 1.0
    for j in range(min(length, len(out) - start)):
        out[start + j] += level * tick[j] / tp * math.sin(math.pi * j / length)


def beetle_notice() -> list[float]:
    rng = random.Random(BEETLE_NOTICE_SEED)
    out = [0.0] * n_samples(BEETLE_NOTICE_DURATION)
    chirp = render_voice(BEETLE_CHIRP_VOICE)
    start = n_samples(BEETLE_CHIRP_START)
    for i, v in enumerate(chirp[:len(out) - start]):
        out[start + i] += v
    for t in BEETLE_CLICK_TIMES:
        _add_tick(rng, out, n_samples(t), n_samples(BEETLE_CLICK_LEN), BEETLE_CLICK_BAND, BEETLE_CLICK_GAIN)
    return finish_rms(out, BEETLE_NOTICE_RMS_DB)


def _beetle_windup(seconds: float) -> list[float]:
    voice = BEETLE_WINDUP_VOICES[seconds]
    out = render_voice(voice)
    rng = random.Random(voice.seed)
    t, slow, fast = 0.0, *BEETLE_BLINK
    while True:   # one tick per blink, the period shrinking with the wind-up as Beetle's blink does
        t += slow + (fast - slow) * (t / seconds)
        if t >= seconds - BEETLE_INTAKE_LEN:
            break
        _add_tick(rng, out, n_samples(t), n_samples(BEETLE_TICK_LEN), BEETLE_TICK_BAND, BEETLE_TICK_GAIN)
    ni = n_samples(BEETLE_INTAKE_LEN)
    intake = bandpass(noise(rng, ni), *BEETLE_INTAKE_BAND)
    ip = max(abs(v) for v in intake) or 1.0
    base = len(out) - ni
    for j in range(ni):
        out[base + j] += BEETLE_INTAKE_GAIN * intake[j] / ip * (j / ni)
    return finish_rms(out, BEETLE_WINDUP_RMS_DB)


def beetle_windup() -> list[float]:
    return _beetle_windup(0.8)


def beetle_windup_long() -> list[float]:
    return _beetle_windup(1.2)


def beetle_pop() -> list[float]:
    rng = random.Random(BEETLE_POP_SEED)
    out = [0.0] * n_samples(BEETLE_POP_DURATION)
    pop = render_voice(BEETLE_POP_VOICE)
    for i, v in enumerate(pop[:len(out)]):
        out[i] += v
    start = n_samples(BEETLE_HISS_START)
    nh = len(out) - start
    hiss = bandpass(noise(rng, nh), *BEETLE_HISS_BAND)
    hp = max(abs(v) for v in hiss) or 1.0
    env = envelope(nh, BEETLE_HISS_ATTACK, BEETLE_HISS_TAU)
    for j in range(nh):
        out[start + j] += BEETLE_HISS_GAIN * hiss[j] / hp * env[j]
    return finish_rms(out, BEETLE_POP_RMS_DB)


# ---------------------------------------------------------------- monster bodies (D101)
# Shared by every monster's body; the rocking is silent. Environment sounds, like the fizzle: peak-capped at -9 dBFS.
# Crumble (remains_crumble): a dry crunch, a quick run of low-mid ticks that thin out, over a short gritty rustle.
REMAINS_CRUMBLE_SEED = 6807
REMAINS_CRUMBLE_DURATION = 0.28
REMAINS_CRUMBLE_TICKS = 11
REMAINS_CRUMBLE_START = 0.006             # s, the first tick, after the rustle's swell (no click)
REMAINS_CRUMBLE_SPAN = 0.16               # s, the ticks fall in this first part, denser at the start
REMAINS_CRUMBLE_TICK_LEN = 0.007
REMAINS_CRUMBLE_TICK_BAND = (1700.0, 1400.0)
REMAINS_CRUMBLE_TICK_GAIN = (0.5, 1.0)
REMAINS_CRUMBLE_RUSTLE_BAND = (900.0, 900.0)
REMAINS_CRUMBLE_RUSTLE_ATTACK = 0.004
REMAINS_CRUMBLE_RUSTLE_TAU = 0.07
REMAINS_CRUMBLE_RUSTLE_GAIN = 0.35
REMAINS_CRUMBLE_RMS_DB = -27.0            # peak-limited by the -9 dBFS cap: the ticks have a high crest
# Burn (remains_burn): the fizzle's family, warmer and fuller: a hiss that flares and cools, with crackles all through.
REMAINS_BURN_SEED = 6808
REMAINS_BURN_DURATION = 0.4
REMAINS_BURN_SWEEP = (1900.0, 700.0)      # Hz, band-pass centre from start to end: lower than the fireball's fizzle
REMAINS_BURN_BANDWIDTH = 1400.0
REMAINS_BURN_ATTACK = 0.03                # s, the flare
REMAINS_BURN_TAU = 0.16
REMAINS_BURN_DRIVE = 1.6
REMAINS_BURN_CRACKLES = 9
REMAINS_BURN_CRACKLE_SPAN = 0.8
REMAINS_BURN_RMS_DB = -25.0
REMAINS_PEAK_DB = -9.0                    # the environment cap (test_ambient_levels)


def remains_crumble() -> list[float]:
    rng = random.Random(REMAINS_CRUMBLE_SEED)
    n = n_samples(REMAINS_CRUMBLE_DURATION)
    rustle = bandpass(noise(rng, n), *REMAINS_CRUMBLE_RUSTLE_BAND)
    rp = max(abs(v) for v in rustle) or 1.0
    env = envelope(n, REMAINS_CRUMBLE_RUSTLE_ATTACK, REMAINS_CRUMBLE_RUSTLE_TAU)
    out = [REMAINS_CRUMBLE_RUSTLE_GAIN * v / rp * e for v, e in zip(rustle, env)]
    for k in range(REMAINS_CRUMBLE_TICKS):
        t = REMAINS_CRUMBLE_START + REMAINS_CRUMBLE_SPAN * (k / REMAINS_CRUMBLE_TICKS) ** 1.6 * rng.uniform(0.9, 1.1)
        level = rng.uniform(*REMAINS_CRUMBLE_TICK_GAIN) * (1 - 0.6 * k / REMAINS_CRUMBLE_TICKS)
        _add_tick(rng, out, n_samples(t), n_samples(REMAINS_CRUMBLE_TICK_LEN), REMAINS_CRUMBLE_TICK_BAND, level)
    return finish_rms(out, REMAINS_CRUMBLE_RMS_DB, REMAINS_PEAK_DB)


def remains_burn() -> list[float]:
    rng = random.Random(REMAINS_BURN_SEED)
    n = n_samples(REMAINS_BURN_DURATION)
    hi, lo = REMAINS_BURN_SWEEP
    hiss = bandpass(noise(rng, n), lambda t: hi + (lo - hi) * t, REMAINS_BURN_BANDWIDTH)
    peak = max(abs(v) for v in hiss) or 1.0
    env = envelope(n, REMAINS_BURN_ATTACK, REMAINS_BURN_TAU)
    out = [math.tanh(REMAINS_BURN_DRIVE * v / peak) * e for v, e in zip(hiss, env)]
    nc = n_samples(EMBER_CRACKLE_LEN)
    for _ in range(REMAINS_BURN_CRACKLES):
        start = rng.randrange(n_samples(REMAINS_BURN_ATTACK), int(n * REMAINS_BURN_CRACKLE_SPAN))
        _add_tick(rng, out, start, nc, EMBER_CRACKLE_BAND, rng.uniform(*EMBER_CRACKLE_GAIN))
    return finish_rms(lowpass(out, FIREBALL_FIZZLE_LOWPASS_HZ), REMAINS_BURN_RMS_DB, REMAINS_PEAK_DB)


# ---------------------------------------------------------------- forest spider (#328, D102)
# Notice (spider_notice): the spider's own call (no shared alert sound, D100): four quick fang ticks climbing in pitch,
# then a short breathy "tss" over a whispered, fried /i/. Not the beetle's two clicks and voiced chirp.
SPIDER_NOTICE_SEED = 6901
SPIDER_NOTICE_DURATION = 0.26
SPIDER_TICK_TIMES = (0.012, 0.034, 0.054, 0.072)  # s, the four fang ticks, closing up
SPIDER_TICK_LEN = 0.006                           # s, one tick
SPIDER_TICK_BANDS = ((2600.0, 1600.0), (3000.0, 1600.0), (3500.0, 1800.0), (4100.0, 2000.0))  # rising
SPIDER_TICK_GAIN = 0.8                            # relative to the hiss's peak
SPIDER_HISS_START = 0.085                         # s
SPIDER_HISS_BAND = (4800.0, 3200.0)               # "tss"
SPIDER_HISS_ATTACK = 0.018
SPIDER_HISS_TAU = 0.06
SPIDER_HISS_GAIN = 0.7
SPIDER_WHISPER_VOICE = Voice(6902, 0.15, (230.0, 200.0), FORMANTS_I, FORMANTS_I, -19.0, asp_len=0.030, asp_level=1.0,
                             attack=0.015, tau=0.06, release=0.020, jitter=0.08, rough=1.0, rough_start=0.0, lowpass=3400.0)
SPIDER_WHISPER_GAIN = 0.45
SPIDER_NOTICE_RMS_DB = -19.0
# Wind-up (spider_windup 0.5 s, spider_windup_long 0.75 s with the assist): noise-led, so it never sounds like the
# beetle's voiced whine or hive_buzz: a hiss that tightens (its band climbs) over a low fried growl, a fang rattle that
# speeds up, and a sharp breath in over the last 60 ms. Played once, as long as the wind-up; it runs to its end even if
# the spider is killed in the wind-up.
SPIDER_WINDUP_VOICES = {0.5: Voice(6903, 0.50, (85.0, 130.0), FORMANTS_UH, FORMANTS_E, -19.4, asp_len=0.020, asp_level=0.3,
                                   attack=0.060, tau=3.0, release=0.020, jitter=0.10, rough=1.0, rough_start=0.0,
                                   lowpass=1800.0),
                        0.75: Voice(6904, 0.75, (85.0, 130.0), FORMANTS_UH, FORMANTS_E, -19.4, asp_len=0.020, asp_level=0.3,
                                    attack=0.080, tau=4.5, release=0.020, jitter=0.10, rough=1.0, rough_start=0.0,
                                    lowpass=1800.0)}
SPIDER_WINDUP_HISS = (1400.0, 3400.0)             # Hz, band-pass centre from start to end: tightening
SPIDER_WINDUP_HISS_BW = 1600.0
SPIDER_WINDUP_HISS_GAIN = 0.9                     # relative to the growl's peak
SPIDER_WINDUP_GROWL_GAIN = 0.55
SPIDER_RATTLE = (0.09, 0.03)                      # s, rattle period at the start and at the end
SPIDER_RATTLE_LEN = 0.005
SPIDER_RATTLE_BAND = (3200.0, 2000.0)
SPIDER_RATTLE_GAIN = 0.45
SPIDER_INTAKE_LEN = 0.06                          # s, the breath in just before the leap
SPIDER_INTAKE_BAND = (2400.0, 1800.0)
SPIDER_INTAKE_GAIN = 0.6
SPIDER_WINDUP_RMS_DB = -19.4
# Leap (spider_leap): a short swish at take-off, noise sweeping up; quiet, like the bramble's lash.
SPIDER_LEAP_SEED = 6905
SPIDER_LEAP_DURATION = 0.16
SPIDER_LEAP_SWEEP = (1200.0, 4200.0)              # Hz, band-pass centre from start to end
SPIDER_LEAP_BW = 1800.0
SPIDER_LEAP_ATTACK = 0.025
SPIDER_LEAP_TAU = 0.08
SPIDER_LEAP_DRIVE = 2.5                           # tanh drive: a dense, low-crest swish
SPIDER_LEAP_RMS_DB = -21.0
# Bite (spider_bite): at the landing, hit or miss (D93): a hard double fang snap "tch-tchk" over a short dull /u/ thud.
SPIDER_BITE_SEED = 6906
SPIDER_BITE_DURATION = 0.20
SPIDER_SNAP_TIMES = (0.012, 0.040)                # s
SPIDER_SNAP_LEN = 0.008
SPIDER_SNAP_BAND = (2400.0, 2200.0)
SPIDER_SNAP_GAIN = (0.65, 0.8)                    # the second snap is the harder one; kept under the thud's peak
SPIDER_THUD_VOICE = Voice(6907, 0.10, (170.0, 110.0), FORMANTS_U, FORMANTS_U, -18.0, asp_len=0.006, asp_level=0.5,
                          attack=0.010, tau=0.060, release=0.020, jitter=0.05, rough=0.6, rough_start=0.2, lowpass=1600.0)
SPIDER_THUD_START = 0.030
SPIDER_THUD_GAIN = 1.0
SPIDER_BITE_RMS_DB = -18.0
# Drop (spider_drop, #483, D103): a fast falling "zzzip" of silk paying out: noise through a band-pass that sweeps down,
# chopped by a fast flutter (the spool's zipper) that slows as it falls, after a tiny tick at the let-go. Not the leap's
# swish, which sweeps up, is airy and has no flutter.
SPIDER_DROP_SEED = 6908
SPIDER_DROP_DURATION = 0.25
SPIDER_DROP_SWEEP = (4200.0, 900.0)               # Hz, band-pass centre from start to end: falling
SPIDER_DROP_BW = 1400.0
SPIDER_DROP_FLUTTER = (90.0, 45.0)                # Hz, the zipper's rate at the start and at the end
SPIDER_DROP_FLUTTER_DEPTH = 0.45
SPIDER_DROP_ATTACK = 0.012
SPIDER_DROP_TAU = 0.16
SPIDER_DROP_TICK_LEN = 0.005
SPIDER_DROP_TICK_BAND = (3800.0, 2000.0)
SPIDER_DROP_TICK_GAIN = 0.5
SPIDER_DROP_DRIVE = 3.5
SPIDER_DROP_RMS_DB = -21.0
# Reel (spider_reel): a soft rising ratchet of ten dry ticks as it climbs back up its thread, harmless, so quiet.
SPIDER_REEL_SEED = 6909
SPIDER_REEL_DURATION = 0.30
SPIDER_REEL_TICKS = 10
SPIDER_REEL_FIRST = 0.012                         # s, the first tick
SPIDER_REEL_GAP = 0.028                           # s between ticks
SPIDER_REEL_TICK_LEN = 0.014
SPIDER_REEL_BAND = ((1600.0, 1200.0), (3600.0, 1800.0))  # the first and the last tick's band: rising
SPIDER_REEL_GAIN = (0.5, 0.9)                     # the first and the last tick's level
SPIDER_REEL_RMS_DB = -27.0
# Snap (spider_snap): the thread breaking when its spider is killed off the floor: a tiny high pluck (a damped tone over a
# short noise transient), played with the kill sound.
SPIDER_SNAP_SEED = 6910
SPIDER_SNAP_DURATION = 0.12
SPIDER_PLUCK_HZ = 2300.0
SPIDER_PLUCK_TAU = 0.05
SPIDER_PLUCK_ATTACK = 0.004
SPIDER_SNAP_DELAY = 0.003                         # s of silence first, so the pluck starts on a zero, not a click
SPIDER_PLUCK_NOISE_LEN = 0.004
SPIDER_PLUCK_NOISE_BAND = (5200.0, 3000.0)
SPIDER_PLUCK_NOISE_GAIN = 0.6
SPIDER_SNAP_RMS_DB = -24.0


def _add_at(out, start, x, gain):
    for i, v in enumerate(x[:max(0, len(out) - start)]):
        out[start + i] += gain * v


def spider_notice() -> list[float]:
    rng = random.Random(SPIDER_NOTICE_SEED)
    out = [0.0] * n_samples(SPIDER_NOTICE_DURATION)
    start = n_samples(SPIDER_HISS_START)
    nh = len(out) - start
    hiss = bandpass(noise(rng, nh), *SPIDER_HISS_BAND)
    hp = max(abs(v) for v in hiss) or 1.0
    env = envelope(nh, SPIDER_HISS_ATTACK, SPIDER_HISS_TAU)
    _add_at(out, start, [h / hp * e for h, e in zip(hiss, env)], SPIDER_HISS_GAIN)
    _add_at(out, start, render_voice(SPIDER_WHISPER_VOICE), SPIDER_WHISPER_GAIN)
    for t, band in zip(SPIDER_TICK_TIMES, SPIDER_TICK_BANDS):
        _add_tick(rng, out, n_samples(t), n_samples(SPIDER_TICK_LEN), band, SPIDER_TICK_GAIN)
    return finish_rms(out, SPIDER_NOTICE_RMS_DB)


def _spider_windup(seconds: float) -> list[float]:
    voice = SPIDER_WINDUP_VOICES[seconds]
    growl = render_voice(voice)
    rng = random.Random(voice.seed)
    n = len(growl)
    lo, hi = SPIDER_WINDUP_HISS
    hiss = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, SPIDER_WINDUP_HISS_BW)
    hp = max(abs(v) for v in hiss) or 1.0
    env = envelope(n, voice.attack, voice.tau)
    out = [SPIDER_WINDUP_GROWL_GAIN * g + SPIDER_WINDUP_HISS_GAIN * h / hp * e for g, h, e in zip(growl, hiss, env)]
    t, slow, fast = 0.0, *SPIDER_RATTLE
    while True:   # the fang rattle speeds up through the wind-up
        t += slow + (fast - slow) * (t / seconds)
        if t >= seconds - SPIDER_INTAKE_LEN:
            break
        _add_tick(rng, out, n_samples(t), n_samples(SPIDER_RATTLE_LEN), SPIDER_RATTLE_BAND, SPIDER_RATTLE_GAIN)
    ni = n_samples(SPIDER_INTAKE_LEN)
    intake = bandpass(noise(rng, ni), *SPIDER_INTAKE_BAND)
    ip = max(abs(v) for v in intake) or 1.0
    _add_at(out, n - ni, [v / ip * (j / ni) for j, v in enumerate(intake)], SPIDER_INTAKE_GAIN)
    return finish_rms(out, SPIDER_WINDUP_RMS_DB)


def spider_windup() -> list[float]:
    return _spider_windup(0.5)


def spider_windup_long() -> list[float]:
    return _spider_windup(0.75)


def spider_leap() -> list[float]:
    rng = random.Random(SPIDER_LEAP_SEED)
    n = n_samples(SPIDER_LEAP_DURATION)
    lo, hi = SPIDER_LEAP_SWEEP
    swish = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, SPIDER_LEAP_BW)
    peak = max(abs(v) for v in swish) or 1.0
    env = envelope(n, SPIDER_LEAP_ATTACK, SPIDER_LEAP_TAU)
    return finish_rms([math.tanh(SPIDER_LEAP_DRIVE * v / peak) * e for v, e in zip(swish, env)], SPIDER_LEAP_RMS_DB,
                      REMAINS_PEAK_DB)


def spider_bite() -> list[float]:
    rng = random.Random(SPIDER_BITE_SEED)
    out = [0.0] * n_samples(SPIDER_BITE_DURATION)
    _add_at(out, n_samples(SPIDER_THUD_START), render_voice(SPIDER_THUD_VOICE), SPIDER_THUD_GAIN)
    for t, gain in zip(SPIDER_SNAP_TIMES, SPIDER_SNAP_GAIN):
        _add_tick(rng, out, n_samples(t), n_samples(SPIDER_SNAP_LEN), SPIDER_SNAP_BAND, gain)
    return finish_rms(out, SPIDER_BITE_RMS_DB)


def spider_drop() -> list[float]:
    rng = random.Random(SPIDER_DROP_SEED)
    n = n_samples(SPIDER_DROP_DURATION)
    lo, hi = SPIDER_DROP_SWEEP
    zip_noise = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, SPIDER_DROP_BW)
    peak = max(abs(v) for v in zip_noise) or 1.0
    env = envelope(n, SPIDER_DROP_ATTACK, SPIDER_DROP_TAU)
    fast, slow = SPIDER_DROP_FLUTTER
    out, phase = [], 0.0
    for i, (v, e) in enumerate(zip(zip_noise, env)):
        phase += (fast + (slow - fast) * i / n) / SAMPLE_RATE
        flutter = 1.0 - SPIDER_DROP_FLUTTER_DEPTH * (0.5 + 0.5 * math.cos(2 * math.pi * phase))
        out.append(math.tanh(SPIDER_DROP_DRIVE * v / peak) * e * flutter)
    _add_tick(rng, out, n_samples(SPIDER_DROP_ATTACK), n_samples(SPIDER_DROP_TICK_LEN), SPIDER_DROP_TICK_BAND, SPIDER_DROP_TICK_GAIN)
    return finish_rms(out, SPIDER_DROP_RMS_DB, REMAINS_PEAK_DB)


def spider_reel() -> list[float]:
    rng = random.Random(SPIDER_REEL_SEED)
    out = [0.0] * n_samples(SPIDER_REEL_DURATION)
    (lo_c, lo_w), (hi_c, hi_w) = SPIDER_REEL_BAND
    for k in range(SPIDER_REEL_TICKS):
        f = k / (SPIDER_REEL_TICKS - 1)
        band = (lo_c + (hi_c - lo_c) * f, lo_w + (hi_w - lo_w) * f)
        gain = SPIDER_REEL_GAIN[0] + (SPIDER_REEL_GAIN[1] - SPIDER_REEL_GAIN[0]) * f
        _add_tick(rng, out, n_samples(SPIDER_REEL_FIRST + k * SPIDER_REEL_GAP), n_samples(SPIDER_REEL_TICK_LEN), band, gain)
    return finish_rms(out, SPIDER_REEL_RMS_DB, REMAINS_PEAK_DB)


def spider_snap() -> list[float]:
    rng = random.Random(SPIDER_SNAP_SEED)
    n, delay = n_samples(SPIDER_SNAP_DURATION), n_samples(SPIDER_SNAP_DELAY)
    env = envelope(n - delay, SPIDER_PLUCK_ATTACK, SPIDER_PLUCK_TAU)
    out = [0.0] * delay + [math.sin(2 * math.pi * SPIDER_PLUCK_HZ * i / SAMPLE_RATE) * e for i, e in enumerate(env)]
    _add_tick(rng, out, delay + n_samples(SPIDER_PLUCK_ATTACK), n_samples(SPIDER_PLUCK_NOISE_LEN), SPIDER_PLUCK_NOISE_BAND,
              SPIDER_PLUCK_NOISE_GAIN)
    return finish_rms(out, SPIDER_SNAP_RMS_DB, REMAINS_PEAK_DB)


# ---------------------------------------------------------------- forest snail (#329, D106)
# Wet and low, so it never sounds like the spider's dry ticks, the beetle's clicks or hive_buzz. Seeds 7001 to 7009.
# Notice (snail_notice): its own call, a soft wet lip-pop and then a short nasal hum that rises like a question, "mm?".
SNAIL_NOTICE_DURATION = 0.30
SNAIL_POP_SEED = 7001
SNAIL_POP_LEN = 0.012                             # s, the lip-pop
SNAIL_POP_BAND = (650.0, 500.0)
SNAIL_POP_GAIN = 0.8
SNAIL_HUM_VOICE = Voice(7002, 0.24, (150.0, 235.0), FORMANTS_U, FORMANTS_U, -19.0, asp_len=0.010, asp_level=0.2,
                        attack=0.030, tau=0.20, release=0.040, jitter=0.03, vibrato=(9.0, 0.025), lowpass=1500.0)
SNAIL_HUM_START = 0.035
SNAIL_HUM_GAIN = 1.0
SNAIL_NOTICE_RMS_DB = -19.0
# Wind-up (snail_windup, snail_windup_long): a slow wet inhale that gurgles and swells as the throat fills, rising in pitch
# with a bubbly tremolo, and ends on a short held catch. A one-shot: once started it plays to its end.
SNAIL_WINDUP_VOICES = {0.8: Voice(7003, 0.80, (70.0, 120.0), FORMANTS_O, FORMANTS_U, -19.4, asp_len=0.030, asp_level=0.4,
                                  attack=0.25, tau=2.0, release=0.050, jitter=0.06, rough=0.5, rough_start=0.1,
                                  vibrato=(6.0, 0.04), lowpass=1400.0),
                       1.2: Voice(7004, 1.20, (70.0, 120.0), FORMANTS_O, FORMANTS_U, -19.4, asp_len=0.030, asp_level=0.4,
                                  attack=0.35, tau=3.0, release=0.050, jitter=0.06, rough=0.5, rough_start=0.1,
                                  vibrato=(6.0, 0.04), lowpass=1400.0)}
SNAIL_GURGLE_BAND = (450.0, 1100.0)               # Hz, band-pass centre from start to end: the throat filling
SNAIL_GURGLE_BW = 500.0
SNAIL_GURGLE_GAIN = 0.8                           # relative to the voice
SNAIL_VOICE_GAIN = 0.6
SNAIL_BUBBLE_RATE = (14.0, 30.0)                  # Hz, the tremolo's rate from start to end: bubbling faster
SNAIL_BUBBLE_DEPTH = 0.7
SNAIL_CATCH_LEN = 0.07                            # s, the held catch at the end: the sound closes off
SNAIL_WINDUP_RMS_DB = -19.4
# Spit (snail_spit): a sharp "ptoo": a plosive pop, a short burst of spray noise and a clipped /u/.
SNAIL_SPIT_SEED = 7005
SNAIL_SPIT_DURATION = 0.16
SNAIL_PLOSIVE_LEN = 0.010
SNAIL_PLOSIVE_BAND = (420.0, 400.0)
SNAIL_PLOSIVE_GAIN = 1.0
SNAIL_SPRAY_LEN = 0.06
SNAIL_SPRAY_BAND = (2600.0, 2400.0)
SNAIL_SPRAY_GAIN = 0.45
SNAIL_SPRAY_START = 0.006
SNAIL_TOO_VOICE = Voice(7006, 0.12, (230.0, 160.0), FORMANTS_U, FORMANTS_U, -18.0, asp_len=0.008, asp_level=0.6,
                        attack=0.006, tau=0.05, release=0.020, jitter=0.04, lowpass=1800.0)
SNAIL_TOO_START = 0.018
SNAIL_TOO_GAIN = 0.9
SNAIL_SPIT_RMS_DB = -18.0
# Splat (snail_splat): wherever a gob ends, hit or miss (D93): a wet slap, a low thud with a short crackle of droplets.
SNAIL_SPLAT_SEED = 7007
SNAIL_SPLAT_DURATION = 0.20
SNAIL_SLAP_VOICE = Voice(7008, 0.12, (120.0, 70.0), FORMANTS_UH, FORMANTS_U, -18.0, asp_len=0.012, asp_level=0.8,
                         attack=0.004, tau=0.05, release=0.020, jitter=0.08, rough=0.7, rough_start=0.1, lowpass=1300.0)
SNAIL_SLAP_GAIN = 1.0
SNAIL_DROPLET_TIMES = (0.030, 0.052, 0.071, 0.097, 0.128)  # s, the droplets landing, thinning out
SNAIL_DROPLET_LEN = 0.006
SNAIL_DROPLET_BAND = (1700.0, 1100.0)
SNAIL_DROPLET_GAIN = 0.35
SNAIL_SPLAT_RMS_DB = -18.0


def snail_notice() -> list[float]:
    rng = random.Random(SNAIL_POP_SEED)
    out = [0.0] * n_samples(SNAIL_NOTICE_DURATION)
    _add_at(out, n_samples(SNAIL_HUM_START), render_voice(SNAIL_HUM_VOICE), SNAIL_HUM_GAIN)
    _add_tick(rng, out, n_samples(0.004), n_samples(SNAIL_POP_LEN), SNAIL_POP_BAND, SNAIL_POP_GAIN)
    return finish_rms(out, SNAIL_NOTICE_RMS_DB)


def _snail_windup(seconds: float) -> list[float]:
    voice = SNAIL_WINDUP_VOICES[seconds]
    groan = render_voice(voice)
    rng = random.Random(voice.seed)
    n = len(groan)
    lo, hi = SNAIL_GURGLE_BAND
    gurgle = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, SNAIL_GURGLE_BW)
    gp = max(abs(v) for v in gurgle) or 1.0
    env = envelope(n, voice.attack, voice.tau)
    slow, fast = SNAIL_BUBBLE_RATE
    out, phase = [], 0.0
    for i in range(n):
        phase += (slow + (fast - slow) * i / n) / SAMPLE_RATE
        bubble = 1.0 - SNAIL_BUBBLE_DEPTH * (0.5 + 0.5 * math.cos(2 * math.pi * phase))
        out.append((SNAIL_VOICE_GAIN * groan[i] + SNAIL_GURGLE_GAIN * gurgle[i] / gp * env[i]) * bubble)
    nc = n_samples(SNAIL_CATCH_LEN)
    for j in range(nc):   # the held catch: the throat closes over the last moment
        out[n - nc + j] *= 1.0 - j / nc
    return finish_rms(out, SNAIL_WINDUP_RMS_DB)


def snail_windup() -> list[float]:
    return _snail_windup(0.8)


def snail_windup_long() -> list[float]:
    return _snail_windup(1.2)


def snail_spit() -> list[float]:
    rng = random.Random(SNAIL_SPIT_SEED)
    out = [0.0] * n_samples(SNAIL_SPIT_DURATION)
    _add_tick(rng, out, n_samples(0.003), n_samples(SNAIL_PLOSIVE_LEN), SNAIL_PLOSIVE_BAND, SNAIL_PLOSIVE_GAIN)
    ns = n_samples(SNAIL_SPRAY_LEN)
    spray = bandpass(noise(rng, ns), *SNAIL_SPRAY_BAND)
    sp = max(abs(v) for v in spray) or 1.0
    env = envelope(ns, 0.004, 0.02)
    _add_at(out, n_samples(SNAIL_SPRAY_START), [v / sp * e for v, e in zip(spray, env)], SNAIL_SPRAY_GAIN)
    _add_at(out, n_samples(SNAIL_TOO_START), render_voice(SNAIL_TOO_VOICE), SNAIL_TOO_GAIN)
    return finish_rms(out, SNAIL_SPIT_RMS_DB)


def snail_splat() -> list[float]:
    rng = random.Random(SNAIL_SPLAT_SEED)
    out = [0.0] * n_samples(SNAIL_SPLAT_DURATION)
    _add_at(out, n_samples(0.002), render_voice(SNAIL_SLAP_VOICE), SNAIL_SLAP_GAIN)
    for t in SNAIL_DROPLET_TIMES:
        _add_tick(rng, out, n_samples(t), n_samples(SNAIL_DROPLET_LEN), SNAIL_DROPLET_BAND, SNAIL_DROPLET_GAIN)
    return finish_rms(out, SNAIL_SPLAT_RMS_DB)


# ---------------------------------------------------------------- forest wolf (#331, D115)
# An animal, never a person (author, 2026-11-21: the first set "sounds like the hero"). The hero's voice set (#313) glides
# between 120 and 470 Hz through human vowels whose first formant lies at 300 to 800 Hz (FORMANTS_A/E/I/O/U/AA/UH); the boss
# goes down to 72 Hz. The wolf's voiced sounds go through a snout resonance whose first formant is 950 to 1050 Hz, above
# every human vowel; its barks are a big dog's, 230 to 380 Hz (author, 2026-11-21: 400 to 680 Hz sounded like a puppy), rough
# and with a breath through the nose, and its snarl is a pulsed, fully rough growl with a hiss through the teeth. Seeds 7301 to 7311.
WOLF_SNOUT = ((950.0, 260.0, 1.0), (2100.0, 380.0, 0.7), (3300.0, 500.0, 0.35))   # bark and yip: a big animal's long muzzle
WOLF_SNOUT_GROWL = ((1050.0, 300.0, 1.0), (2200.0, 400.0, 0.7), (3400.0, 500.0, 0.35))  # the snarl, a bared muzzle
# Notice (wolf_notice): its own call, one sharp dog-like bark: a quick rising yip at the onset, a falling bark through the
# snout, and a breath huffed out after it.
WOLF_NOTICE_DURATION = 0.22
WOLF_HUFF_SEED = 7301
WOLF_YIP_VOICE = Voice(7311, 0.035, (300.0, 380.0), WOLF_SNOUT, WOLF_SNOUT, -19.0, asp_len=0.008, asp_level=0.6,
                       attack=0.004, tau=0.05, release=0.008, jitter=0.04, rough=0.3, rough_start=0.0, lowpass=4500.0)
WOLF_YIP_START = 0.004
WOLF_YIP_GAIN = 0.7
WOLF_BARK_VOICE = Voice(7302, 0.12, (380.0, 250.0), WOLF_SNOUT, WOLF_SNOUT, -19.0, asp_len=0.006, asp_level=0.4,
                        attack=0.004, tau=0.06, release=0.025, jitter=0.06, rough=0.7, rough_start=0.0, lowpass=4500.0)
WOLF_BARK_START = 0.030
WOLF_BARK_GAIN = 1.0
WOLF_HUFF_START = 0.140
WOLF_HUFF_LEN = 0.075
WOLF_HUFF_BAND = (2200.0, 1400.0)                # the breath through the nose after the bark
WOLF_HUFF_GAIN = 0.35
WOLF_NOTICE_RMS_DB = -19.0
# Wind-up (wolf_windup, wolf_windup_long): a rising snarl: a fully rough growl climbing 240 to 330 Hz through the closed
# snout, chopped into growl pulses that quicken, under a hiss through bared teeth that grows faster than the growl, ending on a
# held catch. Exactly the bite warning, 0.5 s and 0.75 s, so it ends with the lunge. A one-shot: it plays to its end.
WOLF_WINDUP_VOICES = {0.5: Voice(7303, 0.50, (240.0, 330.0), WOLF_SNOUT_GROWL, WOLF_SNOUT_GROWL, -19.4, asp_len=0.020,
                                 asp_level=0.3, attack=0.30, tau=2.0, release=0.020, jitter=0.12, rough=1.0, rough_start=0.0,
                                 lowpass=2600.0),
                      0.75: Voice(7304, 0.75, (240.0, 330.0), WOLF_SNOUT_GROWL, WOLF_SNOUT_GROWL, -19.4, asp_len=0.020,
                                  asp_level=0.3, attack=0.45, tau=3.0, release=0.020, jitter=0.12, rough=1.0, rough_start=0.0,
                                  lowpass=2600.0)}
WOLF_GROWL_PULSE = (28.0, 40.0)                  # Hz, the growl's pulse rate from start to end
WOLF_GROWL_PULSE_DEPTH = 0.6
WOLF_HISS_BAND = (2400.0, 4200.0)                # Hz, band-pass centre from start to end: the teeth baring
WOLF_HISS_BW = 1200.0
WOLF_HISS_GAIN = 0.7                             # relative to the growl, at the end; it grows as the square of the time
WOLF_GROWL_GAIN = 0.8
WOLF_CATCH_LEN = 0.06                            # s, the held catch: the breath stops before the lunge
WOLF_WINDUP_RMS_DB = -19.4
# Lunge (wolf_lunge): at take-off, a claw scrabble and a hard breath out. Environment level, as spider_leap.
WOLF_LUNGE_SEED = 7305
WOLF_LUNGE_DURATION = 0.18
WOLF_SCRABBLE_TIMES = (0.004, 0.029, 0.054, 0.079)  # s, four claws on the ground
WOLF_SCRABBLE_LEN = 0.006
WOLF_SCRABBLE_BAND = (1900.0, 1000.0)
WOLF_SCRABBLE_GAIN = 0.6
WOLF_EXHALE_START = 0.055
WOLF_EXHALE_LEN = 0.12
WOLF_EXHALE_SWEEP = (1600.0, 900.0)              # Hz, band-pass centre from start to end: the breath falling
WOLF_EXHALE_BW = 800.0
WOLF_EXHALE_GAIN = 0.8
WOLF_LUNGE_DRIVE = 2.5                           # tanh drive: a dense, low-crest scrabble, as spider_leap
WOLF_LUNGE_RMS_DB = -21.0
# Bite (wolf_bite): hit or miss (D93), a heavy jaw clack, two clicks over a short rough thud through the snout.
WOLF_BITE_SEED = 7306
WOLF_BITE_DURATION = 0.18
WOLF_CLACK_TIMES = (0.010, 0.050)                # s
WOLF_CLACK_LEN = 0.008
WOLF_CLACK_BANDS = ((1600.0, 500.0), (1300.0, 500.0))
WOLF_CLACK_GAIN = (0.7, 0.85)
WOLF_THUD_VOICE = Voice(7307, 0.10, (190.0, 150.0), WOLF_SNOUT_GROWL, WOLF_SNOUT_GROWL, -18.0, asp_len=0.006, asp_level=0.4,
                        attack=0.006, tau=0.05, release=0.020, jitter=0.08, rough=0.8, rough_start=0.0, lowpass=2200.0)
WOLF_THUD_START = 0.030
WOLF_THUD_GAIN = 1.0
WOLF_BITE_RMS_DB = -18.0
# Bay (wolf_bay): at a refuge or its range end, harmless: a frustrated double bark through the snout, the second lower and
# shorter. Quieter than the notice (environment level), so it never reads as a new threat.
WOLF_BAY_DURATION = 0.40
WOLF_BAY_VOICES = (Voice(7308, 0.12, (340.0, 260.0), WOLF_SNOUT, WOLF_SNOUT, -22.0, asp_len=0.008, asp_level=0.5,
                         attack=0.005, tau=0.06, release=0.025, jitter=0.06, rough=0.6, rough_start=0.0, lowpass=4500.0),
                   Voice(7309, 0.10, (300.0, 230.0), WOLF_SNOUT, WOLF_SNOUT, -22.0, asp_len=0.008, asp_level=0.5,
                         attack=0.005, tau=0.05, release=0.025, jitter=0.06, rough=0.6, rough_start=0.0, lowpass=4000.0))
WOLF_BAY_STARTS = (0.010, 0.220)
WOLF_BAY_GAINS = (1.0, 0.75)
WOLF_BAY_RMS_DB = -22.0
# Run (wolf_run): one per gallop cycle while it sprints, in view only: a soft paw patter, a muffled double thud and a faint
# breath. The quietest sound of the set (ambient), under every warning.
WOLF_RUN_SEED = 7310
WOLF_RUN_DURATION = 0.12
WOLF_PAW_TIMES = (0.004, 0.034)                  # s, the two paws
WOLF_PAW_LEN = 0.030
WOLF_PAW_BAND = (110.0, 80.0)
WOLF_PAW_GAIN = 1.0
WOLF_RUN_BREATH_BAND = (1200.0, 700.0)
WOLF_RUN_BREATH_GAIN = 0.35
WOLF_RUN_RMS_DB = -30.0


def wolf_notice() -> list[float]:
    rng = random.Random(WOLF_HUFF_SEED)
    out = [0.0] * n_samples(WOLF_NOTICE_DURATION)
    _add_at(out, n_samples(WOLF_YIP_START), render_voice(WOLF_YIP_VOICE), WOLF_YIP_GAIN)
    _add_at(out, n_samples(WOLF_BARK_START), render_voice(WOLF_BARK_VOICE), WOLF_BARK_GAIN)
    _add_tick(rng, out, n_samples(WOLF_HUFF_START), n_samples(WOLF_HUFF_LEN), WOLF_HUFF_BAND, WOLF_HUFF_GAIN)
    return finish_rms(out, WOLF_NOTICE_RMS_DB)


def _wolf_windup(seconds: float) -> list[float]:
    voice = WOLF_WINDUP_VOICES[seconds]
    growl = render_voice(voice)
    rng = random.Random(voice.seed)
    n = len(growl)
    lo, hi = WOLF_HISS_BAND
    hiss = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, WOLF_HISS_BW)
    hp = max(abs(v) for v in hiss) or 1.0
    slow, fast = WOLF_GROWL_PULSE
    out, phase = [], 0.0
    for i in range(n):
        phase += (slow + (fast - slow) * i / n) / SAMPLE_RATE
        pulse = 1.0 - WOLF_GROWL_PULSE_DEPTH * (0.5 + 0.5 * math.cos(2 * math.pi * phase))
        out.append(WOLF_GROWL_GAIN * growl[i] * pulse + WOLF_HISS_GAIN * hiss[i] / hp * (i / n) ** 2)
    nc = n_samples(WOLF_CATCH_LEN)
    for j in range(nc):   # the held catch: the breath stops before the lunge
        out[n - nc + j] *= 1.0 - j / nc
    return finish_rms(out, WOLF_WINDUP_RMS_DB)


def wolf_windup() -> list[float]:
    return _wolf_windup(0.5)


def wolf_windup_long() -> list[float]:
    return _wolf_windup(0.75)


def wolf_lunge() -> list[float]:
    rng = random.Random(WOLF_LUNGE_SEED)
    out = [0.0] * n_samples(WOLF_LUNGE_DURATION)
    for t in WOLF_SCRABBLE_TIMES:
        _add_tick(rng, out, n_samples(t), n_samples(WOLF_SCRABBLE_LEN), WOLF_SCRABBLE_BAND, WOLF_SCRABBLE_GAIN)
    ne = n_samples(WOLF_EXHALE_LEN)
    lo, hi = WOLF_EXHALE_SWEEP
    breath = bandpass(noise(rng, ne), lambda t: lo + (hi - lo) * t, WOLF_EXHALE_BW)
    bp = max(abs(v) for v in breath) or 1.0
    env = envelope(ne, 0.010, 0.05)
    _add_at(out, n_samples(WOLF_EXHALE_START), [v / bp * e for v, e in zip(breath, env)], WOLF_EXHALE_GAIN)
    peak = max(abs(v) for v in out) or 1.0
    return finish_rms([math.tanh(WOLF_LUNGE_DRIVE * v / peak) for v in out], WOLF_LUNGE_RMS_DB, REMAINS_PEAK_DB)


def wolf_bite() -> list[float]:
    rng = random.Random(WOLF_BITE_SEED)
    out = [0.0] * n_samples(WOLF_BITE_DURATION)
    _add_at(out, n_samples(WOLF_THUD_START), render_voice(WOLF_THUD_VOICE), WOLF_THUD_GAIN)
    for t, band, gain in zip(WOLF_CLACK_TIMES, WOLF_CLACK_BANDS, WOLF_CLACK_GAIN):
        _add_tick(rng, out, n_samples(t), n_samples(WOLF_CLACK_LEN), band, gain)
    return finish_rms(out, WOLF_BITE_RMS_DB)


def wolf_bay() -> list[float]:
    out = [0.0] * n_samples(WOLF_BAY_DURATION)
    for voice, start, gain in zip(WOLF_BAY_VOICES, WOLF_BAY_STARTS, WOLF_BAY_GAINS):
        _add_at(out, n_samples(start), render_voice(voice), gain)
    return finish_rms(out, WOLF_BAY_RMS_DB, REMAINS_PEAK_DB)


def wolf_run() -> list[float]:
    rng = random.Random(WOLF_RUN_SEED)
    n = n_samples(WOLF_RUN_DURATION)
    out = [0.0] * n
    for t in WOLF_PAW_TIMES:
        _add_tick(rng, out, n_samples(t), n_samples(WOLF_PAW_LEN), WOLF_PAW_BAND, WOLF_PAW_GAIN)
    breath = bandpass(noise(rng, n), *WOLF_RUN_BREATH_BAND)
    bp = max(abs(v) for v in breath) or 1.0
    env = envelope(n, 0.020, 0.08)
    _add_at(out, 0, [v / bp * e for v, e in zip(breath, env)], WOLF_RUN_BREATH_GAIN)
    return finish_rms(out, WOLF_RUN_RMS_DB, REMAINS_PEAK_DB)


# ---------------------------------------------------------------- RedMob whip and boss sweeps (#447, D93)
# Consequences: the strike is heard where it lands, hit or miss. whip_crack plays at the start of the RedMob's strike frame;
# boss_sweep at the start of the boss's normal sweep frame, boss_sweep_counter at the start of the counter strike.
# whip_crack: a short rising swish of the lash, then the crack (a hard burst of high noise, a sharp 3 ms ring), a dry tail.
WHIP_SEED = 7101
WHIP_DURATION = 0.16
WHIP_SWISH_LEN = 0.034       # s, the lash cutting the air before the crack
WHIP_SWISH_SWEEP = (1500.0, 4500.0)
WHIP_SWISH_BW = 2200.0
WHIP_SWISH_GAIN = 0.35       # relative to the crack's peak
WHIP_CRACK_BAND = (3600.0, 3800.0)
WHIP_CRACK_LEN = 0.12
WHIP_CRACK_ATTACK = 0.0015
WHIP_CRACK_TAU = 0.022
WHIP_CRACK_DRIVE = 3.0
WHIP_RMS_DB = -18.0
WHIP_PEAK_DB = -3.0
# boss_sweep: a heavy axe whoosh, noise through a low wide band-pass rising 450 -> 1800 Hz, swelling to the strike, driven.
BOSS_SWEEP_SEED = 7102
BOSS_SWEEP_DURATION = 0.30
BOSS_SWEEP_SWEEP = (450.0, 1800.0)
BOSS_SWEEP_BW = 1000.0
BOSS_SWEEP_ATTACK = 0.085
BOSS_SWEEP_TAU = 0.11
BOSS_SWEEP_DRIVE = 2.5
BOSS_SWEEP_RMS_DB = -18.0
BOSS_SWEEP_PEAK_DB = -3.0
# boss_sweep_counter: the same whoosh, lower and longer (300 -> 1200 Hz, 0.40 s), then a dull /u/ thud of the axe's weight.
BOSS_COUNTER_SEED = 7103
BOSS_COUNTER_DURATION = 0.40
BOSS_COUNTER_SWEEP = (300.0, 1200.0)
BOSS_COUNTER_BW = 800.0
BOSS_COUNTER_ATTACK = 0.10
BOSS_COUNTER_TAU = 0.15
BOSS_COUNTER_DRIVE = 2.5
BOSS_COUNTER_THUD_VOICE = Voice(7104, 0.16, (110.0, 62.0), FORMANTS_U, FORMANTS_U, -18.0, asp_len=0.006, asp_level=0.5,
                                attack=0.010, tau=0.090, release=0.030, jitter=0.05, rough=0.7, rough_start=0.2, lowpass=1200.0)
BOSS_COUNTER_THUD_START = 0.17   # s
BOSS_COUNTER_THUD_GAIN = 1.1     # relative to the whoosh's peak
BOSS_COUNTER_RMS_DB = -18.0


def whip_crack() -> list[float]:
    rng = random.Random(WHIP_SEED)
    n = n_samples(WHIP_DURATION)
    out = [0.0] * n
    ns = n_samples(WHIP_SWISH_LEN)
    lo, hi = WHIP_SWISH_SWEEP
    swish = bandpass(noise(rng, ns), lambda t: lo + (hi - lo) * t, WHIP_SWISH_BW)
    sp = max(abs(v) for v in swish) or 1.0
    for j in range(ns):
        out[j] += WHIP_SWISH_GAIN * swish[j] / sp * (j / ns) ** 2
    nc = n_samples(WHIP_CRACK_LEN)
    crack = bandpass(noise(rng, nc), WHIP_CRACK_BAND[0], WHIP_CRACK_BAND[1])
    cp = max(abs(v) for v in crack) or 1.0
    env = envelope(nc, WHIP_CRACK_ATTACK, WHIP_CRACK_TAU)
    for j in range(nc):
        if ns + j < n:
            out[ns + j] += math.tanh(WHIP_CRACK_DRIVE * crack[j] / cp) * env[j]
    return finish_rms(out, WHIP_RMS_DB, WHIP_PEAK_DB)


def _axe_whoosh(seed, duration, sweep, bw, attack, tau, drive) -> list[float]:
    rng = random.Random(seed)
    n = n_samples(duration)
    lo, hi = sweep
    swept = bandpass(noise(rng, n), lambda t: lo + (hi - lo) * t, bw)
    peak = max(abs(v) for v in swept) or 1.0
    env = envelope(n, attack, tau)
    return [math.tanh(drive * v / peak) * e for v, e in zip(swept, env)]


def boss_sweep() -> list[float]:
    x = _axe_whoosh(BOSS_SWEEP_SEED, BOSS_SWEEP_DURATION, BOSS_SWEEP_SWEEP, BOSS_SWEEP_BW, BOSS_SWEEP_ATTACK,
                    BOSS_SWEEP_TAU, BOSS_SWEEP_DRIVE)
    return finish_rms(x, BOSS_SWEEP_RMS_DB, BOSS_SWEEP_PEAK_DB)


def boss_sweep_counter() -> list[float]:
    x = _axe_whoosh(BOSS_COUNTER_SEED, BOSS_COUNTER_DURATION, BOSS_COUNTER_SWEEP, BOSS_COUNTER_BW, BOSS_COUNTER_ATTACK,
                    BOSS_COUNTER_TAU, BOSS_COUNTER_DRIVE)
    peak = max(abs(v) for v in x) or 1.0
    _add_at(x, n_samples(BOSS_COUNTER_THUD_START), render_voice(BOSS_COUNTER_THUD_VOICE), BOSS_COUNTER_THUD_GAIN * peak)
    return finish_rms(x, BOSS_COUNTER_RMS_DB, BOSS_SWEEP_PEAK_DB)


# Spore puff (spore_puff): the puffball's release and both honey fungi's burst (#445). One sound for all three: the harmless and the
# false fungus must not be told apart by ear (D63). A soft "fwuff": a muffled thump of the cap giving way under a breath of
# airy noise that brightens as the cloud spreads. Quieter and longer than dust_puff, so it never reads as the hero's landing.
SPORE_PUFF_SEED = 7201
SPORE_PUFF_DURATION = 0.30
SPORE_THUMP_VOICE = Voice(7202, 0.10, (110.0, 70.0), FORMANTS_UH, FORMANTS_U, -18.0, asp_len=0.010, asp_level=0.5,
                          attack=0.006, tau=0.04, release=0.020, jitter=0.06, rough=0.5, rough_start=0.1, lowpass=700.0)
SPORE_THUMP_GAIN = 0.3
SPORE_AIR_BANDS = ((500.0, 700.0), (1800.0, 1600.0))  # Hz, (centre, bandwidth): the dull first breath, then the bright spread
SPORE_AIR_ATTACKS = (0.012, 0.050)                    # s, soft onsets: never a click
SPORE_AIR_TAUS = (0.050, 0.075)
SPORE_AIR_GAINS = (1.0, 0.55)
SPORE_PUFF_RMS_DB = -27.1
SPORE_PUFF_PEAK_DB = -9.0                      # the environment cap (test_ambient_levels)


def spore_puff() -> list[float]:
    rng = random.Random(SPORE_PUFF_SEED)
    n = n_samples(SPORE_PUFF_DURATION)
    out = [0.0] * n
    raw = noise(rng, n)
    for (fc, bw), attack, tau, gain in zip(SPORE_AIR_BANDS, SPORE_AIR_ATTACKS, SPORE_AIR_TAUS, SPORE_AIR_GAINS):
        air = bandpass(raw, fc, bw)
        pk = max(abs(v) for v in air) or 1.0
        env = envelope(n, attack, tau)
        for i in range(n):
            out[i] += gain * air[i] / pk * env[i]
    _add_at(out, n_samples(0.002), render_voice(SPORE_THUMP_VOICE), SPORE_THUMP_GAIN)
    return finish_rms(out, SPORE_PUFF_RMS_DB, SPORE_PUFF_PEAK_DB)


# Puffball burst (puffball_burst): the spore puffball's release (#558), its own sound now; spore_puff stays the honey fungi's (D63
# pair). A wet "p-KHHHH": the sac tears with a short low pop, the spores jet out of the chimney as a hiss that rises and
# rattles (noise through a band sweeping up, chopped by a slow flutter so it is a grainy spray, not a clean wind), over a low,
# rough wheeze for the sac emptying. Louder and harsher than spore_puff: it is a lethal act, in the bramble lash's tier.
PUFFBALL_BURST_SEED = 7401
PUFFBALL_BURST_DURATION = 0.45
PUFFBALL_POP_VOICE = Voice(7402, 0.07, (150.0, 90.0), FORMANTS_U, FORMANTS_UH, -18.0, asp_len=0.006, asp_level=0.4,
                           attack=0.003, tau=0.025, release=0.012, jitter=0.08, rough=0.6, rough_start=0.2, lowpass=900.0)
PUFFBALL_POP_GAIN = 0.6
PUFFBALL_JET_START = 0.012                    # s, the jet starts as the pop tears
PUFFBALL_JET_SWEEP = (900.0, 3400.0)          # Hz, band centre from, to: the spray rising out of the chimney
PUFFBALL_JET_BW = (700.0, 1800.0)             # Hz, bandwidth from, to: it widens as it spreads
PUFFBALL_JET_ATTACK = 0.015                   # s
PUFFBALL_JET_TAU = 0.13                       # s
PUFFBALL_JET_FLUTTER_HZ = 34.0                # the rattle of the spores
PUFFBALL_JET_FLUTTER_DEPTH = 0.55             # 0 = no rattle, 1 = fully gated
PUFFBALL_JET_DRIVE = 2.2
PUFFBALL_JET_GAIN = 1.0
PUFFBALL_WHEEZE_VOICE = Voice(7403, 0.30, (95.0, 62.0), FORMANTS_AA, FORMANTS_U, -18.0, asp_len=0.05, asp_level=1.2,
                              attack=0.04, tau=0.12, release=0.05, jitter=0.07, rough=0.9, rough_start=0.0, lowpass=1400.0)
PUFFBALL_WHEEZE_START = 0.05                  # s
PUFFBALL_WHEEZE_GAIN = 0.25
PUFFBALL_BURST_LOWPASS_HZ = 6000.0
PUFFBALL_BURST_DRIVE = 2.0                    # a final tanh: lowers the crest factor so the RMS is not peak-limited
PUFFBALL_BURST_RMS_DB = -21.5
PUFFBALL_BURST_PEAK_DB = -9.0                 # the environment cap (test_ambient_levels)


def puffball_burst() -> list[float]:
    rng = random.Random(PUFFBALL_BURST_SEED)
    n = n_samples(PUFFBALL_BURST_DURATION)
    out = [0.0] * n
    nj = n - n_samples(PUFFBALL_JET_START)
    (f0, f1), (b0, b1) = PUFFBALL_JET_SWEEP, PUFFBALL_JET_BW
    jet = bandpass(noise(rng, nj), lambda t: f0 + (f1 - f0) * t, lambda t: b0 + (b1 - b0) * t)
    peak = max(abs(v) for v in jet) or 1.0
    env = envelope(nj, PUFFBALL_JET_ATTACK, PUFFBALL_JET_TAU)
    for i in range(nj):
        gate = 1.0 - PUFFBALL_JET_FLUTTER_DEPTH * (0.5 + 0.5 * math.sin(2 * math.pi * PUFFBALL_JET_FLUTTER_HZ * i / SAMPLE_RATE))
        jet[i] = math.tanh(PUFFBALL_JET_DRIVE * jet[i] / peak * gate) * env[i] * PUFFBALL_JET_GAIN
    _add_at(out, n_samples(PUFFBALL_JET_START), jet, 1.0)
    _add_at(out, 0, render_voice(PUFFBALL_POP_VOICE), PUFFBALL_POP_GAIN)
    _add_at(out, n_samples(PUFFBALL_WHEEZE_START), render_voice(PUFFBALL_WHEEZE_VOICE), PUFFBALL_WHEEZE_GAIN)
    out = lowpass(out, PUFFBALL_BURST_LOWPASS_HZ)
    peak = max(abs(v) for v in out) or 1.0
    out = [math.tanh(PUFFBALL_BURST_DRIVE * v / peak) for v in out]
    return finish_rms(out, PUFFBALL_BURST_RMS_DB, PUFFBALL_BURST_PEAK_DB)


# ---------------------------------------------------------------- thorn root and tuft (#446, D75 principle 4)
# Spring (thorn_spring): the thorns turn lethal. A dull dirt "thk" under a rough rasp: noise through a band-pass, chopped by a
# fast pulse (a rasping buzz, the roughness) so it is a hard dry scrape, not bramble_lash's smooth rising "shk". One sound
# for both the root and the tuft (one family, D93). Quiet like the other environment cues, but plays only once per trap.
THORN_SPRING_SEED = 6601
THORN_SPRING_DURATION = 0.22
THORN_THUMP_BAND = (330.0, 260.0)    # Hz centre, bandwidth: the mound breaking
THORN_THUMP_TAU = 0.035              # s
THORN_RASP_BAND = (1900.0, 1500.0)
THORN_RASP_PULSE_HZ = 85.0           # the chop that makes it a rasp
THORN_RASP_PULSE_DEPTH = 0.7         # 0 = no chop, 1 = fully gated
THORN_RASP_ATTACK = 0.006            # s
THORN_RASP_TAU = 0.07                # s
THORN_RASP_GAIN = 0.9                # against the thump (peak 1.0)
THORN_RASP_DRIVE = 2.5               # a little tanh grit
THORN_SPRING_LOWPASS_HZ = 4500.0
THORN_SPRING_RMS_DB = -26.0       # peak-limited (the chopped rasp is sparse): quieter than bramble_lash
THORN_SPRING_PEAK_DB = -9.0


def thorn_spring() -> list[float]:
    rng = random.Random(THORN_SPRING_SEED)
    n = n_samples(THORN_SPRING_DURATION)
    thump = bandpass(noise(rng, n), *THORN_THUMP_BAND)
    rasp = bandpass(noise(rng, n), *THORN_RASP_BAND)
    tp, rp = max(abs(v) for v in thump) or 1.0, max(abs(v) for v in rasp) or 1.0
    te, re = envelope(n, 0.004, THORN_THUMP_TAU), envelope(n, THORN_RASP_ATTACK, THORN_RASP_TAU)
    out = []
    for i in range(n):
        gate = 1.0 - THORN_RASP_PULSE_DEPTH * (0.5 + 0.5 * math.sin(2 * math.pi * THORN_RASP_PULSE_HZ * i / SAMPLE_RATE))
        r = math.tanh(THORN_RASP_DRIVE * rasp[i] / rp * gate) * re[i] * THORN_RASP_GAIN
        out.append(thump[i] / tp * te[i] + r)
    return finish_rms(lowpass(out, THORN_SPRING_LOWPASS_HZ), THORN_SPRING_RMS_DB, THORN_SPRING_PEAK_DB)


# ---------------------------------------------------------------- leg-hold trap and lance root (#561, #562, D129)
# Seeds 7401 to 7404. Wood and soil, never metal or fire, so none of them sounds like the stump's explosion or the fissure's roar.
# Warning (leghold_rattle): a dry, chattering "t-t-t-t-t" of ten wooden ticks at about 18 Hz that swell and rise a little,
# 0.6 s so it ends before the base 0.8 s snap; a background level, as bramble_rustle, so it never masks another warning.
LEGHOLD_RATTLE_SEED = 7401
LEGHOLD_RATTLE_DURATION = 0.60
LEGHOLD_RATTLE_TICKS = 10
LEGHOLD_RATTLE_FIRST = 0.02          # s, the first tick
LEGHOLD_RATTLE_GAP = 0.055           # s between ticks (about 18 Hz)
LEGHOLD_RATTLE_TICK_LEN = 0.02
LEGHOLD_RATTLE_BAND = (1500.0, 2200.0, 900.0, 1400.0)   # centre and bandwidth of the first and of the last tick, Hz
LEGHOLD_RATTLE_GAIN = (0.6, 1.0)    # the ticks swell
LEGHOLD_RATTLE_RMS_DB = -27.0       # bramble_rustle's background level, the other dry warning of the set
LEGHOLD_RATTLE_PEAK_DB = -9.0        # the environment cap; the ticks are sparse, so level-set by the peak
# Consequence (leghold_snap): one hard "KLAK": a noise crack through a closed band and a dull wooden "tok" body at 220 Hz.
LEGHOLD_SNAP_SEED = 7402
LEGHOLD_SNAP_DURATION = 0.25
LEGHOLD_TOK_HZ = 220.0
LEGHOLD_TOK_ATTACK = 0.006
LEGHOLD_TOK_TAU = 0.09
LEGHOLD_SNAP_CRACK_LEN = 0.03
LEGHOLD_SNAP_CRACK_BAND = (2600.0, 3000.0)
LEGHOLD_SNAP_CRACK_GAIN = 0.7        # relative to the body's peak
LEGHOLD_SNAP_RMS_DB = -22.0
LEGHOLD_SNAP_PEAK_DB = -9.0           # the environment cap
LEGHOLD_SNAP_CRACK_DELAY = 0.004      # s, the body rises first: no click at the start
# Warning (root_creak): a low groan "oooorr" over crackling soil, 0.7 s so it ends before the base 0.8 s thrust; lower and slower
# than fissure_rumble (a different vibrato and vowel), quiet end of the voice set.
ROOT_CREAK_VOICE = Voice(7403, 0.70, (65.0, 95.0), FORMANTS_O, FORMANTS_U, -19.4, asp_len=0.040, asp_level=0.3,
                         attack=0.200, tau=0.600, release=0.100, jitter=0.10, rough=1.0, rough_start=0.0,
                         vibrato=(7.0, 0.12), lowpass=1200.0)
# Consequence (root_thrust): a fast rising "fwoo" (noise through a band-pass climbing 400 -> 2400 Hz) ending in a woody "THOK" at 170 Hz.
ROOT_THRUST_SEED = 7404
ROOT_THRUST_DURATION = 0.35
ROOT_THRUST_SWEEP = (400.0, 2400.0)
ROOT_THRUST_BANDWIDTH = 1100.0
ROOT_THRUST_ATTACK = 0.02
ROOT_THRUST_TAU = 0.12
ROOT_THRUST_DRIVE = 1.8
ROOT_THRUST_SWEEP_LEN = 0.22         # s, the rising part; the knock starts where it ends
ROOT_KNOCK_HZ = 170.0
ROOT_KNOCK_ATTACK = 0.002
ROOT_KNOCK_TAU = 0.05
ROOT_KNOCK_GAIN = 1.3                # relative to the sweep's peak
ROOT_THRUST_RMS_DB = -21.6
ROOT_THRUST_PEAK_DB = -9.0
ROOT_THRUST_LOWPASS_HZ = 5000.0


def leghold_rattle() -> list[float]:
    rng = random.Random(LEGHOLD_RATTLE_SEED)
    out = [0.0] * n_samples(LEGHOLD_RATTLE_DURATION)
    (c0, w0, c1, w1) = LEGHOLD_RATTLE_BAND
    for k in range(LEGHOLD_RATTLE_TICKS):
        f = k / (LEGHOLD_RATTLE_TICKS - 1)
        gain = LEGHOLD_RATTLE_GAIN[0] + (LEGHOLD_RATTLE_GAIN[1] - LEGHOLD_RATTLE_GAIN[0]) * f
        _add_tick(rng, out, n_samples(LEGHOLD_RATTLE_FIRST + k * LEGHOLD_RATTLE_GAP), n_samples(LEGHOLD_RATTLE_TICK_LEN),
                  (c0 + (c1 - c0) * f, w0 + (w1 - w0) * f), gain)
    return finish_rms(out, LEGHOLD_RATTLE_RMS_DB, LEGHOLD_RATTLE_PEAK_DB)


def leghold_snap() -> list[float]:
    rng = random.Random(LEGHOLD_SNAP_SEED)
    n = n_samples(LEGHOLD_SNAP_DURATION)
    env = envelope(n, LEGHOLD_TOK_ATTACK, LEGHOLD_TOK_TAU)
    out = [math.sin(2 * math.pi * LEGHOLD_TOK_HZ * i / SAMPLE_RATE) * e for i, e in enumerate(env)]
    _add_tick(rng, out, n_samples(LEGHOLD_SNAP_CRACK_DELAY), n_samples(LEGHOLD_SNAP_CRACK_LEN), LEGHOLD_SNAP_CRACK_BAND, LEGHOLD_SNAP_CRACK_GAIN)
    return finish_rms(out, LEGHOLD_SNAP_RMS_DB, LEGHOLD_SNAP_PEAK_DB)


def root_creak() -> list[float]:
    return finish_rms(render_voice(ROOT_CREAK_VOICE), ROOT_CREAK_VOICE.rms_db)


def root_thrust() -> list[float]:
    rng = random.Random(ROOT_THRUST_SEED)
    n, ns = n_samples(ROOT_THRUST_DURATION), n_samples(ROOT_THRUST_SWEEP_LEN)
    lo, hi = ROOT_THRUST_SWEEP
    swept = bandpass(noise(rng, ns), lambda t: lo + (hi - lo) * t, ROOT_THRUST_BANDWIDTH)
    peak = max(abs(v) for v in swept) or 1.0
    env = envelope(ns, ROOT_THRUST_ATTACK, ROOT_THRUST_TAU)
    out = [math.tanh(ROOT_THRUST_DRIVE * v / peak) * e for v, e in zip(swept, env)] + [0.0] * (n - ns)
    knock = envelope(n - ns, ROOT_KNOCK_ATTACK, ROOT_KNOCK_TAU)
    for i, e in enumerate(knock):
        out[ns + i] += ROOT_KNOCK_GAIN * math.sin(2 * math.pi * ROOT_KNOCK_HZ * i / SAMPLE_RATE) * e
    return finish_rms(lowpass(out, ROOT_THRUST_LOWPASS_HZ), ROOT_THRUST_RMS_DB, ROOT_THRUST_PEAK_DB)


# ---------------------------------------------------------------- hand-over and elixir (#541, D132)
# The healer's checklist items arrive in his hands (three times per payment, so it is short and quiet): a small glassy "tink", a
# high /i/ with a hard attack and a short ring, like the silver thread's pluck but higher and shorter.
HANDOVER_VOICE = Voice(4801, 0.12, (720.0, 690.0), FORMANTS_I, FORMANTS_I, -19.4, asp_len=0.004, asp_level=0.2,
                       attack=0.008, tau=0.045, release=0.020, jitter=0.01, lowpass=4200.0)
# The elixir reaches Edgard (once): a low swallowed "glug", /o/ falling, then a short rising chime /i/ 0.22 s in.
ELIXIR_VOICES = (Voice(4901, 0.24, (150.0, 95.0), FORMANTS_O, FORMANTS_O, -19.0, asp_len=0.020, asp_level=0.3,
                       attack=0.020, tau=0.110, release=0.030, jitter=0.06, rough=0.4, rough_start=0.5, lowpass=2600.0),
                 Voice(4902, 0.36, (520.0, 700.0), FORMANTS_I, FORMANTS_I, -19.0, asp_len=0.006, asp_level=0.2,
                       attack=0.012, tau=0.160, release=0.050, jitter=0.01, vibrato=(7.0, 0.02), lowpass=4200.0))
ELIXIR_SECOND_OFFSET = 0.22  # s, start of the chime
ELIXIR_RMS_DB = -19.0


def handover() -> list[float]:
    return finish_rms(render_voice(HANDOVER_VOICE), HANDOVER_VOICE.rms_db)


def elixir() -> list[float]:
    first, second = (render_voice(v) for v in ELIXIR_VOICES)
    out = [0.0] * (n_samples(ELIXIR_SECOND_OFFSET) + len(second))
    for i, a in enumerate(first):
        out[i] += a
    for i, a in enumerate(second):
        out[n_samples(ELIXIR_SECOND_OFFSET) + i] += a
    return finish_rms(out, ELIXIR_RMS_DB)


GAME_SOUNDS = {"jump.wav": jump, "hit.wav": hit, "bounce.wav": bounce, "collect.wav": collect,
               "disappear.wav": disappear, "boss_hit.wav": boss_hit, "boss_restore.wav": boss_restore,
               "button_click.wav": button_click, "dust_puff.wav": dust_puff, "wall_rumble.wav": wall_rumble,
               "grit_fall.wav": grit_fall, "explosion.wav": explosion, "stone_impact.wav": stone_impact, "keepalive.wav": keepalive,
               "thread.wav": thread, "deadfall_creak.wav": deadfall_creak, "deadfall_knock.wav": deadfall_knock,
               "bramble_rustle.wav": bramble_rustle, "bramble_lash.wav": bramble_lash,
               "thorn_spring.wav": thorn_spring,
               "hive_buzz.wav": hive_buzz, "stinger_rain.wav": stinger_rain, "hive_fall.wav": hive_fall,
               "fissure_rumble.wav": fissure_rumble, "ember_jet.wav": ember_jet,
               "leghold_rattle.wav": leghold_rattle, "leghold_snap.wav": leghold_snap, "root_creak.wav": root_creak,
               "root_thrust.wav": root_thrust,
               "fireball_fizzle.wav": fireball_fizzle, "beetle_notice.wav": beetle_notice,
               "beetle_windup.wav": beetle_windup, "beetle_windup_long.wav": beetle_windup_long, "beetle_pop.wav": beetle_pop,
               "remains_crumble.wav": remains_crumble, "remains_burn.wav": remains_burn,
               "spider_notice.wav": spider_notice, "spider_windup.wav": spider_windup,
               "spider_windup_long.wav": spider_windup_long, "spider_leap.wav": spider_leap, "spider_bite.wav": spider_bite,
               "spider_drop.wav": spider_drop, "spider_reel.wav": spider_reel, "spider_snap.wav": spider_snap,
               "snail_notice.wav": snail_notice, "snail_windup.wav": snail_windup, "snail_windup_long.wav": snail_windup_long,
               "snail_spit.wav": snail_spit, "snail_splat.wav": snail_splat,
               "whip_crack.wav": whip_crack, "boss_sweep.wav": boss_sweep, "boss_sweep_counter.wav": boss_sweep_counter,
               "spore_puff.wav": spore_puff, "puffball_burst.wav": puffball_burst,
               "wolf_notice.wav": wolf_notice, "wolf_windup.wav": wolf_windup, "wolf_windup_long.wav": wolf_windup_long,
               "wolf_lunge.wav": wolf_lunge, "wolf_bite.wav": wolf_bite, "wolf_bay.wav": wolf_bay, "wolf_run.wav": wolf_run,
               "handover.wav": handover, "elixir.wav": elixir}


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
