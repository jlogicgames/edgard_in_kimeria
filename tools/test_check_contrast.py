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


if __name__ == '__main__':
    unittest.main()
