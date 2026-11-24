#!/usr/bin/env python3
"""Frame strip of the beetle burst, old and proposed (#559). A mock: it re-implements the straight-line particle model of
KimeriaParticleSystem (lerp from start to end, per-kind size and alpha) at game resolution (1 px = 1 world px), with Python's
random instead of RandomXS128, so the shapes are right and the exact pixels are not. Run from the repository root:
    python3 docs/references/beetle-burst/make_strip.py
Writes beetle-burst-strip.png (1x, the game's pixels) and beetle-burst-strip-x3.png (the same, nearest-neighbour x4)."""
import math, random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent
SHEET = Image.open(OUT.parents[2] / "assets/images/objects/monster-beetle.png").convert("RGBA")
FRAME_W, FRAME_H, WHITE_FRAME = 24, 16, 8
CELL_W, CELL_H, FLOOR = 112, 76, 56          # a cell: the blast box (80 wide, 32 tall) plus a margin; the floor line at y 56
CX, CY = CELL_W // 2, FLOOR - 8              # the body centre, 8 px above the floor line
BOX = (CX - 40, FLOOR - 32, CX + 40, FLOOR)
FLAME, SMOKE, GRIT, GLOW = "flame", "smoke", "grit", "glow"
WHITE, PALE, GREY, DARK = (1, 1, 1), (.847, .839, .910), (.604, .592, .678), (.357, .345, .439)
SHELL, SHELL_LIT, EYE = (.525, .063, .122), (.612, .125, .314), (1.0, .42, .341)
SKY_TOP, SKY_LOW, GROUND, GRASS = (14, 22, 48), (28, 40, 78), (22, 16, 22), (34, 58, 46)


def rng_range(r, a, b): return a + r.random() * (b - a)


def old_burst(r):
    ps = [(GLOW, CX, CY, CX, CY, .1, 40, WHITE)]
    for i in range(26):
        side = 1 if i % 2 == 0 else -1
        c = WHITE if r.random() < .4 else PALE
        ps.append((FLAME, CX + rng_range(r, -4, 4), CY, CX + side * rng_range(r, 10, 38), FLOOR - rng_range(r, 2, 30), rng_range(r, .16, .28), rng_range(r, 1, 3), c))
    for i in range(10):
        x, y = CX + rng_range(r, -30, 30), FLOOR - rng_range(r, 4, 24)
        p = r.random(); c = PALE if p < .4 else GREY if p < .8 else DARK
        ps.append((SMOKE, x, y, x, y - rng_range(r, 8, 16), rng_range(r, .35, .55), rng_range(r, 4, 7), c))
    return ps


# ---- the proposal (numbers are the proposal's named constants; see docs/proposals/559-beetle-burst.md)
DOME_DOTS, DOME_RX, DOME_RY, DOME_LIFE, DOME_SIZE = 32, 40, 32, .16, 4
FLASH_SIZE, FLASH_LIFE, CORE_SIZE, CORE_LIFE = 44, .12, 20, .07
SPARKS, CHIPS = 14, 8


def new_burst(r):
    ps = [(GLOW, CX, CY, CX, CY, FLASH_LIFE, FLASH_SIZE, WHITE), (GLOW, CX, CY, CX, CY, CORE_LIFE, CORE_SIZE, WHITE)]
    for i in range(DOME_DOTS):   # the halo: dots fly from the body to the rim of the half-ellipse inscribed in the blast box
        a = math.pi * (i + .5) / DOME_DOTS
        ps.append((FLAME, CX, CY, CX + math.cos(a) * DOME_RX, FLOOR - math.sin(a) * DOME_RY, DOME_LIFE, DOME_SIZE, WHITE if i % 2 == 0 else PALE))
    for i in range(26):          # the old flat spray, kept
        side = 1 if i % 2 == 0 else -1
        c = WHITE if r.random() < .4 else PALE
        ps.append((FLAME, CX + rng_range(r, -4, 4), CY, CX + side * rng_range(r, 10, 38), FLOOR - rng_range(r, 2, 30), rng_range(r, .16, .28), rng_range(r, 1, 3), c))
    for i in range(SPARKS):      # embers in the eye colour, thrown wide and high inside the box
        side = 1 if i % 2 == 0 else -1
        ps.append((FLAME, CX + rng_range(r, -3, 3), CY, CX + side * rng_range(r, 14, 39), FLOOR - rng_range(r, 10, 31), rng_range(r, .22, .34), 2, EYE if i % 3 else WHITE))
    for i in range(CHIPS):       # its own shell, flung and falling to the floor
        side = 1 if i % 2 == 0 else -1
        x = CX + side * rng_range(r, 12, 36)
        ps.append((GRIT, CX + rng_range(r, -4, 4), CY, x, FLOOR - 1, rng_range(r, .30, .42), rng_range(r, 2, 3), SHELL_LIT if i % 2 else SHELL))
    for i in range(10):          # steam, a little longer
        x, y = CX + rng_range(r, -30, 30), FLOOR - rng_range(r, 4, 24)
        p = r.random(); c = PALE if p < .4 else GREY if p < .8 else DARK
        ps.append((SMOKE, x, y, x, y - rng_range(r, 8, 16), rng_range(r, .40, .60), rng_range(r, 4, 7), c))
    return ps


