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
        torch = '<object id="2" name="a" type="Ladder" x="0" y="0"/>'
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
        self.assertEqual({'bush', 'lookalike'}, set(links.decor_kinds()))
        self.assertEqual(('lookalike',), links.decor_still_kinds())


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


if __name__ == '__main__':
    unittest.main()


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
