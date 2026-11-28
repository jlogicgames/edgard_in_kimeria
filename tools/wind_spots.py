#!/usr/bin/env python3
"""Leaf patches of the wind's gust glints (#593, D163; docs/ART_DIRECTION.md, "Wind in the crowns"): reads the art, paints nothing.

    python3 tools/wind_spots.py            # bake assets/wind/forest-spots.txt from the grids
    python3 tools/wind_spots.py --check    # fail on a stale file, on a sheet that differs from its grid, or on a glint too far from its leaf

Colour alone cannot tell a leaf from a trunk or the undergrowth, so every grid types where its leaves are: a line
`leaves <x> <y> <w> <h> [spots]` under its `frame` (a decor grid, frame pixels) or `piece` (a parallax grid, piece pixels); the
optional spot count is for the mid and grove layers, where a cluster split across two pieces shares its number. A decor grid
with at least one box is a crown sheet (`decor-<grid name>`); a new crown is picked up as soon as it has a box.

A spot is a patch of leaf: one of the depth's typed patch masks (PATCHES below: small ragged leaf clusters) laid on the art, of
which only the pixels of the depth's leaf keys are kept, so a patch is never over bark, sky or a shadow, and takes the ragged
edge of the leaves under it. A patch stands wholly inside its box (and inside its 16 px tile on the layers) and keeps at least
the depth's share of its mask. Patches are taken in a fixed pseudo-random order, their top-lefts never closer than the depth's
spacing: up to a fixed number per box on the mid and grove layers (one box is one leaf cluster or spruce; a small one holds
fewer), and on a crown frame one per so many leaf pixels of its boxes, so a bigger crown carries the same wave.

The check also holds the card's colour rule against the runtime PNGs: the OKLab distance between each glint colour of
`effects/CrownGlintFx.java` and every pixel of every patch stays within the depth's limit.
"""
import argparse
import hashlib
import math
import re
import sys
from collections import namedtuple
from pathlib import Path
from PIL import Image
import forest_trap_sprites as objects
import parallax_sprites as layers
from check_contrast import oklab

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'assets/wind/forest-spots.txt'
DECOR_GRIDS = ROOT / 'art/objects/decor'
DECOR_IMAGES = ROOT / 'assets/images/objects'
LAYER_GRIDS = ROOT / 'art/parallax/forest'
LAYER_IMAGES = ROOT / 'assets/images/parallax'
EFFECT = ROOT / 'core/src/main/java/com/jlogicsoftware/kimeria/effects/CrownGlintFx.java'
BOXES_KEYWORD = 'leaves'
CROWN_PIXELS = 150  # leaf pixels of a crown per patch
MASK = '#'
RUN_WIDTH = 2  # a run is [dx, dy, width]
BOX_ARGS = 4  # leaves x y w h
BOX_ARGS_COUNTED = 5  # leaves x y w h spots
SECTION_KEYWORDS = ('frame', 'piece')
TILE = 16
OPAQUE = 255
RGB = 3
ALPHA = 3
# The patch masks, typed: `#` is a pixel of the glint's ragged leaf cluster (about 10x7 on a crown, smaller farther away).
PATCHES = {
    'crown': (
        ('...###....',
         '.######...',
         '#########.',
         '.#########',
         '..#######.',
         '....###...',
         '.....#....'),
        ('.....##...',
         '..######..',
         '.########.',
         '#########.',
         '.######...',
         '##.##.....'),
        ('..##......',
         '.#####.##.',
         '##########',
         '.########.',
         '...######.',
         '......##..'),
        ('....####..',
         '##.######.',
         '.#########',
         '..########',
         '.#####.#..',
         '..##......'),
    ),
    'mid': (
        ('....###....',
         '.#########.',
         '##########.',
         '.##########',
         '..########.',
         '...#####...',
         '.....##....'),
        ('......###..',
         '..#######..',
         '.#########.',
         '###########',
         '########.#.',
         '..####.....',
         '.##........'),
        ('..##.......',
         '.#####.##..',
         '##########.',
         '.##########',
         '..#########',
         '....######.',
         '.......##..'),
    ),
    'grove': (
        ('...###...',
         '.#######.',
         '#########',
         '..#####..'),
        ('......###',
         '..######.',
         '.######..',
         '####.....'),
        ('##.......',
         '.#####...',
         '..######.',
         '.....####'),
    ),
}
# keys: the leaf keys of the depth's grid, never bark. A crown's are the leaf green m, l and the olive tips n (D162's palette): its
# lit tops p and o would hide the glint (0.07 and 0.04 from it), and the contour k, the shades d and q lie too far under it for the
# fleck (0.17 to 0.23). cover: the least share of a mask that must lie on leaf. spacing: the least distance between two patches'
# top-lefts, px. per_box: the most patches per box (None: one per `pixels` leaf pixels). limit: the largest OKLab distance of a glint colour
# from a leaf pixel under it. colours: the Java constants of the depth's glints.
Depth = namedtuple('Depth', 'keys cover spacing per_box pixels limit colours')
DEPTHS = {
    'crown': Depth('mln', 0.75, 9, None, CROWN_PIXELS, 0.17, ('CROWN_LEAF', 'CROWN_FLECK')),
    'mid': Depth('456789', 0.6, 7, 7, None, 0.18, ('MID_LEAF', 'MID_FLECK')),
    'grove': Depth('789a', 0.5, 7, 7, None, 0.14, ('GROVE_LEAF',)),
}
LAYER_DEPTHS = ('mid', 'grove')
HEADER = ('# Leaf patches of the wind\'s gust glints (#593). Written by tools/wind_spots.py from the grids\' `leaves` boxes; do not edit.\n'
          '# sheet <runtime image> <depth> <frame or image width> <height>, then one patch per line: x y of its top-left, then its\n'
          '# rows of leaf pixels as `dx dy width` triples from that corner.\n')

