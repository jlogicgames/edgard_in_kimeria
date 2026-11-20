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
        text = game.MAP.read_text(encoding='utf-8')
        self.assertEqual(text, game.build(text), 'run python3 tools/parallax_game.py: the map differs from the sketch pieces')

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
