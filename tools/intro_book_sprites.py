#!/usr/bin/env python3
"""The intro book (#536, D133): hand-drawn grids in `art/ui/intro-book` -> two opaque indexed images.

`cover`, `leaf`, `gutter`, `fibres`, `fleuron` and `ribbon` are the drawings (the grid format of the forest traps, see
`forest_trap_sprites.parse`). This module only places them: it stretches the nine-patches (cover, leaf), tiles the fibres,
mirrors the right leaf and lays the pieces at the geometry of `sprite_layouts.INTRO_BOOK_*`. It contains no drawing logic.

    assets/images/ui/intro-book.png     640 x 360, the open book on black, both leaves blank
    assets/images/ui/intro-leaves.png   580 x 298, the left and the right leaf side by side (the leaf in flight)

    python3 tools/intro_book_sprites.py
"""
from pathlib import Path
from PIL import Image
import forest_trap_sprites as grids
from sprite_layouts import (INTRO_BOOK_SCREEN, INTRO_BOOK_COVER, INTRO_BOOK_LEAF_SIZE, INTRO_BOOK_LEAF_ORIGIN_LEFT,
                            INTRO_BOOK_LEAF_ORIGIN_RIGHT, INTRO_BOOK_LEAF_CENTER_X, INTRO_BOOK_IMAGES,
                            HUD_PALETTE_SIZE, HUD_PALETTE_BITS, OPAQUE_ALPHA, RGB_CHANNELS)

ROOT = grids.ROOT
GRID_DIR = ROOT / 'art/ui/intro-book'
TARGET_DIR = ROOT / 'assets/images/ui'
PREVIEW = ROOT / 'docs/references/intro-book.png'
COVER_INSET = 10  # nine-patch inset of cover.grid
LEAF_INSET = 8    # and of leaf.grid
GUTTER_X = 304       # the gutter pieces are 32 px wide, centred on the spine
GUTTER_TOP_Y = 20
GUTTER_BOTTOM_Y = 313
FLEURON_Y = 306
FLEURON_HALF_WIDTH = 20
RIBBON_AT = (60, 312)
BLACK = (0, 0, 0)


def piece(name):
    """Every frame of `name.grid` as an RGBA image, by frame name."""
    size, colors, frames = grids.parse(GRID_DIR / f'{name}.grid')
    out = {}
    for frame, rows in frames:
        image = Image.new('RGBA', size)
        pixels = image.load()
        for y, row in enumerate(rows):
            for x, char in enumerate(row):
                if colors[char]:
                    pixels[x, y] = colors[char] + (OPAQUE_ALPHA,)
        out[frame] = image
    return out


def tile(target, source, box):
    x0, y0, x1, y1 = box
    for y in range(y0, y1, source.height):
        for x in range(x0, x1, source.width):
            target.alpha_composite(source.crop((0, 0, min(source.width, x1 - x), min(source.height, y1 - y))), (x, y))


def nine(target, source, box, inset):
    x0, y0, x1, y1 = box
    width, height = source.size
    columns = [(0, inset, x0, x0 + inset), (inset, width - inset, x0 + inset, x1 - inset), (width - inset, width, x1 - inset, x1)]
    rows = [(0, inset, y0, y0 + inset), (inset, height - inset, y0 + inset, y1 - inset), (height - inset, height, y1 - inset, y1)]
    for sy0, sy1, dy0, dy1 in rows:
        for sx0, sx1, dx0, dx1 in columns:
            tile(target, source.crop((sx0, sy0, sx1, sy1)), (dx0, dy0, dx1, dy1))


def leaf(name):
    """One blank leaf: the nine-patch with the fibres laid over its middle."""
    size = INTRO_BOOK_LEAF_SIZE
    image = Image.new('RGBA', size)
    nine(image, piece('leaf')[name], (0, 0) + size, LEAF_INSET)
    tile(image, piece('fibres')['fibres'], (LEAF_INSET, LEAF_INSET, size[0] - LEAF_INSET, size[1] - LEAF_INSET))
    return image


def book():
    image = Image.new('RGBA', INTRO_BOOK_SCREEN, BLACK + (OPAQUE_ALPHA,))
    nine(image, piece('cover')['cover'], INTRO_BOOK_COVER, COVER_INSET)
    for origin, centre, name in ((INTRO_BOOK_LEAF_ORIGIN_LEFT, INTRO_BOOK_LEAF_CENTER_X[0], 'left'),
                                 (INTRO_BOOK_LEAF_ORIGIN_RIGHT, INTRO_BOOK_LEAF_CENTER_X[1], 'right')):
        image.alpha_composite(leaf(name), origin)
        image.alpha_composite(piece('fleuron')['fleuron'], (centre - FLEURON_HALF_WIDTH, FLEURON_Y))
    gutter = piece('gutter')
    image.alpha_composite(gutter['top'], (GUTTER_X, GUTTER_TOP_Y))
    image.alpha_composite(gutter['bottom'], (GUTTER_X, GUTTER_BOTTOM_Y))
    image.alpha_composite(piece('ribbon')['ribbon'], RIBBON_AT)
    return image


def leaves():
    left, right = leaf('left'), leaf('right')
    strip = Image.new('RGBA', (left.width * 2, left.height))
    strip.alpha_composite(left, (0, 0))
    strip.alpha_composite(right, (left.width, 0))
    return strip


def indexed(image):
    """An exact indexed copy: one palette entry per colour, nothing merged, no transparency."""
    colours = []
    for pixel in image.get_flattened_data():
        if pixel not in colours:
            colours.append(pixel)
    if len(colours) > HUD_PALETTE_SIZE:
        raise ValueError(f'{len(colours)} palette entries')
    index = {colour: number for number, colour in enumerate(colours)}
    result = Image.frombytes('P', image.size, bytes(index[pixel] for pixel in image.get_flattened_data()))
    result.putpalette([channel for colour in colours for channel in colour[:RGB_CHANNELS]])
    return result


def export(name, image):
    if image.size != INTRO_BOOK_IMAGES[name]:
        raise ValueError(f'{name}: {image.size} differs from sprite_layouts.INTRO_BOOK_IMAGES')
    if any(alpha != OPAQUE_ALPHA for alpha in image.getchannel('A').get_flattened_data()):
        raise ValueError(f'{name}: the image must be opaque')
    TARGET_DIR.mkdir(parents=True, exist_ok=True)
    target = TARGET_DIR / f'{name}.png'
    indexed(image).save(target, bits=HUD_PALETTE_BITS)
    if Image.open(target).convert('RGBA').tobytes() != image.tobytes():
        raise ValueError(f'{name}: the export changed pixels')


if __name__ == '__main__':
    whole = book()
    export('intro-book', whole)
    export('intro-leaves', leaves())
    whole.convert('RGB').resize((whole.width * 2, whole.height * 2), Image.Resampling.NEAREST).save(PREVIEW)
    print(f'{len(INTRO_BOOK_IMAGES)} images -> {TARGET_DIR.relative_to(ROOT)}')
