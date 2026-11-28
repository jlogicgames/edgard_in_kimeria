---
name: kimeria-combat
description: Implements and reviews Edgard in Kimeria enemies, melee attacks, boss state transitions, hitboxes, stomp behavior, and combat telegraphs. Use when adding an enemy or boss, changing attack timing or damage, or fixing contact and same-frame combat resolution.
---

# Kimeria combat

Work from the repository root. Resolve documentation links relative to this file;
source paths and commands below are relative to the repository root.

## Read rules before tuning

Read D2, D4, D5, D7 and D10 in [design decisions](../../../docs/DECISIONS.md), and D151 (the mosquito swarm, the
exception to the sword and stomp rules).
For the forest boss, also read D13, D157 and [boss states and validation](../../../docs/BOSS.md).
Read [healer](../../../docs/HEALER.md) only when changing recipe/elixir integration.
Use current docs and code for timing values and sprite rows; historical examples
are not a balance specification for every future enemy. Explicitly requested
mechanic changes may revise these rules and their documentation.

Implementation is under `core/src/main/java/com/jlogicsoftware/kimeria/`:
`entity/Actor.java`, `entity/Hitbox.java`, `entity/enemy/{Enemy,FallenEnemy,RedMob,Bat,MosquitoSwarm,Boss}.java`,
`entity/player/Player.java`, `physics/{CollideBody,CollisionUtils}.java` and
`world/Level.java`. Combat ordering lives across Player, enemy and Level updates.

## A new monster (D100)

Card first in [game design](../../../docs/GAME_DESIGN.md) ("Forest monster ideas"; the beetle card and the spider card are the
templates: a burst on the spot, D100, a locked pounce with a recovery, D102, and a ranged spit with a reload, D106), approved by
the author. The notice is body
language in the monster's own frames plus a call in its own voice (no "!" mark, no shared alert sound); a lethal wind-up samples
`TrapWarnings` once (a base of its own goes into `TrapWarnings`, like `BITE_WARNING_SECONDS`); no teaching room and no hint
text. Effects are particles in one owner per `Level` (`effects.BeetleFx`, `effects.SpiderFx`, `effects.SnailFx`); its sprite strip comes from a
grid (`tools/forest_trap_sprites.py monster:<name>`). A lethal touch that can meet a killing hit on the same frame resolves on
the monster's next frame, so the hit wins the tie (the spider's bite). A monster that leaves the floor follows the spider of D103:
an authored route (a Tiled polyline: floors, walls, a thread from a lip) checked at load (`Spider.routeProblem`), the body box by
pose (`Spider.boundsInto`), no strike from a wall, a drop box that is the fangs' swept column, and a body that falls to the floor
below when it is killed off it. A monster that shoots follows the snail of D106: it sees only along a line cut by solids
(`Level.blocksObject`), fires only when the hero touches the projectile's own band, owns its projectile (one per monster, plain
fields, swept every frame, its hit landing on the monster's next frame) and lets every weapon break it and fly on through
`Breakable` (the sword, a stone, a fireball and a torch loops call it); its load check traces every line from every point of
its range, both ways, to a solid or a floor inside the room and off every standing place (`Snail.problem`).

A monster that answers weapons differently follows the mosquito swarm of D151 through `Enemy`'s three hooks: `stopsSword`
(false: it reacts to the blade in a pass of its own, whatever the list order, and the swing goes on to the others),
`touchesDuringSwing` (true makes contact count while the hero swings; the boss uses it too) and `hitByFire` (a fireball or
a thrown torch; a stone still calls `collidedWithActor(true)`).
The defaults are the ordinary monster. The swarm never reacts to the hero, its contact lands on its next frame (so a ball
cast or a stone thrown that frame wins), it flies in silence, its lethal shape is the oval core inside the cloud it is drawn as (`hitboxOverlaps` overridden), and
its load check keeps a free way past along its whole line (`MosquitoSwarm.problem`).

## A monster's body (D101)

Decide on the card whether the monster leaves a body (the applies-to list in [D101](../../../docs/DECISIONS.md)): a quiet kill
does, a burst or blast does not (the mosquito swarm is the accepted exception: killed quietly, it leaves none, D151). Then:

1. **Pose.** The body frame's pose differs from every live frame, both ways round (`RemainsTest` measures the silhouette: at
   least 25 % of the pixels either covers must differ). A recoloured live frame is not a body.
