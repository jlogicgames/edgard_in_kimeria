#!/usr/bin/env python3
"""Forest parallax mock (sketch, #236): writes `art/parallax/forest/mock.tmx`, renders previews and checks the foreground.

The mock copies the first room of `assets/tiles/forest-1.tmx` (its `Background` layer, 80 tile columns) as the play layer
and assembles the sketch tilesets: the forest strips (far, grove, mid, each a seamless strip repeated), hand-placed
foreground pieces (layer `foreground`, pixel defocus) and the shared stars, moon and clouds (`art/parallax/shared/`, used by every world). Run once to create the
file; after that the author edits `mock.tmx` in Tiled and only `--preview` is re-run (it reads the file, so it shows
exactly what Tiled holds). Offset of a layer: `camX * (1 - factor)` from the camera's left edge, with the map's
`parallaxoriginx` set to half the view so Tiled agrees. The stars, the moon and the clouds have factor 0: the stars and the moon never move on
screen; the clouds drift on their own at `driftx` px/s and wrap at `wrapx` (the preview draws them at time 0).

    python3 tools/parallax_mock.py            # build mock.tmx, previews and the check
    python3 tools/parallax_mock.py --preview  # previews and the check only
    python3 tools/parallax_mock.py --check    # check only: foreground over traps, enemies and mechanisms, its height
                                              # and how long it hides the walking hero
    python3 tools/parallax_mock.py --terrain  # art/terrain/forest/mock-terrain.tmx: mock.tmx with the terrain sketch
                                              # (Forest-new.tsx) as the play tileset, its previews and a forest-2 snippet
    python3 tools/parallax_mock.py --room calm   # the same for the calm room (mock-calm.tmx, terrain sketch, preview-calm-*)
    python3 tools/parallax_mock.py --scan     # every room of the forest maps: hazards, gaps and where a tall foreground fits

Without `--room`, `--check` checks every scene (forest-1's first room and the calm room); `--preview` and the build take one
scene, forest-1's first room by default.

The foreground is an objectgroup `foreground` of tile objects (`forest-fg.tsx` is a collection of images over one packed sheet, a sub-rectangle per piece), each room's
foreground authored relative to its room, because a factor of 1.6 cannot be authored on one map-wide layer (forest-1 would need
about 10.6k px of it). Game formula: `screenX = (x - roomX) - f * (camX - roomX)`, with f = 1.6, x the object's x relative
to the map, roomX the left edge of the room being shown and camX the camera's left edge. Tiled draws a parallax group with one
origin (`parallaxoriginx`), so it shows the foreground correctly only for one room origin (this mock: roomX 0), the property
`roomx` of the group; #236 tracks the game's per-room parser.

The check reads the room's objects from its map (everything the active tier of `check_contrast.SPRITES` draws, the patrol range
of a red mob, the travel of a lift, the route of a spider and the parts the game draws for the hive, the deadfall and the fissure)
and fails (exit 1) when a foreground pixel with alpha > 24 lies over any of them on screen (plus 16 px, none under what stands on the
floor) at any camera x of the room (every 4 px, steady camera), or over the hero's figure above the waist (31 to 15 px above the floor of the box,
plus 16 px) in the RoomStart and RoomExit boxes. Elsewhere the foreground may cover the hero and the walk edge, even fully (rule B,
author 2026-11-19), but never hide him where he stands nor for long (cover time, third pass and its review): his 11x31 px figure
counts as hidden when 85 % or more of it lies under foreground pixels of alpha 128 or more; standing at any x of his route from the
start to the exit, with the game's steady camera, he is never hidden, and walking that route at 100 px/s (on the floor and at a
jump's apex) he is hidden at most 0.5 s in a row; the check prints the hidden standing spots and the longest run of each mock. Hints are drawn by the HUD after the world, so the foreground never covers them and the check ignores them.
The previews use the game's framing: a forest room is 368 px tall and the camera top while the hero stands on the floor is
`FallingTrapLayout.floorCameraTop` (room y + 368 - 360 = 8), so a preview shows rows 8 to 368 and a bottom-rising piece must end
at the room's bottom edge (y 368) to reach the screen bottom. These rules are checked at that steady camera only.

Vertical parallax: every layer moves in y by its own factor from that floor camera (ORIGIN_Y, below), so the hero's look up
(100 px) and down (150 px) shows the bleed art each layer carries past the room. Each preview has two look frames (`-up`,
`-down`); `--check` also fails on a visible layer edge (occlusion-aware) and on open ground in the bottom 40 % below the
room, at every camera x and camera top of the look range (room top - 100 to 8 + 150, 10 px apart; check_look). Layer offsets
are snapped as the game's Math.round (half up). `--room calm` also writes
`preview-calm-look.gif` and `preview-calm-walk.gif`.
"""
import math
from collections import namedtuple
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import numpy
from PIL import Image
import check_contrast as contrast
import parallax_sprites as sprites

ROOT = Path(__file__).resolve().parents[1]
DIR = sprites.DIR
MOCK = DIR / 'mock.tmx'
ROWS = 23
TILE = 16
ALPHA = 3                               # the alpha channel of an RGBA pixel or array
RGB_CHANNELS = 3
OPAQUE = 255
HALF_UP = 0.5
SPOT_W = 2                              # a sprite-sheet spot is (col, row, w, h) in tiles
SPOT_H = 3
BOX_H = 3                               # an object box is (x, y, w, h)
HERO_CELL = 64                          # one cell of the hero sheet, px
HERO_CELL_DX = 8                        # the cell starts this far left of the box x
PAD_COLUMNS = 24                        # the play layer is padded (its last column repeated) so the room's last camera shows ground
ROOM_X = 0                              # left edge of the room in a mock: the mock's play layer starts at the room's left edge
VIEW = (640, 360)
WAIST = 15
HERO_H = 31                             # the hero's figure, from the floor line up
HERO_W = 40                             # the hero stands this far left of a RoomExit box's x when he triggers it (preview and check agree)
HERO_BOX = (48, 48)                     # a RoomStart box (the Player spawn box)
CAMERA_STEP = 1
CAMERA_TOP = 8                          # FallingTrapLayout.floorCameraTop for a 368 px room and a 360 px view: 0 + (368 - 360)
BACKGROUND = '#2c3f66'
PLAY_TSX = '../../../assets/tiles/Forest.tsx'
TERRAIN_TSX = '../../terrain/forest/Forest-new.tsx'
TALL_TOP = 120                          # --scan: a bottom-rising piece reaching y 120 (two thirds of the view)
SHEET_COLUMNS = sprites.SHEET_COLUMNS
GID_MASK = 0x0FFFFFFF
COVER_ALPHA = 24
# Hero cover (rule B, author 2026-11-19, third pass, tightened in the review): the hero's figure (HERO_FIGURE_W x HERO_H on
# the floor line) counts as hidden when HIDDEN_FRACTION or more of its pixels lie under foreground pixels of alpha
# HIDDEN_ALPHA or more (128: the dark 'g' fringe, alpha 192, hides the dark hero as well as the solid body does).
# Standing: at every box x of his floor route (1 px apart), with the steady camera there (Player.cameraTarget: the box
# centre minus half the view, clamped to the room), he is never hidden. Walking: from his start to the exit at MOVE_SPEED
# with that camera, on the floor and at a jump's apex, never hidden for more than HIDDEN_MAX s in a row.
# The game's camera follows a focus kept within a dead zone (Player.DEAD_ZONE_WIDTH_FACTOR 3 x the 11 px hitbox = 33 px)
# around the box centre, so it lags his centre by up to 16.5 px while he walks; the check uses the centred camera, and
# replaying the walk with the dead zone and the camera's chase changed the longest hidden run by at most 0.01 s.
MOVE_SPEED = 100                        # px/s, Player.MOVE_SPEED
HERO_FIGURE_W = 11                      # the hitbox width (Player: Hitbox.rect(18, 26, 11, 22)); the figure is HERO_H tall
HERO_FIGURE_DX = 18                     # the figure's left edge, px right of the box x (the hitbox offsetX)
HERO_CAMERA_DX = 24                     # the camera follows the box centre (Player.centerX: box x + 48 / 2)
HIDDEN_ALPHA = 128
HIDDEN_FRACTION = 0.85
HIDDEN_MAX = 0.5
HERO_LIFTS = (0, 60)                    # his feet on the floor line and at a jump's apex (59.67 px, JumpFrameRateTest)
MARGIN = 16
FG_FACTOR = 1.6
FG_PAD = 160                            # px of canvas left of x 0 for foreground objects at negative x
# Everything the active tier of the contrast gate draws is excluded (SPRITES); the hero's start and exit boxes have their own
# rule (above the waist, check_layer). The names the review required are asserted so a change in the gate cannot drop one silently.
EXCLUDED_TYPES = {key.split(':')[0] for key, row in contrast.SPRITES.items() if row[0] == 'active'}
REQUIRED_TYPES = {'HangingHive', 'Deadfall', 'Bat', 'Bramble', 'Collectable', 'FireballRefill', 'Stone', 'Ladder', 'Spider', 'Beetle',
                  'RedMob', 'Escalator', 'Trigger'}
