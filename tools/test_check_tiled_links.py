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

    def test_shipped_maps_pass(self):
        self.assertEqual(0, links.main([]))


if __name__ == '__main__':
    unittest.main()
