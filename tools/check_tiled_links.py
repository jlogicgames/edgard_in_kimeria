#!/usr/bin/env python3
"""Validate Trigger -> mechanism links in every Tiled map, with errors that name the object.

A Trigger links to a mechanism (Actionable, Escalator or Ladder, in SpawnPoints) through its
`target` object property. A Trigger with no `target` is a legacy name link and must match by name.
The rules mirror Level.spawnLevers, so a map that passes here loads in the game.
"""
import argparse
import math
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAPS = ROOT / 'assets' / 'tiles'
MECHANISMS = ('Actionable', 'Escalator', 'Ladder')
ACTIONABLE_KINDS = ('Torch', 'Wall')


def describe(o):
    return f"'{o.get('name', '')}' [{o.get('id')}] at ({int(float(o.get('x', 0)))}, {int(float(o.get('y', 0)))})"


def kind(o):
    return o.get('type') or o.get('class') or ''


def prop(o, key):
    for p in o.findall('properties/property'):
        if p.get('name') == key:
            return p.get('value', '')
    return ''


def layers(root):
    return {g.get('name', ''): g.findall('object') for g in root.findall('objectgroup')}


def check_map(path):
    root = ET.parse(path).getroot()
    groups = layers(root)
    spawn, collisions = groups.get('SpawnPoints', []), groups.get('Collisions', [])
    by_id = {o.get('id'): o for o in spawn}
    errors = []
    for o in collisions:
        if kind(o) in MECHANISMS:
            errors.append(f"{describe(o)}: {kind(o)} is in the Collisions layer; mechanisms must be in SpawnPoints")
    for o in spawn:
        if kind(o) == 'Actionable' and prop(o, 'type') not in ACTIONABLE_KINDS:
            errors.append(f"{describe(o)}: Actionable needs property type = {' or '.join(ACTIONABLE_KINDS)}")
    for trigger in (o for o in spawn if kind(o) == 'Trigger'):
        target = prop(trigger, 'target').strip()
        if target:
            mechanism = by_id.get(target)
            if mechanism is None:
                where = 'is in the Collisions layer' if any(c.get('id') == target for c in collisions) \
                    else 'does not exist'
                errors.append(f"Trigger {describe(trigger)}: target {target} {where}")
            elif kind(mechanism) not in MECHANISMS:
                errors.append(f"Trigger {describe(trigger)}: target {describe(mechanism)} has type "
                              f"'{kind(mechanism)}', expected {', '.join(MECHANISMS)}")
            continue
        same_name = [o for o in spawn if o.get('name') == trigger.get('name') and kind(o) in MECHANISMS]
        solid = [o for o in same_name if kind(o) != 'Escalator'] or same_name
        if len(solid) != 1:
            errors.append(f"Trigger {describe(trigger)}: no 'target' set and {len(solid)} mechanisms share its name "
                          "(set the 'target' property, or give it exactly one same-named mechanism)")
    errors += check_falling_traps(root, groups)
    return [f"{path.name}: {e}" for e in errors]


# FallingBranch / FallingStone (D45, #232): mirrors core/.../world/FallingTrapLayout.java for maps.
FALLING_ZONE = ('zoneX', 'zoneY', 'zoneWidth', 'zoneHeight')
FALLING_JAVA = ROOT / 'core' / 'src' / 'main' / 'java' / 'com' / 'jlogicsoftware' / 'kimeria' / 'entity' / 'objects' / 'FallingStone.java'
HERO_BODY = (18, 26, 11, 22)  # Player hitbox: offset x, offset y, width, height
GRAVITY, MIN_FLIGHT = 900.0, 0.3  # FallingTrap.GRAVITY / MIN_FLIGHT_SECONDS


def falling_stone_skins():
    """The skin names are the string keys of FallingStone.SKINS: one source of truth, read from the Java class."""
    text = FALLING_JAVA.read_text(encoding='utf-8')
    block = re.search(r'SKINS\s*=\s*java\.util\.Map\.of\((.*?)\);', text, re.S)
    return tuple(re.findall(r'"([^"]+)"\s*,\s*new Skin\(', block.group(1))) if block else ()


def _num(o, key):
    return float(o.get(key, 0))


def _rect(o):
    return (_num(o, 'x'), _num(o, 'y'), _num(o, 'width'), _num(o, 'height'))


def _x_overlap(a, b):
    return a[0] < b[0] + b[2] and a[0] + a[2] > b[0]


def _overlap(a, b):
    return _x_overlap(a, b) and a[1] < b[1] + b[3] and a[1] + a[3] > b[1]


def _inside(inner, outer):
    return (inner[0] >= outer[0] and inner[1] >= outer[1]
            and inner[0] + inner[2] <= outer[0] + outer[2] and inner[1] + inner[3] <= outer[1] + outer[3])