assert REQUIRED_TYPES <= EXCLUDED_TYPES, REQUIRED_TYPES - EXCLUDED_TYPES
FUNGUS_TILES = {127, 128, 129}         # the honey fungus is drawn by play-layer tiles, not an object
CLOUD_DRIFT = 3
CLOUD_WRAP = 1024
GIF_FRAME_MS = 70                       # the preview GIFs' frame time: 14.3 frames/s (20 frames/s made the look GIF 5.3 MB)
MS_PER_S = 1000
GIF_FPS = MS_PER_S / GIF_FRAME_MS
GIF_SCALE = 2                           # the GIFs are drawn at 2x, nearest neighbour
PALETTE_SAMPLES = 16                    # frames sampled for the GIF palette
MEGABYTE = 1e6
RUN_FPS = 10                            # the hero's run cycle, frames/s, and its frames in the sheet row
RUN_FRAMES = 6
SMOOTH_BASE = 3                         # smoothstep: t * t * (3 - 2 * t)
SMOOTH_SLOPE = 2
GIF_COLOURS = 128                       # the preview GIFs' global palette (one index is the transparent 'unchanged')
# Vertical parallax (author, 2026-11-19): each layer's y factor is its x factor, measured from the floor camera, so the steady
# frame (camera top ORIGIN_Y) is the authored one: screenY = (y - originY) - f * (camTop - originY), originY = room y +
# ORIGIN_Y, y the art's room y, whole pixels. The standing hero looks up LOOK_UP px and down LOOK_DOWN px (LookAround,
# not clamped to the room), so a layer of factor f is seen from room y ORIGIN_Y - f * LOOK_UP to ROOM_H + f * LOOK_DOWN:
# far 0.15 (bleed 23 px below), grove 0.25 (38), mid 0.4 (35 above, 60 below), play 1 (100 above, 150 below), foreground
# 1.6 (165 above, 240 below), over the full look range. Tiled lines the layers up when the view centre is on the map's parallax origin, so
# `parallaxoriginy` is ORIGIN_Y + half the view; every forest room lies at y 0, so it is exact for every room in y.
ORIGIN_Y = CAMERA_TOP
LOOK_UP = 100                           # LookAround.UP_DISTANCE
LOOK_DOWN = 150                         # LookAround.DOWN_DISTANCE
LOOK_STEP = 5                           # --check: camera tops from ROOM_TOP - LOOK_UP to ORIGIN_Y + LOOK_DOWN, 5 px apart
ROOM_TOP = 0                            # the steady camera top is clamped to the room top (room y 0) when the hero stands high
ROOM_H = ROWS * TILE
BLEED_ROWS = 10                         # play layer rows past the room bottom (160 px: the 150 px look down)
MAP_ROWS = ROWS + BLEED_ROWS
PLAY_ABOVE = 7                          # rows of the `play-above` layer (112 px over the room top: the 100 px look up)
FG_TOP = 176                            # foreground canvas for the look frames: room y -176 to 848
FG_HEIGHT = 1024
PLAY_LAYERS = ('Background', 'play-above', 'play-below')   # the play layer and its decor bleed count as one in the look check
GROUND_FRACTION = 0.4                   # --check: the bottom 40 % of a look frame, below the room, is play or foreground ground
FLAT_RUN = 20                           # --check: art ending flat over this many px in a row is a cut (the longest drawn flat
                                        # edge, the moon disc's top and bottom, is 18 px)
DEEP = {'deep-1': (64, 65), 'deep-2': (66, 67), 'deep-3': (68, 69)}   # deep soil tile ids in Forest-new.tsx (terrain.grid)
BURIED = {61: 70, 62: 71, 63: 72}      # the ground's bottom row (left, middle, right) -> its buried variant over deep soil
# (layer name, tileset key, parallaxx, parallaxy, extra properties, offsety)
LAYERS = (('sky', 'forest/sky', 0, 0, {}, 0), ('stars', 'shared/stars', 0, 0, {}, 0), ('moon', 'shared/moon', 0, 0, {}, 0),
          ('clouds', 'shared/clouds', 0, 0, {'driftx': CLOUD_DRIFT, 'wrapx': CLOUD_WRAP}, 0),
          ('far', 'forest/far', 0.15, 0.15, {}, 0), ('grove', 'forest/grove', 0.25, 0.25, {}, 0),
          ('mid', 'forest/mid', 0.4, 0.4, {}, -48), ('play-above', 'play-above', 1, 1, {}, -PLAY_ABOVE * TILE),
          ('Background', None, 1, 1, {}, 0), ('foreground', 'forest/fg', FG_FACTOR, FG_FACTOR, {'roomx': ROOM_X}, 0))
MOON_AT = (480, 64)   # screen centre of the moon (the 128 px piece snaps to whole tiles: this is its exact centre); the same in every world


class Scene:
    """One room of a forest map mocked over the layers: its map, its `Room` object, the mock file, the play tileset, its
    foreground pieces (room-relative) and its preview cameras {camera: (file name, where the hero stand-in stands)}, the
    stand-in at the room's start, its exit or a room-relative x. All coordinates of a scene are relative to the room's left
    edge, so the mock's play layer starts at the room and its foreground group has `roomx` 0."""

    def __init__(self, source, room, mock, play_tsx, foreground, previews):
        self.source, self.room, self.mock, self.play_tsx, self.foreground, self.previews = \
            source, room, mock, play_tsx, foreground, previews
        self.root = ET.parse(source).getroot()
        self.game = False
        self.origin = 0   # GameScene: the room's x in the map, added to the room-relative camera for map-wide layers
        box = next(o for o in self.root.iter('object') if o.get('type') == 'Room' and o.get('name') == room)
        self.x, self.width = int(float(box.get('x'))), int(float(box.get('width')))
        self.play_columns = self.width // TILE
        self.columns = self.play_columns + PAD_COLUMNS
        self.cameras = range(0, self.width - VIEW[0] + 1, CAMERA_STEP)
        start = hero_boxes(self)['RoomStart']
        self.floor = start[1] + start[BOX_H]


class GameScene(Scene):
    """A room of the game map itself (`assets/tiles/forest-1.tmx`), checked over the layers the game draws: the map-wide scenery
    layers, the Background and the room's own foreground group (`foreground-<room>`, `roomx` the room's x, objects relative to
    the room). The camera of the checks stays room-relative; map-wide layers are shifted by the room's x (`origin`)."""

    def __init__(self, source, room):
        super().__init__(source, room, source, None, (), {})
        self.game, self.origin = True, self.x


def game_scenes():
    """One GameScene per room of the forest maps that has a foreground group, in map order."""
    scenes = []
    for path in sorted((ROOT / 'assets/tiles').glob('forest-*.tmx')):
        root = ET.parse(path).getroot()
        rooms = {int(float(o.get('x'))): o.get('name') for o in root.iter('object') if o.get('type') == 'Room'}
        for group in root.findall('objectgroup'):
            if group.get('name', '').startswith('foreground'):
                scenes.append(GameScene(path, rooms[int(prop(group, 'roomx'))]))
    return scenes


def play_rows(scene):
    text = scene.source.read_text(encoding='utf-8')
    data = re.search(r'<layer [^>]*name="Background"[^>]*>\s*<data encoding="csv">\s*(.*?)</data>', text, re.S).group(1)
    rows = [[int(v) for v in line.strip().rstrip(',').split(',')] for line in data.strip().splitlines()]
    c0, c1 = scene.x // TILE, scene.x // TILE + scene.play_columns
    return [row[c0:c1] + [row[c1 - 1]] * PAD_COLUMNS for row in rows]


class Layer:
    def __init__(self, tileset, columns, rows=MAP_ROWS):
        self.first, self.spots, self.blank = tileset   # self.blank: ids of fully transparent tiles, which stay empty cells
        self.columns = columns
        self.rows = [[0] * columns for _ in range(rows)]

    def stamp(self, piece, col, row):
        c0, r0, w, h = self.spots[piece]
        for j in range(h):
            for i in range(w):
                tile_id = (r0 + j) * SHEET_COLUMNS + c0 + i
                if tile_id not in self.blank and 0 <= col + i < self.columns and 0 <= row + j < len(self.rows):
                    self.rows[row + j][col + i] = self.first + tile_id

    def strip(self, pieces, row):
        col, k = 0, 0
        while col < self.columns:
            self.stamp(pieces[k % len(pieces)], col, row)
            col += self.spots[pieces[k % len(pieces)]][SPOT_W]
            k += 1


