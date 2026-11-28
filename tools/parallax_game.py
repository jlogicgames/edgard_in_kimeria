#!/usr/bin/env python3
"""Installs the Forest parallax layers into the game (D108): assets and the layers of `assets/tiles/forest-{0,1,2}.tmx`.

    python3 tools/parallax_game.py            # copy the assets and (re)write the parallax layers of every forest map

What it does, idempotently (a second run changes nothing):
- copies the exported parallax tilesets (`art/parallax/*/out`) to `assets/images/parallax/` and writes their `.tsx` files to
  `assets/tiles/parallax/` (image paths rewritten); the terrain sketch (`art/terrain/forest/out/ForestTerrain.png`, 140 tiles at the tile
  ids of the retired third-party sheet) becomes `assets/images/Tileset/ForestTerrain.png` with its own `ForestTerrain.tsx`, the play
  tileset of every forest map;
- retiles each map's play layer (`retile`: no seam where two rooms' floors meet, no rope over the room-edge walls, block faces, D114);
- writes into each forest map the map-wide scenery layers (sky, stars, moon, clouds, far, grove, mid, each as wide as its
  parallax factor needs: the camera reaches x = map width - 640, a layer of factor f is seen up to f * that + 640), `play-above` (1 row,
  `offsety` -16) and `play-below` (the deep soil, 1 row, `offsety` = the room height; the bleed is what the camera top's 0 to 8 px range and the shake need, D111), turns the Background's bottom ground row into its
  buried variant, and adds one `foreground-<room>` object group per room (tile objects relative to the room, `roomx` its x in the map).
  Layers with a `parallaxx`/`parallaxy` attribute or the property `scenery` are scenery to the game parser (it keeps them out of the
  play layer and of collision); the foreground is every object group whose name starts with `foreground`.
The game draws them with `world/ParallaxRenderer`; `python3 tools/parallax_mock.py --check --game` checks the foreground of every
room that has one against the map's objects (rule B, cover time, the middle of long rooms). The pieces of each room are the lists
below.
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
SHEET_COLUMNS = 20                                # tiles per row of ForestTerrain.png (320 px)
# the ground tiles of the play tileset: (left, middle, right) of its top, body, bottom and buried bottom rows (D111)
GROUND = ((22, 23, 24), (42, 43, 44), (62, 63, 64), (71, 72, 73))
# the boulder (33-35 over 53-55) and the slope (36-38 over 56-58) -> their back-rock variants (terrain.grid `back-*`): the same rock
# with no straw lip and no moss, for the rock that is scenery, not ground (forest-2's ruin and mounds; the boulder's corners slope, #607)
BACK_ROCK = dict(zip((33, 34, 35, 53, 54, 55, 36, 37, 38, 56, 57, 58), (82, 83, 84, 85, 86, 87, 74, 75, 76, 77, 78, 67)))
SLAB = (26, 27, 28)                               # the floating slab (left, middle, right): its underside is open
# Block faces (D114, terrain.grid `Block faces`): the ground piece's left and right columns are the faces (left in shadow, right lit by the
# moon, up and to the right) and its bottom row the underside; these are the extra tiles `retile` picks from the drawn ground around a cell
BODY_VARIANTS = (43, 5, 9, 12)                    # the soil body and three variants that break its 16 px repeat
FACES = ((42, 15), (44, 16))                      # (left face, its variant), (right face, its variant)
INNER_UL, INNER_UR, INNER_ULR = 17, 18, 19        # a body cell under a wall foot: moss against the face above, up-left, up-right or both
ROCK_RIGHT, ROCK_LEFT, ROCK_FOOT = (2, 3, 4), (6, 7, 8), (10, 11)   # the rock edges of a room (#547, terrain.grid `Rock edges`): three variants for a face with air on its right, on its left, and the feet (right, left)
PILLAR_TOP, PILLAR_BODY = 25, 29                  # a ground column one tile wide: both faces
KIND = {gid: k for k, kind in enumerate(GROUND) for gid in kind}   # tile -> its row: 0 top, 1 body, 2 underside, 3 buried
KIND.update({gid: 1 for gid in BODY_VARIANTS + FACES[0] + FACES[1] + (INNER_UL, INNER_UR, INNER_ULR, PILLAR_BODY)})
KIND.update({gid: 1 for gid in ROCK_RIGHT + ROCK_LEFT + ROCK_FOOT})
KIND[PILLAR_TOP] = 0
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
    'forest-1-spider': (('near-fern', -88, 0), ('near-sprig', 1308, 0), ('roots-curtain', 96, -16)),
    'forest-1-bramble': (('near-fern', -88, 0), ('near-canopy', 540, 0), ('roots-curtain', 72, -16)),
    'forest-1-climb': (('near-fern', -116, 0), ('near-sprig', 540, 0), ('vine-curtain', 544, 0)),
    'forest-1-route': (('near-fern', -88, 0), ('near-canopy', 848, 0)),
    'forest-1-guard': (('near-canopy', -88, 0), ('near-fern', 744, 0), ('vine-curtain', 280, -16)),
    'forest-1-hive': (('near-fern', -88, 0), ('near-sprig', 540, 0), ('rise-reeds', 248, 128)),
    'forest-1-tree': (('near-canopy', -88, 0), ('near-fern', 540, 0), ('roots-curtain', 40, -16)),
    'forest-1-deadfall': (('near-fern', -88, 0), ('near-sprig', 540, 0), ('vine-curtain', 120, 0)),
}
FOREST_0 = {
    'forest-0-lever': (('near-canopy', -128, 0), ('near-fern', 488, 0), ('clump-leaves', 440, 304), ('vine-curtain', 76, 0)),
    # the #597 sample room: more stems and clumps than the others, so the giant leaves are not the only shapes
    'forest-0-ladder': (('near-fern', -88, 0), ('near-canopy', 948, 0), ('roots-curtain', 172, 0), ('rise-reeds', -32, 128),
                        ('rise-reeds', 208, 128), ('rise-fern', 952, 112), ('clump-grass', 96, 294), ('clump-fern', 376, 304),
                        ('clump-mixed', 624, 304), ('tuft', 624, 296), ('tuft', 150, 296)),
    'forest-0-wall': (('near-canopy', -88, 0), ('near-fern', 772, 0), ('vine-curtain', 76, 0)),
    'forest-0-platform': (('near-fern', -88, 0), ('near-sprig', 796, 0), ('rise-reeds', -50, 128)),
    'forest-0-stone': (('near-fern', -88, 0), ('near-sprig', 692, 0), ('vine-curtain', 76, 0)),
    'forest-0-traps': (('near-fern', -88, 0), ('near-canopy', 668, 0), ('roots-curtain', 172, 0)),
}
FOREST_2 = {
    'forest-entry': (('near-canopy', -88, 0), ('near-fern', 692, 0), ('vine-curtain', 76, -16)),
    'forest-walls': (('near-fern', -128, 0), ('near-canopy', 452, 0), ('roots-curtain', 290, -16)),
    'forest-fissure': (('near-canopy', -88, 0), ('near-fern', 540, 0), ('vine-curtain', 76, 0)),
    'forest-glade': (('near-fern', -88, 0), ('near-canopy', 732, 0), ('roots-curtain', 60, -16)),
    'forest-roost': (('near-fern', -88, 0), ('near-sprig', 694, 0), ('roots-curtain', -40, -16)),
    'forest-hollow': (('near-fern', -88, 0), ('near-fern', 692, 0), ('vine-curtain', 76, 0)),   # a stone: no canopy (D113)
    'forest-canopy': (('near-fern', -88, 0), ('near-sprig', 1308, 0), ('rise-fern', 1268, 112)),
    'forest-boss': (('rise-reeds', 426, 128),),
}
# The giant near leaves of every room (#597, D143): blurred, in the room's own nearer group `foreground-near-<room>` at
# `parallax_mock.NEAR_FACTOR`. `edge-giant-left` and `-right` are tall masses on the screen's side edges at the first and the last
# camera; `edge-giant-low-*` are clumps under the floor line. A room takes a side mass only where it passes rule B, the cover time,
# the pit rule and the hidden share.
# Since the author asked for more of them (2026-11-25), some `edge-giant-low-*` stand with their tips on the floor line (top y 272 or
# 280 instead of 304); a few sharp low pieces in the lists above and below are raised the same way (y 294 to 296, tufts 284; 294 was 292 until the 2 px blast shake, #448, D156).
# The floor raise (#648) moved every piece standing from the bottom up 16 px with the floor. By hand where the moved objects or a
# pit came under a piece: seven hanging curtains rise 16 px too (top -16), and two of forest-entry's low leaves stay at y 320.
NEAR = {
    'forest-1-escalators': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                            ('edge-giant-low-c', 376, 288), ('edge-giant-low-d', 576, 272)),
    'forest-1-traps': (('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 180, 304), ('edge-giant-low-a', 64, 304),
                       ('edge-giant-low-d', 566, 272)),
    'forest-1-mob': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -56, 304), ('edge-giant-low-b', 170, 304),
                     ('edge-giant-low-c', 420, 288), ('edge-giant-low-d', 694, 304), ('edge-giant-low-a', 784, 304),
                     ('edge-giant-low-a', 934, 280)),
    'forest-1-lever': (('edge-giant-left', -128, 48), ('edge-giant-right', 416, 176), ('edge-giant-low-a', -80, 280),
                       ('edge-giant-low-b', 160, 304), ('edge-giant-low-c', 376, 272)),
    'forest-1-spider': (('edge-giant-left', -128, 48), ('edge-giant-right', 1568, 176), ('edge-giant-low-a', -56, 304),
                        ('edge-giant-low-b', 204, 304), ('edge-giant-low-c', 352, 304), ('edge-giant-low-d', 772, 304),
                        ('edge-giant-low-a', 1056, 304), ('edge-giant-low-b', 1340, 304), ('edge-giant-low-a', 48, 304),
                        ('edge-giant-low-a', 502, 304), ('edge-giant-low-a', 652, 304), ('edge-giant-low-a', 866, 304),
                        ('edge-giant-low-c', 1536, 272)),
    'forest-1-bramble': (('edge-giant-left', -112, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                         ('edge-giant-low-c', 352, 272)),
    'forest-1-climb': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 96, 304),
                       ('edge-giant-low-d', 576, 272)),
    'forest-1-route': (('edge-giant-left', -112, 48), ('edge-giant-right', 902, 112), ('edge-giant-low-a', -80, 280),
                       ('edge-giant-low-b', 612, 304), ('edge-giant-low-c', 873, 272), ('edge-giant-low-a', 64, 304)),
    'forest-1-guard': (('edge-giant-left', -112, 48), ('edge-giant-right', 794, 176), ('edge-giant-low-a', -80, 280),
                       ('edge-giant-low-b', 176, 304), ('edge-giant-low-c', 409, 288), ('edge-giant-low-d', 737, 272),
                       ('edge-giant-low-a', 64, 304), ('edge-giant-low-a', 822, 280)),
    'forest-1-hive': (('edge-giant-left', -128, 48), ('edge-giant-right', 400, 208), ('edge-giant-low-a', -80, 280),
                      ('edge-giant-low-b', 160, 304), ('edge-giant-low-c', 376, 272)),
    'forest-1-tree': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                      ('edge-giant-low-c', 376, 288), ('edge-giant-low-d', 272, 272), ('edge-giant-low-d', 582, 272)),
    'forest-1-deadfall': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                          ('edge-giant-low-c', 376, 288), ('edge-giant-low-d', 272, 272), ('edge-giant-low-d', 582, 272)),
    'forest-0-lever': (('edge-giant-left', -179, 48), ('edge-giant-right', 426, 368), ('edge-giant-low-a', -128, 280),
                       ('edge-giant-low-b', 136, 304), ('edge-giant-low-c', 353, 288), ('edge-giant-low-d', 272, 272)),
    'forest-0-ladder': (('edge-giant-left', -128, 48), ('edge-giant-right', 1049, 112), ('edge-giant-low-a', 40, 280),
                        ('edge-giant-low-b', 222, 304), ('edge-giant-low-c', 368, 304), ('edge-giant-low-d', 852, 272),
                        ('edge-giant-low-a', 512, 304), ('edge-giant-low-a', 662, 304), ('edge-giant-low-d', 1020, 272)),
    'forest-0-wall': (('edge-giant-left', -112, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 161, 304),
                      ('edge-giant-low-c', 402, 288), ('edge-giant-low-d', 667, 304), ('edge-giant-low-a', 752, 304),
                      ('edge-giant-low-a', 902, 280)),
    'forest-0-platform': (('edge-giant-left', -144, 48), ('edge-giant-right', 808, 176), ('edge-giant-low-a', -56, 304),
                          ('edge-giant-low-b', 170, 304), ('edge-giant-low-c', 420, 288), ('edge-giant-low-d', 694, 304),
                          ('edge-giant-low-d', 336, 272), ('edge-giant-low-a', 774, 304), ('edge-giant-low-d', 924, 272)),
    'forest-0-stone': (('edge-giant-left', -112, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 349, 304),
                       ('edge-giant-low-a', 64, 304)),
    'forest-0-traps': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -56, 304), ('edge-giant-low-b', 476, 304),
                       ('edge-giant-low-a', 96, 304), ('edge-giant-low-a', 582, 304), ('edge-giant-low-b', 752, 272)),
    'forest-entry': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -56, 304), ('edge-giant-low-b', 421, 320),
                     ('edge-giant-low-a', 112, 320), ('edge-giant-low-a', 742, 304)),
    'forest-walls': (('edge-giant-left', -205, 48), ('edge-giant-right', 406, 176), ('edge-giant-low-a', -152, 280),
                     ('edge-giant-low-b', 191, 304), ('edge-giant-low-a', 0, 304), ('edge-giant-low-a', 438, 304)),
    'forest-fissure': (('edge-giant-left', -128, 48), ('edge-giant-right', 456, 208), ('edge-giant-low-a', -80, 280),
                       ('edge-giant-low-b', 160, 304), ('edge-giant-low-c', 376, 272), ('edge-giant-low-b', 512, 272)),
    'forest-glade': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -56, 304), ('edge-giant-low-b', 187, 304),
                     ('edge-giant-low-c', 455, 288), ('edge-giant-low-d', 722, 304), ('edge-giant-low-a', 48, 304),
                     ('edge-giant-low-a', 326, 304), ('edge-giant-low-a', 812, 304), ('edge-giant-low-d', 992, 272)),
    'forest-roost': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                     ('edge-giant-low-c', 584, 288), ('edge-giant-low-d', 784, 272)),
    'forest-hollow': (('edge-giant-left', -128, 48), ('edge-giant-low-a', -80, 280), ('edge-giant-low-b', 160, 304),
                      ('edge-giant-low-c', 376, 288), ('edge-giant-low-d', 812, 272)),
    'forest-canopy': (('edge-giant-left', -112, 48), ('edge-giant-right', 1568, 176), ('edge-giant-low-a', -80, 280),
                      ('edge-giant-low-b', 204, 304), ('edge-giant-low-c', 488, 288), ('edge-giant-low-d', 772, 304),
                      ('edge-giant-low-a', 1056, 304), ('edge-giant-low-b', 1340, 304), ('edge-giant-low-a', 64, 304),
                      ('edge-giant-low-a', 310, 304), ('edge-giant-low-a', 860, 304), ('edge-giant-low-a', 1186, 304)),
    'forest-boss': (('edge-giant-left', -323, 48), ('edge-giant-right', 269, 368), ('edge-giant-low-a', -80, 304),
                    ('edge-giant-low-b', 138, 304), ('edge-giant-low-d', 368, 272)),
}
# More low pieces in every room (#597, D143, author 2026-11-25: "add more of the bottom pieces that were there before, then they
# will be visible"): sharp reeds, ferns and clumps in the gaps between and beside the giant leaves, on top of the rooms' lists above.
MORE_BOTTOM = {
    'forest-1-escalators': (('rise-fern', 552, 112), ('clump-mixed', -96, 304), ('clump-fern', 80, 296), ('tuft', 229, 296),
                            ('tuft', 324, 296), ('clump-mixed', 443, 304)),
    'forest-1-traps': (('rise-reeds', 168, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 296), ('tuft', 229, 296),
                       ('clump-mixed', 516, 304)),
    'forest-1-mob': (('rise-reeds', 864, 128), ('clump-fern', -96, 304), ('clump-leaves', 53, 304), ('tuft', 216, 296),
                     ('clump-mixed', 455, 294), ('clump-fern', 631, 304), ('tuft', 780, 296)),
    'forest-1-lever': (('rise-reeds', 240, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 296), ('tuft', 229, 296),
                       ('tuft', 324, 296), ('clump-grass', 539, 304)),
    'forest-1-spider': (('rise-reeds', 144, 128), ('rise-fern', 1272, 112), ('clump-fern', -96, 304),
                        ('clump-leaves', 53, 294), ('clump-grass', 216, 304), ('clump-mixed', 338, 304),
                        ('clump-fern', 1162, 304)),
    'forest-1-bramble': (('rise-reeds', 72, 128), ('clump-fern', -48, 304), ('clump-leaves', 101, 294), ('tuft', 264, 296),
                         ('clump-mixed', 431, 304)),
    'forest-1-climb': (('rise-reeds', 576, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 296), ('tuft', 229, 296),
                       ('clump-mixed', 540, 304)),
    'forest-1-route': (('rise-fern', 816, 112), ('clump-mixed', -96, 304), ('clump-mixed', 512, 304), ('clump-fern', 688, 304)),
    'forest-1-guard': (('rise-reeds', 0, 128), ('rise-fern', 696, 112), ('clump-mixed', -96, 304), ('clump-fern', 80, 304),
                       ('tuft', 229, 296), ('clump-fern', 444, 304), ('clump-leaves', 593, 304)),
    'forest-1-hive': (('rise-fern', 552, 112), ('clump-mixed', -96, 304), ('clump-fern', 80, 304), ('tuft', 229, 284),
                      ('tuft', 324, 296), ('clump-mixed', 539, 304)),
    'forest-1-tree': (('rise-reeds', 192, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 304), ('tuft', 229, 284),
                      ('tuft', 324, 296), ('clump-mixed', 443, 304)),
    'forest-1-deadfall': (('rise-reeds', 360, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 304), ('tuft', 229, 284),
                          ('tuft', 324, 296), ('clump-mixed', 443, 304)),
    'forest-0-lever': (('rise-reeds', 192, 128), ('clump-fern', -48, 304), ('clump-leaves', 101, 304), ('tuft', 264, 284)),
    'forest-0-ladder': (('clump-fern', -96, 304), ('clump-leaves', 221, 304), ('clump-mixed', 768, 304)),
    'forest-0-wall': (('rise-reeds', 168, 128), ('rise-reeds', 840, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 296),
                      ('tuft', 229, 296), ('clump-fern', 444, 304), ('clump-leaves', 593, 304), ('tuft', 756, 284)),
    'forest-0-platform': (('rise-reeds', 168, 128), ('rise-reeds', 384, 128), ('clump-fern', -96, 304),
                          ('clump-leaves', 53, 294), ('tuft', 216, 296), ('clump-mixed', 455, 304), ('clump-fern', 631, 304)),
    'forest-0-stone': (('rise-reeds', 360, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 296), ('tuft', 229, 296),
                       ('clump-fern', 324, 304), ('clump-leaves', 713, 294)),
    'forest-0-traps': (('rise-reeds', 168, 128), ('clump-fern', -96, 304), ('tuft', 53, 284), ('clump-mixed', 412, 304),
                       ('clump-fern', 588, 304)),
    'forest-entry': (('rise-fern', 696, 112), ('clump-fern', -96, 304), ('tuft', 53, 296), ('clump-mixed', 652, 304)),
    'forest-walls': (('rise-fern', 408, 112), ('clump-fern', -48, 304), ('clump-leaves', 101, 294), ('clump-grass', 264, 304),
                     ('clump-fern', 386, 304)),
    'forest-fissure': (('rise-reeds', 120, 128), ('clump-mixed', -96, 304), ('tuft', 80, 284), ('clump-fern', 175, 304),
                       ('tuft', 324, 296)),
    'forest-glade': (('rise-reeds', 672, 128), ('rise-fern', 840, 112), ('clump-fern', -96, 304), ('clump-leaves', 53, 304),
                     ('clump-grass', 216, 304), ('clump-mixed', 530, 304), ('clump-fern', 706, 304), ('tuft', 855, 284)),
    'forest-roost': (('clump-mixed', -96, 304), ('clump-fern', -24, 296), ('tuft', 383, 296), ('clump-mixed', 621, 304)),
    'forest-hollow': (('clump-mixed', -96, 304), ('clump-mixed', 443, 304), ('clump-fern', 764, 296)),
    'forest-canopy': (('rise-reeds', 1104, 128), ('clump-mixed', -96, 304), ('clump-fern', 80, 304), ('clump-fern', 229, 304),
                      ('clump-fern', 450, 304), ('tuft', 599, 296), ('clump-fern', 694, 296), ('clump-leaves', 843, 304),
                      ('tuft', 1014, 296), ('clump-grass', 1101, 294), ('clump-mixed', 1223, 304)),
    'forest-boss': (('clump-fern', -96, 304), ('clump-leaves', 53, 304), ('clump-mixed', 216, 304), ('clump-fern', 392, 296)),
}
# The middle of the long rooms (#664, D153, author 2026-11-27: sample A of forest-1-spider): now and then a hanging piece between
# the ones at the ends, spread along the room with open sky between them, and a tall stem rising from the bottom where one fits.
# Where no stem fits further in, one stands partly out of view at an end of the room.
# `parallax_mock.check_middle` (validateParallax) sets the limits for every room: a long room added later gets its pieces here.
MIDDLE = {
    'forest-0-ladder': (('near-sprig', 540, 0),),
    'forest-0-wall': (('near-sprig', 504, 0),),
    'forest-0-platform': (('near-fern', 416, 0),),   # fronds, not leaf blobs or vines, by the falling stone and its swaying vines
    'forest-0-stone': (('near-sprig', 464, 0),),
    'forest-1-mob': (('near-sprig', 536, 0), ('rise-reeds', -43, 128)),
    'forest-1-spider': (('vine-curtain', 500, -16), ('near-sprig', 888, 0)),
    'forest-1-route': (('near-canopy', 448, 0), ('rise-reeds', -9, 128)),
    'forest-1-guard': (('rise-reeds', 408, 128),),
    'forest-entry': (('near-sprig', 464, 0), ('rise-reeds', -40, 128)),
    'forest-glade': (('near-sprig', 452, 0), ('rise-reeds', -41, 128)),
    'forest-roost': (('near-fern', 336, 0), ('rise-fern', -210, 112), ('rise-fern', 685, 112)),
    'forest-hollow': (('near-sprig', 464, 0), ('rise-reeds', 516, 128), ('rise-fern', -210, 112)),
    'forest-canopy': (('near-sprig', 360, 0), ('near-fern', 768, 0), ('rise-reeds', -9, 128)),   # a fern by the swaying hanging vines
}
# every forest map and its rooms' sharp foreground
MAPS = {name: {room: pieces + MIDDLE.get(room, ()) + MORE_BOTTOM[room] for room, pieces in rooms.items()}
        for name, rooms in (('forest-0', FOREST_0), ('forest-1', FOREST_1), ('forest-2', FOREST_2))}
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


def variant(r, c, count):
    """A variant index fixed by the cell's place (D114): the same map always gets the same tiles, so a reinstall changes nothing."""
    x = (r * 0x9E3779B1 + c * 0x85EBCA77) & 0xffffffff   # an integer hash (murmur3's finaliser): neighbours look unrelated
    x = (x ^ x >> 16) * 0x85EBCA6B & 0xffffffff
    x = (x ^ x >> 13) * 0xC2B2AE35 & 0xffffffff
    return (x ^ x >> 16) % count


