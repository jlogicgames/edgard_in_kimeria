# Edgard in Kimeria

**[Play in your browser](https://jlogicgames.github.io/edgard_in_kimeria_java/)** — the
web build, deployed automatically from `main` (see
[`.github/workflows/deploy-web.yml`](.github/workflows/deploy-web.yml)).

A 2D platformer, ported to Java and [libGDX](https://libgdx.com/) from the project's
Flutter/Flame version (with reference to its separate Rust/Bevy version for feature parity —
full menu system, localization, gamepad support, dev hotkeys).

Controls are three action buttons — **Jump**, **Shoot** and a context-sensitive
**Attack/Interact** — plus movement. **A / D** move, **J** jump, **K** attack (or
interact while the player is inside a trigger zone — walls, torches, escalators), **L** shoot,
**Esc** pause. Gamepad: **left stick / D-pad** move, **South** jump, **West**/**East**
attack/interact, **North** shoot, **Start** pause. Touch devices get an on-screen stick and the
same three buttons (stick left, buttons right); the Attack/Interact button relabels itself inside
a trigger zone. **Left-handed mode** (Options) mirrors all three. Touch: stick right, buttons
left. Keyboard: **arrows** move, **Z** jump, **X** attack/interact, **C** shoot (the two key sets
are disjoint, so the arrows work only in this mode). Gamepad: **right stick** moves and the
D-pad takes the face buttons' place (**Down** jump, **Left**/**Right** attack/interact, **Up**
shoot); menus and **Start** are the same in both modes. Shoot is bound everywhere but inert until the ranged attack (fireball or throwing knife) is built. Menus: **arrows/Tab** navigate, **Enter/Space/A** confirm,
**Esc/B** back (Esc/B/Start resume from the pause menu, which also offers **Restart Level**; on the desktop main menu they select
Exit; on game over they return to the main menu). Debug keys: **F1** hitbox gizmos, **F2**
invulnerability, **F3** spawn shockwave + ripple, **F4** advance level, **F5** reach a
checkpoint.

The web build opens on a single-button start screen (click, Enter or Space): browsers block audio
until the page has had a user gesture, so the menu music waits for it. Other platforms skip it.

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

- **Only one coin ripple shows at a time.** As in the Flame and Bevy versions, the ripple
  distorts the rendered scene (`screen_effects.frag`, drawn from an offscreen buffer that is
  only rendered while a ripple or the pause glitch is live), and collecting a second coin
  mid-ripple takes over from the first. The wave phase travels with time, as in Rust; Flame
  freezes it.
- **`Torch`'s particle system is a pooled-particle reimplementation**, not a port of
  dozens of independent per-particle timers driving Skia canvas draws with blur mask
  filters (`SpriteBatch` has no blur-mask equivalent). Visual language (flickering core
  flame, rising embers, drifting smoke, green magic sparkles) is kept, tuned down from an
  early pass that over-saturated to white with too many concurrent additive particles.
- **The chromatic-aberration glitch is a pause effect, not an ambient one.** It's off by
  default in the Rust version and dead code in the Flame version; here it's shown only
  while paused, on the frozen world (`screen_effects.frag`, drawn from an offscreen
  `FrameBuffer` that is only rendered while paused or while a ripple is live, so normal play
  is unchanged). The HUD and pause menu draw afterwards, unaffected.
- **Touch controls have no pause button yet.** The on-screen stick and three action buttons
  (`ui.TouchControls`) only show on devices that report a touch screen, so a touch-only player
  can't open the pause menu.
- **Fullscreen is desktop-only.** There is no mobile module in this repo yet, and the web build
  stays in its canvas, so neither shows the Display option; a future mobile backend is
  fullscreen by nature.
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
- `ui.TouchControls` — on-screen stick and Jump / Shoot / Attack-Interact buttons
- `KimeriaGame` — the whole game and its menu states in one place

A fixed-timestep physics accumulator (matching the source projects) was tried and dropped:
even at a rock-steady 60fps average, real frame durations wobble slightly around 1/60, so
the accumulator would sometimes step physics twice in one rendered frame and zero times in
the next — invisible in an FPS counter but visible as jittery movement. `Player` instead
steps physics once per rendered frame with that frame's own (clamped) delta.

### Game state, progress and saves

Gameplay rules and their rationale are in [docs/DECISIONS.md](docs/DECISIONS.md). The
parts that shape the code:

- **A run is described by `KimeriaGame.StartData`** (level index, coins; coins are not saved,
  so `Continue` starts them at 0). `startGame(data)`
  builds a player and a level from it, and `Continue` and `New Game` are just two ways of
  producing a `StartData`: `New Game` uses `StartData.newGame()`, `Continue` uses the saved one.
- **Saved progress** lives in the same libGDX `Preferences` file as settings
  (`Settings.PREFS_NAME`), under its own key in `Settings`: the next level index and nothing else
  (coins are temporary, see D8). The save is written automatically when a level is completed (no manual save), never mid-level
  (completing the last level writes level 1), and read once when the main menu is built or shown. "A save exists" is a single predicate that the main
  menu uses to decide whether to show `Continue`.
- **The main menu hides `Continue` until a save exists.** `Menu` takes a fixed list of
  `MenuItem`s today, so `MenuItem` needs an optional visibility predicate (default: always
  visible) that `Menu` honours when drawing and navigating (skipping hidden items, keeping
  the selection on a visible one). Rebuilding the list on every state change would also work but
  loses the selection, so the predicate is the intended approach.
- **`New Game` over an existing save asks for confirmation** before overwriting it, using the
  same `Menu` mechanism as the other screens (a small confirm menu state).
- **Death does not touch the save.** It resets the level in memory (see D3), with a fast path
  that bypasses the loading screen, and the save only changes on level completion.
- **Continue vs Resume:** `UiState.PAUSED` and the pause menu's *Resume* continue the live run;
  *Continue* on the main menu loads a saved run into a fresh `StartData`.

## Assets

Images, audio and Tiled maps are copied verbatim from the Flame project's `assets/`.
Fonts (`NanoPlus.ttf`, `QuestSquare.ttf`) and `main_menu.mp3`/`button_click.wav` are pulled
from the Rust version, which has them and the Flame baseline doesn't. `button_click.wav`
needed re-encoding from `WAVE_FORMAT_EXTENSIBLE` to plain PCM — libGDX's WAV decoder
rejects the former. The GLSL shaders under `assets/shaders/*.frag` are ported from the
Flame project's Flutter `FragmentProgram` shaders (already GLSL, just wrapped in Flutter's
`runtime_effect.glsl` macros) to plain desktop GLSL consumed via libGDX's `ShaderProgram`.

### Known JVM / Gradle startup warnings

`./gradlew run` and the packaged build start with no JVM `WARNING:` lines on JDK 25+:
`lwjgl3/build.gradle.kts` passes `--enable-native-access=ALL-UNNAMED` (libGDX's
`SharedLibraryLoader` calls the restricted `System::load`) and selects LWJGL's FFM memory
backend instead of its `sun.misc.Unsafe` one. Two cases remain, both outside our scripts:

- **Running the fat jar with `java -jar`** (`buildDesktop`) still prints LWJGL's
  `sun.misc.Unsafe::objectFieldOffset` warning on JDK 25. The jar's flattened layout drops
  LWJGL's multi-release FFM classes, so the backend can't be switched there. The warning is
  harmless; the jar's manifest already carries `Enable-Native-Access`.
- **`packageDesktop`** prints one Gradle deprecation ("Invocation of Task.project at
  execution time") from `org.beryx.runtime` 2.0.1, its latest release. It needs a plugin
  fix upstream.
