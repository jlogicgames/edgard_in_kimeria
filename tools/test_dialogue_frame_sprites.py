"""The dialogue screen art (#337, D78): the exports match the grids, and every frame element keeps its contrast tier.

D76 measures world sprites over the map (`check_contrast.py`); the dialogue screen has no world behind it, so its tiers are
checked here, on the exported art (OKLab lightness L):

- an element's loudness is its loudest colour: the largest |L| difference between any of its opaque colours and the ground
  it sits on (the page fill around it; the portrait opening for a window's bead; the ink for the text box and the choices).
  A window is measured without its opening, which is the portraits' background and is measured with them (below);
- active (what names the speaker, and what can be chosen): at least `check_contrast.ACTIVE_OUTLINE_MIN` (0.28);
- decor (the ornament: page border, text box border, and the listener's window, crest and plate): at most
  `check_contrast.DECOR_OUTLINE_MAX` (0.20) against every ground it touches;
- the thread: its outline (D76's measure) against the composed screen it crosses, either speaker lit, active;
- the band (D134): its visible width, counted from the outer edge to the opening along the middle of each side of the window
  stretched to its drawn size, is the same on all four sides (within 1 px), lit and dim, so no side looks thinner;
- the dimmed listener portrait: an outline of at least 0.15 against its opening, and visibly darker than when lit.

The layout (positions and sizes) is read from `HealerDialogueView.java`, so a layout change there is measured here.
Values measured 2026-11-16 are in docs/ART_DIRECTION.md, "Dialogue screen".
"""
import ast
import operator
import re
import sys
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_contrast as contrast  # noqa: E402
import dialogue_frame_sprites as frames  # noqa: E402
from sprite_layouts import DIALOGUE_SHEETS, OPAQUE_ALPHA  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'assets/images/ui'
VIEW = ROOT / 'core/src/main/java/com/jlogicsoftware/kimeria/ui/HealerDialogueView.java'
ACTIVE_MIN = contrast.ACTIVE_OUTLINE_MIN
DECOR_MAX = contrast.DECOR_OUTLINE_MAX
LISTENER_OUTLINE_MIN = 0.15  # the dimmed portrait's silhouette against its opening
BAND_VISIBLE = 0.05  # |L| from the page fill at which a band pixel counts as visible (the near-black outline sits at 0.022)
BAND_EVEN = 1  # px the four sides may differ by
BAND_MIN = 6  # px the thinnest side must still show (the band is 7 today)
LISTENER_DROP_MIN = 0.10  # the dimmed portrait is visibly darker than the lit one (mean L)
OPENING = (16, 16)  # a pixel of the window's opening
BEAD = (6, 16)  # the bright bead pixel on the window's left band
FILL = (12, 12)  # a pixel inside the page and the text box
PLATE_FILL = (8, 5)  # a pixel of the plate's fill
AIR = 6  # px of opening colour around a measured portrait
RGB = slice(0, 3)
LIT = 0  # frame index in a lit/dim strip
DIM = 1
IDLE = 0  # frame index in the choice strip
SELECTED = 1


def java_layout():
    """Every `static final float|int NAME = expression` of HealerDialogueView, evaluated in declaration order."""
    code = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/', '', VIEW.read_text(encoding='utf-8'))
    values = {}
    operators = {ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul, ast.Div: operator.truediv}

    def evaluate(node):
        if isinstance(node, ast.Constant):
            return node.value
        if isinstance(node, ast.Name):
            return values[node.id]
        if isinstance(node, ast.BinOp) and type(node.op) in operators:
            return operators[type(node.op)](evaluate(node.left), evaluate(node.right))
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
            return -evaluate(node.operand)
        raise KeyError(ast.dump(node))

    for statement in re.finditer(r'static\s+final\s+(?:float|int)\s+([^;]+);', code):
        for declaration in re.split(r',(?![^(]*\))', statement.group(1)):
            name, _, expression = declaration.partition('=')
            expression = re.sub(r'(\d)[fF]\b', r'\1', expression.strip())
            try:
                values[name.strip()] = evaluate(ast.parse(expression, mode='eval').body)
            except (KeyError, SyntaxError):
                pass  # built from another class (FontSizes): not layout this test needs
    return values


LAYOUT = java_layout()


def at(name):
    return int(round(LAYOUT[name]))


def lightness(rgb):
    return contrast.oklab(rgb)[0]


