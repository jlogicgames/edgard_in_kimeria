#!/usr/bin/env python3
"""Reject primitive-based game artwork, including renamed/new render helpers.

UI geometry, debug colliders, dynamic VFX and blank GPU storage are different
categories: they describe interface/measurements/time-varying effects, not an
object's authored appearance. Permissions are per method AND primitive kind;
no entity, directory or whole source file is excluded.
"""
from pathlib import Path
import argparse
import ast
import json
import re
import struct
import sys
from sprite_layouts import (ENEMY_IMAGES, WORLD_IMAGES, OPAQUE_WORLD_IMAGES, HERO_IMAGES, HUD_ICON_SIZE, SILVER_THREAD_SIZE,
    WORLD_PALETTE_SIZE, HUD_PALETTE_SIZE, HUD_ICONS, TOUCH_CONTROL_SIZES, DIALOGUE_IMAGES, OPAQUE_DIALOGUE_IMAGES,
    ARMS_IMAGE_SIZE)

ROOT = Path(__file__).resolve().parents[1]
BASE = 'core/src/main/java/com/jlogicsoftware/kimeria/'
RULES = {
    'quad': re.compile(r'\b(?:Shaders\s*\.\s*)?whiteQuad\s*\('),
    'particle': re.compile(r'\bSoftDot\s*\.\s*(?:region|glowRegion)\s*\('),
    'raster': re.compile(r'\bnew\s+Pixmap\s*\(|\.\s*(?:drawPixel|fillRectangle|fillCircle|fillTriangle)\s*\('),
    'shape': re.compile(r'\bnew\s+(?:[\w]+\.)*ShapeRenderer\s*\('),
    'shape_draw': re.compile(r'\b(?:sr|shapeRenderer|renderer|graphics|pixmap)\s*\.\s*(?:rect|circle|ellipse|triangle|line)\s*\('),
}
# Each permission records what would be lost by replacing this with static art.
ALLOWED = {
    ('KimeriaGame.java', 'create', 'shape'): 'F1 debug renderer allocation',
    ('KimeriaGame.java', 'create', 'quad'): 'GPU quad prewarm, no depicted object',
    ('KimeriaGame.java', 'createTexture', 'raster'): 'blank framebuffer storage for TeaVM, no artwork',
    ('world/Level.java', 'renderDebug', 'shape_draw'): 'F1 collision/attack measurements',
    ('world/Level.java', 'drawHitbox', 'shape_draw'): 'F1 collider measurements',
    ('effects/Shaders.java', 'whiteQuad', 'raster'): 'one white GPU shader/UI texel',
    ('effects/SoftDot.java', 'build', 'raster'): 'shared radial particle kernel, no object silhouette',
    ('effects/ParticleTextures.java', 'prepare', 'particle'): 'builds the shared soft dot and glow once at level load (#483); draws nothing and depicts no object',
    ('effects/ParticleTextures.java', 'prepare', 'quad'): 'builds the shared white texel once at level load (#483); draws nothing and depicts no object',
    ('effects/Torch.java', 'render', 'particle'): 'live simulated flame/smoke particles around a separate torch sprite',
    ('effects/EnvironmentFx.java', 'render', 'particle'): 'simulated jump dust puff (#239, approved particle category): lives 0.4 s, depicts no object',
    ('effects/EnvironmentFx.java', 'render', 'quad'): 'simulated falling ceiling grit (#239, approved particle category): 1 to 2 px specks, depict no object',
    ('effects/FireballFx.java', 'render', 'particle'): 'fireball hit halos (#397, approved particle category): one warm additive light per hit that lives at most 0.3 s, depicts no object',
    ('effects/FireballFx.java', 'render', 'quad'): 'fireball trail flame and sparks as 1 to 3 px squares that shrink out (#397, approved particle category): live at most 0.38 s, depict no object; the ball is the authored fireball sprite',
    ('effects/RingPool.java', 'render', 'quad'): 'the fireball impact ring (#397) and the pickup ring (#402) through the existing shockwave.frag, like ShockwaveEffect: a time-varying shader ring that lives at most 0.5 s, depicts no object',
    ('effects/FireballFx.java', 'drawBallHalo', 'particle'): 'the warm light around the flying fireball (#397), like the torch glow: one additive light quad under the authored fireball sprite, depicts no object',
    ('effects/LeafFx.java', 'render', 'quad'): 'falling leaf ambience (#473, particles approved by the author on 2026-11-18; colours dimmed to the decor tier pending #236): 5 to 6 px thin plates that sway and flip, live 9 to 14 s, drift down a Forest room as scenery and depict no object the hero meets, hits or collects (the trees, branches and traps are authored sprites)',
    ('effects/BeetleFx.java', 'render', 'particle'): "the beetle's burst flash, steam and death poof (#327, D100, particle category): soft additive and smoke dots that live at most 0.55 s, depict no object; the beetle is the authored monster-beetle sprite",
    ('effects/BeetleFx.java', 'render', 'quad'): "the beetle's wind-up specks, burst spray and death shell chips as 1 to 3 px squares (#327, D100, particle category, like the fireball sparks): live at most 0.4 s, depict no object; the beetle is the authored monster-beetle sprite",
    ('effects/SpiderFx.java', 'render', 'particle'): "the spider's fang glints, bite dust and death poof (#328, D102, particle category): soft additive and smoke dots that live at most 0.55 s, depict no object; the spider is the authored monster-spider sprite",
    ('effects/SpiderFx.java', 'render', 'quad'): "the spider's wind-up silk glints, leap silk line and leg flecks as 1 px squares (#328, D102, particle category, like the beetle's specks): live at most 0.45 s, depict no object; the spider is the authored monster-spider sprite",
    ('effects/SnailFx.java', 'render', 'particle'): "the snail's swelling gob glow, the steam of a burnt gob and its death poof (#329, D106, particle category): soft additive and smoke dots that live at most 0.55 s, depict no object; the snail and its gob are the authored monster-snail sprite",
    ('effects/SnailFx.java', 'render', 'quad'): "the snail's wind-up specks, the gob's drips, splat droplets and smear, and its shell chips as 1 to 2 px squares (#329, D106, particle category, like the spider's glints): live at most 0.45 s, depict no object; the snail and its gob are the authored monster-snail sprite",
    ('effects/WolfFx.java', 'render', 'particle'): "the wolf's bite jaw glints, skid and bite dust and death poof (#331, D115, particle category): soft additive and smoke dots that live at most 0.55 s, depict no object; the wolf is the authored monster-wolf sprite",
    ('effects/WolfFx.java', 'render', 'quad'): "the wolf's wind-up breath streaks and death fur flecks as 1 px squares (#331, D115, particle category, like the spider's glints): live at most 0.45 s, depict no object; the wolf is the authored monster-wolf sprite",
    ('effects/RemainsFx.java', 'render', 'particle'): "a monster body's crumble dust, burn glow and burnt wisp (D101, #478, particle category): soft additive and smoke dots that live at most 0.7 s, depict no object; the body is the monster's authored decor sprite",
    ('effects/RemainsFx.java', 'render', 'quad'): "a monster body's crumble chips and burn flames as 1 to 3 px squares (D101, #478, particle category, like the fireball sparks): live at most 0.6 s, depict no object; the body is the monster's authored decor sprite",
    ('effects/FlightFx.java', 'render', 'quad'): 'the 2.5 to 3 px trail and landing sparks behind a flying sprite (D132, the same approved particle category as the pickup sparks): live at most 0.2 s, depict no object; the thing that flies is always an authored sprite region passed to start()',
    ('effects/PickupSparksFx.java', 'render', 'quad'): 'the pickup spark burst as 1 to 3 px squares that shrink out (#402, approved particle category, like the fireball sparks): live at most 0.6 s, depict no object; the item is the authored sprite',
    ('effects/PortalFx.java', 'render', 'particle'): 'the soft violet light of the room-exit portal (#552, D123, approved particle category, behaviour of the pixelfx portal): additive dots that live at most 2.4 s round the exit rectangle, depict no object the hero meets or collects; the exit itself is a Tiled trigger with no sprite',
    ('effects/PortalFx.java', 'render', 'quad'): 'the rim sparks, spiral arms and pulled-in motes of the room-exit portal (#552, D123, approved particle category, like the fireball sparks): 1 to 2 px additive squares that live at most 1.3 s, depict no object',
    ('effects/RoomRippleFx.java', 'render', 'quad'): 'the motes of the room-change ripple (#553, D123, approved particle category, like the pickup sparks) and its rings through the existing shockwave.frag: live at most 0.5 s, depict no object; the hero is the authored sprite',
    ('effects/SoftGlowFx.java', 'render', 'particle'): "the soft light of a pickup and of the fairy's marks (#544, D124, approved particle category, replaces the pickup-glow and fairy-glow sprites): additive dots that live at most 1.6 s, depict no object; the item, the fairy and the target are authored sprites",
    ('effects/SoftGlowFx.java', 'render', 'quad'): 'the specks drifting round a pickup or a fairy mark (#544, D124, particle category, like the pickup sparks): 1 px additive squares that live 1.2 s, depict no object',
    ('effects/SporeCloud.java', 'render', 'quad'): 'the shader haze of the false honey fungus spore cloud (#296, D63): a live procedural swirl clipped to the lethal rectangle, lives 1 s, depicts no object; the mushroom is the authored trap-false-honey-fungus sprite',
    ('effects/PuffballFx.java', 'render', 'quad'): 'simulated lime spores of the puffball (#558, D125, approved particle category): 1 to 2 px specks that live up to 0.9 s inside the lethal rectangle and depict no object; the puffball is the authored trap-puffball sprite',
    ('effects/PuffballFx.java', 'render', 'particle'): 'simulated lime spore haze and release flash of the puffball (#558, approved particle category): live up to 1 s inside the lethal rectangle, depict no object',
    ('effects/SporeCloud.java', 'render', 'particle'): 'simulated lime spore haze (#296, approved particle category, D63): lives up to 1.05 s inside the lethal rectangle, depicts no object',
    ('entity/objects/FallingStone.java', 'render', 'quad'): 'simulated grit trickle from the loose overhang (#360, approved particle category like the ceiling grit): 1 to 2 px specks that live 0.8 s, depict no object; the lip and the stone are the authored trap-stone-lip and trap-stone sprites',
    ('entity/objects/HangingHive.java', 'render', 'quad'): 'simulated specks of the hanging hive (#365, D90, the particle category of the stone grit): 1 px KIND_GRIT specks that drift, gather and scatter for 0.3 to 0.8 s and depict no object; the hive and its stinger band are the authored trap-hive and trap-stingers sprites',
    ('entity/objects/EmberFissure.java', 'render', 'quad'): 'simulated sparks of the ember fissure (#366, D91, the particle category of the torch embers): 1 px KIND_GLOW sparks that rise from the slit in the warning and fall from the jet as it sinks, 0.4 to 0.5 s, depicting no object; the fissure and its jet are the authored trap-fissure and trap-ember-jet sprites',
    ('entity/objects/LegHold.java', 'render', 'quad'): 'simulated specks of the leg-hold trap (#561, D128, the particle category of the deadfall chips): 1 px KIND_GRIT specks that hop from the hinges in the warning and fly up from the jaws at the snap, 0.3 to 0.4 s, depicting no object; the jaws are the authored trap-leghold sprite',
    ('entity/objects/HoneyFungus.java', 'renderBurst', 'particle'): 'simulated golden spore burst (#295, approved particle category, D63): lives up to 1 s, depicts no object; the mushroom itself is the authored trap-honey-fungus sprite',
    ('effects/Firefly.java', 'render', 'particle'): 'time-varying ambient firefly light',
    ('effects/BombExplosionEffect.java', 'render', 'quad'): 'time-varying explosion shader',
    ('effects/ShockwaveEffect.java', 'render', 'quad'): 'time-varying shockwave shader',
    ('effects/FogEffect.java', 'render', 'quad'): 'moving menu atmosphere shader',
    ('effects/KimeriaBloodSystem.java', 'render', 'quad'): 'simulated impact particles',
    ('ui/Overlay.java', 'panel', 'quad'): 'UI panel/fade geometry',
    ('ui/Overlay.java', 'buttons', 'quad'): 'menu focus/button geometry',
}
NON_CODE = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
METHOD = re.compile(r'\b(?:public|private|protected)\s+(?:(?:static|final|synchronized|abstract)\s+)*(?:[\w<>?\[\],.]+\s+)?(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w.,\s]+)?\{')


