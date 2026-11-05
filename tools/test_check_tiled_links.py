#!/usr/bin/env python3
"""Behavioral checks for the Tiled link validator."""
from pathlib import Path
import tempfile
import unittest
import check_tiled_links as links


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


if __name__ == '__main__':
    unittest.main()
