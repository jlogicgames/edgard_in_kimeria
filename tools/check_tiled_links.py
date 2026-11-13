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
# Proximity traps (D44): mirror ThornTrap.problem for what a map can say; the game also checks the hero entrance.
THORNS = ('ThornRoot', 'ThornTuft')
# Explosive traps (D47): mirror ExplosiveTrap.problem (bypass = the thorn jump envelope, blast square clear of standing places).
EXPLOSIVES = ('ExplosiveStump', 'ExplosiveRoot')
BLAST_SIZE = 64.0
# Honey fungus pair (D63, #295): mirror FungusTrap.problem (thorn jump envelope, zone off the entrance, lethal cloud kept clear).
FUNGI = ('HoneyFungus', 'FalseHoneyFungus')
FUNGUS_ZONE_ABOVE, FUNGUS_MIN_HARMLESS_ZONE, FUNGUS_CLEARANCE, FUNGUS_CLOUD = 32.0, 24.0, 48.0, 24.0
# Rhythmic forest traps (D46). Mirrors Puffball.validate / ThornVine.validate / FallingPlatform.Skin in Java.
PUFFBALL_SWELL_ASSISTED = 0.8 * 1.5  # TrapWarnings.DEFAULT_WARNING_SECONDS with the longer-warnings assist
PUFFBALL_MIN_CALM = 0.8
VINE_MIN_PERIOD, VINE_AMPLITUDE = 3.5, (35.0, 60.0)
SKINS = ('', 'log')
VINE_HALF_WIDTH = 4.0
PUFFBALL_CLOUD, PUFFBALL_INSET = 24.0, 2.0
HERO_BOX = (11.0, 22.0, 18.0, 26.0)  # Player hitbox offset and size


def rhythm_number(o, key, default, errors):
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
            period, cloud = rhythm_number(o, 'period', 4.0, errors), rhythm_number(o, 'cloud', 1.0, errors)
            phase = rhythm_number(o, 'phase', 0.0, errors)
            if period is not None and cloud is not None and (
                    cloud <= 0 or period < cloud + PUFFBALL_SWELL_ASSISTED + PUFFBALL_MIN_CALM):
                errors.append(f"Puffball {describe(o)}: period {period} must be at least cloud {cloud} + assisted swell "
                              f"{PUFFBALL_SWELL_ASSISTED} + minimum calm {PUFFBALL_MIN_CALM}")
            elif None not in (period, cloud, phase):
                errors.extend(f"Puffball {describe(o)}: its danger area covers the room {name}"
                              for name, area in standing_places(root, spawn, o) if puffball_ever_lethal(o, area))
        elif kind(o) == 'ThornVine':
            period, amplitude = rhythm_number(o, 'period', 4.5, errors), rhythm_number(o, 'amplitude', 50.0, errors)
            phase = rhythm_number(o, 'phase', 0.0, errors)
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
    return open_floor_problems(o, left, right, collisions, spawn, room)


def open_floor_problems(o, left, right, collisions, spawn, room):
    """Mirror of ThornTrap.openFloorProblem: the shared jump envelope (floor, headroom, inside the room)."""
    x, y, w, h = box(o)
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


def explosive_problems(o, collisions, spawn, room, standing):
    """Mirror of ExplosiveTrap.problem. `standing` is a list of (name, (x, y, w, h)) places the hero must be able to stand."""
    x, y, w, h = box(o)
    if not (w > 0 and h > 0):
        return ['needs positive bounds']
    open_floor = open_floor_problems(o, 0.0, 0.0, collisions, spawn, room)
    if open_floor:
        return open_floor
    blast = [x + w / 2 - BLAST_SIZE / 2, y + h / 2 - BLAST_SIZE / 2, BLAST_SIZE, BLAST_SIZE]
    if room and (blast[0] < room[0] or blast[1] < room[1] or blast[0] + blast[2] > room[0] + room[2]
                 or blast[1] + blast[3] > room[1] + room[3]):
        return ['has its blast square leaving the room']
    for name, area in standing:
        if overlaps(blast, area):
            return [f'has its blast square over the room {name}']
    return []


