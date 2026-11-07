#!/usr/bin/env python3
"""Touch control art (#226, D55): hand-drawn pixel grids -> native indexed PNGs.

`art/hud/touch/<name>.grid` is the drawing (same grid format as the forest traps, see
`forest_trap_sprites.parse`); this module only validates it against
`sprite_layouts.TOUCH_CONTROL_SIZES` and exports `assets/images/HUD/touch/<name>.png`, plus a
contact sheet on the three backgrounds the controls sit on (dark bar, sky, ground). It contains
no drawing logic. The old `Joystick.png`, `JumpButton.png`, `Knob.png` and `PauseButton.png`
stay as they are for any other user.
"""
from pathlib import Path
from PIL import Image
import forest_trap_sprites as grids
from sprite_layouts import TOUCH_CONTROL_SIZES, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA, RGB_CHANNELS
RGBA_CHANNELS = RGB_CHANNELS + 1
LARGEST_SPRITE = 64
HALF = 2
PER_ROW = 10

ROOT = grids.ROOT
GRID_DIR = ROOT / 'art/hud/touch'
TARGET_DIR = ROOT / 'assets/images/HUD/touch'
PREVIEW = ROOT / 'docs/references/touch-controls-art.png'
BACKGROUNDS = ((0x10, 0x10, 0x1c), (0x87, 0xce, 0xeb), (0x7a, 0x4e, 0x2c))  # dark bar, sky, ground
SCALE = 3
PAD = 6


def master(name):
    saved = grids.GRID_DIR
    grids.GRID_DIR = GRID_DIR
    try:
        size, colors, frames = grids.parse(GRID_DIR / f'{name}.grid')
    finally:
        grids.GRID_DIR = saved
    if size != TOUCH_CONTROL_SIZES[name] or len(frames) != 1:
        raise ValueError(f'{name}: grid size/frames differ from sprite_layouts.TOUCH_CONTROL_SIZES')
    if len(colors) > WORLD_PALETTE_SIZE:
        raise ValueError(f'{name}: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    data = bytearray()
    for row in frames[0][1]:
        for char in row:
            data += bytes(colors[char] + (OPAQUE_ALPHA,)) if colors[char] else bytes(RGBA_CHANNELS)
    return Image.frombytes('RGBA', size, bytes(data))


def export(name):
    image = master(name)
    indexed = image.quantize(colors=WORLD_PALETTE_SIZE, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    if indexed.convert('RGBA').tobytes() != image.tobytes():
        raise ValueError(f'{name}: quantizing changed pixels')
    TARGET_DIR.mkdir(parents=True, exist_ok=True)
    indexed.save(TARGET_DIR / f'{name}.png', bits=WORLD_PALETTE_BITS)
    return image


def contact_sheet(images):
    """Blocks of three background strips: dark bar, sky, ground."""
    names = list(images)
    cell = LARGEST_SPRITE * SCALE + PAD
    blocks = [names[i:i + PER_ROW] for i in range(0, len(names), PER_ROW)]
    sheet = Image.new('RGB', (cell * PER_ROW, cell * len(BACKGROUNDS) * len(blocks)))
    for block_index, block in enumerate(blocks):
        for row, colour in enumerate(BACKGROUNDS):
            top = (block_index * len(BACKGROUNDS) + row) * cell
            sheet.paste(colour, (0, top, sheet.width, top + cell))
            for column, name in enumerate(block):
                big = images[name].resize((images[name].width * SCALE, images[name].height * SCALE), Image.Resampling.NEAREST)
                sheet.paste(big, (column * cell + PAD // HALF, top + PAD // HALF + (LARGEST_SPRITE * SCALE - big.height) // HALF), big)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    exported = {name: export(name) for name in TOUCH_CONTROL_SIZES}
    count = len(exported)
    contact_sheet(exported)
    print(f'{count} sprites -> {TARGET_DIR.relative_to(ROOT)}')