def code_only(text):
    return NON_CODE.sub(lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0]), text)


def methods(text):
    found = []
    for match in METHOD.finditer(text):
        start = match.end() - 1
        depth = 1
        end = start + 1
        while end < len(text) and depth:
            depth += (text[end] == '{') - (text[end] == '}')
            end += 1
        found.append((start, end, match[1]))
    return found


def violations(path, source):
    code = code_only(source)
    spans = methods(code)
    declarations = {m.start(1) for m in METHOD.finditer(code)}
    result = []
    for kind, pattern in RULES.items():
        for match in pattern.finditer(code):
            if match.start() in declarations:
                continue
            owner = next((name for a, b, name in reversed(spans) if a <= match.start() < b), '<initializer>')
            if (path, owner, kind) not in ALLOWED:
                line = code.count('\n', 0, match.start()) + 1
                result.append(f'{path}:{line} ({owner}): procedural {kind}; use an authored sprite in assets/images')
    return sorted(result)


def python_art_violations(path, source):
    tree = ast.parse(source, filename=path)
    issues = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and (node.module or '').startswith('PIL'):
            if 'ImageDraw' in (node.module or '') or any(alias.name == 'ImageDraw' for alias in node.names):
                issues.append(f'{path}:{node.lineno}: ImageDraw paints new artwork; use imagegen/source artwork')
        if isinstance(node, ast.Import) and any('ImageDraw' in alias.name for alias in node.names):
            issues.append(f'{path}:{node.lineno}: ImageDraw paints new artwork; use imagegen/source artwork')
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == 'putpixel':
            issues.append(f'{path}:{node.lineno}: pixel painting is not an authored sprite export')
    return issues