def sky(layer):
    # factor 0: rows past the room are never on screen in the game; the bottom band fills them so Tiled shows no gap
    for row in range(MAP_ROWS):
        for col in range(layer.columns):
            layer.stamp(f'band-{min(row, ROWS - 1)}', col, row)


STAR_PIECES = 5                         # the star pieces stars-0 .. stars-4, side by side
STAR_SPACING = 8                        # columns between them
# (piece, column, row) of the clouds, one 1024 px loop (wrapx = 64 columns): every piece ends at or before column 64
CLOUDS = (('cloud-small', 1, 3), ('cloud-big', 11, 1), ('cloud-wisp', 34, 0), ('cloud-mid', 45, 3), ('cloud-wisp', 55, 2))
FAR_ROW = 7                             # the first row of each strip layer
GROVE_ROW = 5


def stars(layer):
    for i in range(STAR_PIECES):
        layer.stamp(f'stars-{i}', STAR_SPACING * i, 0)


def moon(layer):
    _, _, w, h = layer.spots['moon']
    layer.stamp('moon', (MOON_AT[0] - w * TILE // 2) // TILE, (MOON_AT[1] - h * TILE // 2) // TILE)


def clouds(layer):
    # one 1024 px loop (wrapx = 64 columns): every piece ends at or before column 64
    for piece, col, row in CLOUDS:
        layer.stamp(piece, col, row)


def far(layer):
    layer.strip(['ridge-a', 'ridge-b', 'ridge-c', 'ridge-d'], FAR_ROW)


def grove(layer):
    layer.strip(['grove-a', 'grove-b', 'grove-c'], GROVE_ROW)


def mid(layer):
    layer.strip(['mid-a', 'mid-b', 'mid-c', 'mid-d'], 0)   # layer offsety -48: the pieces start 48 px over the room top


GRADED = ('deep-1', 'deep-2')           # the soil rows graded under a floor's bottom row, then deep-3
DEEP_STRIDE_C = 7                       # the two variants of a deep tile alternate on a column and row stride
DEEP_STRIDE_K = 3
PERIOD_ROWS = 9                         # play_above reads rows 1 to 8 of a column for its period


def play_bleed(rows, deep_first):
    """The room's play rows plus BLEED_ROWS of deep soil (Forest-new.tsx, `deep_first` its firstgid): deep-1 under a
    floor's bottom row, deep-2 under it, deep-3 below and under an open column (a pit falls into near black). The
    ground's bottom row on the room's last row becomes its buried variant (no nubs, graded into deep-1), since the
    soil goes on under it."""
    rows = [list(r) for r in rows]
    width = len(rows[0])
    for c, gid in enumerate(rows[ROWS - 1]):
        if (gid & GID_MASK) - 1 in BURIED:   # the play tileset's firstgid is 1 in both mocks
            rows[ROWS - 1][c] = deep_first + BURIED[(gid & GID_MASK) - 1]
    for k in range(BLEED_ROWS):
        row = []
        for c in range(width):
            solid = rows[ROWS - 1][c] != 0
            kind = 'deep-3' if not solid or k >= len(GRADED) else GRADED[k]
            variants = DEEP[kind]
            row.append(deep_first + variants[(c * DEEP_STRIDE_C + k * DEEP_STRIDE_K) % len(variants)])
        rows.append(row)
    return rows


def play_above(rows):
    """PLAY_ABOVE rows over the room top: a column whose top tile is drawn (a rope, a trunk, a wall) carries on upward
    with the repeat of its tiles below row 0 (the shortest period of rows 1 to 8, continued backwards), so a look up shows
    no cut; decor only, no collision."""
    above = [[0] * len(rows[0]) for _ in range(PLAY_ABOVE)]
    for c in range(len(rows[0])):
        if not rows[0][c]:
            continue
        column = [rows[r][c] for r in range(1, PERIOD_ROWS)]
        period = next(p for p in range(1, PERIOD_ROWS) if all(column[i] == column[i % p] for i in range(len(column))))
        for k in range(1, PLAY_ABOVE + 1):   # room row -k: the tile `period * m - k` rows below, inside rows 1..period
            r = -k % period or period
            above[PLAY_ABOVE - k][c] = rows[r][c] if column[0] else rows[0][c]
    return above


# Foreground pieces: (piece, x, top y) in px, x relative to the room. Rule B (author, 2026-11-19): the foreground may cover the
# walk edge and the hero, but no camera offset of the room may carry it over an excluded rect, nor over the hero above the waist at
# the room start and exit. The floor traps and mechanisms sweep every column of this busy room at some camera, so nothing rises
# from the bottom past the floor line; the big shapes hang from the top: the vine curtain over the middle, the root curtain at the
# room's end (deepest, there the camera never carries it over an object), leaves and a bough in the top-left corner. Low clumps
# lie on the floor line with open ground between them.
FOREGROUND = (('hang-leaves-l', -80, 0), ('trunk-near', -48, 0), ('clump-fern', 32, 320), ('tuft', 112, 312),
              ('clump-leaves', 320, 320), ('vine-curtain', 600, 0), ('clump-grass', 608, 320), ('clump-mixed', 832, 320),
              ('roots-curtain', 1208, 0), ('edge-leaves', 1568, 327), ('edge-fern', 1600, 327))



# The calm room: forest-1-mob (`--scan`: the longest stretches with nothing to protect, see the card). #507 reworked the room
# (a red mob at x 192, a falling branch at 464 and an explosive stump at 592 on the route): the middle stretch the first sketch
# put the tall pieces in is protected now, so only two tall pieces still fit where no camera carries them over an object: the
# reeds at the left end and the fern fan at the right end; the vine curtain hangs over the left third, a leaf bough enters at
# the top-left, low clumps sit on the floor line (the near trunk found no x: every column crosses a protected rect).
CALM_FOREGROUND = (('hang-leaves-l', -80, 0), ('vine-curtain', 120, 0), ('rise-reeds', -50, 144), ('clump-grass', 288, 320),
                   ('rise-fern', 780, 128), ('clump-mixed', 744, 320), ('clump-fern', 832, 320))


TOP_OVERLAP = 8                         # a `-top` piece's last row is the piece's y 7: it reaches 8 px into the piece
FOOT_DX = -32                           # a `-foot` or `-top` bleed piece is 64 px wider than its piece, centred on it


def with_bleed(foreground, spots):
    """The scene's pieces, each followed by its bleed pieces (fg.grid): `<piece>-foot` under it (top at the piece's
    bottom), `<piece>-top` over a hanging piece (its last row at the piece's y 7)."""
    result = []
    for piece, x, top in foreground:
        result.append((piece, x, top))
        h = spots[piece][SPOT_H] * TILE
        if f'{piece}-foot' in spots:
            result.append((f'{piece}-foot', x + FOOT_DX, top + h))
        if f'{piece}-top' in spots:
            result.append((f'{piece}-top', x + FOOT_DX, top + TOP_OVERLAP - spots[f'{piece}-top'][SPOT_H] * TILE))
    return result


def foreground_xml(scene, info, first_id):
    first, spots, _ = info
    objects = []
    pieces = with_bleed(scene.foreground, spots)
    for n, (piece, x, top) in enumerate(pieces):
        _, _, w, h = spots[piece]
        index = spots[piece][0]
        # a Tiled tile object's y is its bottom edge
        objects.append(f'  <object id="{first_id + n}" name="{piece}" gid="{first + index}" x="{x}" y="{top + h * TILE}" '
                       f'width="{w * TILE}" height="{h * TILE}"/>')
    return '\n'.join(objects), first_id + len(pieces)


BUILDERS = {'forest/sky': sky, 'shared/stars': stars, 'shared/moon': moon, 'shared/clouds': clouds, 'forest/far': far, 'forest/grove': grove,
            'forest/mid': mid}


def csv(rows):
    return ',\n'.join(','.join(str(v) for v in row) for row in rows)


def relative(path):
    return path.name if path.parent == DIR else f'../{path.parent.name}/{path.name}'


def build(scene):
    play = ET.parse((scene.mock.parent / scene.play_tsx).resolve()).getroot()
    tilesets, first = [(1, scene.play_tsx)], 1 + int(play.get('tilecount'))
    deep_first = 1
    if scene.play_tsx != TERRAIN_TSX:   # the deep soil tiles live in the terrain sketch
        tilesets.append((first, TERRAIN_TSX))
        terrain = ET.parse((scene.mock.parent / TERRAIN_TSX).resolve()).getroot()
        deep_first, first = first, first + int(terrain.get('tilecount'))
    info = {}
    for key in sprites.TILESETS:
        spots, count = sprites.layout(key)
        info[key] = (first, spots, sprites.blank_ids(key))
        tilesets.append((first, relative(sprites.tsx_path(key))))
        first += count
    layers, next_id = [], 1
    for name, key, px, py, props, offset_y in LAYERS:
        parallax = f' parallaxx="{px}" parallaxy="{py}"' if (px, py) != (1, 1) else ''
        parallax += f' offsety="{offset_y}"' if offset_y else ''
        extra = ''.join(f'<property name="{k}" type="float" value="{v}"/>' for k, v in props.items())
        extra = f'\n  <properties>{extra}</properties>' if extra else ''
        if key in sprites.COLLECTIONS:
            objects, after = foreground_xml(scene, info[key], next_id)
            layers.append(f' <objectgroup id="{next_id}" name="{name}"{parallax}>{extra}\n{objects}\n </objectgroup>')
            next_id = after
            continue
        if key == 'play-above':
            rows = play_above(play_rows(scene))
        elif key:
            layer = Layer(info[key], scene.columns)
            BUILDERS[key](layer)
            rows = layer.rows
        else:
            rows = play_bleed(play_rows(scene), deep_first)
        layers.append(f' <layer id="{next_id}" name="{name}" width="{scene.columns}" height="{len(rows)}"{parallax}>{extra}\n'
                      f'  <data encoding="csv">\n{csv(rows)}\n</data>\n </layer>')
        next_id += 1
    sets = '\n'.join(f' <tileset firstgid="{gid}" source="{source}"/>' for gid, source in tilesets)
    source = (f' <properties><property name="sourcemap" value="{scene.source.relative_to(ROOT)}"/>'
              f'<property name="sourceroom" value="{scene.room}"/><property name="sourceroomx" type="int" value="{scene.x}"/></properties>\n')
    scene.mock.write_text(
        f'<?xml version="1.0" encoding="UTF-8"?>\n<map version="1.10" tiledversion="1.12.1" orientation="orthogonal" '
        f'renderorder="right-down" width="{scene.columns}" height="{MAP_ROWS}" tilewidth="{TILE}" tileheight="{TILE}" infinite="0" '
        f'backgroundcolor="{BACKGROUND}" parallaxoriginx="{VIEW[0] // 2}" parallaxoriginy="{ORIGIN_Y + VIEW[1] // 2}" nextlayerid="{next_id}" nextobjectid="{next_id}">\n'
        f'{source if scene.x else ""}{sets}\n' + '\n'.join(layers) + '\n</map>\n', encoding='utf-8')
    print(scene.mock.relative_to(ROOT))


def tile_images(mock=None):
    """Root of mock.tmx (or `mock`), [(firstgid, tileset image RGBA, columns)] highest firstgid first, and {gid: image} of the
    collection tilesets (the foreground pieces)."""
    mock = mock or MOCK
    root = ET.parse(mock).getroot()
    sets, pieces = [], {}
    for element in root.findall('tileset'):
        source = (mock.parent / element.get('source')).resolve()
        tsx = ET.parse(source).getroot()
        if tsx.find('image') is None:
            for tile in tsx.findall('tile'):   # a collection: the tile's image, or its sub-rectangle of a packed sheet
                sheet = Image.open(source.parent / tile.find('image').get('source')).convert('RGBA')
                x, y = int(tile.get('x', 0)), int(tile.get('y', 0))
                box = (x, y, x + int(tile.get('width', sheet.width)), y + int(tile.get('height', sheet.height)))
                pieces[int(element.get('firstgid')) + int(tile.get('id'))] = sheet.crop(box)
            continue
        image = source.parent / tsx.find('image').get('source')
        sets.append((int(element.get('firstgid')), Image.open(image).convert('RGBA'), int(tsx.get('columns'))))
    return root, sorted(sets, key=lambda s: -s[0]), pieces


def render_layer(layer, sets):
    width, height = int(layer.get('width')), int(layer.get('height'))
    image = Image.new('RGBA', (width * TILE, height * TILE))
    values = [int(v) for v in layer.find('data').text.replace('\n', '').split(',') if v.strip()]
    for index, gid in enumerate(values):
        gid &= GID_MASK  # Tiled keeps flip and rotation flags in the top bits; the preview draws tiles unflipped
        if gid:
            first, sheet, columns = next(s for s in sets if gid >= s[0])
            local = gid - first
            x, y = local % columns * TILE, local // columns * TILE
            image.paste(sheet.crop((x, y, x + TILE, y + TILE)), (index % width * TILE, index // width * TILE))
    return image


def seam_kinds():
    """{piece: {'top', 'foot'}} for every foreground piece that has a `<piece>-top` or `<piece>-foot` partner in fg.grid
    (read from the sprite layout, not from a map, so a map that leaves a partner out is still caught)."""
    spots = sprites.layout('forest/fg')[0]
    kinds = {}
    for name in spots:
        for kind in ('top', 'foot'):
            if name.endswith('-' + kind) and name[:-len(kind) - 1] in spots:
                kinds.setdefault(name[:-len(kind) - 1], set()).add(kind)
    return kinds


def render_objects(group, pieces, columns, top=0, height=ROWS * TILE, edges=None):
    """The tile objects of a group on a canvas FG_PAD px wider to the left: canvas x = room-relative x + FG_PAD, canvas
    y = room y + `top` (the steady checks use the room, 0 to 368; the look frames FG_TOP and FG_HEIGHT). With `edges` (a
    list), each object's top and bottom canvas rows are added to it as (row, x0, x1, 'top' | 'bottom', its own opaque
    pixels on that row)."""
    image = Image.new('RGBA', (columns * TILE + FG_PAD, height))
    for obj in group.findall('object'):
        piece = pieces[int(obj.get('gid')) & GID_MASK]
        y = int(float(obj.get('y'))) - piece.height + top   # Tiled's y is the bottom edge of a tile object
        x = int(float(obj.get('x'))) + FG_PAD
        layer = Image.new('RGBA', image.size)
        layer.paste(piece, (x, y))
        image.alpha_composite(layer)
        if edges is not None:
            a = numpy.asarray(piece)[..., ALPHA] > COVER_ALPHA
            edges += [(y, x, x + piece.width, 'top', a[0]), (y + piece.height - 1, x, x + piece.width, 'bottom', a[-1])]
            # a seam row: a piece with a `-top` or `-foot` partner (fg.grid) ends where its partner takes over, on its first
            # (last for -foot) drawn row, which is not always the image's row 0; it is a cut with no length limit
            kinds = seam_kinds().get(obj.get('name'), ())
            rows = numpy.nonzero(a.any(axis=1))[0]
            if len(rows) and 'top' in kinds:
                edges.append((y + int(rows[0]), x, x + piece.width, 'top', a[rows[0]]))
            if len(rows) and 'foot' in kinds:
                edges.append((y + int(rows[-1]), x, x + piece.width, 'bottom', a[rows[-1]]))
    return image


def prop(layer, name, default=0.0):
    element = layer.find(f"properties/property[@name='{name}']")
    return float(element.get('value')) if element is not None else default


def jround(value):
    """Java's Math.round (half up), as the game snaps a layer offset; Python's round() rounds half to even."""
    return math.floor(value + HALF_UP)


def screen_x(x, factor, cam, room_x=ROOM_X):
    """Game formula for a layer of factor f: screenX = (x - roomX) - f * (camX - roomX), x as authored relative to the room."""
    return x - jround(factor * (cam - room_x))


def reach(obj, props, x, w, line):
    """Room-relative x extent of an object including its patrol, lift travel or spider route."""
    x0, x1 = x, x + w
    if props.get('isVertical') != 'true':
        x0, x1 = x0 - float(props.get('offNeg', 0)) * TILE, x1 + float(props.get('offPos', 0)) * TILE
    if line is not None:
        for point in line.get('points').split():
            px = float(point.split(',')[0])
            x0, x1 = min(x0, x + px), max(x1, x + px + w)
    return x0, x1


def room_objects(scene):
    """[(type, props, x, y, w, h, polyline points or '')] of the objects in the room or whose range (patrol, lift travel,
    spider route) reaches into it, x relative to the room (a group's offsetx and offsety applied, as the game does;
    RoomStart and RoomExit only those of this room)."""
    result = []
    for group in scene.root.iter('objectgroup'):
        offset_x, offset_y = float(group.get('offsetx', '0')), float(group.get('offsety', '0'))
        for obj in group.findall('object'):
            x = float(obj.get('x', 0)) + offset_x - scene.x
            props = {p.get('name'): p.get('value') for p in obj.findall('properties/property')}
            line = obj.find('polyline')
            w = float(obj.get('width', 0))
            x0, x1 = reach(obj, props, x, w or HERO_BOX[0], line)
            if x1 <= 0 or x0 >= scene.width:
                continue
            if not 0 <= x < scene.width and obj.get('type') not in EXCLUDED_TYPES:
                continue
            if obj.get('type') in ('RoomStart', 'RoomExit') and obj.get('name') != scene.room:
                continue
            result.append((obj.get('type'), props, x, float(obj.get('y', 0)) + offset_y, w,
                           float(obj.get('height', 0)), line.get('points') if line is not None else ''))
    return result


def hero_boxes(scene):
    """Room-relative x of the room's RoomStart and RoomExit, and their boxes (x, y, w, h)."""
    boxes = {}
    for kind, _, x, y, w, h, _ in room_objects(scene):
        if kind in ('RoomStart', 'RoomExit'):
            boxes[kind] = (x, y, w or HERO_BOX[0], h or HERO_BOX[1])
    return boxes


_FRAMES = {}


def first_frame(key):
    if key not in _FRAMES:
        _FRAMES[key] = contrast.frames(key)[0]
    return _FRAMES[key]


def sprite_placements(scene):
    """(key, x, y, w, h) of every sprite of the room, x relative to the room, as the contrast gate places them (traps, enemies,
    lever, lifts, decor)."""
    errors = []
    roots = {scene.source.stem: scene.root}
    return [(key, x - scene.x, y, w, h) for _, key, x, y, w, h in contrast.placements(errors, roots)
            if 0 <= x - scene.x < scene.width and key in contrast.SPRITES and key != 'Player']


def draw_sprite(frame, key, x, y, w, h):
    anchor = contrast.SPRITES[key][contrast.SPRITE_ANCHOR]
    left = int(x + (w - frame.width) // 2)
    top = int(y + h - frame.height) if anchor == 'floor' else int(y)
    return frame, left, top


def look_tops():
    """Camera tops of the look frames: ROOM_TOP - LOOK_UP to ORIGIN_Y + LOOK_DOWN, LOOK_STEP apart, both ends and the
    floor hero's full look up (ORIGIN_Y - LOOK_UP) included. The steady camera top is ORIGIN_Y with the hero on the floor
    and as low as the room top (0) when he stands high (above y 256), so a look up reaches room y 0 - LOOK_UP."""
    return sorted(set(range(ROOM_TOP - LOOK_UP, ORIGIN_Y + LOOK_DOWN + 1, LOOK_STEP))
                  | {ORIGIN_Y - LOOK_UP, ORIGIN_Y + LOOK_DOWN})


def screen_y(y, factor, cam_top):
    """Game formula for a layer of vertical factor f: screenY = (y - originY) - f * (camTop - originY), y the art's room y
    (whole pixels; the steady camera top ORIGIN_Y shows every layer as authored)."""
    return y - ORIGIN_Y - jround(factor * (cam_top - ORIGIN_Y))


Drawing = namedtuple('Drawing', 'element image oy px py')   # element, image, room y of the image's row 0, parallaxx, parallaxy


class Layers:
    """The mock's layers ready to draw: [(element, image, room y of the image's row 0, parallaxx, parallaxy)], the hero
    stand-in sheet, the room's sprites and the hero boxes."""

    def __init__(self, scene, mock=None):
        root, sets, pieces = tile_images(mock or scene.mock)
        self.root, self.layers, self.origin = root, [], scene.origin
        for layer in root:
            px, py = float(layer.get('parallaxx', 1)), float(layer.get('parallaxy', 1))
            if layer.tag == 'layer' and layer.get('visible', '1') != '0':
                self.layers.append(Drawing(layer, render_layer(layer, sets), int(float(layer.get('offsety', 0))), px, py))
            elif layer.tag == 'objectgroup' and layer.get('name', '').startswith('foreground'):
                if scene.game and prop(layer, 'roomx') != scene.origin:
                    continue   # another room's foreground of the game map
                edges = []
                image = render_objects(layer, pieces, scene.columns, FG_TOP, FG_HEIGHT, edges)
                self.layers.append(Drawing(layer, image, -FG_TOP, px, py))
                self.fg_edges = edges
        self.hero = Image.open(ROOT / 'assets/images/hero/hero-right.png').convert('RGBA')
        self.boxes = hero_boxes(scene)
        self.placed = [draw_sprite(first_frame(key), key, x, y, w, h) for key, x, y, w, h in sprite_placements(scene)]

    def x_shift(self, layer, cam, time=0.0):
        """Screen x of the image's column 0 (a wrapping layer: its drift instead)."""
        element, image, _, px, _ = layer
        if element.tag == 'objectgroup':
            return screen_x(0, px, cam, 0) - FG_PAD   # room-relative camera and objects: the room is the group's origin
        wrap = int(prop(element, 'wrapx'))
        if wrap:
            return jround(prop(element, 'driftx') * time) % wrap
        return -jround(px * (cam + self.origin))

    def frame(self, scene, cam, where, cam_top=ORIGIN_Y, time=0.0, hero=(0, 0)):
        """One frame: every layer at camera (cam, cam_top), the room's sprites, the hero (sheet cell `hero`: column, row)
        standing where the scene says (the start, the exit or a room x)."""
        frame = Image.new('RGBA', VIEW, self.root.get('backgroundcolor'))
        for layer in self.layers:
            element, image, oy, _, py = layer
            x, y = self.x_shift(layer, cam, time), screen_y(oy, py, cam_top)
            if int(prop(element, 'wrapx')):
                paste_clipped(frame, image, x - int(prop(element, 'wrapx')), y)
            paste_clipped(frame, image, x, y)
            if element.get('name') == 'Background':
                for sprite, left, top in self.placed:
                    paste_clipped(frame, sprite, left - cam, top - cam_top)
                box = self.boxes['RoomStart'] if where == 'start' else self.boxes['RoomExit'] if where == 'exit' else None
                hero_x = box[0] if where == 'start' else box[0] - HERO_W if where == 'exit' else where
                floor = box[1] + box[BOX_H] if box else scene.floor
                cell = self.hero.crop((hero[0] * HERO_CELL, hero[1] * HERO_CELL, (hero[0] + 1) * HERO_CELL, (hero[1] + 1) * HERO_CELL))
                paste_clipped(frame, cell, int(hero_x) - HERO_CELL_DX - cam, int(floor) - HERO_CELL - cam_top)
        return frame


LOOK_FRAMES = (('up', ORIGIN_Y - LOOK_UP, (0, 5)), ('down', ORIGIN_Y + LOOK_DOWN, (1, 5)))   # name, camera top, hero cell


def preview(scene, time=0.0, mock=None, out=None, upscale=False):
    """Frames at the scene's preview cameras, with the visible layers, the first frame of every object sprite of the room at
    its rect and the hero stand-in where the scene puts it (the room's start, its exit or an x): the steady camera (camera
    top ORIGIN_Y) and the two look frames, `-up` (camera top ORIGIN_Y - LOOK_UP) and `-down` (ORIGIN_Y + LOOK_DOWN), the
    hero in his look pose. With `upscale`, also a copy twice the size (nearest neighbour) next to each frame."""
    layers = Layers(scene, mock)
    for cam, (name, where) in scene.previews.items():
        for suffix, cam_top, hero in (('', ORIGIN_Y, (0, 0)),) + LOOK_FRAMES:
            frame = layers.frame(scene, cam, where, cam_top, time, hero)
            target = (out or DIR) / (name if not suffix else name.replace('.png', f'-{suffix}.png'))
            frame.convert('RGB').save(target)
            print(shown(target))
            if upscale:
                double = target.with_name(target.stem + '-2x.png')
                frame.convert('RGB').resize((VIEW[0] * 2, VIEW[1] * 2), Image.NEAREST).save(double)
                print(shown(double))


def save_gif(frames, target, fps=GIF_FPS, colours=GIF_COLOURS):
    """Frames (RGBA, VIEW) as a looping GIF at 2x nearest: one global palette of `colours` - 1 taken from every frame (the
    night's dithered gradients need far fewer than 256 to read), no dither; after the first frame a pixel that did not
    change is the transparent index, so the file stores only what moved."""
    rgb = [f.convert('RGB') for f in frames]
    step = max(1, len(rgb) // PALETTE_SAMPLES)
    sample = Image.new('RGB', (VIEW[0], VIEW[1] * len(rgb[::step])))
    for i, f in enumerate(rgb[::step]):
        sample.paste(f, (0, i * VIEW[1]))
    palette = sample.quantize(colours - 1, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    clear = colours - 1
    images, previous = [], None
    for f in rgb:
        index = numpy.asarray(f.quantize(palette=palette, dither=Image.Dither.NONE)).repeat(GIF_SCALE, 0).repeat(GIF_SCALE, 1)
        shown_index = index.copy()
        if previous is not None:
            shown_index[index == previous] = clear
        previous = index
        image = Image.fromarray(shown_index.astype(numpy.uint8), 'P')
        image.putpalette(palette.getpalette()[:RGB_CHANNELS * (colours - 1)] + [0, 0, 0])
        images.append(image)
    images[0].save(target, save_all=True, append_images=images[1:], duration=round(MS_PER_S / fps), loop=0, optimize=False,
                   transparency=clear, disposal=1)
    print(shown(target), f'{len(images)} frames, {target.stat().st_size / MEGABYTE:.1f} MB')


# the look GIF: (seconds, look offset at the start, at the end, the hero's sheet cell or None), as LookAround pans: a 1 s smoothstep
# look down, hold, a 0.2 s return, then the same up
LOOK_PLAN = ((0.5, 0, 0, None), (1.0, 0, LOOK_DOWN, (1, 5)), (0.75, LOOK_DOWN, LOOK_DOWN, (1, 5)), (0.2, LOOK_DOWN, 0, (0, 0)),
             (0.5, 0, 0, None), (1.0, 0, -LOOK_UP, (0, 5)), (0.75, -LOOK_UP, -LOOK_UP, (0, 5)), (0.2, -LOOK_UP, 0, (0, 0)),
             (0.5, 0, 0, None))


def smoothstep(t):
    t = max(0.0, min(1.0, t))
    return t * t * (SMOOTH_BASE - SMOOTH_SLOPE * t)


def gifs(scene, fps=GIF_FPS):
    """The calm room as the game moves its camera: preview-calm-look.gif (the hero standing mid-room; steady, a 1 s
    smoothstep look down, hold, a 0.2 s return, a look up, return, as LookAround pans) and preview-calm-walk.gif (the hero
    walking from his start to the exit at MOVE_SPEED with the steady camera, the run cycle at 10 frames/s)."""
    layers = Layers(scene)
    mid_x = scene.width // 2 - HERO_CAMERA_DX
    cam = hero_camera(scene, mid_x)
    frames, clock = [], 0.0
    for seconds, start, end, pose in LOOK_PLAN:
        n = round(seconds * fps)
        for i in range(n):
            offset = start + (end - start) * smoothstep((i + 1) / n)
            frames.append(layers.frame(scene, cam, mid_x, ORIGIN_Y + jround(offset), clock, pose or (0, 0)))
            clock += 1 / fps
    save_gif(frames, DIR / 'preview-calm-look.gif', fps)
    frames, clock = [], 0.0
    route = hero_route(scene)
    x = float(route[0])
    while x <= route[-1]:
        frames.append(layers.frame(scene, hero_camera(scene, int(x)), int(x), ORIGIN_Y, clock, (int(clock * RUN_FPS) % RUN_FRAMES, 1)))
        x += MOVE_SPEED / fps
        clock += 1 / fps
    save_gif(frames, DIR / 'preview-calm-walk.gif', fps)


def shown(path):
    return path.relative_to(ROOT) if path.is_relative_to(ROOT) else path


def paste_clipped(frame, image, x, y):
    layer = Image.new('RGBA', frame.size)
    layer.paste(image, (x, y))
    frame.alpha_composite(layer)


def exclusions(scene, unique=False):
    """Room-relative rects (name, x0, y0, x1, y1) the foreground must never cover: every object of the active tier (with a red
    mob's patrol range, a lift's travel and a spider's route), the parts the game draws for the hive, the deadfall, the fissure
    and the spider, and the honey fungus tiles."""
    rects = []
    for kind, props, x, y, w, h, points in room_objects(scene):
        if kind not in EXCLUDED_TYPES or (unique and kind == 'Player'):   # the hero's start box is listed by protected()
            continue
        w, h = w or HERO_BOX[0], h or HERO_BOX[1]
        x0, y0, x1, y1 = x, y, x + w, y + h
        neg, pos = float(props.get('offNeg', 0)) * TILE, float(props.get('offPos', 0)) * TILE
        if props.get('isVertical') == 'true':
            y0, y1 = y0 - neg, y1 + pos
        else:
            x0, x1 = x0 - neg, x1 + pos
        if points:   # a spider's route: its silk and leap range lie on the polyline
            for point in points.split():
                px, py = (float(v) for v in point.split(','))
                x0, y0, x1, y1 = min(x0, x + px), min(y0, y + py), max(x1, x + px + w), max(y1, y + py + h)
        rects.append((props.get('name') or kind, x0, y0, x1, y1))
    drawn = {(kind, x, y) for kind, _, x, y, *_ in room_objects(scene)}
    for key, x, y, w, h in sprite_placements(scene):
        if unique and (key.split(':')[0], x, y) in drawn:
            continue   # the sprite of an object already listed
        if contrast.SPRITES[key][0] == 'active':
            rects.append((key, x, y, x + w, y + h))
    for r, row in enumerate(play_rows(scene)):
        for c, gid in enumerate(row[:scene.play_columns]):
            if gid in FUNGUS_TILES:
                rects.append(('honey fungus', c * TILE, r * TILE, c * TILE + TILE, r * TILE + TILE))
    return rects


def check(scene):
    """The steady-camera rules (exclusions, the hero at the start and exit, standing and walking cover: camera top
    ORIGIN_Y only, as the hero walks and stands; a look is a held pose, not a route) and the look rules (check_look)."""
    root, _, pieces = tile_images(scene.mock)
    ok = True
    for group in root.findall('objectgroup'):
        if group.get('name').startswith('foreground') and (not scene.game or prop(group, 'roomx') == scene.origin):
            ok &= check_layer(scene, group, pieces)
    return check_look(scene) and ok


def blit(dst, src, x, y):
    """OR a boolean image into a boolean frame at (x, y), clipped."""
    h, w = src.shape
    x0, y0, x1, y1 = max(x, 0), max(y, 0), min(x + w, dst.shape[1]), min(y + h, dst.shape[0])
    if x1 > x0 and y1 > y0:
        dst[y0:y1, x0:x1] |= src[y0 - y:y1 - y, x0 - x:x1 - x]


def edge_on_screen(order, cut, cam_top):
    """Whether the cut row (layer index, room y, kind) lies on screen with screen beyond it at this camera top."""
    index, y, kind = cut
    row = screen_y(y, order[index].py, cam_top)
    return 0 < row <= VIEW[1] - 1 if kind == 'top' else 0 <= row < VIEW[1] - 1


def check_look(scene):
    """Look rules (vertical parallax), at every camera x of the room and every camera top of look_tops():
    - no layer edge is visible: a cut is an opaque pixel (alpha > COVER_ALPHA) whose neighbour across a row is
      transparent, either on the first or last row of a layer or of a foreground object, or inside a layer anywhere a
      row of art ends flat over FLAT_RUN px or more (a strip or piece cut short, not a drawn edge). The play layer and
      `play-above` count as one, so do the foreground objects (a `-foot` or `-top` continues its piece); the play layer
      is checked on its rows' ends only (its walk surfaces and ceilings are flat by design); the clouds wrap and are
      skipped. A cut is visible when it lies on screen with screen beyond it and no layer drawn in front of it is fully
      opaque (alpha 255) over it on the composited frame;
    - the bottom GROUND_FRACTION of the frame, where it lies below the room (y >= 368), is opaque play or foreground.
    The sky layer (factor 0, every row filled) covers the whole view, so no pixel is ever bare."""
    layers = Layers(scene)
    order = layers.layers
    tops = look_tops()
    groups = {}
    for i, layer in enumerate(order):
        if int(prop(layer.element, 'wrapx')):
            continue
        key = 'play' if layer.element.get('name') in PLAY_LAYERS else layer.element.get('name')
        groups.setdefault(key, []).append(i)
    cuts = {}   # (layer index, room y, kind) -> set of image columns
    for key, members in groups.items():
        oy0 = min(order[i].oy for i in members)
        height = max(order[i].oy + order[i].image.height for i in members) - oy0
        width = max(order[i].image.width for i in members)
        alpha = numpy.zeros((height, width), bool)
        edges = []
        for i in members:
            element, image, oy, _, _ = order[i]
            a = numpy.asarray(image)[..., ALPHA] > COVER_ALPHA
            alpha[oy - oy0:oy - oy0 + a.shape[0], :a.shape[1]] |= a
            if element.tag == 'objectgroup':
                edges += [(i, row - FG_TOP - oy0, x0, x1, kind, own) for row, x0, x1, kind, own in layers.fg_edges]
            else:
                # `play-below` is the Background's own deep soil in the game map (a separate layer): its top row is interior to the
                # play layer, where the soil under a pit meets the open room (flat by design, as the mock's single layer has it)
                if element.get('name') != 'play-below':
                    edges.append((i, oy - oy0, 0, a.shape[1], 'top', a[0]))
                edges.append((i, oy - oy0 + a.shape[0] - 1, 0, a.shape[1], 'bottom', a[-1]))
        for i, row, x0, x1, kind, own in edges:
            own = own[max(-x0, 0):max(-x0, 0) + min(x1, width) - max(x0, 0)]
            x0, x1 = max(x0, 0), min(x1, width)
            if not 0 <= row < height or x1 <= x0:
                continue
            beyond = row - 1 if kind == 'top' else row + 1
            other = alpha[beyond, x0:x1] if 0 <= beyond < height else numpy.zeros(x1 - x0, bool)
            xs = numpy.nonzero(own & ~other)[0]   # the object's own pixel on its edge row, nothing of any member across
            if len(xs):
                cuts.setdefault((i, row + oy0, kind), set()).update((xs + x0).tolist())
        if key == 'play' or order[members[0]].px == 0:   # the play layer is flat by design; a factor-0 layer is the sky's own
            continue
        empty = numpy.zeros((1, width), bool)
        for kind, cut in (('top', alpha & ~numpy.vstack([empty, alpha[:-1]])),
                          ('bottom', alpha & ~numpy.vstack([alpha[1:], empty]))):
            for row in numpy.nonzero(cut.sum(axis=1) >= FLAT_RUN)[0]:
                for x, n in runs(numpy.nonzero(cut[row])[0].tolist()):
                    if n >= FLAT_RUN:
                        cuts.setdefault((members[0], int(row) + oy0, kind), set()).update(range(x, x + n))
    cuts = {k: numpy.array(sorted(v)) for k, v in cuts.items()}
    opaque = [numpy.asarray(layer.image)[..., ALPHA] == OPAQUE for layer in order]
    solid_layers = {i for i, layer in enumerate(order) if layer.element.get('name') in PLAY_LAYERS + ('foreground',) or layer.element.get('name', '').startswith('foreground')}
    ground_rows = numpy.arange(round(VIEW[1] * (1 - GROUND_FRACTION)), VIEW[1])
    seen, ground, frames = {}, 0, 0
    for cam_top in tops:
        live = [(k, xs) for k, xs in cuts.items() if k not in seen and edge_on_screen(order, k, cam_top)]
        rows = ground_rows[ground_rows + cam_top >= ROOM_H]
        for cam in scene.cameras:
            frames += 1
            # clouds count as cover only at their time-0 position (x_shift(layer, cam) without a clock): they drift in the game
            front = [None] * len(order)   # front[i]: opaque pixels of every layer drawn after layer i
            cover = numpy.zeros((VIEW[1], VIEW[0]), bool)
            solid = numpy.zeros((VIEW[1], VIEW[0]), bool)
            for i in range(len(order) - 1, -1, -1):
                front[i] = cover.copy()
                layer = order[i]
                x, y = layers.x_shift(layer, cam), screen_y(layer.oy, layer.py, cam_top)
                wrap = int(prop(layer.element, 'wrapx'))
                for dx in ((x - wrap, x) if wrap else (x,)):
                    blit(cover, opaque[i], dx, y)
                    if i in solid_layers:
                        blit(solid, opaque[i], dx, y)
            ground += int((~solid[rows]).sum())
            for k, xs in live:
                if k in seen:
                    continue
                i, y, kind = k
                sy = screen_y(y, order[i].py, cam_top)
                sx = xs + layers.x_shift(order[i], cam)
                on = (sx >= 0) & (sx < VIEW[0])
                shown_xs = sx[on][~front[i][sy, sx[on]]]
                if len(shown_xs):
                    seen[k] = (cam, cam_top, int(shown_xs.min()), int(shown_xs.max()))
    name = scene.mock.name
    for (i, y, kind), (cam, cam_top, x0, x1) in sorted(seen.items(), key=lambda s: (s[0][1], s[0][0])):
        print(f'{name} look: {order[i].element.get("name")} {kind} edge at room y {y} visible at camera {cam}, top {cam_top} '
              f'(screen x {x0}..{x1})')
    ok = not seen and not ground
    print(f'{name} look: {len(seen)} visible layer edges ({len(cuts)} cut rows) and {ground} px of open ground (bottom '
          f'{GROUND_FRACTION:.0%}, below the room) at {len(scene.cameras)} camera x by {len(tops)} camera tops '
          f'({tops[0]} to {tops[-1]}), {frames} frames: ' + ('ok' if ok else 'FAIL'))
    return ok


def protected(scene, unique=False):
    """(name, x0, y0, x1, y1, margin, margin below) of every rect the foreground must leave clear at every camera: the
    exclusions, and (rule B) the hero above the waist in the RoomStart and RoomExit boxes, his floor the bottom of the box."""
    rects = [(name, x0, y0, x1, y1, MARGIN, y1 < scene.floor) for name, x0, y0, x1, y1 in exclusions(scene, unique)]
    for kind, (x, y, w, h) in hero_boxes(scene).items():
        left = x - HERO_W if kind == 'RoomExit' else x   # the hero stands HERO_W left of the exit box when he triggers it
        rects.append((f'{kind} (hero above the waist)', left, y + h - HERO_H, x + w, y + h - WAIST, MARGIN, False))
    return rects


def check_layer(scene, group, pieces, out=print):
    """Rule B and the hero's cover time of one foreground group; the report goes to `out` (a test passes a silent one)."""
    factor = float(group.get('parallaxx'))
    room_x = prop(group, 'roomx') - scene.origin   # the checks run on the room-relative camera
    alpha = numpy.asarray(render_objects(group, pieces, scene.columns))[..., ALPHA] > COVER_ALPHA
    height = alpha.shape[0]
    failures = set()
    rects = protected(scene)
    for cam in scene.cameras:
        # a foreground pixel at room-relative x lands on screen at x - factor * cam; canvas column = x + FG_PAD
        base = jround(factor * (cam - room_x)) + FG_PAD
        for name, x0, y0, x1, y1, margin, below in rects:
            s0, s1 = max(int(x0 - margin - cam), 0), min(int(x1 + margin - cam), VIEW[0])   # only what is on screen
            if s1 <= s0:
                continue
            r0, r1 = int(y0 - margin), int(y1 + (margin if below else 0))   # no margin under what stands on the floor
            if alpha[max(r0, 0):min(r1, height), max(s0 + base, 0):max(s1 + base, 0)].any():
                failures.add(f'{name} at x {x0:.0f}')
    name = f'{scene.mock.name} {group.get("name")}'
    for failure in sorted(failures):
        out(f'{name} covers', failure)
    out(f'{name}: {len(failures)} covered objects (rule B: the hero and the walk edge may be covered elsewhere; '
          f'above the waist, {HERO_H - WAIST} px, he stays clear at RoomStart and RoomExit)')
    hidden_ok = True
    standing = hero_standing(scene, group, pieces)
    hidden_ok &= not standing
    spots = ', '.join(f'{x0}..{x1}' if x1 > x0 else f'{x0}' for x0, x1 in
                      [(x, x + n - 1) for x, n in runs(standing)])
    out(f'{name}: hero standing hidden ({HIDDEN_FRACTION:.0%} of his figure under alpha {HIDDEN_ALPHA}) at '
          f'{len(standing)} of {len(hero_route(scene))} box x' + (f' ({spots}): FAIL' if standing else ': ok'))
    for lift in HERO_LIFTS:
        hidden, at = hero_hidden(scene, group, pieces, lift=lift)
        where = 'on the floor' if not lift else f'{lift} px up (jump apex)'
        verdict = 'FAIL' if hidden > HIDDEN_MAX else 'ok'
        hidden_ok &= hidden <= HIDDEN_MAX
        out(f'{name}: hero {where} hidden ({HIDDEN_FRACTION:.0%} of his figure under alpha {HIDDEN_ALPHA}) at most '
              f'{hidden:.2f} s in a row, from room x {at:.0f} (limit {HIDDEN_MAX} s): {verdict}')
    return not failures and hidden_ok


def hero_route(scene):
    """Room-relative box x of the hero, 1 px apart, from his start box to where he triggers the exit."""
    boxes = hero_boxes(scene)
    return range(int(boxes['RoomStart'][0]), int(boxes['RoomExit'][0] - HERO_W) + 1)


def hero_camera(scene, x):
    """The game's steady camera left edge for a hero box at x (Player.cameraTarget on the box centre), room-relative."""
    return max(0, min(scene.width - VIEW[0], x + HERO_CAMERA_DX - VIEW[0] // 2))


def hero_cover(scene, group, alpha, x, lift=0, factor=None):
    """Fraction of the hero's figure (box at room x, feet `lift` px above the floor line, steady camera) under `alpha`."""
    factor = float(group.get('parallaxx')) if factor is None else factor
    cam = hero_camera(scene, x)
    left = x + HERO_FIGURE_DX - cam + jround(factor * (cam - (prop(group, 'roomx') - scene.origin))) + FG_PAD   # canvas column of his left edge
    y1 = int(scene.floor) - lift
    return alpha[y1 - HERO_H:y1, max(left, 0):max(left + HERO_FIGURE_W, 0)].sum() / (HERO_FIGURE_W * HERO_H)


def hero_standing(scene, group, pieces, alpha=None):
    """Box x of the route where the hero standing on the floor, under the steady camera, is hidden."""
    if alpha is None:
        alpha = numpy.asarray(render_objects(group, pieces, scene.columns))[..., ALPHA] >= HIDDEN_ALPHA
    return [x for x in hero_route(scene) if hero_cover(scene, group, alpha, x) >= HIDDEN_FRACTION]


def hero_hidden(scene, group, pieces, factor=None, alpha=None, lift=0):
    """(longest run in s, its start x) in which the hero's figure, `lift` px above the floor line, is at least
    HIDDEN_FRACTION covered by foreground pixels of alpha HIDDEN_ALPHA or more, walking the route at MOVE_SPEED with the
    game's camera (the camera's y is the floor framing at any lift: at that steady camera top every layer is as authored)."""
    if alpha is None:
        alpha = numpy.asarray(render_objects(group, pieces, scene.columns))[..., ALPHA] >= HIDDEN_ALPHA
    best, best_at, run, run_at = 0, 0, 0, 0
    for x in hero_route(scene):
        if hero_cover(scene, group, alpha, x, lift, factor) >= HIDDEN_FRACTION:
            run_at = x if run == 0 else run_at
            run += 1
            if run > best:
                best, best_at = run, run_at
        else:
            run = 0
    return best / MOVE_SPEED, best_at


def free_columns(scene, top):
    """Room-relative foreground x (px) where a full-width column from y `top` to the view's bottom stays clear of every
    protected rect at every camera of the room, over the x the room's cameras can show (0 .. width + 0.6 * last camera)."""
    last = scene.cameras[-1]
    span = range(-FG_PAD, int(VIEW[0] + FG_FACTOR * last))
    blocked = numpy.zeros(len(span), bool)
    for cam in scene.cameras:
        base = jround(FG_FACTOR * cam)
        for _, x0, y0, x1, y1, margin, below in protected(scene):
            s0, s1 = max(int(x0 - margin - cam), 0), min(int(x1 + margin - cam), VIEW[0])
            if s1 <= s0 or int(y1 + (margin if below else 0)) <= top:
                continue
            blocked[max(s0 + base + FG_PAD, 0):max(s1 + base + FG_PAD, 0)] = True
    return [x for i, x in enumerate(span) if not blocked[i]]


def runs(xs):
    """[(start, length)] of runs of consecutive integers."""
    result = []
    for x in xs:
        if result and result[-1][0] + result[-1][1] == x:
            result[-1] = (result[-1][0], result[-1][1] + 1)
        else:
            result.append((x, 1))
    return result


PER_KM = 1000                           # --scan counts rects per 1000 px


def scan():
    """Every room of the forest maps: protected rects (objects, ranges, the hero's start and exit) per 1000 px, the longest
    room-x stretch no protected rect touches, and the foreground px (and the widest run) where a column rising from the
    screen bottom to y TALL_TOP fits at every camera."""
    print(f'{"map":9} {"room":20} {"width":>5} {"rects":>5} {"/1000px":>7} {"longest gap":>11} {"tall free px":>12} {"widest run":>10}')
    for path in sorted((ROOT / 'assets/tiles').glob('forest-*.tmx')):
        root = ET.parse(path).getroot()
        for room in [o.get('name') for o in root.iter('object') if o.get('type') == 'Room']:
            scene = Scene(path, room, None, PLAY_TSX, (), {})
            rects = protected(scene, True)   # each object once: not with its sprite, not Player with RoomStart
            cover = numpy.zeros(scene.width, bool)
            for _, x0, _, x1, _, margin, _ in rects:
                cover[max(int(x0 - margin), 0):max(int(x1 + margin), 0)] = True
            gap = max((n for x, n in runs([x for x in range(scene.width) if not cover[x]])), default=0)
            free = [x for x in free_columns(scene, TALL_TOP) if x >= 0] if len(scene.cameras) else []   # x < 0 is barely seen
            widest = max(runs(free), key=lambda r: r[1], default=(0, 0))
            print(f'{path.stem:9} {room:20} {scene.width:5} {len(rects):5} {PER_KM * len(rects) / scene.width:7.1f} {gap:11} '
                  f'{len(free):12} {widest[1]:6} @{widest[0]}')


TERRAIN_DIR = ROOT / 'art/terrain/forest'
TERRAIN_MOCK = TERRAIN_DIR / 'mock-terrain.tmx'
SNIPPET = (ROOT / 'assets/tiles/forest-2.tmx', (0, 1120))   # forest-2 and the left edges of its snippets: the rock wall, the slope


def terrain():
    """mock.tmx with the play tileset swapped to the terrain sketch, its previews and a forest-2 snippet over the scene."""
    text = MOCK.read_text(encoding='utf-8')
    def source(match):
        path = match.group(1)
        if path == PLAY_TSX:
            return 'source="Forest-new.tsx"'
        return f'source="{Path("../../parallax/forest") / path}"' if not path.startswith('..') else \
            f'source="../../parallax/{path.removeprefix("../")}"'
    TERRAIN_MOCK.write_text(re.sub(r'source="([^"]+)"', source, text), encoding='utf-8')
    print(TERRAIN_MOCK.relative_to(ROOT))
    preview(FOREST_1, mock=TERRAIN_MOCK, out=TERRAIN_DIR)
    root, sets, _ = tile_images(TERRAIN_MOCK)
    path, xs = SNIPPET
    data = re.search(r'<data encoding="csv">\s*(.*?)</data>', path.read_text(encoding='utf-8'), re.S).group(1)
    rows = [[int(v) for v in line.strip().rstrip(',').split(',')] for line in data.strip().splitlines()]
    play = ET.fromstring(f'<layer width="{len(rows[0])}" height="{len(rows)}"><data>{csv(rows)}</data></layer>')
    tiles = render_layer(play, [s for s in sets if s[0] == 1])
    back = Image.new('RGBA', VIEW, root.get('backgroundcolor'))
    for layer in root.findall('layer'):
        if layer.get('name') not in ('Background', 'play-above'):
            top = -int(float(layer.get('offsety', 0)))   # rows 0 to 360 of the room
            back.alpha_composite(render_layer(layer, sets).crop((0, top, VIEW[0], top + VIEW[1])))
    for sx in xs:
        frame = back.copy()
        frame.alpha_composite(tiles.crop((sx, 0, sx + VIEW[0], VIEW[1])))
        target = TERRAIN_DIR / f'preview-forest2-{sx}.png'
        frame.convert('RGB').save(target)
        print(target.relative_to(ROOT))


FOREST_1 = Scene(ROOT / 'assets/tiles/forest-1.tmx', 'forest-1-traps', MOCK, PLAY_TSX, FOREGROUND,
                 {0: ('preview-0.png', 'start'), 320: ('preview-320.png', 560), 640: ('preview-640.png', 'exit')})
CALM = Scene(ROOT / 'assets/tiles/forest-1.tmx', 'forest-1-mob', DIR / 'mock-calm.tmx', TERRAIN_TSX, CALM_FOREGROUND,
             {0: ('preview-calm-a.png', 'start'), 80: ('preview-calm-b.png', 330), 160: ('preview-calm-c.png', 'exit')})
SCENES = {'forest-1': FOREST_1, 'calm': CALM}


OPTIONS = {'--scan', '--terrain', '--check', '--preview', '--game'}


def parse_args(args):
    """The options and the --room value; anything else stops the run before a file is written."""
    room, rest = None, list(args)
    if '--room' in rest:
        i = rest.index('--room')
        if i + 1 >= len(rest) or rest[i + 1] not in SCENES:
            sys.exit(f'--room needs one of: {", ".join(SCENES)}')
        room = rest.pop(i + 1)
        rest.pop(i)
    unknown = [a for a in rest if a not in OPTIONS]
    if unknown:
        sys.exit(f'unknown argument(s): {" ".join(unknown)} (options: {", ".join(sorted(OPTIONS))}, --room NAME)')
    return set(rest), room


if __name__ == '__main__':
    options, chosen = parse_args(sys.argv[1:])
    if '--scan' in options:
        scan()
        sys.exit(0)
    if '--terrain' in options:
        terrain()
        sys.exit(0)
    if '--check' in options:
        scenes = [SCENES[chosen]] if chosen else list(SCENES.values())
        if '--game' in options:   # the game map's own rooms (every room with a foreground group), not the sketches
            scenes = game_scenes()
            if not scenes:
                sys.exit('--game: no foreground group in the forest maps')
        sys.exit(0 if all([check(scene) for scene in scenes]) else 1)
    scene = SCENES[chosen or 'forest-1']
    if '--preview' not in options:
        build(scene)
    preview(scene, upscale=scene is not FOREST_1)
    if scene is CALM:
        gifs(scene)
    sys.exit(0 if check(scene) else 1)
