"""The Kimeria arms strip (#350, #416, D95): the export matches the grid, `lit` is the reference drawing, and the other
frames change the sun only (crown, border and shield are the accepted arms in every frame)."""
import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import arms_sprites as arms  # noqa: E402
from sprite_layouts import ARMS_FRAME_SIZE, ARMS_FRAMES  # noqa: E402

SUN_BOX = (3, 12, 20, 29)  # x0, y0, x1, y1 (exclusive): the sun, its rays and the flare tips, inside the border


class ArmsSpritesTest(unittest.TestCase):
    def test_the_shipped_strip_is_the_grid(self):
        shipped = Image.open(arms.TARGET).convert('RGBA')
        self.assertEqual(shipped.tobytes(), arms.master().tobytes())

    def test_only_the_sun_changes_between_frames(self):
        strip = arms.master()
        width, height = ARMS_FRAME_SIZE
        lit = strip.crop((arms.LIT * width, 0, (arms.LIT + 1) * width, height))
        x0, y0, x1, y1 = SUN_BOX
        for index, name in enumerate(ARMS_FRAMES):
            frame = strip.crop((index * width, 0, (index + 1) * width, height))
            for y in range(height):
                for x in range(width):
                    if not (x0 <= x < x1 and y0 <= y < y1):
                        self.assertEqual(frame.getpixel((x, y)), lit.getpixel((x, y)), f'{name} at {x},{y}')


if __name__ == '__main__':
    unittest.main()
