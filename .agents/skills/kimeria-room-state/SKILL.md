---
name: kimeria-room-state
description: Implements and reviews Edgard in Kimeria room transitions, death rollback, collection returns, inventory retention, and level restart. Use when changing room lifecycle or the boundary between provisional pickups, secured progress, run state, and level-only saves.
---

# Kimeria room state

Work from the repository root. Resolve documentation links relative to this file;
source paths and commands below are relative to the repository root.

## Read the applicable rules

Read the [campaign design principle](../../../docs/GAME_DESIGN.md#observe-plan-and-commit-d26)
and [D26](../../../docs/DECISIONS.md#d26-observe-plan-before-committing) first. Then read
[room lifecycle](../../../docs/ROOMS.md) and D3, D6, D8 and D26 in
[design decisions](../../../docs/DECISIONS.md). For collection returns, payment or
encounter locks, read the state-policy table in [healer](../../../docs/HEALER.md).
These documents describe current defaults; an explicit requested design change
may revise them. Update the affected rule alongside the implementation.

## Locate the state owners

Under `core/src/main/java/com/jlogicsoftware/kimeria/`:
- `world/Level.java`: room activation, `keepPickups`, `secureVisit`,
  `resetCurrentRoom`, `returnForCollection`, retained visits and queued objects.
- `KimeriaGame.java`: room/level-entry coin snapshots, player replacement, fade,
  pause, restart and completion orchestration.
- `ArtifactInventory.java`: provisional/secured inventory, level-entry snapshot
  and boss elixir. `SavedProgress.java` owns persistent level progress.

Trace both the Level operation and its KimeriaGame caller; restoring objects alone
can leave the coin count, hero reference, camera or UI state from the old attempt.

## Implement a lifecycle change

For each affected state, identify its owner, scope (attempt, secured room visit,
level, run or preferences), and its treatment on death, forward exit, collection
return, Restart Level and leaving the run. Reuse the existing state-policy table
rather than inventing a universal reset method.

Preserve these distinctions unless the request changes them:
- Death discards provisional pickups and effects from this attempt. Secured
  collection identities and defeated enemies remain secured. Revisit deaths
  restore secured trap and mutable room-owner snapshots; first visits begin in
  authored state. Falling platforms also retain secured phase/timer/position/removal
  state. An optional item may become unreachable after its one-shot support is spent;
  from every reachable secured state produced by supported actions, mandatory exits,
  bosses and required tablet fragments must remain reachable without a forced Restart
  Level. The one-shot consequence must be readable before commitment.
- Collection return secures the current visit idempotently. Retain secured
  pickups, enemy progression, traps and mutable object state; rebuild the room
  graph and linked controls together so owners restore by authored ID. Restore a
  warning-phase platform with a fresh transient light, never stale particles.
  Retained enemies
  must reference the replacement hero and discard support references to old
  platforms before binding to the rebuilt traversal graph (`Enemy.revisit`).
  Inactive rooms do not advance simulation.
- Restart Level restores level-entry currency/inventory and rebuilds the level.
  It is not the operation used for collection return or room death.
- Spent inventory is distinct from collection identity: payment must not respawn
  ingredients or permit farming. Recipe payment and completion happen once.
- The boss elixir survives boss retries, ends on defeat, and clears at the other
  boundaries documented in HEALER.md. It is not part of room rollback or a save.
- Only level completion changes saved progress. Room exit, death and healer
  return do not add mid-level save fields. The one exception is narrative: the
  first death of a run saves that the healer's line was told (D81).

For fades, verify pause/resume and interruption by restart/exit; hero and camera
relocate under black and no old queued effect leaks into the new attempt.

## Verify the changed behavior

Tests are under `core/src/test/java/com/jlogicsoftware/kimeria/`.
Use `world/RoomTest.java` for lifecycle assertions and
`world/RoomReplayTest.java` for real-input routes. Add focused regression cases
for changed state boundaries, especially repeated return/death/payment rather
than only the first successful visit. Include a pass with spent one-shot supports and
secured mechanism/tool state, prove the required route remains possible if optional
content is lost, and prove Restart Level restores authored state. Do not rearm/reset
objects merely to satisfy a gate; the reset button does not make a hidden consequence fair.

Run relevant existing suites, for example:
```sh
./gradlew :core:test --tests '*RoomTest' --tests '*RoomReplayTest' --tests '*ArtifactInventoryTest'
```
Add `*BossCombatTest` for encounter changes or `*SavedProgressTest` for persistence
changes. Choose checks based on the actual edit. For visual transitions, also
check the running game; headless tests cannot establish that the fade or camera
looks correct. Report observed results and remaining manual checks separately.
