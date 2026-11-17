#!/usr/bin/env python3
"""Contrast tiers gate (D76, #344, playtest F15): active sprites must be loud, decor must be quiet.

Every sprite placed in the shipped maps is drawn, in its rest frame, over what the game draws behind it: the
screen-fixed sky (`SkyBackground`: sky.png scaled to the 360 px view, tiled) at several horizontal offsets, because
the camera moves any cloud behind anything, plus the map's `Background` tile layer. Metrics (OKLab):

- outline: mean |L(silhouette pixel) - L(background pixel just outside it)| over every pair of an opaque pixel and a
  transparent 4-neighbour. Figure against ground at the edge; independent of scale.
- body: mean OKLab distance between each opaque pixel and the background under it.
- chroma: 90th percentile of the sprite's OKLab chroma.

Rules (the worst placement and sky offset counts; glowing sprites at full and dimmest glow; the bush, the lever and the stump and root idle loops in every frame;
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
VIEW_HEIGHT = 360  # KimeriaGame.LOGICAL_H: the sky is scaled to the view height
TILE = 16
TILESET_COLUMNS = 20
SKY_OFFSETS = 8  # horizontal sky positions tried per placement (the sky is screen-fixed, the camera moves)
SKY = 'assets/images/background/sky.png'
TILESET = 'assets/images/Tileset/Tileset.png'
NEIGHBOURS = ((1, 0), (-1, 0), (0, 1), (0, -1))


def java_float(path, name):
    text = (ROOT / path).read_text(encoding='utf-8')
    return float(re.search(rf'\b{name}\s*=\s*([0-9.]+)f', text).group(1))


def java_int(path, name):
    text = (ROOT / path).read_text(encoding='utf-8')
    return int(re.search(rf'\b{name}\s*=\s*([0-9]+)\s*;', text).group(1))


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
GLOWS = (1.0, GLOW_MIN)

# key -> (tier, sheet, frame (x, y, w, h), frame count to check, anchor, glow levels). Anchor 'floor': the frame's
# bottom on the object's bottom edge; 'top': the frame's top on the object's top edge (world Y grows downward).
SPRITES = {
    'Decor:bush': ('decor', 'objects/decor-bush.png', (0, 0, 32, 24), 4, 'floor', FULL),  # brush frames are seen too (D64)
    'Decor:lookalike': ('decor', 'objects/decor-lookalike.png', (0, 0, 24, 16), 1, 'floor', FULL),
    'HangingVine': ('decor', 'objects/decor-vine.png', (0, 0, 8, 40), 1, 'top', FULL),
    'Decor:trunk': ('decor', 'objects/decor-trunk.png', (0, 0, 16, 288), 1, 'floor', FULL),  # #349: behind the branches
    'ThornRoot': ('active', 'objects/trap-thorn-root.png', (0, 0, 16, 24), 1, 'floor', FULL),
    'ThornTuft': ('active', 'objects/trap-thorn-tuft.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'FallingBranch': ('active', 'objects/trap-branch.png', (0, 0, 32, 16), 1, 'top', FULL),
    'ExplosiveStump': ('active', 'objects/trap-stump.png', (0, 0, 16, 16), 4, 'floor', FULL),  # the idle tell loop (#346)
    'ExplosiveRoot': ('active', 'objects/trap-root.png', (0, 0, 16, 24), 4, 'floor', FULL),  # the idle tell loop (#346)
    'Bomb': ('active', 'objects/trap-bomb.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'Puffball': ('active', 'objects/trap-puffball.png', (0, 0, 16, 16), 1, 'floor', FULL),
    'ThornVine': ('active', 'objects/trap-vine.png', (0, 0, 24, 40), 1, 'top', FULL),
    'HoneyFungus': ('active', 'objects/trap-honey-fungus.png', (0, 0, 24, 16), 1, 'floor', GLOWS),
    'FalseHoneyFungus': ('active', 'objects/trap-false-honey-fungus.png', (0, 0, 24, 16), 1, 'floor', GLOWS),
    'FallingPlatform': ('active', 'objects/FallingOn.png', (0, 0, 32, 10), 1, 'top', FULL),
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
}
# Types that draw no sprite of their own, with what they are (a new type must be added to one of the two tables).
NO_SPRITE = {
    'Torch': 'a particle light (effects.Torch), no sprite',
    'Actionable': 'a lever target: a Torch is a particle light, a horizontal Wall draws nothing (a vertical one is Actionable:Wall)',
    'FallingStone': 'HIDDEN at rest: nothing is drawn until it falls (D71, cue #360)',
    'Checkpoint': 'an invisible zone',
    'Deadline': 'an invisible kill zone under the room',
    'QuickSand': 'collision only; drawn by the tile layer',
    'Wall': 'collision only; drawn by the tile layer',
    None: 'an untyped collision block; drawn by the tile layer',
}
SPRITE_GROUPS = ('SpawnPoints', 'Collisions')
# Tile-layer scenery reported against the sky (report only, #189): (row range, column range) in the tileset.
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


def frames(key):
    _, sheet, box, count, _, _ = SPRITES[key]
    result = []
    for sheet, (x, y, w, h), count in ((sheet, box, count), *EXTRA_FRAMES.get(key, ())):
        image = Image.open(ROOT / 'assets/images' / sheet).convert('RGBA')
        result += [image.crop((x + n * w, y, x + n * w + w, y + h)) for n in range(count)]
    return result


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


def sky_strip():
    sky = Image.open(ROOT / SKY).convert('RGBA')
    return sky.resize((round(sky.width * VIEW_HEIGHT / sky.height), VIEW_HEIGHT), Image.BILINEAR)


def backgrounds(layer, sky):
    """One composited background per sky offset."""
    result = []
    for n in range(SKY_OFFSETS):
        offset = n * sky.width // SKY_OFFSETS
        image = Image.new('RGBA', layer.size)
        for x in range(-offset, layer.width, sky.width):
            image.paste(sky, (x, 0))
        image.alpha_composite(layer)
        result.append(image)
    return result


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
                key, kind = key_of(group.get('name'), obj, props)
                if key is None:
                    if kind not in NO_SPRITE and kind != 'Platform':
                        errors.append(f'{name}: object type {kind!r} is not classified in tools/check_contrast.py '
                                      f'(add it to SPRITES with its tier, or to NO_SPRITE saying why it draws nothing)')
                    continue
                if key not in SPRITES:
                    errors.append(f'{name}: sprite {key!r} is not classified in tools/check_contrast.py')
                    continue
                yield (name, key, float(obj.get('x')), float(obj.get('y')) + offset_y,
                       float(obj.get('width', TILE)), float(obj.get('height', TILE)))


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


def tile_report(sky):
    """Report only (#189): mean outline and body of the tile scenery against the sky."""
    sums = {name: [[], []] for name in TILE_SCENERY}
    for name in MAPS:
        layer, gids, width = tile_layer(ET.parse(ROOT / f'assets/tiles/{name}.tmx').getroot())
        background = backgrounds(Image.new('RGBA', layer.size), sky)[0]
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
    sky = sky_strip()
    layers = {name: tile_layer(ET.parse(ROOT / f'assets/tiles/{name}.tmx').getroot())[0] for name in MAPS}
    images = {name: backgrounds(layer, sky) for name, layer in layers.items()}
    results = {}
    halo = Image.open(ROOT / 'assets/images' / HALO_SHEET).convert('RGBA')
    for name, key, x, y, w, h in placements(errors):
        anchor, glows = SPRITES[key][4], SPRITES[key][5]
        for frame in frames(key):
            left = int(x + w / 2 - frame.width / 2)
            top = int(y) if anchor == 'top' else int(y + h - frame.height)
            for glow in glows:
                for background in images[name]:
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
    return results, errors, tile_report(sky)


def main():
    results, errors, tiles = run()
    print(f'{"sprite":24} {"tier":7} {"outline min":>11} {"outline max":>11} {"body max":>8}')
    for key, rows in results.items():
        print(f'{key:24} {SPRITES[key][0]:7} {min(r[2] for r in rows):11.3f} {max(r[2] for r in rows):11.3f} '
              f'{max(r[3] for r in rows):8.3f}')
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
