#!/usr/bin/env python3
import unittest
from PIL import Image

import forest_trap_sprites as traps
from sprite_layouts import FOREST_TRAP_SHEETS, WORLD_IMAGES, WORLD_PALETTE_SIZE, OPAQUE_ALPHA

BBOX_BOTTOM = 3  # (left, top, right, bottom)


class ForestTrapSpritesTest(unittest.TestCase):
    def test_every_layout_has_a_grid_and_export_of_the_declared_size(self):
        for name, (size, frames) in FOREST_TRAP_SHEETS.items():
            with self.subTest(sheet=name):
                master, colors = traps.sheet_image(name)
                self.assertEqual((size[0] * len(frames), size[1]), master.size)
                self.assertLessEqual(colors, WORLD_PALETTE_SIZE - 1)
                self.assertEqual(WORLD_IMAGES[f'trap-{name}'], master.size)
                shipped = Image.open(traps.ROOT / f'assets/images/objects/trap-{name}.png')
                self.assertEqual(master.tobytes(), shipped.convert('RGBA').tobytes(),
                    'shipped export differs from the grid; run tools/export_object_sprites.py')

    def test_every_frame_has_artwork_and_binary_alpha(self):
        for name, (size, frames) in FOREST_TRAP_SHEETS.items():
            master, _ = traps.sheet_image(name)
            for index, frame in enumerate(frames):
                with self.subTest(sheet=name, frame=frame):
                    cell = master.crop((index * size[0], 0, (index + 1) * size[0], size[1]))
                    alpha = cell.getchannel('A')
                    self.assertIsNotNone(alpha.getbbox(), 'empty frame')
                    self.assertTrue(set(alpha.tobytes()) <= {0, OPAQUE_ALPHA})

    def test_every_blast_frame_rests_on_its_bottom_row(self):
        # #255: the blast is drawn standing on the stump's base, so the lowest opaque row is the frame's last row.
        size, frames = FOREST_TRAP_SHEETS['blast']
        master, _ = traps.sheet_image('blast')
        for index, frame in enumerate(frames):
            with self.subTest(frame=frame):
                cell = master.crop((index * size[0], 0, (index + 1) * size[0], size[1]))
                self.assertEqual(size[1], cell.getchannel('A').getbbox()[BBOX_BOTTOM], 'blast sinks or floats above its base')


if __name__ == '__main__':
    unittest.main()
