#!/usr/bin/env python3
import unittest
from PIL import Image

import forest_trap_sprites as traps
from sprite_layouts import FOREST_TRAP_SHEETS, DECOR_SHEETS, WORLD_IMAGES, WORLD_PALETTE_SIZE, OPAQUE_ALPHA

BBOX_BOTTOM = 3  # (left, top, right, bottom)
HONEY_FUNGUS_FRAMES = 6
MAX_SILHOUETTE_DIFFERENCE_PX = 12
VINE_SIZE = (8, 40)
VINE_STEM_COLUMNS = (3, 4)  # the olive stem of decor-vine.grid, under the ceiling


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

    def test_the_decor_bush_matches_its_layout_and_shipped_export(self):
        size, frames = DECOR_SHEETS['bush']
        master, colors = traps.sheet_image('decor:bush')
        self.assertEqual((size[0] * len(frames), size[1]), master.size)
        self.assertEqual(WORLD_IMAGES['decor-bush'], master.size)
        self.assertLessEqual(colors, WORLD_PALETTE_SIZE - 1)
        shipped = Image.open(traps.ROOT / 'assets/images/objects/decor-bush.png')
        self.assertEqual(master.tobytes(), shipped.convert('RGBA').tobytes(), 'run tools/forest_trap_sprites.py decor:bush')
        for index in range(len(frames)):
            cell = master.crop((index * size[0], 0, (index + 1) * size[0], size[1]))
            self.assertEqual(size[1], cell.getchannel('A').getbbox()[BBOX_BOTTOM], 'the bush must rest on its bottom row')
            self.assertTrue(set(cell.getchannel('A').tobytes()) <= {0, OPAQUE_ALPHA})

    def test_the_decor_vine_matches_its_layout_hangs_from_the_top_and_keeps_to_the_palette(self):
        size, frames = DECOR_SHEETS['vine']
        self.assertEqual((VINE_SIZE, ('hang',)), (size, frames))
        master, colors = traps.sheet_image('decor:vine')
        self.assertEqual(WORLD_IMAGES['decor-vine'], master.size)
        self.assertLessEqual(colors, WORLD_PALETTE_SIZE - 1)
        shipped = Image.open(traps.ROOT / 'assets/images/objects/decor-vine.png')
        self.assertEqual(master.tobytes(), shipped.convert('RGBA').tobytes(), 'run tools/forest_trap_sprites.py decor:vine')
        alpha = master.getchannel('A')
        self.assertEqual(0, alpha.getbbox()[1], 'the vine is anchored at its top row (world Y grows downward)')
        self.assertEqual(size[1], alpha.getbbox()[BBOX_BOTTOM], 'and hangs the full 40 px')
        self.assertTrue(set(alpha.tobytes()) <= {0, OPAQUE_ALPHA})
        top = master.crop((0, 0, size[0], 1))
        self.assertTrue(all(top.getchannel('A').getpixel((x, 0)) == OPAQUE_ALPHA for x in VINE_STEM_COLUMNS), 'the stem touches the ceiling')

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

    def test_the_two_honey_fungi_are_twins_in_size_and_silhouette_but_not_in_colour(self):
        # #295: the pair must differ only slightly; each frame stands on the floor line and keeps to a 24x16 cell.
        size, _ = FOREST_TRAP_SHEETS['honey-fungus']
        self.assertEqual(size, FOREST_TRAP_SHEETS['false-honey-fungus'][0])
        honey, _ = traps.sheet_image('honey-fungus')
        false, _ = traps.sheet_image('false-honey-fungus')
        differing = sum(a != b for a, b in zip(honey.getdata(), false.getdata()))
        self.assertGreater(differing, 0)
        for index in range(HONEY_FUNGUS_FRAMES):
            cell = (index * size[0], 0, (index + 1) * size[0], size[1])
            alpha_honey, alpha_false = honey.crop(cell).getchannel('A'), false.crop(cell).getchannel('A')
            self.assertEqual(size[1], alpha_honey.getbbox()[BBOX_BOTTOM], 'honey fungus floats above the floor')
            self.assertEqual(size[1], alpha_false.getbbox()[BBOX_BOTTOM], 'false honey fungus floats above the floor')
            changed = sum(a != b for a, b in zip(alpha_honey.getdata(), alpha_false.getdata()))
            self.assertLessEqual(changed, MAX_SILHOUETTE_DIFFERENCE_PX, f'frame {index}: the silhouettes differ by more than a few pixels')


if __name__ == '__main__':
    unittest.main()
