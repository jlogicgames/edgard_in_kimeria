#!/usr/bin/env python3
"""Contrast tiers gate (D76, #344, playtest F15): active sprites must be loud, decor must be quiet.

Every sprite placed in the shipped maps is drawn, in its rest frame, over what the game draws behind it: the map's
flat `background` colour (D92: the map property every shipped map must set, the clear colour behind the world), the map's
scenery layers (parallax layers and factor-1 decor, `world/ParallaxRenderer`, #236) and its `Background` tile layer, in the game's
order. A scenery layer moves with the camera (`x - round(factor * camX)`, vertical shift 0 at the steady camera), so a placement is
measured at every steady camera x (SCENERY_CAMERA_STEP apart) at which it is on screen, inside the camera range of its room, and the
worst case counts; a clouds layer, which also drifts with time, is sampled at DRIFT_PHASES phases wherever it can show through. A map
with no scenery layers (none ships since #236 converted forest-0 and forest-2) is measured over the flat colour and its `Background`
layer alone. Metrics (OKLab):

- outline: mean |L(silhouette pixel) - L(background pixel just outside it)| over every pair of an opaque pixel and a
  transparent 4-neighbour. Figure against ground at the edge; independent of scale.
- body: mean OKLab distance between each opaque pixel and the background under it.
- chroma: 90th percentile of the sprite's OKLab chroma.

Rules (the worst placement counts; glowing sprites at full and dimmest glow; the bush, the lever and the stump and root idle loops in every frame;
collectables with their pickup halo (#345), at rest with the dimmest halo and at the top of the bob with the brightest; the coin has no
halo, NO_HALO):
- active (hero, traps, enemies, collectables, mechanisms): outline >= ACTIVE_OUTLINE_MIN;
- decor (`Decor` kinds, `HangingVine`): outline <= DECOR_OUTLINE_MAX, DECOR_BODY_MIN <= body <= DECOR_BODY_MAX,
  chroma <= DECOR_CHROMA_MAX. D108: there is no absolute ink floor; decor is measured relative to the composited background.
Every object type in SpawnPoints and Collisions must be classified here as a sprite or as drawing nothing of its own;
an unknown type fails, so a new sprite cannot skip the check. Tile-layer scenery is reported, not gated: it is the
third-party props (#189) that the terrain sheet still carries at their old tile ids; the report is made for the maps that draw that
sheet and skips the others.
"""
import math
import re
from collections import namedtuple
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
MAPS = ('forest-0', 'forest-1', 'forest-2')
ACTIVE_OUTLINE_MIN = 0.28
DECOR_OUTLINE_MAX = 0.20
DECOR_BODY_MIN, DECOR_BODY_MAX = 0.08, 0.20
DECOR_CHROMA_MAX = 0.10
CHROMA_PERCENTILE = 0.9
ALPHA_MIN = 128
VIEW_HEIGHT = 360  # KimeriaGame.LOGICAL_H
TILE = 16
VIEW_WIDTH = 640  # KimeriaGame.LOGICAL_W
SCENERY_CAMERA_STEP = 4  # px between the steady camera x values a placement is measured at
SWEEP_CAMERA_STEP = 128  # the same for the fairy and monster-body sweeps, whose positions are already sampled coarsely;
# enough for bodies: a 4 px sweep gave identical results for every kind and map (measured)
DRIFT_PHASES = 8  # samples of a clouds layer's drift along its wrap length
HALF_UP = 0.5
GID_MASK = 0x0FFFFFFF  # Tiled keeps the flip flags in a gid's top bits
PLAY_LAYER = 'Background'  # the play layer: the game draws it after every scenery layer
SCENERY_ATTRIBUTES = ('parallaxx', 'parallaxy')  # a layer with one, or the property scenery=true, is scenery (TiledMapData.isScenery)
SCENERY_PROPERTY = 'scenery'
BACKGROUND_PROPERTY = 'background'  # D92: a Tiled colour, #RRGGBB or #AARRGGBB with alpha ff (Level.parseBackground)
HEX_RGB = 6  # digits of #RRGGBB
HEX_ARGB = 8  # digits of #AARRGGBB
HEX_PAIR = 2  # digits per channel
HEX_BASE = 16
OPAQUE_HEX = 'ff'
TILESET = 'assets/images/Tileset/ForestTerrain.png'   # the play sheet whose third-party props TILE_SCENERY's tile ids index (#189)
NEIGHBOURS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def java_float(path, name):
    text = (ROOT / path).read_text(encoding='utf-8')
    return float(re.search(rf'\b{name}\s*=\s*([0-9.]+)f', text).group(1))


def java_int(path, name):
    text = (ROOT / path).read_text(encoding='utf-8')
    return int(re.search(rf'\b{name}\s*=\s*([0-9]+)\s*[;,]', text).group(1))  # one of several declared on a line too


GLOW_MIN = java_float('core/src/main/java/com/jlogicsoftware/kimeria/entity/objects/FungusTrap.java', 'GLOW_MIN')
# The pickup cue every Collectable draws (#345): an additive halo behind it, alpha pulsing with a 2 px bob.
COLLECTABLE_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/items/Collectable.java'
HALO_SHEET = 'objects/pickup-glow.png'
HALO_STATES = ((0, java_float(COLLECTABLE_JAVA, 'HALO_ALPHA_MIN')),  # (lift in px, halo alpha): at rest, dimmest
               (java_int(COLLECTABLE_JAVA, 'BOB_HEIGHT'), java_float(COLLECTABLE_JAVA, 'HALO_ALPHA_MAX')))  # bob top, brightest
HALO_MARGIN = 8  # px of background around the halo kept when compositing it
# Collectables drawn without the halo (Collectable.hasHalo): the coin's white rim washes out against added light
# (outline 0.123 against the 0.28 floor at forest-2 x 336). They are still measured at rest and at the bob top.
NO_HALO = {'Collectable:Coin'}
FULL = (1.0,)
# The fairy helper (#338, D82) has no map placement: she flies with the hero. Every body frame is measured over her own
# light at its dimmest, and the glints over the selected glow of a 16 px target, at a fixed sweep of every map.
FAIRY_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/ui/FairyGuide.java'
FAIRY_FRAME = java_int(FAIRY_JAVA, 'FRAME')
FAIRY_BODY_FRAMES = java_int(FAIRY_JAVA, 'BODY_FRAMES')
FAIRY_LIGHT_SIZE = java_int(FAIRY_JAVA, 'LIGHT_SIZE')
FAIRY_LIGHT_ALPHA = java_float(FAIRY_JAVA, 'LIGHT_ALPHA_MIN')
FAIRY_GLOW_SIZE, FAIRY_GLOW_BORDER = java_int(FAIRY_JAVA, 'GLOW_SIZE'), java_int(FAIRY_JAVA, 'GLOW_BORDER')
FAIRY_GLOW_PAD, FAIRY_GLINT_GROW = java_float(FAIRY_JAVA, 'GLOW_PAD'), java_float(FAIRY_JAVA, 'GLINT_GROW')
FAIRY_GLINT_SIZE = java_int(FAIRY_JAVA, 'GLINT_SIZE')
FAIRY_SWEEP = (64, 40, 328, 48)  # x step, y from, y to, y step: one in every 4 x 3 tiles of every map
FAIRY_TARGET = 16  # the glints are measured on a lever-sized target's corners
RGB = 3  # colour channels of a pixel, without alpha
CHANNEL_MAX = 255
ALPHA = 3  # index of the alpha channel in an RGBA pixel
ROW_OUTLINE = 2  # (map, x, outline, body): the indices of a result row
ROW_BODY = 3
SPRITE_ANCHOR = 4  # the columns of a SPRITES row used below
SPRITE_GLOWS = 5
ROW_CAMERA = 4  # the steady camera x of a result (None where no scenery moves)
GLOWS = (1.0, GLOW_MIN)

