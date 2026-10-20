# Edgard in Kimeria

**[Play in your browser](https://jlogicgames.github.io/edgard_in_kimeria/)**

A remake and remaster of the original **1994 MS-DOS game**. In the kingdom of
Rufardus the Wise, the banished sorceress L'Kardina casts a curse that hides the sun
and plunges Kimeria into eternal night. A wandering warrior, Edgard, sets out to save
the kingdom, guided by an old healer.

Explore the forest, overcome enemies and cunning traps, and face its guardian in this
arcade puzzle-platformer. [Watch a playthrough of the original](https://www.youtube.com/watch?v=8hD09QE4U3Q).

## About the game

- Two playable levels: a three-room forest with a boss, followed by a second forest level
  and an ending.
- Sword combat, platforming, moving platforms, traps and optional collectibles.
- Instant room retries with unlimited attempts; progress saves between levels.
- English and Ukrainian, keyboard, gamepad and touch controls, plus a left-handed mode.

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

## Basic controls

| Action | Keyboard | Gamepad |
| --- | --- | --- |
| Move | A / D | Left stick / D-pad |
| Jump | J | South button |
| Attack / interact | K | West / East button |
| Pause | Esc | Start |

Touch devices show on-screen controls. Language and left-handed controls are available
in Options. The Shoot action is reserved for a future ranged attack.

## Documentation

See the [documentation index](docs/README.md) for the
[full game design](docs/GAME_DESIGN.md), [roadmap](docs/ROADMAP.md),
[full controls and gameplay rules](docs/GAMEPLAY.md),
[build instructions and technical details](docs/DEVELOPMENT.md),
[story](docs/STORY.md), [original DOS references](docs/ORIGINAL_DOS.md) and [pre-release checklist](docs/PRE_RELEASE_TODO.md).
