#!/usr/bin/env python3
"""Forest parallax mock and foreground gate (D108): writes `art/parallax/forest/mock.tmx`, renders previews and checks the foreground.

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
`roomx` of the group; the game reads each room's group at that origin (D108).

The check reads the room's objects from its map (everything the active tier of `check_contrast.SPRITES` draws, the patrol range
of a red mob, the travel of a lift, the route of a spider and the parts the game draws for the hive, the deadfall and the fissure)
and fails (exit 1) when a foreground pixel with alpha > 24 lies over any of them on screen (plus 16 px, none under what stands on the
floor) at any camera x of the room (every 4 px, steady camera), or over the hero's figure above the waist (31 to 15 px above the floor of the box,
plus 16 px) in the RoomStart and RoomExit boxes. Elsewhere the foreground may cover the hero and the walk edge, even fully (rule B,
author 2026-11-19), but never hide him where he stands nor for long (cover time, third pass and its review): his 11x31 px figure
counts as hidden when 85 % or more of it lies under foreground pixels of alpha 128 or more; standing at any x of his route from the
start to the exit, with the game's steady camera, he is never hidden, and walking that route at 100 px/s (on the floor and at a
jump's apex) he is hidden at most 0.5 s in a row; the check prints the hidden standing spots and the longest run of each mock. Hints are drawn by the HUD after the world, so the foreground never covers them and the check ignores them.
With `--game` the check also holds each room of the game maps to the giant-leaf rules (D143) and to the middle of long rooms
(`check_middle`, D153).
The previews use the game's framing: a forest room is 368 px tall and the camera top while the hero stands on the floor is
`FallingTrapLayout.floorCameraTop` (room y + 368 - 360 = 8), so a preview shows rows 8 to 368 and a bottom-rising piece must end
at the room's bottom edge (y 368) to reach the screen bottom. These rules are checked at that steady camera only.

Vertical parallax: every layer moves in y by its own factor from that floor camera (ORIGIN_Y, below). The camera top still
changes by up to 8 px (the room is 8 px taller than the view, so it follows the hero between room y 0 and ORIGIN_Y) and during a
room change; there is no look up or down any more (D111), so no larger range is previewed or checked. The pictures are drawn in
whole pixels, with offsets snapped as Math.round (half up); the game snaps only the horizontal offset (D154), so the bleed check
uses its exact vertical one. `--room calm` also writes `preview-calm-walk.gif`.
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
WOLF_HEIGHT = 24                        # Wolf.FRAME_HEIGHT: a routed wolf (D158) stands this tall on each floor line of its polyline
WOLF_OVERHANG_LEFT = 6                  # Wolf.BODY_X: its frame reaches this far past its route's left end
WOLF_OVERHANG_RIGHT = 8                 # FRAME_WIDTH - BODY_X - BODY_WIDTH: and this far past the right end
WOLF_LEAP_LIFT = 16                     # Wolf.LEAP_LIFT: a leap's arc rises at most this far over its higher floor
CAMERA_STEP = 1
CAMERA_TOP = 8                          # FallingTrapLayout.floorCameraTop for a 368 px room and a 360 px view: 0 + (368 - 360)
BACKGROUND = '#2c3f66'
PLAY_TSX = '../../../assets/tiles/ForestTerrain.tsx'
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
# The giant near leaves (D143, #597) lie in their own nearer group per room, `foreground-near-<room>`, drawn after
# `foreground-<room>` at NEAR_FACTOR. On top of rule B they have two rules of their own: they never come within MARGIN of an open
# pit from MARGIN above the floor line down (a pit has no collision, so rule B does not see it), and they never hide too much of a
# sharp foreground piece: of the piece's opaque on-screen pixels at most SHARE_POOLED over all cameras of the room together, and at
# most SHARE_RESTING at the first and at the last camera (where the view rests), judged where at least REST_SEEN of the piece is on
# screen. The HUD needs no rule: it is drawn after the world, over every layer (author, 2026-11-25).
GIANT = 'edge-giant-'
NEAR_PREFIX = 'foreground-near-'
NEAR_FACTOR = 2.2
SHARE_POOLED = 0.35
SHARE_RESTING = 0.5
REST_SEEN = 0.25
FG_FACTOR = 1.6
FG_PAD = 640                            # px of canvas left of x 0 for foreground objects at negative x (a narrow room's camera is negative)
# The middle of a long room (#664, D153): a room LONG_ROOM px wide or wider (its camera travels 96 px or more) is not left empty
# between the pieces at its ends. Bare lengths are foreground px (x as authored), between sheet rectangles of the sharp pieces.
LONG_ROOM = VIEW[0] + 6 * TILE
# piece: the top y it may hang at. A suggestion names the first that fits, so the lighter pieces come first.
HANGING = {'near-sprig': (0,), 'near-fern': (0,), 'near-canopy': (0,), 'vine-curtain': (0, -TILE), 'roots-curtain': (0, -TILE)}
RISING = {'rise-reeds': (128,), 'rise-fern': (112,)}   # the tall pieces standing on the bottom, and their top y
RISING_UNASKED = {'rise-trunk': (112,)}   # counts as a tall rising piece but is never asked for: the near tier's trunks are #635's
SKETCH_ONLY = ('hang-leaves-l', 'trunk-near')   # drawn for a sketch's top-left corner (cut at its side): not counted, not asked for
HANG_BARE_MAX = 320                     # the longest stretch with no hanging piece, where one would fit
RISE_BARE_MAX = 480                     # the longest stretch with no tall rising piece, where one would fit
SKY_MIN = 96                            # open sky between hanging pieces in every camera's view: never a continuous band
FIT_CLEAR = 64                          # px a piece that would break a bare stretch keeps from the pieces at its two ends
END_SEEN = 0.3                          # a piece at an end of the span may overhang it, this share of its width staying in view (D113's 0.3)
STONE_TYPES = ('FallingStone', 'Stone')   # a room with one takes none of STONE_BARRED (D113)
STONE_BARRED = ('near-canopy',)         # its hanging leaves could read as stones
# Everything the active tier of the contrast gate draws is excluded (SPRITES); the hero's start and exit boxes have their own
# rule (above the waist, check_layer). The names the review required are asserted so a change in the gate cannot drop one silently.
EXCLUDED_TYPES = {key.split(':')[0] for key, row in contrast.SPRITES.items() if row[0] == 'active'}
REQUIRED_TYPES = {'HangingHive', 'Deadfall', 'Bat', 'Bramble', 'Collectable', 'FireballRefill', 'Stone', 'Ladder', 'Spider', 'Beetle',
                  'RedMob', 'Escalator', 'Trigger'}
assert REQUIRED_TYPES <= EXCLUDED_TYPES, REQUIRED_TYPES - EXCLUDED_TYPES
FUNGUS_TILES = {127, 128, 129}         # the honey fungus is drawn by play-layer tiles, not an object
CLOUD_DRIFT = 3
CLOUD_WRAP = 1024
GIF_FRAME_MS = 70                       # the preview GIFs' frame time: 14.3 frames/s (20 frames/s made the GIFs too large)
MS_PER_S = 1000
GIF_FPS = MS_PER_S / GIF_FRAME_MS
GIF_SCALE = 2                           # the GIFs are drawn at 2x, nearest neighbour
PALETTE_SAMPLES = 16                    # frames sampled for the GIF palette
MEGABYTE = 1e6
RUN_FPS = 10                            # the hero's run cycle, frames/s, and its frames in the sheet row
RUN_FRAMES = 6
GIF_COLOURS = 128                       # the preview GIFs' global palette (one index is the transparent 'unchanged')
# Vertical parallax (author, 2026-11-19): each layer's y factor is its x factor, measured from the floor camera, so the steady
# frame (camera top ORIGIN_Y) is the authored one: screenY = (y - originY) - f * (camTop - originY), originY = room y +
# ORIGIN_Y, y the art's room y, not snapped in the game (D154). The camera top lies between the room top and ORIGIN_Y (8 px apart). Tiled lines the
# layers up when the view centre is on the map's parallax origin, so
# `parallaxoriginy` is ORIGIN_Y + half the view; every forest room lies at y 0, so it is exact for every room in y.
ORIGIN_Y = CAMERA_TOP
ROOM_H = ROWS * TILE
# Bleed (D111): the camera top lies in 0..ORIGIN_Y, so a layer of vertical factor f trails the camera by at most
# f * ORIGIN_Y px (12.8 at the foreground's 1.6), and the screen shake moves the camera by up to SHAKE px after the view was
# taken, but never past the room (ScreenShake.offset clamps it, ScreenShakeTest.theRoomClampIsHonoured): at camera top c it
# goes up by at most c px and down by at most ORIGIN_Y - c px. The margin counts that travel as (1 + f) times, conservatively
# (bleed_margin). Every layer gets one tile row (16 px) of bleed art past the room.
SHAKE = 5.0                             # px, the largest camera shake (ScreenShake.BLAST_AMPLITUDE_PX)
BLEED_ROWS = 1                          # play layer rows past the room bottom (16 px of deep soil)
MAP_ROWS = ROWS + BLEED_ROWS            # the rows of every layer that starts at the room top
PLAY_ABOVE = 1                          # rows of the `play-above` layer (16 px over the room top)
MID_ABOVE = 1                           # rows the `mid` strip starts above the room top
FLOOR_RAISE = TILE                      # px the forest floor lies over the art's first floor line (#648): the far, grove and mid
RAISE_ROWS = FLOOR_RAISE // TILE        # layers rise with it (offsety) and carry this many extra rows so they still reach the bottom
FG_TOP = 32                             # foreground canvas: room y -32 to 416
FG_HEIGHT = 448
CANOPY_TOP = {1, 61, 80, 20}           # the canopy clump's top-row gids (terrain.grid: canopy-l, -m1, -m2, -r), firstgid 1
DEEP = {'deep-1': (64, 65), 'deep-3': (68, 69)}   # deep soil tile ids in Forest-new.tsx (terrain.grid)
BURIED = {61: 70, 62: 71, 63: 72}      # the ground's bottom row (left, middle, right) -> its buried variant over deep soil
# (layer name, tileset key, parallaxx, parallaxy, extra properties, offsety)
LAYERS = (('sky', 'forest/sky', 0, 0, {}, 0), ('stars', 'shared/stars', 0, 0, {}, 0), ('moon', 'shared/moon', 0, 0, {}, 0),
          ('clouds', 'shared/clouds', 0, 0, {'driftx': CLOUD_DRIFT, 'wrapx': CLOUD_WRAP}, 0),
          ('far', 'forest/far', 0.15, 0.15, {}, -FLOOR_RAISE), ('grove', 'forest/grove', 0.25, 0.25, {}, -FLOOR_RAISE),
          ('mid', 'forest/mid', 0.4, 0.4, {}, -MID_ABOVE * TILE - FLOOR_RAISE), ('play-above', 'play-above', 1, 1, {}, -PLAY_ABOVE * TILE),
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
        self.cameras = room_cameras(self.width)
        start = hero_boxes(self)['RoomStart']
        self.floor = start[1] + start[BOX_H]


def room_cameras(width):
    """Room-relative steady camera x values of a room `width` px wide: 0 to width - view, or, for a room narrower than the view, the
    one camera that centres it (Player.cameraTarget: room x + (room width - view width) / 2, negative)."""
    if width < VIEW[0]:
        return range((width - VIEW[0]) // 2, (width - VIEW[0]) // 2 + 1)
    return range(0, width - VIEW[0] + 1, CAMERA_STEP)


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
            name = group.get('name', '')
            if name.startswith('foreground') and not name.startswith(NEAR_PREFIX):   # one scene per room, not per group
                scenes.append(GameScene(path, rooms[int(prop(group, 'roomx'))]))
    return scenes


def play_rows(scene):
    text = scene.source.read_text(encoding='utf-8')
    data = re.search(r'<layer [^>]*name="Background"[^>]*>\s*<data encoding="csv">\s*(.*?)</data>', text, re.S).group(1)
    rows = [[int(v) for v in line.strip().rstrip(',').split(',')] for line in data.strip().splitlines()]
    c0, c1 = scene.x // TILE, scene.x // TILE + scene.play_columns
    return [row[c0:c1] + [row[c1 - 1]] * PAD_COLUMNS for row in rows]


RAISED = ('forest/far', 'forest/grove', 'forest/mid')   # the strips that rise with the floor


def layer_rows(key):
    """Rows of a scenery layer: MAP_ROWS from its top, a raised strip also the rows its offset lifts it by, the mid strip its
    rows above the room too."""
    return MAP_ROWS + (RAISE_ROWS if key in RAISED else 0) + (MID_ABOVE if key == 'forest/mid' else 0)


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

    def run_on(self):
        """The strip's last RAISE_ROWS rows, empty after the stamps, repeat the row over them: the soil dark runs on."""
        for r in range(len(self.rows) - RAISE_ROWS, len(self.rows)):
            if not any(self.rows[r]):
                self.rows[r] = list(self.rows[r - 1])


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
    layer.run_on()


