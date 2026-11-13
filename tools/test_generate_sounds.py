from __future__ import annotations

import math
import struct
import sys
import tempfile
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "audio"))
import generate_sounds as gs  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
# Played with Sound.loop (the wall rumble lasts as long as the wall moves): periodic, no edge fade.
LOOPED = {"wall_rumble.wav", "keepalive.wav"}
# name -> (min seconds, max seconds)
BOUNDS = {"jump.wav": (0.12, 0.20), "hit.wav": (0.22, 0.34), "bounce.wav": (0.17, 0.26), "collect.wav": (0.16, 0.26),
          "disappear.wav": (0.45, 0.65), "boss_hit.wav": (0.19, 0.30), "boss_restore.wav": (0.32, 0.48), "button_click.wav": (0.10, 0.16),
          "dust_puff.wav": (0.10, 0.18), "wall_rumble.wav": (0.9, 1.1),
          "grit_fall.wav": (0.4, 0.6), "keepalive.wav": (0.9, 1.1)}
# Voice-synth effects (#313): target RMS in dBFS. Converted sounds must stay balanced against the jump (-17.0 dBFS);
# a sound joins this table when its replacement is generated. Whispered dust_puff is level-set by peak and is excluded.
VOICE_RMS_DB = {"jump.wav": -17.0, "hit.wav": -16.5, "bounce.wav": -17.5, "collect.wav": -19.0, "disappear.wav": -18.0,
                "boss_hit.wav": -16.5, "boss_restore.wav": -19.0, "button_click.wav": -19.4}
# Environment sounds (batch 3): quiet backgrounds, below every voice effect; same tolerance.
AMBIENT_RMS_DB = {"wall_rumble.wav": -20.0, "grit_fall.wav": -30.0}
AMBIENT_MAX_RMS_DB = -20.0
VOICE_RMS_TOLERANCE_DB = 0.5
MIN_EFFECT_SECONDS = 0.10     # shorter than this is not a usable effect on a waking output
MIN_EFFECT_PEAK_DB = -24.0    # a shipped effect peaks above this (silence or a near-silent render fails)
AUDIBLE_WINDOW_DB = -20.0     # a 10 ms window counts as audible within this of the file's peak
MIN_AUDIBLE_SECONDS = 0.08    # seconds of audible windows an effect must have (the jump has about 0.12)
VOICE_RMS_SPREAD_DB = 3.0    # loudest minus quietest converted effect


def read(path: Path):
    with wave.open(str(path)) as w:
        n = w.getnframes()
        return w.getnchannels(), w.getsampwidth(), w.getframerate(), struct.unpack("<%dh" % n, w.readframes(n))


