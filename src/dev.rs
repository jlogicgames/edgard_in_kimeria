//! Development helpers: debug gizmos and a scripted capture mode.
//!
//! Flame had `debugMode = true` on every component, which drew hitboxes
//! unconditionally. Here the same information is drawn with gizmos behind
//! [`GameSettings::debug_draw`], toggled at runtime with F1.
//!
//! The capture harness exists because a windowed game cannot be verified from a
//! test: set `EIK_CAPTURE=<dir>` to auto-start a run and write a numbered PNG
//! every `EIK_CAPTURE_INTERVAL` seconds (default 1.5), then exit after
//! `EIK_CAPTURE_SHOTS` frames (default 6). `EIK_CAPTURE_LEVEL` picks which
//! level to start on, and `EIK_CAPTURE_INPUT=run` synthesises held movement and
//! periodic jumps so a capture exercises real gameplay rather than an idle
//! player: `run` holds right, `left` holds left, `fx` also fires the shader
//! effects on a cadence.

use bevy::input::InputSystems;
use bevy::prelude::*;
use bevy::render::view::screenshot::{Screenshot, save_to_disk};

use crate::core::{BoxSize, CollisionBlock, Facing, GamePos, Hitbox};
use crate::level::{AdvanceLevel, LoadLevel};
use crate::player::mirrored_pos;
use crate::player::{CheckpointReached, Player};
use crate::{AppState, GameSettings};

#[derive(Resource)]
struct Capture {
    dir: String,
    interval: f32,
    remaining: u32,
    timer: f32,
    index: u32,
    level: usize,
    /// Seconds to linger on the main menu before starting, so a capture can
    /// include it.
    delay: f32,
    elapsed: f32,
    started: bool,
}

pub struct DevPlugin;

impl Plugin for DevPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(Startup, apply_env_settings).add_systems(
            Update,
            (toggle_debug_draw, draw_debug_gizmos, spawn_test_effects),
        );
        if std::env::var("EIK_DEBUG_TILEMAP").is_ok() {
            app.add_systems(Update, debug_tilemap);
        }

        if let Ok(dir) = std::env::var("EIK_CAPTURE") {
            let interval = std::env::var("EIK_CAPTURE_INTERVAL")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(1.5);
            let shots = std::env::var("EIK_CAPTURE_SHOTS")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(6);
            let level = std::env::var("EIK_CAPTURE_LEVEL")
                .ok()
                .and_then(|v| v.parse().ok())
                .unwrap_or(0);
            app.insert_resource(Capture {
                dir,
                interval,
                remaining: shots,
                timer: 0.0,
                index: 0,
                level,
                delay: std::env::var("EIK_CAPTURE_DELAY")
                    .ok()
                    .and_then(|v| v.parse().ok())
                    .unwrap_or(0.0),
                elapsed: 0.0,
                started: false,
            })
            .add_systems(Update, run_capture);

            if matches!(
                std::env::var("EIK_CAPTURE_INPUT").as_deref(),
                Ok("run") | Ok("left") | Ok("fx") | Ok("cycle") | Ok("pause") | Ok("checkpoint")
            ) {
                // Injected after Bevy's own keyboard handling so the synthetic
                // presses survive into `Update`, where the game reads them.
                app.add_systems(PreUpdate, script_input.after(InputSystems));
            }
        }
    }
}

fn toggle_debug_draw(keys: Res<ButtonInput<KeyCode>>, mut settings: ResMut<GameSettings>) {
    if keys.just_pressed(KeyCode::F1) {
        settings.debug_draw = !settings.debug_draw;
    }
    if keys.just_pressed(KeyCode::F2) {
        settings.invulnerable = !settings.invulnerable;
    }
}

/// Applies the env-var debug switches once at startup.
fn apply_env_settings(mut settings: ResMut<GameSettings>) {
    if std::env::var("EIK_INVULNERABLE").is_ok() {
        settings.invulnerable = true;
    }
    if std::env::var("EIK_DEBUG_DRAW").is_ok() {
        settings.debug_draw = true;
    }
    if std::env::var("EIK_CHROMA_GLITCH").is_ok() {
        settings.chroma_glitch = true;
    }
}

/// Draws collision blocks and actor hitboxes in Tiled space, projected the same
/// way [`crate::core::sync_transforms`] projects everything else.
fn draw_debug_gizmos(
    settings: Res<GameSettings>,
    mut gizmos: Gizmos,
    blocks: Query<(&GamePos, &BoxSize, &CollisionBlock)>,
    actors: Query<(&GamePos, &Hitbox, &BoxSize, &Facing)>,
) {
    if !settings.debug_draw {
        return;
    }
    for (pos, size, block) in &blocks {
        if !block.active {
            continue;
        }
        draw_rect(&mut gizmos, **pos, **size, Color::srgb(0.2, 0.9, 0.4));
    }
    for (pos, hitbox, size, facing) in &actors {
        if hitbox.size == Vec2::ZERO {
            continue;
        }
        let origin = mirrored_pos(**pos, hitbox, size.x, facing.right) + hitbox.offset;
        draw_rect(&mut gizmos, origin, hitbox.size, Color::srgb(0.9, 0.3, 0.3));
    }
}

fn draw_rect(gizmos: &mut Gizmos, pos: Vec2, size: Vec2, color: Color) {
    let centre = Vec2::new(pos.x + size.x / 2.0, -(pos.y + size.y / 2.0));
    gizmos.rect_2d(centre, size, color);
}

