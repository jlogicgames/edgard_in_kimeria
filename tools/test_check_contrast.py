#!/usr/bin/env python3
"""Behavioural checks for the contrast tiers gate (tools/check_contrast.py, D76)."""
import unittest
import xml.etree.ElementTree as ET
from unittest import mock
from PIL import Image
import check_contrast as gate

SKY_BLUE = (74, 146, 184, 255)  # the plain sky of sky.png
INK = (0x0b, 0x08, 0x14, 255)
MUTED = (0x67, 0x7d, 0x62, 255)  # the recoloured bush body
# Halo fixtures (#345): a 4 px test halo with a lit 2 px centre, drawn at the centre of the 40 px background.
HALO_TEST_SIZE = 4
HALO_LIT_BOX = (1, 1, 3, 3)
HALO_GREY = (100, 100, 100, 255)
HALO_CENTRE = 20
HALO_TEST_ALPHA = 0.5
HALO_ADDED = 50  # 100 * 0.5 added to each channel
LIT_PIXEL = (19, 19)
UNLIT_PIXEL = (18, 18)
SKY_LIT = tuple(min(255, c + HALO_ADDED) for c in SKY_BLUE[:3]) + SKY_BLUE[3:]
OUTLINE = 2  # index of the outline in a gate.run() result row
WHITE_RIM = (250, 250, 250, 255)
WARM_HALO = (110, 100, 72, 255)
BIG_HALO_SIZE = 16
RIM_AT = 16
BRIGHTEST = 0.9
# The fairy (#338, D82): FairyGuide's LIGHT_SIZE, LIGHT_ALPHA_MIN and BODY_FRAMES; an ink edge on a dark ground is
# nearly invisible until her light lifts it.
FAIRY_LIGHT_SIZE = 20
FAIRY_LIGHT_ALPHA = 0.6
FAIRY_BODY_FRAMES = 14
FAIRY_GLINTS = 2  # big and small, each a row of the fairy report
INK_ON_DARK_MAX = 0.1
LIGHT_LIFT_MIN = 0.2
DOT_SPRITE = 3  # a 3 px sprite with one ink pixel in the middle
DOT = (1, 1)
DOT_SIZE = (1, 1)
LIGHT_PATCH = 5  # a light larger than the sprite, offset one pixel up and left
LIGHT_AQUA = (60, 120, 110, 255)
DARK_GROUND = 8
DARK = (20, 20, 30, 255)
SWEEP = (1, 0, 5, 1)  # x step, y from, y to, y step over the dark ground


def square(colour, size=6, contour=None):
    """A test fixture, not artwork: a filled square on a transparent margin, with an optional 1 px contour."""
    image = Image.new('RGBA', (size + 2, size + 2))
    image.paste(contour or colour, (1, 1, size + 1, size + 1))
    image.paste(colour, (2, 2, size, size))
    return image


def background(colour=SKY_BLUE):
    return Image.new('RGBA', (40, 40), colour)


class MetricTest(unittest.TestCase):
    def test_outline_is_the_lightness_step_at_the_edge(self):
        outline, body = gate.measure(square(INK), background(), 10, 10)
        expected = gate.oklab(SKY_BLUE)[0] - gate.oklab(INK)[0]
        self.assertAlmostEqual(outline, expected, places=6)
        self.assertGreater(body, outline)  # colour distance includes the lightness step

    def test_a_sprite_the_colour_of_its_background_has_no_contrast(self):
        outline, body = gate.measure(square(SKY_BLUE), background(), 10, 10)
        self.assertAlmostEqual(outline, 0.0, places=6)
        self.assertAlmostEqual(body, 0.0, places=6)

    def test_dimming_raises_contrast_of_a_light_sprite_against_a_dark_ground(self):
        dark = background((40, 30, 20, 255))
        light = square((230, 200, 120, 255))
        self.assertGreater(gate.measure(light, dark, 10, 10, 1.0)[0], gate.measure(light, dark, 10, 10, gate.GLOW_MIN)[0])

    def test_the_glow_floor_is_read_from_the_java_constant(self):
        self.assertEqual(gate.GLOW_MIN, 0.55)

    def test_the_pickup_halo_states_are_read_from_the_java_constants(self):
        # #345: at rest with the dimmest halo, at the top of the 2 px bob with the brightest
        self.assertEqual(gate.HALO_STATES, ((0, HALO_TEST_ALPHA), (gate.java_int(gate.COLLECTABLE_JAVA, 'BOB_HEIGHT'), BRIGHTEST)))

    def test_only_the_coin_goes_without_the_halo_as_in_the_java(self):
        self.assertEqual(gate.NO_HALO, {'Collectable:Coin'})
        self.assertIn('return type != ItemType.COIN;', (gate.ROOT / gate.COLLECTABLE_JAVA).read_text(encoding='utf-8'))

    def test_the_halo_is_added_light_only_where_it_is_drawn(self):
        halo = Image.new('RGBA', (HALO_TEST_SIZE, HALO_TEST_SIZE))
        halo.paste(HALO_GREY, HALO_LIT_BOX)
        lit = gate.with_halo(background(), halo, HALO_CENTRE, HALO_CENTRE, HALO_TEST_ALPHA)
        self.assertEqual(lit.getpixel(LIT_PIXEL), SKY_LIT)
        self.assertEqual(lit.getpixel(UNLIT_PIXEL), SKY_BLUE, 'the transparent part of the halo adds nothing')

    def test_a_halo_behind_a_light_rim_lowers_its_outline(self):
        # why the shipped halo's centre is empty: added light right outside a white rim eats the edge contrast
        rim = square(WHITE_RIM)
        halo = Image.new('RGBA', (BIG_HALO_SIZE, BIG_HALO_SIZE), WARM_HALO)
        plain, _ = gate.measure(rim, background(), RIM_AT, RIM_AT)
        lit, _ = gate.measure(rim, gate.with_halo(background(), halo, HALO_CENTRE, HALO_CENTRE, BRIGHTEST), RIM_AT, RIM_AT)
        self.assertLess(lit, plain)


