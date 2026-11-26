#!/usr/bin/env python3
"""One-time migration of #648: raises the floor of every forest room by one tile (16 px), leaving 64 px of soil under it.

    python3 tools/raise_floor.py            # rewrite assets/tiles/forest-{0,1,2}.tmx
    python3 tools/parallax_game.py          # then rewrite the scenery and foreground layers and retile the play layer

World Y grows downward, so "up" is y - 16. The rule: whatever is measured from the floor moves with it, so jumps, patrols,
climbs and trap reaches keep their shape; whatever hangs from the ceiling stays. In detail:
- an edge or a point at room y `CEILING_BAND` or lower on screen moves up by `RAISE`; the room's bottom edge stays;
- a collision block hanging from the ceiling stays whole while its underside is no lower than `CEILING_BLOCK_BOTTOM`; a deeper
  one (a wall that reaches the floor, a low passage) keeps its top and its underside follows the floor;
- room rectangles, hint zones, falling stones, hanging vines, crowns and the kill lines under the screen stay, as do the objects
  of `KEPT` (the top of a climb, hand reviewed); a stone's trigger zone keeps its top and its floor end follows the floor;
- the play layer follows the collisions: every column moves up one row below the ceiling band, and the two lowest rows are soil.

Every object that neither stands on the floor nor hangs from the ceiling is moved with the floor and listed as "review", so a
person can check it. A map whose floor is already raised is left alone (the script refuses to raise twice).
"""
import re
import sys
from pathlib import Path

TILE = 16
RAISE = TILE
ROOM_H = 368
OLD_FLOOR = 320
CEILING_BAND = 64                      # room y: an edge above it (smaller y) belongs to the ceiling
CEILING_BLOCK_BOTTOM = 128             # the deepest underside of a block that hangs high above the play
BOSS_CEILING = ('forest-2', (105, 106, 107))   # the boss chamber's ceiling and its two walls, whose top is at y 80
BODY_GID = 43                          # the play tileset's soil body (parallax_game.GROUND)
BURIED_GIDS = (71, 72, 73)             # its buried bottom row
MAPS = ('forest-0', 'forest-1', 'forest-2')
TILES = Path(__file__).resolve().parents[1] / 'assets/tiles'
STAYING_TYPES = ('Room', 'Hint', 'Deadline', 'FallingStone', 'HangingVine')
ZONE_TYPES = ('FallingStone',)         # stays, but its trigger zone ends on the floor
# (map, object id) -> why it stays although it lies below the ceiling band or is one a person must decide on
KEPT = {('forest-1', 97): 'the top branch of the rotten-branch climb (the issue: high platforms stay)',
        ('forest-1', 7): 'the checkpoint on that top branch',
        ('forest-1', 136): 'a hanging hive stays on its bough and falls 16 px less (raised, it fades into the moon, D76)'}
# (map, object id) of a dropping ladder whose raised place stays and whose drop is RAISE shorter, landing on the raised floor:
# raised, forest-0's first ladder fades into the moon (D76 outline 0.17 under 0.28)
SHORTER_DROP = (('forest-0', 45),)
# map -> {(row, column): gid} of the canopy clump's ragged bottom row added beside the stone at forest-2 x 2224, behind its lip: the trees
# that darkened its backdrop rose out of it, and the lip would fade into a passing cloud (D76 outline 0.22; 0.358 with them). The stone's own column
# stays open: its lip hangs under the block.
ADDED_TILES = {'forest-2': {(3, 138): 41, (3, 140): 60}}


def lifted(edge):
    """Where an absolute room y goes: up by RAISE below the ceiling band, unmoved above it and at the room's bottom or past it."""
    return edge - RAISE if CEILING_BAND <= edge < ROOM_H else edge


def number(value):
    return str(int(value)) if value == int(value) else repr(value)


def attribute(tag, name, default=None):
    found = re.search(r'\s%s="([^"]*)"' % name, tag)
    return found.group(1) if found else default


def with_attribute(tag, name, value):
    return re.sub(r'(\s%s=")[^"]*(")' % name, lambda m: m.group(1) + value + m.group(2), tag, count=1)


def with_property(body, name, change):
    return re.sub(r'(<property name="%s"[^>]*\svalue=")([^"]*)(")' % name,
                  lambda m: m.group(1) + number(change(float(m.group(2)))) + m.group(3), body, count=1)


def zone_lifted(body):
    top = re.search(r'name="zoneY"[^>]*\svalue="([^"]*)"', body)
    height = re.search(r'name="zoneHeight"[^>]*\svalue="([^"]*)"', body)
    if not top or not height:
        return body
    y0, y1 = float(top.group(1)), float(top.group(1)) + float(height.group(1))
    new0, new1 = lifted(y0), lifted(y1)
    body = with_property(body, 'zoneY', lambda _: new0)
    return with_property(body, 'zoneHeight', lambda _: new1 - new0)


