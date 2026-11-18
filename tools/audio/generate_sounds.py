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

GAME_SOUNDS = {"jump.wav": jump, "hit.wav": hit, "bounce.wav": bounce, "collect.wav": collect,
               "disappear.wav": disappear, "boss_hit.wav": boss_hit, "boss_restore.wav": boss_restore,
               "button_click.wav": button_click, "dust_puff.wav": dust_puff, "wall_rumble.wav": wall_rumble,
               "grit_fall.wav": grit_fall, "explosion.wav": explosion, "stone_impact.wav": stone_impact, "keepalive.wav": keepalive,
               "thread.wav": thread, "deadfall_creak.wav": deadfall_creak, "deadfall_knock.wav": deadfall_knock,
               "bramble_rustle.wav": bramble_rustle, "bramble_lash.wav": bramble_lash,
               "hive_buzz.wav": hive_buzz, "stinger_rain.wav": stinger_rain, "hive_fall.wav": hive_fall,
               "fissure_rumble.wav": fissure_rumble, "ember_jet.wav": ember_jet,
               "fireball_fizzle.wav": fireball_fizzle, "beetle_notice.wav": beetle_notice,
               "beetle_windup.wav": beetle_windup, "beetle_windup_long.wav": beetle_windup_long, "beetle_pop.wav": beetle_pop,
               "remains_crumble.wav": remains_crumble, "remains_burn.wav": remains_burn}


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