class RuleTest(unittest.TestCase):
    def test_an_active_sprite_under_the_floor_fails_and_names_the_place(self):
        errors = gate.check_sprite('Bomb', [('forest-9', 100.0, gate.ACTIVE_OUTLINE_MIN - 0.01, 0.3)])
        self.assertEqual(len(errors), 1)
        self.assertIn('forest-9 x 100', errors[0])

    def test_an_active_sprite_on_the_floor_passes(self):
        self.assertEqual(gate.check_sprite('Bomb', [('forest-9', 1.0, gate.ACTIVE_OUTLINE_MIN, 0.3)]), [])

    def test_loud_decor_with_an_ink_contour_fails_every_decor_rule(self):
        loud = square((0x7a, 0x5a, 0xb0, 255), contour=INK)  # the old bush violet and ink
        with mock.patch.object(gate, 'frames', return_value=[loud]):
            errors = gate.check_sprite('Decor:bush', [('forest-9', 1.0, 0.45, 0.30)])
        text = '\n'.join(errors)
        for word in ('outline', 'body', 'chroma', 'ink'):
            self.assertIn(word, text)

    def test_muted_decor_passes(self):
        with mock.patch.object(gate, 'frames', return_value=[square(MUTED)]):
            self.assertEqual(gate.check_sprite('Decor:bush', [('forest-9', 1.0, 0.15, 0.15)]), [])

    def test_decor_too_close_to_the_background_fails_the_visibility_floor(self):
        with mock.patch.object(gate, 'frames', return_value=[square(MUTED)]):
            errors = gate.check_sprite('Decor:bush', [('forest-9', 1.0, 0.01, gate.DECOR_BODY_MIN - 0.01)])
        self.assertTrue(any('body' in e for e in errors))


