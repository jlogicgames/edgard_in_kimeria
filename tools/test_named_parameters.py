#!/usr/bin/env python3
"""Behavioral checks for changed-code numeric enforcement and real hook failures."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import check_named_parameters as guard


class NamedParametersTest(unittest.TestCase):
    def test_tuning_values_need_an_owner_and_named_constant(self):
        for expression in ('velocity.y += 600 * dt;', 'float life = 2;', 'super(x, y, 12, 12);',
                           'batch.draw(sprite, x + 13, y, 16, 16);', 'setColor(1, .82f, .3f, 1);'):
            self.assertTrue(guard.java_violations('NewItem.java', 'void update() { '+expression+' }'))

    def test_constants_and_named_authored_tables_are_data(self):
        source = '''private static final float GRAVITY = 600;
        private static final int[][] PALMS = {{31,34}, {31,35}};
        public static final Sheet TORCH = new Sheet("torch.png", 8, 16);
        void update() { final int OUTLINE_WIDTH = 3; velocity.y += GRAVITY * dt; }'''
        self.assertFalse(guard.java_violations('Item.java', source))

    def test_comments_strings_indices_and_halving_are_not_tuning_parameters(self):
        source = '''// 600 used to be a literal
        String message = "width 150";
        void render() { var first = frames[0]; int next = i + 1;
            var center = width / 2f; var mirrored = 2 * pivot - x; }'''
        self.assertFalse(guard.java_violations('Item.java', source))

    def test_hex_and_scientific_literals_do_not_crash_or_bypass(self):
        self.assertTrue(guard.java_violations('Item.java', 'int color = 0xff; double tolerance = 1e-3;'))
        self.assertFalse(guard.java_violations('Item.java', 'static final int COLOR = 0xff;'))

    def test_unchanged_legacy_lines_are_not_claimed_as_checked(self):
        source = 'float old = 77;\nfloat added = 123;\n'
        issues = guard.java_violations('Item.java', source, {2})
        self.assertEqual(1, len(issues)); self.assertIn(':2:', issues[0])

    def test_new_classes_are_checked_independently_of_names(self):
        for name in ('NewItem.java', 'OtherCamera.java', 'RenamedHud.java'):
            self.assertTrue(guard.java_violations(name, 'class X { float setting = 123; }'))

    def test_export_geometry_must_be_named_but_metadata_is_preserved(self):
        self.assertTrue(guard.python_violations('tools/export.py',
            "from PIL import Image\nimage = Image.new('RGBA', (96, 48))"))
        self.assertFalse(guard.python_violations('tools/export.py',
            "from PIL import Image\nFRAME_SIZE = (96, 48)\nimage = Image.new('RGBA', FRAME_SIZE)"))
        self.assertFalse(guard.python_violations('tools/parser.py', 'HEADER_BYTES = 13'))

    def test_hook_rejects_real_untracked_and_staged_source_and_recovers_after_fix(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            source = root / 'core/src/main/java/Item.java'
            source.parent.mkdir(parents=True)
            source.write_text('class Item { float speed = 280; }')
            for staged in (False, True):
                if staged:
                    subprocess.run(['git', '-C', directory, 'add', '--', str(source)], check=True)
                for event in ('PostToolUse', 'Stop'):
                    result = subprocess.run([sys.executable, guard.__file__, '--root', directory, '--hook'],
                        input=json.dumps({'hook_event_name': event}), text=True, capture_output=True)
                    self.assertEqual(2, result.returncode, result.stderr)
                    self.assertIn('280', result.stderr)
            source.write_text('class Item { static final float SPEED = 280; float speed = SPEED; }')
            result = subprocess.run([sys.executable, guard.__file__, '--root', directory, '--hook'],
                input='{}', text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual({}, json.loads(result.stdout))

    def test_java_export_tools_are_checked_too(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(['git', 'init', '-q', directory], check=True)
            source = Path(directory) / 'tools/Export.java'
            source.parent.mkdir()
            source.write_text('class Export { int width = 48; }')
            issues = guard.check(Path(directory))
            self.assertTrue(issues)
            self.assertIn('tools/Export.java', issues[0])

    def test_missing_git_repository_fails_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, guard.__file__, '--root', directory], text=True, capture_output=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('could not run', result.stderr)


if __name__ == '__main__':
    unittest.main()