def frame(name, index):
    (width, _), _ = DIALOGUE_SHEETS[name]
    sheet = Image.open(UI / f'dialogue-{name}.png').convert('RGBA')
    return sheet.crop((index * width, 0, (index + 1) * width, sheet.height))


def pixel(name, index, point):
    return frame(name, index).getpixel(point)[RGB]


def loudest(image, ground, besides=None):
    """The largest |L| difference between the image's opaque colours (except `besides`) and the ground."""
    return max(abs(lightness(p[RGB]) - lightness(ground)) for p in image.get_flattened_data()
               if p[-1] == OPAQUE_ALPHA and p[RGB] != besides)



def listener():
    text = VIEW.read_text(encoding='utf-8')
    values = re.search(r'LISTENER = new Color\(([.\d]+)f, ([.\d]+)f, ([.\d]+)f', text).groups()
    return tuple(float(v) for v in values)


def scaled(image, factors):
    bands = image.split()
    return Image.merge('RGBA', [band.point(lambda v, m=m: int(v * m)) for band, m in zip(bands, factors + (1.0,))])


def nine_patch(source, width, height, inset):
    """A nine-patch frame stretched to (width, height), as HealerDialogueView.Patch draws it."""
    out = Image.new('RGBA', (width, height))
    columns = ((0, inset, 0, inset), (inset, source.width - inset, inset, width - inset),
               (source.width - inset, source.width, width - inset, width))
    rows = ((0, inset, 0, inset), (inset, source.height - inset, inset, height - inset),
            (source.height - inset, source.height, height - inset, height))
    for sx0, sx1, dx0, dx1 in columns:
        for sy0, sy1, dy0, dy1 in rows:
            part = source.crop((sx0, sy0, sx1, sy1)).resize((dx1 - dx0, dy1 - dy0), Image.Resampling.NEAREST)
            out.paste(part, (dx0, dy0))
    return out


def portrait(name, lit):
    size = at('PORTRAIT')
    image = Image.open(ROOT / f'assets/images/portraits/{name}.png').convert('RGBA').resize(
        (size, size), Image.Resampling.BILINEAR)
    return image if lit else scaled(image, listener())


def screen(edgard_speaking):
    """The screen behind the thread: the page, the two windows and their portraits (the speaker lit, the listener dimmed)."""
    image = nine_patch(frame('page', 0), at('SCREEN_W'), at('SCREEN_H'), at('PAGE_INSET'))
    windows = ((at('EDGARD_WINDOW_X'), 'edgard', edgard_speaking), (at('HEALER_WINDOW_X'), 'healer', not edgard_speaking))
    for x, name, lit in windows:
        window = nine_patch(frame('window', LIT if lit else DIM), at('WINDOW'), at('WINDOW'), at('WINDOW_INSET'))
        image.paste(window, (x, at('WINDOW_Y')))
        image.alpha_composite(portrait(name, lit), (x + at('PORTRAIT_INSET'), at('WINDOW_Y') + at('PORTRAIT_INSET')))
    return image


