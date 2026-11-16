# Trap design system

The design reference for the remake's traps: the visibility rule
([D71](DECISIONS.md#d71-traps-are-visible-before-they-fire-2026-11-16)), the card every trap has, the catalogue that
the gate reads, and the cards themselves. [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md) stays the evidence of the 1994
game and the history of the forest trap set (D42); this document says how a trap of the remake is designed and
checked. Procedure for agents: the [`kimeria-trap-design`](../.agents/skills/kimeria-trap-design/SKILL.md) skill.

## The rule (D71)

A trap is **visible before it fires**. Every trap has one of three visibility words, judged **at rest**: before the
hero has triggered anything, on the first frame of the room.

| Word | Meaning |
| --- | --- |
| **VISIBLE** | The dangerous object is drawn at rest and reads as active, not as scenery: a silhouette or colour no scenery object shares (the "active" contrast tier of [D76](DECISIONS.md#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16): outline at least 0.28, checked by `tools/check_contrast.py`), or it moves. |
| **DISGUISED** | Drawn at rest, but it looks like scenery or a safe prop; the danger shows only after the trigger. |
| **HIDDEN** | Nothing, or almost nothing, is drawn at rest. |

**VISIBLE is the default for every new trap.** A trap may be DISGUISED or HIDDEN only when all three hold:

1. **At-rest cue.** Something is always drawn at the trap's place before any trigger, the same on every instance of
   the type, so that seeing it once teaches every later one. A hint text is not a cue (hints exist only in the
   tutorial, D33).
2. **Telegraph.** The [D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements) warning after the trigger, its
   length from `TrapWarnings`, unchanged by this rule.
3. **Taught safely.** The first meeting with the type is in a room whose purpose is to teach it (a tutorial room
   with its hint, or a room built to show it), never first in a room that tests mastery.

A trap whose cue is not drawn yet is allowed only with an open issue for the cue, written `pending #NNN` in its
catalogue row; the gate refuses a DISGUISED or HIDDEN row with neither a cue nor that link.

D63's look-alike mushroom pair is consistent with the rule: both mushrooms are visibly active (they glow); the
puzzle is which of the two kills, not whether something is there.

Supporting input: the reference study of [#352](https://github.com/jlogicgames/edgard_in_kimeria/issues/352), section
[GAME_DESIGN.md, "Primal Planet level study"](GAME_DESIGN.md#primal-planet-level-study-playtest-f16-352) (a tell before damage, an unexplained death is a
defect, short retries; its sources give little data on trap readability).

## Read distance

The camera is centred on the hero and clamped to the room (`Player.roomCameraTarget`), so a VISIBLE trap enters
the picture about half a screen, **~320 px**, ahead of the hero. A DISGUISED or HIDDEN trap reads only from its
trigger, which is why it needs the cue. The 320 px figure is read from the camera code, not measured on screen
(in-game check: #368).

## Trap card template

Every trap type has a card below, in this form. Write the card **before** the code; the author approves it (and its
D-number) first.

```markdown
### <Name> (`<TiledType>`)

| Field | Value |
| --- | --- |
| Family | proximity / position / touch / rhythm / crumble |
| Visibility (D71) | VISIBLE / DISGUISED / HIDDEN |
| At rest | what is drawn before any trigger; for VISIBLE, what makes it not scenery |
| At-rest cue | DISGUISED or HIDDEN only: the always-drawn cue, or "pending #NNN" |
| Reads from | how far ahead the danger is legible (~320 px for VISIBLE, else the trigger) |
| Trigger | zone, touch or clock: the rectangle and the hero body box it uses |
| Telegraph (D7) | frames or effect after the trigger |
| Warning | `TrapWarnings.<constant>`: base s / assist s ("the motion is the warning" for a continuous rhythm only) |
| Lethal part | the rectangle and when it is lethal |
| Counter | every answer that passes without ammunition (D26), then the optional paid answers |
| Reset | what a retry, Restart Level and a room re-entry restore |
| Taught in | the room where the type is first met, in backticks first, then how it teaches (`TrapTeachingRoomTest` checks it against the room cards) |
| Load checks | the validator in `Level` and its mirror in `tools/check_tiled_links.py` |
| Tests | test classes |
| Art and sound | sheet and frames, contrast tier (D76: active, with the measured outline; an at-rest cue is drawn in the active tier), sound |
| Decision | D-number(s) |
```

## Catalogue

The gate `TrapCatalogTest` (`:core:test`) reads the two tables below and the spawn switch of `world/Level.java`.
It fails when a type spawned by `Level` is in neither table, when a row names a type `Level` does not spawn, when a
trap row has an empty column, an unknown visibility or family word or no `###` card heading, and when a DISGUISED or
HIDDEN row has neither a cue nor `pending #NNN`, or no teaching room. Keep one row per Tiled object type and the column order as it is.
`TrapTeachingRoomTest` (D75) reads the `Taught in` column against [ROOM_CARDS.md](ROOM_CARDS.md): it starts with the
teaching room in backticks, a shipped room with a card that shows the trap (or a reskin of it); no earlier room in play
order shows the type; and for a DISGUISED or HIDDEN trap that room's card introduces the trap's element.

### Traps

<!-- trap-catalogue:start -->
| Type | Family | Visibility | At-rest cue | Warning | Taught in | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| `ThornRoot` | proximity | HIDDEN | pending #359 | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-ladder`, hint `HINT_LADDER_ROOT` | D44, D48 |
| `ThornTuft` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-traps`, first trap of the stretch | D44, D58 |
| `FallingBranch` | position | DISGUISED | pending #361 | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-wall`, hint `HINT_WALL_BRANCH` | D45, D48 |
| `FallingStone` | position | HIDDEN | pending #360 | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-platform`, hint `HINT_PLATFORM_STONE` | D45, D48, D59 |
| `ExplosiveStump` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-stone`, hint `HINT_STONE_STUMP` | D47, D48 |
| `ExplosiveRoot` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-stone` (the stump's idle tell and wind-up language) | D47, D59 |
| `Bomb` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-entry` (`forest-2`) | D7, D51, D60 |
| `Puffball` | rhythm | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-traps`, seen swelling from afar | D46, D58 |
| `ThornVine` | rhythm | VISIBLE | none needed | the motion is the warning | `forest-canopy` (`forest-2`), seen swinging from afar | D46, D59 |
| `HoneyFungus` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-lever`, harmless demonstration | D63 |
| `FalseHoneyFungus` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-canopy` (`forest-2`), after the harmless `HoneyFungus` of `forest-0-lever` | D63 |
| `FallingPlatform` | crumble | DISGUISED | pending #362 | `FALLING_PLATFORM_SECONDS` 1.0 / 1.5 s | `forest-0-traps` | D7, D40, D46, D69, D80 |
<!-- trap-catalogue:end -->

`FallingPlatform` with `skin=log` (the rotten log, D46) or `skin=branch` (the rotten tree branch, D80) is the same type
and row: their rotten look is the cue for those skins; the plain skin is the one pending (#362).

### Spawn types that are not traps

<!-- non-trap-types:start -->
| Type | Category | Why it is not a trap |
| --- | --- | --- |
| `Player` | hero | The hero's spawn point. |
| `Collectable` | pickup | Optional coins and ingredients; never harmful. |
| `FireballRefill` | pickup | Refills fireball charges; never harmful. |
| `Checkpoint` | route | The level's goal marker. |
| `Torch` | scenery | Light source; never harmful. |
| `Stone` | tool | The hero's carryable stone. |
| `CarryableTorch` | tool | The hero's carryable torch. |
| `Trigger` | mechanism | An invisible switch area for mechanisms; it never hurts. |
| `Actionable` | mechanism | Levers, walls and other linked mechanisms; solid, never lethal. |
| `Ladder` | mechanism | Climbable support. |
| `Escalator` | mechanism | Moving platform; solid, never lethal. |
| `Decor` | scenery | Scenery, including the static look-alike mushrooms (#299); no hitbox. |
| `HangingVine` | scenery | The harmless vine that sways when a stone lands; no hitbox (not `ThornVine`). |
| `Bat` | enemy | Enemy: telegraphs are enemy wind-ups (#240), not trap cards. |
| `RedMob` | enemy | Enemy, as above. |
| `YellowMob` | enemy | Enemy, as above. |
| `Boss` | enemy | The boss ([BOSS.md](BOSS.md)). |
<!-- non-trap-types:end -->

Two lethal things are outside the spawn switch and so outside the gate: the level's `Deadline` (the pit line; the
pit itself is the visible part) and `QuickSand` (a collision type that slows; it does not kill by itself).

## Cards

### Thorn root (`ThornRoot`)

| Field | Value |
| --- | --- |
| Family | proximity |
| Visibility (D71) | HIDDEN |
| At rest | frame `hidden`: a three-row brown dirt mound, no bone; likely lost against the ground tile |
| At-rest cue | pending [#359](https://github.com/jlogicgames/edgard_in_kimeria/issues/359): a bone sliver in the mound, active tier |
| Reads from | its zone: 64 px before the column in `forest-0-ladder`, 80 px by default |
| Trigger | the hero body box (11×22) overlaps the footprint widened by `zone` per side, from 32 px above to the floor |
| Telegraph (D7) | tip peeks and wobbles (`peek1`/`peek2`, every 0.1 s) |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | `rising` for 0.12 s (lower half of the column), then `up`, 6×22 px, until the room is rebuilt |
| Counter | jump it (take-offs 7-63 px before the column); trigger it, wait for the rise, then jump; walk back |
| Reset | retry, Restart Level and room re-entry rebuild it hidden |
| Taught in | `forest-0-ladder` with `HINT_LADDER_ROOT` |
| Load checks | `ThornTrap.openFloorProblem` (jump envelope, floor, headroom); mirrored in `check_tiled_links.py` |
| Tests | `ProximityTrapsTest`, `LadderLessonTest` |
| Art and sound | `trap-thorn-root` 16×24: hidden, peek1, peek2, rising, up |
| Decision | D44, D48 |

### Bristling thorn tuft (`ThornTuft`)

| Field | Value |
| --- | --- |
| Family | proximity |
| Visibility (D71) | VISIBLE |
| At rest | frame `calm`: crimson-magenta stems with bone tips, deliberately not bush-green; no idle motion |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | as the thorn root (zone 80 by default) |
| Telegraph (D7) | `bristle` frame |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | `spiked`, 12×12 px centred, until the room is rebuilt |
| Counter | jump it (take-offs 2-61 px before it); trigger it and wait, then jump |
| Reset | room rebuild |
| Taught in | `forest-1-traps`, the first trap of the open stretch, on open floor |
| Load checks | as the thorn root |
| Tests | `ProximityTrapsTest`, `Forest1TrapsTest` |
| Art and sound | `trap-thorn-tuft` 16×16: calm, bristle, spiked |
| Decision | D44, D58 |

### Falling branch (`FallingBranch`)

| Field | Value |
| --- | --- |
| Family | position |
| Visibility (D71) | DISGUISED |
| At rest | a hanging branch that reads as scenery; no foliage tiles above it (a D48 gap) |
| At-rest cue | pending [#361](https://github.com/jlogicgames/edgard_in_kimeria/issues/361): a cracked joint at the bough |
| Reads from | its zone (`forest-0-wall`: x 1728-1776, the column itself) |
| Trigger | the hero body box overlaps the authored zone (`zoneX`, `zoneY`, `zoneWidth`, `zoneHeight`) |
| Telegraph (D7) | rustles in the first half of the warning, sags in the second |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | the swept box during the fall for a hero on the ground, the landed box at impact |
| Counter | run on or jump; do not stand under it; step back out of the column |
| Reset | room rebuild; the landed branch stays as harmless scenery until then |
| Taught in | `forest-0-wall` with `HINT_WALL_BRANCH` |
| Load checks | `FallingTrapLayout.validate`; mirrored in `check_tiled_links.py` |
| Tests | `FallingTrapsTest`, `WallLessonTest` |
| Art and sound | `trap-branch` 32×16 |
| Decision | D45, D48 |

### Falling stone (`FallingStone`)

| Field | Value |
| --- | --- |
| Family | position |
| Visibility (D71) | HIDDEN |
| At rest | nothing: the stone is not drawn until it falls |
| At-rest cue | pending [#360](https://github.com/jlogicgames/edgard_in_kimeria/issues/360): a cracked rock lip at the start point with a deterministic grit trickle ("loose overhang") |
| Reads from | its zone: 32 px before the column in `forest-0-platform`, 16 px in `forest-2` |
| Trigger | the hero body box overlaps the authored zone |
| Telegraph (D7) | a ground shadow grows small, medium, large |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | the stone while it falls, then the landed stone at impact |
| Counter | walk on; do not stand under it |
| Reset | room rebuild; the cracked stone stays until then |
| Taught in | `forest-0-platform` with `HINT_PLATFORM_STONE`; `forest-2` has no hint, so the cue matters most there |
| Load checks | `FallingTrapLayout.validate` (start at the room top or under a solid); mirrored in `check_tiled_links.py` |
| Tests | `FallingTrapsTest`, `PlatformLessonTest`, `Forest2TrapsTest` |
| Art and sound | `trap-stone`, `trap-shadow` (skin `forest`); impact effects are #347 |
| Decision | D45, D48, D59 |

### Exploding stump (`ExplosiveStump`)

| Field | Value |
| --- | --- |
| Family | touch |
| Visibility (D71) | VISIBLE (was DISGUISED, playtest F18; idle tell #346) |
| At rest | the idle tell, frames `idle1`..`idle4` looped once per second: a glowing crack across the cut and the bark, a dull-red ember pulsing in it, and a 1 px smoke wisp rising from it. It moves, and no scenery has ember colours (decor is muted, D76) |
| At-rest cue | none needed |
| Reads from | ~320 px (it moves and carries an ember accent) |
| Trigger | the hero body box overlaps the 10×13 trigger box; a fireball or thrown stone hitting it from afar |
| Telegraph (D7) | cracks, smoke, glow (three wind-up frames): the ember turns orange, then yellow, the wisp becomes puffs |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | one 64×64 blast square check at detonation |
| Counter | jump it (the floor bypass validated at load); light it from afar and wait; walk through during the wind-up |
| Reset | room rebuild; spent remains until then |
| Taught in | `forest-0-stone` with `HINT_STONE_STUMP`; again in `forest-0-traps` |
| Load checks | `ExplosiveTrap.problem` (floor bypass); mirrored in `check_tiled_links.py` |
| Tests | `ExplosiveTrapsTest`, `StoneLessonTest`, `TrapsLessonTest` |
| Art and sound | `trap-stump` 16×16, eight frames (idle1..4, windup1..3, spent); active tier, idle outline 0.33 at worst (D76); `blast` 32×32 at 2x; `explosion` sound, no idle sound |
| Decision | D47, D48 |

### Exploding root (`ExplosiveRoot`)

Same rules as the exploding stump, 16×24, sheet `trap-root`, and the same idle tell (#346): VISIBLE, an ember crack
down the root and a smoke wisp from its tip, `idle1`..`idle4` looped once per second; idle outline 0.35 at worst
(D76). It has no teaching room of its own: it shares the stump's idle tell and wind-up language, taught in
`forest-0-stone`. Placed in `forest-2` `forest-canopy`. Decision D47, D59.

### Bomb (`Bomb`)

| Field | Value |
| --- | --- |
| Family | touch |
| Visibility (D71) | VISIBLE |
| At rest | its own redrawn bomb sprite (D60): an object, not scenery |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | the 10×13 activation box on touch |
| Telegraph (D7) | warm tint flashing every 0.15 s |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | one 64×64 square check at detonation |
| Counter | clear the square during the fuse |
| Reset | saved trap state on a healer revisit (D7), otherwise room rebuild |
| Taught in | `forest-entry` (`forest-2`; the explosive language is taught by the stump) |
| Load checks | none of its own |
| Tests | `BombTest` |
| Art and sound | bomb sprite (D60), shader blast `BombExplosionEffect` |
| Decision | D7, D51, D60 |

### Spore puffball (`Puffball`)

| Field | Value |
| --- | --- |
| Family | rhythm |
| Visibility (D71) | VISIBLE |
| At rest | a pale round cap that swells and bursts on a period, so it moves |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | the level clock: `(elapsed + phase) mod period` (default period 4 s) |
| Telegraph (D7) | cap swells in two steps |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s inside the period |
| Lethal part | the 24×24 cloud for `cloud` s (default 1 s) |
| Counter | cross in the calm window |
| Reset | the clock restarts at the authored phase on room rebuild |
| Taught in | `forest-1-traps`, seen swelling from afar |
| Load checks | `Level.requireSafeSetting` (entrance, exit, checkpoint) |
| Tests | `RhythmTrapsTest`, `Forest1TrapsTest` |
| Art and sound | `trap-puffball`, `trap-spores` |
| Decision | D46, D58 |

### Swinging thorn vine (`ThornVine`)

| Field | Value |
| --- | --- |
| Family | rhythm |
| Visibility (D71) | VISIBLE |
| At rest | always swinging: `amplitude * sin(2 pi (elapsed + phase) / period)` |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | the level clock |
| Telegraph (D7) | none needed: the motion is the warning (D46) |
| Warning | none (the motion) |
| Lethal part | the vine's lower half, 8 px wide |
| Counter | time the pass |
| Reset | clock restart on room rebuild |
| Taught in | `forest-canopy` (`forest-2`), seen swinging from afar |
| Load checks | `Level.requireSafeSetting`, period 3.5 s or more, amplitude 35-60° |
| Tests | `RhythmTrapsTest`, `Forest2TrapsTest` |
| Art and sound | `trap-vine` frame `hang`, rotated |
| Decision | D46, D59 |

### Honey fungus and false honey fungus (`HoneyFungus`, `FalseHoneyFungus`)

| Field | Value |
| --- | --- |
| Family | proximity |
| Visibility (D71) | VISIBLE (both glow; which one kills is the D63 look-alike puzzle) |
| At rest | `idle1` with a smooth brightness glow, 2.5 s period, 0.55 to 1.0 |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | the hero body box enters the zone (default 64 px per side, 32 px above) |
| Telegraph (D7) | the cap swells (two frames) |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | false one only: the 24×24 spore cloud for 1.0 s; the harmless one has no hitbox |
| Counter | trigger it and step back, or pass the cloud |
| Reset | room rebuild |
| Taught in | the false one in `forest-canopy` (`forest-2`), after the harmless one in `forest-0-lever` and `forest-0-wall` |
| Load checks | `Level.validateFungus`, `Level.requireSafeSetting` (false one) |
| Tests | `HoneyFungusTest` |
| Art and sound | `trap-honey-fungus`, `trap-false-honey-fungus`, `SporeCloud` |
| Decision | D63 |

### Falling platform, rotten log and rotten branch (`FallingPlatform`)

| Field | Value |
| --- | --- |
| Family | crumble |
| Visibility (D71) | DISGUISED (plain skin); the `log` and `branch` skins are VISIBLE: their rot is the cue, drawn on every instance |
| At rest | plain skin: an ordinary platform; log: a rotten log; branch: a grey, dead branch on a trunk, a split with a pale splinter in the middle of its top and one dry leaf under its tip, no green (unlike the sound branch, a one-way `Platform` with `skin=branch`: brown bark, a pale top line, green leaves) |
| At-rest cue | plain: pending [#362](https://github.com/jlogicgames/edgard_in_kimeria/issues/362), a hairline crack on the intact frame; log and branch: their rotten look |
| Reads from | landing on it (plain); ~320 px for the log and the branch, if their rot reads (not verified, #368) |
| Trigger | the hero lands on it (feet onto its top while falling); rising through it from below triggers nothing (one-way) |
| Telegraph (D7) | the platform shakes; the log and the branch show crumble1 (the split opens), then crumble2 (it snaps), each for half the warning |
| Warning | `TrapWarnings.FALLING_PLATFORM_SECONDS`: 1.0 s / 1.5 s |
| Lethal part | none of its own: the fall leaves the hero over a pit or the `Deadline`; over a solid floor the platform sinks into it and the hero is left standing on the floor line (D69). A rotten branch never stands over a pit or above a one-way platform (D80), so its fall only costs height |
| Counter | keep moving, jump off before the fall; on a tree, jump up to the next branch (a 48 px climb takes about 0.45 s of the 1.0 s) or take the sound tree |
| Reset | saved room state (D7) on a healer revisit, otherwise room rebuild; a fallen branch stays fallen until then, so it is never the only way up (D26, D80) |
| Taught in | `forest-0-traps` (D40); the branch skin is first met in `forest-1-tree`, on the lowest branch of its rotten column, whose fall lands on the floor 48 px down |
| Load checks | `FallingPlatform.Skin.fromMap` (unknown skin fails); branch: `FallingPlatform.branchProblem` (no one-way platform under its fall, D69; the room's goal reachable with every rotten branch gone, D26), mirrored in `check_tiled_links.py` |
| Tests | `TrapsLessonTest`, `RhythmTrapsTest` (log), `Forest1TrapsTest`, `FallingPlatformFloorTest` (D69), `TreeClimbTest` (branch) |
| Art and sound | platform sprite; `trap-log`; `trap-rotten-perch` 32×16 × 6 (intact, crumble1, crumble2 and their copies for the left of a trunk; grid `art/objects/forest-traps/rotten-perch.grid`), active tier, outline 0.427 at its worst placement (D76; the sound branch `trap-perch` 0.458, the trunk `decor-trunk` decor tier 0.121 to 0.170, body 0.146 at most). Palette: grey wood `4a4048`/`7a6e6c`, pale top `b8ad9c`, the log's splinter `efe6c4`, dry leaf `9c6a34`, ink `0b0814`; no accent hue. It reads apart from the falling branch hazard by place (underfoot, not overhead), colour (dead grey, not tan with violet buds) and crack (mid-top, not #361's joint). No sound of its own, like the other skins |
| Decision | D7, D40, D46, D69, D80 |

## Proposed traps (D71, not built)

Chosen by the author on 2026-11-16 for later work; each has its own issue and needs an approved card before code.

| Trap | At rest (VISIBLE) | Telegraph and lethal part | Counter | Room purpose | Issue |
| --- | --- | --- | --- | --- | --- |
| Trip-line deadfall | a log pulled back and tied off on a bough; a pale trip vine stretched across the floor | touching the vine: creak and shiver for the shared warning, then one swing across body height | jump the vine (no trigger at all); or trip it and step back | teach "a visible trigger can be avoided" | [#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363) |
| Creeping bramble | a thorn clump rooted in a wall or ledge side, tips always showing | rhythm: rustle and a first tip for the shared warning, then thorns grow across a lane, hold, retract | pass in the retracted window | a timing gate on a horizontal lane (a ledge top, a ladder exit) | [#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364) |
| Hanging hive | a hive on a branch with a few drifting specks at its mouth (deterministic) | rhythm: the hive shakes and specks gather for the shared warning, then a 1 s curtain of stingers falls straight down | time the pass; optionally knock the hive down with a stone or fireball (never required, D26) | combine with a vertical route (drop, ladder) | [#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365) |

**Candidate (author, 2026-11-16):** an **ember fissure**, a glowing ground crack that jets embers upward on a rhythm
(a vertical danger on the floor; it would echo the fire gift of #340), tracked by
[#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366); not chosen for building yet.

**Rejected (author, 2026-11-16):** a **snap burr**, a huge open seed husk that snaps shut, because D42 rejected a
carnivorous plant and this is too close to one.

## Maintenance

When a cue issue lands, change the catalogue row's cue from `pending #NNN` to the cue, update the card, and keep the
visibility word (a DISGUISED trap with a cue stays DISGUISED). A new trap adds its card and its catalogue row in the
same change as its `case` in `Level`; `TrapCatalogTest` fails otherwise.
