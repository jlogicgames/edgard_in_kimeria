#!/usr/bin/env python3
"""Skills gate (D75, `.agents/skills/README.md`).

Checks that the shared skills and the Claude pointers stay one consistent set:
  S1 every `.agents/skills/<dir>` has a SKILL.md whose frontmatter `name` is the folder name and a description;
  S2 every such folder is listed in the index `.agents/skills/README.md` (an unlisted folder is usually one the Codex
     app's Claude import created from an old pointer name; delete it);
  S3 every `kimeria-*` skill has a Claude pointer `.claude/skills/<same name>/SKILL.md`;
  S4 every Claude pointer names an existing shared skill, has the same name and description, links to it and stays
     thin (at most POINTER_MAX_LINES lines);
  S5 a shared skill is never itself a pointer (the import would have overwritten it);
  S6 everything under `.agents/skills` and `.claude/skills` is English: no Cyrillic (AGENTS.md).
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHARED, CLAUDE, INDEX = '.agents/skills', '.claude/skills', '.agents/skills/README.md'
POINTER_MAX_LINES = 15
FRONTMATTER = re.compile(r'\A---\nname: *(?P<name>.+?) *\ndescription: *(?P<description>.+?) *\n---\n', re.DOTALL)
# Wording of a pointer, never of a real procedure: the Claude form below and the Codex copy the import writes.
POINTER_WORDS = re.compile(r'Claude Code pointer|pointer for (?:Claude|Codex)|\((?:Claude|Codex) entry point\)')
CYRILLIC = re.compile('[Ѐ-ӿ]')


def frontmatter(path):
    match = FRONTMATTER.match(path.read_text(encoding='utf-8'))
    return (match['name'], match['description']) if match else (None, None)


def check(root):
    issues = []
    shared_dir, claude_dir = root / SHARED, root / CLAUDE
    index = (root / INDEX).read_text(encoding='utf-8') if (root / INDEX).is_file() else ''
    if not index:
        issues.append(f'{INDEX}: missing (the index of every skill)')
    shared = {}
    for folder in sorted(p for p in shared_dir.iterdir() if p.is_dir()):
        skill = folder / 'SKILL.md'
        if not skill.is_file():
            issues.append(f'{SHARED}/{folder.name}: no SKILL.md')
            continue
        name, description = frontmatter(skill)
        if name != folder.name or not description:
            issues.append(f'{SHARED}/{folder.name}/SKILL.md: frontmatter name must be "{folder.name}" with a description, got {name!r}')
        shared[folder.name] = description
        if index and f'[{folder.name}]({folder.name}/SKILL.md)' not in index:
            issues.append(f'{SHARED}/{folder.name}: not listed in {INDEX}; list it, or delete the folder if the Codex Claude '
                          'import created it from an old pointer name')
        if POINTER_WORDS.search(skill.read_text(encoding='utf-8')):
            issues.append(f'{SHARED}/{folder.name}/SKILL.md: is a pointer, not a shared skill (restore it from git)')
    for name in shared:
        if name.startswith('kimeria-') and not (claude_dir / name / 'SKILL.md').is_file():
            issues.append(f'{SHARED}/{name}: no Claude pointer {CLAUDE}/{name}/SKILL.md')
    if claude_dir.is_dir():
        for folder in sorted(p for p in claude_dir.iterdir() if p.is_dir()):
            pointer = folder / 'SKILL.md'
            where = f'{CLAUDE}/{folder.name}/SKILL.md'
            if not pointer.is_file():
                issues.append(f'{CLAUDE}/{folder.name}: no SKILL.md')
                continue
            text = pointer.read_text(encoding='utf-8')
            name, description = frontmatter(pointer)
            if folder.name not in shared:
                issues.append(f'{where}: no shared skill {SHARED}/{folder.name}; a pointer is named like its shared skill')
                continue
            if name != folder.name or description != shared[folder.name]:
                issues.append(f'{where}: name and description must equal the shared skill\'s')
            if f'.agents/skills/{folder.name}/SKILL.md' not in text:
                issues.append(f'{where}: does not link .agents/skills/{folder.name}/SKILL.md')
            if text.count('\n') > POINTER_MAX_LINES:
                issues.append(f'{where}: a pointer stays thin (at most {POINTER_MAX_LINES} lines); move the content to the shared skill')
    for base in (shared_dir, claude_dir):
        for file in sorted(base.rglob('*')) if base.is_dir() else ():
            if file.is_file() and file.suffix in ('.md', '.yaml', '.yml') and CYRILLIC.search(file.read_text(encoding='utf-8')):
                issues.append(f'{file.relative_to(root).as_posix()}: not English (AGENTS.md)')
    return issues


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--hook', action='store_true')
    args = parser.parse_args()
    try:
        if args.hook:
            json.load(sys.stdin)
        issues = check(args.root.resolve())
    except (OSError, ValueError) as error:
        print(f'Skills check could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('Skills and their Claude pointers are out of step (.agents/skills/README.md):\n' + '\n'.join(issues), file=sys.stderr)
        return 2
    print('{}' if args.hook else 'Skills check passed: shared skills, index and Claude pointers agree.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
