# Edgard in Kimeria

A 2D platformer built with Rust and Bevy 0.19.

Controls: **WASD / arrows** move, **J**/**Z** jump, **K**/**X** attack,
**L**/**C** interact, **Esc** pause. Debug keys: **F1** hitbox gizmos, **F2** invulnerability,
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
├── audio/  fonts/  images/  shaders/  tiles/
```

Copying only the executable fails loudly rather than silently: `AppState::Loading`
panics naming the first asset it could not find, with the full path it looked in.

Verified by running the release binary from `/` with `CARGO_MANIFEST_DIR` and
`BEVY_ASSET_ROOT` unset.

### Web

```sh
rustup target add wasm32-unknown-unknown
cargo install trunk
trunk serve            # http://127.0.0.1:8080, rebuilds on change
trunk build --release  # writes dist/
```

The `wasm32-unknown-unknown` target needs WebGL2 (`bevy/webgl2`, since wgpu's
WebGPU backend isn't broadly supported yet) and `getrandom`'s `wasm_js`
backend, enabled via a `--cfg` in `.cargo/config.toml`; both are wired up
already, so a plain `trunk build` picks them up. `Trunk.toml` sets
`public_url` for the GitHub Pages path this repo publishes to
(`.github/workflows/deploy-web.yml`, on every push to `main`).

## Assets

`assets/fonts/NanoPlus.ttf` (button labels) and `assets/fonts/QuestSquare.ttf`
(everything else — headings, body copy, the HUD counter) are pixel fonts from
[spicygame.itch.io/fonts](https://spicygame.itch.io/fonts). Grab any other
face from that same page if a future screen needs one, so the whole UI keeps
a consistent source. Note that QuestSquare's glyphs sit noticeably smaller in
their em-box than NanoPlus's — matching them visually needs a bigger nominal
`font_size` on the QuestSquare side, not the same number.

## Coordinate convention

Gameplay runs in **Tiled space** — origin at the map's top-left, `+y` down,
entities anchored top-left. `GamePos` holds it and `core::sync_transforms` is
the single place that projects onto Bevy's y-up `Transform`. Nothing else
writes `Transform.translation` for gameplay entities.

## Architecture

**Components and systems, not inheritance.** `Velocity`, `Gravity`, `Hitbox`,
`Grounded` and `ContactState` are plain data; the resolution logic is free
functions in `core::collision`, and an entity simply omits the components it
doesn't need — a bat has no `Gravity`. Collision resolution snapshots the
world into `CollisionWorld` once per fixed step, so list order and
break-on-first-contact stay deterministic.

**Animation is one enum component.** `ActorState` is written freely by
gameplay systems; `AnimationSet`/`AnimationPlayer` notice the change and drive
the atlas index.

**Waits are explicit routine states.** `PlayerRoutine` (`Dying` →
`Reappearing`, or `LeavingLevel`) makes each animation-gated wait a state the
schedule can see, polled via `AnimationPlayer::finished`.

**Events and observers, not callbacks.** `EnemyStomped` and `PlayerKilled` are
observer events; `TriggerActivated` and `LoadLevel`/`AdvanceLevel` are
buffered messages. Nothing needs a handle on anything else.

**`bevy_ui` + state-scoped entities for menus.** Each menu is tagged
`DespawnOnExit(state)`, so it can't outlive the state it belongs to.
`bevy_ui` over `bevy_egui`: the overlays are four small node trees and a coin
counter — nothing wants immediate mode or an inspector toolkit — and staying
native means one render path, no extra dependency, and state-scoped cleanup.

**Bullet time.** Physics runs on `Time<Virtual>` (whose relative speed drops
to 0.5 near a bat) and animation on `Time<Real>`, so slowing gameplay never
slows the animation clock.

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
- [x] Torch, firefly ambience
- [x] Ripple and chroma glitch post process
- [x] HUD, main menu, pause menu, game over screen
- [x] Audio playback (needs Bevy's non-default `wav` feature; every sound is
      8-bit PCM `.wav` and rodio panics without it)
- [ ] Touch controls (joystick, jump button) — **dropped by decision**; the
      unused `assets/images/HUD/*` art is not committed.

## Deliberate design choices

- **Camera snaps on level load** instead of gliding in from the world origin,
  so play is visible immediately rather than after a long pan.
- **Fog lives on the main menu/About screen**, not in a level. `ui.rs`'s
  `MenuFog` spawns the `FogEffect`/`FogMaterial` shader behind the menu panel,
  parented to the camera so it doubles as the menu's animated backdrop.
- **Torch sparkle bursts are capped at 40** to keep particle counts bounded.
- **Straight alpha, not additive**, for the shockwave and torch glow —
  additive blending needs a custom blend state in `specialize`, and on this
  dark backdrop the difference is a slightly softer glow.
- **Ripple and chroma glitch share one pass.** Both sample the finished frame;
  two passes would cost a second full-screen copy for no visual difference.
- **The chroma glitch is off by default** (`GameSettings::chroma_glitch`), so
  the game looks as intended without it. It's implemented in full and can be
  toggled at startup — see `EIK_CHROMA_GLITCH` below.
- **Pause actually freezes the player**, not just enemies.
- **Circle hitboxes are tested as their bounding box.** Coins and bombs have
  an 8px circular hitbox inscribed in a 16x16 sprite; the difference is under
  a pixel at the corners, and it keeps every contact test on one code path.
- **`bomb_explosion.frag` uses `1.0 - smoothstep(0.0, 0.18, x)`** rather than a
  reversed-argument `smoothstep(0.18, 0.0, x)` — WGSL leaves `low >= high`
  undefined and Metal returns 0, which would erase the effect.

## Faithfully preserved oddities

These look like bugs and are kept because they change how the game plays.

- Four deaths, not three, before game over: the life count only decrements
  while it's already above zero.
- The red mob teleports 300px right when its attack animation ends.
- Object-layer offsets are ignored. `forest.tmx`'s `SpawnPoints` layer carries
  `offsety="-16"`, which is read raw rather than honoured; applying it would
  shift every entity in that level by 16px.
- Gravity is added per fixed *step*, not scaled by `dt`.
- Escalators add only their x velocity to the rider, even when vertical.
- Checkpoints and triggers are invisible — their animations were commented out.
- Landing on a falling platform *from below* kills the player.

## Development

```sh
cargo test      # 15 tests
cargo clippy
```

`tests/collision.rs` covers AABB resolution — the code most likely to drift,
since it has three coordinate fixups whose reasons are not obvious from the
arithmetic. `tests/triggers.rs` covers the trigger/actionable wiring headlessly,
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
main menu first. `EIK_CAPTURE_ABOUT` goes to the About screen instead of
loading a level — the only way to see a non-gameplay menu's actual layout
without a mouse. **Unlike every other switch on this page, it only exists in
a `--features dev` build: the code behind it is `#[cfg(feature = "dev")]`, so
a plain `cargo run` / release build does not compile that branch at all — the
env var does nothing there, not even a lookup, because the `std::env::var`
call itself is not in the binary.** `EIK_INVULNERABLE`, `EIK_DEBUG_DRAW`,
`EIK_CHROMA_GLITCH` and `EIK_DEBUG_TILEMAP` set the matching switches at
startup and, like the rest of this harness, work in every build.
