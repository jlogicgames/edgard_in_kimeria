#!/usr/bin/env python3
"""Behavioural checks for the per-frame allocation scanner: rejection, recovery and the limits it states."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import check_allocations as guard

SOURCE = 'core/src/main/java/com/jlogicsoftware/kimeria/effects/Item.java'


def wrap(body, name='update'):
    return 'class Item { void %s(float dt) { %s } }' % (name, body)


class AllocationCheckTest(unittest.TestCase):
    def kinds(self, source, path='effects/Item.java'):
        return {kind for _, _, kind, _ in guard.hits(path, source)}

    def test_new_objects_and_arrays_are_rejected_in_per_frame_methods(self):
        for body in ('Vector2 v = new Vector2();', 'float[] a = new float[4];', 'var p = new Particle<>(1);'):
            for name in ('update', 'render', 'draw', 'updateHint', 'renderDebug', 'drawShadow'):
                self.assertEqual({'new'}, self.kinds(wrap(body, name)), (name, body))

    def test_exception_and_error_types_are_error_paths(self):
        self.assertFalse(guard.hits('effects/Item.java', wrap('throw new IllegalStateException("x");')))
        self.assertFalse(guard.hits('effects/Item.java', wrap('throw new AssertionError(1);')))

    def test_constructors_and_other_methods_may_allocate(self):
        self.assertFalse(guard.hits('effects/Item.java', 'class Item { Item() { v = new Vector2(); } void load() { w = new int[3]; } }'))
        self.assertFalse(guard.hits('effects/Item.java', 'class Item { public Item(float x) { v = new Vector2(); } }'))

    def test_strings_are_allocation(self):
        for body in ('String s = String.format("%d", n);', 'String s = String.valueOf(n);', 'String s = "a" + n;',
                     'String s = n + "px";', 's += "x";'):
            self.assertEqual({'string'}, self.kinds(wrap(body)), body)
        self.assertFalse(guard.hits('effects/Item.java', wrap('// "a" + b and new Vector2() in a comment\nint x = 1 + 2;')))
        self.assertFalse(guard.hits('effects/Item.java', wrap('log("new Vector2() + here");')))

    def test_factories_and_streams(self):
        for body in ('list.stream().count();', 'var l = List.of(1);', 'var s = Set.of(1);', 'var m = Map.of();',
                     'var l = Arrays.asList(1);', 'var l = Collections.singletonList(1);'):
            self.assertEqual({'factory'}, self.kinds(wrap(body)), body)

    def test_for_each_over_declared_collections_only(self):
        source = 'class Item { List<Foo> items; Array<Foo> pool; void update(float dt) { for (Foo f : items) f.update(dt);\n for (Foo f : this.items) f.update(dt); } void render() { for (Foo f : pool) f.draw(); } }'
        self.assertEqual({'iterator'}, self.kinds(source))
        self.assertEqual(2, len(guard.hits('effects/Item.java', source)))
        self.assertFalse(guard.hits('effects/Item.java', 'class Item { Array<Foo> pool; void render() { for (Foo f : pool) f.draw(); for (int i = 0; i < n; i++) g(i); } }'))
        self.assertEqual({'iterator'}, self.kinds('class Item { Map<String, Foo> by; void render() { for (Foo f : by.values()) f.draw(); } }'))
        self.assertEqual({'iterator'}, self.kinds('class Item { void render(Set<Foo> seen) { for (Foo f : seen) f.draw(); } }'))

    def test_lambda_is_flagged_but_switch_arrows_are_not(self):
        self.assertEqual({'lambda'}, self.kinds(wrap('Runnable r = () -> go(dt);')))
        self.assertFalse(guard.hits('effects/Item.java', wrap('int x = switch (k) { case 1 -> 2; default -> 3; };')))
        self.assertFalse(guard.hits('effects/Item.java', wrap('switch (k) { case A, B -> go(); default -> stop(); }')))

    def test_named_helpers_of_a_hot_method_are_listed_and_checked(self):
        source = 'class Torch { private void spawnCore() { Particle p = new Particle(); } private void other() { Particle p = new Particle(); } }'
        self.assertEqual(1, len(guard.hits('effects/Torch.java', source)))
        self.assertFalse(guard.hits('effects/Other.java', source))

    def test_allowlist_is_per_file_method_and_kind(self):
        source = 'class Level { List<Foo> items; void renderDebug() { for (Foo f : items) f.draw(); Vector2 v = new Vector2(); } }'
        issues = guard.violations('world/Level.java', source)
        self.assertEqual(1, len(issues), issues)
        self.assertIn('`new`', issues[0])
        self.assertEqual(2, len(guard.violations('world/Other.java', source)))

    def test_every_allowlist_and_hot_entry_has_a_reason_and_points_at_real_code(self):
        for table in (guard.ALLOWED, guard.HOT_METHODS):
            for (path, method, *_), reason in table.items():
                self.assertGreater(len(reason), 20, (path, method))
                file = guard.ROOT / guard.BASE / path
                self.assertTrue(file.is_file(), path)
                self.assertTrue(any(name == method for _, _, name in guard.methods(guard.code_only(file.read_text(encoding='utf-8')))), (path, method))
        real = {(path, owner, kind) for path in (p.relative_to(guard.ROOT / guard.BASE).as_posix()
                for p in (guard.ROOT / guard.BASE).rglob('*.java'))
                for _, owner, kind, _ in guard.hits(path, (guard.ROOT / guard.BASE / path).read_text(encoding='utf-8'))}
        for entry in guard.ALLOWED:
            self.assertIn(entry, real, f'dead allowlist entry {entry}')

    def test_only_changed_lines_are_checked(self):
        source = 'class Item {\n void update() {\n  Vector2 old = new Vector2();\n  Vector2 added = new Vector2();\n }\n}\n'
        issues = guard.violations('effects/Item.java', source, {4})
        self.assertEqual(1, len(issues)); self.assertIn(':4 ', issues[0])
        self.assertEqual(2, len(guard.violations('effects/Item.java', source)))

    def run_hook(self, directory, event='PostToolUse', *extra):
        return subprocess.run([sys.executable, guard.__file__, '--root', directory, '--hook', *extra],
            input=json.dumps({'hook_event_name': event}), text=True, capture_output=True)

    def test_hook_rejects_untracked_and_staged_source_and_recovers_after_fix(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            source = root / SOURCE
            source.parent.mkdir(parents=True)
            source.write_text(wrap('Vector2 v = new Vector2();'))
            for staged in (False, True):
                if staged:
                    subprocess.run(['git', '-C', directory, 'add', '--', SOURCE], check=True)
                for event in ('PostToolUse', 'Stop'):
                    result = self.run_hook(directory, event)
                    self.assertEqual(2, result.returncode, result.stderr)
                    self.assertIn('Item.java:1', result.stderr)
            source.write_text(wrap('v.set(dt, dt);'))
            result = self.run_hook(directory)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual({}, json.loads(result.stdout))

    def test_committed_legacy_allocation_is_not_blamed_until_its_line_is_touched(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', directory], check=True)
            source = root / SOURCE
            source.parent.mkdir(parents=True)
            source.write_text('class Item {\n void update(float dt) {\n  Vector2 old = new Vector2();\n  x += dt;\n }\n}\n')
            subprocess.run(['git', '-C', directory, 'add', '--', SOURCE], check=True)
            subprocess.run(['git', '-C', directory, '-c', 'user.name=t', '-c', 'user.email=t@t', 'commit', '-q', '-m', 'legacy'], check=True)
            source.write_text(source.read_text().replace('x += dt;', 'x += dt * 2;'))
            self.assertEqual(0, self.run_hook(directory).returncode)
            source.write_text(source.read_text().replace('new Vector2()', 'new Vector2(1, 1)'))
            self.assertEqual(2, self.run_hook(directory).returncode)

    def test_all_mode_lists_every_hit_and_exits_zero(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / SOURCE
            source.parent.mkdir(parents=True)
            source.write_text(wrap('Vector2 v = new Vector2();'))
            result = subprocess.run([sys.executable, guard.__file__, '--root', directory, '--all'], text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('Item.java:1 (update) new', result.stdout)

    def test_missing_git_repository_fails_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([sys.executable, guard.__file__, '--root', directory], text=True, capture_output=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('could not run', result.stderr)


if __name__ == '__main__':
    unittest.main()
