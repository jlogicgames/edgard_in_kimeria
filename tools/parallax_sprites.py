#!/usr/bin/env python3
"""Parallax tilesets (sketch, #236): hand-authored pixel grids -> tileset PNGs and Tiled `.tsx` files.

`art/parallax/<set>/<tileset>.grid` is one tileset (`forest`: sky, far, grove, mid and the foreground fg (pixel
defocus, exported as a Tiled collection of images: every piece a sub-rectangle of one packed sheet, so the game binds one texture, placed as tile objects); `shared`: stars, moon and clouds, used by every world): pieces (modular stamps) of
whole 16 px tiles, one character per pixel (the drawing itself, like an artist's file). This module only parses, packs the
pieces into a sheet and writes files; it contains no drawing logic, no filter and no ImageDraw/putpixel calls. Soft edges
(the defocused foreground, the moon's glow) are drawn in the grid as flat colours with a straight alpha, never computed.

Grid format: `tile N`, `color <char> <RRGGBB[AA]>` lines (`.` is transparent; AA is a straight alpha), then for each piece
`piece <name> <wtiles> <htiles>` followed by htiles*N rows of wtiles*N characters.

Outputs (next to the grid): `out/<set>-<name>.png` and `<set>-<name>.tsx`. A tileset with only opaque colours is an exact
indexed PNG (transparent index 0 when needed); one with partial alpha is RGBA. Not registered in
`sprite_layouts` yet (sketch).
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PARALLAX = ROOT / 'art/parallax'
DIR = PARALLAX / 'forest'
TILESETS = ('forest/sky', 'forest/far', 'forest/grove', 'forest/mid', 'forest/fg', 'shared/stars',
            'shared/moon', 'shared/clouds')
SHEET_COLUMNS = 16
COLLECTIONS = ('forest/fg',)   # tilesets exported as a Tiled collection of images over ONE packed sheet (a sub-rectangle per piece), placed as tile objects
COLLECTION_COLUMNS = 56        # tiles across the packed sheet of a collection: its pieces are wider than SHEET_COLUMNS
RGBA = 4
OPAQUE = 255
HEX_RGBA = 8       # hex digits of RRGGBBAA
ALPHA = 3          # the alpha channel of an RGBA pixel
RGB = 3            # the colour channels of a pixel
PIECE_NAME = 0     # the fields of a parsed piece: [name, wtiles, htiles, options, rows]
PIECE_W = 1
PIECE_H = 2
PIECE_OPTIONS = 3
PIECE_ROWS = 4


def parse(path):
    return parse_text(path.read_text(encoding='utf-8'), path.name)


def parse_text(text, label):
    colors = {'.': None}
    tile = None
    pieces = []  # [name, wtiles, htiles, options, rows]
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.rstrip()
        if not line or line.startswith('#'):
            continue
        word, *args = line.split()
        if word == 'tile':
            tile = int(args[0])
        elif word == 'color':
            colors[args[0]] = tuple(bytes.fromhex(args[1])) + (() if len(args[1]) == HEX_RGBA else (OPAQUE,))
        elif word == 'piece':
            pieces.append([args[PIECE_NAME], int(args[PIECE_W]), int(args[PIECE_H]), args[PIECE_OPTIONS:], []])
        elif pieces and tile and len(line) == pieces[-1][PIECE_W] * tile:
            pieces[-1][PIECE_ROWS].append(line)
        else:
            raise ValueError(f'{label}:{number}: unexpected line (row width must be wtiles*{tile})')
    for name, w, h, _, rows in pieces:
        if len(rows) != h * tile:
            raise ValueError(f'{label}: piece {name} has {len(rows)} rows, expected {h * tile}')
        for row in rows:
            if any(char not in colors for char in row):
                raise ValueError(f'{label}: piece {name} uses an undefined colour')
    return tile, colors, pieces


def columns_of(key):
    """Tiles across the sheet of a tileset."""
    return COLLECTION_COLUMNS if key in COLLECTIONS else SHEET_COLUMNS


def pack(tile, pieces, columns=SHEET_COLUMNS):
    """Shelf-pack pieces left to right, `columns` tiles wide. Returns ({name: (col, row, w, h)}, rows)."""
    spots, col, row, shelf = {}, 0, 0, 0
    for name, w, h, _, _ in pieces:
        if col + w > columns:
            col, row, shelf = 0, row + shelf, 0
        spots[name] = (col, row, w, h)
        col += w
        shelf = max(shelf, h)
    return spots, row + shelf


def piece_image(tile, colors, piece):
    name, w, h, options, rows = piece
    data = bytearray()
    for row in rows:
        for char in row:
            data += bytes(colors[char]) if colors[char] else bytes(RGBA)
    if options:
        raise ValueError(f'piece {name}: unknown options {options} (soft edges are drawn in the grid, not filtered)')
    return Image.frombytes('RGBA', (w * tile, h * tile), bytes(data))


def split(key):
    """'forest/sky' -> (directory, 'forest', 'sky'); a bare name is a forest tileset."""
    world, name = key.split('/') if '/' in key else ('forest', key)
    return PARALLAX / world, world, name


def sheet(key):
    folder, _, name = split(key)
    tile, colors, pieces = parse(folder / f'{name}.grid')
    columns = columns_of(key)
    spots, rows = pack(tile, pieces, columns)
    image = Image.new('RGBA', (columns * tile, rows * tile))
    for piece in pieces:
        col, row, _, _ = spots[piece[0]]
        image.paste(piece_image(tile, colors, piece), (col * tile, row * tile))
    soft = any(c and c[ALPHA] not in (0, OPAQUE) for c in colors.values())
    return tile, spots, image, soft


def layout(key):
    """{piece: (col, row, wtiles, htiles)} and the tile count of the tileset, for building maps. A collection has one tile
    per piece: its spot is (index, 0, w, h) and the gid of the piece is firstgid + index."""
    if key in COLLECTIONS:
        folder, _, name = split(key)
        _, _, pieces = parse(folder / f'{name}.grid')
        return {p[PIECE_NAME]: (i, 0, p[PIECE_W], p[PIECE_H]) for i, p in enumerate(pieces)}, len(pieces)
    tile, spots, image, _ = sheet(key)
    return spots, image.width // tile * (image.height // tile)


def blank_ids(key):
    """Ids of the tiles of a sheet tileset that are fully transparent: a map layer leaves their cells empty (gid 0, nothing drawn)."""
    if key in COLLECTIONS:
        return set()
    tile, _, image, _ = sheet(key)
    columns = image.width // tile
    return {row * columns + col for row in range(image.height // tile) for col in range(columns)
            if not any(image.crop((col * tile, row * tile, col * tile + tile, row * tile + tile)).getchannel('A').get_flattened_data())}


def indexed(image):
    """Exact indexed copy (index 0 transparent when any pixel is), no quantizer: the grid's own colours."""
    pixels = image.get_flattened_data()
    colours = sorted({p[:RGB] for p in pixels if p[ALPHA]})
    transparent = any(not p[ALPHA] for p in pixels)
    first = 1 if transparent else 0
    index = {c: i + first for i, c in enumerate(colours)}
    result = Image.frombytes('P', image.size, bytes(index[p[:RGB]] if p[ALPHA] else 0 for p in pixels))
    result.putpalette(([0, 0, 0] if transparent else []) + [v for c in colours for v in c])
    return result


