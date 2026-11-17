#!/usr/bin/env python3
"""The Kimeria arms (#350, #416, D95): the hand-drawn grid -> one native indexed PNG strip.

`art/ui/arms/arms.grid` is the drawing (the grid format of the forest traps, see `forest_trap_sprites.parse`); this module
only validates it against `sprite_layouts.ARMS_FRAMES`, checks that frame `lit` is the reference drawing
`art/heraldry/kimeria-arms.png` pixel for pixel, and exports `assets/images/ui/kimeria-arms.png`. It contains no drawing logic.

    python3 tools/arms_sprites.py
"""
from PIL import Image
import forest_trap_sprites as grids
from dialogue_frame_sprites import indexed
from sprite_layouts import ARMS_FRAME_SIZE, ARMS_FRAMES, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA

ROOT = grids.ROOT
GRID = ROOT / 'art/ui/arms/arms.grid'
REFERENCE = ROOT / 'art/heraldry/kimeria-arms.png'
TARGET = ROOT / 'assets/images/ui/kimeria-arms.png'
LIT = ARMS_FRAMES.index('lit')
TRANSPARENT = (0, 0, 0, 0)


def master():
    """The strip of every frame, left to right, as RGBA."""
    size, colors, frames = grids.parse(GRID)
    if size != ARMS_FRAME_SIZE or tuple(name for name, _ in frames) != ARMS_FRAMES:
        raise ValueError('arms.grid: size/frames differ from sprite_layouts.ARMS_FRAMES')
    if len(colors) > WORLD_PALETTE_SIZE:
        raise ValueError(f'arms.grid: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    width, height = size
    strip = Image.new('RGBA', (width * len(frames), height))
    for index, (_, rows) in enumerate(frames):
        data = bytearray()
        for row in rows:
            for char in row:
                data += bytes(colors[char] + (OPAQUE_ALPHA,)) if colors[char] else bytes(TRANSPARENT)
        strip.paste(Image.frombytes('RGBA', size, bytes(data)), (index * width, 0))
    lit = strip.crop((LIT * width, 0, (LIT + 1) * width, height))
    if lit.tobytes() != Image.open(REFERENCE).convert('RGBA').tobytes():
        raise ValueError('arms.grid: frame lit must be art/heraldry/kimeria-arms.png pixel for pixel')
    return strip


def export():
    image = master()
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    indexed(image, False).save(TARGET, bits=WORLD_PALETTE_BITS, transparency=0)
    if Image.open(TARGET).convert('RGBA').tobytes() != image.tobytes():
        raise ValueError('arms: the export changed pixels')
    return image


if __name__ == '__main__':
    export()
    print(f'{len(ARMS_FRAMES)} frames -> {TARGET.relative_to(ROOT)}')