# One runtime sheet: its grid's pixels (pixels(x, y) -> colour key or None, in sheet coordinates), boxes in sheet coordinates.
Sheet = namedtuple('Sheet', 'name depth width height image pixels colours boxes')


def leaf_boxes(text, label):
    """{frame or piece name: [(x, y, w, h, spots or None), ...]} of a grid's `leaves` lines, in the order typed."""
    boxes, section = {}, None
    for number, raw in enumerate(text.splitlines(), 1):
        words = raw.split()
        if not words:
            continue
        if words[0] in SECTION_KEYWORDS and len(words) > 1:
            section = words[1]
        elif words[0] == BOXES_KEYWORD:
            args = words[1:]
            if section is None or len(args) not in (BOX_ARGS, BOX_ARGS_COUNTED) or not all(w.isdigit() for w in args):
                raise ValueError(f'{label}:{number}: expected `leaves x y w h [spots]` under a frame or a piece')
            values = [int(w) for w in args]
            boxes.setdefault(section, []).append((*values[:BOX_ARGS], values[BOX_ARGS] if len(values) == BOX_ARGS_COUNTED else None))
    return boxes


def checked(box, width, height, label):
    x, y, w, h = box[:BOX_ARGS]
    if w < 1 or h < 1 or x + w > width or y + h > height:
        raise ValueError(f'{label}: leaves box {box[:BOX_ARGS]} does not fit {width}x{height}')
    return box


def crown_sheets():
    for path in sorted(DECOR_GRIDS.glob('*.grid')):
        name = path.stem
        typed = leaf_boxes(path.read_text(encoding='utf-8'), path.name)
        if not typed:
            continue
        (width, height), colours, frames = objects.parse(path)
        frame, rows = frames[0]
        boxes = typed.get(frame)
        if not boxes:
            raise ValueError(f'{path.name}: leaves boxes must stand under the first frame, {frame}')
        for box in boxes:
            checked(box, width, height, path.name)
        yield Sheet(f'decor-{name}', 'crown', width, height, DECOR_IMAGES / f'decor-{name}.png',
                    lambda x, y, rows=rows: rows[y][x], colours, boxes)