class ClassificationTest(unittest.TestCase):
    def roots(self, objects):
        root = ET.fromstring('<map><objectgroup name="SpawnPoints">' + objects + '</objectgroup></map>')
        return {'forest-9': root}

    def test_an_unclassified_object_type_fails(self):
        errors = []
        list(gate.placements(errors, self.roots('<object type="NewTrap" x="0" y="0" width="16" height="16"/>')))
        self.assertEqual(len(errors), 1)
        self.assertIn("'NewTrap'", errors[0])

    def test_types_that_draw_nothing_need_no_tier(self):
        errors = []
        found = list(gate.placements(errors, self.roots('<object type="Checkpoint" x="0" y="0"/>'
                                                        '<object type="Torch" x="0" y="0"/>')))
        self.assertEqual((errors, found), ([], []))

    def test_keys_follow_what_the_game_draws(self):
        objects = ('<object type="Trigger" x="0" y="0"/>'
                   '<object type="Decor" x="0" y="0"><properties><property name="kind" value="lookalike"/></properties></object>'
                   '<object type="Actionable" x="0" y="0"><properties><property name="type" value="Wall"/>'
                   '<property name="movement" value="vertical"/></properties></object>'
                   '<object type="Actionable" x="0" y="0"><properties><property name="type" value="Torch"/></properties></object>')
        errors = []
        keys = [p[1] for p in gate.placements(errors, self.roots(objects))]
        self.assertEqual(errors, [])
        self.assertEqual(keys, ['Trigger', 'Decor:lookalike', 'Actionable:Wall'])


    # #363 (D88): a bough at (0, BOUGH_Y) with swing=left, and a mirrored one at RIGHT_X (DeadfallTrap's geometry).
    BOUGH_Y = 243.0
    RIGHT_X = 400.0
    PIVOT_X = 48.0
    RIGHT_PIVOT_X = 64.0
    FLOOR = 320.0
    VINE_CENTRE = 110.0
    VINE_W = 24.0
    VINE_H = 8.0
    LOG_W = 32.0

    def test_a_deadfall_is_measured_as_the_three_parts_it_draws_at_rest(self):
        objects = (f'<object type="Deadfall" x="0" y="{self.BOUGH_Y:g}" width="112" height="24"/>'
                   f'<object type="Deadfall" x="{self.RIGHT_X:g}" y="{self.BOUGH_Y:g}" width="112" height="24"><properties>'
                   '<property name="swing" value="right"/></properties></object>')
        errors = []
        found = list(gate.placements(errors, self.roots(objects)))
        self.assertEqual(errors, [])
        self.assertEqual([p[1] for p in found], ['Deadfall', 'Deadfall:log', 'Deadfall:vine',
                                                  'Deadfall-right', 'Deadfall:log-right', 'Deadfall:vine-right'])
        (_, _, bough_x, bough_y, _, _), (_, _, log_x, _, _, _), (_, _, vine_x, vine_y, _, _) = found[:len(found) // 2]
        _, _, mirrored_log_x, _, _, _ = found[len(found) // 2 + 1]
        self.assertEqual((bough_x, bough_y), (0.0, self.BOUGH_Y))
        self.assertEqual((vine_x, vine_y), (self.PIVOT_X - self.VINE_CENTRE - self.VINE_W / 2, self.FLOOR - self.VINE_H),
                         'the vine sprite stands on the floor before the pivot')
        self.assertGreater(log_x, self.PIVOT_X, 'the tied log hangs on the far side of the pivot')
        self.assertLess(mirrored_log_x + self.LOG_W, self.RIGHT_X + self.RIGHT_PIVOT_X, 'mirrored: the tied log is left of the pivot')
        mirrored, plain = gate.frames('Deadfall:vine-right')[0], gate.frames('Deadfall:vine')[0]
        self.assertEqual(mirrored.tobytes(), plain.transpose(gate.Image.Transpose.FLIP_LEFT_RIGHT).tobytes())


class ShippedArtTest(unittest.TestCase):
    def test_shipped_decor_is_inside_the_decor_tier(self):
        results, errors, _ = gate.run()
        decor = [e for e in errors if '(decor)' in e]
        self.assertEqual(decor, [])
        for key in ('Decor:bush', 'Decor:lookalike', 'HangingVine'):
            self.assertIn(key, results)

    def test_shipped_collectables_stay_active_over_their_halo(self):
        results, errors, _ = gate.run()
        self.assertEqual([e for e in errors if 'Collectable' in e], [])
        for key in ('Collectable:Coin', 'Collectable:Bat', 'Collectable:Mushroom'):
            self.assertGreaterEqual(min(r[OUTLINE] for r in results[key]), gate.ACTIVE_OUTLINE_MIN)


class FairyTest(unittest.TestCase):
    """#338 (D82): the fairy has no placement; her frames and glints are swept over every map."""
    def test_the_fairy_constants_are_read_from_the_java(self):
        self.assertEqual(gate.FAIRY_LIGHT_SIZE, FAIRY_LIGHT_SIZE)
        self.assertEqual(gate.FAIRY_LIGHT_ALPHA, FAIRY_LIGHT_ALPHA)
        self.assertEqual(gate.FAIRY_BODY_FRAMES, FAIRY_BODY_FRAMES)

    def test_added_light_lifts_an_ink_edge_on_a_dark_ground(self):
        sprite = Image.new('RGBA', (DOT_SPRITE, DOT_SPRITE)); sprite.paste(Image.new('RGBA', DOT_SIZE, INK), DOT)
        light = Image.new('RGBA', (LIGHT_PATCH, LIGHT_PATCH), LIGHT_AQUA)
        dark = Image.new('RGBA', (DARK_GROUND, DARK_GROUND), DARK)
        bare = gate.swept_outline(gate.edge_pairs(sprite, None, 0, 0, 0), dark, *SWEEP, DOT_SPRITE)[0]
        lit = gate.swept_outline(gate.edge_pairs(sprite, light, -1, -1, 1.0), dark, *SWEEP, DOT_SPRITE)[0]
        self.assertLess(bare, INK_ON_DARK_MAX)
        self.assertGreater(lit, bare + LIGHT_LIFT_MIN)

    def test_the_fairy_without_her_light_would_fail_and_with_it_passes(self):
        images = {name: gate.backgrounds(gate.tile_layer(ET.parse(gate.ROOT / f'assets/tiles/{name}.tmx').getroot())[0],
                                         gate.sky_strip()) for name in gate.MAPS}
        errors, report = gate.check_fairy(images)
        self.assertEqual(errors, [])
        self.assertEqual(len(report), gate.FAIRY_BODY_FRAMES + FAIRY_GLINTS)
        with mock.patch.object(gate, 'FAIRY_LIGHT_ALPHA', 0.0):
            errors, _ = gate.check_fairy(images)
        self.assertTrue(any('Fairy frame' in e for e in errors), 'her ink contour needs her light on dark tiles')


if __name__ == '__main__':
    unittest.main()
