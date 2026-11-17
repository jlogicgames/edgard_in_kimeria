#!/usr/bin/env python3
"""The moon rim (D92, #430): what it recolours, what it leaves, and the exporter flags (all off until the night change)."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_contrast as gate  # noqa: E402
import forest_trap_sprites as traps  # noqa: E402
import hero_sprites as hero  # noqa: E402
import moon_rim  # noqa: E402

INK = (0x0b, 0x08, 0x14, 255)
BONE = (0xef, 0xe6, 0xc4, 255)
RIM = moon_rim.RIM + (255,)
SIZE = 6  # a 6x6 cell holding a 4x4 ink square at (1, 1)
SQUARE_START = 1  # the square covers 1..4 in x and y
SQUARE_END = 5
TOP = 1  # the square's edge rows and columns
BOTTOM = 4
LEFT = 1
RIGHT = 4
PAIR = 2  # two frames side by side
MIDDLE = 2
BAND = 2  # half-width of the band across a frame border
NIGHT = (0x2c, 0x3f, 0x66, 255)  # D92's approved night colour
NIGHT_SIZE = 64
AT = 24  # where a trap frame is measured on the night patch
TRIED_SHEET = 'thorn-tuft'  # an active trap whose ink contour fails on the night (0.189, w6-430 rim notes)


def ink_square(colour=INK, cells=1):
    """A test fixture, not artwork: `cells` side-by-side 6x6 cells, each with a 4x4 square in `colour`."""
    image = Image.new('RGBA', (SIZE * cells, SIZE))
    for n in range(cells):
        image.paste(colour, (SQUARE_START + n * SIZE, SQUARE_START, SQUARE_END + n * SIZE, SQUARE_END))
    return image


def cell_of(name):
    _, _, (size, _) = traps.layout(name)
    return size


class RimTest(unittest.TestCase):
    def test_the_lit_sides_are_rimmed_and_the_underside_keeps_its_ink(self):
        out = moon_rim.rim(ink_square())
        self.assertEqual(out.getpixel((MIDDLE, TOP)), RIM, 'top edge')
        self.assertEqual(out.getpixel((LEFT, MIDDLE)), RIM, 'left edge')
        self.assertEqual(out.getpixel((RIGHT, MIDDLE)), RIM, 'right edge')
        self.assertEqual(out.getpixel((MIDDLE, MIDDLE)), INK, 'inside')
        self.assertEqual(out.getpixel((MIDDLE + 1, BOTTOM)), INK, 'the bottom edge faces the ground')
        self.assertEqual(out.getpixel((0, 0)), (0, 0, 0, 0), 'no pixel is added')

    def test_light_edges_are_left_alone(self):
        self.assertEqual(moon_rim.rim(ink_square(BONE)).tobytes(), ink_square(BONE).tobytes())

    def test_the_silhouette_never_changes(self):
        before, after = ink_square(), moon_rim.rim(ink_square())
        self.assertEqual(before.getchannel('A').tobytes(), after.getchannel('A').tobytes())

    def test_frames_in_a_strip_do_not_shield_each_other(self):
        strip = Image.new('RGBA', (SIZE * PAIR, SIZE))
        strip.paste(INK, (SIZE - BAND, TOP, SIZE + BAND, BOTTOM + 1))  # a band across the border between the two frames
        out = moon_rim.rim(strip, (SIZE, SIZE))
        self.assertEqual(out.getpixel((SIZE - 1, MIDDLE)), RIM, 'the right edge of frame 1 is a frame edge')
        self.assertEqual(out.getpixel((SIZE, MIDDLE)), RIM, 'the left edge of frame 2 is a frame edge')
        self.assertEqual(moon_rim.rim(strip).getpixel((SIZE - 1, MIDDLE)), INK, 'as one cell the band is solid there')

    def test_it_is_idempotent_so_a_hand_softened_rim_survives(self):
        once = moon_rim.rim(ink_square(cells=PAIR), (SIZE, SIZE))
        self.assertEqual(moon_rim.rim(once, (SIZE, SIZE)).tobytes(), once.tobytes())

    def test_a_mirrored_frame_gets_the_mirrored_rim(self):
        image = ink_square()
        image.paste(INK, (SQUARE_END, MIDDLE, SQUARE_END + 1, MIDDLE + 1))  # an asymmetric bump on the right
        flipped = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        self.assertEqual(moon_rim.rim(flipped).tobytes(), moon_rim.rim(image).transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes())

    def test_a_sheet_must_be_whole_cells(self):
        with self.assertRaises(ValueError):
            moon_rim.rim(ink_square(), (SIZE - 1, SIZE))

    def test_the_cli_writes_the_rimmed_sheet(self):
        with tempfile.TemporaryDirectory() as folder:
            source, target = Path(folder) / 'in.png', Path(folder) / 'out.png'
            ink_square(cells=PAIR).save(source)
            self.assertEqual(moon_rim.main([str(source), '--cell', str(SIZE), str(SIZE), '--out', str(target)]), 0)
            self.assertEqual(Image.open(target).convert('RGBA').tobytes(), moon_rim.rim(ink_square(cells=PAIR), (SIZE, SIZE)).tobytes())
            self.assertEqual(Image.open(source).convert('RGBA').tobytes(), ink_square(cells=PAIR).tobytes(), 'source untouched')

    def test_the_rim_lifts_a_failing_trap_over_the_floor_on_the_night(self):
        frame_size = cell_of(TRIED_SHEET)
        sheet, _ = traps.sheet_image(TRIED_SHEET)
        frame = sheet.crop((0, 0) + frame_size)
        night = Image.new('RGBA', (NIGHT_SIZE, NIGHT_SIZE), NIGHT)
        before = gate.measure(frame, night, AT, AT)[0]
        after = gate.measure(moon_rim.rim(frame), night, AT, AT)[0]
        self.assertLess(before, gate.ACTIVE_OUTLINE_MIN)
        self.assertGreaterEqual(after, gate.ACTIVE_OUTLINE_MIN)


class ExporterFlagTest(unittest.TestCase):
    def test_every_flag_is_off_until_the_night_change(self):
        self.assertEqual(traps.RIM_SHEETS, frozenset())
        self.assertEqual(hero.RIM_SHEETS, frozenset())

    def test_a_flagged_trap_sheet_is_rimmed_with_one_more_colour(self):
        plain, colours = traps.sheet_image(TRIED_SHEET)
        with mock.patch.object(traps, 'RIM_SHEETS', frozenset({TRIED_SHEET})):
            rimmed, rimmed_colours = traps.sheet_image(TRIED_SHEET)
        self.assertEqual(rimmed.tobytes(), moon_rim.rim(plain, cell_of(TRIED_SHEET)).tobytes())
        self.assertEqual(rimmed_colours, colours + 1)

    def test_a_flagged_hero_sheet_is_rimmed_frame_by_frame(self):
        plain = hero.sheet_images()
        sheet = next(iter(plain))
        with mock.patch.object(hero, 'RIM_SHEETS', frozenset({sheet})):
            rimmed = hero.sheet_images()
        cell = (hero.N, hero.N)
        self.assertEqual(rimmed[sheet].tobytes(), moon_rim.rim(plain[sheet], cell).tobytes())

    def test_the_hero_palette_has_no_slot_for_the_rim_yet_and_says_so(self):
        colours, _ = hero.parse()
        master = next(iter(hero.sheet_images().values()))
        with self.assertRaises(ValueError):
            hero.indexed(master, {**colours, 'moon rim': moon_rim.RIM})


if __name__ == '__main__':
    unittest.main()
