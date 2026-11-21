#!/usr/bin/env python3
"""Installs the Forest parallax sketch into the game (#236): assets and the layers of `assets/tiles/forest-{0,1,2}.tmx`.

    python3 tools/parallax_game.py            # copy the assets and (re)write the parallax layers of every forest map

What it does, idempotently (a second run changes nothing):
- copies the exported parallax tilesets (`art/parallax/*/out`) to `assets/images/parallax/` and writes their `.tsx` files to
  `assets/tiles/parallax/` (image paths rewritten); the terrain sketch (`art/terrain/forest/out/ForestTerrain.png`, 140 tiles at the tile
  ids of the retired third-party sheet) becomes `assets/images/Tileset/ForestTerrain.png` with its own `ForestTerrain.tsx`, the play
  tileset of every forest map;
- retiles each map's play layer (`retile`: no seam where two rooms' floors meet, no rope over the room-edge walls);
- writes into each forest map the map-wide scenery layers (sky, stars, moon, clouds, far, grove, mid, each as wide as its
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
TERRAIN_FROM = ROOT / 'art/terrain/forest/out/ForestTerrain.png'
TERRAIN_TO = ROOT / 'assets/images/Tileset/ForestTerrain.png'
TERRAIN_TSX = TILES / 'ForestTerrain.tsx'   # the play tileset of every forest map: the terrain sketch
FOREST_FIRST_GID, FOREST_TILES = 1, 140          # ForestTerrain.tsx: the play tileset, the first of the map
# the ground tiles of the play tileset: (left, middle, right) of its top, body, bottom and buried bottom rows (D111)
GROUND = ((22, 23, 24), (42, 43, 44), (62, 63, 64), (71, 72, 73))
# the boulder (33-35 over 53-55) and the slope (36-38 over 56-58) -> their back-rock variants (terrain.grid `back-*`): the same rock
# with no straw lip and no moss, for the rock that is scenery, not ground (forest-2's ruin and mounds)
BACK_ROCK = dict(zip((33, 34, 35, 53, 54, 55, 36, 37, 38, 56, 57, 58), (82, 83, 84, 85, 86, 87, 74, 75, 76, 77, 78, 67)))
SLAB = (26, 27, 28)                               # the floating slab (left, middle, right): its underside is open
ROPE = (79, 99, 105, 119, 139)                    # the rope column and its bracket (third-party layout, drawn over the room-edge walls)
TERRAIN_TSX_TEXT = ('<?xml version="1.0" encoding="UTF-8"?>\n<tileset version="1.10" tiledversion="1.11.2" name="ForestTerrain" '
                    'tilewidth="16" tileheight="16" tilecount="140" columns="20">\n'
                    ' <image source="../images/Tileset/ForestTerrain.png" width="320" height="112"/>\n</tileset>\n')
SCENERY = ('sky', 'stars', 'moon', 'clouds', 'far', 'grove', 'mid', 'play-above', 'play-below')
EDGE_COLUMNS = 2                                  # spare columns past what a layer's factor needs (the camera is fractional)
# The foreground of every room (#236, D113): room name -> pieces (piece, x, top y), x relative to the room. The near pieces (`near-*`,
# drawn by hand in art/parallax/forest/near/) frame the view from the top edge: a big top mass in the start corner (the canopy and the
# hanging fern in turn) and another in the end corner, right of the moon at the last camera (the sprig where only it fits). Rooms with a
# falling or thrown stone take no canopy: its hanging leaves could read as stones. Each piece was kept only where the room still passes
# rule B and the cover time with everything placed before it, with at least a fifth (canopy, fern) or 0.3 (sprig) of it on screen over
# the room at the first or last camera; one earlier thin piece (a curtain, the reeds) stays where fewer than three near pieces fit, and
# forest-0-lever keeps its broad-leaf clump (the bleed test needs a standing piece in the first room). The boss room keeps its reeds.
FOREST_1 = {
    'forest-1-escalators': (('near-canopy', -88, 0), ('near-fern', 540, 0)),
    'forest-1-traps': (('near-fern', -88, 0), ('near-canopy', 540, 0)),
    'forest-1-mob': (('near-canopy', -88, 0), ('near-fern', 796, 0), ('vine-curtain', 120, 0)),
    'forest-1-lever': (('near-fern', -88, 0), ('near-canopy', 540, 0), ('vine-curtain', 56, 0)),
    'forest-1-spider': (('near-fern', -88, 0), ('near-sprig', 1308, 0), ('roots-curtain', 96, 0)),
    'forest-1-bramble': (('near-fern', -88, 0), ('near-canopy', 540, 0), ('roots-curtain', 72, 0)),
    'forest-1-climb': (('near-fern', -116, 0), ('near-sprig', 540, 0), ('vine-curtain', 544, 0)),
    'forest-1-route': (('near-fern', -88, 0), ('near-canopy', 848, 0)),
    'forest-1-guard': (('near-canopy', -88, 0), ('near-fern', 744, 0), ('vine-curtain', 280, 0)),
    'forest-1-hive': (('near-fern', -88, 0), ('near-sprig', 540, 0), ('rise-reeds', 248, 144)),
    'forest-1-tree': (('near-canopy', -88, 0), ('near-fern', 540, 0), ('roots-curtain', 40, 0)),
    'forest-1-deadfall': (('near-fern', -88, 0), ('near-sprig', 540, 0), ('vine-curtain', 120, 0)),
}
FOREST_0 = {
    'forest-0-lever': (('near-canopy', -128, 0), ('near-fern', 488, 0), ('clump-leaves', 440, 320), ('vine-curtain', 76, 0)),
    'forest-0-ladder': (('near-fern', -88, 0), ('near-canopy', 948, 0), ('roots-curtain', 172, 0)),
    'forest-0-wall': (('near-canopy', -88, 0), ('near-fern', 772, 0), ('vine-curtain', 76, 0)),
    'forest-0-platform': (('near-fern', -88, 0), ('near-sprig', 796, 0), ('rise-reeds', -50, 144)),
    'forest-0-stone': (('near-fern', -88, 0), ('near-sprig', 692, 0), ('vine-curtain', 76, 0)),
    'forest-0-traps': (('near-fern', -88, 0), ('near-canopy', 668, 0), ('roots-curtain', 172, 0)),
}
FOREST_2 = {
    'forest-entry': (('near-canopy', -88, 0), ('near-fern', 692, 0), ('vine-curtain', 76, 0)),
    'forest-walls': (('near-fern', -128, 0), ('near-canopy', 452, 0), ('roots-curtain', 20, 0)),
    'forest-fissure': (('near-canopy', -88, 0), ('near-fern', 540, 0), ('vine-curtain', 76, 0)),
    'forest-canopy': (('near-fern', -88, 0), ('near-sprig', 1308, 0), ('rise-fern', 1268, 128)),
    'forest-boss': (('rise-reeds', 426, 144),),
}
MAPS = {'forest-0': FOREST_0, 'forest-1': FOREST_1, 'forest-2': FOREST_2}   # every forest map and its rooms' foreground
GAME_FOREGROUND = {room: pieces for rooms in MAPS.values() for room, pieces in rooms.items()}


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
    TERRAIN_TSX.write_text(TERRAIN_TSX_TEXT, encoding='utf-8')


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


def retile(rows, solid=frozenset()):
    """The play rows with the room-edge seams and the ropes gone (#236), every cell's emptiness unchanged (collision and
    `CollisionVisibilityTest` read it): a ground tile takes its left, middle or right column by its horizontal neighbours, keeping
    its authored row (top, body, bottom), so two blocks that meet at a room edge read as one floor; a rope cell (the third-party
    rope column drawn over a `Wall`: the grab cue's straw where no rope is) becomes soil like the other walls, its top cell the lit
    walk edge where open air is above it (a short wall's top can be stood on), the rest the soil body; a slab tile set into a floor
    (ground tops on both sides) becomes the floor's top. The lit walk edge is the "stand here" cue, so a drawn cell that is not in
    `solid` ((row, column) cells of the map's solid collision blocks) never carries it: a ground top there shows the soil body, a
    boulder or slope cell its back-rock variant (D112)."""
    rows = [list(r) for r in rows]
    width = len(rows[0])
    tops = GROUND[0]
    for r, row in enumerate(rows):
        for c, gid in enumerate(row):
            if gid in ROPE:
                row[c] = GROUND[0 if r and not rows[r - 1][c] else 1][1]
            elif gid in SLAB and 0 < c < width - 1 and row[c - 1] in tops and row[c + 1] in tops:
                row[c] = GROUND[0][1]   # a floating slab's tile set into a floor (forest-2, x 1520): the floor's own top
    if solid:
        for r, row in enumerate(rows):
            for c, gid in enumerate(row):
                if (r, c) in solid:
                    continue
                if gid in BACK_ROCK:
                    row[c] = BACK_ROCK[gid]
                elif gid in tops:
                    row[c] = GROUND[1][tops.index(gid)]
    for row in rows:
        family = [next((k for k, kind in enumerate(GROUND) if gid in kind), None) for gid in row]
        for c, kind in enumerate(family):
            if kind is None:
                continue
            left = c == 0 or family[c - 1] is not None
            right = c == width - 1 or family[c + 1] is not None
            row[c] = GROUND[kind][1 if left and right else 0 if right else 2 if left else 1]
    return rows


def solid_cells(text):
    """(row, column) of every cell a solid collision block covers (Platform and QuickSand are not ground to stand on)."""
    import xml.etree.ElementTree as ET
    cells = set()
    for group in ET.fromstring(text).findall('objectgroup'):
        if group.get('name') != 'Collisions':
            continue
        ox, oy = float(group.get('offsetx', 0)), float(group.get('offsety', 0))
        for o in group.findall('object'):
            if o.get('type') in ('Platform', 'QuickSand'):
                continue
            x, y = float(o.get('x')) + ox, float(o.get('y')) + oy
            w, h = float(o.get('width', 0)), float(o.get('height', 0))
            for r in range(int(y // pm.TILE), int((y + h - 1) // pm.TILE) + 1):
                for c in range(int(x // pm.TILE), int((x + w - 1) // pm.TILE) + 1):
                    cells.add((r, c))
    return frozenset(cells)


def build(text, map_name='forest-1'):
    """The text of `map_name`.tmx with the parallax layers (re)written."""
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
    rows = retile(rows, solid_cells(text))
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
    for room, pieces in MAPS[map_name].items():
        room_x = int(float(re.search(r'name="%s" type="Room" x="(\d+)"' % room, text).group(1)))
        objects, after = pm.foreground_xml(SimpleNamespace(foreground=pieces), first_info, next_object)
        next_object = after
        groups.append(f' <objectgroup id="{next_layer}" name="foreground-{room}" parallaxx="{pm.FG_FACTOR}" parallaxy="{pm.FG_FACTOR}">\n'
                      f'  <properties><property name="roomx" type="float" value="{room_x}"/></properties>\n{objects}\n </objectgroup>\n')
        next_layer += 1
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
    for name in MAPS:
        path = TILES / f'{name}.tmx'
        path.write_text(build(path.read_text(encoding='utf-8'), name), encoding='utf-8')
        print(path.relative_to(ROOT))
