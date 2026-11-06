# Rooms in Tiled

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

The teaching-layout revision `9d5e630` changes the current Forest placement after
the earlier test-version acceptance. Its automated route checks pass, but the new
spacing still needs the targeted human readability check recorded below. Earlier
author acceptance must not be read as evidence for a later map layout.

The MVP first level is `forest-1`; the second and last is the boss level `forest-3`
(intended Forest order: `forest-0` → `forest-1` → (`forest-2`?) → `forest-3`, [D32](DECISIONS.md#d32-forest-level-order-and-identity)). A level is one TMX map
containing an ordered chain of rooms. `forest-3` has three
rooms: the original 1280 px course, a 672 px forest course with an ascending
optional coin route, and the authored 448 px boss chamber. The canopy RoomExit
leads into forest-boss. Boss death and the automatic victory walk lead to the post-boss healer dialogue (#86);
its final advance completes the level; as the last level it opens the ending scroll and then the menu, with the save cleared.
`forest-1` is the unmarked, single-room first level; its checkpoint leads to `forest-3`. See [MVP.md](MVP.md). Replay budgets and measured
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

Every playable level must have at least one positive-size rectangle with
`type=Deadline` in `SpawnPoints`.
It is a lethal trigger volume: player-body contact causes the same immediate room
retry as falling into a pit, without death slow motion. Use it to close off-map
fall paths and keep it clear of safe routes. A Deadline is level-wide rather than
room-owned, so it may span several rooms and extend outside the map.

Other SpawnPoints and Collisions objects belong to the room containing their
initial rectangle's centre (left/top inclusive, right/bottom exclusive). Keep
collision shapes, patrol routes, and interactive objects within their room.
Moving objects retain this ownership. Trigger targets must be in the same room;
cross-room links are rejected. Atmosphere (sky, rain, fireflies; fog is menu-only)
is decorative and continues across retries. Rooms may span several screens.

### Hint zones (#195, D33)

An optional object layer `Hints` holds `Hint` rectangles (each inside one room). Properties:
`message` (a `Msg` key such as `HINT_TUTORIAL_WALK`, shown at the zone's horizontal centre, 45% down it, after a short 0.35 s dwell; it then lingers 2.5 s after the hero leaves, see D33) and/or
`controls` (bool, enables the pickup/lever/ladder instructions of D24). The hero's body centre
inside the zone shows the hint; the `message` and the live `controls` label are separate channels drawn together, neither hiding the other (D33, #211); nothing is stored, so it repeats on every retry and return. No
layer means no hints. `forest-0` (`assets/tiles/forest-0.tmx`) has six rooms (lever room 576 px; since #235/D48 the ladder room 896 px, wall room 784 px, platform room 800 px and stone room 736 px, each with one trap; traps room 720 px since #200) named
`forest-0-lever`, `-ladder`, `-wall`, `-platform`, `-stone`, `-traps` (room property `lesson`);
each starts as a floor strip and two side walls (all six rooms now have their lessons, see the recipe below). Lessons add their objects, collisions and zones.

<a id="tutorial-lesson-room-recipe-186"></a>

### Tutorial lesson room recipe (#186)

The lever room is the sample. Later lessons (#196–#200; all five are built) copy it:

1. **Room.** Fill the existing `forest-0-<lesson>` room (the lever room is 576 px, the ladder room 896 px, the wall room 784 px, the platform room 800 px, the stone room 736 px and the traps room 720 px; about 500–720 px suits one object, a trap and its hint leads add 140–210 px (D48); every extra empty stretch is walking time with nothing to learn; floor strip y=320, side walls, RoomStart,
   RoomExit; no enemies, and hazards only in the traps lesson (D40), where the last room ends at the level's `Checkpoint` instead of a RoomExit). Add only that lesson's objects/collisions. One new element per room, except the four middle rooms of `forest-0`, which also hold one forest trap each after the lever (author decision D42, [D48](DECISIONS.md#d48-traps-in-the-tutorial-rooms-one-new-trap-per-lesson-room)): put the trap first (entrance, trap, lever, object): its hint covers the whole crossing, the next text starts beyond the trap's column, each text has at least about 160 px from its zone start to what it warns about (~0.9 s of full text, `HintLeadTest`), and the lesson's object stays within 316 px of the lever's reach (the lesson tests assert one screen), which only the trap-first order allows.
   **Ceiling:** do not author one. `Level` closes every room from above with an invisible 64 px slab whose underside is the
   room's top edge (y=0), so the clamberable side walls cannot be wall-jumped over the top ([D38](DECISIONS.md#d38-every-room-has-an-invisible-ceiling), #215).
   Keep the side walls and any ledge walls inside the room (`RoomCeilingTest` checks every room of forest-0, forest-1 and forest-3).
2. **Object + link.** Put the mechanism in `SpawnPoints` as type `Actionable` with property `type` = `Torch` or
   `Wall` (`check_tiled_links.py` rejects anything else; a moving platform is instead a SpawnPoints `Escalator` with `isVertical`, `offNeg`/`offPos` in 32 px units and `running`=false, linked the same way; a Torch starts dark with `Intensity` = 0, lit with a
   positive value; a Trigger that a *thrown* carryable should operate, not the hero's Up + Action, gets the bool property `throwActivated` = true, see [D39](DECISIONS.md#d39-the-tutorial-thrown-stone-lesson-rule), and must stand where the hero cannot overlap it; a Stone with the bool `respawn` = true returns to where it was authored when it falls out of the room or hits such a lever). Add a `Trigger` with property `target` (type *object*) = the mechanism's id. Names are written by
   the Tiled extension `tools/tiled-extensions/link-names.js` as `Torch [40] ← 41` (own id, then the id of each
   trigger pointing at it) and `Lever [41 → 40]` (own id, then its target); do not hand-craft other formats. Both
   objects stay in the room, the effect is visible within one screen of the trigger, and `nextobjectid` is
   advanced. Stand mechanisms on the floor like the lever (torch y = 304 for a 16 px object over the y=320 floor)
   unless the lesson needs height. Run `python3 tools/check_tiled_links.py`.
3. **Hint zones** (layer `Hints`, full room height 0–368, inside the room). Horizontally, a room's usable range is
   between its side walls (x 16–560 for the 576 px lever room; add the room's x offset for later rooms).
   One `controls` zone over that whole range gives the live "Up + Action: Turn on - Lever" label near the lever; and
   `message` zones tile the range so every standing position belongs to a hint zone (the text appears after the 0.35 s dwell, not instantly; the first message of a room needs the dwell plus a 0.45 s fade-in, so about 0.8 s). **Place each lesson zone so a continuous walk gives its message about 1 s fully visible before the object is in reach** (`HintLeadTest`; at 100 px/s that is roughly 180 px between the zone start, or the room start for the first message, and the object's reach, so lengthen the approach rather than the rest of the room), because the text needs the dwell and fade-in to be readable, and the HUD keeps it up for 2.5 s after the hero leaves and at least 0.7 s before another message replaces it (D33); a zone that starts at the object is read only after the hero has passed it. **Message zones are position-only:
   they know nothing about object state or whether the player already acted, so every lesson text must be true in every
   state** (lever off, on, never touched). Do not write "the torch is dark" or "works both ways" on a position-only
   zone; say what the object does ("This lever lights the torch and puts it out") and where the exit is. Messages are
   Msg keys named `HINT_<LESSON>_*`, in all five catalogs, without key names (devices differ), at most two lines at
   200 px. The HUD draws a message hint about 45% down the zone and shows it after a 0.35 s dwell with a fade-in and
   overshoot slide (D33), so keep zones wide enough (about 200 px or more) for the dwell to matter; add them to the layout assertion in `LocalizationLayoutTest`.
4. **Rule.** Decide and document, as D34 does: toggle or one-shot, what a retry restores, and why the exit is
   reachable in every state (D26). Prefer reversible, route-independent first lessons.
5. **Tests** (copy `LeverLessonTest`): headless `Level("forest-0")` driven by keyboard input — walk to the object,
   operate it, assert the linked effect; assert the exit (`level.atRoomExit()`) is reached from every state
   (off, on, after `resetCurrentRoom`, new `Level` for Restart Level); assert the exact zone message (`zoneHint()`) at every standing x with the object in each state and after
   retry, and that the live label (`contextHint()`) shows beside it, never instead of it (#211); update `TutorialLevelTest` if new object types appear; measure the mastered route.
6. **Record.** Mastered time (target about 20 s per room is a hypothesis; lever room is about 4.3 s after #209; after D48 ladder room about 8.8 s, wall room about 6.5 s, platform room about 11.9 s, stone room about 6.9 s (8.5 s setting the stump off from afar), traps room about 6.1 s) labelled an
   *author check, not a novice playtest* (novice playtests are #129–#131), plus findings and what was not verified.
7. **Checklist:** `./gradlew :core:test --rerun-tasks`, `validateLocalization`, `validateNamedParameters`,
   `validateSpriteArt`, `python3 tools/test_*.py`, `check_tiled_links.py`, then look at the room in the game
   (`runDev`), and update ROADMAP, GAME_DESIGN (Forest status), DECISIONS and this section.

## Falling traps (#232, D45)

Authoring recipe for `FallingBranch` and `FallingStone` (rule: [D45](DECISIONS.md#d45-falling-traps-the-position-triggered-branch-and-stone)); placed in `forest-0` by #235 (D48): one branch in the wall room, one stone in the platform room.

- **Objects (SpawnPoints):** the object rectangle is the hanging position and the lethal footprint (branch 32x16, stone 16x16). Properties `zoneX`, `zoneY`, `zoneWidth`, `zoneHeight` (absolute world px, inside the same room), stone only `skin` (default `forest`). `tools/check_tiled_links.py` checks them.
- **Trigger zone vs lethal column:** the lethal column is the object's x-range from the object down to the floor. **Load checks (Java and `check_tiled_links.py`):** the zone must overlap the column's x-range and the 22 px above the floor, must not arm the trap at the room entrance while the hero stands in the column, and a stone must start at the room top or under a solid block; no lever-raised `Wall`, `Escalator` or `FallingPlatform` may stand in the fall. Start the zone **up to 16 px before the column** and let it cover the first part of it ("under the foliage"): a hero who keeps walking is clear of the column about 0.2 s before the branch starts to fall (the tested fixture: zone x 144-192, column x 160-192, hero 11 px wide at 100 px/s; with the assist 0.6 s). A zone that starts further before the column makes the pass tighter (at 32 px the margin is only about 0.05 s) because the hero has farther to run after the trigger. The zone's top may start at the object's height and must reach the floor, because the hero's body box (22 px tall on the floor) has to overlap it.
- **Minimum margin:** keep `warning - time to clear the column` at 0.15 s or more at the base value (tested: `theBranchHasMarginForANaturalWalkNotJustAnOptimalOne`). Flight time is on top of that, at least 0.3 s (the loader refuses a drop under about 41 px). Do not place a trap where the only way out of the column is blocked (a wall, a pit) within 0.8 s.
- **Branch placement:** hang the bottom of the branch at least 24 px above the floor, but for readability 48 px or more (a standing hero is 22 px tall); there must be foliage or a trunk in the tiles above it so the branch reads as part of the tree. A resting branch does not block or hurt.
- **Stone placement:** the stone starts **inside** the room, at its top edge or lower (the D38 slab above the room is solid and a stone there would not load: "outside rooms"). Show a hollow, ledge or foliage in the tiles above its column so the fall has a source. The shadow appears on the first solid below the stone's x-range, so keep that floor flat under the whole column. A drop from the room top of about 300 px takes about 0.8 s; shorter drops are harsher, so lengthen the zone lead rather than the warning.
- **Required routes (D26):** a route may pass a falling trap by walking on or by a running jump at any point of the zone; do not make a required passage that needs standing in the column. At the branch a jump in place also works if it is timed so the hero is airborne above the landed branch at impact (Jump pressed 3 to 48 frames, 0.05-0.8 s, before the impact); never rely on that window for a required route, only on walking on. The trap is single-shot: retry, Restart Level and room re-entry re-arm it.
- **Hint zone advice (used by #235, D48 found about 160 px enough):** a message zone that starts at least 160-200 px before the trap's own zone (so about 1 s of the text is visible before the trigger, see `HintLeadTest`) and says what to do, not where: for the branch "the branch falls soon after you walk under it - run on", for the stone "a shadow on the ground shows where a stone will fall". No hint text is added by #232.
- **Pause and retry:** timers tick only in `Level.update`; death rebuilds the room, so the trap is armed again with the hero at the entrance.

## Explosive traps (#233, D47)

Authoring recipe for `ExplosiveStump` (16x16) and `ExplosiveRoot` (16x24), which replace the glass-bottle bomb (rule: [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root)). Shipped placements: the stump in `forest-0-traps` and one stump in `forest-0-stone` (D48).

- **Object (SpawnPoints):** no properties. The rectangle is the sprite footprint; the trigger box is the bomb's inner box (10x13 in a 16x16 frame, 3 px in per side and 2 px down, scaled with the object); stand it on the floor (stump `y = floor - 16`, root `y = floor - 24`). **Touch** (the hero's body overlapping it) or a **hit from afar** (a fireball or a thrown stone) starts the wind-up; it lasts the shared warning, 0.8 s (1.2 s with the assist); then one 64x64 blast centred on the object, checked once; the remains stay as harmless, non-solid scenery. A retry on the first visit or Restart Level makes it whole again; leaving the room alive and returning keeps a spent trap spent (room-state retention).
- **The bypass rule (every explosive, checked at load by Java and by `check_tiled_links.py`):** keep the **jump envelope clear**: floor under the trap and 64 px either side, 96 px of free height over all of that (no roof, ledge, wall, ladder, escalator or falling platform in it, counting their travel), nothing solid in the trap's own column, all of it inside the room. That lets a hero with **no ammunition** jump over it, so it is never a one-tile corridor or the only way. The load also fails when the 64x64 blast square leaves the room or covers the room entrance, exit or a checkpoint (a hit from afar can light it while the hero waits there): keep the entrance, exit and checkpoint **at least one blast radius (32 px) plus the hero's body clear of the centre**, and for a checkpoint ideally the 64 px that `forest-0-traps` leaves.
- **Choice rule (author, D42/D47):** on a required path an explosive offers *pay with ammunition from a distance (fireball, or a thrown stone for free)* or *the riskier bypass (jump over it)*; the required route never needs ammunition (D26). The validator guarantees the bypass; the author guarantees the player can see both answers. If you also want a higher ledge route, add it as an extra: the validator does not trace routes, so it never replaces the floor bypass.
- **Fairness numbers to keep:** the blast square reaches 32 px either side of the centre, so a hero 33 px or more from the centre (body edge) is safe; the warning leaves time to step out of it (a hero who walks on through a lit stump is clear when it goes off: tested in the forest-0 room). A hit from afar gives the full warning, so a hero standing within 32 px of the centre must still step away.
- **Hint advice for #235:** a position-only zone that starts at least 200 px before the trap and says the rule of the trap (not its state), e.g. "A touched stump smokes, then explodes. Jump over it or run clear" (`HINT_TRAP_STUMP`). Where the lesson teaches the choice (the stone room in the tutorial plan), add a second sentence that a thrown stone or a fireball sets it off from afar, in all five languages, at most two lines at 200 px. Do not say "the stump is dark/lit"; zones know nothing about the trap's state.
- **Tests to copy:** `ExplosiveTrapsTest` (headless, fixtures `explosive-test.tmx`, `explosive-shield.tmx` and the `explosive-bad-*` maps: `corridor`, `pit`, `pillar`, `entrance`) and `TrapsLessonTest` for a real room. The old `Bomb` type still exists and still loads (`forest-3.tmx` has one) but is not used for new rooms.

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
animation. It rebuilds the current room graph, restores that room's latest secured
snapshots for mutable room owners, and discards the attempt's queued effects. On a
first visit the authored map is the baseline. Falling platforms retain their secured
position, phase, timers and removed state, including after death; warning lights are
reconstructed without their old particles. A spent one-shot support may close an optional
pickup route until Restart Level, while every mandatory exit and boss route remains
passable. Other mutable objects, such as portable tools, switched torches, walls and
moving platforms, roll back to the latest secured state. Passed rooms freeze and keep
their secured progression without updating. Future rooms start their first attempt only
when entered. A healer collection return secures the current room and applies the same
owner policy while retaining secured pickups, enemies and traps; see [HEALER.md](HEALER.md)
for the full state table.

Before a room is secured, a held carryable is released onto clear, stable static collision
support within one hero-width of that room's authored entrance, where the player can reach
it on return. The tool remains owned by that room; it is not transported in the fresh
hero's hands.

<a id="puzzle-platformer-authoring-contract-d26"></a>

## Puzzle-platformer authoring contract (D26)

Design from [D26](DECISIONS.md#d26-observe-plan-before-committing) and the
[campaign intent](GAME_DESIGN.md#observe-plan-and-commit-d26): let players study a room,
form a plan, then commit to a jump or interaction. A one-shot support, switched mechanism
or moved tool may remain changed after a secured forward exit or healer return, so later
searches for secrets can differ from the first pass.

From every reachable secured state produced by supported player actions, keep every
mandatory room exit, boss and required tablet fragment reachable without a forced Restart
Level. Optional secrets and ingredients may be missed until Restart Level.
Before an irreversible action, show the player enough about the one-shot behavior and its
consequence to make the risk understandable. A reset button does not excuse a hidden trap.
Do not reset or rearm an object merely to make a gate pass. Test the route after securing
spent supports and saved mechanism/tool state, verify the required objective still works,
and verify that Restart Level restores the authored opportunity. Do not weaken the existing
20-second mastered-route or 60-second first-learning hypotheses to disguise an inaccessible
mandatory route; those budgets remain separate from this contract.

The forest Mushroom case is optional: spent platform 115 may close its route until Restart Level,
but the canopy exit remains reachable without the Mushroom. Runtime retention is tracked in #158;
acceptance for each campaign map is verified separately. The authoring rule applies to
future rooms and all required tablet fragments.

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
| `forest-1` / unmarked room | 1280 px | 11.58 s | None | Within 20 s |
| `forest-3` / `forest-entry` | 1280 px | 17.60 s | 20.27 s | Within 20 / 30 s |
| `forest-3` / `forest-canopy` | 672 px | 5.17 s | 5.47 s | Within 20 / 30 s |

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
measurements assert coins, not a complete Bat/Mushroom collection route. Artifact
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

- `forest-3.tmx`: `bf227d733af41d97f575b1d16bcebb6cf408590b26f19510942ffc19f2b88f96`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

## Completed test-version review and historical evidence

### Artifact checks (#72)

The author accepted the existing Bat and Mushroom positions as final for the MVP on
2026-10-19: Bat is object 94 at (448, 240), Mushroom is object 95 at (1728, 208) in
`forest-3.tmx`. Keep these placements; no relocation or additional artifact challenge
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

Actual desktop capture follow-up on 2026-10-14: running `forest-3` at a 0.001 s
world step reproduced the below-ground explosion at approximately 0.05 s into
the effect. The shader-only check missed libGDX's `TextureRegion` adjustment of
a 1x1 texture to UV 0.25..0.75. That moved the 0.85 ground origin outside the
quad. `Shaders.whiteQuad` now explicitly restores full 0..1 coordinates, keeping
the Y-down flip. A second desktop capture at the same effect time shows fire
above the ground at the original bomb location. Actual GL texture coordinates
were checked at runtime; `ShadersTest` also guards against the 1x1 inset.

### Room transition checks

The pre-release transition checklist is maintained in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#rooms-pickups-and-traps).

## Rhythmic traps (#234, D46)

Objects go in the `SpawnPoints` layer of the room that owns them, like every trap. The tests
use `core/src/test/resources/tiles/rhythm-test.tmx` as the worked example.

| Type | Size | Properties (default) | Notes |
| --- | --- | --- | --- |
| `Puffball` | 16×16, base on the floor | `period` 4.0 s, `phase` 0 s, `cloud` 1.0 s | cap swells 0.8 s (1.2 s with the assist), then a 1.0 s lethal cloud, then calm |
| `ThornVine` | 24×40, top edge is the anchor | `period` 4.5 s (min 3.5), `phase` 0 s, `amplitude` 50° (35-60) | lethal lower half only; the stem is harmless |
| `FallingPlatform` | 32×16 | `skin=log` | the rotten log; behaviour unchanged |

**Period and phase.** Time starts at the room's entrance and again on every retry, so the
authored `phase` is also what the hero meets on every attempt: the first cloud or swing a
newcomer reaches is always the same one. Use `phase` to keep neighbouring traps out of step,
never to hide a trap behind a rhythm that is only passable by luck.

**Safe-window minimum.** `Puffball`: `period >= cloud + 1.2 + 0.8` (the longest assisted swell
plus 0.8 s of calm); the load fails otherwise. The hero crosses the 38 px column in about 0.38 s.
Never put two puffballs closer than a crossing apart on one line unless their phases leave a
common safe window; test any chain with the walker harness in `RhythmTrapsTest`. `ThornVine`:
floor 64 px below the object's top edge; the vine must hang clear of the room's walls and
ceiling (swept area is `length + 4` px each way from the anchor, the load fails if it leaves the room).
With that recipe the safe window is about 1.2 s per swing and the crossing needs about 0.8 s, so a
careful hero waits at the edge, watches the swing lift the tip, and runs under.

**Never over a place the hero must stand.** The load (and `check_tiled_links.py`) rejects a trap whose danger area, over its whole rhythm, touches the room entrance body, the room exit or a checkpoint of the room.

**Retry and fairness (D26).** A death resets the room, including the rhythm. Both traps can be
passed with ordinary controls, no ammunition and no precise jump; neither needs a bypass. The
swell and the swing are the warning (D7); the assist lengthens the swell only, never the swing.
Keep a rhythmic trap within one screen of its approach so the hero can watch it before committing.

**Hint zone advice for #235.** Put a `Hint` zone before the trap (a full crossing length back)
whose text names the tell, not the answer: the cap swells before it bursts, wait for the cloud
to clear; the vine is only dangerous low, pass while it is swung to the side. Add no text before
the hero can see the trap move. No hint text exists yet; #235 adds it.

## Teaching order and walkthrough (#73/#75)

Rule for the two levels: teach each of stomp, sword, falling platforms and bombs where
it is the only new thing on screen, on forgiving ground and without a required timing
test, then combine. `forest-3` (level one) teaches; `forest-1` (level two, the finale)
combines. Optional routes stay separate from required progression; nothing here is
tuned around coins, and no coin or artifact was added, moved or removed.

Current teaching-layout identity (`9d5e630`): `git hash-object` of `forest-3.tmx` is `619bf64b559a`, of `forest-1.tmx`
`44fbc6aa95c3`. Positions are map pixels (16 px per tile; SpawnPoints carry a -16 px
layer offset that this text already includes in the visible position).

### `forest-3` / `forest-entry` (required route, left to right)

| x | What appears | Role |
| ---: | --- | --- |
| 64 | Start. The first mob's zone begins 4 tiles ahead. | Safe entrance |
| 192 | YellowMob (id 50), patrol 128–208, beside a one-tile step | **Stomp, alone**. Jump on it from above, or K. |
| 240–320 | Quicksand, vertical Bat enemy (272); optional one-way steps to the coins/Bat pickup | Hazard + Bat enemy; not a taught mechanic |
| 512–608 | Horizontal Bat (patrol now ends at about 608) and a second quicksand | Same, repeated |
| 416–480 | **Ladder** (id 123, initially x 416, 208–288; lever lowers it to 240–320) beside the one-way ledge (id 122) under the Bat | **Ladder and lever**: Up beside it takes hold; the nearby lever raises and lowers it; hints only in this room |
| 656 | **Bomb** (id 54), moved from 128 | **Bomb, alone**: flat ground, 0.8 s warning (1.2 s with Assist), 64 px blast |
| 832 | YellowMob (id 16), aggro zone 752–896 | Stomp repeated, first light combination: leave the blast, then meet the mob |
| 912, 992 | Two tall walls | Wall jump |
| 1136 | RedMob (id 60), zone 1056–1216, just before the exit | **Sword, alone**: wind-up is visible; K or stomp |

The bomb used to sit 4 tiles from the start inside the first YellowMob's patrol, so stomp
and bomb were the first two things met and overlapped. The bomb is now about 470 px
beyond the first mob and the bat patrol and the second mob's zone were pulled back so that
the blast square (about 624–688) and a running escape of about 64 px overlap neither.

### `forest-3` / `forest-canopy`

| x | What appears | Role |
| ---: | --- | --- |
| 1344 | Start | Safe entrance |
| 1440–1488 | One-tile step; RedMob (id 68), zone 1408–1488 | Sword again, now beside terrain |
| 1488 | Optional one-way platform | Start of the optional climb |
| 1568 | **FallingPlatform** (id 115, was a fixed platform) holding coin 87 | **Falling platform, alone**: 1 s warning (1.5 s with Assist). Ground is 80 px below; isolated from bombs and nearby walking enemies at spawn; a bad landing can spend the optional ascent |
| 1648–1728 | One-way platform and the Mushroom | Optional reward |
| 1760 | Vertical Bat enemy near the Mushroom | Optional hazard |
| 1872 | RoomExit | |

The collapse is one-way and remains secured after room exit or healer return. A player
who lets it fall before reaching the upper route may lose the optional Mushroom/coin ascent
until Restart Level. The required route never needs the platform. This map fact makes
the one-shot cue and optional-route consequence an explicit manual fairness check under
D26; older teaching-layout route results below do not establish that acceptance.

### `forest-1` (level two, unchanged)

Falling platforms (x 96–224, y 272), the RedMob patrol (192–512) that covers them, the
escalators and the trigger-opened wall at 480–528 are the first time those pieces
are combined. The falling platforms are now a second sighting rather than the first.
By the map geometry there are 32 px between the ground and the platforms and the hero's
hitbox is 22 px, so the course can probably also be walked underneath. This is read from
the map, not exercised in play; the author did walk over the platforms without trouble.

### Evidence and limits

The teaching-layout implementation recorded 131 passing core tests under its then-current
reset policy; these are historical results, not D26 policy validation. `RoomReplayTest`
re-measured every route on this layout
with real input and hazards on, three identical attempts each: `forest-entry` 16.27 s
required / 20.00 s all coins, `forest-canopy` 5.00 s / 5.00 s, `forest-1` 11.58 s, and the
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

These 2026-10-20 timings predate #158's secured falling-platform retention. They remain
movement/replay references, not evidence that Mushroom 95 remains reachable after platform 115
has been secured collapsed. Final-policy route evidence is in [HEALER.md](HEALER.md#room-state-retention-validation-2026-10-24-158).

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

- `forest-3.tmx`: `83adfbbf997718092e33c8b4ac952483f4edad244d1864f26afb4f735de4436d`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

| Level / room | Required route | All room coins |
| --- | ---: | ---: |
| `forest-1` / unmarked | 11.58 s | No coins |
| `forest-3` / `forest-entry` | 16.27 s | 20.00 s |
| `forest-3` / `forest-canopy` | 5.00 s | 5.00 s |

At normal speed, the entry required route uses a fresh jump before the second wall. The
coin route pauses beside the upper coin for a ground jump into it from below the one-way
platform, after securing the ground coin. These actions and their elapsed time are included
in `RoomReplayTest`; no hazards or pickups are excluded. Collection returns for missed
Bat/Mushroom still pass through both rooms three times without farming (14.78/5.13 s on the
first return, 14.25/5.00 s later). Earlier author acceptance does not establish
readability of the changed teaching layout; its targeted check remains deferred.

## Proximity traps (#231)

Authoring recipe for the thorn root and the thorn tuft ([D44](DECISIONS.md#d44-proximity-traps-thorn-root-and-bristling-thorn-tuft)). Shipped once, in `forest-0-ladder` (D48); `core/src/test/resources/tiles/thorn-test.tmx` is the worked example.

- **Objects:** type `ThornRoot` (footprint 16×24) or `ThornTuft` (16×16) in `SpawnPoints`. `x`/`y`/size is the footprint; **y + height is the floor**. Optional float properties: `zone` (px each side, default 80), `zoneLeft`, `zoneRight` (override a side; 0 = one-sided). There is no warning-time property: the warning is always the shared D7 value (0.8 s / 1.2 s assist).
- **Placement:** the jump envelope (footprint plus `max(zone, 64)` px each side, 96 px up) lies inside the room, has floor under all of it, and is free of every solid thing (blocks, one-way platforms, falling platforms, walls, ladders and escalators with their travel); zone not over the room entrance; each used zone side at least 64 px; zone values plain non-negative decimals. A map that breaks these does not load (error names the object) and `python3 tools/check_tiled_links.py` flags the same except the entrance.
- **Open floor only:** never in a one-tile corridor, under an overhang or in front of a wall the hero must jump: the hero has to be able to jump over an armed trap. Leave landing floor behind it: a root jump covers about 60 px.
- **Zone sizes:** the default 80 px makes a walker (100 px/s) cross the zone in ~0.97 s, longer than the 0.8 s warning, so walking straight in is hit and the warning is what teaches the jump. A shorter zone (say 32) lets a quick walker run through before it rises; use it deliberately. Do not place two traps whose zones overlap if the second should be a surprise: the first one's rise is the lesson.
- **Jump window:** body right edge 7 to 63 px before the root, 2 to 61 before the tuft; mastered jump-over about 30 px before.
- **Hint zones (for #235):** start the message zone at the trap's zone edge or earlier (about 208 px before the trap gives a continuous walker ~1 s of reading), say what the trap does ("it rises when you come close; jump over it"), keep it true in every state (dormant, warning, up), no key names, at most two lines. Hint text is not added by #231.
- **Reset:** Retry, Restart Level and re-entering the room all rebuild it hidden/calm. A trap that is up stays up for the rest of the attempt.

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

## Directional-controls practice revision (#95)

Current teaching scope (2026-10-23, [D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying)):
the entire `forest-entry` room teaches contextual item/mechanism actions. There is
no 320×144 subregion or fixed travel-distance limit. New practice
objects anywhere in this room receive the same teaching; other rooms/levels do not.
Holding a stone or torch near a usable lever shows its activation instruction
together with any active placement/throwing reminder, anchored at the lever.
Each pickup re-arms the carrying reminder; scrolling the camera after exiting its
dead zone consumes that reminder until a new pickup. It must fade out rather than
stick to the viewport edge. Mechanism lessons remain repeatable after it expires.
Retry/collection return
keeps this rule; no persistent lesson-completion flags are introduced.

2026-10-22, `forest-3.tmx` SHA-256 `9d74c49bf53afefbaa6c86d020dffc0d4288a37386f911dbfb7e0261decd5b06`:
added optional entrance objects 116 (stone), 117 (portable torch), 118 (trigger)
and 119 (linked light). Collision geometry, enemies and collection identities are
unchanged. See [the control contract and dependencies](DIRECTIONAL_CONTROLS.md).
`Stone` and `CarryableTorch` are supported SpawnPoints types; give each a unique
ID and a 12 × 12 rectangle inside its owner room. Decorative `Torch` remains
stationary; portable torches use the explicit new type. Their secured position and state
resume on collection return and roll back to the latest secured snapshot on death; they do
not enter persistent inventory. This replaces the earlier reconstruction rule from D23.

[D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket), accepted
2026-10-23, fixes one shared carryable slot and visible front-hand presentation.
Author puzzles around choosing, placing and retrieving one object; do not assume
a pocket stone can accompany a hand torch. Secured tool state resumes on collection
return and rolls back on death; a held tool is released onto safe support before forward
room exit. The decision adds no cross-room transport,
extra inventory snapshot or persistent save field. Both stone and torch now render
in the animated front hand; the stone's old hidden treatment was never a second slot.

The production-input replay now chooses Up + Action for mechanisms and plain
Action for sword combat. With live hazards and no fireball spending, three identical
attempts each remain: entry 16.27 s required / 20.00 s all coins; canopy 5.00 s /
5.00 s; forest-1 5.32 s required. Collection returns: entry 14.78 / 14.25 / 14.25 s,
canopy 5.13 / 5.00 / 5.00 s. These still check the 20/30-second gates. This does
not establish all-artifact coverage, new human readability or physical-device
acceptance. The optional practice objects are not required route steps.

### Close interaction revision (2026-10-22)

Current `forest-3.tmx` SHA-256:
`d883340f605dc849290d33395841a9de26f19070b9064d99eb9296d2dd405742`.
Objects 116/117 retain their IDs and positions. Trigger 118 is local to the practice
light (x=108, width=16); light 119 is at hand height (authored y=306, before -16 layer
offset). The prior elevated light/large trigger were not needed. Collision geometry,
collection identities and required route remain unchanged. Live-hazard replay gates
still pass with no fireballs: entry 16.27 s required / 20.00 s all coins; canopy
5.00 s / 5.00 s; forest-1 5.32 s, each three identical attempts. Collection-return
measurements remain 14.78 / 14.25 / 14.25 s for entry and 5.13 / 5.00 / 5.00 s for canopy.
This is automated route evidence, not author/novice teaching acceptance.


### Practice lever display

Forest trigger 118 (`practice-light`) retains its authored 16×16 interaction zone
at (128,320), before SpawnPoints' -16 Y offset. Its `appearance=Lever` property
requests a visible 16×16 up/down control at the zone's bottom centre and must link
exactly one `Actionable` Torch by name. Selection highlights this lever, while the
linked Torch (119) remains at its independently authored wall position. The state
comes from the torch: up/on, down/off. No new object IDs or collision geometry are
introduced. A first visit or fresh Level uses authored state; a room retry or collection
return restores the secured torch state, from which the lever is rebuilt.

Standing inspection ([D25](DECISIONS.md#d25-standing-look-with-delayed-vertical-inspection))
adds a deliberate vertical offset to the ordinary room-clamped camera. Horizontal
bounds and transition ownership remain unchanged. Pose/camera inspection never
changes the active room, collision, hero position or visible carryable attachment.


### Fireball refill revision (#98, 2026-10-25)

Local map identities (pending commit/review):

- `forest-3.tmx` SHA-256: `3809d6b20bbb0672a8ca332ef036ac74b0a510790afad7968adb1ebdbe23f912`.
- `forest-1.tmx` SHA-256: `0f937265c9dda74b5027b6016e85ec8fa622a5b8a9d1b5fae667bb59e6814e3f`.

Only one optional `FireballRefill` object is added per map; collision geometry,
existing IDs and routes are unchanged. Forest refill 121 belongs to `forest-canopy`,
authored x=1824/y=320, effective y=304 after the SpawnPoints -16 layer offset,
before RoomExit 113 into the healer/boss flow. Level-two refill 25 is at x=312/y=176,
above horizontal Escalator 17 (authored x=304/y=240). It uses the existing native
16×16 fireball sprite, separate from the detailed HUD art. TMX ID counters advance.

`RoomReplayTest` uses production input/physics, normal assist-off live hazards and
zero room-entry charges for required routes: first room 16.27 s; canopy 5.00 s;
second level 5.32 s, each a fresh attempt and two identical room retries. No Ranged
input is used. Coin routes remain 20.00 / 5.00 s. Existing secured-platform and
collection-return route checks also pass. “All collections” in older output means
all coins; it does not claim every artifact/refill.

The optional level-two refill exercise starts with two charges, follows normal
movement and jumps onto/above the moving platform, stops horizontally beneath the
pickup and makes a fresh jump. Contact grants the third charge at 3.67 s on all
three attempts, with hazards active. This exercise ends at pickup, before the
checkpoint secures it, then retries the room to prove the opportunity/stock rollback.
The independent required-route measurement still reaches the actual checkpoint.
No teleport, invulnerability or disabled hazards are used in these scripted routes.

`RoomTest` also verifies provisional pickup/stock rollback, secured ID 121 absence
through three healer returns, boss-entry ammunition rollback and authored reset on
Restart Level. `DirectionalActionsTest` verifies full-stock local feedback outside
the tutorial, retention at capacity and automatic +1 collection when space opens.
All 219 core tests, desktop and TeaVM release builds, localization, sprite-art and
named-parameter checks pass. Snyk reports only the two existing LOW findings in
unchanged `tools/PortraitExport.java`; no new security findings.

A browser development smoke visually checks three fixed HUD slots, darkened spent
charges, no ammunition number and FPS spacing, and sees the world pickup before
the boss-room exit. This is not a human readability/balance playtest, an unassisted
complete run, or a desktop-renderer check. Manual pickup-hint/second-level readability
acceptance remains in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).

## Ladder practice revision (#99, historical)

`forest-3.tmx` SHA-256 `bdd719de7af0ccc56302322777072f48e3316cd3ccffc1aaa621876d61cc32c3`. Added to `forest-entry`:
one-way ledge collision 122 (416, 240, 64×16) with matching Background tiles, and `Ladder`
object 123 (`first-ladder`, SpawnPoints x 416, y 256 before the -16 layer offset, so the
climbable span is world y 240–320, five 16 px frames). The Bat (id 94) already hovers at
the ledge's surface, so the ladder is an alternative to the stair-and-jump route, not a new
requirement. No enemy, coin or artifact was added, moved or removed; the required route and
the existing replay gates are untouched (all three replay routes still pass).

At this revision `Ladder` was a SpawnPoints type: width exactly one frame (16), height a whole
number of frames, top edge on the surface the hero should step onto, bottom edge on the floor.
The room owning its centre owns it, like every other object. It carried no state at that time.
Put support at both ends: the hero ignores terrain while attached and steps off wherever the ends are.

Safe test route: from the entrance walk to x≈424, hold Up to the ledge (about 1.2 s), step
to the Bat, then come back with Down from the ledge's left end or by jumping off. The
nearest threat is the horizontal Bat (id 43), whose patrol begins about 64 px right of the
ladder foot; it is not a taught mechanic here. This is a scripted check, not a human playtest.

## Toggle ladder update (2026-10-26)

Current `forest-3.tmx` SHA-256: `df609760fe2f15f8d34995117c579c99ab19052fee0819b4295a1b3af6827a2a`.
Map update (#194, 2026-11-04): the file was `forest.tmx` before this rename. Map-level properties
`fireflies`, `healerBeforeBoss`, `ingredients` and `tutorialRoom` now configure the level
([D32](DECISIONS.md#d32-forest-level-order-and-identity)); no geometry changed. Current SHA-256:
`c973dfe7890fde3dad0afb063b729c59aadf03b12577fc01f9507a813cf5b01d` (earlier hashes in this file name the old `forest.tmx`).
Hint update (#195, 2026-11-04): `tutorialRoom` was replaced by a `Hints` layer with one `controls` zone over room 0
([D33](DECISIONS.md#d33-the-forest-0-tutorial-level-and-map-authored-hints)); geometry unchanged. Current SHA-256: `1beda5c49f3f27d973a6ff918e50d886f36b840f59890dd25e05f10d4550f4fe`.
`forest-0.tmx` update (#206, 2026-11-05; superseded SHA below): the lever room shrank from 1280 to 768 px (tile layer 480 to 448 columns; lever x=344,
torch x=424, exit x=704, walk/torch/exit zones 16–272/272–528/528–752, controls zone 16–752); the five later rooms and the
`Checkpoint` moved 512 px left, geometry otherwise unchanged. `forest-0.tmx` SHA-256 before #196 (superseded): `ded8651e937d049144bbea34393ef3c4032a3a9a88512aa0c78c12e7196c3262`.
`forest-0.tmx` update (#196, 2026-11-05): the ladder room is built and shortened to 768 px (x 768–1536; tile layer 448 to 416 columns). Lever (id 46)
at x=920 on the floor; retracted `Ladder` (id 45, `dropDistance` 112, 16×112) at x=1120, hanging y 96–208 and lowering to y 208–320; a solid
ledge (collision id 47, x 1136–1520, top y=208, tiled with the ground-edge tiles) with the exit at x=1440, y=160 on top. Zones: lever 784–1040,
ladder 1040–1200, exit 1200–1520, controls 784–1520. The four later rooms and the `Checkpoint` moved another 512 px left (Checkpoint x=6592).
`forest-0.tmx` update (#209, 2026-11-05): both lesson rooms resized. Lever room 768 → 576 px, ladder room 768 → 704 px (tile layer 416 to 400 columns). Lever room: lever x=376, torch x=456, exit x=512, zones walk 16–160, lever/torch 160–440 (starts 216 px before the lever), exit 440–560, controls 16–560. Ladder room (x 576–1280): RoomStart x=624, lever x=824, ladder x=1024, ledge collision id 47 x 1040–1264 (shrank from 384 to 224 px; exit x=1184), zones lever 592–896, climb 896–1040 (starts 128 px before the ladder), exit 1040–1264, controls 592–1264. The room was lengthened by 96 px before the lever so a continuous walk from RoomStart gives the lever message about 1 s fully visible before the lever is in reach (`HintLeadTest`). The four later rooms and the `Checkpoint` moved 256 px left (Checkpoint x=6336, `Deadline` shortened to match); ids and links unchanged.
`forest-0.tmx` SHA-256 before #197 (superseded): `eddb6531cd16290f6fd08c3ec9a08a4791bd3e4fb04d494c5b9915b4cc65aeae`. Mastered routes (headless author checks, `LeverLessonTest`/`LadderLessonTest`, not novice playtests): lever room about 4.3 s, ladder room about 6.9 s; this superseded revision itself was a headless check only (the author's play-through of 2026-11-05 is of the final map, below).
`forest-0.tmx` update (#197, 2026-11-05): the wall room is built and shortened from 1280 to 640 px (x 1280–1920; tile layer 400 to 360 columns). RoomStart x=1328, lever (id 53) x=1528 on the floor, vertical `Wall` Actionable (id 52, 48×96, `time` 1.5 s, same as forest-1) at x=1760, y=224 (lowered: y 224–320, standing on the floor; raised: y 128–224, a 96 px gap), exit x=1856. Zones: lever message (`HINT_WALL_LEVER`) 1296–1584 (starts 232 px before the lever), pass message (`HINT_WALL_PASS`) 1584–1904 (starts 176 px before the wall), controls 1296–1904. The three later rooms and the `Checkpoint` moved 640 px left (Checkpoint x=5696, `Deadline` shortened to match); ids and links unchanged, new ids 52–56.
`forest-0.tmx` SHA-256 before #198 (superseded): `7ac1e29aafbf1740c973e782ab4f3af823914182ba8d2c7215bf6af2faf4233a`. Mastered route (headless author check, `WallLessonTest`, not a novice playtest): wall room about 5.1 s; this superseded revision itself was a headless check only (the author's play-through of 2026-11-05 is of the final map, below).
`forest-0.tmx` update (#198, 2026-11-05): the platform room is built and shortened from 1280 to 640 px (x 1920–2560; tile layer 360 to 320 columns). RoomStart x=1968, lever (id 58) x=2168 on the floor, vertical `Escalator` (id 57, 32×16, `isVertical`, `offNeg` 4, `offPos` 0, `running` false) at x=2384, y=304 (bottom; the top end is y=176), ledge collision (id 62) x=2416–2544, top y=208, tiled with the ground-edge tiles, exit x=2496, y=160 on the ledge. Zones: lever message (`HINT_PLATFORM_LEVER`) 1936–2200 (starts 232 px before the lever), ride message (`HINT_PLATFORM_RIDE`) 2200–2544 (starts 184 px before the platform), controls 1936–2544. The three later rooms and the `Checkpoint` moved 640 px left (Checkpoint x=5056, `Deadline` shortened to match); ids and links unchanged, new ids 57–62.
`forest-0.tmx` SHA-256 before #199 (superseded): `313d8df1bae13e7498ab84fced2e687181ab432288b6207b50275b3a0abeb611`. Mastered route (headless author check, `PlatformLessonTest`, not a novice playtest): platform room about 9.6 s; this superseded revision itself was a headless check only (the author's play-through of 2026-11-05 is of the final map, below).
`forest-0.tmx` update (#199, 2026-11-05): the stone room is built and shortened from 1280 to 528 px (x 2560–3088; tile layer 320 to 273 columns). RoomStart x=2624; `Stone` (id 63, 12×12, bool `respawn`) at x=2848, y=308; near floor x 2560–2912 (collision id 28), a 96 px **chasm** x 2912–3008 with no floor (the level-wide `Deadline` below), far floor x 3008–3088 (id 69). Horizontal `Escalator` (id 64, 32×16, `isVertical` false, `offNeg` 0, `offPos` 2, `running` false) parked at x=2912, y=320, flush with both lips, ending at the far lip when it runs; lever `Trigger` (id 65, 16×48 at x=3008, y=272, `target` 64, bool `throwActivated`) at the far lip; an **overhang** (plain collision id 70, x 2864–3024, y 0–272, tiled with the ground-fill tile) that caps jumps to about 26 px of rise over the chasm and the 48 px before it, so no jump crosses the 64 px left beside the parked platform; exit x=3032. Zones: throw message (`HINT_STONE_THROW`) 2576–2872 (starts 272 px before the stone), platform message (`HINT_STONE_EXIT`) 2872–3008 (near edge to the far lip), far-side message (`HINT_STONE_ACROSS`) 3008–3072, controls 2576–3072. The last room and the `Checkpoint` moved 752 px left (Checkpoint x=4304, `Deadline` shortened to match); ids and links unchanged, new ids 63–71.
`forest-0.tmx` SHA-256 before #200 (superseded): `51c0ef939136dbb7d79a96ad9409fc0cbe44ee7d1bffbdcd81a36f802fdadd70`. Mastered route (headless author check, `StoneLessonTest`, not a novice playtest): stone room about 4.8 s; this superseded revision itself was a headless check only (the author's play-through of 2026-11-05 is of the final map, below).
`forest-0.tmx` update (#200, 2026-11-05): the traps room is built and shortened from 1280 to 720 px (x 3088–3808; tile layer 273 to 238 columns). RoomStart x=3120; floor x 3088–3328 (id 34), a 144 px **pit** x 3328–3472 with no floor (the level-wide `Deadline` below), far floor x 3472–3808 (id 74); `FallingPlatform` (id 72, 32×16) at x=3392, y=320 flush with the floor; `Bomb` (id 73, 16×16) at x=3648, y=304; `Checkpoint` (id 3, the level end) moved to x=3760, right wall (id 36) x=3792. Zones: platform message (`HINT_TRAP_PLATFORM`, id 75) 3104–3440, bomb message (`HINT_TRAP_BOMB`, id 76) 3440–3792; no controls zone. `nextobjectid` 77; no later room, so nothing else moved. Rule: [D40](DECISIONS.md#d40-the-tutorial-traps-lesson-rule).
`forest-0.tmx` SHA-256 before #233 (superseded): `0faee8732d8695c557fab6f1f846b4619cd7366d75ca35f2233de79ccc7037af`. Mastered route (headless author check, `TrapsLessonTest`, not a novice playtest): traps room about 6.1 s. This is the revision the author played through on 2026-11-05 (acceptance note below).
`forest-0.tmx` update (#233, 2026-11-05): the `Bomb` (id 73, x=3648, y=304) became an `ExplosiveStump` (id 73, same rectangle and place, name "Exploding Stump"); the hint zone id 76 was renamed `forest-0-traps-stump` and shows `HINT_TRAP_STUMP` (was `HINT_TRAP_BOMB`). Nothing else moved (blast square x 3624-3688, `Checkpoint` x=3760). Rule: [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root).
`forest-0.tmx` SHA-256 before #235 (superseded): `5e9eca8c5d97148efa55877187053ca7bc7f9e339bdebb2d7be8c977fd007475`. Mastered route (headless author check, `TrapsLessonTest`, not a novice playtest): traps room about 6.1 s (re-measured 6.12 s walking through the lit stump and 6.12 s jumping over it). Not yet played through by the author after #233 (the acceptance note below is for the previous revision).

`forest-0.tmx` update (#235, 2026-11-06, revised after review, [D48](DECISIONS.md#d48-traps-in-the-tutorial-rooms-one-new-trap-per-lesson-room)): one trap in each of four rooms, the trap first in the three rooms with a lever; 238 -> 282 tile columns. Ladder room 704 -> 896 px: `ThornRoot` (id 77) x=896, y=296 (`zone` 64), lever x 1080 (was 824), ladder x 1216 (was 1024), ledge collision id 47 x 1232, exit x 1376; zones root `HINT_LADDER_ROOT` (id 81) 592-912, lever 912-1072, climb 1072-1232, exit 1232-1456, controls 592-1456. Wall room (x 1472) 640 -> 784 px: `FallingBranch` (id 78) x=1744, y=240, zone x 1728 y 240 48x80; lever x 1944, wall x 2096, exit x 2192; zones branch `HINT_WALL_BRANCH` (id 82) 1488-1776, lever 1776-1934, pass 1934-2240, controls 1488-2240. Platform room (x 2256) 640 -> 800 px: `FallingStone` (id 79) x=2544, y=0, zone x 2512 y 16 48x304 (skin default `forest`); lever x 2728, platform x 2880; zones stone `HINT_PLATFORM_STONE` (id 83) 2272-2560, lever 2560-2718, ride 2718-3040, controls 2272-3040. Stone room (x 3056) 528 -> 736 px: `ExplosiveStump` (id 80, "Stone Room Stump") x=3488, y=304; stone (id 63) x=3328; chasm, platform, lever, exit and overhang (collision id 70, x 3568) 208 px right of before (lip 3616, far lip 3712); zones throw 3072-3322, stump `HINT_STONE_STUMP` (id 84) 3322-3576, platform 3576-3712, far side 3712-3776, controls 3072-3776. Traps room now x 3792-4512, `Checkpoint` x=4464; `nextobjectid` 85. Measured mastered routes (headless author checks, not novice playtests): ladder 8.82 s, wall 6.48 s, platform 11.85 s, stone 6.88 s (8.47 s when the stump is set off from afar), traps 6.1 s.
Current `forest-0.tmx` SHA-256: `e25f4e98f947dba000c7ff485364e23ddc1521824351e85522c3da18f092fe57`. Not played through by the author yet (the acceptance note below is for the revision before #233 and #235).

#### Author acceptance of forest-0 (2026-11-05)

Adam Konter (project author) played the whole forest-0 tutorial on desktop (keyboard only) and also checked the web build, against `forest-0.tmx` SHA-256 `0faee8732d8695c557fab6f1f846b4619cd7366d75ca35f2233de79ccc7037af` (the revision before #233 replaced the bomb with an exploding stump). All six rooms (lever, ladder, wall, platform, stone, traps), both healer dialogues (start tale and farewell) and the transition into forest-1 were checked, with no remarks (OK). **Not checked:** gamepad, touch controls, native-speaker quality of the de/pl/uk/be translations. No novice playtest (#129–#131) has been done. Mastered route times above stay headless author checks.

**Mobile browser (same day, same author):** the web build was also played on an iPhone in the browser, and all the tutorial screens were completed with the on-screen touch controls, so touch controls are now partly checked on one device (one phone, one browser; gamepad still unchecked). Gameplay worked; four presentation problems are filed as [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226) (portrait control placement, no fullscreen, landscape shows only half the game, screen auto-lock).
The first-room ladder starts 32 px (two 16 px tiles) above its former position. Trigger 124,
named `first-ladder`, places a lever beside it on the floor. Each interaction animates the
ladder between the raised y=208–288 span and its former lowered y=240–320 span at 160 px/s;
the climbable bounds track the sprite throughout. The lowered position reaches the floor and
aligns its top with ledge 122. The ladder position and switch state are included in room
snapshots, so death rolls back to the latest secured state and Restart Level restores the raised
position.

The replay measurements above and `RoomReplayTest` results refer to earlier map hashes and do
not measure this lever route. A fresh required-route and retry measurement for this map revision
is pending; the build environment could not start Gradle (wrapper cache access was denied, and
the installed Gradle could not load its macOS native library). No scripted or human route result
is claimed for this update.

### Ladder support and midpoint entry (2026-10-26)

The map hash is unchanged. The ladder's top now has a one-way support that follows its
animated position, so climbing to the top leaves the hero standing instead of dropping to the
next platform. From a grounded intermediate platform at least one third down the ladder, Up
and Down both grab the rungs and the first-room hint shows separate lines for both directions.
The upper span and bottom accept
Up; the top surface accepts Down. This behavior change has not been included in route timing or
automated/physical play checks yet. See [D28](DECISIONS.md#d28-vertical-ladders).
