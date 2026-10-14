# Rooms in Tiled

A level is one TMX map containing an ordered chain of rooms. `forest` now has two
rooms: the original 1280 px course and a shorter 672 px forest course with an
ascending optional coin route, a RedMob, and a vertical bat. The second course
ends at its relocated Checkpoint and no longer includes a bomb or quicksand.
`forest-1` remains an unmarked, single-room level. Replay budgets and measured
routes for #70 are recorded below.

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
  `Checkpoint` in `SpawnPoints` there to finish the level.

Bounds must be positive, inside the map, and must not overlap. Entrances and
exit rectangles must fit inside their room. Duplicate, missing, orphaned, and
unknown room markers fail loading with a diagnostic instead of changing gameplay
silently. An entirely absent or empty Rooms layer means one room covering the
map, using the existing Player spawn.

SpawnPoints and Collisions objects belong to the room containing their initial
rectangle's centre (left/top inclusive, right/bottom exclusive). Keep collision
shapes, patrol routes, and interactive objects within their room. Moving objects
retain this ownership. Trigger targets must be in the same room; cross-room
links are rejected. Atmosphere (sky, rain, fog, fireflies) is decorative and
continues across retries. Rooms may span several screens.

## Transition and retry

Room traversal is forward only. Seal physical boundaries with terrain so the
player reaches the exit trigger rather than walking into an inactive room.
The camera stays inside the current room; if the room is smaller than the
viewport, it is centred on that axis.

Entering RoomExit freezes the world, hero animation, and movement input.
The entire scene fades to black over **0.25 seconds**, then the next room and a
fresh idle hero are placed at its entrance and the camera relocates while the
screen is fully black. A fully black frame is always rendered, even after a
long frame. The scene fades in over **0.25 seconds**, then play resumes. There
is no hero disappearance/appearance animation, inter-room camera pan, loading
screen, or level-save write. Timing uses real time, independent of bullet time.
Pause freezes the fade; Resume continues it. Restart Level and leaving gameplay
cancel the transition.

Death keeps the existing slow-motion/fast eased camera return and respawn
animation. It rebuilds only the current room's enemies, items, bombs, triggers,
mutable walls, moving platforms, and falling platforms from the already-loaded
TMX descriptions, discarding that attempt's queued effects. Passed rooms retain
their runtime objects and are frozen, not recreated by a later death. Future
rooms start their first attempt only when entered.

Collected object IDs remain provisional until leaving the room or reaching the
level Checkpoint; death before then restores those objects. The current coin
counter likewise rolls back to its room-entry value, preserving previous rooms'
coins. This supplies room-level collection bookkeeping for future artifacts;
artifact graphics, level outcomes, and persistent artifact saves are not yet
implemented (D6). Restart Level resets all rooms and the level-entry coin count.
Saved progress remains per level (D8). A dev-mode TMX reload keeps the player
in the room containing its position; if that position was removed from the map,
it falls back to the first entrance.

## Replay budget (#70)

The target for a **mastered successful required route is at most 20 seconds**.
An optional route collecting all existing room pickups has a separate **30 second**
budget. These are level-authoring targets, not player deadlines: exploring,
learning, and taking longer never cause a failure or penalty. A room may span
several screens. Split or rework a room when a demonstrated route cannot meet
its budget; do not exempt a long required segment by calling it optional.

Measure from the first frame in which the player can act at the entrance to
first overlap with RoomExit or the final Checkpoint. Include deliberate waiting,
combat, interaction, backtracking, and bat-induced slow motion in **real elapsed
play time**. Exclude loading, pauses, the death/respawn presentation before input
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
coins. The first level has no collectables, so it needs no separate detour.

| Level / room | Width | Required route | All room pickups | Result |
| --- | ---: | ---: | ---: | --- |
| `forest-1` / unmarked room | 640 px | 5.32 s | None | Within 20 s |
| `forest` / `forest-entry` | 1280 px | 17.60 s | 20.27 s | Within 20 / 30 s |
| `forest` / `forest-canopy` | 672 px | 5.17 s | 5.47 s | Within 20 / 30 s |

The revised canopy is 608 px shorter than its previous 1280 px layout (47.5%).
Its required route has no bomb or quicksand; the ascending platforms and their
optional pickups remain. The first two rooms already meet the budgets and need
no additional split for replay cost. Teaching and threat readability still have
their own review in #73/#75.

The required routes run forward, jumping obstacles and hazards, using normal
sword attacks near grounded enemies and wall jumps at tall walls. In `forest-1`,
K activates the wall trigger before reaching the level exit. The forest-entry
collection route waits 40 frames (0.67 s, included in its time), takes the coin
behind the entrance, follows the raised route to the upper coin, then returns
from about x=400 to x=330 for the ground coin before continuing to RoomExit.
The canopy collection route follows the ascending platforms before dropping
back to the ground coin and Checkpoint. The exact press thresholds and route
control are executable in `RoomReplayTest`; they are a reference successful
route, not a claim that beginners will finish at these speeds.

Reproduce all five measurements with:

```sh
./gradlew :core:test --tests '*RoomReplayTest'
```

These are **headless gameplay measurements**, not a recorded human playtest.
Texture/font rendering and camera drawing are omitted; an alias of the exact
`forest-1.tmx` file skips only its decorative sky/rain, which have no collision,
AI, or movement role. Map contents and all gameplay objects are retained.
This measures the replay cost at 60 fps; visual readability, input comfort, and
behaviour at other frame rates require the full playtest in #75. Rerun these
routes and record new timings whenever map geometry or gameplay timing changes.

Measured map SHA-256 values:

- `forest.tmx`: `bf227d733af41d97f575b1d16bcebb6cf408590b26f19510942ffc19f2b88f96`
- `forest-1.tmx`: `1ae0a0ce7b92582010462d55b7688385974e831ae34de904a5f542249a02206e`

## Manual checks

Start New Game and use F4 to reach `forest` (the second level); F2 optionally
makes the traversal invulnerable. Walk through the original room to its far-right
exit and verify fade out, a black midpoint, and fade in at the second entrance,
with an idle hero and no camera travel. Walk the ascending platforms in room two
and check its optional coins. Turn F2 off, collect a coin and die: the hero must
return to room two, its coin and hazards must reset, and room one's coin count
must remain. Repeat a death, then finish at the far-right Checkpoint. Also pause
mid-fade, Resume, Restart Level, and run `forest-1` without room markers.
