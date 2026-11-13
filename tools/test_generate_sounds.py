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
BOUNDS = {"jump.wav": (0.12, 0.20), "dust_puff.wav": (0.08, 0.14), "wall_rumble.wav": (0.9, 1.1),
          "grit_fall.wav": (0.4, 0.6), "keepalive.wav": (0.9, 1.1)}


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

    def test_grunt_pitch_falls(self) -> None:
        x = [v / 32768.0 for v in read(ROOT / "assets/audio/jump.wav")[3]]

        def f0(seg):
            lag = max(range(gs.SAMPLE_RATE // 200, gs.SAMPLE_RATE // 80),
                      key=lambda l: sum(seg[i] * seg[i + l] for i in range(len(seg) - l)))
            return gs.SAMPLE_RATE / lag
        self.assertGreater(f0(x[1300:3000]), f0(x[4800:6500]))


if __name__ == "__main__":
    unittest.main()
