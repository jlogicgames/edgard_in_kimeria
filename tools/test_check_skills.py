#!/usr/bin/env python3
"""Behavioural checks for the skills gate: the repository passes, and each rule fires on a broken copy."""
from pathlib import Path
import tempfile
import unittest
import check_skills as gate


def skill(name, description='Does a thing. Use when a thing.', body='# Body\n\nSteps.\n'):
    return f'---\nname: {name}\ndescription: {description}\n---\n\n{body}'


def pointer(name, description='Does a thing. Use when a thing.'):
    return skill(name, description, f'# {name} (Claude Code pointer)\n\nFollow `.agents/skills/{name}/SKILL.md`.\n')


class SkillsGateTest(unittest.TestCase):
    def tree(self, tmp, shared=None, claude=None, index=None):
        root = Path(tmp)
        shared = {'kimeria-a': skill('kimeria-a'), 'libgdx-b': skill('libgdx-b')} if shared is None else shared
        claude = {'kimeria-a': pointer('kimeria-a')} if claude is None else claude
        for name, text in shared.items():
            (root / gate.SHARED / name).mkdir(parents=True)
            (root / gate.SHARED / name / 'SKILL.md').write_text(text)
        for name, text in claude.items():
            (root / gate.CLAUDE / name).mkdir(parents=True)
            (root / gate.CLAUDE / name / 'SKILL.md').write_text(text)
        listed = index if index is not None else ''.join(f'[{n}]({n}/SKILL.md)\n' for n in shared)
        (root / gate.INDEX).write_text(listed)
        return root

    def issues(self, **kwargs):
        with tempfile.TemporaryDirectory() as tmp:
            return gate.check(self.tree(tmp, **kwargs))

    def test_the_repository_passes(self):
        self.assertEqual([], gate.check(gate.ROOT))

    def test_a_consistent_tree_passes(self):
        self.assertEqual([], self.issues())

    def test_an_unlisted_folder_such_as_an_imported_old_pointer_fails(self):
        codex_copy = skill('design-room', body='# Design a room\n\nThis is a pointer for Codex. Follow ...\n')
        found = self.issues(shared={'kimeria-a': skill('kimeria-a'), 'design-room': codex_copy},
                            index='[kimeria-a](kimeria-a/SKILL.md)\n')
        self.assertTrue(any('design-room: not listed' in i for i in found), found)
        self.assertTrue(any('design-room/SKILL.md: is a pointer' in i for i in found), found)

    def test_a_shared_skill_overwritten_by_a_pointer_fails(self):
        found = self.issues(shared={'kimeria-a': pointer('kimeria-a')})
        self.assertTrue(any('is a pointer, not a shared skill' in i for i in found), found)

    def test_name_mismatch_missing_pointer_and_orphan_pointer_fail(self):
        found = self.issues(shared={'kimeria-a': skill('other')}, claude={})
        self.assertTrue(any('frontmatter name must be "kimeria-a"' in i for i in found), found)
        self.assertTrue(any('no Claude pointer' in i for i in found), found)
        found = self.issues(claude={'kimeria-a': pointer('kimeria-a'), 'game-text': pointer('game-text')})
        self.assertTrue(any('no shared skill .agents/skills/game-text' in i for i in found), found)

    def test_a_pointer_must_match_link_and_stay_thin(self):
        found = self.issues(claude={'kimeria-a': pointer('kimeria-a', 'Something else.')})
        self.assertTrue(any('must equal the shared skill' in i for i in found), found)
        found = self.issues(claude={'kimeria-a': skill('kimeria-a', body='No link here.\n')})
        self.assertTrue(any('does not link' in i for i in found), found)
        fat = pointer('kimeria-a') + 'more\n' * 20
        self.assertTrue(any('stays thin' in i for i in self.issues(claude={'kimeria-a': fat})))

    def test_non_english_metadata_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp)
            (root / gate.SHARED / 'kimeria-a' / 'agents').mkdir()
            (root / gate.SHARED / 'kimeria-a' / 'agents' / 'openai.yaml').write_text('interface:\n  display_name: "Кимерия"\n')
            self.assertTrue(any('not English' in i for i in gate.check(root)))


if __name__ == '__main__':
    unittest.main()