def move_object(map_name, group, offset_y, block, report, kept_cells):
    """The new text of one `<object>` block of `group`."""
    tag = re.match(r'<object\b[^>]*?/?>', block).group(0)
    body = block[len(tag):]
    ident = int(attribute(tag, 'id'))
    kind = attribute(tag, 'type', '')
    x, y = float(attribute(tag, 'x')), float(attribute(tag, 'y')) + offset_y
    w, h = float(attribute(tag, 'width', '0')), float(attribute(tag, 'height', '0'))
    label = f'{map_name} #{ident} {kind or group} "{attribute(tag, "name", "")}" x {number(x)} y {number(y)} {number(w)}x{number(h)}'
    new_tag = tag
    if (map_name, ident) in SHORTER_DROP:
        body = with_property(body, 'dropDistance', lambda value: value - RAISE)
        report.append(f'kept, drop {RAISE} px shorter (by hand): {label}')
    elif (map_name, ident) in KEPT:
        report.append(f'kept (by hand): {label}: {KEPT[(map_name, ident)]}')
    elif group == 'Collisions' and (y == 0 or map_name == BOSS_CEILING[0] and ident in BOSS_CEILING[1]):
        bottom = y + h
        if bottom <= CEILING_BLOCK_BOTTOM:
            report.append(f'stays (ceiling block): {label}')
            kept_cells.update((r, c) for r in range(int(y // TILE), int((bottom - 1) // TILE) + 1)
                              for c in range(int(x // TILE), int((x + w - 1) // TILE) + 1))
        else:
            kept_cells.update((int(y // TILE), c) for c in range(int(x // TILE), int((x + w - 1) // TILE) + 1))
            new_tag = with_attribute(tag, 'height', number(lifted(bottom) - y))
            if bottom != OLD_FLOOR:
                report.append(f'review (ceiling block, its underside follows the floor): {label}')
    elif kind in STAYING_TYPES or 'value="crown"' in body:
        if kind in ZONE_TYPES:
            body = zone_lifted(body)
    elif group == 'Collisions' and h:
        top, bottom = lifted(y), lifted(y + h)
        new_tag = with_attribute(tag, 'y', number(top - offset_y))
        if bottom - top != h:
            new_tag = with_attribute(new_tag, 'height', number(bottom - top))
        if y < OLD_FLOOR and y + h != OLD_FLOOR:
            report.append(f'review (platform or ledge off the floor, moved): {label}')
    elif y < CEILING_BAND:
        if 'value="trunk"' in body or 'value="snag"' in body:   # a tree stands on the floor: its top moves although it is high
            new_tag = with_attribute(tag, 'y', number(y - RAISE - offset_y))
            report.append(f'review (tree reaching the ceiling band, moved with its floor): {label}')
        else:
            report.append(f'unclassified (left alone): {label}')
    else:
        new_tag = with_attribute(tag, 'y', number(y - RAISE - offset_y))
        body = zone_lifted(body)
        # a tile object's y is its bottom edge; none of the gameplay objects is one, so y is the top edge here
        if abs(y + h - OLD_FLOOR) > 1 and kind not in ('RoomStart', 'RoomExit', 'Player'):
            report.append(f'review (not standing on the floor line, moved): {label}')
    return new_tag + body


def hanging_cells(rows):
    """(row, column) of the drawn tiles that hang from the room top and end no lower than `CEILING_BLOCK_BOTTOM`: ceiling scenery."""
    cells = set()
    for c in range(len(rows[0])):
        run = 0
        while run < len(rows) and rows[run][c]:
            run += 1
        if run * TILE <= CEILING_BLOCK_BOTTOM:
            cells.update((r, c) for r in range(run))
    return cells


def move_rows(rows, kept_cells):
    """The play rows with the floor one row higher: `kept_cells` (ceiling blocks), the hanging scenery and the ceiling band
    stay."""
    kept_cells = kept_cells | hanging_cells(rows)
    height = len(rows)
    first = CEILING_BAND // TILE
    result = [list(r) for r in rows]
    for r in range(first, height - 1):
        for c in range(len(rows[0])):
            if (r, c) not in kept_cells and (r + 1, c) not in kept_cells:   # a ceiling tile is never pulled up
                below = rows[r + 1][c]
                result[r][c] = BODY_GID if r + 1 == height - 1 and below in BURIED_GIDS else below
    return result


def raise_map(text, map_name, report):
    collisions = re.search(r'<objectgroup [^>]*name="Collisions".*?</objectgroup>', text, re.S).group(0)
    if not any(attribute(tag, 'y') == str(OLD_FLOOR) for tag in re.findall(r'<object\b[^>]*>', collisions)):
        raise ValueError(f'{map_name}: no collision at the old floor line y {OLD_FLOOR}; the floor is already raised')
    kept_cells = set()

    def group_text(match):
        block = match.group(0)
        head = re.match(r'<objectgroup\b[^>]*>', block).group(0)
        name = attribute(head, 'name')
        if name.startswith('foreground'):
            return block
        offset_y = float(attribute(head, 'offsety', '0'))
        return re.sub(r'<object\b[^>]*?/>|<object\b[^>]*?>.*?</object>',
                      lambda m: move_object(map_name, name, offset_y, m.group(0), report, kept_cells), block, flags=re.S)

    text = re.sub(r'<objectgroup\b[^>]*>.*?</objectgroup>', group_text, text, flags=re.S)
    data = re.search(r'(<layer [^>]*name="Background"[^>]*>\s*<data encoding="csv">\s*)(.*?)(\s*</data>)', text, re.S)
    rows = [[int(v) for v in line.strip().rstrip(',').split(',')] for line in data.group(2).strip().splitlines()]
    rows = move_rows(rows, kept_cells)
    for (r, c), gid in ADDED_TILES.get(map_name, {}).items():
        rows[r][c] = gid
    csv = ',\n'.join(','.join(str(v) for v in row) for row in rows)
    return text[:data.start(2)] + csv + text[data.end(2):]


def main():
    for map_name in MAPS:
        path = TILES / f'{map_name}.tmx'
        report = []
        path.write_text(raise_map(path.read_text(encoding='utf-8'), map_name, report), encoding='utf-8')
        print('\n'.join(report))
    return 0


if __name__ == '__main__':
    sys.exit(main())
