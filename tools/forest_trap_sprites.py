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
import moon_rim
from sprite_layouts import FOREST_TRAP_SHEETS, DECOR_SHEETS, PICKUP_SHEETS, FAIRY_SHEETS, MONSTER_SHEETS, WORLD_PALETTE_SIZE, WORLD_PALETTE_BITS, OPAQUE_ALPHA, RGB_CHANNELS

ROOT = Path(__file__).resolve().parents[1]
GRID_DIR = ROOT / 'art/objects/forest-traps'
DECOR_GRID_DIR = ROOT / 'art/objects/decor'
DECOR_PREFIX = 'decor:'  # a sheet name for a decor strip: 'decor:bush' -> decor-bush.png from art/objects/decor/bush.grid
PICKUP_GRID_DIR = ROOT / 'art/objects/pickup'
PICKUP_PREFIX = 'pickup:'  # the shared pickup cue (#345): 'pickup:glow' -> pickup-glow.png from art/objects/pickup/glow.grid
FAIRY_GRID_DIR = ROOT / 'art/objects/fairy'
FAIRY_PREFIX = 'fairy:'  # the fairy helper (#338): 'fairy:body' -> fairy-body.png from art/objects/fairy/body.grid
MONSTER_GRID_DIR = ROOT / 'art/objects/monster'
MONSTER_PREFIX = 'monster:'  # Forest monsters (#327, #328, #329, #331): 'monster:beetle' -> monster-beetle.png from art/objects/monster/beetle.grid
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
# Sheets exported with the moon rim (D92, tools/moon_rim.py), by `layout` name: the active sheets whose ink contour falls
# under the active floor on the night background (check_contrast.py), plus the false honey fungus so the pair stays alike.
RIM_SHEETS = frozenset({'bomb', 'bramble', 'branch', 'deadfall', 'deadfall-bough', 'falling-platform', 'false-honey-fungus',
                        'hive', 'honey-fungus', 'log', 'monster:beetle', 'monster:spider', 'monster:spider-wall', 'monster:snail', 'monster:wolf', 'perch', 'puffball', 'root', 'rotten-perch', 'stone-lip', 'stump',
                        'thorn-root', 'thorn-tuft', 'fairy:body', 'fairy:glint'})
# Grid chars the rim never repaints, per sheet: a cue drawn in ink on a lit edge (#362: the falling platform's crack), and the
# small glint's side points (`K`, the ink), so its rim is the top point alone and half its contour stays dark.
RIM_KEEP = {'falling-platform': 'c', 'fairy:glint': 'K'}
# The fairy (#338, D82, #236) is no moon-rimmed trap: she carries her own light. Her contour is lit from above in her lantern
# white `f2fff4` (already in her palette, so no new entry), and only from above, so her sides and underside keep the ink:
# half light, half dark reads both on the near-black play band (lifted by her own glow) and over the moon and the clouds.
FAIRY_RIM = (0xf2, 0xff, 0xf4)
# The wolf (#331, D115) is lit from above only: its long back and head take the rim, its sides keep their ink. A full rim on a
# 38 px body measured outline 0.52 to 0.74, far louder than the other danger-family monsters (0.30 to 0.34); from above only
# it measures 0.32 to 0.37 at the monster spawns (2026-11-21), the family's loudness.
RIM_STYLE = {'fairy:body': (FAIRY_RIM, moon_rim.UP_SIDE), 'fairy:glint': (FAIRY_RIM, moon_rim.UP_SIDE), 'monster:wolf': (moon_rim.RIM, moon_rim.UP_SIDE)}
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
    """(grid path, runtime stem, expected (size, frames)) for a trap name or a `decor:` / `pickup:` / `fairy:` / `monster:` name."""
    if name.startswith(MONSTER_PREFIX):
        short = name[len(MONSTER_PREFIX):]
        return MONSTER_GRID_DIR / f'{short}.grid', f'monster-{short}', MONSTER_SHEETS[short]
    if name.startswith(FAIRY_PREFIX):
        short = name[len(FAIRY_PREFIX):]
        return FAIRY_GRID_DIR / f'{short}.grid', f'fairy-{short}', FAIRY_SHEETS[short]
    if name.startswith(PICKUP_PREFIX):
        short = name[len(PICKUP_PREFIX):]
        return PICKUP_GRID_DIR / f'{short}.grid', f'pickup-{short}', PICKUP_SHEETS[short]
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
    image = Image.frombytes('RGBA', (width, size[1]), bytes(data))
    if name in RIM_SHEETS:  # D92: the lit contour recoloured, one more palette entry (export_sheet checks the 16)
        chars = RIM_KEEP.get(name, '')
        keep = {(index * size[0] + x, y) for index, (_, rows) in enumerate(frames) for y, row in enumerate(rows)
                for x, char in enumerate(row) if char in chars}
        colour, sides = RIM_STYLE.get(name, (moon_rim.RIM, moon_rim.LIT_SIDES))
        return moon_rim.rim(image, size, colour=colour, keep=keep, sides=sides), len(colors)
    return image, len(colors) - 1


def exact_indexed(name, master):
    """`master` indexed with its own colours (index 0 transparent), for a palette the octree quantizer cannot keep exact."""
    opaque = sorted({p[:RGB_CHANNELS] for p in master.get_flattened_data() if p[RGB_CHANNELS]})
    if len(opaque) > WORLD_PALETTE_SIZE - 1:
        raise ValueError(f'{name}: {len(opaque)} opaque colours; reduce the palette to fit {WORLD_PALETTE_SIZE} entries')
    index = {c + (OPAQUE_ALPHA,): i + 1 for i, c in enumerate(opaque)}
    image = Image.frombytes('P', master.size, bytes(index.get(p, 0) for p in master.get_flattened_data()))
    image.putpalette([0, 0, 0] + [v for c in opaque for v in c])
    image.info['transparency'] = 0
    if image.convert('RGBA').tobytes() != master.tobytes():
        raise ValueError(f'{name}: exact indexing changed pixels')
    return image


def export_sheet(name):
    master, _ = sheet_image(name)
    _, stem, _ = layout(name)
    master_path = ROOT / 'art/objects' / f'{stem}-pixel.png'
    master.save(master_path)
    indexed = master.quantize(colors=WORLD_PALETTE_SIZE, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    if indexed.convert("RGBA").tobytes() != master.tobytes():
        indexed = exact_indexed(name, master)  # the octree merges close colours (the D92 night decor palettes)
    target = ROOT / 'assets/images/objects' / f'{stem}.png'
    indexed.save(target, bits=WORLD_PALETTE_BITS, **({'transparency': 0} if 'transparency' in indexed.info else {}))
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
    for name in names or [*FOREST_TRAP_SHEETS, *(DECOR_PREFIX + n for n in DECOR_SHEETS), *(PICKUP_PREFIX + n for n in PICKUP_SHEETS),
                          *(FAIRY_PREFIX + n for n in FAIRY_SHEETS), *(MONSTER_PREFIX + n for n in MONSTER_SHEETS)]:
        preview(name, export_sheet(name))


if __name__ == '__main__':
    import sys
    export_all(sys.argv[1:])