def retile(rows, solid=frozenset(), edges=None):
    """The play rows with the room-edge seams and the ropes gone (#236) and the blocks given faces (D114), every cell's emptiness
    unchanged (collision and `CollisionVisibilityTest` read it). A rope cell (the third-party rope column drawn over a `Wall`: the grab
    cue's straw where no rope is) becomes soil like the other walls, its top cell the lit walk edge where open air is above it (a short
    wall's top can be stood on); a slab tile set into a floor (ground tops on both sides) becomes the floor's top. The lit walk edge is
    the "stand here" cue, so a drawn cell that is not in `solid` ((row, column) cells of the map's solid collision blocks) never carries
    it: a ground top there shows the soil body, a boulder or slope cell its back-rock variant (D112).
    Then every ground cell takes its tile from the drawn ground around it (past the map's sides and bottom is ground, above it air): a top with ground above it
    (a wall's foot) is soil, so the walk edge ends at the wall; a body cell with air below is the underside; a cell with air on its left
    shows the left face (shadow), on its right the right face (lit), on both the pillar; a body cell under a wall foot whose top-left or
    top-right neighbour is air carries the inner corner; the faces take a variant from `variant`, and
    other body cells a soil body by `variant` among those unlike their left and upper neighbours. In the `edges` columns ({column: side of the room}) the faces and one-tile pillar cells above the floor (the first row whose inner neighbour is ground)
    become rock (#547): a variant by `variant` unlike the one above, facing the room, the last one a foot."""
    edges = edges or {}
    rows = [list(r) for r in rows]
    height, width = len(rows), len(rows[0])
    for r, row in enumerate(rows):
        for c, gid in enumerate(row):
            if gid in ROPE:
                row[c] = GROUND[0 if r and not rows[r - 1][c] else 1][1]
            elif gid in SLAB and 0 < c < width - 1 and KIND.get(row[c - 1]) == 0 and KIND.get(row[c + 1]) == 0:
                row[c] = GROUND[0][1]   # a floating slab's tile set into a floor (forest-2, x 1520): the floor's own top
    if solid:
        for r, row in enumerate(rows):
            for c, gid in enumerate(row):
                if (r, c) in solid:
                    continue
                if gid in BACK_ROCK:
                    row[c] = BACK_ROCK[gid]
                elif KIND.get(gid) == 0:
                    row[c] = GROUND[1][1]
    kinds = [[KIND.get(gid) for gid in row] for row in rows]

    def ground(r, c):   # past the map's sides and bottom the soil goes on; above its top is open air
        return r >= 0 and (not (r < height and 0 <= c < width) or kinds[r][c] is not None)

    for r, row in enumerate(rows):
        for c, kind in enumerate(kinds[r]):
            if kind is None:
                continue
            if kind == 0 and ground(r - 1, c):
                kind = 1
            if kind == 1 and not ground(r + 1, c):
                kind = 2
            left, right = not ground(r, c - 1), not ground(r, c + 1)
            if kind == 0:
                row[c] = PILLAR_TOP if left and right else GROUND[0][0 if left else 2 if right else 1]
            elif kind == 1 and left and right:
                row[c] = PILLAR_BODY
            elif kind == 1 and (left or right):
                row[c] = FACES[0 if left else 1][variant(r, c, 2)]
            elif kind == 1:
                above = ground(r - 1, c)
                inner_l, inner_r = above and not ground(r - 1, c - 1), above and not ground(r - 1, c + 1)
                # a soil body unlike the cell on its left and the one above (both already final), so no two alike touch
                bodies = [v for v in BODY_VARIANTS if v != (row[c - 1] if c else 0) and v != (rows[r - 1][c] if r else 0)]
                row[c] = (INNER_ULR if inner_l and inner_r else INNER_UL if inner_l else INNER_UR if inner_r
                          else bodies[variant(r, c, len(bodies))])
            else:
                row[c] = GROUND[kind][1 if left == right else 0 if left else 2]
    for c, inside in sorted(edges.items()):   # inside: +1 where the room lies to the right of the column (its first column), -1 to the left
        faces = ROCK_RIGHT if inside > 0 else ROCK_LEFT
        run = []
        for r, row in enumerate(rows):
            if 0 <= c + inside < width and kinds[r][c + inside] is not None:
                break   # the floor: from the first row whose inner neighbour is ground on, the column is soil, never rock or a foot
            if row[c] in FACES[0] + FACES[1] + (PILLAR_TOP, PILLAR_BODY):
                run.append(r)
        for r in run:
            above = rows[r - 1][c] if r else 0
            options = [v for v in faces if v != above]
            rows[r][c] = options[variant(r, c, len(options))]
        if run:
            rows[run[-1]][c] = ROCK_FOOT[inside < 0]
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


