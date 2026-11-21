import re
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
    'boss': {(c, r) for r in range(1, 8) for c in range(4)},  # Boss.java: rows 1 to 7; row 0 (an unused walk), 8 and 9 never
    'yellow-mob': {(c, r) for r in (1, 4, 5) for c in range(4)},
}
ENEMY_JAVA = ROOT / 'core/src/main/java/com/jlogicsoftware/kimeria/entity/enemy'
FLOOR_ROW = 31
# D10, measured per frame on the grid: the boxes are read from the Java source, so a changed box fails here too.
BOSS_AXE = set('sSBwaj')  # the axe: steel, edge, bevel, haft, its own ink and eye (boss.grid); everything else is the body
BOSS_LIVE = ('idle1', 'idle2', 'idle3', 'idle4', 'walk1', 'walk2', 'walk3', 'walk4', 'raise', 'cock', 'sweep', 'follow',
             'warn1', 'warn2', 'counter1', 'counter2', 'recoil', 'throne1', 'throne2', 'throne3', 'throne4')
BOSS_ACTIVE = {'sweep': 'NORMAL_BOX', 'counter1': 'COUNTER_BOX', 'counter2': 'COUNTER_BOX'}  # Boss.isAttackActive frames
BOSS_COUNTER_WINDUPS = ('warn1', 'warn2')  # the counter's tell: the eyes flare white (the normal attack's stay red)
BOSS_NORMAL_WINDUPS = ('raise', 'cock')
BOSS_CHEST_ROW = 17  # mid-chest: the normal wind-up holds the axe head below this row (at the hip), the counter above it
BOSS_MARGIN = 2  # the body hitbox is at most this many pixels wider than the drawn body on either side ("slightly")
YELLOW_LIVE = ('idle1', 'idle2', 'idle3', 'idle4', 'run1', 'run2', 'run3', 'run4')


def java_box(java, name):
    """(x, y, w, h) of `name = Hitbox.rect(x, y, w, h)` in an enemy's Java source."""
    source = (ENEMY_JAVA / java).read_text(encoding='utf-8')
    return tuple(int(v) for v in re.search(name + r'\s*=\s*Hitbox\.rect\((\d+), (\d+), (\d+), (\d+)\)', source).groups())


def pixels(rows, chars=None, exclude=frozenset()):
    return [(x, y) for y, row in enumerate(rows) for x, c in enumerate(row)
            if c != '.' and c not in exclude and (chars is None or c in chars)]


def inside(point, box, margin=0):
    x, y = point
    left, top, width, height = box
    return left + margin <= x < left + width - margin and top + margin <= y < top + height


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


class BossAndYellowMobBoxesTest(unittest.TestCase):
    """D10 for the redrawn boss and yellow mob: the boxes stay as they were, the drawing is fitted to them."""

    def setUp(self):
        self.boss = dict(parse(enemies.GRID_DIR / 'boss.grid')[FRAMES])
        self.yellow = dict(parse(enemies.GRID_DIR / 'yellow-mob.grid')[FRAMES])

    def test_the_boss_body_lies_a_pixel_inside_its_body_hitbox_and_fills_it(self):
        hitbox = java_box('Boss.java', 'hitbox')
        for frame in BOSS_LIVE:
            body = pixels(self.boss[frame], exclude=BOSS_AXE)
            with self.subTest(frame=frame):
                self.assertEqual([], [p for p in body if not inside(p, hitbox, 1) or p[1] < hitbox[1] + 1])
                xs = [x for x, _ in body]
                left, _, width, _ = hitbox
                self.assertLessEqual(min(xs) - left, BOSS_MARGIN)
                self.assertLessEqual(left + width - 1 - max(xs), BOSS_MARGIN)
                self.assertEqual(FLOOR_ROW, max(y for _, y in body), 'feet on the floor line')

    def test_the_boss_axe_fills_the_attack_box_on_every_active_frame_and_nothing_else_leaves_the_body(self):
        hitbox = java_box('Boss.java', 'hitbox')
        for frame, name in BOSS_ACTIVE.items():
            box = java_box('Boss.java', name)
            rows = self.boss[frame]
            with self.subTest(frame=frame):
                stray = [p for p in pixels(rows) if not inside(p, hitbox)
                         and not (rows[p[1]][p[0]] in BOSS_AXE and inside(p, box))]
                self.assertEqual([], stray, 'only the axe leaves the body hitbox, and only into the attack box')
                axe = [p for p in pixels(rows, BOSS_AXE) if inside(p, box)]
                left, top, width, height = box
                self.assertEqual((left, left + width - 1), (min(x for x, _ in axe), max(x for x, _ in axe)))
                self.assertEqual((top, top + height - 1), (min(y for _, y in axe), max(y for _, y in axe)))

    def test_the_two_boss_attacks_wind_up_apart_low_and_red_or_high_and_white(self):
        # The normal attack draws the axe back low at the hip with the eyes red; the counter raises it high with the eyes white.
        steel = set('sSB')
        for frame in BOSS_NORMAL_WINDUPS:
            rows = self.boss[frame]
            self.assertFalse(any('W' in row for row in rows), frame)
            self.assertGreater(min(y for _, y in pixels(rows, steel)), BOSS_CHEST_ROW, frame)
        for frame in BOSS_COUNTER_WINDUPS:
            rows = self.boss[frame]
            self.assertTrue(any('W' in row for row in rows), frame)
            self.assertLess(min(y for _, y in pixels(rows, steel)), BOSS_CHEST_ROW, frame)

    def test_the_boss_flash_is_the_recoil_silhouette(self):
        self.assertEqual(pixels(self.boss['recoil']), pixels(self.boss['flash']))
        self.assertEqual(set('W.'), {c for row in self.boss['flash'] for c in row})

    def test_the_yellow_mob_lies_inside_its_body_hitbox_and_covers_its_hurtbox(self):
        hitbox, hurtbox = java_box('YellowMob.java', 'hitbox'), java_box('YellowMob.java', 'hurtbox')
        for frame in YELLOW_LIVE:
            art = pixels(self.yellow[frame])
            with self.subTest(frame=frame):
                self.assertEqual([], [p for p in art if not inside(p, hitbox, 1) or p[1] < hitbox[1] + 1])
                self.assertEqual(FLOOR_ROW, max(y for _, y in art))
                # The lethal box never lies past the drawing: every row and every column of it holds art.
                left, top, width, height = hurtbox
                lethal = [p for p in art if inside(p, hurtbox)]
                self.assertEqual(set(range(top, top + height)), {y for _, y in lethal})
                self.assertEqual(set(range(left, left + width)), {x for x, _ in lethal})


if __name__ == '__main__':
    unittest.main()
