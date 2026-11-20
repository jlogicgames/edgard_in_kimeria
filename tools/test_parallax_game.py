"""Unit tests of tools/parallax_game.py and the game-map mode of tools/parallax_mock.py (#236)."""
import contextlib
import io
import unittest
import xml.etree.ElementTree as ET
import parallax_game as game
import parallax_mock as pm
import parallax_sprites as sprites


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
        self.assertEqual([[44, 0, 0, 42], [44, 0, 0, 42], [23, 23, 23, 23], [43, 43, 43, 43]], game.retile(rows), "the map edge counts as soil")
        self.assertEqual(game.retile(rows), game.retile(game.retile(rows)), 'idempotent')
        pit = [[23, 24, 0, 22, 23]]
        self.assertEqual(pit, game.retile(pit), 'a real edge at a pit stays')
        short = [[0, 0, 0], [0, 79, 0], [0, 99, 0]]
        self.assertEqual([[0, 0, 0], [0, 23, 0], [0, 43, 0]], game.retile(short), "a short wall's top is the lit walk edge")

    def test_scenery_never_carries_the_walk_edge(self):
        """D112: the lit lip means "stand here": a drawn cell with no collision shows the soil body or the back rock."""
        rows = [[33, 34, 35, 0, 22, 23, 24], [53, 54, 55, 0, 42, 43, 44]]
        self.assertEqual([[82, 83, 84, 0, 42, 43, 43], [85, 86, 87, 0, 42, 43, 43]], game.retile(rows, frozenset({(9, 9)})))
        solid = frozenset((r, c) for r in range(2) for c in range(7))
        self.assertEqual([[33, 34, 35, 0, 22, 23, 23], [53, 54, 55, 0, 42, 43, 43]], game.retile(rows, solid), 'solid ground keeps its lip')

    def test_only_a_slab_set_into_a_floor_becomes_floor(self):
        self.assertEqual([[23, 23, 23]], game.retile([[23, 27, 23]]))
        slab = [[26, 27, 28, 27], [43, 43, 43, 0]]
        self.assertEqual(slab[0], game.retile(slab)[0], 'a slab lip over a block keeps its caps (forest-1, row 13)')

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
        # a tall fern placed over the red mob (room x 192) must be caught
        broken = pm.ET.fromstring(pm.ET.tostring(group))
        fern = next(o for o in broken.findall('object') if o.get('name') == 'clump-fern')
        fern.set('x', '150')
        fern.set('y', '330')
        self.assertFalse(pm.check_layer(scene, broken, pieces, silent))


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