class GenerateSoundsTest(unittest.TestCase):
    def test_deterministic_and_contract(self) -> None:
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            gs.generate(Path(a))
            gs.generate(Path(b))
            for name, (lo, hi) in BOUNDS.items():
                self.assertEqual((Path(a) / name).read_bytes(), (Path(b) / name).read_bytes(), name)
                head = (Path(a) / name).read_bytes()[:36]
                # libGDX's decoder needs RIFF/WAVE, a 16-byte plain-PCM "fmt " chunk (tag 1), no EXTENSIBLE.
                self.assertEqual((head[:4], head[8:16], struct.unpack("<IH", head[16:22])),
                                 (b"RIFF", b"WAVEfmt ", (16, 1)), name)
                ch, width, rate, s = read(Path(a) / name)
                self.assertEqual((ch, width, rate), (1, 2, gs.SAMPLE_RATE), name)
                self.assertTrue(lo <= len(s) / rate <= hi, name)
                self.assertLessEqual(max(map(abs, s)) / 32768.0, 10 ** (gs.PEAK_LIMIT_DB / 20) + 1e-4, name)
                self.assertLess(abs(sum(s) / len(s)) / 32768.0, 0.001, name)
                if name in LOOPED:
                    # A seamless loop has no edge fade: the wrap from the last sample to the first must be as smooth
                    # as any step inside the file (the one-shots instead start and end at exactly 0).
                    step = max(abs(s[i + 1] - s[i]) for i in range(len(s) - 1))
                    self.assertLessEqual(abs(s[0] - s[-1]), step, name)
                else:
                    self.assertEqual((s[0], s[-1]), (0, 0), name)

    def test_shipped_files_match_generator(self) -> None:
        """assets/audio must be what the script produces, so regenerating is a no-op."""
        with tempfile.TemporaryDirectory() as a:
            gs.generate(Path(a))
            for name in gs.GAME_SOUNDS:
                self.assertEqual((Path(a) / name).read_bytes(), (ROOT / "assets/audio" / name).read_bytes(), name)

    def test_keepalive_is_quiet_but_not_silent(self) -> None:
        """Below audibility, above digital silence, and a loop that wraps without a jump (#316)."""
        x = read(ROOT / "assets/audio/keepalive.wav")[3]
        f = [v / 32768.0 for v in x]
        rms_db = 20 * math.log10(math.sqrt(sum(v * v for v in f) / len(f)))
        self.assertAlmostEqual(rms_db, gs.KEEPALIVE_RMS_DB, delta=1.0)
        self.assertLess(rms_db, -60.0)
        self.assertLess(20 * math.log10(max(abs(v) for v in f)), -50.0)
        self.assertGreater(sum(1 for v in x if v != 0) / len(x), 0.95)   # not digital silence
        self.assertGreaterEqual(max(abs(v) for v in x), 8)               # well above the 16-bit quantisation floor
        step = max(abs(x[i + 1] - x[i]) for i in range(len(x) - 1))
        self.assertLessEqual(abs(x[0] - x[-1]), step)

    def test_jump_is_an_open_vowel_ha(self) -> None:
        """Smooth attack (no plosive click), RMS near the other effects, formant energy at the open vowel, dark."""
        x = [v / 32768.0 for v in read(ROOT / "assets/audio/jump.wav")[3]]
        rms_db = 20 * math.log10(math.sqrt(sum(v * v for v in x) / len(x)))
        self.assertTrue(-17.5 <= rms_db <= -16.0, rms_db)
        self.assertLessEqual(max(abs(v) for v in x), 10 ** (-2.0 / 20))
        self.assertLess(max(abs(v) for v in x[:gs.n_samples(0.002)]), 0.1 * max(abs(v) for v in x))  # no click

        def energy(lo, hi):
            tot = 0.0
            for f in range(lo, hi, 50):
                re = sum(v * math.cos(2 * math.pi * f * i / gs.SAMPLE_RATE) for i, v in enumerate(x))
                im = sum(v * math.sin(2 * math.pi * f * i / gs.SAMPLE_RATE) for i, v in enumerate(x))
                tot += re * re + im * im
            return tot
        self.assertGreater(energy(600, 1500), energy(100, 500))      # open vowel, not a closed-mouth murmur
        self.assertLess(energy(3000, 6000), 0.04 * energy(100, 3000))  # dark; measured 2.15 % for the breathy recipe

    def test_voice_set_levels_are_balanced(self) -> None:
        """Every converted voice effect sits at its target RMS, peaks at most -2 dBFS and none strays from the rest."""
        levels = {}
        for name, target in VOICE_RMS_DB.items():
            x = [v / 32768.0 for v in read(ROOT / "assets/audio" / name)[3]]
            levels[name] = 20 * math.log10(math.sqrt(sum(v * v for v in x) / len(x)))
            self.assertAlmostEqual(levels[name], target, delta=VOICE_RMS_TOLERANCE_DB, msg=name)
            self.assertLessEqual(max(abs(v) for v in x), 10 ** (gs.VOICE_PEAK_CAP_DB / 20) + 1e-4, name)
        self.assertLessEqual(max(levels.values()) - min(levels.values()), VOICE_RMS_SPREAD_DB, levels)

    def test_ambient_levels(self) -> None:
        """The rumble and the grit sit at their target RMS, below the voice effects, peak at most -9 dBFS."""
        for name, target in AMBIENT_RMS_DB.items():
            x = [v / 32768.0 for v in read(ROOT / "assets/audio" / name)[3]]
            rms_db = 20 * math.log10(math.sqrt(sum(v * v for v in x) / len(x)))
            self.assertAlmostEqual(rms_db, target, delta=VOICE_RMS_TOLERANCE_DB, msg=name)
            self.assertLessEqual(rms_db, AMBIENT_MAX_RMS_DB + VOICE_RMS_TOLERANCE_DB, name)
            self.assertLessEqual(max(abs(v) for v in x), 10 ** (-9.0 / 20) + 1e-4, name)

    def test_rumble_is_dark_and_low(self) -> None:
        """Most of the loop's energy is below 600 Hz and very little above 2 kHz (non-fatiguing over seconds)."""
        x = [v / 32768.0 for v in read(ROOT / "assets/audio/wall_rumble.wav")[3]]

        def energy(lo, hi):
            tot = 0.0
            for f in range(lo, hi, 10):
                re = sum(v * math.cos(2 * math.pi * f * i / (gs.SAMPLE_RATE / 2)) for i, v in enumerate(x[::2]))
                im = sum(v * math.sin(2 * math.pi * f * i / (gs.SAMPLE_RATE / 2)) for i, v in enumerate(x[::2]))
                tot += re * re + im * im
            return tot
        total = energy(20, 4000)
        self.assertGreater(energy(20, 600), 0.8 * total)
        self.assertLess(energy(2000, 4000), 0.02 * total)

    def test_voice_effects_start_and_end_softly(self) -> None:
        """No click: the first 2 ms stay well under the peak (breath or vowel ramps in, as for the jump)."""
        for name in list(VOICE_RMS_DB) + ["grit_fall.wav"]:
            x = [v / 32768.0 for v in read(ROOT / "assets/audio" / name)[3]]
            self.assertLess(max(abs(v) for v in x[:gs.n_samples(0.002)]), 0.1 * max(abs(v) for v in x), name)

    def test_every_shipped_effect_is_audible_and_well_formed(self) -> None:
        """Each generated effect: plain PCM mono 16-bit 44.1 kHz, not silent, at least MIN_EFFECT_SECONDS long (a very
        short sound is swallowed by an output that is waking up, #316), with real energy for most of its length."""
        for name in gs.GAME_SOUNDS:
            if name == "keepalive.wav":
                continue
            ch, width, rate, s = read(ROOT / "assets/audio" / name)
            self.assertEqual((ch, width, rate), (1, 2, 44100), name)
            self.assertGreaterEqual(len(s) / rate, MIN_EFFECT_SECONDS, name)
            peak = max(map(abs, s))
            self.assertGreater(peak / 32768.0, 10 ** (MIN_EFFECT_PEAK_DB / 20), name)
            win = rate // 100   # 10 ms windows
            loud = sum(1 for i in range(0, len(s) - win, win)
                       if max(map(abs, s[i:i + win])) > peak * 10 ** (AUDIBLE_WINDOW_DB / 20))
            self.assertGreaterEqual(loud * win / rate, MIN_AUDIBLE_SECONDS, name)

    def test_grunt_pitch_falls(self) -> None:
        x = [v / 32768.0 for v in read(ROOT / "assets/audio/jump.wav")[3]]

        def f0(seg):
            lag = max(range(gs.SAMPLE_RATE // 200, gs.SAMPLE_RATE // 80),
                      key=lambda l: sum(seg[i] * seg[i + l] for i in range(len(seg) - l)))
            return gs.SAMPLE_RATE / lag
        self.assertGreater(f0(x[1300:3000]), f0(x[4800:6500]))


if __name__ == "__main__":
    unittest.main()
