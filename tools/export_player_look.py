#!/usr/bin/env python3
"""Composite authored head poses onto the unchanged native idle body; pack up/down."""
from pathlib import Path
from PIL import Image
from sprite_layouts import (PLAYER_FRAME_SIZE, PLAYER_IDLE_ROW, PLAYER_LOOK_FRAME_COUNT,
    PLAYER_HEAD_BOUNDS, PLAYER_LOOK_SIZE, RGB_CHANNELS, ALPHA_CHANNEL, PALETTE_ENTRIES,
    ALPHA_THRESHOLD, OPAQUE_ALPHA)

ROOT = Path(__file__).resolve().parents[1]


def export():
    base = Image.open(ROOT / 'assets/images/hero/Player.png').convert('RGBA').crop(
        (0, PLAYER_IDLE_ROW * PLAYER_FRAME_SIZE, PLAYER_FRAME_SIZE, (PLAYER_IDLE_ROW + 1) * PLAYER_FRAME_SIZE))
    source = Image.open(ROOT / 'art/hero/look-master.png').convert('RGBA')
    if source.width != source.height * PLAYER_LOOK_FRAME_COUNT:
        raise ValueError('Look source must contain two square frames, up then down')
    # Reuse the shipped character colors. This is palette mapping, not pixel painting.
    colors = sorted({rgb[:RGB_CHANNELS] for rgb in base.get_flattened_data() if rgb[ALPHA_CHANNEL]})
    palette = Image.new('P', (1, 1))
    palette.putpalette([value for color in colors for value in color] + [0] * (PALETTE_ENTRIES * RGB_CHANNELS - len(colors) * RGB_CHANNELS))
    sheet = Image.new('RGBA', PLAYER_LOOK_SIZE)
    head = PLAYER_HEAD_BOUNDS
    for i in range(PLAYER_LOOK_FRAME_COUNT):
        frame = source.crop((i * source.height, 0, (i + 1) * source.height, source.height))
        frame = frame.resize((PLAYER_FRAME_SIZE, PLAYER_FRAME_SIZE), Image.Resampling.NEAREST)
        alpha = frame.getchannel('A').point(lambda a: OPAQUE_ALPHA if a >= ALPHA_THRESHOLD else 0)
        frame = frame.convert('RGB').quantize(palette=palette, dither=Image.Dither.NONE).convert('RGBA')
        frame.putalpha(alpha)
        clean = Image.new('RGBA', frame.size)
        clean.paste(frame, (0, 0), alpha)
        frame = clean
        pose = base.copy()
        # Replace only the authored head; preserve body, hand, sword and feet registration.
        pose.paste(frame.crop(head), (head[0], head[1]))
        sheet.paste(pose, (i * PLAYER_FRAME_SIZE, 0))
    # Original hero frame has 16 opaque colors plus transparency (17 actual entries).
    target = ROOT / 'assets/images/hero/Look.png'
    # Lossless indexed encoding: octree quantization can merge even existing sword shades.
    rgba = list(sheet.get_flattened_data())
    entries = sorted(set(rgba))
    indices = {color: i for i, color in enumerate(entries)}
    output = Image.frombytes('P', sheet.size, bytes(indices[color] for color in rgba))
    output.putpalette([v for color in entries for v in color[:RGB_CHANNELS]])
    output.info['transparency'] = bytes(color[ALPHA_CHANNEL] for color in entries)
    output.save(target)
    print(target.relative_to(ROOT))


if __name__ == '__main__':
    export()
