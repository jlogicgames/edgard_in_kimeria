#!/usr/bin/env python3
"""Behavioural checks for the contrast tiers gate (tools/check_contrast.py, D76)."""
import json
import math
import unittest
import xml.etree.ElementTree as ET
from unittest import mock
import numpy as np
from PIL import Image
import check_contrast as gate

FOREST_BLUE = (74, 146, 184, 255)  # the forest maps' background (D92), #4a92b8
NIGHT_HEX = '#2c3f66'  # the night colour approved for #430's last part
NIGHT = (0x2c, 0x3f, 0x66)
NIGHT_GLOW_MIN = 0.80  # FungusTrap.GLOW_MIN since D92 (#430); 0.55 before
AT = 10  # where the test squares are drawn on the 40 px background
LAYER = 4  # a 4 px test tile layer with one ink pixel
INK_AT = (1, 1)
INK_BOX = INK_AT + (INK_AT[0] + 1, INK_AT[1] + 1)
INK = (0x0b, 0x08, 0x14, 255)
MUTED = (0x67, 0x7d, 0x62, 255)  # the recoloured bush body
NEAR_BLACK_GROUND = (4, 5, 9, 255)  # the Forest ground's depth (OKLab L about 0.03 to 0.12 in the game's composite)
DARK_DECOR = (30, 34, 44, 255)  # a dim slate body and its darker edge: quiet over that ground (D108)
DARK_DECOR_EDGE = (22, 26, 34, 255)
FAINT_DECOR = (8, 9, 14, 255)  # almost the ground itself: under the body floor
# Halo fixtures (#345): a 4 px test halo with a lit 2 px centre, drawn at the centre of the 40 px background.
HALO_TEST_SIZE = 4
HALO_LIT_BOX = (1, 1, 3, 3)
HALO_GREY = (100, 100, 100, 255)
HALO_CENTRE = 20
HALO_TEST_ALPHA = 0.5
HALO_ADDED = 50  # 100 * 0.5 added to each channel
LIT_PIXEL = (19, 19)
UNLIT_PIXEL = (18, 18)
FOREST_LIT = tuple(min(255, c + HALO_ADDED) for c in FOREST_BLUE[:gate.RGB]) + FOREST_BLUE[3:]
OUTLINE = 2  # index of the outline in a gate.run() result row
SOIL_BROWN = (0x22, 0x18, 0x0f, 255)  # terrain.grid key 2, the soil body
OLD_BARK = (0x25, 0x1a, 0x10, 255)  # the trunk's bark key m before D130: the soil's own brown
RED_BARK = (0x2e, 0x18, 0x13, 255)  # the bark key m since D130
PROP_CONTOUR = (0x2d, 0x26, 0x21)  # terrain.grid key A, the props' bark contour: decor tier (D120), not ground
WHITE_RIM = (250, 250, 250, 255)
WARM_HALO = (110, 100, 72, 255)
BIG_HALO_SIZE = 16
RIM_AT = 16
BRIGHTEST = 0.9
POOL_BOUNDS = (1.0, 2.0)  # SoftGlowFx.POOL_DIM and POOL_BRIGHT
# The fairy (#338, D82, D124): FairyGuide's SELF_RADIUS, GLOW_PAD and BODY_FRAMES, typed here independently of the Java; an ink edge on a
# dark ground is nearly invisible until her light lifts it.
FAIRY_SELF_RADIUS = 4.0
FAIRY_GLOW_PAD = 6.0
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
# The spider's route fixture (#483, D103): a 96 px wall face at x 400 (frame x 384, tops 224 to 288), a shelf lip at x 160 on
# y 208 whose thread ends at 300 (the last hanging frame every 8 px from 208 is at 296); forest-2's dark left map wall face.
SPIDER_WALL_X = 384.0
SPIDER_WALL_TOP = 224
SPIDER_WALL_LAST = 288
SPIDER_LIP_X = 160
SPIDER_SHELF = 208
SPIDER_LAST_HANG = 296
DARK_FACE_X = 16
OLD_CLIFF = (0x7e, 0x63, 0x39, 255)  # the retired third-party cliff tiles' mean colour beside forest-2's left wall (D112)
CLIFF_WIDTH = 48
# The snail fixture (#329, D106): floor top y 320, a block at x 180..196, the snail's range x 272..384 (frame tops 304).
SNAIL_FLOOR = 320
WOLF_FLOOR_BEYOND = 12  # px of floor past the wolf's authored range end: its lunge widens the body spots by that much and no more
MOB_X = 320  # a bat's or a mob's authored x in the #478 body tests, over a floor from 0 to MOB_FLOOR_END
MOB_OFF = 4  # tiles of range either side
MOB_FLOOR_END = 352  # the floor ends inside the range: a pit beyond
MOB_WALL_X = 272  # a wall's left face left of the mob, inside its range
FLOOR_DEPTH = 48.0  # how deep the test floor is
SNAIL_FRAME_TOP = 304
SNAIL_LEFT_END = 272
SNAIL_RIGHT_END = 384
SNAIL_BLOCK_FACE = 196
SWEEP = (1, 0, 5, 1)  # x step, y from, y to, y step over the dark ground
# The synthetic images of VectorizedTest: random colours, and alphas on both sides of ALPHA_MIN.
SEED = 76
SYNTHETIC_SPRITE = (7, 6)  # width, height
SYNTHETIC_GROUND = (12, 10)
SYNTHETIC_ALPHAS = (0, gate.ALPHA_MIN - 1, gate.ALPHA_MIN, gate.CHANNEL_MAX)
SYNTHETIC_SPRITES = 4
PAST_EDGE = 2  # positions this far past every side of the background: some, then none, of the sprite is on it
BRIGHTENING = 1.6  # a glow that clips the light channels at 255
SYNTHETIC_GLOWS = (1.0, gate.GLOW_MIN, BRIGHTENING)
SOLID_SIZE = 8  # an opaque sprite this large, SOLID_AT up and left, hides all of a SMALL_GROUND: body pixels, no edge
SMALL_GROUND = 3
SOLID_AT = -2
SWEEP_GROUND = (24, 20)  # a background wider and taller than the sprite the sweep places on it
SYNTHETIC_SWEEP = (3, 0, 9, 2)  # x step, y from, y to, y step; from 0 a pair above or left of its pixel wraps, as in PIL
SYNTHETIC_LIGHT_AT = (-2, -1)


def square(colour, size=6, contour=None):
    """A test fixture, not artwork: a filled square on a transparent margin, with an optional 1 px contour."""
    image = Image.new('RGBA', (size + 2, size + 2))
    image.paste(contour or colour, (1, 1, size + 1, size + 1))
    image.paste(colour, (2, 2, size, size))
    return image


def background(colour=FOREST_BLUE):
    return Image.new('RGBA', (40, 40), colour)


BODY_FLOOR = 320  # the synthetic map's ground line, and the ledge above it no monster reaches
BODY_LEDGE = 224
BEETLE_X = 320
BEETLE_OFF_NEG = 2  # tiles left of the spawn
BEETLE_OFF_POS = 3
NEAR_BLOCK = 8  # a dynamic block this far into a monster's range
FAR_BLOCK = 400  # and one out of every reach
DROP_BLOCK_RISE = 64  # a block this far over the floor, in a spider's drop column
WALL_FACE = 400  # left face of the wall a spider climbs in the wall-pose test
WIDE_FLOOR = 640
SPIDER_RANGE_X = 320
SPIDER_OFF_NEG = 2
SPIDER_OFF_POS = 3
BAND_BLOCK_INSET = 8  # a block starting this far inside the pounce band
TWO_PLACEMENTS = 2
SHORT_FLOOR_WIDTH = 400  # a floor ending 10 px past the authored body
WALL_BEYOND = 8  # a wall face this far past the authored body's left edge, inside the pounce and lunge reach
SHELF_LEFT = 96  # the shelf a spider walks, its lip at the right end
SHELF_RIGHT = 160
SHELF_TOP = 208
FLYING_X = 320  # a flying beetle's frame x
FLYING_OFF = 4  # its range either side, in tiles
FLYING_HEIGHT = 48  # its body bottom this far over the floor
LEDGE_X = 200       # a ledge across a flying beetle's line, left of where it is authored
LEDGE_WIDTH = 48
LEDGE_OFF = 20      # a line long enough to leave the ledge's climb behind, in tiles