def grove(layer):
    layer.strip(['grove-a', 'grove-b', 'grove-c'], GROVE_ROW)
    layer.run_on()


def mid(layer):
    layer.strip(['mid-a', 'mid-b', 'mid-c', 'mid-d'], 0)   # layer offsety -32: the pieces start 32 px over the room top
    layer.run_on()


GRADED = ('deep-1',)                    # the soil rows graded under a floor's bottom row, then deep-3 (one row now, D111)
DEEP_STRIDE_C = 7                       # the two variants of a deep tile alternate on a column and row stride
DEEP_STRIDE_K = 3
PERIOD_ROWS = 9                         # play_above reads rows 1 to 8 of a column for its period


def play_bleed(rows, deep_first):
    """The room's play rows plus BLEED_ROWS of deep soil (Forest-new.tsx, `deep_first` its firstgid): deep-1 under a
    floor's bottom row, deep-3 under an open column (a pit falls into near black). The
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
    with the repeat of its tiles below row 0 (the shortest period of rows 1 to 8, continued backwards), so the camera's shake
    shows no cut; decor only, no collision."""
    above = [[0] * len(rows[0]) for _ in range(PLAY_ABOVE)]
    for c in range(len(rows[0])):
        if not rows[0][c]:
            continue
        if rows[0][c] in CANOPY_TOP:   # a canopy clump's top row tiles vertically: it repeats over the room top
            for k in range(PLAY_ABOVE):
                above[k][c] = rows[0][c]
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
FOREGROUND = (('hang-leaves-l', -80, 0), ('trunk-near', -48, 0), ('clump-fern', 32, 304), ('tuft', 112, 296),
              ('clump-leaves', 272, 304), ('vine-curtain', 600, 0), ('clump-grass', 608, 304), ('clump-mixed', 832, 304),
              ('roots-curtain', 1208, 0), ('edge-leaves', 1568, 311), ('edge-fern', 1600, 311))



