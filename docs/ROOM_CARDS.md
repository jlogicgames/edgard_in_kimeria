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
exempt roles has fewer than 3 beats, fewer than 2 distinct elements or more than 4.0 s per beat (no shipped room is
exempt: #500 reworked the rooms that were below D105), has
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
| Below density | none: no shipped room is exempt since #500 (the gate rejects the field) | the measured beats, time and pace, and the issue that reworks the room (D105) |
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
Skills are what the geometry asks for; their first sighting is declared on the cards only. Their `Geometry:` keys are not Tiled types: `RoomCardsTest` derives them for D105 beats only, `Geometry:branch` from any object with `skin=branch` (the `Geometry:wall` key went with the wall jump, D121).
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
| lever | `Trigger` | operates a lever and reads its linked effect (a lift in `forest-0-lever`, D127) | mechanism |
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
| touch-explosive | `ExplosiveStump`, `ExplosiveRoot`, `Bomb` | jumps it, walks clear of the wind-up, or sets it off from afar; no Forest room places the `Bomb` since D128 (it is kept for the Fortress, [#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116)) | hazard |
| leg-hold | `LegHold` | stands short of open jaws, lets them snap empty and walks over them shut within their window, or jumps them (D128) | hazard |
| puffball | `Puffball` | crosses a rhythmic spore cloud in its calm | hazard |
| thorn-vine | `ThornVine` | passes a swinging vine by watching its rhythm | hazard |
| bramble | `Bramble` | waits out a thorn arm that grows across a lane on a clock and passes in its open window (D89) | hazard |
| hanging-hive | `HangingHive` | passes under, or climbs through, a falling curtain of stingers in its calm; may knock the hive down | hazard |
| clocked-column | `LanceRoot`, `EmberFissure` | waits beside a burl on the floor for its lance of root to sink and passes in the calm (D91, D129); the `EmberFissure` (a fire jet) is a reskin kept for the Swamp ([#105](https://github.com/jlogicgames/edgard_in_kimeria/issues/105), [#399](https://github.com/jlogicgames/edgard_in_kimeria/issues/399)) | hazard |
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
| wolf | `Wolf` | jumps a wolf's low lunge, timed on its drawn line; strikes it as it comes or in its recovery; goes up out of its reach and watches it bay; or shoots it (D115); it cannot be outrun without the longer-warnings assist | monster |
| boss | `Boss` | fights the stage boss | monster |
| fireball-refill | `FireballRefill` | picks up a ranged-attack charge | pickup |
| coin | `Collectable:Coin` | collects coins, often off the required route | pickup |
| ingredient | `Collectable:Bat`, `Collectable:Mushroom` | collects a healer ingredient | pickup |
| branch-climb | `Geometry:branch` (skill) | climbs a tree by jumping up through one-way branches (`Collisions:Platform` with `skin=branch`, D80) | skill |

Not elements (scenery, markers, plain geometry): `Decor:bush`, `Decor:lookalike`, `Decor:trunk`, `Decor:snag` (D122), `Decor:crown`, `HangingVine`, `Torch`
(decorative), `Checkpoint`, `Player`, `Deadline`, `Collisions:Wall` (solid room walls, never grabbed since D121), `Collisions:Platform`
(one-way platforms), plain `Collisions`, the `Rooms` markers and `Hints` (a `Hint`'s `coach` property, D139, arms a step of the touch tutorial; it is not an element and needs no registry row).

## forest-0 (tutorial, play order 1)

### forest-0-lever

| Field | Value |
| --- | --- |
| Level | forest-0, play order 1, room 1 of 6 |
| Role | lesson |
| Purpose | Cause and effect, safely: a lever starts and stops a lift beside it, which hangs above the walking line and never bars the exit (D127). |
| Introduces | lever, lever-platform, honey-fungus |
| Develops | - |
| Combines | - |
| Mastered route | about 4.3 s (`LeverLessonTest`); target about 20 s (D19) |
| First learning | not measured (#135) |
| Retry cost | none: the room has no hazard |
| D26 | the exit is reachable with the lift stopped, running or stopped mid-air, or the lever never touched (D34, D127) |
| Hints | walk (`coach` stick-drag, touch text `HINT_TUTORIAL_WALK_TOUCH`, D139), lever, exit, live controls label (`coach` stick-up, D139) |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D33, D34, D63, D127 |

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
| Introduces | falling-stone |
| Develops | lever-platform: met as the lift of `forest-0-lever` (D127), now the only way up to the exit ledge |
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
| Hints | throw, stump, exit, across, live controls label (`coach` ranged, D139) |
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
| Level | forest-1, play order 2, room 1 of 12 (800 px, x 3520-4320: placed after the tree room on the map, first in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | The first enemy: it sees you and charges, but a fallen log holds it; step up on the log and read its raised arm, then sword or stomp it (or run on past it); a loose branch and an exploding stump on the way to the refill, which gives a spent charge back. |
| Introduces | redmob, fireball-refill |
| Develops | - |
| Combines | - |
| Repeats | falling-branch, touch-explosive |
| Mastered route | 6.75 s, zero charges: the mob charges into the log, the hero jumps onto the log and swords it, walks on under the branch, jumps the stump, takes the refill (`RoomReplayTest`, `Forest1MobTest`); the same 6.75 s for a hero who never swings (never hit, `Forest1MobTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 6.17 s at worst (standing beside the stump when it blows, `Forest1MobTest`); 2.5 to 2.7 s for a hero who stops on the log inside the whip's reach |
| Beats | redmob@3776, falling-branch@3984, touch-explosive@4112 |
| Beat time | 6.75 (`RoomReplayTest`) |
| First meeting | redmob: beat 1, pocket at x 3716, on the floor behind the log inside its range (x 3712-3872), where it notices him, runs at him and is stopped by the log (x 3728-3760), its whip short of him, 112 px from the branch column (`FirstMeetingPocketTest.forest1MobTheRedMob`, acting `RUN`) |
| D26 | no lever, support or one-shot state; the log is plain geometry the mob cannot climb, so the hero can always step back behind it; the sword or a stomp needs no charge, and the stump is jumped or outrun |
| Hints | none (forest-1 has none, D33) |
| Status | new, [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375) (cards approved by the author 2026-11-17); `Forest1MobTest` checks the mob and the refill are met one at a time. Role `variation`, not `lesson`: lesson rooms follow the tutorial rules D33-D40 and D48 (hint zones, one lesson element plus at most one trap), and this room has two elements, neither a trap, and no hints (forest-1 has none, D33); it is held to the two-introduction cap instead, which it meets |
| Decisions | D85, D72, D105 |

Sketch (Y grows downward, floor top y 304; walls x 3520-3536 and 4304-4320):

```
x    3520 3552      3728   3776  3824            4208   4256    4320
     |wall|start    [ hero-centre range ]           R     exit  |wall|
     |    |  @       .......  M  .......            o      [ ]  |    |
=====+==========================================================+=====  floor y 304
```

Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (2026-11-19): a fallen log, a loose branch and an exploding stump were added, and the mob's range grew one tile to the left.

```
x    3520 3552     3712 3728-3760 3776    3872     3984     4112   4208 4256    4320
     |wall|start    :   [ log ]    M        :        |b       s      R   exit   |wall|
=====+==================================================================+=====  floor y 304
```

- RoomStart (3552, 256). RedMob, the old id 11 moved here, at x 3776, y 272, `offNeg` 4, `offPos` 3 tiles (wakes for a
  hero centre in x 3712-3824, span x 3712-3872).
- New solid log (plain `Collisions`, id 176) x 3728-3760, y 288-304, two tiles wide: the sword reaches 17 px past the body, so
  across a 16 px stump it would kill the mob from cover and its whip would never be read; across 32 px the hero must step onto
  the log, inside the whip's reach. The mob blocked at the log's right face cannot reach the pocket at x 3716.
- New `FallingBranch` (id 177) x 3984, y 192 (zone x 3976-4024, y 192-304), 112 px past the mob's span.
- New `ExplosiveStump` (id 178) x 4112, y 288, jumped or outrun; clear of the refill (x 4208) and the exit (x 4256).
- FireballRefill, the old id 25 moved here (identity kept), at x 4208, y 272 (one tile over the floor), picked up by walking.
  With the camera clamped to the room, it is off screen while the hero stands in the mob's range (screen right edge
  4160 until the hero's centre passes about 3840), so the refill is seen only after the mob.
- RoomExit (4256, 256, 32 x 48).

### forest-1-deadfall

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 2 of 12 (640 px, one screen, x 4960-5600: appended after the lever room on the map, second in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A trap whose trigger is in plain sight: jump the trip vine, or trip it, stop and let the log swing by; then keep walking under a loose stone and beat the RedMob at the exit. |
| Introduces | deadfall |
| Develops | - |
| Combines | - |
| Repeats | falling-stone, redmob |
| Mastered route | 5.85 s jumping the vine (take-offs 2 to 60 px before it, the stone walked under, the mob swung at), 7.15 s tripping it and stopping (7.57 s with the longer-warnings assist) (`DeadfallTrapTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 5.2 s at worst: a hero stopping under the stone is hit 5.18 s after the entrance, one who walks on past the mob without a swing 4.4 s; walking into the swing costs 2.00 s (2.32 s with the assist) (`DeadfallTrapTest`) |
| Beats | deadfall@5184, falling-stone@5392, redmob@5472 |
| Beat time | 5.85 (`DeadfallTrapTest`) |
| First meeting | deadfall: beat 1, pocket at x 5120, on the vine (x 5112-5128): the hero trips it and stands, 54 px short of the swing band (x 5182-5282), and sees the log slip and swing past, never hit (`FirstMeetingPocketTest.forest1DeadfallTheDeadfall`, acting `SWING`); a touch trap whose trigger lies outside its lethal band (D88, D105, ROOMS.md "Density and first meetings") |
| D26 | no support, no one-shot state the route needs: a jump leaves the log tied, a trip leaves it hanging from the snag above head height and the vine cut, and the exit is reachable from both (`DeadfallTrapTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the log hauled up and lashed to the bough by the tree trunk |
| Status | new, [#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363) (card, art and placement approved by the author 2026-11-17). Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (2026-11-19): the deadfall moved 64 px left (box x 5184-5296, trunk x 5292), a falling stone (id 179, x 5392, zone x 5376-5424) and a RedMob (id 180, x 5472, `offNeg` 3, `offPos` 0) before the exit. The trap is in view from the entrance with the exit (D84) |
| Decisions | D88, D72, D105 |

Sketch (Y grows downward, floor top y 304; walls x 4960-4976 and 5584-5600; P is the rope knot, x 5232, y 236; since #500 the
trap is 64 px left of where D88 first placed it, to make room for the stone and the mob):

```
x    4960 4992  5112-5128   5182 --- 5282  5292   5392    5472   5536    5600
     |wall|start  vine       [ swing band ]  trunk   o      M    exit   |wall|
     |    |  @     =      bough====P====log| |      (lip)          [ ]  |    |
=====+==========================================================+=====  floor y 304
```

- RoomStart (4992, 256). Deadfall (the bough, 112 x 24) at (5184, 227), `swing` left: the log is tied on the right
  and swings left, at a hero coming from the entrance. A `Decor:trunk` at x 5292 holds the bough's thick end.
- `FallingStone` (id 179) x 5392, zone x 5376-5424, y 272-304; `RedMob` (id 180) x 5472, `offNeg` 3, `offPos` 0.
- RoomExit (5536, 256, 32 x 48).

### forest-1-escalators

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 3 of 12 (640 px, one screen, x 0-640: the first half of the old trap course, split under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), D110) |
| Role | variation |
| Purpose | Machinery that runs by itself: cross the crumbling platforms, pass the escalators that never stop, and open the wall with its lever. |
| Introduces | running-platform |
| Develops | - |
| Combines | - |
| Repeats | crumble-support, lever, lever-wall |
| Mastered route | 5.48 s to the room exit (`RoomReplayTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | none: nothing in the room kills (the falling platforms drop the hero onto the floor, D69, D107) |
| Beats | crumble-support@160, running-platform@304, lever-wall@480 |
| Beat time | 5.48 (`RoomReplayTest`) |
| First meeting | running-platform: beat 2, pocket at x 240, on the floor between the falling platforms (x 96-224, which drop the hero onto the floor, so D107 does not count them) and the escalator's patrol (x 256-384), the escalator seen running (`FirstMeetingPocketTest.forest1EscalatorsTheEscalator`) |
| D26 | no support the route needs: the platforms drop the hero onto the floor and the step blocks are hops; the wall's lever is a reversible toggle |
| Hints | none (forest-1 has none, D33) |
| Status | split from `forest-1-traps` under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (batch 3, D110, approved by the author 2026-11-20): the room is x 0-640 of the old course, with a seam wall at x 624 and the exit 16 px wide before it; lever 33 and its overhead escalator 34, a repeat with no role (the escalator ran above the hero's body), were removed. |
| Decisions | D58, D67, D72, D85, D105, D110 |

### forest-1-traps

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 4 of 12 (640 px, one screen, x 640-1280: the trap course, split from the escalators under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), D110) |
| Role | variation |
| Purpose | A spore cloud that keeps its own time, read from the entrance and crossed in its calm; then the known RedMob and a log that crumbles over a lethal pit. |
| Introduces | puffball |
| Develops | crumble-support: the log crumbles over a lethal pit, not a recoverable drop |
| Combines | - |
| Repeats | redmob |
| Mastered route | 6.00 s to the room exit with no wait at the puffball (`Forest1TrapsTest`, `RoomReplayTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 6.58 s for a death at the log, standing on it until it drops (`Forest1TrapsTest`; 12.73 s before the split) |
| Beats | puffball@768, redmob@928, crumble-support@1088 |
| Beat time | 6.00 (`RoomReplayTest`) |
| First meeting | puffball: beat 1, pocket at x 744, between the entrance and the cloud (x 766-786), 152 px short of the RedMob's span and far from its wake range (x 896-960), the cloud seen out at the entry and sinking (`FirstMeetingPocketTest.forest1TrapsThePuffball`, acting `CLOUD`) |
| D26 | the pit is the level `Deadline`, a retry; nothing is re-armed (D58) |
| Hints | none |
| Status | D72 audit 2026-11-16: change; done by [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375) (D85). Split under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (batch 3, D110, approved by the author 2026-11-20): the escalators became `forest-1-escalators`; the puffball (x 944 to 768, `phase` 0 to 3.4) and the RedMob 38 (x 864 to 928) swapped order so the puffball is met first from a pocket: its cloud is out at the entry, sinks at 0.6 s, the straight walker crosses its column at 0.8 to 1.1 s in the calm and a hero who waits sees the swell at 2.8 s before the next cloud at 3.6 s (`Forest1TrapsTest`); the thorn tuft 35 left (forest-1 keeps one in `forest-1-spider`). |
| Decisions | D58, D72, D85, D105, D110 |

The course after the split (x 640-1280; each room is exactly one 640 px screen, so the puffball is never in view from the
escalators and the whole course is in view on entering):

```
x  656    768   896-1008    1024-1168       1216
   start  puff  M38 (span)  pit + log 1088  exit
          cloud 766-786                      
```

- Walls x 624-640 (`forest-1-escalators`) and 640-656 (`forest-1-traps`) are drawn and solid; the floor is cut at the seam
  (x 0-640 and 640-1024) so each room owns its floor.
- The 96 px rule: the puffball's box is 112 px from the RedMob's span and 304 px from the log.

### forest-1-lever

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 5 of 12 (640 px, one screen) |
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
| Hints | lever (`HINT_LEVER_BOTH`, #554, D131: one message-only zone over the whole room; the one D33 exception in forest-1) |
| Landmark | the lift beside the lever, the ladder hanging above the gap, the ledge it will reach |
| Status | New (#418, D86, after the D84 playtest). D84 check: (1) taught before gating: the combination is met here, with nothing at stake, before the route room gates progress with it; (2) the effects land on screen: the room is one screen wide (640 px = the camera's view), the lift is 80 px and the ladder 192 px from the lever; (3) a stuck player always sees something untried: the lever, the running lift or the hanging ladder; (4) the challenge is only boarding a moving lift. The room is built and tested with a scripted hero; the author played it on 2026-11-17, and no novice has played it yet (#129-#131). |
| Decisions | D84, D86, D67 |

### forest-1-spider

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 6 of 12 (1120 px, x 6880-8000: appended at the map's end, listed after `forest-1-lever` in the `Rooms` list, which sets the play order; widened from 640 px by #500) |
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
| First meeting | spider: beat 1, retry 2.32 s, pocket at x 7030 (the hero's centre), inside its hearing, where it notices him and chases to its range's end without reaching him, with the snail off screen (the camera ends at x 7520 while the spider is met) (`FirstMeetingPocketTest.forest1SpiderTheSpider`); snail: beat 2, pocket at x 7456 on a one-way step (x 7440-7472, top y 272), inside its sight and above its firing line, where it notices him and watches without spitting (D106), out of the spider's hearing (`FirstMeetingPocketTest.forest1SpiderTheSnail`) |
| D26 | no lever, no support, no one-shot state; the sword or a stomp answers both monsters, the stone and the tuft need no ammunition, and a retry rebuilds everything calm |
| Hints | none (no hint for a monster: new monsters are met in play, D100) |
| Landmark | the snail turning its stalks up at the hero on the log step |
| Status | new, [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484) (cards approved by the author 2026-11-19); reworked under D105 by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) with the snail (#329) (card approved by the author 2026-11-19): the room grew from 640 to 1120 px so the snail is off screen while the spider is met (one unknown at a time, D72), and `forest-1-climb` moved 480 px right. Built and tested with a scripted hero |
| Decisions | D72, D100, D101, D102, D105, D106 |

Sketch (Y grows downward, floor top y 304; walls x 6880-6896 and 7984-8000; `^` the one-way step x 7440-7472, top y 272):

```
x    6880 6912     7136  7184  7232   7440-7472  7584 7600 7616  7744       7872   7920    8000
     |wall|start   [ spider range ]     ^step     [ snail range ]  stone      tuft   exit  |wall|
     |    | @         <  S  >                       <  n  >        (lip)       *     [ ]   |    |
=====+==================================================================================+=====  floor y 304
```

- `Spider` (id 148) x 7184, y 288, 32 x 16, `offNeg` 3, `offPos` 3: `position.x` 7136-7232, its fang reach x 7089-7303 at most.
- One-way `Collisions:Platform` (id 169) x 7440-7472, y 272-288, drawn as the plain platform.
- `Snail` (id 168) x 7600, y 288, `offNeg` 1, `offPos` 1 (range 7584-7616): its lines end on the floor at x 7396-7428 (left)
  and x 7794-7826 (right), clear of the entrance and the exit; from the step the hero is in its sight and 23 px above its line.
- `FallingStone` (id 170) column x 7744 (zone x 7728-7776, y 272-304); `ThornTuft` (id 171) x 7872, zone 80.
- `RoomStart` (id 161) (6912, 256); `RoomExit` (id 162) x 7920-7952, y 256-304. Floor (id 151) x 6880-8000; walls ids 152, 153.

### forest-1-bramble

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 7 of 12 (640 px, one screen, x 5600-6240: appended at the map's end, listed after `forest-1-spider` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A thorn arm grows over a ladder top on a clock: jump a thorn root, walk under a loose branch, watch the arm from the ladder's foot, wait on the ladder, climb out when it pulls back. |
| Introduces | bramble |
| Develops | ladder: its top is now a place to wait and watch, and leaving it is timed |
| Combines | - |
| Repeats | ladder, proximity-thorn, falling-branch |
| Mastered route | 9.47 s from a fresh start, the same with the longer-warnings assist; 8.13 to 11.52 s over every start offset (0.05 s steps) for a hero who goes when the lane is open or waits on the ladder for the arm to come back (`Forest1BrambleTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 5.42 s: a hero who climbs straight out from a fresh start is caught 5.42 s after the entrance and replays the room from its entrance (`Forest1BrambleTest`; 6.70 s before the entrance moved) |
| Beats | proximity-thorn@5872, falling-branch@6000, bramble@6160 |
| Beat time | 9.47 (`Forest1BrambleTest`) |
| First meeting | bramble: beat 3, retry 5.42 s, pocket at x 6170 at the ladder's foot, where its band never reaches and the arm is seen growing and pulling back, 128 px from the branch column (`FirstMeetingPocketTest.forest1BrambleTheBramble`) |
| D26 | no lever, no support, no one-shot state the route needs: the ladder is always down and the arm comes back every 4 s, so waiting on the ladder always gives another window; the band never reaches the floor, so the walk to the ladder is safe; the root is jumped and the branch falls behind a walker (`Forest1BrambleTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the thorn arm sweeping over the ladder top under the shelf's end |
| Status | Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (2026-11-19): `RoomStart` x 5632 to 5760 (the route comes back left along the shelf, so every added beat lies left of the bramble; the nearer entrance cuts the blind-climb death from 6.70 s to 5.42 s), `Bramble` `phase` 1.28 (a fresh start meets the clock as before), a thorn root (id 181, x 5872, `zone` 64) and a loose branch (id 182, x 6000, y 208, under the shelf). Originally: new, [#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364) (card, art and placement approved by the author 2026-11-17). D84 check: the bramble is the room's only hazard and is in view, cycling, from the entrance; the hero walks the whole floor under it out of reach before the climb, so its rhythm is shown before it gates progress. Built and tested with a scripted hero; played by the author 2026-11-17, approved |
| Decisions | D89, D72, D105 |

Sketch (Y grows downward, floor top y 304, shelf top y 192; walls x 5600-5616 and 6224-6240):

```
x    5600 5760              5904 5920                    6160     6192 6208 6224 6240
     |wall|start             [ shelf 5904-6192, y 192-208 ]  B~~~~~~~~~~~~~~~~|wall|
     |    | @                 E (exit 5920-5952, y 160-192)          | L  |    |    |
=====+=====================================================================+====+====  floor y 304
```

- `Bramble` (id 127) x 6160, y 168, 64 x 24, `root=right`: rooted in the right wall's face at x 6224, the band y 174-190,
  the grown arm over x 6160-6224, the always-lethal clump x 6212-6224.
- Static `Ladder` (id 126) x 6192-6208, y 192-304, no lever: one tile off the wall, so its top is clear of the clump (D89).
- `RoomExit` (id 125) x 5920-5952, y 160-192, on the shelf's left end. `RoomStart` (5760, 256).
- `ThornRoot` (id 181) x 5872, y 280, `zone` 64 (a root's zone is at least 64 px a side): jumped on the walk, 83 px from the entrance body.
  `FallingBranch` (id 182) x 6000, y 208, hanging under the shelf, zone x 5992-6040, y 208-304: it falls behind a walker.

### forest-1-climb

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 8 of 12 (640 px, one screen, x 8000-8640: at the map's end, listed after `forest-1-bramble` in the `Rooms` list, which sets the play order; moved 480 px right when `forest-1-spider` grew, #500) |
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

Sketch (Y grows downward, floor top y 304, block top y 192, shelf top y 80; walls x 8000-8016 and 8624-8640):

```
x    8000 8032  8112   8176  8240  8288 8336 8384-8416 8432        8512         8640
     |wall|start  :              [shelf 8288-8384, y 80]   L                    |wall|
     |    |       :             [ block 8288-8432, y 192 ]|<- face             |    |
     |    |       :   s2 (240)  [     n                    ]|                   |    |
     |    | @     o  s1 (272)   [                          ]|  ~~~~S~~~~        |    |
=====+=========================+==========================+===================+=====  floor y 304
```

- `FallingStone` (id 172) column x 8112 (zone x 8096-8144, y 272-304), over the approach before the first step.
- `Snail` (id 173) on the block top x 8336, y 176, `offNeg` 0, `offPos` 1 (range 8336-8352), left of the lift: its left line runs
  off the block's left edge and its gob falls onto the floor at x 8088-8104, past the entrance body; its right line lands at x
  8606-8620.
- `Spider` (id 149) polyline from (8512, 304): `0,0 -80,0 -80,-112 -160,-112`: the floor right of the block (x 8432-8512), up its
  right face (x 8432, 112 px, the block on its left) and along its top (x 8352-8432). Its pounce on the top reaches x 8304 at
  most, so the block's left 16 px are out of its reach; a hero there is out of its hearing too, and it waits on the face.
- `Escalator` (id 167) x 8384-8416, vertical, running, its top between y 80 and 144 (its underside at most y 160). Block (id 157)
  x 8288-8432, y 192-304; shelf (id 166) x 8288-8384, y 80-112. One-way `Platform` steps (ids 158, 159) x 8176 y 256 and x 8240 y 240.
- `RoomStart` (id 164) (8032, 256); `RoomExit` (id 165) x 8304-8336, y 48-80 on the shelf.

### forest-1-route

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 9 of 12 (832 px) |
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
| Status | D72 audit 2026-11-16: keep. Since #418 (D86) the two-mechanism lever is taught in forest-1-lever first, so this room uses it as a choice, not as a new rule. [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484) (cards approved by the author 2026-11-19): the silk drop's first room. `Spider` (id 150), a polyline from (1632, 304): `0,0 0,-112 -80,-112`, a 112 px thread from the slab's right lip (it hangs at x 1638, drop box x 1635-1643) and the slab (x 1552-1632, top y 192); it starts at the lip. The drop is on the mandatory floor 218 px before the bat's beat; a walker sets it off and is past the column when it falls, so the first drop is seen and heard missing behind him, at every skitter phase with and without the assist (`Forest1ShelfSpiderTest`). Reworked under D105 by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) with the snail (#329) (card approved by the author 2026-11-19): `Snail` (id 174) x 1576, y 288, no patrol (it holds still facing the entrance), under the slab right of the lowered ladder's foot; its line ends on the floor at x 1388, clear of the entrance body; a solid 16 x 16 stump (id 175, x 1600-1616, y 288-304) ends its line and its sight to the right, so it never fires at the drop column, the bat's pocket or a hero who passed it, and the drop column keeps floor 16 px past it on each side (x 1619-1659, D103). The snail is met and answered before the drop, which is still seen alone. The bat stays the room's only new element. Built and tested with a scripted hero |
| Decisions | D67, D86, D103, D105, D106 |

### forest-1-guard

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 10 of 12 (768 px) |
| Role | combination |
| Purpose | A guard that cannot be jumped, under a spore cloud: time the sword, pay a charge, or take the roof route; then a beetle on the open floor past the tunnel: shoot it, lure it to commit and step back, or run past it. |
| Introduces | beetle |
| Develops | redmob: a guard in a 32 px tunnel that cannot be jumped or stomped, so it must be beaten or avoided by the roof |
| Combines | redmob + puffball: the cloud covers every place the sword reaches from, so the melee needs the calm; ladder + fireball-refill: the lever-lowered ladder leads to a roof route that pays one charge |
| Repeats | lever |
| Mastered route | 6.50 s timed melee, 5.82 s with a fireball, 7.33 s by the roof (7.40 s before D126 moved the lever 16 px left), the other two and the roof each 0.3 s shorter than before #500 (the start, the lever and the puffball's clock moved together, D68), none changed by the beetle: the floor routes run past it and it bursts behind the hero, and the roof run drops past it without triggering it and reaches the exit alive (`Forest1CombatTest`); from the tunnel's mouth to the exit, 3.65 s killing the beetle with a fireball, 4.53 s luring it (step out, step back into the tunnel, let it burst); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)); a straight run from the entrance dies once by design (D68) |
| Retry cost | about 1.4 s for the first walk-in death (the cloud); 5.82 s for a death in the beetle's burst: the mastered melee from the entrance (x 2208), then a stop beside the beetle when it commits (`Forest1CombatTest`) |
| Beats | puffball@2375, redmob@2394, beetle@2632 |
| Beat time | 6.50 (`Forest1CombatTest`) |
| First meeting | beetle: beat 3, retry 5.82 s (Forest1CombatTest), pocket at x 2550, in the tunnel's mouth under the roof, where it hears him, notices, chases and waits at its range end facing him, never committing, its blast (x 2572 on) short of the tunnel, 158 px past the guard (`FirstMeetingPocketTest.forest1GuardTheBeetle`, acting `NOTICE`) |
| Optional | the roof route and its refill |
| D26 | the floor route needs no support; the roof is optional (D68); the beetle never blocks the exit: its blast (x 2572-2716 at most) reaches neither the tunnel (it ends at x 2560), nor the roof's drop (x 2704 on; its trigger ends at x 2696), nor the exit (x 2800), and a hero with no charge passes it by luring or by running on |
| Hints | none (no hint for the beetle: new monsters are met in play, D100) |
| Landmark | the beetle crawling under the roof's end, in view from the entrance |
| Status | D72 audit 2026-11-16: keep. [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's first room. It is the only unknown on screen (the guard and the puffball are known) and is met after the tunnel, apart from the guard and the cloud; it is in view, crawling, from the entrance and hears the hero from inside the tunnel, which is a shelter its burst never reaches. Built and tested with a scripted hero; the author played the room on 2026-11-18 and approved the beetle's look and sounds ("all good", #327). Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) batch 3 (D110, 2026-11-20): `RoomStart` 61 x 2176 to 2208, the lever 63 x 2224 to 2256 and the puffball 77 `phase` 2.2 to 2.52, together, so every route is 0.32 s shorter and the beetle's retry 5.82 s, while the first death and the price list of D68 are unchanged; the entrance-to-cloud clearance is asserted 117 px (was 140), D68 revised |
| Decisions | D68, D100, D101 |

### forest-1-hive

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 11 of 12 (640 px, one screen, x 6240-6880: appended at the map's end, listed after `forest-1-guard` in the `Rooms` list, which sets the play order) |
| Role | combination |
| Purpose | Pass under the floor hive in its calm, climb through the ladder hive's curtain, then step off the ladder onto the ledge where a spider guards the exit: make it miss and strike it. |
| Introduces | hanging-hive |
| Develops | - |
| Combines | hanging-hive + ladder: the second hive's band covers the whole ladder to the exit ledge, so the climb must start right after a curtain and fit its window; hanging-hive + spider: the curtain pushes the hero off the ladder onto a strip the spider cannot reach, and only there can he bait it |
| Repeats | ladder, carry-throw, spider |
| Mastered route | 8.85 s from a fresh start (8.58 to 14.22 s over start offsets of 0 to 9 s in 0.1 s steps, with and without the longer-warnings assist) for a hero who waits outside each band while it is in the air or about to be, starts the climb right after a curtain, then baits the spider's pounce, steps back and strikes it in its recovery (`Forest1HiveTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 7.5 s at most: the hives 3.1 to 7.5 s after the entrance (12 deaths under the floor hive, 33 under the ladder hive in 45 start offsets), the spider 6.25 s for a hero who walks on along the ledge without baiting it (`Forest1HiveTest`) |
| Beats | hanging-hive@6440, hanging-hive@6604, spider@6752 |
| Beat time | 8.85 (`Forest1HiveTest`) |
| First meeting | hanging-hive: beat 1, pocket at x 6420, just short of the floor hive's band, where its curtain is seen falling, 140 px from the ladder hive and 316 px from the spider (`FirstMeetingPocketTest.forest1HiveTheFloorHive`) |
| Optional | knock the floor hive down: with the stone by the entrance by a jump-throw (28 of 28 tested throw points, body right edge x 6330 to 6438), or with a fireball near the top of a jump (10 of 13 tested jump timings, one charge); after that the floor pass is free. The ladder hive hangs out of reach of both |
| D26 | no lever, support or one-shot state on the route: both hives are passed by timing alone, from every start offset, and the spider's reach (x 6697-6823) never covers the ladder, the ladder hive's band or the exit; a hero on the strip x 6624-6697 is outside it; the sword answers it with no charge; a knock-down only removes a danger, and a retry rebuilds everything calm (`Forest1HiveTest`) |
| Hints | none (forest-1 has none, D33; no spider hint, D100) |
| Landmark | the ladder with the hive hanging over it, its curtain running down the rungs |
| Status | Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (2026-11-19): a spider (id 183) on the exit ledge, x 6736, y 176, `offNeg` 0, `offPos` 1 (range x 6736-6784, pounce reach x 6697-6823: the load check keeps it 9 px short of the exit, x 6832). Originally: new, [#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365) (card, art, sound and placement approved by the author 2026-11-17). D84 check: the room is one screen, so both hives are in view and raining from the entrance; the floor hive is met first, alone, over open floor (the ladder is known); the danger falls where it is drawn; the waiting places (beside each band, the ladder's foot) are obvious; the ladder hive's sounds play only while it is in view. Built and tested with a scripted hero; played by the author 2026-11-17, approved |
| Decisions | D90, D72, D102, D105 |

Sketch (Y grows downward, floor top y 304, ledge top y 192; walls x 6240-6256 and 6864-6880):

```
x    6240 6272  6336        6424 6440-6464         6588 6604-6628 6624            6832  6864 6880
     |wall|start  s          T   [hive A]           T   [hive B]   [ ledge 6624-6864, y 192 ]E |wall|
     |    | @     o          T     ||| band 6448-56 T     |||L     |                          |    |
=====+============================================================+===========================+====  floor y 304
```

- `HangingHive` (id 135) x 6440, y 216 (mouth y 242, band x 6448-6456, 62 px to the floor), period 4 s, phase 0; its bough
  meets a `Decor:trunk` (id 139) at x 6424. Its body (y 223-241) is reached by a jump-throw or a jump-fire.
- `HangingHive` (id 136) x 6604, y 96 (mouth y 122, band x 6612-6620, 182 px down the ladder to the floor), period 4.5 s,
  phase 2 (out of step with the first); its bough meets a `Decor:trunk` (id 140) at x 6588.
- Static `Ladder` (id 137) x 6608-6624, y 192-304, no lever, against the ledge (id 144, x 6624-6864, y 192-304).
- `Stone` (id 138) x 6336, y 292. `RoomStart` (id 133) (6272, 256); `RoomExit` (id 134) x 6832-6864, y 144-192, on the ledge.
- `Spider` (id 183) x 6736, y 176, 32 x 16, on the ledge (floor top y 192), `offNeg` 0, `offPos` 1: the strip x 6624-6697 between the
  ladder hive's band (x 6612-6620) and its reach is the safe place to bait it from.

### forest-1-tree

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 12 of 12 (640 px, one screen) |
| Role | reward |
| Purpose | The calm after the hive and the level's one reward room: nothing in it can kill. Walk to the first coin, then climb a tree by jumping up through its branches (a grey branch gives way and carries you down alive, so keep climbing) to the level's end on the crown. |
| Introduces | branch-climb, coin |
| Develops | crumble-support: on a climb a broken branch costs height, not life, and the escape is upward (the next branch), not across, and the rotten column falls behind the climber, so a stop rides him down to the floor |
| Combines | - |
| Mastered route | 4.95 s by the rotten column, 6.23 s by the sound tree (`TreeClimbTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | none: nothing in the room kills; the most expensive mistake (stopping on the top rotten branch, riding it down, then the sound tree) finishes in 11.05 s, 6.10 s more than the fast line (`TreeClimbTest`) |
| D26 | the sound tree reaches the crown with any rotten branch gone (load check `FallingPlatform.branchProblem`, swept over all 32 states in `TreeClimbTest`); nothing lethal is placed in the room (`TreeClimbTest`) |
| Optional | one coin on the floor at x 3040 (id 145), 128 px from the entrance and left of the first trunk, off the climb: the level's first coin, a pure first sighting (forest-1 has no healer, so it feeds no recipe; the healer counts only the forest's own coins) |
| Hints | none |
| Landmark | the two trees, with the level's end on the sound tree's crown |
| Status | Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (2026-11-19, the author chose "Alternative B" of the batch-2 design): the room is forest-1's one `reward` room; the beetle (id 147, [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327)) left it, nothing lethal was added, the level's end stays on the crown (checkpoint id 7, D80). A `reward` room is exempt from the density minimum and the first-meeting pocket and may hold nothing lethal; a rotten branch over the floor is not lethal (D107). Earlier: [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417) (D96): the first coin was added here, so `forest-entry` meets coins as a repeat. [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's second room, met after `forest-1-guard` and `forest-1-hive`; it is known here, so the branch climb and the coin stay the only unknowns; the author played the room on 2026-11-18 and approved the beetle's look and sounds ("all good") |
| Decisions | D80, D96, D105, D107 |

## forest-2 (play order 3)

### forest-entry

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 1 of 6 (x 0-736, 736 px, 1.15 screens) |
| Role | variation |
| Purpose | New ground, a new enemy and a new, hidden trap before any climbing: a YellowMob charges, is stopped by a low earth block and is stomped from it; climb the stair over the first pit past a Bat; on the floor after it a leg-hold trap lies under leaves (two tooth tips, sinew wraps and a peg show): stand short of it, let it snap empty and walk over it shut (or jump it); then cross the second pit past a second Bat to the exit, and see the coins. |
| Introduces | yellowmob, leg-hold |
| Develops | - |
| Combines | - |
| Repeats | bat-enemy, coin |
| Mastered route | 8.37 s required (jump the cover onto the YellowMob, the stomp, up the stair past Bat 41, down to the floor, stand short of the leg-hold until it snaps empty and walk over it shut, over pit 2, the second Bat at the exit), 12.02 s all coins (the route waits on the ground while Bat 41 is within 40 px, which the coins at x 336 and 368 under and beside its column need) and 7.00 s on a collection return (`RoomReplayTest`, three identical attempts); target about 20 s, coins about 30 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, about 8.4 s (the required route, 8.37 s, `RoomReplayTest`), for a death at the second Bat at its end or a fall into pit 2; the leg-hold, beat 3, about 4.6 s (the stair's landing, the soonest the hero can touch its teeth) |
| Beats | yellowmob@192, bat-enemy@352, leg-hold@496, bat-enemy@672 |
| Beat time | 8.37 (`RoomReplayTest`) |
| First meeting | yellowmob: beat 1, pocket at x 100, on the floor behind the 32 px cover (x 112-144) inside its range (hero centre x 96-192): it wakes, runs at him and is stopped by the cover, 112 px from Bat 41's column (x 352). Both Bats are known (met since forest-1-route) and in view from the pocket (the room is barely wider than a screen), neither can reach it (`FirstMeetingPocketTest.forestEntryTheYellowMob`, acting `RUN`); leg-hold: beat 3, retry 4.6 s, pocket at x 478, on the floor where the stair's jump lands, inside the trap's 24 px zone (from x 476) and 16.5 px clear of its litter (the body's edge to x 500): the cue (two tooth tips, sinew wraps, a peg and its cord) is in view, then the leaves shiver, burst and the jaws spring up to 14 px and snap empty, shut and settle back, and the cycle repeats while he stands there, and he is never caught. Bat 41 is 128 px away and Bat 43 (from x 656) 128 px from the jaws (`FirstMeetingPocketTest.forestEntryTheLegHold`, acting `SNAP`) |
| Optional | three coins (x 32, 336, 368; the first coin was met in `forest-1-tree`, #417); the coins at x 336 (floor) and x 368 (platform 3) lie under and beside Bat 41's column, so taking them means waiting under the Bat (all coins 12.02 s since D128, `RoomReplayTest`); the Bat ingredient that stood on the ledge at x 416-480 moved to `forest-walls` (#417) |
| D26 | no one-shot support or object: the leg-hold re-arms after its shut window and never blocks, it is crossed shut or jumped; the coins near Bat 41 cost a wait under the Bat but no coin is required; a fall into a pit (the level's Deadline, y 384) is a retry into the room, never a soft-lock; the required route crosses the pits by the stair and by a 48 px jump (`RoomReplayTest`, which fails if the route falls) |
| Hints | none (the D24 controls zone is removed by #376: forest-0 teaches the controls) |
| Landmark | the stair of three platforms (x 240-400) over the first pit, climbing to the coin at its top |
| Status | D72 audit 2026-11-16: change, done by [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376): the practice stone, carryable torch, practice light and ladder (objects 116-119, 123, 124) are removed and the room is split at x 736. Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (batch 4, 2026-11-20): the step became a 32 px cover (x 112-144), the mob came down behind it, Bat 41 moved 80 px right, the twig moved to the pit lip. D116 (playtest 2, F27, [#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561), approved by the author 2026-11-21): the Bomb (id 54) became a leg-hold (D128); Bat 43 moved from x 512 to x 672, `offNeg` 1 (patrol x 656-688, `RoomReplayTest.theExitIsReachedSafelyPastBat43AtEveryBatPhase` passes at every start offset) for the 96 px clearance of the leg-hold's first meeting and a clear pit-2 landing. The approved proposal put the leg-hold on the ledge; the ledge is not on the required route (the route walks the floor under it), so it stands on the floor at x 496-528, which is where the stair's jump lands |
| Decisions | D72, D96, D105, D128 |

Quicksand leaves the Forest ([#486](https://github.com/jlogicgames/edgard_in_kimeria/issues/486)). Each former pool becomes a
plain pit, as in `forest-0`: the floor block stops and restarts (x 0-240, x 320-560, x 608-736) and the level's `Deadline`
(id 125, y 384) kills a fall. Pit 1 is x 240-320 (80 px) under the stair; pit 2 is x 560-608 (48 px) (since D128 the second Bat patrols
past it, x 656-688, and the leg-hold stands before it). A hero's run jump covers about 86 px, so pit 1 leaves 17 px of margin and pit 2 49 px
(the hitbox is 11 px wide). The first YellowMob chases only while the hero's centre is within x 96-192 (since #500), so its hitbox ends
never travels right of its spawn (`offPos` 0), so its hitbox (x 198-218 at rest) ends at x 218, 22 px short of pit 1's edge (x 240). The YellowMob and the coins keep their places; Bat 43 (patrol x 656-688) and the leg-hold are described above (D128). No new element: a pit is plain `Collisions` plus the `Deadline`, both outside the registry.

Split detail ([#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376); history, the Bomb was removed by D128). The free floor between the
Bomb's blast (x 632-696) and the second YellowMob (x 832, left patrol end x 752, hitbox from x 758) is 56 px, too
narrow for this room's exit and right wall plus the next room's left wall and a 48 px safe entrance. The split is at
**x 736**: `forest-entry` ends with its exit at x 704-720 (8 px clear of the blast) and a wall at x 720-736;
`forest-walls` starts with a wall at x 736-752 and its entrance at x 752. To keep that entrance safe, the second
YellowMob's left patrol was shortened from `offNeg` 5 to 1 (left end x 816, hitbox from x 822, 41 px from the hero's
body); history: #500 moved that mob past the walls (x 1040) and the note no longer applies. The floor block x 608-1280 is cut at x 736 so each room owns its
own floor.

### forest-walls

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 2 of 6 (x 736-1280, 544 px, 0.85 screen) |
| Role | variation |
| Purpose | The two tall walls cannot be climbed: climb the tree in front of them and leap from its top branch across the first wall on to the second, where the Bat ingredient lies; drop from it on to the YellowMob, then the RedMob guards the exit. |
| Introduces | ingredient |
| Develops | branch-climb: the climb is no longer the goal (the crown of `forest-1-tree`) but the run-up for a leap: from the top branch a run jump crosses the first wall and lands on the second wall's 16 px top |
| Combines | - |
| Repeats | yellowmob, redmob |
| Mastered route | 5.55 s required (climb the three branches, leap on to the second wall and take the Bat, walk off on to the YellowMob and the RedMob: two stomps), 5.43 s on a collection return with its mobs already beaten, Bat included (`RoomReplayTest`, three identical attempts; `ForestWallsTest` asserts the route and the Bat); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, at most 5.55 s (the required route; the RedMob is reached 4.75 s in, `ForestWallsTest`); the cheapest death is a late leap (take-off from x 909 on) that overshoots the second wall into the YellowMob's range: 3.73 s from the entrance, 2.3 s of climbing and 1.3 s from the take-off to the bite (`ForestWallsTest.aLateLeapOvershootsTheSecondWallAndIsBittenByTheYellowMobWithinFourSeconds`) |
| Beats | branch-climb@880, yellowmob@1040, redmob@1136 |
| Beat time | 5.55 (`RoomReplayTest`) |
| First meeting | none: `branch-climb` was introduced in `forest-1-tree`, the yellowmob in `forest-entry` and the redmob in `forest-1`; the ingredient is a pickup, never a beat. The wall-jump pocket (`FirstMeetingPocketTest.forestWallsTheWallJump`) is removed, not replaced, and the wall jump itself is gone (D121) |
| Optional | the Bat ingredient (id 94, x 992, y 208 in the map: the SpawnPoints layer is drawn 16 px up, so the item's bottom edge is the wall's top edge y 208) on top of the second wall (`RoomReplayTest.theBatRestsOnTopOfTheSecondWall`), unchanged: the mastered leap lands on it, 27 px of the top branch's take-offs do (x 882-908), the second tree reaches it from the gap; moved here from `forest-entry` by #417 |
| D26 | no one-shot object: the branches are sound one-way platforms and never break; the floor is gapless (no pit). Every fall lands on the floor or a branch: a hero who drops left of the first wall climbs tree A again, one who walks off the top branch lands on branch B1 in the gap (x 975, `ForestWallsTest`), one who drops into the gap climbs out by B1 to the second wall's top in 1.27 s (`ForestWallsTest.aHeroWhoDropsIntoTheGapClimbsOutByTheSecondTreeToTheTopOfTheSecondWall`); a hero who overshoots the second wall lands in the YellowMob's range and is retried. The Bat stays reachable from every state until taken; the RedMob's range (x 1104-1216) does not meet the YellowMob's (x 1040-1056) |
| Hints | none (forest-2 has none, D33) |
| Landmark | the tree in front of the two walls, and the leap from its top on to the Bat |
| Status | redesigned, [#545](https://github.com/jlogicgames/edgard_in_kimeria/issues/545) (card approved by the author 2026-11-21: branches, a second tree with one branch in the gap, the stomp as the mastered answer, role `variation`, no crowns, the shipped branch and trunk sprites), after D116 removed the wall jump ([#546](https://github.com/jlogicgames/edgard_in_kimeria/issues/546)). Earlier: new room from the D72 split of `forest-entry`, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376); took the Bat from `forest-entry`, [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417); the YellowMob moved to the second wall's foot under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (batch 4). D84 check: the whole room is in view from the entrance; the tree and its branches are the only thing to try in front of the wall; every mistake is a slower way, never a death except the overshoot into the YellowMob. Built and tested with a scripted hero (`ForestWallsTest`: the route, the leap strip, the walker, the late leap, the gap) |
| Decisions | D72, D80, D96, D105, D116 |

Sketch (Y grows downward, floor top y 304; one character is 16 px, column 0 is x 736; `T` trunks, `=` branches, `W` the two walls, `B` the Bat, `Y` YellowMob, `R` RedMob, `E` the exit, `@` the entrance):

```
       0000000000111111111122222222223333
       0123456789012345678901234567890123
y 144  |       T      T                 |
y 160  |       T==    T                 |    A3 x 880-912
y 176  |       T      T                 |
y 192  |       T  W   TB                |    wall 1 top y 192; the Bat on wall 2
y 208  |     ==T  W   TW                |    A2 x 832-864 (mirror); wall 2 top y 208
y 224  |       T  W   TW                |
y 240  |       T  W   TW                |
y 256  |       T==W ==TW             EE |    A1 x 880-912; B1 x 944-976 (mirror)
y 272  |@      T  W   TW  YY    RR   EE |
y 288  |@      T  W   TW  YY    RR   EE |
y 304  ==================================
```

- Walls (ids 44, 45) x 912-928 y 192-304 and x 992-1008 y 208-304, the Bat (id 94), both mobs, the bushes, the entrance and the exit are unchanged.
- Tree A: `Decor` trunk (id 230) x 864 and `Collisions:Platform` branches `skin=branch` A1 (id 232) x 880-912 y 256, A2 (id 233, `mirror`) x 832-864 y 208, A3 (id 234) x 880-912 y 160: three 48 px steps. Tree B: trunk (id 231) x 976 and B1 (id 235, `mirror`) x 944-976 y 256, 48 px above the gap's floor and 48 px below the second wall's top. The trunks are map y 32 (the SpawnPoints layer is drawn 16 px up), 288 tall, as in `forest-1-tree`.
- The leap, measured by the body's centre x at the take-off from A3 (`ForestWallsTest.theLeapFromTheTopBranchLandsOnTheSecondWallFromA27PxStripOfTheBranch`): 882 to 908 lands on the second wall's top (centre x 987-1013), 881 and less meets its face, 909 and more overshoots it into the YellowMob. The mastered take-off is x 896.
- A walker off the end of A3 lands on B1 (centre x 975, feet y 256), not on the first wall's top: the 96 px fall takes about 0.57 s and carries him about 57 px, far past the 16 px wall.

### forest-fissure

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 3 of 6 (640 px, one screen, x 3008-3648: appended at the map's end, listed after `forest-walls` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A burl on the floor lances a root up on a clock: watch the first one alone on open floor and pass between thrusts, then climb through the branch the second one pierces, starting right after a thrust; on the ledge a beetle guards the exit: shoot it from the lip, lure it to commit and step back, or walk on past it. |
| Introduces | clocked-column |
| Develops | - |
| Combines | - |
| Repeats | beetle |
| Mastered route | 9.30 s from the entrance by the lure, the answer that needs no charge (`RoomReplayTest`, three identical attempts; `ForestFissureTest`), 8.60 s with one fireball from the ledge's lip, 8.25 s on every collection return (the beetle stays gone; `RoomReplayTest`); 6.40 to 10.27 s over start offsets of 0 to 3.5 s (0.05 s steps), with and without the longer-warnings assist, for a hero who waits short of each column until a jet ends and lures the beetle (`ForestFissureTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | the most expensive death is a stop beside the beetle when it commits, after the mastered climb: 8.35 s from the entrance at start offset 0, and 8.75 s at worst over start offsets of 0 to 3.5 s counted from the end of the wait (the old card's basis and that of the offset times above; 11.70 s from entering with the wait included) (`ForestFissureTest`); a careless walker dies at the floor fissure at 23 of 71 start offsets, at most 3.5 s after entering |
| Beats | clocked-column@3232, clocked-column@3360, beetle@3508 |
| Beat time | 9.30 (`RoomReplayTest`) |
| First meeting | clocked-column: beat 1, pocket at x 3200, just short of the first column on open floor, where it is seen lancing, 112 px from the second (`FirstMeetingPocketTest.forestFissureTheFirstFissure`) |
| D26 | no lever, support or one-shot state on the route: both fissures are passed by timing alone from every start offset (`ForestFissureTest`), and the clock restarts with the room. The beetle never blocks the exit: its blast (x 3480-3592 at most, y 176-208) reaches neither the ledge's lip (x 3424-3480), nor the branch below it (a hero on it has his top at y 234), nor the exit (x 3600); it needs no charge: the lure, or walking straight past it, which reaches the exit at every crawl phase with and without the assist (it bursts behind him). A retry rebuilds it at its spawn |
| Hints | none (forest-2 has none, D33; no hint for the beetle: new monsters are met in play, D100) |
| Landmark | the root lancing up through the branch, seen across the screen from the entrance |
| Status | new, [#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366) (card, art, sound and placement approved by the author 2026-11-17). D84 check: one screen, so both fissures are in view and jetting from the entrance; the first is met alone on open floor; the danger is lethal exactly where drawn; the waiting places (either side of each column, the floor under the branch) are obvious; the sounds play only while a fissure is in view. The author played the room and heard `fissure_rumble` and `ember_jet` in play on 2026-11-18 ([#463](https://github.com/jlogicgames/edgard_in_kimeria/issues/463)). [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327) (card approved by the author 2026-11-18): the beetle's third room and forest-2's only one. It crawls the exit ledge, in view from the entrance, and hears the hero only once he is on the ledge (never on the floor or the branch). The approved card counted it as a develop ("walking into it kills: the exit is 1 s away"); measured, a hero who keeps walking always clears the blast (80 px in the 0.8 s wind-up, its whole width), as in forest-1, so the card records it as a repeat. Built and tested with a scripted hero. D116 (playtest 2, F28, [#562](https://github.com/jlogicgames/edgard_in_kimeria/issues/562), approved by the author 2026-11-21): both `EmberFissure` objects (ids 151, 152) became `LanceRoot` (D129), with the same clock, phases, column and beats; the ember fissure returns as a Swamp geyser |
| Decisions | D91, D129, D72, D100, D101 |

Sketch (Y grows downward, floor top y 304, ledge top y 208; walls x 3008-3024 and 3632-3648):

```
x    3008 3040        3232-48           3328    3360-76 3392  3424      3508-3564          3600-3632 3648
     |wall|start        A              [ branch 3328-3392 ]   [ ledge 3424-3648, y 208 ]  b  >>  E |wall|
     |    | @           |||                      |||B         |                               |
=====+===================================================================================+========  floor y 304
```

- `LanceRoot` A (id 151, an `EmberFissure` until D129) x 3232, y 316 in the map (the SpawnPoints layer is drawn 16 px up: top y 300, 4 px above the
  floor line), period 3.5 s, jet 1.0 s, height 96, phase 0.
- One-way `Collisions:Platform` branch (ids 155, 156) x 3328-3392, y 256; `LanceRoot` B (id 152) x 3360, y 316, phase
  1.75 (half a period out of step with A): its column passes through the branch (embers pass one-way platforms).
- Ledge (id 157) x 3424-3648, y 208-304, tiled with the ground-edge tiles; `RoomStart` (id 149) (3040, 256); `RoomExit`
  (id 150) x 3600-3632, y 160-208, on the ledge against the wall (moved 16 px right by #327, from x 3584).
- `Beetle` (id 159) map x 3508, map y 208 (top y 192, floor line 208, on the ledge), `offNeg` 0, `offPos` 2: its centre crawls
  x 3520-3552, its blast covers at most x 3480-3592, y 176-208. The lip x 3424-3480 is outside every blast and its trigger
  (from x 3501), so a hero there is heard and waited for, never reached.

### forest-glade

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 4 of 6 (832 px, 1.3 screens, x 3648-4480: appended at the map's end, listed after `forest-fissure` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | Meet the wolf from a branch it cannot reach: jump a thorn root, hop on to a fallen trunk that hides you from the wolf, step out on the one-way branch, watch it notice you, sprint under you and bay, then step off on to its back; jump a second thorn root on the way to the exit. |
| Introduces | wolf |
| Develops | - |
| Combines | - |
| Repeats | proximity-thorn |
| Mastered route | 8.85 s from the entrance (`RoomReplayTest`, three identical attempts, `ForestGladeTest` asserts it with and without the assist, the route meets no wind-up): jump the thorn root, jump on to the trunk, wait on the branch until the wolf bays, walk to the branch's end and step off on to it (a stomp), run to the second thorn root and jump it, walk to the exit; 7.42 s on every collection return (the wolf stays gone with its body, D101, pinned in `RoomReplayTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | the most expensive death is the second thorn root after the wolf: hit at 8.15 s from the entrance for a hero who walks straight into it after the mastered route (`ForestGladeTest.aHeroWhoWalksStraightIntoTheSecondThornRootAfterTheWolfIsHit`), the whole room replayed. D105's 6 s limit is for a first meeting that is not among the first two beats: the wolf is beat 2 and the root is a known element, so neither is held to it, and 8.15 s is under the 15 s at which D105 asks for a split. The wolf's own bite on the mastered pace is a hop down off the trunk or the branch's near end in front of it: 4.27 s (hops at x 3930 to 3960, `ForestGladeTest.aHeroWhoHopsDownInFrontOfTheWolfIsBittenWithinSixSeconds`, from x 3966 on the same hop lands on it or beyond). The first thorn root at the soonest 1.17 s (a hero who walks straight on) |
| Beats | proximity-thorn@3808, wolf@4144, proximity-thorn@4360 |
| Beat time | 8.85 (`RoomReplayTest`) |
| First meeting | wolf: beat 2, pocket at x 3976, standing on the one-way branch (top y 272, 32 px above the wolf's floor), which does not cut its sight as the trunk's face does: it notices him, sprints under him, bays at its range end, gives up and trots on its patrol, and he is never committed to (a hero more than 24 px up is out of its reach). The thorn root (x 3808-3824) is 160 px from the wolf's span (x 3984-4192), 152 px from the pocket (`FirstMeetingPocketTest.forestGladeTheWolf`, raised pocket at y 272, acting `BAY`) |
| Optional | none |
| D26 | the exit is always reachable: the wolf can never hold a place. It bays for at most 1.5 s, gives up and does not notice that hero again until he has been out of its sight for 1.0 s. Every answer on its floor works (a jump, a swing, a stomp, running with the assist) and the trunk top and the branch are refuges. The floor from x 4264 on is out of its reach, and the second thorn root (x 4360) and the exit (x 4448) stand on it. The first root (x 3808) is off the wolf's floor because the trunk stands between them, the second lies beyond x 4351, the end of its reach. Both stay up in their own columns and are jumped (`ForestGladeTest`) |
| Hints | none (forest-2 has none, D33; new monsters are met in play, D100) |
| Landmark | the fallen trunk with its branch over the wolf's run, and the wolf baying under it |
| Status | new, [#331](https://github.com/jlogicgames/edgard_in_kimeria/issues/331) (card approved by the author 2026-11-21: a new room, fourth in forest-2, the stomp from the branch as the mastered answer, the wall jump as the third beat, replaced on 2026-11-21 by a second thorn root after D116 removed the wall jump, #545 #546). D84 check: one screen and a third; the wolf is in view from the branch, the danger is what the wolf card draws (the breath line, the crouch), the waiting places (the branch, the trunk top) are obvious, nothing hides the wolf from a hero on the branch. Built and tested with a scripted hero (`ForestGladeTest`: the route, the stomp, the hop bite, the root, a jump from every commit gap with and without the assist, a runner with and without it, a swing, the refuges and the reach, a wait for the give-up) |
| Decisions | D72, D100, D105, D115 |

Sketch (Y grows downward, floor top y 304, trunk and branch tops y 272; one character is 16 px, column 0 is x 3648; the sky above y 256 is empty):

```
col   0         1         2         3         4         5
      0123456789012345678901234567890123456789012345678901
y 256 |                                                 E|
y 272 |                TT====                           E|
y 288 |  @      ^      TT  <ww       <ww          ^     E|
y 304 ====================================================
```

- `RoomStart` (id 195) (3680, 256): the hero's body x 3698-3709, 18 px outside the wolf's sense box (x 3727-4447).
- `ThornRoot` (id 192) x 3808, y 296 in the map (the SpawnPoints layer is drawn 16 px up: top y 280, standing on the floor line), `zone` 64.
- Left wall `Wall` (id 186) x 3648-3664; floor (id 187) x 3648-4480, y 304-368 (one gapless block, the wolf's range must have none under it).
- Trunk (untyped solid, id 188) x 3920-3952, y 272-304: its right face cuts the wolf's sight at the eye height of 16 px and the lunge stops against it; its top is a refuge 32 px up, out of the sight band's reach on the left, so a hero on it is not seen.
- One-way `Platform` branch (id 189, no skin: it is not a branch-climb beat) x 3952-4016, y 272: flush with the trunk top, so he walks out on to it. A platform is not a solid for sight, so a hero on it is seen from the wolf's floor.
- `Wolf` (id 193) x 4144, y 296 in the map (48x24: top y 280, floor line y 304), `offNeg` 10, `offPos` 0: its body trots x 3984-4144, the minimum range of 160 px; sight x 3727-4447, its lunge box at the range end x 4184-4263.
- Second `ThornRoot` (id 197) x 4360, y 296 in the map, `zone` 64: its column x 4360-4376 and its trigger zone x 4296-4440 lie past the wolf's reach (x 4351 at most), 168 px from its span (x 3984-4192).
- Right wall `Wall` (id 191) x 4464-4480, y 0-304; `RoomExit` (id 196) x 4448-4464, y 256-304, on the floor against the wall: 16 px wide so it lies outside the wolf's sense box (x 3727-4447). `Room` (id 194), `leaves` 10.

### forest-canopy

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 5 of 6 |
| Role | approach |
| Purpose | The forest's trap families one at a time before the boss. |
| Introduces | thorn-vine, false-honey-fungus |
| Develops | touch-explosive: the exploding root, with fireballs now in hand, makes the from-afar choice cost ammunition; crumble-support: the falling platform holds an optional ascent that a bad landing spends; honey-fungus: its lethal look-alike twin |
| Combines | - |
| Repeats | redmob, falling-stone, coin, ingredient |
| Mastered route | 9.48 s (`Forest2TrapsTest`, `RoomReplayTest`), unchanged with Bat 65 gone; target about 20 s |
| First learning | not measured (#135) |
| Retry cost | about 9.5 s for a death at the falling stone; a death at the vine 5.93 s at the soonest (a walker held 36 frames at the entrance, `Forest2TrapsTest.theCheapestDeathAtTheVineOnTheMasteredPaceIsPinned`): the mastered pace, held up to 35 frames, passes |
| Beats | redmob@1472, false-honey-fungus@1672, thorn-vine@1896, touch-explosive@2064, falling-stone@2224 |
| Beat time | 9.48 (`RoomReplayTest`) |
| First meeting | false-honey-fungus: beat 2, pocket at x 1640, inside its trigger zone (from x 1608) and 26 px short of its cloud (x 1672-1696): it swells and bursts in front of him and is spent, a trip-and-stand pocket (D109), 136 px from the RedMob's span (`FirstMeetingPocketTest.forestCanopyTheFalseFungus`, acting `BURST`); thorn-vine: beat 3, retry 5.93 s (measured, hero hit, at most 6 s), pocket at x 1848, 20 px short of the tip's sweep, 144 px from the root: the vine is seen swinging toward him and back (`FirstMeetingPocketTest.forestCanopyTheThornVine`, acting `SWING_LEFT`) |
| Optional | coins and the Mushroom on the ascent over the falling platform |
| D26 | the spent platform closes only the optional ascent; the exit stays reachable |
| Hints | none |
| Status | D72 audit 2026-11-16: keep. Reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) for D105 (batch 4, 2026-11-20): Bat 65 (x 1760) left the room, so the false honey fungus has its pocket and the vine is beat 3 instead of 4; the vine's `phase` went from 0 to 2.77 s so its cheapest death is 5.93 s, inside D105's 6 s (it was 6.45 s); the mastered route is unchanged at 9.48 s and the room left the first-meeting list |
| Decisions | D59, D63, D105, D109 |

### forest-boss

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 6 of 6 |
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

