#!/usr/bin/env python3
"""Print duration, peak, RMS (and spectral figures) of WAV files; optionally render PNG previews if matplotlib exists."""
from __future__ import annotations

import cmath
import math
import struct
import sys
import wave
import zlib
from pathlib import Path


def load(path: str) -> tuple[list[float], int]:
    with wave.open(path) as w:
        n, width, sr = w.getnframes(), w.getsampwidth(), w.getframerate()
        raw = w.readframes(n)
    if width == 2:
        return [v / 32768.0 for v in struct.unpack("<%dh" % n, raw)], sr
    return [(b - 128) / 128.0 for b in raw], sr


def db(v: float) -> float:
    return 20.0 * math.log10(max(v, 1e-9))


def dft_mag(x: list[float], sr: int, fmax: float = 8000.0, step: float = 25.0) -> list[tuple[float, float]]:
    n = len(x)
    win = [0.5 - 0.5 * math.cos(2 * math.pi * i / (n - 1)) for i in range(n)]
    xs = [a * b for a, b in zip(x, win)]
    out = []
    f = step
    while f <= fmax:
        w = -2j * math.pi * f / sr
        acc = sum(v * cmath.exp(w * i) for i, v in enumerate(xs))
        out.append((f, abs(acc)))
        f += step
    return out


def write_png(path: Path, rows: list[bytearray], w: int) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data))
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, len(rows), 8, 0, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def envelope_png(x: list[float], out: Path, w: int = 480, h: int = 120) -> None:
    """Greyscale waveform envelope (min/max per column, mirrored) with a dB-free linear scale; stdlib only."""
    rows = [bytearray([255] * w) for _ in range(h)]
    for c in range(w):
        seg = x[c * len(x) // w:max((c + 1) * len(x) // w, c * len(x) // w + 1)]
        top = int((1 - max(seg)) * (h - 1) / 2)
        bot = int((1 - min(seg)) * (h - 1) / 2)
        for r in range(max(top, 0), min(bot, h - 1) + 1):
            rows[r][c] = 40
    for c in range(w):
        rows[h // 2][c] = min(rows[h // 2][c], 170)
    write_png(out, rows, w)


def main() -> None:
    args = sys.argv[1:]
    preview = None
    if args[:1] == ["--png"]:
        preview, args = Path(args[1]), args[2:]
    for path in args:
        x, sr = load(path)
        if preview:
            preview.mkdir(parents=True, exist_ok=True)
            envelope_png(x, preview / (Path(path).stem + ".png"))
        peak = max(abs(v) for v in x)
        rms = math.sqrt(sum(v * v for v in x) / len(x))
        mean = sum(x) / len(x)
        line = f"{path}: {len(x) / sr:.3f}s peak {db(peak):.1f} dBFS rms {db(rms):.1f} dBFS dc {mean:+.5f}"
        if "--spectrum" in sys.argv[0:1] or True:
            spec = dft_mag(x, sr)
            e = [m * m for _, m in spec]
            tot = sum(e)
            cen = sum(f * m * m for f, m in spec) / tot
            below3k = sum(m * m for f, m in spec if f <= 3000) / tot
            f1 = sum(m * m for f, m in spec if 300 <= f <= 900) / tot
            line += f" centroid {cen:.0f} Hz, <=3kHz {100 * below3k:.0f}% , 300-900Hz {100 * f1:.0f}%"
        print(line)


if __name__ == "__main__":
    main()
