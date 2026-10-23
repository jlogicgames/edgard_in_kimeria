#!/usr/bin/env python3
"""Composite authored head poses onto the unchanged native idle body; pack up/down."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def export():
    base = Image.open(ROOT / 'assets/images/hero/Player.png').convert('RGBA').crop((0, 432, 48, 480))
    source = Image.open(ROOT / 'art/hero/look-master.png').convert('RGBA')
    if source.width != source.height * 2:
        raise ValueError('Look source must contain two square frames, up then down')
    # Reuse the shipped character colors. This is palette mapping, not pixel painting.
    colors = sorted({rgb[:3] for rgb in base.get_flattened_data() if rgb[3]})
    palette = Image.new('P', (1, 1))
    palette.putpalette([value for color in colors for value in color] + [0] * (768 - len(colors) * 3))
    sheet = Image.new('RGBA', (96, 48))
    head = (24, 23, 31, 31)
    for i in range(2):
        frame = source.crop((i * source.height, 0, (i + 1) * source.height, source.height))
        frame = frame.resize((48, 48), Image.Resampling.NEAREST)
        alpha = frame.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
        frame = frame.convert('RGB').quantize(palette=palette, dither=Image.Dither.NONE).convert('RGBA')
        frame.putalpha(alpha)
        clean = Image.new('RGBA', frame.size)
        clean.paste(frame, (0, 0), alpha)
        frame = clean
        pose = base.copy()
        # Replace only the authored head; preserve body, hand, sword and feet registration.
        pose.paste(frame.crop(head), head[:2])
        sheet.paste(pose, (i * 48, 0))
    # Original hero frame has 16 opaque colors plus transparency (17 actual entries).
    target = ROOT / 'assets/images/hero/Look.png'
    # Lossless indexed encoding: octree quantization can merge even existing sword shades.
    rgba = list(sheet.get_flattened_data())
    entries = sorted(set(rgba))
    indices = {color: i for i, color in enumerate(entries)}
    output = Image.frombytes('P', sheet.size, bytes(indices[color] for color in rgba))
    output.putpalette([v for color in entries for v in color[:3]])
    output.info['transparency'] = bytes(color[3] for color in entries)
    output.save(target)
    print(target.relative_to(ROOT))


if __name__ == '__main__':
    export()
