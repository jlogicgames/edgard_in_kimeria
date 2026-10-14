# Rooms in Tiled

A level is one TMX map containing an ordered chain of rooms. `forest` now has two
rooms: the original course and a second forest course with an ascending optional
coin route, changed enemy encounters, and a bomb later in the room. `forest-1`
remains an unmarked, single-room level.

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

## Manual checks

Start New Game and use F4 to reach `forest` (the second level); F2 optionally
makes the traversal invulnerable. Walk through the original room to its far-right
exit and verify fade out, a black midpoint, and fade in at the second entrance,
with an idle hero and no camera travel. Walk the ascending platforms in room two
and check its optional coins. Turn F2 off, collect a coin and die: the hero must
return to room two, its coin and hazards must reset, and room one's coin count
must remain. Repeat a death, then finish at the far-right Checkpoint. Also pause
mid-fade, Resume, Restart Level, and run `forest-1` without room markers.
