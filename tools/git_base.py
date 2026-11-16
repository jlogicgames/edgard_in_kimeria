#!/usr/bin/env python3
"""The diff base shared by the changed-line gates (check_allocations.py, check_named_parameters.py, check_doc_gaps.py).

'auto' is the default of the Gradle tasks and of a plain run: the merge-base of HEAD with origin/main when that ref
exists, so a clean pull-request checkout (CI) still judges every line the branch changed; the working tree's
uncommitted edits are always included, because the gates diff the base against the working tree. Without origin/main
it falls back to HEAD (uncommitted edits only) and says so on stderr. The hooks keep HEAD: an edit is judged on what it
changed now, not on the whole branch.
"""
import subprocess
import sys

AUTO, UPSTREAM = 'auto', 'origin/main'


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)


def resolve(root, base, gate):
    """A commit to diff the working tree against, or 'HEAD'. 'auto' picks the merge-base with origin/main if it exists."""
    if base == AUTO:
        try:
            git(root, 'rev-parse', '--verify', '--quiet', UPSTREAM + '^{commit}')
        except subprocess.CalledProcessError:
            print(f'{gate}: no {UPSTREAM} ref, so only uncommitted changes are checked '
                  '(fetch it, for example `git fetch origin main`, to check the whole branch).', file=sys.stderr)
            return 'HEAD'
        base = UPSTREAM
    return base if base == 'HEAD' else git(root, 'merge-base', 'HEAD', base).strip()


def default_base(args_base, hook):
    """The base when --base is not given: HEAD for a hook, 'auto' otherwise."""
    return args_base if args_base is not None else ('HEAD' if hook else AUTO)
