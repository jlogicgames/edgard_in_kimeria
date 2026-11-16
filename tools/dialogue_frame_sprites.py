#!/usr/bin/env python3
"""Dialogue screen art (#337, D78): hand-drawn pixel grids -> native indexed PNG strips.

`art/ui/dialogue/<name>.grid` is the drawing (the grid format of the forest traps, see `forest_trap_sprites.parse`); this
module only validates it against `sprite_layouts.DIALOGUE_SHEETS` and exports `assets/images/ui/dialogue-<name>.png`, plus
a contact sheet `docs/references/dialogue-frame-art.png`. It contains no drawing logic.

    python3 tools/dialogue_frame_sprites.py
"""
from pathlib import Path
from PIL import Image
import forest_trap_sprites as grids
from sprite_layouts import (DIALOGUE_SHEETS, OPAQUE_DIALOGUE_IMAGES, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS,
                            OPAQUE_ALPHA, RGB_CHANNELS, ALPHA_CHANNEL)

RGBA_CHANNELS = RGB_CHANNELS + 1
ROOT = grids.ROOT
GRID_DIR = ROOT / 'art/ui/dialogue'
TARGET_DIR = ROOT / 'assets/images/ui'
PREVIEW = ROOT / 'docs/references/dialogue-frame-art.png'
NIGHT = (0x0c, 0x0e, 0x1a)
SCALE = 3
PAD = 8


def master(name):
    """The strip of every frame of `name`, left to right, as RGBA."""
    size, colors, frames = grids.parse(GRID_DIR / f'{name}.grid')
    frame_size, names = DIALOGUE_SHEETS[name]
    if size != frame_size or tuple(n for n, _ in frames) != names:
        raise ValueError(f'{name}: grid size/frames differ from sprite_layouts.DIALOGUE_SHEETS')
    if len(colors) > WORLD_PALETTE_SIZE:
        raise ValueError(f'{name}: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    width, height = size
    strip = Image.new('RGBA', (width * len(frames), height))
    for index, (_, rows) in enumerate(frames):
        data = bytearray()
        for row in rows:
            for char in row:
                data += bytes(colors[char] + (OPAQUE_ALPHA,)) if colors[char] else bytes(RGBA_CHANNELS)
        strip.paste(Image.frombytes('RGBA', size, bytes(data)), (index * width, 0))
    transparent = any(alpha == 0 for alpha in strip.getchannel('A').get_flattened_data())
    if transparent == (name in OPAQUE_DIALOGUE_IMAGES):
        raise ValueError(f'{name}: transparency does not match OPAQUE_DIALOGUE_IMAGES')
    return strip


def indexed(image, opaque):
    """An exact indexed copy: one palette entry per colour (entry 0 transparent unless opaque), so nothing is merged."""
    pixels = list(image.get_flattened_data())
    palette = [] if opaque else [(0, 0, 0, 0)]
    for pixel in pixels:
        if pixel[ALPHA_CHANNEL] == OPAQUE_ALPHA and pixel not in palette:
            palette.append(pixel)
    if len(palette) > WORLD_PALETTE_SIZE:
        raise ValueError(f'{len(palette)} palette entries')
    index = {colour: number for number, colour in enumerate(palette)}
    result = Image.frombytes('P', image.size, bytes(index[pixel] if pixel[ALPHA_CHANNEL] == OPAQUE_ALPHA else 0 for pixel in pixels))
    result.putpalette([channel for colour in palette for channel in colour[:RGB_CHANNELS]])
    return result


def export(name):
    image = master(name)
    opaque = name in OPAQUE_DIALOGUE_IMAGES
    result = indexed(image, opaque)
    TARGET_DIR.mkdir(parents=True, exist_ok=True)
    target = TARGET_DIR / f'dialogue-{name}.png'
    if opaque:
        result.save(target, bits=WORLD_PALETTE_BITS)
    else:
        result.save(target, bits=WORLD_PALETTE_BITS, transparency=0)
    if Image.open(target).convert('RGBA').tobytes() != image.tobytes():
        raise ValueError(f'{name}: the export changed pixels')
    return image


def contact_sheet(images):
    """Every strip at 3x on the night colour, one per row."""
    width = max(image.width for image in images.values()) * SCALE + PAD * 2
    height = sum(image.height * SCALE + PAD for image in images.values()) + PAD
    sheet = Image.new('RGB', (width, height), NIGHT)
    top = PAD
    for image in images.values():
        big = image.resize((image.width * SCALE, image.height * SCALE), Image.Resampling.NEAREST)
        sheet.paste(big, (PAD, top), big)
        top += big.height + PAD
    sheet.save(PREVIEW)


if __name__ == '__main__':
    exported = {name: export(name) for name in DIALOGUE_SHEETS}
    contact_sheet(exported)
    print(f'{len(exported)} sprites -> {TARGET_DIR.relative_to(ROOT)}')
