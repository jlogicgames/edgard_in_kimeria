#!/usr/bin/env python3
"""Forest trap sprite sheets (#230): hand-drawn pixel grids -> native indexed PNG strips.

Each `art/objects/forest-traps/<name>.grid` is a hand-authored pixel drawing, one character
per pixel (the drawing itself, like an artist's Aseprite file). This module only parses the
grids, validates them against `sprite_layouts.FOREST_TRAP_SHEETS` and writes the native master
and the runtime export; it contains no drawing logic and no ImageDraw/putpixel calls.

Grid format: `size W H`, `color <char> <RRGGBB>` lines (`.` is transparent), then for each
frame `frame <name> [mirror]` or `frame <name> flip <other-frame>` (a horizontally flipped copy) followed by H rows of W characters (with `mirror`, the left W/2
characters of each row: a symmetry aid for round shapes, like an editor's mirror mode). Frames form one horizontal strip.
"""
from pathlib import Path
from PIL import Image
from sprite_layouts import FOREST_TRAP_SHEETS, DECOR_SHEETS, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA

ROOT = Path(__file__).resolve().parents[1]
GRID_DIR = ROOT / 'art/objects/forest-traps'
DECOR_GRID_DIR = ROOT / 'art/objects/decor'
DECOR_PREFIX = 'decor:'  # a sheet name for a decor strip: 'decor:bush' -> decor-bush.png from art/objects/decor/bush.grid
PREVIEW_DIR = ROOT / 'docs/references/forest-traps'
RGBA_CHANNELS = 4
PREVIEW_SCALE = 6
PREVIEW_PADDING = 4
SIZE_ARGS = 2  # size W H
COLOR_ARGS = 2  # color <char> <RRGGBB>
NAME_ARGS = 1  # frame <name>
MIRROR_ARGS = 2  # frame <name> mirror
FLIP_ARGS = 3  # frame <name> flip <other>
HALF = 2
FIRST_LINE = 1
BACKGROUNDS = ((0x0c, 0x0a, 0x1e), (0x3a, 0x12, 0x4e), (0x16, 0x3c, 0x44))  # night, canopy, trunk


def parse(path):
    colors = {'.': None}
    size = None
    frames = []
    for number, raw in enumerate(path.read_text(encoding='utf-8').splitlines(), FIRST_LINE):
        line = raw.rstrip()
        if not line or line.startswith('#'):
            continue
        keyword, *args = line.split()
        if keyword == 'size' and len(args) == SIZE_ARGS:
            size = (int(args[0]), int(args[1]))
        elif keyword == 'color' and len(args) == COLOR_ARGS:
            colors[args[0]] = tuple(bytes.fromhex(args[1]))
        elif keyword == 'frame' and len(args) == FLIP_ARGS and args[1] == 'flip':
            _, _, source_name = args
            source = dict((n, r) for n, r, _ in frames)[source_name]
            frames.append((args[0], [row[::-1] for row in source], False))
        elif keyword == 'frame' and len(args) in (NAME_ARGS, MIRROR_ARGS):
            frames.append((args[0], [], len(args) == MIRROR_ARGS and args[1] == 'mirror'))
        elif frames and size and len(line) == (size[0] // HALF if frames[-1][-1] else size[0]):
            mirrored = frames[-1][-1]
            frames[-1][1].append(line + line[::-1] if mirrored else line)
        else:
            raise ValueError(f'{path.name}:{number}: unexpected line {line!r} (row width must be {size})')
    frames = [(name, rows) for name, rows, _ in frames]
    for name, rows in frames:
        if len(rows) != size[1]:
            raise ValueError(f'{path.name}: frame {name} has {len(rows)} rows, expected {size[1]}')
        for row in rows:
            for char in row:
                if char not in colors:
                    raise ValueError(f'{path.name}: frame {name} uses undefined colour {char!r}')
    return size, colors, frames


def layout(name):
    """(grid path, runtime stem, expected (size, frames)) for a trap name or a `decor:<name>` name."""
    if name.startswith(DECOR_PREFIX):
        short = name[len(DECOR_PREFIX):]
        return DECOR_GRID_DIR / f'{short}.grid', f'decor-{short}', DECOR_SHEETS[short]
    return GRID_DIR / f'{name}.grid', f'trap-{name}', FOREST_TRAP_SHEETS[name]


def sheet_image(name):
    grid, _, (expected_size, expected_frames) = layout(name)
    size, colors, frames = parse(grid)
    if size != expected_size or [f for f, _ in frames] != list(expected_frames):
        raise ValueError(f'{name}: grid size/frames differ from sprite_layouts')
    if len(colors) > WORLD_PALETTE_SIZE - 1:
        raise ValueError(f'{name}: more than {WORLD_PALETTE_SIZE - 1} opaque colours')
    data = bytearray()
    width = size[0] * len(frames)
    for y in range(size[1]):
        for _, rows in frames:
            for char in rows[y]:
                colour = colors[char]
                data += bytes(colour + (OPAQUE_ALPHA,)) if colour else bytes(RGBA_CHANNELS)
    return Image.frombytes('RGBA', (width, size[1]), bytes(data)), len(colors) - 1


def export_sheet(name):
    master, _ = sheet_image(name)
    _, stem, _ = layout(name)
    master_path = ROOT / 'art/objects' / f'{stem}-pixel.png'
    master.save(master_path)
    indexed = master.quantize(colors=WORLD_PALETTE_SIZE, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    if indexed.convert("RGBA").tobytes() != master.tobytes():
        raise ValueError(f'{name}: quantizing changed pixels; reduce the palette to fit 16 entries')
    target = ROOT / 'assets/images/objects' / f'{stem}.png'
    indexed.save(target, bits=WORLD_PALETTE_BITS)
    print(target.relative_to(ROOT))
    return master


def preview(name, master, scale=PREVIEW_SCALE):
    """Contact sheet: the strip at x6 on night, canopy and trunk backgrounds, plus 1x native."""
    width, height = master.size
    pad = PREVIEW_PADDING
    sheet = Image.new('RGB', (width * scale + pad * HALF, (height * scale + pad) * len(BACKGROUNDS) + height + pad * HALF))
    for index, colour in enumerate(BACKGROUNDS):
        top = index * (height * scale + pad)
        sheet.paste(colour, (0, top, sheet.width, top + height * scale + pad))
        big = master.resize((width * scale, height * scale), Image.Resampling.NEAREST)
        sheet.paste(big, (pad, top + pad // HALF), big)
    bottom = len(BACKGROUNDS) * (height * scale + pad)
    sheet.paste(BACKGROUNDS[0], (0, bottom, sheet.width, sheet.height))
    sheet.paste(master, (pad, bottom + pad), master)
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW_DIR / f'{layout(name)[1].replace("trap-", "", 1)}-preview.png')


def export_all(names=None):
    for name in names or [*FOREST_TRAP_SHEETS, *(DECOR_PREFIX + n for n in DECOR_SHEETS)]:
        preview(name, export_sheet(name))


if __name__ == '__main__':
    import sys
    export_all(sys.argv[1:])
