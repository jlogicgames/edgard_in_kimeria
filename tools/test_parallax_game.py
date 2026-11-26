"""Unit tests of tools/parallax_game.py and of both check modes of tools/parallax_mock.py, game maps and sketches (#236)."""
import contextlib
import io
import itertools
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET
import numpy
import parallax_game as game
import parallax_mock as pm
import parallax_sprites as sprites
import terrain_sprites


def canonical(rows):
    """The rows with every face and soil variant shown as the ground piece's own tile (D114): the variants are looks, not shapes."""
    base = {gid: 43 for gid in game.BODY_VARIANTS}
    base.update({game.FACES[0][1]: game.FACES[0][0], game.FACES[1][1]: game.FACES[1][0]})
    return [[base.get(v, v) for v in row] for row in rows]


def silent(*_):
    """The checks print their verdict; a test that expects a failure must not leave a FAIL line in the build log."""


class InstalledMapTest(unittest.TestCase):
    def test_the_installed_layers_are_what_the_installer_writes(self):
        for name in game.MAPS:
            with self.subTest(map=name):
                text = (game.TILES / f'{name}.tmx').read_text(encoding='utf-8')
                self.assertEqual(text, game.build(text, name), 'run python3 tools/parallax_game.py: the map differs from the sketch pieces')

    def test_every_room_of_every_forest_map_has_a_foreground(self):
        for name in game.MAPS:
            root = ET.parse(game.TILES / f'{name}.tmx').getroot()
            rooms = {o.get('name') for o in root.iter('object') if o.get('type') == 'Room'}
            if name != 'forest-1':   # forest-1 keeps the three rooms of the first pass (#236)
                self.assertEqual(rooms, set(game.MAPS[name]), name)

    def test_retile_closes_the_seam_where_two_floors_meet_and_drops_the_ropes(self):
        rows = [[105, 0, 0, 79], [99, 0, 0, 99], [23, 24, 22, 23], [43, 44, 42, 43]]
        self.assertEqual([[44, 0, 0, 42], [44, 0, 0, 42], [game.INNER_UR, 23, 23, game.INNER_UL], [43, 43, 43, 43]],
                         canonical(game.retile(rows)), "the map edge counts as soil; the floor under a wall is its foot (D114)")
        self.assertEqual(game.retile(rows), game.retile(game.retile(rows)), 'idempotent')
        pit = [[23, 24, 0, 22, 23]]
        self.assertEqual(pit, game.retile(pit), 'a real edge at a pit stays')
        short = [[0, 0, 0], [0, 79, 0], [0, 99, 0]]
        self.assertEqual([[0, 0, 0], [0, game.PILLAR_TOP, 0], [0, game.PILLAR_BODY, 0]], game.retile(short),
                         "a short wall's top is the lit walk edge (a one-tile pillar: both faces, D114)")

    def test_scenery_never_carries_the_walk_edge(self):
        """D112: the lit lip means "stand here": a drawn cell with no collision shows the soil body or the back rock."""
        rows = [[33, 34, 35, 0, 22, 23, 24], [53, 54, 55, 0, 42, 43, 44]]
        self.assertEqual([[82, 83, 84, 0, 42, 43, 43], [85, 86, 87, 0, 42, 43, 43]], canonical(game.retile(rows, frozenset({(9, 9)}))))
        solid = frozenset((r, c) for r in range(2) for c in range(7))
        self.assertEqual([[33, 34, 35, 0, 22, 23, 23], [53, 54, 55, 0, 42, 43, 43]], canonical(game.retile(rows, solid)), 'solid ground keeps its lip')

    def test_only_a_slab_set_into_a_floor_becomes_floor(self):
        self.assertEqual([[23, 23, 23]], game.retile([[23, 27, 23]]))
        slab = [[26, 27, 28, 27], [43, 43, 43, 0]]
        self.assertEqual(slab[0], game.retile(slab)[0], 'a slab lip over a block keeps its caps (forest-1, row 13)')

    def test_a_block_with_air_below_gets_the_underside(self):
        """D114: a raised block's lowest row is the underside (soil darkening to a lip over a dark underside), its corners included."""
        block = [[0, 0, 0, 0, 0], [0, 22, 23, 24, 0], [0, 42, 43, 44, 0], [0, 42, 43, 44, 0], [0, 0, 0, 0, 0], [23, 23, 23, 23, 23]]
        out = game.retile(block)
        self.assertEqual([0, 62, 63, 64, 0], out[3], 'air below: the underside row')
        self.assertEqual([0, 22, 23, 24, 0], out[1], 'the top keeps its lit walk edge and its caps')
        self.assertEqual([0, 42, 43, 44, 0], canonical(out)[2], 'ground below: the faces and the body')
        self.assertEqual([23] * 5, out[5], 'the floor under the gap keeps its top')

    def test_a_left_edge_gets_the_left_face_and_a_right_edge_the_lit_face(self):
        """D114: the moon is up and to the right: air on a cell's left shows the shadow face, on its right the lit face."""
        wall = [[0, 43, 43, 43, 0] for _ in range(8)] + [[43] * 5]
        out = game.retile(wall)
        for r in range(8):
            self.assertIn(out[r][1], game.FACES[0], f'row {r}: the left face')
            self.assertIn(out[r][3], game.FACES[1], f'row {r}: the right face')
            self.assertIn(out[r][2], game.BODY_VARIANTS, f'row {r}: the soil body between them')
        self.assertEqual({2}, {len({row[1] for row in out[:8]}), len({row[3] for row in out[:8]})}, 'both face variants are used')

    def test_a_wall_foot_carries_the_inner_corner_and_never_the_walk_edge(self):
        """D114: where a wall stands on a floor the walk edge ends at the wall; the cell under the wall is soil with moss in the corner."""
        rows = [[0, 0, 43, 43, 0, 0], [0, 0, 43, 43, 0, 0], [23, 23, 23, 23, 23, 23], [43] * 6]
        out = game.retile(rows)
        self.assertEqual([23, 23, game.INNER_UL, game.INNER_UR, 23, 23], out[2])
        self.assertEqual([0, 0, 15, 16, 0, 0], [{42: 15, 44: 16}.get(v, v) for v in canonical(out)[1]], 'the wall shows both faces')
        lone = [[0, 43, 0], [23, 23, 23], [43, 43, 43]]
        self.assertEqual([23, game.INNER_ULR, 23], game.retile(lone)[1], 'a one-tile column: moss on both sides')

    def test_the_new_tiles_never_carry_the_walk_edge(self):
        """The straw `e1cfa5` means "stand here" (D112): only a top (its caps and the pillar's) may show it."""
        sheet, (columns, tile) = terrain_sprites.sheet(), (game.SHEET_COLUMNS, pm.TILE)
        walk_edge = terrain_sprites.parse(terrain_sprites.DIR / 'terrain.grid')[1]['S']
        tops = set(game.GROUND[0]) | {game.PILLAR_TOP}
        for gid in [g for g, kind in game.KIND.items() if kind] + [game.PILLAR_TOP]:
            x, y = (gid - 1) % columns * tile, (gid - 1) // columns * tile
            straw = sum(1 for p in sheet.crop((x, y, x + tile, y + tile)).getdata() if p[3] and p[:3] == walk_edge[:3])
            with self.subTest(gid=gid):
                self.assertEqual(gid in tops, straw > 0)

    def test_variants_break_the_repeat_and_are_fixed_by_place(self):
        field = [[43] * 24 for _ in range(10)]
        out = game.retile(field)
        self.assertEqual(set(game.BODY_VARIANTS), {v for row in out for v in row}, 'every soil body is used')
        runs = max(sum(1 for _ in g) for row in out for _, g in itertools.groupby(row))
        self.assertEqual(1, runs, 'no two alike side by side')
        self.assertFalse(any(out[r][c] == out[r + 1][c] for r in range(9) for c in range(24)), 'nor one above the other')
        self.assertEqual(out, game.retile(out), 'a reinstall picks the same tiles')
        self.assertEqual(out, game.retile([[game.BODY_VARIANTS[(r + c) % 4] for c in range(24)] for r in range(10)]), 'the place decides, not the tile')

    def test_retile_never_changes_which_cells_are_drawn(self):
        for name in game.MAPS:
            text = (game.TILES / f'{name}.tmx').read_text(encoding='utf-8')
            _, rows = game.background_rows(text)
            out = game.retile(rows, game.solid_cells(text))
            with self.subTest(map=name):
                self.assertEqual([[bool(v) for v in row] for row in rows], [[bool(v) for v in row] for row in out])

    def test_a_narrow_room_has_the_one_centred_camera(self):
        scene = next(s for s in pm.game_scenes() if s.room == 'forest-boss')
        self.assertEqual([(448 - pm.VIEW[0]) // 2], list(scene.cameras))
        self.assertEqual(scene.cameras[0], pm.hero_camera(scene, 100))

    def test_every_foreground_room_is_found_in_the_map(self):
        scenes = pm.game_scenes()
        self.assertEqual(sorted(game.GAME_FOREGROUND), sorted(scene.room for scene in scenes))
        for scene in scenes:
            self.assertTrue(scene.game)
            self.assertEqual(scene.x, scene.origin)

    def test_scenery_layers_are_as_wide_as_their_factor_needs(self):
        columns = 540
        self.assertEqual(pm.VIEW[0] // pm.TILE + game.EDGE_COLUMNS, game.columns_for(0, columns))
        self.assertGreaterEqual(game.columns_for(0.4, columns) * pm.TILE, 0.4 * (columns * pm.TILE - pm.VIEW[0]) + pm.VIEW[0])

    def test_rounding_is_half_up_as_javas_math_round(self):
        self.assertEqual([1, 0, 3, -1], [pm.jround(v) for v in (0.5, -0.5, 2.5, -1.5)])


class SketchCheckTest(unittest.TestCase):
    def test_the_check_of_the_sketches_exits_0(self):
        """`--check` without `--game` reads mock.tmx and mock-calm.tmx against their rooms in the game map (#599)."""
        run = subprocess.run([sys.executable, pm.__file__, '--check'], capture_output=True, text=True, check=False)
        self.assertEqual(0, run.returncode, run.stdout + run.stderr)
        for scene in pm.SCENES.values():
            self.assertIn(f'{scene.mock.name} foreground: 0 covered objects', run.stdout)

    def test_the_terrain_sketch_is_mock_tmx_over_the_terrain_tileset(self):
        """mock-terrain.tmx is generated from mock.tmx and no check reads it, so a hand edit of one must reach the other."""
        self.assertEqual(pm.terrain_text(pm.MOCK.read_text(encoding='utf-8')), pm.TERRAIN_MOCK.read_text(encoding='utf-8'),
                         'run python3 tools/parallax_mock.py --terrain: mock-terrain.tmx differs from mock.tmx')


class PackedSheetTest(unittest.TestCase):
    MAX_TEXTURE = 2048   # the smallest texture side a phone browser is sure to take

    def test_the_foreground_is_one_sheet_with_a_sub_rectangle_per_piece(self):
        tsx = ET.parse(game.TILES / 'parallax/forest-fg.tsx').getroot()
        sources = {tile.find('image').get('source') for tile in tsx.findall('tile')}
        self.assertEqual({'../../images/parallax/forest-fg.png'}, sources)
        _, spots, image, _ = sprites.sheet('forest/fg')
        self.assertLessEqual(max(image.size), self.MAX_TEXTURE)
        for tile, (col, row, w, h) in zip(tsx.findall('tile'), spots.values()):
            self.assertEqual((col * pm.TILE, row * pm.TILE, w * pm.TILE, h * pm.TILE),
                             tuple(int(tile.get(k)) for k in ('x', 'y', 'width', 'height')))

    def test_fully_transparent_tiles_are_not_cells(self):
        blank = sprites.blank_ids('forest/far')
        self.assertTrue(blank)
        layer = pm.Layer((1, sprites.layout('forest/far')[0], blank), 8, 8)
        layer.strip(['ridge-a'], 0)
        used = {(gid - 1) for row in layer.rows for gid in row if gid}
        self.assertFalse(used & blank)


class GameCheckTest(unittest.TestCase):
    def test_the_negative_check_prints_nothing(self):
        scene = next(s for s in pm.game_scenes() if s.room == 'forest-1-mob')
        root, _, pieces = pm.tile_images(scene.mock)
        group = next(g for g in root.findall('objectgroup') if g.get('name') == 'foreground-forest-1-mob')
        noise = io.StringIO()
        with contextlib.redirect_stdout(noise):
            pm.check_layer(scene, group, pieces, silent)
        self.assertEqual('', noise.getvalue())

    def test_a_foreground_over_a_protected_object_fails_the_check(self):
        scene = next(s for s in pm.game_scenes() if s.room == 'forest-1-mob')
        root, _, pieces = pm.tile_images(scene.mock)
        group = next(g for g in root.findall('objectgroup') if g.get('name') == 'foreground-forest-1-mob')
        self.assertTrue(pm.check_layer(scene, group, pieces, silent))
        # the near canopy lowered over the red mob (room x 192) must be caught
        broken = pm.ET.fromstring(pm.ET.tostring(group))
        canopy = next(o for o in broken.findall('object') if o.get('name') == 'near-canopy')
        canopy.set('x', '150')
        canopy.set('y', '330')
        self.assertFalse(pm.check_layer(scene, broken, pieces, silent))

    @staticmethod
    def scene_groups(room):
        scene = next(s for s in pm.game_scenes() if s.room == room)
        root, _, pieces = pm.tile_images(scene.mock)
        return scene, pm.room_groups(scene, root), pieces

    @staticmethod
    def copy(groups):
        return [pm.ET.fromstring(pm.ET.tostring(g)) for g in groups]

    def test_a_giant_leaf_over_a_pit_or_a_room_without_one_fails_the_check(self):
        scene, groups, pieces = self.scene_groups('forest-0-traps')
        self.assertTrue(pm.check_giant(scene, groups, pieces, silent))
        pit_x, _ = pm.pits(scene)[0]
        # a floor clump slid over the pit, as the camera there carries it
        broken = self.copy(groups)
        leaf = next(o for o in broken[-1].findall('object') if o.get('name') == 'edge-giant-low-a')
        leaf.set('x', str(pit_x))
        leaf.set('y', '416')
        self.assertFalse(pm.check_giant(scene, broken, pieces, silent))
        self.assertFalse(pm.check_giant(scene, groups[:-1], pieces, silent), 'a room without its near group')

    def test_the_near_group_keeps_its_factor_its_pieces_and_its_place_in_the_draw_order(self):
        scene, groups, pieces = self.scene_groups('forest-0-traps')
        slow = self.copy(groups)
        slow[-1].set('parallaxx', str(pm.FG_FACTOR))
        self.assertFalse(pm.check_giant(scene, slow, pieces, silent))
        self.assertFalse(pm.check_giant(scene, groups[::-1], pieces, silent), 'drawn under the sharp pieces')
        mixed = self.copy(groups)
        mixed[0].append(mixed[-1].find('object'))
        self.assertFalse(pm.check_giant(scene, mixed, pieces, silent), 'a giant leaf among the sharp pieces')

    def test_a_giant_leaf_that_hides_a_sharp_piece_fails_the_check(self):
        scene, groups, pieces = self.scene_groups('forest-1-traps')   # one camera: both layers stand where they are authored
        self.assertTrue(pm.check_share(scene, groups, pieces, silent))
        clump = next(o for o in groups[0].findall('object') if o.get('name').startswith('clump-') and 0 <= int(o.get('x')) < 400)
        broken = self.copy(groups)
        leaf = next(o for o in broken[-1].findall('object') if o.get('name') == 'edge-giant-low-a')
        leaf.set('x', clump.get('x'))
        leaf.set('y', '416')
        self.assertFalse(pm.check_share(scene, broken, pieces, silent))
        hidden = {(s.piece, s.x): s.pooled for s in pm.hidden_shares(scene, broken, pieces)}
        self.assertGreater(hidden[clump.get('name'), int(clump.get('x'))], pm.SHARE_POOLED)

    def test_the_hero_cover_counts_both_layers_together(self):
        scene, groups, pieces = self.scene_groups('forest-1-traps')
        both = pm.hero_layers(scene, groups, pieces)
        x = pm.hero_route(scene)[0]
        for layer in both:
            self.assertLessEqual(pm.hero_cover(scene, [layer], x), pm.hero_cover(scene, both, x))
        solid = numpy.zeros_like(both[0][0])
        left, right = solid.copy(), solid.copy()
        column = x + pm.HERO_FIGURE_DX - pm.hero_camera(scene, x) + pm.FG_PAD
        floor = int(scene.floor)
        left[floor - pm.HERO_H:floor, column:column + 6] = True
        right[floor - pm.HERO_H:floor, column + 6:column + pm.HERO_FIGURE_W] = True
        split = [(left, 1.0, 0), (right, 1.0, 0)]
        self.assertEqual(1.0, pm.hero_cover(scene, split, x), 'two layers each hiding half hide the whole figure')


if __name__ == '__main__':
    unittest.main()


class BleedCheckTest(unittest.TestCase):
    """D111: `check_bleed` passes the shipped rooms and fails when a bleed piece or layer is removed (not vacuous)."""

    def run_bleed(self, scene, mutate):
        original = pm.tile_images
        def patched(mock=None):
            root, sets, pieces = original(mock)
            mutate(root)
            return root, sets, pieces
        pm.tile_images = patched
        try:
            return pm.check_bleed(scene, out=silent)
        finally:
            pm.tile_images = original

    @staticmethod
    def drop_objects(suffix):
        def mutate(root):
            for group in root.findall('objectgroup'):
                for obj in list(group.findall('object')):
                    if obj.get('name', '').endswith(suffix):
                        group.remove(obj)
        return mutate

    @staticmethod
    def drop_layer(name):
        def mutate(root):
            for layer in list(root):
                if layer.get('name') == name:
                    root.remove(layer)
        return mutate

    def test_the_shipped_rooms_pass(self):
        for scene in pm.game_scenes() + list(pm.SCENES.values()):
            with self.subTest(scene=scene.room):
                self.assertTrue(self.run_bleed(scene, lambda root: None))

    def test_a_removed_top_foot_or_layer_fails(self):
        scene = pm.game_scenes()[0]
        for label, mutate in (('a -top', self.drop_objects('-top')), ('a -foot', self.drop_objects('-foot')),
                              ('play-above', self.drop_layer('play-above')), ('play-below', self.drop_layer('play-below')),
                              ('mid', self.drop_layer('mid')), ('sky', self.drop_layer('sky'))):
            with self.subTest(removed=label):
                self.assertFalse(self.run_bleed(scene, mutate))

    def test_every_single_top_and_foot_is_needed(self):
        for scene in pm.game_scenes() + list(pm.SCENES.values()):
            names = {o.get('name') for d in pm.Layers(scene).layers if d.element.tag == 'objectgroup'   # this room's own group
                     for o in d.element.findall('object') if o.get('name', '').endswith(('-top', '-foot'))}
            self.assertTrue(names)
            for name in sorted(names):
                with self.subTest(scene=scene.room, removed=name):
                    def mutate(root, name=name):
                        for group in root.findall('objectgroup'):
                            for obj in list(group.findall('object')):
                                if obj.get('name') == name:
                                    group.remove(obj)
                    self.assertFalse(self.run_bleed(scene, mutate))
