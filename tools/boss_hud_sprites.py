#!/usr/bin/env python3
"""Boss health bar art (#543): hand-drawn pixel grids -> native indexed PNGs.

`art/ui/boss/<name>.grid` is the drawing (the grid format of the forest traps, see `forest_trap_sprites.parse`); this module
only validates it against `sprite_layouts.BOSS_HUD_SHEETS` and exports `assets/images/ui/boss/<name>.png`, plus a contact sheet
`docs/references/boss-hud/boss-hud-art.png`. It contains no drawing logic.

    python3 tools/boss_hud_sprites.py
"""
from pathlib import Path
from PIL import Image
import forest_trap_sprites as grids
from dialogue_frame_sprites import indexed
from sprite_layouts import (BOSS_HUD_SHEETS, OPAQUE_BOSS_HUD_IMAGES, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA,
                            RGB_CHANNELS, BOSS_HUD_SLOTS, BOSS_HUD_INSET, BOSS_HUD_PITCH)

RGBA_CHANNELS = RGB_CHANNELS + 1
ROOT = grids.ROOT
GRID_DIR = ROOT / 'art/ui/boss'
TARGET_DIR = ROOT / 'assets/images/ui/boss'
PREVIEW = ROOT / 'docs/references/boss-hud/boss-hud-art.png'
NIGHT = (0x2c, 0x3f, 0x66)  # the open levels' night, D92
SCALE = 3
PAD = 8
ROWS = 2  # a full bar over an empty one


def master(name):
    """The drawing of `name` as RGBA."""
    size, colors, frames = grids.parse(GRID_DIR / f'{name}.grid')
    if size != BOSS_HUD_SHEETS[name] or len(frames) != 1:
        raise ValueError(f'{name}: grid size or frame count differs from sprite_layouts.BOSS_HUD_SHEETS')
    if len(colors) > WORLD_PALETTE_SIZE:
        raise ValueError(f'{name}: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    data = bytearray()
    for row in frames[0][1]:
        for char in row:
            data += bytes(colors[char] + (OPAQUE_ALPHA,)) if colors[char] else bytes(RGBA_CHANNELS)
    image = Image.frombytes('RGBA', size, bytes(data))
    transparent = any(alpha == 0 for alpha in image.getchannel('A').get_flattened_data())
    if transparent == (name in OPAQUE_BOSS_HUD_IMAGES):
        raise ValueError(f'{name}: transparency does not match OPAQUE_BOSS_HUD_IMAGES')
    return image


def export(name):
    image = master(name)
    opaque = name in OPAQUE_BOSS_HUD_IMAGES
    result = indexed(image, opaque)
    TARGET_DIR.mkdir(parents=True, exist_ok=True)
    target = TARGET_DIR / f'{name}.png'
    if opaque:
        result.save(target, bits=WORLD_PALETTE_BITS)
    else:
        result.save(target, bits=WORLD_PALETTE_BITS, transparency=0)
    if Image.open(target).convert('RGBA').tobytes() != image.tobytes():
        raise ValueError(f'{name}: the export changed pixels')
    return image


def contact_sheet(images):
    """The frame with a full and an empty bar and the two segments, at 3x on the night colour."""
    frame, filled, empty = (images[name] for name in ('frame', 'segment-filled', 'segment-empty'))
    sheet = Image.new('RGBA', (frame.width + PAD * ROWS, frame.height * ROWS + PAD * (ROWS + 1)), NIGHT + (OPAQUE_ALPHA,))
    for row, count in enumerate((BOSS_HUD_SLOTS, 0)):
        top = PAD + row * (frame.height + PAD)
        sheet.alpha_composite(frame, (PAD, top))
        for i in range(BOSS_HUD_SLOTS):
            sheet.alpha_composite(filled if i < count else empty, (PAD + BOSS_HUD_INSET[0] + i * BOSS_HUD_PITCH, top + BOSS_HUD_INSET[1]))
    big = sheet.resize((sheet.width * SCALE, sheet.height * SCALE), Image.Resampling.NEAREST)
    big.convert('RGB').save(PREVIEW)


if __name__ == '__main__':
    exported = {name: export(name) for name in BOSS_HUD_SHEETS}
    contact_sheet(exported)
    print(f'{len(exported)} sprites -> {TARGET_DIR.relative_to(ROOT)}')
