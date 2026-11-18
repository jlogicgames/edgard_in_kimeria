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
| Art and sound | sheet and frames, contrast tier (D76: active, with the measured outline; an at-rest cue is drawn in the active tier), sound; then `Consequence (D93):` the sound of its act, heard hit or miss, in backticks (a file in `assets/audio` listed in `Assets.SOUND_EFFECTS`), or `none yet (#NNN)`, or `intentionally none (Dxx)` |
| Decision | D-number(s) |
```

## Catalogue

The gate `TrapCatalogTest` (`:core:test`) reads the two tables below and the spawn switch of `world/Level.java`.
It fails when a type spawned by `Level` is in neither table, when a row names a type `Level` does not spawn, when a
trap row has an empty column, an unknown visibility or family word or no `###` card heading, and when a DISGUISED or
HIDDEN row has neither a cue nor `pending #NNN`, or no teaching room. It also reads every trap's card (D93): the card holds
exactly one `Consequence (D93):` statement, sound names in backticks that exist in `assets/audio` and
`Assets.SOUND_EFFECTS`, `none yet (#NNN)` or `intentionally none (Dxx)`. Keep one row per Tiled object type and the column order as it is.
`TrapTeachingRoomTest` (D75) reads the `Taught in` column against [ROOM_CARDS.md](ROOM_CARDS.md): it starts with the
teaching room in backticks, a shipped room with a card that shows the trap (or a reskin of it); no earlier room in play
order shows the type; and for a DISGUISED or HIDDEN trap that room's card introduces the trap's element.

### Traps

