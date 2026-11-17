#!/usr/bin/env python3
"""The silver thread on death (#341, D81): the hand-drawn grid -> one native indexed PNG.

`art/objects/silver-thread.grid` is the drawing (the grid format of the forest traps, see `forest_trap_sprites.parse`): the
1x2 strand at (0, 0) and the 5x5 glint at (1, 0). The game stamps the strand once per pixel along a curve computed between
two moving points (Edgard's wrist and the room start), so the thread's colours stay authored while its length changes with
every death (D81). This module only validates the grid and exports it exactly; it contains no drawing logic.

    python3 tools/silver_thread_sprites.py
"""
from pathlib import Path
from PIL import Image
import forest_trap_sprites as grids
from dialogue_frame_sprites import indexed
from sprite_layouts import SILVER_THREAD_SIZE, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA, RGB_CHANNELS

ROOT = grids.ROOT
GRID = ROOT / 'art/objects/silver-thread.grid'
TARGET = ROOT / 'assets/images/objects/silver-thread.png'
RGBA_CHANNELS = RGB_CHANNELS + 1
FRAMES = ('thread',)


def master():
    """The grid as RGBA."""
    size, colors, frames = grids.parse(GRID)
    if size != SILVER_THREAD_SIZE or tuple(name for name, _ in frames) != FRAMES:
        raise ValueError('silver-thread: grid size/frames differ from sprite_layouts.SILVER_THREAD_SIZE')
    if len(colors) > WORLD_PALETTE_SIZE:
        raise ValueError(f'silver-thread: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    data = bytearray()
    for row in frames[0][1]:
        for char in row:
            data += bytes(colors[char] + (OPAQUE_ALPHA,)) if colors[char] else bytes(RGBA_CHANNELS)
    return Image.frombytes('RGBA', size, bytes(data))


def export():
    image = master()
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    indexed(image, False).save(TARGET, bits=WORLD_PALETTE_BITS, transparency=0)
    if Image.open(TARGET).convert('RGBA').tobytes() != image.tobytes():
        raise ValueError('silver-thread: the export changed pixels')
    return image


if __name__ == '__main__':
    export()
    print(TARGET.relative_to(ROOT))