# The calm room: forest-1-mob (`--scan`: the longest stretches with nothing to protect, see the card). #507 reworked the room
# (a red mob at x 192, a falling branch at 464 and an explosive stump at 592 on the route): the middle stretch the first sketch
# put the tall pieces in is protected now, so only two tall pieces still fit where no camera carries them over an object: the
# reeds at the left end and the fern fan at the right end; the vine curtain hangs over the left third, a leaf bough enters at
# the top-left, low clumps sit on the floor line (the near trunk found no x: every column crosses a protected rect).
CALM_FOREGROUND = (('hang-leaves-l', -80, 0), ('vine-curtain', 120, 0), ('rise-reeds', -50, 128), ('clump-grass', 288, 304),
                   ('rise-fern', 780, 112), ('clump-mixed', 744, 304), ('clump-fern', 832, 304))


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
            layer = Layer(info[key], scene.columns, layer_rows(key))
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


def render_objects(group, pieces, columns, top=0, height=ROWS * TILE):
    """The tile objects of a group on a canvas FG_PAD px wider to the left: canvas x = room-relative x + FG_PAD, canvas
    y = room y + `top` (the steady checks use the room, 0 to 368; the previews FG_TOP and FG_HEIGHT). The canvas reaches the
    group's rightmost object: a faster layer carries pieces from further right onto the screen."""
    right = max((int(float(o.get('x'))) + pieces[int(o.get('gid')) & GID_MASK].width for o in group.findall('object')), default=0)
    image = Image.new('RGBA', (max(columns * TILE, right) + FG_PAD, height))
    for obj in group.findall('object'):
        piece = pieces[int(obj.get('gid')) & GID_MASK]
        y = int(float(obj.get('y'))) - piece.height + top   # Tiled's y is the bottom edge of a tile object
        x = int(float(obj.get('x'))) + FG_PAD
        layer = Image.new('RGBA', image.size)
        layer.paste(piece, (x, y))
        image.alpha_composite(layer)
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
    """Room-relative x of the room's RoomStart and RoomExit, and their boxes (x, y, w, h). A RoomStart is a point: the hero's
    box (HERO_BOX) stands on the first solid top under it. The last room of a map has no RoomExit (RoomLayout); there the
    route ends at the room's right wall, so the stand-in exit box is put against the wall on the start's floor."""
    boxes, solids = {}, []
    for kind, props, x, y, w, h, _ in room_objects(scene):
        if kind in ('RoomStart', 'RoomExit'):
            boxes[kind] = (x, y, w or HERO_BOX[0], h or HERO_BOX[1])
    for group in scene.root.iter('objectgroup'):
        if group.get('name') == 'Collisions':
            solids += [(float(o.get('x')) - scene.x, float(o.get('y')), float(o.get('width', 0))) for o in group.findall('object')
                       if o.get('type') not in ('Platform', 'QuickSand')]
    x, y, w, h = boxes['RoomStart']
    tops = [top for left, top, width in solids if y < top < y + h and left < x + w and x < left + width]
    if tops:   # the box would sink into the floor: it stands on it
        boxes['RoomStart'] = (x, min(tops) - h, w, h)
    if 'RoomExit' not in boxes:
        floor = boxes['RoomStart'][1] + h
        boxes['RoomExit'] = (scene.width - TILE - HERO_BOX[0] + HERO_W, floor - HERO_BOX[1], HERO_BOX[0] - HERO_W, HERO_BOX[1])
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
    top = int(y + h - frame.height) if anchor == 'floor' else int(y) - contrast.TOP_LIFT.get(key, 0)
    return frame, left, top


def game_screen_y(y, factor, cam_top):
    """Game formula for a layer of vertical factor f: screenY = (y - originY) - f * (camTop - originY), y the art's room y, not
    snapped (D154); the steady camera top ORIGIN_Y shows every layer as authored."""
    return y - ORIGIN_Y - factor * (cam_top - ORIGIN_Y)


