#!/usr/bin/env python3
"""Reject per-frame allocation on changed lines of hot methods (docs/DEVELOPMENT.md, "Particle system plan", section 13).

Hot methods are those named update*, render*, draw* and the ones listed in HOT_METHODS (helpers the scanner cannot
discover by name). The check runs on CHANGED lines against --base (default HEAD; origin/main includes committed PR
changes), so existing code needs no baseline; --all prints every hit in the tree, which is how the audit table in the
plan is regenerated. It is a net for new code: the runtime AllocationBudgetTest is what proves a path clean.

It does not follow calls, has no types (varargs and boxing are invisible), and flags a lambda in a hot method without
knowing whether it captures. Every ALLOWED entry states what the excluded code carries.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = 'core/src/main/java/com/jlogicsoftware/kimeria/'
SOURCE_PATHS = ('core/src/main/java',)

HOT_NAME = re.compile(r'^(?:update|render|draw)')
# Helpers called from a hot method that the name rule cannot find: (file, method) -> why it is per-frame.
HOT_METHODS = {
    ('effects/Torch.java', 'spawnCore'): 'called from update every 0.04 to 0.16 s',
    ('effects/Torch.java', 'spawnEmbers'): 'called from update every 0.15 to 0.35 s',
    ('effects/Torch.java', 'spawnSparkles'): 'called from update every 0.08 to 0.26 s',
    ('effects/Torch.java', 'spawnSmoke'): 'called from update every 0.3 to 0.8 s',
    ('effects/EnvironmentFx.java', 'emitGrit'): 'called from update while a grit burst is emitting',
    ('effects/EnvironmentFx.java', 'dustPuff'): 'called from Player.playerJump, which runs inside the player update',
    ('effects/EnvironmentFx.java', 'ceilingGrit'): 'called from Level.activateTrigger inside the player update',
}
# (file, method, kind) -> what the excluded code carries and why fixing it would lose that.
# Existing hot-path allocations are NOT listed: the check only looks at changed lines, so touching one means fixing it.
ALLOWED = {
    ('world/Level.java', 'renderDebug', 'iterator'):
        'F1 collider/hitbox gizmos, drawn only while the debug flag is on: a measurement overlay, not a gameplay frame',
}

RULES = {
    'new': re.compile(r'\bnew\s+(?P<type>[\w.]+)(?:\s*<[^;()]*?>)?\s*[(\[]'),
    'string': re.compile(r'\bString\s*\.\s*(?:format|valueOf)\s*\(|"[ ]*"\s*\+|\+(?!=)\s*"[ ]*"|\+=\s*"[ ]*"'),
    'factory': re.compile(r'\.\s*stream\s*\(\s*\)|\b(?:List|Set|Map)\s*\.\s*of\s*\(|\bArrays\s*\.\s*asList\s*\(|\bCollections\s*\.\s*\w+\s*\('),
    'iterator': None,  # needs the file's declarations, see iterator_hits
    'lambda': None,    # needs the surrounding statement, see lambda_hits
}
ADVICE = {
    'new': 'no `new` per frame: reuse a field or a fixed array, pool with DefaultPool/PoolManager, or cache it',
    'string': 'String.format/valueOf/concatenation allocates: cache the text or change it only when its inputs change',
    'factory': 'this factory/stream call allocates: use a reused libGDX Array/FloatArray and an index loop',
    'iterator': 'for-each over a List/Set/Map creates an Iterator: use an index loop or a libGDX Array',
    'lambda': 'lambda in a hot method (it allocates when it captures): use a field or a named method reference cached once',
}
EXCEPTION_TYPE = re.compile(r'(?:Exception|Error|Throwable)$')
COLLECTION_TYPES = ('List|ArrayList|LinkedList|Set|HashSet|LinkedHashSet|TreeSet|SortedSet|Collection|Map|HashMap|'
                    'LinkedHashMap|TreeMap|Iterable|Queue|Deque|ArrayDeque')
DECLARATION = re.compile(r'\b(?:' + COLLECTION_TYPES + r')\s*<[^;=(){}]*>\s+(\w+)\s*[;=,)]')
FOR_EACH = re.compile(r'\bfor\s*\([^;()]*?:\s*(?:this\s*\.\s*)?(\w+)\s*(?P<call>\.\s*(?:values|keySet|entrySet)\s*\(\s*\))?\s*\)')
NON_CODE = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
METHOD = re.compile(
    r'(?:^|[;{}])\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|private|protected|static|final|synchronized|abstract|default)\s+)*'
    r'(?:<[^>]+>\s+)?(?:[\w<>?\[\],.]+\s+)?(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w.,\s]+)?\{')
NOT_METHODS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new', 'else', 'try', 'do'}


def code_only(text):
    """Blank comments and literals but keep the quotes of a string, so concatenation stays visible."""
    def blank(match):
        literal = match[0]
        if literal.startswith('"'):
            return '"' + ''.join('\n' if c == '\n' else ' ' for c in literal[1:-1]) + '"'
        return ''.join('\n' if c == '\n' else ' ' for c in literal)
    return NON_CODE.sub(blank, text)


def methods(code):
    found = []
    for match in METHOD.finditer(code):
        name = match[1]
        if name in NOT_METHODS:
            continue
        start = match.end() - 1
        depth = 1
        end = start + 1
        while end < len(code) and depth:
            depth += (code[end] == '{') - (code[end] == '}')
            end += 1
        found.append((start, end, name))
    return found


def owner_of(spans, position):
    inside = [(b - a, name) for a, b, name in spans if a <= position < b]
    return min(inside)[1] if inside else None


def is_hot(path, name):
    return name is not None and (HOT_NAME.match(name) or (path, name) in HOT_METHODS)


def lambda_hits(code):
    for match in re.finditer(r'->', code):
        segment = code[max(code.rfind(';', 0, match.start()), code.rfind('{', 0, match.start()), code.rfind('}', 0, match.start())) + 1:match.start()]
        # `case X ->` and `default ->` are switch arms, not lambdas.
        if re.match(r'\s*(?:case\b|default\b)', segment) and '->' not in segment:
            continue
        yield match.start()


def iterator_hits(code):
    names = set(DECLARATION.findall(code))
    for match in FOR_EACH.finditer(code):
        if match[1] in names:
            yield match.start()


def hits(path, source):
    """Every (line, method, kind, text) in a hot method, allowed or not."""
    code = code_only(source)
    spans = methods(code)
    found = []

    def add(position, kind):
        owner = owner_of(spans, position)
        if is_hot(path, owner):
            line = code.count('\n', 0, position) + 1
            found.append((line, owner, kind, source.splitlines()[line - 1].strip()))

    for kind, pattern in RULES.items():
        if pattern is not None:
            for match in pattern.finditer(code):
                if kind == 'new' and EXCEPTION_TYPE.search(match['type']):
                    continue
                add(match.start(), kind)
    for position in iterator_hits(code):
        add(position, 'iterator')
    for position in lambda_hits(code):
        add(position, 'lambda')
    return sorted(set(found))


def violations(path, source, added=None):
    """Hits on changed lines (all lines when `added` is None) that no ALLOWED entry covers."""
    issues = []
    for line, owner, kind, _ in hits(path, source):
        if added is not None and line not in added:
            continue
        if (path, owner, kind) in ALLOWED:
            continue
        issues.append(f'{path}:{line} ({owner}): {ADVICE[kind]}')
    return issues


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)


def changed_lines(root, base):
    if base == 'HEAD':
        try:
            git(root, 'rev-parse', '--verify', 'HEAD')
        except subprocess.CalledProcessError:
            # A new repository has no legacy code: both staged and untracked files are new.
            return {path: None for path in git(root, 'ls-files', '--cached', '--others',
                '--exclude-standard', '--', *SOURCE_PATHS).splitlines()}
    else:
        base = git(root, 'merge-base', 'HEAD', base).strip()
    patch = git(root, 'diff', '--no-ext-diff', '--no-color', '--unified=0', base, '--', *SOURCE_PATHS)
    result = {}
    path = None
    line = 0
    for text in patch.splitlines():
        if text.startswith('+++ b/'):
            path = text[6:]
        elif text.startswith('@@'):
            line = int(re.search(r'\+(\d+)', text)[1])
        elif text.startswith('+') and path:
            result.setdefault(path, set()).add(line)
            line += 1
        elif text.startswith(' '):
            line += 1
    for path in git(root, 'ls-files', '--others', '--exclude-standard', '--', *SOURCE_PATHS).splitlines():
        result[path] = None
    return result


def relative(path):
    return path[len(BASE):] if path.startswith(BASE) else path


def check(root, base='HEAD'):
    issues = []
    for path, added in sorted(changed_lines(root, base).items()):
        file = root / path
        if path.endswith('.java') and file.is_file():
            issues.extend(violations(relative(path), file.read_text(encoding='utf-8'), added))
    return issues


def all_hits(root):
    directory = root / BASE
    if not directory.is_dir():
        raise ValueError(f'Java source directory is missing: {directory}')
    lines = []
    for file in sorted(directory.rglob('*.java')):
        path = file.relative_to(directory).as_posix()
        for line, owner, kind, text in hits(path, file.read_text(encoding='utf-8')):
            allowed = ALLOWED.get((path, owner, kind))
            note = f'  [allowed: {allowed}]' if allowed else ''
            lines.append(f'{path}:{line} ({owner}) {kind}: {text}{note}')
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--base', default='HEAD', help='Use origin/main to include committed PR changes')
    parser.add_argument('--all', action='store_true', help='Print every hit in the tree instead of checking changed lines')
    parser.add_argument('--hook', action='store_true')
    args = parser.parse_args()
    try:
        if args.hook:
            json.load(sys.stdin)
        if args.all:
            lines = all_hits(args.root.resolve())
            print('\n'.join(lines) + f'\n{len(lines)} hit(s) in hot methods.')
            return 0
        issues = check(args.root.resolve(), args.base)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Allocation check could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('Allocation in a per-frame method (update*, render*, draw*, HOT_METHODS); see AGENTS.md:\n' + '\n'.join(issues), file=sys.stderr)
        return 2
    print('{}' if args.hook else 'Allocation check passed for changed production code.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
