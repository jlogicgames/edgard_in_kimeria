# Edgard in Kimeria: rules for every coding agent

Single source of truth for Claude Code, Codex, Qwen and any other agent working in this repo
(`CLAUDE.md` only imports this file). Short; details live in `docs/`. It lists the mistakes that
have already cost a review round.

## Language: everything is written in English

All project documentation is English only: `README.md`, `AGENTS.md`, everything under `docs/`,
code comments, commit messages, and GitHub issues and pull requests (titles, descriptions,
comments). Do not write or leave Russian (or any other language) there, whatever language the
conversation with the user is in. Player-facing game strings are the exception: they live in
the localization bundles and follow the supported languages. When you find a non-English issue
or document, translate it.

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

## No allocation in `update` and `render`

Per-frame code (`update*`, `render*`, `draw*` and everything they call) must not allocate: no `new`, no varargs call, no
`for-each` over a `List` or `Set` (it creates an iterator), no boxing, no `String.format` or string concatenation, no
capturing lambda. Reuse fields and fixed-size arrays; use libGDX `Array`, `FloatArray`, `IntArray` with index loops; pool
through libGDX `DefaultPool` or `PoolManager` (`Pools` and `ReflectionPool` are deprecated in 1.14.2). Cache regions, textures and
text layouts. Constructors, level load and error paths may allocate. A claim of "allocation free" needs a `ThreadMXBean`
number from the budget test (`AllocationBudgetTest`), not a reading of the code. An exception goes in the allowlist of
`tools/check_allocations.py` with a reason that says what it carries.

## Tests are not to be weakened silently

If a threshold or tolerance must change, say so in the report and in `docs/DECISIONS.md`,
with the measured value and why the new number is right (D48: 200 to 160 px, D49/D50: label
tolerance). Never raise a tolerance just to make a test pass.

## Gaps written into docs must be tracked

Any "not yet", "known gap", "TODO", "to be measured" or "needs upstream fix" line added to a
design, architecture or process document (`docs/*.md`) needs an open GitHub issue **in the same
change**, and the doc line links it (`#NNN`). The issue's checklist must include "update
<that document>" so the note is removed or revised when the work lands. A gap that exists only
as prose gets lost (audit of `docs/DEVELOPMENT.md`, 2026-11-06, found five untracked ones).
When closing such an issue, remove the doc note in the same PR. `tools/check_doc_gaps.py` (`validateDocGaps`, and the
hooks) checks changed doc lines for these markers and "pending"; it cannot tell whether the issue is open.

## Issues and feedback

Write every GitHub issue with the `kimeria-issue` skill: duplicate search, English body, a checklist that names the
owning skill, a separate "check in the running game" box and the docs to update; a box is ticked only on evidence.
A round of playtest feedback goes through `kimeria-feedback-triage`: analysis against the build, the author's decisions
before filing, a `docs/PLAYTEST_N.md` record, and the open boxes that waited for real players.

## Style system (D75)

The game is kept in one style through `docs/STYLE_SYSTEM.md`: six principles, which document and gate own each
system (text, traps, rooms, sizes, effects, sound, art), and the order of work. Design on the card first (trap, room or
text), get the author's approval, then build; a change that spans systems follows the order there. A new design rule
lands complete (D-number, reference doc, gate, skill with its Claude pointer, a paragraph here, a row there). Skills:
`.agents/skills/README.md`; each `kimeria-*` skill has a Claude pointer of the same name in `.claude/skills/`, and
`tools/check_skills.py` keeps them in step. The sections below are the rules that already cost a review round.

## Rooms: every room gives something new (D72)

Before building or changing a room, write or update its card in `docs/ROOM_CARDS.md` (role, purpose, what it
introduces, develops or combines, D19 times, retry cost) and keep `RoomCardsTest` green. A new look on a known trap
(a reskin) is not novelty; outside the tutorial a room introduces at most two elements. A new Tiled object type gets
a registry row in the same change. Rule and workflow: `docs/ROOMS.md`, "Every room gives something new (D72)".
A trap's teaching room in `docs/TRAPS.md` must match the room cards (`TrapTeachingRoomTest`).

