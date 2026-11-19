# Room cards

One card per shipped room, in play order (`KimeriaGame.LEVEL_NAMES`, then each map's room order). The rules the
cards serve are [D72](DECISIONS.md#d72-every-room-gives-the-player-something-new): every room introduces, develops or
combines at least one element the player has not met in that form; and
[D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499):
every room keeps the player busy, about one beat every 3 s of the mastered route, and a new element is met in a safe
pocket, not in a room of its own. How to write a card and when:
[ROOMS.md, "Every room gives something new (D72)"](ROOMS.md#every-room-gives-something-new-d72) and its section
"Density and first meetings (D105)". The skill `kimeria-room-design` (`.agents/skills/`) walks through it.

`RoomCardsTest` (`:core:test`) reads this file and the maps. It fails when a shipped room has no card, a card names
no room, a required field is empty, an element id is unknown, a Tiled type in a map is missing from the registry, the
room where an element is first met does not list it under **Introduces**, a card introduces something already met,
or develops or combines something not met yet, or a non-lesson room introduces more than two elements without an
**Overload reason** that cites an issue. For D105 it also fails when an element has no **Beat kind**, a room outside the
exempt roles has fewer than 3 beats, fewer than 2 distinct elements or more than 4.0 s per beat (unless it is one of
the rooms shipped below D105, on the test's shrink-only list, with **Below density** citing its rework issue), has
fewer than 1.5 s per beat without an **Overload reason** that cites an issue (`#` and digits), lists a beat that is a
pickup, has no map object (or, for a skill, geometry) of its own at that x, is out of rising-x order, or lies within
96 px of a listed beat of the same element; gives a **Beat time** that is not the first time of **Mastered route** or
that the named route test does not pin as a float literal; or introduces an element without a **First meeting** entry
that is among the first two beats or cheap to retry (at most 6 s), clear of other lethal objects by 96 px, with its
`pocket at x N`, and naming a test method that names the room, the element and N (`FirstMeetingPocketTest`, which
stands the hero there in the real camera view and asserts he is never hit while the element is seen acting). A beat
time must be asserted (`assertEquals`) as a float literal in the route test the card names. An exempt role is checked against the room: a `lesson`
in `forest-0` or the test's fixed list, a `boss` with the boss, a `rest` or `reward` with nothing lethal. What the test
cannot judge (whether a develop really changes the answer) is the reviewer's and the author's job.

## Card format

A card is a `### <room>` heading (the room's `Room` name; a map without a `Rooms` layer uses the map name) followed
by a two-column table. Keys, in this order:

| Key | Required | Value |
| --- | --- | --- |
| Level | yes | map name, play order, room index |
| Role | yes | one of `lesson`, `variation`, `combination`, `challenge`, `approach`, `reward`, `rest`, `boss` |
| Purpose | yes | one sentence: what the player learns or proves here |
| Introduces | one of these three | element ids met here for the first time, comma-separated, or `-` |
| Develops | one of these three | `id: what changes the answer`, entries separated by `;`, or `-` |
| Combines | one of these three | `id + id: why both are needed`, entries separated by `;`, or `-` |
| Repeats | no | known element ids repeated for mastery |
| Mastered route | yes | measured time and the test that measures it, against about 20 s (D19) |
| First learning | yes | measured value and source, or "not measured" with the issue that will measure it (D19: about 60 s) |
| Retry cost | yes | what the most expensive death replays |
| Beats | yes, outside `lesson`, `boss`, `rest`, `reward` | the required route's beats in rising map x, `id@x`, comma-separated: one placed instance of an element whose Beat kind counts, `x` inside that object's span (its box, a polyline's points, a horizontal patrol), each object backing one beat; a `skill` beat names its geometry's x; two instances of one element within 96 px are one beat, listed once (D105) |
| Beat time | with Beats | `seconds (`TestClass`)`: the zero-charge mastered required time, the first time `Mastered route` states, pinned within 0.1 s in that test as a float literal (D105) |
| First meeting | when a room outside `lesson` and `boss` introduces an element that counts as a beat | one entry per such element, separated by `;`: `id: beat k`, then `, retry R s` when it is not among the first two beats, then `pocket at x N` (the hero's centre at the safe spot just before that beat) and the test method that pins it in backticks (`FirstMeetingPocketTest.method`, whose body names the room, the element id and N); no `;` inside an entry (D105) |
| Below density | only on the rooms shipped below D105 | the measured beats, time and pace, and the issue that reworks the room (D105) |
| Optional | no | optional routes and rewards |
| D26 | no | how every mandatory exit stays reachable |
| Hints | no | hint zones, if any |
| Landmark | no | the one thing the player will remember the room by (a suggestion from the Primal Planet study, #352) |
| Overload reason | when over a cap | why more than two introductions, or fewer than 1.5 s per beat (D105), and the issue that resolves it |
| Status | no | the D72 audit verdict and follow-up |
| Decisions | no | the D-numbers the room implements |

Lesson rooms (`Role` = `lesson`) follow D33-D40 and D48 (one lesson element, plus at most one trap) instead of the
two-introduction cap and the density of D105. Outside `forest-0` a lesson room holds no lethal element (a `hazard` or
`monster`) and must be on the test's fixed list (`LESSON_ROOMS`, today only `forest-1-lever`, D86): it teaches a
combination with nothing at stake (D84). `boss` rooms are outside the density too (D19 measures them apart), only when
they hold the boss; `rest` and `reward` rooms, the calm beat after danger (#352), are exempt from its minimum only
with nothing lethal in them, at most one per level.

## Element registry

What counts as an element is what changes what the player does. A Tiled type maps to one element; several types may
map to the same element when only the look differs (a **reskin**, not novelty under D72). Keys are the object's
`type`, then `:` and the `Actionable` `type`, the `Decor` `kind`, a non-default `skin`, `lever`/`running` for an
`Escalator` (`running` = false or not), or the `Collectable` name; `Collisions:` prefixes a collision type.
Skills are what the geometry asks for; their first sighting is declared on the cards only. Their `Geometry:` keys are not Tiled types: `RoomCardsTest` derives them for D105 beats only, `Geometry:wall` from a free-standing `Collisions:Wall` (not from the map's top) at least 96 px tall, `Geometry:branch` from any object with `skin=branch`.
How each trap looks and warns is its own card in [TRAPS.md](TRAPS.md) (D71); this registry only groups traps by
what the player does about them.

**Beat kind** (D105) says whether a placed instance counts as a beat on the required route: a `hazard` (a trap) and a
`monster` once per instance; a `mechanism` once per lever the route pulls or hits, with everything it drives (pull,
ladder and climb are one beat; a ladder with no lever is not a beat of its own); a `platform` once per running
platform the route boards or must pass while it moves; a `skill` once per obstacle that needs it (one wall, one tree),
posed by its geometry. A `pickup` and a `harmless` element never count. Two instances of one element whose spans lie
within 96 px of each other are one beat (a row of falling platforms, the two walls of `forest-walls`); plain geometry
(a pit, a step) is not a beat.

| Element id | Tiled keys | What the player does | Beat kind |
| --- | --- | --- | --- |
| lever | `Trigger`, `Actionable:Torch` | operates a lever and reads its linked effect | mechanism |
| ladder | `Ladder` | lowers a ladder by lever and climbs it | mechanism |
| lever-wall | `Actionable:Wall` | raises and lowers a wall that blocks the way | mechanism |
| lever-platform | `Escalator:lever` | starts and stops a platform and rides it | mechanism |
| running-platform | `Escalator:running` | times a jump onto a platform that never stops | platform |
| carry-throw | `Stone` | picks up a stone and throws it (at a lever, at a trap) | mechanism |
| carry-torch | `CarryableTorch` | carries a torch and lights things with it | mechanism |
| crumble-support | `FallingPlatform`, `FallingPlatform:log`, `FallingPlatform:branch` | crosses before the support gives way | hazard |
| proximity-thorn | `ThornRoot`, `ThornTuft` | jumps thorns that rise when the hero comes near | hazard |
| falling-branch | `FallingBranch` | keeps walking under a branch that falls behind him | hazard |
| falling-stone | `FallingStone` | reads the shadow and does not stand under it | hazard |
| touch-explosive | `ExplosiveStump`, `ExplosiveRoot`, `Bomb` | jumps it, walks clear of the wind-up, or sets it off from afar | hazard |
| puffball | `Puffball` | crosses a rhythmic spore cloud in its calm | hazard |
| thorn-vine | `ThornVine` | passes a swinging vine by watching its rhythm | hazard |
| bramble | `Bramble` | waits out a thorn arm that grows across a lane on a clock and passes in its open window (D89) | hazard |
| hanging-hive | `HangingHive` | passes under, or climbs through, a falling curtain of stingers in its calm; may knock the hive down | hazard |
| ember-fissure | `EmberFissure` | waits beside a floor crack for its jet of fire to sink and passes in the calm (D91) | hazard |
| honey-fungus | `HoneyFungus` | sees a harmless mushroom swell and burst | harmless |
| false-honey-fungus | `FalseHoneyFungus` | tells the lethal twin apart and jumps or waits out its cloud | hazard |
| deadfall | `Deadfall` | jumps a trip vine, or trips it, stops and lets the log swing past | hazard |
| quicksand | `Collisions:QuickSand` | keeps moving across sinking ground; no Forest room uses it (moved to the Swamp, planned under [#105](https://github.com/jlogicgames/edgard_in_kimeria/issues/105), [#486](https://github.com/jlogicgames/edgard_in_kimeria/issues/486)); the engine support stays | hazard |
| redmob | `RedMob` | beats a walking enemy with a visible whip wind-up (sword or stomp) | monster |
| yellowmob | `YellowMob` | stomps a walking enemy | monster |
| bat-enemy | `Bat` | passes or beats a flying enemy | monster |
| beetle | `Beetle` | outruns a beetle that commits and bursts, or kills it from afar with a fireball or a stone (D100) | monster |
| snail | `Snail` | crosses a snail's line during its reload, jumps or cuts its gob, comes up behind it or hides behind cover, or shoots it (D106) | monster |
| spider | `Spider` | steps out of a spider's locked pounce and strikes it while it recovers, meets its leap with the sword, or shoots it (D102); a spider on a route (a polyline) also climbs walls and drops on its thread (D103): each of those is a **develop** of `spider`, one per room, listed under Develops by the room that first shows it: the climb in `forest-1-climb`, the drop in `forest-1-route` (#484) | monster |
| boss | `Boss` | fights the stage boss | monster |
| fireball-refill | `FireballRefill` | picks up a ranged-attack charge | pickup |
| coin | `Collectable:Coin` | collects coins, often off the required route | pickup |
| ingredient | `Collectable:Bat`, `Collectable:Mushroom` | collects a healer ingredient | pickup |
| wall-jump | `Geometry:wall` (skill) | wall-jumps a wall the route cannot pass otherwise | skill |
| branch-climb | `Geometry:branch` (skill) | climbs a tree by jumping up through one-way branches (`Collisions:Platform` with `skin=branch`, D80) | skill |

Not elements (scenery, markers, plain geometry): `Decor:bush`, `Decor:lookalike`, `Decor:trunk`, `Decor:crown`, `HangingVine`, `Torch`
(decorative), `Checkpoint`, `Player`, `Deadline`, `Collisions:Wall` (clamberable room walls), `Collisions:Platform`
(one-way platforms), plain `Collisions`, the `Rooms` markers and `Hints`.

## forest-0 (tutorial, play order 1)

### forest-0-lever

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 1 of 6 |
| Role | lesson |
| Purpose | Cause and effect, safely: a lever lights and darkens a torch. |
| Introduces | lever, honey-fungus |
| Develops | - |
| Combines | - |
| Mastered route | about 4.3 s (`LeverLessonTest`); target about 20 s (D19) |
| First learning | not measured (#135) |
| Retry cost | none: the room has no hazard |
| D26 | the exit is reachable with the torch on, off or never touched (D34) |
| Hints | walk, lever, exit, live controls label |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D33, D34, D63 |

### forest-0-ladder

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 2 of 6 |
| Role | lesson |
| Purpose | A lever changes the route: it lowers the ladder to the exit ledge. |
| Introduces | ladder, proximity-thorn |
| Develops | - |
| Combines | - |
| Repeats | lever |
| Mastered route | 8.82 s (`LadderLessonTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the thorn root hit comes about 2.6 s in |
| D26 | toggle ladder; the exit is reachable from every state (D35) |
| Hints | root, lever, climb, exit |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D35, D48 |

### forest-0-wall

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 3 of 6 |
| Role | lesson |
| Purpose | A lever moves an obstacle: the wall across the corridor. |
| Introduces | lever-wall, falling-branch |
| Develops | - |
| Combines | - |
| Repeats | lever, honey-fungus |
| Mastered route | 6.48 s (`WallLessonTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the branch is met in the first few seconds; about 6.5 s at worst |
| D26 | the wall is a toggle; the exit is reachable from every state (D36) |
| Hints | branch, lever, pass |
| Status | D72 audit 2026-11-16: keep, watched. The branch carries the novelty; the wall repeats the ladder room's "press, the way opens" and is the fourth lever press in a row. A timing develop is an option if the author wants one. |
| Decisions | D36, D48 |

### forest-0-platform

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 4 of 6 |
| Role | lesson |
| Purpose | Ride a platform the lever starts and stops, up to the exit ledge. |
| Introduces | lever-platform, falling-stone |
| Develops | - |
| Combines | - |
| Repeats | lever |
| Mastered route | 11.85 s (`PlatformLessonTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the stone is met first; about 12 s at worst |
| D26 | the platform is a toggle; the exit is reachable from every state (D37) |
| Hints | stone, lever, ride |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D37, D48 |

### forest-0-stone

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 5 of 6 |
| Role | lesson |
| Purpose | Carry a stone and throw it at a lever out of reach across the chasm. |
| Introduces | carry-throw, touch-explosive |
| Develops | - |
| Combines | - |
| Repeats | lever-platform |
| Mastered route | 6.88 s, 8.47 s setting the stump off from afar (`StoneLessonTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | about 7 s at worst |
| D26 | the stone comes home when it falls or hits the lever (D39); the stump has a floor bypass (D47) |
| Hints | throw, stump, exit, across |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D39, D47, D48 |

### forest-0-traps

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 6 of 6 |
| Role | lesson |
| Purpose | A support that gives way: cross the falling platform before it drops. |
| Introduces | crumble-support |
| Develops | touch-explosive: the stump now stands on the walking route and is lit by touch, not by a throw |
| Combines | - |
| Mastered route | 6.1 s (`TrapsLessonTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | about 6 s at worst |
| D26 | the pit is a retry, never a soft-lock (D40) |
| Hints | platform, stump |
| Status | D72 audit 2026-11-16: keep. The map's `lesson` property is `falling-platform` (was `existing-traps`, renamed in [#377](https://github.com/jlogicgames/edgard_in_kimeria/issues/377)). |
| Decisions | D40, D47 |

## forest-1 (play order 2)

### forest-1-mob

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 1 of 11 (800 px, x 3520-4320: placed after the tree room on the map, first in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | The first enemy, alone on flat ground: read the whip's raised arm, then sword it, stomp it or spend a charge; the refill behind it gives a spent charge back. |
| Introduces | redmob, fireball-refill |
| Develops | - |
| Combines | - |
| Mastered route | 7.43 s, zero charges, the sword takes the mob (`RoomReplayTest`); 6.97 s to the refill with two charges; target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | under 3 s: the mob is the only hazard, its range starts about 150 px from the entrance |
| Beats | redmob@3776 |
| Beat time | 7.43 (`RoomReplayTest`) |
| First meeting | redmob: beat 1, its range (x 3728-3872) is clear of other lethal objects, but it stands idle until the hero is in its reach (measured: idle for a hero centre up to x 3728, a hit from x 3732), so no safe spot shows it acting, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Below density | 1 beat in 7.43 s (7.43 s per beat): the first RedMob in a room of its own (D85), built before D105; reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| D26 | no support, no one-shot state; the sword or a stomp needs no ammunition |
| Hints | none (forest-1 has none, D33) |
| Status | new, [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375) (cards approved by the author 2026-11-17); `Forest1MobTest` checks the mob and the refill are met one at a time. Role `variation`, not `lesson`: lesson rooms follow the tutorial rules D33-D40 and D48 (hint zones, one lesson element plus at most one trap), and this room has two elements, neither a trap, and no hints (forest-1 has none, D33); it is held to the two-introduction cap instead, which it meets |
| Decisions | D85 (revises D58), D72 |

Sketch (Y grows downward, floor top y 320; walls x 3520-3536 and 4304-4320):

```
x    3520 3552      3728   3776  3824            4208   4256    4320
     |wall|start    [ hero-centre range ]           R     exit  |wall|
     |    |  @       .......  M  .......            o      [ ]  |    |
=====+==========================================================+=====  floor y 320
```

- RoomStart (3552, 272). RedMob, the old id 11 moved here, at x 3776, y 288, `offNeg`/`offPos` 3 tiles (wakes for a
  hero centre in x 3728-3824). It is the only object on screen from the entrance.
- FireballRefill, the old id 25 moved here (identity kept), at x 4208, y 288 (one tile over the floor), picked up by walking.
  With the camera clamped to the room, it is off screen while the hero stands in the mob's range (screen right edge
  4160 until the hero's centre passes about 3840), so the mob and the refill are each met alone, in that order.
- RoomExit (4256, 272, 32 x 48).

### forest-1-deadfall

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 2 of 11 (640 px, one screen, x 4960-5600: appended after the lever room on the map, second in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A trap whose trigger is in plain sight: jump the trip vine, or trip it, stop and let the log swing by. |
| Introduces | deadfall |
| Develops | - |
| Combines | - |
| Mastered route | 5.17 s jumping the vine (take-offs 2 to 60 px before it), 6.47 s tripping it and stopping (6.88 s with the longer-warnings assist) (`DeadfallTrapTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 2.65 s: a hero who walks in without jumping is hit 2.65 s after the entrance (2.97 s with the assist) |
| Beats | deadfall@5248 |
| Beat time | 5.17 (`DeadfallTrapTest`) |
| First meeting | deadfall: beat 1, retry 2.65 s, watched from the entrance with nothing else in the room, but a touch trap acts only when its vine is tripped (D88), so no safe spot shows it acting, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Below density | 1 beat in 5.17 s (5.17 s per beat): the deadfall as the room's only object (D88), built before D105; reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| D26 | no support, no one-shot state the route needs: a jump leaves the log tied, a trip leaves it hanging from the snag above head height and the vine cut, and the exit is reachable from both (`DeadfallTrapTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the log hauled up and lashed to the bough by the tree trunk |
| Status | new, [#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363) (card, art and placement approved by the author 2026-11-17). The deadfall is the room's only object and is in view from the entrance with the exit (D84). Built and tested with a scripted hero; played by the author 2026-11-17, approved |
| Decisions | D88, D72 |

Sketch (Y grows downward, floor top y 320; walls x 4960-4976 and 5584-5600; P is the rope knot, x 5296, y 252):

```
x    4960 4992        5176-5192        5228  5296  5364      5536    5600
     |wall|start       vine (trip)      [ swing band ]  trunk  exit  |wall|
     |    |  @          =             bough====P====log| |      [ ]  |    |
=====+==========================================================+=====  floor y 320
```

- RoomStart (4992, 272). Deadfall (the bough, 112 x 24) at (5248, 243), `swing` left: the log is tied on the right
  and swings left, at a hero coming from the entrance. A `Decor:trunk` at x 5356 holds the bough's thick end.
- RoomExit (5536, 272, 32 x 48).

### forest-1-traps

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 3 of 11 (1280 px, two screens) |
| Role | combination |
| Purpose | Machinery that runs by itself, then the trap course: tuft, a known enemy, a spore cloud and a log over a lethal pit. |
| Introduces | running-platform, puffball |
| Develops | crumble-support: the log crumbles over a lethal pit, not a recoverable drop; proximity-thorn: the tuft's wider 80 px zone |
| Combines | - |
| Repeats | redmob, lever, lever-wall, lever-platform |
| Mastered route | 12.27 s to the room exit (`Forest1TrapsTest`, `RoomReplayTest`), unchanged by #375; 14.98 s with the longer-warnings assist; target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 12.73 s for a death at the log (D58; `Forest1TrapsTest`, unchanged by #375) |
| Beats | crumble-support@160, running-platform@304, lever-wall@480, lever-platform@544, proximity-thorn@696, redmob@864, puffball@944, crumble-support@1088 |
| Beat time | 12.27 (`RoomReplayTest`) |
| First meeting | running-platform: beat 2 (`Forest1TrapsTest`), its patrol (x 256-384) passes 32 px from the falling platforms (x 96-224), under the 96 px clearance, built before D105 (D58) and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500); puffball: beat 7, met one screen after the escalators right behind the known RedMob, built before D105 (D58) and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| D26 | the pit is the level `Deadline`, a retry; nothing is re-armed (D58) |
| Hints | none |
| Status | D72 audit 2026-11-16: change; done by [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375) (D85): the first RedMob (id 11) and the refill (id 25) moved to `forest-1-mob`, the lever-and-torch pair (ids 12, 13) was removed, the trap course x 616-1280 is untouched. |
| Decisions | D58, D67, D72, D85 |

What changed (x 0-1280, unchanged geometry):

```
x  32    96-224     128  240   272   304-384  312  352   432    480-528  544   696  864  944  1024-1168  1216
   start platforms  L12  T13   L15   esc 17   R25  M11   esc19  wall 14  L33   tuft M38 puff  pit+log    exit
                    ---  ---               ---  ---
                    removed (12, 13); moved to forest-1-mob (11, 25)
```

- Escalators (x 256-464) are then met alone: from there the screen ends before x 784, so the tuft (known, x 696) is the
  only other thing in view and the puffball (x 944) is not. The puffball enters the screen only when the hero's centre
  passes about x 624, after the escalators.
- The trap course after the wall (tuft, RedMob 38, puffball, log, room exit) keeps every object, id and coordinate.

### forest-1-lever

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 4 of 11 (640 px, one screen) |
| Role | lesson |
| Purpose | One lever drives two mechanisms in opposite states: pulling it lowers the ladder and stops the lift, pulling again raises the ladder and restarts the lift; both are in view when it is pulled, and a wrong pull costs nothing. |
| Introduces | - |
| Develops | lever: one lever now drives two mechanisms at once, a ladder and a lift, so the player must read both effects, not one |
| Combines | ladder + running-platform: the lift is the only way up to the lever's ledge and the lowered ladder is the only way on to the exit ledge, so the way forward needs both, one after the other, and the same lever is what changes both |
| Repeats | lever, ladder, running-platform |
| Mastered route | 8.43 s (`Forest1LeverLessonTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)); the author played the room on 2026-11-17 (D86) |
| Retry cost | none: the room has no enemy, trap or pit; a wrong pull costs the pull that undoes it (about 0.1 s) |
| D26 | the lever is a reversible toggle; the floor runs under the lever ledge, so from the floor the hero either rides the running lift to the lever or, with the ladder down, climbs it to the exit; 312 lever-timing, pull-count and drop runs and every floor start in both lever states reach the exit without Restart Level (`Forest1LeverLessonTest`) |
| Hints | none: forest-1 shows none (D33) |
| Landmark | the lift beside the lever, the ladder hanging above the gap, the ledge it will reach |
| Status | New (#418, D86, after the D84 playtest). D84 check: (1) taught before gating: the combination is met here, with nothing at stake, before the route room gates progress with it; (2) the effects land on screen: the room is one screen wide (640 px = the camera's view), the lift is 80 px and the ladder 192 px from the lever; (3) a stuck player always sees something untried: the lever, the running lift or the hanging ladder; (4) the challenge is only boarding a moving lift. The room is built and tested with a scripted hero; the author played it on 2026-11-17, and no novice has played it yet (#129-#131). |
| Decisions | D84, D86, D67 |

### forest-1-spider

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 5 of 11 (1120 px, x 6880-8000: appended at the map's end, listed after `forest-1-lever` in the `Rooms` list, which sets the play order; widened from 640 px by #500) |
| Role | variation |
| Purpose | Two monsters, one at a time, then two known traps: make the spider miss and strike it; step up out of the snail's line and watch it notice you, then jump its gob; keep walking under a loose stone; jump a thorn tuft. |
| Introduces | spider, snail |
| Develops | - |
| Combines | - |
| Repeats | falling-stone, proximity-thorn |
| Mastered route | 11.53 s, zero charges: baiting the spider's pounce, stepping back and striking it in its recovery, then jumping the snail's gob and walking on past the snail in its reload without a swing (it is left alive behind the hero and its next gob falls short of him: pinned, it spat and is alive at the exit), walking on under the stone and jumping the tuft, the same on a retry; 9.80 s with one fireball at the spider from afar; 10.48 s rushing the spider with the sword during its wind-up (`Forest1SpiderTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 11.23 s at worst: a hero who answers the spider and the snail and stops in the stone's column is struck 11.23 s after the entrance; walking into the bristled tuft 11.08 s; walking into the gob without a jump 8.18 s; stopping in the spider's reach 2.32 s (`Forest1SpiderTest`) |
| Beats | spider@7184, snail@7600, falling-stone@7744, proximity-thorn@7872 |
| Beat time | 11.53 (`Forest1SpiderTest`) |
| First meeting | spider: beat 1, retry 2.32 s, pocket at x 7030 (the hero's centre), inside its hearing, where it notices him and chases to its range's end without reaching him, with the snail off screen (the camera ends at x 7520 while the spider is met) (`FirstMeetingPocketTest.forest1SpiderTheSpider`); snail: beat 2, pocket at x 7456 on a one-way step (x 7440-7472, top y 288), inside its sight and above its firing line, where it notices him and watches without spitting (D106), out of the spider's hearing (`FirstMeetingPocketTest.forest1SpiderTheSnail`) |
| D26 | no lever, no support, no one-shot state; the sword or a stomp answers both monsters, the stone and the tuft need no ammunition, and a retry rebuilds everything calm |
| Hints | none (no hint for a monster: new monsters are met in play, D100) |
| Landmark | the snail turning its stalks up at the hero on the log step |
| Status | new, [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484) (cards approved by the author 2026-11-19); reworked under D105 by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) with the snail (#329) (card approved by the author 2026-11-19): the room grew from 640 to 1120 px so the snail is off screen while the spider is met (one unknown at a time, D72), and `forest-1-climb` moved 480 px right. Built and tested with a scripted hero |
| Decisions | D72, D100, D101, D102, D105, D106 |

Sketch (Y grows downward, floor top y 320; walls x 6880-6896 and 7984-8000; `^` the one-way step x 7440-7472, top y 288):

```
x    6880 6912     7136  7184  7232   7440-7472  7584 7600 7616  7744       7872   7920    8000
     |wall|start   [ spider range ]     ^step     [ snail range ]  stone      tuft   exit  |wall|
     |    | @         <  S  >                       <  n  >        (lip)       *     [ ]   |    |
=====+==================================================================================+=====  floor y 320
```

- `Spider` (id 148) x 7184, y 304, 32 x 16, `offNeg` 3, `offPos` 3: `position.x` 7136-7232, its fang reach x 7089-7303 at most.
- One-way `Collisions:Platform` (id 169) x 7440-7472, y 288-304, drawn as the plain platform.
- `Snail` (id 168) x 7600, y 304, `offNeg` 1, `offPos` 1 (range 7584-7616): its lines end on the floor at x 7396-7428 (left)
  and x 7794-7826 (right), clear of the entrance and the exit; from the step the hero is in its sight and 23 px above its line.
- `FallingStone` (id 170) column x 7744 (zone x 7728-7776, y 288-320); `ThornTuft` (id 171) x 7872, zone 80.
- `RoomStart` (id 161) (6912, 272); `RoomExit` (id 162) x 7920-7952, y 272-320. Floor (id 151) x 6880-8000; walls ids 152, 153.

### forest-1-bramble

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 6 of 11 (640 px, one screen, x 5600-6240: appended at the map's end, listed after `forest-1-spider` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A thorn arm grows over a ladder top on a clock: watch it from the floor, wait on the ladder, climb out when it pulls back. |
| Introduces | bramble |
| Develops | ladder: its top is now a place to wait and watch, and leaving it is timed |
| Combines | - |
| Repeats | ladder |
| Mastered route | 10.73 s from a fresh start, the same with the longer-warnings assist; 9.42 to 12.80 s over every start offset (0.05 s steps) for a hero who goes when the lane is open or waits on the ladder for the arm to come back (`Forest1BrambleTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 6.70 s: a hero who climbs straight out from a fresh start is caught 6.70 s after the entrance and replays the room from its entrance (`Forest1BrambleTest`) |
| Beats | bramble@6160 |
| Beat time | 10.73 (`Forest1BrambleTest`) |
| First meeting | bramble: beat 1, pocket at x 6170 at the ladder's foot, where its band never reaches and the arm is seen growing and pulling back (`FirstMeetingPocketTest.forest1BrambleTheBramble`) |
| Below density | 1 beat in 10.73 s (10.73 s per beat): the bramble as the room's only hazard (D89), built before D105; reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| D26 | no lever, no support, no one-shot state: the ladder is always down and the arm comes back every 4 s, so waiting on the ladder always gives another window; the band never reaches the floor, so the walk to the ladder is safe (`Forest1BrambleTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the thorn arm sweeping over the ladder top under the shelf's end |
| Status | new, [#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364) (card, art and placement approved by the author 2026-11-17). D84 check: the bramble is the room's only hazard and is in view, cycling, from the entrance; the hero walks the whole floor under it out of reach before the climb, so its rhythm is shown before it gates progress. Built and tested with a scripted hero; played by the author 2026-11-17, approved |
| Decisions | D89, D72 |

Sketch (Y grows downward, floor top y 320, shelf top y 208; walls x 5600-5616 and 6224-6240):

```
x    5600 5632              5904 5920                    6160     6192 6208 6224 6240
     |wall|start             [ shelf 5904-6192, y 208-224 ]  B~~~~~~~~~~~~~~~~|wall|
     |    | @                 E (exit 5920-5952, y 176-208)          | L  |    |    |
=====+=====================================================================+====+====  floor y 320
```

- `Bramble` (id 127) x 6160, y 184, 64 x 24, `root=right`: rooted in the right wall's face at x 6224, the band y 190-206,
  the grown arm over x 6160-6224, the always-lethal clump x 6212-6224.
- Static `Ladder` (id 126) x 6192-6208, y 208-320, no lever: one tile off the wall, so its top is clear of the clump (D89).
- `RoomExit` (id 125) x 5920-5952, y 176-208, on the shelf's left end. `RoomStart` (5632, 272).

### forest-1-climb

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 7 of 11 (640 px, one screen, x 8000-8640: at the map's end, listed after `forest-1-bramble` in the `Rooms` list, which sets the play order; moved 480 px right when `forest-1-spider` grew, #500) |
| Role | combination |
| Purpose | The hero must wait on the block for the lift: the snail on the block top makes him jump, the spider coming up the block's far face punishes him for standing still; finish the snail, bait the spider and strike it, shoot it as it tops the edge, or be bitten waiting. |
| Introduces | - |
| Develops | spider: the wall climb. It comes up the block's far face to a hero it cannot reach on the floor (it never strikes from a wall), so a block is no refuge: the hero must meet it on the block top |
| Combines | spider + running-platform: the running lift is the only way up to the exit, so the hero must wait on the block for it, and the wait is what the climbing spider punishes; snail + spider: both on the block top where the hero waits: its gob must be jumped and moving is what makes the spider's locked pounce miss, while a hero who stands still is the spider's target |
| Repeats | falling-stone |
| Mastered route | 13.33 s with no charge (the required route): under the stone on the approach, up the steps, the snail finished on the block top (the gob jumped, then the sword), the spider baited on the block top and struck in its recovery, then the lift, the same on a retry; 10.77 s with one fireball, shot the moment it tops the block's far edge (13.33 s for a pause of 1.87 s or more on the second step, which meets the snail later and misses a lift); both at every pause of 0 to 2 s on the second step (sampled every 4 frames); a shot fired even after its leap has begun still wins (D102's tie rule); the lift ride alone (no spider) is 10.77 s (`Forest1ClimbTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 6.65 s at worst: a hero who finishes the snail, waits at the block's left end, out of the spider's reach, and walks to the lift as it comes down is bitten 5.33 to 6.65 s after the entrance; one who waits under the lift is bitten 5.33 to 6.63 s in; each pinned at every pause of 0 to 2 s on the second step, sampled every 4 frames; standing on the snail's line at the block's left end is hit by the gob 4.73 s in; stopping under the stone 1.98 s in (`Forest1ClimbTest`) |
| Beats | falling-stone@8112, snail@8344, running-platform@8384, spider@8432 |
| Beat time | 13.33 (`Forest1ClimbTest`) |
| D26 | no lever, support or one-shot state: the lift never stops, the steps are static one-way platforms, and the spider's pounce on the block top never reaches the shelf or the exit; the snail's lines never reach the shelf or the exit; the lift's underside stops 19 px above the spider's body, clear of its leap's 6 px arc, and 16 px above the snail's shell, so the lift never touches either (a full-cycle test with the spider waiting under it); a retry rebuilds both calm |
| Hints | none (D100) |
| Landmark | the snail on the block top under the lift, the spider coming up the far face |
| Status | new, [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484) (cards approved by the author 2026-11-19; the layout reworked to the author's Option A after review, 2026-11-19: the first layout, a ledge climbed beside its face, let the jump onto the ledge stomp the spider as it came over the lip in 1,700 of 1,736 measured runs). Reworked under D105 by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) with the snail (#329) and a falling stone (card approved by the author 2026-11-19); the mastered route finishes the snail before the spider hears the hero, so the climb is still first seen alone. The whole room is in view from the entrance. Built and tested with a scripted hero |
| Decisions | D72, D103, D105, D106 |

Sketch (Y grows downward, floor top y 320, block top y 208, shelf top y 96; walls x 8000-8016 and 8624-8640):

```
x    8000 8032  8112   8176  8240  8288 8336 8384-8416 8432        8512         8640
     |wall|start  :              [shelf 8288-8384, y 96]   L                    |wall|
     |    |       :             [ block 8288-8432, y 208 ]|<- face             |    |
     |    |       :   s2 (240)  [     n                    ]|                   |    |
     |    | @     o  s1 (272)   [                          ]|  ~~~~S~~~~        |    |
=====+=========================+==========================+===================+=====  floor y 320
```

- `FallingStone` (id 172) column x 8112 (zone x 8096-8144, y 288-320), over the approach before the first step.
- `Snail` (id 173) on the block top x 8336, y 192, `offNeg` 0, `offPos` 1 (range 8336-8352), left of the lift: its left line runs
  off the block's left edge and its gob falls onto the floor at x 8088-8104, past the entrance body; its right line lands at x
  8606-8620.
- `Spider` (id 149) polyline from (8512, 320): `0,0 -80,0 -80,-112 -160,-112`: the floor right of the block (x 8432-8512), up its
  right face (x 8432, 112 px, the block on its left) and along its top (x 8352-8432). Its pounce on the top reaches x 8304 at
  most, so the block's left 16 px are out of its reach; a hero there is out of its hearing too, and it waits on the face.
- `Escalator` (id 167) x 8384-8416, vertical, running, its top between y 96 and 160 (its underside at most y 176). Block (id 157)
  x 8288-8432, y 208-320; shelf (id 166) x 8288-8384, y 96-112. One-way `Platform` steps (ids 158, 159) x 8176 y 272 and x 8240 y 240.
- `RoomStart` (id 164) (8032, 272); `RoomExit` (id 165) x 8304-8336, y 64-96 on the shelf.

### forest-1-route

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 8 of 11 (832 px) |
| Role | variation |
| Purpose | A snail under the ladder slab fires along the entrance floor: jump its gob and finish it; then one lever, two supports in opposite states: choose the optional ladder or the mandatory lift, and pay a one-shot step for a refill; a spider on the ladder slab drops on its silk thread at anyone who stops under the slab's end; the bat guards the lift. |
| Introduces | bat-enemy |
| Develops | crumble-support: a one-shot step whose loss closes the optional refill until Restart Level; fireball-refill: the bat gives a reason to spend a charge, so the refill is worth something; spider: it drops from above on its thread at a hero under the slab's lip, so stepping back along the floor is no longer the answer: never stop under a lip, or step aside and strike it as it hangs |
| Combines | ladder + running-platform: one lever lowers the ladder and stops the lift, so the two routes exclude each other (first taught by forest-1-lever, D86, so here it is a choice) |
| Repeats | lever, snail |
| Mastered route | 11.02 s required: the snail first (its gob jumped, then the sword), then the drop, the bat and the lift; 18.45 s with the optional refill (the lever pulled in passing, the snail finished while the ladder comes down) (`Forest1RouteTest`); the walker sets off the spider's drop and is past the column when it falls, and the detour walks across its slab without stopping; target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | up to about 11 s (a death at the lift replays the room); a hero who walks straight in is hit by the gob 1.85 s after the entrance; one who stops under the slab's lip is bitten 3.40 s in (`Forest1ShelfSpiderTest`); one who answers the snail and walks into the bat is hit 5.43 s in; one who climbs to the slab on the optional detour and stops in its middle 5.77 s in (`Forest1RouteTest`) |
| Beats | snail@1576, spider@1632, bat-enemy@1856, running-platform@1936 |
| Beat time | 11.02 (`Forest1RouteTest`) |
| First meeting | bat-enemy: beat 3, retry 5.43 s, pocket at x 1760, past the known spider's drop column and 96 px short of the bat's patrol, where it is seen flying, with the snail behind it unable to fire to its right (the stump) (`FirstMeetingPocketTest.forest1RouteTheBat`) |
| Optional | the fireball refill on the pad over the one-shot step |
| D26 | every state reached by supported actions reaches the exit without Restart Level (D67); the snail and its stump never block the lift, the ladder's foot or the exit: the stump is a 16 px hop, and the snail, finished or passed, never fires to the right of it; the spider never blocks the lift or the exit: its slab is on the optional ladder route only, its thread lands 298 px short of the lift, and a retry rebuilds it calm on its slab. The detour meets it (`Forest1RouteTest`'s teleporting pad checks remove the spider, the bat and the snail, as they are about geometry, #497): it holds at the slab's end while the hero climbs (its leap may leave the slab's range only over authored floor, and a ladder's top step is not floor to it, so its bite cannot reach the ladder's top and it does not wind up, #497), and a hero who walks on along the slab makes it wind up and is past when it lands (asserted: at least one wind-up, never a bite); the drop off the pad's left end then stomps it, with and without the assist (it was left on its slab without it), at every skitter phase (`Forest1RouteTest`) |
| Hints | none (no hint for a monster, D100) |
| Status | D72 audit 2026-11-16: keep. Since #418 (D86) the two-mechanism lever is taught in forest-1-lever first, so this room uses it as a choice, not as a new rule. [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484) (cards approved by the author 2026-11-19): the silk drop's first room. `Spider` (id 150), a polyline from (1632, 320): `0,0 0,-112 -80,-112`, a 112 px thread from the slab's right lip (it hangs at x 1638, drop box x 1635-1643) and the slab (x 1552-1632, top y 208); it starts at the lip. The drop is on the mandatory floor 218 px before the bat's beat; a walker sets it off and is past the column when it falls, so the first drop is seen and heard missing behind him, at every skitter phase with and without the assist (`Forest1ShelfSpiderTest`). Reworked under D105 by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) with the snail (#329) (card approved by the author 2026-11-19): `Snail` (id 174) x 1576, y 304, no patrol (it holds still facing the entrance), under the slab right of the lowered ladder's foot; its line ends on the floor at x 1388, clear of the entrance body; a solid 16 x 16 stump (id 175, x 1600-1616, y 304-320) ends its line and its sight to the right, so it never fires at the drop column, the bat's pocket or a hero who passed it, and the drop column keeps floor 16 px past it on each side (x 1619-1659, D103). The snail is met and answered before the drop, which is still seen alone. The bat stays the room's only new element. Built and tested with a scripted hero |
| Decisions | D67, D86, D103, D105, D106 |

### forest-1-guard

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 9 of 11 (768 px) |
| Role | combination |
| Purpose | A guard that cannot be jumped, under a spore cloud: time the sword, pay a charge, or take the roof route; then a beetle on the open floor past the tunnel: shoot it, lure it to commit and step back, or run past it. |
| Introduces | beetle |
| Develops | redmob: a guard in a 32 px tunnel that cannot be jumped or stomped, so it must be beaten or avoided by the roof |
| Combines | redmob + puffball: the cloud covers every place the sword reaches from, so the melee needs the calm; ladder + fireball-refill: the lever-lowered ladder leads to a roof route that pays one charge |
| Repeats | lever |
| Mastered route | 6.83 s timed melee, 6.15 s with a fireball, 7.67 s by the roof, all unchanged by the beetle: the floor routes run past it and it bursts behind the hero, and the roof run drops past it without triggering it and reaches the exit alive (`Forest1CombatTest`); from the tunnel's mouth to the exit, 3.65 s killing the beetle with a fireball, 4.53 s luring it (step out, step back into the tunnel, let it burst); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)); a straight run from the entrance dies once by design (D68) |
| Retry cost | about 1.7 s for the first walk-in death (the cloud); 6.08 s for a death in the beetle's burst: the mastered melee from the entrance (x 2176), then a stop beside the beetle when it commits (`Forest1CombatTest`) |
| Beats | puffball@2375, redmob@2394, beetle@2632 |
| Beat time | 6.83 (`Forest1CombatTest`) |
| First meeting | beetle: beat 3, retry 6.08 s (`Forest1CombatTest`), in view from the entrance and met past the tunnel, 158 px from the guard, but the retry is over 6 s, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Optional | the roof route and its refill |
| D26 | the floor route needs no support; the roof is optional (D68); the beetle never blocks the exit: its blast (x 2572-2716 at most) reaches neither the tunnel (it ends at x 2560), nor the roof's drop (x 2704 on; its trigger ends at x 2696), nor the exit (x 2800), and a hero with no charge passes it by luring or by running on |
| Hints | none (no hint for the beetle: new monsters are met in play, D100) |
| Landmark | the beetle crawling under the roof's end, in view from the entrance |
| Status | D72 audit 2026-11-16: keep. [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's first room. It is the only unknown on screen (the guard and the puffball are known) and is met after the tunnel, apart from the guard and the cloud; it is in view, crawling, from the entrance and hears the hero from inside the tunnel, which is a shelter its burst never reaches. Built and tested with a scripted hero; the author played the room on 2026-11-18 and approved the beetle's look and sounds ("all good", #327) |
| Decisions | D68, D100, D101 |

### forest-1-hive

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 10 of 11 (640 px, one screen, x 6240-6880: appended at the map's end, listed after `forest-1-guard` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A hive drops a curtain of stingers on a clock: pass under the first in its calm on open ground, then climb a ladder the second hive's curtain runs down, starting right after a curtain. |
| Introduces | hanging-hive |
| Develops | - |
| Combines | hanging-hive + ladder: the second hive's band covers the whole ladder to the exit ledge, so the climb must start right after a curtain and fit its window |
| Repeats | ladder, carry-throw |
| Mastered route | 7.12 s from a fresh start; 6.85 to 12.22 s over start offsets of 0 to 9 s (0.1 s steps), with and without the longer-warnings assist, for a hero who waits outside each band while it is in the air or about to be and starts the climb right after a curtain (`Forest1HiveTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 7.5 s at most: a hero who walks and climbs without waiting dies in every one of 45 start offsets, 12 times under the floor hive and 33 times under the ladder hive, 3.1 to 7.5 s after the entrance, and replays the room from its entrance (`Forest1HiveTest`) |
| Beats | hanging-hive@6440, hanging-hive@6604 |
| Beat time | 7.12 (`Forest1HiveTest`) |
| First meeting | hanging-hive: beat 1, pocket at x 6420, just short of the floor hive's band, where its curtain is seen falling, 140 px from the ladder hive (`FirstMeetingPocketTest.forest1HiveTheFloorHive`) |
| Below density | 2 beats in 7.12 s (3.56 s per beat), one element (two hives), built before D105; reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Optional | knock the floor hive down: with the stone by the entrance by a jump-throw (28 of 28 tested throw points, body right edge x 6330 to 6438), or with a fireball near the top of a jump (10 of 13 tested jump timings, one charge); after that the floor pass is free. The ladder hive hangs out of reach of both |
| D26 | no lever, support or one-shot state on the route: both hives are passed by timing alone, from every start offset (`Forest1HiveTest`); a knock-down only removes a danger, and a retry rebuilds both hives |
| Hints | none (forest-1 has none, D33) |
| Landmark | the ladder with the hive hanging over it, its curtain running down the rungs |
| Status | new, [#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365) (card, art, sound and placement approved by the author 2026-11-17). D84 check: the room is one screen, so both hives are in view and raining from the entrance; the floor hive is met first, alone, over open floor (the ladder is known); the danger falls where it is drawn; the waiting places (beside each band, the ladder's foot) are obvious; the ladder hive's sounds play only while it is in view. Built and tested with a scripted hero; played by the author 2026-11-17, approved |
| Decisions | D90, D72 |

Sketch (Y grows downward, floor top y 320, ledge top y 208; walls x 6240-6256 and 6864-6880):

```
x    6240 6272  6336        6424 6440-6464         6588 6604-6628 6624            6832  6864 6880
     |wall|start  s          T   [hive A]           T   [hive B]   [ ledge 6624-6864, y 208 ]E |wall|
     |    | @     o          T     ||| band 6448-56 T     |||L     |                          |    |
=====+============================================================+===========================+====  floor y 320
```

- `HangingHive` (id 135) x 6440, y 232 (mouth y 258, band x 6448-6456, 62 px to the floor), period 4 s, phase 0; its bough
  meets a `Decor:trunk` (id 139) at x 6424. Its body (y 239-257) is reached by a jump-throw or a jump-fire.
- `HangingHive` (id 136) x 6604, y 96 (mouth y 122, band x 6612-6620, 198 px down the ladder to the floor), period 4.5 s,
  phase 2 (out of step with the first); its bough meets a `Decor:trunk` (id 140) at x 6588.
- Static `Ladder` (id 137) x 6608-6624, y 208-320, no lever, against the ledge (id 144, x 6624-6864, y 208-320).
- `Stone` (id 138) x 6336, y 308. `RoomStart` (id 133) (6272, 272); `RoomExit` (id 134) x 6832-6864, y 160-208, on the ledge.

### forest-1-tree

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 11 of 11 (640 px, one screen) |
| Role | variation |
| Purpose | Climb a tree by jumping up through its branches; a grey branch gives way, so keep climbing and never stop on it. |
| Introduces | branch-climb, coin |
| Develops | crumble-support: on a climb a broken branch costs height, not life, and the escape is upward (the next branch), not across, and the rotten column falls behind the climber, so a stop rides him down to the floor; beetle: it crawls at the sound tree's foot, not the rotten column's, so the sure climb costs a lure or a charge while the fast one stays free, and one branch up the hero is above its hearing band and it loses him |
| Combines | - |
| Mastered route | 4.95 s by the rotten column (the beetle never commits), 6.23 s by the sound tree (`TreeClimbTest`), both unchanged by the beetle; target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | one possible death, standing in the beetle's burst: a hero who walks to the sound tree and stops beside it when it commits dies 5.12 s after the entrance (`TreeClimbTest`); the rotten branches never kill: the most expensive mistake (stopping on the top rotten branch, riding it down, then the sound tree) finishes in 11.05 s, 6.10 s more than the fast line |
| Beats | branch-climb@3184 |
| Beat time | 4.95 (`TreeClimbTest`) |
| First meeting | branch-climb: beat 1 (`TreeClimbTest`), the rotten branches never kill, but they are the climb's own column and the beetle's patrol (x 3264-3384) is 48 px from it, under the 96 px clearance, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Below density | 1 beat in 4.95 s (4.95 s per beat): the climb alone on the fast line, built before D105; reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| D26 | the sound tree reaches the crown with any rotten branch gone (load check `FallingPlatform.branchProblem`, swept over all 32 states in `TreeClimbTest`); the beetle's blast (x 3236-3412 at most, y 288-320) never reaches the rotten column (x 3184-3216) nor any branch, and a hero on the lowest branch is out of its hearing |
| Optional | one coin on the floor at x 3040 (id 145), 128 px from the entrance and left of the first trunk, off the climb: the level's first coin, a pure first sighting (forest-1 has no healer, so it feeds no recipe; the healer counts only the forest's own coins) |
| Hints | none |
| Landmark | the two trees, with the level's end on the sound tree's crown |
| Status | [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417) (D96): the first coin was added here, so `forest-entry` meets coins as a repeat. [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's second room, met after `forest-1-guard` and `forest-1-hive`; it is known here, so the branch climb and the coin stay the only unknowns; the author played the room on 2026-11-18 and approved the beetle's look and sounds ("all good") |
| Decisions | D80, D96, D100 |

## forest-2 (play order 3)

### forest-entry

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 1 of 5 (x 0-736, 736 px, 1.15 screens) |
| Role | variation |
| Purpose | New ground and a new enemy before any climbing: stomp a YellowMob, cross two plain pits (a fall is a retry), pass the Bats, jump the Bomb, and see the coins. The only new thing is the YellowMob. |
| Introduces | yellowmob |
| Develops | touch-explosive: the Bomb (a reskin of the stump) stands 48 px past the second pit (x 560-608), so the hero lands from that jump straight into the Bomb's range, and backing away from its wind-up leaves only a 24 px pocket (x 608-632) at the pit's edge, between the pit and the blast |
| Combines | - |
| Repeats | bat-enemy, coin |
| Mastered route | 6.80 s required, 10.40 s all coins and 6.12 s on a collection return (`RoomReplayTest`, three identical attempts), re-measured with the two pits of [#486](https://github.com/jlogicgames/edgard_in_kimeria/issues/486) with the same result as with the sand; target about 20 s, coins about 30 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, about 7 s (the required route, 6.80 s, re-measured with the pits), for a death at the Bomb at its end or a fall into a pit (was about 16 s for the undivided room) |
| Beats | yellowmob@192, bat-enemy@272, bat-enemy@512, touch-explosive@656 |
| Beat time | 6.80 (`RoomReplayTest`) |
| First meeting | yellowmob: beat 1 (`RoomReplayTest`), its patrol (x 128-256) passes 16 px from the first Bat's column (x 272), under the 96 px clearance, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Optional | three coins (x 32, 336, 368; the first coin was met in `forest-1-tree`, #417); the Bat ingredient that stood on the ledge at x 416-480 moved to `forest-walls` (#417) |
| D26 | no one-shot support; the Bomb is the only one-shot object and the exit stays reachable after it blows; a fall into a pit (the level's Deadline, y 384) is a retry into the room, never a soft-lock; the required route crosses the pits by the stair and by a 48 px jump (`RoomReplayTest`, which fails if the route falls) |
| Hints | none (the D24 controls zone is removed by #376: forest-0 teaches the controls) |
| Landmark | the stair of three platforms (x 240-400) over the first pit, climbing to the coin at its top |
| Status | D72 audit 2026-11-16: change, done by [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376): the practice stone, carryable torch, practice light and ladder (objects 116-119, 123, 124) are removed and the room is split at x 736 |
| Decisions | D72, D96 |

Quicksand leaves the Forest ([#486](https://github.com/jlogicgames/edgard_in_kimeria/issues/486)). Each former pool becomes a
plain pit, as in `forest-0`: the floor block stops and restarts (x 0-240, x 320-560, x 608-736) and the level's `Deadline`
(id 125, y 384) kills a fall. Pit 1 is x 240-320 (80 px) under the stair; pit 2 is x 560-608 (48 px) under the end of the
second Bat's floor-level patrol. A hero's run jump covers about 86 px, so pit 1 leaves 17 px of margin and pit 2 49 px
(the hitbox is 11 px wide). The first YellowMob chases only while the hero's centre is within x 128-208, so its hitbox ends
below x 225, 15 px short of pit 1's edge (x 240); its patrol is unchanged. The Bat patrols, the Bomb, the YellowMob and the
coins keep their places. No new element: a pit is plain `Collisions` plus the `Deadline`, both outside the registry.

Split detail ([#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)). The free floor between the
Bomb's blast (x 632-696) and the second YellowMob (x 832, left patrol end x 752, hitbox from x 758) is 56 px, too
narrow for this room's exit and right wall plus the next room's left wall and a 48 px safe entrance. The split is at
**x 736**: `forest-entry` ends with its exit at x 704-720 (8 px clear of the blast) and a wall at x 720-736;
`forest-walls` starts with a wall at x 736-752 and its entrance at x 752. To keep that entrance safe, the second
YellowMob's left patrol is shortened from `offNeg` 5 to 1 (left end x 816, hitbox from x 822, 41 px from the hero's
body); its right end and the climb are unchanged. The floor block x 608-1280 is cut at x 736 so each room owns its
own floor.

### forest-walls

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 2 of 5 (x 736-1280, 544 px, 0.85 screen) |
| Role | challenge |
| Purpose | Climb two tall walls by wall jumps, starting where a YellowMob walks, take the Bat ingredient lying on the second wall's top, then pass a RedMob to the exit. |
| Introduces | wall-jump, ingredient |
| Develops | - |
| Combines | yellowmob + wall-jump: the YellowMob patrols up to the foot of the first wall, so the climb starts only after it is stomped or timed past |
| Repeats | yellowmob, redmob |
| Mastered route | 8.80 s required, 7.32 s on a collection return with its mobs already beaten, Bat included: the Bat lies on the walking route, so it costs no time (`RoomReplayTest`, three identical attempts); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, about 9 s, for a death at the RedMob by the exit |
| Beats | yellowmob@832, wall-jump@912, redmob@1136 |
| Beat time | 8.80 (`RoomReplayTest`) |
| First meeting | wall-jump: beat 2 (`RoomReplayTest`), the first wall (x 912) stands inside the YellowMob's patrol (x 816-944), so it is met combined, not alone, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Optional | the Bat ingredient (id 94, x 992, y 224 in the map: the SpawnPoints layer is drawn 16 px up, so the item's bottom edge is the wall's top edge y 224) on top of the second wall (`RoomReplayTest.theBatRestsOnTopOfTheSecondWall`), so a hero who walks on after the climb passes through it; moved here from `forest-entry` by #417 |
| D26 | no one-shot object; both walls are climbable from every state |
| Hints | none |
| Landmark | the two tall walls (x 912 and x 992) |
| Status | new room from the D72 split of `forest-entry`, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376); took the Bat ingredient from `forest-entry`, [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417) |
| Decisions | D72, D96 |

### forest-fissure

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 3 of 5 (640 px, one screen, x 3008-3648: appended at the map's end, listed after `forest-walls` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A floor crack jets fire on a clock: watch the first one alone on open floor and pass between jets, then climb through the branch the second one pierces, starting right after a jet; on the ledge a beetle guards the exit: shoot it from the lip, lure it to commit and step back, or walk on past it. |
| Introduces | ember-fissure |
| Develops | - |
| Combines | - |
| Repeats | beetle |
| Mastered route | 9.30 s from the entrance by the lure, the answer that needs no charge (`RoomReplayTest`, three identical attempts; `ForestFissureTest`), 8.60 s with one fireball from the ledge's lip, 8.25 s on every collection return (the beetle stays gone; `RoomReplayTest`); 6.40 to 10.27 s over start offsets of 0 to 3.5 s (0.05 s steps), with and without the longer-warnings assist, for a hero who waits short of each column until a jet ends and lures the beetle (`ForestFissureTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | the most expensive death is a stop beside the beetle when it commits, after the mastered climb: 8.35 s from the entrance at start offset 0, and 8.75 s at worst over start offsets of 0 to 3.5 s counted from the end of the wait (the old card's basis and that of the offset times above; 11.70 s from entering with the wait included) (`ForestFissureTest`); a careless walker dies at the floor fissure at 23 of 71 start offsets, at most 3.5 s after entering |
| Beats | ember-fissure@3232, ember-fissure@3360, beetle@3508 |
| Beat time | 9.30 (`RoomReplayTest`) |
| First meeting | ember-fissure: beat 1, pocket at x 3200, just short of the first column on open floor, where it is seen jetting, 112 px from the second (`FirstMeetingPocketTest.forestFissureTheFirstFissure`) |
| D26 | no lever, support or one-shot state on the route: both fissures are passed by timing alone from every start offset (`ForestFissureTest`), and the clock restarts with the room. The beetle never blocks the exit: its blast (x 3480-3592 at most, y 192-224) reaches neither the ledge's lip (x 3424-3480), nor the branch below it (a hero on it has his top at y 250), nor the exit (x 3600); it needs no charge: the lure, or walking straight past it, which reaches the exit at every crawl phase with and without the assist (it bursts behind him). A retry rebuilds it at its spawn |
| Hints | none (forest-2 has none, D33; no hint for the beetle: new monsters are met in play, D100) |
| Landmark | the column of fire through the branch, seen across the screen from the entrance |
| Status | new, [#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366) (card, art, sound and placement approved by the author 2026-11-17). D84 check: one screen, so both fissures are in view and jetting from the entrance; the first is met alone on open floor; the danger is lethal exactly where drawn; the waiting places (either side of each column, the floor under the branch) are obvious; the sounds play only while a fissure is in view. The author played the room and heard `fissure_rumble` and `ember_jet` in play on 2026-11-18 ([#463](https://github.com/jlogicgames/edgard_in_kimeria/issues/463)). [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's third room and forest-2's only one. It crawls the exit ledge, in view from the entrance, and hears the hero only once he is on the ledge (never on the floor or the branch). The approved card counted it as a develop ("walking into it kills: the exit is 1 s away"); measured, a hero who keeps walking always clears the blast (80 px in the 0.8 s wind-up, its whole width), as in forest-1, so the card records it as a repeat. Built and tested with a scripted hero |
| Decisions | D91, D72, D100, D101 |

Sketch (Y grows downward, floor top y 320, ledge top y 224; walls x 3008-3024 and 3632-3648):

```
x    3008 3040        3232-48           3328    3360-76 3392  3424      3508-3564          3600-3632 3648
     |wall|start        A              [ branch 3328-3392 ]   [ ledge 3424-3648, y 224 ]  b  >>  E |wall|
     |    | @           |||                      |||B         |                               |
=====+===================================================================================+========  floor y 320
```

- `EmberFissure` A (id 151) x 3232, y 332 in the map (the SpawnPoints layer is drawn 16 px up: top y 316, 4 px above the
  floor line), period 3.5 s, jet 1.0 s, height 96, phase 0.
- One-way `Collisions:Platform` branch (ids 155, 156) x 3328-3392, y 272; `EmberFissure` B (id 152) x 3360, y 332, phase
  1.75 (half a period out of step with A): its column passes through the branch (embers pass one-way platforms).
- Ledge (id 157) x 3424-3648, y 224-320, tiled with the ground-edge tiles; `RoomStart` (id 149) (3040, 272); `RoomExit`
  (id 150) x 3600-3632, y 176-224, on the ledge against the wall (moved 16 px right by #327, from x 3584).
- `Beetle` (id 159) map x 3508, map y 224 (top y 208, floor line 224, on the ledge), `offNeg` 0, `offPos` 2: its centre crawls
  x 3520-3552, its blast covers at most x 3480-3592, y 192-224. The lip x 3424-3480 is outside every blast and its trigger
  (from x 3501), so a hero there is heard and waited for, never reached.

### forest-canopy

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 4 of 5 |
| Role | approach |
| Purpose | The forest's trap families one at a time before the boss. |
| Introduces | thorn-vine, false-honey-fungus |
| Develops | touch-explosive: the exploding root, with fireballs now in hand, makes the from-afar choice cost ammunition; crumble-support: the falling platform holds an optional ascent that a bad landing spends; honey-fungus: its lethal look-alike twin |
| Combines | - |
| Repeats | redmob, bat-enemy, falling-stone, coin, ingredient |
| Mastered route | 9.48 s (`Forest2TrapsTest`, `RoomReplayTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | about 9.5 s for a death at the falling stone |
| Beats | redmob@1472, false-honey-fungus@1672, bat-enemy@1760, thorn-vine@1896, touch-explosive@2064, falling-stone@2224 |
| Beat time | 9.48 (`RoomReplayTest`) |
| First meeting | false-honey-fungus: beat 2 (`RoomReplayTest`), 64 px from the Bat's column (x 1760), under the 96 px clearance, tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500); thorn-vine: beat 4, its retry not measured, built before D105 and tracked by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) |
| Optional | coins and the Mushroom on the ascent over the falling platform |
| D26 | the spent platform closes only the optional ascent; the exit stays reachable |
| Hints | none |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D59, D63 |

### forest-boss

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 5 of 5 |
| Role | boss |
| Purpose | The Forest boss. |
| Introduces | boss |
| Develops | - |
| Combines | - |
| Mastered route | not measured: no fight time has been recorded (BOSS.md); timing three mastered fights is a deferred release check (PRE_RELEASE_TODO.md, "Boss combat, teaching and replay time"), [#384](https://github.com/jlogicgames/edgard_in_kimeria/issues/384) |
| First learning | not measured (#135) |
| Retry cost | the fight restarts at the chamber entrance |
| Status | D72 audit 2026-11-16: keep |
| Decisions | BOSS.md |