def layer_sheet(depth):
    path = LAYER_GRIDS / f'{depth}.grid'
    text = path.read_text(encoding='utf-8')
    tile, colours, pieces = layers.parse_text(text, path.name)
    spots, rows = layers.pack(tile, pieces)
    typed = leaf_boxes(text, path.name)
    placed, boxes = [], []
    for name, w, h, _, piece_rows in pieces:
        column, row = spots[name][0], spots[name][1]
        placed.append((column * tile, row * tile, w * tile, h * tile, piece_rows))
        for box in typed.get(name, ()):
            x, y, bw, bh, count = checked(box, w * tile, h * tile, f'{path.name}: {name}')
            boxes.append((column * tile + x, row * tile + y, bw, bh, count))

    def pixel(x, y):
        for left, top, width, height, piece_rows in placed:
            if left <= x < left + width and top <= y < top + height:
                return piece_rows[y - top][x - left]
        return None

    return Sheet(f'forest-{depth}', depth, layers.SHEET_COLUMNS * tile, rows * tile, LAYER_IMAGES / f'forest-{depth}.png',
                 pixel, colours, boxes)


def digest(sheet_name, spot):
    """A fixed pseudo-random number of a patch place: its mask and its turn in the picking order."""
    return hashlib.sha256(f'{sheet_name}:{spot[0]}:{spot[1]}'.encode()).digest()


def patch_at(sheet, x, y):
    """The leaf pixels (dx, dy) of the mask this place takes, laid with its top-left at (x, y); None when too little of it is leaf."""
    depth = DEPTHS[sheet.depth]
    masks = PATCHES[sheet.depth]
    mask = masks[digest(sheet.name, (x, y))[-1] % len(masks)]
    wanted = [(dx, dy) for dy, row in enumerate(mask) for dx, char in enumerate(row) if char == MASK]
    leaf = [(dx, dy) for dx, dy in wanted if sheet.pixels(x + dx, y + dy) in depth.keys]
    if len(leaf) < depth.cover * len(wanted):
        return None
    left, top = min(dx for dx, _ in leaf), min(dy for _, dy in leaf)
    return (x + left, y + top), [(dx - left, dy - top) for dx, dy in leaf]


def candidates(sheet, box):
    """{top-left: leaf pixels} of the patches wholly inside `box` (and, on a layer, inside one tile)."""
    x0, y0, w, h = box[:BOX_ARGS]
    masks = PATCHES[sheet.depth]
    width = max(len(m[0]) for m in masks)
    height = max(len(m) for m in masks)
    found = {}
    for y in range(y0, y0 + h - height + 1):
        for x in range(x0, x0 + w - width + 1):
            if sheet.depth in LAYER_DEPTHS and (x % TILE > TILE - width or y % TILE > TILE - height):
                continue
            patch = patch_at(sheet, x, y)
            if patch:
                found[patch[0]] = patch[1]
    return found


def pick(sheet_name, found, count, spacing, taken):
    """Adds to `taken` ({top-left: pixels}) up to `count` of the patches `found`, in the fixed order, none closer than `spacing`."""
    added = 0
    for spot in sorted(found, key=lambda s: digest(sheet_name, s)):
        if added == count:
            break
        if all(math.dist(spot, other) >= spacing for other in taken):
            taken[spot] = found[spot]
            added += 1
    return added


def leaf_pixels(sheet, boxes):
    """How many leaf pixels the boxes hold together."""
    keys = DEPTHS[sheet.depth].keys
    inside = {(x, y) for x0, y0, w, h, _ in boxes for y in range(y0, y0 + h) for x in range(x0, x0 + w)}
    return sum(sheet.pixels(x, y) in keys for x, y in inside)


def spots_of(sheet):
    """[((x, y), [(dx, dy), ...]), ...] of a sheet's patches, in reading order."""
    depth = DEPTHS[sheet.depth]
    taken = {}
    if depth.per_box is None:
        count = max(1, round(leaf_pixels(sheet, sheet.boxes) / depth.pixels))
        found = {}
        for box in sheet.boxes:
            found.update(candidates(sheet, box))
        pick(sheet.name, found, count, depth.spacing, taken)
        if len(taken) < count:
            raise ValueError(f'{sheet.name}: only {len(taken)} of {count} leaf patches fit its boxes')
    else:
        for box in sheet.boxes:
            wanted = box[BOX_ARGS] or depth.per_box
            if not pick(sheet.name, candidates(sheet, box), wanted, depth.spacing, taken):
                raise ValueError(f'{sheet.name}: leaves box {box[:BOX_ARGS]} holds no leaf patch')
    return sorted(taken.items(), key=lambda s: (s[0][1], s[0][0]))


