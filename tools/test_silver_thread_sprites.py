"""The silver thread on death (#341, D81): the export matches the grid, and the thread is loud enough (D76 active tier).

The thread is not a placed map object, so `check_contrast.py` does not see it; this test measures it the same way. The
strand (read from the exported PNG, so a recolour is measured here) is stamped along the curves the game draws, from a death
point to every RoomStart of the three maps, taut and slack, level and sloped, over every sky offset; the worst outline must
reach `check_contrast.ACTIVE_OUTLINE_MIN`. Measured 2026-11-17: 0.340 worst, forest-0's lever room (D81 in docs/DECISIONS.md).
"""
import math
import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_contrast as contrast  # noqa: E402
import silver_thread_sprites as thread  # noqa: E402

STRAND_W = 1  # SilverThread.STRAND_W
STRAND_H = 2  # SilverThread.STRAND_H
STRAND = (0, 0, STRAND_W, STRAND_H)  # the strand's cell in the sheet (SilverThread.java)
ANCHOR_OFFSET = (15.5, 36.5)  # the anchor from a RoomStart's top-left: the landing frame's left wrist (23, 52) facing right, minus the frame offset (8, 16), plus the pixel centre
# (dx, dy, sag) of a death point relative to the anchor: level and sloped, taut (0) and slack (16, Player.THREAD_SAG).
SHAPES = ((240, 0, 0), (240, 0, 16), (160, -40, 0), (320, 24, 16))
SAG_SHAPE = 4  # SilverThread.SAG_SHAPE: the sag is sag * 4u(1 - u)
SAG_PATH = 2  # SilverThread.steps: the sag adds twice its depth to the path


def curve(ax, ay, bx, by, sag):
    """The pixels SilverThread.draw stamps from A to B (one step per pixel of the longer axis plus the sag; floor, as
    SilverThread.pixel: the ends are pixel centres)."""
    steps = max(1, int(max(abs(bx - ax), abs(by - ay)) + SAG_PATH * abs(sag)))
    points = []
    for i in range(steps + 1):
        u = i / steps
        points.append((math.floor(ax + (bx - ax) * u), math.floor(ay + (by - ay) * u + sag * SAG_SHAPE * u * (1 - u))))
    return points


def stamped(size, points, strand):
    layer = Image.new('RGBA', size)
    for x, y in points:
        if 0 <= x <= size[0] - STRAND_W and 0 <= y <= size[1] - STRAND_H:
            layer.alpha_composite(strand, (x, y))
    return layer


class SilverThreadTest(unittest.TestCase):
    def test_export_matches_the_grid(self):
        exported = Image.open(thread.TARGET).convert('RGBA')
        self.assertEqual(thread.master().tobytes(), exported.tobytes(), 'run python3 tools/silver_thread_sprites.py')

    def test_the_thread_is_active_tier_at_every_room_start(self):
        strand = Image.open(thread.TARGET).convert('RGBA').crop(STRAND)
        sky = contrast.sky_strip()
        worst = None
        for name, root in contrast.map_roots().items():
            layer, _, _ = contrast.tile_layer(root)
            backgrounds = contrast.backgrounds(layer, sky)
            starts = [(float(o.get('x')), float(o.get('y')), o.get('name')) for g in root.findall('objectgroup')
                      for o in g.findall('object') if o.get('type') == 'RoomStart']
            self.assertTrue(starts, f'{name}: no RoomStart')
            for sx, sy, room in starts:
                ax, ay = sx + ANCHOR_OFFSET[0], sy + ANCHOR_OFFSET[1]
                for dx, dy, sag in SHAPES:
                    sprite_layer = stamped(layer.size, curve(ax + dx, ay + dy, ax, ay, sag), strand)
                    box = sprite_layer.getbbox()
                    sprite = sprite_layer.crop(box)
                    for background in backgrounds:
                        outline, _ = contrast.measure(sprite, background, box[0], box[1])
                        if outline is not None and (worst is None or outline < worst[0]):
                            worst = (outline, name, room, dx, dy, sag)
        self.assertGreaterEqual(worst[0], contrast.ACTIVE_OUTLINE_MIN, f'thread outline {worst}')


if __name__ == '__main__':
    unittest.main()
