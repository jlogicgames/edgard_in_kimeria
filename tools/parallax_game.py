#!/usr/bin/env python3
"""Installs the Forest parallax sketch into the game (#236): assets and the layers of `assets/tiles/forest-1.tmx`.

    python3 tools/parallax_game.py            # copy the assets and (re)write the parallax layers of forest-1.tmx

What it does, idempotently (a second run changes nothing):
- copies the exported parallax tilesets (`art/parallax/*/out`) to `assets/images/parallax/` and writes their `.tsx` files to
  `assets/tiles/parallax/` (image paths rewritten); the terrain sketch (`art/terrain/forest/out/Tileset.png`, the same 140 tile ids)
  becomes `assets/images/Tileset/ForestTerrain.png` with its own `ForestTerrain.tsx`, which forest-1 uses as its play tileset (forest-0 and
  forest-2 keep `Forest.tsx` and the third-party `Tileset.png`, which the terrain exporter also reads for its `keep` tiles);
- writes into forest-1.tmx the map-wide scenery layers (sky, stars, moon, clouds, far, grove, mid, each as wide as its
  parallax factor needs: the camera reaches x = map width - 640, a layer of factor f is seen up to f * that + 640), `play-above` (1 row,
  `offsety` -16) and `play-below` (the deep soil, 1 row, `offsety` = the room height; the bleed is what the camera top's 0 to 8 px range and the shake need, D111), turns the Background's bottom ground row into its
  buried variant, and adds one `foreground-<room>` object group per room (tile objects relative to the room, `roomx` its x in the map).
  Layers with a `parallaxx`/`parallaxy` attribute or the property `scenery` are scenery to the game parser (it keeps them out of the
  play layer and of collision); the foreground is every object group whose name starts with `foreground`.
The game draws them with `world/ParallaxRenderer`; `python3 tools/parallax_mock.py --check --game` checks the foreground of every
room that has one against the map's objects (rule B, cover time). The pieces of each room are the lists below.
"""
import re
import shutil
import sys
from types import SimpleNamespace
from pathlib import Path
import parallax_mock as pm
import parallax_sprites as sprites

ROOT = Path(__file__).resolve().parents[1]
TILES = ROOT / 'assets/tiles'
IMAGES = ROOT / 'assets/images/parallax'
MAP = TILES / 'forest-1.tmx'
TERRAIN_FROM = ROOT / 'art/terrain/forest/out/Tileset.png'
TERRAIN_TO = ROOT / 'assets/images/Tileset/ForestTerrain.png'
TERRAIN_TSX = TILES / 'ForestTerrain.tsx'   # forest-1's play tileset: the terrain sketch (forest-0 and forest-2 keep Forest.tsx)
FOREST_FIRST_GID, FOREST_TILES = 1, 140          # Forest.tsx: the play tileset, the first of the map
SCENERY = ('sky', 'stars', 'moon', 'clouds', 'far', 'grove', 'mid', 'play-above', 'play-below')
EDGE_COLUMNS = 2                                  # spare columns past what a layer's factor needs (the camera is fractional)
# the foreground of each room of forest-1 that has one: room name -> pieces (piece, x, top y), x relative to the room
# #500 batch 3 (D110) split the 1280 px trap course at x 640: the sketch's pieces left of x 600 stay with `forest-1-escalators` (x 0-640);
# the ones from x 600 on move to `forest-1-traps` (x 640-1280) 640 px to the left, since pieces are relative to their room. The two
# `edge-*` pieces stood at x 1568 and 1600, which the old room's camera (up to x 640, factor 1.6) carried to about x 544 and 576 on screen at
# the room's end; in a one-screen room the camera never moves, so they are placed there directly, at x 560 and 592, framing its right edge.
ESCALATORS_FOREGROUND = tuple(p for p in pm.FOREGROUND if p[1] < 600)
TRAPS_FOREGROUND = (tuple((name, x - 640, top) for name, x, top in pm.FOREGROUND if 600 <= x < 1280)
                    + tuple((name, x - 1008, top) for name, x, top in pm.FOREGROUND if name.startswith('edge-')))
GAME_FOREGROUND = {'forest-1-escalators': ESCALATORS_FOREGROUND, 'forest-1-traps': TRAPS_FOREGROUND, 'forest-1-mob': pm.CALM_FOREGROUND}


def label(key):
    world, name = key.split('/')
    return f'{world}-{name}'


def install_assets():
    for key in sprites.TILESETS:
        folder, world, name = sprites.split(key)
        text = sprites.tsx_path(key).read_text(encoding='utf-8')
        (TILES / 'parallax').mkdir(parents=True, exist_ok=True)
        (TILES / 'parallax' / f'{label(key)}.tsx').write_text(text.replace('source="out/', 'source="../../images/parallax/'), encoding='utf-8')
        IMAGES.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(folder / 'out' / f'{label(key)}.png', IMAGES / f'{label(key)}.png')
    shutil.copyfile(TERRAIN_FROM, TERRAIN_TO)
    TERRAIN_TSX.write_text((TILES / 'Forest.tsx').read_text(encoding='utf-8').replace('Tileset/Tileset.png', 'Tileset/ForestTerrain.png')
                           .replace('name="Forest"', 'name="ForestTerrain"'), encoding='utf-8')


