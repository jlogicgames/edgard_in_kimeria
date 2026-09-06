# Edgard in Kimeria — Bevy port

A Rust/Bevy 0.19 port of the Flutter + Flame 2D platformer. All binary assets
(sprite sheets, audio, Tiled maps and tileset) are the originals, copied
byte-for-byte; only code and shaders were rewritten.

Controls: **WASD / arrows** move, **J** jump, **K** attack, **L** interact,
**Esc** pause. Debug keys: **F1** hitbox gizmos, **F2** invulnerability,
**F3** spawn shockwave + ripple, **F4** advance level, **F5** reach a checkpoint.

## Building and running

```sh
cargo run                    # debug
cargo run --features dev     # debug + fast relink + asset hot reload
cargo run --release          # optimised
```

### Development

`--features dev` turns on two Bevy features. Neither belongs in a shipped build.

- **`file_watcher`** hot-reloads anything under `assets/`. This is the reason to
  use the feature.
- **`dynamic_linking`** links Bevy as a shared library, skipping the static link
  of the engine on every rebuild. Measured on this crate, touching one source
  file: **3.4s without, 2.5s with**. That is a smaller win than the feature's
  reputation suggests, because this crate is small and most of the 2.5s is our
  own codegen; the gap widens as the game grows. The resulting binary needs
  `libbevy_dylib` from `target/`, so it is not redistributable, and toggling the
  feature forces a full rebuild.

Debug builds still compile dependencies at `opt-level = 3` (`[profile.dev.package."*"]`).
Without that, sprite decoding and the tilemap upload make the game unplayable;
our own crate stays at `opt-level = 1` so it remains cheap to rebuild.

### Hot reload

With `--features dev`, saving a file under `assets/` takes effect in the running
game — no restart:

| Edit | Result |
| --- | --- |
| `assets/shaders/*.wgsl` | `PipelineCache` sees `AssetEvent::Modified` and re-queues every pipeline built from that shader, so the effect changes live. On a WGSL error the game keeps running and `naga` logs a diagnostic with the offending line, but that effect **stops drawing** until the file is valid again — saving a fix restores it in the same process. |
| `assets/images/**` | The sprite sheet is swapped in place. Atlas layouts are built once at startup from the image's dimensions, so **changing a sheet's size needs a restart**. |
| `assets/audio/**` | Picked up on the next play of that sound. |
| `assets/tiles/*.tmx`, `Forest.tsx` | The asset reloads, but entities were already spawned from the old data. Press **F4** to reload the level. |

Rust code is not hot reloaded; that needs a rebuild.

### Release

```sh
cargo build --release
```

`[profile.release]` sets `lto = "fat"`, `codegen-units = 1` and
`strip = "symbols"` — the usual shipping trade of build time for run-time and
size. Measured from clean: **6 minutes**, producing a **56 MB** binary. No
frame-rate comparison against a plain `--release` build has been made, so treat
the settings as convention rather than a tuned result.

The binary resolves assets from `BEVY_ASSET_ROOT`, else `CARGO_MANIFEST_DIR`
(set only when launched through cargo), else **the directory containing the
executable**. So a distribution is the binary with `assets/` beside it:

```
edgard_in_kimeria
assets/
├── audio/  images/  shaders/  tiles/
```

Copying only the executable fails loudly rather than silently: `AppState::Loading`
panics naming the first asset it could not find, with the full path it looked in.

Verified by running the release binary from `/` with `CARGO_MANIFEST_DIR` and
`BEVY_ASSET_ROOT` unset.

## Layout

Each Dart file or directory maps to one Bevy plugin.

| Dart | Rust | Contents |
| --- | --- | --- |
| `main.dart`, `edgard_in_kimeria.dart` | `src/main.rs`, `src/lib.rs` | App setup, `AppState`, shared resources |
| — | `src/assets.rs` | Preload + atlas construction (new; see below) |
| `actor.dart`, `custom_hitbox.dart`, `mixins/` | `src/core/` | Position, velocity, hitbox, gravity, AABB resolution |
| `player.dart` | `src/player.rs` | Input, movement, attack, death, checkpoints |
| `enemy/` | `src/enemy.rs` | Bat, yellow mob, red mob |
| `levels/level.dart`, `environment/` | `src/level.rs` | `.tmx` loading, object-layer spawning |
| `items/` | `src/items.rs` | Collectables, bomb, checkpoint, trigger, actionable wall |
| `objects/` | `src/objects.rs` | Escalator, falling platform |
| `effects/` | `src/effects/` | Shader quads, post process, CPU particles |
| `overlay/` | `src/ui.rs` | HUD, main menu, pause, game over |
| `flame_audio` calls | `src/audio.rs` | One-shot sounds |
| `debugMode = true` | `src/dev.rs` | Gizmos, debug keys, capture harness |

