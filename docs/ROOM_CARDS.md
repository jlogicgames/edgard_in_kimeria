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
| honey-fungus | `HoneyFungus` | sees a harmless mushroom swell and burst |
| false-honey-fungus | `FalseHoneyFungus` | tells the lethal twin apart and jumps or waits out its cloud |
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
| Status | D72 audit 2026-11-16: keep. The map's `lesson=existing-traps` property is stale (the stump is no longer new); rename it when the map is next edited ([#377](https://github.com/jlogicgames/edgard_in_kimeria/issues/377)). |
| Decisions | D40, D47 |

## forest-1 (play order 2)

### forest-1-traps

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 1 of 4 (1280 px, two screens) |
| Role | combination |
| Purpose | First enemies and machinery that runs by itself, mixed with the tutorial's levers and traps. |
| Introduces | redmob, running-platform, fireball-refill, puffball |
| Develops | crumble-support: the log crumbles over a lethal pit, not a recoverable drop; proximity-thorn: the tuft's wider 80 px zone |
| Combines | - |
| Repeats | lever, lever-wall, lever-platform |
| Mastered route | 12.27 s to the room exit (`Forest1TrapsTest`, `RoomReplayTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | about 12.7 s for a death at the log (D58) |
| D26 | the pit is the level `Deadline`, a retry; nothing is re-armed (D58) |
| Hints | none |
| Overload reason | Four introductions, and the first enemy is not met alone. Measured on the shipped map (2026-11-16): the platforms (x 96-224) come first and are behind the hero when the RedMob is met: it reacts only once the hero is back on the floor (its range needs the hero's centre in x 192-512 and a vertical overlap, so a hero standing on a platform, body y 250-272, never wakes it), then steps from body x 358 to 352 and stays at x 352-372, right of the 16 px block at x 304-352, 128 px from the last platform; it is met instead in the stretch x 256-450 where two other first sightings stand, the running escalators (x 256-432, y 240; x 432, y 224-288) and the fireball refill (x 312, y 176), so it is still not the only unknown thing on screen; the lever-and-torch pair repeats forest-0-lever with no role. D67 kept this course one room when it appended the route and guard rooms. Splitting this room, meeting the RedMob alone first and removing the torch lever are tracked by [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375). |
| Status | D72 audit 2026-11-16 (redone after #187/#104): change (split this room itself so the RedMob is met alone first, handle the lever-and-torch pair, ids 12/13, and revisit D58), [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375) |
| Decisions | D58, D67, D72 |

### forest-1-route

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 2 of 4 (832 px) |
| Role | variation |
| Purpose | One lever, two supports in opposite states: choose the optional ladder or the mandatory lift, and pay a one-shot step for a refill. |
| Introduces | bat-enemy |
| Develops | crumble-support: a one-shot step whose loss closes the optional refill until Restart Level; fireball-refill: the bat gives a reason to spend a charge, so the refill is worth something |
| Combines | ladder + running-platform: one lever lowers the ladder and stops the lift, so the two routes exclude each other |
| Repeats | lever |
| Mastered route | 11.02 s required, 18.32 s with the optional refill (`Forest1RouteTest`); target about 20 s |
| First learning | not measured ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)) |
| Retry cost | up to about 11 s (a death at the bat or the lift replays the room) |
| Optional | the fireball refill on the pad over the one-shot step |
| D26 | every state reached by supported actions reaches the exit without Restart Level (D67) |
| Hints | none |
| Status | D72 audit 2026-11-16: keep |
| Decisions | D67 |

### forest-1-guard

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 3 of 4 (768 px) |
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

### forest-1-tree

| Field | Value |
| --- | --- |
| Level | forest-1, play order 2, room 4 of 4 (640 px, one screen) |
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
| Level | forest-2, play order 3, room 1 of 3 |
| Role | combination |
| Purpose | New enemies and ground: stomp, quicksand and wall jumps between flyers, with the first coins and ingredient. |
| Introduces | yellowmob, quicksand, wall-jump, coin, ingredient, carry-torch |
| Develops | touch-explosive: the Bomb on flat ground between enemies (a reskin of the stump, so the enemies around it are what is new) |
| Combines | - |
| Repeats | redmob, bat-enemy, carry-throw, lever, ladder |
| Mastered route | 16.27 s required, 20.00 s all coins (`RoomReplayTest`); target about 20 s, coins about 30 s |
| First learning | not measured (#135) |
| Retry cost | up to about 16 s, the longest in the Forest |
| Optional | coins, the Bat ingredient, the practice stone, torch, light and ladder near the entrance |
| D26 | no one-shot support on the required route |
| Hints | live controls label only |
| Overload reason | Six introductions, and the practice objects of D24/#95 repeat forest-0 lessons. The D72 audit (2026-11-16) proposes removing the repeats and splitting the room in the 64 px gap between the Bomb's blast (ends about x 696) and the second YellowMob's reach (from x 752); x 640, the first estimate, lies inside the blast; tracked by [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376). |
| Status | D72 audit 2026-11-16: change (remove the practice stone, light, ladder and the carryable torch; split the room in the x 696-752 gap), [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376) |
| Decisions | D24, D72 |

### forest-canopy

| Field | Value |
| --- | --- |
| Level | forest-2, play order 3, room 2 of 3 |
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
| Level | forest-2, play order 3, room 3 of 3 |
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
