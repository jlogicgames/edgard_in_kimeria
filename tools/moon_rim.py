#!/usr/bin/env python3
"""The moon rim (D92, #430): the night-time contour of an active sprite.

On the night background (`2c3f66`) a dark contour no longer separates a sprite from the sky. The rim repaints an authored
sprite's **existing** dark contour pixels on the lit sides (up, left and right) in the pale moon colour `c4cfe8`; contour
pixels facing down keep their ink, so the underside still reads on the ground. It adds no pixel and moves none: the
silhouette, the hitbox and every shape stay as drawn, only the colour of some outermost pixels changes. It is a recolour
of authored art, like the exporters' palette packing, not procedural drawing.

- A pixel is rimmed when it is opaque, darker than `SOURCE_L` (OKLab lightness) and has a transparent 4-neighbour above,
  left or right of it. Pixels outside the frame cell count as transparent, so frames packed side by side in a strip do
  not shield each other.
- Light edges (bone, gold, silver) are left alone: they already read. A rim painted by hand in the grid is light too, so
  it is kept, and `rim` is idempotent: softening the rim by hand in the source survives a re-export.
- Left and right are both lit, so the rim of a mirrored frame is the mirror of the rim (the hero's left facing).

Library use: `rim(image, cell)` returns a new RGBA image. The exporters call it for the sheets named in their `RIM_SHEETS`
(`forest_trap_sprites.RIM_SHEETS`, `hero_sprites.RIM_SHEETS`). CLI, for sheets with no grid source (the inherited enemy
and lift sheets): `python3 tools/moon_rim.py SHEET.png --cell W H [--out OUT.png]` writes an RGBA PNG.
"""
import argparse
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_contrast import ALPHA_MIN, oklab  # noqa: E402  one OKLab formula and one opacity rule for the rim and its gate

RIM = (0xc4, 0xcf, 0xe8)  # pale moon blue, OKLab L about 0.85
SOURCE_L = 0.50  # contour pixels darker than this are rimmed; lighter edges already read on the night
CHANNELS = 4  # RGBA
ALPHA = 3  # index of alpha in an RGBA pixel
CELL_ARGS = 2  # --cell W H
LIT_SIDES = ((0, -1), (-1, 0), (1, 0))  # up, left, right in Y-down image space; down keeps its ink


def rim(image, cell=None, colour=RIM):
    """A copy of `image` with the moon rim; `cell` (w, h) is the frame size of a strip or grid sheet, default the image."""
    source = image.convert('RGBA')
    width, height = source.size
    cell_w, cell_h = cell or source.size
    if width % cell_w or height % cell_h:
        raise ValueError(f'{width}x{height} is not a whole number of {cell_w}x{cell_h} cells')
    data = source.tobytes()
    out = bytearray(data)

    def opaque(x, y):
        return data[(y * width + x) * CHANNELS + ALPHA] >= ALPHA_MIN

    for y in range(height):
        for x in range(width):
            at = (y * width + x) * CHANNELS
            if data[at + ALPHA] < ALPHA_MIN or oklab(data[at:at + ALPHA])[0] >= SOURCE_L:
                continue
            left, top = x - x % cell_w, y - y % cell_h
            for dx, dy in LIT_SIDES:
                nx, ny = x + dx, y + dy
                if not (left <= nx < left + cell_w and top <= ny < top + cell_h) or not opaque(nx, ny):
                    out[at:at + ALPHA] = bytes(colour)
                    break
    return Image.frombytes('RGBA', source.size, bytes(out))


def main(argv=None):
    parser = argparse.ArgumentParser(description='Apply the moon rim (D92) to a PNG sheet with no grid source.')
    parser.add_argument('sheet', type=Path)
    parser.add_argument('--cell', type=int, nargs=CELL_ARGS, metavar=('W', 'H'), help='frame size; default the whole image')
    parser.add_argument('--out', type=Path, help='output PNG; default overwrite the sheet')
    args = parser.parse_args(argv)
    result = rim(Image.open(args.sheet), tuple(args.cell) if args.cell else None)
    result.save(args.out or args.sheet)
    print(args.out or args.sheet)
    return 0


if __name__ == '__main__':
    sys.exit(main())
