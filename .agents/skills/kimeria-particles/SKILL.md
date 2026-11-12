---
name: kimeria-particles
description: Use for any task that adds or changes particles or VFX in Edgard in Kimeria - dust, sparks, smoke, blood, grit, embers. Names the project's own allocation-free classes KimeriaParticleSystem and KimeriaBloodSystem (not libGDX's ParticleSystem or ParticleEffect), says which to use, the recipe to own and render one, how to add a kind, and the tests and guards that must pass.
---

# Kimeria particles

Project-owned. Read with [kimeria-libgdx-rules](../kimeria-libgdx-rules/SKILL.md) (coordinates, no allocation) and
[AGENTS.md](../../../AGENTS.md). Design, measurements and the audit are in
[DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) "Particle system plan" (issue #285); this file does not repeat them.

## Names: these are not libGDX's

- `com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem` and `effects.KimeriaBloodSystem` are this
  project's classes. They have no relation to `com.badlogic.gdx.graphics.g3d.particles.ParticleSystem` (3D) or to
  `ParticleEffect` / `ParticleEmitter` (2D), which the vendored `libgdx-particles` skill documents. The `Kimeria`
  prefix (renamed 2026-11-12) exists so an agent does not mix the two.
- Why not `ParticleEffect` (plan option B, section 3): fixed struct-of-arrays storage gives zero allocation,
  exposes state to headless tests and needs no `.p` files or `flipY`; `ParticleEffect` stays in reserve for large
  authored effects.

## Which one to use

| Need | Class | Notes |
| --- | --- | --- |
| A point flying in a straight line from start to end (flame, ember, spark, smoke, dust) | `KimeriaParticleSystem` | `spawn(kind, startX, startY, endX, endY, life, size, r, g, b)`; position is `lerp(from, to, t)`, `t = 1 - life/maxLife` |
| Ballistic drops with gravity that land on a floor and splash | `KimeriaBloodSystem` | `spray(originX, originY, direction, floorY, empowered)`; its own arrays, colour and gravity, so it does not fit the lerp model |

`KimeriaParticleSystem` kinds (both multiply alpha by a peak of 0.7):

| Kind | Blend | Size at `t` | Alpha at `t` |
| --- | --- | --- | --- |
| `KIND_GLOW` | additive | `size * (1 - 0.4 t)` (shrinks) | `0.7 * (1 - t)^2` |
| `KIND_SMOKE` | normal | `size * (1 + 1.0 t)` (grows) | `0.7 * (1 - t)` |

`render` draws the normal-blend pass (smoke) first, then the additive pass, then restores white and normal blending.

## Recipe for a new emitter

1. **One system per owner**, allocated in the owner's constructor (`Torch` owns one of 96; `Level` owns the blood
   system). Never create a system, array or particle per event.
2. **Capacity from a measured peak plus headroom.** Add a capacity test that records the maximum live count over many
   seeds and frame rates (`TorchParticleCapacityTest`, `KimeriaBloodSystemCapacityTest`). A full pool drops the oldest
   particle silently, so an undersized capacity is invisible without that test.
3. **One seeded `RandomXS128` per system** (`system.random()`); never `MathUtils.random` in particles, so a seed
   reproduces a run and the golden test stays valid. Keep the random-call order stable when you edit a spawner.
4. **Spawn in `update`**, `system.update(dt)` each frame; named constants for every tuning number
   (`tools/check_named_parameters.py`).
5. **Render** with `system.render(batch, SoftDot.region())` (or `Shaders.whiteQuad()`); both return one cached region.
   Blood renders with `blood.render(batch)`.
6. **World Y grows downward**: a rising particle has an end Y smaller than its start Y; a floor is the largest Y a
   drop may reach. Name the direction before writing the sign.
7. **Pause by not updating** (no state changes while the game is paused). **Clear with the room**
   (`clear()` on room change, restore or switch-off, as `Torch.restoreState` and `Level` do) so nothing carries over.

## Adding a new kind

A third kind `KIND_GRIT` is planned for the ceiling grit of #239 and does not exist yet; update this skill when it
lands. To add any kind to `KimeriaParticleSystem`:

- Add a `KIND_*` constant, then extend `drawSize(i)`, `alpha(i)` and the blend choice in `draw` (a kind that needs
  a third blend state needs a third pass; keep one blend switch per pass and none for an empty pass).
- Add cases to `KimeriaParticleSystemTest` (curve values at `t = 0`, 0.5, 1) and keep the existing assertions.
- `ParticleGoldenTest` pins the existing spawners; never edit its numbers to pass. A new kind gets its own recorded
  scenario.
- Add an `AllocationBudgetTest` scenario with a ceiling (below) and a capacity test for the owner.

## Guards

- `AllocationBudgetTest` measures bytes per frame with `ThreadMXBean` after warm-up (HotSpot only, not TeaVM). A
  new emitter gets a scenario that runs spawn, update and render, a ceiling set at the highest of 3 runs plus about 8 %
  with the measurements in the Javadoc, and the number in the report. Never raise a ceiling to pass.
- `tools/check_allocations.py` (`./gradlew validateAllocations`) rejects `new`, varargs, for-each and similar on
  changed lines of `update*`, `render*`, `draw*` and `HOT_METHODS`; a spawn helper called from `update` is added to
  `HOT_METHODS`. An `ALLOWED` entry must say what the excluded code carries.
- AGENTS.md "No allocation in `update` and `render`" is the rule behind both.
- Also run `./gradlew buildWeb` (TeaVM) for any change under `effects/`.

## Pitfalls

- No `new`, `Color`, `Vector2`, varargs call, `String.format` or for-each in `update`/`render`; use
  `batch.setColor(r, g, b, a)` floats and restore `setColor(1, 1, 1, 1)` and the normal blend function.
- Never mutate the shared cached region (`SoftDot.region()`, `Shaders.whiteQuad()` return one instance; do not
  `flip` it or change its size or texture).
- Do not use `MathUtils.random` or a second `Random` in particle code.
- Do not confuse the Y conventions: particles are in the Y-down world; `BitmapFont` text is Y-up.
- Do not call libGDX `ParticleSystem` or `ParticleEffect` API on these classes; they do not share an interface.