def columns_for(factor, map_columns):
    """Columns a layer of `factor` needs over a map `map_columns` wide: the camera reaches x = map width - view width."""
    if factor == 0:
        return pm.VIEW[0] // pm.TILE + 1 + EDGE_COLUMNS - 1
    reach = factor * (map_columns * pm.TILE - pm.VIEW[0]) + pm.VIEW[0]
    return -(-int(reach) // pm.TILE) + EDGE_COLUMNS


def background_rows(text):
    match = re.search(r'(<layer [^>]*name="Background"[^>]*>\s*<data encoding="csv">\s*)(.*?)(</data>)', text, re.S)
    rows = [[int(v) for v in line.strip().rstrip(',').split(',')] for line in match.group(2).strip().splitlines()]
    return match, rows


def build(text):
    """The text of forest-1.tmx with the parallax layers (re)written."""
    text = re.sub(r' ?<layer [^>]*name="(?:%s)"[^>]*>.*?</layer>\n?' % '|'.join(SCENERY), '', text, flags=re.S)
    text = re.sub(r' <objectgroup [^>]*name="foreground[^"]*"[^>]*>.*?</objectgroup>\n?', '', text, flags=re.S)
    text = re.sub(r' <tileset firstgid="\d+" source="parallax/[^"]*"/>\n', '', text)
    match, rows = background_rows(text)
    map_columns = len(rows[0])
    next_layer = max(int(v) for v in re.findall(r'<(?:layer|objectgroup) id="(\d+)"', text)) + 1
    next_object = max(int(v) for v in re.findall(r'<object id="(\d+)"', text)) + 1
    first, info, sets = FOREST_FIRST_GID + FOREST_TILES, {}, []
    for key in sprites.TILESETS:
        spots, count = sprites.layout(key)
        info[key] = (first, spots, sprites.blank_ids(key))
        sets.append(f' <tileset firstgid="{first}" source="parallax/{label(key)}.tsx"/>\n')
        first += count
    bled = pm.play_bleed(rows, FOREST_FIRST_GID)    # the Background's buried bottom row and the deep soil rows below
    rows[pm.ROWS - 1] = bled[pm.ROWS - 1]
    layers = []
    def layer(name, width, grid, attrs='', props=''):
        nonlocal next_layer
        extra = f'\n  <properties>{props}</properties>' if props else ''
        layers.append(f' <layer id="{next_layer}" name="{name}" width="{width}" height="{len(grid)}"{attrs}>{extra}\n'
                      f'  <data encoding="csv">\n{pm.csv(grid)}\n</data>\n </layer>\n')
        next_layer += 1
    for name, key, px, py, props, offset_y in pm.LAYERS:
        if key is None or key in sprites.COLLECTIONS or key == 'play-above':
            continue
        width = 64 if props.get('wrapx') else columns_for(px, map_columns)
        grid = pm.Layer(info[key], width, pm.layer_rows(key))
        pm.BUILDERS[key](grid)
        attrs = (f' parallaxx="{px}" parallaxy="{py}"' if px else ' parallaxx="0" parallaxy="0"') + (f' offsety="{offset_y}"' if offset_y else '')
        layer(name, width, grid.rows, attrs, ''.join(f'<property name="{k}" type="float" value="{v}"/>' for k, v in props.items()))
    scenery = '<property name="scenery" type="bool" value="true"/>'
    layer('play-above', map_columns, pm.play_above(rows), f' offsety="{-pm.PLAY_ABOVE * pm.TILE}"', scenery)
    layer('play-below', map_columns, bled[pm.ROWS:], f' offsety="{pm.ROOM_H}"', scenery)
    text = text[:match.start(2)] + pm.csv(rows) + '\n' + text[match.end(2):]
    first_info = info['forest/fg']
    groups = []
    for room, pieces in GAME_FOREGROUND.items():
        room_x = int(float(re.search(r'name="%s" type="Room" x="(\d+)"' % room, text).group(1)))
        objects, after = pm.foreground_xml(SimpleNamespace(foreground=pieces), first_info, next_object)
        next_object = after
        groups.append(f' <objectgroup id="{next_layer}" name="foreground-{room}" parallaxx="{pm.FG_FACTOR}" parallaxy="{pm.FG_FACTOR}">\n'
                      f'  <properties><property name="roomx" type="float" value="{room_x}"/></properties>\n{objects}\n </objectgroup>\n')
        next_layer += 1
    text = text.replace('<tileset firstgid="1" source="Forest.tsx"/>', '<tileset firstgid="1" source="ForestTerrain.tsx"/>')
    anchor = '<tileset firstgid="1" source="ForestTerrain.tsx"/>\n'
    text = text.replace(anchor, anchor + ''.join(sets))
    here = text.index(' <layer id="', text.index(anchor))
    text = text[:here] + ''.join(layers) + text[here:]
    text = text.replace('</map>', ''.join(groups) + '</map>')
    text = re.sub(r' nextlayerid="\d+"', f' nextlayerid="{next_layer}"', text, count=1)
    text = re.sub(r' nextobjectid="\d+"', f' nextobjectid="{next_object}"', text, count=1)
    if 'parallaxoriginx' not in text:
        text = text.replace(' nextlayerid=', f' parallaxoriginx="{pm.VIEW[0] // 2}" parallaxoriginy="{pm.ORIGIN_Y + pm.VIEW[1] // 2}" nextlayerid=', 1)
    return text


if __name__ == '__main__':
    install_assets()
    MAP.write_text(build(MAP.read_text(encoding='utf-8')), encoding='utf-8')
    print(MAP.relative_to(ROOT))