class MetricTest(unittest.TestCase):
    def test_outline_is_the_lightness_step_at_the_edge(self):
        outline, body = gate.measure(square(INK), background(), AT, AT)
        expected = gate.oklab(FOREST_BLUE)[0] - gate.oklab(INK)[0]
        self.assertAlmostEqual(outline, expected, places=6)
        self.assertGreater(body, outline)  # colour distance includes the lightness step

    def test_a_sprite_the_colour_of_its_background_has_no_contrast(self):
        outline, body = gate.measure(square(FOREST_BLUE), background(), AT, AT)
        self.assertAlmostEqual(outline, 0.0, places=6)
        self.assertAlmostEqual(body, 0.0, places=6)

    def test_dimming_raises_contrast_of_a_light_sprite_against_a_dark_ground(self):
        dark = background((40, 30, 20, 255))
        light = square((230, 200, 120, 255))
        self.assertGreater(gate.measure(light, dark, 10, 10, 1.0)[0], gate.measure(light, dark, 10, 10, gate.GLOW_MIN)[0])

    def test_the_glow_floor_is_read_from_the_java_constant(self):
        self.assertEqual(gate.GLOW_MIN, NIGHT_GLOW_MIN)

    def test_the_pickup_light_bounds_are_read_from_the_java_constants(self):
        # #345, #544: measured at rest and at the top of the 2 px bob, each with the soft light's dimmest and brightest bound
        self.assertEqual(gate.GLOW_LEVELS, ('dim', 'bright'))
        self.assertEqual(gate.BOB_HEIGHT, gate.java_int(gate.COLLECTABLE_JAVA, 'BOB_HEIGHT'))
        self.assertEqual((gate.POOL_DIM, gate.POOL_BRIGHT), POOL_BOUNDS)
        look = gate.java_look('PICKUP')
        self.assertEqual(len(look), len(gate.LOOK_FIELDS))
        dim, bright = (gate.glow_layer(look, gate.GLOW_RADIUS, gate.GLOW_RADIUS, level) for level in gate.GLOW_LEVELS)
        self.assertEqual(dim.size, bright.size)
        centre = dim.width // 2
        self.assertGreater(sum(bright.getpixel((centre, centre))[:gate.RGB]), sum(dim.getpixel((centre, centre))[:gate.RGB]), 'the bright bound is brighter at the centre')

    def test_only_the_coin_goes_without_the_halo_as_in_the_java(self):
        self.assertEqual(gate.NO_HALO, {'Collectable:Coin'})
        self.assertIn('return type != ItemType.COIN;', (gate.ROOT / gate.COLLECTABLE_JAVA).read_text(encoding='utf-8'))

    def test_the_halo_is_added_light_only_where_it_is_drawn(self):
        halo = Image.new('RGBA', (HALO_TEST_SIZE, HALO_TEST_SIZE))
        halo.paste(HALO_GREY, HALO_LIT_BOX)
        lit = gate.with_halo(background(), halo, HALO_CENTRE, HALO_CENTRE, HALO_TEST_ALPHA)
        self.assertEqual(lit.getpixel(LIT_PIXEL), FOREST_LIT)
        self.assertEqual(lit.getpixel(UNLIT_PIXEL), FOREST_BLUE, 'the transparent part of the halo adds nothing')

    def test_a_halo_behind_a_light_rim_lowers_its_outline(self):
        # why the shipped halo's centre is empty: added light right outside a white rim eats the edge contrast
        rim = square(WHITE_RIM)
        halo = Image.new('RGBA', (BIG_HALO_SIZE, BIG_HALO_SIZE), WARM_HALO)
        plain, _ = gate.measure(rim, background(), RIM_AT, RIM_AT)
        lit, _ = gate.measure(rim, gate.with_halo(background(), halo, HALO_CENTRE, HALO_CENTRE, BRIGHTEST), RIM_AT, RIM_AT)
        self.assertLess(lit, plain)


def reference_measure(sprite, ground, x, y, glow=1.0):
    """gate.measure written pixel by pixel."""
    pairs, body = [], []
    for j in range(sprite.height):
        for i in range(sprite.width):
            if not gate.is_opaque(sprite, i, j) or not (0 <= x + i < ground.width and 0 <= y + j < ground.height):
                continue
            s = gate.oklab(sprite.getpixel((i, j)), glow)
            body.append(math.dist(s, gate.oklab(ground.getpixel((x + i, y + j)))))
            for u, v in gate.NEIGHBOURS:
                if not gate.is_opaque(sprite, i + u, j + v) and 0 <= x + i + u < ground.width and 0 <= y + j + v < ground.height:
                    pairs.append(abs(s[0] - gate.oklab(ground.getpixel((x + i + u, y + j + v)))[0]))
    if not body:
        return None, None
    return (sum(pairs) / len(pairs) if pairs else None), sum(body) / len(body)


def reference_chroma_p90(sprite, glow=1.0):
    """gate.chroma_p90 written pixel by pixel."""
    chroma = sorted(math.hypot(*gate.oklab(sprite.getpixel((i, j)), glow)[1:])
                    for j in range(sprite.height) for i in range(sprite.width) if gate.is_opaque(sprite, i, j))
    return chroma[max(0, int(gate.CHROMA_PERCENTILE * len(chroma)) - 1)]


def reference_swept_outline(pairs, ground, step_x, y_from, y_to, step_y, size, xs=None):
    """gate.swept_outline written pair by pair."""
    pixels, worst = ground.load(), (1.0, 0, 0)
    for x in (range(0, ground.width - size, step_x) if xs is None else xs):
        for y in range(y_from, min(y_to, ground.height - size), step_y):
            total = 0.0
            for lightness, dx, dy, added in pairs:
                lit = tuple(min(gate.CHANNEL_MAX, c + a) for c, a in zip(pixels[x + dx, y + dy][:gate.RGB], added))
                total += abs(lightness - gate.oklab(lit)[0])
            outline = total / len(pairs)
            if outline < worst[0]:
                worst = (outline, x, y)
    return worst


