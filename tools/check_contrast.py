#!/usr/bin/env python3
"""Contrast tiers gate (D76, #344, playtest F15): active sprites must be loud, decor must be quiet.

Every sprite placed in the shipped maps is drawn, in its rest frame, over what the game draws behind it: the map's
flat `background` colour (D92: the map property every shipped map must set, the clear colour behind the world) plus
the map's `Background` tile layer. Metrics (OKLab):

- outline: mean |L(silhouette pixel) - L(background pixel just outside it)| over every pair of an opaque pixel and a
  transparent 4-neighbour. Figure against ground at the edge; independent of scale.
- body: mean OKLab distance between each opaque pixel and the background under it.
- chroma: 90th percentile of the sprite's OKLab chroma.

Rules (the worst placement counts; glowing sprites at full and dimmest glow; the bush, the lever and the stump and root idle loops in every frame;
collectables with their pickup halo (#345), at rest with the dimmest halo and at the top of the bob with the brightest; the coin has no
halo, NO_HALO):
- active (hero, traps, enemies, collectables, mechanisms): outline >= ACTIVE_OUTLINE_MIN;
- decor (`Decor` kinds, `HangingVine`): outline <= DECOR_OUTLINE_MAX, DECOR_BODY_MIN <= body <= DECOR_BODY_MAX,
  chroma <= DECOR_CHROMA_MAX, and no ink pixel (L < INK_L_MAX).
Every object type in SpawnPoints and Collisions must be classified here as a sprite or as drawing nothing of its own;
an unknown type fails, so a new sprite cannot skip the check. Tile-layer scenery is reported, not gated: it is the
third-party tileset that #189 replaces (D76).
"""
import math
import re
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
INK_L_MAX = 0.30
CHROMA_PERCENTILE = 0.9
ALPHA_MIN = 128
VIEW_HEIGHT = 360  # KimeriaGame.LOGICAL_H
TILE = 16
TILESET_COLUMNS = 20
BACKGROUND_PROPERTY = 'background'  # D92: a Tiled colour, #RRGGBB or #AARRGGBB with alpha ff (Level.parseBackground)
HEX_RGB = 6  # digits of #RRGGBB
HEX_ARGB = 8  # digits of #AARRGGBB
HEX_PAIR = 2  # digits per channel
HEX_BASE = 16
OPAQUE_HEX = 'ff'
TILESET = 'assets/images/Tileset/Tileset.png'
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
    'Bat': ('active', 'enemy/Bat.png', (0, 0, 16, 16), 1, 'top', FULL),
    'RedMob': ('active', 'enemy/Mobs.png', (0, 160, 48, 32), 1, 'floor', FULL),
    'YellowMob': ('active', 'enemy/yellow_mob.png', (0, 160, 48, 32), 1, 'floor', FULL),
    'Boss': ('active', 'enemy/Boss.png', (0, 224, 48, 32), 1, 'floor', FULL),  # THRONE, its rest state
    'Player': ('active', 'hero/hero-right.png', (0, 0, 64, 64), 4, 'floor', FULL),  # idle facing right (#343, D79)
    # #363 (D88): the deadfall draws three parts at rest, each placed by deadfall_parts as the game draws it: the bough on the
    # object, the tied log hauled up on the far side, the taut trip vine on the floor. `-right` keys are the mirrored copies.
    'Deadfall': ('active', 'objects/trap-deadfall-bough.png', (0, 0, 112, 24), 1, 'top', FULL),
    'Deadfall:log': ('active', 'objects/trap-deadfall.png', (0, 0, 32, 16), 1, 'top', FULL),
    'Deadfall:vine': ('active', 'objects/trap-deadfall-vine.png', (0, 0, 24, 8), 1, 'top', FULL),
}
MIRRORED = {f'{key}-right': key for key in ('Deadfall', 'Deadfall:log', 'Deadfall:vine', 'Bramble')}
SPRITES.update({mirrored: SPRITES[key] for mirrored, key in MIRRORED.items()})
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
TILE_SCENERY = {'sign': ((0, 2), (9, 10)), 'rock / rubble': ((0, 2), (12, 16)),
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


def chroma_and_ink(sprite, glow=1.0):
    chroma, darkest = [], 1.0
    for j in range(sprite.height):
        for i in range(sprite.width):
            if is_opaque(sprite, i, j):
                lightness, a, b = oklab(sprite.getpixel((i, j)), glow)
                chroma.append(math.hypot(a, b))
                darkest = min(darkest, lightness)
    chroma.sort()
    return chroma[max(0, int(CHROMA_PERCENTILE * len(chroma)) - 1)], darkest


# More frames measured under the same key: the hero faces either way at every placement (#343, D79).
EXTRA_FRAMES = {'Player': (('hero/hero-left.png', (0, 0, 64, 64), 4),  # idle facing left
                          ('hero/hero-right.png', (0, 640, 64, 64), 4), ('hero/hero-left.png', (0, 640, 64, 64), 4))}  # carry idle (#343 part 2)


BRAMBLE_CLUMP = 16  # the clump frame's width: it is drawn at the root end of the object, not centred on it


def frames(key):
    _, sheet, box, count, _, _ = SPRITES[key]
    result = []
    for sheet, (x, y, w, h), count in ((sheet, box, count), *EXTRA_FRAMES.get(key, ())):
        image = Image.open(ROOT / 'assets/images' / sheet).convert('RGBA')
        result += [image.crop((x + n * w, y, x + n * w + w, y + h)) for n in range(count)]
    if key in MIRRORED:
        result = [frame.transpose(Image.Transpose.FLIP_LEFT_RIGHT) for frame in result]
    return result


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


def tile_layer(root):
    width, height = int(root.get('width')), int(root.get('height'))
    gids = [int(v) for v in root.find('layer/data').text.replace('\n', '').split(',') if v.strip()]
    tileset = Image.open(ROOT / TILESET).convert('RGBA')
    layer = Image.new('RGBA', (width * TILE, height * TILE))
    for index, gid in enumerate(gids):
        if gid:
            column, row = (gid - 1) % TILESET_COLUMNS, (gid - 1) // TILESET_COLUMNS
            layer.paste(tileset.crop((column * TILE, row * TILE, column * TILE + TILE, row * TILE + TILE)),
                        ((index % width) * TILE, (index // width) * TILE))
    return layer, gids, width


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
    """What the game draws behind every sprite: the flat background colour, then the tile layer."""
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
    for group in root.findall('objectgroup'):
        for obj in group.findall('object'):
            if obj.get('type') != 'Room':
                continue
            rx, ry, rw, rh = (float(obj.get(k)) for k in ('x', 'y', 'width', 'height'))
            if rx <= x < rx + rw and ry <= y < ry + rh:
                return ry + (rh - VIEW_HEIGHT) / 2 if rh <= VIEW_HEIGHT else ry + rh - VIEW_HEIGHT
    raise ValueError(f'no Room holds ({x}, {y})')


def lip_top(root, x, y):
    """Where FallingTrapLayout puts a stone's lip: its `lipTop` property (absolute world px), else its top lowered on screen."""
    for group in root.findall('objectgroup'):
        for obj in group.findall('object'):
            if obj.get('type') == 'FallingStone' and float(obj.get('x')) == x:
                for prop in obj.findall('properties/property'):
                    if prop.get('name') == 'lipTop':
                        return float(prop.get('value'))
    return max(y, floor_camera_top(root, x, y))


def check_sprite(key, results):
    """Errors for one sprite key from its measured (map, x, outline, body) results."""
    tier = SPRITES[key][0]
    worst_low = min(results, key=lambda r: r[2])
    worst_high = max(results, key=lambda r: r[2])
    errors = []
    if tier == 'active' and worst_low[2] < ACTIVE_OUTLINE_MIN:
        errors.append(f'{key} (active): outline {worst_low[2]:.3f} < {ACTIVE_OUTLINE_MIN} at {worst_low[0]} x {worst_low[1]:.0f}')
    if tier == 'decor':
        if worst_high[2] > DECOR_OUTLINE_MAX:
            errors.append(f'{key} (decor): outline {worst_high[2]:.3f} > {DECOR_OUTLINE_MAX} at {worst_high[0]} x {worst_high[1]:.0f}')
        bodies = [r[3] for r in results]
        if max(bodies) > DECOR_BODY_MAX or min(bodies) < DECOR_BODY_MIN:
            errors.append(f'{key} (decor): body {min(bodies):.3f}..{max(bodies):.3f} outside {DECOR_BODY_MIN}..{DECOR_BODY_MAX}')
        for n, frame in enumerate(frames(key)):
            chroma, darkest = chroma_and_ink(frame)
            if chroma > DECOR_CHROMA_MAX:
                errors.append(f'{key} (decor) frame {n}: chroma {chroma:.3f} > {DECOR_CHROMA_MAX}')
            if darkest < INK_L_MAX:
                errors.append(f'{key} (decor) frame {n}: ink pixel (OKLab L {darkest:.3f} < {INK_L_MAX}); '
                              f'a decor contour is a darker shade of its own hue')
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


def swept_outline(pairs, background, step_x, y_from, y_to, step_y, size):
    """Worst mean outline of the pairs over a grid of positions in `background`: (outline, x, y)."""
    pixels, worst = background.load(), (1.0, 0, 0)
    for x in range(0, background.width - size, step_x):
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


def check_fairy(images):
    """Errors for the fairy and her glints (active tier), and the worst outline of each case for the report."""
    errors, report = [], {}
    step_x, y_from, y_to, step_y = FAIRY_SWEEP
    for name, sprite, light, lx, ly, alpha in fairy_cases():
        pairs = edge_pairs(sprite, light, lx, ly, alpha)
        worst = (1.0, None, 0, 0)
        for map_name, background in images.items():
            outline, x, y = swept_outline(pairs, background, step_x, y_from, y_to, step_y, sprite.width)
            if outline < worst[0]:
                worst = (outline, map_name, x, y)
        outline, place, x, y = worst
        report[name] = outline
        if outline < ACTIVE_OUTLINE_MIN:
            errors.append(f'{name} (active): outline {outline:.3f} < {ACTIVE_OUTLINE_MIN} at {place} x {x} y {y}')
    return errors, report


def tile_report(roots):
    """Report only (#189): mean outline and body of the tile scenery against each map's background colour."""
    sums = {name: [[], []] for name in TILE_SCENERY}
    for name, root in roots.items():
        layer, gids, width = tile_layer(root)
        background = composite(Image.new('RGBA', layer.size), map_background(root, name))
        for index, gid in enumerate(gids):
            category = next((c for c, ((r0, r1), (c0, c1)) in TILE_SCENERY.items()
                             if gid and r0 <= (gid - 1) // TILESET_COLUMNS <= r1 and c0 <= (gid - 1) % TILESET_COLUMNS <= c1), None)
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
    return {c: (sum(o) / len(o), sum(b) / len(b)) for c, (o, b) in sums.items() if o}


def run():
    errors = []
    roots = map_roots()
    images = {name: composite(tile_layer(root)[0], map_background(root, name)) for name, root in roots.items()}
    results = {}
    halo = Image.open(ROOT / 'assets/images' / HALO_SHEET).convert('RGBA')
    for name, key, x, y, w, h in placements(errors, roots):
        anchor, glows = SPRITES[key][4], SPRITES[key][5]
        for frame in frames(key):
            left = int(x + w / 2 - frame.width / 2)
            top = int(y) if anchor == 'top' else int(lip_top(roots[name], x, y)) if anchor == 'view' else int(y + h - frame.height)
            for glow in glows:
                background = images[name]
                if not key.startswith('Collectable:'):
                    outline, body = measure(frame, background, left, top, glow)
                    results.setdefault(key, []).append((name, x, outline, body))
                    continue
                for lift, alpha in HALO_STATES:  # what the game draws: the halo behind the bobbing item
                    reach = halo.width // 2 + HALO_MARGIN
                    cx, cy = int(x + w / 2), int(y + h / 2) - lift
                    box = (max(0, cx - reach), max(0, cy - reach),
                           min(background.width, cx + reach), min(background.height, cy + reach))  # never past the map
                    local = background.crop(box)
                    if key not in NO_HALO:
                        local = with_halo(local, halo, cx - box[0], cy - box[1], alpha)
                    outline, body = measure(frame, local, left - box[0], top - lift - box[1], glow)
                    results.setdefault(key, []).append((name, x, outline, body))
    for key in SPRITES:
        if key in results:
            errors.extend(check_sprite(key, results[key]))
    fairy_errors, fairy = check_fairy(images)
    errors.extend(fairy_errors)
    for name, outline in fairy.items():
        results[name] = [('sweep', 0, outline, 0.0)]
    return results, errors, tile_report(roots)


def main():
    results, errors, tiles = run()
    print(f'{"sprite":24} {"tier":7} {"outline min":>11} {"outline max":>11} {"body max":>8}')
    for key, rows in results.items():
        tier = SPRITES[key][0] if key in SPRITES else 'active'  # the fairy rows (#338) are active
        print(f'{key:24} {tier:7} {min(r[ROW_OUTLINE] for r in rows):11.3f} {max(r[ROW_OUTLINE] for r in rows):11.3f} '
              f'{max(r[ROW_BODY] for r in rows):8.3f}')
    print('tile scenery, report only (#189, background tier ceiling = decor ceiling):')
    for category, (outline, body) in tiles.items():
        print(f'  {category:16} outline {outline:.3f} body {body:.3f}')
    if errors:
        print('Contrast tiers (D76) failed:\n  ' + '\n  '.join(errors))
        return 1
    print('Contrast tiers (D76) passed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
