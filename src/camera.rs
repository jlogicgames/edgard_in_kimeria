//! Camera rig and scrolling backdrop.
//!
//! Bevy has no camera-follow or parallax built in, so fixed-resolution
//! letterboxing, anchored follow, eased movement toward a target, and a
//! scrolling backdrop are all reproduced here explicitly:
//!
//! - fixed resolution -> [`ScalingMode::AutoMin`], which letterboxes to a
//!   fixed logical resolution;
//! - anchored follow  -> [`follow_player`], converting a top-left follow
//!   target into a centre-anchored Bevy translation;
//! - eased movement    -> a capped move toward the target, at 500 px/s;
//! - backdrop          -> [`scroll_backdrop`], a tiled sprite parented to the
//!   camera so it tracks the view without entering world space.

use bevy::prelude::*;

use crate::assets::GameAssets;
use crate::core::{GamePos, z};
use crate::player::Player;
use crate::{AppState, LOGICAL_RESOLUTION};

/// Camera follow speed, in px/s.
const FOLLOW_SPEED: f32 = 500.0;
/// `kLeftFollow` / `kUpFollow`: how far into the viewport the player sits.
const LEFT_FOLLOW: f32 = 200.0;
const UP_FOLLOW: f32 = 200.0;
/// `SkyTile.scrollSpeed`.
const BACKDROP_SCROLL_SPEED: f32 = 5.0;

#[derive(Component)]
pub struct MainCamera;

/// Cleared whenever a level loads, so the first frame snaps to the player
/// instead of gliding in from the world origin.
#[derive(Component, Default)]
pub struct CameraPlaced(pub bool);

#[derive(Component)]
struct Backdrop {
    /// Width of one tile of the sky image, used to wrap the scroll offset.
    tile_width: f32,
}

pub struct CameraPlugin;

impl Plugin for CameraPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnExit(AppState::Loading), spawn_camera)
            .add_systems(
                Update,
                (
                    follow_player,
                    scroll_backdrop.run_if(in_gameplay),
                    sync_backdrop_visibility,
                ),
            );
    }
}

fn spawn_camera(mut commands: Commands, assets: Res<GameAssets>, images: Res<Assets<Image>>) {
    let sky = assets.image("images/background/sky.png");
    let tile_width = images
        .get(&sky)
        .map(|image| image.size().x as f32)
        .unwrap_or(LOGICAL_RESOLUTION.x);

    commands
        .spawn((
            Camera2d,
            Projection::Orthographic(OrthographicProjection {
                // Letterbox to the authored 640x360 rather than showing more
                // world on a wider window, matching `withFixedResolution`.
                scaling_mode: bevy::camera::ScalingMode::AutoMin {
                    min_width: LOGICAL_RESOLUTION.x,
                    min_height: LOGICAL_RESOLUTION.y,
                },
                ..OrthographicProjection::default_2d()
            }),
            MainCamera,
            CameraPlaced(false),
            // Uniform block read by the screen-space post process.
            crate::effects::postprocess::ScreenEffects::default(),
            Name::new("MainCamera"),
        ))
        .with_child((
            Sprite {
                image: sky,
                custom_size: Some(Vec2::new(
                    // One extra tile of slack so wrapping never shows a seam.
                    LOGICAL_RESOLUTION.x + tile_width,
                    LOGICAL_RESOLUTION.y,
                )),
                image_mode: SpriteImageMode::Tiled {
                    tile_x: true,
                    tile_y: false,
                    stretch_value: 1.0,
                },
                ..default()
            },
            Transform::from_xyz(0.0, 0.0, z::BACKGROUND),
            Backdrop { tile_width },
            Name::new("Backdrop"),
        ));
}

/// The follow target is computed as the camera's *top-left* point offset from
/// the player, with a wider lead when facing left. Bevy positions a camera by
/// its centre, so the target is that top-left plus half the viewport, then
/// negated into y-up.
fn follow_player(
    time: Res<Time<Real>>,
    player: Query<(&GamePos, &crate::core::Facing), With<Player>>,
    mut camera: Query<(&mut Transform, &mut CameraPlaced), With<MainCamera>>,
) {
    let Ok((pos, facing)) = player.single() else {
        return;
    };
    let Ok((mut transform, mut placed)) = camera.single_mut() else {
        return;
    };

    // The player's hitbox is 11 px wide; that's folded into the offsets below.
    const HITBOX_WIDTH: f32 = 11.0;
    let top_left = if facing.right {
        Vec2::new(pos.x - LEFT_FOLLOW - HITBOX_WIDTH, pos.y - UP_FOLLOW)
    } else {
        Vec2::new(
            pos.x - LEFT_FOLLOW * 2.0 - HITBOX_WIDTH * 3.0,
            pos.y - UP_FOLLOW,
        )
    };

    let centre = top_left + LOGICAL_RESOLUTION / 2.0;
    let target = Vec3::new(centre.x, -centre.y, transform.translation.z);

    if !placed.0 {
        transform.translation = target;
        placed.0 = true;
        return;
    }

    let delta = target - transform.translation;
    let max_step = FOLLOW_SPEED * time.delta_secs();
    if delta.length() <= max_step {
        transform.translation = target;
    } else {
        transform.translation += delta.normalize() * max_step;
    }
}

/// Continuous horizontal scroll, wrapped at one tile width.
fn scroll_backdrop(time: Res<Time<Real>>, mut query: Query<(&Backdrop, &mut Transform)>) {
    for (backdrop, mut transform) in &mut query {
        transform.translation.x -= BACKDROP_SCROLL_SPEED * time.delta_secs();
        if transform.translation.x <= -backdrop.tile_width {
            transform.translation.x += backdrop.tile_width;
        }
    }
}

/// The sky only belongs to an actual level (`forest-1` / `forest`), so it
/// runs and shows for the states a level is loaded in — not the other way
/// round. Whitelisting the gameplay states, rather than blacklisting each
/// menu, means a future menu state (there have already been three: MainMenu,
/// About, Options) can't reintroduce this by omission.
fn is_gameplay_state(state: &AppState) -> bool {
    matches!(
        state,
        AppState::Playing | AppState::Paused | AppState::GameOver
    )
}

fn in_gameplay(state: Res<State<AppState>>) -> bool {
    is_gameplay_state(state.get())
}

/// See [`is_gameplay_state`]: the backdrop is visible only while a level is loaded.
fn sync_backdrop_visibility(
    state: Res<State<AppState>>,
    mut query: Query<&mut Visibility, With<Backdrop>>,
) {
    let Ok(mut visibility) = query.single_mut() else {
        return;
    };
    *visibility = if is_gameplay_state(state.get()) {
        Visibility::Visible
    } else {
        Visibility::Hidden
    };
}
