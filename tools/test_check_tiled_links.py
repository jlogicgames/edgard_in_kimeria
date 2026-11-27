#!/usr/bin/env python3
"""Behavioral checks for the Tiled link validator."""
from pathlib import Path
import tempfile
import unittest
import check_tiled_links as links

ROOT = links.ROOT


def tmx(spawn='', collisions=''):
    return ('<?xml version="1.0"?><map><objectgroup name="SpawnPoints">' + spawn + '</objectgroup>'
            '<objectgroup name="Collisions">' + collisions + '</objectgroup></map>')


def check(text):
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / 'm.tmx'
        path.write_text(text, encoding='utf-8')
        return links.check_map(path)


def lever(target, id=1):
    return (f'<object id="{id}" name="Lever" type="Trigger" x="8" y="9"><properties>'
            f'<property name="target" type="object" value="{target}"/></properties></object>')


ESCALATOR = '<object id="2" name="Lift" type="Escalator" x="1" y="2"/>'


class TiledLinksTest(unittest.TestCase):
    def test_a_layer_offset_is_folded_into_its_objects_like_the_game_loader_does(self):
        import xml.etree.ElementTree as ET
        root = ET.fromstring('<map><objectgroup name="SpawnPoints" offsetx="4" offsety="-16"><object id="1" x="10" y="312"/></objectgroup>'
                             '<objectgroup name="Collisions"><object id="2" x="0" y="320"/></objectgroup></map>')
        links.apply_layer_offsets(root)
        spawn, floor = root.findall('objectgroup')
        self.assertEqual((14.0, 296.0), (float(spawn[0].get('x')), float(spawn[0].get('y'))))
        self.assertEqual((0.0, 320.0), (float(floor[0].get('x')), float(floor[0].get('y'))), "a group without offset is untouched")

    def test_reference_to_a_spawnpoint_mechanism_is_valid(self):
        self.assertEqual([], check(tmx(lever(2) + ESCALATOR)))

    def test_missing_target_names_trigger_and_id(self):
        errors = check(tmx(lever(99)))
        self.assertEqual(1, len(errors)); self.assertIn('[1]', errors[0]); self.assertIn('99 does not exist', errors[0])

    def test_mechanism_in_collisions_layer_is_called_out(self):
        errors = check(tmx(lever(2), collisions=ESCALATOR))
        self.assertTrue(any('Collisions layer' in e and '[2]' in e for e in errors))

    def test_reference_to_a_non_mechanism_is_rejected(self):
        errors = check(tmx(lever(2) + '<object id="2" name="x" type="Checkpoint" x="0" y="0"/>'))
        self.assertIn('expected Actionable', errors[0])

    def test_actionable_needs_a_kind(self):
        errors = check(tmx(lever(2) + '<object id="2" name="d" type="Actionable" x="0" y="0"/>'))
        self.assertIn('needs property type', errors[0])

    def test_legacy_name_link_still_works_and_ambiguity_fails(self):
        trigger = '<object id="1" name="a" type="Trigger" x="0" y="0"/>'
        torch = '<object id="2" name="a" type="Ladder" x="200" y="0"/>'  # a ladder 200 px away (D126 keeps levers clear)
        self.assertEqual([], check(tmx(trigger + torch)))
        self.assertIn('0 mechanisms', check(tmx(trigger))[0])
        self.assertIn('2 mechanisms', check(tmx(trigger + torch + torch.replace('id="2"', 'id="3"')))[0])

    def test_rhythm_traps_with_defaults_and_valid_properties_pass(self):
        valid = ('<object id="1" type="Puffball" x="0" y="0"/>'
                 '<object id="2" type="Puffball" x="0" y="0"><properties><property name="period" value="3"/>'
                 '<property name="phase" value="1.5"/></properties></object>'
                 '<object id="3" type="ThornVine" x="0" y="0"/>'
                 '<object id="4" type="FallingPlatform" x="0" y="0"><properties><property name="skin" value="log"/></properties></object>')
        self.assertEqual([], check(tmx(valid)))

    def test_rhythm_traps_reject_an_unfair_or_malformed_rhythm(self):
        def prop_obj(kind_, name, value, id=1):
            return (f'<object id="{id}" type="{kind_}" x="1" y="2"><properties>'
                    f'<property name="{name}" value="{value}"/></properties></object>')
        self.assertIn('minimum calm', check(tmx(prop_obj('Puffball', 'period', 2.9)))[0])
        self.assertIn('not a number', check(tmx(prop_obj('Puffball', 'phase', 'soon')))[0])
        self.assertIn('below the minimum', check(tmx(prop_obj('ThornVine', 'period', 3)))[0])
        self.assertIn('amplitude', check(tmx(prop_obj('ThornVine', 'amplitude', 75)))[0])
        self.assertIn("unknown skin 'oak'", check(tmx(prop_obj('FallingPlatform', 'skin', 'oak')))[0])

    def test_rhythm_traps_reject_non_finite_numbers(self):
        def prop_obj(kind_, name, value):
            return (f'<object id="1" type="{kind_}" x="1" y="2"><properties>'
                    f'<property name="{name}" value="{value}"/></properties></object>')
        for value in ('NaN', 'nan', 'inf', '-inf', '1,5'):
            for kind_ in ('Puffball', 'ThornVine'):
                for key in ('period', 'phase'):
                    self.assertTrue(check(tmx(prop_obj(kind_, key, value))), f'{kind_} {key}={value}')

    def test_rhythm_traps_must_not_cover_the_entrance_exit_or_a_checkpoint(self):
        rooms = ('<objectgroup name="Rooms"><object name="a" type="Room" x="0" y="0" width="320" height="368"/>'
                 '<object name="a" type="RoomStart" x="32" y="272"><point/></object>'
                 '<object name="b" type="Room" x="320" y="0" width="320" height="368"/>'
                 '<object name="a" type="RoomExit" x="272" y="272" width="32" height="48"/>'
                 '<object name="b" type="RoomStart" x="352" y="272"><point/></object></objectgroup>')
        checkpoint = '<object id="8" type="Checkpoint" x="150" y="288" width="16" height="32"/>'

        def run(trap):
            text = tmx(checkpoint + trap).replace('<map>', '<map>' + rooms)
            return check(text)
        self.assertEqual([], run('<object id="2" type="Puffball" x="200" y="304" width="16" height="16"/>'))
        self.assertEqual([], run('<object id="2" type="ThornVine" x="200" y="256" width="24" height="40"/>'))
        self.assertIn('entrance', run('<object id="2" type="Puffball" x="40" y="304" width="16" height="16"/>')[0])
        self.assertIn('exit', run('<object id="2" type="Puffball" x="280" y="304" width="16" height="16"/>')[0])
        self.assertIn('checkpoint', run('<object id="2" type="Puffball" x="150" y="304" width="16" height="16"/>')[0])
        self.assertIn('entrance', run('<object id="2" type="ThornVine" x="40" y="256" width="24" height="40"/>')[0])
        self.assertIn('exit', run('<object id="2" type="ThornVine" x="258" y="256" width="24" height="40"/>')[0])
        self.assertIn('checkpoint', run('<object id="2" type="ThornVine" x="140" y="256" width="24" height="40"/>')[0])

    def test_bramble_fixtures_agree_with_the_game(self):
        """#364 (D89): the Java loader accepts bramble-test and refuses the three broken fixtures for the same reasons."""
        tiles = ROOT / 'core' / 'src' / 'test' / 'resources' / 'tiles'
        self.assertEqual([], links.check_map(tiles / 'bramble-test.tmx'))
        for name, reason in (('bramble-bad-period-test', 'shorter than'), ('bramble-no-face-test', 'solid face'),
                             ('bramble-covers-exit-test', 'covers the room exit')):
            errors = links.check_map(tiles / f'{name}.tmx')
            self.assertEqual(1, len(errors), name)
            self.assertIn(reason, errors[0], name)

    def test_bramble_properties_are_strict(self):
        wall = '<object id="8" type="Wall" x="0" y="0" width="16" height="320"/><object id="7" x="0" y="320" width="640" height="48"/>'

        def bramble(props, width=64, height=24):
            body = ''.join(f'<property name="{k}" value="{v}"/>' for k, v in props.items())
            return check(tmx(f'<object id="2" type="Bramble" x="16" y="296" width="{width}" height="{height}">'
                             f'<properties>{body}</properties></object>', collisions=wall))
        self.assertEqual([], bramble({'root': 'left'}))
        self.assertEqual([], bramble({'root': 'left', 'period': 4.5, 'phase': 1, 'hold': 1.5}))
        self.assertIn('root must be', bramble({})[0])
        self.assertIn('root must be', bramble({'root': 'up'})[0])
        self.assertIn('unknown property', bramble({'root': 'left', 'speed': 2})[0])
        self.assertIn('not a number', bramble({'root': 'left', 'hold': 'long'})[0])
        self.assertIn('hold must be', bramble({'root': 'left', 'hold': 2})[0])
        self.assertIn('width', bramble({'root': 'left'}, width=112)[0])
        self.assertIn('height', bramble({'root': 'left'}, height=16)[0])
        self.assertIn('root edge', bramble({'root': 'right'})[0])

    def test_bramble_accepts_a_split_face_a_split_floor_and_a_half_pixel_offset(self):
        """Bramble.placementProblem merges spans within FLOOR_TOLERANCE, as DeadfallTrap does (review of #364)."""
        def bramble(collisions):
            return check(tmx('<object id="2" type="Bramble" x="16" y="296" width="64" height="24">'
                             '<properties><property name="root" value="left"/></properties></object>', collisions=collisions))
        split_face = ('<object id="8" type="Wall" x="0" y="0" width="16" height="306"/>'
                      '<object id="9" type="Wall" x="0" y="306" width="16" height="14"/>')
        split_floor = '<object id="7" x="0" y="320" width="72" height="48"/><object id="6" x="72" y="320" width="568" height="48"/>'
        self.assertEqual([], bramble(split_face + split_floor))
        self.assertEqual([], bramble('<object id="8" type="Wall" x="0.4" y="0" width="16" height="320"/>'
                                     '<object id="7" x="0" y="320.3" width="640" height="48"/>'))
        gap = '<object id="7" x="0" y="320" width="72" height="48"/><object id="6" x="80" y="320" width="560" height="48"/>'
        self.assertIn('needs floor', bramble(split_face + gap)[0])
        short_face = '<object id="8" type="Wall" x="0" y="0" width="16" height="306"/>'
        self.assertIn('root edge', bramble(short_face + split_floor)[0])

    def test_rhythm_test_fixtures_agree_with_the_game(self):
        tiles = ROOT / 'core' / 'src' / 'test' / 'resources' / 'tiles'
        self.assertEqual([], links.check_map(tiles / 'rhythm-test.tmx'))
        self.assertTrue(links.check_map(tiles / 'rhythm-bad-period-test.tmx'))

    ROOT = '<object id="3" type="ThornRoot" x="288" y="296" width="16" height="24">%s</object>'
    FLOOR = '<object id="9" x="0" y="320" width="640" height="48"/>'
    ROOM = ('<objectgroup name="Rooms"><object name="one" type="Room" x="0" y="0" width="640" height="368"/>'
            '</objectgroup>')

    def thorn(self, props='', spawn='', collisions=FLOOR, root=ROOT, rooms=''):
        body = root % (f'<properties>{props}</properties>' if props else '')
        return check(tmx(body + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def prop_xml(self, name, value):
        return f'<property name="{name}" value="{value}"/>'

    def test_a_well_placed_thorn_trap_passes(self):
        self.assertEqual([], self.thorn())
        self.assertEqual([], self.thorn(self.prop_xml('zone', '64')))
        self.assertEqual([], self.thorn(self.prop_xml('zoneLeft', '0') + self.prop_xml('zoneRight', '64'), rooms=self.ROOM))

    def test_floor_pit_and_ledge_edge_beside_the_trap_are_rejected(self):
        self.assertIn('open floor', self.thorn(collisions='')[0])
        self.assertIn('open floor', self.thorn(root=self.ROOT.replace('y="296"', 'y="280"'))[0])
        pit = '<object id="9" x="0" y="320" width="340" height="48"/><object id="8" x="400" y="320" width="240" height="48"/>'
        self.assertIn('open floor', self.thorn(collisions=pit)[0])
        joined = '<object id="9" x="0" y="320" width="330" height="48"/><object id="8" x="330" y="320" width="310" height="48"/>'
        self.assertEqual([], self.thorn(collisions=joined))

    def test_anything_solid_in_the_jump_envelope_is_rejected_including_moving_things(self):
        pillar = '<object id="8" x="360" y="240" width="16" height="80"/>'
        self.assertIn('free height', self.thorn(collisions=self.FLOOR + pillar)[0])
        platform = '<object id="8" type="Platform" x="320" y="256" width="64" height="16"/>'
        self.assertIn('free height', self.thorn(collisions=self.FLOOR + platform)[0])
        lift = ('<object id="6" type="Escalator" x="240" y="150" width="32" height="16"><properties>'
                '<property name="isVertical" value="true"/><property name="offPos" value="3"/></properties></object>')
        self.assertIn('free height', self.thorn(spawn=lift)[0])
        lift_far = lift.replace('y="150"', 'y="100"')
        self.assertEqual([], self.thorn(spawn=lift_far.replace('value="3"', 'value="1"')))
        ladder = '<object id="6" type="Ladder" x="300" y="200" width="16" height="48"/>'
        self.assertIn('free height', self.thorn(spawn=ladder)[0])
        wall = '<object id="6" type="Actionable" x="352" y="240" width="16" height="48"><properties><property name="type" value="Wall"/></properties></object>'
        self.assertIn('free height', self.thorn(spawn=wall)[0])
        falling = '<object id="6" type="FallingPlatform" x="288" y="300" width="32" height="16"/>'
        self.assertIn('own column', self.thorn(spawn=falling)[0])

    def test_zone_sides_need_a_minimum_and_bad_numbers_are_rejected(self):
        self.assertIn('at least 64 px', self.thorn(self.prop_xml('zone', '32'))[0])
        self.assertIn('at least 64 px', self.thorn(self.prop_xml('zoneRight', '10'))[0])
        for bad in ('far', 'NaN', 'inf', 'Infinity', '1e2', '1_0', '0x10'):
            self.assertIn('not a plain number', self.thorn(self.prop_xml('zone', bad))[0], bad)
        self.assertIn('not negative', self.thorn(self.prop_xml('zone', '-5'))[0])
        self.assertIn('not negative', self.thorn(self.prop_xml('zoneLeft', '0') + self.prop_xml('zoneRight', '0'))[0])

    def test_envelope_must_fit_in_the_room_and_size_is_required(self):
        near_wall = self.ROOT.replace('x="288"', 'x="40"')
        self.assertIn('inside its room', self.thorn(root=near_wall, rooms=self.ROOM)[0])
        self.assertIn('positive bounds', self.thorn(root=self.ROOT.replace('width="16"', 'width="0"'))[0])
        self.assertTrue(any('Collisions layer' in e for e in check(tmx(collisions=self.FLOOR + self.ROOT % ''))))


    def test_shipped_maps_pass(self):
        self.assertEqual(0, links.main([]))


TILES = links.ROOT / 'core' / 'src' / 'test' / 'resources' / 'tiles'


def fmap(spawn, collisions='<object id="7" x="0" y="320" width="640" height="48"/>'):
    return ('<?xml version="1.0"?><map width="40" height="23" tilewidth="16" tileheight="16">'
            '<objectgroup name="SpawnPoints"><object id="1" type="Player" x="32" y="272" width="48" height="48"/>'
            + spawn + '</objectgroup><objectgroup name="Collisions">' + collisions + '</objectgroup></map>')


def falling(kind, zone='zoneX=144 zoneY=224 zoneWidth=48 zoneHeight=96', extra='', box='x="160" y="224" width="32" height="16"'):
    props = ''.join(f'<property name="{k}" value="{v}"/>' for k, v in (z.split('=') for z in zone.split()))
    return f'<object id="5" name="t" type="{kind}" {box}><properties>{props}{extra}</properties></object>'


SOIL_GID = 43  # the terrain's soil body (ForestTerrain.tsx, firstgid 1)


def with_tiles(tmx_text, row, column, width=40, height=23, gid=SOIL_GID):
    """The map with a tile layer whose only drawn cell is (row, column) (#360: a lip hangs under a drawn tile row)."""
    cells = ['0'] * (width * height)
    cells[row * width + column] = str(gid)
    layer = f'<layer name="Background" width="{width}" height="{height}"><data encoding="csv">{",".join(cells)}</data></layer>'
    return tmx_text.replace('<objectgroup name="SpawnPoints">', layer + '<objectgroup name="SpawnPoints">', 1)


STONE_BOX = 'x="400" y="0" width="16" height="16"'
STONE_ZONE = 'zoneX=352 zoneY=16 zoneWidth=64 zoneHeight=304'


class FallingTrapLinksTest(unittest.TestCase):
    def test_complete_traps_pass(self):
        self.assertEqual([], check(fmap(falling('FallingBranch'))))
        self.assertEqual([], check(fmap(falling('FallingStone', STONE_ZONE, box=STONE_BOX))))
        self.assertEqual([], check(fmap(falling('FallingStone', STONE_ZONE, '<property name="skin" value="Forest"/>', STONE_BOX))))

    def test_missing_zone_property_is_named(self):
        errors = check(fmap(falling('FallingBranch', zone='zoneX=1 zoneY=2')))
        self.assertIn('zoneWidth, zoneHeight', errors[0])

    def test_bad_numbers_sizes_and_skins_fail(self):
        self.assertIn('numbers', check(fmap(falling('FallingBranch', zone='zoneX=a zoneY=2 zoneWidth=3 zoneHeight=4')))[0])
        self.assertIn('finite', check(fmap(falling('FallingBranch', zone='zoneX=NaN zoneY=2 zoneWidth=3 zoneHeight=4')))[0])
        self.assertIn('finite', check(fmap(falling('FallingBranch', zone='zoneX=Infinity zoneY=2 zoneWidth=3 zoneHeight=4')))[0])
        self.assertIn('positive', check(fmap(falling('FallingStone', 'zoneX=1 zoneY=2 zoneWidth=0 zoneHeight=4', box=STONE_BOX)))[0])
        self.assertIn('unknown skin', check(fmap(falling('FallingStone', STONE_ZONE, '<property name="skin" value="castle"/>', STONE_BOX)))[0])

    def test_geometry_gaps_are_rejected(self):
        self.assertIn('no solid floor', check(fmap(falling('FallingBranch'), collisions=''))[0])
        self.assertIn('too short', check(fmap(falling('FallingBranch', box='x="160" y="280" width="32" height="16"')))[0])
        self.assertIn('never trigger', ' '.join(check(fmap(falling('FallingBranch', 'zoneX=144 zoneY=224 zoneWidth=48 zoneHeight=66')))))
        self.assertIn('lethal column', ' '.join(check(fmap(falling('FallingBranch', 'zoneX=96 zoneY=224 zoneWidth=48 zoneHeight=96')))))
        self.assertIn('mid-air', check(fmap(falling('FallingStone', STONE_ZONE, box='x="400" y="32" width="16" height="16"')))[0])

    def test_the_stone_lip_must_be_on_screen_and_above_the_floor(self):
        # The fixture level is 368 px tall and the view 360: with the hero on the floor the camera's top edge is y 8 (#360).
        lip = lambda v: f'<property name="lipTop" value="{v}"/>'
        self.assertEqual([], check(fmap(falling('FallingStone', STONE_ZONE, lip(8), STONE_BOX))))
        self.assertEqual([], check(with_tiles(fmap(falling('FallingStone', STONE_ZONE, lip(48), STONE_BOX)), row=2, column=25)))
        self.assertIn('off screen', check(fmap(falling('FallingStone', STONE_ZONE, lip(4), STONE_BOX)))[0])
        self.assertIn('below the floor', check(fmap(falling('FallingStone', STONE_ZONE, lip(316), STONE_BOX)))[0])

    def test_the_stone_lip_hangs_from_the_default_line_or_something_drawn(self):
        lip = lambda v: f'<property name="lipTop" value="{v}"/>'
        self.assertIn('floats', check(fmap(falling('FallingStone', STONE_ZONE, lip(200), STONE_BOX)))[0])
        self.assertIn('floats', check(fmap(falling('FallingStone', STONE_ZONE, lip(48), STONE_BOX)))[0], 'no tile over the column')
        self.assertIn('floats', check(with_tiles(fmap(falling('FallingStone', STONE_ZONE, lip(32), STONE_BOX)), row=2, column=25))[0],
                      'inside the drawn row, not under it')
        self.assertIn('floats', check(with_tiles(fmap(falling('FallingStone', STONE_ZONE, lip(48), STONE_BOX)), row=2, column=30))[0],
                      'a tile row beside the column')
        for gid in sorted(links.CANOPY_GIDS):   # D112: the canopy clump is foliage for the vines, not a row a stone hangs from
            self.assertIn('floats', check(with_tiles(fmap(falling('FallingStone', STONE_ZONE, lip(48), STONE_BOX)), row=2, column=25, gid=gid))[0],
                          f'canopy gid {gid}')

    def test_forest_2s_stone_would_float_under_a_canopy_instead_of_its_block(self):
        """Mutation of the shipped map: the soil block over forest-2's stone (cols 138 to 141, rows 0 to 2) swapped for canopy fails."""
        import re
        text = (links.MAPS / 'forest-2.tmx').read_text(encoding='utf-8')
        match = re.search(r'(<layer [^>]*name="Background"[^>]*>\s*<data encoding="csv">\s*)(.*?)(</data>)', text, re.S)
        rows = [line.rstrip(',').split(',') for line in match.group(2).strip().splitlines()]
        for r in range(3):
            for c in range(138, 142):
                rows[r][c] = '61'
        mutated = text[:match.start(2)] + ',\n'.join(','.join(row) for row in rows) + '\n' + text[match.end(2):]
        import tempfile
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'forest-2.tmx'
            path.write_text(mutated, encoding='utf-8')
            self.assertTrue(any('floats' in e for e in links.check_map(path)))
        self.assertEqual([], links.check_map(links.MAPS / 'forest-2.tmx'))

    def test_a_misspelled_falling_trap_property_is_refused(self):
        lip = lambda v: f'<property name="lipTop" value="{v}"/>'
        errors = check(fmap(falling('FallingStone', STONE_ZONE, '<property name="liptop" value="48"/>', STONE_BOX)))
        self.assertIn("unknown property liptop", errors[0])
        self.assertIn('unknown property', check(fmap(falling('FallingBranch', extra='<property name="zonex" value="1"/>')))[0])
        self.assertIn('not a number', check(fmap(falling('FallingStone', STONE_ZONE, lip('high'), STONE_BOX)))[0])
        self.assertIn('FallingStone only', check(fmap(falling('FallingBranch', extra=lip(240))))[0])

    def test_skin_names_are_read_from_the_java_class(self):
        self.assertEqual(('forest',), links.falling_stone_skins())

    def test_every_java_fixture_agrees_with_the_checker(self):
        self.assertEqual([], links.check_map(TILES / 'falling-traps-test.tmx'))
        bad = sorted(TILES.glob('falling-bad-*-test.tmx'))
        self.assertGreaterEqual(len(bad), 13)
        for path in bad:
            self.assertTrue(links.check_map(path), f'{path.name} must fail like the Java loader does')


class ExplosiveTrapLinksTest(unittest.TestCase):
    """D47: mirror of ExplosiveTrap.problem (bypass = open floor and headroom; blast square clear of standing places)."""
    FLOOR = '<object id="9" x="0" y="320" width="640" height="48"/>'
    STUMP = '<object id="3" type="ExplosiveStump" x="288" y="304" width="16" height="16"/>'
    ROOT = '<object id="3" type="ExplosiveRoot" x="288" y="296" width="16" height="24"/>'
    ROOM = ('<objectgroup name="Rooms"><object name="one" type="Room" x="0" y="0" width="640" height="368"/>'
            '<object name="one" type="RoomStart" x="32" y="272"><point/></object>'
            '<object name="one" type="RoomExit" x="600" y="272" width="32" height="48"/></objectgroup>')

    def explosive(self, trap=STUMP, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_a_stump_and_a_root_on_open_floor_pass(self):
        self.assertEqual([], self.explosive())
        self.assertEqual([], self.explosive(self.ROOT))
        self.assertEqual([], self.explosive(rooms=''))

    def test_no_bypass_without_floor_or_run_up(self):
        self.assertIn('open floor', self.explosive(collisions='')[0])
        pit = '<object id="9" x="0" y="320" width="330" height="48"/><object id="8" x="400" y="320" width="240" height="48"/>'
        self.assertIn('open floor', self.explosive(collisions=pit)[0])
        ledge = self.STUMP.replace('y="304"', 'y="288"')
        self.assertIn('open floor', self.explosive(ledge)[0])

    def test_no_bypass_under_a_low_ceiling_in_a_corridor_or_behind_a_pillar(self):
        ceiling = '<object id="8" x="200" y="240" width="200" height="16"/>'
        self.assertIn('free height', self.explosive(collisions=self.FLOOR + ceiling)[0])
        pillar = '<object id="8" x="340" y="240" width="16" height="80"/>'
        self.assertIn('free height', self.explosive(collisions=self.FLOOR + pillar)[0])
        far = '<object id="8" x="372" y="240" width="16" height="80"/>'  # beyond the 64 px landing: not in the way
        self.assertEqual([], self.explosive(collisions=self.FLOOR + far))
        high = '<object id="8" x="200" y="200" width="200" height="16"/>'  # 104 px of headroom
        self.assertEqual([], self.explosive(collisions=self.FLOOR + high))
        wall = ('<object id="6" type="Actionable" x="352" y="240" width="16" height="48"><properties>'
                '<property name="type" value="Wall"/></properties></object>')
        self.assertIn('free height', self.explosive(spawn=wall)[0])
        falling = '<object id="6" type="FallingPlatform" x="288" y="300" width="32" height="16"/>'
        self.assertIn('own column', self.explosive(spawn=falling)[0])

    def test_the_blast_square_may_not_cover_the_entrance_the_exit_or_a_checkpoint(self):
        self.assertIn('entrance', self.explosive(self.STUMP.replace('x="288"', 'x="64"'))[0])
        self.assertIn('exit', self.explosive(rooms=self.ROOM.replace('x="600" y="272"', 'x="320" y="272"'))[0])
        checkpoint = '<object id="7" type="Checkpoint" x="330" y="272" width="16" height="32"/>'
        self.assertEqual([], self.explosive(spawn=checkpoint.replace('x="330"', 'x="336"')))
        self.assertIn('checkpoint', self.explosive(spawn=checkpoint.replace('x="330"', 'x="320"'))[0])

    def test_the_blast_square_must_stay_inside_the_room_and_size_is_required(self):
        low_room = self.ROOM.replace('height="368"', 'height="330"')
        self.assertIn('blast square leaving the room', self.explosive(rooms=low_room)[0])
        self.assertIn('positive bounds', self.explosive(self.STUMP.replace('width="16"', 'width="0"'))[0])
        self.assertTrue(any('Collisions layer' in e for e in check(tmx(collisions=self.FLOOR + self.STUMP))))

    def test_every_java_explosive_fixture_agrees_with_the_checker(self):
        self.assertEqual([], links.check_map(TILES / 'explosive-test.tmx'))
        self.assertEqual([], links.check_map(TILES / 'explosive-shield.tmx'))
        for name in ('corridor', 'pit', 'pillar', 'entrance'):
            self.assertTrue(links.check_map(TILES / f'explosive-{name}.tmx'), f'explosive-{name}.tmx must fail like the Java loader does')


class DeadfallLinksTest(unittest.TestCase):
    """D88: mirror of DeadfallTrap.problem (the bough's size, swing, floor run, free swing and approach, standing places)."""
    FLOOR = '<object id="9" x="0" y="320" width="640" height="48"/>'
    TRAP = '<object id="3" type="Deadfall" x="288" y="243" width="112" height="24"/>'  # pivot x 336, floor 320
    ROOM = ('<objectgroup name="Rooms"><object name="one" type="Room" x="0" y="0" width="640" height="368"/>'
            '<object name="one" type="RoomStart" x="32" y="272"><point/></object>'
            '<object name="one" type="RoomExit" x="576" y="272" width="32" height="48"/></objectgroup>')

    def deadfall(self, trap=TRAP, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_a_deadfall_on_open_floor_passes_either_way(self):
        self.assertEqual([], self.deadfall())
        right = self.TRAP.replace('/>', '><properties><property name="swing" value="right"/></properties></object>')
        self.assertEqual([], self.deadfall(right.replace('x="288"', 'x="160"')))

    def test_size_and_swing_are_checked(self):
        self.assertIn('112x24', self.deadfall(self.TRAP.replace('width="112"', 'width="96"'))[0])
        up = self.TRAP.replace('/>', '><properties><property name="swing" value="up"/></properties></object>')
        self.assertIn("swing 'up'", self.deadfall(up)[0])

    def test_a_pit_a_pillar_in_the_swing_and_a_low_ceiling_over_the_vine_fail(self):
        pit = '<object id="9" x="0" y="320" width="300" height="48"/><object id="8" x="340" y="320" width="300" height="48"/>'
        self.assertIn('solid floor', self.deadfall(collisions=pit)[0])
        self.assertIn("log's swing", self.deadfall(collisions=self.FLOOR + '<object id="8" x="390" y="280" width="16" height="40"/>')[0])
        self.assertIn('free height', self.deadfall(collisions=self.FLOOR + '<object id="8" x="180" y="240" width="48" height="16"/>')[0])
        wall = ('<object id="6" type="Actionable" x="300" y="200" width="16" height="48"><properties>'
                '<property name="type" value="Wall"/></properties></object>')
        self.assertIn("log's swing", self.deadfall(spawn=wall)[0])

    def test_the_entrance_may_not_be_on_the_vine_and_no_standing_place_in_the_band(self):
        self.assertIn('trip vine under the room entrance', self.deadfall(rooms=self.ROOM.replace('x="32" y="272"', 'x="202" y="272"'))[0])
        self.assertIn('through the room exit', self.deadfall(rooms=self.ROOM.replace('x="576" y="272"', 'x="330" y="272"'))[0])

    def test_the_geometry_is_read_from_the_java_class(self):
        self.assertEqual(links.DEADFALL_BOUGH, (112.0, 24.0))
        self.assertEqual(links.DEADFALL_VINE, (104.0, 120.0, 5.0))
        self.assertEqual(links.DEADFALL_SWEEP_HALF, 68.0)
        self.assertEqual(links.DEADFALL_BAND, (62.0, 27.0))
        with self.assertRaises(ValueError):
            links.deadfall_constant('NO_SUCH_CONSTANT')

    def test_it_must_fit_in_its_room_and_stay_out_of_collisions(self):
        self.assertIn('inside its room', self.deadfall(self.TRAP.replace('x="288"', 'x="120"'))[0])
        self.assertTrue(any('Collisions layer' in e for e in check(tmx(collisions=self.FLOOR + self.TRAP))))


class HoneyFungusLinksTest(unittest.TestCase):
    """D63: mirror of FungusTrap.problem (thorn jump envelope, zone off the entrance, the lethal cloud kept 48 px clear)."""
    FLOOR = '<object id="9" x="0" y="320" width="640" height="48"/>'
    ROOM = ('<objectgroup name="Rooms"><object name="one" type="Room" x="0" y="0" width="640" height="368"/>'
            '<object name="one" type="RoomStart" x="32" y="272"><point/></object>'
            '<object name="one" type="RoomExit" x="600" y="272" width="32" height="48"/></objectgroup>')

    @staticmethod
    def fungus(kind_='FalseHoneyFungus', x=288, props=''):
        body = f'<properties>{props}</properties>' if props else ''
        return f'<object id="3" type="{kind_}" x="{x}" y="304" width="24" height="16">{body}</object>'

    def run_map(self, trap, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_both_types_on_open_floor_pass(self):
        self.assertEqual([], self.run_map(self.fungus()))
        self.assertEqual([], self.run_map(self.fungus('HoneyFungus')))
        self.assertEqual([], self.run_map(self.fungus(), rooms=''))

    def test_no_bypass_without_floor_run_up_or_headroom(self):
        self.assertIn('open floor', self.run_map(self.fungus(), collisions='')[0])
        pit = '<object id="9" x="0" y="320" width="300" height="48"/><object id="8" x="400" y="320" width="240" height="48"/>'
        self.assertIn('open floor', self.run_map(self.fungus(), collisions=pit)[0])
        ceiling = '<object id="8" x="200" y="240" width="200" height="16"/>'
        self.assertIn('free height', self.run_map(self.fungus(), collisions=self.FLOOR + ceiling)[0])
        self.assertIn('free height', self.run_map(self.fungus('HoneyFungus'), collisions=self.FLOOR + ceiling)[0])

    def test_the_lethal_one_needs_zone_sides_of_64_and_the_harmless_one_only_24(self):
        small = '<property name="zone" value="40"/>'
        self.assertIn('at least 64', self.run_map(self.fungus(props=small))[0])
        self.assertEqual([], self.run_map(self.fungus('HoneyFungus', props=small)))
        tiny = '<property name="zone" value="16"/>'
        self.assertIn('at least 24', self.run_map(self.fungus('HoneyFungus', props=tiny))[0])
        self.assertIn('plain number', self.run_map(self.fungus(props='<property name="zone" value="NaN"/>'))[0])
        self.assertIn('needs a zone', self.run_map(self.fungus(props='<property name="zone" value="-5"/>'))[0])

    def test_the_zone_may_not_cover_the_entrance_and_the_cloud_keeps_48_px_from_standing_places(self):
        self.assertIn('entrance', self.run_map(self.fungus(x=100))[0])
        # Harmless with a short zone: 24 px of zone clears the entrance body (x 50..61) at x 100, the lethal one never would.
        self.assertEqual([], self.run_map(self.fungus('HoneyFungus', x=100, props='<property name="zone" value="30"/>')))
        self.assertIn('exit', self.run_map(self.fungus(x=540, props='<property name="zoneRight" value="0"/><property name="zoneLeft" value="64"/>'))[0])
        checkpoint = '<object id="7" type="Checkpoint" x="350" y="272" width="16" height="32"/>'
        self.assertIn('checkpoint', self.run_map(self.fungus(), spawn=checkpoint)[0])
        self.assertEqual([], self.run_map(self.fungus(), spawn=checkpoint.replace('x="350"', 'x="372"')))

    def test_the_envelope_stays_inside_the_room_and_size_is_required(self):
        self.assertIn('inside its room', self.run_map(self.fungus(x=10))[0])
        self.assertIn('positive bounds', self.run_map(self.fungus().replace('width="24"', 'width="0"'))[0])
        self.assertTrue(any('Collisions layer' in e for e in check(tmx(collisions=self.FLOOR + self.fungus()))))

    def test_the_shipped_maps_pass_with_the_fungi_placed(self):
        for name in ('forest-0', 'forest-2'):
            self.assertEqual([], links.check_map(links.MAPS / f'{name}.tmx'))


FLOOR = '<object id="9" x="0" y="320" width="400" height="48"/>'


def decor(kind_='bush', box='x="64" y="296" width="32" height="24"'):
    return f'<object id="7" name="Bush" type="Decor" {box}><properties><property name="kind" value="{kind_}"/></properties></object>'


class DecorLinksTest(unittest.TestCase):
    def test_a_bush_standing_on_the_floor_passes(self):
        self.assertEqual([], check(tmx(decor(), FLOOR)))

    def test_unknown_kind_missing_kind_and_bad_size_fail(self):
        self.assertIn("unknown kind 'tree'", check(tmx(decor('tree'), FLOOR))[0])
        self.assertIn("unknown kind ''", check(tmx(decor(''), FLOOR))[0])
        self.assertIn('positive size', check(tmx(decor(box='x="64" y="320" width="0" height="0"'), FLOOR))[0])

    def test_a_floating_or_sunken_bush_is_named(self):
        self.assertIn('not on the top of a solid block', check(tmx(decor(box='x="64" y="290" width="32" height="24"'), FLOOR))[0])
        self.assertIn('not on the top of a solid block', check(tmx(decor(box='x="64" y="300" width="32" height="24"'), FLOOR))[0])
        self.assertIn('not on the top of a solid block', check(tmx(decor(box='x="500" y="296" width="32" height="24"'), FLOOR))[0])

    def test_the_kinds_are_read_from_the_java_table(self):
        self.assertEqual({'bush', 'lookalike', 'trunk', 'snag', 'crown'}, set(links.decor_kinds()))  # trunk: #349 (D80), snag: D122, crown: #421
        self.assertEqual(('lookalike',), links.decor_still_kinds())
        self.assertEqual(('crown',), links.decor_hanging_kinds())

    def test_a_crown_hangs_from_the_room_top_or_a_solid_underside_not_from_the_floor(self):
        crown = lambda y: decor('crown', f'x="64" y="{y}" width="96" height="40"')
        room = '<objectgroup name="Rooms"><object name="one" type="Room" x="0" y="0" width="400" height="368"/></objectgroup>'
        self.assertEqual([], check(tmx(crown(0), FLOOR).replace('<objectgroup name="Spawn', room + '<objectgroup name="Spawn', 1)))
        self.assertEqual([], check(tmx(crown(16), CEILING + FLOOR)))  # under a solid block
        self.assertIn("neither the room's top nor the bottom of a solid block", check(tmx(crown(20), CEILING + FLOOR))[0])
        self.assertIn("neither the room's top", check(tmx(crown(280), FLOOR))[0])  # standing on the floor is not hanging


CEILING = '<object id="8" x="0" y="0" width="400" height="16"/>'
STONE = '<object id="5" name="Stone" type="FallingStone" x="120" y="16" width="16" height="16"/>'


def vine(box='x="100" y="16" width="8" height="40"'):
    return f'<object id="6" name="Vine" type="HangingVine" {box}/>'


def vine_errors(text):
    return [e for e in check(text) if 'HangingVine' in e]


class HangingVineLinksTest(unittest.TestCase):
    def test_a_vine_hanging_from_a_ceiling_block_near_a_stone_passes(self):
        self.assertEqual([], vine_errors(tmx(vine() + STONE, CEILING + FLOOR)))

    def test_a_vine_that_does_not_hang_from_anything_is_named(self):
        self.assertIn("neither the room's top nor the bottom of a solid block",
                      vine_errors(tmx(vine('x="100" y="20" width="8" height="40"') + STONE, CEILING + FLOOR))[0])
        self.assertIn('neither', vine_errors(tmx(vine('x="100" y="10" width="8" height="40"') + STONE, CEILING + FLOOR))[0])

    def test_a_bad_size_fails(self):
        self.assertIn('positive size', vine_errors(tmx(vine('x="100" y="16" width="0" height="0"') + STONE, CEILING))[0])

    def test_a_vine_with_no_stone_in_reach_is_dead_art(self):
        self.assertIn('no FallingStone', vine_errors(tmx(vine(), CEILING + FLOOR))[0])
        far = '<object id="5" name="Stone" type="FallingStone" x="300" y="16" width="16" height="16"/>'
        self.assertIn('no FallingStone', vine_errors(tmx(vine() + far, CEILING + FLOOR))[0])

    def test_the_reach_is_read_from_the_java_class(self):
        self.assertEqual(160.0, links.vine_reach())

    def test_the_shipped_vines_pass_in_every_map(self):
        shipped = sorted((links.ROOT / 'assets' / 'tiles').glob('forest-*.tmx'))
        self.assertEqual(3, len(shipped))
        for path in shipped:
            self.assertEqual([], [e for e in links.check_map(path) if 'HangingVine' in e], path.name)


class BranchPlacementTest(unittest.TestCase):
    """#349 (D80): the rotten branch's D69 side margin and fall depth, the same boundary FallingPlatform.branchProblem pins in TreeClimbTest."""

    @staticmethod
    def obj(kind, x, y, w=32, h=16, skin='branch'):
        import xml.etree.ElementTree as ET
        props = f'<properties><property name="skin" value="{skin}"/></properties>' if skin else ''
        return ET.fromstring(f'<object id="9" type="{kind}" x="{x}" y="{y}" width="{w}" height="{h}">{props}</object>')

    def problems(self, platform_x, platform_y):
        branch = self.obj('FallingPlatform', 300, 80)
        floor = self.obj('', 0, 320, 640, 48, skin=None)
        collisions = [floor, self.obj('Platform', platform_x, platform_y)]
        return links.branch_problems(branch, collisions, [0, 0, 640, 368], (0, 272), [(0, 288, 16, 32)])

    def test_sixteen_pixels_beside_passes_and_fifteen_fails_on_either_side(self):
        margin = links.platform_constant('BRANCH_SIDE_MARGIN')
        self.assertEqual(16.0, margin)
        self.assertEqual([], self.problems(300 + 32 + 16, 128))
        self.assertIn('D69', self.problems(300 + 32 + 15, 128)[0])
        self.assertEqual([], self.problems(300 - 16 - 32, 128))
        self.assertIn('D69', self.problems(300 - 15 - 32, 128)[0])

    def test_within_the_two_hundred_pixel_fall_fails_and_below_it_or_level_passes(self):
        self.assertIn('D69', self.problems(300, 80 + 200)[0])
        self.assertEqual([], self.problems(300, 80 + 201))
        self.assertEqual([], self.problems(300, 80))


class LadderClearanceTest(unittest.TestCase):
    """D126: nothing the hero operates by hand stands within 32 px of a ladder zone, raised or lowered."""
    LADDER = ('<object id="9" name="Ladder" type="Ladder" x="100" y="96" width="16" height="112"><properties>'
              '<property name="dropDistance" type="float" value="112"/></properties></object>')

    def lever_at(self, x, y=304, extra=''):
        return (f'<object id="7" name="Lever" type="Trigger" x="{x}" y="{y}" width="16" height="16">'
                f'<properties>{extra}</properties></object>')

    def errors(self, spawn):
        return [e for e in check(tmx(spawn)) if 'D126' in e]

    def test_a_lever_32_px_from_the_ladder_passes_and_16_px_fails(self):
        self.assertEqual([], self.errors(self.LADDER + self.lever_at(52)))     # gap 32
        self.assertEqual(1, len(self.errors(self.LADDER + self.lever_at(68)))) # gap 16

    def test_the_lowered_span_counts_too(self):
        # The raised ladder spans y 96-208 and the lowered one y 208-320: a lever at the foot is near only when lowered.
        self.assertEqual(1, len(self.errors(self.LADDER + self.lever_at(84, y=304))))

    def test_a_stone_counts_and_a_thrown_at_lever_does_not(self):
        stone = '<object id="8" name="Stone" type="Stone" x="108" y="308" width="12" height="12"/>'
        self.assertEqual(1, len(self.errors(self.LADDER + stone)))
        thrown = self.lever_at(108, extra='<property name="throwActivated" type="bool" value="true"/>')
        self.assertEqual([], self.errors(self.LADDER + thrown))

    def test_the_shipped_maps_pass(self):
        for m in sorted(links.MAPS.glob('*.tmx')):
            self.assertEqual([], [e for e in links.check_map(m) if 'D126' in e], m.name)


if __name__ == '__main__':
    unittest.main()


class BranchRootLinksTest(unittest.TestCase):
    """D122: a branch always grows from a trunk; its root lies at least MIN_ROOT_OVERLAP px over a trunk's bark."""

    @staticmethod
    def roots(spawn, collisions=''):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'm.tmx'
            path.write_text(tmx(spawn, collisions), encoding='utf-8')
            return links.check_branch_roots(path)

    @staticmethod
    def trunk(x, kind='trunk', y=32, h=288):
        return (f'<object id="20" name="Trunk" type="Decor" x="{x}" y="{y}" width="16" height="{h}">'
                f'<properties><property name="kind" value="{kind}"/></properties></object>')

    @staticmethod
    def perch(x, mirror=False, y=128):
        flip = '<property name="mirror" type="bool" value="true"/>' if mirror else ''
        return (f'<object id="21" name="Branch" type="Platform" x="{x}" y="{y}" width="32" height="16">'
                f'<properties><property name="skin" value="branch"/>{flip}</properties></object>')

    def test_the_trunk_kinds_are_read_from_the_java_table(self):
        self.assertEqual({'trunk', 'snag'}, set(links.decor_trunk_kinds()))

    def test_a_branch_beside_a_trunk_passes_on_either_side_and_one_px_further_fails(self):
        # a trunk at x 100 is drawn from x 94; its bark runs from x 97 to 119
        self.assertEqual([], self.roots(self.trunk(100), self.perch(116)))  # root x 116: 4 px over the bark
        self.assertEqual([], self.roots(self.trunk(100), self.perch(117)))  # 3 px passes
        self.assertIn('lies 2 px', self.roots(self.trunk(100), self.perch(118))[0])
        self.assertEqual([], self.roots(self.trunk(100), self.perch(68, mirror=True)))  # root x 99: 3 px
        self.assertIn('lies 2 px', self.roots(self.trunk(101), self.perch(68, mirror=True))[0])  # bark from x 98

    def test_a_branch_with_no_trunk_or_with_the_trunk_on_the_wrong_side_is_named(self):
        self.assertIn('lies 0 px', self.roots('', self.perch(116))[0])
        self.assertIn('on its right', self.roots(self.trunk(100), self.perch(116, mirror=True))[0])
        hive = '<object id="22" name="Hive" type="HangingHive" x="116" y="96" width="24" height="32"/>'
        self.assertEqual([], self.roots(self.trunk(100) + hive))
        self.assertIn('HangingHive', self.roots(self.trunk(80) + hive)[0])

    def test_the_root_must_meet_bark_not_the_cap_or_below_the_trunk(self):
        self.assertIn('lies 0 px', self.roots(self.trunk(100, 'snag', 200, 120), self.perch(116, y=192))[0])  # joint y 198, cap to 206
        self.assertEqual([], self.roots(self.trunk(100, 'snag', 200, 120), self.perch(116, y=208)))
        self.assertIn('lies 0 px', self.roots(self.trunk(100, 'bush', 296, 24), self.perch(116, y=300))[0])  # a bush is no trunk

    def test_the_shipped_maps_pass(self):
        for path in sorted((links.ROOT / 'assets' / 'tiles').glob('forest-*.tmx')):
            self.assertEqual([], links.check_branch_roots(path), path.name)


class StillDecorLinksTest(unittest.TestCase):
    """#299: a static look-alike mushroom never lies on an active mushroom's zone, a hint zone, a trap or a pickup."""
    FLOOR = '<object id="9" x="0" y="320" width="1200" height="48"/>'
    LOOKALIKE_BOX = 'x="400" y="304" width="24" height="16"'  # 24x16 standing on the floor at y 320

    def lookalike(self, box=LOOKALIKE_BOX, id_=7):
        return decor('lookalike', box).replace('id="7"', f'id="{id_}"')

    def fungus(self, x, kind_='FalseHoneyFungus', extra=''):
        return (f'<object id="20" name="F" type="{kind_}" x="{x}" y="304" width="24" height="16">'
                f'<properties>{extra}</properties></object>')

    def run_map(self, spawn, hints=''):
        return check('<?xml version="1.0"?><map><objectgroup name="SpawnPoints">' + spawn + '</objectgroup>'
                     '<objectgroup name="Collisions">' + self.FLOOR + '</objectgroup>'
                     '<objectgroup name="Hints">' + hints + '</objectgroup></map>')

    def test_a_lookalike_away_from_everything_passes(self):
        self.assertEqual([], self.run_map(self.lookalike() + self.fungus(100)))

    def test_a_lookalike_on_an_active_mushrooms_zone_is_named(self):
        # Zone of a mushroom at x 330: 266..420 (default 64 each side) overlaps the look-alike at 400..424.
        errors = self.run_map(self.lookalike() + self.fungus(330))
        self.assertTrue(any('zone of the active FalseHoneyFungus' in e for e in errors), errors)
        errors = self.run_map(self.lookalike() + self.fungus(330, 'HoneyFungus'))
        self.assertTrue(any('zone of the active HoneyFungus' in e for e in errors), errors)

    def test_the_mushrooms_own_zone_sides_are_honoured(self):
        near = self.fungus(330, 'HoneyFungus', extra='<property name="zone" type="float" value="24"/>')  # zone 306..378: clear of 400
        self.assertEqual([], self.run_map(self.lookalike() + near))
        wide = self.fungus(100, extra='<property name="zoneRight" type="float" value="400"/>')  # 100..524 reaches 400
        self.assertTrue(any('zone of the active' in e for e in self.run_map(self.lookalike() + wide)))

    def test_a_lookalike_in_a_hint_zone_is_named(self):
        hint = '<object id="30" name="walk" type="Hint" x="380" y="0" width="100" height="368"/>'
        self.assertTrue(any('hint zone' in e for e in self.run_map(self.lookalike(), hint)))
        far = '<object id="30" name="walk" type="Hint" x="500" y="0" width="100" height="368"/>'
        self.assertEqual([], self.run_map(self.lookalike(), far))

    def test_a_lookalike_over_a_trap_or_pickup_is_named(self):
        coin = '<object id="31" name="Coin" type="Collectable" x="410" y="290" width="16" height="16"/>'
        self.assertTrue(any('hides Collectable' in e for e in self.run_map(self.lookalike() + coin)))

    def test_more_than_two_lookalikes_are_named(self):
        three = ''.join(self.lookalike(f'x="{400 + 100 * i}" y="304" width="24" height="16"', 50 + i) for i in range(3))
        self.assertTrue(any('at most 2 per map' in e for e in self.run_map(three)))
        two = ''.join(self.lookalike(f'x="{400 + 100 * i}" y="304" width="24" height="16"', 50 + i) for i in range(2))
        self.assertEqual([], self.run_map(two))

    def test_a_bush_is_not_held_to_the_look_alike_rules(self):
        self.assertEqual([], self.run_map(decor(box='x="330" y="296" width="32" height="24"') + self.fungus(330)))

    def test_the_shipped_maps_keep_their_look_alikes_in_order(self):
        for name in ('forest-0', 'forest-1', 'forest-2'):
            self.assertEqual([], links.check_map(links.MAPS / f'{name}.tmx'))


class HangingHiveLinksTest(unittest.TestCase):
    """#365 (D90): mirror of HangingHive.fromMap / floorBelow / movingSolidProblem / validate and Level.spawnHive."""
    FLOOR = '<object id="9" x="0" y="320" width="640" height="48"/>'
    ROOM = HoneyFungusLinksTest.ROOM

    @staticmethod
    def hive(x=288, y=200, props='', w=24, h=32):
        body = f'<properties>{props}</properties>' if props else ''
        return f'<object id="3" type="HangingHive" x="{x}" y="{y}" width="{w}" height="{h}">{body}</object>'

    def run_map(self, trap, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_a_hive_over_open_floor_passes(self):
        self.assertEqual([], self.run_map(self.hive()))

    def test_properties_size_and_floor(self):
        self.assertIn("unknown property 'perod'", self.run_map(self.hive(props='<property name="perod" value="4"/>'))[0])
        self.assertIn('not a number', self.run_map(self.hive(props='<property name="period" value="4,5"/>'))[0])
        self.assertIn('must be 24x32', self.run_map(self.hive(w=16))[0])
        self.assertIn('no solid under its mouth', self.run_map(self.hive(), collisions='')[0])
        self.assertIn('minimum is 32', self.run_map(self.hive(y=280))[0])

    def test_period_leaves_calm_at_the_floor(self):
        # band 94 px: 1.0 + 0.392 + 1.2 + 0.8 = 3.39 s at least
        self.assertEqual([], self.run_map(self.hive(props='<property name="period" value="3.4"/>')))
        self.assertIn('shorter than curtain', self.run_map(self.hive(props='<property name="period" value="3.3"/>'))[0])

    def test_band_stops_on_the_first_solid_and_never_covers_a_standing_place(self):
        ledge = self.FLOOR + '<object id="10" x="280" y="260" width="40" height="16"/>'
        self.assertEqual([], self.run_map(self.hive(), collisions=ledge))  # band ends on the ledge, 34 px deep
        self.assertIn('covers the room entrance', self.run_map(self.hive(x=40))[0])
        self.assertIn('covers the room exit', self.run_map(self.hive(x=600))[0])
        lift = '<object id="11" type="Escalator" x="290" y="280" width="32" height="16"/>'
        self.assertIn('moving or switchable Escalator [11]', self.run_map(self.hive(), spawn=lift)[0])


class EmberFissureLinksTest(unittest.TestCase):
    """#366 (D91): mirror of EmberFissure.fromMap / validate / ceilingAbove / floorProblem / movingSolidProblem and Level.fissureFor."""
    FLOOR = HangingHiveLinksTest.FLOOR
    ROOM = HoneyFungusLinksTest.ROOM

    @staticmethod
    def fissure(x=288, y=316, props='', w=16, h=12):
        body = f'<properties>{props}</properties>' if props else ''
        return f'<object id="3" type="EmberFissure" x="{x}" y="{y}" width="{w}" height="{h}">{body}</object>'

    def run_map(self, trap, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_a_fissure_on_open_floor_passes(self):
        self.assertEqual([], self.run_map(self.fissure()))

    def test_properties_size_and_rhythm(self):
        self.assertIn("unknown property 'perod'", self.run_map(self.fissure(props='<property name="perod" value="4"/>'))[0])
        self.assertIn('not a number', self.run_map(self.fissure(props='<property name="period" value="3,5"/>'))[0])
        self.assertIn('must be 16x12', self.run_map(self.fissure(w=24))[0])
        self.assertEqual([], self.run_map(self.fissure(props='<property name="period" value="3.0"/>')))  # 1.0 + 1.2 + 0.8
        self.assertIn('shorter than jet', self.run_map(self.fissure(props='<property name="period" value="2.9"/>'))[0])
        self.assertIn('jet 0.5', self.run_map(self.fissure(props='<property name="jet" value="0.5"/>'))[0])
        self.assertIn('multiple of 16', self.run_map(self.fissure(props='<property name="height" value="100"/>'))[0])

    def test_floor_ceiling_and_platforms(self):
        self.assertIn('needs a solid floor', self.run_map(self.fissure(y=300))[0])
        half = '<object id="9" x="0" y="320" width="296" height="48"/>'
        self.assertIn('needs a solid floor', self.run_map(self.fissure(), collisions=half)[0])
        split = '<object id="9" x="0" y="320" width="296" height="48"/><object id="10" x="296" y="320.3" width="344" height="48"/>'
        self.assertEqual([], self.run_map(self.fissure(), collisions=split))
        branch = self.FLOOR + '<object id="11" type="Platform" x="280" y="272" width="32" height="16"/>'
        self.assertEqual([], self.run_map(self.fissure(), collisions=branch))  # embers pass through a one-way branch
        low = self.FLOOR + '<object id="12" x="280" y="260" width="32" height="40"/>'
        self.assertIn('cuts its jet to 20.0 px', self.run_map(self.fissure(), collisions=low)[0])
        high = self.FLOOR + '<object id="12" x="280" y="200" width="32" height="40"/>'
        self.assertEqual([], self.run_map(self.fissure(), collisions=high))  # cut to 80 px, still a column

    def test_never_over_a_standing_place_and_no_moving_solid_in_the_jet(self):
        self.assertIn('covers the room entrance', self.run_map(self.fissure(x=40))[0])
        self.assertIn('covers the room exit', self.run_map(self.fissure(x=604))[0])
        lift = '<object id="11" type="Escalator" x="280" y="250" width="32" height="16"/>'
        self.assertIn('moving or switchable Escalator [11]', self.run_map(self.fissure(), spawn=lift)[0])

    def test_the_java_fixture_passes_the_mirror(self):
        fixture = Path(__file__).resolve().parents[1] / 'core/src/test/resources/tiles/fissure-test.tmx'
        self.assertEqual([], check(fixture.read_text(encoding='utf-8')))


class MosquitoSwarmLinksTest(unittest.TestCase):
    """#645 (D151): mirror of MosquitoSwarm.problem and Level.validateSwarm."""
    FLOOR = HangingHiveLinksTest.FLOOR
    ROOM = HoneyFungusLinksTest.ROOM

    @staticmethod
    def swarm(x=309, y=298, neg=4, pos=4, vertical=False):
        v = '<property name="isVertical" type="bool" value="true"/>' if vertical else ''
        return (f'<object id="3" type="MosquitoSwarm" x="{x}" y="{y}" width="22" height="18"><properties>{v}'
                f'<property name="offNeg" value="{neg}"/><property name="offPos" value="{pos}"/></properties></object>')

    def run_map(self, swarm, collisions=FLOOR, rooms=ROOM):
        return check(tmx(swarm, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_low_high_and_vertical_lines_pass(self):
        for swarm in (self.swarm(), self.swarm(y=302), self.swarm(y=278), self.swarm(y=298, neg=3, pos=0, vertical=True)):
            self.assertEqual([], self.run_map(swarm), swarm)

    def test_no_free_way_past(self):
        self.assertIn('no way past', self.run_map(self.swarm(y=284))[0])
        ceiling = self.FLOOR + '<object id="12" x="200" y="220" width="240" height="24"/>'
        self.assertIn('no way past', self.run_map(self.swarm(), collisions=ceiling)[0])
        self.assertEqual([], self.run_map(self.swarm(y=270), collisions=ceiling))
        self.assertIn('no way past open', self.run_map(self.swarm(y=274, neg=0, pos=0.625, vertical=True))[0])

    def test_numbers_room_solids_standing_places_and_floor(self):
        self.assertIn('offNeg', self.run_map(self.swarm(neg=-1))[0])
        self.assertIn('leaving the room', self.run_map(self.swarm(x=20, pos=0))[0])
        self.assertIn('camera', self.run_map(self.swarm(y=4, neg=0, pos=0))[0])
        post = self.FLOOR + '<object id="12" x="390" y="290" width="16" height="30"/>'
        self.assertIn('solid', self.run_map(self.swarm(), collisions=post)[0])
        self.assertIn('entrance', self.run_map(self.swarm(x=72, neg=0))[0])
        broken = '<object id="9" x="0" y="320" width="300" height="48"/><object id="10" x="340" y="320" width="300" height="48"/>'
        self.assertIn('needs a floor', self.run_map(self.swarm(), collisions=broken)[0])

    def test_the_java_fixtures_pass_or_fail_as_in_the_game(self):
        tiles = Path(__file__).resolve().parents[1] / 'core/src/test/resources/tiles'
        for name in ('mosquito-test', 'mosquito-still', 'mosquito-run', 'mosquito-high', 'mosquito-vertical'):
            self.assertEqual([], check((tiles / f'{name}.tmx').read_text(encoding='utf-8')), name)
        self.assertIn('entrance', check((tiles / 'mosquito-entrance.tmx').read_text(encoding='utf-8'))[0])


class LegHoldLinksTest(unittest.TestCase):
    """#561 (D128): mirror of LegHold.fromMap / validate / problem and Level.legHoldFor."""
    FLOOR = HangingHiveLinksTest.FLOOR
    ROOM = HoneyFungusLinksTest.ROOM

    @staticmethod
    def hold(x=288, y=304, props='', w=32, h=16):
        body = f'<properties>{props}</properties>' if props else ''
        return f'<object id="3" type="LegHold" x="{x}" y="{y}" width="{w}" height="{h}">{body}</object>'

    def run_map(self, trap, spawn='', collisions=FLOOR, rooms=ROOM):
        return check(tmx(trap + spawn, collisions=collisions).replace('<objectgroup name="Spawn', rooms + '<objectgroup name="Spawn', 1))

    def test_a_leghold_on_open_floor_passes(self):
        self.assertEqual([], self.run_map(self.hold()))

    def test_properties_size_and_ranges(self):
        self.assertIn("unknown property 'zon'", self.run_map(self.hold(props='<property name="zon" value="24"/>'))[0])
        self.assertIn('not a number', self.run_map(self.hold(props='<property name="zone" value="2,4"/>'))[0])
        self.assertIn('must be 32x16', self.run_map(self.hold(w=16))[0])
        self.assertIn('zone 8.0', self.run_map(self.hold(props='<property name="zone" value="8"/>'))[0])
        self.assertIn('window 0.5', self.run_map(self.hold(props='<property name="window" value="0.5"/>'))[0])
        self.assertEqual([], self.run_map(self.hold(props='<property name="zone" value="16"/><property name="window" value="3"/>')))

    def test_floor_headroom_and_platforms(self):
        self.assertIn('needs a floor', self.run_map(self.hold(y=290))[0])
        half = '<object id="9" x="0" y="320" width="300" height="48"/>'
        self.assertIn('needs a floor', self.run_map(self.hold(), collisions=half)[0])
        platform = '<object id="9" type="Platform" x="256" y="320" width="96" height="16"/>'
        self.assertEqual([], self.run_map(self.hold(), collisions=platform))  # a one-way platform is a floor
        roof = self.FLOOR + '<object id="12" x="280" y="250" width="64" height="16"/>'
        self.assertIn('free height', self.run_map(self.hold(), collisions=roof)[0])
        overhead = self.FLOOR + '<object id="12" type="Platform" x="280" y="240" width="64" height="16"/>'
        self.assertEqual([], self.run_map(self.hold(), collisions=overhead))  # forest-entry's ledge is overhead

    def test_never_over_a_standing_place(self):
        self.assertIn('covers the room entrance', self.run_map(self.hold(x=40))[0])
        self.assertIn('covers the room exit', self.run_map(self.hold(x=590))[0])

    def test_the_lance_root_has_the_fissures_rules(self):
        root = '<object id="3" type="LanceRoot" x="288" y="316" width="16" height="12"/>'
        self.assertEqual([], self.run_map(root))
        bad = '<object id="3" type="LanceRoot" x="288" y="316" width="24" height="12"/>'
        self.assertIn('LanceRoot', self.run_map(bad)[0])

    def test_the_java_fixtures_pass_the_mirror(self):
        for name in ('leghold-test.tmx', 'lance-test.tmx'):
            fixture = Path(__file__).resolve().parents[1] / 'core/src/test/resources/tiles' / name
            self.assertEqual([], check(fixture.read_text(encoding='utf-8')), name)
