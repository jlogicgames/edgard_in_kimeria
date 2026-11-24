"""The intro book (#536, D133): the shipped images are the composed grids, the leaves match the geometry the Java code repeats, and the
story ink reads on every colour a text column can sit on."""
import re
import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import intro_book_sprites as book  # noqa: E402
from sprite_layouts import (INTRO_BOOK_COVER, INTRO_BOOK_IMAGES, INTRO_BOOK_LEAF_LEFT, INTRO_BOOK_LEAF_RIGHT,  # noqa: E402
                            INTRO_BOOK_LEAF_SIZE, INTRO_BOOK_SCREEN, RGB_CHANNELS)

JAVA = book.ROOT / 'core/src/main/java/com/jlogicsoftware/kimeria/ui/'
STORY_INK = (0x2a, 0x1a, 0x10)
MIN_CONTRAST = 4.5  # WCAG AA for body text
LEFT = 0  # the fields of a box (x0, y0, x1, y1)
TOP = 1
RIGHT = 2
BOTTOM = 3
HEX = 16
CHANNEL_MAX = 255
SRGB_LINEAR_LIMIT = 0.04045
SRGB_LINEAR_SLOPE = 12.92
SRGB_OFFSET = 0.055
SRGB_SCALE = 1.055
SRGB_GAMMA = 2.4
LUMA_RED = 0.2126
LUMA_GREEN = 0.7152
LUMA_BLUE = 0.0722
FLARE = 0.05
# IntroBook.COLUMN_LEFT / COLUMN_RIGHT with COLUMN_WIDTH, and IntroPages.TEXT_TOP / PAGE_BOTTOM, in screen pixels.
COLUMNS = ((56, 292), (348, 584))
TEXT_TOP = 40
TEXT_BOTTOM = 304


def luminance(rgb):
    def channel(value):
        value /= CHANNEL_MAX
        return value / SRGB_LINEAR_SLOPE if value <= SRGB_LINEAR_LIMIT else ((value + SRGB_OFFSET) / SRGB_SCALE) ** SRGB_GAMMA
    r, g, b = (channel(v) for v in rgb)
    return LUMA_RED * r + LUMA_GREEN * g + LUMA_BLUE * b


def contrast(a, b):
    low, high = sorted((luminance(a), luminance(b)))
    return (high + FLARE) / (low + FLARE)


def pixels_of(path):
    with Image.open(path) as image:
        return image.convert('RGBA').tobytes()


class IntroBookSpritesTest(unittest.TestCase):
    def test_the_shipped_images_are_the_composed_grids(self):
        self.assertEqual(pixels_of(book.TARGET_DIR / 'intro-book.png'), book.book().tobytes())
        self.assertEqual(pixels_of(book.TARGET_DIR / 'intro-leaves.png'), book.leaves().tobytes())

    def test_sizes_follow_the_layout(self):
        for name, size in INTRO_BOOK_IMAGES.items():
            with Image.open(book.TARGET_DIR / f'{name}.png') as image:
                self.assertEqual(image.size, size)
        self.assertEqual(book.book().size, INTRO_BOOK_SCREEN)

    def test_the_leaves_sit_inside_the_cover_and_meet_at_the_spine(self):
        cover = INTRO_BOOK_COVER
        for box in (INTRO_BOOK_LEAF_LEFT, INTRO_BOOK_LEAF_RIGHT):
            self.assertTrue(cover[LEFT] < box[LEFT] and box[RIGHT] < cover[RIGHT], box)
            self.assertTrue(cover[TOP] < box[TOP] and box[BOTTOM] < cover[BOTTOM], box)
        self.assertEqual(INTRO_BOOK_LEAF_LEFT[RIGHT], INTRO_BOOK_LEAF_RIGHT[LEFT])
        self.assertEqual((INTRO_BOOK_LEAF_LEFT[TOP], INTRO_BOOK_LEAF_LEFT[BOTTOM]), (INTRO_BOOK_LEAF_RIGHT[TOP], INTRO_BOOK_LEAF_RIGHT[BOTTOM]))

    def test_the_java_geometry_is_the_layout(self):
        java = (JAVA / 'IntroBook.java').read_text(encoding='utf-8')

        def number(name):
            return int(re.search(rf'\b{name}\s*=\s*(\d+)', java).group(1))
        self.assertEqual((number('SCREEN_WIDTH'), number('SCREEN_HEIGHT')), INTRO_BOOK_SCREEN)
        self.assertEqual((number('LEAF_WIDTH'), number('LEAF_HEIGHT')), INTRO_BOOK_LEAF_SIZE)
        self.assertEqual(number('COVER_BOTTOM'), INTRO_BOOK_COVER[BOTTOM])
        self.assertEqual(number('LEAF_TOP'), INTRO_BOOK_LEAF_LEFT[TOP])
        self.assertEqual(number('SPINE_X'), INTRO_BOOK_LEAF_LEFT[RIGHT])
        for name, column in (('COLUMN_LEFT', COLUMNS[0]), ('COLUMN_RIGHT', COLUMNS[1])):
            self.assertEqual(int(float(re.search(rf'\b{name}\s*=\s*(\d+)f', java).group(1))), column[0])
        self.assertEqual(int(re.search(r'COLUMN_WIDTH\s*=\s*(\d+)f', java).group(1)), COLUMNS[0][1] - COLUMNS[0][0])
        overlay = (JAVA / 'Overlay.java').read_text(encoding='utf-8')
        ink = re.search(r'STORY_INK = new Color\(0x(\w\w) / 255f, 0x(\w\w) / 255f, 0x(\w\w) / 255f', overlay)
        self.assertEqual(tuple(int(part, HEX) for part in ink.groups()), STORY_INK)

    def test_the_story_ink_reads_on_every_leaf_colour_a_text_column_sits_on(self):
        leaf = book.leaf('left')
        seen = set()
        for column_left, column_right in COLUMNS:
            for x in range(column_left, column_right):
                leaf_x = x - (INTRO_BOOK_LEAF_LEFT[LEFT] if x < INTRO_BOOK_LEAF_LEFT[RIGHT] else INTRO_BOOK_LEAF_RIGHT[LEFT])
                for y in range(TEXT_TOP, TEXT_BOTTOM):
                    seen.add(leaf.getpixel((leaf_x, y - INTRO_BOOK_LEAF_LEFT[TOP]))[:RGB_CHANNELS])
        self.assertTrue(seen)
        for colour in seen:
            self.assertGreaterEqual(contrast(STORY_INK, colour), MIN_CONTRAST, f'ink on {colour}')


if __name__ == '__main__':
    unittest.main()