2. **Look.** The body is the monster's dead frame in its live palette, ink and moon rim, with no recolour, tint or halo
   (D117). For a `FallenEnemy` it is a cell of the monster's own sheet; otherwise a second grid (`art/objects/monster/<name>-body.grid`, pixel for pixel the `dead` frame, its sheet in `RIM_SHEETS`
   of `tools/forest_trap_sprites.py`), added to `BODY_SWEEP` in `tools/check_contrast.py` and measured as active.
3. **Behaviour.** A monster whose one-shot death clip ends in the body extends `FallenEnemy` (the bat, the red and yellow
   mobs), which does all of this; its body is a cell of its own sheet, listed in `BODY_SWEEP`. Otherwise implement `Remains`: stay in the level as a defeated enemy; react when any actor moves through it (`Remains.moverNear`) (the beetle and
   the spider rock with the shared `RemainsSwing`, a big body twitches); `crumble()` for the sword and a stomp (the stomp bounces the hero), `burnUp()` for a fireball or a thrown torch
   (both fly on, unspent); a stone and other thrown objects pass straight through a body, which they neither crumble nor burn (D119). The `Player`, `Carryable` and `Fireball` loops already call these (the swing that killed a
   monster skips its body); do not add a hurtbox.
4. **Effects and sound.** Crumble and burn go through the shared `effects.RemainsFx` (`Level.remainsFx()`), with the monster's
   own live chip colours as a static palette (`BeetleFx.SHELL_CHIPS`, `SpiderFx.SPIDER_CHIPS`, `SnailFx.SNAIL_CHIPS`, `WolfFx.WOLF_CHIPS`, the `CHIPS` of `Bat`, `RedMob` and `YellowMob`); re-measure its pools in
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
  sword/fireball energy damage, lethal axe only during active strike frames. The
  mosquito swarm is another (D151): no stomp and no sword kill.
- One-way platforms resolve landing vertically and never push actors sideways.
  Verify combat near walls/platforms as well as in open space.

## Model an attack and its resolution

Identify wind-up, active strike, recovery and allowed interruption transitions.
Start melee wind-up from the eventual attack collider's overlap, so an unrelated
trigger radius cannot drift away from its real reach. Harm begins on active frames;
warning art alone must not damage. Check both facings and relevant frame boundaries.

For the existing forest boss preserve, unless the request revises the design:
- A player swing damages this boss once. An approach, sweep or recovery hit outside
  poise interrupts into recoil/counter. The orange HURT/WARNING/COUNTER/COUNTER_RECOVERY
  sequence is invulnerable to sword and fireball damage; rejected hits produce no
  landed-hit feedback or timer changes. Register a rejected sword overlap for that
  swing, and consume a contacting fireball once.
- **Poise (D157):** from the sweep's crouch to the end of its strike (0.1556-0.4667 s)
  and through every special (rocks, roots, blow) a hit takes its energy but causes no
  recoil or counter; one such hit per attack, later ones are rejected
  (`Boss.rejectsHits`). Do not "fix" it into an interruption.
- The specials are chosen by the hero's place on a 3 s clock, never by chance; their
  lethal areas keep off the arena's protected spans (`BossArena`); each warns for its
  own `TrapWarnings` time. Card: [BOSS.md](../../../docs/BOSS.md).
- A same-frame interruption wins over an unpoised normal strike and its pending shake.
  The specials' harm resolves after the sword, so lethal damage wins: it cancels every
  attack and the remaining body damage; a hero already killed by another hazard cannot
  deliver a posthumous hit. After a hero's death the attack under way plays on, harmless.
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
for changed behavior: `world/BossCombatTest.java`, `world/BossSpecialsTest.java` and
`world/BossFightRouteTest.java` (whole fights with real input, pinned times), `entity/enemy/StompTest.java`,
`entity/enemy/EnemyTurnTest.java`, `entity/HitboxBoundsTest.java`,
`physics/ShapeOverlapTest.java`, `physics/OneWayPlatformTest.java` and `physics/SolidBlockVerticalTest.java`
(which face a body leaves a solid block by, D10).
For example, for a boss edit:
```sh
./gradlew :core:test --tests '*Boss*' --tests '*ArtifactInventoryTest'
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