def tsx(label, tile, spots, image):
    tiles = '\n'.join(
        f' <tile id="{row * SHEET_COLUMNS + col}"><properties><property name="piece" value="{piece} {w}x{h}"/></properties></tile>'
        for piece, (col, row, w, h) in spots.items())
    count = image.width // tile * (image.height // tile)
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<tileset version="1.10" tiledversion="1.12.1" name="{label}" '
            f'tilewidth="{tile}" tileheight="{tile}" tilecount="{count}" columns="{SHEET_COLUMNS}">\n'
            f' <image source="out/{label}.png" width="{image.width}" height="{image.height}"/>\n{tiles}\n</tileset>\n')


def tsx_path(key):
    folder, world, name = split(key)
    return folder / f'{world}-{name}.tsx'


def collection_tsx(label, tile, spots, image):
    """Collection-of-images tileset over one packed sheet: tile id = piece index, each tile the sub-rectangle of `out/<label>.png`
    that holds its piece (Tiled 1.9 tile `x`, `y`, `width`, `height`)."""
    tiles = '\n'.join(
        f' <tile id="{i}" x="{col * tile}" y="{row * tile}" width="{w * tile}" height="{h * tile}">'
        f'<image source="out/{label}.png" width="{image.width}" height="{image.height}"/></tile>'
        for i, (col, row, w, h) in enumerate(spots.values()))
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n<tileset version="1.10" tiledversion="1.12.1" name="{label}" tilewidth="{tile}" '
            f'tileheight="{tile}" tilecount="{len(spots)}" columns="0">\n <grid orientation="orthogonal" width="1" height="1"/>\n{tiles}\n</tileset>\n')


def export_collection(key):
    folder, world, name = split(key)
    label = f'{world}-{name}'
    tile, spots, image, _ = sheet(key)
    (folder / 'out').mkdir(parents=True, exist_ok=True)
    target = folder / 'out' / f'{label}.png'
    image.save(target)
    tsx_path(key).write_text(collection_tsx(label, tile, spots, image), encoding='utf-8')
    print(target.relative_to(ROOT))


def export(key):
    if key in COLLECTIONS:
        return export_collection(key)
    folder, world, name = split(key)
    label = f'{world}-{name}'
    tile, spots, image, soft = sheet(key)
    (folder / 'out').mkdir(parents=True, exist_ok=True)
    target = folder / 'out' / f'{label}.png'
    if soft:
        image.save(target)
    else:
        indexed(image).save(target, **({'transparency': 0} if any(not p[ALPHA] for p in image.get_flattened_data()) else {}))
    tsx_path(key).write_text(tsx(label, tile, spots, image), encoding='utf-8')
    print(target.relative_to(ROOT))


if __name__ == '__main__':
    import sys
    for tileset in sys.argv[1:] or TILESETS:
        export(tileset)
