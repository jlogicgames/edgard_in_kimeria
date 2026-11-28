---
name: kimeria-particles
description: Use for any task that adds or changes particles or VFX in Edgard in Kimeria - dust, sparks, smoke, blood, grit, embers. Names the project's own allocation-free classes KimeriaParticleSystem and KimeriaBloodSystem (not libGDX's ParticleSystem or ParticleEffect), says which to use, the recipe to own and render one, how to add a kind, and the tests and guards that must pass.
---

# Kimeria particles

Project-owned. Read with [kimeria-libgdx-rules](../kimeria-libgdx-rules/SKILL.md) (coordinates, no allocation) and
[AGENTS.md](../../../AGENTS.md). An impact effect is the visible half of D75's "a hazard warns before it acts and
shows a consequence after" ([style system](../../../docs/STYLE_SYSTEM.md); the falling stone, D73, is the model;
D93: seen and heard when the hazard acts, hit or miss); its sound is
[kimeria-sound](../kimeria-sound/SKILL.md). Design, measurements and the audit are in
[DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) "Particles and the allocation guard" (issue #285); this file does not repeat them.

## Names: these are not libGDX's

- `com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem` and `effects.KimeriaBloodSystem` are this
  project's classes. They have no relation to `com.badlogic.gdx.graphics.g3d.particles.ParticleSystem` (3D) or to
  `ParticleEffect` / `ParticleEmitter` (2D), which the vendored `libgdx-particles` skill documents. The `Kimeria`
  prefix (renamed 2026-11-12) exists so an agent does not mix the two.
- Why not `ParticleEffect` (DEVELOPMENT.md, "Why a custom system and not `ParticleEffect`"): fixed struct-of-arrays storage gives zero allocation,
  exposes state to headless tests and needs no `.p` files or `flipY`; `ParticleEffect` stays in reserve for large
  authored effects.

## Which one to use

| Need | Class | Notes |
| --- | --- | --- |
| A point flying in a straight line from start to end (flame, ember, spark, smoke, dust, falling grit) | `KimeriaParticleSystem` | `spawn(kind, startX, startY, endX, endY, life, size, r, g, b)`; position is `lerp(from, to, t)`, `t = 1 - life/maxLife` |
| Ballistic drops with gravity that land on a floor and splash | `KimeriaBloodSystem` | `spray(originX, originY, direction, floorY, empowered)` (boss, 32/48 drops) and `sprayLight(...)` (12 drops, a monster killed by the sword, via `Level.mobBlood`); its own arrays, colour and gravity, so it does not fit the lerp model |

`KimeriaParticleSystem` kinds (the first three multiply alpha by a peak of 0.7):

| Kind | Blend | Size at `t` | Alpha at `t` |
| --- | --- | --- | --- |
| `KIND_GLOW` | additive | `size * (1 - 0.4 t)` (shrinks) | `0.7 * (1 - t)^2` |
| `KIND_SMOKE` | normal | `size * (1 + 1.0 t)` (grows) | `0.7 * (1 - t)` |
| `KIND_GRIT` | normal | `size` (constant) | `0.7 * (1 - t)` |
| `KIND_FLAME` | normal | `size * (1 - t)` (shrinks out) | `1` (opaque, no fade) |
| `KIND_ORBIT` | additive | constant `size` | rides an ellipse (`spawnOrbit`: centre, radii, start turn, sweep, rho from and to); ramps in and out over a quarter of its life each, full between; `render(batch, region, intensity)` scales every alpha (#552: `PortalFx`, `SoftGlowFx`, `RoomRippleFx`, D123, D124) |
| `KIND_DOME` | additive | as `KIND_GLOW`, half as tall | as `KIND_GLOW`; the upper half of a glow with its flat edge on y: a flash lying on a floor (`effects.BeetleFx`, D147) |
| `KIND_SPARK` | normal | head `size * (1 - 0.4 t)` and a streak up to 5 px back along its flight, closing as `(1 - t)^3` | `1` until `t = 0.7`, then down to `0.45`; brakes along its line (`1 - (1 - t)^4`): the false honey fungus burst (D152, `effects.SporeCloud`) |
| `KIND_LEAF` | normal | long side `size`, width flips edge-on to full; sways and tilts | `0.85`, ramped in and out (`spawnLeaf`; `effects.LeafFx`, #473) |
| `KIND_GLINT` | normal | a patch of native pixels (runs from its top-left), never moves | its own peak, in over 30 % of its life, out over 50 % (`spawnGlint`; `effects.CrownGlintFx`, D163) |

`KIND_FLAME` (#397) is for warm pixels over the sky: a fading warm colour mixes with the blue into olive or magenta, a shrinking
opaque one does not. The fireball's `effects.FireballFx` (one per `Level`) uses it for its trail, splash and sparks and `KIND_GLOW`
for its hit halos, and draws its impact ring through the existing `shockwave.frag` from fixed slots (no `ShockwaveEffect` object
per hit); it is the model for a weapon or projectile effect. Rings come from the shared `effects.RingPool` (fixed slots, per-pool colour, size and
life); the pickup's `effects.PickupSparksFx` (#402) is a small second user: sparks on `KIND_FLAME` plus a faint ring.

`effects.CrownGlintFx` (D163) is the wind's effect: light on leaf pixels as a `world.Wind` gust passes, one system per depth, a full
pool skipping the new glint. Its leaf patches are baked: after a change to a crown grid, `mid.grid`, `grove.grid`, a `leaves` box or a glint
colour run `python3 tools/wind_spots.py` (`validateWindSpots`); the card is in ART_DIRECTION.md, "Wind in the crowns". A later wind
effect reads the same `world.Wind` front.

`effects.FlightFx` (D132) is the project's sprite-flight helper (a cached sprite from A to B in screen space, a `KIND_FLAME` trail, a landing twinkle): the pickup flying to the HUD, the healer hand-over, the elixir. Use it, not a new pool, for any "something flies to where it goes" picture; it is cosmetic and cleared with the room.

The false honey fungus's `effects.SporeCloud` (D152) is `KIND_SPARK` sparks plus one `RingPool` ring.

`render` draws the normal-blend pass (smoke, grit, flame, leaves, sparks and glints) first, then the additive pass, then restores white and normal blending.

## Recipe for a new emitter

1. **One system per owner**, allocated in the owner's constructor (`Torch` owns one of 96; `Level` owns the blood
   system of 96 drops and `EnvironmentFx` the dust of 24 and the grit of 32). Never create a system, array or particle per event.
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

`KIND_GRIT` (the ceiling grit of #239, owned by `effects.EnvironmentFx` with the dust) is the
model for the next (`EnvironmentFxTest` holds its capacity tests): it added a constant, a branch in `drawSize`, the blend choice in `draw`, cases in
`KimeriaParticleSystemTest` and an `AllocationBudgetTest` scenario. To add any kind to `KimeriaParticleSystem`:

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
