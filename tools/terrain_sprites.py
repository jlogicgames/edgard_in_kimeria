#!/usr/bin/env python3
"""Forest terrain tileset (sketch, #189): the hand-authored grid -> a drop-in replacement of the third-party Tileset.png.

`art/terrain/forest/terrain.grid` is the drawing, one character per pixel. This module only parses it, places the pieces on the
same 320x112 sheet at the same tile ids as `assets/images/Tileset/Tileset.png`, and writes `out/Tileset.png` and `Forest-new.tsx`;
it contains no drawing logic. Grid format: `tile N`, `color <char> <RRGGBB>`, `keep <ids>` (old tile ids copied unchanged from the
third-party sheet: props the maps use that are not terrain), then `piece <name> <wtiles> <htiles> at <col> <row>` plus rows.
"""
from pathlib import Path
from PIL import Image
import parallax_sprites as sprites

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / 'art/terrain/forest'
OLD = ROOT / 'assets/images/Tileset/Tileset.png'
SHEET = (320, 112)
COLUMNS = 20
AT_ROW = 2   # `at <col> <row>`: the row follows the column, two words after `at`


def parse(path):
    lines, keep = [], []
    for raw in path.read_text(encoding='utf-8').splitlines():
        if raw.startswith('keep '):
            keep = [int(v) for v in raw.split()[1:]]
        else:
            lines.append(raw)
    tile, colors, pieces = sprites.parse_text('\n'.join(lines) + '\n', path.name)
    return tile, colors, pieces, keep


def sheet():
    tile, colors, pieces, keep = parse(DIR / 'terrain.grid')
    image = Image.new('RGBA', SHEET)
    old = Image.open(OLD).convert('RGBA')
    for tid in keep:
        x, y = tid % COLUMNS * tile, tid // COLUMNS * tile
        image.paste(old.crop((x, y, x + tile, y + tile)), (x, y))
    for piece in pieces:
        at = piece[sprites.PIECE_OPTIONS]
        col, row = int(at[at.index('at') + 1]), int(at[at.index('at') + AT_ROW])
        # `at <col> <row>` is consumed here; piece_image refuses any option left over
        image.paste(sprites.piece_image(tile, colors, piece[:sprites.PIECE_OPTIONS] + [[]] + piece[sprites.PIECE_ROWS:]), (col * tile, row * tile))
    return image


def export():
    out = DIR / 'out'
    out.mkdir(parents=True, exist_ok=True)
    sheet().save(out / 'Tileset.png')
    (DIR / 'Forest-new.tsx').write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n<tileset version="1.10" tiledversion="1.12.1" name="Forest" tilewidth="16" '
        'tileheight="16" tilecount="140" columns="20">\n <image source="out/Tileset.png" width="320" height="112"/>\n</tileset>\n',
        encoding='utf-8')
    print((out / 'Tileset.png').relative_to(ROOT))


if __name__ == '__main__':
    export()
