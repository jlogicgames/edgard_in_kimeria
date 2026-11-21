#!/usr/bin/env python3
"""The inherited enemy sheets (RedMob, Bat, Boss, YellowMob) from hand-drawn pixel grids -> native indexed PNG sheets.

Each `art/objects/enemy/<name>.grid` is a hand-authored drawing in the grid format of `forest_trap_sprites` (one character
per pixel, `frame <name>` blocks). This module parses it, places every frame in the cells `sprite_layouts.ENEMY_SHEETS`
names (the cells the entity's animations read), gives the sheet the moon rim (D92, `moon_rim`, cell by cell) and writes
`assets/images/enemy/<sheet>.png` with an exact palette. It contains no drawing logic.

Usage: `python3 tools/enemy_sprites.py [name ...]` exports the named sheets (default all); `--check` exports nothing and
fails when a shipped sheet differs from its grid.
"""
import sys
from pathlib import Path
from PIL import Image
import moon_rim
from forest_trap_sprites import parse, exact_indexed
from sprite_layouts import ENEMY_SHEETS, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA

ROOT = Path(__file__).resolve().parents[1]
GRID_DIR = ROOT / 'art/objects/enemy'
ASSET_DIR = ROOT / 'assets/images/enemy'
RGBA_CHANNELS = 4
CHECK_FLAG = '--check'
RESERVED_ENTRIES = 2  # palette entries that are not grid colours: transparency and the moon rim


def frame_image(size, colors, rows):
    data = bytearray()
    for row in rows:
        for char in row:
            colour = colors[char]
            data += bytes(colour + (OPAQUE_ALPHA,)) if colour else bytes(RGBA_CHANNELS)
    return Image.frombytes('RGBA', size, bytes(data))


def sheet_image(name):
    """The RGBA sheet for `name`, every frame in its cells, with the moon rim."""
    _, frame_size, sheet_size, cells = ENEMY_SHEETS[name]
    size, colors, frames = parse(GRID_DIR / f'{name}.grid')
    if size != frame_size or [f for f, _ in frames] != list(cells):
        raise ValueError(f'{name}: grid size/frames differ from sprite_layouts.ENEMY_SHEETS')
    # Two grid characters may share a colour (the boss's axe ink `a` is the ink `k`, so tests can tell the weapon from the
    # body); the palette counts colours, and the rim takes one more entry besides transparency.
    if len({c for c in colors.values() if c}) > WORLD_PALETTE_SIZE - RESERVED_ENTRIES:
        raise ValueError(f'{name}: more than {WORLD_PALETTE_SIZE - RESERVED_ENTRIES} opaque colours')
    sheet = Image.new('RGBA', sheet_size)
    for frame, rows in frames:
        image = frame_image(size, colors, rows)
        for column, row in cells[frame]:
            sheet.paste(image, (column * size[0], row * size[1]))
    return moon_rim.rim(sheet, size)


def export(name):
    sheet, _, _, _ = ENEMY_SHEETS[name]
    target = ASSET_DIR / f'{sheet}.png'
    indexed = exact_indexed(name, sheet_image(name))
    indexed.save(target, bits=WORLD_PALETTE_BITS, transparency=0)
    print(target.relative_to(ROOT))


def differences(names):
    return [name for name in names
            if Image.open(ASSET_DIR / f'{ENEMY_SHEETS[name][0]}.png').convert('RGBA').tobytes() != sheet_image(name).tobytes()]


def main(argv):
    names = [a for a in argv if a != CHECK_FLAG] or list(ENEMY_SHEETS)
    if CHECK_FLAG in argv:
        stale = differences(names)
        if stale:
            print('Stale enemy sheets, run tools/enemy_sprites.py: ' + ', '.join(stale))
            return 1
        print('Enemy sheets match their grids.')
        return 0
    for name in names:
        export(name)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
