# Rooms in Tiled

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

The teaching-layout revision `9d5e630` changes the current Forest placement after
the earlier test-version acceptance. Its automated route checks pass, but the new
spacing still needs the targeted human readability check recorded below. Earlier
author acceptance must not be read as evidence for a later map layout.

The MVP first level is `forest`; the second is `forest-1`. A level is one TMX map
containing an ordered chain of rooms. `forest` has three
rooms: the original 1280 px course, a 672 px forest course with an ascending
optional coin route, and the authored 448 px boss chamber. The canopy RoomExit
leads into forest-boss. Boss death and the automatic victory walk lead to the post-boss healer dialogue (#86);
its final advance completes the level and starts normal next-level loading.
`forest-1` is the unmarked, single-room second level; its checkpoint leads to the
ending scroll and then the menu, with the save cleared. See [MVP.md](MVP.md). Replay budgets and measured
routes for #70 are recorded below.

For the future stage → levels → rooms hierarchy, see the
[full campaign design](GAME_DESIGN.md#campaign-structure). This document describes
current map authoring and retry behavior.

## Authoring

Add an object layer named `Rooms`. Coordinates are Tiled's top-left, Y-down
coordinates, including any layer offset. In that layer:

- `Room`: a rectangle with a unique, nonempty name. The order of **Room objects in
  the layer's object list** is the traversal order; it is not sorted by X or ID.
- `RoomStart`: a point with the same name as its `Room`. Its position is the
  player's top-left spawn position. Each room has exactly one entrance. Leave a
  safe 48-by-48 area for the hero and ground under its feet.
- `RoomExit`: a rectangle with the same name as its `Room`. Every room except the
  last has exactly one. Overlapping it with the player's hitbox starts a forward
  transition to the next room. The last room has no RoomExit; place the existing
  `Checkpoint` in `SpawnPoints` there to finish a level without a boss. A boss level
  completes only after its victory walk; Checkpoint contact and F5 are blocked throughout it.

Bounds must be positive, inside the map, and must not overlap. Entrances and
exit rectangles must fit inside their room. Duplicate, missing, orphaned, and
unknown room markers fail loading with a diagnostic instead of changing gameplay
silently. An entirely absent or empty Rooms layer means one room covering the
map, using the existing Player spawn.

SpawnPoints and Collisions objects belong to the room containing their initial
rectangle's centre (left/top inclusive, right/bottom exclusive). Keep collision
shapes, patrol routes, and interactive objects within their room. Moving objects
retain this ownership. Trigger targets must be in the same room; cross-room
links are rejected. Atmosphere (sky, rain, fireflies; fog is menu-only) is decorative and
continues across retries. Rooms may span several screens.

## Transition and retry

Room exits are forward only. Before the boss, the healer can teleport the hero back to
the first entrance for another forward collection pass (#84). Seal physical boundaries with terrain so the
player reaches the exit trigger rather than walking into an inactive room.
The camera stays inside the current room; if the room is smaller than the
viewport, it is centred on that axis.

Entering RoomExit freezes the world, hero animation, and movement input.
The entire scene fades to black over **0.25 seconds**, then the next room and a
fresh idle hero are placed at its entrance and the camera relocates while the
screen is fully black. A fully black frame is always rendered, even after a
long frame. The scene fades in over **0.25 seconds**, then play resumes. There
is no hero disappearance/appearance animation, inter-room camera pan, loading
screen, or level-save write. Timing uses real time, independent of death slow motion.
Pause freezes the fade; Resume continues it. Restart Level and leaving gameplay
cancel the transition.

Death keeps the existing slow-motion/fast eased camera return and respawn
animation. It rebuilds only the current room's enemies, items, bombs, triggers,
mutable walls, moving platforms, and falling platforms from the already-loaded
TMX descriptions, discarding that attempt's queued effects. Passed rooms retain their enemy/trap progression without updating. Future rooms start their
first attempt only when entered. A healer collection return rebuilds traversal objects while
retaining secured pickups, enemy state and trap state; see [HEALER.md](HEALER.md) for the
first-visit/revisit death rules and the complete reset/retention policy.

Collected object IDs remain provisional until leaving the room, reaching
level completion or entering/returning from the healer; death before then restores those objects. The current coin
counter likewise rolls back to its room-entry value, preserving previous rooms'
coins. Artifact sprites and rollback use this same bookkeeping; the optional healer bargain
consumes the forest recipe once (#84), keeping collection identities separate from inventory.
Persistent artifact saves are not implemented (D6). Restart Level resets all rooms and the level-entry coin count.
Saved progress remains per level (D8). A dev-mode TMX reload keeps the player
in the room containing its position; if that position was removed from the map,
it falls back to the first entrance.

## Replay budget (#70)

The current author-approved working hypothesis is **about 20 seconds of mastered
traversal between recovery points**, plus a separate **about 60 seconds of first-time
learning until the first successful exit** (2026-10-20, D19). The latter includes
observation, planning, failed attempts and the successful traversal; do not add
20 seconds on top. This replaces D18's 40-second replay target. Measure required
and all-pickup routes separately. Do not pad a shorter room to reach 20 seconds.
These are level-authoring targets, not player deadlines: exploring,
learning, and taking longer never cause
a failure or penalty. A room may span several screens. Split or rework an overly
long replay; do not exempt a long segment by calling it optional.

The measurements below retain their original 20/30-second criteria as historical
evidence. `RoomReplayTest` still uses those original limits on the existing maps;
this documentation update does not claim that the test constants or maps changed.
Synchronize the measurement checks when implementing new campaign rooms.

This mastered-route budget is different from first-time learning with observation
and failed attempts. The future campaign's five-room/five-level model and 20/60
timing pair are planning assumptions, not verified reference-game averages. The
former 3–5-minute first-learning hypothesis is superseded. See
[pacing and measurement](GAME_DESIGN.md#pacing-and-duration). Record both metrics
separately before changing a room boundary or the replay target.

For first learning, observe a player unfamiliar with the room and record active
time from usable entrance input to the first successful exit, including retries.
Record deaths, required/optional routes and presentation downtime separately.
Sixty seconds is a hypothesis for an ordinary room, not a time limit or a required
result for every player, boss or optional challenge.

Measure from the first frame in which the player can act at the entrance to
first overlap with RoomExit or the final Checkpoint. Include deliberate waiting,
combat, interaction and backtracking in **real elapsed play time**. The historical
2026-10-14 results below included bat-induced slow motion; the current gameplay
does not. Exclude loading, pauses, the death/respawn presentation before input
returns, room fades, and the final Checkpoint's disappearance delay. Do not
include failed attempts or initial discovery in a mastered-route measurement.
Record collection detours separately instead of adding them to the required route.

### Reference measurements — 2026-10-14

The shipped maps at revision `f174d8d` (PR #76) were exercised with the production
`Level.update` and `Player.update` logic by `RoomReplayTest`. The driver sends
ordinary A/D/J/K keyboard input at 60 frames per real second. Enemies, lethal
contact, bombs, quicksand, moving/falling platforms, triggers, and 0.5-speed bat
proximity remain enabled. Invulnerability and trap assist are off. Each route
succeeds on a fresh attempt and repeats twice after `resetCurrentRoom`, with the
same elapsed time and no deaths. Both collection routes collect all three room
coins. The `forest-1` course has no collectables, so it needs no separate detour.

| Level / room | Width | Required route | All room coins | Result |
| --- | ---: | ---: | ---: | --- |
| `forest-1` / unmarked room | 640 px | 5.32 s | None | Within 20 s |
| `forest` / `forest-entry` | 1280 px | 17.60 s | 20.27 s | Within 20 / 30 s |
| `forest` / `forest-canopy` | 672 px | 5.17 s | 5.47 s | Within 20 / 30 s |

The revised canopy is 608 px shorter than its previous 1280 px layout (47.5%).
Its required route has no bomb or quicksand; the ascending platforms and their
optional pickups remain. The first two rooms already meet the budgets and need
no additional split for replay cost. The author reviewed the earlier test version
in play (2026-10-20, #73/#75). The later teaching-layout spacing has a separate
uncompleted human check; see
[Teaching order and walkthrough (#73/#75)](#teaching-order-and-walkthrough-7375).

The required routes run forward, jumping obstacles and hazards, using normal
sword attacks near grounded enemies and wall jumps at tall walls. In `forest-1`,
K activates the wall trigger before reaching the level exit. The forest-entry
collection route waits 40 frames (0.67 s, included in its time), takes the coin
behind the entrance, follows the raised route to the upper coin, then returns
from about x=400 to x=330 for the ground coin before continuing to RoomExit.
The canopy collection route follows the ascending platforms before dropping
back to the ground coin and Checkpoint. The exact press thresholds and route
control are executable in `RoomReplayTest`; they are a reference successful
route, not a claim that beginners will finish at these speeds. The fresh collection
measurements assert coins, not a complete Chicken/Key collection route. Artifact
return checks below are separate and start from secured, seeded state.

Reproduce all five measurements with:

```sh
./gradlew :core:test --tests '*RoomReplayTest'
```

These are **headless gameplay measurements**, not a recorded human playtest.
Texture/font rendering and camera drawing are omitted; an alias of the exact
`forest-1.tmx` file skips only its decorative sky/rain, which have no collision,
AI, or movement role. Map contents and all gameplay objects are retained.
This measures the replay cost at 60 fps. The author has since played the rooms by hand and
accepted visual readability and input comfort for the test version (2026-10-20, #75);
behaviour at other frame rates is deferred to [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).
When map geometry or gameplay timing changes, rerun the affected automated routes
and record new timings as part of that change.

Measured map SHA-256 values:

- `forest.tmx`: `bf227d733af41d97f575b1d16bcebb6cf408590b26f19510942ffc19f2b88f96`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

## Completed test-version review and historical evidence

### Artifact checks (#72)

The author accepted the existing Chicken and Key positions as final for the MVP on
2026-10-19: Chicken is object 94 at (448, 240), Key is object 95 at (1728, 208) in
`forest.tmx`. Keep these placements; no relocation or additional artifact challenge
paths are planned for this MVP. Collection remains optional for completion.

The pre-release artifact checklist is maintained in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#rooms-pickups-and-traps).

Verification on 2026-10-15: automated tests cover gameplay contact, duplicate pickup,
two-frame animation cadence, no/partial/full collection, room rollback, securing items
at both room and level exits, cross-level identity/retention, and level restart. The full
secured forest set gives no automatic combat bonus. The optional consumed elixir
instead multiplies weapon damage by 10 (#82/#83/#84). A hidden desktop GL render check confirmed upright sprites, both animation frames,
and the single HUD row after a six-digit coin count. This is an isolated rendering check;
full traversal/readability was accepted in the author's human playthrough (2026-10-20).
Current boss validation is recorded in [BOSS.md](BOSS.md).

### Bomb warning checks (#71)

The pre-release bomb checklist is maintained in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#rooms-pickups-and-traps).

Verification on 2026-10-14: automated tests cover standard/assisted timing,
settings changes during warning, repeated contact, square corners/edges, tint
alternation and batch color restoration, damage at detonation, and active/queued
effect cleanup on retry. Headless tests alone do not establish in-game warning
readability; the author's playthrough of the rooms (2026-10-20) accepted it for the
test version.

Explosion shader follow-up on 2026-10-14: retain the original upward fire/smoke
shapes and palette. Place the origin at 85% of the 64x64 quad's height, aligned
with the art-sized hitbox's bottom in world space, excluding the sprite frame's
transparent bottom row. Fade just above that baseline, with no visible pixels
below it. This leaves room above for rising smoke. Use soft border transparency and a fade to zero before
removal. The cosmetic quad is shifted upward; the centered lethal square remains
64x64. A WebGL render check with the actual `quad.vert` and `bomb_explosion.frag`
sampled 61 lifetime positions: compilation/linking succeeded, no GL errors,
transparent outer edges throughout, and a fully transparent final frame.
An early/middle/late visual comparison confirmed the original plume rising from
the lower part of the quad. The noise loops and draw size are unchanged. This is
an isolated shader check, not a full gameplay playtest.

Actual desktop capture follow-up on 2026-10-14: running `forest` at a 0.001 s
world step reproduced the below-ground explosion at approximately 0.05 s into
the effect. The shader-only check missed libGDX's `TextureRegion` adjustment of
a 1x1 texture to UV 0.25..0.75. That moved the 0.85 ground origin outside the
quad. `Shaders.whiteQuad` now explicitly restores full 0..1 coordinates, keeping
the Y-down flip. A second desktop capture at the same effect time shows fire
above the ground at the original bomb location. Actual GL texture coordinates
were checked at runtime; `ShadersTest` also guards against the 1x1 inset.

### Room transition checks

The pre-release transition checklist is maintained in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#rooms-pickups-and-traps).

## Teaching order and walkthrough (#73/#75)

Rule for the two levels: teach each of stomp, sword, falling platforms and bombs where
it is the only new thing on screen, on forgiving ground and without a required timing
test, then combine. `forest` (level one) teaches; `forest-1` (level two, the finale)
combines. Optional routes stay separate from required progression; nothing here is
tuned around coins, and no coin or artifact was added, moved or removed.

Current teaching-layout identity (`9d5e630`): `git hash-object` of `forest.tmx` is `619bf64b559a`, of `forest-1.tmx`
`44fbc6aa95c3`. Positions are map pixels (16 px per tile; SpawnPoints carry a -16 px
layer offset that this text already includes in the visible position).

### `forest` / `forest-entry` (required route, left to right)

| x | What appears | Role |
| ---: | --- | --- |
| 64 | Start. The first mob's zone begins 4 tiles ahead. | Safe entrance |
| 192 | YellowMob (id 50), patrol 128–208, beside a one-tile step | **Stomp, alone**. Jump on it from above, or K. |
| 240–320 | Quicksand, vertical Bat (272); optional one-way steps to the coins/Chicken | Hazard + Bat; not a taught mechanic |
| 512–608 | Horizontal Bat (patrol now ends at about 608) and a second quicksand | Same, repeated |
| 656 | **Bomb** (id 54), moved from 128 | **Bomb, alone**: flat ground, 0.8 s warning (1.2 s with Assist), 64 px blast |
| 832 | YellowMob (id 16), aggro zone 752–896 | Stomp repeated, first light combination: leave the blast, then meet the mob |
| 912, 992 | Two tall walls | Wall jump |
| 1136 | RedMob (id 60), zone 1056–1216, just before the exit | **Sword, alone**: wind-up is visible; K or stomp |

The bomb used to sit 4 tiles from the start inside the first YellowMob's patrol, so stomp
and bomb were the first two things met and overlapped. The bomb is now about 470 px
beyond the first mob and the bat patrol and the second mob's zone were pulled back so that
the blast square (about 624–688) and a running escape of about 64 px overlap neither.

### `forest` / `forest-canopy`

| x | What appears | Role |
| ---: | --- | --- |
| 1344 | Start | Safe entrance |
| 1440–1488 | One-tile step; RedMob (id 68), zone 1408–1488 | Sword again, now beside terrain |
| 1488 | Optional one-way platform | Start of the optional climb |
| 1568 | **FallingPlatform** (id 115, was a fixed platform) holding coin 87 | **Falling platform, alone**: 1 s warning (1.5 s with Assist). Ground is 80 px below; isolated from bombs and nearby walking enemies at spawn; a bad landing costs only the climb |
| 1648–1728 | One-way platform and the Key | Optional reward |
| 1760 | Vertical Bat near the Key | Optional hazard |
| 1872 | RoomExit | |

The collapse is one-way for that attempt, so a player who lets it fall without
reaching the next platform loses the optional Key/coin ascent until death, a room
retry or the healer's collection return rebuilds it (D7). The required route never
needs the platform. Every optional pickup remains reachable.

### `forest-1` (level two, unchanged)

Falling platforms (x 96–224, y 272), the RedMob patrol (192–512) that covers them, the
escalators and the trigger-opened wall at 480–528 are the first time those pieces
are combined. The falling platforms are now a second sighting rather than the first.
By the map geometry there are 32 px between the ground and the platforms and the hero's
hitbox is 22 px, so the course can probably also be walked underneath. This is read from
the map, not exercised in play; the author did walk over the platforms without trouble.

### Evidence and limits

The teaching-layout implementation recorded 131 passing core tests. `RoomReplayTest`
re-measured every route on this layout
with real input and hazards on, three identical attempts each: `forest-entry` 16.27 s
required / 20.00 s all coins, `forest-canopy` 5.00 s / 5.00 s, `forest-1` 5.32 s, and the
collection returns 14.78 / 14.25 s, unchanged from the table above. The new test
`forestIntroducesStompBombAndFallingPlatformOneAtATime` fails on the previous layout and
guards the order (a YellowMob before the bomb), the 150 px gap between the bomb and any
walking enemy, no falling platform in the entry room, and a lone falling platform
without a bomb or walking enemy nearby in the canopy.

Not established: that the new spacing feels readable. The targeted human checks
for the bomb runway, shortened bat patrol, canopy collapse and understandable
failure are tracked in the active
[pre-release room checklist](PRE_RELEASE_TODO.md#rooms-pickups-and-traps), with
Assist off and on (#75). Scripted input is not that evidence.

## Forest boss validation (#73/#75/#83)

The historical measurements above describe the two traversal rooms before the boss room
was authored. Their replay routes still end at their actual RoomExit; they do not measure
the boss fight. The former canopy Checkpoint is replaced by RoomExit in the current map.
For boss-specific rule teaching, escape, retry and victory checks, use [BOSS.md](BOSS.md).
Scripted movement and automated collision assertions alone are insufficient evidence for
discoverability and fairness; the author's human playtest (2026-10-20) accepted both for
the test version.


## Current replay measurements after removing bat slow motion

The historical 2026-10-14 table above includes the superseded proximity slow motion.
Gameplay now runs at normal speed around bats; death still owns its separate slow-motion
presentation. Fog is created only by the menu backdrop. Enemy AI/contact, bombs, quicksand,
platforms and triggers remain enabled in the real-input replays below, with invulnerability
and assist off. Each route succeeded on three consecutive identical attempts under the
then-current 20 s required-route / 30 s collection-route limits. Those results are
unchanged; the current authoring hypothesis is about 20 s mastered / 60 s first
learning as specified above. The recorded runs do not measure first learning.

These timings were rechecked on the teaching layout during the merge review on
2026-10-20. Current map SHA-256 identities (distinct from the historical values above):

- `forest.tmx`: `83adfbbf997718092e33c8b4ac952483f4edad244d1864f26afb4f735de4436d`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

| Level / room | Required route | All room coins |
| --- | ---: | ---: |
| `forest-1` / unmarked | 5.32 s | No coins |
| `forest` / `forest-entry` | 16.27 s | 20.00 s |
| `forest` / `forest-canopy` | 5.00 s | 5.00 s |

At normal speed, the entry required route uses a fresh jump before the second wall. The
coin route pauses beside the upper coin for a ground jump into it from below the one-way
platform, after securing the ground coin. These actions and their elapsed time are included
in `RoomReplayTest`; no hazards or pickups are excluded. Collection returns for missed
Chicken/Key still pass through both rooms three times without farming (14.78/5.13 s on the
first return, 14.25/5.00 s later). Earlier author acceptance does not establish
readability of the changed teaching layout; its targeted check remains deferred.

## Merge review — 2026-10-20

The room markers, teaching-layout object IDs/positions and current map hashes were
checked against the TMX files. No conflict markers remain. The focused
`RoomTest`, `RoomReplayTest` and `ArtifactInventoryTest` run passed **34 tests**:
19 room lifecycle, 10 replay/teaching and 5 inventory tests, with no failures or errors.
Required/coin-route and collection-return timings above were reproduced.

The existing wall-clamber/jump in `Player` and fresh wall-jump presses in the replay
driver confirm that the wall-jump references in the route descriptions are current
behavior. Its future role in the campaign is a design question, not a missing feature.
This review changed documentation only. It did not rerun the full core suite or
perform a new visual/human playtest; it does not close the deferred release checks.