def fungus_problems(o, collisions, spawn, room, standing):
    """Mirror of FungusTrap.problem. `standing` is a list of (name, (x, y, w, h)) places the hero must be able to stand."""
    x, y, w, h = box(o)
    if not (w > 0 and h > 0):
        return ['needs positive bounds']
    lethal = kind(o) == 'FalseHoneyFungus'
    try:
        zone = number(o, 'zone', MIN_ZONE)
        left, right = number(o, 'zoneLeft', zone), number(o, 'zoneRight', zone)
    except ValueError as e:
        return [str(e)]
    if left < 0 or right < 0 or left + right <= 0:
        return ['needs a zone (zone, zoneLeft, zoneRight): not negative, at least one side used']
    smallest = MIN_ZONE if lethal else FUNGUS_MIN_HARMLESS_ZONE
    if (0 < left < smallest) or (0 < right < smallest):
        return [f'needs each used zone side to be at least {int(smallest)} px (zone, zoneLeft, zoneRight; 0 leaves a side unused)']
    open_floor = open_floor_problems(o, left, right, collisions, spawn, room)
    if open_floor:
        return open_floor
    zone_box = [x - left, y - FUNGUS_ZONE_ABOVE, w + left + right, h + FUNGUS_ZONE_ABOVE]
    if any(name == 'entrance' and overlaps(zone_box, area) for name, area in standing):
        return ['has its trigger zone over the room entrance (the hero would start triggered)']
    if lethal:
        cloud = [x + w / 2 - FUNGUS_CLOUD / 2, y + h - FUNGUS_CLOUD, FUNGUS_CLOUD, FUNGUS_CLOUD]
        wide = [cloud[0] - FUNGUS_CLEARANCE, cloud[1] - FUNGUS_CLEARANCE, cloud[2] + 2 * FUNGUS_CLEARANCE, cloud[3] + 2 * FUNGUS_CLEARANCE]
        for name, area in standing:
            if overlaps(wide, area):
                return [f'has its spore cloud within {int(FUNGUS_CLEARANCE)} px of the room {name}']
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


def apply_layer_offsets(root):
    """Fold each object group's offsetx/offsety into its objects, as the game's loader does (forest-2's SpawnPoints is offset by -16)."""
    for group in root.findall('objectgroup'):
        dx, dy = float(group.get('offsetx', 0)), float(group.get('offsety', 0))
        if not (dx or dy):
            continue
        for o in group.findall('object'):
            o.set('x', repr(float(o.get('x', 0)) + dx)); o.set('y', repr(float(o.get('y', 0)) + dy))


def check_map(path):
    root = ET.parse(path).getroot()
    apply_layer_offsets(root)
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
    for o in collisions:
        if kind(o) in THORNS:
            errors.append(f"{describe(o)}: {kind(o)} is in the Collisions layer; traps must be in SpawnPoints")
    for o in (o for o in spawn if kind(o) in THORNS):
        errors.extend(f"{kind(o)} {describe(o)} {e}" for e in thorn_problems(o, collisions, spawn, thorn_room(root, o)))
    for o in collisions:
        if kind(o) in EXPLOSIVES:
            errors.append(f"{describe(o)}: {kind(o)} is in the Collisions layer; traps must be in SpawnPoints")
    for o in (o for o in spawn if kind(o) in EXPLOSIVES):
        errors.extend(f"{kind(o)} {describe(o)} {e}"
                      for e in explosive_problems(o, collisions, spawn, thorn_room(root, o), standing_places(root, spawn, o)))
    for o in collisions:
        if kind(o) in FUNGI:
            errors.append(f"{describe(o)}: {kind(o)} is in the Collisions layer; traps must be in SpawnPoints")
    for o in (o for o in spawn if kind(o) in FUNGI):
        errors.extend(f"{kind(o)} {describe(o)} {e}"
                      for e in fungus_problems(o, collisions, spawn, thorn_room(root, o), standing_places(root, spawn, o)))
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
    errors += check_decor(spawn, collisions)
    errors += check_still_decor(spawn, groups.get('Hints', []))
    errors += check_hanging_vines(root, spawn, collisions)
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


DECOR_JAVA = FALLING_JAVA.with_name('Decor.java')


def decor_kinds():
    """The kind names are the string keys of Decor.KINDS: one source of truth, read from the Java class."""
    block = re.search(r'KINDS\s*=\s*java\.util\.Map\.of\((.*?)\);', DECOR_JAVA.read_text(encoding='utf-8'), re.S)
    return tuple(re.findall(r'"([^"]+)"\s*,\s*new Kind\(', block.group(1))) if block else ()


def decor_still_kinds():
    """The kinds with the `still` flag in Decor.KINDS (#299): never animated, so they must not pose as an active mushroom."""
    block = re.search(r'KINDS\s*=\s*java\.util\.Map\.of\((.*?)\);', DECOR_JAVA.read_text(encoding='utf-8'), re.S)
    return tuple(re.findall(r'"([^"]+)"\s*,\s*new Kind\([^)]*,\s*true\)', block.group(1))) if block else ()


def check_decor(spawn, collisions):
    """Decor (#239): a known kind, a positive size, and the bottom edge on the top of a solid block (world Y grows downward)."""
    kinds, errors = decor_kinds(), []
    for o in (o for o in spawn if kind(o) == 'Decor'):
        label = f"Decor {describe(o)}"
        if (prop(o, 'kind') or '').lower() not in kinds:
            errors.append(f"{label}: unknown kind '{prop(o, 'kind')}', known: {', '.join(kinds)}"); continue
        body = _rect(o)
        if not (body[2] > 0 and body[3] > 0):
            errors.append(f"{label}: needs a positive size"); continue
        bottom = body[1] + body[3]
        if not any(kind(c) != 'QuickSand' and _x_overlap(_rect(c), body) and _num(c, 'y') == bottom for c in collisions):
            errors.append(f"{label}: its bottom edge ({bottom:g}) is not on the top of a solid block")
    return errors