def screen_y(y, factor, cam_top):
    """`game_screen_y` in the whole pixels the mock draws in: the same at ORIGIN_Y, within half a pixel at another camera top."""
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
                image = render_objects(layer, pieces, scene.columns, FG_TOP, FG_HEIGHT)
                self.layers.append(Drawing(layer, image, -FG_TOP, px, py))
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


def preview(scene, time=0.0, mock=None, out=None, upscale=False):
    """Frames at the scene's preview cameras, with the visible layers, the first frame of every object sprite of the room at
    its rect and the hero stand-in where the scene puts it (the room's start, its exit or an x): the steady camera (camera
    top ORIGIN_Y). With `upscale`, also a copy twice the size (nearest neighbour) next to each frame."""
    layers = Layers(scene, mock)
    for cam, (name, where) in scene.previews.items():
        frame = layers.frame(scene, cam, where, ORIGIN_Y, time, (0, 0))
        target = (out or DIR) / name
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


def gifs(scene, fps=GIF_FPS):
    """The calm room as the game moves its camera: preview-calm-walk.gif (the hero walking from his start to the exit at
    MOVE_SPEED with the steady camera, the run cycle at 10 frames/s)."""
    layers = Layers(scene)
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
    mob's patrol range, a lift's travel, a spider's route and the boss's whole arena above the floor), the parts the game draws for the hive, the deadfall, the fissure
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
        if kind == 'Boss':   # the boss has no authored range: it roams its whole arena, wall to wall and up to its jumps
            x0, y0, x1, y1 = 0, 0, scene.width, scene.floor
        if points and kind == 'Wolf':   # a wolf's route (D158): its legs are floor lines, its 24 px body stands on them
            ps = [tuple(float(v) for v in point.split(',')) for point in points.split()]
            x0, x1 = x + min(px for px, _ in ps) - WOLF_OVERHANG_LEFT, x + max(px for px, _ in ps) + WOLF_OVERHANG_RIGHT
            y0, y1 = y + min(py for _, py in ps) - WOLF_HEIGHT - WOLF_LEAP_LIFT, y + max(py for _, py in ps)
        elif points:   # a spider's route: its silk and leap range lie on the polyline
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


BOX_TOP = 1                             # a PIL bbox is (left, top, right, bottom)
BOX_BOTTOM = 3
STRIP_LAYERS = ('sky', 'far', 'grove', 'mid')   # the layers whose art runs on to the bottom of the view; stars, moon and clouds are free pieces


def drawn_rows(image):
    """(first, last + 1) drawn rows of `image` (alpha above 0), or None when it is empty."""
    box = image.getchannel('A').getbbox()
    return None if box is None else (box[BOX_TOP], box[BOX_BOTTOM])


def bleed_margin(factor, room_left):
    """Screen px a layer of vertical factor `factor` must run past an edge for the camera shake, when the camera has
    `room_left` px of room beyond that edge of the view: the shake is clamped to the room (D156), so it travels no further."""
    return math.ceil(min(SHAKE, room_left) * (1 + factor))


def check_bleed(scene, out=print):
    """No layer shows a cut edge at any camera top from 0 to ORIGIN_Y (the whole range the camera moves in, D111), with the
    camera shake on top, and no bleed piece is missing. A cut is a drawn first row above the screen's top or a drawn last row
    above its bottom, by the margin its factor needs at that camera top (`bleed_margin`: the shake is clamped to the room, so
    none is needed below the floor camera or above the top one).
    - Every layer of LAYERS is present (`play-above` too; `play-below` in the game map, where the deep soil is its own layer).
    - A tile layer whose art touches its first image row must start above the screen; the last DRAWN row of a strip layer (sky,
      far, grove, mid) must end below the screen.
      The play layer, `play-above` and `play-below` count as one stack (its first and last drawn rows).
    - Every foreground piece that has a `-top` or `-foot` in fg.grid has it, at the position `with_bleed` gives (a removed or
      moved partner fails); a `-top` must start above the screen at its first DRAWN row (its piece's own rows 0 to 7 are empty),
      a `-foot` and an `edge-*` piece must end below it at their last drawn row.
    The report goes to `out` (a test passes a silent one)."""
    layers, ok = Layers(scene), True
    def fail(text):
        nonlocal ok
        out(f'{scene.source.name} {text}')
        ok = False
    def edges(label, top_y, rows, factor, top_edge, bottom_edge):
        for cam_top in range(ORIGIN_Y + 1):
            top = game_screen_y(top_y + rows[0], factor, cam_top)
            margin = bleed_margin(factor, cam_top)
            if top_edge and top > -margin:
                fail(f'{label}: its first drawn row is at screen y {top:g} at camera top {cam_top} (needs {-margin} or less)')
                break
        for cam_top in range(ORIGIN_Y + 1):
            bottom = game_screen_y(top_y + rows[1], factor, cam_top)
            margin = bleed_margin(factor, ORIGIN_Y - cam_top)
            if bottom_edge and bottom < VIEW[1] + margin:
                fail(f'{label}: its last drawn row ends at screen y {bottom:g} at camera top {cam_top} (needs {VIEW[1] + margin} or more)')
                break
    present = {d.element.get('name') for d in layers.layers}
    for name in [layer[0] for layer in LAYERS] + ['play-above'] + (['play-below'] if scene.game else []):
        if name not in present and name != 'foreground':
            fail(f'layer {name} is missing')
    play = [d for d in layers.layers if d.element.get('name') in ('play-above', 'Background', 'play-below')]
    if play:
        first, last = min(play, key=lambda d: d.oy), max(play, key=lambda d: d.oy + d.image.height)
        edges('play layer top', first.oy, drawn_rows(first.image) or (0, 0), 1, True, False)
        edges('play layer bottom', last.oy, drawn_rows(last.image) or (0, 0), 1, False, True)
    for d in layers.layers:
        if d.element.tag == 'layer' and d.element.get('name') not in ('play-above', 'Background', 'play-below'):
            rows = drawn_rows(d.image) or (0, 0)
            edges(d.element.get('name'), d.oy, rows, d.py, rows[0] == 0, d.element.get('name') in STRIP_LAYERS)
    spots = sprites.layout('forest/fg')[0]
    _, _, pieces = tile_images(scene.mock)
    for d in layers.layers:
        if d.element.tag != 'objectgroup':
            continue
        placed = {(o.get('name'), int(float(o.get('x'))), int(float(o.get('y')))) for o in d.element.findall('object')}
        for name, x, y in sorted(placed):
            if name.endswith(('-top', '-foot')):
                continue
            top = y - spots[name][SPOT_H] * TILE
            for partner in with_bleed([(name, x, top)], spots)[1:]:
                partner_name, partner_x, partner_top = partner
                want_y = partner_top + spots[partner_name][SPOT_H] * TILE   # an object's y is its bottom edge
                want = (partner_name, partner_x, want_y)
                if want not in placed:
                    fail(f'{name} at x {x}: its bleed piece {partner_name} is missing at x {partner_x}, y {want_y}')
        for obj in d.element.findall('object'):
            name, piece = obj.get('name'), pieces[int(obj.get('gid')) & GID_MASK]
            rows = drawn_rows(piece)
            if rows:
                edges(f'{name} at x {int(float(obj.get("x")))}', int(float(obj.get('y'))) - piece.height, rows, d.py,
                      name.endswith('-top'), name.endswith('-foot') or name.startswith('edge-'))
    return ok