class VectorizedTest(unittest.TestCase):
    """The array code gives, to the last bit, what the pixel-by-pixel reference above gives."""

    def setUp(self):
        self.random = np.random.default_rng(SEED)

    def noise(self, size, alphas=SYNTHETIC_ALPHAS):
        """A test fixture, not artwork: random colours, each pixel's alpha drawn from `alphas`."""
        width, height = size
        pixels = self.random.integers(0, gate.CHANNEL_MAX, (height, width, gate.ALPHA + 1), dtype=np.uint8, endpoint=True)
        pixels[..., gate.ALPHA] = self.random.choice(alphas, (height, width))
        return Image.fromarray(pixels, 'RGBA')

    def assert_same(self, sprite, ground, x, y, glow):
        expected = reference_measure(sprite, ground, x, y, glow)
        found = gate.measure(sprite, ground, x, y, glow)
        self.assertEqual(found, expected, f'at ({x}, {y}) glow {glow}')
        self.assertEqual([type(v) for v in found], [type(v) for v in expected])
        return found

    def test_measure_matches_the_reference_wherever_the_sprite_lies(self):
        width, height = SYNTHETIC_SPRITE
        measured, unmeasured = 0, 0
        for _ in range(SYNTHETIC_SPRITES):
            sprite, ground = self.noise(SYNTHETIC_SPRITE), self.noise(SYNTHETIC_GROUND)
            for background_image in (ground, ground.convert('RGB')):
                for x in range(-width - PAST_EDGE, ground.width + PAST_EDGE):
                    for y in range(-height - PAST_EDGE, ground.height + PAST_EDGE):
                        for glow in SYNTHETIC_GLOWS:
                            body = self.assert_same(sprite, background_image, x, y, glow)[1]
                            measured, unmeasured = measured + (body is not None), unmeasured + (body is None)
        self.assertTrue(measured and unmeasured, 'both on the background and wholly off it')

    def test_measure_matches_the_reference_with_nothing_opaque_or_no_edge(self):
        ground = self.noise(SYNTHETIC_GROUND)
        hidden = self.noise(SYNTHETIC_SPRITE, alphas=(0, gate.ALPHA_MIN - 1))
        self.assertEqual(self.assert_same(hidden, ground, 1, 1, 1.0), (None, None))
        solid = self.noise((SOLID_SIZE, SOLID_SIZE), alphas=(gate.CHANNEL_MAX,))
        outline, body = self.assert_same(solid, self.noise((SMALL_GROUND, SMALL_GROUND)), SOLID_AT, SOLID_AT, 1.0)
        self.assertIsNone(outline)
        self.assertIsNotNone(body)
        self.assertIsNotNone(self.assert_same(solid, ground, 1, 1, 1.0)[0], 'the box edge of a solid sprite is an edge')

    def test_chroma_matches_the_reference(self):
        for _ in range(SYNTHETIC_SPRITES):
            sprite = self.noise(SYNTHETIC_SPRITE)
            for glow in SYNTHETIC_GLOWS:
                self.assertEqual(gate.chroma_p90(sprite, glow), reference_chroma_p90(sprite, glow))

    def test_the_sweep_matches_the_reference_with_and_without_light(self):
        size = max(SYNTHETIC_SPRITE)
        for _ in range(SYNTHETIC_SPRITES):
            sprite, light, ground = self.noise(SYNTHETIC_SPRITE), self.noise(SYNTHETIC_SPRITE), self.noise(SWEEP_GROUND)
            flat = Image.new('RGBA', SWEEP_GROUND, FOREST_BLUE)  # every position ties: the first one wins
            for lamp, under in ((None, ground), (light, ground), (None, flat)):
                pairs = gate.edge_pairs(sprite, lamp, *SYNTHETIC_LIGHT_AT, 1.0)
                for xs in (None, (0,), (1, 0)):
                    self.assertEqual(gate.swept_outline(pairs, under, *SYNTHETIC_SWEEP, size, xs=xs),
                                     reference_swept_outline(pairs, under, *SYNTHETIC_SWEEP, size, xs=xs))


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
        for word in ('outline', 'body', 'chroma'):
            self.assertIn(word, text)

    def test_dark_decor_on_a_dark_ground_passes_there_is_no_absolute_ink_floor(self):
        # D108: the decor band is relative to the background behind it; a near-black sprite on a near-black ground is quiet.
        dark = square(DARK_DECOR, contour=DARK_DECOR_EDGE)
        ground = background(NEAR_BLACK_GROUND)
        outline, body = gate.measure(dark, ground, AT, AT)
        self.assertLessEqual(outline, gate.DECOR_OUTLINE_MAX)
        self.assertGreaterEqual(body, gate.DECOR_BODY_MIN)
        self.assertLessEqual(body, gate.DECOR_BODY_MAX)
        with mock.patch.object(gate, 'frames', return_value=[dark]):
            self.assertEqual(gate.check_sprite('Decor:bush', [('forest-9', 1.0, outline, body)]), [])

    def test_decor_too_bright_for_its_dark_ground_still_fails(self):
        bright = square(MUTED)
        outline, body = gate.measure(bright, background(NEAR_BLACK_GROUND), AT, AT)
        with mock.patch.object(gate, 'frames', return_value=[bright]):
            errors = gate.check_sprite('Decor:bush', [('forest-9', 1.0, outline, body)])
        self.assertTrue(any('outline' in e for e in errors) and any('body' in e for e in errors), errors)

    def test_decor_too_close_to_a_dark_ground_fails_the_body_floor(self):
        faint = square(FAINT_DECOR)
        outline, body = gate.measure(faint, background(NEAR_BLACK_GROUND), AT, AT)
        self.assertLess(body, gate.DECOR_BODY_MIN)
        with mock.patch.object(gate, 'frames', return_value=[faint]):
            errors = gate.check_sprite('Decor:bush', [('forest-9', 1.0, outline, body)])
        self.assertTrue(any('body' in e for e in errors), errors)

    def test_muted_decor_passes(self):
        with mock.patch.object(gate, 'frames', return_value=[square(MUTED)]):
            self.assertEqual(gate.check_sprite('Decor:bush', [('forest-9', 1.0, 0.15, 0.15)]), [])

    def test_decor_too_close_to_the_background_fails_the_visibility_floor(self):
        with mock.patch.object(gate, 'frames', return_value=[square(MUTED)]):
            errors = gate.check_sprite('Decor:bush', [('forest-9', 1.0, 0.01, gate.DECOR_BODY_MIN - 0.01)])
        self.assertTrue(any('body' in e for e in errors))

