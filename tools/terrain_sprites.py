#!/usr/bin/env python3
"""Forest terrain tileset (#189, #236): the hand-authored grid -> the play tileset of every forest map.

`art/terrain/forest/terrain.grid` is the drawing, one character per pixel. This module only parses it, places the pieces on a
320x112 sheet (20x7 tiles, the tile ids of the retired third-party sheet, which the maps keep), and writes `out/ForestTerrain.png`
and `Forest-new.tsx`; it contains no drawing logic. Grid format: `tile N`, `color <char> <RRGGBB>`, then
`piece <name> <wtiles> <htiles> at <col> <row>` plus rows. The third-party props the maps still use (`prop-<id>`) are pieces too.
"""
from pathlib import Path
from PIL import Image
import parallax_sprites as sprites

ROOT = Path(__file__).resolve().parents[1]
DIR = ROOT / 'art/terrain/forest'
SHEET = (320, 112)
AT_ROW = 2   # `at <col> <row>`: the row follows the column, two words after `at`


def parse(path):
    return sprites.parse_text(path.read_text(encoding='utf-8'), path.name)


def sheet():
    tile, colors, pieces = parse(DIR / 'terrain.grid')
    image = Image.new('RGBA', SHEET)
    for piece in pieces:
        at = piece[sprites.PIECE_OPTIONS]
        col, row = int(at[at.index('at') + 1]), int(at[at.index('at') + AT_ROW])
        # `at <col> <row>` is consumed here; piece_image refuses any option left over
        image.paste(sprites.piece_image(tile, colors, piece[:sprites.PIECE_OPTIONS] + [[]] + piece[sprites.PIECE_ROWS:]), (col * tile, row * tile))
    return image


def export():
    out = DIR / 'out'
    out.mkdir(parents=True, exist_ok=True)
    sheet().save(out / 'ForestTerrain.png')
    (DIR / 'Forest-new.tsx').write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n<tileset version="1.10" tiledversion="1.12.1" name="Forest" tilewidth="16" '
        'tileheight="16" tilecount="140" columns="20">\n <image source="out/ForestTerrain.png" width="320" height="112"/>\n</tileset>\n',
        encoding='utf-8')
    print((out / 'ForestTerrain.png').relative_to(ROOT))


if __name__ == '__main__':
    export()
