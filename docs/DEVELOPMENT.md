# Development and implementation

## Dev FPS fix and GH Pages release audit — 2026-10-23

`runDev` loads the game through DevHost rather than Lwjgl3Launcher. DevHost now
explicitly calls the `(platformTouch=false, debugMode=true)` constructor on startup
and hot restart. Previously the no-argument constructor silently selected release
mode despite the `[dev]` title. Stop/restart `runDev` once after this host update;
F7 reloads only the hosted game. An isolated real desktop window showed FPS at
[startup](references/run-dev-fps-start.png) and after a structural-source
[hot restart](references/run-dev-fps-hot-restart.png). The user's running dev process
was left intact; the isolated test sources/probe were removed after verification.

The published GH Pages workflow and successful
[run 39802877036](https://github.com/jlogicgames/edgard_in_kimeria/actions/runs/39802877036)
confirm `buildWeb`, the named release compile/build tasks, and the upload directory
`web/build/dist/js/release/webapp`. At the time, gdx-teavm 1.6.2 defaulted to
`BALANCED` optimization and `debugInformation=false`; the release target enabled
obfuscation only, F1–F6 were polled in release mode, and copied assets included
editor and host metadata. Minification alone did not remove debug functionality or
prove maximum performance.

### [Issue #153 release contract](https://github.com/jlogicgames/edgard_in_kimeria/issues/153) — 2026-10-24

The named `release` target now explicitly selects TeaVM's strongest supported
`AGGRESSIVE` optimization, retains obfuscation, and disables debug information and
source maps. Its effective compile-task values are checked before compilation.
The release entry point is `WebLauncher`; development uses a separate launcher.
`KimeriaGame` ignores F1–F6 unless it was constructed in debug mode, which remains
enabled for desktop `run`/`runDev` and the browser development target.

`./gradlew clean buildWeb` clears prior build output and verifies the exact
directory uploaded by Pages. The release build removes generated servlet
metadata, Tiled project/session files, `.DS_Store`, and the authored boss-art
README. Runtime TMX/TSX maps, all five localization catalogs, fonts, audio,
shaders, images, and the bundled Howler MIT notice remain present. The checker
rejects source maps, TeaVM debug sidecars, Java source/classes, editor metadata,
OS metadata, server/dev payloads, missing runtime sentinels, or a missing Howler
license notice. To recheck an existing build directly, run
`python3 tools/check_web_release_artifact.py`; it always validates the repository's
fixed Pages upload directory.

The clean BALANCED baseline was built from current `main` before these changes; the
clean AGGRESSIVE artifact uses the same main base after the release and debug-mode
changes. Sizes below sum individual files; gzip uses level 9 per file with
deterministic timestamps. The before/after size difference includes those code and
artifact-cleanup changes, so it does not isolate optimization-level impact.

| Build | Files | Raw total | Per-file gzip total | `app.js` raw / gzip |
| --- | ---: | ---: | ---: | ---: |
| BALANCED baseline | 80 | 3,870,521 B | 2,782,747 B | 672,177 / 235,771 B |
| AGGRESSIVE release | 76 | 4,284,018 B | 2,866,193 B | 1,089,746 / 321,210 B |

The final local Pages artifact was opened in the Codex in-app Chromium browser and
reached the title screen, main menu, and forest gameplay with terrain and HUD assets
loaded. FPS was not shown; F1, F4 and F6 did not reveal debug drawing or skip rooms.
A reliable audio playback check and comparable browser load/frame-time measurement
were not captured because performance instrumentation could not attach to the active
browser profile. No measurement is inferred from bundle size. Repeat an audio and
performance check after deployment; the deployed Pages site has not been tested by
this change.

Automated validation: `./gradlew clean buildWeb`,
`./gradlew :core:test --tests '*BossCombatTest'`, and
`python3 tools/test_web_release_artifact.py` pass. The separate desktop DevHost FPS
constructor fix remains outside issue #153.

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

`input.GameplayInput` owns per-device action/ranged fresh presses and active-device
prompts. `RangedStock` owns run/level ammunition; `Level` snapshots room stock.
`Carryable` and `Fireball` provide real handlers behind the directional dispatcher.

## Feature parity

- [x] Player movement, jump, gravity, coyote time, quicksand slowdown, wall clamber/jump
- [x] Tile collision — solid, one-way platform, wall, quicksand
- [x] Tilemap rendering from the unmodified `.tmx`/`.tsx`, with viewport culling
- [x] Bat, Yellow mob, Red mob — patrol/chase/attack, without proximity slow motion
- [x] Sword attack killing enemies
- [x] Collectables (coin, bat, mushroom + pickup ripple), artifact HUD and room rollback;
      Bomb (explosion, kills player)
- [x] Falling platform — warning torch, delay, drop; carries the player down without
      losing contact
- [x] Falling branch and falling stone (#232, D45) — zone-triggered, shared D7 warning, lethal while falling; not yet in a shipped level
- [x] Escalator — patrol, carries the player (including vertically), trigger toggles it
- [x] Directional actions (#95), one carried stone/portable torch, deliberate throwing and consumable fireballs with two authored +1 refills, capacity three, full-stock hints and a three-icon HUD; see [contract and dependencies](DIRECTIONAL_CONTROLS.md)
- [x] Trigger → actionable wall removal and torch toggle via Up + Action
- [x] Checkpoint → next level or ending; death in slow motion, then an instant room reset and respawn (no lives)
- [x] Torch, firefly and rain ambience; fog only in menus
- [x] Shockwave/ripple/bomb-explosion shaders (ported to GLSL, see Assets below)
- [x] HUD, main menu, About, Options, pause menu
- [x] Continue / New Game with overwrite confirmation and automatic level-only saves
      on desktop and web; finishing the last level shows the ending, then clears the run save
- [x] Options → Assist → Longer trap warnings: an independent, persisted toggle (off by default)
      giving falling platforms a 1.5 s warning instead of 1 s, with no penalties
- [x] Options → Assist → Screen shake (D62): a persisted On/Off toggle, on by default; on the web the default
      follows `prefers-reduced-motion: reduce` until the player chooses. Off silences every camera shake,
      the boss shake included.
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
      repeated collection returns; D26 requires retained world state and passable mandatory routes (runtime work tracked in #158; see [authoring contract](ROOMS.md#puzzle-platformer-authoring-contract-d26))
- [x] Dev hotkeys (F1–F6, see [Controls](GAMEPLAY.md#controls))
- [x] Chromatic-aberration glitch post-process — applied to the frozen gameplay or dialogue screen while paused
      (the pause menu and HUD stay crisp); see Deviations below

## Deviations from the source projects

- **Only one coin ripple shows at a time.** As in the Flame and Bevy versions, the ripple
  distorts the rendered scene (`screen_effects.frag`, drawn from an offscreen buffer that is
  only rendered while a ripple or the pause glitch is live), and collecting a second coin
  mid-ripple takes over from the first. The wave phase travels with time, as in Rust; Flame
  freezes it.
- **`Torch`'s particle system is an allocation-free reimplementation** on the shared
  `effects.KimeriaParticleSystem` (fixed struct-of-arrays, 96 slots per torch, a full pool drops the
  oldest particle, one seeded `RandomXS128` per system; 0.1 B/frame measured), not a port of
  dozens of independent per-particle timers driving Skia canvas draws with blur mask
  filters (`SpriteBatch` has no blur-mask equivalent). Visual language (flickering core
  flame, rising embers, drifting smoke, green magic sparkles) is kept, tuned down from an
  early pass that over-saturated to white with too many concurrent additive particles.
- **The chromatic-aberration glitch is a pause effect, not an ambient one.** It's off by
  default in the Rust version and dead code in the Flame version; here it's shown only
  while paused, on the frozen gameplay or healer-dialogue screen (`screen_effects.frag`, drawn from an offscreen
  `FrameBuffer` that is only rendered while paused or while a ripple is live, so normal play
  is unchanged). The HUD and pause menu draw afterwards, unaffected.
- **Mobile browsers** (**Implemented and frozen; the mobile version is cancelled (D61), maintenance only.** Author iPhone play-through, 2026-11-05; fixes in D53 to D57, [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), closed 2026-11-07; the author confirmed portrait and landscape layout, sharpness, the light touch controls, the wake lock and the pause button on a real iPhone in Safari. Android Chrome, tablet and mid-range-phone frame rate checks: **Cancelled (D61): will not be implemented** (was tracked in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277)):
  in portrait the picture is pinned to the top and the stick and buttons sit in the strip below it
  (`ui.TouchLayout`), with a rotate hint; in landscape on a window wider than 16:9 the stick is in the left bar
  and the buttons in the right bar beside the picture (D54); on every touch landscape window the picture shrinks to leave bars of at least 104 units so nothing covers it (D56); the canvas is backed with physical pixels (`usePhysicalPixels`, width x `devicePixelRatio`, D57) so pixel art and text stay crisp on 2x/3x phones; the page uses the visible viewport (`100dvh`), no scroll or
  zoom; the screen wake lock is requested while the page is visible. **What iOS cannot do:** iPhone
  Safari has no page Fullscreen API and a web page cannot hide its toolbars, so there is no fullscreen
  button on iPhone and a Safari tab always loses some height to the toolbars. Fullscreen and the
  Add to Home Screen app were dropped as not needed (author decision, 2026-11-07); the code stays
  (`DisplayControl`, the release page's standalone metadata and manifest, the Options "Display" item
  where the Fullscreen API exists) but is not required or verified on a device. Android
  Chrome and desktop browsers do have the Fullscreen API and get a "Display" item in Options. The wake
  lock needs iOS 16.4 or newer; before that the screen still auto-locks.
- **Fullscreen:** desktop windows via Options (LWJGL), browsers that have the Fullscreen API via
  `DisplayControl` (web launcher). There is no native mobile app, so no native mobile fullscreen ([D61](DECISIONS.md#d61-no-native-mobile-version-release-is-desktop-and-web), [#266](https://github.com/jlogicgames/edgard_in_kimeria/issues/266)).
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
- `ui.TouchControls` — two-axis on-screen stick, Jump / Ranged / directional Action buttons and a top-centre pause button (D52); in portrait they move into the strip under the picture, in any touch landscape window into the side bars (`ui.TouchLayout`, `ui.PlayfieldViewport`, D53, D54); their art is the light high-contrast set in `assets/images/HUD/touch/`, authored as grids in `art/hud/touch/` and exported with `tools/touch_control_sprites.py` (D55)
- `KimeriaGame` — the whole game and its menu states in one place
- `lwjgl3/src/dev` — `runDev`'s launcher, compiler, watcher and reloader (`DevHost`); a source
  set of its own that no release build includes, see "Live editing" above

A fixed-timestep physics accumulator (matching the source projects) was tried and dropped:
even at a rock-steady 60fps average, real frame durations wobble slightly around 1/60, so
the accumulator would sometimes step physics twice in one rendered frame and zero times in
the next — invisible in an FPS counter but visible as jittery movement. `Player` instead
steps physics once per rendered frame with that frame's own (clamped) delta.

### Per-frame allocation

Per-frame code (`update*`, `render*`, `draw*` and what they call) must not allocate; the rule is in `AGENTS.md`. It is guarded twice:
[`AllocationBudgetTest`](../core/src/test/java/com/jlogicsoftware/kimeria/AllocationBudgetTest.java) measures bytes per frame
(`ThreadMXBean`) against ceilings, and [`tools/check_allocations.py`](../tools/check_allocations.py) (`validateAllocations`,
`-PallocationBase=origin/main` for a full PR audit) rejects allocation on changed lines of per-frame methods.

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
  the same helper with `Lwjgl3Preferences` before libGDX starts. There is no native mobile
  module and none is planned (D61, [#266](https://github.com/jlogicgames/edgard_in_kimeria/issues/266)): phones and tablets use the web backend (browser
  local storage) through the mobile browser.
- **Death does not touch the save.** It resets the current room in memory (see D3), with a fast path
  that bypasses the loading screen, and the save only changes on level completion.
- **Continue vs Resume:** `UiState.PAUSED` and the pause menu's *Resume* continue the live run;
  *Continue* on the main menu loads a saved run into a fresh `StartData`.

## Assets

Images, audio and the original Tiled maps come from the Flame project's `assets/`.
`forest-2` has since been extended to three rooms in the same tileset.
Fonts (`NanoPlus.ttf`, `QuestSquare.ttf`) and `main_menu.mp3`/`button_click.wav` are pulled
from the Rust version, which has them and the Flame baseline doesn't. `button_click.wav`
needed re-encoding from `WAVE_FORMAT_EXTENSIBLE` to plain PCM — libGDX's WAV decoder
rejects the former. The GLSL shaders under `assets/shaders/*.frag` are ported from the
Flame project's Flutter `FragmentProgram` shaders (already GLSL, just wrapped in Flutter's
`runtime_effect.glsl` macros) to plain desktop GLSL consumed via libGDX's `ShaderProgram`.

### Original synthesized sound effects

`jump.wav`, `dust_puff.wav`, `wall_rumble.wav` and `grit_fall.wav` are project-original: synthesized by
`tools/audio/generate_sounds.py` (stdlib only, fixed random seeds, byte-identical on every run; 44.1 kHz,
16-bit mono PCM). Licence: project-original, the same as `intro.wav`. Regenerate with
`python3 tools/audio/generate_sounds.py`; every parameter is a named constant at the top of the script.
`tools/test_generate_sounds.py` checks determinism, format, duration bounds, peak (at most -1 dBFS), no DC
offset, and that the shipped files equal the script's output. `python3 tools/audio/analyze_sounds.py [--png DIR] FILE...`
prints duration, peak, RMS and spectral figures and can render waveform previews.

- The legacy `jump.wav` (inherited from the Flame/Rust versions) was replaced on 2026-11-12 by a synthesized
  breathy, throaty effort "ha!" grunt. The author chose it on audition (earlier "hup" and nasal "hm" versions were
  rejected) and wants the crude, 90s-speech-synthesizer character kept. The sound approach, recipes and the
  procedure for new sounds are recorded in [SOUND_DIRECTION.md](SOUND_DIRECTION.md).
- `dust_puff.wav`, `wall_rumble.wav` and `grit_fall.wav` are the sounds of the environment effects in #239: `Player.playerJump` plays the first on a ground jump, and
  `Level.activateTrigger` plays the other two when a lever moves a wall (all behind `game.playSounds()`).
- The other legacy effects (`hit`, `collect`, `bounce`, `disappear`, `boss_*`, `button_click`) and
  `main_menu.mp3` still come from the Flame/Rust versions; their provenance is part of #189.

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

## Sprite artwork guard

`./gradlew validateSpriteArt` runs the source-art policy and new world/HUD PNG contract checks.
The latter inspect PNG metadata with the Python standard library: world native
dimensions, indexed palette up to 16 entries and binary alpha; HUD 128×128 indexed
exports up to 256 entries; the touch-control PNGs in `assets/images/HUD/touch/` (D55) are 64x64 / 32x32 indexed, up to 16 entries, binary alpha. Legacy multi-frame sheets remain separate authored assets. Core `check`, the
actual desktop JAR/native-packaging dependency and TeaVM release-build task depend
on it, including when called directly instead of using the root aliases. Python 3
is required for this developer check; no Python is shipped with the game. Asset
export alone additionally uses Pillow. `python3 tools/test_sprite_art_guard.py`
exercises rejection and hook exit behavior.

The checked-in `.codex/hooks.json` configures synchronous PostToolUse/Stop checks.
Review/trust it through Codex `/hooks` before automatic session enforcement; see
[artwork rules and test limits](ART_DIRECTION.md#gameplay-sprites-and-procedural-art-prohibition-2026-10-22).

## Named gameplay and artwork parameters

Follow [parameter ownership](NAMED_PARAMETERS.md) for map cells, sprite frames,
collision bodies, projectile timing and UI geometry. `validateNamedParameters`
runs on core check and release packaging alongside the sprite-art check.
Use `./gradlew validateNamedParameters -PnamedParameterBase=origin/main` to include
committed PR changes; the default HEAD comparison checks current edits only.
The PostToolUse/Stop hooks also run this changed-code gate. Run
`python3 tools/test_named_parameters.py` to verify rejection and recovery behavior.

## Particle system plan

Approved by the author on 2026-11-11 and tracked by [#285](https://github.com/jlogicgames/edgard_in_kimeria/issues/285); no production code changed. Sections 10 to 14 add the vendored libGDX skills, a proposed documentation rule, an allocation audit and a guard, and are the only parts that touch files outside this document (`.agents/skills/`). It replaces the
self-written particle code (`Torch`, `BloodBurst`, `SoftDot`, `Shaders.whiteQuad`) with an
allocation-free system before the dust and ceiling grit of [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239)
are built on it. Every number below was measured on 2026-11-10 unless it says "read from the code".

On 2026-11-12 the classes were renamed `ParticleSystem` to `KimeriaParticleSystem` and `BloodSystem` to `KimeriaBloodSystem` so they cannot be confused with libGDX's 3D `ParticleSystem` (no behaviour change; golden numbers and ceilings unchanged), and the project skill `.agents/skills/kimeria-particles/SKILL.md` documents them. The third kind, `KIND_GRIT` (constant size, linear alpha, normal blend), landed with #285 step 5 together with the dust and grit systems of #239 in `EnvironmentFx`.

### 1. Why: what the current code allocates (measured)

Method: a throw-away JUnit probe (deleted afterwards) built real `Level`s headlessly (the
`RoomCeilingTest` harness), drove `update(1/60)` and `render` through a no-op `Batch`, and read
`ThreadMXBean.getThreadAllocatedBytes` over 1200 frames after 300 warm-up frames, on HotSpot
(GraalVM CE 25). Figures are bytes per frame at 60 Hz; each was repeated in 2 to 3 runs and varied by
a few percent.

| Scene | Burning torches at room start | `update` | `update` + `render` |
| --- | --- | --- | --- |
| forest-0 rooms 0 to 3, 5 | 0 | 1.0 to 1.4 KB | 1.3 to 1.8 KB |
| forest-0 room 4 | 0 | 2.3 to 2.8 KB | 2.7 to 3.2 KB |
| forest-1 (48 `RainDrop`s) | 1 | 1.9 to 2.1 KB | 4.2 to 4.5 KB |
| forest-2 room 0 (boss approach) | 2 | 8.6 to 8.8 KB | 5.6 to 9.7 KB |
| forest-2 room 1 | 3 | 1.8 to 1.9 KB | 3.0 to 3.1 KB |
| forest-2 room 2 (boss) | 5 | 1.8 to 2.1 KB | 2.9 to 3.5 KB |

The same probe on single objects:

- **`Torch`, alone:** `update` allocates 118 to 120 B per frame per torch (about 7 KB/s): a `Particle` with
  three `Vector2` and a `Color` per spawn, plus `ArrayList` churn. `render` calls `SoftDot.region()`,
  which is 40 B per call (measured in isolation); HotSpot removed it in some loops (0 to 72 B
  observed, presumably by escape analysis), so the desktop figure understates what a runtime without that optimisation
  pays. About 160 B per frame per torch in total, 9.6 KB/s.
- **`SoftDot.region()` and `Shaders.whiteQuad()`:** 40 B per call each. `RainDrop.render` calls
  `whiteQuad()` per drop per frame, so forest-1 spends about 48 x 40 = 1.9 KB/frame on that alone; this
  is the same size as the difference between the two columns of its row.
- **`BloodBurst`:** 1,968 B to construct (32 drops), 2,928 B empowered (48); `update` allocates 0;
  `render` 39 B per frame (`new Color`). A whole burst is 4.1 KB (5.1 KB empowered) over 55 frames.
  A burst is rare, so blood is a small cost next to the torches.
- **`KimeriaGame` line 670 and `Level.cameraShakeOffset`:** a `Rectangle` (32 B) and a `Vector2` (24 B)
  every frame, 56 B/frame.

The torches are not the whole of a room's frame. A sampled allocation profile (JFR
`ObjectAllocationSample`, mixed rooms, a ranking and not a measure) shows the largest allocators outside
the particle code: the `Rectangle` returned by `Actor.boundsOf`, `Carryable.bounds` and `Carryable.update`,
`Level.blocksObject` and `ArrayList` iterators in `Level.update`/`render`. (The first JFR ranking also named the varargs arrays of
`KeyBindings.anyPressed`/`downHeld`; step 0 re-checked that against `main` and the source has none: `anyPressed` takes an `int[]` and
`downHeld()` takes no arguments. The samples most likely came from the headless harness's proxied `Gdx.input`, whose every call
boxes its arguments.) In forest-2 room 2, five torches at about 160 B each are roughly 0.8 KB of 2.9 to 3.5 KB.
So the particle work removes the torches, the rain and the blood, and a second allocation pass over entity
and level code would be needed for the rest (question 4). The 8.6 KB `update` of forest-2 room 0 was not
attributed to a cause.

`docs/DEVELOPMENT.md` ("Deviations") called the torch system "pooled". It was not: every spawn was a
`new Particle()`. Fixed in step 2 (below).

### 2. The two options, run

| | A. libGDX `ParticleEffect` + `ParticleEffectPool` (`.p` files) | B. Custom system on fixed arrays, cached regions, seeded generator |
| --- | --- | --- |
| Runs in the TeaVM web build | **Yes, measured.** A spike (a scratch copy of the repo, never committed) loaded `particles/fire.p` and its sprite with `ParticleEffect.load`, pooled three effects and drew them. `:web:gdx_teavm_web_js_release_build` succeeded with no reflection or resource configuration; `tools/check_web_release_artifact.py` passed (110 files) with a `.p` file in `assets/`. In the browser pane (`index.html?spike`) the console printed `loaded emitters=1`, then 12 and 11 active particles at frames 120 and 300 (the pool freed and re-obtained an effect at those frames), and the canvas showed the three flames. | Uses arrays and `float` maths only, the same as the existing code, so no new TeaVM surface is expected; confirmed per step by `buildWeb` and a browser run |
| Allocation per frame | **0.4 B/frame** for three pooled effects over 3000 frames, with one free/obtain every 100 frames (desktop JVM) | 0 by construction; pinned by an allocation test (section 4) |
| Web size | `app.js` 1,249,811 to 1,301,860 B raw (+52,049 B, +4.2 %), 367,482 to 381,960 B gzip (+14,478 B), for the library's emitter classes plus a small spike class | Expected to be small; measured with the same byte comparison in the PR that adds it |
| Authoring | A separate particle editor (libGDX tools) writes `.p`; the editor itself was not run | Code and named constants, as today (the `validateNamedParameters` gate applies; the spike class was flagged by it) |
| Testability, determinism | Same seed gives the same state, different seed differs (measured, with `MathUtils.random` replaced by a seeded `RandomXS128`, as `BloodBurstTest` already does). The particle array is private (`getParticles()` is protected), so a test sees counts, `getPercentComplete` and the bounding box, not particle positions | Every field is readable in a test; golden numbers per frame are possible |
| Blending | `ParticleEmitter.setAdditive` per emitter | Per kind, drawn as one additive pass and one normal pass (fewer `setBlendFunction` flushes than today's per-particle switching; the saving was not measured) |
| Reproduce the current torch | Approximate: a gradient tint and scaled values over life replace the current per-particle `from` to `to` lerp, `(1 - t)^2` alpha and size curve; they would look similar, not identical | Exact: same formulas, same random-call order |
| Reproduce the current blood | Gravity and aligned streaks exist; the floor splash does not (no collision or callback member in `ParticleEmitter`, `javap`), the red varies per particle (a `ParticleEmitter` tint is one gradient over life) | Exact, floor splash included |
| World Y grows downward | **Measured:** an emitter at angle 90 moved to larger Y (down on screen: particles spanned y 97.1 to 107.8 around a spawn at y = 100); after `ParticleEffect.flipY()` they spanned 92.2 to 102.9, i.e. up. Every authored angle and gravity needs `flipY` and a test, the AGENTS.md trap | The code names the direction, as the rest of the game does |
| TeaVM risk | Low for loading and updating (measured); mixed additive and normal emitters in one effect, atlas loading and other browsers were not exercised | Low |

### 3. Recommendation: option B, a small shared fixed-array system

Reasons, in order of weight:

1. **The look is already tuned by hand** (`docs/BOSS.md`: "Author tuning" of the blood; the torch was
   toned down from an over-saturated pass). Option B can keep it exactly and prove it with numbers;
   option A can only approximate it, and an approximation cannot be pinned by a numeric test.
2. **Blood needs the floor splash and per-particle colour**, which `ParticleEmitter` does not offer, so
   `BloodBurst` would stay custom code under option A anyway.
3. **Tests.** The project pins behaviour with headless tests; B exposes state, A hides it.
4. **#239 already assumes it.** Its plan (`docs/ART_DIRECTION.md` on `claude/env-effects-239-c3d32d`) designs
   fixed struct-of-arrays pools (dust 24, grit 32) with no `new` after construction. Building the shared
   type first means dust and grit are two more instances of it instead of a second hand-rolled copy.
5. Option A's cost is real but modest (+52 KB raw, +14.5 KB gzip, a `.p` workflow and the `flipY` rule),
   and it stays available later for large authored effects (explosions) without conflicting with B.

Design (for the migration, not final API):

- `effects.KimeriaParticleSystem`: capacity fixed at construction, struct-of-arrays (`float[] x, y, fromX, fromY,
  toX, toY, life, maxLife, size, r, g, b`, `byte[] kind`), free slots recycled (a full pool overwrites the
  oldest slot, never allocates), `spawn(...)`, `update(dt)`, `clear()`, `render(batch, region)` in one
  additive and one normal pass, colours set with `batch.setColor(r, g, b, a)` floats and restored once.
- One `RandomXS128` instance per system, seeded in the constructor. It is the algorithm behind
  `MathUtils.random`, so with the same call order a seed reproduces today's sequence; the #239 plan names
  an `int` xorshift instead and should adopt this type (question 3).
- `SoftDot.region()`, `glowRegion()` and `Shaders.whiteQuad()` return one cached `TextureRegion`;
  `dispose()` drops it with the texture.
- Each `Torch` owns a small `KimeriaParticleSystem` allocated in its constructor. Blood moves to a
  `Level`-owned system, so `Boss` calls `level.blood(...)` and no `BloodBurst` object is queued per hit.
  Capacities come from a measured peak (a test records the maximum live count over a long run) plus headroom.

### 4. Migration steps, in order

| # | Change | Cost |
| --- | --- | --- |
| 0 | **Pin the current look and install the guard.** (a) A test with a fixed seed records the live particle count and each particle's position, size and alpha at chosen frames of a `Torch` and of a 32- and 48-drop `BloodBurst`, as constants (read through reflection, like `BloodBurstTest`); it lands against the old code, so the later steps are compared with it. (b) The allocation guard of section 13: the `AllocationProbe` test helper with ceilings set to today's measured numbers, and `tools/check_allocations.py` on changed lines, wired into Gradle and `.codex/hooks.json`. (c) The rule text of section 11, in the form the author approves. | medium |
| 1 | **Cache the regions:** `SoftDot.region()`, `glowRegion()`, `Shaders.whiteQuad()`; remove the `new Color` in `BloodBurst.render`. Removes 40 B per call everywhere, including the forest-1 rain (about 1.9 KB/frame) and every torch draw. Check that no caller mutates the shared region (`whiteQuad()` is flipped on purpose) and that `dispose()` resets it, also under `runDev` hot reload. **Done (#285 step 1):** measured on 2026-11-11, forest-1 4,491.8 to 4,525.5 -> 2,530.2 to 2,564.4 B/frame, `BloodBurst` 97.6 -> 32.8 B/frame (the constructor alone), one `Torch` 190.9 to 195.1 -> 149.4 to 151.3 B/frame (HotSpot had already hidden most of the 40 B per `SoftDot.region()` call). | small |
| 2 | **`KimeriaParticleSystem` and `Torch`:** the new class with its unit tests, then `Torch` on it with the same formulas and the same random-call order. Fix the "pooled" paragraph in "Deviations" in the same PR. **Done (#285 step 2):** `KimeriaParticleSystem` with `KimeriaParticleSystemTest` and `TorchParticleCapacityTest` (peak 59 live particles over 40 seeds x 3 frame rates x 20,000 frames; capacity 96); `ParticleGoldenTest` passes with the golden numbers unchanged (the torch is built with the seed directly instead of replacing `MathUtils.random`; positions use `Vector2.lerp`'s form `from*(1-t) + to*t` to match to the float). Draw order: smoke (normal blend) is drawn first, then the additive flame, then the glow, so smoke still sits behind the flame. Measured 2026-11-12: one `Torch` 149.4 to 151.3 -> 0.1 B/frame, forest-1 2,530 to 2,564 -> 2,441.8, forest-2 room 0 9,370 to 9,430 -> 8,920 to 9,000, room 1 2,550 to 2,605 -> 2,184.0, room 2 2,651 to 2,657 -> 1,992.0 B/frame. | medium |
| 3 | **Blood:** `BloodBurst` on the shared system with the floor splash kept; `BloodBurstTest` is rewritten for the new state (it reads the private `drops` and `Drop` fields by reflection, so it cannot stay as it is). `Boss`, `docs/BOSS.md` ("Blood uses code particles") follow. **Done (#285 step 3):** `effects.KimeriaBloodSystem`, a blood-specific fixed-array class beside `KimeriaParticleSystem` (it follows the same rules but keeps its own arrays: a drop is ballistic with its own colour and a floor to land on, not a lerp between two points), owned by `Level`; `Boss` calls `level.blood(...)`, `BloodBurst` is deleted. `ParticleGoldenTest` passes with the golden numbers unchanged (only the way state is read changed). `BloodBurstTest` became `KimeriaBloodSystemTest`; `KimeriaBloodSystemCapacityTest` measured a peak of 48 live drops (capacity 96; the boss cannot be hit again for 1.35 s and a drop lives at most 0.9 s). Measured 2026-11-12: a blood spray 32.8 -> 0.0 B/frame (a 48-drop empowered spray 0.0; the old one was about 49 B/frame over a 60-frame cycle, from its 2,928 B constructor), forest-2 room 2 with a spray every 60 frames 1,991.9 to 1,992.6 B/frame, the other scenes unchanged. Draw order: blood is drawn after all `objects` and before `ambience`, where the queued burst used to sit in the object list. | medium |
| 4 | **Per-frame `Rectangle` and `Vector2`:** reuse fields for `KimeriaGame` line 670 and `Level.cameraShakeOffset`. #239 step 1 plans the same change; whichever lands first does it and the other drops it (#285 step 4 landed first; #239 step 1 then moved the shake into `effects.ScreenShake`, D62). **Done (#285 step 4):** `KimeriaGame` reuses a `reusableShakeView` rectangle and reads `shake.x`/`shake.y` at once; `Level.cameraShakeOffset` returns a field that the next call overwrites (Javadoc says so; the callers, `KimeriaGame` and the tests, read it straight away and keep nothing). `Boss.render` and `BossHud.render` save and restore the batch and font colour as four floats instead of a `Color` copy; `BatchColourRestoreTest` pins that the colour comes back exactly. The `Boss.render` copy was the same 40 B as the others on paper; `new Color` is 32 B on HotSpot. Measured 2026-11-12: camera shake query 24.0 -> 0.0 B/frame (the `KimeriaGame` rectangle, 32 B, is in no scenario, it is read from the code), `BossHud.render` 160.0 -> 96.0 B/frame (-40 %; the rest is the `Matrix4` copy of the projection, step-4 scope excludes it), forest-2 room 2 1,992.0 -> 1,960.0 B/frame (-1.6 %, the `Boss` copy), the other room scenes unchanged. #239 step 1's "same change" is therefore already done. | small |
| 5 | **Docs and #239:** the "Deviations" text, the `docs/BOSS.md` blood note and the #239 plan point at `KimeriaParticleSystem`; this section is rewritten as a description of what exists, and sections 10 to 14 are folded into `docs/DEVELOPMENT.md`'s permanent text (skills, rule, guard). | small |

Each step is its own pull request, and all six are the checklist of the single issue in section 14. Step 1 alone removes the rain's 1.9 KB/frame in forest-1 and 40 B from every torch draw.

### 5. What stays identical, and how that is checked

- **Torch and blood:** the step 0 numbers must hold after steps 2 and 3, to the float. If a number has to
  change, the report says so and `docs/DECISIONS.md` records the measured value and the reason (the rule for
  tolerances in `AGENTS.md`).
- **A real frame:** headless tests cannot show pixels. For steps 2 and 3 the PR attaches a before and an
  after capture from `runDev` of forest-2 room 2 (five torches) and of a sword hit on the boss, for the
  author's eye. This was not done for this plan.
- **Hints, hero label, collision, timing:** untouched; no step reads particle state in gameplay.

### 6. Tests to add

- `KimeriaParticleSystem`: never exceeds capacity; a full pool recycles the oldest slot; `clear()` empties it;
  the same seed gives the same state and another seed a different one; every particle is dead by its
  `maxLife`.
- The step 0 golden test, ported to the new API in steps 2 and 3.
- An allocation budget (`ThreadMXBean`, after warm-up, a few tens of bytes per frame as the ceiling; section 13) for one
  `Torch` and for a whole blood spray; the measured 0.4 B/frame of the pooled library effect shows such a
  threshold is reachable. HotSpot only; it does not speak for TeaVM.
- Region caching: `SoftDot.region()` returns the same instance twice; after `dispose()` a new one.
- A peak-live-count test per effect that sizes the capacity.

### 7. Risks

- **Shared cached regions** can be mutated by a caller (step 1 checks all of them).
- **Capacity too small** drops the oldest particle: visible only as a thinner flame, never a crash; the
  peak test sets it.
- **Blend-pass order:** drawing all additive particles, then all normal ones, changes the draw order inside
  one torch (today they interleave in spawn order). Smoke is the only normal-blended kind and sits behind
  the flame; step 2's capture compares it.
- **TeaVM:** nothing in B is new to the backend, but step 2 and step 3 each run `buildWeb` and a browser
  load of the changed scene.

### 8. What these measurements do not cover

- **Allocation or GC pauses in the browser build.** All allocation figures are from a desktop JVM. The
  browser was used only to check that the library effect loads and runs, not to read memory or frame times
  of the real game. Whether the web release shows GC pauses today was not observed.
- The real game on the web with torches or rain (the spike ran as a separate entry in a scratch copy).
- Mixed additive and normal emitters in one library effect, atlas-based loading, the particle editor, other
  browsers and mobile browsers.
- The cause of forest-2 room 0's 8.6 KB `update`, and the exact ranking of the other allocators (a sampled
  profile only).
- Frame times and draw-call flushes, before or after.

### 9. How #239 uses it

Its step 2 (jump dust) and step 3 (ceiling grit) become two `KimeriaParticleSystem` instances owned by
`EnvironmentFx`: dust with normal blending and the `SoftDot` region, grit with the white quad, capacities 24
and 32 as that plan states, spawned by `fx.dustPuff(...)` and `fx.ceilingGrit(...)`. They need no particle
class of their own, no new art and no atlas ([#284](https://github.com/jlogicgames/edgard_in_kimeria/issues/284)
measures batch flushes later). That plan's own text stays valid except for the generator type and the
"older classes are left alone" sentence, which step 5 updates. #239 step 1 (shake manager) can proceed
independently; only the `KimeriaGame` line 670 change overlaps (step 4 above).

### 10. Vendored libGDX skills and the project skill (done in this change)

What was done, on 2026-11-11:

- Six skills were copied from [kyu-n/gdx-claude-skills](https://github.com/kyu-n/gdx-claude-skills) at the pinned commit `0887af2` (MIT):
  `libgdx-particles`, `libgdx-collections-json`, `libgdx-2d-rendering`, `libgdx-application-lifecycle`,
  `libgdx-camera-viewport`, `libgdx-tiled`, each as its `SKILL.md` only, under `.agents/skills/`, next to the
  `kimeria-*` skills. The MIT text and one provenance file are kept: [`.agents/skills/SOURCE.md`](../.agents/skills/SOURCE.md)
  (upstream URL, commit, copy date, "adapted for this project; no upstream tracking", every local change, what
  was checked against the libGDX 1.14.2 sources and what was not). The plugin marketplace is not used.
- Every line was read. No instruction aimed at the agent, command, hidden text or URL beyond two libGDX documentation
  links was found; frontmatter is `name` and `description` only (the `libgdx-tiled` name was corrected to match its folder).
- The edits that matter: the skills assume a Y-up camera, GWT and Android, while this game is Y-down, TeaVM and
  desktop/web, so each carries a project note, GWT statements are marked "GWT, not TeaVM", and a `TmxMapLoader.Parameters`
  line that does not exist in 1.14.2 was removed. The game does not use libGDX's Tiled loader at all (section "Architecture").
- **Pools:** in the 1.14.2 sources `Pools` and `ReflectionPool` are `@Deprecated`, `DefaultPool` (supplier constructors) and `PoolManager`
  exist. Any pooled object in this plan therefore uses `DefaultPool` or `PoolManager`; `KimeriaParticleSystem` itself needs no pool (fixed arrays,
  section 3). `ParticleEffectPool` (option A) is its own, non-deprecated pool class and `PooledEffect.free()` is its API.
- One project skill, `.agents/skills/kimeria-libgdx-rules/SKILL.md`, holds what upstream cannot know and points to the docs instead
  of repeating them: Y-down world and Y-up text, no allocation in `update`/`render`, libGDX pools and collections,
  cached regions, `Nearest` and the sprite-versus-VFX rule, TeaVM limits and `buildWeb`, tests never weakened silently, English only.

### 11. Proposed documentation rule (text for the author; `AGENTS.md` is not changed by this plan)

Proposed `AGENTS.md` section, after "Coordinates":

> ## No allocation in `update` and `render`
>
> Per-frame code (`update*`, `render*`, `draw*` and everything they call) must not allocate: no `new`, no varargs call, no
> `for-each` over a `List` or `Set` (it creates an iterator), no boxing, no `String.format` or string concatenation, no
> capturing lambda. Reuse fields and fixed-size arrays; use libGDX `Array`, `FloatArray`, `IntArray` with index loops; pool
> through libGDX `DefaultPool` or `PoolManager` (`Pools` and `ReflectionPool` are deprecated in 1.14.2). Cache regions, textures and
> text layouts. Constructors, level load and error paths may allocate. A claim of "allocation free" needs a `ThreadMXBean`
> number from the budget test, not a reading of the code. An exception goes in the allowlist of `tools/check_allocations.py`
> with a reason that says what it carries.

Proposed `docs/DEVELOPMENT.md` text, as a short "Per-frame allocation" subsection under "Architecture": the same rule in two sentences,
a link to `AllocationBudgetTest` and `tools/check_allocations.py` (section 13), and the line that `docs/DEVELOPMENT.md` "Deviations" no
longer calls the torch system pooled (step 2).

### 12. Allocation audit of the remaining `new` in hot paths

Method: the measured profile of section 1, plus a syntactic scan of `core/src/main/java` (comments and strings removed, methods found as
in `tools/check_sprite_art.py`). Scan 1 lists `new` in methods named `update*`, `render*`, `draw*`: 18 expressions in 12 methods. Scan 2 lists
`new` in every method that is not a constructor, loader or `create`, excluding exception types: 183 expressions in 41 files (`world/Level.java` 51,
`KimeriaGame.java` 21, `entity/player/Player.java` 13, `ui/Overlay.java` 10, `ui/Hud.java` 7, then at most 6 each). Scan 2 over-counts
(menus, save snapshots and `Assets` caches run rarely) and under-counts nothing it can see, but neither scan follows calls, so an allocation
in a helper called from `update` is only in scan 2, and iterators, varargs and boxing are in neither. The "How found" column says which rows were
measured and which were read from the code.

| Where | Allocates | Cost | How found | Disposition |
| --- | --- | --- | --- | --- |
| `KimeriaGame.render` (line 670), `Level.cameraShakeOffset` | `Rectangle`, `Vector2` | 56 B/frame | measured (section 1) | step 4, done: reused fields, query 0.0 B/frame |
| `Torch.spawnCore/Embers/Smoke/Sparkles`, called from `update` | `Particle`, 3 `Vector2`, `Color` | 118 to 120 B/frame/torch | measured | step 2, done: `KimeriaParticleSystem`, 0.1 B/frame |
| `SoftDot.region`, `glowRegion`, `Shaders.whiteQuad` (called by `Torch.render`, `RainDrop.render`) | `TextureRegion` | 40 B per call; about 1.9 KB/frame in forest-1 | measured | step 1, done: one cached region each |
| `BloodBurst.render` (line 69) | `Color` copy of the batch colour | 39 B/frame while visible | measured | step 1, done: float components; step 3, done: `BloodBurst` replaced by `KimeriaBloodSystem`, 0.0 B/frame |
| `Boss.render` (236), `BossHud.render` (49, 80) | `Color` copy of the batch colour | about 40 B per draw | read from the code | step 4, done: float components; `BossHud` 160.0 -> 96.0 B/frame, forest-2 room 2 1,992.0 -> 1,960.0 |
| `Hud.render` (86, 102), `renderInteractionHint` (150, 154), `draw` (180, 181) | `GlyphLayout` | per frame while the FPS text, hint or zone message is shown | read from the code | question 4 |
| `Carryable.update` (119), `Fireball.update` (36), `Actor.boundsOf`, `Carryable.bounds`, `Level.blocksObject` | `Rectangle` | per object per frame | JFR ranking (a ranking, not a measure) and the code | question 4 |
| `Level.update` / `render` and other loops: 89 `for-each` over lists in `core` (45 in `Level.java`) | `Iterator` | per loop per frame | counted by grep; the JFR ranking names `ArrayList` iterators | question 4 |
| `BossHud.render` (`getProjectionMatrix().cpy()`) | `Matrix4` | 96 B per frame while the boss bar is shown | measured in step 4 | question 4 |
| `Player.renderPassiveInteractionTargets` (772) | `HashSet` | per call | read from the code | question 4 |
| `GamepadInput.update` (66) | `boolean[]` | when the pad's button count is read; may be conditional | read from the code | question 4 |
| `HealerDialogueView.render` (78) | `IllegalStateException` | only on an impossible `switch` branch | read from the code | allowlist, reason "error path" |
| `KeyBindings.anyPressed` | none in the current source (an `int[]` parameter, no varargs) | 0 | read from the code | re-checked against `main` in step 0: confirmed, no varargs (section 1 corrected) |

Rows marked "question 4" are out of this plan's migration steps; they stay in the table so the guard's allowlist and the follow-up plan start
from one list.

### 13. The guard: a budget test and a source scanner

Two checks that fail for different reasons; neither replaces the other.

**`AllocationBudgetTest` (runtime).** A test helper `AllocationProbe` in `core/src/test` runs a frame loop through a no-op `Batch`, reads
`com.sun.management.ThreadMXBean.getThreadAllocatedBytes` for the current thread over 1200 frames after 300 warm-up frames (the method of
section 1), and returns bytes per frame. Tests assert a ceiling per scenario, written as a named constant with the measured value in a comment:
one `Torch`, one blood spray (`KimeriaBloodSystem`, formerly `BloodBurst`), one `KimeriaParticleSystem` at capacity, and the forest-1 and forest-2 room scenes of section 1. Step 0 sets the
ceilings to today's measured numbers plus headroom for the few-percent variation (so the test passes on the old code and protects against a
regression); each of steps 1 to 4 lowers the ceilings it earned, and the particle objects end at a few tens of bytes per frame. A ceiling that
is raised is reported and recorded in `docs/DECISIONS.md` with the measured value and why (the tolerance rule in `AGENTS.md`).
It runs in `:core:test`, so in `check`. It fails if the MXBean does not support the call; it does not skip silently.

Can catch: any allocation on an exercised path, including hidden ones (iterators, boxing, varargs, library internals), in whatever method
it sits. Cannot catch: paths the scenario does not run; HotSpot's escape analysis, which removes some allocations that a browser build would
still pay (the 0 to 72 B spread of `SoftDot.region()` in section 1); anything about TeaVM, GC pauses or frame time (section 8); noise below the
ceiling's headroom.

**`tools/check_allocations.py` (static).** Modelled on `tools/check_sprite_art.py` (`code_only`, `methods`, rules by regular expression, an
`ALLOWED` table keyed by file, method and kind) and run the way `tools/check_named_parameters.py` runs: on **changed lines** against `--base`
(default `HEAD`; `origin/main` to include committed PR changes), so the existing code needs no baseline and no blanket exemption. Wiring: a
`validateAllocations` Gradle `Exec` task like `validateSpriteArt`, a dependency of `:core:check`, the desktop jar and the web release build,
and two more commands in `.codex/hooks.json` (PostToolUse and Stop, `--hook`), which need the same `/hooks` trust review as the existing two.
`python3 tools/test_check_allocations.py` pins rejection and recovery like the other checkers. An `--all` mode prints every hit, which is how
the table in section 12 is regenerated.

Rules, applied inside methods named `update*`, `render*`, `draw*` and inside the methods listed in a `HOT_METHODS` table (helpers that the
scanner cannot discover by name, for example `Torch.spawnCore`):

- `new T(...)` and `new T[...]` for any `T` except exception and error types;
- `String.format`, `String.valueOf`, string concatenation with `+` where one operand is a string literal;
- `.stream()`, `List.of`, `Set.of`, `Map.of`, `Arrays.asList`, `Collections.*` factory calls;
- a `for` over a name declared as a `List`, `Set`, `Collection`, `Map` or `Iterable` in the same file (an iterator).

Can catch: the syntactic cases above on lines a change touches, the same way `Torch.spawn*` would be caught once listed. Cannot catch: an
allocation in a helper nobody listed (the call graph is not followed), varargs and boxing (the checker has no types), iterators over a field
whose type is declared in another file, allocation inside libGDX or the JDK, lambdas that capture (flagged only as "lambda in a hot method").
It also gives false positives: a `new` in a branch that runs once (a lazy cache fill), `String.valueOf` on a path taken only when a debug
flag is on, and a `for` over a list that is in fact an `Array` of another name. Each false positive needs an allowlist entry; each entry
states what the excluded code carries and why fixing it would lose that (the gate-exemption rule in the global working agreement), for example
"error path, never reached in play" or "runs once per room entry, measured 0 B/frame by the budget test". The scanner is a net for new
code; the budget test is what proves a path clean.

### 14. Migration steps and the single GitHub issue

The steps are the table of section 4 (steps 0 to 5), in that order. Proposed text for the one issue (created after the author approves the plan;
the `docs/DEVELOPMENT.md` gaps rule applies to every line of this document that names remaining work):

> **Title:** Allocation-free particles: shared `KimeriaParticleSystem`, allocation guard, libGDX skills
>
> **Body:** Implements the "Particle system plan" in `docs/DEVELOPMENT.md` (option B, section 3). Each step is one pull request that
> references this issue. Background: measured per-frame allocation of torches, rain and blood; the vendored skills and rule are described in
> sections 10 and 11.
>
> - [ ] Step 0: golden test of the current torch and blood; `AllocationProbe` and `AllocationBudgetTest` with ceilings at today's numbers;
>   `tools/check_allocations.py`, `validateAllocations`, `.codex/hooks.json`, `tools/test_check_allocations.py`; the approved rule text in
>   `AGENTS.md` and `docs/DEVELOPMENT.md`; vendored skills committed (`.agents/skills/`, `SOURCE.md`)
> - [x] Step 1: cached `SoftDot.region()`, `glowRegion()`, `Shaders.whiteQuad()`; no `new Color` in `BloodBurst.render`; ceilings lowered
> - [x] Step 2: `KimeriaParticleSystem` and `Torch` on it; golden test unchanged; `buildWeb` and a browser load (menu and level 0 only; a lit torch was not seen in the browser); torch ceiling at a few tens of bytes
> - [x] Step 3: blood on the shared system with the floor splash (`KimeriaBloodSystem`, `Level.blood`); `BloodBurstTest` rewritten as `KimeriaBloodSystemTest`; `Boss`, `docs/BOSS.md`; blood ceilings 8 B/frame (measured 0.0)
> - [x] Step 4: reuse the per-frame `Rectangle`/`Vector2` (`KimeriaGame` line 670, `Level.cameraShakeOffset`) and the `Color` copies in `Boss`/`BossHud`
> - [x] Step 5: #239 dust and grit as `KimeriaParticleSystem` instances (`EnvironmentFx.dustPuff`/`ceilingGrit`, `KIND_GRIT`; measured 2026-11-12: jump dust 0.0 B/frame, lever grit 4.6 B/frame of which the `HashSet` in `Level.activateTrigger` is the lever pull itself)
> - [ ] Update `docs/DEVELOPMENT.md`: replace the "Particle system plan" section by a description of what exists (skills, rule, guard,
>   `KimeriaParticleSystem`), fix the "Deviations" paragraph that calls the torch system pooled, remove any note this issue made obsolete
> - [ ] Update `docs/BOSS.md` ("Blood uses code particles") and the #239 plan
> - [ ] Re-check `.agents/skills/SOURCE.md` and the `kimeria-libgdx-rules` skill against the final code

### 15. Questions for the author (answered 2026-11-11)

The author approved the plan with the recommended answer to every question below: question 1 option B with A in reserve; 2 identical look; 3 one `RandomXS128` per system; 4 the rest of the allocations get their own plan after this one; 5 browser GC measurement after step 1; 6 a hard `AGENTS.md` rule plus the budget test; 7 the scanner checks changed lines only; 8 Gradle and Codex first, Claude hooks later; 9 all six skills stay in `.agents/skills/` with no upstream tracking; 10 a hard ceiling for the particle objects, per-scene ceilings tightened later.

1. Is B (custom fixed arrays) the base, with A kept in reserve for large authored effects such as
   explosions? Or is a particle editor workflow worth +52 KB raw to you for everything?
2. Torch and blood: must they look **identical** after the migration (the plan assumes yes, pinned
   numerically), or may they be re-tuned as part of it?
3. One `RandomXS128` per system (same sequence as today) or the `int` xorshift of the #239 plan?
4. In forest-2 room 2 the torches are roughly a quarter of the per-frame allocation (estimated from 160 B each). Should the rest (entity
   rectangles, list iterators, key-binding varargs) get its own plan and issue after this one?
5. Do you want a real browser GC measurement (for example the Chrome DevTools memory timeline on forest-2
   room 2, which I cannot drive from here) before step 2, or after step 1 as the first comparison?
6. Is the rule text of section 11 right as a hard `AGENTS.md` rule, or should it be a guideline that only the budget test
   enforces (the scanner then being optional)?
7. Should the scanner check changed lines only (proposed, no baseline needed) or whole files with an allowlist of every existing hit
   (about 183 sites today, section 12)?
8. The existing hooks live in `.codex/hooks.json`; the repository has no Claude Code project hooks. Should the new check also be wired for
   Claude sessions (a project `.claude/settings.json`), or stay Gradle plus Codex only?
9. Keep all six vendored skills in `.agents/skills/` where every agent loads them, or only `libgdx-particles` and
   `libgdx-collections-json` plus the project skill? Confirm "no upstream tracking" is what you want (the copies then never update).
10. Budget ceilings: a hard "few tens of bytes per frame" for the particle objects only (proposed), or also a ceiling per room scene
    that tightens as question 4's follow-up lands?
