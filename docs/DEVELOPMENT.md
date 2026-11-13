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
- [x] Decor bush (#239 step 4) — cosmetic `Decor` object (`kind` = `bush`), brushes when the hero walks past; no collision; see [ART_DIRECTION.md](ART_DIRECTION.md) section 4
- [x] Hanging vine (#239 step 5) — cosmetic `HangingVine` object (no properties), sways when a falling stone lands within 160 px (off with the Screen shake option); needs a falling stone in its room (`tools/check_tiled_links.py`); see [ART_DIRECTION.md](ART_DIRECTION.md) section 4
- [x] Honey fungus and false honey fungus (#295, D63) — Tiled types `HoneyFungus` (harmless, 16 golden `KimeriaParticleSystem` spores, no hitbox) and `FalseHoneyFungus` (lethal `trap-spores` cloud, 1 s, hitbox = drawn 24x24), one base class `FungusTrap` (idle, zone-triggered swell over the shared `TrapWarnings` duration, one burst, then spent; no re-arming); optional properties `zone`, `zoneLeft`, `zoneRight` (px, default 64); load-time placement rules in `Level.validateFungus` mirrored by `tools/check_tiled_links.py`; placed in forest-0 (x 150, 1804) and forest-2 canopy (x 1672)
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
`-PallocationBase=origin/main` for a full PR audit) rejects allocation on changed lines of per-frame methods. What exists and its measured ceilings: "Particles and the allocation guard" below.

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

`jump.wav`, `dust_puff.wav`, `wall_rumble.wav`, `grit_fall.wav` and `keepalive.wav` are project-original: synthesized by
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
- `dust_puff.wav`, `wall_rumble.wav` and `grit_fall.wav` are the sounds of the environment effects in #239: `Player.playerJump` plays the first on a ground jump (and the landing at 0.6 volume), and
  `Level.activateTrigger` plays the other two when a lever moves a wall (the rumble is a seamless loop, stopped when the wall stops) (all behind `game.playSounds()`).
- **First sound lost (2026-11-13, web build; desktop is not affected as far as the code shows).** The sounds were created on
  their first play. On the web that makes a new Howler instance in the middle of the game, which also creates the audio
  context after the key press that would have unlocked it, so the first jump was silent and the second was heard. Howler also
  suspends its context after 30 s without sound and resumes it from the next play, which comes from the render loop and not
  from an input event; a browser may refuse that, so the first sound after a pause was lost again. Fix: `Assets.preloadSounds()`
  creates every effect at start-up (`Assets.SOUND_EFFECTS`, checked by `SoundPreloadTest`), and `WebLauncher.keepAudioAwake()`
  turns `Howler.autoSuspend` off and resumes a not-running context inside every key, pointer and touch event. The root cause
  was read from the Howler 2.2.4 source and the backend, not reproduced in a browser: the author has to listen in the web
  build (first jump after load, and after more than 30 s of idle).
- **Sound starts late after silence (macOS desktop, #316, open).** Reported: after about a second without any sound the
  next one is audible only from ~0.5 s on (its start is lost); while sounds follow one another all are heard. The
  symptoms match an output pipeline that idles after a silent second and needs ~0.5 s to wake (Bluetooth, some macOS
  outputs). Mitigation, desktop only: `KimeriaGame.updateAudioKeepAlive` loops `keepalive.wav` (-65 dBFS RMS noise, see
  [SOUND_DIRECTION.md](SOUND_DIRECTION.md)) through the same `loops` map while `playSounds` is on; it stops when sounds are
  switched off and on `dispose`, and is restarted if its handle is gone. `Lwjgl3Launcher` enables it
  (`KimeriaGame.setAudioKeepAlive`); `-Dkimeria.nokeepalive=true` turns it off for an A/B comparison; the web build does not
  use it (it has its own hardening above). With `-Dkimeria.debug=true` the console logs once per sound the time `play()`
  took and the keep-alive state. The Lwjgl3 audio config (`setAudioConfig`: 16 sources, buffer size 512, count 9) is left
  alone: buffer size and count are used only by `AudioDevice` (raw PCM streaming), not by `Sound`, so shrinking them cannot
  change this latency. **Not verified on hardware:** the cause is a hypothesis and nobody has listened on a Mac; the author
  must test with the affected output device (#316 stays open until then; remove this note when it is closed).
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

## Particles and the allocation guard

Built under [#285](https://github.com/jlogicgames/edgard_in_kimeria/issues/285) (steps 0 to 5, 2026-11-11 and 2026-11-12) so that
the particle code of the game allocates nothing per frame and the dust and grit of
[#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239) are instances of one shared system. This section describes
what is on `main`; the numbers are read from the code and the tests named next to them.

### The rule and the two guards

`AGENTS.md` ("No allocation in `update` and `render`") is the rule; "Per-frame allocation" under "Architecture" summarises it. Two checks
enforce it, for different reasons, and neither replaces the other:

- **`AllocationBudgetTest`** (runtime, `:core:test`, so part of `check`). `AllocationProbe` runs a scenario through a no-op `Batch` and
  reads `ThreadMXBean.getThreadAllocatedBytes` for the current thread; each scenario asserts a named ceiling in bytes per frame, with the
  measured values in the constant's Javadoc. The test fails, never skips, if the JVM cannot report per-thread allocation
  (`theProbeCanReadAllocatedBytes`). It sees every allocation on a path it runs (iterators, boxing, varargs, library internals) and
  nothing on a path it does not run. It is HotSpot only: escape analysis removes some allocations that a browser build would still pay,
  and it says nothing about TeaVM, GC pauses or frame time.
- **`tools/check_allocations.py`** (static; `./gradlew validateAllocations`, a dependency of `:core:check`, the desktop jar and the web
  release build; the same command runs in `.codex/hooks.json` as a PostToolUse and a Stop hook with `--hook`). It checks **changed
  lines** against `--base` (`HEAD` by default; `-PallocationBase=origin/main` for a whole PR) inside methods named `update*`, `render*`,
  `draw*` and the helpers listed in its `HOT_METHODS` table (the torch spawners, `EnvironmentFx.emitGrit`, `dustPuff`, `ceilingGrit`).
  It flags `new`, `String.format`/`valueOf`/concatenation, stream and collection-factory calls, for-each over a declared collection, and
  lambdas; it does not follow calls and has no types, so varargs and boxing are invisible to it. Existing code needs no baseline: touching
  an allocating line means fixing it. An `ALLOWED` entry must say what the excluded code carries (today one: the F1 debug overlay in
  `Level.renderDebug`). `python3 -m unittest discover -s tools -p 'test_*.py'` runs `tools/test_check_allocations.py`.

Ceilings are never raised to make a test pass: a change is reported and recorded in `docs/DECISIONS.md` with the measured value.
Current ceilings (`AllocationBudgetTest`; measured values are in the Javadoc of each constant):

| Scenario | Ceiling, B/frame | Measured |
| --- | --- | --- |
| One `Torch` | 8 | 0.1 (was 190.9 to 195.1 before the migration) |
| One 32-drop blood spray, spawn + update + render | 8 | 0.0 (was 97.6) |
| One 48-drop empowered spray | 8 | 0.0 |
| Camera-shake query as `KimeriaGame.render` makes it | 8 | 0.0 (was 24.0 for the returned `Vector2`) |
| Jump dust through `Level.fx()` | 8 | 0.0 |
| Lever shake through `Level.activateTrigger` | 16 | 4.5 |
| Lever grit burst through `Level.activateTrigger` | 16 | 4.6 |
| `BossHud.render` with an empowered boss | 104 | 96.0 |
| forest-1 (one torch, 48 `RainDrop`s) | 2,640 | 2,441.8 to 2,441.9 |
| forest-2 room 0 / room 1 / room 2 | 9,720 / 2,360 / 2,120 | 8,920.6 to 9,000.0 / 2,184.0 / 1,960.0 to 1,960.6 |
| forest-2 room 2 with an empowered spray every 60 frames through `Level.blood` | 2,120 | 1,991.9 to 1,992.6 |

The lever scenarios' 4.5 and 4.6 B are the `HashSet` that `Level.activateTrigger` creates once per lever pull, not the shake or the
particles (those read 0.0). The room figures are the harness, entities and level code; the particle objects contribute almost nothing.
The cause of forest-2 room 0's large `update` share was never attributed.

### The shared system: `effects.KimeriaParticleSystem`

Fixed struct-of-arrays storage allocated in the constructor (`float[] x, y, fromX, fromY, toX, toY, life, maxLife, size, r, g, b`,
`byte[] kind`). `spawn(kind, startX, startY, endX, endY, life, size, r, g, b)` writes the next free slot; a full pool drops the **oldest**
particle, so nothing allocates after construction and an undersized capacity is invisible except as a thinner effect. Every particle
flies in a straight line from start to end over its life (`Vector2.lerp`'s form, kept so positions match the pre-migration torch to the
float). `update(dt)` ages, moves and removes the dead, keeping spawn order; `clear()` empties it; `render(batch, region)` draws the
normal-blend particles first, then the additive ones, with one blend switch per pass (none for an empty pass), and restores white and
normal blending. Each system owns one `RandomXS128` (`random()`, the algorithm behind `MathUtils.random`), seeded in the constructor, so
a seed reproduces a run. World Y grows downward: a rising particle has an end Y smaller than its start Y.

Three kinds (alpha is multiplied by a fixed peak of 0.7; `t = 1 - life / maxLife`):

| Kind | Blend | Size | Alpha | Used by |
| --- | --- | --- | --- | --- |
| `KIND_GLOW` | additive | `size * (1 - 0.4 t)` | `0.7 * (1 - t)^2` | torch flame core, embers, sparkles |
| `KIND_SMOKE` | normal | `size * (1 + t)` | `0.7 * (1 - t)` | torch smoke, jump dust |
| `KIND_GRIT` | normal | `size` (constant) | `0.7 * (1 - t)` | ceiling grit (#239) |

Owners:

- **`Torch`** owns one system of **96** slots (`PARTICLE_CAPACITY`), drawn with `SoftDot.region()`. `TorchParticleCapacityTest` measured a
  peak of 59 live particles (40 seeds x 3 frame rates x 20,000 frames; it requires 50 % headroom).
- **`effects.KimeriaBloodSystem`** is owned by `Level` (`Level.blood(...)` sprays, `bloodParticles()` for tests) with **96** drops
  (`Level.BLOOD_CAPACITY`). It has its own arrays because a drop is ballistic (velocity, gravity, a floor to land on and splash on,
  its own colour), not a lerp between two points. A spray is 32 drops, 48 when empowered; `KimeriaBloodSystemCapacityTest` measured a
  peak of 48 (one empowered spray) and requires 100 % headroom. Drawn with the cached `Shaders.whiteQuad()`; `Boss` calls
  `level.blood(...)`, so no object is created per hit. It is cleared with the room.
- **`effects.EnvironmentFx`** (created by `Level`, `Level.fx()`) owns two more systems and the `ScreenShake` manager: **dust** (24 slots,
  `KIND_SMOKE`, 6 particles per `dustPuff`, called after the ordinary ground jump in `Player.playerJump`) and **grit** (32 slots,
  `KIND_GRIT`, 24 particles per `ceilingGrit` burst emitted over 0.6 s, called when a lever lowers a wall). Each has its own seeded
  stream (the grit seed is the dust seed XOR a constant), so the two do not disturb each other or the torches. Both ignore the
  Screen shake option; they advance only in `Level.update` (pause freezes them) and are cleared with the room.
- `SoftDot.region()`, `SoftDot.glowRegion()` and `Shaders.whiteQuad()` each return one cached `TextureRegion`; callers must not mutate it.

`ParticleGoldenTest` pins the torch and blood numerically (positions, sizes, alphas at chosen frames of a fixed seed); it passed
unchanged through the migration and must not be edited to pass. `KimeriaParticleSystemTest`, `KimeriaBloodSystemTest`,
`EnvironmentFxTest` and `ScreenShakeTest` cover the classes themselves.

### Why a custom system and not `ParticleEffect`

Decision of 2026-11-11 (option B; option A kept in reserve for large authored effects such as explosions). A spike of libGDX
`ParticleEffect` + `ParticleEffectPool` was run on 2026-11-10 in a scratch copy of the repository (never committed, so these figures
cannot be reproduced from the tree): it built under TeaVM with no reflection configuration and ran in the browser; three pooled effects
allocated 0.4 B/frame on the desktop JVM; the web bundle grew by 52,049 B raw (+4.2 %) and 14,478 B gzip; an emitter at angle 90 moved
down on screen until `flipY()` was applied, the Y-down trap of `AGENTS.md`. Option B won because the torch and blood were already tuned
by hand and can be kept exactly and pinned by a numeric test (option A can only approximate them), blood needs a floor splash and
per-particle colour that `ParticleEmitter` does not offer, and a fixed-array system exposes its state to headless tests. Not measured:
GC pauses or memory in the browser build (all allocation figures are from a desktop JVM), frame times and batch flushes
([#284](https://github.com/jlogicgames/edgard_in_kimeria/issues/284) covers batch flushes with the atlas work).

### Vendored skills and the project skills

- Six skills from [kyu-n/gdx-claude-skills](https://github.com/kyu-n/gdx-claude-skills) at commit `0887af2` (MIT) are vendored under
  `.agents/skills/`: `libgdx-particles`, `libgdx-collections-json`, `libgdx-2d-rendering`, `libgdx-application-lifecycle`,
  `libgdx-camera-viewport`, `libgdx-tiled`, each as its `SKILL.md` only, each with a project note (Y-down world, TeaVM not GWT, what
  the game does not use). No upstream tracking. [`SOURCE.md`](../.agents/skills/SOURCE.md) holds the provenance, every local edit, what
  was checked against the libGDX 1.14.2 sources and what was not, and the security review.
- **Pools:** in the 1.14.2 sources `Pools` and `ReflectionPool` are `@Deprecated`; `DefaultPool` (supplier constructors) and
  `PoolManager` exist. Any pooled object therefore uses those. `KimeriaParticleSystem` needs no pool (fixed arrays).
- Two project-owned skills: [`kimeria-libgdx-rules`](../.agents/skills/kimeria-libgdx-rules/SKILL.md) (Y-down world and Y-up text, no
  allocation, pools, cached regions, `Nearest`, the sprite-versus-VFX rule, TeaVM limits, English only) and
  [`kimeria-particles`](../.agents/skills/kimeria-particles/SKILL.md) (which class to use, the recipe to own and render an emitter, how to
  add a kind, the tests and guards). The `Kimeria` prefix on the two classes (renamed 2026-11-12, no behaviour change) exists so that
  nobody mixes them with libGDX's 3D `ParticleSystem`.

### Allocation outside the particle code

The audit of 2026-11-10 (a syntactic scan of `core/src/main/java` plus the measured profile; the call graph was not followed, and
iterators, varargs and boxing are not seen by a scan) and later measurements list these sites, which are outside #285 and allocate per
frame as of 2026-11-12 (tracked by [#303](https://github.com/jlogicgames/edgard_in_kimeria/issues/303), whose checklist includes updating this section):

| Where | Allocates | How found |
| --- | --- | --- |
| `BossHud.render` (`getProjectionMatrix().cpy()`) | `Matrix4`, 96 B per frame while the boss bar is shown | measured (`bossHud` scenario) |
| `Hud.render`, `renderInteractionHint`, `draw` | `GlyphLayout`, per frame while the FPS text, hint or zone message is shown | read from the code |
| `Carryable.update`, `Fireball.update`, `Actor.boundsOf`, `Carryable.bounds`, `Level.blocksObject` | `Rectangle`, per object per frame | sampled JFR ranking (a ranking, not a measure) and the code |
| for-each loops over lists in `Level.update`/`render` (10 of the 35 hits of `--all` are in `Level.java`), `MenuBackdrop`, `KimeriaGame` | `Iterator` | `check_allocations.py --all` |
| `HealerDialogueView.render`, `Hud.render` | string concatenation, `String.valueOf` | `check_allocations.py --all` |
| `Player.renderPassiveInteractionTargets` | `HashSet`, per call | read from the code |
| `GamepadInput.update` | `boolean[]` when the pad's button count is read | read from the code |
| `Level.activateTrigger` | `HashSet`, once per lever pull | measured (lever scenarios) |

Line numbers are omitted on purpose; `python3 tools/check_allocations.py --all` prints the current list (35 syntactic hits in per-frame
methods on 2026-11-12, two allowlisted lines). It cannot see varargs or boxing, and it does not follow calls.