`assets/shaders/*.wgsl` are translations of `shaders/*.frag`.

## Coordinate convention

Gameplay runs in **Tiled space** — origin at the map's top-left, `+y` down,
entities anchored top-left — because that is what Flame used, so the ported
physics is a literal translation rather than a sign-flipped rewrite. `GamePos`
holds it and `core::sync_transforms` is the single place that projects onto
Bevy's y-up `Transform`. Nothing else writes `Transform.translation` for
gameplay entities.

## Architecture mapping

**Inheritance and mixins → components and systems.** `Actor` + `GravityMixin` +
`CollideMixin` reached into the owning component's mutable state; a `Bat`
inherited velocity and ground state it never used. Now `Velocity`, `Gravity`,
`Hitbox`, `Grounded` and `ContactState` are plain data, the resolution logic is
free functions in `core::collision`, and a bat simply has no `Gravity`. The
Dart's list order and `break`-on-first-contact are preserved exactly — resolving
against a different block first moves the actor somewhere else — by snapshotting
the world into `CollisionWorld` once per fixed step.

**Animation state machines → an enum component.** `ActorState` is written freely
by gameplay systems; `AnimationSet`/`AnimationPlayer` notice the change and drive
the atlas index. Three partly-overlapping Dart `State` enums (one of which
shadowed another) collapsed into one.

**`await animationTicker.completed` → explicit routines.** The Dart suspended
mid-`update` and resumed an arbitrary number of frames later with no guarantee
the entity still existed. `PlayerRoutine` (`Dying` → `Reappearing`, or
`LeavingLevel`) makes each wait a state the schedule can see, and
`AnimationPlayer::finished` is polled where the future was awaited.

**Callback overrides → events and observers.** `EnemyStomped` and `PlayerKilled`
are observer events; `TriggerActivated` and `LoadLevel`/`AdvanceLevel` are
buffered messages. Nothing needs a handle on anything else, so `Trigger` no
longer walks `parent.children` looking for matching `Actionable`s.

**Flutter overlays → `bevy_ui` + state-scoped entities.** Each menu is tagged
`DespawnOnExit(state)`, removing the class of bug the Dart risked whenever an
`overlays.add` and its matching `overlays.remove` lived in different files.
`bevy_ui` over `bevy_egui`: the overlays are four small node trees and a coin
counter — nothing wants immediate mode or an inspector toolkit — and staying
native means one render path, no extra dependency, and state-scoped cleanup.

**Bullet time.** The Dart scaled gameplay `dt` by hand while passing unscaled
`dt` to animation. Bevy already separates those clocks: physics runs on
`Time<Virtual>` (whose relative speed drops to 0.5 near a bat) and animation on
`Time<Real>`.

## No direct Bevy equivalent

