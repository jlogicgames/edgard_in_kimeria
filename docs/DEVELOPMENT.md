# Development and implementation

Builds, live editing, troubleshooting, feature parity and implementation details.
See [gameplay reference](GAMEPLAY.md) for controls and detailed game rules, or the
[documentation index](README.md) for design and validation records.

## Building and running

```sh
./gradlew run              # builds and launches the desktop build
./gradlew buildDesktop     # builds a runnable fat jar at lwjgl3/build/libs/ (needs a local JRE)
./gradlew packageDesktop   # builds a native installer at lwjgl3/build/jpackage/ (bundles its own JRE)

./gradlew runWeb     # web build, dev server with auto-reload
./gradlew buildWeb   # static, minified output at web/build/dist/js/release/webapp/

./gradlew runDev     # desktop, development only: edits show up in the running game
```

(`./gradlew` is the Gradle wrapper committed to this repo — no separate Gradle install
needed. `run`/`runDev`/`buildDesktop`/`packageDesktop`/`runWeb`/`buildWeb` are root-level aliases
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

### Live editing (`./gradlew runDev`)

Development only. Leave `runDev` running and edit code, images, levels or shaders: the game in
the window updates within about two seconds (the sources and `assets/` are polled once a
second). Like Flutter, it picks the least disruptive way for each change:

- **Hot reload, the run keeps going** — a method-body edit is swapped into the live classes;
  an edited image is re-uploaded into the same texture; an edited sound or shader is reloaded
  on its next use (a shader that does not compile is rejected and the old one keeps running);
  an edited `.tmx`/`.tsx` rebuilds the current level with the player left where they stood
  (enemies and items reset).
- **Hot restart, back to the main menu** — when the JVM cannot redefine the change: adding or
  removing a field, method, class member or **lambda**, or editing a fonts/music file. A game
  that crashed also restarts on the next save. **F7** forces a restart.
- A source file that does not compile prints its errors and the running game stays as it was.

Limits of hot reload: `static final` constants are inlined into their users and static
initializers do not rerun, so edits to those need a restart (F7); a texture replaced by one of
a different size keeps its old regions. Gradle is not involved after startup: `DevLauncher`
compiles `core` itself into `core/build/dev-classes`.

None of this can reach a release. It lives in the separate `dev` source set of `lwjgl3`
(`lwjgl3/src/dev`), which only `runDev` uses; the game jar, `packageDesktop` and CI build from
`main`, and `core` has no reference to it.

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
- [x] Bat, Yellow mob, Red mob — patrol/chase/attack, without proximity slow motion
- [x] Sword attack killing enemies
- [x] Collectables (coin, chicken, key + pickup ripple), artifact HUD and room rollback;
      Bomb (explosion, kills player)
- [x] Falling platform — warning torch, delay, drop; carries the player down without
      losing contact
- [x] Escalator — patrol, carries the player (including vertically), trigger toggles it
- [x] Trigger → actionable wall removal and torch toggle
- [x] Checkpoint → next level or ending; death in slow motion, then an instant room reset and respawn (no lives)
- [x] Torch, firefly and rain ambience; fog only in menus
- [x] Shockwave/ripple/bomb-explosion shaders (ported to GLSL, see Assets below)
- [x] HUD, main menu, About, Options, pause menu
- [x] Continue / New Game with overwrite confirmation and automatic level-only saves
      on desktop and web; finishing the last level shows the ending, then clears the run save
- [x] Options → Assist → Longer trap warnings: an independent, persisted toggle (off by default)
      giving falling platforms a 1.5 s warning instead of 1 s, with no penalties
- [x] Keyboard, mouse and gamepad menu navigation
- [x] English/Ukrainian/Polish/German/Belarusian localization, switchable from Options and remembered across launches
- [x] Shared local settings for language, display mode, left-handed controls and trap-warning
      assist; missing or invalid values use defaults (English on first launch, regardless of
      system locale). Settings use desktop preferences / browser local storage and keep
      level-only saves in the same file without overwriting them.
- [x] Desktop build starts fullscreen; Options has a Display toggle to opt into a windowed
      1280x720 window; the mode is remembered across launches (`~/.prefs/edgard-in-kimeria`).
      Window size and position are not persisted.
- [x] Main-menu music with fade in/out, button click/hover sounds, gameplay SFX
- [x] NES-style story intro on every new game: centered scrolling text in all five supported languages,
      black fades and a separate placeholder music loop; any fresh key, gamepad button or
      click/tap skips with a shared picture/music fade (menu holds do not skip it).
      `assets/audio/intro.wav` is an original synthesized placeholder; replace it and
      `Msg.INTRO_STORY` when revising the healer-narrated account of Kimeria and L'Kardina.
- [x] Optional forest healer: dialogue in all five supported languages, recipe checklist, paid elixir and
      repeated collection returns with retained progression and restored traversal paths
- [x] Dev hotkeys (F1–F6, see [Controls](GAMEPLAY.md#controls))
- [x] Chromatic-aberration glitch post-process — applied to the frozen gameplay or dialogue screen while paused
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
  while paused, on the frozen gameplay or healer-dialogue screen (`screen_effects.frag`, drawn from an offscreen
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
- `localization.*` — typed `Msg` keys, five UTF-8 catalogs, English fallback and strict
  validation; see [localization](LOCALIZATION.md)
- `input.GamepadInput` — generic Xbox-style controller mapping via `gdx-controllers`
- `ui.TouchControls` — on-screen stick and Jump / Shoot / Attack-Interact buttons
- `KimeriaGame` — the whole game and its menu states in one place
- `lwjgl3/src/dev` — `runDev`'s launcher, compiler, watcher and reloader (`DevHost`); a source
  set of its own that no release build includes, see "Live editing" above

A fixed-timestep physics accumulator (matching the source projects) was tried and dropped:
even at a rock-steady 60fps average, real frame durations wobble slightly around 1/60, so
the accumulator would sometimes step physics twice in one rendered frame and zero times in
the next — invisible in an FPS counter but visible as jittery movement. `Player` instead
steps physics once per rendered frame with that frame's own (clamped) delta.

### Game state, progress and saves

Gameplay rules and their rationale are in [docs/DECISIONS.md](DECISIONS.md). The
parts that shape the code:

- **A run is described by `KimeriaGame.StartData`** (level index, coins; coins are not saved,
  so `Continue` starts them at 0). `startGame(data)`
  builds a player and a level from it, and `Continue` and `New Game` are just two ways of
  producing a `StartData`: `New Game` uses `StartData.newGame()`, `Continue` uses the saved one.
- **Saved progress** lives in the same libGDX `Preferences` file as settings
  (`Settings.PREFS_NAME`), under `Settings.KEY_NEXT_LEVEL`, managed by `SavedProgress`: the next level index and nothing else
  (coins are temporary, see D8). The save is written automatically when a level is completed (no manual save), never mid-level
  (the last level shows the ending; completing its scroll deletes the run save), and read once when the main menu is built or shown. "A save exists" is a single predicate that the main
  menu uses to decide whether to show `Continue`.
- **The main menu hides `Continue` until a save exists.** `MenuItem` has an optional visibility predicate
  (default: always visible), honoured by layout, drawing, mouse and keyboard/gamepad navigation.
  Continue is preselected whenever the main menu opens with a valid save.
  Missing, malformed or out-of-range saved indices hide Continue; no index is clamped.
- **`New Game` over an existing save asks for confirmation** before overwriting it, using the
  same `Menu` mechanism as the other screens (a small confirm menu state, Cancel selected
  by default). Confirming clears the old save before the story intro; no new save exists
  until the first level is completed. Cancelling leaves progress untouched.
- **Persistence uses each platform’s libGDX `Preferences` backend** and flushes every write:
  desktop preferences are stored on disk, web preferences in browser local storage.
  `Settings` loads one snapshot, exposes typed option keys and flushes each changed setting
  through one write path. A failed settings read uses defaults; failed settings writes keep
  the choice for the current session and report the storage error. The desktop launcher uses
  the same helper with `Lwjgl3Preferences` before libGDX starts. Mobile will use its platform
  backend through `Gdx.app.getPreferences`; there is no mobile module to run or verify yet.
- **Death does not touch the save.** It resets the current room in memory (see D3), with a fast path
  that bypasses the loading screen, and the save only changes on level completion.
- **Continue vs Resume:** `UiState.PAUSED` and the pause menu's *Resume* continue the live run;
  *Continue* on the main menu loads a saved run into a fresh `StartData`.

## Assets

Images, audio and the original Tiled maps come from the Flame project's `assets/`.
`forest` has since been extended to three rooms in the same tileset.
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
