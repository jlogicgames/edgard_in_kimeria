#!/usr/bin/env python3
"""Hero sprite sheets (#343, D79): the hand-drawn pixel grid art/hero/hero.grid -> assets/images/hero/hero-<sheet>.png.

The grid is the authored drawing (one character per pixel); this module only parses it, checks it against
`sprite_layouts.HERO_SHEETS` and packs the frames, one clip per row of 64x64 cells. It draws nothing itself.

Format: `color <char> <RRGGBB>` lines (`.` is transparent), then frames:
  frame <prefix>.<clip><n>               start from an empty 64x64 cell
  frame <sheet>.<clip><n> from <other>   start from a copy of another frame
  frame <sheet>.<clip><n> flip <other>   start from a horizontal mirror of another frame (about x 31.5, the body axis)
followed by any number of blocks: `at X Y` and rows of pixels placed with their first character at (X, Y + row).
In a block `_` keeps the pixel underneath and `.` clears it, so a frame can be a small authored edit of another
(a turned head, a moved hand). Registration (D79): feet on row 63, body centre on x 31.5.
"""
from pathlib import Path
from PIL import Image
import moon_rim
from sprite_layouts import HERO_SHEETS, HERO_FRAME_PREFIX, HERO_IMAGES, HERO_FRAME_SIZE, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA

ROOT = Path(__file__).resolve().parents[1]
GRID = ROOT / 'art/hero/hero.grid'
KEEP, CLEAR = '_', '.'
RGBA_CHANNELS = 4
N = HERO_FRAME_SIZE
COLOR_ARGS = 2   # color <char> <RRGGBB>
NAME_ARGS = 1    # frame <name>
SOURCE_ARGS = 3  # frame <name> from|flip <other>
AT_ARGS = 2      # at <x> <y>
# Sheets exported with the moon rim (D92, tools/moon_rim.py); empty until #430's night change turns them on.
RIM_SHEETS = frozenset()


class Block:
    """An `at X Y` block: where its next row goes."""
    def __init__(self, x, y):
        self.x, self.y, self.row = x, y, 0


def parse(path=GRID):
    """(colours, {frame name: 64 rows of 64 characters})."""
    colors, frames, current, block = {CLEAR: None}, {}, None, None
    for number, raw in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        line = raw.rstrip()
        if not line or line.startswith('#'):
            continue
        keyword, *args = line.split()
        if keyword == 'color' and len(args) == COLOR_ARGS:
            char, hex_rgb = args
            colors[char] = tuple(bytes.fromhex(hex_rgb))
        elif keyword == 'frame' and len(args) in (NAME_ARGS, SOURCE_ARGS):
            name, *source = args
            if name in frames:
                raise ValueError(f'{path.name}:{number}: frame {name} defined twice')
            if source:
                mode, other = source
                if mode not in ('from', 'flip'):
                    raise ValueError(f'{path.name}:{number}: expected from/flip, found {mode!r}')
                if other not in frames:
                    raise ValueError(f'{path.name}:{number}: {other} is not defined before {name}')
                cells = [list(row[::-1]) if mode == 'flip' else list(row) for row in frames[other]]
            else:
                cells = [[CLEAR] * N for _ in range(N)]
            frames[name] = cells
            current, block = name, None
        elif keyword == 'at' and len(args) == AT_ARGS and current:
            block = Block(int(args[0]), int(args[1]))
        elif block is not None:
            if not 0 <= block.y + block.row < N or not 0 <= block.x or block.x + len(line) > N:
                raise ValueError(f'{path.name}:{number}: row outside the 64x64 cell')
            for i, char in enumerate(line):
                if char == KEEP:
                    continue
                if char not in colors:
                    raise ValueError(f'{path.name}:{number}: undefined colour {char!r}')
                frames[current][block.y + block.row][block.x + i] = char
            block.row += 1
        else:
            raise ValueError(f'{path.name}:{number}: unexpected line {line!r}')
    return colors, {name: [''.join(row) for row in cells] for name, cells in frames.items()}


def frame_name(sheet, clip, n):
    """`r.idle1`: the sheet's short prefix (r, l, ladder), the clip and the 1-based frame number."""
    return f'{HERO_FRAME_PREFIX[sheet]}.{clip}{n}'


def frame_image(colors, rows):
    data = bytearray()
    for row in rows:
        for char in row:
            colour = colors[char]
            data += bytes(colour + (OPAQUE_ALPHA,)) if colour else bytes(RGBA_CHANNELS)
    return Image.frombytes('RGBA', (N, N), bytes(data))


def sheet_images(path=GRID):
    """{sheet: RGBA image}, checked against the layout; unused or missing frames are errors."""
    colors, frames = parse(path)
    if len(colors) - 1 > WORLD_PALETTE_SIZE - 1:
        raise ValueError(f'hero.grid: {len(colors) - 1} opaque colours, at most {WORLD_PALETTE_SIZE - 1}')
    expected = {frame_name(sheet, clip, n) for sheet, clips in HERO_SHEETS.items() for clip, count in clips for n in range(1, count + 1)}
    if set(frames) != expected:
        raise ValueError(f'hero.grid: missing {sorted(expected - set(frames))}, unused {sorted(set(frames) - expected)}')
    images = {}
    for sheet, clips in HERO_SHEETS.items():
        image = Image.new('RGBA', HERO_IMAGES[sheet])
        for row, (clip, count) in enumerate(clips):
            for n in range(count):
                image.paste(frame_image(colors, frames[frame_name(sheet, clip, n + 1)]), (n * N, row * N))
        images[sheet] = moon_rim.rim(image, (N, N)) if sheet in RIM_SHEETS else image
    return images


def indexed(master, colors):
    """`master` as a 4-bit indexed PNG image with the grid's own palette (index 0 transparent): exact, no quantizing."""
    opaque = [c for c in colors.values() if c]
    if len(opaque) > WORLD_PALETTE_SIZE - 1:  # the moon rim (D92) is one more colour: the grid must leave it a slot
        raise ValueError(f'{len(opaque)} opaque colours, at most {WORLD_PALETTE_SIZE - 1} fit a 4-bit sheet')
    index = {c + (OPAQUE_ALPHA,): i + 1 for i, c in enumerate(opaque)}
    pixels = bytes(index.get(p, 0) for p in master.get_flattened_data())
    image = Image.frombytes('P', master.size, pixels)
    image.putpalette([0, 0, 0] + [v for c in opaque for v in c])
    image.info['transparency'] = 0
    if image.convert('RGBA').tobytes() != master.tobytes():
        raise ValueError('indexing changed pixels')
    return image


def export():
    colors, _ = parse()
    for sheet, master in sheet_images().items():
        target = ROOT / f'assets/images/hero/hero-{sheet}.png'
        palette = {**colors, 'moon rim': moon_rim.RIM} if sheet in RIM_SHEETS else colors
        indexed(master, palette).save(target, bits=WORLD_PALETTE_BITS, transparency=0)
        print(target.relative_to(ROOT))


if __name__ == '__main__':
    export()