<!-- trap-catalogue:start -->
| Type | Family | Visibility | At-rest cue | Warning | Taught in | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| `ThornRoot` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-ladder`, hint `HINT_LADDER_ROOT` | D44, D48 |
| `ThornTuft` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-traps`, first trap of the stretch | D44, D58 |
| `FallingBranch` | position | DISGUISED | cracked joint (#361) | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-wall`, hint `HINT_WALL_BRANCH` | D45, D48 |
| `FallingStone` | position | HIDDEN | loose overhang: a cracked rock lip with the stone's crown wedged in it, grit trickling every 1.5 s | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-platform`, hint `HINT_PLATFORM_STONE` | D45, D48, D59 |
| `ExplosiveStump` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-stone`, hint `HINT_STONE_STUMP` | D47, D48 |
| `ExplosiveRoot` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-stone` (the stump's idle tell and wind-up language) | D47, D59 |
| `Bomb` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-entry` (`forest-2`) | D7, D51, D60 |
| `Puffball` | rhythm | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-traps`, seen swelling from afar | D46, D58 |
| `ThornVine` | rhythm | VISIBLE | none needed | the motion is the warning | `forest-canopy` (`forest-2`), seen swinging from afar | D46, D59 |
| `HoneyFungus` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-0-lever`, harmless demonstration | D63 |
| `FalseHoneyFungus` | proximity | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-canopy` (`forest-2`), after the harmless `HoneyFungus` of `forest-0-lever` | D63 |
| `FallingPlatform` | crumble | DISGUISED | a hairline crack through the slab (#362) | `FALLING_PLATFORM_SECONDS` 1.0 / 1.5 s | `forest-0-traps` | D7, D40, D46, D69, D80 |
| `Deadfall` | touch | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-deadfall`, met alone on open floor | D88 |
| `Bramble` | rhythm | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s inside the period | `forest-1-bramble`, seen cycling from the floor before the climb | D46, D89 |
| `HangingHive` | rhythm | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-1-hive`, seen raining from the entrance over open floor, then over the ladder | D90 |
| `EmberFissure` | rhythm | VISIBLE | none needed | `DEFAULT_WARNING_SECONDS` 0.8 / 1.2 s | `forest-fissure`, seen jetting from the entrance over open floor, then under a branch it pierces | D91 |
<!-- trap-catalogue:end -->

`FallingPlatform` with `skin=log` (the rotten log, D46) or `skin=branch` (the rotten tree branch, D80) is the same type
and row: their rotten look is the cue for those skins; the plain skin's cue is the hairline crack of #362.

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
| `Beetle` | enemy | Enemy (D100): its burst's warning is the shared `TrapWarnings` wind-up on its own body, recorded in D100 and the beetle card of GAME_DESIGN.md, not a trap card. |
| `Boss` | enemy | The boss ([BOSS.md](BOSS.md)). |
<!-- non-trap-types:end -->

Two lethal things are outside the spawn switch and so outside the gate: the level's `Deadline` (the pit line; the
pit itself is the visible part) and `QuickSand` (a collision type that slows; it does not kill by itself).

## Cards

### Thorn root (`ThornRoot`)

| Field | Value |
| --- | --- |
| Family | proximity |
| Visibility (D71) | VISIBLE (was HIDDEN until [#359](https://github.com/jlogicgames/edgard_in_kimeria/issues/359)) |
| At rest | frame `hidden`: the dirt mound with the stake's bone tip breaking through and two dark thorn barbs at its edges, a thorny silhouette no scenery shares; outline 0.380 (active tier) |
| At-rest cue | none needed: the rest frame shows the stake; the barbs stay through `peek1`, `peek2` and `rising`, so the warning is the tip rising two rows and wobbling |
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
| Art and sound | `trap-thorn-root` 16×24: hidden (tip and barbs, #359), peek1, peek2, rising, up; no sound and no particles when it springs. Consequence (D93): none yet ([#446](https://github.com/jlogicgames/edgard_in_kimeria/issues/446)) |
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
| Art and sound | `trap-thorn-tuft` 16×16: calm, bristle, spiked; no sound when it bristles into `spiked`. Consequence (D93): none yet ([#446](https://github.com/jlogicgames/edgard_in_kimeria/issues/446)) |
| Decision | D44, D58 |

### Falling branch (`FallingBranch`)

| Field | Value |
| --- | --- |
| Family | position |
| Visibility (D71) | DISGUISED |
| At rest | a tan branch with violet buds hanging overhead, its joint cracked; it grows from a `Decor:trunk` under a `Decor:crown` of leaves (#421, D48), scenery in the decor tier, so the pale cracked joint stays the only bright, warm mark |
| At-rest cue | a cracked joint at the bough, drawn on every instance in the resting and rustle frames ([#361](https://github.com/jlogicgames/edgard_in_kimeria/issues/361)): a pale raw-wood band (`fff4b0`) round the joint, 2 px right of the knob and through the whole depth of the bough, cut by an ink zig-zag, with one loose chip of light wood 3 px under it. It reads apart from the rotten branch (D80) by place (overhead, not underfoot), colour (tan, not dead grey) and crack (a pale band across the joint, not a 1 px split mid-top) |
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
| Art and sound | `trap-branch` 32×16 × 4 (resting, rustle, sag, falling; grid `art/objects/forest-traps/branch.grid`), active tier, outline 0.463 at rest in `forest-0-wall` (D76; 0.483 before the cue), rustle 0.429. No impact feedback when it lands (no dust, sound or shake, unlike the stone's D73). Consequence (D93): none yet ([#443](https://github.com/jlogicgames/edgard_in_kimeria/issues/443)) |
| Decision | D45, D48 |

### Falling stone (`FallingStone`)

| Field | Value |
| --- | --- |
| Family | position |
| Visibility (D71) | HIDDEN |
| At rest | the loose overhang (#360): frame `loose` of `trap-stone-lip`, a cracked rock lip with the stone's crown wedged in it, drawn over the stone's column at the map's `lipTop` (absolute world y of its top edge) or, without it, at the start point lowered to the camera's top edge while the hero stands on the floor; `forest-0-platform` y 8 (no property), `forest-2` y 48 (`lipTop`, under the dirt block over the stone); the stone itself is drawn only once it falls, from its unchanged start |
| At-rest cue | the lip, always drawn, plus a grit trickle while armed: every 1.5 s four specks 0.1 s apart fall about 48 px from one of the lip's two side cracks (alternating), dark and light so one of each reads on sky and on dirt; seeded from the stone's start position, so a run repeats. The trickle stops at the trigger: the warning is the shadow alone. After the fall the lip shows `broken`, the empty socket |
| Reads from | the lip and its trickle, on screen while the hero stands on the floor (rooms are 8 px taller than the view, so their top 8 px are off screen; the loader refuses a `lipTop` above that line, and `RoomCeilingTest` checks the lip is in view in both rooms), from about 300 px before the zone; the trigger zone starts 32 px before the column in `forest-0-platform`, 16 px in `forest-2` |
| Trigger | the hero body box overlaps the authored zone |
| Telegraph (D7) | a ground shadow grows small, medium, large |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s |
| Lethal part | the stone while it falls, then the landed stone at impact |
| Counter | walk on; do not stand under it |
| Reset | room rebuild; the cracked stone stays until then |
| Taught in | `forest-0-platform` with `HINT_PLATFORM_STONE`; `forest-2` has no hint, so the cue matters most there |
| forest-2 zone (measured for #360) | zone x 2208-2240, y 288-320; a hero walking right triggers it 16 px before the column (0.16 s at 100 px/s), one walking left already under it; trigger to impact 0.8 + 0.84 = 1.64 s (1.2 + 0.84 = 2.04 s with the assist), so a hero who keeps walking is 164 px on at impact. Widening is unnecessary: the zone decides only when the stone falls, and the cue is read from the lip long before it |
| Load checks | `FallingTrapLayout.validate` (start at the room top or under a solid; only the properties `zoneX`, `zoneY`, `zoneWidth`, `zoneHeight`, `skin`, `lipTop`, so a misspelled key fails; `lipTop` on a stone only, on screen with the hero on the floor, not above the start, the lip above the landing floor, and on the default line or directly under a solid block or a drawn tile row over the column, D45's rule for the start); mirrored in `check_tiled_links.py` |
| Tests | `FallingTrapsTest` (with the cue: lip frames, trickle period, cracks, determinism, pool, the `lipTop` load check), `RoomCeilingTest` (the lip in the camera view), `PlatformLessonTest`, `Forest2TrapsTest`, `AllocationBudgetTest.fallingStoneLooseOverhang` |
| Art and sound | `trap-stone`, `trap-shadow`, `trap-stone-lip` 24×8 (`loose`, `broken`; skin `forest`); active tier, lip outline at worst (`loose` / `broken`): 0.387 / 0.403 in `forest-0-platform` at y 8, 0.323 / 0.336 in `forest-2` at y 48 (D76; at y 8 over the dirt block forest-2 measured 0.251, under the floor, hence `lipTop`); grit is `KIND_GRIT` of the stone's own `KimeriaParticleSystem`; impact effects are #347. Consequence (D93): `stone_impact` |
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
| Art and sound | `trap-stump` 16×16, eight frames (idle1..4, windup1..3, spent); active tier, idle outline 0.33 at worst (D76); `blast` 32×32 at 2x; `explosion` sound, no idle sound; no screen shake at the blast, a decision not taken yet ([#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448)). Consequence (D93): `explosion` |
| Decision | D47, D48 |

### Exploding root (`ExplosiveRoot`)

Same rules as the exploding stump, 16×24, sheet `trap-root`, and the same idle tell (#346): VISIBLE, an ember crack
down the root and a smoke wisp from its tip, `idle1`..`idle4` looped once per second; idle outline 0.35 at worst
(D76). It has no teaching room of its own: it shares the stump's idle tell and wind-up language, taught in
`forest-0-stone`. Placed in `forest-2` `forest-canopy`. Consequence (D93): `explosion`, as the stump. Decision D47, D59.

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
| Art and sound | bomb sprite (D60), shader blast `BombExplosionEffect`, `explosion` sound; no screen shake at the blast, a decision not taken yet ([#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448)). Consequence (D93): `explosion` |
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
| Art and sound | `trap-puffball`, `trap-spores`; the burst has no sound. Consequence (D93): none yet ([#445](https://github.com/jlogicgames/edgard_in_kimeria/issues/445)) |
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
| Art and sound | `trap-vine` frame `hang`, rotated. Consequence (D93): intentionally none (D46: it never acts, it swings; the motion is the warning) |
| Decision | D46, D59 |

### Honey fungus and false honey fungus (`HoneyFungus`, `FalseHoneyFungus`)

| Field | Value |
| --- | --- |
| Family | proximity |
| Visibility (D71) | VISIBLE (both glow; which one kills is the D63 look-alike puzzle) |
| At rest | `idle1` with a smooth brightness glow, 2.5 s period, 0.80 to 1.0 (0.55 until D92: the moon rim must stay readable at the dimmest glow on the night) |
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
| Art and sound | `trap-honey-fungus`, `trap-false-honey-fungus`, `SporeCloud`; neither burst has a sound; both must get the same one, so the sound does not tell which kills (D63). Consequence (D93): none yet ([#445](https://github.com/jlogicgames/edgard_in_kimeria/issues/445)) |
| Decision | D63 |

### Falling platform, rotten log and rotten branch (`FallingPlatform`)

| Field | Value |
| --- | --- |
| Family | crumble |
| Visibility (D71) | DISGUISED (plain skin); the `log` and `branch` skins are VISIBLE: their rot is the cue, drawn on every instance |
| At rest | plain skin: a platform with a hairline crack through its slab; log: a rotten log; branch: a grey, dead branch on a trunk, a split with a pale splinter in the middle of its top and one dry leaf under its tip, no green (unlike the sound branch, a one-way `Platform` with `skin=branch`: brown bark, a pale top line, green leaves) |
| At-rest cue | plain: a hairline crack ([#362](https://github.com/jlogicgames/edgard_in_kimeria/issues/362)), a 1 px ink line through the striped slab at x 22-23, slanting against the stripes, with a 1 px gap in the slab's top and bottom edge where it breaks through, drawn in every fan frame and in every state; log and branch: their rotten look (no extra crack: the branch's mid-top split is already its cue) |
| Reads from | ~320 px for the plain skin, if its 1 px crack reads at that range, and for the log and the branch, if their rot reads (not verified, #368) |
| Trigger | the hero lands on it (feet onto its top while falling); rising through it from below triggers nothing (one-way) |
| Telegraph (D7) | the platform shakes; the log and the branch show crumble1 (the split opens), then crumble2 (it snaps), each for half the warning |
| Warning | `TrapWarnings.FALLING_PLATFORM_SECONDS`: 1.0 s / 1.5 s |
| Lethal part | none of its own: the fall leaves the hero over a pit or the `Deadline`; over a solid floor the platform sinks into it and the hero is left standing on the floor line (D69). A rotten branch never stands over a pit or above a one-way platform (D80), so its fall only costs height |
| Counter | keep moving, jump off before the fall; on a tree, jump up to the next branch (a 48 px climb takes about 0.45 s of the 1.0 s) or take the sound tree |
| Reset | saved room state (D7) on a healer revisit, otherwise room rebuild; a fallen branch stays fallen until then, so it is never the only way up (D26, D80) |
| Taught in | `forest-0-traps` (D40); the branch skin is first met in `forest-1-tree`, on the lowest branch of its rotten column, whose fall lands on the floor 48 px down |
| Load checks | `FallingPlatform.Skin.fromMap` (unknown skin fails); branch: `FallingPlatform.branchProblem` (no one-way platform under its fall, D69; the room's goal reachable with every rotten branch gone, D26), mirrored in `check_tiled_links.py` |
| Tests | `TrapsLessonTest`, `RhythmTrapsTest` (log), `Forest1TrapsTest`, `FallingPlatformFloorTest` (D69), `TreeClimbTest` (branch) |
| Art and sound | plain: `trap-falling-platform` 32×10 × 4 (fan1..fan4, grid `art/objects/forest-traps/falling-platform.grid`, the old `FallingOn.png` plus the crack), active tier, outline 0.379 at its worst placement and frame (D76; unchanged by the crack); `trap-log`; `trap-rotten-perch` 32×16 × 6 (intact, crumble1, crumble2 and their copies for the left of a trunk; grid `art/objects/forest-traps/rotten-perch.grid`), active tier, outline 0.427 at its worst placement (D76; the sound branch `trap-perch` 0.458, the trunk `decor-trunk` decor tier 0.121 to 0.170, body 0.146 at most). Palette: grey wood `4a4048`/`7a6e6c`, pale top `b8ad9c`, the log's splinter `efe6c4`, dry leaf `9c6a34`, ink `0b0814`; no accent hue. It reads apart from the falling branch hazard by place (underfoot, not overhead), colour (dead grey, not tan with violet buds) and crack (mid-top, not #361's joint). No sound of its own and no particles when it gives way, in any skin. Consequence (D93): none yet ([#444](https://github.com/jlogicgames/edgard_in_kimeria/issues/444)) |
| Decision | D7, D40, D46, D69, D80 |

### Trip-line deadfall (`Deadfall`)

Geometry below is for a log that swings **left**, towards a hero coming from the left (the default); `swing=right`
mirrors it. P is the rope knot on the bough; F is the floor line, 68 px below P. World Y grows downward, so "above"
means a smaller Y.

| Field | Value |
| --- | --- |
| Family | touch |
| Visibility (D71) | VISIBLE |
| At rest | frame `tied`: a dark bark log (24×10) hauled up on the far side and lashed to the bough with a pale cord, its two ropes slack under it, the log's underside at F-53 (31 px over the hero's head); a pale, bone-coloured trip vine (`efe6c4`) stretched taut between two dark pegs across the floor, 16 px wide and 4 px high, at P.x-120 to P.x-104. No scenery is a horizontal log hung on ropes, and no scenery is pale at ankle height on a floor. A bare snag sticks down from the bough on the near side, its tip at P.x-40, 8 px under the bough: where the log will end |
| At-rest cue | none needed: the log, the ropes and the vine are all drawn at rest |
| Reads from | ~320 px: the tied log and the vine enter the screen together |
| Trigger | the hero body box (11×22) overlaps the vine's box (x P.x-120 to P.x-104, y F-5 to F); a hero in the air above F-5 clears it. Either walking direction trips it. Nothing else trips it (no stone, no fireball): the answer is the hero's own feet |
| Telegraph (D7) | frame `strain` for the first half of the warning (the vine tugged into a V, the log shivers 1 px sideways every 0.1 s), frame `slip` for the second (the lashing's knot slips, the log drops 1 px on its ropes) |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s, sampled once at the trip |
| Lethal part | the log's box (24×10) while it swings: one pass from the tied angle (+80°, far side) to the snag (-78°, near side, where the ropes meet the snag's tip) in 0.5 s, on an eased arc (rope 56 px from P to the log's centre, `θ = 80° - 158° · (1 - cos(π t / 0.5)) / 2`). It is at body height (its underside below F-22) only from 0.16 s to 0.34 s after the release, over x P.x-50 to P.x+50. Before the release and once it hangs from the snag it is harmless. A hero who trips the vine and keeps walking (100 px/s) is hit with either warning length: in `forest-1-deadfall` 0.27 s into the swing with 0.8 s, 0.18 s with 1.2 s (`DeadfallTrapTest`) |
| Counter | jump the vine: nothing fires and the log stays tied, so the hero walks under it; or trip it and stop (or step back): at the trip the hero's centre is at P.x-125, 70 px before the band, so stopping within 0.68 s of the trip is safe with either warning (measured), and the log goes by in front of him; walking back is safe too; then walk on. Jump take-offs from 2 to 60 px before the vine clear it (measured). No ammunition answer and none needed (D26) |
| Reset | retry, Restart Level and room re-entry rebuild it tied, with the vine whole. At the catch the ropes lie across the snag's tip and the log eases in under it over 0.25 s (no jump, `DeadfallTrap.SETTLE_SECONDS`); until the rebuild it hangs from the snag on what is left of its ropes (frame `caught`, centre P.x-40, underside at F-36, 14 px over the head) and the vine lies cut in two (frame `cut`), harmless scenery |
| Taught in | `forest-1-deadfall`, met alone on open floor, the only object in the room |
| Load checks | `DeadfallTrap.problem` (in `Level` for every room at load): the object is the bough's own 112×24 rectangle and `swing` is empty, `left` or `right`; solid floor (not quicksand) at F from P.x-184 (64 px before the vine, room to stop or to take off) to P.x+56; nothing solid (blocks, falling platforms, walls with their travel, ladders, escalators with their travel) from the bough down to the floor within P.x±68 (the swing), nor within 96 px over the floor from P.x-184 to P.x-68 (the jump over the vine); all of it inside the room; the entrance not on the vine; no entrance, exit or checkpoint where the log passes at body height (P.x±62, F-27 to F). Mirrored in `check_tiled_links.py` |
| Tests | `DeadfallTrapTest` (the room; the trip at the vine's first pixel; no jump at the catch (at most 2.13 px a frame); the warning equals the shared one with both assist values, strain then slip; the body-height window and band; harmless before the release and after the catch; the jump window; a walker who keeps going is hit with both warnings; stopping and walking back live; a hero who cannot be hurt is not seen; `swing=right`; reset on retry and Restart Level; pause; the load check), `test_check_tiled_links.DeadfallLinksTest`, `test_check_contrast` (the three parts); `AllocationBudgetTest.deadfallTripAndSwing` (0.0 B/frame) and `forest1DeadfallRoom` |
| Art and sound | sheets `trap-deadfall` (log, 32×16: `tied`, `strain`, `slip`, `swing`, `caught`), `trap-deadfall-bough` (112×24: the bough, its two rope knots and the snag; its thick end meets a `Decor:trunk`), `trap-deadfall-vine` (24×8: `taut`, `tugged`, `cut`), the rope a stretched 1×8 strip; grids under `art/objects/forest-traps/`; palette of the sound branch (`perch`) plus end grain `e8c890`/`9c6a34` and the bone `efe6c4`/`b8ad9c` of cord and vine, no accent hue. Active tier: the contrast gate measures the three parts drawn at rest where the game draws them (`tools/check_contrast.py`, `deadfall_parts`), worst in `forest-1-deadfall` (the same over the old sky and over the D92 background): bough 0.464, tied log 0.410, taut vine 0.323 (floor 0.28); from the approved mock-up, also vine 0.343 tugged / 0.369 cut, swinging log 0.389, caught log 0.423. Sounds: `deadfall_creak` at the trip (warning, 0.6 s, ends before the base 0.8 s release), `deadfall_knock` when the ropes hit the snag (consequence), with six bark chips (`KIND_GRIT`, `EnvironmentFx.barkChips`) falling from it. Consequence (D93): `deadfall_knock` |
| Decision | D88 |

### Creeping bramble (`Bramble`)

Approved by the author on 2026-11-17 ([#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364),
[D89](DECISIONS.md#d89-the-creeping-bramble-a-clocked-thorn-arm-across-a-lane-taught-at-a-ladder-exit-364-2026-11-17)).

| Field | Value |
| --- | --- |
| Family | rhythm |
| Visibility (D71) | VISIBLE |
| At rest | frame `rest`: a knot of magenta canes rooted in a wall or ledge face, brown roots going into the face, four bone thorn tips always pointing out of it. The thorn family's language (magenta cane, bone tips, ink contour, as the tuft and the root), in a shape no scenery and no other trap has: a clump on a vertical face, not on the floor. Outline in `forest-1-bramble` (`check_contrast.py`, all six frames at the root end, mirrored for `root=right`; the same over the old sky and over the D92 background): `rest` 0.419, worst frame 0.414 (`cane1`, `cane2`) |
| At-rest cue | none needed: the clump is the danger, and it is lethal at every moment |
| Reads from | ~320 px: the clump at rest, and the arm going out and back every period |
| Trigger | the level clock, `(elapsed + phase) mod period`, as the puffball (D46); no dependence on the hero |
| Telegraph (D7) | the clump rustles (`rustle1` for the first half of the warning, `rustle2` for the second: the canes shift by a pixel and a first, then a second bone tip pokes past the clump), with the rustle sound |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s inside the period |
| Rhythm | one cycle: **rest**, then **warning** (0.8 / 1.2 s), then **grow** 0.25 s (the tip runs from the clump to the full reach), **hold** (property `hold`, default 1.0 s, allowed 0.6-1.5 s), **retract** 0.5 s (the tip runs back), and rest again. Defaults: `period` 4.0 s, `phase` 0, so rest is 1.45 s (1.05 s with the assist); rest plus warning, the window in which the lane is open, is 2.25 s with or without the assist. Load rule: rest at least 0.8 s with the assisted warning, so `period >= 1.2 + 0.25 + hold + 0.5 + 0.8` (3.75 s at the default hold) |
| Lethal part | world Y grows downward. A band 16 px tall whose top is 18 px above the floor line and whose bottom is 2 px above it (`floor - 18` to `floor - 2`), so a hero standing on the floor (body `floor - 22` to `floor`) always overlaps it and a graze over the tips does not. Along the lane: the first 12 px from the root face in every phase (the clump); from the root face to the tip while it grows, holds and retracts (the tip moves linearly, 12 px to the full reach in 0.25 s, back in 0.5 s). The full reach is the object's width (48-96 px, default recipe 64) |
| Rhythm, measured | `BrambleTest`, fixture `bramble-test.tmx` (the teaching recipe): a hero who waits on the ladder and climbs out when the retract ends reaches the exit from every start offset (0.05 s steps); a blind climb-out survives 37 of 80 offsets, the longest safe run 1.85 s |
| Counter | pass during rest or warning (the lane beyond the clump is open for 2.25 s per cycle); wait outside the reach, or below the lane's floor line on a ladder, and go at the next retract. The tip grows at about 200 px/s, faster than the hero runs, so the warning means "leave now or wait". On an open lane a jump over the grown arm also passes; the teaching room puts it over a ladder exit, where the hero comes up inside the lane and only the timed pass works. No ammunition answer: a fireball or a stone does nothing to it |
| Reset | the clock restarts at the authored phase on every room rebuild (retry, Restart Level, room re-entry); no saved room state, as the puffball. Pause freezes it (the clock advances only in `update`) |
| Taught in | `forest-1-bramble` (after `forest-1-lever`): seen cycling from the floor, out of reach, before the hero climbs the ladder it guards. Recipe: the guarded ladder stands at least one tile off the root face, since the clump's 12 px are always lethal (D89) |
| Load checks | `Bramble.fromMap`: the properties `root` (`left` or `right`, required), `period`, `phase`, `hold` read strictly (finite numbers, no unknown key), height 24, width 48-96, the rhythm rule above; `Level.validateBramble`, for every bramble of every room at level load: `Bramble.placementProblem`, the root edge against a solid face (not a one-way platform) over the band's whole height, and a floor or shelf top under the tip end and 16 px beyond it (split collisions and 0.5 px offsets merged with `FLOOR_TOLERANCE`, as the deadfall); `Level.requireSafeSetting` (inside the room; the lethal part at full reach never covers the entrance, the `RoomExit` or a `Checkpoint`). Mirrored in `check_tiled_links.py` (`check_bramble`) |
| Tests | `BrambleTest` (fixtures `bramble-test.tmx` and three broken ones): the warning equals the shared warning in play (0.8 and 1.2 s); every phase boundary at both assist values; the cues once per cycle; the tip's extent; the lethal band as a rectangle against the floor line (top `floor - 18`, bottom `floor - 2`, from the root face); a standing hero hit only where the thorns are; reset on retry and Restart Level; pause; the static ladder; the waiting and blind ladder exits; the load failures; split faces and floors and sub-pixel offsets within the tolerance. `AllocationBudgetTest.brambleCycles` 0.0 to 0.7 B/frame (ceiling 8). `tools/test_check_tiled_links.py` (the mirror agrees with the fixtures). `Forest1BrambleTest` (the room: census, in view from the entrance, its cues heard from the entrance (camera-view gate), mastered route 10.73 s, every start offset 9.42 to 12.80 s, retry) |
| Art and sound | `trap-bramble` 16×24 × 6 (`rest`, `rustle1`, `rustle2`, `cane1`, `cane2`, `tip`; grid `art/objects/forest-traps/bramble.grid`): the clump is drawn at the root; the arm is alternating `cane1`/`cane2` segments behind the `tip` at the current extent, so it grows and retracts by whole pixels; drawn mirrored for `root=right`. Palette: ink `0b0814`, cane `c2307a`/`6e1650`, root `4a2e1c`, bone `efe6c4`/`a89c78` (the tuft's magenta and bone, no new accent). Active tier (D76). Sounds `bramble_rustle` at the warning and `bramble_lash` at the grow, heard only while the bramble is in the camera view (SOUND_DIRECTION; auditioned by the author 2026-11-17, approved). Consequence (D93): `bramble_lash` |
| Decision | D46 (rhythm rules), D89 |

### Hanging hive (`HangingHive`)

| Field | Value |
| --- | --- |
| Family | rhythm |
| Visibility (D71) | VISIBLE |
| At rest | frame `rest` of `trap-hive` (24×32): a paper wasp hive in pale and dark bands with an ink contour and a dark mouth, hanging by a short stalk from a bough drawn into the top 5 rows; specks drift at its mouth: every 0.4 s one 1 px speck (ink `0b0814` and bone `efe6c4` in turn, so one of each reads on sky and on dirt) flies from the mouth to a point 4-8 px below or beside it over 0.8 s, `KIND_GRIT` of the hive's own `KimeriaParticleSystem` (24), seeded from the object's position. No scenery hangs from a bough, has an ink contour or moves: it reads as active |
| At-rest cue | none needed |
| Reads from | ~320 px |
| Trigger | the level clock, `(elapsed + phase) mod period`: calm, warning, then release for the last `curtain` s of the period (as `Puffball`); no dependence on the hero |
| Telegraph (D7) | `shake1` and `shake2` in turn every 0.1 s (the body sways 1 px under a still bough; `shake2` shows stinger tips in the mouth); specks gather: every 0.05 s one flies from a 10 px ring into the mouth over 0.3 s |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s, taken from the calm; the release starts at the same point of every cycle, assist or not |
| Lethal part | a falling band 8 px wide (the drawn width of `trap-stingers`) centred on the hive, from the mouth (object top + 26) to the first solid under it (any Collisions block but quicksand; a ladder is not solid). Stingers leave for `curtain` s (1.0) and fall at 240 px/s: the band runs from `mouth + 240 max(0, s - curtain)` to `mouth + 240 s`, cut at the solid (`s`: seconds since the release began). Every height is lethal for exactly `curtain` s, d px below the mouth from d / 240 s after the release. A release that would have begun before the room started counts from its start. The hive never hurts |
| Counter | free: pass in the calm (on open floor the 8 px band plus the 11 px body is about 0.2 s of walking); on a ladder under it, start the climb right after a curtain has passed the foot (a 112 px ladder under a 198 px band with a 4.5 s period: a 2.94 s window). Optional, never required (D26): knock it down by hitting its 20×19 body box (x + 2, y + 7) with a thrown stone (free, D39; from the floor it is a jump-throw, a standing throw rises only about 10 px) or a fireball (one charge, spent). It falls harmlessly and lies split on the solid, the bough is left `bare`, the specks scatter, no stinger leaves after the hit; those already falling finish their fall |
| Reset | retry, Restart Level and room re-entry rebuild it hanging at the authored phase (as the puffball; no saved room state) |
| Taught in | `forest-1-hive` (after `forest-1-guard`): first over open floor, seen raining from the entrance, then over the ladder to the exit ledge; the stone by the entrance offers the knock-down |
| Load checks | `HangingHive.fromMap` and `Level.hiveFor`, at load, for every room: only `period` (default 4.0), `phase` (0), `curtain` (1.0), finite; 24×32; a solid under the mouth in its room at least 32 px down; `period >= curtain + depth / 240 + 1.2 + 0.8`; no `Actionable`, `Escalator` or `FallingPlatform` in the band; `Level.requireSafeSetting` (not over the entrance, exit or a checkpoint). Mirrored in `check_tiled_links.py` (`hive_problems`) |
| Tests | `HangingHiveTest` (`hive-test.tmx`), `Forest1HiveTest` (the room), `AllocationBudgetTest.hangingHivesCycleAndOneIsKnockedDown`, `test_check_tiled_links.HangingHiveLinksTest` |
| Art and sound | `trap-hive` 24×32 × 7 (rest, shake1, shake2, release, bare, falling, fallen; grid `art/objects/forest-traps/hive.grid`) and `trap-stingers` 8×16 × 2 (fall1, fall2, drawn a row at a time down the band so the darts move with it; `stingers.grid`), authored sprites; the specks are particles. Palette: ink `0b0814`, bough `4a2e1c`/`8a5a30`, paper `d0c294`/`9a8660`/`5a4a34`, bone `efe6c4`/`a89c78`; no accent hue. Active tier, every frame gated where it is drawn in `forest-1-hive` (D76): hive frames 0.450 at worst, falling 0.438, fallen 0.343 (drawn on the floor line, over the floor tiles), stingers 0.346. Sounds `hive_buzz` (warning), `stinger_rain` (release), `hive_fall` (knock-down), heard only while the hive or its band is in the camera view. Consequence (D93): `stinger_rain`, `hive_fall` |
| Decision | D90 |

### Ember fissure (`EmberFissure`)

Approved by the author on 2026-11-17 ([#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366),
[D91](DECISIONS.md#d91-the-ember-fissure-a-clocked-fire-jet-from-a-floor-crack-taught-in-forest-2-366-2026-11-17)).

| Field | Value |
| --- | --- |
| Family | rhythm |
| Visibility (D71) | VISIBLE |
| At rest | frames `rest1`/`rest2` of `trap-fissure` (16×12, top = floor line − 4: four rows of crust stand on the floor, eight cut into it): a low crust of ash with a pale sky-facing rim (`d8d6e8`) split by a slit glowing dull red, pulsing red ↔ red-orange every 0.5 s. It moves, sits on the floor line where no scenery is drawn, and carries the ember accent no scenery has (D76) |
| At-rest cue | none needed |
| Reads from | ~320 px: the pulse and the pale crust over the floor line; the jet itself is seen across the screen |
| Trigger | the level clock, `(elapsed + phase) mod period`: calm, warning, then the jet for the last `jet` s of the period (as the hive); no dependence on the hero |
| Telegraph (D7) | `warn1` for the first half of the warning (the slit orange), `warn2` for the second (the crust lips lift 1 px, the slit turns yellow, their inner edges lit); sparks rise: every 0.08 s one 1 px spark (`ffd23a`, `f07a1a`, `fff4b0` in turn) climbs 8-24 px from the slit over 0.4 s, `KIND_GLOW` of the fissure's own `KimeriaParticleSystem` (24), seeded from its position; the rumble sound |
| Warning | `TrapWarnings.DEFAULT_WARNING_SECONDS`: 0.8 s / 1.2 s, taken from the calm; the jet starts at the same point of every cycle, assist or not |
| Rhythm | one cycle: **calm**, **warning** (0.8 / 1.2 s), **jet** (`jet`, default 1.0 s: the top rises in 0.1 s, holds, sinks back in the last 0.2 s). Defaults: `period` 3.5 s, `phase` 0, `height` 96 px: calm 1.7 s (1.3 s with the assist), the way open (calm + warning) 2.5 s with or without the assist. Load rule: `period >= jet + 1.2 + 0.8` (the assisted warning and at least 0.8 s of calm), so 3.0 s at the default jet. A cycle whose warning would have begun before the room started stays calm, so every jet is announced in full |
| Lethal part | world Y grows downward. A column 8 px wide centred on the fissure (object x + 4 to x + 12), from the floor line up to the jet's top: `floor − height × f`, f rising 0 → 1 in 0.1 s, 1 through the hold, 1 → 0 in the last 0.2 s. It is lethal exactly where it is drawn. Embers pass through a one-way `Platform` (a branch); a solid block above cuts the column at its bottom edge. The crust itself never hurts |
| Counter | free: walk past in the calm or the warning (the 8 px column and the 11 px body are 0.19 s of walking); wait beside the column; on a branch it pierces, stand clear of its 8 px or cross between jets. Not jumpable at the default height (96 px, the jump about 58 px plus the 22 px body). No ammunition answer: a fireball or a stone passes through and does nothing |
| Reset | retry, Restart Level and room re-entry rebuild it at the authored phase (no saved room state); pause freezes it (the clock advances only in `update`) |
| Taught in | `forest-fissure` (`forest-2`, after `forest-walls`): seen jetting from the entrance over open floor, then under a one-way branch on the step to the exit ledge, which it pierces; `ForestFissureTest` |
| Load checks | `EmberFissure.fromMap` and `Level.fissureFor`, at load, for every room: only `period` (3.5), `phase` (0), `jet` (1.0, 0.6-1.5), `height` (96, a multiple of 16 from 48 to 128), finite; 16×12; the rhythm rule above; a solid floor (not a one-way platform or quicksand) whose top is the floor line under all 16 px (split blocks merged, 0.5 px tolerance); a solid block above may cut the column to no less than 32 px (`ceilingAbove`); no `Actionable`, `Escalator` or `FallingPlatform` in the column; `Level.requireSafeSetting` (the column inside the room, never over the entrance, the exit or a checkpoint). Mirrored in `check_tiled_links.py` (`fissure_problems`) |
| Tests | `EmberFissureTest` (`fissure-test.tmx`: open floor, then the branch step): the warning equals the shared warning (0.8 and 1.2 s); every phase boundary; the column's rise, hold and sink as a rectangle against the floor line; lethal only inside the 8 px column, never the crust; no unannounced jet at the room start; the sounds once per cycle and only in the camera view; the falling sparks and the spent frame after every jet; a walker who goes when a jet ends passes at every 0.05 s offset with and without the assist (a blind walker dies at 23 of 71 offsets); a climber who goes when a jet ends crosses the branch to the ledge at every offset with and without the assist (3.75 to 6.73 s after the start delay); waiting on the branch beside the column is safe; retry and Restart Level; pause; determinism and the spark pool; the load rules. `AllocationBudgetTest.emberFissuresCycle` 1.1 B/frame (ceiling 8). `test_check_tiled_links.EmberFissureLinksTest` |
| Art and sound | `trap-fissure` 16×12 × 6 (`rest1`, `rest2`, `warn1`, `warn2`, `vent`, `spent`; grid `art/objects/forest-traps/fissure.grid`) and `trap-ember-jet` 8×16 × 4 (`jet1`, `jet2`: the column tile, drawn a row at a time up from the floor line and swapped every 0.05 s so the flame climbs; `top1`, `top2`: the 8 px tip; `ember-jet.grid`), authored sprites; the sparks are particles. Palette: ink `0b0814`, ash `d8d6e8`/`9a97ad`/`5b5870`, ember `7a1a14`/`b3201a`/`d8461c`/`f07a1a`/`ffd23a`/`fff4b0` (the stump's family: dull red at rest, orange and yellow only in the warning and the jet); no new hue. Active tier over the night `2c3f66` (D92), measured by `check_contrast.py` at the two fissures of `forest-fissure`: fissure 0.377 at worst, jet tile 0.349, tip 0.398 (floor 0.28), with no moon rim: the pale ash rim and the flame already read on the night. Sounds `fissure_rumble` (warning) and `ember_jet` (the jet), heard only while the fissure or its column is in the camera view (SOUND_DIRECTION). Every jet, hit or miss, also leaves seven sparks falling from the upper column as it sinks and the sooted `spent` frame for 0.4 s. Consequence (D93): `ember_jet`. The sounds are not yet auditioned and the room not yet played by the author ([#463](https://github.com/jlogicgames/edgard_in_kimeria/issues/463)) |
| Decision | D46 (rhythm rules), D91 |

## Proposed traps (D71, not built)

Chosen by the author on 2026-11-16, each with its own issue and an approved card before code. All four are built and
their cards are above: the trip-line deadfall ([#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363)), the
creeping bramble ([#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364)), the hanging hive
([#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365)) and the ember fissure
([#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366), chosen on 2026-11-17).

**Rejected (author, 2026-11-16):** a **snap burr**, a huge open seed husk that snaps shut, because D42 rejected a
carnivorous plant and this is too close to one.

## Maintenance

When a cue issue lands, change the catalogue row's cue from `pending #NNN` to the cue, update the card, and keep the
visibility word (a DISGUISED trap with a cue stays DISGUISED). A new trap adds its card and its catalogue row in the
same change as its `case` in `Level`; `TrapCatalogTest` fails otherwise.
