#!/usr/bin/env python3
"""Catch unnamed tuning/geometry literals on changed production lines (or a whole PR)."""
import argparse
import ast
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
NON_CODE = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
NUMBER = re.compile(r'(?<![\w.])(?:0[xX][\da-fA-F_]+|(?:\d[\d_]*(?:\.[\d_]*)?|\.[\d_]+)(?:[eE][+-]?\d+)?)[fFdDlL]?(?![\w.])')
CONSTANT = re.compile(r'\b(?:static\s+final|final\s+static|final)\s+[\w.<>,?\[\]]+\s+[A-Z][A-Z_\d]*\s*=[^;]*;')
SOURCE_PATHS = ('core/src/main/java', 'lwjgl3/src/main/java', 'lwjgl3/src/dev/java', 'web/src/main/java', 'tools')


def java_violations(path, source, added=None):
    code = NON_CODE.sub(lambda m: ''.join('\n' if c == '\n' else ' ' for c in m[0]), source)
    constants = [(m.start(), m.end()) for m in CONSTANT.finditer(code)]
    issues = []
    for match in NUMBER.finditer(code):
        line = code.count('\n', 0, match.start()) + 1
        if added is not None and line not in added:
            continue
        if any(a <= match.start() < b for a, b in constants):
            continue
        value = match[0].replace('_', '')
        number = int(value.rstrip('lL'), 16) if value.lower().startswith('0x') else float(value.rstrip('fFdDlL'))
        # Zero/one encode neutral state and indices. Two is structural only as a factor/divisor.
        if number in (0, 1):
            continue
        if number == 2 and (re.search(r'[*/]\s*$', code[:match.start()])
                            or re.match(r'\s*\*', code[match.end():])):
            continue
        issues.append(f'{path}:{line}: name parameter {match[0]} in its owning class or asset definition')
    return issues


def python_violations(path, source, added=None):
    tree = ast.parse(source, filename=path)
    # This rule owns artwork/export geometry, not parser byte formats or checker internals.
    if not any(isinstance(n, ast.ImportFrom) and (n.module or '').split('.')[0] == 'PIL'
               or isinstance(n, ast.Import) and any(a.name.split('.')[0] == 'PIL' for a in n.names)
               for n in ast.walk(tree)):
        return []
    parents = {child: node for node in ast.walk(tree) for child in ast.iter_child_nodes(node)}
    issues = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Constant) or type(node.value) not in (int, float):
            continue
        if added is not None and node.lineno not in added:
            continue
        owner = parents.get(node)
        cursor = owner
        named = False
        while cursor is not None:
            if isinstance(cursor, (ast.Assign, ast.AnnAssign)):
                targets = cursor.targets if isinstance(cursor, ast.Assign) else [cursor.target]
                named = all(isinstance(t, ast.Name) and t.id.isupper() for t in targets)
                break
            cursor = parents.get(cursor)
        if named or node.value in (0, 1):
            continue
        if node.value == 2 and isinstance(owner, ast.BinOp) and isinstance(owner.op, (ast.Mult, ast.Div, ast.FloorDiv)):
            continue
        issues.append(f'{path}:{node.lineno}: name artwork/export parameter {node.value}')
    return issues


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, stderr=subprocess.PIPE)


def changed_lines(root, base):
    if base == 'HEAD':
        try:
            git(root, 'rev-parse', '--verify', 'HEAD')
        except subprocess.CalledProcessError:
            # A new repository has no legacy code: both staged and untracked files are new.
            return {path: None for path in git(root, 'ls-files', '--cached', '--others',
                '--exclude-standard', '--', *SOURCE_PATHS).splitlines()}
    if base != 'HEAD':
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
        if path.endswith(('.java', '.py')):
            result[path] = None
    return result


def check(root, base='HEAD'):
    issues = []
    for path, added in sorted(changed_lines(root, base).items()):
        file = root / path
        if not file.is_file():
            continue
        if path.endswith('.java'):
            issues.extend(java_violations(path, file.read_text(), added))
        elif path.endswith('.py'):
            issues.extend(python_violations(path, file.read_text(), added))
    return issues


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--base', default='HEAD', help='Use origin/main to include committed PR changes')
    parser.add_argument('--hook', action='store_true')
    args = parser.parse_args()
    try:
        if args.hook:
            json.load(sys.stdin)
        issues = check(args.root.resolve(), args.base)
    except (OSError, ValueError, SyntaxError, subprocess.CalledProcessError) as error:
        print(f'Named-parameter check could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('Unnamed tuning or geometry parameters:\n' + '\n'.join(issues), file=sys.stderr)
        return 2
    print('{}' if args.hook else 'Named-parameter check passed for changed production code.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
