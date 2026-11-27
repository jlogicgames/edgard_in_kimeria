#!/usr/bin/env python3
"""Near foreground pieces (D113): copy-only stamping of hand-typed rows, like pasting a drawn tile with transparency.

The drawing is in the typed files next to this script: the glyphs (`leaf-*.txt`, `pinna.txt`, `rachis.txt`, `stem.txt`, every row
typed by hand) and the placement lists (`*.spec`, every position typed by hand). This script only copies: each listed glyph's characters
go onto a canvas at the listed x, y, '.' leaves what is under it and a later entry lies in front. One rule keeps layering clean:
a soft (partial-alpha) edge character that lands on an opaque shape becomes that shape's dark edge `a` (g, h) or leaves it as it
is (i, j) instead of punching a see-through hole in it. Then the canvas is sliced into the piece and its `-top` / `-foot` (D111).
It draws no shape, adds no noise and applies no filter.

    python3 art/parallax/forest/near/stamp.py SPEC [SPEC ...]   # grid text on stdout: the pieces that end fg.grid

SPEC lines (file names relative to the spec):
    canvas W H X0 Y0            the canvas, its column 0 at piece x X0 and its row 0 at piece y Y0 (room for the -top and -foot)
    glyph NAME FILE [BLOCK]     the typed rows of FILE (only those under '# BLOCK' when given)
    recolour NAME FROM OLD NEW  NAME is glyph FROM with the characters OLD swapped for NEW (a palette swap)
    put NAME X Y                copy glyph NAME with its top-left at piece x X, y Y
    piece NAME WT HT KIND [OPT] the piece, WT x HT tiles; KIND hang (with a -top), stand (with a -foot), both or plain (neither:
                                the art runs off the piece's bottom itself); OPT is passed to the grid's piece line (blur=R)
"""
import sys
from pathlib import Path

TILE = 16
SOFT = 'ghij'          # the fg.grid colours with partial alpha
DARK_EDGE = 'a'        # what a soft g or h becomes over an opaque shape
BLEED_X = 32           # a -top or -foot is 2 x 32 px wider than its piece (parallax_mock.with_bleed)
TOP_ROWS = 24          # a -top covers piece y -24 .. 7; the hanging piece leaves its rows 0 .. 7 empty
EMPTY_TOP = 8
FOOT_ROWS = 16
BLEED_TILES = 2 * BLEED_X // TILE                # the extra width of a -top or -foot, in tiles
TOP_TILES = (TOP_ROWS + EMPTY_TOP) // TILE       # their heights, in tiles
FOOT_TILES = FOOT_ROWS // TILE


def read_spec(spec):
    here, glyphs, puts, piece, canvas = Path(spec).parent, {}, [], None, None
    for line in Path(spec).read_text(encoding='utf-8').splitlines():
        words = line.split()
        if not words or words[0].startswith('#'):
            continue
        kind, args = words[0], words[1:]
        if kind == 'canvas':
            canvas = tuple(int(v) for v in args)
        elif kind == 'glyph':
            name, file, *block = args
            glyphs[name] = typed_rows(here / file, block[0] if block else None)
        elif kind == 'recolour':
            name, source, old, new = args
            glyphs[name] = [row.translate(str.maketrans(old, new)) for row in glyphs[source]]
        elif kind == 'put':
            name, x, y = args
            puts.append((name, int(x), int(y)))
        elif kind == 'piece':
            name, wtiles, htiles, shape, *options = args
            piece = (name, int(wtiles), int(htiles), shape, options)
        else:
            raise ValueError(f'{spec}: unknown line {line!r}')
    return canvas, glyphs, puts, piece


def typed_rows(path, block):
    rows, current, blocks = [], None, {}
    for row in path.read_text(encoding='utf-8').splitlines():
        if row.startswith('#'):
            current = row[1:].strip()
            blocks[current] = []
        elif row:
            (blocks[current] if current else rows).append(unpack(row))
    return blocks[block] if block else rows


def unpack(row):
    """A typed row as characters. A row that starts with a digit is typed as runs, `<count><char>` separated by spaces
    (`12. 1h 1g 20b`): a shorthand for the same hand-typed pixels, nothing is added."""
    if not row[0].isdigit():
        return row
    return ''.join(run[-1] * int(run[:-1]) for run in row.split())


def stamp(spec):
    (width, height, x0, y0), glyphs, puts, (name, wtiles, htiles, kind, options) = read_spec(spec)
    canvas = [['.'] * width for _ in range(height)]
    for glyph, x, y in puts:
        for dy, row in enumerate(glyphs[glyph]):
            for dx, char in enumerate(row):
                cx, cy = x - x0 + dx, y - y0 + dy
                if char == '.' or not (0 <= cx < width and 0 <= cy < height):
                    continue
                under = canvas[cy][cx]
                if char in SOFT and under not in SOFT + '.':
                    char = DARK_EDGE if char in 'gh' else under
                canvas[cy][cx] = char
    rows = [''.join(row) for row in canvas]
    w, h, px, py = wtiles * TILE, htiles * TILE, -x0, -y0
    body = [row[px:px + w] for row in rows[py:py + h]]
    if kind in ('hang', 'both'):
        body = ['.' * w if i < EMPTY_TOP else row for i, row in enumerate(body)]
    out = [' '.join(['piece', name, str(wtiles), str(htiles), *options])] + body
    wide = lambda r: r[px - BLEED_X:px + w + BLEED_X]
    if kind in ('hang', 'both'):
        out += [f'piece {name}-top {wtiles + BLEED_TILES} {TOP_TILES}'] + [wide(r) for r in rows[py - TOP_ROWS:py + EMPTY_TOP]]
    if kind in ('stand', 'both'):
        out += [f'piece {name}-foot {wtiles + BLEED_TILES} {FOOT_TILES}'] + [wide(r) for r in rows[py + h:py + h + FOOT_ROWS]]
    return '\n'.join(out)


if __name__ == '__main__':
    print('\n'.join(stamp(spec) for spec in sys.argv[1:]))