def check(scene):
    """The steady-camera rules (exclusions, the hero at the start and exit, standing and walking cover: camera top
    ORIGIN_Y only, as the hero walks and stands) and the bleed at every camera top (check_bleed)."""
    root, _, pieces = tile_images(scene.mock)
    ok = check_bleed(scene)
    groups = room_groups(scene, root)
    ok &= check_layer(scene, groups, pieces)
    if scene.game:
        ok &= check_giant(scene, groups, pieces)
        ok &= check_share(scene, groups, pieces)
        ok &= check_middle(scene, groups, pieces)
    return ok


def room_groups(scene, root):
    """The foreground groups of the scene's room, in draw order."""
    return [g for g in root.findall('objectgroup')
            if g.get('name').startswith('foreground') and (not scene.game or prop(g, 'roomx') == scene.origin)]


def protected(scene, unique=False):
    """(name, x0, y0, x1, y1, margin, margin below) of every rect the foreground must leave clear at every camera: the
    exclusions, and (rule B) the hero above the waist in the RoomStart and RoomExit boxes, his floor the bottom of the box."""
    rects = [(name, x0, y0, x1, y1, MARGIN, y1 < scene.floor) for name, x0, y0, x1, y1 in exclusions(scene, unique)]
    for kind, (x, y, w, h) in hero_boxes(scene).items():
        left = x - HERO_W if kind == 'RoomExit' else x   # the hero stands HERO_W left of the exit box when he triggers it
        rects.append((f'{kind} (hero above the waist)', left, y + h - HERO_H, x + w, y + h - WAIST, MARGIN, False))
    return rects


def check_layer(scene, groups, pieces, out=print):
    """Rule B for each foreground group of one room, and the hero's cover time under all of them together (each at its own
    factor); `groups` is one group or a list. The report goes to `out` (a test passes a silent one)."""
    groups = groups if isinstance(groups, list) else [groups]
    name = f'{scene.mock.name} {groups[0].get("name")}'
    rects = protected(scene)
    failures = set()
    for group in groups:
        factor = float(group.get('parallaxx'))
        room_x = prop(group, 'roomx') - scene.origin   # the checks run on the room-relative camera
        alpha = numpy.asarray(render_objects(group, pieces, scene.columns))[..., ALPHA] > COVER_ALPHA
        height = alpha.shape[0]
        for cam in scene.cameras:
            # a foreground pixel at room-relative x lands on screen at x - factor * cam; canvas column = x + FG_PAD
            base = jround(factor * (cam - room_x)) + FG_PAD
            for rect, x0, y0, x1, y1, margin, below in rects:
                s0, s1 = max(int(x0 - margin - cam), 0), min(int(x1 + margin - cam), VIEW[0])   # only what is on screen
                if s1 <= s0:
                    continue
                r0, r1 = int(y0 - margin), int(y1 + (margin if below else 0))   # no margin under what stands on the floor
                if alpha[max(r0, 0):min(r1, height), max(s0 + base, 0):max(s1 + base, 0)].any():
                    failures.add(f'{rect} at x {x0:.0f}')
    for failure in sorted(failures):
        out(f'{name} covers', failure)
    out(f'{name}: {len(failures)} covered objects (rule B: the hero and the walk edge may be covered elsewhere; '
          f'above the waist, {HERO_H - WAIST} px, he stays clear at RoomStart and RoomExit)')
    layers = hero_layers(scene, groups, pieces)
    standing = hero_standing(scene, layers)
    hidden_ok = not standing
    spots = ', '.join(f'{x0}..{x1}' if x1 > x0 else f'{x0}' for x0, x1 in
                      [(x, x + n - 1) for x, n in runs(standing)])
    out(f'{name}: hero standing hidden ({HIDDEN_FRACTION:.0%} of his figure under alpha {HIDDEN_ALPHA}) at '
          f'{len(standing)} of {len(hero_route(scene))} box x' + (f' ({spots}): FAIL' if standing else ': ok'))
    for lift in HERO_LIFTS:
        hidden, at = hero_hidden(scene, layers, lift=lift)
        where = 'on the floor' if not lift else f'{lift} px up (jump apex)'
        verdict = 'FAIL' if hidden > HIDDEN_MAX else 'ok'
        hidden_ok &= hidden <= HIDDEN_MAX
        out(f'{name}: hero {where} hidden ({HIDDEN_FRACTION:.0%} of his figure under alpha {HIDDEN_ALPHA}) at most '
              f'{hidden:.2f} s in a row, from room x {at:.0f} (limit {HIDDEN_MAX} s): {verdict}')
    return not failures and hidden_ok


def pits(scene):
    """(x, width) of every open pit of the room: columns where no collision covers the line just under the floor."""
    blocks = [(float(o.get('x')) - scene.x, float(o.get('y')), float(o.get('width', 0)), float(o.get('height', 0)))
              for g in scene.root.findall('objectgroup') if g.get('name') == 'Collisions' for o in g.findall('object')]
    under = scene.floor + 1
    return runs([x for x in range(TILE, scene.width - TILE)   # the end walls' columns are never a pit
                 if not any(bx <= x < bx + w and by <= under < by + h for bx, by, w, h in blocks)])