FUNGI = ('HoneyFungus', 'FalseHoneyFungus')
FUNGUS_ZONE_ABOVE = 32.0  # FungusTrap.ZONE_ABOVE: the trigger zone starts this far above the footprint (world Y grows downward)
FUNGUS_DEFAULT_ZONE = 64.0  # FungusTrap.DEFAULT_ZONE
STILL_MAX_PER_MAP = 2  # #299: one or two static look-alikes per level, or the player learns to ignore every mushroom
SCENERY = ('Decor', 'HangingVine', 'Deadline', 'Player')  # spawn objects that are not traps, pickups or mechanisms


def _overlaps(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def _number(o, key, default):
    try:
        return float(prop(o, key)) if prop(o, key) != '' else default
    except ValueError:
        return default


def fungus_zone(o):
    """The trigger zone of an active mushroom, as FungusTrap.triggerZone: the footprint widened by zone (or zoneLeft/zoneRight)."""
    zone = _number(o, 'zone', FUNGUS_DEFAULT_ZONE)
    left, right = _number(o, 'zoneLeft', zone), _number(o, 'zoneRight', zone)
    x, y, w, h = _rect(o)
    return (x - left, y - FUNGUS_ZONE_ABOVE, w + left + right, h + FUNGUS_ZONE_ABOVE)


def check_still_decor(spawn, hints):
    """Static look-alike mushrooms (#299): at most STILL_MAX_PER_MAP per map; never over an active mushroom's zone (which holds
    its swell), a hint zone, or any trap, pickup or mechanism, so scenery can never hide a warning, a hint or a pickup."""
    still = decor_still_kinds()
    mine = [o for o in spawn if kind(o) == 'Decor' and (prop(o, 'kind') or '').lower() in still]
    errors = []
    if len(mine) > STILL_MAX_PER_MAP:
        errors.append(f"{len(mine)} static look-alike decor objects, at most {STILL_MAX_PER_MAP} per map")
    others = [o for o in spawn if kind(o) not in SCENERY and kind(o) not in FUNGI]
    for o in mine:
        box, label = _rect(o), f"Decor {describe(o)}"
        for f in (f for f in spawn if kind(f) in FUNGI and _overlaps(box, fungus_zone(f))):
            errors.append(f"{label}: static look-alike lies on the zone of the active {kind(f)} {describe(f)}")
        for h in (h for h in hints if _overlaps(box, _rect(h))):
            errors.append(f"{label}: static look-alike lies in the hint zone {describe(h)}")
        for t in (t for t in others if _overlaps(box, _rect(t))):
            errors.append(f"{label}: static look-alike hides {kind(t)} {describe(t)}")
    return errors


VINE_JAVA = FALLING_JAVA.with_name('HangingVine.java')


def vine_reach():
    """HangingVine.REACH, read from the Java class so the check and the game cannot drift apart."""
    m = re.search(r'REACH\s*=\s*([0-9.]+)f', VINE_JAVA.read_text(encoding='utf-8'))
    return float(m.group(1)) if m else 0.0


def check_hanging_vines(root, spawn, collisions):
    """HangingVine (#239 step 5): a positive size, the top edge on the room's ceiling line or under a solid block (world Y
    grows downward, so the top edge is `y`), and a FallingStone in the same room within reach: a vine nothing can move is dead art."""
    errors, reach = [], vine_reach()
    stones = [o for o in spawn if kind(o) == 'FallingStone']
    for o in (o for o in spawn if kind(o) == 'HangingVine'):
        label = f"HangingVine {describe(o)}"
        body = _rect(o)
        if not (body[2] > 0 and body[3] > 0):
            errors.append(f"{label}: needs a positive size"); continue
        room = thorn_room(root, o)
        hangs = (room is not None and body[1] == room[1]) or any(
            kind(c) != 'QuickSand' and _x_overlap(_rect(c), body) and _num(c, 'y') + _num(c, 'height') == body[1] for c in collisions)
        if not hangs:
            errors.append(f"{label}: its top edge ({body[1]:g}) is neither the room's top nor the bottom of a solid block"); continue
        anchor = body[0] + body[2] / 2
        near = [s for s in stones if thorn_room(root, s) == room and abs(_num(s, 'x') + _num(s, 'width') / 2 - anchor) < reach]
        if not near:
            errors.append(f"{label}: no FallingStone in its room within {reach:g} px of its anchor, so nothing can move it")
    return errors


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
