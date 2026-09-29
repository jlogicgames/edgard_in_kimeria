# Edgard in Kimeria

**[Play in your browser](https://jlogicgames.github.io/edgard_in_kimeria_java/)** — the
web build, deployed automatically from `main` (see
[`.github/workflows/deploy-web.yml`](.github/workflows/deploy-web.yml)).

A 2D platformer, ported to Java and [libGDX](https://libgdx.com/) from the project's
Flutter/Flame version (with reference to its separate Rust/Bevy version for feature parity —
full menu system, localization, gamepad support, dev hotkeys).

Controls: **WASD / arrows** move, **J** jump, **K** attack, **L** interact, **Esc** pause.
Gamepad: **left stick / D-pad** move, **South** jump, **West**/**East** attack, **North**
interact, **Start** pause. Menus: **arrows/Tab** navigate, **Enter/Space/A** confirm,
**Esc/B** back (Esc/B/Start resume from the pause menu; on the main menu they select
Exit; on game over they return to the main menu). Debug keys: **F1** hitbox gizmos, **F2**
invulnerability, **F3** spawn shockwave + ripple, **F4** advance level, **F5** reach a
checkpoint.

## Building and running

```sh
./gradlew run              # builds and launches the desktop build
./gradlew buildDesktop     # builds a runnable fat jar at lwjgl3/build/libs/ (needs a local JRE)
./gradlew packageDesktop   # builds a native installer at lwjgl3/build/jpackage/ (bundles its own JRE)

./gradlew runWeb     # web build, dev server with auto-reload
./gradlew buildWeb   # static, minified output at web/build/dist/js/release/webapp/
```

(`./gradlew` is the Gradle wrapper committed to this repo — no separate Gradle install
needed. `run`/`buildDesktop`/`packageDesktop`/`runWeb`/`buildWeb` are root-level aliases
for the underlying per-module tasks, so no module path or plugin-generated task name
needs memorizing. `packageDesktop` uses the
[badass-runtime](https://github.com/beryx/badass-runtime-plugin) plugin's `jpackage`
integration to produce a native, self-contained build: `.exe`/`.msi` on Windows,
`.app`/`.dmg` on macOS, `.deb`/`.rpm` on Linux — no local Java install needed to play it.
jpackage can only target the OS it runs on, so `.github/workflows/deploy-desktop.yml`
builds all three on a Windows/macOS/Linux matrix. The web build uses the
[gdx-teavm](https://github.com/xpenatan/gdx-teavm) backend, which compiles ordinary JVM
bytecode to JavaScript via [TeaVM](https://teavm.org/) — see `web/build.gradle.kts` and
`web/src/main/java/.../WebLauncher.java`. `.github/workflows/deploy-web.yml` builds and
deploys the release output to GitHub Pages on every push to `main`.)

### Troubleshooting: window fails to open with a `Checks.check` NullPointerException

```
Exception in thread "main" java.lang.NullPointerException
	at org.lwjgl.system.Checks.check(Checks.java:188)
	at org.lwjgl.glfw.GLFW.nglfwGetMonitorPos(...)
```

Two different causes produce this same crash:

1. **The display is asleep or locked** when the game launches — GLFW can't enumerate
   monitors in that state. Wake the screen and re-run.
2. **LWJGL's bundled GLFW native fails to enumerate monitors** even on an awake display,
   observed on very new macOS releases; not specific to this game. Two mitigations are
   already applied in `lwjgl3/build.gradle.kts`: forcing a newer LWJGL (3.4.3) via a
   dependency resolution override, and, if a system GLFW is installed, pointing LWJGL at it
   instead of its bundled copy:

   ```sh
   brew install glfw
   ```

   The `run` task auto-detects `/opt/homebrew/lib/libglfw.dylib` (or the Intel Homebrew
   path) and passes `-Dorg.lwjgl.glfw.libname=...` when present.

## Feature parity

- [x] Player movement, jump, gravity, coyote time, quicksand slowdown, wall clamber/jump
- [x] Tile collision — solid, one-way platform, wall, quicksand
- [x] Tilemap rendering from the unmodified `.tmx`/`.tsx`, with viewport culling
- [x] Bat, Yellow mob, Red mob — patrol/chase/attack, bullet time near a Bat
- [x] Sword attack killing enemies
- [x] Collectables (coin + ripple, heart + shockwave), Bomb (explosion, kills player)
- [x] Falling platform — warning torch, delay, drop; carries the player down without
      losing contact
- [x] Escalator — patrol, carries the player (including vertically), trigger toggles it
- [x] Trigger → actionable wall removal and torch toggle
- [x] Checkpoint → next level, with wrap; death, respawn, life count, game over
- [x] Torch, firefly, rain ambience; fog effect
- [x] Shockwave/ripple/bomb-explosion shaders (ported to GLSL, see Assets below)
- [x] HUD, main menu, About, Options, pause menu, game over screen
- [x] Keyboard, mouse and gamepad menu navigation
- [x] English/Ukrainian localization, switchable from Options
- [x] Desktop build starts fullscreen; Options has a Display toggle to opt into a windowed
      1280x720 window, remembered across launches (`~/.prefs/edgard-in-kimeria`)
- [x] Main-menu music with fade in/out, button click/hover sounds, gameplay SFX
- [x] Dev hotkeys (F1–F5, see Controls above)
- [x] Chromatic-aberration glitch post-process — applied to the frozen world while paused
      (the pause menu and HUD stay crisp); see Deviations below

## Deviations from the source projects

- **The coin-pickup ripple no longer distorts the rendered background.** The Flame
  original captures the whole scene through a global shader and displaces its pixels;
  reproducing that needs an offscreen framebuffer capture-and-redraw per ripple. Since
  it's a one-off cosmetic flourish, it's the same expanding-ring shader as the heart
  pickup's shockwave, tinted gold, instead.
- **`Torch`'s particle system is a pooled-particle reimplementation**, not a port of
  dozens of independent per-particle timers driving Skia canvas draws with blur mask
  filters (`SpriteBatch` has no blur-mask equivalent). Visual language (flickering core
  flame, rising embers, drifting smoke, green magic sparkles) is kept, tuned down from an
  early pass that over-saturated to white with too many concurrent additive particles.
- **The chromatic-aberration glitch is a pause effect, not an ambient one.** It's off by
  default in the Rust version and dead code in the Flame version; here it's shown only
  while paused, on the frozen world (`chroma_glitch.frag`, drawn from an offscreen
  `FrameBuffer` that is only rendered while paused, so normal play is unchanged). The HUD
  and pause menu draw afterwards, unaffected.
- **No touch controls** (on-screen joystick/jump button) — present in the Rust version,
  out of scope for a desktop-first port.
- **Fullscreen is desktop-only.** There is no mobile module in this repo yet (see "No touch
  controls" above), and the web build stays in its canvas, so neither shows the Display
  option; a future mobile backend is fullscreen by nature.
- **Menus are hand-drawn immediate-mode UI**, not Scene2D, to avoid pulling in a full
  Scene2D skin for a handful of simple screens.

## Architecture

The whole game renders through a **Y-down** `OrthographicCamera`
(`camera.setToOrtho(true, ...)`, see `KimeriaGame`) on purpose: every coordinate in the
source projects, and in the raw `.tmx` level files, has (0,0) at the top-left with Y
growing downward. Matching that convention meant the ported physics, collision and spawn
code (`Player`, `CollideBody`, `Level`) could be translated close to line-for-line instead
of flipping signs throughout. The consequence: every loaded `TextureRegion` (see `Assets`)
is pre-flipped vertically to compensate, since libGDX's default texture orientation assumes
a Y-up camera. Text is the exception — a `BitmapFont`'s glyph quads are built from UVs
baked into each `Glyph` at generation time, not from its page `TextureRegion`, so flipping
the region has no effect on it; text is instead drawn through a separate, plain Y-up
projection sized to the same logical resolution (see `Overlay`'s and `Hud`'s class docs).

Level data (`.tmx`/`.tsx`) is read with a small hand-rolled parser (`tiled.TiledMapData`)
instead of libGDX's `gdx-tiled` extension, specifically to avoid that extension's
row-flipping (which assumes the same Y-up convention) and keep tile/object coordinates
identical to the source file.

Package layout:

- `entity.player.Player`, `entity.enemy.{Enemy,Bat,YellowMob,RedMob}` — actors
- `entity.items.*`, `entity.environment.*`, `entity.objects.*` — level objects
- `physics.{GravityBody,CollideBody,CollisionUtils}` — gravity and AABB collision
- `effects.*` — particle/shader effects
- `world.Level` — loads a `.tmx`, spawns everything, owns the per-frame update/render pass
- `ui.*` — HUD, menus, keyboard/gamepad/mouse navigation
- `localization.*` — `Msg`/`Language`, English and Ukrainian
- `input.GamepadInput` — generic Xbox-style controller mapping via `gdx-controllers`
- `KimeriaGame` — the whole game and its menu states in one place

A fixed-timestep physics accumulator (matching the source projects) was tried and dropped:
even at a rock-steady 60fps average, real frame durations wobble slightly around 1/60, so
the accumulator would sometimes step physics twice in one rendered frame and zero times in
the next — invisible in an FPS counter but visible as jittery movement. `Player` instead
steps physics once per rendered frame with that frame's own (clamped) delta.

## Assets

Images, audio and Tiled maps are copied verbatim from the Flame project's `assets/`.
Fonts (`NanoPlus.ttf`, `QuestSquare.ttf`) and `main_menu.mp3`/`button_click.wav` are pulled
from the Rust version, which has them and the Flame baseline doesn't. `button_click.wav`
needed re-encoding from `WAVE_FORMAT_EXTENSIBLE` to plain PCM — libGDX's WAV decoder
rejects the former. The GLSL shaders under `assets/shaders/*.frag` are ported from the
Flame project's Flutter `FragmentProgram` shaders (already GLSL, just wrapped in Flutter's
`runtime_effect.glsl` macros) to plain desktop GLSL consumed via libGDX's `ShaderProgram`.