# key -> (tier, sheet, frame (x, y, w, h), frame count to check, anchor, glow levels). Anchor 'floor': the frame's
# bottom on the object's bottom edge; 'top': the frame's top on the object's top edge (world Y grows downward); 'view': as
# 'top', but at the object's `lipTop` property if set, else no higher than the camera's top edge while the hero stands on the floor
# (FallingStone's lip, #360: FallingTrapLayout.floorCameraTop).
SPRITES = {
    'Decor:bush': ('decor', 'objects/decor-bush.png', (0, 0, 32, 24), 4, 'floor', FULL),  # brush frames are seen too (D64)
    'Decor:lookalike': ('decor', 'objects/decor-lookalike.png', (0, 0, 24, 16), 1, 'floor', FULL),
    'HangingVine': ('decor', 'objects/decor-vine.png', (0, 0, 8, 40), 1, 'top', FULL),
    'Decor:trunk': ('decor', 'objects/decor-trunk.png', (0, 0, 16, 288), 1, 'floor', FULL),  # #349: behind the branches
    'Decor:crown': ('decor', 'objects/decor-crown.png', (0, 0, 96, 40), 1, 'top', FULL),  # #421: hung over a trunk
    'ThornRoot': ('active', 'objects/trap-thorn-root.png', (0, 0, 16, 24), 1, 'floor', FULL),
    'ThornTuft': ('active', 'objects/trap-thorn-tuft.png', (0, 0, 16, 16), 1, 'floor', FULL),
    # #364 (D89): the bramble's clump at rest, at the root end of its box (`root=right` is the mirrored `Bramble-right`).
    'Bramble': ('active', 'objects/trap-bramble.png', (0, 0, 16, 24), 6, 'floor', FULL),  # all six: rest, the rustle tell, the arm
    'FallingBranch': ('active', 'objects/trap-branch.png', (0, 0, 32, 16), 1, 'top', FULL),
    'FallingStone': ('active', 'objects/trap-stone-lip.png', (0, 0, 24, 8), 1, 'view', FULL),  # the loose overhang at rest (#360)
    'ExplosiveStump': ('active', 'objects/trap-stump.png', (0, 0, 16, 16), 4, 'floor', FULL),  # the idle tell loop (#346)
    'ExplosiveRoot': ('active', 'objects/trap-root.png', (0, 0, 16, 24), 4, 'floor', FULL),  # the idle tell loop (#346)
    'Bomb': ('active', 'objects/trap-bomb.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'Puffball': ('active', 'objects/trap-puffball.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'ThornVine': ('active', 'objects/trap-vine.png', (0, 0, 24, 40), 1, 'top', FULL),
    # #365 (D90): every frame the hive can show, each placed by hive_parts where HangingHive.render draws it: rest, shake1,
    # shake2, release and bare at the object; falling halfway down its drop; fallen on the solid; the stinger tile down the band.
    'HangingHive': ('active', 'objects/trap-hive.png', (0, 0, 24, 32), 5, 'top', FULL),
    'HangingHive:falling': ('active', 'objects/trap-hive.png', (120, 0, 24, 32), 1, 'top', FULL),
    # #366 (D91): every frame the fissure can show at the object (rest1, rest2, warn1, warn2, vent, spent), and its jet as
    # EmberFissure.render draws it at full height (fissure_parts): the tip (top1, top2: the upper 8 rows) and the tile below.
    'EmberFissure': ('active', 'objects/trap-fissure.png', (0, 0, 16, 12), 6, 'top', FULL),
    'EmberFissure:jet': ('active', 'objects/trap-ember-jet.png', (0, 0, 8, 16), 2, 'top', FULL),
    'EmberFissure:tip': ('active', 'objects/trap-ember-jet.png', (16, 0, 8, 8), 2, 'top', FULL),
    'HangingHive:fallen': ('active', 'objects/trap-hive.png', (144, 0, 24, 32), 1, 'top', FULL),
    'HangingHive:stingers': ('active', 'objects/trap-stingers.png', (0, 0, 8, 16), 2, 'top', FULL),
    'HoneyFungus': ('active', 'objects/trap-honey-fungus.png', (0, 0, 24, 16), 1, 'floor', GLOWS),
    'FalseHoneyFungus': ('active', 'objects/trap-false-honey-fungus.png', (0, 0, 24, 16), 1, 'floor', GLOWS),
    'FallingPlatform': ('active', 'objects/trap-falling-platform.png', (0, 0, 32, 10), 4, 'top', FULL),  # #362: every fan frame
    'FallingPlatform:log': ('active', 'objects/trap-log.png', (0, 0, 32, 16), 1, 'top', FULL),
    'Platform': ('active', 'objects/platform.png', (0, 0, 32, 16), 1, 'top', FULL),
    # #349 (D80): the branch skins; `-left` is the copy drawn on the left of a trunk (`mirror`), a later frame of the strip.
    'Platform:branch': ('active', 'objects/trap-perch.png', (0, 0, 32, 16), 1, 'top', FULL),
    'Platform:branch-left': ('active', 'objects/trap-perch.png', (32, 0, 32, 16), 1, 'top', FULL),
    'FallingPlatform:branch': ('active', 'objects/trap-rotten-perch.png', (0, 0, 32, 16), 1, 'top', FULL),
    'FallingPlatform:branch-left': ('active', 'objects/trap-rotten-perch.png', (96, 0, 32, 16), 1, 'top', FULL),
    'Escalator': ('active', 'objects/GreyOff.png', (0, 0, 32, 8), 1, 'top', FULL),
    'Ladder': ('active', 'objects/ladder.png', (0, 0, 16, 16), 1, 'top', FULL),
    'Trigger': ('active', 'objects/lever.png', (0, 0, 16, 16), 2, 'floor', FULL),  # every Trigger draws a Lever: up, and down once pulled
    'Collectable:Coin': ('active', 'objects/coin.png', (0, 0, 16, 16), 2, 'floor', FULL),  # the face and the edge-on turn (#350)
    'Collectable:Bat': ('active', 'objects/bat.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'Collectable:Mushroom': ('active', 'objects/mushroom.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'CarryableTorch': ('active', 'objects/torch.png', (0, 0, 8, 16), 1, 'floor', FULL),
    'Stone': ('active', 'objects/stone.png', (0, 0, 8, 8), 1, 'floor', FULL),
    'FireballRefill': ('active', 'objects/fireball.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'Actionable:Wall': ('active', 'objects/wall-brick.png', (0, 0, 48, 96), 1, 'top', FULL),  # only a vertical Wall draws it
    'Bat': ('active', 'enemy/Bat.png', (0, 0, 16, 16), 4, 'top', FULL),  # the four flap frames of IDLE (art/objects/enemy/bat.grid)
    'RedMob': ('active', 'enemy/Mobs.png', (0, 160, 48, 32), 4, 'floor', FULL),  # every IDLE frame (art/objects/enemy/red-mob.grid)
    'YellowMob': ('active', 'enemy/yellow_mob.png', (0, 160, 48, 32), 1, 'floor', FULL),
    'Beetle': ('active', 'objects/monster-beetle.png', (0, 0, 24, 16), 10, 'floor', FULL),  # #327 (D100): every frame, the wind-up's white too
    # D101: the body a killed beetle leaves. It has no placement of its own: it lies wherever a beetle dies, so it is measured
    # by BODY_SWEEP on every floor top of every shipped map (body_sweep), as decor: harmless, it must not read as a live beetle.
    'Beetle:body': ('decor', 'objects/monster-beetle-body.png', (0, 0, 24, 16), 1, 'floor', FULL),
    'Spider': ('active', 'objects/monster-spider.png', (0, 0, 32, 16), 9, 'floor', FULL),  # #328 (D102): every frame
    'Spider:body': ('decor', 'objects/monster-spider-body.png', (0, 0, 32, 16), 1, 'floor', FULL),  # D101, by BODY_SWEEP
    # #483 (D103): a spider authored as a polyline route is measured along it (spider_parts): its floor frames every 16 px of
    # each floor, its wall frames (wall1, wall2, walltap) every 16 px up each wall, head up and head down (`-down`, flipped), on
    # the wall's side (`-left` mirrored for a solid on its left); on a thread its hanging frames every 8 px down the column and
    # the 1x4 silk tile every 4 px. A dark wall face fails the placement here, not in the art (the placement rule of D103).
    'Spider:wall': ('active', 'objects/monster-spider-wall.png', (0, 0, 16, 32), 3, 'top', FULL),
    'Spider:hang': ('active', 'objects/monster-spider.png', (288, 0, 32, 16), 2, 'top', FULL),
    'Spider:silk': ('active', 'objects/monster-spider.png', (352, 0, 1, 4), 1, 'top', FULL),
    # #329 (D106): the snail is measured along its range (snail_parts) in every frame, and its gob, both wobble frames, along
    # every firing line where the snail draws it (the gob's cell is the strip's ninth, its two 8x8 frames at the cell's top-left).
    'Snail': ('active', 'objects/monster-snail.png', (0, 0, 24, 16), 8, 'floor', FULL),
    'Snail:gob': ('active', 'objects/monster-snail.png', (192, 0, 8, 8), 2, 'top', FULL),
    'Snail:body': ('decor', 'objects/monster-snail-body.png', (0, 0, 24, 16), 1, 'floor', FULL),  # D101, by BODY_SWEEP
    # #331 (D115): every frame of the wolf, and its body (D101); measured once the wolf is placed, until then at the monster
    # spawns of the three maps (the art pass of 2026-11-21).
    'Wolf': ('active', 'objects/monster-wolf.png', (0, 0, 48, 24), 17, 'floor', FULL),
    'Wolf:body': ('decor', 'objects/monster-wolf-body.png', (0, 0, 48, 24), 1, 'floor', FULL),
    'Boss': ('active', 'enemy/Boss.png', (0, 224, 48, 32), 1, 'floor', FULL),  # THRONE, its rest state
    'Player': ('active', 'hero/hero-right.png', (0, 0, 64, 64), 4, 'floor', FULL),  # idle facing right (#343, D79)
    # #363 (D88): the deadfall draws three parts at rest, each placed by deadfall_parts as the game draws it: the bough on the
    # object, the tied log hauled up on the far side, the taut trip vine on the floor. `-right` keys are the mirrored copies.
    'Deadfall': ('active', 'objects/trap-deadfall-bough.png', (0, 0, 112, 24), 1, 'top', FULL),
    'Deadfall:log': ('active', 'objects/trap-deadfall.png', (0, 0, 32, 16), 1, 'top', FULL),
    'Deadfall:vine': ('active', 'objects/trap-deadfall-vine.png', (0, 0, 24, 8), 1, 'top', FULL),
}
MIRRORED = {f'{key}-right': key for key in ('Deadfall', 'Deadfall:log', 'Deadfall:vine', 'Bramble')}
MIRRORED.update({'Spider:wall-left': 'Spider:wall', 'Spider:wall-left-down': 'Spider:wall'})
SPRITES.update({mirrored: SPRITES[key] for mirrored, key in MIRRORED.items()})
# Keys drawn upside down (a spider heading down a wall, Spider.renderWall).
FLIPPED = {'Spider:wall-down': 'Spider:wall', 'Spider:wall-left-down': 'Spider:wall'}
SPRITES.update({flipped: SPRITES[key] for flipped, key in FLIPPED.items() if flipped not in SPRITES})
# Types that draw no sprite of their own, with what they are (a new type must be added to one of the two tables).
NO_SPRITE = {
    'Torch': 'a particle light (effects.Torch), no sprite',
    'Actionable': 'a lever target: a Torch is a particle light, a horizontal Wall draws nothing (a vertical one is Actionable:Wall)',
    'Checkpoint': 'an invisible zone',
    'Deadline': 'an invisible kill zone under the room',
    'QuickSand': 'collision only; drawn by the tile layer',
    'Wall': 'collision only; drawn by the tile layer',
    None: 'an untyped collision block; drawn by the tile layer',
}
SPRITE_GROUPS = ('SpawnPoints', 'Collisions')
# Tile-layer scenery reported against the map background (report only, #189): (row range, column range) in the tileset.
TILE_SCENERY = {'sign': ((0, 2), (9, 10)),
                'tile bush': ((4, 6), (6, 8)), 'dead tree': ((4, 6), (9, 16))}


def linear(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def oklab(pixel, glow=1.0):
    r, g, b = (linear(min(255, pixel[i] * glow)) for i in range(3))
    l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def is_opaque(image, x, y):
    return 0 <= x < image.width and 0 <= y < image.height and image.getpixel((x, y))[3] >= ALPHA_MIN


def measure(sprite, background, x, y, glow=1.0):
    """(outline, body) of `sprite` drawn with its top-left at (x, y) over `background`; outline is None with no edge."""
    pairs, body = [], []
    for j in range(sprite.height):
        for i in range(sprite.width):
            if not is_opaque(sprite, i, j) or not (0 <= x + i < background.width and 0 <= y + j < background.height):
                continue
            s = oklab(sprite.getpixel((i, j)), glow)
            body.append(math.dist(s, oklab(background.getpixel((x + i, y + j)))))
            for u, v in NEIGHBOURS:
                if not is_opaque(sprite, i + u, j + v) and 0 <= x + i + u < background.width and 0 <= y + j + v < background.height:
                    pairs.append(abs(s[0] - oklab(background.getpixel((x + i + u, y + j + v)))[0]))
    if not body:
        return None, None  # nothing opaque inside the background (an empty tile cell)
    return (sum(pairs) / len(pairs) if pairs else None), sum(body) / len(body)


def chroma_p90(sprite, glow=1.0):
    chroma = []
    for j in range(sprite.height):
        for i in range(sprite.width):
            if is_opaque(sprite, i, j):
                _, a, b = oklab(sprite.getpixel((i, j)), glow)
                chroma.append(math.hypot(a, b))
    chroma.sort()
    return chroma[max(0, int(CHROMA_PERCENTILE * len(chroma)) - 1)]


# More frames measured under the same key: the hero faces either way at every placement (#343, D79).
EXTRA_FRAMES = {'Player': (('hero/hero-left.png', (0, 0, 64, 64), 4),  # idle facing left
                          ('hero/hero-right.png', (0, 576, 64, 64), 4), ('hero/hero-left.png', (0, 576, 64, 64), 4))}  # carry idle (#343 part 2)


BRAMBLE_CLUMP = 16  # the clump frame's width: it is drawn at the root end of the object, not centred on it


def frames(key):
    _, sheet, box, count, _, _ = SPRITES[key]
    result = []
    for sheet, (x, y, w, h), count in ((sheet, box, count), *EXTRA_FRAMES.get(key, ())):
        image = Image.open(ROOT / 'assets/images' / sheet).convert('RGBA')
        result += [image.crop((x + n * w, y, x + n * w + w, y + h)) for n in range(count)]
    if key in MIRRORED:
        result = [frame.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for frame in result]
    if key in FLIPPED:
        result = [frame.transpose(Image.Transpose.FLIP_TOP_BOTTOM) for frame in result]
    return result


# The spider's route (#483, D103), as Spider.Route traces it: the floor frame, the wall frame and the hanging frame.
SPIDER_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/enemy/Spider.java'
SPIDER_FRAME = (32, 16)
SPIDER_WALL = (16, 32)
SPIDER_BODY_X = 2
SPIDER_BODY_WIDTH = 20
SPIDER_HANG_CLEARANCE = java_float(SPIDER_JAVA, 'HANG_CLEARANCE')
SPIDER_HANG_BODY_X = java_float(SPIDER_JAVA, 'HANG_BODY_X')
SPIDER_HANG_BODY_WIDTH = java_float(SPIDER_JAVA, 'HANG_BODY_WIDTH')
SPIDER_HANG_GAP = java_float(SPIDER_JAVA, 'HANG_GAP')
SPIDER_HANG_THREAD_X = java_float(SPIDER_JAVA, 'HANG_THREAD_X')  # the spinnerets' x in the hanging frame
SPIDER_STEP = 16
SPIDER_HANG_STEP = 8
SPIDER_START_FLOOR_FAR = 2  # a start thread's floor runs from point 1 (the lip) to point 2
SPIDER_SILK_TILE = 4


def spider_thread_out(lip_on_right):
    """Spider.threadOut: the hanging body clears the ledge's face by HANG_CLEARANCE on either side of a lip."""
    return SPIDER_HANG_CLEARANCE + (SPIDER_HANG_THREAD_X - SPIDER_HANG_BODY_X if lip_on_right
                                    else SPIDER_HANG_BODY_X + SPIDER_HANG_BODY_WIDTH - SPIDER_HANG_THREAD_X)


def spider_solids(root):
    """The static solids a route's walls run along: untyped and Wall collision boxes, in world px."""
    out = []
    for group in root.findall('objectgroup'):
        if group.get('name') != 'Collisions':
            continue
        offset_y = float(group.get('offsety', '0'))
        for obj in group.findall('object'):
            if obj.get('type') in (None, '', 'Wall'):
                out.append((float(obj.get('x')), float(obj.get('y')) + offset_y, float(obj.get('width', TILE)),
                            float(obj.get('height', TILE))))
    return out


def _covers(solids, x, top, bottom, on_right):
    covered, grew = top, True
    while grew and covered < bottom:
        grew = False
        for sx, sy, sw, sh in solids:
            if (sx == x if on_right else sx + sw == x) and sy <= covered < sy + sh:
                covered, grew = sy + sh, True
    return covered >= bottom


def spider_route(root, x, y, text):
    """The route Spider.Route.trace builds from a polyline (#483, D103), in the same terms: legs as dicts with `kind` ('floor'
    or 'wall'), `lo`, `hi` (position.x on a floor, the wall frame's top on a wall), `line` (the floor line, or the face's x) and,
    on a wall, `solid_right`; threads as dicts with `side` (0 at the route's start, 1 at its end), `thread_x`, `anchor_y` and
    `end_y` (the spinnerets at the thread's end). A polyline that is not a route gives no legs (Spider names it at load).
    The JUnit and Python sides are pinned to the same fixture, core/src/test/resources/tiles/spider-routes.json."""
    points = [(x + float(a), y + float(b)) for a, b in (pair.split(',') for pair in text.split())]
    solids = spider_solids(root)
    _, fh = SPIDER_FRAME
    _, wh = SPIDER_WALL
    segments = len(points) - 1
    pieces = []
    for s, ((ax, ay), (bx, by)) in enumerate(zip(points, points[1:])):
        if ay == by:
            pieces.append(('floor', s))
            continue
        top, bottom = min(ay, by), max(ay, by)
        right, left_side = _covers(solids, ax, top, bottom, True), _covers(solids, ax, top, bottom, False)
        if right != left_side:
            pieces.append(('wall', s, right))  # the third field: the solid is on the face's right
        elif s in (0, segments - 1) and not (right or left_side):
            pieces.append(('thread', s))
        else:
            return {'legs': [], 'threads': []}
    legs, threads = [], []
    for piece in pieces:
        kind, s = piece[0], piece[1]
        (ax, ay), (bx, by) = points[s], points[s + 1]
        if kind == 'floor':
            legs.append({'kind': 'floor', 'lo': min(ax, bx) - SPIDER_BODY_X, 'hi': max(ax, bx) - SPIDER_BODY_X - SPIDER_BODY_WIDTH,
                         'line': ay})
        elif kind == 'wall':
            legs.append({'kind': 'wall', 'lo': min(ay, by), 'hi': max(ay, by) - wh, 'line': ax, 'solid_right': piece[-1]})
    for piece in pieces:
        if piece[0] != 'thread':
            continue
        s = piece[1]
        start = s == 0
        (lip_x, lip_y), floor_y = (points[1], points[0][1]) if start else (points[s], points[s + 1][1])
        floor_far = points[SPIDER_START_FLOOR_FAR][0] if start else points[s - 1][0]  # the other end of the floor it hangs from
        out = 1 if floor_far < lip_x else -1
        threads.append({'side': 0 if start else 1, 'thread_x': lip_x + out * spider_thread_out(out > 0), 'anchor_y': lip_y,
                        'end_y': floor_y - SPIDER_HANG_GAP - fh})
    return {'legs': legs, 'threads': threads}


def spider_parts(root, x, y, text):
    """(key, left, top, w, h) for every pose a spider takes along its polyline route, where Spider draws it."""
    route = spider_route(root, x, y, text)
    fw, fh = SPIDER_FRAME
    ww, wh = SPIDER_WALL
    for leg in route['legs']:
        if leg['kind'] == 'floor':  # position.x from end to end, the frame on the floor line
            for left in range(int(leg['lo']), int(leg['hi']) + 1, SPIDER_STEP):
                yield 'Spider', left, leg['line'] - fh, fw, fh
            continue
        right = leg['solid_right']  # the frame on the open side of the face, head up and head down
        cell_x = leg['line'] - ww if right else leg['line']
        for cell_top in range(int(leg['lo']), int(leg['hi']) + 1, SPIDER_STEP):
            for down in (False, True):
                yield 'Spider:wall' + ('' if right else '-left') + ('-down' if down else ''), cell_x, cell_top, ww, wh
    for thread in route['threads']:
        thread_x, lip_y, end = thread['thread_x'], thread['anchor_y'], thread['end_y']
        for cell_top in range(int(lip_y), int(end) + 1, SPIDER_HANG_STEP):
            yield 'Spider:hang', thread_x - SPIDER_HANG_THREAD_X, cell_top, fw, fh
        for silk_top in range(int(lip_y), int(end), SPIDER_SILK_TILE):
            yield 'Spider:silk', thread_x, silk_top, 1, SPIDER_SILK_TILE


# The snail (#329, D106), as Snail draws it: its frame along its range, and its gob along every firing line.
SNAIL_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/enemy/Snail.java'
SNAIL_FRAME = (24, 16)
SNAIL_GOB = 8           # the drawn gob, an 8x8 cell centred on its core
SNAIL_STEP = 16         # px between measured snail positions along its range
SNAIL_GOB_STEP = 8      # px between measured gob positions along a line
SNAIL_MOUTH_X = java_float(SNAIL_JAVA, 'MOUTH_X')  # the mouth in the frame facing right
SNAIL_PIVOT_X = java_float(SNAIL_JAVA, 'BODY_X') + java_float(SNAIL_JAVA, 'BODY_WIDTH') / 2  # the body's centre: the mirror's axis
SNAIL_GOB_HEIGHT = java_float(SNAIL_JAVA, 'GOB_HEIGHT')
SNAIL_GOB_RANGE = java_float(SNAIL_JAVA, 'GOB_RANGE')
SNAIL_GOB_CORE = java_float(SNAIL_JAVA, 'GOB_CORE')


def snail_parts(root, x, y, w, h, props):
    """(key, left, top, w, h) for the snail at every SNAIL_STEP of its range and its gob every SNAIL_GOB_STEP along the
    firing line from each end of the range, facing either way, up to its range or the first solid (Snail.lineEnd)."""
    fw, fh = SNAIL_FRAME
    low, high = x - float(props.get('offNeg', 0)) * TILE, x + float(props.get('offPos', 0)) * TILE
    floor = y + h
    lefts = list(range(int(low), int(high) + 1, SNAIL_STEP))
    if lefts[-1] != int(high):
        lefts.append(int(high))
    for left in lefts:
        yield 'Snail', left, floor - fh, fw, fh
    solids = spider_solids(root)
    line_y = floor - SNAIL_GOB_HEIGHT
    core_top, core = line_y - SNAIL_GOB_CORE / 2, SNAIL_GOB_CORE
    for left in (low, high):
        for right in (True, False):
            mouth = left + SNAIL_MOUTH_X if right else left + 2 * SNAIL_PIVOT_X - SNAIL_MOUTH_X
            d = 0
            while d <= SNAIL_GOB_RANGE:
                cx = mouth + (d if right else -d)
                box_x = cx if right else cx - core
                if any(sx < box_x + core and box_x < sx + sw and sy < core_top + core and core_top < sy + sh for sx, sy, sw, sh in solids):
                    break
                yield 'Snail:gob', cx - SNAIL_GOB / 2, line_y - SNAIL_GOB / 2, SNAIL_GOB, SNAIL_GOB
                d += SNAIL_GOB_STEP


DEADFALL_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/objects/DeadfallTrap.java'


def java_number(path, name):
    """A numeric constant with or without the `f` suffix, one of several declared on a line too."""
    text = (ROOT / path).read_text(encoding='utf-8')
    return float(re.search(rf'\b{name}\s*=\s*(-?[0-9.]+)f?\s*[;,]', text).group(1))


def deadfall_parts(x, y, swing_right):
    """(key, left, top, width, height) of the three parts DeadfallTrap.render draws at rest, for a bough object at (x, y)."""
    n = lambda name: java_number(DEADFALL_JAVA, name)
    width, pivot_x, pivot_y = n('BOUGH_WIDTH'), n('PIVOT_X'), n('PIVOT_Y')
    toward, suffix = (1.0, '-right') if swing_right else (-1.0, '')
    px, py = x + (width - pivot_x if swing_right else pivot_x), y + pivot_y
    floor = py + n('FLOOR_BELOW_PIVOT')
    tied = math.radians(n('TIED_DEGREES'))
    cx, cy = px - toward * n('ROPE_LENGTH') * math.sin(tied), py + n('ROPE_LENGTH') * math.cos(tied)
    _, _, (_, _, log_w, log_h), *_ = SPRITES['Deadfall:log']
    _, _, (_, _, vine_w, vine_h), *_ = SPRITES['Deadfall:vine']
    vine_left = px + toward * n('VINE_SPRITE_CENTRE') - vine_w / 2
    return [('Deadfall' + suffix, x, y, width, n('BOUGH_HEIGHT')),
            ('Deadfall:log' + suffix, cx - n('FRAME_CENTRE_X'), cy - n('FRAME_CENTRE_Y'), log_w, log_h),
            ('Deadfall:vine' + suffix, vine_left, floor - n('VINE_SPRITE_DROP'), vine_w, vine_h)]


HIVE_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/objects/HangingHive.java'


def hive_parts(root, x, y):
    """(key, left, top, width, height) of what HangingHive.render draws for a hive at (x, y): the hive's frames at the object,
    the knocked hive halfway down and lying on the solid, and the stinger tile at every 16 px of the band (mirror of floorBelow)."""
    n = lambda name: java_number(HIVE_JAVA, name)
    width, height, band, mouth = n('WIDTH'), n('HEIGHT'), n('BAND_WIDTH'), y + n('MOUTH_OFFSET')
    left = x + (width - band) / 2
    floor = None
    for group in root.findall('objectgroup'):
        if group.get('name') != 'Collisions':
            continue
        for c in group.findall('object'):
            cx, cy, cw = float(c.get('x')), float(c.get('y')), float(c.get('width', 0))
            if c.get('type') == 'QuickSand' or cy < mouth - n('SIZE_TOLERANCE') or cx >= left + band or cx + cw <= left:
                continue
            floor = cy if floor is None else min(floor, cy)
    parts = [('HangingHive', x, y, width, height)]
    if floor is None:
        return parts  # the game refuses such a map; check_tiled_links says why
    drop = floor - (y + n('FALLING_BOTTOM'))
    parts += [('HangingHive:falling', x, y + drop / 2, width, height),
              ('HangingHive:fallen', x, floor - n('FALLEN_BOTTOM'), width, height)]
    _, _, (_, _, _, tile), *_ = SPRITES['HangingHive:stingers']
    top = mouth
    while top + tile <= floor:
        parts.append(('HangingHive:stingers', left, top, band, tile))
        top += tile
    return parts


FISSURE_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/objects/EmberFissure.java'


def fissure_parts(root, obj, x, y):
    """(key, left, top, width, height) of what EmberFissure.render draws for a fissure at (x, y): its frames at the object,
    and the full jet (the tip on top, every whole 16 px tile up from the floor line), cut by a solid above as
    EmberFissure.ceilingAbove cuts it (a one-way Platform and quicksand do not)."""
    n = lambda name: java_number(FISSURE_JAVA, name)
    props = {p.get('name'): p.get('value') for p in obj.findall('properties/property')}
    width, jet_width, floor = n('WIDTH'), n('JET_WIDTH'), y + n('FLOOR_OFFSET')
    height, left = float(props.get('height') or n('DEFAULT_HEIGHT')), x + (width - jet_width) / 2
    for group in root.findall('objectgroup'):
        if group.get('name') != 'Collisions':
            continue
        for c in group.findall('object'):
            cx, cy, cw, ch = (float(c.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
            if c.get('type') in ('Platform', 'QuickSand') or cx >= left + jet_width or cx + cw <= left:
                continue
            if floor - height < cy + ch <= floor - n('SIZE_TOLERANCE'):
                height = floor - (cy + ch)
    parts = [('EmberFissure', x, y, width, n('HEIGHT'))]
    _, _, (_, _, _, tile), *_ = SPRITES['EmberFissure:jet']
    _, _, (_, _, _, tip), *_ = SPRITES['EmberFissure:tip']
    top = floor - height
    parts.append(('EmberFissure:tip', left, top, jet_width, tip))
    row = floor - tile  # the tile rows are anchored on the floor line (render: floorMod(y - floor, 16)); a partial top row is left out
    while row >= top + tip:
        parts.append(('EmberFissure:jet', left, row, jet_width, tile))
        row -= tile
    return parts


def with_halo(background, halo, centre_x, centre_y, alpha):
    """`background` with `halo` added on top (GL_SRC_ALPHA, GL_ONE) at `alpha`, centred on (centre_x, centre_y)."""
    layer = Image.new('RGB', background.size)
    scaled = Image.eval(halo.convert('RGB'), lambda channel: int(channel * alpha))
    layer.paste(scaled, (centre_x - halo.width // 2, centre_y - halo.height // 2), halo.getchannel('A'))
    return Image.merge('RGBA', (*ImageChops.add(background.convert('RGB'), layer).split(), background.getchannel('A')))


def tsx_of(first):
    """The root element and the path of the `.tsx` a map's `<tileset>` points at (the shipped maps do not embed tilesets)."""
    if not first.get('source'):
        raise ValueError('a tileset without a source: the gate reads tilesets from their .tsx files')
    path = (ROOT / 'assets/tiles' / first.get('source')).resolve()
    return ET.parse(path).getroot(), path


def play_tileset(root):
    """(sheet path, columns) of the map's first tileset, the play layer's (gid 1 on): forest-1 draws the terrain sketch through
    its own `ForestTerrain.tsx`, as forest-0 and forest-2 do since #236. Read from the `.tsx`."""
    first = root.find('tileset')
    if first is None:
        raise ValueError('the map has no tileset')
    tsx, path = tsx_of(first)
    image = tsx.find('image')
    if image is None:
        raise ValueError(f'{path.name}: the play tileset must be a sheet')
    return (path.parent / image.get('source')).resolve(), int(tsx.get('columns'))


def sheet_tilesets(root):
    """[(firstgid, columns, sheet)] of every sheet tileset of the map, in file order; a collection of images (the foreground
    pieces) has no sheet and holds no layer tile."""
    sets = []
    for first in root.findall('tileset'):
        tsx, path = tsx_of(first)
        image = tsx.find('image')
        if image is None:
            continue
        if int(tsx.get('tilewidth')) != TILE or int(tsx.get('tileheight')) != TILE:
            raise ValueError(f'{path.name}: tiles must be {TILE} px')
        sets.append((int(first.get('firstgid')), int(tsx.get('columns')), sheet_image((path.parent / image.get('source')).resolve())))
    return sets


_SHEETS = {}


def sheet_image(path):
    if path not in _SHEETS:
        _SHEETS[path] = Image.open(path).convert('RGBA')
    return _SHEETS[path]


def render_cells(layer, sets, name):
    """(image, gids, width) of a tile layer drawn from the map's tilesets; a flipped cell is refused (the game refuses it too)."""
    width, height = int(layer.get('width')), int(layer.get('height'))
    gids = [int(v) for v in layer.find('data').text.replace('\n', '').split(',') if v.strip()]
    image = Image.new('RGBA', (width * TILE, height * TILE))
    for index, gid in enumerate(gids):
        if gid & ~GID_MASK:
            raise ValueError(f'{name}: layer {layer.get("name")} has a flipped cell, which the gate does not draw')
        if gid:
            first, columns, sheet = next(entry for entry in reversed(sets) if entry[0] <= gid)
            column, row = (gid - first) % columns, (gid - first) // columns
            image.paste(sheet.crop((column * TILE, row * TILE, column * TILE + TILE, row * TILE + TILE)),
                        ((index % width) * TILE, (index // width) * TILE))
    return image, gids, width


def tile_layer(root, name='map'):
    """The map's play layer: the layer named Background. A map without it fails: the gate never guesses which layer is the play layer."""
    flat = [l.get('name') for l in root.findall('layer') if not is_scenery(l)]
    if len(flat) > 1:  # TiledMapData overwrites every non-scenery tile layer into one play grid, last wins; this gate composites only one
        raise ValueError(f'{name}: more than one non-scenery tile layer {flat} (the game overwrites layer by layer, last wins; the gate composites only {PLAY_LAYER!r})')
    play = next((l for l in root.findall('layer') if l.get('name') == PLAY_LAYER), None)
    if play is None:
        raise ValueError(f'{name}: no layer named {PLAY_LAYER!r} (the play layer; scenery layers come before it)')
    return render_cells(play, sheet_tilesets(root), name)


Scenery = namedtuple('Scenery', 'name image factor offset_x offset_y wrap')


def is_scenery(layer):
    """TiledMapData.isScenery: a parallax attribute or the property scenery=true."""
    props = {p.get('name'): p.get('value') for p in layer.findall('properties/property')}
    return any(layer.get(a) is not None for a in SCENERY_ATTRIBUTES) or props.get(SCENERY_PROPERTY) == 'true'


def scenery_layers(root, name='map'):
    """The map's scenery layers in file order (their draw order), each as its image and parallax data (opacity folded into alpha)."""
    sets, result = sheet_tilesets(root), []
    for layer in root.findall('layer'):
        if not is_scenery(layer):
            continue
        image = render_cells(layer, sets, name)[0]
        opacity = float(layer.get('opacity', 1))
        if opacity != 1:
            image.putalpha(image.getchannel('A').point(lambda a: int(a * opacity)))
        props = {p.get('name'): p.get('value') for p in layer.findall('properties/property')}
        result.append(Scenery(layer.get('name'), image, float(layer.get('parallaxx', 1)), int(float(layer.get('offsetx', 0))),
                              int(float(layer.get('offsety', 0))), int(float(props.get('wrapx', 0)))))
    return result


def shift(factor, cam):
    """ParallaxRenderer.shift: how far a layer of `factor` trails the camera, round(factor * cam), half up."""
    return math.floor(factor * cam + HALF_UP)


def room_of(root, x, y):
    """(x, y, width, height) of the Room holding (x, y)."""
    for group in root.findall('objectgroup'):
        for obj in group.findall('object'):
            if obj.get('type') != 'Room':
                continue
            rx, ry, rw, rh = (float(obj.get(k)) for k in ('x', 'y', 'width', 'height'))
            if rx <= x < rx + rw and ry <= y < ry + rh:
                return rx, ry, rw, rh
    raise ValueError(f'no Room holds ({x}, {y})')


class Scene:
    """What the game draws behind the sprites of one map: the flat colour, the scenery layers and the play layer."""

    def __init__(self, name, root):
        self.name, self.root = name, root
        self.flat = tuple(map_background(root, name)) + (CHANNEL_MAX,)
        self.play = tile_layer(root, name)[0]
        self.layers = scenery_layers(root, name)
        self.strips = {}

    @property
    def size(self):
        return self.play.size

    def cameras(self, x, y, width, step):
        """The steady camera x values, `step` apart, at which x .. x + width is on screen, inside the camera range of the room
        holding (x, y) (the camera is clamped to its room); (None,) for a map with no scenery layer, where the camera changes nothing."""
        if not self.layers:
            return (None,)
        try:
            room_x, _, room_width, _ = room_of(self.root, x, y)
        except ValueError:   # a position between rooms (the fairy sweep): the whole map
            room_x, room_width = 0, self.size[0]
        low, high = room_x, room_x + max(room_width - VIEW_WIDTH, 0)
        if room_width < VIEW_WIDTH:   # a room narrower than the view is centred in it (Player.cameraTarget)
            return (int(room_x + (room_width - VIEW_WIDTH) // 2),)
        low, high = max(low, math.floor(x) - VIEW_WIDTH), min(high, math.ceil(x + width))
        low, high = min(low, high), max(low, high)
        return sorted({*range(int(low), int(high) + 1, step), int(high)})

    def clamp(self, box):
        left, top, right, bottom = box
        return max(0, int(left)), max(0, int(top)), min(self.size[0], int(right)), min(self.size[1], int(bottom))

    def draw(self, canvas, layers, box, cam, phase):
        left, top, right, bottom = box
        for layer in layers:
            base = layer.offset_x + cam - shift(layer.factor, cam)
            if layer.wrap:   # a clouds layer: drifted by `phase` of its wrap length, drawn twice so the seam never shows
                base += layer.wrap * phase // DRIFT_PHASES
            for origin in ((base - layer.wrap, base) if layer.wrap else (base,)):
                canvas.alpha_composite(layer.image.crop((left - origin, top - layer.offset_y, right - origin, bottom - layer.offset_y)))

    def backgrounds(self, box, cam):
        """The RGBA images of `box` (world px, inside the map) that the game draws behind it at steady camera x `cam`: flat colour,
        scenery in file order, the play layer. One image, or one per drift phase when a clouds layer can show through."""
        left, top, right, bottom = box
        phases = (0,)
        drifting = [i for i, layer in enumerate(self.layers) if layer.wrap]
        if cam is not None and drifting:
            over = Image.new('RGBA', (right - left, bottom - top))   # everything drawn after the first clouds layer
            self.draw(over, self.layers[drifting[0] + 1:], box, cam, 0)
            over.alpha_composite(self.play.crop(box))
            if over.getchannel('A').getextrema()[0] != CHANNEL_MAX:
                phases = range(DRIFT_PHASES)
        images = []
        for phase in phases:
            canvas = Image.new('RGBA', (right - left, bottom - top), self.flat)
            if cam is not None:
                self.draw(canvas, self.layers, box, cam, phase)
            canvas.alpha_composite(self.play.crop(box))
            images.append(canvas)
        return images

    def windows(self, box, x, y, width, step=SCENERY_CAMERA_STEP):
        """[(camera, origin x, origin y, image)] for `box`: every distinct background over the cameras of `cameras`."""
        box = self.clamp(box)
        seen, result = set(), []
        for cam in self.cameras(x, y, width, step):
            for image in self.backgrounds(box, cam):
                key = image.tobytes()
                if key not in seen:
                    seen.add(key)
                    result.append((cam, box[0], box[1], image))
        return result


def parse_colour(value):
    """(r, g, b) of a Tiled colour as Level.parseBackground reads it: #RRGGBB, or #AARRGGBB with alpha ff."""
    digits = value[1:] if value.startswith('#') else value
    if len(digits) not in (HEX_RGB, HEX_ARGB) or any(c not in '0123456789abcdefABCDEF' for c in digits):
        raise ValueError(f'{BACKGROUND_PROPERTY}: expected #RRGGBB or #AARRGGBB, got {value!r}')
    if len(digits) == HEX_ARGB:
        if digits[:HEX_PAIR].lower() != OPAQUE_HEX:
            raise ValueError(f'{BACKGROUND_PROPERTY}: must be opaque (alpha ff), got {value!r}')
        digits = digits[HEX_PAIR:]
    return tuple(int(digits[i:i + HEX_PAIR], HEX_BASE) for i in range(0, HEX_RGB, HEX_PAIR))


def map_background(root, name='map'):
    """The map's flat background colour (D92). A shipped map without the property fails: the gate never guesses."""
    for prop in root.findall('properties/property'):
        if prop.get('name') == BACKGROUND_PROPERTY:
            return parse_colour(prop.get('value', ''))
    raise ValueError(f'{name}: set the map property {BACKGROUND_PROPERTY!r} (D92), the colour behind the world')


def composite(layer, colour):
    """The flat background colour, then one tile layer: a map with no scenery layers, and the test fixtures."""
    image = Image.new('RGBA', layer.size, tuple(colour) + (CHANNEL_MAX,))
    image.alpha_composite(layer)
    return image


def key_of(group, obj, props):
    kind = obj.get('type')
    side = '-left' if props.get('mirror') == 'true' else ''
    if group == 'Collisions':
        if kind == 'Platform' and props.get('skin') == 'branch':
            return 'Platform:branch' + side, kind
        return kind if kind == 'Platform' else None, kind
    if kind == 'Decor':
        return 'Decor:' + props.get('kind', 'bush'), kind
    if kind == 'Collectable':
        return 'Collectable:' + (props.get('item') or obj.get('name') or 'Coin').title(), kind
    if kind == 'Bramble':
        return ('Bramble-right' if props.get('root') == 'right' else 'Bramble'), kind  # -right: drawn mirrored (Bramble.java)
    if kind == 'FallingPlatform' and props.get('skin') == 'log':
        return 'FallingPlatform:log', kind
    if kind == 'FallingPlatform' and props.get('skin') == 'branch':
        return 'FallingPlatform:branch' + side, kind
    if kind == 'Actionable' and props.get('type') == 'Wall' and props.get('movement') == 'vertical':
        return 'Actionable:Wall', kind
    return (kind if kind in SPRITES else None), kind


def map_roots():
    return {name: ET.parse(ROOT / f'assets/tiles/{name}.tmx').getroot() for name in MAPS}


def placements(errors, roots=None):
    """Yields (map, key, x, y, w, h) for every sprite; records unclassified types in `errors`."""
    for name, root in (roots or map_roots()).items():
        for group in root.findall('objectgroup'):
            if group.get('name') not in SPRITE_GROUPS:
                continue
            offset_y = float(group.get('offsety', '0'))
            for obj in group.findall('object'):
                props = {p.get('name'): p.get('value') for p in obj.findall('properties/property')}
                if group.get('name') == 'SpawnPoints' and obj.get('type') == 'EmberFissure':
                    yield from ((name, *part) for part in fissure_parts(root, obj, float(obj.get('x')), float(obj.get('y')) + offset_y))
                    continue
                if group.get('name') == 'SpawnPoints' and obj.get('type') == 'HangingHive':
                    yield from ((name, *part) for part in hive_parts(root, float(obj.get('x')), float(obj.get('y')) + offset_y))
                    continue
                if group.get('name') == 'SpawnPoints' and obj.get('type') == 'Spider' and obj.find('polyline') is not None:
                    for key, left, top, w, h in spider_parts(root, float(obj.get('x')), float(obj.get('y')) + offset_y,
                                                             obj.find('polyline').get('points', '')):
                        yield name, key, left, top, w, h
                    continue
                if group.get('name') == 'SpawnPoints' and obj.get('type') == 'Snail':
                    for key, left, top, w, h in snail_parts(root, float(obj.get('x')), float(obj.get('y')) + offset_y,
                                                            float(obj.get('width', TILE)), float(obj.get('height', TILE)), props):
                        yield name, key, left, top, w, h
                    continue
                if group.get('name') == 'SpawnPoints' and obj.get('type') == 'Deadfall':
                    for key, left, top, w, h in deadfall_parts(float(obj.get('x')), float(obj.get('y')) + offset_y,
                                                               props.get('swing') == 'right'):
                        yield name, key, left, top, w, h
                    continue
                key, kind = key_of(group.get('name'), obj, props)
                if key is None:
                    if kind not in NO_SPRITE and kind != 'Platform':
                        errors.append(f'{name}: object type {kind!r} is not classified in tools/check_contrast.py '
                                      f'(add it to SPRITES with its tier, or to NO_SPRITE saying why it draws nothing)')
                    continue
                if key not in SPRITES:
                    errors.append(f'{name}: sprite {key!r} is not classified in tools/check_contrast.py')
                    continue
                x, w = float(obj.get('x')), float(obj.get('width', TILE))
                if kind == 'Bramble':  # measure the clump where the game draws it, at the root end of the box
                    x, w = (x + w - BRAMBLE_CLUMP if key in MIRRORED else x), BRAMBLE_CLUMP
                yield (name, key, x, float(obj.get('y')) + offset_y, w, float(obj.get('height', TILE)))


def floor_camera_top(root, x, y):
    """Level.floorCameraTop for the room holding (x, y): a room taller than the view shows its bottom, hiding its top rows."""
    _, ry, _, rh = room_of(root, x, y)
    return ry + (rh - VIEW_HEIGHT) / 2 if rh <= VIEW_HEIGHT else ry + rh - VIEW_HEIGHT


def lip_top(root, x, y):
    """Where FallingTrapLayout puts a stone's lip: its `lipTop` property (absolute world px), else its top lowered on screen."""
    for group in root.findall('objectgroup'):
        for obj in group.findall('object'):
            if obj.get('type') == 'FallingStone' and float(obj.get('x')) == x:
                for prop in obj.findall('properties/property'):
                    if prop.get('name') == 'lipTop':
                        return float(prop.get('value'))
    return max(y, floor_camera_top(root, x, y))


def at(row):
    """Where a result row was measured: map, x, and the steady camera x when scenery moved behind it."""
    camera = row[ROW_CAMERA] if len(row) > ROW_CAMERA and row[ROW_CAMERA] is not None else None
    return f'{row[0]} x {row[1]:.0f}' + (f' camera {camera}' if camera is not None else '')


def check_sprite(key, results):
    """Errors for one sprite key from its measured (map, x, outline, body) results."""
    tier = SPRITES[key][0]
    worst_low = min(results, key=lambda r: r[2])
    worst_high = max(results, key=lambda r: r[2])
    errors = []
    if tier == 'active' and worst_low[2] < ACTIVE_OUTLINE_MIN:
        errors.append(f'{key} (active): outline {worst_low[ROW_OUTLINE]:.3f} < {ACTIVE_OUTLINE_MIN} at {at(worst_low)}')
    if tier == 'decor':
        if worst_high[2] > DECOR_OUTLINE_MAX:
            errors.append(f'{key} (decor): outline {worst_high[ROW_OUTLINE]:.3f} > {DECOR_OUTLINE_MAX} at {at(worst_high)}')
        bodies = [r[3] for r in results]
        if max(bodies) > DECOR_BODY_MAX or min(bodies) < DECOR_BODY_MIN:
            errors.append(f'{key} (decor): body {min(bodies):.3f}..{max(bodies):.3f} outside {DECOR_BODY_MIN}..{DECOR_BODY_MAX}')
        for n, frame in enumerate(frames(key)):
            chroma = chroma_p90(frame)
            if chroma > DECOR_CHROMA_MAX:
                errors.append(f'{key} (decor) frame {n}: chroma {chroma:.3f} > {DECOR_CHROMA_MAX}')
    return errors


def nine_slice(glow, w, h, border):
    """The glow sheet drawn as FairyGuide.nineSlice does: corners fixed, edges and middle stretched (nearest)."""
    out = Image.new('RGBA', (w, h))
    s = glow.width
    spans = lambda size: ((0, border, 0, border), (border, s - border, border, size - border), (s - border, s, size - border, size))
    for sx0, sx1, dx0, dx1 in spans(w):
        for sy0, sy1, dy0, dy1 in spans(h):
            if dx1 > dx0 and dy1 > dy0:
                out.paste(glow.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST), (dx0, dy0))
    return out


def edge_pairs(sprite, light, lx, ly, alpha):
    """(sprite L, dx, dy, added RGB) for every outline pair of `sprite`; the light's top-left sits at (lx, ly) in sprite space."""
    pairs = []
    for j in range(sprite.height):
        for i in range(sprite.width):
            if not is_opaque(sprite, i, j):
                continue
            lightness = oklab(sprite.getpixel((i, j)))[0]
            for u, v in NEIGHBOURS:
                if is_opaque(sprite, i + u, j + v):
                    continue
                gx, gy = i + u - lx, j + v - ly
                added = (0, 0, 0)
                if light and 0 <= gx < light.width and 0 <= gy < light.height and light.getpixel((gx, gy))[ALPHA]:
                    added = tuple(int(c * alpha) for c in light.getpixel((gx, gy))[:RGB])
                pairs.append((lightness, i + u, j + v, added))
    return pairs


def swept_outline(pairs, background, step_x, y_from, y_to, step_y, size, xs=None):
    """Worst mean outline of the pairs over a grid of positions in `background` (x from `xs`, else every step_x): (outline, x, y)."""
    pixels, worst = background.load(), (1.0, 0, 0)
    for x in (range(0, background.width - size, step_x) if xs is None else xs):
        for y in range(y_from, min(y_to, background.height - size), step_y):
            total = 0.0
            for lightness, dx, dy, added in pairs:
                lit = tuple(min(CHANNEL_MAX, c + a) for c, a in zip(pixels[x + dx, y + dy][:RGB], added))
                total += abs(lightness - oklab(lit)[0])
            outline = total / len(pairs)
            if outline < worst[0]:
                worst = (outline, x, y)
    return worst


def fairy_cases():
    """(name, sprite, light, light x, light y, alpha) for every fairy body frame and both glints."""
    body = Image.open(ROOT / 'assets/images/objects/fairy-body.png').convert('RGBA')
    glow = Image.open(ROOT / 'assets/images/objects/fairy-glow.png').convert('RGBA')
    glint = Image.open(ROOT / 'assets/images/objects/fairy-glint.png').convert('RGBA')
    light = nine_slice(glow, FAIRY_LIGHT_SIZE, FAIRY_LIGHT_SIZE, FAIRY_GLOW_BORDER)
    offset = (FAIRY_FRAME - FAIRY_LIGHT_SIZE) // 2
    cases = [(f'Fairy frame {n}', body.crop((n * FAIRY_FRAME, 0, (n + 1) * FAIRY_FRAME, FAIRY_FRAME)), light, offset, offset,
              FAIRY_LIGHT_ALPHA) for n in range(FAIRY_BODY_FRAMES)]
    pad = int(FAIRY_GLOW_PAD)
    target_glow = nine_slice(glow, FAIRY_TARGET + 2 * pad, FAIRY_TARGET + 2 * pad, FAIRY_GLOW_BORDER)
    # The top-left glint: centred on the target's corner grown by GLINT_GROW; the glow box starts GLOW_PAD out.
    gx = gy = round(-FAIRY_GLINT_GROW - FAIRY_GLINT_SIZE / 2) + pad
    for n, name in enumerate(('big', 'small')):
        sprite = glint.crop((n * FAIRY_GLINT_SIZE, 0, (n + 1) * FAIRY_GLINT_SIZE, FAIRY_GLINT_SIZE))
        cases.append((f'Fairy glint {name}', sprite, target_glow, -gx, -gy, 1.0))
    return cases


def swept_scene(pairs, scene, step_x, y_from, y_to, step_y, size):
    """swept_outline over a Scene: at every sweep x, every steady camera at which x is on screen (SWEEP_CAMERA_STEP apart) and
    every drift phase; the strip of background around x is composited as the game draws it. (outline, x, y, camera)."""
    worst = (1.0, 0, 0, None)
    for x in range(0, scene.size[0] - size, step_x):
        key = (x, size)
        if key not in scene.strips:
            box = (x - 1, 0, x + size + 1, scene.size[1])
            scene.strips[key] = [(cam, ox, image) for cam, ox, _, image in scene.windows(box, x, y_from, size, SWEEP_CAMERA_STEP)]
        for cam, origin, image in scene.strips[key]:
            outline, _, y = swept_outline(pairs, image, step_x, y_from, y_to, step_y, size, xs=(x - origin,))
            if outline < worst[0]:
                worst = (outline, x, y, cam)
    return worst


def check_fairy(images):
    """Errors for the fairy and her glints (active tier), and the worst outline of each case for the report. `images` maps a map
    name to a Scene (the game's composite) or to one flat image."""
    errors, report = [], {}
    step_x, y_from, y_to, step_y = FAIRY_SWEEP
    for name, sprite, light, lx, ly, alpha in fairy_cases():
        pairs = edge_pairs(sprite, light, lx, ly, alpha)
        worst = (1.0, None, 0, 0)
        for map_name, background in images.items():
            if isinstance(background, Scene):
                outline, x, y, _ = swept_scene(pairs, background, step_x, y_from, y_to, step_y, sprite.width)
            else:
                outline, x, y = swept_outline(pairs, background, step_x, y_from, y_to, step_y, sprite.width)
            if outline < worst[0]:
                worst = (outline, map_name, x, y)
        outline, place, x, y = worst
        report[name] = outline
        if outline < ACTIVE_OUTLINE_MIN:
            errors.append(f'{name} (active): outline {outline:.3f} < {ACTIVE_OUTLINE_MIN} at {place} x {x} y {y}')
    return errors, report


def tile_report(roots):
    """Report only (#189): mean outline and body of the tile scenery against each map's background colour. The categories are tile
    ids of the props in the play sheet (TILESET), so only the maps that draw that sheet are reported; the others are returned in `skipped`
    with the sheet they draw."""
    sums = {name: [[], []] for name in TILE_SCENERY}
    skipped = {}
    for name, root in roots.items():
        sheet, columns = play_tileset(root)
        if sheet != (ROOT / TILESET).resolve():
            skipped[name] = sheet.name
            continue
        layer, gids, width = tile_layer(root, name)
        background = composite(Image.new('RGBA', layer.size), map_background(root, name))
        for index, gid in enumerate(gids):
            category = next((c for c, ((r0, r1), (c0, c1)) in TILE_SCENERY.items()
                             if gid and r0 <= (gid - 1) // columns <= r1 and c0 <= (gid - 1) % columns <= c1), None)
            if category is None:
                continue
            x, y = (index % width) * TILE, (index // width) * TILE
            if y + TILE > VIEW_HEIGHT:
                continue
            tile = layer.crop((x - 1, y - 1, x + TILE + 1, y + TILE + 1))
            pad = background.crop((x - 1, y - 1, x + TILE + 1, y + TILE + 1))
            outline, body = measure(tile, pad, 0, 0)
            if outline is not None:
                sums[category][0].append(outline)
            if body is not None:
                sums[category][1].append(body)
    return {c: (sum(o) / len(o), sum(b) / len(b)) for c, (o, b) in sums.items() if o}, skipped


# D101, D108: monster bodies have no map placement of their own. A body lies only where its monster can be killed, dropped
# straight down to the first floor under it (velocity.x = 0 in Beetle/Spider/Snail.fall; one that falls out of the room is
# gone), so each body is measured at those spots, derived from the monster's placements in the shipped maps (a new placement
# is covered with no edit here):
# - beetle and snail: every position.x of the authored range, on the range's floor (the load checks keep the whole range on
#   floor without gaps; the beetle's notice hop and the body's flip hop land on the same floor);
# - wolf (#331, D115): every position.x of its range, widened by Wolf.LUNGE_REACH while authored floor stays under the whole body at
#   its floor line (the body of a wolf killed in its lunge slides on to the line's end, over floor only);
# - spider: every position.x of each floor leg, widened by Spider.POUNCE_REACH while authored floor stays under the whole
#   body at that floor line (#497: the leap never leaves that floor); every wall pose and every thread pose, set on the open
#   side as Spider.die does and dropped to the floor below.
# Floor tops elsewhere (other rooms, ledges no monster of that kind reaches) carry nothing for this gate: no body can lie there.
BODY_SWEEP = ('Beetle:body', 'Spider:body', 'Snail:body', 'Wolf:body')
BODY_STEP = 8  # px between measured body positions along a range or a route
LANDING_TYPES = (None, 'Wall', 'Platform')  # every collision box a falling body settles on (not quicksand)
SPIDER_POUNCE_REACH = java_number(SPIDER_JAVA, 'POUNCE_REACH')
SPIDER_FLOOR_PROBE = java_number(SPIDER_JAVA, 'FLOOR_PROBE')
SPIDER_REACH_STEP = java_number(SPIDER_JAVA, 'REACH_STEP')  # the leap's reach is tested in these steps
SPIDER_WALL_BODY_X = java_number(SPIDER_JAVA, 'WALL_BODY_X')  # the turned body in the wall frame
SPIDER_WALL_BODY_WIDTH = java_number(SPIDER_JAVA, 'WALL_BODY_WIDTH')
HALF = 0.5
SPIDER_START_LIP = 1  # a thread's lip: the route's second vertex, or its last but one (spider_route)
SPIDER_END_LIP = -2
SPIDER_BODY_FLOOR = java_number(SPIDER_JAVA, 'BODY_Y') + java_number(SPIDER_JAVA, 'BODY_HEIGHT')


def landings(root):
    """The collision boxes a falling body settles on, in world px."""
    out = []
    for group in root.findall('objectgroup'):
        if group.get('name') != 'Collisions':
            continue
        offset_y = float(group.get('offsety', '0'))
        for obj in group.findall('object'):
            if obj.get('type') in LANDING_TYPES:
                out.append((float(obj.get('x')), float(obj.get('y')) + offset_y, float(obj.get('width', TILE)),
                            float(obj.get('height', TILE))))
    return out


DYNAMIC_TYPES = ('FallingPlatform', 'Escalator', 'Deadfall', 'QuickSand')  # solids that move, fall or swallow: landings() is static


def dynamic_blocks(root):
    """(type, x, y, width, height) of every object whose collision changes in play, in world px (any object group)."""
    out = []
    for group in root.findall('objectgroup'):
        offset_y = float(group.get('offsety', '0'))
        out += [(o.get('type'), float(o.get('x')), float(o.get('y')) + offset_y, float(o.get('width', TILE)), float(o.get('height', TILE)))
                for o in group.findall('object') if o.get('type') in DYNAMIC_TYPES]
    return out


def check_static_column(blocks, name, kind, left, right, top, line):
    """A body spot is derived from static Collisions only: fail when a dynamic block sits in the column it falls through (top to
    `line`, unbounded below when nothing catches it) or at its floor line."""
    for block, x, y, w, h in blocks:
        if x < right and left < x + w and y + h > top - 1 and (line is None or y <= line + 1):
            raise SystemExit(f'check_contrast: {name}: a {kind} body at x {left:g} (column {left:g}..{right:g}, y {top:g} to '
                             f'{"open" if line is None else format(line, "g")}) crosses a {block} at ({x:g}, {y:g}); '
                             'body_spots reads static Collisions only, so model the block before the gate can vouch for this spot')


def drop(boxes, body_left, body_right, bottom):
    """The floor line a body with this horizontal extent settles on, falling from `bottom`; None when nothing catches it."""
    tops = [y for x, y, w, _ in boxes if x < body_right and body_left < x + w and y >= bottom - 1]
    return min(tops) if tops else None


def floor_under(boxes, left, right, line):
    """True when authored floor at `line` lies under every SPIDER_FLOOR_PROBE px of left..right (Spider.floorAt)."""
    column = left
    while column <= right + SPIDER_FLOOR_PROBE - 1:
        probe = min(column, right - 1)
        if not any(x <= probe < x + w and abs(y - line) < 1 for x, y, w, _ in boxes):
            return False
        column += SPIDER_FLOOR_PROBE
    return True


def _steps(lo, hi):
    lefts = list(range(int(lo), int(hi) + 1, BODY_STEP))
    if not lefts or lefts[-1] != int(hi):
        lefts.append(int(hi))
    return lefts


def spider_body_spots(root, boxes, x, y, props, polyline, blocks=(), name='map'):
    """(position.x, floor line) of every place a spider's body can lie."""
    if polyline is None:  # a range spider: one floor leg
        legs = [{'kind': 'floor', 'lo': x - float(props.get('offNeg', 0)) * TILE, 'hi': x + float(props.get('offPos', 0)) * TILE,
                 'line': y + SPIDER_BODY_FLOOR}]  # Spider.Route.floor: the line under the authored body
        threads = []
    else:
        route = spider_route(root, x, y, polyline)
        legs, threads = route['legs'], route['threads']
        points = [x + float(pair.split(',')[0]) for pair in polyline.split()]
        lips = (points[SPIDER_START_LIP], points[SPIDER_END_LIP])
    spots = set()
    for leg in legs:
        if leg['kind'] == 'floor':
            line = leg['line']
            # Level.spiderFloorAt counts active falling platforms, escalators and deadfalls as floor; the widening reads static
            # floor only, so fail when such a block continues the floor anywhere in the pounce band.
            band = [b for b in blocks if b[0] != 'QuickSand']
            check_static_column(band, name, 'Spider', leg['lo'] - SPIDER_POUNCE_REACH,
                                leg['hi'] + SPIDER_POUNCE_REACH + SPIDER_BODY_X + SPIDER_BODY_WIDTH, line, line)
            fits = lambda left: floor_under(boxes, left + SPIDER_BODY_X, left + SPIDER_BODY_X + SPIDER_BODY_WIDTH, line)
            lo, hi = leg['lo'], leg['hi']
            while lo - SPIDER_REACH_STEP >= leg['lo'] - SPIDER_POUNCE_REACH and fits(lo - SPIDER_REACH_STEP):
                lo -= SPIDER_REACH_STEP
            while hi + SPIDER_REACH_STEP <= leg['hi'] + SPIDER_POUNCE_REACH and fits(hi + SPIDER_REACH_STEP):
                hi += SPIDER_REACH_STEP
            spots.update((left, line) for left in _steps(lo, hi))
            continue
        ww, _ = SPIDER_WALL
        right = leg['solid_right']
        cell_x = leg['line'] - ww if right else leg['line']
        centre = cell_x + (SPIDER_WALL_BODY_X + SPIDER_WALL_BODY_WIDTH * HALF if right else ww - SPIDER_WALL_BODY_X - SPIDER_WALL_BODY_WIDTH * HALF)
        left = centre - SPIDER_BODY_X - SPIDER_BODY_WIDTH * HALF
        left = min(left, leg['line'] - SPIDER_BODY_X - SPIDER_BODY_WIDTH) if right else max(left, leg['line'] - SPIDER_BODY_X)
        for cell_top in _steps(leg['lo'], leg['hi']):
            spots.add((left, cell_top))
    for thread in threads:
        centre = thread['thread_x'] - SPIDER_HANG_THREAD_X + SPIDER_HANG_BODY_X + SPIDER_HANG_BODY_WIDTH * HALF
        left = centre - SPIDER_BODY_X - SPIDER_BODY_WIDTH * HALF
        # Spider.die: the body is kept off the ledge, on the open side of its lip (the route's first or last vertex but one).
        lip = lips[thread['side']]
        left = max(left, lip - SPIDER_BODY_X) if thread['thread_x'] > lip else min(left, lip - SPIDER_BODY_X - SPIDER_BODY_WIDTH)
        for top in _steps(thread['anchor_y'], thread['end_y']):
            spots.add((left, top))
    out = set()
    for left, top in spots:
        line = drop(boxes, left + SPIDER_BODY_X, left + SPIDER_BODY_X + SPIDER_BODY_WIDTH, top)
        check_static_column(blocks, name, 'Spider', left + SPIDER_BODY_X, left + SPIDER_BODY_X + SPIDER_BODY_WIDTH, top, line)
        if line is not None:
            out.add((left, line))
    return out


WOLF_JAVA = 'core/src/main/java/com/jlogicsoftware/kimeria/entity/enemy/Wolf.java'
WOLF_BODY_X = java_number(WOLF_JAVA, 'BODY_X')
WOLF_BODY_WIDTH = java_number(WOLF_JAVA, 'BODY_WIDTH')
WOLF_FRAME_HEIGHT = java_number(WOLF_JAVA, 'FRAME_HEIGHT')  # the floor line is the frame's bottom: Wolf.problem's y + FRAME_HEIGHT
WOLF_LUNGE_REACH = java_number(WOLF_JAVA, 'LUNGE_REACH')
WOLF_REACH_STEP = java_number(WOLF_JAVA, 'REACH_STEP')


def wolf_body_spots(boxes, x, y, props, blocks=(), name='map'):
    """(position.x, floor line) of every place a wolf's body can lie: its range, widened by the lunge's reach over floor."""
    low, high = x - float(props.get('offNeg', 0)) * TILE, x + float(props.get('offPos', 0)) * TILE
    line = y + WOLF_FRAME_HEIGHT
    band = [b for b in blocks if b[0] != 'QuickSand']
    check_static_column(band, name, 'Wolf', low - WOLF_LUNGE_REACH, high + WOLF_LUNGE_REACH + WOLF_BODY_X + WOLF_BODY_WIDTH, line, line)
    fits = lambda left: floor_under(boxes, left + WOLF_BODY_X, left + WOLF_BODY_X + WOLF_BODY_WIDTH, line)
    lo, hi = low, high
    while lo - WOLF_REACH_STEP >= low - WOLF_LUNGE_REACH and fits(lo - WOLF_REACH_STEP):
        lo -= WOLF_REACH_STEP
    while hi + WOLF_REACH_STEP <= high + WOLF_LUNGE_REACH and fits(hi + WOLF_REACH_STEP):
        hi += WOLF_REACH_STEP
    return {(left, line) for left in _steps(lo, hi)}


def placed_body_placements(roots):
    """(BODY_SWEEP key, map, object index) of every monster placed in `roots`: each must yield a measured body."""
    return {(obj.get('type') + ':body', name, index) for name, root in roots.items() for group in root.findall('objectgroup')
            if group.get('name') == 'SpawnPoints' for index, obj in enumerate(group.findall('object')) if obj.get('type') + ':body' in BODY_SWEEP}


def body_spots(roots):
    """(key, map, position.x, floor line) of every place a monster body can lie in the shipped maps (see above)."""
    for key, name, _, left, line in body_spot_placements(roots):
        yield key, name, left, line


def body_spot_placements(roots):
    """(key, map, object index, position.x, floor line): body_spots with the placement each spot belongs to."""
    for name, root in roots.items():
        boxes, blocks = landings(root), dynamic_blocks(root)
        for group in root.findall('objectgroup'):
            if group.get('name') != 'SpawnPoints':
                continue
            offset_y = float(group.get('offsety', '0'))
            for index, obj in enumerate(group.findall('object')):
                kind = obj.get('type')
                if kind not in ('Beetle', 'Spider', 'Snail', 'Wolf'):
                    continue
                props = {p.get('name'): p.get('value') for p in obj.findall('properties/property')}
                x, y = float(obj.get('x')), float(obj.get('y')) + offset_y
                h = float(obj.get('height', TILE))
                if kind == 'Spider':
                    polyline = obj.find('polyline')
                    spots = spider_body_spots(root, boxes, x, y, props, None if polyline is None else polyline.get('points', ''), blocks, name)
                elif kind == 'Wolf':
                    spots = wolf_body_spots(boxes, x, y, props, blocks, name)
                else:
                    low, high = x - float(props.get('offNeg', 0)) * TILE, x + float(props.get('offPos', 0)) * TILE
                    spots = {(left, y + h) for left in _steps(low, high)}
                    for left, line in spots:
                        check_static_column(blocks, name, kind, left, left + float(obj.get('width', TILE)), line, line)
                for left, line in sorted(spots):
                    yield kind + ':body', name, index, left, line


def body_sweep(roots, scenes):
    """(key, map, x, outline, body, camera, placement) for every monster body at every spot of body_spots, over every steady
    camera (SWEEP_CAMERA_STEP apart) at which it is on screen."""
    for key, name, index, left, line in body_spot_placements(roots):
        for frame in frames(key):
            left_px, at_top = int(left), int(line) - frame.height
            box = (left_px - 1, at_top - 1, left_px + frame.width + 1, at_top + frame.height + 1)
            for cam, ox, oy, image in scenes[name].windows(box, left_px, line, frame.width, SWEEP_CAMERA_STEP):
                outline, body = measure(frame, image, left_px - ox, at_top - oy)
                if outline is not None:
                    yield key, name, left_px, outline, body, cam, index


def sprite_spots(key, x, y, w, h, root):
    """[(frame, left, top)] of every frame of `key` for the placement (x, y, w, h), where the game draws it."""
    anchor = SPRITES[key][SPRITE_ANCHOR]
    spots = []
    for frame in frames(key):
        left = int(x + w / 2 - frame.width / 2)
        top = int(y) if anchor == 'top' else int(lip_top(root, x, y)) if anchor == 'view' else int(y + h - frame.height)
        spots.append((frame, left, top))
    return spots


def measure_placement(scene, root, key, x, y, w, h, halo):
    """[(outline, body, camera)] of every frame, glow level and halo state of one placement over every background the game draws
    behind it (every steady camera at which it is on screen, every drift phase)."""
    spots, glows = sprite_spots(key, x, y, w, h, root), SPRITES[key][SPRITE_GLOWS]
    collectable = key.startswith('Collectable:')
    rects = [(left, top, left + frame.width, top + frame.height) for frame, left, top in spots]
    reach = halo.width // 2 + HALO_MARGIN
    cx, cy = int(x + w / 2), int(y + h / 2)
    if collectable:   # the halo's box at rest and at the bob's top, as the game draws it behind the bobbing item
        rects += [(cx - reach, cy - lift - reach, cx + reach, cy - lift + reach) for lift, _ in HALO_STATES]
    lefts, tops, rights, bottoms = zip(*rects)
    box = (min(lefts) - 1, min(tops) - 1, max(rights) + 1, max(bottoms) + 1)
    rows = []
    for cam, ox, oy, window in scene.windows(box, x, y, w):
        for frame, left, top in spots:
            for glow in glows:
                if not collectable:
                    rows.append((*measure(frame, window, left - ox, top - oy, glow), cam))
                    continue
                for lift, alpha in HALO_STATES:
                    area = (max(0, cx - reach), max(0, cy - lift - reach), min(scene.size[0], cx + reach), min(scene.size[1], cy - lift + reach))
                    area_left, area_top, area_right, area_bottom = area
                    local = window.crop((area_left - ox, area_top - oy, area_right - ox, area_bottom - oy))   # never past the map
                    if key not in NO_HALO:
                        local = with_halo(local, halo, cx - area_left, cy - lift - area_top, alpha)
                    rows.append((*measure(frame, local, left - area_left, top - lift - area_top, glow), cam))
    return rows


def run():
    errors = []
    roots = map_roots()
    scenes = {name: Scene(name, root) for name, root in roots.items()}
    results = {}
    halo = Image.open(ROOT / 'assets/images' / HALO_SHEET).convert('RGBA')
    for name, key, x, y, w, h in placements(errors, roots):
        for outline, body, cam in measure_placement(scenes[name], roots[name], key, x, y, w, h, halo):
            results.setdefault(key, []).append((name, x, outline, body, cam))
    measured = set()
    for key, name, x, outline, body, cam, index in body_sweep(roots, scenes):
        results.setdefault(key, []).append((name, x, outline, body, cam))
        measured.add((key, name, index))
    for key in SPRITES:
        if key in results:
            errors.extend(check_sprite(key, results[key]))
    for key, name, index in sorted(placed_body_placements(roots) - measured):  # a placed monster never measured is a gap, not a pass
        errors.append(f'{key}: no body was measured for the monster placed in {name} (object {index}; no spot, or never on screen)')
    fairy_errors, fairy = check_fairy(scenes)
    errors.extend(fairy_errors)
    for name, outline in fairy.items():
        results[name] = [('sweep', 0, outline, 0.0, None)]
    return results, errors, tile_report(roots)


def main():
    results, errors, (tiles, skipped) = run()
    print(f'{"sprite":24} {"tier":7} {"outline min":>11} {"outline max":>11} {"body max":>8}')
    for key, rows in results.items():
        tier = SPRITES[key][0] if key in SPRITES else 'active'  # the fairy rows (#338) are active
        print(f'{key:24} {tier:7} {min(r[ROW_OUTLINE] for r in rows):11.3f} {max(r[ROW_OUTLINE] for r in rows):11.3f} '
              f'{max(r[ROW_BODY] for r in rows):8.3f}')
    print('tile scenery, report only (#189, background tier ceiling = decor ceiling):')
    for category, (outline, body) in tiles.items():
        print(f'  {category:16} outline {outline:.3f} body {body:.3f}')
    for name, sheet in skipped.items():
        print(f'  {name}: skipped, it draws {sheet}, not the sheet the categories index')
    if errors:
        print('Contrast tiers (D76) failed:\n  ' + '\n  '.join(errors))
        return 1
    print('Contrast tiers (D76) passed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