def png_contract(path, data, size, colors, alpha_mode):
    """Inspect PNG metadata without adding an imaging dependency to release builds."""
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        return [f'{path}: not a PNG']
    chunks = {}
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        if offset + length + 12 > len(data):
            return [f'{path}: truncated PNG chunk']
        chunks[data[offset + 4:offset + 8]] = data[offset + 8:offset + 8 + length]
        offset += length + 12
    header = chunks.get(b'IHDR', b'')
    if len(header) != 13:
        return [f'{path}: missing PNG header']
    width, height, depth, mode = struct.unpack('>IIBB', header[:10])
    issues = []
    if (width, height) != size:
        issues.append(f'{path}: expected native {size}, found {(width, height)}')
    palette = chunks.get(b'PLTE', b'')
    if mode != 3 or not palette or len(palette) // 3 > colors:
        issues.append(f'{path}: expected indexed PNG with at most {colors} palette entries including transparency')
    alpha = chunks.get(b'tRNS', b'')
    if alpha_mode != 'opaque' and (not alpha or 0 not in alpha):
        issues.append(f'{path}: transparent background required')
    if alpha_mode == 'binary' and any(value not in (0, 255) for value in alpha):
        issues.append(f'{path}: gameplay alpha must be binary; no antialiased edges')
    if alpha_mode == 'opaque' and any(value != 255 for value in alpha):
        issues.append(f'{path}: full-frame world surface must be opaque')
    return issues


