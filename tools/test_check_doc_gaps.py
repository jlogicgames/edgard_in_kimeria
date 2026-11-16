#!/usr/bin/env python3
"""Behavioural checks for the doc-gap gate: each marker fires, an issue reference clears it, only changed blocks count."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import check_doc_gaps as gate


class DocGapTest(unittest.TestCase):
    def found(self, text, added=None):
        return gate.violations('docs/X.md', text, added)

    def test_every_marker_fires_without_an_issue(self):
        for line in ('TODO: measure it.', 'The cue is not yet drawn.', 'A known gap stays.', 'The time is to be measured.',
                     'It needs an upstream fix.', 'The plain skin is pending.'):
            self.assertEqual(1, len(self.found(line)), line)

    def test_an_issue_reference_in_the_same_block_clears_it(self):
        for ref in ('#362', '(#362)', '[#362](https://github.com/o/r/issues/362)', 'https://github.com/o/r/issues/362'):
            self.assertEqual([], self.found('The plain skin is pending,\ntracked by ' + ref + '.'), ref)

    def test_anchors_placeholders_and_entities_are_not_issue_references(self):
        for fake in ('#2-foo', '[the draft](TEXT_AUDIT.md#4-draft-style-system)', '#NNN', '&#35;12', 'issues/12-x',
                     '`#362`', 'v1.#3'):
            self.assertEqual(1, len(self.found('The plain skin is pending, see ' + fake + '.')), fake)

    def test_inline_code_quotes_syntax_and_is_not_judged(self):
        self.assertEqual([], self.found('A row is written `pending #NNN` when its cue is open.'))
        self.assertEqual([], self.found('Run `grep TODO` over the tree.'))

    def test_the_reference_must_be_in_the_same_block(self):
        self.assertEqual(1, len(self.found('The cue is not yet drawn.\n\nSee #362 for something else.')))
        self.assertEqual(1, len(self.found('- The cue is not yet drawn.\n- Another item, #362.')))
        self.assertEqual(1, len(self.found('| a | not yet drawn |\n| b | #362 |')))

    def test_words_that_only_look_like_markers_do_not_fire(self):
        for line in ('Depending on the room.', 'A todo list.', 'An element the player has not yet met.',
                     'Not yet seen by the hero in play order.', 'See [Pre-release TODO](PRE_RELEASE_TODO.md).', 'Issue &#35;5'):
            self.assertEqual([], self.found(line), line)

    def test_a_heading_is_a_title_not_a_gap(self):
        self.assertEqual([], self.found('## Known gaps'))
        self.assertEqual(1, len(self.found('## Known gaps\n\n- The cue is not yet drawn.')))

    def test_fenced_code_is_skipped(self):
        self.assertEqual([], self.found('```\nTODO in an example\n```'))

    def test_only_blocks_with_an_added_line_are_judged(self):
        text = 'Old gap, not yet built.\n\nNew line, not yet built.\n'
        self.assertEqual([], self.found(text, added=set()))
        found = self.found(text, added={3})
        self.assertEqual(1, len(found))
        self.assertIn('docs/X.md:3', found[0])
        self.assertEqual(1, len(self.found('A long paragraph\nwhose second line is new and not yet built.', added={1})))


class DocGapRepositoryTest(unittest.TestCase):
    def run_gate(self, root, *args, stdin=''):
        return subprocess.run([sys.executable, str(Path(gate.__file__)), '--root', str(root), *args],
                              input=stdin, text=True, capture_output=True)

    def test_changed_and_untracked_docs_against_head_and_hook_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            git = lambda *a: subprocess.run(['git', '-C', tmp, *a], check=True, capture_output=True)
            git('init', '-q')
            git('config', 'user.email', 't@example.invalid')
            git('config', 'user.name', 't')
            (root / 'docs').mkdir()
            (root / 'docs/A.md').write_text('Old line, not yet built.\n')
            git('add', '.')
            git('commit', '-qm', 'base')
            self.assertEqual(0, self.run_gate(root).returncode, 'committed history is not re-judged')
            (root / 'docs/A.md').write_text('Old line, not yet built.\n\nA new gap, not yet built.\n')
            result = self.run_gate(root)
            self.assertEqual(2, result.returncode)
            self.assertIn('docs/A.md:3', result.stderr)
            (root / 'docs/A.md').write_text('Old line, not yet built.\n\nA new gap, not yet built (#7).\n')
            hook = self.run_gate(root, '--hook', stdin=json.dumps({'hook_event_name': 'Stop'}))
            self.assertEqual((0, '{}'), (hook.returncode, hook.stdout.strip()))
            (root / 'docs/B.md').write_text('Untracked: TODO.\n')
            self.assertIn('docs/B.md:1', self.run_gate(root).stderr)

    def test_the_default_base_is_the_merge_base_with_origin_main(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            git = lambda *a: subprocess.run(['git', '-C', tmp, '-c', 'user.email=t@example.invalid', '-c', 'user.name=t', *a],
                                            check=True, capture_output=True)
            git('init', '-q')
            (root / 'docs').mkdir()
            (root / 'docs/A.md').write_text('Base.\n')
            git('add', '.')
            git('commit', '-qm', 'base')
            git('commit', '-q', '--allow-empty', '-m', 'nothing')
            (root / 'docs/A.md').write_text('Base.\n\nCommitted on the branch, not yet built.\n')
            git('commit', '-qam', 'branch')
            no_ref = self.run_gate(root)
            self.assertEqual(0, no_ref.returncode, 'without origin/main only the clean working tree is checked')
            self.assertIn('no origin/main ref', no_ref.stderr)
            git('update-ref', 'refs/remotes/origin/main', 'HEAD~1')
            branch = self.run_gate(root)
            self.assertEqual(2, branch.returncode, 'a committed gap on the branch is found')
            self.assertIn('docs/A.md:3', branch.stderr)
            self.assertEqual(0, self.run_gate(root, '--base', 'HEAD').returncode, '--base HEAD checks uncommitted changes only')


if __name__ == '__main__':
    unittest.main()