| Flame / Flutter | Replacement |
| --- | --- |
| `images.loadAllImages()` (synchronous cache) | An `AppState::Loading` step that waits for every sheet, then builds atlas layouts once. This also removed the `Future.delayed(seconds: 1)` the Dart used to paper over the same race on level change. |
| `CameraComponent.withFixedResolution` | `ScalingMode::AutoMin`, which letterboxes identically. |
| `camera.moveTo(target, speed:)` | An explicit capped move toward the target in `camera::follow_player`, same 500 px/s. |
| `camera.backdrop` + `ParallaxComponent` | A tiled sprite parented to the camera, scrolled and wrapped by `scroll_backdrop`. |
| `Decorator` re-recording the scene and calling `toImageSync` every frame | A real render-graph post-process node in `Core2d`'s `PostProcess` set. No per-frame CPU rasterisation. |
| `PostProcess` (Flame) | The same node — ripple and glitch share one full-screen pass. |
| Flutter shader hot reload | Equivalent, via the `dev` feature: `PipelineCache` rebuilds pipelines on `AssetEvent::Modified`, so editing a `.wgsl` changes the running game. See [Hot reload](#hot-reload). |
| `AudioPool` + a silent priming play | Nothing. Bevy decodes on a mixer thread and an `AudioPlayer` entity is cheap, so a sound is an entity that despawns when it finishes. |
| `MaskFilter.blur`, `BlendMode.plus` | A generated radial-gradient texture, alpha blended. See deviations. |

## Feature parity

Verified by scripted capture runs (`src/dev.rs`) unless noted.

- [x] Player movement, jump, gravity, coyote time, quicksand slowdown
- [x] Wall clamber and wall jump
- [x] Tile collision — solid, one-way platform, wall, quicksand
- [x] Tilemap rendering from the unmodified `.tmx` / `.tsx`
- [x] Bat — horizontal and vertical patrol, lethal on contact, bullet time in range
- [x] Yellow mob — chase in range, stomp to kill, bounce
- [x] Red mob — chase, attack swing, damage mid-swing
- [x] Sword attack killing enemies
- [x] Collectables — coin (counter + ripple), heart (shockwave)
- [x] Bomb — explosion shader, kills player
- [x] Falling platform — warning torch, delay, drop, removal
- [x] Escalator — patrol, carries the player, trigger toggles it
- [x] Trigger → actionable wall removal and torch toggle
- [x] Checkpoint → next level, with wrap
- [x] Death, respawn, life count, game over
- [x] Torch, firefly, fog, rain ambience
- [x] Ripple and chroma glitch post process
- [x] HUD, main menu, pause menu, game over screen
- [x] Audio playback (needs Bevy's non-default `wav` feature; every sound is
      8-bit PCM `.wav` and rodio panics without it)
- [ ] Touch controls (joystick, jump button) — **dropped by decision**;
      `showControls` was `false` in the Dart, so they were already dead code.
      The unused `assets/images/HUD/*` art is not committed.

## Deliberate deviations

Changes where matching the original exactly would have been wrong or impossible.

- **Camera snaps on level load** instead of gliding in from the world origin.
  The Dart's `moveTo(..., speed: 500)` in `onLoad` was meant to *place* the
  camera; because Flame rebuilt the camera per level at the origin, it produced
  a long pan before play became visible.
- **Rain is a fixed recycled pool.** Each Dart drop scheduled a replacement on
  completion *and* the first drop spawned 60 more, so the population grew
  without bound.
- **Torch sparkle bursts are capped at 40.** The Dart spawned one component per
  spark, up to 300, several times a second.
- **Straight alpha, not additive.** The shockwave and torch glow used
  `BlendMode.plus`; reproducing that needs a custom blend state in `specialize`,
  and on this dark backdrop the difference is a slightly softer glow.
- **Ripple and chroma glitch share one pass.** Both sampled the finished frame;
  two passes would cost a second full-screen copy for no visual difference.
- **The chroma glitch is off by default** (`GameSettings::chroma_glitch`).
  `ChromaGlitchManager` was never constructed anywhere in the Dart, so the
  effect never ran; it is ported in full but left disabled so the game looks as
  it did.
- **Pause actually freezes the player.** `game.pause()` only stopped enemies —
  they check `isGameStarted`, but the player's `update` kept running physics.
- **Circle hitboxes are tested as their bounding box.** Coins and bombs had an
  8px `CircleHitbox` inscribed in a 16x16 sprite; the difference is under a
  pixel at the corners, and it keeps every contact test on one code path.
- **`bomb_explosion.frag` used a reversed `smoothstep`** (`smoothstep(0.18, 0.0, x)`).
  GLSL tolerates it; WGSL leaves `low >= high` undefined and Metal returns 0,
  which erased the effect. Rewritten as `1.0 - smoothstep(0.0, 0.18, x)`.

## Faithfully preserved oddities

These look like bugs and are kept because they change how the game plays.

- Four deaths, not three, before game over: the Dart decremented `numberOfLives`
  only when it was already above zero.
- The red mob teleports 300px right when its attack animation ends
  (`position.x += 300`, commented "back to initial position after attack").
- Object-layer offsets are ignored. `forest.tmx`'s `SpawnPoints` layer carries
  `offsety="-16"`, and the Dart read `spawnPoint.y` raw; honouring it would
  shift every entity in that level by 16px against the original.
- Gravity is added per fixed *step*, not scaled by `dt`.
- Escalators add only their x velocity to the rider, even when vertical.
- Checkpoints and triggers are invisible — their animations were commented out.
- Landing on a falling platform *from below* kills the player.

## Development

```sh
cargo test      # 15 tests
cargo clippy
```

`tests/collision.rs` covers the ported AABB resolution — the code most likely to
drift, since `checkCollision` has three coordinate fixups whose reasons are not
obvious from the arithmetic. Each case is derived from the Dart, not from the
Rust. `tests/triggers.rs` covers the trigger/actionable wiring headlessly,
because confirming it by playing needs the player to stand in a 16px box and
press a key on exactly the right frame.

Everything else was checked with scripted capture runs from `src/dev.rs` — a
windowed game cannot be verified from a unit test.

```sh
EIK_CAPTURE=/tmp/shots EIK_CAPTURE_INPUT=run EIK_CAPTURE_LEVEL=1 \
  EIK_CAPTURE_SHOTS=8 EIK_CAPTURE_INTERVAL=0.5 cargo run
```

`EIK_CAPTURE_INPUT` accepts `run`, `left`, `fx` (also fires the shader effects),
`cycle` (hops between levels), `checkpoint` (reaches one, exercising the
disappear animation and its three-second delay), and `pause`. `EIK_CAPTURE_DELAY` lingers on the
main menu first. `EIK_INVULNERABLE`, `EIK_DEBUG_DRAW`, `EIK_CHROMA_GLITCH` and
`EIK_DEBUG_TILEMAP` set the matching switches at startup.