def image_contracts(root):
    issues = []
    contracts = [(f'assets/images/objects/{name}.png', size, WORLD_PALETTE_SIZE,
        'opaque' if name in OPAQUE_WORLD_IMAGES else 'binary') for name, size in WORLD_IMAGES.items()]
    contracts += [(f'assets/images/HUD/{name}.png', HUD_ICON_SIZE, HUD_PALETTE_SIZE, 'soft')
                  for name in HUD_ICONS]
    # Touch controls are small indexed pixel art with binary alpha, on the world-art palette limit.
    contracts += [(f'assets/images/HUD/touch/{name}.png', size, WORLD_PALETTE_SIZE, 'binary')
                  for name, size in TOUCH_CONTROL_SIZES.items()]
    # The dialogue screen frame (#337, D78): native pixel art on the world-art palette limit.
    contracts += [(f'assets/images/ui/dialogue-{name}.png', size, WORLD_PALETTE_SIZE,
                   'opaque' if name in OPAQUE_DIALOGUE_IMAGES else 'binary') for name, size in DIALOGUE_IMAGES.items()]
    # The Kimeria arms (#350, #416): the menu mark and the ending's sun, native pixel art, binary alpha.
    contracts.append(('assets/images/ui/kimeria-arms.png', ARMS_IMAGE_SIZE, WORLD_PALETTE_SIZE, 'binary'))
    # The hero (#343, D79): world pixel art, 16 palette entries including transparency, binary alpha.
    contracts += [(f'assets/images/hero/hero-{name}.png', size, WORLD_PALETTE_SIZE, 'binary') for name, size in HERO_IMAGES.items()]
    # The redrawn enemy sheets (2026-11-21): RedMob and Bat from art/objects/enemy grids, world pixel art, binary alpha.
    contracts += [(f'assets/images/enemy/{name}.png', size, WORLD_PALETTE_SIZE, 'binary') for name, size in ENEMY_IMAGES.items()]
    # The silver thread on death (#341, D81): the strand and glint the computed curve is stamped with.
    contracts.append(('assets/images/objects/silver-thread.png', SILVER_THREAD_SIZE, WORLD_PALETTE_SIZE, 'binary'))
    for relative, size, colors, alpha_mode in contracts:
        path = root / relative
        if not path.is_file():
            issues.append(f'{relative}: missing authored image')
        else:
            issues.extend(png_contract(relative, path.read_bytes(), size, colors, alpha_mode))
    return issues


