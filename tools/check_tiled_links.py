#!/usr/bin/env python3
"""Validate Trigger -> mechanism links in every Tiled map, with errors that name the object.

A Trigger links to a mechanism (Actionable, Escalator or Ladder, in SpawnPoints) through its
`target` object property. A Trigger with no `target` is a legacy name link and must match by name.
The rules mirror Level.spawnLevers, so a map that passes here loads in the game.
"""
import argparse
import math
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAPS = ROOT / 'assets' / 'tiles'
MECHANISMS = ('Actionable', 'Escalator', 'Ladder')
ACTIONABLE_KINDS = ('Torch', 'Wall')
# Rhythmic forest traps (D46). Mirrors Puffball.validate / ThornVine.validate / FallingPlatform.Skin in Java.
PUFFBALL_SWELL_ASSISTED = 0.8 * 1.5  # TrapWarnings.DEFAULT_WARNING_SECONDS with the longer-warnings assist
PUFFBALL_MIN_CALM = 0.8
VINE_MIN_PERIOD, VINE_AMPLITUDE = 3.5, (35.0, 60.0)
SKINS = ('', 'log')
VINE_HALF_WIDTH = 4.0
PUFFBALL_CLOUD, PUFFBALL_INSET = 24.0, 2.0
HERO_BOX = (11.0, 22.0, 18.0, 26.0)  # Player hitbox offset and size


def number(o, key, default, errors):
    raw = prop(o, key).strip()
    if not raw:
        return default
    try:
        value = float(raw)
    except ValueError:
        errors.append(f"{kind(o)} {describe(o)}: property {key} = '{raw}' is not a number")
        return None
    if not math.isfinite(value):
        errors.append(f"{kind(o)} {describe(o)}: property {key} = '{raw}' is not finite")
        return None
    return value


def box(o):
    return (float(o.get('x', 0)), float(o.get('y', 0)), float(o.get('width', 0)), float(o.get('height', 0)))


def overlaps(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def segment_hits(seg, rect):
    """Slab test of a segment against a box (mirror of ThornVine.segmentHitsBox)."""
    (x0, y0, x1, y1), (minx, miny, maxx, maxy) = seg, rect
    t0, t1, dx, dy = 0.0, 1.0, x1 - x0, y1 - y0
    for p, q in ((-dx, x0 - minx), (dx, maxx - x0), (-dy, y0 - miny), (dy, maxy - y0)):
        if p == 0:
            if q < 0:
                return False
        else:
            t = q / p
            if p < 0:
                if t > t1:
                    return False
                t0 = max(t0, t)
            else:
                if t < t0:
                    return False
                t1 = min(t1, t)
    return True


def vine_ever_lethal(o, amplitude, area):
    x, y, w, h = box(o)
    ax = x + w / 2
    grown = (area[0] - VINE_HALF_WIDTH, area[1] - VINE_HALF_WIDTH,
             area[0] + area[2] + VINE_HALF_WIDTH, area[1] + area[3] + VINE_HALF_WIDTH)
    steps = int(amplitude * 2 / 0.5) + 1
    for i in range(steps + 1):
        a = math.radians(-amplitude + min(i * 0.5, amplitude * 2))
        sin, cos = math.sin(a), math.cos(a)
        if segment_hits((ax + h * 0.5 * sin, y + h * 0.5 * cos, ax + h * sin, y + h * cos), grown):
            return True
    return False


def puffball_ever_lethal(o, area):
    x, y, w, h = box(o)
    cloud = (x + w / 2 - PUFFBALL_CLOUD / 2 + PUFFBALL_INSET, y + h - PUFFBALL_CLOUD + PUFFBALL_INSET,
             PUFFBALL_CLOUD - 2 * PUFFBALL_INSET, PUFFBALL_CLOUD - 2 * PUFFBALL_INSET)
    return overlaps(cloud, area)


def standing_places(root, spawn, o):
    """Where the hero must be able to stand in the room owning o: entrance body, exit and checkpoints."""
    rooms = [r for r in layers(root).get('Rooms', []) if kind(r) == 'Room']
    cx, cy = box(o)[0] + box(o)[2] / 2, box(o)[1] + box(o)[3] / 2
    places = []
    owner = None
    for r in rooms:
        rx, ry, rw, rh = box(r)
        if rx <= cx < rx + rw and ry <= cy < ry + rh:
            owner = r
    if owner is None:
        start = next((p for p in spawn if kind(p) == 'Player'), None)
        if start is not None:
            places.append(('entrance', (box(start)[0] + HERO_BOX[0], box(start)[1] + HERO_BOX[1], HERO_BOX[2], HERO_BOX[3])))
        bounds = None
    else:
        bounds = box(owner)
        for m in layers(root).get('Rooms', []):
            if m.get('name') != owner.get('name'):
                continue
            if kind(m) == 'RoomStart':
                places.append(('entrance', (box(m)[0] + HERO_BOX[0], box(m)[1] + HERO_BOX[1], HERO_BOX[2], HERO_BOX[3])))
            elif kind(m) == 'RoomExit':
                places.append(('exit', box(m)))
    for c in spawn:
        if kind(c) == 'Checkpoint':
            px, py = box(c)[0] + box(c)[2] / 2, box(c)[1] + box(c)[3] / 2
            if bounds is None or (bounds[0] <= px < bounds[0] + bounds[2] and bounds[1] <= py < bounds[1] + bounds[3]):
                places.append(('checkpoint', box(c)))
    return places


def check_rhythm_traps(root, spawn):
    errors = []
    for o in spawn:
        if kind(o) == 'Puffball':
            period, cloud = number(o, 'period', 4.0, errors), number(o, 'cloud', 1.0, errors)
            phase = number(o, 'phase', 0.0, errors)
            if period is not None and cloud is not None and (
                    cloud <= 0 or period < cloud + PUFFBALL_SWELL_ASSISTED + PUFFBALL_MIN_CALM):
                errors.append(f"Puffball {describe(o)}: period {period} must be at least cloud {cloud} + assisted swell "
                              f"{PUFFBALL_SWELL_ASSISTED} + minimum calm {PUFFBALL_MIN_CALM}")
            elif None not in (period, cloud, phase):
                errors.extend(f"Puffball {describe(o)}: its danger area covers the room {name}"
                              for name, area in standing_places(root, spawn, o) if puffball_ever_lethal(o, area))
        elif kind(o) == 'ThornVine':
            period, amplitude = number(o, 'period', 4.5, errors), number(o, 'amplitude', 50.0, errors)
            phase = number(o, 'phase', 0.0, errors)
            if period is not None and period < VINE_MIN_PERIOD:
                errors.append(f"ThornVine {describe(o)}: period {period} is below the minimum {VINE_MIN_PERIOD}")
            if amplitude is not None and not VINE_AMPLITUDE[0] <= amplitude <= VINE_AMPLITUDE[1]:
                errors.append(f"ThornVine {describe(o)}: amplitude {amplitude} is outside {VINE_AMPLITUDE[0]}..{VINE_AMPLITUDE[1]}")
            elif None not in (period, amplitude, phase) and period >= VINE_MIN_PERIOD:
                errors.extend(f"ThornVine {describe(o)}: its danger area covers the room {name}"
                              for name, area in standing_places(root, spawn, o) if vine_ever_lethal(o, amplitude, area))
        elif kind(o) == 'FallingPlatform' and prop(o, 'skin') not in SKINS:
            errors.append(f"FallingPlatform {describe(o)}: unknown skin '{prop(o, 'skin')}' (expected log)")
    return errors


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
    errors.extend(check_rhythm_traps(root, spawn))
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