def edge_columns(text):
    """{column: +1 or -1} for the first (+1, the room lies to its right) and last (-1) column of every Room of the map: the end walls, drawn as rock."""
    import xml.etree.ElementTree as ET
    cols = {}
    for o in ET.fromstring(text).iter('object'):
        if o.get('type') == 'Room':
            first = int(float(o.get('x'))) // pm.TILE
            cols[first] = 1
            cols[first + int(float(o.get('width'))) // pm.TILE - 1] = -1
    return cols


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
    rows = retile(rows, solid_cells(text), edge_columns(text))
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
    rooms = MAPS[map_name]
    # every room's sharp group first, then every room's near group: the game draws a room's groups in file order
    for prefix, factor, placed in (('foreground-', pm.FG_FACTOR, rooms), (pm.NEAR_PREFIX, pm.NEAR_FACTOR, {r: NEAR[r] for r in rooms})):
        for room, pieces in placed.items():
            room_x = int(float(re.search(r'name="%s" type="Room" x="(\d+)"' % room, text).group(1)))
            objects, after = pm.foreground_xml(SimpleNamespace(foreground=pieces), first_info, next_object)
            next_object = after
            groups.append(f' <objectgroup id="{next_layer}" name="{prefix}{room}" parallaxx="{factor}" parallaxy="{factor}">\n'
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
