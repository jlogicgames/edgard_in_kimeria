#!/usr/bin/env python3
"""Validate Trigger -> mechanism links in every Tiled map, with errors that name the object.

A Trigger links to a mechanism (Actionable, Escalator or Ladder, in SpawnPoints) through its
`target` object property. A Trigger with no `target` is a legacy name link and must match by name.
The rules mirror Level.spawnLevers, so a map that passes here loads in the game.
"""
import argparse
from pathlib import Path
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