# A test bramble placement: the box's width and the x of a left-root and a right-root box.
BRAMBLE_REACH = 64.0
BRAMBLE_LEFT_X = 100.0
BRAMBLE_RIGHT_X = 300.0


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

    def test_a_mirrored_decor_is_measured_as_drawn(self):
        # #635: Decor.render flips a `mirror=true` decor, so the gate measures the flipped frame, not the authored one.
        objects = ('<object type="Decor" x="0" y="0" width="208" height="160"><properties>'
                   '<property name="kind" value="crown-droop"/><property name="mirror" type="bool" value="true"/>'
                   '</properties></object>')
        errors = []
        keys = [p[1] for p in gate.placements(errors, self.roots(objects))]
        self.assertEqual((errors, keys), ([], ['Decor:crown-droop-mirror']))
        flipped, = gate.frames('Decor:crown-droop-mirror')
        drawn, = gate.frames('Decor:crown-droop')
        self.assertEqual(flipped.tobytes(), drawn.transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes())


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

    def test_a_bramble_is_measured_at_its_root_end_and_mirrored_for_a_right_root(self):
        """#364 (D89): the game draws the clump at the root end of the box, flipped when root=right."""
        reach, left_x, right_x = BRAMBLE_REACH, BRAMBLE_LEFT_X, BRAMBLE_RIGHT_X
        template = ('<object type="Bramble" x="{x}" y="296" width="{w}" height="24"><properties>'
                    '<property name="root" value="{root}"/></properties></object>')
        objects = template.format(x=left_x, w=reach, root='left') + template.format(x=right_x, w=reach, root='right')
        errors = []
        found = [(key, x, w) for _, key, x, _, w, _ in gate.placements(errors, self.roots(objects))]
        self.assertEqual(errors, [])
        clump = gate.BRAMBLE_CLUMP
        self.assertEqual(found, [('Bramble', left_x, clump), ('Bramble-right', right_x + reach - clump, clump)])
        left, right = gate.frames('Bramble')[0], gate.frames('Bramble-right')[0]
        self.assertEqual(list(right.getdata()), list(left.transpose(gate.Image.Transpose.FLIP_LEFT_RIGHT).getdata()))

    def test_a_snail_is_measured_along_its_range_and_its_gob_along_every_line(self):
        """#329 (D106): the snail at every 16 px of its range, and its gob every 8 px along each firing line up to the first solid."""
        root = ET.fromstring('<map><objectgroup name="Collisions"><object x="0" y="320" width="640" height="48"/>'
                             '<object x="180" y="288" width="16" height="32"/></objectgroup><objectgroup name="SpawnPoints">'
                             '<object type="Snail" x="320" y="304" width="24" height="16"><properties>'
                             '<property name="offNeg" value="3"/><property name="offPos" value="4"/></properties></object>'
                             '</objectgroup></map>')
        errors = []
        found = [(key, x, y) for _, key, x, y, _, _ in gate.placements(errors, {'forest-9': root})]
        self.assertEqual(errors, [])
        snails = [(x, y) for key, x, y in found if key == 'Snail']
        self.assertEqual(snails[0], (SNAIL_LEFT_END, SNAIL_FRAME_TOP), 'from its left end, on the floor')
        self.assertEqual(snails[-1], (SNAIL_RIGHT_END, SNAIL_FRAME_TOP), 'to its right end')
        gobs = [(x, y) for key, x, y in found if key == 'Snail:gob']
        self.assertTrue(all(y == SNAIL_FLOOR - gate.SNAIL_GOB_HEIGHT - gate.SNAIL_GOB / 2 for _, y in gobs), 'the drawn gob centred over the line')
        self.assertGreaterEqual(min(x for x, _ in gobs), SNAIL_BLOCK_FACE, 'the block stops the line going left')
        self.assertLessEqual(max(x for x, _ in gobs), SNAIL_RIGHT_END + gate.SNAIL_MOUTH_X + gate.SNAIL_GOB_RANGE, 'a line going right ends at its range')

    def test_a_spider_route_is_measured_on_its_floors_its_wall_and_its_thread(self):
        """#483 (D103): a polyline spider is measured in every pose along its route, where Spider draws it."""
        root = ET.fromstring('<map><objectgroup name="Collisions"><object x="0" y="320" width="640" height="48"/>'
                             '<object x="400" y="224" width="224" height="96"/><object x="96" y="208" width="64" height="16"/>'
                             '</objectgroup><objectgroup name="SpawnPoints">'
                             '<object type="Spider" x="320" y="320"><polyline points="0,0 80,0 80,-96 208,-96"/></object>'
                             '<object type="Spider" x="112" y="208"><polyline points="0,0 48,0 48,112"/></object>'
                             '</objectgroup></map>')
        errors = []
        found = [(key, x, y) for _, key, x, y, _, _ in gate.placements(errors, {'forest-9': root})]
        self.assertEqual(errors, [])
        keys = {key for key, _, _ in found}
        self.assertEqual(keys, {'Spider', 'Spider:wall', 'Spider:wall-down', 'Spider:hang', 'Spider:silk'})
        walls = [(x, y) for key, x, y in found if key == 'Spider:wall']
        self.assertEqual(walls[0], (SPIDER_WALL_X, SPIDER_WALL_TOP), 'the wall frame on the open side of the face, from the wall top')
        self.assertEqual(walls[-1], (SPIDER_WALL_X, SPIDER_WALL_LAST), 'down to the wall foot')
        hangs = [(x, y) for key, x, y in found if key == 'Spider:hang']
        self.assertEqual(hangs[0], (SPIDER_LIP_X + gate.spider_thread_out(True) - gate.SPIDER_HANG_THREAD_X, SPIDER_SHELF),
                         'hanging 6 px out from the lip')
        self.assertEqual(hangs[-1][1], SPIDER_LAST_HANG, 'down the column every 8 px to the thread end (300)')
        flipped, upright = gate.frames('Spider:wall-down')[0], gate.frames('Spider:wall')[0]
        self.assertEqual(flipped.tobytes(), upright.transpose(gate.Image.Transpose.FLIP_TOP_BOTTOM).tobytes())

    def test_a_newly_placed_beetle_leaves_its_body_only_along_its_range(self):
        """D108: a body is measured where its monster can die, derived from the placement: a new beetle is swept along its range on
        its floor, and a ledge it never reaches is not."""
        root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="640" height="48"/>'
                             f'<object x="400" y="{BODY_LEDGE}" width="224" height="16"/></objectgroup><objectgroup name="SpawnPoints">'
                             f'<object type="Beetle" x="{BEETLE_X}" y="{BODY_FLOOR - gate.TILE}" width="24" height="16"><properties>'
                             f'<property name="offNeg" value="{BEETLE_OFF_NEG}"/><property name="offPos" value="{BEETLE_OFF_POS}"/>'
                             '</properties></object></objectgroup></map>')
        spots = [(key, x, line) for key, _, x, line in gate.body_spots({'forest-9': root})]
        self.assertEqual({key for key, _, _ in spots}, {'Beetle:body'})
        self.assertEqual({line for _, _, line in spots}, {BODY_FLOOR}, 'on its own floor, never on the ledge')
        xs = [x for _, x, _ in spots]
        self.assertEqual((min(xs), max(xs)), (BEETLE_X - BEETLE_OFF_NEG * gate.TILE, BEETLE_X + BEETLE_OFF_POS * gate.TILE))

    def _beetle_map(self, extra):
        return ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="640" height="48"/></objectgroup>'
                             f'<objectgroup name="SpawnPoints"><object type="Beetle" x="{BEETLE_X}" y="{BODY_FLOOR - gate.TILE}" width="24" height="16">'
                             f'<properties><property name="offNeg" value="{BEETLE_OFF_NEG}"/><property name="offPos" value="{BEETLE_OFF_POS}"/>'
                             f'</properties></object>{extra}</objectgroup></map>')

    def test_a_dynamic_block_on_a_monsters_range_fails_the_body_spots(self):
        """body_spots reads static Collisions only: a falling platform, escalator, deadfall or quicksand in a range fails loudly."""
        for kind in gate.DYNAMIC_TYPES:
            root = self._beetle_map(f'<object type="{kind}" x="{BEETLE_X + NEAR_BLOCK}" y="{BODY_FLOOR}" width="32" height="16"/>')
            with self.assertRaises(SystemExit, msg=kind) as caught:
                list(gate.body_spots({'forest-9': root}))
            self.assertIn(kind, str(caught.exception))
        far = self._beetle_map(f'<object type="Escalator" x="{BEETLE_X + FAR_BLOCK}" y="{BODY_FLOOR}" width="32" height="16"/>')
        self.assertTrue(list(gate.body_spots({'forest-9': far})), 'a block out of reach changes nothing')

    def test_a_dynamic_block_in_a_spiders_drop_column_fails_the_body_spots(self):
        root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="640" height="48"/>'
                             f'<object x="{SHELF_LEFT}" y="{SHELF_TOP}" width="{SHELF_RIGHT - SHELF_LEFT}" height="16"/></objectgroup>'
                             '<objectgroup name="SpawnPoints"><object type="Spider" x="112" y="208"><polyline points="0,0 48,0 48,112"/></object>'
                             f'<object type="FallingPlatform" x="{SHELF_RIGHT + NEAR_BLOCK}" y="{BODY_FLOOR - DROP_BLOCK_RISE}" width="32" height="16"/>'
                             '</objectgroup></map>')
        with self.assertRaises(SystemExit):
            list(gate.body_spots({'forest-9': root}))

    def test_the_shipped_maps_have_no_dynamic_block_in_a_body_spot(self):
        self.assertTrue(list(gate.body_spots(gate.map_roots())))

    def test_a_placed_body_kind_with_no_result_fails_the_run(self):
        """A BODY_SWEEP monster with a placement and no measured body is a gap, not a pass."""
        with mock.patch.object(gate, 'placements', return_value=[]), mock.patch.object(gate, 'body_sweep', return_value=iter(())), \
                mock.patch.object(gate, 'check_fairy', return_value=([], {})), mock.patch.object(gate, 'tile_report', return_value=({}, [])):
            _, errors, _ = gate.run()
        self.assertTrue(any(e.startswith('Beetle:body') and 'no body was measured' in e for e in errors), errors)

    def test_the_boss_bar_frame_is_measured_over_the_boss_room_and_an_ink_frame_fails(self):
        """#543: the frame sits on the sky and the tree band alike; a near-black outline sank into the sky (0.101), the bone rim does not."""
        import tempfile
        from pathlib import Path
        roots = gate.map_roots()
        scenes = {name: gate.Scene(name, root) for name, root in roots.items() if name == 'forest-2'}
        errors, report = gate.check_boss_hud(scenes, roots)
        self.assertEqual([], errors)
        self.assertGreaterEqual(report[gate.BOSS_HUD_NAME], gate.ACTIVE_OUTLINE_MIN)
        with tempfile.TemporaryDirectory() as folder:
            ink = Path(folder) / 'frame.png'
            Image.new('RGBA', Image.open(gate.ROOT / gate.BOSS_HUD_FRAME).size, 'black').save(ink)
            with mock.patch.object(gate, 'BOSS_HUD_FRAME', str(ink)):
                errors, _ = gate.check_boss_hud(scenes, roots)
        self.assertTrue(any(e.startswith(gate.BOSS_HUD_NAME) and '(active)' in e for e in errors), errors)

    def test_a_map_with_two_non_scenery_tile_layers_fails_loudly(self):
        """The game overwrites every non-scenery layer into the play grid, last wins; the gate composites only Background."""
        layer = '<layer name="{}" width="1" height="1"{}><data encoding="csv">0</data></layer>'
        head = '<map width="1" height="1"><tileset firstgid="1" source="ForestTerrain.tsx"/>'
        two = ET.fromstring(head + layer.format('Background', '') + layer.format('Extra', '') + '</map>')
        with self.assertRaises(ValueError):
            gate.tile_layer(two, 'broken')
        for scenery in (' parallaxx="0.5"', ' parallaxy="0.5"'):
            self.assertTrue(gate.is_scenery(ET.fromstring(layer.format('s', scenery))))
        ok = ET.fromstring(head + layer.format('Background', '') + layer.format('sky', ' parallaxx="0.5"')
                           + '<layer name="moon" width="1" height="1"><properties><property name="scenery" value="true"/></properties>'
                           '<data encoding="csv">0</data></layer></map>')
        self.assertTrue(gate.is_scenery(ok.findall('layer')[-1]))
        self.assertEqual([l.get('name') for l in ok.findall('layer') if not gate.is_scenery(l)], ['Background'])

    def test_a_spider_wall_pose_is_swept_on_the_open_side_and_dropped_to_the_floor(self):
        """D108: every wall pose of a route (down the wall every 8 px) is a spot, set on the open side and dropped to the floor below."""
        root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{WIDE_FLOOR}" height="48"/>'
                             f'<object x="{WALL_FACE}" y="{BODY_LEDGE}" width="224" height="96"/></objectgroup><objectgroup name="SpawnPoints">'
                             f'<object type="Spider" x="{SPIDER_RANGE_X}" y="{BODY_FLOOR}"><polyline points="0,0 80,0 80,-96 208,-96"/></object>'
                             '</objectgroup></map>')
        spots = list(gate.body_spots({'forest-9': root}))
        right_edge = lambda x: x + gate.SPIDER_BODY_X + gate.SPIDER_BODY_WIDTH
        wall = [x for _, _, x, line in spots if line == BODY_FLOOR and right_edge(x) <= WALL_FACE]
        self.assertTrue(wall, 'a wall pose falls to the ground on the open side of the face')
        self.assertTrue(all(right_edge(x) <= WALL_FACE for x in wall))

    def test_a_range_spider_is_widened_by_the_pounce_reach_while_floor_lasts(self):
        legs = lambda width: ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{width}" height="48"/>'
                                           f'</objectgroup><objectgroup name="SpawnPoints"><object type="Spider" x="{SPIDER_RANGE_X}" '
                                           f'y="{BODY_FLOOR - gate.SPIDER_BODY_FLOOR}"><properties><property name="offNeg" value="{SPIDER_OFF_NEG}"/>'
                                           f'<property name="offPos" value="{SPIDER_OFF_POS}"/></properties></object></objectgroup></map>')
        xs = lambda root: [x for _, _, x, _ in gate.body_spots({'forest-9': root})]
        wide = xs(legs(WIDE_FLOOR))
        self.assertEqual(min(wide), SPIDER_RANGE_X - SPIDER_OFF_NEG * gate.TILE - gate.SPIDER_POUNCE_REACH)
        self.assertEqual(max(wide), SPIDER_RANGE_X + SPIDER_OFF_POS * gate.TILE + gate.SPIDER_POUNCE_REACH)
        authored_hi = SPIDER_RANGE_X + SPIDER_OFF_POS * gate.TILE
        body_end = gate.SPIDER_BODY_X + gate.SPIDER_BODY_WIDTH
        short = xs(legs(authored_hi + body_end))
        self.assertEqual(max(short), authored_hi, 'floor ends under the authored body: no widening to the right')
        partial = xs(legs(SHORT_FLOOR_WIDTH))
        self.assertEqual(max(partial), SHORT_FLOOR_WIDTH - body_end, 'the pounce stops where the body would leave the floor')

    def test_a_solid_stops_the_pounce_and_lunge_widening_but_a_one_way_platform_does_not(self):
        """#594: Spider.solidAt and Wolf.solidAt stop the leap against a solid, so no body is measured inside or past a wall."""
        authored_lo = SPIDER_RANGE_X - SPIDER_OFF_NEG * gate.TILE
        body_x = {'Spider': gate.SPIDER_BODY_X, 'Wolf': gate.WOLF_BODY_X}
        frame_y = {'Spider': BODY_FLOOR - gate.SPIDER_BODY_FLOOR, 'Wolf': BODY_FLOOR - gate.WOLF_FRAME_HEIGHT}
        reach = {'Spider': gate.SPIDER_POUNCE_REACH, 'Wolf': gate.WOLF_LUNGE_REACH}
        for kind in body_x:
            wall_face = authored_lo + body_x[kind] - WALL_BEYOND  # the right face of a block standing on the floor, left of the range

            def lefts(block_type):
                root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{WIDE_FLOOR}" height="48"/>'
                                     f'<object{block_type} x="{wall_face - gate.TILE}" y="{BODY_FLOOR - gate.TILE}" width="{gate.TILE}" height="{gate.TILE}"/>'
                                     f'</objectgroup><objectgroup name="SpawnPoints"><object type="{kind}" x="{SPIDER_RANGE_X}" y="{frame_y[kind]}">'
                                     f'<properties><property name="offNeg" value="{SPIDER_OFF_NEG}"/></properties></object></objectgroup></map>')
                return [x for _, _, x, _ in gate.body_spots({'forest-9': root})]
            self.assertEqual(min(lefts('')) + body_x[kind], wall_face, f'{kind}: the body stops against the wall')
            self.assertEqual(min(lefts(' type="Platform"')), authored_lo - reach[kind], f'{kind}: a one-way platform is not in the way')

    def test_a_wolfs_body_is_swept_along_its_range_widened_by_the_lunge_reach_while_floor_lasts(self):
        """#331 (D115): a wolf killed in its lunge slides on to the line's end over floor, so its body is measured out to the reach."""
        legs = lambda width: ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{width}" height="48"/>'
                                           f'</objectgroup><objectgroup name="SpawnPoints"><object type="Wolf" x="{SPIDER_RANGE_X}" '
                                           f'y="{BODY_FLOOR - gate.WOLF_FRAME_HEIGHT}" width="48" height="24"><properties>'
                                           f'<property name="offNeg" value="{SPIDER_OFF_NEG}"/><property name="offPos" value="{SPIDER_OFF_POS}"/>'
                                           '</properties></object></objectgroup></map>')
        spots = lambda root: [(key, x, line) for key, _, x, line in gate.body_spots({'forest-9': root})]
        wide = spots(legs(WIDE_FLOOR))
        self.assertEqual({key for key, _, _ in wide}, {'Wolf:body'})
        self.assertEqual({line for _, _, line in wide}, {BODY_FLOOR})
        xs = [x for _, x, _ in wide]
        self.assertEqual(min(xs), SPIDER_RANGE_X - SPIDER_OFF_NEG * gate.TILE - gate.WOLF_LUNGE_REACH)
        self.assertEqual(max(xs), SPIDER_RANGE_X + SPIDER_OFF_POS * gate.TILE + gate.WOLF_LUNGE_REACH)
        body_end = gate.WOLF_BODY_X + gate.WOLF_BODY_WIDTH
        authored_hi = SPIDER_RANGE_X + SPIDER_OFF_POS * gate.TILE
        self.assertEqual(max(x for _, x, _ in spots(legs(authored_hi + body_end + WOLF_FLOOR_BEYOND))), authored_hi + WOLF_FLOOR_BEYOND, 'the lunge stops where the body would leave the floor')

    def test_a_flying_beetle_is_measured_along_its_line_and_where_it_dives_and_falls(self):
        """#330 (D145): the live frames every step of the line and at every dive end with floor under it; the body wherever it
        can be dropped, out to the commit reach past either end, never over a pit. No shipped map places one yet."""
        fw, fh = gate.FLYING_FRAME
        bottom_in_frame = gate.java_number(gate.FLYING_JAVA, 'BODY_Y') + gate.java_number(gate.FLYING_JAVA, 'BODY_HEIGHT')
        reach = gate.java_number(gate.FLYING_JAVA, 'COMMIT_REACH')
        x, y = FLYING_X, BODY_FLOOR - FLYING_HEIGHT - bottom_in_frame
        root = lambda width: ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{width}" height="48"/>'
                                           f'</objectgroup><objectgroup name="SpawnPoints"><object type="FlyingBeetle" x="{x}" y="{y}" '
                                           f'width="{fw}" height="{fh}"><properties><property name="offNeg" value="{FLYING_OFF}"/>'
                                           f'<property name="offPos" value="{FLYING_OFF}"/></properties></object></objectgroup></map>')
        low, high = x - FLYING_OFF * gate.TILE, x + FLYING_OFF * gate.TILE
        props = {'offNeg': str(FLYING_OFF), 'offPos': str(FLYING_OFF)}
        parts = list(gate.flying_beetle_parts(root(WIDE_FLOOR), x, y, props))
        self.assertEqual({(w, h) for _, _, _, w, h in parts}, {(fw, fh)})
        line = [left for _, left, top, _, _ in parts if top == y]
        self.assertEqual((min(line), max(line)), (low, high))
        dives = [left for _, left, top, _, _ in parts if top == BODY_FLOOR - fh]
        self.assertEqual((min(dives), max(dives)), (low - reach, high + reach))
        spots = [(key, left, floor) for key, _, left, floor in gate.body_spots({'forest-9': root(WIDE_FLOOR)})]
        self.assertEqual({key for key, _, _ in spots}, {'FlyingBeetle:body'})
        self.assertEqual({floor for _, _, floor in spots}, {BODY_FLOOR})
        lefts = [left for _, left, _ in spots]
        self.assertEqual((min(lefts), max(lefts)), (low - reach, high + reach))
        over_pit = [left for _, _, left, _ in gate.body_spots({'forest-9': root(low)})]
        body_x = gate.java_number(gate.FLYING_JAVA, 'BODY_X')
        self.assertTrue(over_pit and all(left + body_x < low for left in over_pit), 'nothing lies where there is no floor')

    def test_a_flying_beetle_is_measured_over_a_ledge_its_line_crosses(self):
        """D146: along the route the frame is lifted so the body is CLIMB_HEIGHT over the ledge, sinking beside it one px a px;
        a body dropped there lies on the ledge."""
        bottom_in_frame = gate.java_number(gate.FLYING_JAVA, 'BODY_Y') + gate.java_number(gate.FLYING_JAVA, 'BODY_HEIGHT')
        climb = gate.java_number(gate.FLYING_JAVA, 'CLIMB_HEIGHT')
        x, line = FLYING_X, BODY_FLOOR - FLYING_HEIGHT
        y = line - bottom_in_frame
        root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{WIDE_FLOOR}" height="48"/>'
                             f'<object type="Platform" x="{LEDGE_X}" y="{line}" width="{LEDGE_WIDTH}" height="16"/></objectgroup></map>')
        props = {'offNeg': str(LEDGE_OFF), 'offPos': '0'}
        route = gate.flying_route(gate.landings(root), x, y, props)
        self.assertEqual(route(LEDGE_X), line - climb)
        self.assertEqual(route(x - LEDGE_OFF * gate.TILE), line, 'back on its line far from the ledge')
        tops = {left: top for _, left, top, _, _ in gate.flying_beetle_parts(root, x, y, props) if top < y}
        self.assertEqual(min(tops.values()), y - climb)
        spots = gate.flying_beetle_body_spots(gate.landings(root), x, y, props)
        self.assertIn(line, {floor for _, floor in spots}, 'a body over the ledge lies on it')

    def test_a_bat_body_is_dropped_from_its_line_and_none_lies_over_a_pit(self):
        """#478: a bat falls from any point of its line, across or up and down; the body rests a pixel above its frame's bottom."""
        boxes = [(0.0, float(BODY_FLOOR), float(MOB_FLOOR_END), FLOOR_DEPTH)]
        x, y, reach = MOB_X, BODY_LEDGE, MOB_OFF * gate.TILE
        spots = gate.bat_body_spots(boxes, x, y, {'offNeg': str(MOB_OFF), 'offPos': str(MOB_OFF)})
        self.assertEqual({line for _, line in spots}, {BODY_FLOOR + gate.BAT_FRAME - gate.BAT_FLOOR_IN_FRAME})
        lefts = [left for left, _ in spots]
        self.assertEqual(min(lefts), x - reach)
        self.assertLess(max(lefts), MOB_FLOOR_END, 'nothing lies over the pit')
        self.assertGreater(max(lefts) + gate.BAT_FRAME, MOB_FLOOR_END - gate.BODY_STEP)
        upright = gate.bat_body_spots(boxes, x, y, {'offPos': str(MOB_OFF), 'isVertical': 'true'})
        self.assertEqual({left for left, _ in upright}, {x}, 'a bat flying up and down falls in one column')
        step = boxes + [(float(x), float(y + gate.TILE // 2), float(gate.TILE), float(gate.TILE))]
        raised = gate.bat_body_spots(step, x, y, {}, solids=step)
        self.assertEqual({line for _, line in raised}, {y + gate.TILE // 2 + gate.BAT_FRAME - gate.BAT_FLOOR_IN_FRAME},
                         'a solid it overlaps as it dies, top face nearer, takes the body on its top')
        above = boxes + [(float(x), float(y - gate.TILE // 2), float(gate.TILE), float(gate.TILE))]
        under = gate.bat_body_spots(above, x, y, {}, solids=above)
        self.assertEqual({line for _, line in under}, {BODY_FLOOR + gate.BAT_FRAME - gate.BAT_FLOOR_IN_FRAME},
                         'underside nearer: it leaves below and falls on')

    def test_a_mob_body_lies_where_it_can_walk_up_to_a_wall(self):
        """#478: a mob runs until its middle is at the hero's, inside the range, and stops at a solid."""
        body_x, _, width, height = gate.hitbox_of('RedMob')
        body_y = gate.hitbox_of('RedMob')[1]
        y = BODY_FLOOR - body_y - height
        root = lambda wall: ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="640" height="48"/>{wall}'
                                          f'</objectgroup><objectgroup name="SpawnPoints"><object type="RedMob" x="{MOB_X}" y="{y}" width="48" height="32">'
                                          f'<properties><property name="offNeg" value="{MOB_OFF}"/><property name="offPos" value="{MOB_OFF}"/>'
                                          '</properties></object></objectgroup></map>')
        spots = [(key, x, line) for key, _, x, line in gate.body_spots({'forest-9': root('')})]
        self.assertEqual({(key, line) for key, _, line in spots}, {('RedMob:body', BODY_FLOOR)})
        middle, reach = body_x + width / 2, MOB_OFF * gate.TILE
        self.assertEqual((min(x for _, x, _ in spots), max(x for _, x, _ in spots)), (MOB_X - reach - middle, MOB_X + reach - middle))
        walled = [x for _, _, x, _ in gate.body_spots({'forest-9': root(f'<object type="Wall" x="{MOB_WALL_X}" y="{BODY_LEDGE}" width="16" height="96"/>')})]
        self.assertGreaterEqual(min(walled) + body_x, MOB_WALL_X + gate.TILE, 'it stops at the wall')

    def test_a_dynamic_block_continuing_a_spiders_floor_in_the_pounce_band_fails(self):
        root = lambda kind: ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="{WIDE_FLOOR}" height="48"/>'
                                          f'</objectgroup><objectgroup name="SpawnPoints"><object type="Spider" x="{SPIDER_RANGE_X}" '
                                          f'y="{BODY_FLOOR - gate.SPIDER_BODY_FLOOR}"><properties><property name="offPos" value="{SPIDER_OFF_POS}"/>'
                                          '</properties></object><object type="' + kind + f'" x="{SPIDER_RANGE_X + SPIDER_OFF_POS * gate.TILE + gate.SPIDER_POUNCE_REACH - BAND_BLOCK_INSET}" '
                                          f'y="{BODY_FLOOR}" width="32" height="16"/></objectgroup></map>')
        with self.assertRaises(SystemExit):
            list(gate.body_spots({'forest-9': root('FallingPlatform')}))

    def test_each_placed_monster_needs_its_own_measured_body(self):
        two = ET.fromstring('<map><objectgroup name="SpawnPoints"><object type="Beetle" x="0" y="0"/><object type="Beetle" x="64" y="0"/></objectgroup></map>')
        self.assertEqual(len(gate.placed_body_placements({'forest-9': two})), TWO_PLACEMENTS)

    def test_a_spider_body_lies_on_its_floor_or_drops_from_its_thread(self):
        """D108: a spider's body lies along its floor leg (the pounce never leaves that floor) or falls from its thread to the floor below."""
        root = ET.fromstring(f'<map><objectgroup name="Collisions"><object x="0" y="{BODY_FLOOR}" width="640" height="48"/>'
                             f'<object x="{SHELF_LEFT}" y="{SHELF_TOP}" width="{SHELF_RIGHT - SHELF_LEFT}" height="16"/></objectgroup>'
                             '<objectgroup name="SpawnPoints"><object type="Spider" x="112" y="208"><polyline points="0,0 48,0 48,112"/>'
                             '</object></objectgroup></map>')
        spots = [(x, line) for _, _, x, line in gate.body_spots({'forest-9': root})]
        shelf = [x for x, line in spots if line == SHELF_TOP]
        self.assertTrue(shelf)
        self.assertGreaterEqual(min(shelf) + gate.SPIDER_BODY_X, SHELF_LEFT, 'the pounce stops where the shelf ends')
        self.assertLessEqual(max(shelf) + gate.SPIDER_BODY_X + gate.SPIDER_BODY_WIDTH, SHELF_RIGHT)
        dropped = [x for x, line in spots if line == BODY_FLOOR]
        self.assertTrue(dropped, 'killed on its thread, the body falls to the ground')
        self.assertTrue(all(x + gate.SPIDER_BODY_X >= SHELF_RIGHT for x in dropped), 'on the open side of the lip')
        self.assertEqual({line for _, line in spots}, {SHELF_TOP, BODY_FLOOR})

    def test_the_python_route_matches_the_shared_fixture(self):
        """#483: spider_route re-implements Spider.Route.trace; both are pinned to one fixture, so the two cannot drift."""
        tiles = gate.ROOT / 'core/src/test/resources/tiles'
        fixture = json.loads((tiles / 'spider-routes.json').read_text(encoding='utf-8'))
        maps = [name for name in fixture if not name.startswith('_')]
        self.assertTrue(maps)
        for name in maps:
            root = ET.parse(tiles / f'{name}.tmx').getroot()
            spider = next(o for o in root.iter('object') if o.get('type') == 'Spider')
            route = gate.spider_route(root, float(spider.get('x')), float(spider.get('y')), spider.find('polyline').get('points'))
            self.assertEqual(route, fixture[name], name)

    def test_a_spider_wall_on_a_dark_face_fails_the_placement(self):
        """#483 (D103): the placement rule. A wall face as light as the spider's body fails it: forest-2's left map wall (face x 16)
        stood against the third-party cliff (its mean colour `7e6339`, L 0.52) until #236 retired that sheet (D112)."""
        root = gate.map_roots()['forest-2']
        image = gate.composite(gate.Image.new('RGBA', gate.tile_layer(root)[0].size), gate.map_background(root, 'forest-2'))
        image.paste(gate.Image.new('RGBA', (CLIFF_WIDTH, SPIDER_WALL_LAST), OLD_CLIFF), (DARK_FACE_X, 0))
        results = [('forest-2', DARK_FACE_X, *gate.measure(frame, image, DARK_FACE_X, top)) for frame in gate.frames('Spider:wall-left')
                   for top in range(DARK_FACE_X, SPIDER_WALL_LAST, gate.SPIDER_STEP)]
        errors = gate.check_sprite('Spider:wall-left', results)
        self.assertEqual(len(errors), 1)
        self.assertIn('forest-2 x 16', errors[0])


class ShippedArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shipped = gate.run()  # the unpatched run over the shipped files; the tests below only read it

    def test_shipped_decor_is_inside_the_decor_tier(self):
        results, errors, _ = self.shipped
        decor = [e for e in errors if '(decor)' in e]
        self.assertEqual(decor, [])
        for key in ('Decor:bush', 'Decor:lookalike', 'HangingVine'):
            self.assertIn(key, results)

    def test_shipped_collectables_stay_active_over_their_halo(self):
        results, errors, _ = self.shipped
        self.assertEqual([e for e in errors if 'Collectable' in e], [])
        for key in ('Collectable:Coin', 'Collectable:Bat', 'Collectable:Mushroom'):
            self.assertGreaterEqual(min(r[OUTLINE] for r in results[key]), gate.ACTIVE_OUTLINE_MIN)


class TrunkSoilTest(unittest.TestCase):
    """D130 (#555): a trunk kind stays TRUNK_SOIL_MIN from the ground's colours, so a tree never reads as a pillar of ground."""
    GROUND = [gate.oklab(SOIL_BROWN)]

    def test_bark_in_the_soils_own_brown_fails(self):
        self.assertLess(gate.soil_distance(square(OLD_BARK), self.GROUND), gate.TRUNK_SOIL_MIN)
        with mock.patch.object(gate, 'frames', return_value=[square(OLD_BARK)]):
            errors, _ = gate.check_trunk_soil(self.GROUND)
        self.assertTrue(errors and all('reads as ground' in e for e in errors), errors)

    def test_red_brown_bark_passes(self):
        with mock.patch.object(gate, 'frames', return_value=[square(RED_BARK)]):
            errors, distances = gate.check_trunk_soil(self.GROUND)
        self.assertEqual(errors, [])
        self.assertTrue(all(d >= gate.TRUNK_SOIL_MIN for d in distances.values()))

    def test_the_ground_leaves_out_the_decor_tier_props(self):
        ground = gate.terrain_colours()
        self.assertIn(gate.oklab(SOIL_BROWN[:gate.RGB]), ground)
        self.assertNotIn(gate.oklab(PROP_CONTOUR), ground)

    def test_shipped_trunks_are_apart_from_the_ground(self):
        errors, distances = gate.check_trunk_soil()
        self.assertEqual(errors, [])
        self.assertEqual(set(distances), {'Decor:trunk', 'Decor:trunk-knot', 'Decor:snag'})


class FairyTest(unittest.TestCase):
    """#338 (D82): the fairy has no placement; her frames and glints are swept over every map."""
    def test_the_fairy_constants_are_read_from_the_java(self):
        self.assertEqual(gate.FAIRY_SELF_RADIUS, FAIRY_SELF_RADIUS)
        self.assertEqual(gate.FAIRY_GLOW_PAD, FAIRY_GLOW_PAD)
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
        # Over what the game draws behind her (#236: the scenery layers too). Her lit top edge (#236) carries her over the flat
        # colour and the tiles alone; over the layered forest-1 her ink half still needs her light (0.269 to 0.278 without it).
        roots = {name: ET.parse(gate.ROOT / f'assets/tiles/{name}.tmx').getroot() for name in gate.MAPS}
        images = {name: gate.Scene(name, root) for name, root in roots.items()}
        errors, report = gate.check_fairy(images)
        self.assertEqual(errors, [])
        self.assertEqual(len(report), gate.FAIRY_BODY_FRAMES + FAIRY_GLINTS)
        with mock.patch.object(gate, 'POOL_DIM', 0.0):  # her own light switched off
            errors, _ = gate.check_fairy(images)
        self.assertTrue(any('Fairy frame' in e for e in errors), 'her ink contour needs her light on dark tiles')



class BackgroundTest(unittest.TestCase):
    """D92: the gate measures over each map's own `background` property, the colour the game clears to."""

    def root(self, value=None):
        props = '' if value is None else f'<properties><property name="background" type="color" value="{value}"/></properties>'
        return ET.fromstring(f'<map>{props}</map>')

    def test_tiled_colours_parse_as_the_game_reads_them(self):
        self.assertEqual(gate.map_background(self.root('#ff4a92b8')), FOREST_BLUE[:gate.RGB])
        self.assertEqual(gate.map_background(self.root(NIGHT_HEX)), NIGHT)

    def test_a_missing_or_bad_background_fails(self):
        for value in (None, '#804a92b8', '#4a92b', 'blue', '#gg92b8'):
            with self.assertRaises(ValueError, msg=value):
                gate.map_background(self.root(value))

    def test_every_shipped_map_sets_its_background(self):
        for name in gate.MAPS:
            gate.map_background(ET.parse(gate.ROOT / f'assets/tiles/{name}.tmx').getroot(), name)

    def test_the_composite_is_the_colour_under_the_tiles(self):
        layer = Image.new('RGBA', (LAYER, LAYER)); layer.paste(INK, INK_BOX)
        image = gate.composite(layer, FOREST_BLUE[:gate.RGB])
        self.assertEqual(image.getpixel((0, 0)), FOREST_BLUE)
        self.assertEqual(image.getpixel(INK_AT), INK)


FOREST_1_MAP = 'forest-1'
FOREST_2_MAP = 'forest-2'
FAR_FACTOR = 0.15          # the far layer's parallaxx in forest-1
HALF_SHIFT_CAMERA = 10     # 0.15 * 10 = 1.5: half up is 2
LOW_SHIFT_CAMERA = 3       # 0.15 * 3 = 0.45 -> 0
EXPECTED_HIGH_SHIFT = 2
VIEW_TOP = 8               # the steady camera top of a 368 px room
VIEW_BOTTOM = 368
VIEW_BOX = (0, VIEW_TOP, 640, VIEW_BOTTOM)
FAR_ROOM_X = 4960          # forest-1-deadfall: a room that does not start at the map's left edge
FAR_ROOM_WIDTH = 640
SAMPLE_Y = 100
PLAY_TILES = 20


class SceneryCompositeTest(unittest.TestCase):
    """#236: a sprite is measured over what the game draws behind it, scenery layers included."""
    def test_a_layer_trails_the_camera_by_its_factor_rounded_half_up(self):
        self.assertEqual(gate.shift(FAR_FACTOR, HALF_SHIFT_CAMERA), EXPECTED_HIGH_SHIFT)
        self.assertEqual(gate.shift(FAR_FACTOR, LOW_SHIFT_CAMERA), 0)

    def test_a_map_without_scenery_is_flat_colour_and_play_layer_only(self):
        root = gate.map_roots()[FOREST_2_MAP]
        for layer in [l for l in root.findall('layer') if gate.is_scenery(l)]:   # every shipped map has scenery since #236
            root.remove(layer)
        scene = gate.Scene(FOREST_2_MAP, root)
        self.assertEqual(scene.layers, [])
        self.assertEqual(scene.cameras(0, 0, 1, gate.SCENERY_CAMERA_STEP), (None,))
        flat = gate.composite(gate.tile_layer(root)[0], gate.map_background(root, FOREST_2_MAP))
        self.assertEqual(scene.backgrounds(VIEW_BOX, None)[0].tobytes(), flat.crop(VIEW_BOX).tobytes())

    def test_the_camera_range_stays_inside_the_room_and_on_screen(self):
        scene = gate.Scene(FOREST_1_MAP, gate.map_roots()[FOREST_1_MAP])
        cameras = scene.cameras(FAR_ROOM_X + SAMPLE_Y, SAMPLE_Y, gate.TILE, gate.SCENERY_CAMERA_STEP)
        self.assertGreaterEqual(min(cameras), FAR_ROOM_X)
        self.assertLessEqual(max(cameras), FAR_ROOM_X + max(FAR_ROOM_WIDTH - gate.VIEW_WIDTH, 0) + PLAY_TILES * gate.TILE)
        self.assertEqual(cameras, sorted(set(cameras)))

    def test_scenery_shows_through_the_play_layer_and_moves_with_the_camera(self):
        scene = gate.Scene(FOREST_1_MAP, gate.map_roots()[FOREST_1_MAP])
        flat = gate.composite(scene.play, gate.map_background(scene.root, FOREST_1_MAP))
        first, second = (scene.backgrounds((cam, VIEW_TOP, cam + gate.VIEW_WIDTH, VIEW_BOTTOM), cam)[0] for cam in (0, PLAY_TILES * gate.TILE))
        self.assertNotEqual(first.tobytes(), flat.crop(VIEW_BOX).tobytes())
        self.assertNotEqual(first.tobytes(), second.tobytes())

    def test_a_map_without_a_background_layer_fails_loudly(self):
        root = ET.fromstring('<map width="1" height="1"><tileset firstgid="1" source="ForestTerrain.tsx"/>'
                             '<layer name="ground" width="1" height="1"><data encoding="csv">0</data></layer></map>')
        with self.assertRaises(ValueError):
            gate.tile_layer(root, 'broken')

    def test_the_sheet_columns_are_read_from_the_tileset(self):
        sheet, columns = gate.play_tileset(gate.map_roots()[FOREST_1_MAP])
        self.assertEqual(sheet.name, 'ForestTerrain.png')
        self.assertEqual(columns, PLAY_TILES)

    def test_the_tile_scenery_report_skips_a_map_with_a_different_sheet(self):
        roots = gate.map_roots()
        reported, skipped = gate.tile_report(roots)
        self.assertEqual(skipped, {}, 'every forest map draws the terrain sheet that carries the props (#236)')
        self.assertTrue(reported)
        other = ET.fromstring('<map width="1" height="1"><tileset firstgid="1" source="parallax/forest-sky.tsx"/></map>')
        _, skipped = gate.tile_report({'other': other})
        self.assertEqual(skipped, {'other': 'forest-sky.png'})


if __name__ == '__main__':
    unittest.main()