def check_giant(scene, groups, pieces, out=print):
    """The giant near leaves of one room (D143): there is at least one, so no rule here can pass on nothing; they lie only in the
    near group at NEAR_FACTOR, which holds nothing else and is drawn after the sharp pieces; and they clear every pit at every
    camera x."""
    name = f'{scene.mock.name} {groups[0].get("name")}'
    failures = set()
    near = [g for g in groups if g.get('name').startswith(NEAR_PREFIX)]
    if not any(o.get('name', '').startswith(GIANT) for g in near for o in g.findall('object')):
        failures.add(f'no {GIANT}* piece in a near group')
    for group in groups:
        names = [o.get('name', '') for o in group.findall('object')]
        if group in near:
            if float(group.get('parallaxx')) != NEAR_FACTOR or float(group.get('parallaxy')) != NEAR_FACTOR:
                failures.add(f'{group.get("name")}: its factor is not {NEAR_FACTOR}')
            if not all(n.startswith(GIANT) for n in names):
                failures.add(f'{group.get("name")}: a piece that is no {GIANT}*')
        elif any(n.startswith(GIANT) for n in names):
            failures.add(f'{group.get("name")}: a {GIANT}* piece outside the near group')
    if near and groups.index(near[0]) != len(groups) - len(near):
        failures.add('the near group is not drawn last')
    holes = pits(scene)
    for group in near:
        factor = float(group.get('parallaxx'))
        room_x = prop(group, 'roomx') - scene.origin
        alpha = numpy.asarray(render_objects(group, pieces, scene.columns))[..., ALPHA] > COVER_ALPHA
        for cam in scene.cameras:
            base = jround(factor * (cam - room_x)) + FG_PAD
            for x, width in holes:
                s0, s1 = max(x - MARGIN - cam, 0), min(x + width + MARGIN - cam, VIEW[0])
                if s1 > s0 and alpha[int(scene.floor) - MARGIN:, max(s0 + base, 0):max(s1 + base, 0)].any():
                    failures.add(f'giant leaves over the pit at x {x}')
    for failure in sorted(failures):
        out(f'{name} near layer:', failure)
    out(f'{name}: near layer, {len(failures)} faults' + (': FAIL' if failures else ': ok'))
    return not failures


# one sharp piece: its name and room x, and the share of its opaque pixels the giant leaves hide (all cameras, first, last)
Share = namedtuple('Share', 'piece x pooled first last')


def hidden_shares(scene, groups, pieces):
    """A Share for every sharp foreground piece (not a bleed
    piece): how much of its opaque on-screen pixels the giant leaves in front of it hide at the steady camera. A resting share is
    0 where less than REST_SEEN of the piece is on screen there."""
    near = [(float(g.get('parallaxx')), prop(g, 'roomx') - scene.origin,
             numpy.asarray(render_objects(g, pieces, scene.columns))[..., ALPHA] >= HIDDEN_ALPHA)
            for g in groups if g.get('name').startswith(NEAR_PREFIX)]
    top, bottom = CAMERA_TOP, ROOM_H   # the rows of the room the steady view shows
    result = []
    for group in groups:
        if group.get('name').startswith(NEAR_PREFIX):
            continue
        factor = float(group.get('parallaxx'))
        room_x = prop(group, 'roomx') - scene.origin
        for obj in group.findall('object'):
            piece_name = obj.get('name', '')
            if piece_name.endswith(('-top', '-foot')):
                continue
            image = pieces[int(obj.get('gid')) & GID_MASK]
            x, y = int(float(obj.get('x'))), int(float(obj.get('y'))) - image.height
            opaque = numpy.asarray(image)[..., ALPHA] >= HIDDEN_ALPHA
            rows = slice(max(top - y, 0), max(min(bottom - y, image.height), 0))
            seen = covered = 0
            resting = {}
            for cam in scene.cameras:
                left = x - jround(factor * (cam - room_x))          # the piece's left edge on screen
                s0, s1 = max(left, 0), min(left + image.width, VIEW[0])
                if s1 <= s0 or rows.stop <= rows.start:
                    continue
                shown_px = opaque[rows, s0 - left:s1 - left]
                total = int(shown_px.sum())
                if not total:
                    continue
                cover = numpy.zeros_like(shown_px)
                for near_factor, near_room, alpha in near:
                    base = jround(near_factor * (cam - near_room)) + FG_PAD
                    part = alpha[y + rows.start:y + rows.stop, max(s0 + base, 0):max(s1 + base, 0)]
                    cover[:part.shape[0], :part.shape[1]] |= part
                hidden = int((shown_px & cover).sum())
                seen += total
                covered += hidden
                if total >= REST_SEEN * opaque.sum():
                    resting[cam] = hidden / total
            result.append(Share(piece_name, x, covered / seen if seen else 0.0,
                                resting.get(scene.cameras[0], 0.0), resting.get(scene.cameras[-1], 0.0)))
    return result


def check_share(scene, groups, pieces, out=print):
    """No giant leaf hides more of a sharp foreground piece than SHARE_POOLED over the room's cameras, or than SHARE_RESTING at
    the first or last camera (D143)."""
    name = f'{scene.mock.name} {groups[0].get("name")}'
    shares = hidden_shares(scene, groups, pieces)
    bad = [s for s in shares if s.pooled > SHARE_POOLED or max(s.first, s.last) > SHARE_RESTING]
    for piece, x, pooled, first, last in bad:
        out(f'{name}: giant leaves hide {piece} at x {x}: {pooled:.0%} over all cameras, {first:.0%} at the first, {last:.0%} at the last')
    worst = max((s.pooled for s in shares), default=0.0)
    worst_rest = max((max(s.first, s.last) for s in shares), default=0.0)
    out(f'{name}: sharp pieces hidden by giant leaves at most {worst:.0%} over all cameras (limit {SHARE_POOLED:.0%}) and '
        f'{worst_rest:.0%} at a resting camera (limit {SHARE_RESTING:.0%}): ' + ('FAIL' if bad else 'ok'))
    return not bad


def fg_span(scene):
    """The foreground x the room's cameras show: from 0 to the view's right edge at the last camera."""
    return VIEW[0] + jround(FG_FACTOR * scene.cameras[-1])


def covered(span, extents):
    """Which of the columns 0 .. span the (x0, x1) extents cover."""
    cover = numpy.zeros(span, bool)
    for x0, x1 in extents:
        cover[max(x0, 0):max(x1, 0)] = True
    return cover


