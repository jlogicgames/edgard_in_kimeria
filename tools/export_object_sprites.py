#!/usr/bin/env python3
"""Prepare authored raster art: native pixel sprites and separate detailed HUD icons."""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
WORLD = {'torch': (8, 16), 'stone': (8, 8), 'fireball': (16, 16),
         'lever-up': (16, 16), 'lever-down': (16, 16)}


def export(source, target, size, pixel_art):
    artwork = Image.open(source).convert('RGBA')
    if pixel_art:
        # Remove generated edge coverage, not draw geometry. World alpha is binary.
        artwork.putalpha(artwork.getchannel('A').point(lambda alpha: 255 if alpha >= 128 else 0))
    bounds = artwork.getchannel('A').getbbox()
    if bounds is None:
        raise ValueError(f'{source}: no artwork')
    artwork = ImageOps.contain(artwork.crop(bounds), size,
        Image.Resampling.NEAREST if pixel_art else Image.Resampling.LANCZOS)
    output = Image.new('RGBA', size)
    output.paste(artwork, ((size[0] - artwork.width) // 2, (size[1] - artwork.height) // 2))
    output = output.quantize(colors=16 if pixel_art else 256,
        method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    target.parent.mkdir(parents=True, exist_ok=True)
    output.save(target, bits=4 if pixel_art else 8)
    print(target.relative_to(ROOT))


def export_levers():
    # Preserve the shared source canvas/pivot. Independent alpha crops would move the base.
    frames = [Image.open(ROOT / 'art/objects' / f'lever-{pose}-pixel.png').convert('RGBA')
              for pose in ('up', 'down')]
    if frames[0].size != frames[1].size:
        raise ValueError('Lever poses must share a registered source canvas')
    strip = Image.new('RGBA', (32, 16))
    for index, frame in enumerate(frames):
        frame.putalpha(frame.getchannel('A').point(lambda alpha: 255 if alpha >= 128 else 0))
        strip.paste(frame.resize((16, 16), Image.Resampling.NEAREST), (index * 16, 0))
    strip = strip.quantize(colors=16, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    for index, pose in enumerate(('up', 'down')):
        target = ROOT / 'assets/images/objects' / f'lever-{pose}.png'
        strip.crop((index * 16, 0, index * 16 + 16, 16)).save(target, bits=4)
        print(target.relative_to(ROOT))


if __name__ == '__main__':
    for name, size in WORLD.items():
        if name.startswith('lever-'): continue
        export(ROOT / 'art/objects' / f'{name}-pixel.png',
            ROOT / 'assets/images/objects' / f'{name}.png', size, True)
    export_levers()
    for name in ('coin', 'chicken', 'key', 'fireball'):
        source = ROOT / 'art' / ('objects' if name == 'fireball' else 'hud') / f'{name}-master.png'
        export(source, ROOT / 'assets/images/HUD' / f'{name}.png', (128, 128), False)
