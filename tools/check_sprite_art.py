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

ROOT = Path(__file__).resolve().parents[1]
BASE = 'core/src/main/java/com/jlogicsoftware/kimeria/'
WORLD_IMAGES = {'torch': (8, 16), 'stone': (8, 8), 'fireball': (16, 16),
                'lever': (32, 16)}  # Two registered 16x16 states: up/on, down/off.
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
    ('effects/Torch.java', 'render', 'particle'): 'live simulated flame/smoke particles around a separate torch sprite',
    ('effects/Firefly.java', 'render', 'particle'): 'time-varying ambient firefly light',
    ('effects/BombExplosionEffect.java', 'render', 'quad'): 'time-varying explosion shader',
    ('effects/ShockwaveEffect.java', 'render', 'quad'): 'time-varying shockwave shader',
    ('effects/FogEffect.java', 'render', 'quad'): 'moving menu atmosphere shader',
    ('effects/RainDrop.java', 'render', 'quad'): 'simulated rain streak, no persistent object',
    ('effects/BloodBurst.java', 'render', 'quad'): 'simulated impact particles',
    ('ui/Overlay.java', 'panel', 'quad'): 'UI panel/fade geometry',
    ('ui/Overlay.java', 'buttons', 'quad'): 'menu focus/button geometry',
    ('ui/HealerDialogueView.java', 'frame', 'quad'): 'dialogue frame/focus geometry',
    ('ui/InteractionHighlight.java', 'corners', 'quad'): 'selection annotation around a separately drawn sprite',
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


def png_contract(path, data, size, colors, binary_alpha):
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
    if not alpha or 0 not in alpha:
        issues.append(f'{path}: transparent background required')
    if binary_alpha and any(value not in (0, 255) for value in alpha):
        issues.append(f'{path}: gameplay alpha must be binary; no antialiased edges')
    return issues


def image_contracts(root):
    issues = []
    contracts = [(f'assets/images/objects/{name}.png', size, 16, True) for name, size in WORLD_IMAGES.items()]
    contracts += [(f'assets/images/HUD/{name}.png', (128, 128), 256, False)
                  for name in ('coin', 'chicken', 'key', 'fireball')]
    # Character poses reuse the legacy hero's 16 opaque colors plus alpha; PNG pads to 32.
    contracts.append(('assets/images/hero/Look.png', (96, 48), 32, True))
    for relative, size, colors, binary_alpha in contracts:
        path = root / relative
        if not path.is_file():
            issues.append(f'{relative}: missing authored image')
        else:
            issues.extend(png_contract(relative, path.read_bytes(), size, colors, binary_alpha))
    return issues


def check(root):
    directory = root / BASE
    if not directory.is_dir():
        raise ValueError(f'Java source directory is missing: {directory}')
    issues = []
    for path in sorted(directory.rglob('*.java')):
        issues.extend(violations(path.relative_to(directory).as_posix(), path.read_text()))
    for path in sorted((root / 'tools').rglob('*.py')):
        issues.extend(python_art_violations(path.relative_to(root).as_posix(), path.read_text()))
    for path in sorted((root / 'tools').rglob('*.java')):
        code = code_only(path.read_text())
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