def bare_stretches(span, extents):
    """[(start, length)] of the stretches of 0 .. span that none of the (x0, x1) extents covers."""
    return runs([int(x) for x in numpy.flatnonzero(~covered(span, extents))])


def open_sky(scene, extents):
    """(px, camera) of the camera whose view has the least open sky: the widest stretch of its view that no (x0, x1) extent of a
    hanging piece covers, at the camera where that stretch is narrowest."""
    cover = covered(fg_span(scene), extents)
    worst = (VIEW[0], scene.cameras[0])
    for cam in scene.cameras:
        left = max(jround(FG_FACTOR * cam), 0)
        edges = numpy.flatnonzero(numpy.concatenate(([True], cover[left:left + VIEW[0]], [True])))
        worst = min(worst, (int(numpy.diff(edges).max()) - 1, cam))
    return worst


def sharp_pieces(groups, pieces):
    """(name, x, top y, width) of every piece of the groups' sharp foreground."""
    for group in groups:
        if group.get('name').startswith(NEAR_PREFIX):
            continue
        for obj in group.findall('object'):
            image = pieces[int(obj.get('gid')) & GID_MASK]
            yield obj.get('name'), int(float(obj.get('x'))), int(float(obj.get('y'))) - image.height, image.width


def piece_extents(groups, pieces, placements):
    """[(x0, x1)] of the sharp foreground pieces that stand as `placements` ({piece: top ys}) allows."""
    return [(x, x + width) for name, x, top, width in sharp_pieces(groups, pieces) if top in placements.get(name, ())]


def keep_clear(scene):
    """The room's foreground canvas (render_objects, 0 to ROOM_H) with every pixel set that rule B bars to a sharp piece at some
    camera: check_layer's rects, carried to where each camera shows them."""
    mask = numpy.zeros((ROOM_H, fg_span(scene) + FG_PAD), bool)   # past it nothing is ever on screen
    rects = protected(scene)
    for cam in scene.cameras:
        base = jround(FG_FACTOR * cam) + FG_PAD
        for _, x0, y0, x1, y1, margin, below in rects:
            s0, s1 = max(int(x0 - margin - cam), 0), min(int(x1 + margin - cam), VIEW[0])
            if s1 > s0:
                r0, r1 = int(y0 - margin), int(y1 + (margin if below else 0))
                mask[max(r0, 0):max(r1, 0), max(s0 + base, 0):max(s1 + base, 0)] = True
    return mask


def clears(mask, image, x, y):
    """Whether the piece `image` at foreground (x, y) touches no barred pixel of keep_clear's mask."""
    alpha = numpy.asarray(image)[..., ALPHA] > COVER_ALPHA
    left = x + FG_PAD
    r0, r1 = max(y, 0), min(y + alpha.shape[0], mask.shape[0])
    c0, c1 = max(left, 0), min(left + alpha.shape[1], mask.shape[1])
    return r1 <= r0 or c1 <= c0 or not (alpha[r0 - y:r1 - y, c0 - left:c1 - left] & mask[r0:r1, c0:c1]).any()


def foreground_gids(root):
    """{piece name: its gid in this map} and {piece name: its sheet spot} of the foreground collection."""
    first = next(int(t.get('firstgid')) for t in root.findall('tileset') if t.get('source').endswith('forest-fg.tsx'))
    spots = sprites.layout('forest/fg')[0]
    return {name: first + spot[0] for name, spot in spots.items()}, spots


def placed_group(group, root, placed):
    """A copy of the foreground `group` holding only the pieces `placed` ((piece, x, top y)), each with its bleed pieces."""
    gids, spots = foreground_gids(root)
    copy_of = ET.fromstring(ET.tostring(group))
    for old in copy_of.findall('object'):
        copy_of.remove(old)
    for piece, x, top in with_bleed(placed, spots):
        w, h = spots[piece][SPOT_W] * TILE, spots[piece][SPOT_H] * TILE
        ET.SubElement(copy_of, 'object', name=piece, gid=str(gids[piece]), x=str(x), y=str(top + h), width=str(w), height=str(h))
    return copy_of


def candidate_xs(span, stretch, width):
    """The x a piece `width` px wide may take to break the bare `stretch` (start, length) of 0 .. `span`, the most central first:
    FIT_CLEAR from a piece at an end of the stretch; at an end of the span it may overhang, END_SEEN of its width staying in view."""
    start, length = stretch
    end = start + length
    seen = math.ceil(width * END_SEEN)
    low = start + FIT_CLEAR if start > 0 else seen - width
    high = end - FIT_CLEAR - width if end < span else span - seen
    return sorted(range(low, high + 1), key=lambda x: (abs(2 * x + width - start - end), x))


def fitting_piece(scene, groups, pieces, candidates, stretch, mask, hanging):
    """The first (piece, x, top y) of `candidates` ({piece: top ys}) that would break the bare `stretch` (start, length) and pass
    the room's foreground rules there: an x of candidate_xs that passes rule B, leaves the hero's cover time and the giant leaves'
    hidden share within their limits and, if `hanging`, leaves SKY_MIN of open sky. None where no piece fits at any such x, the
    only reason a longer stretch is accepted."""
    root = scene.root
    gids, spots = foreground_gids(root)
    sharp = next(g for g in groups if not g.get('name').startswith(NEAR_PREFIX))
    near = [g for g in groups if g.get('name').startswith(NEAR_PREFIX)]
    base = None
    for name, tops in candidates.items():
        xs = candidate_xs(fg_span(scene), stretch, spots[name][SPOT_W] * TILE)
        for top in tops:
            parts = with_bleed([(name, 0, top)], spots)
            for x in xs:
                if not all(clears(mask, pieces[gids[part]], x + dx, y) for part, dx, y in parts):
                    continue
                added = placed_group(sharp, root, [(name, x, top)])
                base = base if base is not None else hero_layers(scene, groups, pieces)
                layers = base + hero_layers(scene, [added], pieces)
                if hero_standing(scene, layers) or any(hero_hidden(scene, layers, lift)[0] > HIDDEN_MAX for lift in HERO_LIFTS):
                    continue
                if any(s.pooled > SHARE_POOLED or max(s.first, s.last) > SHARE_RESTING
                       for s in hidden_shares(scene, [added] + near, pieces)):
                    continue
                if hanging and open_sky(scene, piece_extents(groups + [added], pieces, HANGING))[0] < SKY_MIN:
                    continue
                return name, x, top
    return None


