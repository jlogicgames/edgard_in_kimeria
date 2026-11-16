#!/usr/bin/env python3
"""Behavioural checks for the shared diff base of the changed-line gates."""
import contextlib
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
import git_base


class GitBaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        for message in ('one', 'two', 'three'):
            self.git('commit', '-q', '--allow-empty', '-m', message)

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(['git', '-C', self.tmp.name, '-c', 'user.email=t@example.invalid', '-c', 'user.name=t', *args],
                              check=True, capture_output=True, text=True).stdout.strip()

    def test_auto_is_the_merge_base_with_origin_main(self):
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD~2')
        self.assertEqual(self.git('rev-parse', 'HEAD~2'), git_base.resolve(self.root, 'auto', 'Gate'))

    def test_auto_without_origin_main_is_head_with_a_note(self):
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual('HEAD', git_base.resolve(self.root, 'auto', 'Gate'))
        self.assertIn('Gate: no origin/main ref', err.getvalue())

    def test_head_and_explicit_refs(self):
        self.assertEqual('HEAD', git_base.resolve(self.root, 'HEAD', 'Gate'))
        self.assertEqual(self.git('rev-parse', 'HEAD~1'), git_base.resolve(self.root, 'HEAD~1', 'Gate'))

    def test_hooks_keep_head_and_plain_runs_default_to_auto(self):
        self.assertEqual('HEAD', git_base.default_base(None, hook=True))
        self.assertEqual('auto', git_base.default_base(None, hook=False))
        self.assertEqual('origin/main', git_base.default_base('origin/main', hook=True))


if __name__ == '__main__':
    unittest.main()
