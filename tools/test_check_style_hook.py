#!/usr/bin/env python3
"""Behavioural checks for the style hook dispatcher: which gate runs for which change, failures, reminders."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import check_style_hook as hook


class StyleHookTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        git = lambda *a: subprocess.run(['git', '-C', self.tmp.name, *a], check=True, capture_output=True)
        git('init', '-q')
        (self.root / 'tools').mkdir()
        (self.root / 'tools/pass.py').write_text('print("ok")\n')
        (self.root / 'tools/fail.py').write_text('import sys\nprint("broken text", file=sys.stderr)\nsys.exit(1)\n')
        git('add', '.')
        git('-c', 'user.email=t@example.invalid', '-c', 'user.name=t', 'commit', '-qm', 'base')
        self.saved = hook.GATES
        hook.GATES = (
            ('text style', lambda p: p.startswith('assets/localization/'), ['tools/fail.py']),
            ('doc gaps', lambda p: p.startswith('docs/'), ['tools/pass.py']),
        )

    def tearDown(self):
        hook.GATES = self.saved
        self.tmp.cleanup()

    def write(self, path, text='x\n'):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def test_no_change_runs_nothing(self):
        self.assertEqual(([], [], None, ''), hook.run(self.root, {}))

    def test_only_the_gates_that_apply_run(self):
        self.write('docs/A.md')
        failures, ran, reminder, _ = hook.run(self.root, {})
        self.assertEqual((['doc gaps'], [], None), (ran, failures, reminder))

    def test_a_failing_gate_is_reported(self):
        self.write('assets/localization/en.json')
        failures, ran, _, _ = hook.run(self.root, {})
        self.assertEqual(['text style'], ran)
        self.assertIn('broken text', failures[0])

    def test_post_tool_use_judges_only_the_touched_file(self):
        self.write('assets/localization/en.json')  # someone else's broken change in the shared tree
        self.write('src/Foo.java')
        unrelated = {'hook_event_name': 'PostToolUse', 'tool_input': {'file_path': str(self.root / 'src/Foo.java')}}
        self.assertEqual(([], []), hook.run(self.root, unrelated)[:2], 'an unrelated edit is not blocked')
        shell = {'hook_event_name': 'PostToolUse', 'tool_input': {'command': 'ls -la'}}
        self.assertEqual(([], []), hook.run(self.root, shell)[:2], 'a call that names no changed file runs nothing')
        touched = {'hook_event_name': 'PostToolUse', 'tool_input': {'command': "sed -i '' s/a/b/ assets/localization/en.json"}}
        failures, ran, _, _ = hook.run(self.root, touched)
        self.assertEqual(['text style'], ran)
        self.assertIn('broken text', failures[0])
        failures, ran, _, _ = hook.run(self.root, {'hook_event_name': 'Stop'})
        self.assertEqual(['text style'], ran, 'Stop sweeps every changed file')
        self.assertTrue(failures)

    def test_a_per_file_gate_gets_only_the_matching_paths(self):
        (self.root / 'tools/args.py').write_text('import sys\nprint(" ".join(sys.argv[1:]), file=sys.stderr)\nsys.exit(1)\n')
        hook.GATES = (('doc gaps', lambda p: p.startswith('docs/'), ['tools/args.py', '--only', hook.PATHS]),)
        self.write('docs/A.md')
        self.write('docs/B.md')
        failures, _, _, _ = hook.run(self.root, {'hook_event_name': 'PostToolUse', 'tool_input': {'file_path': 'docs/A.md'}})
        self.assertIn('--only docs/A.md', failures[0])
        self.assertNotIn('docs/B.md', failures[0])

    def test_an_empty_or_blank_payload_is_no_payload(self):
        for stdin in ('', '  \n'):
            result = subprocess.run([sys.executable, str(Path(hook.__file__)), '--root', str(self.root), '--hook'],
                                    input=stdin, text=True, capture_output=True)
            self.assertEqual((0, '{}'), (result.returncode, result.stdout.strip()), repr(stdin))

    def test_trap_and_room_inputs_get_the_gradle_command(self):
        self.write('docs/TRAPS.md')
        _, _, at_stop, _ = hook.run(self.root, {'hook_event_name': 'Stop'})
        self.assertIn('TrapCatalogTest', at_stop)
        _, _, unrelated_call, _ = hook.run(self.root, {'hook_event_name': 'PostToolUse', 'tool_input': {'command': 'ls'}})
        self.assertIsNone(unrelated_call, 'a PostToolUse reminder only for the call that touched the file')
        _, _, edit, _ = hook.run(self.root, {'hook_event_name': 'PostToolUse',
                                             'tool_input': {'file_path': str(self.root / 'docs/ROOM_CARDS.md')}})
        self.assertIn('docs/ROOM_CARDS.md', edit)

    def test_the_real_gate_table_routes_paths(self):
        routes = {name: applies for name, applies, _ in self.saved}
        self.assertTrue(routes['text style']('assets/localization/de.json'))
        self.assertTrue(routes['text style']('assets/tiles/forest-0.tmx'))
        self.assertTrue(routes['map links']('assets/tiles/ForestTerrain.tsx'))
        self.assertTrue(routes['doc gaps']('docs/sub/X.md'))
        self.assertFalse(routes['doc gaps']('docs/references/a.png'))
        self.assertTrue(routes['skills']('.claude/skills/kimeria-sound/SKILL.md'))
        self.assertTrue(routes['contrast']('assets/images/objects/bush.png'))
        self.assertTrue(routes['contrast']('assets/tiles/forest-1.tmx'))
        self.assertFalse(routes['contrast']('assets/localization/en.json'))
        self.assertFalse(any(applies('core/src/main/java/A.java') for applies in routes.values()))


if __name__ == '__main__':
    unittest.main()
