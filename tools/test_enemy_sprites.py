import unittest
from PIL import Image
import enemy_sprites as enemies
from forest_trap_sprites import parse
from sprite_layouts import ENEMY_SHEETS, ENEMY_IMAGES, WORLD_PALETTE_SIZE

ALPHA = 3
ROOT = enemies.ROOT
ATTACK_BOX_LEFT = 26  # RedMob.attackHitbox rect(26, 4, 6, 28): the whip's reach, x 26..32
LASH_TIP = (31, 14)  # the strike frame's lash tip, inside the attack box
REACH_CLEAR_LEFT = 28  # no frame but the strike and the recovery draws past x 28
BAT_BODY_TOP = 2  # Bat.hitbox rect(0, 2, 16, 13): y 2..15
COLORS_AND_FRAMES = slice(1, None)  # parse() returns (size, colours, frames)
FRAMES = 2
# The cells each entity reads (RedMob.java, Bat.java through Assets.animation: four frames a row, a fifth on the next row).
READ_CELLS = {
    'red-mob': {(c, r) for r in (1, 2, 4, 5) for c in range(4)},
    'bat': {(c, r) for r in (0, 2) for c in range(4)} | {(0, 1), (0, 3)} | {(c, 4) for c in range(4)},
}


def cell(image, size, column, row):
    return image.crop((column * size[0], row * size[1], (column + 1) * size[0], (row + 1) * size[1]))


def opaque_rows(image):
    return [y for y in range(image.height) if any(image.getpixel((x, y))[ALPHA] for x in range(image.width))]


class EnemySpritesTest(unittest.TestCase):
    def test_the_shipped_sheets_are_the_exported_grids(self):
        for name, (sheet, _, size, _) in ENEMY_SHEETS.items():
            with self.subTest(name=name):
                shipped = Image.open(ROOT / f'assets/images/enemy/{sheet}.png')
                self.assertEqual(ENEMY_IMAGES[sheet], shipped.size)
                self.assertEqual('P', shipped.mode)
                self.assertLessEqual(len(shipped.getcolors()), WORLD_PALETTE_SIZE)
                self.assertEqual(enemies.sheet_image(name).tobytes(), shipped.convert('RGBA').tobytes(),
                                 f'run tools/enemy_sprites.py {name}')

    def test_every_cell_the_entity_reads_has_art_and_no_other_cell_does(self):
        for name, (_, size, sheet_size, cells) in ENEMY_SHEETS.items():
            with self.subTest(name=name):
                image = enemies.sheet_image(name)
                filled = {c for placed in cells.values() for c in placed}
                self.assertEqual(READ_CELLS[name], filled)
                for column in range(sheet_size[0] // size[0]):
                    for row in range(sheet_size[1] // size[1]):
                        has_art = bool(opaque_rows(cell(image, size, column, row)))
                        self.assertEqual((column, row) in filled, has_art, f'{name} cell {column},{row}')

    def test_the_red_mob_stands_on_its_floor_line_and_keeps_inside_its_body_box(self):
        _, size, _, cells = ENEMY_SHEETS['red-mob']
        image = enemies.sheet_image('red-mob')
        for frame, placed in cells.items():
            art = cell(image, size, *placed[0])
            with self.subTest(frame=frame):
                if frame != 'down':
                    self.assertEqual(size[1] - 1, opaque_rows(art)[-1], 'feet on the floor line (row 31)')
                body = art.crop((0, 0, ATTACK_BOX_LEFT, size[1]))  # left of the whip's attack box (x 26..32), the body hitbox x 6..26
                self.assertTrue(opaque_rows(body))

    def test_the_red_mob_whip_reaches_the_attack_box_only_in_the_strike(self):
        _, size, _, cells = ENEMY_SHEETS['red-mob']
        image = enemies.sheet_image('red-mob')
        strike = cell(image, size, *cells['strike'][0])
        self.assertTrue(strike.getpixel(LASH_TIP)[ALPHA], 'the lash tip at x 31, inside the attack box x 26..32')
        for frame in ('idle1', 'run1', 'windup1', 'windup2'):
            art = cell(image, size, *cells[frame][0])
            self.assertFalse(any(art.getpixel((x, y))[ALPHA] for x in range(REACH_CLEAR_LEFT, size[0]) for y in range(size[1])), frame)

    def test_the_bat_flaps_symmetrically_inside_its_body_box(self):
        _, size, _, cells = ENEMY_SHEETS['bat']
        image = enemies.sheet_image('bat')
        for frame in ('up', 'mid', 'level', 'down'):
            art = cell(image, size, *cells[frame][0])
            with self.subTest(frame=frame):
                rows = opaque_rows(art)
                self.assertGreaterEqual(rows[0], BAT_BODY_TOP)
                self.assertEqual(art.tobytes(), art.transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes())

    def test_the_wind_up_and_death_flash_are_drawn_in_white(self):
        _, frames = parse(enemies.GRID_DIR / 'red-mob.grid')[COLORS_AND_FRAMES]
        drawn = dict(frames)
        self.assertTrue(any('W' in row for row in drawn['windup2']), 'the eyes flare in the wind-up')
        self.assertTrue(any('W' in row for row in drawn['flash']))
        bat = dict(parse(enemies.GRID_DIR / 'bat.grid')[FRAMES])
        self.assertTrue(any('W' in row for row in bat['flash']))


if __name__ == '__main__':
    unittest.main()