fn run_capture(
    mut commands: Commands,
    time: Res<Time<Real>>,
    state: Res<State<AppState>>,
    mut capture: ResMut<Capture>,
    mut next_state: ResMut<NextState<AppState>>,
    mut loads: MessageWriter<LoadLevel>,
    mut exit: MessageWriter<AppExit>,
) {
    capture.elapsed += time.delta_secs();

    // Skip the menu so the capture shows gameplay, unless asked to linger.
    if !capture.started {
        capture.timer += time.delta_secs();
        if capture.timer >= capture.interval && capture.remaining > 0 {
            capture.timer = 0.0;
            capture.remaining -= 1;
            let path = format!("{}/frame-{:02}.png", capture.dir, capture.index);
            capture.index += 1;
            commands
                .spawn(Screenshot::primary_window())
                .observe(save_to_disk(path));
        }
        if capture.elapsed >= capture.delay && *state.get() == AppState::MainMenu {
            loads.write(LoadLevel(capture.level));
            next_state.set(AppState::Playing);
            capture.started = true;
        }
        return;
    }

    capture.timer += time.delta_secs();
    if capture.timer < capture.interval {
        return;
    }
    capture.timer = 0.0;

    if capture.remaining == 0 {
        exit.write(AppExit::Success);
        return;
    }
    capture.remaining -= 1;
    let path = format!("{}/frame-{:02}.png", capture.dir, capture.index);
    capture.index += 1;
    commands
        .spawn(Screenshot::primary_window())
        .observe(save_to_disk(path));
}

/// Temporary tilemap diagnostic, enabled with `EIK_DEBUG_TILEMAP=1`.
pub fn debug_tilemap(
    mut done: Local<bool>,
    time: Res<Time<Real>>,
    layers: Query<(
        Entity,
        Option<&Name>,
        &GlobalTransform,
        &InheritedVisibility,
        Option<&bevy_ecs_tiled::prelude::TileStorage>,
    )>,
) {
    if *done || time.elapsed_secs() < 2.0 {
        return;
    }
    *done = true;
    for (entity, name, transform, visible, storage) in &layers {
        if storage.is_none() && name.map(|n| !n.as_str().contains("Level")).unwrap_or(true) {
            continue;
        }
        info!(
            "TILEDBG {entity} name={:?} pos={:?} visible={} tiles={:?}",
            name.map(|n| n.to_string()),
            transform.translation(),
            visible.get(),
            storage.map(|s| s.iter().filter(|t| t.is_some()).count()),
        );
    }
}

/// Synthesises held movement plus periodic jumps and swings, so a capture walks
/// the player through the level instead of filming an idle sprite.
fn script_input(time: Res<Time<Real>>, mut keys: ResMut<ButtonInput<KeyCode>>) {
    let t = time.elapsed_secs();
    let mode = std::env::var("EIK_CAPTURE_INPUT").unwrap_or_default();
    if mode == "left" {
        keys.press(KeyCode::ArrowLeft);
    } else {
        keys.press(KeyCode::ArrowRight);
    }

    // `just_pressed` is what the attack reads, so press for exactly one frame.
    if t % 1.0 < time.delta_secs() {
        keys.press(KeyCode::KeyJ);
    } else {
        keys.release(KeyCode::KeyJ);
    }
    if t % 3.0 < time.delta_secs() {
        keys.press(KeyCode::KeyK);
    } else {
        keys.release(KeyCode::KeyK);
    }
    // `fx` mode also fires the shader effects on a cadence, so a capture shows
    // them without having to reach the one collectable that triggers each.
    if mode == "fx" && t % 2.0 < time.delta_secs() {
        keys.press(KeyCode::F3);
    } else {
        keys.release(KeyCode::F3);
    }
    // `checkpoint` mode reaches a checkpoint once, five seconds in.
    if mode == "checkpoint" && (5.0..5.0 + time.delta_secs()).contains(&t) {
        keys.press(KeyCode::F5);
    } else {
        keys.release(KeyCode::F5);
    }
    // `cycle` mode hops between levels, exercising unload and reload.
    if mode == "cycle" && t % 2.5 < time.delta_secs() {
        keys.press(KeyCode::F4);
    } else {
        keys.release(KeyCode::F4);
    }
    // `pause` mode opens the pause menu a second in and leaves it open.
    if mode == "pause" && (1.0..1.0 + time.delta_secs()).contains(&t) {
        keys.press(KeyCode::Escape);
    } else {
        keys.release(KeyCode::Escape);
    }
}

/// F3 fires a shockwave and a ripple at the player; F4 advances the level and
/// F5 reaches a checkpoint.
///
/// Both are otherwise reachable only by picking up a specific collectable in a
/// specific level, which makes them tedious to eyeball while tuning the shaders.
fn spawn_test_effects(
    mut commands: Commands,
    keys: Res<ButtonInput<KeyCode>>,
    mut advance: MessageWriter<AdvanceLevel>,
    player: Query<(&GamePos, &BoxSize), With<Player>>,
) {
    // F4 forces the level transition directly; F5 goes the long way round,
    // through the checkpoint routine, so the disappear animation and its
    // three-second delay are exercised too. Neither needs a walk to the flag.
    if keys.just_pressed(KeyCode::F4) {
        advance.write(AdvanceLevel);
    }
    if keys.just_pressed(KeyCode::F5) {
        commands.trigger(CheckpointReached);
    }
    if !keys.just_pressed(KeyCode::F3) {
        return;
    }
    let Ok((pos, size)) = player.single() else {
        return;
    };
    let centre = **pos + **size / 2.0;
    crate::effects::spawn_shockwave(&mut commands, centre);
    crate::effects::spawn_ripple(&mut commands, centre);
}
