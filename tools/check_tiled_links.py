#!/usr/bin/env python3
"""Validate Trigger -> mechanism links in every Tiled map, with errors that name the object.

A Trigger links to a mechanism (Actionable, Escalator or Ladder, in SpawnPoints) through its
`target` object property. A Trigger with no `target` is a legacy name link and must match by name.
The rules mirror Level.spawnLevers, so a map that passes here loads in the game.
"""
import argparse
import re
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAPS = ROOT / 'assets' / 'tiles'
MECHANISMS = ('Actionable', 'Escalator', 'Ladder')
ACTIONABLE_KINDS = ('Torch', 'Wall')
# Proximity traps (D44): mirror ThornTrap.problem for what a map can say; the game also checks the hero entrance.
THORNS = ('ThornRoot', 'ThornTuft')


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


NUMBER = re.compile(r'[+-]?(\d+(\.\d*)?|\.\d+)')
DEFAULT_ZONE, MIN_ZONE, HEADROOM, FLOOR_TOLERANCE, ESCALATOR_TILE = 80.0, 64.0, 96.0, 0.5, 32.0


def number(o, key, fallback):
    raw = prop(o, key).strip()
    if raw == '':
        return fallback
    if not NUMBER.fullmatch(raw):
        raise ValueError(f"{key} '{raw}' is not a plain number")
    return float(raw)


def box(o):
    return [float(o.get(k, 0)) for k in ('x', 'y', 'width', 'height')]


def overlaps(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def swept(o):
    """Everything a mechanism can occupy while it moves (mirrors ThornTrap.sweptBounds), or None."""
    x, y, w, h = box(o)
    def lenient(key):
        raw = prop(o, key).strip()
        return float(raw) if NUMBER.fullmatch(raw) else 0.0
    t = kind(o)
    if t == 'FallingPlatform':
        return [x, y, w, h]
    if t == 'Ladder':
        return [x, y, w, h + max(0.0, lenient('dropDistance'))]
    if t == 'Escalator':
        neg, pos = max(0.0, lenient('offNeg')) * ESCALATOR_TILE, max(0.0, lenient('offPos')) * ESCALATOR_TILE
        if prop(o, 'isVertical').lower() == 'true':
            return [x, y - neg, w, h + neg + pos]
        return [x - neg, y, w + neg + pos, h]
    if t == 'Actionable' and prop(o, 'type') == 'Wall':
        return [x - w, y - h, 3 * w, 3 * h]
    return None


def thorn_problems(o, collisions, spawn=(), room=None):
    """Mirror of ThornTrap.problem for what a map can say. `room` is the (x, y, w, h) of the owning room, if known."""
    x, y, w, h = box(o)
    if w <= 0 or h <= 0:
        return ['needs positive bounds']
    try:
        zone = number(o, 'zone', DEFAULT_ZONE)
        left, right = number(o, 'zoneLeft', zone), number(o, 'zoneRight', zone)
    except ValueError as e:
        return [str(e)]
    if left < 0 or right < 0 or left + right <= 0:
        return ['needs a zone (zone, zoneLeft, zoneRight): not negative, at least one side used']
    if (0 < left < MIN_ZONE) or (0 < right < MIN_ZONE):
        return [f'needs each used zone side to be at least {int(MIN_ZONE)} px (zone, zoneLeft, zoneRight; 0 leaves a side unused)']
    floor = y + h
    env_left, env_right = x - max(left, MIN_ZONE), x + w + max(right, MIN_ZONE)
    if room and (env_left < room[0] or env_right > room[0] + room[2] or floor - HEADROOM < room[1]
                 or floor > room[1] + room[3]):
        return [f'needs its whole jump envelope ({int(MIN_ZONE)} px of floor either side, {int(HEADROOM)} px above) inside its room']
    spans = sorted((float(c.get('x', 0)), float(c.get('x', 0)) + float(c.get('width', 0)))
                   for c in collisions if kind(c) != 'QuickSand' and abs(float(c.get('y', 0)) - floor) < FLOOR_TOLERANCE)
    covered = env_left
    for start, end in spans:
        if start <= covered + FLOOR_TOLERANCE:
            covered = max(covered, end)
    if covered < env_right:
        return [f'must stand on open floor: floor under its whole footprint and {int(MIN_ZONE)} px either side '
                '(no pit, pillar or ledge edge beside it)']
    footprint = [x, y, w, h]
    air = [env_left, floor - HEADROOM, env_right - env_left, HEADROOM - 1.0]
    things = [box(c) for c in collisions if kind(c) != 'QuickSand'] + [s for s in map(swept, spawn) if s]
    for thing in things:
        if overlaps(thing, footprint):
            return ['has another solid object standing in its own column']
        if overlaps(thing, air):
            return [f'needs {int(HEADROOM)} px of free height over it and {int(MIN_ZONE)} px either side '
                    '(jumpable, not a one-tile corridor)']
    return []


def thorn_room(root, o):
    """The (x, y, w, h) of the room holding the object's centre, or the whole map, or None when the map has no size."""
    cx, cy = float(o.get('x', 0)) + float(o.get('width', 0)) / 2, float(o.get('y', 0)) + float(o.get('height', 0)) / 2
    for r in layers(root).get('Rooms', []):
        if kind(r) == 'Room':
            rx, ry, rw, rh = box(r)
            if rx <= cx < rx + rw and ry <= cy < ry + rh:
                return [rx, ry, rw, rh]
    if root.get('width') and root.get('tilewidth'):
        return [0.0, 0.0, int(root.get('width')) * int(root.get('tilewidth')), int(root.get('height')) * int(root.get('tileheight'))]
    return None


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
    for o in collisions:
        if kind(o) in THORNS:
            errors.append(f"{describe(o)}: {kind(o)} is in the Collisions layer; traps must be in SpawnPoints")
    for o in (o for o in spawn if kind(o) in THORNS):
        errors.extend(f"{kind(o)} {describe(o)} {e}" for e in thorn_problems(o, collisions, spawn, thorn_room(root, o)))
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
    return [f"{path.name}: {e}" for e in errors]


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
