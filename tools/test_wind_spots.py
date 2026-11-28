#!/usr/bin/env python3
"""Behavioural checks for the wind's leaf spots gate (tools/wind_spots.py, #593)."""
import contextlib
import io
import math
import unittest
from unittest import mock
from PIL import Image
import wind_spots as gate

OPAQUE = 255
LEAF = (0x23, 0x32, 0x23)  # a crown grid's `m`
BARK = (0x2e, 0x18, 0x13)
CARD = {'CROWN_LEAF': (0x3d, 0x55, 0x38), 'CROWN_FLECK': (0x54, 0x5a, 0x2b)}
FAR = (0xf0, 0xf0, 0xf0)
SIDE = 40  # the square test frame
PIXELS_PER_PATCH = gate.CROWN_PIXELS
PATCHES_ON_SIDE = round(SIDE * SIDE / PIXELS_PER_PATCH)  # the square frame, all leaf
SMALL_FRAME = 8
BOX = 24  # a square box that holds every patch mask
WHOLE = (0, 0, SIDE, SIDE, None)
SMALL = (0, 0, BOX, BOX, None)
TILE_ROW = 32  # the width of two layer tiles
FIRST_BOX = (0, 0, 4, 4, None)
SECOND_BOX = (4, 4, 4, 4, None)
SHARED_BOXES = [(0, 0, 8, 4, None), (2, 0, 8, 4, None)]  # two boxes sharing columns 2 to 7
SHARED_PIXELS = 40
TWO_RUNS = [(0, 0), (1, 0), (3, 0), (1, 1)]
TWO_RUNS_PACKED = [[0, 0, 2], [3, 0, 1], [1, 1, 1]]
MID_BOXES = 7  # six clusters, one split in two
GROVE_BOXES = 7  # seven spruces
FEWEST_PER_BOX = 1
EVERY_OTHER = 2  # every other row carries bark


def sheet(rows, boxes, depth='crown', colours=None):
    return gate.Sheet(f'test-{depth}', depth, len(rows[0]), len(rows), None, lambda x, y: rows[y][x],
                      colours or {'m': LEAF, 'b': BARK}, boxes)


class LeafBoxesTest(unittest.TestCase):
    def test_boxes_belong_to_the_frame_or_piece_above_them(self):
        text = 'size 8 8\n# leaves in a comment\nframe a\nleaves 0 0 4 4\nleaves 4 4 4 4\npiece b 1 1\nleaves 0 0 4 4 2\n'
        self.assertEqual({'a': [FIRST_BOX, SECOND_BOX], 'b': [(*FIRST_BOX[:-1], len('ab'))]}, gate.leaf_boxes(text, 't'))

    def test_a_box_without_a_section_or_with_missing_numbers_fails(self):
        for text in ('leaves 0 0 4 4\n', 'frame a\nleaves 0 0 4\n', 'frame a\nleaves 0 0 4 x\n'):
            with self.assertRaises(ValueError):
                gate.leaf_boxes(text, 't')

    def test_a_box_outside_its_frame_fails(self):
        with self.assertRaises(ValueError):
            gate.checked(WHOLE, SMALL_FRAME, SMALL_FRAME, 't')


