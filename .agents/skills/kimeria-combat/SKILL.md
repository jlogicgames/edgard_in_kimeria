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

## A new monster (D100)

Card first in [game design](../../../docs/GAME_DESIGN.md) ("Forest monster ideas"; the beetle card and the spider card are the
templates: a burst on the spot, D100, and a locked pounce with a recovery, D102), approved by the author. The notice is body
language in the monster's own frames plus a call in its own voice (no "!" mark, no shared alert sound); a lethal wind-up samples
`TrapWarnings` once (a base of its own goes into `TrapWarnings`, like `BITE_WARNING_SECONDS`); no teaching room and no hint
text. Effects are particles in one owner per `Level` (`effects.BeetleFx`, `effects.SpiderFx`); its sprite strip comes from a
grid (`tools/forest_trap_sprites.py monster:<name>`). A lethal touch that can meet a killing hit on the same frame resolves on
the monster's next frame, so the hit wins the tie (the spider's bite).

## A monster's body (D101)

Decide on the card whether the monster leaves a body (the applies-to list in [D101](../../../docs/DECISIONS.md)): a quiet kill
does, a burst or blast does not. Then:

1. **Pose.** The body frame's pose differs from every live frame, both ways round (`RemainsTest` measures the silhouette: at
   least 25 % of the pixels either covers must differ). A recoloured live frame is not a body.
2. **Tier.** Draw the kill in the monster's active `dead` frame, then cross-fade into the same frame in a decor palette: a
   second grid (`art/objects/monster/<name>-body.grid`, no ink, no rim), added to `BODY_SWEEP` in `tools/check_contrast.py`.
3. **Behaviour.** Implement `Remains`: stay in the level as a defeated enemy; react when the hero comes near (the beetle and
   the spider rock with the shared `RemainsSwing`, a big body twitches); `crumble()` for the sword, a stone and a stomp (the stomp bounces the hero), `burnUp()` for a fireball
   or a torch; weapons fly on. The `Player`, `Carryable` and `Fireball` loops already call these (the swing that killed a
   monster skips its body); do not add a hurtbox.
4. **Effects and sound.** Crumble and burn go through the shared `effects.RemainsFx` (`Level.remainsFx()`), with the monster's
   own chip colours as a static palette (`BeetleFx.SHELL_CHIPS`, `SpiderFx.SPIDER_CHIPS`); re-measure its pools in
   `RemainsFxTest` if a body adds more particles. The shared sounds `remains_crumble` and `remains_burn`; the reaction is silent.
5. **Gate.** Add a `Case` to `RemainsTest` (with its reaction); extend the monster's `AllocationBudgetTest` scenario with the body; run
   `./gradlew :core:check`.

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
  sword/fireball energy damage, lethal axe only during active strike frames.
- One-way platforms resolve landing vertically and never push actors sideways.
  Verify combat near walls/platforms as well as in open space.

## Model an attack and its resolution

Identify wind-up, active strike, recovery and allowed interruption transitions.
Start melee wind-up from the eventual attack collider's overlap, so an unrelated
trigger radius cannot drift away from its real reach. Harm begins on active frames;
warning art alone must not damage. Check both facings and relevant frame boundaries.

For the existing forest boss preserve, unless the request revises the design:
- A player swing damages this boss once. A normal approach/attack/recovery hit
  interrupts into recoil/counter. The orange HURT/WARNING/COUNTER/COUNTER_RECOVERY
  sequence is invulnerable to sword and fireball damage; rejected hits produce no
  landed-hit feedback or timer changes. Register a rejected sword overlap for that
  swing, and consume a contacting fireball once.
- A same-frame interruption wins over a normal strike and its pending shake.
  Lethal sword damage cancels remaining boss attack/body damage; a hero already
  killed by another hazard cannot deliver a posthumous hit.
- The consumed elixir multiplies sword damage, not boss energy. Its scope and
  retry/reset lifetime follow HEALER.md; fireballs always remove exactly 2 energy,
  and artifacts alone grant no automatic bonus.
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