def check_middle(scene, groups, pieces, out=print):
    """The middle of one room (D153): a room with a stone takes no STONE_BARRED piece (D113); every hanging or tall rising piece
    stands at a top y its table allows; a long room (LONG_ROOM) has no stretch over HANG_BARE_MAX without a hanging piece, nor
    over RISE_BARE_MAX without a tall rising one, unless no such piece fits it (fitting_piece), and every camera's view keeps
    SKY_MIN of open sky between its hanging pieces."""
    name = f'{scene.mock.name} {groups[0].get("name")}'
    failures = []
    stone = any(kind in STONE_TYPES and 0 <= x < scene.width for kind, _, x, *_ in room_objects(scene))
    if stone and any(piece in STONE_BARRED for piece, *_ in sharp_pieces(groups, pieces)):
        failures.append(f'{", ".join(STONE_BARRED)} in a room with a stone (D113)')
    rising = {**RISING, **RISING_UNASKED}
    placements = {**HANGING, **rising}
    for piece, x, top, _ in sharp_pieces(groups, pieces):
        if piece in placements and top not in placements[piece]:
            failures.append(f'{piece} at x {x} has its top at y {top}, not at {" or ".join(str(t) for t in placements[piece])}')
    if scene.width < LONG_ROOM:
        report = f'a room under {LONG_ROOM} px, no limit on its bare stretches'
    else:
        span = fg_span(scene)
        mask = None
        report = []
        for what, counted, limit, candidates in (('hanging', HANGING, HANG_BARE_MAX, HANGING),
                                                 ('tall rising', rising, RISE_BARE_MAX, RISING)):
            allowed = {k: v for k, v in candidates.items() if not (stone and k in STONE_BARRED)}
            stretches = bare_stretches(span, piece_extents(groups, pieces, counted))
            full = 0
            for start, length in stretches:
                if length <= limit:
                    continue
                mask = mask if mask is not None else keep_clear(scene)
                fit = fitting_piece(scene, groups, pieces, allowed, (start, length), mask, candidates is HANGING)
                if fit:
                    piece, x, top = fit
                    failures.append(f'{length} px from x {start} without a {what} piece (limit {limit}): {piece} fits at x {x}, y {top}')
                else:
                    full += 1
            longest = max((n for _, n in stretches), default=0)
            report.append(f'{longest} px at most without a {what} piece (limit {limit}'
                          + (f', no piece fits in {full} longer' if full else '') + ')')
        sky, cam = open_sky(scene, piece_extents(groups, pieces, HANGING))
        if sky < SKY_MIN:
            failures.append(f'a continuous band: {sky} px of open sky at camera {cam} (at least {SKY_MIN})')
        report = ', '.join(report) + f', open sky at least {sky} px (limit {SKY_MIN})'
    for failure in failures:
        out(f'{name} middle:', failure)
    out(f'{name}: middle of the room, {report}' + (': FAIL' if failures else ': ok'))
    return not failures


def hero_route(scene):
    """Room-relative box x of the hero, 1 px apart, from his start box to where he triggers the exit."""
    boxes = hero_boxes(scene)
    return range(int(boxes['RoomStart'][0]), int(boxes['RoomExit'][0] - HERO_W) + 1)


def hero_camera(scene, x):
    """The game's steady camera left edge for a hero box at x (Player.cameraTarget on the box centre), room-relative; a room
    narrower than the view is centred in it."""
    if scene.width < VIEW[0]:
        return scene.cameras[0]
    return max(0, min(scene.width - VIEW[0], x + HERO_CAMERA_DX - VIEW[0] // 2))


def hero_layers(scene, groups, pieces, threshold=HIDDEN_ALPHA):
    """[(opaque canvas, factor, room x)] of the groups, what the hero cover rules read."""
    return [(numpy.asarray(render_objects(g, pieces, scene.columns))[..., ALPHA] >= threshold,
             float(g.get('parallaxx')), prop(g, 'roomx') - scene.origin) for g in groups]


def hero_cover(scene, layers, x, lift=0):
    """Fraction of the hero's figure (box at room x, feet `lift` px above the floor line, steady camera) under the opaque
    pixels of any of the `layers` (hero_layers)."""
    cam = hero_camera(scene, x)
    y1 = int(scene.floor) - lift
    figure = numpy.zeros((HERO_H, HERO_FIGURE_W), bool)
    for alpha, factor, room_x in layers:
        left = x + HERO_FIGURE_DX - cam + jround(factor * (cam - room_x)) + FG_PAD   # canvas column of his left edge
        part = alpha[y1 - HERO_H:y1, max(left, 0):max(left + HERO_FIGURE_W, 0)]
        figure[:part.shape[0], :part.shape[1]] |= part
    return figure.sum() / (HERO_FIGURE_W * HERO_H)


def hero_standing(scene, layers):
    """Box x of the route where the hero standing on the floor, under the steady camera, is hidden."""
    return [x for x in hero_route(scene) if hero_cover(scene, layers, x) >= HIDDEN_FRACTION]


def hero_hidden(scene, layers, lift=0):
    """(longest run in s, its start x) in which the hero's figure, `lift` px above the floor line, is at least
    HIDDEN_FRACTION covered by foreground pixels of alpha HIDDEN_ALPHA or more, walking the route at MOVE_SPEED with the
    game's camera (the camera's y is the floor framing at any lift: at that steady camera top every layer is as authored)."""
    best, best_at, run, run_at = 0, 0, 0, 0
    for x in hero_route(scene):
        if hero_cover(scene, layers, x, lift) >= HIDDEN_FRACTION:
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


def terrain_text(text):
    """The text of mock-terrain.tmx for the text of mock.tmx: the play tileset swapped to the terrain sketch, the other
    tileset paths rebased on its directory."""
    def source(match):
        path = match.group(1)
        if path == PLAY_TSX:
            return 'source="Forest-new.tsx"'
        return f'source="{(Path("../../parallax/forest") / path).as_posix()}"' if not path.startswith('..') else \
            f'source="../../parallax/{path.removeprefix("../")}"'
    return re.sub(r'source="([^"]+)"', source, text)


def terrain():
    """mock-terrain.tmx (mock.tmx over the terrain sketch), its previews and a forest-2 snippet over the scene."""
    TERRAIN_MOCK.write_text(terrain_text(MOCK.read_text(encoding='utf-8')), encoding='utf-8')
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
