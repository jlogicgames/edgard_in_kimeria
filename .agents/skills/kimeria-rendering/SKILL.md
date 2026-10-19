---
name: kimeria-rendering
description: Implements and diagnoses Edgard in Kimeria rendering, HUD/text orientation, shaders, UV coordinates, post-processing and viewport state. Use for visual bugs or effect changes involving the Y-down world, Y-up text, SpriteBatch or desktop/web rendering differences.
---

# Kimeria rendering

Work from the repository root. Documentation links are relative to this file;
source paths and commands are relative to the repository root.

## Establish the rendering path

Read Architecture, Assets and Deviations in [README](../../../README.md).
For ground-aligned explosions read the render-check follow-ups in
[rooms](../../../docs/ROOMS.md); for paused dialogue read
[healer](../../../docs/HEALER.md). Check later corrections rather than treating
an older successful shader-only check as final gameplay evidence.

Under `core/src/main/java/com/jlogicsoftware/kimeria/`, trace `KimeriaGame.java`
(world, UI, post-process and resize), `Assets.java`, `effects/Shaders.java` and the
affected effect or UI renderer. Inspect real textures/sprite sheets before
changing frame dimensions or orientation. Prefer existing art unless replacing
it is part of the request.

## Keep coordinate and state contracts intact

- World, map and ordinary screen geometry use top-left Y-down coordinates.
  Assets.region already flips regions vertically. Do not add a second flip or
  migrate physics/map coordinates to fix an isolated upside-down image.
- BitmapFont glyph UVs are baked independently of the page TextureRegion. Text
  uses a separate Y-up projection, converting its position from the top when
  necessary. Follow `ui/Overlay.java`, `ui/Hud.java` and `ui/TouchControls.java`.
- Copy the old projection with `.cpy()` before switching it: SpriteBatch mutates
  its own matrix, so retaining its reference cannot restore the previous value.
  Restore changed shader, color, blend and projection state at pass boundaries.
- Shaders.whiteQuad explicitly restores full 0..1 UVs with the Y-down flip. The
  default 1x1 TextureRegion inset is 0.25..0.75 and breaks procedural coordinates;
  set bounds directly rather than reapplying setRegion's inset.
- The scene-buffer texture is drawn without an extra flip in the existing
  Y-down pipeline. After FrameBuffer.end, reapply the UI viewport: the GL viewport
  has returned to the whole window. Size buffers to the viewport in backbuffer
  pixels, recreate on size changes and dispose the old allocation.
- Keep the Pixmap-backed FrameBuffer workaround unless replacement is verified
  in the TeaVM development build as well: the stock null-data texture allocation
  previously failed there, even when other builds worked.
- Shared shader uniforms must be set for every use, including disabled effects.
  A prior frame's ripple or intensity must not leak into another draw.
- Pause freezes simulation, not the presentation pass. Gameplay pause processes
  the frozen scene; healer pause processes the exact dialogue, never the world.
  HUD/menu ordering must preserve the crisp overlays described in README.
  Screen shake is a bounded render offset, not a change to physics or AI targets.

Explicit visual-design changes can revise these defaults; update relevant docs
and verify the actual changed pass rather than treating the rules as immutable.

## Verify through the real renderer

Use focused existing tests under `core/src/test/java/com/jlogicsoftware/kimeria/`,
for example `effects/ShadersTest.java` for quad UVs and `entity/items/BombTest.java`
for explosion timing/color restoration:
```sh
./gradlew :core:test --tests '*ShadersTest' --tests '*BombTest'
```
Select additional tests only for affected behavior. Shader compilation or numeric
sampling alone cannot establish correct SpriteBatch UVs or in-world placement.

For a visual fix, capture the actual game with the affected shader, texture and
camera at representative times. Check orientation, ground alignment, transparent
edges, expiration, layering and residual state in the next draw. For viewport
changes, check resize, letterboxing and high-DPI rendering. For shader/backend
changes, check desktop and the relevant browser build; `buildWeb` alone does not
exercise browser GL behavior. Fullscreen toggles are deferred outside drawing;
recreate buffers before beginning the scene framebuffer/batch pass, as
`ensureSceneBuffer` currently does inside render. Avoid resizing/recreating a
framebuffer while that buffer or its drawing batch is active.

Describe what was visually observed and what remains untested. Keep temporary
preview hooks/captures out of shipped code and remove temporary resources created
for verification. Do not label a headless test as a rendering check.
