---
name: kimeria-libgdx-rules
description: Project rules for libGDX code in Edgard in Kimeria that the generic libgdx-* skills cannot know - Y-down world and Y-up text, no allocation in update/render, pooled libGDX utils, cached regions, Nearest filter, the sprite versus VFX rule, TeaVM web limits, tests and English-only text. Use before writing or reviewing any rendering, camera, particle, effect, entity update or pooling code.
---

# Kimeria libGDX rules

Use with the vendored `libgdx-*` skills (see [SOURCE.md](../SOURCE.md)); where they disagree with this
file or with [AGENTS.md](../../../AGENTS.md), this file and AGENTS.md win. This file only points to the
docs, it does not repeat them. libGDX is 1.14.2; targets are desktop and web (TeaVM), no native mobile (D61).

## Coordinates

- World Y grows **downward** (`camera.setToOrtho(true, ...)`). `batch.draw(region, x, y, w, h)` covers
  `y .. y + h` downward; to lift something subtract from Y; `Entity.position.y` is the top edge.
- `BitmapFont` text is drawn through a separate **Y-up** projection (`Hud`, `Overlay`). Never mix the two
  conventions in one calculation. Name the direction before changing a position and verify with a frame
  or a rect test. Details: [AGENTS.md](../../../AGENTS.md), [DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) "Architecture".
- Regions from `Assets` are already flipped for this camera: do not `flip` them again.

## No allocation in `update` / `render`

- No `new` in per-frame code, and none hidden: no varargs call, no `for-each` over an `Iterable`
  (iterator), no boxing (`Integer`/`Float` in collections, `String.format`, string `+` building), no
  stream or lambda that captures state, no `Arrays.asList`/`List.of`, no `new Color/Vector2/Rectangle`,
  no `new TextureRegion`, no `new GlyphLayout`.
- Reuse fields (`Vector2.set`, `Rectangle.set`, `Color.set`), index loops over libGDX `Array`/`FloatArray`/
  `IntArray`, and fixed-capacity arrays sized at construction.
- Pooling uses libGDX utils only: `DefaultPool` (supplier, no reflection) for one type, `PoolManager`
  for shared pools; `Pool.Poolable` for `reset()`. `Pools` and `ReflectionPool` are deprecated in 1.14.2.
  Pair every `obtain` with a `free`; a pool over its `max` discards without `reset`.
- Constructors, level load and `create` may allocate; call-site examples and the audit are in
  [DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) "Particles and the allocation guard".
- Measure with `ThreadMXBean.getThreadAllocatedBytes` after warm-up (HotSpot only; it does not speak
  for TeaVM). Never claim "allocation free" without a number.
- Particles and VFX: the project's own `KimeriaParticleSystem`, `KimeriaBloodSystem` and `EnvironmentFx` (not libGDX's `ParticleSystem`)
  are described in the [kimeria-particles](../kimeria-particles/SKILL.md) skill.

## Rendering and art

- Cache regions and textures (`SoftDot.region()`, `Shaders.whiteQuad()` return one instance; `Assets`
  caches textures). Never create a texture, region or shader per frame.
- World art is **sprites**; particles and shaders are VFX for time-varying effects only. A world object
  never becomes a primitive or procedural drawing. Rule, sizes and the allowed list:
  [ART_DIRECTION.md](../../../docs/ART_DIRECTION.md); the check is `tools/check_sprite_art.py`
  (`./gradlew validateSpriteArt`), the `kimeria-art-direction` and `kimeria-rendering` skills apply.
- World textures use `Nearest`; HUD/portrait art uses `Linear` per asset contract (see art direction).
- Do not move `setProjectionMatrix` or `setBlendFunction` into inner loops: each flushes the batch.

## Web build (TeaVM, not GWT)

- The generic skills discuss GWT; the web build here is TeaVM (`web/build.gradle.kts`, gdx-teavm 1.6.2).
  Do not rely on reflection, threads (`Timer`, `AsyncExecutor`) or file APIs outside `Gdx.files` in
  `core` without a web check.
- Every change to `core` that touches rendering, assets or libGDX utilities needs `./gradlew buildWeb`
  and a browser load of the changed scene before it is called done. Desktop-only runs do not show TeaVM
  failures.

## Sounds

- Sounds: see `docs/SOUND_DIRECTION.md`; sounds are generated (`tools/audio/generate_sounds.py`), never sampled.

## Tests and text

- Never weaken a threshold or tolerance silently: say so in the report and record it in
  `docs/DECISIONS.md` with the measured value and why (AGENTS.md).
- Tuning and geometry literals are named constants (`tools/check_named_parameters.py`).
- Everything in the repo is English (docs, comments, commits, issues); player-facing strings live in the
  localization bundles.
- A "not yet" or "gap" line in a design document needs an open issue in the same change (AGENTS.md).
