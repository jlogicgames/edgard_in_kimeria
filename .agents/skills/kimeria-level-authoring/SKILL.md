---
name: kimeria-level-authoring
description: Authors and validates Edgard in Kimeria Tiled TMX rooms, spawn points, collision geometry, trigger links, and playable routes. Use when adding a level, changing map geometry or object placement, or measuring room replay budgets.
---

# Kimeria level authoring

Work from the repository root. Resolve documentation links relative to this file;
source paths and commands below are relative to the repository root.

## Read the map contract

Read [room authoring and replay protocol](../../../docs/ROOMS.md), including the
latest measurement section: older measurements may describe removed mechanics.
Read D3, D5, D7 and D10 in [design decisions](../../../docs/DECISIONS.md).
For a boss chamber read [boss](../../../docs/BOSS.md); for collection-route
changes read [healer](../../../docs/HEALER.md).

Inspect the target `assets/tiles/*.tmx` and referenced TSX files before editing.
The custom loader is `tiled/TiledMapData.java`; `world/RoomLayout.java` validates
markers and ownership and `world/Level.java` maps object types to game entities,
under `core/src/main/java/com/jlogicsoftware/kimeria/`. Check supported types and
properties there rather than assuming libGDX's standard tiled loader behavior.

## Author with the existing coordinate system

- Coordinates are top-left, Y-down, including object-layer offsets. Do not flip
  rows or convert maps to Y-up. Preserve unrelated author art and geometry.
- The `Rooms` object layer orders rooms by its Room object list, not X or ID.
  Each named Room rectangle has one same-named RoomStart point. Every non-final
  room has one same-named RoomExit rectangle; the final room has none.
- RoomStart places the hero's top-left. Keep a safe 48-by-48 area and supporting
  ground. Bounds are positive, inside the map and non-overlapping; entrances
  and exits fit inside their own room.
- An absent/empty Rooms layer intentionally means one map-sized room. Keep
  diagnostics for duplicate, missing, orphaned and unknown markers.
- SpawnPoints/Collisions ownership uses the initial rectangle centre, with
  left/top inclusive and right/bottom exclusive bounds. Keep collision shapes,
  patrol routes and linked interactive objects within their owner room. Moving
  entities retain ownership. Cross-room trigger links are rejected.
- Keep existing object IDs stable when moving pickups: secured collection uses
  identity. Allocate unique IDs for additions and maintain TMX ID counters.
- Seal boundaries so players reach the intended exit instead of entering an
  inactive room. Non-boss levels finish at Checkpoint; boss maps complete through
  their protected victory flow, not a bypass checkpoint.

## Demonstrate a route

Use `core/src/test/java/com/jlogicsoftware/kimeria/world/RoomReplayTest.java` as
an executable reference. Exercise production Level/Player updates with normal
input and real hazards enabled. Inspect what each route actually asserts: the
existing `measure` collection routes assert every room coin, not every artifact.
For an all-pickups claim, also assert the authored artifact identities. The
collection-return test seeds secured kills/coins/traps directly before exercising
real movement; it is not evidence of a fresh all-pickups route through live enemies.
Adapt route input when geometry changes; do not
teleport past a required obstacle or disable hazards to meet a budget.

Current authoring targets are 20 seconds for a mastered required route and
30 seconds for an optional all-pickups route, not player time limits. Include
waiting, combat, interactions and backtracking in real active play time; exclude
pauses, loading, respawn presentation and room fades per ROOMS.md. Measure a
fresh successful attempt plus two retries; for collection-return edits also
verify repeated forward passes without duplicate pickups or blocked traversal.

A slow required segment remains required. Rework or split it rather than
reclassifying it as optional or weakening a gate. Boss timings need actual
fight evidence; traversal-only measurements do not prove the boss budget.

## Verify and record

Run the map/lifecycle and route suites relevant to the edit:
```sh
./gradlew :core:test --tests '*RoomTest' --tests '*RoomReplayTest'
```
For new object behavior, run/add its focused test. Use the running game to check
camera bounds, exits, hazard readability, collision alignment and traversal;
headless timing does not prove comfort, discoverability or fairness.

When geometry or gameplay timing changes, update the affected route measurements
and protocol notes in ROOMS.md with the map revision/hash or other reproducible
identity, conditions, times and evidence limits. Keep historical results clearly
separate from current results. Do not claim a human playtest from scripted input.
