# Room cards

One card per shipped room, in play order (`KimeriaGame.LEVEL_NAMES`, then each map's room order). The rule the
cards serve is [D72](DECISIONS.md#d72-every-room-gives-the-player-something-new): every room introduces, develops or
combines at least one element the player has not met in that form. How to write a card and when:
[ROOMS.md, "Every room gives something new (D72)"](ROOMS.md#every-room-gives-something-new-d72). The skill
`kimeria-room-design` (`.agents/skills/`) walks through it.

`RoomCardsTest` (`:core:test`) reads this file and the maps. It fails when a shipped room has no card, a card names
no room, a required field is empty, an element id is unknown, a Tiled type in a map is missing from the registry, the
room where an element is first met does not list it under **Introduces**, a card introduces something already met,
or develops or combines something not met yet, or a non-lesson room introduces more than two elements without an
**Overload reason** that cites an issue. What the test cannot judge (whether a develop really changes the answer,
whether each new element is first met alone) is the reviewer's and the author's job.

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
| Optional | no | optional routes and rewards |
| D26 | no | how every mandatory exit stays reachable |
| Hints | no | hint zones, if any |
| Landmark | no | the one thing the player will remember the room by (a suggestion from the Primal Planet study, #352) |
| Overload reason | when over the cap | why more than two introductions, and the issue that resolves it |
| Status | no | the D72 audit verdict and follow-up |
| Decisions | no | the D-numbers the room implements |

Lesson rooms (`Role` = `lesson`) follow D33-D40 and D48 (one lesson element, plus at most one trap) instead of the
two-introduction cap.

## Element registry

What counts as an element is what changes what the player does. A Tiled type maps to one element; several types may
map to the same element when only the look differs (a **reskin**, not novelty under D72). Keys are the object's
`type`, then `:` and the `Actionable` `type`, the `Decor` `kind`, a non-default `skin`, `lever`/`running` for an
`Escalator` (`running` = false or not), or the `Collectable` name; `Collisions:` prefixes a collision type.
Elements without a Tiled key are skills the geometry asks for; their first sighting is declared on the cards only.
How each trap looks and warns is its own card in [TRAPS.md](TRAPS.md) (D71); this registry only groups traps by
what the player does about them.

| Element id | Tiled keys | What the player does |
| --- | --- | --- |
| lever | `Trigger`, `Actionable:Torch` | operates a lever and reads its linked effect |
| ladder | `Ladder` | lowers a ladder by lever and climbs it |
| lever-wall | `Actionable:Wall` | raises and lowers a wall that blocks the way |
| lever-platform | `Escalator:lever` | starts and stops a platform and rides it |
| running-platform | `Escalator:running` | times a jump onto a platform that never stops |
| carry-throw | `Stone` | picks up a stone and throws it (at a lever, at a trap) |
| carry-torch | `CarryableTorch` | carries a torch and lights things with it |
| crumble-support | `FallingPlatform`, `FallingPlatform:log`, `FallingPlatform:branch` | crosses before the support gives way |
| proximity-thorn | `ThornRoot`, `ThornTuft` | jumps thorns that rise when the hero comes near |
| falling-branch | `FallingBranch` | keeps walking under a branch that falls behind him |
| falling-stone | `FallingStone` | reads the shadow and does not stand under it |
| touch-explosive | `ExplosiveStump`, `ExplosiveRoot`, `Bomb` | jumps it, walks clear of the wind-up, or sets it off from afar |
| puffball | `Puffball` | crosses a rhythmic spore cloud in its calm |
| thorn-vine | `ThornVine` | passes a swinging vine by watching its rhythm |
| bramble | `Bramble` | waits out a thorn arm that grows across a lane on a clock and passes in its open window (D89) |
| hanging-hive | `HangingHive` | passes under, or climbs through, a falling curtain of stingers in its calm; may knock the hive down |
| honey-fungus | `HoneyFungus` | sees a harmless mushroom swell and burst |
| false-honey-fungus | `FalseHoneyFungus` | tells the lethal twin apart and jumps or waits out its cloud |
| deadfall | `Deadfall` | jumps a trip vine, or trips it, stops and lets the log swing past |
| quicksand | `Collisions:QuickSand` | keeps moving across sinking ground |
| redmob | `RedMob` | beats a walking enemy with a visible whip wind-up (sword or stomp) |
| yellowmob | `YellowMob` | stomps a walking enemy |
| bat-enemy | `Bat` | passes or beats a flying enemy |
| boss | `Boss` | fights the stage boss |
| fireball-refill | `FireballRefill` | picks up a ranged-attack charge |
| coin | `Collectable:Coin` | collects coins, often off the required route |
| ingredient | `Collectable:Bat`, `Collectable:Mushroom` | collects a healer ingredient |
| wall-jump | (skill) | wall-jumps a wall the route cannot pass otherwise |
| branch-climb | (skill) | climbs a tree by jumping up through one-way branches (`Collisions:Platform` with `skin=branch`, D80) |

Not elements (scenery, markers, plain geometry): `Decor:bush`, `Decor:lookalike`, `Decor:trunk`, `HangingVine`, `Torch`
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
| Level | forest-1, play order 2, room 1 of 9 (800 px, x 3520-4320: placed after the tree room on the map, first in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | The first enemy, alone on flat ground: read the whip's raised arm, then sword it, stomp it or spend a charge; the refill behind it gives a spent charge back. |
| Introduces | redmob, fireball-refill |
| Develops | - |
| Combines | - |
| Mastered route | 7.43 s, zero charges, the sword takes the mob (`RoomReplayTest`); 6.97 s to the refill with two charges; target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | under 3 s: the mob is the only hazard, its range starts about 150 px from the entrance |
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
| Level | forest-1, play order 2, room 2 of 9 (640 px, one screen, x 4960-5600: appended after the lever room on the map, second in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A trap whose trigger is in plain sight: jump the trip vine, or trip it, stop and let the log swing by. |
| Introduces | deadfall |
| Develops | - |
| Combines | - |
| Mastered route | 5.17 s jumping the vine (take-offs 2 to 60 px before it), 6.47 s tripping it and stopping (6.88 s with the longer-warnings assist) (`DeadfallTrapTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 2.65 s: a hero who walks in without jumping is hit 2.65 s after the entrance (2.97 s with the assist) |
| D26 | no support, no one-shot state the route needs: a jump leaves the log tied, a trip leaves it hanging from the snag above head height and the vine cut, and the exit is reachable from both (`DeadfallTrapTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the log hauled up and lashed to the bough by the tree trunk |
| Status | new, [#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363) (card, art and placement approved by the author 2026-11-17). The deadfall is the room's only object and is in view from the entrance with the exit (D84). Built and tested with a scripted hero, not yet played by the author or a novice ([#436](https://github.com/jlogicgames/edgard_in_kimeria/issues/436)) |
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
| Level | forest-1, play order 2, room 3 of 9 (1280 px, two screens) |
| Role | combination |
| Purpose | Machinery that runs by itself, then the trap course: tuft, a known enemy, a spore cloud and a log over a lethal pit. |
| Introduces | running-platform, puffball |
| Develops | crumble-support: the log crumbles over a lethal pit, not a recoverable drop; proximity-thorn: the tuft's wider 80 px zone |
| Combines | - |
| Repeats | redmob, lever, lever-wall, lever-platform |
| Mastered route | 12.27 s to the room exit (`Forest1TrapsTest`, `RoomReplayTest`), unchanged by #375; 14.98 s with the longer-warnings assist; target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 12.73 s for a death at the log (D58; `Forest1TrapsTest`, unchanged by #375) |
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
| Level | forest-1, play order 2, room 4 of 9 (640 px, one screen) |
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

### forest-1-bramble

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 5 of 9 (640 px, one screen, x 5600-6240: appended at the map's end, listed after `forest-1-lever` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A thorn arm grows over a ladder top on a clock: watch it from the floor, wait on the ladder, climb out when it pulls back. |
| Introduces | bramble |
| Develops | ladder: its top is now a place to wait and watch, and leaving it is timed |
| Combines | - |
| Repeats | ladder |
| Mastered route | 10.73 s from a fresh start, the same with the longer-warnings assist; 9.42 to 12.80 s over every start offset (0.05 s steps) for a hero who goes when the lane is open or waits on the ladder for the arm to come back (`Forest1BrambleTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 6.70 s: a hero who climbs straight out from a fresh start is caught 6.70 s after the entrance and replays the room from its entrance (`Forest1BrambleTest`) |
| D26 | no lever, no support, no one-shot state: the ladder is always down and the arm comes back every 4 s, so waiting on the ladder always gives another window; the band never reaches the floor, so the walk to the ladder is safe (`Forest1BrambleTest`) |
| Hints | none (forest-1 has none, D33) |
| Landmark | the thorn arm sweeping over the ladder top under the shelf's end |
| Status | new, [#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364) (card, art and placement approved by the author 2026-11-17). D84 check: the bramble is the room's only hazard and is in view, cycling, from the entrance; the hero walks the whole floor under it out of reach before the climb, so its rhythm is shown before it gates progress. Built and tested with a scripted hero, not yet played by the author or a novice ([#438](https://github.com/jlogicgames/edgard_in_kimeria/issues/438)) |
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

### forest-1-route

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 6 of 9 (832 px) |
| Role | variation |
| Purpose | One lever, two supports in opposite states: choose the optional ladder or the mandatory lift, and pay a one-shot step for a refill. |
| Introduces | bat-enemy |
| Develops | crumble-support: a one-shot step whose loss closes the optional refill until Restart Level; fireball-refill: the bat gives a reason to spend a charge, so the refill is worth something |
| Combines | ladder + running-platform: one lever lowers the ladder and stops the lift, so the two routes exclude each other (first taught by forest-1-lever, D86, so here it is a choice) |
| Repeats | lever |
| Mastered route | 11.02 s required, 18.32 s with the optional refill (`Forest1RouteTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | up to about 11 s (a death at the bat or the lift replays the room) |
| Optional | the fireball refill on the pad over the one-shot step |
| D26 | every state reached by supported actions reaches the exit without Restart Level (D67) |
| Hints | none |
| Status | D72 audit 2026-11-16: keep. Since #418 (D86) the two-mechanism lever is taught in forest-1-lever first, so this room uses it as a choice, not as a new rule. |
| Decisions | D67, D86 |

### forest-1-guard

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 7 of 9 (768 px) |
| Role | combination |
| Purpose | A guard that cannot be jumped, under a spore cloud: time the sword, pay a charge, or take the roof route. |
| Introduces | - |
| Develops | redmob: a guard in a 32 px tunnel that cannot be jumped or stomped, so it must be beaten or avoided by the roof |
| Combines | redmob + puffball: the cloud covers every place the sword reaches from, so the melee needs the calm; ladder + fireball-refill: the lever-lowered ladder leads to a roof route that pays one charge |
| Repeats | lever |
| Mastered route | 6.83 s timed melee, 6.15 s with a fireball, 7.67 s by the roof (`Forest1CombatTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)); a straight run from the entrance dies once by design (D68) |
| Retry cost | about 1.7 s for the first walk-in death; under 8 s at worst |
| Optional | the roof route and its refill |
| D26 | the floor route needs no support; the roof is optional (D68) |
| Hints | none |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D68 |

### forest-1-hive

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 8 of 9 (640 px, one screen, x 6240-6880: appended at the map's end, listed after `forest-1-guard` in the `Rooms` list, which sets the play order) |
| Role | variation |
| Purpose | A hive drops a curtain of stingers on a clock: pass under the first in its calm on open ground, then climb a ladder the second hive's curtain runs down, starting right after a curtain. |
| Introduces | hanging-hive |
| Develops | - |
| Combines | hanging-hive + ladder: the second hive's band covers the whole ladder to the exit ledge, so the climb must start right after a curtain and fit its window |
| Repeats | ladder, carry-throw |
| Mastered route | 7.12 s from a fresh start; 6.85 to 12.22 s over start offsets of 0 to 9 s (0.1 s steps), with and without the longer-warnings assist, for a hero who waits outside each band while it is in the air or about to be and starts the climb right after a curtain (`Forest1HiveTest`); target about 20 s (D19) |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | 7.5 s at most: a hero who walks and climbs without waiting dies in every one of 45 start offsets, 12 times under the floor hive and 33 times under the ladder hive, 3.1 to 7.5 s after the entrance, and replays the room from its entrance (`Forest1HiveTest`) |
| Optional | knock the floor hive down: with the stone by the entrance by a jump-throw (28 of 28 tested throw points, body right edge x 6330 to 6438), or with a fireball near the top of a jump (10 of 13 tested jump timings, one charge); after that the floor pass is free. The ladder hive hangs out of reach of both |
| D26 | no lever, support or one-shot state on the route: both hives are passed by timing alone, from every start offset (`Forest1HiveTest`); a knock-down only removes a danger, and a retry rebuilds both hives |
| Hints | none (forest-1 has none, D33) |
| Landmark | the ladder with the hive hanging over it, its curtain running down the rungs |
| Status | new, [#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365) (card, art, sound and placement approved by the author 2026-11-17). D84 check: the room is one screen, so both hives are in view and raining from the entrance; the floor hive is met first, alone, over open floor (the ladder is known); the danger falls where it is drawn; the waiting places (beside each band, the ladder's foot) are obvious; the ladder hive's sounds play only while it is in view. Built and tested with a scripted hero; not yet played by the author or a novice ([#440](https://github.com/jlogicgames/edgard_in_kimeria/issues/440)) |
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
| Level | forest-1, play order 2, room 9 of 9 (640 px, one screen) |
| Role | variation |
| Purpose | Climb a tree by jumping up through its branches; a grey branch gives way, so keep climbing and never stop on it. |
| Introduces | branch-climb |
| Develops | crumble-support: on a climb a broken branch costs height, not life, and the escape is upward (the next branch), not across, and the rotten column falls behind the climber, so a stop rides him down to the floor |
| Combines | - |
| Mastered route | 4.95 s by the rotten column, 6.23 s by the sound tree (`TreeClimbTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | no death by design: the most expensive mistake (stopping on the top rotten branch, riding it down, then the sound tree) finishes in 11.05 s, 6.10 s more than the fast line (`TreeClimbTest`) |
| D26 | the sound tree reaches the crown with any rotten branch gone (load check `FallingPlatform.branchProblem`, swept over all 32 states in `TreeClimbTest`) |
| Hints | none |
| Landmark | the two trees, with the level's end on the sound tree's crown |
| Decisions | D80 |

## forest-2 (play order 3)

### forest-entry

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 1 of 4 (x 0-736, 736 px, 1.15 screens) |
| Role | variation |
| Purpose | New ground and a new enemy before any climbing: stomp a YellowMob, keep moving over quicksand, pass the Bats, jump the Bomb, and see the first coins and the Bat ingredient. |
| Introduces | yellowmob, quicksand, coin, ingredient |
| Develops | touch-explosive: the Bomb (a reskin of the stump) stands 48 px past the second quicksand pool, so the hero comes off sinking ground straight into its jump, and backing away from its wind-up means backing onto the sand |
| Combines | - |
| Repeats | bat-enemy |
| Mastered route | 6.80 s required, 10.40 s all coins and 6.12-6.72 s on a collection return with the Bat (`RoomReplayTest`, three identical attempts); target about 20 s, coins about 30 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, about 7 s, for a death at the Bomb at its end (was about 16 s for the undivided room) |
| Optional | three coins (x 32, 336, 368) and the Bat ingredient on the ledge at x 416-480, reached from the platform stair (the ladder that also led there is removed) |
| D26 | no one-shot support; the Bomb is the only one-shot object and the exit stays reachable after it blows |
| Hints | none (the D24 controls zone is removed by #376: forest-0 teaches the controls) |
| Landmark | the stair of three platforms over the first quicksand pool |
| Overload reason | Four introductions: the split moves the wall jump out, but the coin and the ingredient have no earlier room that can take them (forest-0 is lessons, forest-1 is being re-cut by #375, forest-canopy is at the cap). Moving the first coin or the ingredient elsewhere is tracked by [#417](https://github.com/jlogicgames/edgard_in_kimeria/issues/417). |
| Status | D72 audit 2026-11-16: change, done by [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376): the practice stone, carryable torch, practice light and ladder (objects 116-119, 123, 124) are removed and the room is split at x 736 |
| Decisions | D72 |

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
| Level | forest-2, play order 3, room 2 of 4 (x 736-1280, 544 px, 0.85 screen) |
| Role | challenge |
| Purpose | Climb two tall walls by wall jumps, starting where a YellowMob walks, then pass a RedMob to the exit. |
| Introduces | wall-jump |
| Develops | - |
| Combines | yellowmob + wall-jump: the YellowMob patrols up to the foot of the first wall, so the climb starts only after it is stomped or timed past |
| Repeats | yellowmob, redmob |
| Mastered route | 8.80 s required, 7.32 s on a collection return with its mobs already beaten (`RoomReplayTest`, three identical attempts); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | the whole room, about 9 s, for a death at the RedMob by the exit |
| D26 | no one-shot object; both walls are climbable from every state |
| Hints | none |
| Landmark | the two tall walls (x 912 and x 992) |
| Status | new room from the D72 split of `forest-entry`, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376) |
| Decisions | D72 |

### forest-canopy

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 3 of 4 |
| Role | approach |
| Purpose | The forest's trap families one at a time before the boss. |
| Introduces | thorn-vine, false-honey-fungus |
| Develops | touch-explosive: the exploding root, with fireballs now in hand, makes the from-afar choice cost ammunition; crumble-support: the falling platform holds an optional ascent that a bad landing spends; honey-fungus: its lethal look-alike twin |
| Combines | - |
| Repeats | redmob, bat-enemy, falling-stone, coin, ingredient |
| Mastered route | 9.48 s (`Forest2TrapsTest`, `RoomReplayTest`); target about 20 s |
| First learning | not measured (#135) |
| Retry cost | about 9.5 s for a death at the falling stone |
| Optional | coins and the Mushroom on the ascent over the falling platform |
| D26 | the spent platform closes only the optional ascent; the exit stays reachable |
| Hints | none |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D59, D63 |

### forest-boss

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 4 of 4 |
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
