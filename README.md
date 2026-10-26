# Edgard in Kimeria

**[Play in your browser](https://jlogicgames.github.io/edgard_in_kimeria/)**

A remake and remaster of the original **1994 MS-DOS game**. In the kingdom of
Rufardus the Wise, the banished witch L'Kardina casts a curse that hides the sun
and plunges Kimeria into eternal night. A wandering warrior, Edgard, sets out to save
the kingdom, guided by an old healer.

Explore the forest, overcome enemies and cunning traps, and face its guardian in this
arcade puzzle-platformer. [Watch a playthrough of the original](https://www.youtube.com/watch?v=8hD09QE4U3Q).

The visual direction preserves the original artist's character and style while adding
richer color, finer detail and more natural illustration. See [art direction](docs/ART_DIRECTION.md).

## About the game

- Two playable levels: a three-room forest with a boss, followed by a second forest level
  and an ending.
- Sword combat, platforming, moving platforms, traps and optional collectibles.
- Instant room retries with unlimited attempts; progress saves between levels.
- English, Ukrainian, Polish, German and Belarusian; keyboard, gamepad and touch controls,
  plus a left-handed mode.

The game is currently a two-level test version. The planned full campaign spans
Forest, Swamp, Steppe and Fortress; see the [game design](docs/GAME_DESIGN.md) and
[development roadmap](docs/ROADMAP.md).

## Technology

Written in **Java** using **[libGDX](https://libgdx.com/)**. Runs on desktop
(Windows, macOS and Linux) and in the browser. Ported from the project's Flutter/Flame
version, with the Rust/Bevy version as an additional reference.

## Run locally

Install a **JDK 17 or newer**, then run from the repository root. Gradle is included
through the wrapper; no separate installation is needed.

```sh
./gradlew run       # Desktop
./gradlew runWeb    # Browser development server
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Local desktop (`run`/`runDev`) and browser development targets show FPS at the
far right and reserve space for it in the HUD. `buildDesktop` and `buildWeb`
produce release launches without that counter; the fireball stock occupies the
rightmost HUD position as three fixed 32×32 icons, with spent charges darkened
and no ammunition number. Coin icons use the same slot size.
`runDev` selects debug mode through DevHost as well as the ordinary launcher's
configuration. After updating DevHost itself, stop and restart `runDev` once:
F7 restarts the hosted game, not the dev launcher/host.

## Basic controls

| Action | Keyboard | Gamepad |
| --- | --- | --- |
| Move / vertical direction | A / D, W / S | Left stick / D-pad |
| Look while standing (hold) | W / S | Movement stick up / down |
| Jump | J | South button |
| Sword | K | West / East button |
| Pick up / mechanism | W + K | Up + West / East |
| Put down held object | S + K | Down + West / East |
| Throw held object / fireball | L | North button |
| Pause | Esc | Start |

Touch devices show on-screen controls. Language and left-handed controls are available
in Options. Empty-handed ranged attacks spend one fireball; object throws spend none.
Carry at most three charges. Two authored pickups restore one each on contact;
a full stock leaves the pickup available and displays a local hint.
See [directional controls](docs/DIRECTIONAL_CONTROLS.md) for both layouts and recovery rules.

Translations live in editable UTF-8 catalogs. See [localization](docs/LOCALIZATION.md)
for editing, validation and font generation.

## Documentation

See the [documentation index](docs/README.md) for the
[full game design](docs/GAME_DESIGN.md), [roadmap](docs/ROADMAP.md),
[full controls and gameplay rules](docs/GAMEPLAY.md),
[build instructions and technical details](docs/DEVELOPMENT.md),
[story](docs/STORY.md), [original DOS references](docs/ORIGINAL_DOS.md) and [pre-release checklist](docs/PRE_RELEASE_TODO.md).
