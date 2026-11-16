#!/usr/bin/env python3
"""#343, D79: the hero grid exports to the shipped sheets, within the world-art contract."""
import unittest
from PIL import Image

import hero_sprites as hero
from sprite_layouts import HERO_SHEETS, HERO_IMAGES, HERO_FRAME_SIZE, WORLD_PALETTE_SIZE, OPAQUE_ALPHA

RGB_CHANNELS = 3  # a PNG palette entry is three bytes


class HeroSpritesTest(unittest.TestCase):
    def test_the_shipped_sheets_are_the_grid_exactly(self):
        images = hero.sheet_images()
        self.assertEqual(set(HERO_SHEETS), set(images))
        for name, master in images.items():
            with self.subTest(sheet=name):
                self.assertEqual(HERO_IMAGES[name], master.size)
                shipped = Image.open(hero.ROOT / f'assets/images/hero/hero-{name}.png')
                self.assertEqual('P', shipped.mode)
                self.assertLessEqual(len(shipped.getpalette()) // RGB_CHANNELS, WORLD_PALETTE_SIZE)
                self.assertEqual(master.tobytes(), shipped.convert('RGBA').tobytes(), 'run tools/hero_sprites.py')
                self.assertTrue(set(master.getchannel('A').tobytes()) <= {0, OPAQUE_ALPHA}, 'binary alpha')

    def test_the_grid_keeps_to_one_world_palette(self):
        colors, _ = hero.parse()
        self.assertLessEqual(len(colors) - 1, WORLD_PALETTE_SIZE - 1)

    def test_every_layout_frame_is_drawn_and_none_is_spare(self):
        _, frames = hero.parse()
        expected = {hero.frame_name(sheet, clip, n) for sheet, clips in HERO_SHEETS.items()
                    for clip, count in clips for n in range(1, count + 1)}
        self.assertEqual(expected, set(frames))

    def test_a_flip_mirrors_about_the_body_axis(self):
        # Column c of a 64 px frame mirrors to 63 - c, so the axis x 31.5 (the hitbox centre) stays put.
        _, frames = hero.parse()
        right, left = frames['r.clamber1'], frames['l.clamber1']
        self.assertEqual([row[::-1] for row in right], left)


if __name__ == '__main__':
    unittest.main()
