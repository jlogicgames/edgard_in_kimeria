#!/usr/bin/env python3
"""Prepare authored raster art: native pixel sprites and separate detailed HUD icons."""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
from sprite_layouts import (WORLD_FRAMES, WORLD_PALETTE_SIZE, HUD_PALETTE_SIZE,
    WORLD_PALETTE_BITS, HUD_PALETTE_BITS, ALPHA_THRESHOLD, OPAQUE_ALPHA,
    LEVER_FRAME_SIZE, LEVER_FRAME_COUNT, HUD_ICON_SIZE)


def export(source, target, size, pixel_art):
    artwork = Image.open(source).convert('RGBA')
    if pixel_art:
        # Remove generated edge coverage, not draw geometry. World alpha is binary.
        artwork.putalpha(artwork.getchannel('A').point(lambda alpha: OPAQUE_ALPHA if alpha >= ALPHA_THRESHOLD else 0))
    bounds = artwork.getchannel('A').getbbox()
    if bounds is None:
        raise ValueError(f'{source}: no artwork')
    artwork = ImageOps.contain(artwork.crop(bounds), size,
        Image.Resampling.NEAREST if pixel_art else Image.Resampling.LANCZOS)
    output = Image.new('RGBA', size)
    output.paste(artwork, ((size[0] - artwork.width) // 2, (size[1] - artwork.height) // 2))
    output = output.quantize(colors=WORLD_PALETTE_SIZE if pixel_art else HUD_PALETTE_SIZE,
        method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    target.parent.mkdir(parents=True, exist_ok=True)
    output.save(target, bits=WORLD_PALETTE_BITS if pixel_art else HUD_PALETTE_BITS)
    print(target.relative_to(ROOT))


def export_levers():
    # Preserve the shared source canvas/pivot. Independent alpha crops would move the base.
    source = Image.open(ROOT / 'art/objects/lever-pixel.png').convert('RGBA')
    if source.width != source.height * LEVER_FRAME_COUNT:
        raise ValueError('Lever sheet must contain two square registered frames: up, down')
    frames = [source.crop((i * source.height, 0, (i + 1) * source.height, source.height))
              for i in range(LEVER_FRAME_COUNT)]
    strip = Image.new('RGBA', (LEVER_FRAME_SIZE * LEVER_FRAME_COUNT, LEVER_FRAME_SIZE))
    for index, frame in enumerate(frames):
        frame.putalpha(frame.getchannel('A').point(lambda alpha: OPAQUE_ALPHA if alpha >= ALPHA_THRESHOLD else 0))
        strip.paste(frame.resize((LEVER_FRAME_SIZE, LEVER_FRAME_SIZE), Image.Resampling.NEAREST), (index * LEVER_FRAME_SIZE, 0))
    strip = strip.quantize(colors=WORLD_PALETTE_SIZE, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    target = ROOT / 'assets/images/objects/lever.png'
    strip.save(target, bits=WORLD_PALETTE_BITS)
    print(target.relative_to(ROOT))


if __name__ == '__main__':
    for name, size in WORLD_FRAMES.items():
        export(ROOT / 'art/objects' / f'{name}-pixel.png',
            ROOT / 'assets/images/objects' / f'{name}.png', size, True)
    export_levers()
    for name in ('coin', 'chicken', 'key', 'fireball'):
        source = ROOT / 'art/hud' / f'{name}-master.png'
        export(source, ROOT / 'assets/images/HUD' / f'{name}.png', HUD_ICON_SIZE, False)
