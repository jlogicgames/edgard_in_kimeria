# Rooms in Tiled

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

The teaching-layout revision `9d5e630` changes the current Forest placement after
the earlier test-version acceptance. Its automated route checks pass, but the new
spacing still needs the targeted human readability check recorded below. Earlier
author acceptance must not be read as evidence for a later map layout.

The Forest levels are the tutorial `forest-0`, then `forest-1`, then the boss level `forest-2`
(intended Forest order: `forest-0` → `forest-1` → `forest-2`, [D32](DECISIONS.md#d32-forest-level-order-and-identity)). A level is one TMX map
containing an ordered chain of rooms. `forest-2` has seven
rooms in play order: `forest-entry` (736 px), `forest-walls` (544 px, split off it by #376), `forest-fissure` (640 px, the
ember fissure's teaching room, #366, appended at the map's end, see "forest-2 fissure room" below), `forest-glade` (832 px, the forest wolf's first room, #331, appended at the map's end after it, see "forest-2 glade room" below), `forest-roost` (736 px, the forest flying beetle's first room, #330, appended at the map's end after it, see "forest-2 roost room" below), `forest-canopy`
(1120 px, with an ascending optional coin route) and the authored 448 px boss chamber. The canopy RoomExit
leads into forest-boss. Boss death and the automatic victory walk lead to the post-boss healer dialogue (#86);
its final advance completes the level; as the last level it opens the ending scroll and then the menu, with the save cleared.
`forest-1` has twelve rooms since #500 batch 3 ([D110](DECISIONS.md#d110-forest-1s-trap-course-is-split-in-two-the-escalators-and-the-puffball-each-get-a-room-and-the-course-loses-a-tuft-and-a-lever-pair-500-499): the 1280 px trap course was split at x 640 into the 640 px `forest-1-escalators`, third in play order, and the 640 px `forest-1-traps`, fourth; see "Reworked under D105, batch 3" below), eleven since #484 (the `forest-1-spider`, fifth in play order after the lever lesson, 1120 px since #500, and the 640 px `forest-1-climb`, seventh after the bramble room: the forest spider and the snail met one at a time, then the spider climbing to a hero waiting for a lift, see "forest-1 spider rooms" below, both authored at the end of the map), nine since #365 (the 640 px `forest-1-hive`, [D90](DECISIONS.md#d90-the-hanging-hive-a-rhythm-trap-whose-stingers-fall-as-a-band-365-2026-11-17), eighth in play order after the guard room: the hanging hive over open floor, then over a ladder, see "forest-1 hive room" below, authored at the end of the map), eight since #364 (the 640 px `forest-1-bramble`, [D89](DECISIONS.md#d89-the-creeping-bramble-a-clocked-thorn-arm-across-a-lane-taught-at-a-ladder-exit-364-2026-11-17), fifth in play order after the lever lesson: the creeping bramble over a ladder top, see "forest-1 bramble room" below, authored at the end of the map), seven since #363 (the 640 px `forest-1-deadfall`, [D88](DECISIONS.md#d88-the-trip-line-deadfall-a-touch-trap-whose-trigger-is-drawn-on-the-floor-363-2026-11-17), second in play order: the trip-line deadfall met alone, authored after the end of the map like `forest-1-mob` and listed between it and the trap course), six since #418, five since #375 ([D85](DECISIONS.md#d85-forest-1-opens-with-the-first-redmob-alone-in-a-room-of-its-own-375-2026-11-17): the 800 px `forest-1-mob`, the first RedMob alone and then a refill, is played first although it lies after the tree room on the map, because the `Rooms` list sets the order), four since #349, three since #104 and two since #187 ([D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15)): the 1280 px trap course (`forest-1-traps`, [D58](DECISIONS.md#d58-forest-1-traps-and-a-second-redmob-three-traps-and-one-enemy-in-the-open-stretch)), the 640 px lever lesson room (`forest-1-lever`, [D86](DECISIONS.md#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17), see "forest-1 lever room" below, authored after the end of the map like `forest-1-mob` and listed between the trap course and the route room), the 832 px route room (`forest-1-route`, see "forest-1 route room" below) and the 768 px guard room (`forest-1-guard`, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), see "forest-1 guard room" below) and the 640 px tree room (`forest-1-tree`, [D80](DECISIONS.md#d80-tree-branches-one-way-perches-and-rotten-branches-349-playtest-f13-2026-11-16), see "forest-1 tree room" below); the checkpoint on the tree room's crown leads to `forest-2`. See [MVP.md](MVP.md). Replay budgets and measured
routes for #70 are recorded below.

For the future stage → levels → rooms hierarchy, see the
[full campaign design](GAME_DESIGN.md#campaign-structure). This document describes
current map authoring and retry behavior.

**Floor line (D144, [#648](https://github.com/jlogicgames/edgard_in_kimeria/issues/648), 2026-11-26).** The floor line of every forest room is now
**y 304**, over 64 px of soil. The dated placement plans, tables and measurements below were written for a floor at y 320: read a
floor-measured y in them as 16 px higher (`y - 16`). What hangs from the ceiling kept its y, with the hand-reviewed exceptions in
[D144](DECISIONS.md#d144-the-forest-floor-is-one-tile-higher-64-px-of-soil-under-it-648-2026-11-26). The current coordinates are in
[ROOM_CARDS.md](ROOM_CARDS.md) and the maps.

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

The game draws a portal on both kinds of way out; the map authors none ([D123](DECISIONS.md#d123-a-portal-marks-every-room-exit-and-a-ripple-marks-the-room-change-552-553-2026-11-22)).
A violet one is centred on every `RoomExit`. An amber one marks the level end ([#605](https://github.com/jlogicgames/edgard_in_kimeria/issues/605)): in a level without
a boss it is centred on the final `Checkpoint`, so moving that object moves the portal. The oval is 40 to 56 px tall and reaches 4 px past each end of a 32 px
rectangle, so a `Checkpoint` at the room's top edge loses the top of the oval (forest-1's does, accepted by the author). A boss level has no `Checkpoint`: its amber
portal opens after the kill at the right end of the boss room, on the floor.

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
cross-room links are rejected. Atmosphere (fireflies; fog is menu-only)
is decorative and continues across retries. The falling leaves are the exception: the `Room` marker's int property `leaves` (default 0,
[#473](https://github.com/jlogicgames/edgard_in_kimeria/issues/473)) sets how many leaves drift down that room; they start fresh with the room, are cleared on
every room change, death and retry, and never start over a trap object's columns (ART_DIRECTION.md, "Falling leaves"). Put it only on a room with trees
or branches, and check the room's trap warnings with the leaves falling. Rooms may span several screens.

### Hint zones (#195, D33)

An optional object layer `Hints` holds `Hint` rectangles (each inside one room). Properties:
`message` (a `Msg` key such as `HINT_TUTORIAL_WALK`, shown at the zone's horizontal centre, 45% down it, after a short 0.35 s dwell; it then lingers 2.5 s after the hero leaves, see D33) and/or
`controls` (bool, enables the pickup/lever/ladder instructions of D24), and two optional touch properties (D139, [#496](https://github.com/jlogicgames/edgard_in_kimeria/issues/496)): `touchMessage` (a `Msg` name that replaces `message` while the touch tutorial guides the player, see `TouchControls.touchGuided()`; only with a `message`) and `coach` (`stick-drag`, `stick-up` or `ranged`: arms a step of the touch tutorial `ui.TouchCoach` while the hero is in the zone; a `Hint` may carry only a `coach`). Neither is a new object type or an element of the room cards. The message text follows the plain voice and the
limits of [TEXT_STYLE.md](TEXT_STYLE.md) (D74); a hint is never a trap's at-rest cue (D71). The hero's body centre
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
   room's top edge (y=0), which was first made so the side walls could not be wall-jumped over the top ([D38](DECISIONS.md#d38-every-room-has-an-invisible-ceiling), #215); walls cannot be grabbed since [D121](DECISIONS.md#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21), and the slab stays as a guard (it still closes the room from above).
   Keep the side walls and any ledge walls inside the room (`RoomCeilingTest` checks every room of forest-0, forest-1 and forest-2).
2. **Object + link.** Put the mechanism in `SpawnPoints` as type `Actionable` with property `type` = `Torch` or
   `Wall` (`check_tiled_links.py` rejects anything else; a moving platform is instead a SpawnPoints `Escalator` with `isVertical`, `offNeg`/`offPos` in 32 px units and `running`=false, linked the same way; a Torch starts dark with `Intensity` = 0, lit with a
   positive value; a Trigger that a *thrown* carryable should operate, not the hero's Up, gets the bool property `throwActivated` = true, see [D39](DECISIONS.md#d39-the-tutorial-thrown-stone-lesson-rule), and must stand where the hero cannot overlap it; a Stone with the bool `respawn` = true returns to where it was authored when it falls out of the room or hits such a lever). Add a `Trigger` with property `target` (type *object*) = the mechanism's id. Names are written by
   the Tiled extension `tools/tiled-extensions/link-names.js` as `Torch [40] ← 41` (own id, then the id of each
   trigger pointing at it) and `Lever [41 → 40]` (own id, then its target); do not hand-craft other formats. Both
   objects stay in the room, the effect is visible within one screen of the trigger, and `nextobjectid` is
   advanced. Stand mechanisms on the floor like the lever (torch y = 288 for a 16 px object over the y=304 floor)
   unless the lesson needs height. Run `python3 tools/check_tiled_links.py`.
3. **Hint zones** (layer `Hints`, full room height 0–368, inside the room). Horizontally, a room's usable range is
   between its side walls (x 16–560 for the 576 px lever room; add the room's x offset for later rooms).
   One `controls` zone over that whole range gives the live "W: Turn on - Lever" label (D126) near the lever; and
   `message` zones tile the range so every standing position belongs to a hint zone (the text appears after the 0.35 s dwell, not instantly; the first message of a room needs the dwell plus a 0.45 s fade-in, so about 0.8 s). **Place each lesson zone so a continuous walk gives its message about 1 s fully visible before the object is in reach** (`HintLeadTest`; at 100 px/s that is roughly 180 px between the zone start, or the room start for the first message, and the object's reach, so lengthen the approach rather than the rest of the room), because the text needs the dwell and fade-in to be readable, and the HUD keeps it up for 2.5 s after the hero leaves and at least 0.7 s before another message replaces it (D33); a zone that starts at the object is read only after the hero has passed it. **Message zones are position-only:
   they know nothing about object state or whether the player already acted, so every lesson text must be true in every
   state** (lever off, on, never touched). Do not write "the torch is dark" or "works both ways" on a position-only
   zone; say what the object does ("Use the lever to light the torch or put it out") and where the exit is, in the one formula of
   D97 (TEXT_STYLE §1.1). Messages are
   Msg keys named `HINT_<LESSON>_*`, in all five catalogs, without key names (devices differ), at most two lines at
   200 px. The HUD draws a message hint about 45% down the zone (20 px font, 400 px wide, D50) and shows it after a 0.35 s dwell with a fade-in and
   overshoot slide (D33), so keep zones wide enough (about 200 px or more) for the dwell to matter; add them to the layout assertion in `LocalizationLayoutTest`.
4. **Rule.** Decide and document, as D34 does: toggle or one-shot, what a retry restores, and why the exit is
   reachable in every state (D26). Prefer reversible, route-independent first lessons.
5. **Tests** (copy `LeverLessonTest`): headless `Level("forest-0")` driven by keyboard input — walk to the object,
   operate it, assert the linked effect; assert the exit (`level.atRoomExit()`) is reached from every state
   (off, on, after `resetCurrentRoom`, new `Level` for Restart Level); assert the exact zone message (`zoneHint()`) at every standing x with the object in each state and after
   retry, and that the live label (`contextHint()`) shows beside it, never instead of it (#211); update `TutorialLevelTest` if new object types appear; measure the mastered route.
6. **Record.** Mastered time (target about 20 s per room is a hypothesis; lever room is about 4.3 s after #209; after D48 ladder room about 8.8 s, wall room about 6.5 s, platform room about 11.9 s, stone room about 6.9 s (8.5 s setting the stump off from afar), traps room about 6.1 s) labelled an
   *author check, not a novice playtest* (external playtests: [PLAYTEST_1.md](PLAYTEST_1.md), later rounds #135, #140), plus findings and what was not verified.
7. **Checklist:** `./gradlew :core:test --rerun-tasks`, `validateLocalization`, `validateNamedParameters`,
   `validateSpriteArt`, `python3 tools/test_*.py`, `check_tiled_links.py`, then look at the room in the game
   (`runDev`), and update ROADMAP, GAME_DESIGN (Forest status), DECISIONS and this section.

## Falling traps (#232, D45)

Authoring recipe for `FallingBranch` and `FallingStone` (rule: [D45](DECISIONS.md#d45-falling-traps-the-position-triggered-branch-and-stone)); placed in `forest-0` by #235 (D48): one branch in the wall room, one stone in the platform room.

- **Objects (SpawnPoints):** the object rectangle is the hanging position and the lethal footprint (branch 32x16, stone 16x16). Properties `zoneX`, `zoneY`, `zoneWidth`, `zoneHeight` (absolute world px, inside the same room), stone only `skin` (default `forest`) and `lipTop` (#360, optional, absolute world px: the top edge of the 24×8 loose-overhang lip, the stone's at-rest cue; without it the lip hangs at the stone's start lowered to the camera's top edge while the hero stands on the floor, y 8 in a 368 px room). Set `lipTop` where that line falls on scenery the lip does not read against, e.g. under a block over the stone (`forest-2` uses 48); it must stay on screen with the hero on the floor, not above the stone's start, above the landing floor, and hang from something: the default line, or directly under a solid block or a drawn background tile row over the stone's column (with open air in the row below), D45's rule for the stone's own start; the stone still starts and falls from its own y. A falling trap reads no other property: a misspelled key (`liptop`) fails the load. `tools/check_tiled_links.py` checks them.
- **Trigger zone vs lethal column:** the lethal column is the object's x-range from the object down to the floor. **Load checks (Java and `check_tiled_links.py`):** the zone must overlap the column's x-range and the 22 px above the floor, must not arm the trap at the room entrance while the hero stands in the column, and a stone must start at the room top or under a solid block; no lever-raised `Wall`, `Escalator` or `FallingPlatform` may stand in the fall. Start the zone **up to 16 px before the column** and let it cover the first part of it ("under the foliage"): a hero who keeps walking is clear of the column about 0.2 s before the branch starts to fall (the tested fixture: zone x 144-192, column x 160-192, hero 11 px wide at 100 px/s; with the assist 0.6 s). A zone that starts further before the column makes the pass tighter (at 32 px the margin is only about 0.05 s) because the hero has farther to run after the trigger. The zone's top may start at the object's height and must reach the floor, because the hero's body box (22 px tall on the floor) has to overlap it.
- **Minimum margin:** keep `warning - time to clear the column` at 0.15 s or more at the base value (tested: `theBranchHasMarginForANaturalWalkNotJustAnOptimalOne`). Flight time is on top of that, at least 0.3 s (the loader refuses a drop under about 41 px). Do not place a trap where the only way out of the column is blocked (a wall, a pit) within 0.8 s.
- **Branch placement:** hang the bottom of the branch at least 24 px above the floor, but for readability 48 px or more (a standing hero is 22 px tall); there must be a tree it grows from so the branch reads as part of it: a `Decor:trunk` behind the branch's knob, topped by a `Decor:crown` hung from the room top (forest-0-wall, #421). A resting branch does not block or hurt.
- **Stone placement:** the stone starts **inside** the room, at its top edge or lower (the D38 slab above the room is solid and a stone there would not load: "outside rooms"). Show a hollow, ledge or foliage in the tiles above its column so the fall has a source. The shadow appears on the first solid below the stone's x-range, so keep that floor flat under the whole column. A drop from the room top of about 300 px takes about 0.8 s; shorter drops are harsher, so lengthen the zone lead rather than the warning.
- **Required routes (D26):** a route may pass a falling trap by walking on or by a running jump at any point of the zone; do not make a required passage that needs standing in the column. At the branch a jump in place also works if it is timed so the hero is airborne above the landed branch at impact (Jump pressed 3 to 48 frames, 0.05-0.8 s, before the impact); never rely on that window for a required route, only on walking on. The trap is single-shot: retry, Restart Level and room re-entry re-arm it.
- **Hint zone advice (used by #235, D48 found about 160 px enough):** a message zone that starts at least 160-200 px before the trap's own zone (so about 1 s of the text is visible before the trigger, see `HintLeadTest`) and says what to do, not where: for the branch "the branch falls soon after you walk under it - run on", for the stone "a shadow on the ground shows where a stone will fall". No hint text is added by #232.
- **Pause and retry:** timers tick only in `Level.update`; death rebuilds the room, so the trap is armed again with the hero at the entrance.
- **`FallingPlatform` over a floor (#355, [D69](DECISIONS.md#d69-a-collapsing-fallingplatform-never-carries-a-body-into-the-floor-under-it-355-2026-11-16)):** a collapsing platform keeps falling as scenery, but it never carries a body into a solid block beneath it: the hero is released onto the floor line (feet at the floor top) when the platform sinks below it, with no sideways shove whichever direction is held. Over a pit he is carried down with it as before. Authoring a platform over a floor is therefore safe: nothing to configure or validate.

## Explosive traps (#233, D47)

Authoring recipe for `ExplosiveStump` (16x16) and `ExplosiveRoot` (16x24), which replace the glass-bottle bomb (rule: [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root)). Shipped placements: the stump in `forest-0-traps` and one stump in `forest-0-stone` (D48).

- **Object (SpawnPoints):** no properties. The rectangle is the sprite footprint; the trigger box is the bomb's inner box (10x13 in a 16x16 frame, 3 px in per side and 2 px down, scaled with the object); stand it on the floor (stump `y = floor - 16`, root `y = floor - 24`). **Touch** (the hero's body overlapping it) or a **hit from afar** (a fireball or a thrown stone) starts the wind-up; it lasts the shared warning, 0.8 s (1.2 s with the assist); then one 64x64 blast centred on the object, checked once; the remains stay as harmless, non-solid scenery. A retry on the first visit or Restart Level makes it whole again; leaving the room alive and returning keeps a spent trap spent (room-state retention).
- **The bypass rule (every explosive, checked at load by Java and by `check_tiled_links.py`):** keep the **jump envelope clear**: floor under the trap and 64 px either side, 96 px of free height over all of that (no roof, ledge, wall, ladder, escalator or falling platform in it, counting their travel), nothing solid in the trap's own column, all of it inside the room. That lets a hero with **no ammunition** jump over it, so it is never a one-tile corridor or the only way. The load also fails when the 64x64 blast square leaves the room or covers the room entrance, exit or a checkpoint (a hit from afar can light it while the hero waits there): keep the entrance, exit and checkpoint **at least one blast radius (32 px) plus the hero's body clear of the centre**, and for a checkpoint ideally the 64 px that `forest-0-traps` leaves.
- **Choice rule (author, D42/D47):** on a required path an explosive offers *pay with ammunition from a distance (fireball, or a thrown stone for free)* or *the riskier bypass (jump over it)*; the required route never needs ammunition (D26). The validator guarantees the bypass; the author guarantees the player can see both answers. If you also want a higher ledge route, add it as an extra: the validator does not trace routes, so it never replaces the floor bypass.
- **Fairness numbers to keep:** the blast square reaches 32 px either side of the centre, so a hero 33 px or more from the centre (body edge) is safe; the warning leaves time to step out of it (a hero who walks on through a lit stump is clear when it goes off: tested in the forest-0 room). A hit from afar gives the full warning, so a hero standing within 32 px of the centre must still step away.
- **Hint advice for #235:** a position-only zone that starts at least 200 px before the trap and says the rule of the trap (not its state), e.g. "A touched stump smokes, then explodes. Jump it or run clear" (`HINT_TRAP_STUMP`, D97 formula). Where the lesson teaches the choice (the stone room in the tutorial plan), add a second sentence that a thrown stone or a fireball sets it off from afar, in all five languages, at most two lines at 200 px. Do not say "the stump is dark/lit"; zones know nothing about the trap's state.
- **Tests to copy:** `ExplosiveTrapsTest` (headless, fixtures `explosive-test.tmx`, `explosive-shield.tmx` and the `explosive-bad-*` maps: `corridor`, `pit`, `pillar`, `entrance`) and `TrapsLessonTest` for a real room. The `Bomb` type is separate (D51), has its own sprite (`trap-bomb`, D60) and shader blast, and still loads (`forest-2.tmx` has one) but is not used for new rooms.

## Transition and retry

Room exits are forward only. Before the boss, the healer can teleport the hero back to
the first entrance for another forward collection pass (#84). Seal physical boundaries with terrain so the
player reaches the exit trigger rather than walking into an inactive room.
The camera stays inside the current room; if the room is smaller than the
viewport, it is centred on that axis.

Entering RoomExit freezes the world, hero animation, and movement input. The change then runs
in four steps ([D123](DECISIONS.md#d123-a-portal-marks-every-room-exit-and-a-ripple-marks-the-room-change-552-553-2026-11-22)):

1. A **0.20 s** pause on a clear screen: the leaving ripple plays, and halfway through it the hero is gone into the portal.
2. The entire scene fades to black over **0.25 seconds**, then the next room and a fresh idle hero are
   placed at its entrance and the camera relocates while the screen is fully black. A fully black frame is
   always rendered, even after a long frame.
3. The scene fades in over **0.25 seconds** without the hero.
4. A **0.35 s** pause on the clear screen: the entering ripple starts as the fade ends and the hero appears
   **0.15 s** into it. Then play resumes, 1.05 s after the exit was reached, while the rest of the ripple plays out.

Entering the boss room after the healer's Fight choice has no leaving pause; steps 2 to 4 are the same.
There is no inter-room camera pan, loading screen, or level-save write. Timing uses real time,
independent of death slow motion. Pause freezes the change; Resume continues it. Restart Level and
leaving gameplay cancel it.

Death keeps its slow motion; after a death with a visible cause the silver thread pulls
Edgard back under a death fade and the camera snaps at black, after a pit the fast eased
camera return stays ([D81](DECISIONS.md#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)). It rebuilds the current room graph, restores that room's latest secured
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

<a id="every-room-gives-something-new-d72"></a>

## Every room gives something new (D72)

Rule: [D72](DECISIONS.md#d72-every-room-gives-the-player-something-new), from playtest remark F20
([PLAYTEST_1.md](PLAYTEST_1.md)). Each room introduces, develops or combines at least one element the player has not
met **in that form**, counted in play order (`KimeriaGame.LEVEL_NAMES`, then each map's room order):

- **Introduce:** the first sighting of a mechanic, enemy, trap, pickup, interaction or view.
- **Develop:** a known element under a condition that **changes the answer** (a crumbling support now over a lethal
  pit; a choice from afar that now costs ammunition). A known element on a new surface or in a new setting counts when
  it changes what the player does (Primal Planet study, #352, in GAME_DESIGN.md "Reference research").
- **Combine:** known elements whose solution needs both at once.

A new look with the same trigger and answer (a **reskin**: the thorn tuft after the root, the exploding root or the
Bomb after the stump, the rotten log after the falling platform) is not novelty, and neither is more of the same
geometry. Repeating known elements for mastery is welcome, as long as the room has something else too. Outside the
tutorial a room **introduces at most two elements**, and each is first met where it is the only unknown thing on
screen before it is combined (the "teach alone, then combine" rule of the teaching walkthrough below, applied in play
order). "Only unknown" never meant "only object": since [D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) a first meeting sits among known beats in a safe
pocket, and the room itself is dense (below). Lesson rooms keep their own stricter form (one lesson element, plus at most one trap: D33-D40, D48).
A calm, wordless beat after a dangerous one (#352) is a good role for a room, but it needs its own novelty, such as a
new view or reward.

**Workflow.** Write the room's card in [ROOM_CARDS.md](ROOM_CARDS.md) **before** building the map: role, purpose,
what it introduces, develops or combines (element ids from the card file's registry), the mastered-route and
first-learning targets of D19, the retry cost and the D26 notes. Then build the map with the recipes in this
document, measure, and fill the measured times in. The skill `kimeria-room-design` (`.agents/skills/`) is the
checklist. A new Tiled object type gets a registry row in the same change.

**Gate.** `RoomCardsTest` (`:core:test`; the Gradle test task declares the card file as an input, so a card edit
alone reruns it) fails when a shipped room has no card or a card names no room, a required field is empty, an element
id or a Tiled key is not in the registry, the room where an element is first met does not introduce it, a card
introduces something already met or develops or combines something not met yet, or a non-lesson room introduces more
than two elements without an `Overload reason` citing an issue. It cannot judge whether a develop changes the answer
or whether a new element is met alone: review that against the card. Since D105 it also checks the density and the
first meetings (see "Density and first meetings (D105)" below and [ROOM_CARDS.md](ROOM_CARDS.md)).

**Audit of the Forest (#351, 2026-11-16).** Verdicts are on the cards. Ten rooms keep (the tutorial's
`forest-0-wall` is watched: its wall repeats the ladder room's "press, the way opens"; `forest-1-route` and
`forest-1-guard`, added by #187/D67 and #104/D68, pass with a bat introduction and two combinations); two change,
both for overload and misplaced repeats rather than for too little novelty. `forest-1-traps`, the unchanged #260
course, introduces four elements and meets its first enemy in the same stretch (x 256-450) as two other first sightings,
the running escalators and the fireball refill, not on the falling platforms: measured, the RedMob wakes only once the
hero is back on the floor past them and then holds body x 352-372, 128 px beyond the last platform; its
lever-and-torch pair repeats `forest-0-lever` with no role: split the room itself so the RedMob is met alone first, handle that pair (ids 12/13) and revisit D58, [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375). `forest-entry` introduced six elements and held the D24/#95
practice objects that `forest-0` now teaches; [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376) removed them and split the room (see "forest-entry split (#376)" below).
`forest-entry` introduced four elements; [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417) ([D96](DECISIONS.md#d96-the-first-coin-is-met-in-the-tree-room-and-the-bat-ingredient-in-the-wall-room-417-2026-11-17)) took the Bat ingredient to `forest-walls` and put the level's first coin in `forest-1-tree`, so it introduces two.

**Done for forest-1 (#375, D85, 2026-11-17).** The first RedMob (id 11) and the refill (id 25) moved out of
`forest-1-traps` into a new first room, `forest-1-mob` (x 3520-4320, after the tree room on the map, first in the
`Rooms` list), where the mob is met alone and the refill comes into view only after it (`Forest1MobTest`). The
lever-and-torch pair (ids 12, 13) was removed. `forest-1-traps` now introduces two elements, the running escalators and
the puffball, met one screen apart, and its `Overload reason` is gone; the trap course x 616-1280 is unchanged.

### Density and first meetings (D105)

Rule: [D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) (#499). Every room keeps the player busy: about **one beat every 3 s** of the mastered route.

- **A beat** is one placed instance of a registry element the **required route** makes the player read and answer. The
  registry's **Beat kind** says what counts: a `hazard` and a `monster` once per instance; a `mechanism` once per lever the
  route pulls or hits, with everything it drives; a `platform` once per running platform the route boards or must pass
  while it moves; a `skill` once per obstacle, posed by map geometry (an object with `skin=branch` for a branch climb; the wall
  jump is gone, [D121](DECISIONS.md#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21)). A `pickup`, a `harmless` element and plain
  geometry never count. Two instances of one element whose spans (box, polyline, horizontal patrol) lie within 96 px of
  each other are one beat. Beats are listed in route order, read as rising map x.
- **The band.** With the zero-charge mastered time `T` and `N` beats: at least **3 beats** of at least **2 distinct
  elements**, at most **4.0 s per beat**, aim about **3 s**; under **1.5 s per beat** the card needs an `Overload reason`
  that cites an issue. A 9 to 12 s room holds 3 or 4 beats; a room at the 20 s of D19 up to six or seven.
- **A first meeting gets a pocket, not a room of its own:** a safe spot in view of the new element, outside every danger
  zone; no other lethal danger zone within 96 px of it along the route and nothing else lethal active in view from the
  pocket (known levers, lifts and ladders may share the stretch); among the room's first two beats, or a death at it
  replays at most 6 s; not combined on first sight. For a monster (met in play, D100) the danger zone is its whole reach,
  hearing included. `FirstMeetingPocketTest` pins the pocket: the hero stands at its x in the real camera view, is never
  hit, and sees the element act.
- **A skill's pocket ([D109](DECISIONS.md#d109-a-skills-first-meeting-has-a-pocket-in-view-and-calm-and-the-false-honey-fungus-has-a-trip-and-stand-pocket-500-499)).** A skill (a branch climb is the shipped one) never acts, so "seen acting" does not apply to a skill: its pocket is a
  spot where the **whole first obstacle is in the camera view** and **no enemy of the room stirs** during the wait
  (`FirstMeetingPocketTest.skillPocket(map, room, element, pocketX, geometryX)`: the hero stands 12 s, is never hit, the
  collision block (a wall or a one-way branch) at `geometryX` lies inside the view every frame, and no enemy, hazard or trap of the room stirs: enemies, lethal hazards, deadfalls, fungus, falling, exploding and thorn traps and puffballs keep their position, state and
  phase). The view check is trivial in a room narrower than the view; `aSkillPocketTooFarFromTheWallFails` shows it and the stirring check can fail.
- **Clearance counts what can kill ([D107](DECISIONS.md#d107-the-first-meeting-clearance-counts-what-can-kill-a-crumble-support-over-a-floor-is-not-a-lethal-neighbour-500-499)).**
  A crumble support (`FallingPlatform`, any skin) is a lethal neighbour only when what lies under it is lethal (no solid
  collision beneath its span: a pit; the rotten log over the pit in `forest-1-traps` still counts); over a floor it drops
  the hero alive onto it (D69). An object that is part of the first-met skill's own geometry (the rotten branches are
  `skin=branch` objects like the climb's sound ones) is not its own neighbour. The same relief decides whether a `rest` or
  `reward` room "holds nothing lethal".
- **A touch trap whose trigger lies outside its lethal band** (the deadfall: its trip vine is 54 px short of the swing
  band, D88; and the false honey fungus: its trigger zone reaches 64 px beyond its 24 px cloud, D63, [D109](DECISIONS.md#d109-a-skills-first-meeting-has-a-pocket-in-view-and-calm-and-the-false-honey-fungus-has-a-trip-and-stand-pocket-500-499)) has a
  **trip-and-stand pocket on the trigger**, a measured condition (`FirstMeetingPocketTest.tripAndStand`, asserted from the map by the deadfall and fungus pocket tests; `anExplodingStumpOrRootFailsTheTripAndStandCondition` shows the stump and root fail it): the trigger reaches at least a
  hero's body width (16 px) past the lethal band and the pocket stands at least that clear of the band. The deadfall, the false honey fungus and the thorn root and tuft (their zone reaches 64 px past the kill band; `aThornRootOrTuftMeetsTheTripAndStandCondition`) meet it; the exploding stump and root and the Bomb, whose trigger lies inside their blast, do not and are met among known beats as before. Decided by the author 2026-11-20: the measured rule decides, no trap is excluded by name. The deadfall's pocket: the hero stands on the vine, trips it, is never hit and sees the log swing past
  (`FirstMeetingPocketTest` signal `DeadfallTrap.phase()`, acting `SWING`); or stands in the fungus's zone short of its cloud and
  sees it swell and burst (signal `FungusTrap.state()`, acting `BURST`).
- **Times and retry.** D19 stands. Density fills a room, it does not lengthen it; a room whose retry passes about 15 s
  because of density is split, each half passing the band. A short room is merged or given more route, never padded.
- **Exempt, each earned:** `lesson` in `forest-0` and in the test's fixed list of lessons elsewhere (only `forest-1-lever`,
  D86), holding nothing lethal outside the tutorial; `boss` only in a room with the boss; `rest` and `reward` skip the
  minimum only in a room with nothing lethal, at most one per level.

**Workflow under D105.** On the card, before the map: list the beats in route order as `id@x`, divide the planned
mastered time by their number, and place each new element's first instance in its pocket. After building, write the
measured zero-charge time first in `Mastered route`, pin it in the route test within 0.1 s, write `Beat time` as
`seconds (TestClass)` naming that test, and for each first meeting add a method to `FirstMeetingPocketTest` that stands
the hero at the card's `pocket at x N` and names the room and the element.

**Audit (2026-11-19, verified against each room's route test).** Of the fourteen rooms the band applies to, seven passed
(`forest-1-traps` at 1.53 s per beat, `forest-1-route`, `forest-1-guard`, `forest-entry`, `forest-walls`, `forest-fissure`,
`forest-canopy`) and seven were too empty (`forest-1-mob`, `forest-1-deadfall`, `forest-1-spider`, `forest-1-bramble`,
`forest-1-climb`, `forest-1-hive`, `forest-1-tree`). The numbers are on the cards and in D105. The too-empty rooms were on the
gate's shrink-only list with `Below density`, and the forest-1 rework was tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500); the spider rooms were reworked with the
snail (#329, [#504](https://github.com/jlogicgames/edgard_in_kimeria/pull/504)) first, and the other five by batch 2 below ([#507](https://github.com/jlogicgames/edgard_in_kimeria/pull/507)), so **the list was empty** and the test deleted it (no shipped room is exempt). `forest-1-traps` passed by 0.03 s
(1.534 s per beat); batch 3 split it (below, 2.00 s per beat). Five rooms met a new element late, crowded or with no safe spot that shows it
acting (`forest-1-traps` for the puffball, `forest-1-guard`, `forest-entry`, `forest-walls`, `forest-canopy`), on a second
shrink-only list under the same issue; batch 3 (below, D110) took `forest-1-traps` and `forest-1-guard` off it and batch 4 (below, D109) cleared `forest-entry`, `forest-walls` and `forest-canopy` (once its thorn vine was retimed), so the second list is empty too: **#500 is done** (batches [#504](https://github.com/jlogicgames/edgard_in_kimeria/pull/504), [#507](https://github.com/jlogicgames/edgard_in_kimeria/pull/507), [#509](https://github.com/jlogicgames/edgard_in_kimeria/pull/509) and the forest-2 batch 4), and `RoomCardsTest` no longer carries either list.

### Reworked under D105, batch 2 (#500, D107, cards approved by the author 2026-11-19)

Five rooms of forest-1, no room widened, no new element, monster or trap type, no hint text; every added beat is an element met
earlier in the level. Times are the zero-charge mastered routes of the room tests, pinned within 0.1 s.

| Room | Beats (id@x) | Time | s/beat | What was added |
| --- | --- | ---: | ---: | --- |
| `forest-1-mob` | redmob@3776, falling-branch@3984, touch-explosive@4112 | 6.75 s | 2.25 | a 32 px log (id 176, x 3728-3760) holds the RedMob, `offNeg` 3 to 4; a `FallingBranch` (id 177); an `ExplosiveStump` (id 178) |
| `forest-1-deadfall` | deadfall@5184, falling-stone@5392, redmob@5472 | 5.85 s | 1.95 | the deadfall and its trunk 64 px left; a `FallingStone` (id 179); a RedMob (id 180, `offNeg` 3) before the exit |
| `forest-1-bramble` | proximity-thorn@5872, falling-branch@6000, bramble@6160 | 9.47 s | 3.16 | `RoomStart` 5632 to 5760, `Bramble` `phase` 1.28; a `ThornRoot` (id 181, `zone` 64); a `FallingBranch` (id 182) under the shelf |
| `forest-1-hive` | hanging-hive@6440, hanging-hive@6604, spider@6752 | 8.85 s | 2.95 | a ledge `Spider` (id 183, x 6736, y 192, `offNeg` 0, `offPos` 1); its reach x 6697-6823 stays 9 px short of the exit, the strip x 6624-6697 is the bait place |
| `forest-1-tree` | none: a `reward` room | 4.95 s (fast line) | - | the beetle (id 147) left; nothing lethal; the level still ends on the crown (D80) |

- First meetings pinned in `FirstMeetingPocketTest`: `forest1MobTheRedMob` (x 3716, behind the log, acting `RUN`),
  `forest1DeadfallTheDeadfall` (x 5120 on the vine, acting `SWING`), `forest1TrapsTheEscalator` (x 240; renamed `forest1EscalatorsTheEscalator` by batch 3; the falling platforms
  beside it no longer count, D107), `forest1BrambleTheBramble` (x 6170, now beat 3 with a 5.42 s retry) and
  `forest1HiveTheFloorHive` (x 6420, unchanged). `forest-1-tree` needs none: a `reward` room is calm.
- Deviations from the design proposal, found in the build: the thorn root's `zone` is 64 (the load check wants at least 64 px a
  side, not 32); the hive spider sits at x 6736 with range 6736-6784 (a spider at x 6752 with `offPos` 2 would reach the
  exit, x 6832, and the load check refuses it); the measured times differ from the estimates (mob 6.75 s against about 8.1,
  deadfall 5.85 against 6.2, hive 8.85 against 9.6, bramble 9.47 against 9.5) because the walk is faster than estimated.
- Deaths (retry cost): mob 6.17 s at the stump (2.5 s on the log); deadfall 5.18 s under the stone, 4.4 s past the mob, 2.00 s in
  the swing; bramble 5.42 s blind climb; hive 3.1 to 7.5 s the hives, 6.25 s the spider.

### Reworked under D105, batch 3 (#500, D110, cards approved by the author 2026-11-20)

Two rooms of forest-1 take the last forest-1 rooms off the first-meeting list (`FIRST_MEETING_GRANDFATHERED` 5 to 3: only
`forest-entry`, `forest-walls` and `forest-canopy` remain). Known elements only, no new monster or trap type, no hint text.
Coordinates are the map's (Y grows downward, floor top y 320); times are the zero-charge mastered routes of the room tests,
pinned within 0.1 s.

| Room | Beats (id@x) | Time | s/beat | What changed |
| --- | --- | ---: | ---: | --- |
| `forest-1-escalators` (new, x 0-640) | crumble-support@160, running-platform@304, lever-wall@480 | 5.48 s | 1.83 | the first half of the old trap course; lever 33 and its overhead escalator 34 removed (a repeat with no role) |
| `forest-1-traps` (x 640-1280) | puffball@768, redmob@928, crumble-support@1088 | 6.00 s | 2.00 | the second half: the puffball moved 944 to 768 (`phase` 0 to 3.4), the RedMob 864 to 928, thorn tuft 35 removed |
| `forest-1-guard` | puffball@2375, redmob@2394, beetle@2632 | 6.50 s | 2.17 | `RoomStart` 61 x 2176 to 2208, lever 63 x 2224 to 2256, puffball 77 `phase` 2.2 to 2.52: every route 0.32 s shorter, the beetle's retry 6.08 to 5.82 s |

- Split (map): `Room` 220 `forest-1-escalators` (0, 0, 640, 368) with `RoomStart` 221 (32, 272) and `RoomExit` 222 (608, 272, 16 x
  48), inserted in the `Rooms` layer before `Room` 43 (the layer order is the play order); `Room` 43 `forest-1-traps` is
  (640, 0, 640, 368) with `RoomStart` 44 (656, 272); walls 223 (x 624) and 224 (x 640), floor 3 cut to x 0-640 and a new
  floor 225 at x 640-1024; the seam tiles (columns 39 and 40 of the `Background` layer) copy columns 219 and 220, another forest-1 seam, so
  both solid walls are drawn; the foreground group of the old room was split into `foreground-forest-1-escalators` and `foreground-forest-1-traps` (D110); the bushes 41 and 42 moved to x 560 and 832. No other room moved; ids 33, 34, 35 are gone.
- Puffball first sight (D84): the clock starts at the room's entry at `phase` 3.4, so the cloud is out at the entry, sinks at
  0.6 s, the straight walker is in its column at 0.8 to 1.13 s in the calm (no wait) and the swell comes at 2.8 s, the next
  cloud at 3.6 s. The guard room's puffball is the opposite on purpose: its first death is deterministic (D68).
- First meetings pinned in `FirstMeetingPocketTest`: `forest1EscalatorsTheEscalator` (x 240; was `forest1TrapsTheEscalator`),
  `forest1TrapsThePuffball` (x 744, acting `CLOUD`: the puffball does not move or act, so `signals()` reports its phase) and
  `forest1GuardTheBeetle` (x 2550, the tunnel's mouth, acting `NOTICE`).
- Deaths (retry cost): the trap course 6.58 s at the log (12.73 s before the split); the guard room 1.37 s in the cloud on the
  first walk-in and 5.82 s in the beetle's burst after the mastered melee.
- Changed assertion: the guard room's entrance-to-cloud clearance, 140 to 117 px (D68, Update; the rule it stands for holds).

### Reworked under D105, batch 4 (#500, D109, cards approved by the author 2026-11-20)

The three forest-2 rooms on the first-meeting list. No room added, widened or moved; no new element, monster or trap type; no hint
text. Times are the zero-charge mastered routes of the room tests, pinned within 0.1 s. Map ids are in `forest-2.tmx`.

| Room | Beats (id@x) | Time | s/beat | What changed |
| --- | --- | ---: | ---: | --- |
| `forest-entry` | yellowmob@192, bat-enemy@352, leg-hold@496, bat-enemy@672 | 8.37 s (6.83 s with the Bomb, before D128) | 2.09 | the step (Collisions id 6) became a 32 px cover at x 112-144, drawn with the step's end caps (tiles 26, 28, row 19, cols 7-8); the YellowMob (id 50) stands on the floor (Tiled y 304), `offNeg` 6, `offPos` 0 (it wakes for a hero centre in x 96-192, stops against the cover); Bat 41 moved from x 272 to x 352; the twig moved from the old step to col 14, on the floor at pit 1's lip |
| `forest-walls` | branch-climb@880, yellowmob@1040, redmob@1136 (until #545: wall-jump@912 in the first place, 7.45 s, 2.48 s per beat) | 5.55 s | 1.85 | YellowMob 16 moved from x 832 to x 1040 (`offNeg` 0, `offPos` 1); RedMob 60 `offNeg` 5 to 2 (wakes from x 1104); bush 132 moved from x 1040 to x 944 |
| `forest-canopy` | redmob@1472, false-honey-fungus@1672, thorn-vine@1896, touch-explosive@2064, falling-stone@2224 | 9.48 s | 1.90 | Bat 65 (x 1760) removed; the vine's `phase` 0 to 2.77 s |

- First meetings pinned in `FirstMeetingPocketTest`: `forestEntryTheYellowMob` (x 100, behind the cover, acting `RUN`; Bats 41 and 43 are
  known and in view, neither reaches it), `forestCanopyTheFalseFungus` (x 1640 in its
  trigger zone, acting `BURST`, D109) and `forestCanopyTheThornVine` (x 1848, acting `SWING_LEFT`).
- `forest-entry`'s mastered route jumps the cover and stomps the YellowMob, which stands about 40 px short of the cover when the hero is
  airborne (it stops chasing a hero who is not at its height); the pocket test shows the mob charging a hero who stays behind the cover.
  The all-coins route (12.02 s since D128; 12.05 s before it, 10.40 s before that) waits on the ground while Bat 41 is within 40 px (`RoomReplayTest.batClose`): the coins at
  x 336 (floor) and x 368 (platform 3) lie under and beside the Bat's column: a hero who wants them waits under the Bat, a risk-and-reward choice.
- `forest-walls`' route swords the YellowMob rather than stomping it from wall 45 (7.45 s against the estimate of about 8.8 s). Since #545 (D116) the wall jump is gone: the route climbs a tree, leaps on to the second wall and stomps both mobs (5.55 s, `ForestWallsRoute`; `ForestWallsTest` asserts that both die to the stomp and bounce, never the sword), and the room has no first-meeting pocket.
- **The thorn vine's retry (phase retimed).** The vine is now beat 3, so D105 asks a retry of at most 6 s. With `phase` 0 the cheapest death
  was **6.45 s** (the Phase 1 estimate of about 5 s was the mastered hero's arrival, not a death), so the vine's `phase` (id 127) went from 0 to
  **2.77 s**. Scanned over 0 to 4.4 s in 0.1 s steps (0.01 s steps from 2.65 to 2.85) with a walker held 0 to 269 frames at the entrance: slack and retry
  trade one frame for one frame. At 2.77 s the mastered pace still passes with no wait (a walker held 0 to 35 frames, 0.6 s, passes; was 0.85 s at
  phase 0) and the cheapest death is **5.93 s** (held 36 frames; phase 2.9 would give 0.45 s and 5.80 s, phase 2.7 0.65 s and 6.00 s, no margin), hero
  hit (`Forest2TrapsTest.theCheapestDeathAtTheVineOnTheMasteredPaceIsPinned`, asserted at most 6 s). The vine swings all the time and is in view (any part of its sweep) for
  **4.90 s** before the mastered hero's body reaches its swept area, more than its 4.5 s period, swinging both ways to its full amplitude
  (`Forest2TrapsTest.theVineIsSeenSwingingForAFullPeriodBeforeTheMasteredHeroCanReachIt`); its whole sweep is in view for 2.27 s of that. The mastered route stays 9.48 s (1.90 s per beat); the
  fungus pocket and every other pin are unchanged. `forest-canopy` left `FIRST_MEETING_GRANDFATHERED`, which is now empty (ceiling 0, with batch 3).

## Leg-hold and lance root: the two replacements (#561, #562, D128, D129)

`forest-entry`: the Bomb (id 54) became a `LegHold` (same id 54) on the floor at x 496-528, where the stair's jump lands (the ledge x 416-480
is not on the required route, which walks the floor under it). Bat 43 moved from x 512 to x 672 (`offNeg` 1, `offPos` 0: patrol x 656-688)
so that the leg-hold's first meeting has 96 px of clearance. The mastered route stands at x 478 (hero centre, 16 px short of the teeth)
until the jaws snap empty, then walks over them shut: **8.37 s** required (6.83 s before, `RoomReplayTest`, three identical attempts), 4 beats
at 2.09 s per beat, 12.02 s all coins. The leg-hold is beat 3, reached at about 4.6 s (pinned by `RoomReplayTest.theLegHoldRetryIsAboutFourSeconds`) (the retry of a death at its teeth); its pocket is pinned by
`FirstMeetingPocketTest.forestEntryTheLegHold`. The `Bomb` is gone from the map; its two gameplay tests run on `bomb-test.tmx`.
`forest-fissure`: both `EmberFissure` objects (ids 151, 152) became `LanceRoot` with the same properties, so every number of the room is unchanged
(`ForestFissureTest`, `RoomReplayTest` 9.30 s). Cards: [ROOM_CARDS.md](ROOM_CARDS.md), [TRAPS.md](TRAPS.md).

## forest-entry split (#376, D72)

Objects 116-119 (practice stone, carryable torch, practice lever and its torch) and 123/124 (ladder and its lever) are
removed from `forest-2`, and `forest-entry` is split at **x 736**: `forest-entry` x 0-736 (exit x 704-720, wall x
720-736, its D24 `controls` hint zone, Hint 126, removed) and the new `forest-walls` x 736-1280 (wall x 736-752, entrance x
752, 272). The 56 px of floor between the Bomb's blast (x 632-696) and the second YellowMob's patrol could not hold an
exit, two walls and a safe entrance, so that YellowMob's left patrol is shortened (`offNeg` 5 to 1, left end x 816).
The floor block x 608-1280 is cut at x 736; trunk tiles mark the new seam as at the other room seams; bush 131 moves
from x 700 to x 768 (it sank into the new wall). The Bat ledge (x 416-480) is still reached from the platform stair (it no longer holds the Bat since #417);
the collection-return replay collects the Bat in `forest-walls`. Cards: [ROOM_CARDS.md](ROOM_CARDS.md).

Measured (`RoomReplayTest`, real input, hazards on, three identical attempts): `forest-entry` **6.80 s** required,
**10.40 s** all coins; `forest-walls` **8.80 s**; `forest-canopy` unchanged 9.48 s; collection returns entry 6.12 /
6.12 / 6.12 s (it was 6.72 / 6.12 / 6.12 s while the Bat stood on the entry ledge; the Bat left in #417), walls 7.32 s on every trip (the Bat is now taken there, on the way), canopy 9.62 / 9.48 / 9.48 s. The worst retry in the Forest drops from about 16 s to
about 9 s. `forest-2.tmx` SHA-256 after #417 (Bat moved, nothing else): `52391071515f322a082b41a9cc72610736d28e0407a790b4b0136f06047c59ea`. The carry, lever and
ladder hint tests that used the removed objects run on `core/src/test/resources/tiles/practice-test.tmx`,
a small fixture with the removed practice objects (116-119), the ladder (123/124) and a `controls` zone in its first room. Checked in the running game (camera at the seam, tile look) by playtest 2 and the author, 2026-11-24: [#428](https://github.com/jlogicgames/edgard_in_kimeria/issues/428).

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
coins. The `forest-1` trap course has no collectables, so it needs no separate detour (the route room's refill detour is measured in its own section).

| Level / room | Width | Required route | All room coins | Result |
| --- | ---: | ---: | ---: | --- |
| `forest-1` / `forest-1-mob` (room 1 since #375, D85, 2026-11-17) | 800 px | 6.75 s, log jumped, mob by sword, stump jumped (6.97 s before the #500 rework) | None (one fireball refill) | Within 20 s |
| `forest-1` / unmarked room (the old 1280 px trap course, 12.27 s since the D58 traps, split by #500, D110) | 1280 px | 11.58 s (historical) | None | Within 20 s |
| `forest-1` / `forest-1-escalators` (room 3 since #500, D110) | 640 px | 5.48 s | None | Within 20 s |
| `forest-1` / `forest-1-traps` (room 4 since #500, D110) | 640 px | 6.00 s | None | Within 20 s |
| `forest-1` / `forest-1-lever` (room 3 since #418, D86, 2026-11-17) | 640 px | 8.43 s (ride, pull, drop, climb) | None | Within 20 s |
| `forest-1` / `forest-1-route` (room 4 since #418; #187, D67, 2026-11-15) | 832 px | 11.02 s, bat by sword (18.32 s with the refill detour) | None (one fireball refill) | Within 20 s |
| `forest-1` / `forest-1-guard` (room 5 since #418; #104, D68, 2026-11-15) | 768 px | 6.50 s timed melee (the floor); fireball 5.82 s; roof 7.40 s (6.83, 6.15 and 7.67 s before #500 batch 3) | None (one optional fireball refill on the roof) | Within 20 s |
| `forest-3` (now `forest-2`) / `forest-entry` | 1280 px | 17.60 s | 20.27 s | Within 20 / 30 s |
| `forest-3` (now `forest-2`) / `forest-canopy` | 672 px | 5.17 s | 5.47 s | Within 20 / 30 s |

The revised canopy is 608 px shorter than its previous 1280 px layout (47.5%).
Its required route has no bomb or quicksand; the ascending platforms and their
optional pickups remain. The first two rooms already meet the budgets and need
no additional split for replay cost. The author reviewed the earlier test version
in play (2026-10-20, #73/#75). The later teaching-layout spacing has a separate
uncompleted human check; see
[Teaching order and walkthrough (#73/#75)](#teaching-order-and-walkthrough-7375).

The required routes run forward, jumping obstacles and hazards, using normal
sword attacks near grounded enemies and wall jumps at tall walls (the wall jump was removed later, [D121](DECISIONS.md#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21)). In `forest-1`,
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
Texture/font rendering and camera drawing are omitted; the shipped `forest-1.tmx`
is loaded as is (until #430 an alias skipped its decorative sky and rain).
Map contents and all gameplay objects are retained.
This measures the replay cost at 60 fps. The author has since played the rooms by hand and
accepted visual readability and input comfort for the test version (2026-10-20, #75);
behaviour at other frame rates is deferred to [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).
When map geometry or gameplay timing changes, rerun the affected automated routes
and record new timings as part of that change.

Measured map SHA-256 values:

- `forest-3.tmx` (now `forest-2.tmx`): `bf227d733af41d97f575b1d16bcebb6cf408590b26f19510942ffc19f2b88f96`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

## Completed test-version review and historical evidence

### Artifact checks (#72)

The author accepted the existing Bat and Mushroom positions as final for the MVP on
2026-10-19: Bat is object 94 at (448, 240), Mushroom is object 95 at (1728, 208) in
`forest-3.tmx` (now `forest-2.tmx`). Keep these placements; no relocation or additional artifact challenge
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

Actual desktop capture follow-up on 2026-10-14: running `forest-3` (now `forest-2`) at a 0.001 s
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
| `HangingHive` | 24×32, top edge is the bough | `period` 4.0 s, `phase` 0 s, `curtain` 1.0 s | shakes 0.8 s (1.2 s assisted), then stingers fall from the mouth (top + 26) at 240 px/s for `curtain` s to the first solid under it; an 8 px band (D90) |
| `EmberFissure` | 16×12, top edge 4 px above the floor line | `period` 3.5 s, `phase` 0 s, `jet` 1.0 s (0.6-1.5), `height` 96 px (48-128, multiple of 16) | glows 0.8 s (1.2 s assisted), then an 8 px fire column rises from the floor line in 0.1 s, holds, sinks in 0.2 s; through one-way platforms, cut by a solid block (D91) |

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

`HangingHive`: `period >= curtain + depth / 240 + 1.2 + 0.8`, where depth is the band from the mouth to the solid (at least 32 px); the load fails otherwise. Over open floor the pass takes about 0.2 s; over a ladder the climb must fit between the last stinger leaving the foot and the next head reaching the top (2.94 s for a 112 px ladder, a 182 px band and a 4.5 s period, as in `forest-1-hive`; measure any new layout with the climber in `HangingHiveTest`). From the floor a stone reaches the body only by a jump-throw; a fireball only from a standing place level with it.

`EmberFissure`: `period >= jet + 1.2 + 0.8`; the load fails otherwise, and also without a solid floor (not a one-way platform) under all 16 px at the floor line, or when a solid block above cuts the column below 32 px. Over open floor the pass takes about 0.2 s and the way is open for `period - jet` (2.5 s at the defaults). A one-way branch over the fissure is pierced by the jet: a hero who waits beside the 8 px column and crosses right after a jet climbs over it with time to spare (`EmberFissureTest`, the branch step: 3.75 to 6.73 s after any start delay); measure any new layout with that climber.

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

**Historical order (D72 audit, 2026-11-16).** This walkthrough was written when `forest-2` was played before
`forest-1`. Since #193/D32 the play order is `forest-0` → `forest-1` → `forest-2`, so `forest-1-traps` now meets the falling
platforms, then the RedMob beside the first running escalators and fireball refill. The rule "teach
alone, then combine" stands (it is part of D72); the `forest-1` layout below is corrected by [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375).
The `forest-entry` tables below describe the map after [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376) (2026-11-17): the practice objects are gone and
the room is split at x 736 into `forest-entry` and `forest-walls`.

Rule for the two levels: teach each of stomp, sword, falling platforms and bombs where
it is the only new thing on screen, on forgiving ground and without a required timing
test, then combine. `forest-3` (now `forest-2`) (level one) teaches; `forest-1` (level two, the finale)
combines. Optional routes stay separate from required progression; nothing here is
tuned around coins, and no coin or artifact was added, moved or removed.

Current teaching-layout identity (`9d5e630`): `git hash-object` of `forest-3.tmx` (now `forest-2.tmx`) is `619bf64b559a`, of `forest-1.tmx`
`44fbc6aa95c3`. Positions are map pixels (16 px per tile; SpawnPoints carry a -16 px
layer offset that this text already includes in the visible position).

### `forest-3` (now `forest-2`) / `forest-entry` (required route, left to right)

| x | What appears | Role |
| ---: | --- | --- |
| 64 | Start. The first mob's zone begins 4 tiles ahead. | Safe entrance |
| 112–144 | A 32 px earth cover (Collisions id 6, since #500, D105) | The mob stops against it |
| 192 | YellowMob (id 50) on the floor, wakes for a hero centre in x 96–192 | **Stomp**. Jump on it from the cover or over it, or K. |
| 240–320 | A plain pit (80 px; the quicksand pool became a pit, #486), vertical Bat enemy (352 since #500); optional one-way steps to the coins | Pit + Bat enemy |
| 416–480 | One-way ledge (id 122), reached from the stair; empty since #417 moved the Bat ingredient to `forest-walls` (the ladder, id 123/124, is removed by #376) | Scenery |
| 512–608 | Horizontal Bat (patrol ends at about 608); a second plain pit at x 560-608 (48 px) | Same, repeated |
| 656 | **Bomb** (id 54) | **Bomb**: flat ground 48 px past the second pit, 0.8 s warning (1.2 s with Assist), 64 px blast (x 632-696) |
| 704–736 | RoomExit (x 704-720), room wall (x 720-736) | End of the room |

### `forest-2` / `forest-walls` (x 736-1280, since #376)

| x | What appears | Role |
| ---: | --- | --- |
| 752 | Start, beside the room wall (x 736-752) | Safe entrance |
| 832-912 | Tree A (trunk x 864; branches A1 x 880-912 y 272, A2 x 832-864 y 224, A3 x 880-912 y 176) | Branch climb, a develop: the top branch is the take-off for the leap (#545, D116) |
| 912, 992 | Two tall walls, no longer grabbable | Plain geometry; the leap from A3 crosses the first and lands on the second (take-off x 882-908), the Bat on its top |
| 944-992 | Tree B in the gap (trunk x 976, branch B1 x 944-976 y 272) | The way out of the gap, and a second way to the second wall's top |
| 1040 | YellowMob (id 16), zone x 1040–1056 (moved from x 832 by #500) | Stomp repeated, at the foot of the second wall, read from above |
| 1136 | RedMob (id 60), zone 1104–1216 (`offNeg` 2 since #500), just before the exit | Sword or stomp (the mastered route stomps it, #545); first met in `forest-1` |

Foreground (#545): the room's `roots-curtain` moved from x 20 to x 290 in `tools/parallax_game.py` (`FOREST_2`). At x 20 it hung over branches A1 and A2 (room x 96-176) and `parallax_mock.py --check --game` failed rule B ("covers Platform"); at x 290 it covers nothing. The installer also renumbers every foreground object id after the map's highest object id (198-229 became 236-267): its output must equal the map byte for byte (`test_parallax_game`), and no test or doc names those ids.

(Historical, #73.) The bomb used to sit 4 tiles from the start inside the first YellowMob's patrol, so stomp
and bomb were the first two things met and overlapped. The bomb is now about 470 px
beyond the first mob and the bat patrol and the second mob's zone were pulled back so that
the blast square (about 624–688) and a running escape of about 64 px overlap neither.

### `forest-3` (now `forest-2`) / `forest-canopy`

| x | What appears | Role |
| ---: | --- | --- |
| 1344 | Start | Safe entrance |
| 1440–1488 | One-tile step; RedMob (id 68), zone 1408–1488 | Sword again, now beside terrain |
| 1488 | Optional one-way platform | Start of the optional climb |
| 1568 | **FallingPlatform** (id 115, was a fixed platform) holding coin 87 | **Falling platform, alone**: 1 s warning (1.5 s with Assist). Ground is 80 px below; isolated from bombs and nearby walking enemies at spawn; a bad landing can spend the optional ascent |
| 1648–1728 | One-way platform and the Mushroom | Optional reward |
| 1760 | (Bat 65 removed by #500, D105) | |
| 1872 | RoomExit | |

The collapse is one-way and remains secured after room exit or healer return. A player
who lets it fall before reaching the upper route may lose the optional Mushroom/coin ascent
until Restart Level. The required route never needs the platform. This map fact makes
the one-shot cue and optional-route consequence an explicit manual fairness check under
D26; older teaching-layout route results below do not establish that acceptance.

### `forest-1` (level two; traps added by #260/D58, see "forest-1 trap placement plan")

Since #375 the first RedMob is met alone in `forest-1-mob`, the level's first room ([D85](DECISIONS.md#d85-forest-1-opens-with-the-first-redmob-alone-in-a-room-of-its-own-375-2026-11-17)); the
paragraph below describes the trap course before that change, when RedMob id 11 stood among its escalators.
Falling platforms (x 96–224, y 272), the RedMob patrol (192–512) that covers them, the
escalators and the trigger-opened wall at 480–528 are the first time those pieces
are combined. The falling platforms are now a second sighting rather than the first.
(Measured 2026-11-16 for D72: the patrol numbers are the range in which the hero's centre wakes the mob, not where it
walks. The mob reacts only once the hero is back on the floor past the platforms, then stays at body x 352-372, right
of the 16 px block at x 304-352, 128 px from the last platform; the platforms and the mob are met one after the other.)
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

- `forest-3.tmx` (now `forest-2.tmx`): `83adfbbf997718092e33c8b4ac952483f4edad244d1864f26afb4f735de4436d`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

| Level / room | Required route | All room coins |
| --- | ---: | ---: |
| `forest-1` / unmarked (the old trap course; 12.27 s since D58, split by #500, D110) | 11.58 s (historical) | No coins |
| `forest-1` / `forest-1-escalators`, `forest-1-traps` (#500, D110) | 5.48 s, 6.00 s | No coins |
| `forest-1` / `forest-1-lever` (room 3, #418) | 8.43 s | No coins |
| `forest-1` / `forest-1-route` (room 4 since #418, #187) | 11.02 s | No coins (refill detour: whole room 18.32 s) |
| `forest-1` / `forest-1-guard` (room 5 since #418, #104) | 6.50 s (timed melee; roof 7.40 s with the refill; 6.83 and 7.67 s before #500 batch 3) | No coins |
| `forest-3` (now `forest-2`) / `forest-entry` | 16.27 s | 20.00 s |
| `forest-3` (now `forest-2`) / `forest-canopy` | 5.00 s | 5.00 s |

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
driver confirmed, at that review, that the wall-jump references in the route descriptions were current
behavior. The wall jump was removed afterwards ([D121](DECISIONS.md#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21)).
This review changed documentation only. It did not rerun the full core suite or
perform a new visual/human playtest; it does not close the deferred release checks.

## Directional-controls practice revision (#95)

**Removed from the shipped map (2026-11-17, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)).** Objects 116-119 described below are no longer in
`forest-2`: `forest-0` now teaches pickup, throw and the lever, so they were repeats (D72). The behaviour this section
documents is unchanged and is still tested, on `core/src/test/resources/tiles/practice-test.tmx`,
a small fixture with the removed practice objects (116-119), the ladder (123/124) and a `controls` zone in its first room. Its `controls` zone (Hint 126) is removed too, so `forest-2` has no hint zone. The text below is the historical record.

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

2026-10-22, `forest-3.tmx` (now `forest-2.tmx`) SHA-256 `9d74c49bf53afefbaa6c86d020dffc0d4288a37386f911dbfb7e0261decd5b06`:
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

The production-input replay now chooses a fresh Up for mechanisms (D126; an Up + Action chord before) and plain
Action for sword combat. With live hazards and no fireball spending, three identical
attempts each remain: entry 16.27 s required / 20.00 s all coins; canopy 5.00 s /
5.00 s; forest-1 5.32 s required. Collection returns: entry 14.78 / 14.25 / 14.25 s,
canopy 5.13 / 5.00 / 5.00 s. These still check the 20/30-second gates. This does
not establish all-artifact coverage, new human readability or physical-device
acceptance. The optional practice objects are not required route steps.

### Close interaction revision (2026-10-22)

Current `forest-3.tmx` (now `forest-2.tmx`) SHA-256:
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
requests a visible 16×16 floor lever at the zone's bottom centre and must link
exactly one `Actionable` Torch by name. Selection highlights this lever, while the
linked Torch (119) remains at its independently authored wall position. The state
comes from the torch: handle right/on, handle left/off. No new object IDs or collision geometry are
introduced. A first visit or fresh Level uses authored state; a room retry or collection
return restores the secured torch state, from which the lever is rebuilt.

Standing inspection (D25) was removed (D111): the camera is the ordinary room-clamped one.


### Fireball refill revision (#98, 2026-10-25)

Local map identities (pending commit/review):

- `forest-3.tmx` (now `forest-2.tmx`) SHA-256: `3809d6b20bbb0672a8ca332ef036ac74b0a510790afad7968adb1ebdbe23f912`.
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

**Removed from the shipped map (2026-11-17, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)).** Ladder 123 and its lever 124 are gone from
`forest-2` (`forest-0-ladder` teaches the ladder). Ledge 122 stays and is reached from the platform stair, so the Bat
ingredient is still collected (`RoomReplayTest`, collection return). Ladder hint tests run on `practice-test.tmx`.

`forest-3.tmx` (now `forest-2.tmx`) SHA-256 `bdd719de7af0ccc56302322777072f48e3316cd3ccffc1aaa621876d61cc32c3`. Added to `forest-entry`:
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

Current `forest-3.tmx` (now `forest-2.tmx`) SHA-256: `df609760fe2f15f8d34995117c579c99ab19052fee0819b4295a1b3af6827a2a`.
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

Adam Konter (project author) played the whole forest-0 tutorial on desktop (keyboard only) and also checked the web build, against `forest-0.tmx` SHA-256 `0faee8732d8695c557fab6f1f846b4619cd7366d75ca35f2233de79ccc7037af` (the revision before #233 replaced the bomb with an exploding stump). All six rooms (lever, ladder, wall, platform, stone, traps), both healer dialogues (start tale and farewell) and the transition into forest-1 were checked, with no remarks (OK). **Not checked in that session:** gamepad, touch controls, native-speaker quality of the de/pl/uk/be translations (for the 2026-11-06 desktop report see below). No novice playtest (#129–#131) has been done. Mastered route times above stay headless author checks.

**Mobile browser (same day, same author):** the web build was also played on an iPhone in the browser, and all the tutorial screens were completed with the on-screen touch controls, so touch controls are now partly checked on one device (one phone, one browser). Gameplay worked; four presentation problems are filed as [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226) (portrait control placement, no fullscreen, landscape shows only half the game, screen auto-lock). #226 is closed (2026-11-07): the author confirmed the fixes on a real iPhone in Safari (portrait and landscape layout, sharpness, light touch controls, screen stays on, pause button); fullscreen was dropped as not needed. Android and tablets remain in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).

**Desktop keyboard and gamepad, both layouts (2026-11-06, same author):** Adam Konter reported that he checked the desktop version with keyboard and with a gamepad, in both the right-handed and the left-handed layout. He did not say which build, which rooms or whether `forest-0.tmx` was the current revision, so this is not recorded as an acceptance of any specific revision. **Still not checked:** touch (beyond the partial iPhone check above), native-speaker quality of the de/pl/uk/be translations, novice playtests (#129–#131).
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

## forest-1 trap placement plan (#260, D58)

Approved by the author on 2026-11-09 and implemented ([D58](DECISIONS.md#d58-forest-1-traps-and-a-second-redmob-three-traps-and-one-enemy-in-the-open-stretch)). Issue:
[#260](https://github.com/jlogicgames/edgard_in_kimeria/issues/260) (stays open for the author's check in the running game; its checklist updates this section). Coordinates are
the map's: **world Y grows downward**, the floor top is y = 320, `y` of an object is its top edge
(`y = 320 - height` for a trap standing on the floor), a larger y is lower on screen.
Map hashes: before the change `forest-1.tmx` SHA-256 `4363b8d4cb3e80ef6990338aa8de03ab5166422e8b890463ef37ec88be5fe400`
(`git hash-object` `fb591412b9de`); after `e4503a4c845355ddadc5d74b9d9b8b9176d1f611e5c502b632f3648bec035013`
(`0b199000a3fe`) before the final PR; recompute with `shasum -a 256 assets/tiles/forest-1.tmx` if the map changes again.
Preview with the placed objects and zones: [forest-1-trap-placement.png](references/forest-1-trap-placement.png) (a schematic drawn from the map and tileset, not a game screenshot).

### What the level is

**Update (#500 batch 3, D110, 2026-11-20):** the course was split at x 640 into `forest-1-escalators` (x 0-640) and `forest-1-traps` (x 640-1280); the thorn tuft 35 and lever 33 with escalator 34 were removed and the puffball (x 768) and RedMob (x 928) moved; the coordinates in this plan are historical (see "Reworked under D105, batch 3").

Room 2 of 5 since #375 (D85; room 1 of 2 since #187, D67): `forest-1-traps`, x 0-1280, which lost its first RedMob, its refill and its lever-and-torch pair to the new first room `forest-1-mob` in #375 (the coordinates below are otherwise current), whose `RoomStart` is the old `Player` spawn x 32 (so every retry restarts there, as when the level was one unmarked room) and whose `RoomExit` (1216, 272, 32 x 48) stands where the `Checkpoint` was; the checkpoint moved to the route room appended after it. Before #187 this was the whole level, 80 x 23 tiles (1280 x 368 px). No hints (`TutorialLevelTest`). The first part is unchanged apart from what #375 moved or removed: four `FallingPlatform`
(x 96-224), escalators, lever 272 and the `Wall` at x 480-528, lever 544. The stretch **x 580-1200 was empty floor**
and now holds, one thing at a time:

```
x/16   0         1         2         3         4         5         6         7
       01234567890123456789012345678901234567890123456789012345678901234567890123456789
y224   #..............t..............WWW..............................................#
y240   #..................EE.RRR.....WWW..............................................#
y272   #.....pppppp..................WWW.EE........................................C..#
y304   #.@.....l.BBB....l.BBB........WWW.l........^..........M....*...............BB..#
y320   ================================================================    LL   =======
x px   0   160   320   480   640   800   960  1120      (1 char = 16 px)
```

`@` spawn, `l` lever, `W` wall, `p` falling platforms, `E` escalators, `R` the first RedMob, `C` checkpoint,
**`^` thorn tuft (col 43), `M` new RedMob (col 54), `*` puffball (col 59), `LL` rotten log in the pit (cols 68-69, pit cols 64-72)**. (Positions in the table are authoritative; the strip is approximate; `C` is the room exit since #187.)

| Section | What | Object | x (px) | y (px) | Size | Properties | Reach (the area it owns) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T1 | Bristling thorn tuft (proximity, D44) | `ThornTuft` (id 35) | 696 | 304 | 16 x 16 | `zone` 80 | trigger and jump envelope x 616-792 |
| E | Second RedMob (walking enemy) | `RedMob` (id 38) | 864 | 288 | 48 x 32 | `offNeg` 2, `offPos` 2 | hero centre x 832-896 wakes it; body and whip never outside x 816-912 |
| T2 | Spore puffball (rhythm, D46) | `Puffball` (id 36) | 944 | 304 | 16 x 16 | `period` 4, `cloud` 1, `phase` 0 | cloud column about x 940-964, swell 0.8 s (1.2 s assisted) |
| T3 | Rotten-log bridge (crumble, D46) | `FallingPlatform` `skin=log` (id 37) | 1088 | 320 | 32 x 16 | `skin` log | pit x 1024-1168 (144 px, no floor, the level `Deadline` below), log flush with the floor, 64 px past the lip and 48 px before the far floor |

Floor: the `Collisions` rectangle id 3 is x 0-1024, the new id 39 is x 1168-1280, the floor tile rows (y 320-368) have no tiles in columns 64-72 and edge tiles in columns 63 and 73. `nextobjectid` is 40. Nothing is added to `Hints`.

### The enemy (author change to the plan)

The author asked for an enemy in the trap stretch instead of keeping the RedMob alone. Only the **RedMob** is both implemented and already met in forest-1 (the first RedMob at x 192-512), so the new one is a second **RedMob** (YellowMob and Bat are not in forest-0 or forest-1). Telegraph (D7): the raised arm of the whip, 0.4 s of wind-up before a 0.2 s strike; enemy wind-ups are not scaled by the longer-warnings assist (that setting is `TrapWarnings`, traps only). Beaten by a sword hit or a stomp; **no ammunition** (D26); the route is blocked by it at floor level, so it is faced, not bypassed. It wakes only when the hero's centre is in its small range (x 832-896), so a hero waiting for the puffball is never chased. It sits **between** the tuft zone (ends x 792, 24 px clear of the mob's worst reach at x 816) and the puffball cloud (starts x 940, 28 px clear of reach at x 912), so the player faces one thing at a time; `Forest1TrapsTest` asserts those clearances and that the mob stays inside its reach. Expected cost: about 1 s (a sword hit or a stomp on the walk); a death at it replays about 8 s.

### Per trap

**T1, thorn tuft at x 696.** First open floor after the wall and lever puzzle, 88 px past the last escalator's travel (x 608). Teaches the learned root's rule (thorns come up when you are close, jump) with a new silhouette. Telegraph: the tuft is a clearly different colour from bushes; entering its zone makes it bristle for 0.8 s (1.2 s assisted), then it is lethal until the retry. The default 80 px zone means a walker who keeps going is hit; a jump (body edge 2-61 px before the tuft) clears it. Cost about 0 s. D26: a jump, no ammunition; the loader requires the jump envelope.

**T2, puffball at x 944.** A trap that does not react to the hero: watch the cap, cross in the calm (cycle: calm 2.2 s, swell 0.8 s, cloud 1.0 s; crossing needs about 0.4 s). `phase` 0: the clock starts at the entrance and on every retry, so every attempt meets the same pattern; a mastered walker arrives in the calm (no wait, measured), a hero 2-3 s late meets the swell. Cost 0-2.2 s of waiting. No bypass rule (not explosive).

**T3, rotten log over a pit at x 1088.** Last because a fall is the most expensive death. Teaches what the player saw at x 96-224 in this level (the platform lights, then falls) in the forest skin: cross, do not stand. The pit (144 px) is wider than any jump (a full jump flies about 88 px), so the log is the only way; a dawdle of up to about 1.45 s after landing still crosses. A hero who steps back during the warning finds the log gone: the pit is the `Deadline`, so that is a retry (D40, D26), never a soft-lock, and nothing is re-armed. The room exit (x 1216, the old `Checkpoint` spot) is 48 px past the pit and clear of every hazard.

### Required route and measured times

After the unchanged lever-272 wall: jump the tuft, sword or stomp the mob, cross the cloud in the calm, jump to the log, jump to the far floor, step to the room exit (the checkpoint before #187). **No ammunition** (the refill at x 312 is not on it). Measured with a scripted hero, real keyboard input, zero ammunition (`Forest1TrapsTest`, `RoomReplayTest`; an author check, not a novice playtest): **12.27 s** (11.58 s before), 0.00 s waiting for the puffball, **14.98 s with the longer-warnings assist**; the budget is 20 s. A death at the log replays about 12.7 s (the whole trap course is one room; the author accepted this and the course is not split. #187 appended a second room after it, which changes nothing here: a retry in the course restarts at x 32).

### Tests

New `Forest1TrapsTest` (headless, the shipped map): census and positions, the clearances between every trap and the mob's reach, the 48 px rule for the room exit (the checkpoint before #187), the zero-ammunition mastered route under 20 s with a sword kill, three identical attempts after retries, a death at the log and its retry, a walker killed by the tuft and a jumper not, the tuft's 0.8 s / 1.2 s warning, the swell before the cloud and the cloud killing a hero timed into it, the mob beaten by sword and by stomp (swept takeoffs), the mob staying inside its reach, the log's fall and the pit as a retry (also with the assist), and the whole route with the assist. Changed existing tests (stated in D58): `RoomReplayTest` takeoff list for forest-1, `RhythmTrapsTest` allowance for forest-1. Left out on purpose and recorded in D58: exploding root, swinging vine, falling branch and stone, stump and root (no room or art support).

### Author's answers recorded

Three traps, not four; retry cost accepted, no split; the thorn tuft first; the log last; no exploding root and no fireball choice; an enemy in the stretch (second RedMob). Open for the running game (tracked by #260): the new sprites and pit edge tiles on this map, and how the 12-13 s retry loop feels.

## forest-1 deadfall room (#363, D88)

`forest-1-deadfall`, x 4960-5600 (640 px, one camera view), floor top y 320, walls at x 4960-4976 and 5584-5600. It is
appended after the lever room on the map (forest-1 grew from 310 to 350 tiles) and listed second in `Rooms`, so it is
played right after `forest-1-mob` and before the trap course. Room card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-deadfall);
trap card: [TRAPS.md](TRAPS.md#trip-line-deadfall-deadfall).

- RoomStart (4992, 272); RoomExit (5536, 272, 32 x 48).
- `Deadfall` (the bough, 112 x 24) at (5184, 243) (5248 until #500 moved it 64 px left), `swing` left: rope knot P (5232, 252), the
  log tied on the right and swinging left at a hero coming from the entrance; the trip vine at x 5112-5128; the swing band
  x 5182-5282. A `Decor:trunk` at x 5292 holds the bough's thick end.
- Since #500 (D105) the room also holds a `FallingStone` (id 179, x 5392) and a `RedMob` (id 180, x 5472, `offNeg` 3); the tied
  log, the vine and the exit are all on screen from the entrance (D84).
- Measured (`DeadfallTrapTest`): jumping the vine from 2 to 60 px before it fires nothing and reaches the exit in 5.85 s (the stone
  walked under, the mob swung at); tripping it and stopping within 0.68 s is safe with either warning (7.15 s to the exit, 7.57 s
  with the assist); a hero who walks straight in is hit 2.00 s after the entrance (2.32 s with the assist), 0.27 s (0.18 s) into
  the swing. (5.17 s, 6.47 s and 2.65 s before #500.)
- D26: no support and no one-shot state the route needs; the spent log hangs above head height and the cut vine is
  scenery, so the exit stays reachable from every state.

## forest-1 bramble room (#364, D89)

`forest-1-bramble`, x 5600-6240 (640 px, one camera view), floor top y 320, walls at x 5600-5616 and 6224-6240. It is
appended at the map's end (forest-1 grew from 350 to 390 tiles) and listed after `forest-1-lever` in `Rooms`, so it is
played between the lever lesson and the route room. Room card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-bramble); trap
card: [TRAPS.md](TRAPS.md#creeping-bramble-bramble).

- RoomStart (5760, 272; 5632 until #500 moved it 128 px right); RoomExit (5920, 176, 32 x 32) on the shelf's left end.
- A solid shelf x 5904-6192, top y 208 (16 px thick, tiles row 13), with the floor running under it.
- A static `Ladder` x 6192-6208, y 208-320 (no lever, no `dropDistance`), one tile off the right wall; the 16 px between
  it and the wall are open down to the floor.
- `Bramble` x 6160, y 184, 64 x 24, `root=right`: rooted in the right wall's face, the band y 190-206 (Y down: 18 to 2 px
  above the shelf line), the grown arm over the ladder top and the shelf's last 48 px; the clump's always-lethal 12 px
  (x 6212-6224) are clear of the ladder. **Recipe:** a ladder whose top a bramble guards stands at least one tile off the
  root face, or its top is inside the clump and there is no way out (D89).
- Nothing else: the bramble is in view, cycling, from the entrance, and the floor walk to the ladder is out of its reach.
- Measured (`Forest1BrambleTest`): mastered route 10.73 s from a fresh start (the same with the longer-warnings assist),
  9.42 to 12.80 s over every start offset for a hero who goes when the lane is open or waits on the ladder; a blind climb
  from a fresh start is caught 6.70 s after the entrance. On the fixture `bramble-test.tmx` (same recipe) a blind climb-out
  after a fixed wait survives 37 of 80 offsets, the longest safe run 1.85 s (`BrambleTest`).
- D26: no lever, support or one-shot state; the arm comes back every 4 s, so waiting on the ladder always gives another
  window.

## forest-1 spider rooms (#484, D102, D103)

**Historical:** the coordinates, times and test notes in the list below are those of the #484 placement and are kept as its
record. Since #500 `forest-1-spider` is 1120 px (x 6880-8000) and `forest-1-climb` lies at x 8000-8640, 480 px right of the
x given here; the rooms as they are now, and their measured times, are under "Reworked under D105 with the snail" at the end
of this section and on the room cards.

The forest spider is placed on room cards in the order floor, wall, thread, each the only new reading on its screen (D72).
No shipped room could show it alone, so forest-1 grew from 430 to 510 tiles with two one-screen rooms at the map's end,
listed in `Rooms` so that the order is lever, **spider**, bramble, **climb**, route. Room cards:
[ROOM_CARDS.md](ROOM_CARDS.md#forest-1-spider), [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-climb) and
[ROOM_CARDS.md](ROOM_CARDS.md#forest-1-route).

- **`forest-1-spider`**, x 6880-7520, floor top y 320, walls x 6880-6896 and 7504-7520 (tiles copied from
  `forest-1-deadfall`). RoomStart (6912, 272); RoomExit (7456, 272, 32 x 48). `Spider` x 7184, y 304, `offNeg`/`offPos`
  3 (a D102 box): `position.x` 7136-7232, pounce reach x 7090-7302 at most. Measured (`Forest1SpiderTest`): 6.90 s baiting
  the pounce and striking in the recovery (the same on a retry), 5.85 s rushing with the sword, 5.17 s with a fireball;
  a hero who stops in its reach is bitten 2.32 s in (3.58 s before #497, when the leap could not leave the range). **Finding:** walking straight on is no answer here, unlike the
  beetle: the locked bite lands where he walks.
- **`forest-1-climb`**, x 7520-8160, floor top y 320, walls x 7520-7536 and 8144-8160 (the author's Option A after review).
  Two one-way `Platform` steps (x 7696 y 272, x 7760 y 240) lead onto a block (x 7808-7952, top y 208); the RoomExit (7824, 64,
  32 x 32) is on a shelf above it (x 7808-7904, top y 96), reached only by a running vertical `Escalator` (x 7904-7936, its top
  between y 96 and 160). RoomStart (7552, 272). `Spider` polyline from (8032, 320): `0,0 -80,0 -80,-112 -160,-112` (the floor
  right of the block, its right face x 7952, its top x 7872-7952; its reach on the top ends at x 7824). Measured
  (`Forest1ClimbTest`, every pause of 0 to 2 s on the second step, sampled every 4 frames): the lift ride alone 10.77 s; one fireball as it tops the
  block's far edge 10.77 s; baiting and striking it on the block top 13.33 s; even a shot during its leap wins (D102's tie rule);
  waiting under the lift is bitten 5.33 to 6.38 s in, walking to the lift once it holds the block top 5.57 to 6.92 s in (the
  retry cost), each pinned at every pause. **Finding (re-review):** with the lift's lowest stop at y 176 its underside touched
  the spider's frame, and twice in 31 pauses it cut the spider's leap short (the lift counts as a solid, `Level.blocksObject`),
  so a hero waiting under it lived; the lift and the shelf moved up 16 px, and a full-cycle test keeps a spider under the lift. **Finding (review):** the first layout, a ledge climbed beside its face, let the jump onto the ledge stomp it as
  it came over the lip in 1,700 of 1,736 runs, so the room taught nothing; here the hero must wait for the lift, and the climb
  is what brings the spider to him. Contrast on the block's right face: wall frames 0.296 to 0.376.
- **`forest-1-route`** (below) gained `Spider` (id 150), a polyline from (1632, 320): `0,0 0,-112 -80,-112`, a 112 px
  thread from the ladder slab's right lip (hanging at x 1638) and the slab. A walker on the mandatory floor sets off the
  drop and is past the column when it falls, at every skitter phase with and without the assist, so the required 11.02 s
  and the 18.32 s with the refill are unchanged; a hero who stops under the lip is bitten 3.43 s in
  (`Forest1ShelfSpiderTest`). The optional ladder detour meets it: it hears the hero at the ladder's foot and holds at the
  slab's left end while he climbs; its leap never leaves the slab's floor, so the ladder's top is out of its bite and (#497) it does
  not wind up there, and a hero who walks on along the slab is past when it lands, at every skitter phase with and without the
  assist (the drop off the pad's left end then stomps it, with or without the assist). A hero who stops mid-slab is bitten 5.55 s in
  (`Forest1RouteTest`; 5.22 s before #497).
- Tests changed by the two new rooms, and why: every forest-1 test that reaches a room by its index or by counting room
  changes now counts the two rooms (`RoomTest`, `RoomCeilingTest`, `AllocationBudgetTest`,
  `Forest1CombatTest`, `Forest1LeverLessonTest`, `Forest1RouteTest`, `TreeClimbTest`, `Forest1HiveTest`,
  `Forest1BrambleTest`, `FairyOffscreenTest`); `RoomCardsTest` counts 22 rooms. `Forest1RouteTest` counts two monsters and
  takes the spider out of the one test that checks the slab's geometry alone; its ladder climb now asserts the hero is never
  bitten. The new tests and the hive and bramble tests find their room by name (`HeadlessLevelHarness.room`), so a later
  insertion does not move them. No threshold or tolerance changed.

**Reworked under D105 with the snail (#500, D106, cards approved by the author 2026-11-19).** The numbers above are the #484
placement (historical); the rooms now are:

- **`forest-1-spider`**, x 6880-8000 (1120 px; the floor block, id 151, widened, the right wall, id 153, at x 7984, the tile layer
  given 30 columns of plain floor at x 7504), so `forest-1-climb` and everything in it moved 480 px right. Beats: the spider
  (unchanged), the `Snail` (id 168) x 7600, `offNeg`/`offPos` 1, a one-way `Platform` step (id 169) x 7440-7472 at y 288 (its
  pocket: in its sight, above its line), a `FallingStone` (id 170) x 7744 (zone x 7728-7776), a `ThornTuft` (id 171) x 7872;
  the exit at x 7920. 11.53 s, 2.88 s per beat; retry 11.23 s (`Forest1SpiderTest`). The snail is out of view while the spider is met.
- **`forest-1-climb`**, x 8000-8640: a `FallingStone` (id 172) x 8112 (zone x 8096-8144) over the approach and a `Snail` (id 173)
  on the block top x 8336, `offNeg` 0, `offPos` 1. 13.33 s, 3.33 s per beat; retry 6.65 s (`Forest1ClimbTest`).
- **`forest-1-route`**: a `Snail` (id 174) x 1576, y 304, with no patrol, under the slab, and a solid 16 x 16 stump (id 175, one
  tile) at x 1600-1616 that ends its line and sight to the right. 11.02 s, 2.76 s per beat; the bat, now beat 3, kills 5.43 s in
  (`Forest1RouteTest`). Load checks hold (`Snail.problem`: every line ends on floor or the stump, clear of the entrance and the exit).

## forest-1 hive room (#365, D90)

`forest-1-hive`, x 6240-6880 (640 px, one camera view), floor top y 320, walls at x 6240-6256 and 6864-6880. It is
appended at the map's end (forest-1 grew from 390 to 430 tiles) and listed after `forest-1-guard` in `Rooms`, so it is
played between the guard room and the tree room. Room card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-hive); trap card:
[TRAPS.md](TRAPS.md#hanging-hive-hanginghive).

- RoomStart (6272, 272); RoomExit (6832, 160, 32 x 48) on the ledge. A solid ledge x 6624-6864, top y 208 (tiles rows
  13-19), against the right wall.
- `HangingHive` x 6440, y 232 over open floor (band x 6448-6456, y 258-320), a `Decor:trunk` at x 6424 for its bough; a
  `Stone` at x 6336, y 308 by the entrance.
- A static `Ladder` x 6608-6624, y 208-320 against the ledge; `HangingHive` x 6604, y 96 over it (band x 6612-6620, y 122-320,
  down the whole ladder), period 4.5 s, phase 2, a `Decor:trunk` at x 6588. **Recipe:** a hive over a ladder needs a period
  that leaves the climb room: here the window from the last stinger leaving the foot to the next head reaching the ledge is
  2.94 s for a climb of about 1.6 s (`HangingHiveTest`); measure any other ladder with the climber there.
- Measured (`Forest1HiveTest`): mastered route 7.12 s from a fresh start, 6.85 to 12.22 s over start offsets of 0 to 9 s with
  and without the assist; a hero who never waits dies in all 45 tested offsets, 3.1 to 7.5 s after the entrance (12 at the
  floor hive, 33 at the ladder). The stone knocks the floor hive down by a jump-throw (28 of 28 throw points), a fireball by a
  jump-fire (10 of 13 timings).
- D26: no lever, support or one-shot state; both hives come back every period, so waiting outside a band always gives
  another window.

## forest-2 fissure room (#366, D91)

`forest-fissure`, x 3008-3648 (640 px, one camera view), floor top y 320, walls at x 3008-3024 and 3632-3648 (the right
one above the ledge only). It is appended at the map's end (forest-2 grew from 188 to 228 tiles; the `Deadline` already
reached x 3712) and listed after `forest-walls` in `Rooms`, so it is played between the wall room and the canopy.
Room card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-fissure); trap card: [TRAPS.md](TRAPS.md#ember-fissure-emberfissure).

- RoomStart (3040, 272); RoomExit (3600, 176, 32 x 48) on the ledge, against the right wall (x 3584 until #327 moved it
  16 px right, clear of the beetle's blast). A solid ledge x 3424-3648, top y 224 (tiles rows
  14-19, the ground-edge tiles), against the right wall.
- `EmberFissure` A x 3232 (map y 332: the SpawnPoints layer is drawn 16 px up, so its top is y 316 and its floor line 320)
  on open floor, defaults (period 3.5 s, jet 1.0 s, height 96, phase 0).
- One-way `Collisions:Platform` 32 x 16 at x 3328 and 3360, y 272 (the branch); `EmberFissure` B x 3360, phase 1.75, under
  it: its column x 3364-3372 rises from 320 to the ledge top 224, through the branch.
- **Recipe:** the step is the fixture's (`fissure-test.tmx`, room 1): wait on the floor short of the branch (body front
  x 3300) until a jet ends, jump on to the branch, through the column, and on to the ledge; the climb takes 3.75 to 6.73 s in
  the fixture. B is half a period out of step with A, so a hero who crosses A right after its jet meets B mid-calm.
- **Beetle on the ledge (#327, D100):** `Beetle` (id 159) map x 3508, map y 224 (floor line 224), `offNeg` 0, `offPos` 2:
  centre x 3520-3552, blast at most x 3480-3592, y 192-224, 8 px short of the exit; the lip x 3424-3480 is outside every
  blast and its trigger. It hears the hero only on the ledge (its 48 px band is y 176-224), waits at its range end facing a
  hero on the lip, and is answered there: a fireball from the lip (one charge; the room has no stone), the lure (step in
  until it commits, step back on to the lip), or walking straight on past it (80 px in the 0.8 s wind-up, so it bursts
  behind him at every crawl phase, with and without the assist). Its blast never reaches the branch or the floor below.
- Measured (`ForestFissureTest`, `RoomReplayTest`, 2026-11-18, with the beetle): the mastered route by the lure 9.30 s from
  the entrance (three identical attempts), 8.60 s with a fireball from the lip, 8.25 s on every collection return (the
  beetle stays gone; the 0.17 s over the old 8.08 s is the exit's 16 px; both pinned in `RoomReplayTest`), 6.40 to 10.27 s
  over start offsets of 0 to 3.5 s with and without the assist (pinned in `ForestFissureTest`); a death in the beetle's burst
  after the mastered climb (a stop beside it when it commits) 8.35 s from the entrance at offset 0, 8.75 s at worst over the
  offsets after the wait (11.70 s from entering); a careless walker dies at A at 23 of 71 offsets, at most 3.5 s after entering. Before the
  beetle (#366): 8.08 s, 5.18 to 8.63 s over offsets. Contrast at the placed fissures over `2c3f66`: fissure 0.377, jet tile
  0.349, tip 0.398; the beetle on the ledge outline 0.334 to 0.519, body at most 0.591 (as at its forest-1 spawns).
- D26: no lever, support or one-shot state on the route; the clock restarts with the room; the beetle needs no charge and
  a retry rebuilds it at its spawn.

## forest-2 glade room (#331, D115)

`forest-glade`, x 3648-4480 (832 px, 1.3 camera views), floor top y 320, a left wall at x 3648-3664 and a right wall at x 4464-4480
(down to the floor). It is appended at the map's end (forest-2 grew from 228 to 280 tiles) and listed after
`forest-fissure` in `Rooms`, so it is played between the fissure room and the canopy. The `Deadline` is unchanged: the room has no
pit. Room card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-glade); monster card: [GAME_DESIGN.md](GAME_DESIGN.md#wolf-card-built-d115-331).

- RoomStart (3680, 272); RoomExit (4448, 272, 16 x 48) on the floor against the right wall (16 px wide: a 32 px exit at x 4432 would lie inside the wolf's sense box, x 3727-4447, and fail its load check).
- `ThornRoot` x 3808, `zone` 64 (map y 312: the SpawnPoints layer is drawn 16 px up), the only other beat before the wolf, and a second `ThornRoot` x 4360, `zone` 64, past the wolf's reach (x 4351 at most), as the third beat. D116 removed the wall jump, so the room's first design (a 96 px wall-jump block before the exit) was replaced by it.
- A 32 x 32 solid trunk x 3920-3952, y 288-320 (ground tiles, `tools/parallax_game.py` gives it its faces), and a 64 x 16 one-way
  `Collisions:Platform` branch x 3952-4016, y 288, flush with the trunk top.
- `Wolf` x 4144 (map y 312), `offNeg` 10, `offPos` 0: it trots x 3984-4144, the 160 px minimum. Its load checks pass with the
  entrance's body 18 px outside its sense box (x 3727-4447) and the exit on the floor 1 px right of it (x 4448). Moving the wolf right or widening its range puts the exit inside its sense box and fails `Wolf.problem` at load.
- **The pocket:** the one-way branch. A solid block's face cuts the wolf's sight at eye height, a platform does not, so the
  hero on the branch is seen from its floor, 32 px up, out of its 24 px lunge box. The trunk hides a hero on its top and on the
  floor before it (`ForestGladeTest.theTrunkHidesAHeroOnItsTopAndOnTheFloorBeforeItFromTheWolfForAWholePatrol`).
- **Recipe (the mastered route):** jump the thorn root, jump on to the trunk, walk out on the branch and wait until the wolf bays
  under it, then walk to the branch's end and step off on to its back (a stomp, with the bounce), then run to the second thorn root (x 4360) and jump it, and walk to the exit.
  Without the wait the stomp still lands when the wolf is under the branch's end, as a hurrying hero reaches it
  (`ForestGladeTest.aHeroWhoHurriesOverTheBranchStompsItAsItSprintsUnderHim`).
- Answers on its floor (`ForestGladeTest`): a jump a quarter second after the commit clears the lunge from every commit gap of 1 to 72 px
  with and without the assist; a hero who stays put is bitten; a runner is bitten without the assist and missed with it, and then
  climbs the trunk, a refuge; a swing 0.2 s after the commit kills it; the branch, the trunk top and the floor from x 4264
  on are never committed to.
- Measured (`RoomReplayTest`, `ForestGladeTest`, 2026-11-21, scripted hero): the mastered route 8.85 s (three identical attempts,
  with and without the assist; 2.95 s per beat), 7.42 s on a collection return with the wolf gone (pinned in `RoomReplayTest`); the most
  expensive death is the second thorn root after the wolf, 8.15 s from the entrance (the whole room replayed; D105's 6 s limit is for a first meeting that is not
  among the first two beats, which the wolf, beat 2, is not, and 8.15 s is under D105's 15 s split rule); the wolf's own bite on the mastered pace is a hop down
  in front of it, 4.27 s; the first thorn root kills a hero who walks straight on at 1.17 s. Contrast of the placed wolf over its range (`check_contrast.py`): live frames, outline 0.411 to 0.542 (floor 0.28), body
  (decor) 0.152 to 0.197 (ceiling 0.20). Evidence limit: a scripted hero, no novice playtest ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)).
- D26: no lever, support or one-shot state on the route; the wolf gives up after 1.5 s of bay and cannot hold a place; a retry rebuilds
  it calm at its spawn.

## forest-2 roost room (#330, D145, D146)

`forest-roost`, x 4480-5216 (736 px, 1.15 screens), floor top y 304, side walls at x 4480-4496 and x 5200-5216 (down to the
floor). It is appended at the map's end (forest-2 grew from 280 to 326 tiles) and listed after `forest-glade` in `Rooms`, so it is
played between the glade and the canopy. The `Deadline` is unchanged: the room has no pit. Room card:
[ROOM_CARDS.md](ROOM_CARDS.md#forest-roost); monster card: [GAME_DESIGN.md](GAME_DESIGN.md#flying-beetle-card-built-d145-330).
Object positions are on the room card.

- **The pocket:** on the floor under the one-way ledge (x 4800-4864, top y 256). The beetle's line crosses the ledge: it flies
  over it, 48 px above its top, and a one-way platform cuts the line of its dive. A hero's centre at x 4800 to 4848 is never
  committed to; beside the ledge on either side, and on it, he is dived at and can leave the blast's reach (`ForestRoostTest`).
- **Recipe (the mastered route):** jump the thorn root (take-off at frame x 4550) and walk right to the exit: the beetle commits
  behind him and the stone falls behind him.
- The stone hangs under a drawn soil overhang (x 5072-5136, y 0-96, no collision) with `lipTop` 96: on the default line it
  showed only half at the top edge beside the HUD's fireball icons, and at `lipTop` 48 the drifting cloud lay behind its cue
  once the camera scrolled (D146). Its body still starts at the room top.
- Foreground: `tools/parallax_game.py` (`forest-roost` entries); one standing piece of the usual set (`rise-reeds`) is left out
  and the root curtain stands left of the entrance, because both would cover the beetle or the ledge (rule B).
- Widening (D146): 6 tile columns copied from the plain floor left of x 4688; every object right of it moved 96 px.
- Measured (`RoomReplayTest`, `ForestRoostTest`, `FirstMeetingPocketTest`, 2026-11-27, scripted hero, forest-2.tmx SHA-256
  `9b7e0d72f1594f2be8674716820664ffbf555dae81061b22c58b161f54d87191`): the mastered route 6.43 s (three identical attempts, with and
  without the assist, 2.14 s per beat, the same on a collection return); every start delayed 5 frames at a time through 12 s
  reaches the exit with the beetle committing on the way; stopping under the beetle dies at 4.42 s, under the stone at 7.0 s from
  the entrance. Evidence limit: a scripted hero, no novice playtest
  ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)).
- D26: no lever or support in the room; the stone is the one one-shot object and blocks nothing; a retry rebuilds the beetle calm on
  its route.

## forest-1 lever room (#418, D86)

Built by [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418) after the D84 playtest of 2026-11-17: the author pulled the `Route` lever of the route room, saw only the ladder react, did not notice the lift stop and thought the room impassable. By the author's decision of the same day, a short teaching room stands between `forest-1-traps` (room 2 since #375) and `forest-1-route`: one lever, two mechanisms next to it, both in view at the moment of the pull, and a mistake that costs nothing. The card is written first ([ROOM_CARDS.md](ROOM_CARDS.md#forest-1-lever)); the rule and the split between the author's decisions and the agent's are in [D86](DECISIONS.md#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17). **Author check, not a novice playtest:** the numbers below are from a scripted hero with real keyboard input; the author played the room on desktop on 2026-11-17 and confirmed that everything is clearly visible (see D86).

Coordinates are the map's: **world Y grows downward** (floor top y = 320, `y` is an object's top edge, a smaller y is higher on screen). **Placement:** the room is authored after the end of the map, at x 4320-4960 (the map grew from 270 to 310 tiles, `nextobjectid` 115, the level `Deadline` from 6176 to 6816 px), and is listed in the `Rooms` list between `forest-1-traps` and `forest-1-route`, which sets the play order (mob, traps, lever, route, guard, tree), exactly as #375 did for `forest-1-mob`: no existing object, collision or tile moved. SHA-256 before `c5acdc5a30bac873735246ccda12cdd5edbcf5ea1dc96a238d9cd0f7058ec9b8` (origin/main after #429), after `4b9ced293cad0e9c61bd770bbf4edddbce94ac166f57d23bff3f4ff42d9a3f64` (recompute with `shasum -a 256 assets/tiles/forest-1.tmx`; the hash moves with later edits). Checked by script: every object and tile column of the old map is unchanged (the first 270 columns are identical, every old object is identical except the `Deadline`'s width) and only the 11 new objects (ids 104-114), the 40 appended columns and the three `Rooms` entries are new.

```
local x/16  0123456789012345678901234567890123456789   (room 3 only; x = 4320 + 16 * column)
y96-208     ..........................L.............   the raised ladder hangs here (x 4736): its foot is level with the ledges' tops
y192        .............v..........................   the lever v on the slab (x 4528)
y208        ........sssssss............EEEEEEEEEEEE.   slab s (x 4448-4560); exit ledge E (x 4752-4944); the lowered ladder's top is level with E
y176-208    ..................................ccc...   the room exit c on E (x 4864-4912)
y240-304    ......ll................................   the lift l runs here (x 4416-4448, y 240-304)
y304        ..@.....................................   entrance 4352
y320        ========================================   floor under the whole room; end walls at x 4320 and 4944
```

| What | Object | x (px) | y (px) | Size | Properties | Role |
| --- | --- | ---: | ---: | --- | --- | --- |
| Trap course exit | `RoomExit` (id 45), room `forest-1-traps` | 1216 | 272 | 32 x 48 | none | unchanged: it now leads into this room |
| Room 3, `forest-1-lever` | `Room` (id 112), `RoomStart` (id 113) | 4320 | 0 | 640 x 368; start 4352, 272 | none | one screen wide (the camera shows 640 x 360), so the whole room is in view wherever the hero stands; safe entrance, floor under it, nothing in reach |
| Lift | `Escalator` `Lesson` (id 106) | 4416 | 240 | 32 x 16 | `isVertical`, `offNeg` 0, `offPos` 2 | starts running at its top (y 240), descending, 50 px/s between y 240 and y 304 (2.56 s a cycle); the only way up to the slab; boardable from the floor while it is at y 248.2 or lower |
| Slab | collision (id 110) | 4448 | 208 | 112 x 16 | plain collision, floating | the lever's ledge, 112 px above the floor (a hop is about 58 px, a boarding reach 71.8 px): out of reach from the floor; 32 px above the lift's top (a hop); the floor passes under it |
| Lever | `Trigger` `Lesson` (id 104) | 4528 | 192 | 16 x 16 | none (legacy name link) | on the slab; pulled with Up, only standing on the slab (swept); 80 px from the lift, 192 px from the ladder (nearest edges) |
| Ladder | `Ladder` `Lesson` (id 105) | 4736 | 96 | 16 x 112 | `dropDistance` 112 | starts raised, foot 112 px above the floor and 176 px from the slab's end; lowered y 208-320, its top rung level with the exit ledge; the only way on |
| Exit ledge | collision (id 111) | 4752 | 208 | 192 x 112 | plain collision, not a `Wall` | 112 px high, against the ladder's right side and the right wall: reachable only by the ladder |
| Room exit | `RoomExit` (id 114), room `forest-1-lever` | 4864 | 176 | 48 x 32 | none | on the ledge top, 112 px from the ladder, 32 px before the right wall; leads to the route room |
| Floor, walls | collisions 107, 108, 109 | 4320 | 320 | 640 x 48; walls 16 x 320 at x 4320 and 4944 | | closed room; the invisible ceiling (D38) is added by the loader |

The lever, ladder and lift share the name `Lesson` (the legacy name link of `Level.spawnLevers` and `tools/check_tiled_links.py`, as the route room's `Route`): the ladder is the lever's face. The authored state is the route room's: ladder raised, lift running. No code was needed. Tiles: the floor, the two end columns and the exit ledge copy the route room's art (ids 22-24, 42-44, 62-64, wall columns), the slab uses the platform block ids 26-28 that the other forest-1 slabs use. No new art, no hint, no monster, no hazard, no pickup, no one-shot object.

### How it plays

The whole room is on screen from the entrance: a lift moving beside a ledge with a lever on it, a ladder hanging in mid-air to the right over a ledge that carries the exit, nothing else. **Route:** walk to the lift, hop on when it comes down within reach, ride it up and hop onto the slab, walk to the lever, pull it: the ladder comes down at once on the right (about 0.7 s) and the lift beside the hero stops. Walk off the slab, walk under it to the ladder, climb, walk to the exit. **Why it teaches the link:** the way on needs both mechanisms in turn, and one lever changes both: the lift is the only way to the lever, the ladder is the only way to the exit. The pull happens with the lift 80 px away (it just carried the hero there and now stops) and the ladder 192 px away (it comes down), both inside the same view. **A wrong pull:** pulling again raises the ladder and restarts the lift; nothing is lost and there is nothing to be hurt by.

### Recovery and D26 (swept by `Forest1LeverLessonTest`)

- The lever is a reversible toggle, in reach only standing on the slab; never from the floor, the lift, the ladder or the exit ledge (swept over every x and four heights in both lever states), so no support is taken from under the hero.
- The slab is floating, so the floor runs from the entrance to the exit ledge's face. From the floor the hero is never shut in: with the ladder raised the lift is running and is boarded from any floor x (the lift stays within the boarding reach, so no stopped state exists with the ladder raised), with the ladder lowered the ladder reaches the floor. The first version had a slab solid down to the floor: the sweep found a hero who dropped left of it after a pull, with the lift stopped out of reach and the lever on the slab, with no way back (a soft-lock). The floating slab removes it.
- Every state reached by supported actions (the hero waits 0 to 2.5 s on the slab in 26 steps so that the pull stops the lift at different heights, then pulls 1 to 4 times, then stays, drops to the right or drops to the left): 312 runs, and every floor position in both lever states, reach the exit with ordinary keys and no Restart Level.
- The raised ladder and the exit ledge are out of reach from the slab, with every kind of key spam (a leaping hero gets at best 68 px short of the ladder) and from the floor.
- State boundary: death and room retry restore the authored state (ladder raised, lift running from its top, lever down, hero at x 4352, the room's RoomStart). The room is secured when the hero leaves it, like the others.

### Measured (scripted hero, real input; author check, not a novice playtest)

| Route | Result | Budget |
| --- | ---: | --- |
| Required (walk, ride, hop, pull, drop, walk, climb, walk), three identical attempts after retries | **8.43 s** | about 20 s (D19) |

**Agent check in the running web build (Chrome, 2026-11-17):** the room renders as one view; the hero rode the lift up, hopped onto the slab, pulled the lever (Up + Action), dropped, climbed the lowered ladder and walked to the exit, and the route room loaded (observed on the first version of the branch, which inserted the room at x 1280 instead of appending it; the room's layout and mechanics are identical); no console errors. **Chrome check of the appended version (2026-11-17, orchestrator):** the room chain `forest-1-mob` to `forest-1-traps` to `forest-1-lever` to `forest-1-route` ran in the web build; each transition put the hero at the next room's RoomStart with the camera inside the room; no console errors. The full lever-room route (lift, slab, pull, ladder, exit) was played on the first version only; on the appended version the lift boarding was not repeated (tool latency). While the hero holds Up at the lever the fairy glows the lever, the ladder and the lift; at the pull the glow disappears at once, the ladder visibly drops and the lift just stops with no cue, so its change is shown only by its stopping ([#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419), checklist item "Keep a cue on in-view linked mechanisms briefly after the pull"). **Not verified, tracked by [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418):** the author's play-through must answer whether the player noticed that the lift stopped; if not, the stopped lift needs a use that does not create a timing puzzle (open for the author).

First-learning time (the 60 s hypothesis) was not measured: that needs a person who has not seen the room (#135). Allocation (`AllocationBudgetTest.forest1LeverRoom`, `...RidingTheLift`, `...PullingTheLever`): 513.8 to 564.5 B/frame in 10 isolated runs (an earlier run of the rest scene measured 345.8; JIT state), ceiling 620 (highest measured 564.5 + about 10 %, D85's rule); the route room's riding scene measures the same here.

### Tests

`Forest1LeverLessonTest` (headless, real shipped map, 19 tests): the census and the authored state, the opposite-states link (five pulls) and the lever's picture, the camera view (the lever, the whole lift range and the whole ladder range inside one view; the camera's horizontal target never moves), the lever's reach, no hop or jump from the floor or from the slab reaching the ladder or the exit ledge, the required route and its time, three identical attempts, the pull-twice recovery, the D26 sweeps, a wrong pull costing nothing, retry, the room chain, the transitions (traps to lever, lever to route: start, room and camera), the entrance and the exit rectangle. Changed existing tests (stated in D86): `Forest1RouteTest`, `Forest1CombatTest` and `TreeClimbTest` enter one more room and name it in their room-chain assertions (no coordinate changed), `RoomTest`, `RoomCeilingTest`, `RoomCardsTest` and `AllocationBudgetTest` count and index the new room. No threshold or tolerance was relaxed.

## forest-1 route room (#187, D67)

Built by [#187](https://github.com/jlogicgames/edgard_in_kimeria/issues/187) (part of [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103)) from the author's placement decision of 2026-11-15: the room is **appended** to `forest-1`, after the trap course, and the level `Checkpoint` moved to its end; the combination room ([#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104), "forest-1 guard room" below) was then appended after it: this room's checkpoint became a `RoomExit` (2048, 112, 48 x 32, the same x, so its times did not move). The author then decided that a **one-shot element** is needed, after which the reward is lost until Restart Level (2026-11-15); the room was revised in a second commit of the same PR. Rule and rationale: [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15). **Author check, not a novice playtest:** the numbers below are from a scripted hero with real keyboard input; the author played the room on desktop on 2026-11-17 and confirmed that everything is clearly visible (see D67).

Coordinates are the map's: **world Y grows downward** (floor top y = 320, `y` is an object's top edge, a smaller y is higher on screen, `y = 320 - height` for something standing on the floor). `forest-1.tmx` is now 132 x 23 tiles (2112 x 368 px), a `Rooms` layer was added, `nextobjectid` is 60. SHA-256 before `e5160f69b69e254e71497609835a8047a993732be202f836f3b6c626e22642c3`, after `4070e78bd247525efa8e6c6f68cbded20a71d3e024539ca24a59427f54607519` (recompute with `shasum -a 256 assets/tiles/forest-1.tmx`).

```
local x/16  0123456789012345678901234567890123456789012345678901   (room 2 only; x = 1280 + 16 * column)
y112        #..........................R....................C..#
y128        #.........................ppp..............EEEEEEEE#
y168        #......................FF..................EEEEEEEE#
y208        #...............Asssss.....................EEEEEEEE#
y296        #...................................BBBB...EEEEEEEE#
y304        #...@.....l..............................L.EEEEEEEE#
y320        ====================================================
```

`@` start, `l` lever, `A` the raised ladder (it hangs y 96-208), `s` the slab, `F` the one-shot step (a falling platform, y 168), `p` the refill pad (top y 128), `R` the refill, `B` the bat's beat, `L` the lift (bottom shown; it runs y 176-304), `E` the exit ledge (top y 160, solid below), `C` the room exit on it, `#` the end walls (the strip is approximate and shows only the rows where something sits; the table is authoritative).

| What | Object | x (px) | y (px) | Size | Properties | Role |
| --- | --- | ---: | ---: | --- | --- | --- |
| Room 2 (room 1 before #375), `forest-1-traps` | `Room`, `RoomStart` (32, 272), `RoomExit` | 0 | 0 | 1280 x 368; exit 1216, 272, 32 x 48 | none | the trap course; the exit replaces its checkpoint. Since #500 batch 3 (D110) it is x 640-1280 (start 656) and the escalators are `forest-1-escalators`, x 0-640 |
| Room 3 (room 2 before #375), `forest-1-route` | `Room` (id 46), `RoomStart` (id 47) | 1280 | 0 | 832 x 368; start 1344, 272 | none | third room since #375; safe entrance, floor under it, nothing in reach; ends at the `RoomExit` below |
| Lever | `Trigger` `Route` (id 48) | 1440 | 304 | 16 x 16 | none (legacy name link) | pulled with Up, only on the floor |
| Ladder | `Ladder` `Route` (id 49) | 1536 | 96 | 16 x 112 | `dropDistance` 112 | starts raised, foot 112 px above the floor; lowered y 208-320; optional route |
| Slab | collision (id 53) | 1552 | 208 | 80 x 16 | plain collision, floating | landing and waiting place; the floor passes under it, so the lever stays in walking reach |
| One-shot step | `FallingPlatform` (id 58) | 1648 | 168 | 32 x 16 | none (default look) | 40 px above the slab, 40 px below the pad; falls once, 1 s after it is stood on (1.5 s assisted) |
| Refill pad | collision (id 57) | 1696 | 128 | 48 x 16 | plain collision, floating | 80 px above the slab: out of reach without the step |
| Refill | `FireballRefill` (id 51) | 1712 | 112 | 16 x 16 | none | one charge (D27), on the pad |
| Lift | `Escalator` `Route` (id 50) | 1936 | 176 | 32 x 16 | `isVertical`, `offNeg` 0, `offPos` 4 | starts running at its top (y 176), descending, 50 px/s between y 176 and y 304; mandatory route |
| Bat | `Bat` (id 59) | 1856 | 296 | 16 x 16 | `isVertical` false, `offNeg` 0, `offPos` 3 | patrols x 1856-1904 at the hero's height; sword, stomp, hop or fireball; never chases |
| Exit ledge | collision (id 54) | 1968 | 144 | 128 x 176 | plain collision, not a `Wall` | not jumpable or climbable from the floor; 32 px above the lift's top |
| Room exit | `RoomExit` (id 75) | 2048 | 112 | 48 x 32 | none | on the ledge top, 80 px from its edge; leads to room 3. The level `Checkpoint` stood here until #104 (same x: same times) |
| Floor, walls | collisions 52, 55, 56 | 1280 | 320 | 832 x 48; walls 16 x 320 at x 1280 and 2096 | | closed room; the invisible ceiling (D38) is added by the loader |

Tiles: the floor, the two end columns and the exit ledge copy the forest-0 ledge art (ids 22-24, 42-44, 62-64, wall columns 79/99/139); the slab and the pad use the platform block ids 26-28 that forest-1 already uses at x 160 and 304; the step is the existing falling platform. No new art, no hints, one monster (the bat), no hazard.

### How it plays

The whole room is readable from the entrance side: the lever, the raised ladder, the slab with the step and, above it, the pad with the refill, the lift in motion and the exit ledge. **Required route (no lever, zero charges):** walk right, beat the bat with the sword (or hop over it, or shoot it from afar if you have a charge), step onto the lift as it comes down or up through its lowest 56 px, ride up 1.7 s, hop onto the exit ledge (16 px higher than the lift's top), walk to the room exit. **Optional route:** pull the lever (the ladder comes down in about 0.7 s and the lift stops where it is), climb to the slab, hop onto the step (it lights up and will fall), hop on to the pad, take the refill, drop to the floor, walk back to the lever, pull it again (ladder up, lift running), then ride. **The one-shot:** the step falls about 1 s after it is stood on and never returns. A hero who steps on it and back, or who hesitates, has spent it and cannot reach the pad any more: the refill is lost until Restart Level (before the room is secured, until a death retry). The lift route is unaffected. **The bat:** it patrols the floor stretch before the lift (x 1856-1904) and never chases; its beat is clear of the lever, the ladder, the step and the landing of a drop off the pad (swept over 54 bat phases; the first beat, from x 1760, caught a hero who held Right off the pad), so the one-shot decision is never made under attack. A fireball from afar kills it; the refill then returns the charge, which is why the refill matters here even at a full stock (spend one on the bat first).

### Recovery and D26 (swept by `Forest1RouteTest`)

- The lever is a reversible toggle; the step is the one one-shot in the room, and nothing mandatory depends on it.
- The lever is in reach only on the floor, 96 px from the ladder foot and about 430 px from the lift; never from the slab, the step, the pad, the ladder, the lift or the exit ledge (swept over every x and three heights in both ladder states). So no support is taken from under the hero.
- Every state the player can produce (any number of lever pulls at any rhythm; the lift stopped at each of 53 moments 0.1 s apart over its 5.12 s cycle; the step intact or spent; the hero on the floor or on the slab; the refill taken or lost) still reaches the checkpoint by flipping the lever back if the lift is stopped: 106 lever-sweep runs and 56 step-state runs, 0 failures; no Restart Level is ever needed.
- With the step spent, nothing reaches the pad: every takeoff from the slab (every 4 px, standing and jumping) falls short, and the floor is 192 px below it.
- A stopped lift never opens the exit ledge without the lever, with the bat alive or not: the hero boards it from the floor only while it is at y 248 or lower on screen (a lift within 56 px of its bottom; 71.8 px above the floor is the measured reach), and from there the exit ledge (top y 144) is 104 px up, about 32 px beyond the reach. (The first version had the ledge top at y 208 and the sweep showed a lift stopped at y 248 then made a two-jump route; the second review asked for a wider margin once a stomped bat can bounce the hero: y 160 became y 144.)
- The pad is 128 px from the lift and from the exit ledge, so neither reaches it.
- State boundary: death and room retry before securing restore the authored state (step intact, ladder raised, lift running from its top, lever down, refill back, the unsecured charge rolled back, hero at x 1344). If the room is secured (its forward exit into the guard room, #104, or a healer return) the snapshot keeps the step spent and the ladder as left, the refill stays out of reach and the exit works (`aSecuredSpentStepStaysSpentOnARevisitAndTheExitStillWorks`). Restart Level rebuilds the level from the map: the step is intact and the refill is back (`theRefillLostStateStillReachesTheCheckpointAndRestartLevelBringsTheRefillBack`). Room 1 is secured when the hero leaves it, so a death in room 2 does not replay the trap course.

### The refill, the bat and a full stock

A run starts with three charges and room 1 needs none, so the refill alone would be wasted. The bat (author's request, 2026-11-15) gives the room its own use for a charge: shoot the bat, then the refill returns it. The fireball buys safety, not time. The #104 room that follows (a RedMob, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15)) offers a fireball as another optional choice: built, a held charge shoots the guard from afar, and that room has a refill of its own on its roof that returns the charge.

### Measured (scripted hero, real input, zero charges; author check, not a novice playtest)

| Route | Result | Budget |
| --- | ---: | --- |
| Required (walk, bat by sword, ride, hop, walk), three identical attempts after retries | **11.02 s** | about 20 s (D19) |
| One fireball on the bat instead of the sword | 11.02 s (the lift's schedule decides both) | optional |
| Optional detour (lever, ladder, step, pad, refill, drop, back, lever) | 8.03 s | separate measurement |
| Whole room with the refill | **18.32 s** (asserted against the same 20 s) | about 20 s |

First-learning time (the 60 s hypothesis) was not measured: that needs a person who has not seen the room (#129-#131). Allocation: about 1,026 B/frame in the route room (bat alive; ceiling 1,130, `AllocationBudgetTest.forest1RouteRoom`; the riding scene measured 930 B here and 993.8 for the reviewer: whole-scene JIT-dependent numbers); the rider path is guarded separately by `ridingARunningEscalatorCostsNoMoreThanStandingStill` (riding minus standing 0.0 B/frame, ceiling 8; 24.0 with the old code); the trap course is unchanged. The riding path used to allocate a `Vector2` per frame (`Escalator.currentMoveDirection()`); `Escalator.riderVelocityX()` replaced that use in `Player`.

### Tests

`Forest1RouteTest` (headless, real shipped map, 34 tests): census and positions, the one-lever opposite-states link and the lever's picture, lever reach, the pad closed without the lever (floor sweep, lift sweep), no stopped lift opening the exit ledge, the required route with zero charges and its time, three identical attempts, the optional detour with the step and its charge, the bat (patrol, clearances to the lever and the one-shot decision, sword, hop and fireball answers, the refill's worth at stock 2 and 3, retry and secured kill), the one-shot step (falls once, stays gone, pad closed), the refill-lost state reaching the exit, Restart Level, retry, a secured revisit, the lever sweeps and the step-state sweep, ledge drops, entrance. Changed existing tests (stated in D67): `Forest1TrapsTest` ends at the room exit, `RoomTest` (the unmarked-map test became an in-memory strip plus a two-room check), `RoomCeilingTest` (both rooms and a 160 px ledge head start), `AllocationBudgetTest` (new ceiling and a riding scene). No threshold was relaxed.


## forest-1 guard room (#104, D68)

Built by [#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104) (part of [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103)) on top of the route room (#187, PR #334): the third room of `forest-1`, appended after `forest-1-route` by the author's decision of 2026-11-15; the level `Checkpoint` moved to its end, and since #349 (D80) it stands on the tree room's crown and this room ends at a `RoomExit` at the same x (the times below are unchanged). Rule, finding and rationale: [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15). **Author check, not a novice playtest:** the numbers below are from a scripted hero with real keyboard input; the author played the room on desktop on 2026-11-17 and accepted it as it is (see D68). The room was revised twice after review (see "Finding"); the final layout follows the author's request to add a second threat next to the RedMob.

Coordinates are the map's: **world Y grows downward** (floor top y = 320, `y` is an object's top edge, a smaller y is higher on screen). `forest-1.tmx` is now 180 x 23 tiles (2880 x 368 px), `nextobjectid` 79. SHA-256 before (PR #334) `4070e78bd247525efa8e6c6f68cbded20a71d3e024539ca24a59427f54607519`, after `cefc011bd9448e234dbcb6db40151016d3d3aff03bdde435255859f728ee6c46` (recompute with `shasum -a 256 assets/tiles/forest-1.tmx`).

```
local x/16  012345678901234567890123456789012345678901234567   (room 3 only; x = 2112 + 16 * column)
y96-208     ...........A....................................   the raised ladder hangs y 96-208
y192        .................................R..............   the refill on the slab (x 2640)
y208        ............sssssssssssssssssssssssss...........   the slab, x 2304-2704; above the tunnel it is the roof
y224-288    ............################....................   the roof block, x 2304-2560
y288-320    ................P.G.............................   the tunnel (32 px high): puffball P (x 2375), guard G (body 2400-2420)
y272-320    ...........................................X....   the room exit on the floor (x 2800; the checkpoint until #349)
y304        ....@..l........................................   entrance 2208, lever 2256
y320        ================================================   floor; end walls at x 2112 and 2864
```

| What | Object | x (px) | y (px) | Size | Properties | Role |
| --- | --- | ---: | ---: | --- | --- | --- |
| Room 2 exit | `RoomExit` (id 75), room `forest-1-route` | 2048 | 112 | 48 x 32 | none | the route room's checkpoint became this exit (D67 times unchanged) |
| Room 3, `forest-1-guard` | `Room` (id 60), `RoomStart` (id 61) | 2112 | 0 | 768 x 368; start 2208, 272 (2176 before #500, D110) | none | third room; safe entrance, floor under it, nothing in reach; the start point is the top-left of the hero's 48 x 48 square |
| Lever | `Trigger` `Bridge` (id 63) | 2240 | 304 | 16 x 16 | none (legacy name link) | pulled with Up, only on the floor; reach x 2230.5-2265.5 (x 2256 before D126, which keeps the hand-operated lever 32 px from the ladder at x 2288; x 2224 before #500, D110) |
| Ladder | `Ladder` `Bridge` (id 64) | 2288 | 96 | 16 x 112 | `dropDistance` 112 | starts raised, foot 112 px above the floor; lowered y 208-320; leads to the roof |
| Slab (roof) | collision (id 73) | 2304 | 208 | 400 x 16 | plain collision, one piece | the roof route: up from the ladder, 112 px above the floor, over the tunnel, ends at x 2704 |
| Roof block | collision (id 78) | 2304 | 224 | 256 x 64 | plain collision | closes the tunnel from above: ceiling y 288, so the opening is 32 px high (x 2304-2560): no hop over the guard, no stomp |
| Puffball | `Puffball` (id 77) | 2375 | 304 | 16 x 16 | `period` 4, `cloud` 1, `phase` 2.52 (2.2 before #500, D110) | the second threat: swell 0.8 s then a 1 s cloud every 4 s (calm 2.2 s); cloud x 2373-2393, y 298-318, over every place from which the sword reaches the guard |
| Guard | `RedMob` (id 69) | 2394 | 288 | 48 x 32 | `offNeg` 0, `offPos` 0 | in the tunnel on the floor; never moves; faces left; strikes only within 6 px of its body (x 2400-2420); the sword reaches it from a gap of up to 17 px |
| Refill | `FireballRefill` (id 76) | 2640 | 192 | 16 x 16 | none | one charge (D27) on the roof's far part; not a coin |
| Beetle | `Beetle` (id 146, #327) | 2632 | 304 | 24 x 16 | `offNeg` 2, `offPos` 2 | the beetle's first room (D100): its centre crawls x 2612-2676 on the floor past the tunnel, in view from the entrance; its blast covers x 2572-2716 at most, so it never reaches the tunnel (a shelter), the roof's drop (its trigger ends at x 2696) or the exit |
| Room 3 exit | `RoomExit` (id 82), room `forest-1-guard` | 2800 | 272 | 16 x 48 | none | on the floor, 96 px past the slab's end, 48 px from the right wall; where the level `Checkpoint` (id 7) stood until #349 moved it to the tree room (same x: same times) |
| Floor, walls | collisions 72, 70, 71 | 2112 | 320 | 768 x 48; walls 16 x 320 at x 2112 and 2864 | | closed room; the invisible ceiling (D38) is added by the loader (`RoomCeilingTest` covers this room) |

Tiles: the floor and the two end columns copy room 2's (ids 22-24, 42-44, 62-64, wall columns 79/99/105/119/139); the slab uses the platform block ids 26-28 that `forest-1` already uses (25 tiles, columns 144-168); the roof block uses the ledge ids 42/43/44 of room 2's exit ledge (rows 14-17, columns 144-159). No new art, no hint, one monster, one trap, no falling platform. Rooms 1 and 2 are unchanged except for the route room's exit.

**Beetle (#327, 2026-11-18).** The beetle (id 146, row above) was added past the tunnel by the author's approval of its card
([ROOM_CARDS.md](ROOM_CARDS.md#forest-1-guard)); the guard, the puffball and the roof are unchanged. Measured in
`Forest1CombatTest`: the three pinned routes keep their times (melee 6.83 s, shot 6.15 s, roof 7.67 s before #500 batch 3, which moved the start, the lever and the puffball's clock together and shortened them to 6.50, 5.82 and 7.40 s), because a hero
running on triggers it and is clear of the blast when it bursts behind him; a hero in the tunnel's mouth is heard (its
notice beat) but never reached; from the mouth to the exit, a fireball kill takes 3.65 s and a lure (step out until it
commits, step back into the tunnel, let it burst) 4.53 s; the roof run drops past it without triggering it (7.40 s); a death
in its burst after the mastered melee comes 5.82 s after the entrance (6.08 s before #500 batch 3). All four are pinned to 0.1 s. The author played the
room on 2026-11-18 and approved the beetle's look and sounds.

### How it plays

The floor route (the support-free route: no lever, ladder or other support) runs through a low tunnel; the roof route is the second way through, and both reach the checkpoint with zero charges. In it a spore puffball and, right after it, a RedMob that never moves. The cap swells for 0.8 s before each 1 s cloud; the cloud covers the whole width from which the sword reaches the guard (7-17 px from it, the whip reaches 6 px), so the swing must start in the calm and be finished before the cloud arrives (the hero is frozen while it plays). **Timed melee:** arrive in the calm, step in, swing once, walk on: 6.83 s once the cycle is known (6.50 s since #500 batch 3; 6.83 s before; it restarts at the same phase after every retry). **Fireball:** shoot from outside (the guard never moves and the cloud does not stop a fireball), then cross the cloud's width in the calm: 5.82 s, one charge (6.15 s before #500 batch 3). **Roof:** pull the lever (the ladder comes down in about 0.7 s), climb, walk the roof (nothing threatens it), take the refill, drop off the end: 7.40 s, +1 charge (7.67 s before #500 batch 3). The roof route needs the lever and never touches the tunnel. At a **full stock** (three) the refill pays nothing (D27).

### Finding and revisions (answers #104's "does the combination add a new decision?", and the author's request)

1. **First layout (guard on the floor under the slab, hole in the slab):** no new decision. One swing at any gap of 0-64 px killed the guard (33 of 33); a hop passed it from 22 of 42 takeoff gaps (stomp 17, whipped 3).
2. **Second layout (guard on the slab at the ladder's top, refill beyond):** a 0.1 s reflex (a swing within 5 frames of stepping off kills, 6 of 15 delays; a shot 4 of 15, or from feet y 216-208) and a refill that paid nothing at a full stock.
3. **Third layout (this one; the author's request to add a second threat beside the RedMob):** the cloud makes melee a timed risk with a telegraph. Measured over the 60 start phases (4 frames apart) of a hero 30 px before the cloud:

| Choice | Succeeds | Time from the entrance | Charges |
| --- | ---: | ---: | --- |
| Melee, impatient, at artificial start phases (hero teleported 30 px before the cloud) | 30 of 60 (50 %); the cloud kills the rest | 5.33 s when it works (`Forest1CombatTest`, measured from the teleport, 30 px before the cloud) | 0 |
| **Straight run from the room's entry (a player's first walk-in)** | **dies, every time, at frame 82**; works only after a pause of 9 frames (0.15 s) or more | 6.8 s once it works | 0 |
| Melee, patient (waits for 1.0 s of calm) | 60 of 60 | 6.36 s on average (`Forest1CombatTest`, measured from the same teleport; includes the wait) | 0 |
| Melee, mastered (knows the phase) | always | **6.50 s** | 0 |
| Fireball, then a patient crossing | 30 of 30 phases | **5.82 s** | -1 |
| Roof (lever, ladder, roof, refill) | 10 of 10 phases | **7.40 s** | **+1** |

**A real player does not arrive at an arbitrary phase.** The 50 % comes from a sweep with the hero teleported to 30 px before the cloud at 60 artificial start phases; a player enters at the room's start, where the cloud is always at the same point of its cycle (2.52 s into its cycle since #500, D110: the clock starts 0.32 s further on and the walk is 0.32 s shorter, so the hero meets it at the same point), and the retry replays the identical phase. Measured with the hero holding Right from the entry and swinging from 16 px (no teleport): **a straight run with no pause dies, every time, at frame 82 (1.37 s; frame 101 before #500 batch 3)** (the hero reaches the cloud's edge as the swell ends); a pause before setting off of 0, 3 or 6 frames (up to 0.1 s) dies the same way, and **a pause of 9 frames (0.15 s) or more succeeds** (sweep of 0-90 frames in steps of 3: 3 deaths, then 28 successes). So the first walk-in is a deterministic first death and the retry teaches the stop-and-wait; whether the swell and the cloud read as that cue is the author's play-through to judge.

**Does the sword still dominate?** Not as an instant answer. For an unaware hero the roof (7.40 s, safe, +1 charge) costs 1.04 s more than the patient melee from the same 30 px point (7.40 s against 6.36 s; the sweeps start at a teleport, the roof from the entrance, so the old "roof is faster than a patient melee" comparison is dropped), and the shot buys 0.68 s over the mastered melee for one charge. For a hero who knows the cycle and is at a **full stock** the mastered melee is still the best (0.90 s faster than the roof, free), so the decision there is only 0.90 s of insurance. The risk is a learning risk, not an execution risk. Whether that is a meaningful decision is the author's play-through to judge.

### Recovery and D26 (swept by `Forest1CombatTest`)

- The floor route needs no support, no lever and no charge; the lever is a reversible toggle and is in reach only on the floor (swept over every x and two heights in both ladder states), so the ladder is never taken from under a hero.
- **216 runs, 0 failures, no Restart Level:** lever pulled 0, 1 or 2 times, guard alive or dead, refill taken or not, the cloud at four points of its cycle (0, 1.2, 2.3, 3.5 s), hero at the entrance, the ladder's foot, the tunnel's mouth, the ladder's top, the roof, by the refill, or (guard dead) beyond the guard; zero charges.
- Clearances (asserted, measured over a standing, a jumping and a climbing hero): the lever's reach (x 2246.5-2281.5, y 282.5-320 since #500; 2214.5-2249.5 before) is **91.5 px** from the danger (guard, whip and cloud, x 2373-2420, y 292-320); a climber (x 2290.5-2303.7) is **69.3 px** away (71.5 px before #500); the cloud lies wholly under the roof; a hero 40 px short of the guard stands for 10 s untouched.
- State boundary: death (the cloud) and room retry on a first visit restore the authored room (guard alive in its place, the puffball's cycle back at its authored phase 2.52 s, ladder raised, lever down, refill back, the unsecured charge rolled back, hero at x 2208; the same route takes the same time twice). A secured room (a forward exit, a healer return; `keepPickups()` and `Level.secureVisit()` in the test) keeps a dead guard dead, a taken refill gone and the ladder as left, and the floor still finishes. Restart Level rebuilds the room from the map.

### Measured (scripted hero, real input; author check, not a novice playtest)

Mastered melee 6.50 s, fireball 5.82 s, roof 7.40 s (since #500 batch 3; 6.83, 6.15 and 7.67 s before): all well inside the 20 s budget (D19), each pinned by the tests within 0.1 s. First-learning time (the 60 s hypothesis) was not measured: that needs a person who has not seen the room (#129-#131). Allocation: 1,162.1 to 1,162.2 B/frame with the guard asleep, 849.7 to 1,017.8 with its attack running (ceiling 1,280, `AllocationBudgetTest.forest1GuardRoom`).

### Tests

`Forest1CombatTest` (headless, real shipped map, 22 tests): census and coordinates, the entrance, the post, the tunnel (no hop, no stomp), the cloud over the sword's places and its phase lengths, the start-phase sweeps of melee and shot, the straight run from the entry, the roof unreachable without the lever, the mastered melee, the mistimed melee and retry, the roof route at ten phases, a full stock, the outcome table, the clearances, the empty-stock routes, the D26 sweep, the secured and death states, Restart Level, the room chain, the lever's reach. Changed existing tests (stated in [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15)): `RoomTest` (three rooms, the route room's exit, the checkpoint in room 3), `RoomCeilingTest` (three forest-1 rooms), `Forest1RouteTest` (ends at the room exit; the checkpoint-x and ledge assertions became an exit-rectangle test and one threshold was reworded, with the reasons in D68), `AllocationBudgetTest` (two new scenes). No threshold was relaxed.

### Agent check in the running web build (Chrome, 2026-11-15)

This was a Claude agent's check, not the author's play-through and not a novice playtest (#129-#131). It used the release web build (`buildWeb`, served locally) of this branch in desktop Chrome, with ordinary keyboard input (A/D, J jump, K sword, L fireball) and no invulnerability. To skip the room-1 trap course, the *served copy* of `forest-1.tmx` had room 1's `RoomStart` moved next to its exit. The shipped map was not changed.

**Seen and working:**
- Rooms 2 and 3 render with their existing tiles: the raised ladder, the slab, the striped one-shot step, the refill pad, the lift, the exit ledge, the tunnel block, the roof and its refill.
- In room 2, the sword swings and a fireball shoots the bat down (stock 3 to 2). The lift carries the hero.
- In room 3, a fireball from the entrance kills the guard. The hero crosses the puffball after its cloud and walks through the tunnel to the checkpoint, and the game then loads `forest-2` (the build of that check; since #349 this point is the room exit to the tree room).
- The console showed no new errors.

**Not done in that check:**
- the jump from the lift onto the exit ledge (the agent's input latency, about 0.5-1 s, could not hit the top of the lift, so it entered room 3 another way);
- the lever, ladder, one-shot step and room-2 refill;
- timed melee under the cloud;
- the roof route.

The macOS desktop build was built and launched, but not played: screen-control access to it was declined. The author played both rooms on desktop on 2026-11-17 (see D67 and D68).

## forest-1 tree room (#349, D80)

Built by [#349](https://github.com/jlogicgames/edgard_in_kimeria/issues/349) (playtest F13): the fourth and last room of `forest-1`, appended after `forest-1-guard` by the author's decision of 2026-11-16, with the level `Checkpoint` moved to the sound tree's crown. Rule and numbers: [D80](DECISIONS.md#d80-tree-branches-one-way-perches-and-rotten-branches-349-playtest-f13-2026-11-16); card: [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-tree); the rotten branch's trap card: [TRAPS.md](TRAPS.md#falling-platform-rotten-log-and-rotten-branch-fallingplatform). **Author check, not a novice playtest:** the numbers are from a scripted hero with real keyboard input; the room has not been seen in the running game ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).

Coordinates are the map's: **world Y grows downward** (floor top y = 320, `y` is an object's top edge, a smaller y is higher on screen). `forest-1.tmx` is now 220 x 23 tiles (3520 x 368 px), `nextobjectid` 98. SHA-256 before `cefc011bd9448e234dbcb6db40151016d3d3aff03bdde435255859f728ee6c46`, after `e4b4196d28eb1444345702d744ea24243404e4c08773bc6e1ff281395c64ee4e`.

```
local x/16  0123456789012345678901234567890123456789   (room 4 only; x = 2880 + 16 * column)
y0-32       ...........................C............   the checkpoint on the crown (x 3240)
y32         ..................|.......<s|...........   crown: sound branch, left of trunk T2 (x 3232)
y80         ..................|r........|s..........   rotten (x 3184) | sound right (x 3280)
y128        ..................|r......<s|...........   rotten          | sound left (x 3232)
y176        ..................|r........|s..........
y224        ..................|r......<s|...........
y272        ..@...............|r........|s..........   entrance 2912; trunk T1 x 3168, trunk T2 x 3264
y320        ========================================   floor; end walls at x 2880 and 3504
```

| What | Object | x (px) | y (px) | Size | Properties | Role |
| --- | --- | ---: | ---: | --- | --- | --- |
| Room 4, `forest-1-tree` | `Room` (id 83), `RoomStart` (id 84) | 2880 | 0 | 640 x 368; start 2912, 272 | none | last room; safe entrance on the floor, both trees in view from it |
| Rotten branches | `FallingPlatform` (ids 85-89) | 3184 | 272, 224, 176, 128, 80 | 32 x 16 each | `skin` branch | the fast line, right of trunk T1; each gives way 1.0 s after it is landed on and falls 200 px |
| Sound branches | `Platform` collisions (ids 90-94) | 3280 / 3232 | 272, 224, 176, 128, 80 | 32 x 16 each | `skin` branch; `mirror` on the left ones (x 3232) | the sure line: right of trunk T2 first (the far side), then left, in turn |
| Crown | `Platform` collision (id 97) | 3232 | 32 | 32 x 16 | `skin` branch, `mirror` | where both lines meet: 48 px above the rotten column's top (16 px to its right) and the zig-zag's top (16 px to its left) |
| Checkpoint | `Checkpoint` (id 7, moved) | 3240 | 0 | 16 x 32 | none | on the crown; ends the level |
| Trunks | `Decor` (ids 95, 96) | 3168, 3264 | 32 | 16 x 288 | `kind` trunk | scenery behind the branches (decor tier, no collision) |
| Floor, walls | collisions 79, 80, 81 | 2880 | 320 | 640 x 48; walls 16 x 320 at x 2880 and 3504 | | closed room; the invisible ceiling is added by the loader (`RoomCeilingTest`) |
| Coin | `Collectable` Coin (id 145) | 3040 | 304 | 16 x 16 | none | the first coin of the game, a sighting only: forest-1 has no healer |
| Beetle (removed) | `Beetle` (id 147, #327) | 3264 | 304 | 24 x 16 | `offNeg` 0, `offPos` 6 | left the room under #500 (D105): the room is forest-1's one `reward` room |
| Deadline | `Deadline` (id 26) | -1728 | 384 | 5376 x 16 | | widened to cover the new room |

Tiles: the floor copies room 3's (ids 23/43/63) and the two end columns are the wall columns (79/99/139 with the floor's edge tiles). No hint and nothing lethal: the beetle added by #327 left under #500, which made the room forest-1's one `reward` room (D105, D107). One coin (id 145, x 3040, y 304) on the floor, 128 px from the entrance and off the climb: the level's first coin ([D96](DECISIONS.md#d96-the-first-coin-is-met-in-the-tree-room-and-the-bat-ingredient-in-the-wall-room-417-2026-11-17), #417).

### How it plays

From the entrance both trees are in view. The near tree's branches are grey and cracked; the far tree's are brown and leafy. **Fast line:** walk to the near tree, jump up through its lowest branch and keep jumping straight up; each branch splits as it is landed on and drops away a second later, behind the climber; from the top, one more jump up and right lands on the crown: 4.95 s. **Sure line:** walk on to the far tree, climb its zig-zag (the first branch is on the far side), then the crown: 6.23 s. **A stop on a rotten branch** rides the hero down with it to the floor, alive (D69 stands him on the floor line); he then takes the sure line: 7.72 s (lowest branch) to 11.05 s (top branch) in total. The lowest rotten branch is the first one met, alone, and its fall is 48 px.

### Recovery and D26 (swept by `TreeClimbTest`)

A fallen branch stays fallen until death or a healer revisit; the branches never kill, so within a visit it stays fallen (the beetle of #327 that could kill a hero beside the sound tree left the room under #500; nothing in it kills). The sure line uses no rotten branch and reaches the crown with any of the 32 subsets of the column gone. Walking off a sinking rotten branch to either side, at seven moments of its warning and fall, always ends alive on the floor or a sound branch, never inside a solid. The load refuses a rotten branch that is the only way up or that hangs above a one-way platform (`FallingPlatform.branchProblem`, `tools/check_tiled_links.py`).

### Tests

`TreeClimbTest` (headless, real shipped map): census and coordinates, the room chain and retry, the placement under D69, the fast and sure lines and their order, the five stop-and-ride routes, the D26 sweep, the sideways exits, the telegraph and the one-way trigger, the load checks, and that the room holds no monster and nothing lethal (#500). Allocation: two scenes in `AllocationBudgetTest` (D80). Changed existing tests are listed in D80.

## forest-2 trap placement plan (#261, D59)

**Status: approved by the author and implemented** ([D59](DECISIONS.md#d59-forest-2-traps-in-the-approach-to-the-boss-a-thorn-vine-an-exploding-root-and-a-falling-stone)). The issue [#261](https://github.com/jlogicgames/edgard_in_kimeria/issues/261) stays open for the author's check in the running game. Issue:
[#261](https://github.com/jlogicgames/edgard_in_kimeria/issues/261) (its checklist carries "update this section"). Coordinates are the map's:
**world Y grows downward**, the floor top is y = 320, an object's `y` is its top edge (a trap standing on the floor has `y = 320 - height`), a larger y is lower on screen. `SpawnPoints` carries a -16 px layer offset: the values below are visible positions, so the Tiled `y` of a `SpawnPoints` object is the value here **plus 16** (the Bomb is Tiled y 320, visible y 304, footprint 304-320).
Map hash: before the change `forest-2.tmx` SHA-256 `85fa8a48b89767c1b42f34b9f001304359794444460944269b636189785da915`; after `cead205d17bc511d5622e69ca34477c8f657cf6240fb20685041cf5a560acb92` (`git hash-object` `f6c44a6f58cd`); recompute with `shasum -a 256 assets/tiles/forest-2.tmx` if the map changes again.
Preview (schematic drawn from the map, not a game screenshot): [forest-2-trap-placement.png](references/forest-2-trap-placement.png).

### What the level offered before the change

Three rooms, left to right: `forest-entry` (x 0-1280), `forest-canopy` (x 1280-1952), `forest-boss` (x 2112-2560, entrance 2192,304; the 160 px between canopy and boss is void, the healer scene runs there). Mastered required routes: entry **16.27 s**, canopy **5.00 s** (budget about 20 s, D3).

**`forest-entry` has no free floor.** Between the hazards that already stand there, the only gaps are 64 px (x 688-752, between the Bomb's blast and the YellowMob's reach), 16 px (896-912) and 48 px (1008-1056). A thorn tuft or explosive needs a 128-176 px jump envelope; a falling trap or vine needs a clear column; a puffball needs a crossing. The room already teaches stomp, ladder and lever, quicksand (removed from the Forest by #486), the Bomb, wall jump and sword, and its route is 16.27 s, so a death late in it replays 16 s. **Plan: the entry room is not touched.**

**Later (#376, 2026-11-17):** the 64 px gap at x 688-752 is now the seam between `forest-entry` (x 0-736) and `forest-walls` (x 736-1280); the second YellowMob's left patrol end moved from x 752 to x 816. Neither room has gained free floor for a trap.

**Later (D63, #295 phase 2): the 128 px canopy slot below holds the `FalseHoneyFungus` at x 1672** (cloud x 1672-1696, zone x 1608-1760), and the harmless `HoneyFungus` stands in `forest-0-lever` (x 150) and `forest-0-wall` (x 1804); see D63 "As built". The mastered floor routes through the canopy take off at hero x 1600 to clear the cloud.

**`forest-canopy` has one slot of about 128 px** (x 1616-1744, under the optional ledge at 1648) between the RedMob (reach 1392-1536), the optional falling platform (column 1568-1600), the vertical Bat (x 1752-1784) and the 48 px exit clearance (from 1824). That fits one puffball and nothing else (a tuft needs 176 px). It is the fallback (option A below).

### As built (option B): the canopy is 448 px longer, three traps in the new stretch

Insert **448 px (28 tiles)** of floor at the cut x = 1808 (64 px after the Bat's reach, 64 px before today's exit). Everything at x >= 1808 moves right by 448 (the old exit 1872 becomes 2320, the end wall 1936 becomes 2384, the whole boss room 2112-2560 becomes 2560-3008, the `Deadline` grows by 448, the map becomes 188 tiles wide). `forest-canopy` becomes x 1280-2400 (1120 px). The boss room is **only translated**: no geometry, object, tile or rule in it changes. Precedent: D48 lengthened forest-0 rooms the same way.

```
x      1760  1808                1908        2072        2232       2320
        Bat    |<---- new 448 px ---->|          |           |          |
floor  =========================================================================
                 T1 vine (1908)      T2 root (2064)   T3 stone (2224)   Exit
```

| # | Trap | Object, position (visible px) | Size | Properties | What it owns | Why there, what it tests |
| --- | --- | --- | --- | --- | --- | --- |
| T1 | Swinging thorn vine (rhythm, D46, first placement in any level) | `ThornVine` x **1896** (anchor at the centre, x 1908), y 256 (top edge = anchor; floor 64 below) | 24 x 40 | `period` 4.5, `amplitude` 50, `phase` 0 (2.77 since #500, history below) | swept area x 1864-1952, danger only while within about 32 deg of vertical | Timing by watching, not reaction; (history, as planned: first thing after the Bat, 124 px from the Bat's reach; Bat 65 left the room under #500 and the vine is met among known beats) |
| T2 | Exploding root (touch, D47, first placement) | `ExplosiveRoot` x 2064, y 296 | 16 x 24 | none | jump envelope x 2000-2144 (floor, 96 px clear above), blast square x 2040-2104 | The choice trap: jump over it (no ammunition), walk through the lit root (the blast comes after 0.8 s; a walker is 80 px past), or pay a fireball / thrown stone from afar |
| T3 | Falling stone (position, D45) | `FallingStone` x 2224, y 0 (flush with the room top, 320 px drop, 0.84 s flight), `skin` forest, `lipTop` 48 (#360: its lip under the dirt block) | 16 x 16 | `zoneX` 2208, `zoneY` 288, `zoneWidth` 32, `zoneHeight` 32 | lethal column x 2224-2240, shadow on the floor | Last: a trap that watches where you stand; the shadow is the telegraph; the answer is to keep walking |

Gaps (px; history, Bat 65 left the room under #500): Bat reach end 1784 -> vine swept 1864: **80**; vine swept end 1952 -> root envelope 2000: **48**; root envelope end 2144 -> stone zone 2208: **64**; stone zone end 2240 -> `RoomExit` 2320: **80** (rule: 48 px from entrance, exit, checkpoint; there is no checkpoint in this room). No trap shares an envelope or a telegraph with an enemy or the Bomb: the Bomb is in another room, 1100+ px back. The three traps use three different telegraph kinds (motion, smoke, ground shadow) one after another.

### How each telegraph reads without a hint (D7)

forest-2 has no hint zones besides the full-room controls zone, and the plan adds none (the traps were taught in forest-0/1). **T1:** the vine moves; its rhythm is the warning, the player watches two swings and goes when it is lifted to the side. **T2:** the root is lit only by touch or a hit from afar; its wind-up (cracks, smoke, glow, 0.8 s, 1.2 s with the assist) leaves time to walk on or step away, and the explosive rule (D47) already lets a naive walker survive (forest-0's stump is passed by walking on in `TrapsLessonTest`). **T3:** the shadow grows for 0.8 s (1.2 s assisted) before the 0.84 s fall; a walker is out of the column long before.

### Measured costs (scripted hero, real keyboard input, zero ammunition; an author check, not a novice playtest)

| Room | Mastered route | Retry cost | Notes |
| --- | --- | --- | --- |
| `forest-entry` | 16.27 s, unchanged (all coins 20.00 s) | 16 s, unchanged | |
| `forest-canopy` | **9.48 s** (was 5.00 s), identical jumping the root or walking through it, identical with the longer-warnings assist; collection route 9.48 s (was 5.00 s) | a death at the stone replays about 9 s | under the 20 s budget; 0 s waiting at the vine with `phase` 0 |
| collection returns through the canopy | 9.62 s first return, 9.48 s later returns (was 5.13 / 5.00 s) | | still inside the 30 s collection limit |
| `forest-boss` | unchanged (only moved +448 px) | unchanged | a death in the boss fight resets the boss room only, so the new traps are never replayed in the fight |

**History (as built by #261, vine `phase` 0):** the mastered route reaches the vine at 4.87 s after room entry and passes it; a hero held at the entrance first still passed when 0 to 0.8 s later, a blind walker 0.9 to 1.3 s later was killed (0.1 s steps). **Since #500 (D105, D109): `phase` 2.77 s**: held 0 to 35 frames (0.6 s) passes, 36 frames and more is killed, the cheapest death 5.93 s. With a blind walker (no jump, no wait) started at every 0.1 s offset, 30 of 45 pass and 15 are killed, so a safe window exists in every period and a careful hero waits at the edge and watches the swing.

### Required route and D26

Walk the canopy as before; pass the vine in a lifted-vine window; **jump the root** (or walk through it); walk on under the stone's shadow; exit. **No ammunition** on the required route. The explosive bypass is the floor jump the loader validates (open floor 64 px each side, 96 px clear, blast inside the room and 80 px clear of the exit). Measured for the root (every pixel, with and without the assist): a running jump whose takeoff has the body's right edge **5 to 57 px before the root** leaves it unlit; an earlier takeoff lands on it and a later one touches it, and neither kills a hero who keeps moving; walking through the lit root is survived (the blast goes off behind the hero) with and without the assist. The fireball refill (id 121, x 1696 on the optional climb) comes before the stretch, so a hero who took the optional climb may pay a fireball for the root; the required route depends on none. The root stays spent if the hero leaves alive and returns (room-state retention); the vine and the stone rebuild on every entry and retry. Nothing can soft-lock: the root is non-solid scenery once spent, the stone is harmless once landed, the vine never blocks (`Forest2TrapsTest` also walks through a spent root).

### Collection returns, boss route and post-boss validation

The healer's collection returns re-enter the canopy and pass the new stretch (times above). The boss rules and `POST_BOSS_VALIDATION.md` are unaffected: the boss room is translated by +448 and nothing inside it changes; the victory walk and the guide scene run in the boss room; the F4/F6 debug paths and the "no checkpoint or F5 on a boss map" rule are independent of x. `BOSS.md` carries the moved coordinates (entrance 2640,304; Boss object 2912,304).

### Deviations from the plan as approved

1. The vine's object is at **x 1896** (its anchor, the centre, is at 1908): the plan's swept-area numbers (x 1864-1952, 80 px after the Bat's reach, 48 px before the root's envelope) assume the anchor at 1908; the object x 1908 would have put the swept area at 1876-1964 and the gap to the root at 36 px.
2. `tools/check_tiled_links.py` ignored an object group's `offsetx` / `offsety`, which the game's loader applies (forest-2's `SpawnPoints` has `offsety` -16), so it judged the root and the stone 16 px too low. The tool now folds the offset into the objects (`apply_layer_offsets`, tested in `tools/test_check_tiled_links.py`); no other map has a group offset, so no other result changes.
3. The preview picture is a schematic of the pre-change map plus the planned objects, not a game screenshot; the table above is authoritative.

### Tests and records changed

New `Forest2TrapsTest` (headless, the shipped map): census and positions, every gap to enemies, exit and entrance at least 48 px and none shared, the boss room translated by exactly 448, the zero-ammunition route (jumping and walking through the root, three identical attempts after retries, under 20 s, with the assist), the vine kills a standing hero and a blind walker at the wrong time and the mastered route has slack, the root's jump window, walk-through and hit from afar with the full 0.8 s warning, the stone's warning (49 / 73 updates) and its column. Changed existing tests, coordinates and counts only, no tolerance relaxed: `BossCombatTest` (2112 -> 2560, 2192,304 -> 2640,304), `RhythmTrapsTest` (allowance: `ThornVine` in forest-2), `ExplosiveTrapsTest` (census: one root in forest-2), `FallingTrapsTest` (forest-2 has one stone, no branch). `RoomReplayTest` needed no change (its canopy takeoff list works unchanged; times above).

### Option not taken

Option A (no geometry change, one puffball in the 128 px slot at x 1616-1744) was rejected: it adds almost nothing to the hardest level.

### Author's answers recorded

Lengthen the canopy by 448 px; three traps; both new families (vine and root); no enemy on the new stretch; no Bomb companion; no hints; no extra stone pickup. Open for the running game (tracked by #261): the look of the vine, root and stone in this map, the earth blocks that hold the vine and the stone, and how the 9.5 s retry loop feels.

### The lever lesson drives a lift (D127, #549, 2026-11-22)

`forest-0-lever` no longer lights a torch: the lever (id 41, x 376) starts and stops a lift (`Escalator` id 40, x 432, y 256, 32 x 16, vertical, `offNeg` 3, at rest until pulled), a one-way step (id 142, x 400, y 304) and a two-piece ledge (ids 143, 144, x 472-536, y 160) let the curious ride it. The lift's deck bottom is y 272 at rest, so no lift state touches the floor route: the exit stays ungated (D26, D34 amended). The recipe above still holds with `Escalator` as the mechanism: a platform lever is a plain `Trigger` with `target`; the tutorial recipe's "`Actionable` `Torch` or `Wall`" is the list of `Actionable` types only. Hints: `HINT_LEVER_TORCH` ("Use the lever to start the lift or stop it") and `HINT_LEVER_EXIT` keep their key names. Hand-operated objects stay 32 px from ladder zones in every room (D126, `check_ladder_clearance`); `forest-1-guard`'s lever moved from x 2256 to x 2240 for it.