def check(root):
    directory = root / BASE
    if not directory.is_dir():
        raise ValueError(f'Java source directory is missing: {directory}')
    issues = []
    for path in sorted(directory.rglob('*.java')):
        issues.extend(violations(path.relative_to(directory).as_posix(), path.read_text(encoding='utf-8')))
    for path in sorted((root / 'tools').rglob('*.py')):
        issues.extend(python_art_violations(path.relative_to(root).as_posix(), path.read_text(encoding='utf-8')))
    for path in sorted((root / 'tools').rglob('*.java')):
        code = code_only(path.read_text(encoding='utf-8'))
        for match in re.finditer(r'\.\s*(?:fillRect|fillOval|fillPolygon|drawLine|drawOval|drawPolygon|setRGB)\s*\(', code):
            issues.append(f'{path.relative_to(root)}:{code.count(chr(10), 0, match.start()) + 1}: shape painting is not an authored sprite export')
    if (root / 'assets').is_dir():
        issues.extend(image_contracts(root))
    return issues


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--hook', action='store_true')
    args = parser.parse_args()
    try:
        if args.hook:
            # Read only the stable event envelope, never transcripts or credentials.
            json.load(sys.stdin)
        issues = check(args.root.resolve())
    except (OSError, ValueError, SyntaxError, json.JSONDecodeError) as error:
        print(f'Sprite art guard could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('Game objects require authored sprites; primitive placeholders are forbidden.\n' + '\n'.join(issues[:15]), file=sys.stderr)
        return 2
    if args.hook:
        print('{}')
    else:
        print('Sprite art guard passed: authored object art and separate world/HUD PNG contracts.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