def background():
    im = Image.new("RGBA", (CELL_W, CELL_H))
    px = im.load()
    for y in range(CELL_H):
        for x in range(CELL_W):
            if y >= FLOOR + 3: c = GROUND
            elif y >= FLOOR: c = GRASS
            else:
                k = y / FLOOR
                c = tuple(int(SKY_TOP[i] + (SKY_LOW[i] - SKY_TOP[i]) * k) for i in range(3))
            px[x, y] = c + (255,)
    return im


def draw_particle(px, kind, x, y, s, rgb, a):
    if s < .5: return
    col = [int(c * 255) for c in rgb]
    x0, y0 = round(x - s / 2), round(y - s / 2)
    n = max(1, round(s))
    for yy in range(y0, y0 + n):
        for xx in range(x0, x0 + n):
            if not (0 <= xx < CELL_W and 0 <= yy < CELL_H): continue
            aa = a
            if kind in (GLOW, SMOKE):   # SoftDot: a round soft texture
                d = math.hypot(xx + .5 - x, yy + .5 - y) / (s / 2)
                if d >= 1: continue
                aa = a * (1 - d * d)
            base = px[xx, yy]
            if kind == GLOW: px[xx, yy] = tuple(min(255, int(base[i] + col[i] * aa)) for i in range(3)) + (255,)
            else: px[xx, yy] = tuple(int(base[i] * (1 - aa) + col[i] * aa) for i in range(3)) + (255,)


def render(ps, t, with_box):
    im = background()
    if t < 0:
        im.alpha_composite(SHEET.crop((WHITE_FRAME * FRAME_W, 0, (WHITE_FRAME + 1) * FRAME_W, FRAME_H)), (CX - 12, FLOOR - FRAME_H))
    px = im.load()
    for order in (SMOKE, GRIT, FLAME, GLOW):   # the system draws normal blends first (smoke, grit, flame), then additive
        for kind, sx, sy, ex, ey, life, size, rgb in ps:
            if kind != order or t < 0 or t >= life: continue
            u = t / life
            x, y = sx + (ex - sx) * u, sy + (ey - sy) * u
            if kind == FLAME: s, a = size * (1 - u), 1
            elif kind == SMOKE: s, a = size * (1 + u), .7 * (1 - u)
            elif kind == GRIT: s, a = size, .7 * (1 - u)
            else: s, a = size * (1 - .4 * u), .7 * (1 - u) ** 2
            draw_particle(px, kind, x, y, s, rgb, a)
    if with_box:   # the lethal box, as the F1 debug view draws it
        for x in range(BOX[0], BOX[2] + 1):
            for y in (BOX[1], BOX[3]): px[x, y] = (255, 255, 0, 255)
        for y in range(BOX[1], BOX[3] + 1):
            for x in (BOX[0], BOX[2]): px[x, y] = (255, 255, 0, 255)
    return im


TIMES = (-.05, .02, .04, .07, .10, .15, .22, .35)
LABELS = "wind-up end, 0.02 s, 0.04 s, 0.07 s, 0.10 s, 0.15 s, 0.22 s, 0.35 s"


def strip(ps, with_box):
    out = Image.new("RGBA", (CELL_W * len(TIMES), CELL_H))
    for i, t in enumerate(TIMES): out.paste(render(ps, t, with_box), (i * CELL_W, 0))
    return out


def main():
    rows = [strip(old_burst(random.Random(559)), False), strip(new_burst(random.Random(559)), False), strip(new_burst(random.Random(559)), True)]
    sheet = Image.new("RGBA", (rows[0].width, CELL_H * 3 + 4), (0, 0, 0, 255))
    for i, row in enumerate(rows): sheet.paste(row, (0, i * (CELL_H + 2)))
    sheet.save(OUT / "beetle-burst-strip.png")
    sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(OUT / "beetle-burst-strip-x3.png")
    print("rows: old / proposed / proposed with the lethal box (80x32); columns:", LABELS)


main()