class DialogueFrameTest(unittest.TestCase):
    def test_the_layout_is_read_from_the_view(self):
        for name in ('SCREEN_W', 'SCREEN_H', 'WINDOW', 'WINDOW_Y', 'WINDOW_INSET', 'PAGE_INSET', 'PORTRAIT',
                     'PORTRAIT_INSET', 'EDGARD_WINDOW_X', 'HEALER_WINDOW_X', 'THREAD_X', 'THREAD_Y'):
            self.assertIn(name, LAYOUT, f'{name} must stay a plain constant in HealerDialogueView')
        self.assertEqual(LAYOUT['HEALER_WINDOW_X'], LAYOUT['SCREEN_W'] - LAYOUT['EDGARD_WINDOW_X'] - LAYOUT['WINDOW'])

    def test_exports_match_the_grids(self):
        for name in DIALOGUE_SHEETS:
            exported = Image.open(UI / f'dialogue-{name}.png').convert('RGBA')
            self.assertEqual(frames.master(name).tobytes(), exported.tobytes(),
                             f'{name}: run python3 tools/dialogue_frame_sprites.py')

    def test_what_names_the_speaker_or_can_be_chosen_is_loud(self):
        page, ink = pixel('page', 0, FILL), pixel('box', 0, FILL)
        opening = pixel('window', LIT, OPENING)
        cases = (('speaker window bead, on its opening', abs(lightness(pixel('window', LIT, BEAD)) - lightness(opening))),
                 ('speaker window band, on the page', loudest(frame('window', LIT), page, besides=opening)),
                 ('speaker crest, on the page', loudest(frame('crest', LIT), page)),
                 ('speaker plate, on the page', loudest(frame('plate', LIT), page)),
                 ('speaker plate rule, on its fill', loudest(frame('plate', LIT), pixel('plate', LIT, PLATE_FILL))),
                 ('notch, on the page', loudest(frame('notch', 0), page)),
                 ('idle choice, on the page', loudest(frame('choice', IDLE), page)),
                 ('idle choice rule, on its ink', loudest(frame('choice', IDLE), ink)),
                 ('selected choice, on its ink', loudest(frame('choice', SELECTED), ink)))
        for what, value in cases:
            self.assertGreaterEqual(value, ACTIVE_MIN, what)

    def test_the_ornament_and_the_listener_are_quiet(self):
        page, ink = pixel('page', 0, FILL), pixel('box', 0, FILL)
        opening = pixel('window', DIM, OPENING)
        cases = (('listener window bead, on its opening', abs(lightness(pixel('window', DIM, BEAD)) - lightness(opening))),
                 ('listener window band, on the page', loudest(frame('window', DIM), page, besides=opening)),
                 ('listener crest, on the page', loudest(frame('crest', DIM), page)),
                 ('listener plate, on the page', loudest(frame('plate', DIM), page)),
                 ('listener plate rule, on its fill', loudest(frame('plate', DIM), pixel('plate', DIM, PLATE_FILL))),
                 ('page border, on the page', loudest(frame('page', 0), page)),
                 ('text box border, on the page', loudest(frame('box', 0), page)),
                 ('text box border, on its ink', loudest(frame('box', 0), ink)))
        for what, value in cases:
            self.assertLessEqual(value, DECOR_MAX, what)

    def test_the_band_reads_the_same_on_all_four_sides(self):
        page, size = pixel('page', 0, FILL), at('WINDOW')
        middle = size // 2
        for index, name in ((LIT, 'lit'), (DIM, 'dim')):
            opening = pixel('window', index, OPENING)
            window = nine_patch(frame('window', index), size, size, at('WINDOW_INSET')).convert('RGB')
            sides = {'left': [(i, middle) for i in range(size)], 'top': [(middle, i) for i in range(size)],
                     'right': [(size - 1 - i, middle) for i in range(size)], 'bottom': [(middle, size - 1 - i) for i in range(size)]}
            widths = {}
            for side, points in sides.items():
                count = 0
                for point in points[:at('WINDOW_INSET')]:  # the band and its inner edge only, never the opening or beyond
                    if window.getpixel(point) == opening:
                        break
                    count += abs(lightness(window.getpixel(point)) - lightness(page)) >= BAND_VISIBLE
                widths[side] = count
            self.assertLessEqual(max(widths.values()) - min(widths.values()), BAND_EVEN, f'{name} window band: {widths}')
            self.assertGreaterEqual(min(widths.values()), BAND_MIN, f'{name} window band: {widths}')

    def test_the_thread_is_loud_on_the_screen_it_crosses(self):
        """The thread over what is drawn behind it: the page, both window bands and both portraits, either speaker lit."""
        thread = Image.open(UI / 'dialogue-thread.png').convert('RGBA')
        for edgard_speaking in (True, False):
            outline, _ = contrast.measure(thread, screen(edgard_speaking), at('THREAD_X'), at('THREAD_Y'))
            self.assertGreaterEqual(outline, ACTIVE_MIN, f'thread, Edgard speaking {edgard_speaking}')

    def test_the_dimmed_listener_still_reads_on_its_opening(self):
        size = at('PORTRAIT') + AIR * 2
        window = Image.new('RGBA', (size, size), pixel('window', DIM, OPENING) + (OPAQUE_ALPHA,))
        factors = listener()
        for name in ('edgard', 'healer'):
            outline, _ = contrast.measure(portrait(name, False), window, AIR, AIR)
            self.assertGreaterEqual(outline, LISTENER_OUTLINE_MIN, f'{name} dimmed outline')
            opaque = [p for p in portrait(name, True).get_flattened_data() if p[-1] >= contrast.ALPHA_MIN]
            lit_l = sum(lightness(p[RGB]) for p in opaque) / len(opaque)
            dim_l = sum(lightness(tuple(int(c * f) for c, f in zip(p[RGB], factors))) for p in opaque) / len(opaque)
            self.assertGreaterEqual(lit_l - dim_l, LISTENER_DROP_MIN, f'{name} reads as dimmed')


if __name__ == '__main__':
    unittest.main()