def check_falling_traps(root, groups):
    spawn, collisions = groups.get('SpawnPoints', []), groups.get('Collisions', [])
    traps = [o for o in spawn if kind(o) in ('FallingBranch', 'FallingStone')]
    if not traps:
        return []
    markers = groups.get('Rooms', [])
    rooms = [(m.get('name'), _rect(m)) for m in markers if kind(m) == 'Room']
    starts = {m.get('name'): (_num(m, 'x'), _num(m, 'y')) for m in markers if kind(m) == 'RoomStart'}
    if not rooms:
        tw, th = int(root.get('tilewidth', 16)), int(root.get('tileheight', 16))
        player = next((o for o in spawn if kind(o) == 'Player'), None)
        rooms = [('level', (0.0, 0.0, int(root.get('width', 0)) * tw, int(root.get('height', 0)) * th))]
        starts = {'level': (_num(player, 'x'), _num(player, 'y')) if player is not None else (0.0, 0.0)}

    def owner(o):
        r = _rect(o)
        cx, cy = r[0] + r[2] / 2, r[1] + r[3] / 2
        for name, b in rooms:
            if b[0] <= cx < b[0] + b[2] and b[1] <= cy < b[1] + b[3]:
                return name, b
        return None, None

    skins = falling_stone_skins()
    errors = []
    for o in traps:
        label = f"{kind(o)} {describe(o)}"
        stone = kind(o) == 'FallingStone'
        body = _rect(o)
        if not (body[2] > 0 and body[3] > 0):
            errors.append(f"{label}: needs a positive size"); continue
        name, room = owner(o)
        if room is None:
            errors.append(f"{label}: object outside rooms"); continue
        if not _inside(body, room):
            errors.append(f"{label}: must start inside its room (never in the D38 ceiling slab)"); continue
        missing = [k for k in FALLING_ZONE if not prop(o, k).strip()]
        if missing:
            errors.append(f"{label}: missing zone propert{'y' if len(missing) == 1 else 'ies'} {', '.join(missing)}"); continue
        try:
            zone = tuple(float(prop(o, k)) for k in FALLING_ZONE)
        except ValueError:
            errors.append(f"{label}: zone properties must be numbers"); continue
        if not all(math.isfinite(v) for v in zone):
            errors.append(f"{label}: zone properties must be finite numbers"); continue
        if zone[2] <= 0 or zone[3] <= 0:
            errors.append(f"{label}: zone needs a positive width and height"); continue
        if not _inside(zone, room):
            errors.append(f"{label}: zone must lie inside its room"); continue
        if stone and (prop(o, 'skin') or 'forest').lower() not in skins:
            errors.append(f"{label}: unknown skin '{prop(o, 'skin')}', known: {', '.join(skins)}"); continue
        bottom = body[1] + body[3]
        same_room = [c for c in collisions if owner(c)[0] == name and kind(c) != 'QuickSand']
        floors = [_num(c, 'y') for c in same_room if _x_overlap(_rect(c), body) and _num(c, 'y') >= bottom]
        if not floors:
            errors.append(f"{label}: has no solid floor below it in its room"); continue
        landing = min(floors)
        flight = math.sqrt(2 * (landing - bottom) / GRAVITY)
        if flight < MIN_FLIGHT:
            errors.append(f"{label}: drop is too short (flight {flight:.2f} s, minimum {MIN_FLIGHT} s)"); continue
        mover = next((m for m in spawn if kind(m) in ('Actionable', 'Escalator', 'FallingPlatform')
                      and _x_overlap(_rect(m), body) and _num(m, 'y') < landing and _num(m, 'y') + _num(m, 'height') > bottom), None)
        if mover is not None:
            errors.append(f"{label}: a moving or switchable {kind(mover)} [{mover.get('id')}] stands in its fall; its landing could not be fixed")
            continue
        if not _x_overlap(zone, body):
            errors.append(f"{label}: zone must overlap the lethal column (the trap's x-range)")
        if zone[1] + zone[3] <= landing - HERO_BODY[3] or zone[1] >= landing:
            errors.append(f"{label}: zone must overlap the hero's body standing on the floor, or a walking hero can never trigger it")
        sx, sy = starts.get(name, (0.0, 0.0))
        hero = (sx + HERO_BODY[0], sy + HERO_BODY[1], HERO_BODY[2], HERO_BODY[3])
        if _overlap(hero, zone) and _x_overlap(hero, body):
            errors.append(f"{label}: the room entrance starts the hero inside the zone and the lethal column")
        if stone:
            under = any(_num(c, 'y') + _num(c, 'height') == body[1] and _x_overlap(_rect(c), body) and kind(c) != 'Platform'
                        for c in same_room)
            if body[1] != room[1] and not under:
                errors.append(f"{label}: a stone must start at the room top or directly under a solid block, or it pops into view in mid-air")
    return errors


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('maps', nargs='*', type=Path, help='maps to check (default: assets/tiles/*.tmx)')
    args = parser.parse_args(argv)
    errors = [e for m in (args.maps or sorted(MAPS.glob('*.tmx'))) for e in check_map(m)]
    for e in errors:
        print(e, file=sys.stderr)
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
