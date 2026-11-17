#!/usr/bin/env python3
"""Style-system hook dispatcher (D75, docs/STYLE_SYSTEM.md "Gates and hooks").

Run by the PostToolUse and Stop hooks of Codex (`.codex/hooks.json`) and Claude Code (`.claude/settings.json`). It runs
only the fast gates that apply to the files in question:

- **PostToolUse:** the files the tool call touched: changed files of the working tree (`git status`, untracked included)
  that the call names (its `file_path`, or a path or file name inside a shell command or patch). The working tree is
  shared, so a gate failing on someone else's file does not block an unrelated edit; a call that names no changed file
  runs nothing.
- **Stop:** every changed file of the working tree (the full sweep before the turn ends).

  text style   assets/localization/*, tools/text_style.json, docs/TEXT_STYLE.md, Msg.java, maps  -> check_text_style.py
  map links    assets/tiles/*.tmx, *.tsx                                                        -> check_tiled_links.py
  doc gaps     docs/**/*.md                                                                     -> check_doc_gaps.py
  skills       .agents/skills/**, .claude/skills/**                                             -> check_skills.py
  contrast     assets/images/**, assets/tiles/*.tmx, *.tsx (sprites and their placements, D76)  -> check_contrast.py

A failing gate fails the hook (exit 2, its message on stderr), as the sprite, parameter and allocation hooks do. The
trap and room gates are JUnit tests that need a Gradle run, which does not fit a hook: when the tool call touched (or,
at Stop, the working tree holds) a file they read, the hook prints the exact command instead of running it.
"""
import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PY = sys.executable or 'python3'
PATHS = object()  # replaced by the matching paths of the run, so a per-file gate judges only those files
MSG = 'core/src/main/java/com/jlogicsoftware/kimeria/localization/Msg.java'


def is_map(p): return p.startswith('assets/tiles/') and p.endswith(('.tmx', '.tsx'))


GATES = (
    ('text style', lambda p: p.startswith('assets/localization/') or p in ('tools/text_style.json', 'docs/TEXT_STYLE.md',
                                                                         'tools/check_text_style.py', MSG) or is_map(p),
     ['tools/check_text_style.py']),
    ('map links', lambda p: is_map(p) or p == 'tools/check_tiled_links.py', ['tools/check_tiled_links.py']),
    ('doc gaps', lambda p: p.startswith('docs/') and p.endswith('.md'), ['tools/check_doc_gaps.py', '--only', PATHS]),
    ('skills', lambda p: p.startswith(('.agents/skills/', '.claude/skills/')), ['tools/check_skills.py']),
    # About 2.5 s: it draws every placed sprite over each map's background colour and tile layer (D76, D92).
    ('contrast', lambda p: p.startswith('assets/images/') or is_map(p) or p == 'tools/check_contrast.py',
     ['tools/check_contrast.py']),
)
GRADLE_INPUTS = ('docs/TRAPS.md', 'docs/ROOM_CARDS.md', 'core/src/main/java/com/jlogicsoftware/kimeria/world/Level.java')
GRADLE_COMMAND = "./gradlew :core:test --tests '*TrapCatalogTest' --tests '*RoomCardsTest' --tests '*TrapTeachingRoomTest'"


def needs_gradle(path): return path in GRADLE_INPUTS or (path.startswith('assets/tiles/') and path.endswith('.tmx'))


def changed_paths(root):
    out = subprocess.check_output(['git', '-C', str(root), 'status', '--porcelain', '-uall', '--no-renames'],
                                  text=True, encoding='utf-8', stderr=subprocess.PIPE)
    return sorted({line[3:].strip().strip('"') for line in out.splitlines() if len(line) > 3})


def named_by_call(payload, candidates):
    """The candidate paths the tool call names: its file_path, or a path or file name in a shell command or patch."""
    tool_input = payload.get('tool_input') if isinstance(payload, dict) else None
    if not tool_input:
        return set()
    text = tool_input if isinstance(tool_input, str) else json.dumps(tool_input, ensure_ascii=False)
    return {path for path in candidates if path in text or Path(path).name in text}


def touched_by_call(payload, root):
    """Gradle inputs (trap and room gates) the tool call named, changed or not."""
    maps = tuple(p.relative_to(root).as_posix() for p in (root / 'assets/tiles').glob('*.tmx'))
    return named_by_call(payload, GRADLE_INPUTS + maps)


def run(root, payload):
    event = payload.get('hook_event_name', '') if isinstance(payload, dict) else ''
    paths = changed_paths(root)
    if event == 'PostToolUse':
        paths = sorted(named_by_call(payload, paths))
    failures, ran = [], []
    for name, applies, command in GATES:
        matching = [p for p in paths if applies(p)]
        if matching:
            args = []
            for a in command[1:]:
                args.extend(matching if a is PATHS else [a])
            result = subprocess.run([PY, str(root / command[0]), *args], cwd=root, text=True, capture_output=True)
            ran.append(name)
            if result.returncode != 0:
                failures.append(f'[{name}] {command[0]} failed:\n{(result.stderr or result.stdout).strip()}')
    gradle = (touched_by_call(payload, root) if event == 'PostToolUse' else {p for p in paths if needs_gradle(p)})
    reminder = None
    if gradle:
        reminder = (f'Changed {", ".join(sorted(gradle))}: the trap and room gates are JUnit tests, run them with '
                    f'{GRADLE_COMMAND} (docs/STYLE_SYSTEM.md).')
    return failures, ran, reminder, event


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--hook', action='store_true', help='read a hook payload on stdin and answer in JSON')
    args = parser.parse_args()
    root = args.root.resolve()
    try:
        raw = sys.stdin.read() if args.hook else ''
        payload = json.loads(raw) if raw.strip() else {}  # an empty payload is no payload, not an error
        failures, ran, reminder, event = run(root, payload)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Style hook could not run: {error}', file=sys.stderr)
        return 2
    if failures:
        print('Style system gate failed (docs/STYLE_SYSTEM.md):\n' + '\n\n'.join(failures)
              + (f'\n\n{reminder}' if reminder else ''), file=sys.stderr)
        return 2
    if not args.hook:
        print('Style gates passed: ' + (', '.join(ran) if ran else 'nothing to check') + '.' + (f'\n{reminder}' if reminder else ''))
        return 0
    answer = {}
    if reminder:
        answer['systemMessage'] = reminder
        if event == 'PostToolUse':
            answer['hookSpecificOutput'] = {'hookEventName': 'PostToolUse', 'additionalContext': reminder}
    print(json.dumps(answer))
    return 0


if __name__ == '__main__':
    sys.exit(main())