## Rooms are dense (D105)

A room keeps the player busy: about one beat every 3 s of the zero-charge mastered route, at least 3 beats of 2
distinct elements, 1.5 to 4.0 s per beat (`lesson` in the tutorial or a fixed list, and `boss` with the boss, are
exempt). A beat is one placed hazard, monster, pulled lever (with what it drives), running platform or skill obstacle
(posed by its geometry) on the required route; pickups never count, and two of one element within 96 px are one beat.
The card lists `Beats` (`id@x`, rising x) and `Beat time` (`seconds (TestClass)`, pinned within 0.1 s); a new element's
first meeting is a safe pocket among known beats (among the first two beats or a retry of at most 6 s, no other lethal
object within 96 px), recorded under `First meeting` with its `pocket at x N` and pinned by a method of `FirstMeetingPocketTest` (hero
there, never hit, the element seen acting), never a room of its own. A skill (a branch climb) never acts: its pocket shows the whole first obstacle in view while no lethal object in the room stirs (`skillPocket`, D109). A lesson outside the tutorial needs its own decision. `RoomCardsTest` checks it, and no shipped room is exempt: the rooms that were below it before D105 were all reworked (#500).

## Git in this repo

- Never commit or push without the user's approval in that moment; never commit on `main`.
- Stage explicit paths, then check `git diff --cached --name-only` before `git commit`.
  Deleted files fail a path-list `git add` and a following `git commit` still runs: stop on
  the first failure (`set -e` or `&&`) or the commit comes out incomplete (PR 242).
- The author's editor may auto-reformat Java files. Do not commit reformat noise; keep the
  project's compact style (several constants on one line, short methods) and commit only the
  real change.

## Traps

A trap is visible before it fires (D71). Write its card in `docs/TRAPS.md` before the code; a disguised or hidden
trap needs an always-drawn at-rest cue and a teaching room. `TrapCatalogTest` fails a spawned type with no catalogue
row. When it acts it is seen and heard, hit or miss; the hero's death beat does not count (D93). Procedure: the
`kimeria-trap-design` skill (`.agents/skills/`).

## Monsters (D100)

A monster that reacts to the hero notices in its own body language (a stop and a reaction drawn in its frames, about 0.5 s,
not scaled by the assist) and a short call in its own voice: no mark over its head, no shared alert sound. Its lethal
wind-up takes its length from `TrapWarnings`. New monsters are met in play: no teaching room, no hint text; their first room is a dense room with a pocket around the monster's whole reach (D105). Design the
monster's card in `docs/GAME_DESIGN.md` ("Forest monster ideas") first; the beetle, the spider (D102) and the snail (D106) are
the templates (`Beetle`, `BeetleFx`; `Spider`, `SpiderFx`; `Snail`, `SnailFx`). A ranged monster owns its projectile, one per
monster with no allocation, and every weapon breaks it and flies on (`Breakable`, the snail's gob). A monster that leaves its floor (the spider's walls and silk thread, D103) moves
on a route checked at load and strikes only along a line its wind-up has drawn; never from a wall.

## Monster bodies (D101)

A monster killed without bursting leaves its body (`Remains`): its pose must differ from every live frame (the readability
rule, not the palette), and it keeps its live palette, ink and moon rim (D117): no recolour, tint or halo, measured as active. It is harmless, blocks nothing and counts
as defeated; it rocks (and decor bushes sway) when any actor passes, not only the hero; only the sword and a stomp (with the normal
bounce) crumble it and a fireball or a thrown torch burns it (both fly on, never stopped or spent), while a stone and every other thrown object fly straight through it (D119). A burst or blast leaves nothing; a reset or a passed room leaves no
body; the swing that kills a monster does not crumble its body. Crumble and burn go through the shared `RemainsFx`. Add every
new `Remains` class to `RemainsTest`, which fails on an unlisted one.

## Player-facing text

New or changed strings follow `docs/TEXT_STYLE.md`: tale voice for narration and the healer (no
control names, no game jargon), plain imperative for hints, one label per concept from its glossary.
Write EN first and get the author's approval before translating; then all five catalogs in the same
change. `python3 tools/check_text_style.py` (also `./gradlew validateTextStyle`, part of `:core:check`)
and `./gradlew :core:test` must pass. Never add an allowlist entry for new text: fix the text.

## Text has a minimum size on the screen (D137)

The picture shrinks on a phone (0.61 CSS px per unit in portrait), so text is sized from the picture's scale, never from the
device: below 1 CSS px per unit the label and body take their 18 px fonts and the dialogue its 20 px one (`ui.FontSizes`,
`Assets.setLargeText`, `TouchLayout.cssPerUnit`), at least 11 CSS px on a 390 px portrait phone. A view that keeps a font or laid-out
text compares `Assets.textGeneration()` and rebuilds when it moves; a new text box must fit both tiers (`LocalizationLayoutTest`
runs both) and `TextMinimumSizeTest` must stay green. The touch buttons carry pictograms, not text (D138, `ui.TouchGlyph`); the portrait rotate hint is the one touch text left, measured in its own strip.

## Parallax layers (D108)

The forest maps draw scenery layers (a tile layer with `parallaxx`/`parallaxy` or the property `scenery=true`) behind the play layer and a
`foreground-<room>` object group in front of the world (`world.ParallaxRenderer`; maths and card: `docs/ART_DIRECTION.md`, "Forest look and
parallax layers"). A change to a layer or a foreground piece goes through `tools/parallax_game.py`, then `python3 tools/parallax_mock.py --check
--game` (`validateParallax`) must pass: the foreground never covers a protected object (rule B) and never hides the hero. Colours follow depth (D120): the foreground and the decor tier take a neutral dark
palette with real hues (leaf green, bark brown, moss; not black, not grey ghosts), and mid, grove and far turn bluer, lighter and greyer with
depth; the sky, the moon and the gameplay objects keep theirs. The giant `edge-giant-*` leaves (D143) are the one blurred art (baked at
export, nothing else is blurred), lie in the faster group `foreground-near-<room>` (2.2), stay clear of open pits and never hide more
than 35 % of a sharp piece (50 % at a room's first or last camera); the HUD is drawn over every layer, so it needs no clearance. Draw code stays
allocation free (`AllocationBudgetTest`). Status: accepted by the author, 2026-11-24 ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)).
Every forest room has a foreground, and a long one (736 px or wider) is not left empty in the middle (D153): no stretch over 320 px
without a hanging piece or over 480 px without a rising stem unless none fits, 96 px of open sky between hanging pieces in every
view (`MIDDLE` in `tools/parallax_game.py`); a room with a falling or thrown stone takes no canopy (D113). A new or changed room
passes `validateParallax` before it is done.
Procedure: the `kimeria-parallax` skill (`.agents/skills/`).

## Branches grow from trunks (D122)

Every object drawn as a branch or hanging from a bough (sound and rotten branches, `FallingBranch`, the hive's and the deadfall's
boughs) lays its root at least 3 px over the bark of a `Decor` trunk or snag on its root's side; `check_tiled_links.py`
(`check_branch_roots`) fails a map where one floats. A branch that stays shows a bark collar at the joint, never a moon-rimmed cut end;
a trunk stays decor (bark palette, not solid, drawn behind the play tiles). Placing or moving a branch means placing its tree: see
`docs/ART_DIRECTION.md`, "A branch always grows from a trunk (D122)".

## Hints

`Hud` draws two separate channels: the map-authored zone message (anchor at 45% down the zone,
`HINT_ZONE_HEIGHT_FRACTION`) and the label above the hero. Their sizes and the 24 px gap are
pinned by tests (D33, D49, D50). Check `docs/DECISIONS.md` before touching either. Zone message text follows
one formula (D97, `docs/TEXT_STYLE.md` §1.1): an optional cue sentence, then one imperative, at most 60 EN
characters; levers read "Use the lever to ...", exits "Head for the exit ahead".