class PatchesTest(unittest.TestCase):
    ROWS = ['m' * SIDE] * SIDE

    def test_every_mask_is_a_ragged_cluster_from_its_top_left(self):
        for depth, masks in gate.PATCHES.items():
            for mask in masks:
                self.assertEqual(1, len({len(row) for row in mask}), depth)
                self.assertTrue(any(gate.MASK in row for row in mask), depth)

    def test_a_patch_keeps_only_leaf_pixels_never_bark_or_sky(self):
        rows = ['mmmmmmmmmmbbbbbbbbbbbbbb' if y % EVERY_OTHER else 'm' * BOX for y in range(BOX)]
        drawn = sheet(rows, [])
        found = gate.candidates(drawn, SMALL)
        self.assertTrue(found)
        for (x, y), pixels in found.items():
            self.assertEqual(0, min(dx for dx, _ in pixels))
            self.assertEqual(0, min(dy for _, dy in pixels))
            self.assertTrue(all(rows[y + dy][x + dx] == 'm' for dx, dy in pixels))

    def test_a_place_with_too_little_leaf_takes_no_patch(self):
        rows = ['m.' * (BOX // 2)] * BOX  # half of any mask lies on sky, under the crown's three quarters
        self.assertEqual({}, gate.candidates(sheet(rows, []), SMALL))

    def test_a_layer_patch_stays_inside_its_tile(self):
        rows = ['5' * TILE_ROW] * gate.TILE
        for (x, y), pixels in gate.candidates(sheet(rows, [], 'mid'), (0, 0, TILE_ROW, gate.TILE, None)).items():
            self.assertEqual(x // gate.TILE, (x + max(dx for dx, _ in pixels)) // gate.TILE)
            self.assertLess(y + max(dy for _, dy in pixels), gate.TILE)

    def test_a_crown_takes_one_patch_per_so_many_leaf_pixels_apart_and_always_the_same(self):
        patches = gate.spots_of(sheet(self.ROWS, [WHOLE]))
        self.assertEqual(PATCHES_ON_SIDE, len(patches))
        self.assertEqual(patches, gate.spots_of(sheet(self.ROWS, [WHOLE])))
        for i, (a, _) in enumerate(patches):
            for b, _ in patches[i + 1:]:
                self.assertGreaterEqual(math.dist(a, b), gate.DEPTHS['crown'].spacing)

    def test_two_boxes_over_the_same_leaves_count_them_once(self):
        self.assertEqual(SHARED_PIXELS, gate.leaf_pixels(sheet(self.ROWS, []), SHARED_BOXES))

    def test_pixels_pack_into_horizontal_runs_row_by_row(self):
        self.assertEqual(TWO_RUNS_PACKED, gate.runs(TWO_RUNS))

    def test_a_box_without_a_leaf_patch_fails(self):
        rows = ['.' * BOX] * BOX
        with self.assertRaises(ValueError):
            gate.spots_of(sheet(rows, [SMALL], 'mid'))


class ColourTest(unittest.TestCase):
    ROWS = ['m' * SIDE] * SIDE

    def errors(self, colours, image_colour=LEAF):
        image = Image.new('RGBA', (SIDE, SIDE), image_colour + (OPAQUE,))
        return gate.colour_errors(gate.baked([sheet(self.ROWS, [WHOLE])]), colours, lambda path: image)[0]

    def test_the_card_colours_pass_on_the_leaf(self):
        self.assertEqual([], self.errors(CARD))

    def test_a_glint_too_far_from_the_leaf_fails(self):
        self.assertTrue(self.errors({**CARD, 'CROWN_LEAF': FAR}))

    def test_an_image_that_differs_from_its_grid_fails(self):
        self.assertTrue(self.errors(CARD, BARK))

    def test_a_missing_java_colour_fails(self):
        with self.assertRaises(ValueError):
            gate.glint_colours('int CROWN_LEAF = 0x3d5538;')


class ShippedTest(unittest.TestCase):
    def test_the_committed_file_is_fresh_and_every_glint_is_within_its_limit(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gate.main(['--check']))

    def test_a_stale_file_fails(self):
        with mock.patch.object(gate, 'bake', return_value='stale\n'), contextlib.redirect_stdout(io.StringIO()), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, gate.main(['--check']))

    def test_every_depth_has_patches_in_every_box(self):
        sheets = {s.name: s for s in gate.all_sheets()}
        for name, boxes in (('forest-mid', MID_BOXES), ('forest-grove', GROVE_BOXES)):
            self.assertEqual(boxes, len(sheets[name].boxes))
            self.assertGreaterEqual(len(gate.spots_of(sheets[name])), boxes * FEWEST_PER_BOX)
        self.assertTrue(any(s.depth == 'crown' for s in sheets.values()))


if __name__ == '__main__':
    unittest.main()
