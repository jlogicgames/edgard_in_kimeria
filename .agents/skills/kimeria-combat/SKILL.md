---
name: kimeria-combat
description: Implements and reviews Edgard in Kimeria enemies, melee attacks, boss state transitions, hitboxes, stomp behavior, and combat telegraphs. Use when adding an enemy or boss, changing attack timing or damage, or fixing contact and same-frame combat resolution.
---

# Kimeria combat

Work from the repository root. Resolve documentation links relative to this file;
source paths and commands below are relative to the repository root.

## Read rules before tuning

Read D2, D4, D5, D7 and D10 in [design decisions](../../../docs/DECISIONS.md).
For the forest boss, also read D13 and [boss states and validation](../../../docs/BOSS.md).
Read [healer](../../../docs/HEALER.md) only when changing recipe/elixir integration.
Use current docs and code for timing values and sprite rows; historical examples
are not a balance specification for every future enemy. Explicitly requested
mechanic changes may revise these rules and their documentation.

Implementation is under `core/src/main/java/com/jlogicsoftware/kimeria/`:
`entity/Actor.java`, `entity/Hitbox.java`, `entity/enemy/{Enemy,RedMob,Bat,Boss}.java`,
`entity/player/Player.java`, `physics/{CollideBody,CollisionUtils}.java` and
`world/Level.java`. Combat ordering lives across Player, enemy and Level updates.

## Keep visible contact consistent with geometry

- Contact, sword, stomp and aggro use facing-aware hitbox bounds, not sprite
  rectangles. Distinguish the generous enemy body from the lethal hurtbox;
  harmful contact must not extend beyond visible art. Inspect sprite sheets
  before selecting frames or authoring attack boxes.
- Author boxes for the right-facing sprite. Mirror art and attack geometry about
  the body hitbox centre using Actor's existing helpers. Turning must not move
  the collision body into a wall.
- Ordinary enemy stomp requires downward motion and previous-frame feet above
  the enemy top. Side/underside contacts are not stomps. The existing sword
  attack is grounded-only; do not assume it is an airborne escape option when
  evaluating fairness. Dying enemies are harmless.
  The forest boss is an explicit exception: solid harmless body, safe top bounce,
  sword-only energy damage, lethal axe only during active strike frames.
- One-way platforms resolve landing vertically and never push actors sideways.
  Verify combat near walls/platforms as well as in open space.

## Model an attack and its resolution

Identify wind-up, active strike, recovery and allowed interruption transitions.
Start melee wind-up from the eventual attack collider's overlap, so an unrelated
trigger radius cannot drift away from its real reach. Harm begins on active frames;
warning art alone must not damage. Check both facings and relevant frame boundaries.

For the existing forest boss preserve, unless the request revises the design:
- A player swing damages this boss once. A normal approach/attack/recovery hit
  interrupts into recoil/counter; hits during protected counter states still
  damage without restarting the timer or recursively scheduling another counter.
- A same-frame interruption wins over a normal strike and its pending shake.
  Lethal sword damage cancels remaining boss attack/body damage; a hero already
  killed by another hazard cannot deliver a posthumous hit.
- The consumed elixir multiplies sword damage, not boss energy. Its scope and
  retry/reset lifetime follow HEALER.md; artifacts alone grant no automatic bonus.
- Feedback fires on a landed hit, including lethal hits. Camera shake changes
  render framing only and remains within room limits. Clear old queued damage
  and effects at the documented pause/death/transition boundaries.
- Victory disables hero control and damaging interactions, removes the dead boss
  after its animation, and completes/saves once after the protected exit walk.
  Checkpoint/F5 cannot bypass a boss map; F4 remains a separate developer skip.

## Verify behavior and readability

Tests are under `core/src/test/java/com/jlogicsoftware/kimeria/`. Select suites
for changed behavior: `world/BossCombatTest.java`, `entity/enemy/StompTest.java`,
`entity/enemy/EnemyTurnTest.java`, `entity/HitboxBoundsTest.java`,
`physics/ShapeOverlapTest.java` and `physics/OneWayPlatformTest.java`.
For example, for a boss edit:
```sh
./gradlew :core:test --tests '*BossCombatTest' --tests '*ArtifactInventoryTest'
```

Add regression cases for the changed interaction: strike start/end, last-frame
interrupt, repeated hits during counter, lethal trade, both facings and retry.
Timing-sensitive escape checks should include 30/60/120 steps per second using
actual player attack recovery and input rather than moving the hero artificially.
Run route checks when movement or required combat duration changes.

Use F1 in the running game for collider alignment. Check whether warning,
escape opportunity and attack art are readable at gameplay scale. Automated
state/collision assertions do not prove a fair fight or teachability; report
human-playtest work still needed without claiming it passed.

## Named projectile parameters

Use `ProjectileStep` for shared projectile time limits and each entity's body-size
constant for collision, placement and spawning. Keep launch speed, gravity and
lifespan scoped to their relevant entity; artwork dimensions remain in `SpriteSheets`.
Follow [parameter ownership](../../../docs/NAMED_PARAMETERS.md) and run the
named-parameter check when adding or changing combat code.
