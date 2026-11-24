"""The boss health bar art (#543): the exports are the grids, match BossHud's measurements, and the three states stay readable."""
import re
import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import boss_hud_sprites as art  # noqa: E402
import check_contrast as contrast  # noqa: E402
from sprite_layouts import (BOSS_HUD_SHEETS, BOSS_HUD_SLOTS, BOSS_HUD_PITCH, BOSS_HUD_INSET, BOSS_HUD_WELL_MARGIN,  # noqa: E402
                            BOSS_HUD_FILL_GAP_MIN, WORLD_PALETTE_SIZE, OPAQUE_ALPHA)

HUD = art.ROOT / 'core/src/main/java/com/jlogicsoftware/kimeria/ui/BossHud.java'


def constants():
    source = HUD.read_text(encoding='utf-8')
    values = {}
    for name in ('FRAME_WIDTH', 'FRAME_HEIGHT', 'SEGMENT_WIDTH', 'SEGMENT_HEIGHT', 'SEGMENT_GAP', 'INSET_X', 'INSET_Y'):
        values[name] = int(re.search(rf'\b{name}\s*=\s*(\d+)', source).group(1))
    return values


def mean_lightness(image):
    """The mean OKLab L of the opaque pixels, the measure of D76."""
    pixels = [p for p in image.get_flattened_data() if p[art.RGB_CHANNELS]]
    return sum(contrast.oklab(p[:art.RGB_CHANNELS])[0] for p in pixels) / len(pixels)


class BossHudArtTest(unittest.TestCase):
    def test_the_shipped_pngs_are_the_grids(self):
        for name in BOSS_HUD_SHEETS:
            shipped = Image.open(art.TARGET_DIR / f'{name}.png').convert('RGBA')
            self.assertEqual(art.master(name).tobytes(), shipped.tobytes(), name)

    def test_the_sizes_are_the_ones_bosshud_draws(self):
        c = constants()
        self.assertEqual((c['FRAME_WIDTH'], c['FRAME_HEIGHT']), BOSS_HUD_SHEETS['frame'])
        for name in ('segment-filled', 'segment-empty'):
            self.assertEqual((c['SEGMENT_WIDTH'], c['SEGMENT_HEIGHT']), BOSS_HUD_SHEETS[name])

    def test_the_layout_is_the_one_bosshud_draws(self):
        c = constants()
        self.assertEqual((c['INSET_X'], c['INSET_Y']), BOSS_HUD_INSET)
        self.assertEqual(c['SEGMENT_WIDTH'] + c['SEGMENT_GAP'], BOSS_HUD_PITCH)

    def test_every_segment_sits_inside_the_frames_well(self):
        c = constants()
        frame = art.master('frame')
        last = c['INSET_X'] + (BOSS_HUD_SLOTS - 1) * BOSS_HUD_PITCH + c['SEGMENT_WIDTH']
        self.assertLessEqual(last, c['FRAME_WIDTH'] - BOSS_HUD_WELL_MARGIN, 'the last segment must end inside the right cap')
        # every pixel a segment covers is the well's backing in the frame (or its shadow row), never rim or cap
        backing = frame.getpixel((c['INSET_X'] + 1, c['INSET_Y'] + 1))
        shadow = frame.getpixel((c['INSET_X'] + 1, c['INSET_Y']))
        for i in range(BOSS_HUD_SLOTS):
            left = c['INSET_X'] + i * BOSS_HUD_PITCH
            for x in range(left, left + c['SEGMENT_WIDTH']):
                for y in range(c['INSET_Y'], c['INSET_Y'] + c['SEGMENT_HEIGHT']):
                    self.assertIn(frame.getpixel((x, y)), (backing, shadow), f'segment {i} at ({x}, {y})')

    def test_filled_and_empty_differ_in_lightness_and_shape(self):
        filled, empty = art.master('segment-filled'), art.master('segment-empty')
        self.assertGreater(mean_lightness(filled) - mean_lightness(empty), BOSS_HUD_FILL_GAP_MIN,
                           'a lit gem against a dark socket, not only a hue (measured 0.216)')
        self.assertEqual(filled.getchannel('A').tobytes(), empty.getchannel('A').tobytes(), 'the same silhouette, so the fill hides the socket')

    def test_the_palette_is_small_and_hard_edged(self):
        for name in BOSS_HUD_SHEETS:
            image = art.master(name)
            self.assertLess(len(set(p for p in image.get_flattened_data() if p[art.RGB_CHANNELS])), WORLD_PALETTE_SIZE, name)
            self.assertTrue(all(a in (0, OPAQUE_ALPHA) for a in image.getchannel('A').get_flattened_data()), name)


if __name__ == '__main__':
    unittest.main()
