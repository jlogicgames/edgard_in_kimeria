# Edgard in Kimeria: rules for every coding agent

Single source of truth for Claude Code, Codex, Qwen and any other agent working in this repo
(`CLAUDE.md` only imports this file). Short; details live in `docs/`. It lists the mistakes that
have already cost a review round.

## Coordinates: world Y grows DOWNWARD

The world camera is `camera.setToOrtho(true, ...)` (`KimeriaGame`), so (0,0) is the top-left,
**larger Y is lower on screen** and a smaller Y is higher. This matches `.tmx` coordinates
(see `docs/DEVELOPMENT.md`, "Architecture"). Consequences, none of them optional:

- `batch.draw(region, x, y, w, h)` covers `y` .. `y + h` **downward**. `y` is the *top* edge of
  what you draw, not the bottom. A 64 px frame drawn at a 16 px object's top hangs 48 px
  below the object.
- To lift something, **subtract** from Y. To lower it, add. "Raise the label 20 px" means
  `y - 20`.
- `Entity.position.y` is the top edge of the box, `position.y + size.y` its bottom edge (the
  floor line for something standing on the ground).
- Text is the exception: `BitmapFont` text is drawn through a separate **Y-up** projection
  (`Hud`, `Overlay`). Do not mix the two conventions in one calculation.
- Before changing any position, name the direction (up or down on screen) and check the
  sign. A fix that "puts X on the floor" must be verified by an actual frame or a test that
  measures the rect (`y + h` against the floor line), not by reasoning from memory: PR 257
  assumed Y-up and left the stump blast 48 px under the stump.

## Tests are not to be weakened silently

If a threshold or tolerance must change, say so in the report and in `docs/DECISIONS.md`,
with the measured value and why the new number is right (D48: 200 to 160 px, D49/D50: label
tolerance). Never raise a tolerance just to make a test pass.

## Git in this repo

- Never commit or push without the user's approval in that moment; never commit on `main`.
- Stage explicit paths, then check `git diff --cached --name-only` before `git commit`.
  Deleted files fail a path-list `git add` and a following `git commit` still runs: stop on
  the first failure (`set -e` or `&&`) or the commit comes out incomplete (PR 242).
- The author's editor may auto-reformat Java files. Do not commit reformat noise; keep the
  project's compact style (several constants on one line, short methods) and commit only the
  real change.

## Hints

`Hud` draws two separate channels: the map-authored zone message (anchor at 45% down the zone,
`HINT_ZONE_HEIGHT_FRACTION`) and the label above the hero. Their sizes and the 24 px gap are
pinned by tests (D33, D49, D50). Check `docs/DECISIONS.md` before touching either.