def runs(pixels):
    """The pixels (dx, dy) as horizontal runs (dx, dy, width), row by row."""
    result = []
    for dx, dy in sorted(pixels, key=lambda p: (p[1], p[0])):
        if result and result[-1][1] == dy and result[-1][0] + result[-1][RUN_WIDTH] == dx:
            result[-1][RUN_WIDTH] += 1
        else:
            result.append([dx, dy, 1])
    return result


def all_sheets():
    return [*crown_sheets(), *(layer_sheet(d) for d in LAYER_DEPTHS)]


def baked(sheets):
    """[(sheet, patches)], computed once."""
    return [(sheet, spots_of(sheet)) for sheet in sheets]


def bake(patched):
    lines = [HEADER]
    for sheet, spots in patched:
        lines.append(f'sheet {sheet.name} {sheet.depth} {sheet.width} {sheet.height}\n')
        for (x, y), pixels in spots:
            lines.append(' '.join(str(v) for v in (x, y, *(n for run in runs(pixels) for n in run))) + '\n')
    return ''.join(lines)


def glint_colours(source):
    """{constant: (r, g, b)} of the `NAME = 0xRRGGBB` glint colours in the effect's source."""
    names = {name for depth in DEPTHS.values() for name in depth.colours}
    found = {}
    for name in sorted(names):
        match = re.search(rf'\b{name}\s*=\s*0x([0-9a-fA-F]{{6}})\b', source)
        if not match:
            raise ValueError(f'{EFFECT.name}: no glint colour {name} = 0xRRGGBB')
        found[name] = tuple(bytes.fromhex(match.group(1)))
    return found


def colour_errors(patched, colours, load=Image.open):
    """The errors of the runtime PNGs against the grids and the distance limits, and {depth: (least, largest) distance}."""
    errors, ranges = [], {}
    for sheet, spots in patched:
        depth = DEPTHS[sheet.depth]
        image = load(sheet.image).convert('RGBA')
        for (x, y), pixels in spots:
            for i, j in ((x + dx, y + dy) for dx, dy in pixels):
                pixel = image.getpixel((i, j))
                typed = sheet.colours[sheet.pixels(i, j)]
                if pixel[ALPHA] != OPAQUE or pixel[:RGB] != tuple(typed[:RGB]):
                    errors.append(f'{sheet.name}: pixel ({i}, {j}) of the image differs from its grid; re-export the sheet')
                    continue
                for name in depth.colours:
                    distance = math.dist(oklab(colours[name]), oklab(pixel))
                    low, high = ranges.get(sheet.depth, (distance, distance))
                    ranges[sheet.depth] = (min(low, distance), max(high, distance))
                    if distance > depth.limit:
                        errors.append(f'{sheet.name}: {name} is {distance:.3f} from the leaf at ({i}, {j}), over the {sheet.depth} limit {depth.limit}')
    return errors, ranges


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--check', action='store_true', help='compare with the committed file instead of writing it')
    args = parser.parse_args(argv)
    patched = baked(all_sheets())
    text = bake(patched)
    errors, ranges = colour_errors(patched, glint_colours(EFFECT.read_text(encoding='utf-8')))
    if args.check:
        if not OUT.exists() or OUT.read_text(encoding='utf-8') != text:
            errors.append(f'{OUT.relative_to(ROOT)} is stale: run python3 tools/wind_spots.py')
    elif not errors:
        OUT.parent.mkdir(parents=True, exist_ok=True)
        OUT.write_text(text, encoding='utf-8')
    for sheet, spots in patched:
        sizes = sorted(len(pixels) for _, pixels in spots)
        print(f'{sheet.name}: {len(spots)} patches of {sizes[0]} to {sizes[-1]} px (median {sizes[len(sizes) // 2]}) in {len(sheet.boxes)} leaves boxes')
    for name, (low, high) in ranges.items():
        print(f'{name}: glint to leaf OKLab distance {low:.3f} to {high:.3f} (limit {DEPTHS[name].limit})')
    for error in errors:
        print('wind spots:', error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
