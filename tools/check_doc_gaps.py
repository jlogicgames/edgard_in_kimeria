#!/usr/bin/env python3
"""Doc-gap gate (AGENTS.md "Gaps written into docs must be tracked", D75).

A gap written into a design, architecture or process document (`docs/**/*.md`) names an open GitHub issue. This gate
looks only at blocks (paragraphs, list items, table rows) that contain an added or changed line against a base, so the
history already in the docs is not re-judged. A changed block that holds a gap marker must also hold an issue
reference (`#NNN` or `.../issues/NNN`). Whether that issue is open is a review check: the gate has no network.

Usage:
  python3 tools/check_doc_gaps.py                     # every change of the branch: committed since the merge-base with
                                                      # origin/main, plus the working tree (HEAD if origin/main is absent)
  python3 tools/check_doc_gaps.py --base HEAD         # only uncommitted changes
  python3 tools/check_doc_gaps.py --base origin/main  # the same as the default, but fails if origin/main is missing
  python3 tools/check_doc_gaps.py --hook              # read a hook payload on stdin, print {} on success
"""
import argparse
import json
import re
import subprocess
import sys

import git_base
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC_PATHS = ('docs',)

# The markers of AGENTS.md, plus "pending" (D71's `pending #NNN` form). "TODO" is matched in capitals only; the others
# ignore case. Each is a whole phrase: "pending" does not match "depending". "not yet" followed by a verb of the
# player's progress ("not yet met", "not yet seen") describes what the player has met in play order (D72), not the
# state of the project, so it is a different statement and does not count as a gap.
PLAYER_PROGRESS = r'(?:met|seen|known|reached|learned|learnt|taught|unlocked|found|collected|visited|introduced)'
MARKERS = (
    ('TODO', re.compile(r'\bTODO\b(?!\]\()')),  # not the link text of a document title ("[Pre-release TODO](...)")
    ('not yet', re.compile(r'\bnot yet\b(?! ' + PLAYER_PROGRESS + r'\b)', re.IGNORECASE)),
    ('known gap', re.compile(r'\bknown gaps?\b', re.IGNORECASE)),
    ('to be measured', re.compile(r'\bto be measured\b', re.IGNORECASE)),
    ('needs upstream fix', re.compile(r'\bneeds? (an )?upstream fix\b', re.IGNORECASE)),
    ('pending', re.compile(r'\bpending\b', re.IGNORECASE)),
)
# A real issue reference only: `#362` or `.../issues/362`. Not an anchor (`#2-foo`, `ROOMS.md#4-draft`), not an HTML
# entity (`&#35;`), not the `#NNN` placeholder.
ISSUE = re.compile(r'(?<![\w&/.])#\d+(?![\w-])|/issues/\d+(?![\w-])')
# Inline code quotes syntax (`pending #NNN`, `TODO`): it is not a statement about the project, so neither a marker nor a
# reference inside backticks counts.
CODE_SPAN = re.compile(r'`[^`\n]*`')
DEFAULT_BASE = git_base.AUTO
HEADING = re.compile(r'^\s*#{1,6} ')
BLOCK_START = re.compile(r'^\s*(?:[-*+] |\d+\. |\||#{1,6} |>)')


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)


def changed_lines(root, base):
    """{path: set of 1-based added line numbers, or None for an untracked file (every line is new)}."""
    base = git_base.resolve(root, base, 'Doc-gap check')
    patch = git(root, 'diff', '--no-ext-diff', '--no-color', '--unified=0', base, '--', *DOC_PATHS)
    result, path, line = {}, None, 0
    for text in patch.splitlines():
        if text.startswith('+++ '):
            path = text[6:] if text.startswith('+++ b/') else None
        elif text.startswith('@@'):
            line = int(re.search(r'\+(\d+)', text)[1])
        elif text.startswith('+') and path:
            result.setdefault(path, set()).add(line)
            line += 1
        elif text.startswith(' '):
            line += 1
    for path in git(root, 'ls-files', '--others', '--exclude-standard', '--', *DOC_PATHS).splitlines():
        result[path] = None
    return {p: lines for p, lines in result.items() if p.endswith('.md')}


def blocks(text):
    """(first line, last line, text) of each paragraph, list item and table row, outside fenced code."""
    out, start, buf, fenced = [], None, [], False

    def flush(end):
        nonlocal start, buf
        if buf:
            out.append((start, end, '\n'.join(buf)))
        start, buf = None, []

    for number, line in enumerate(text.split('\n'), 1):
        if line.lstrip().startswith('```'):
            flush(number - 1)
            fenced = not fenced
            continue
        if fenced:
            continue
        if not line.strip():
            flush(number - 1)
            continue
        if BLOCK_START.match(line) or line.lstrip().startswith('|'):
            flush(number - 1)
        if start is None:
            start = number
        buf.append(line)
        if line.lstrip().startswith('|') or re.match(r'^\s*#{1,6} ', line):
            flush(number)
    flush(text.count('\n') + 1)
    return out


def violations(path, text, added):
    issues = []
    for first, last, block in blocks(text):
        if added is not None and not any(first <= n <= last for n in added):
            continue
        if HEADING.match(block):
            continue  # a title ("## Known gaps") names a section; the gaps are its items, which are checked
        prose = CODE_SPAN.sub('', block)
        found = [name for name, pattern in MARKERS if pattern.search(prose)]
        if found and not ISSUE.search(prose):
            snippet = ' '.join(block.split())[:110]
            issues.append(f'{path}:{first}: gap marker {", ".join(repr(f) for f in found)} without an issue (#NNN): {snippet}')
    return issues


def check(root, base='HEAD', only=None):
    issues = []
    for path, added in sorted(changed_lines(root, base).items()):
        if only is not None and path not in only:
            continue
        file = root / path
        if file.is_file():
            issues.extend(violations(path, file.read_text(encoding='utf-8'), added))
    return issues


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--base', default=DEFAULT_BASE,
                        help="'auto' (default): merge-base with origin/main if it exists, else HEAD; or any ref")
    parser.add_argument('--hook', action='store_true')
    parser.add_argument('--only', nargs='*', help='check only these repository-relative paths (the style hook passes the touched docs)')
    args = parser.parse_args()
    try:
        if args.hook:
            json.load(sys.stdin)
        issues = check(args.root.resolve(), args.base, set(args.only) if args.only is not None else None)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Doc-gap check could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('A gap written into docs/ must link an open issue (AGENTS.md, D75); add the #NNN, or open one first:\n'
              + '\n'.join(issues), file=sys.stderr)
        return 2
    print('{}' if args.hook else 'Doc-gap check passed for changed documentation.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
