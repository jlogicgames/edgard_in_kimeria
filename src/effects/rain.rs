//! A single falling drop, recycled to the top rather than respawned.
//!
//! The Dart had each drop schedule a *new* `RainDrop` component on completion
//! and also spawn 60 more on first load, so the population grew without bound.
//! Recycling a fixed pool keeps the same look with a stable entity count.

use bevy::prelude::*;
use rand::Rng;

use crate::LOGICAL_RESOLUTION;
use crate::camera::MainCamera;
use crate::core::GamePos;

#[derive(Component)]
pub struct RainDrop {
    pub(super) area: Vec2,
    pub(super) speed: f32,
    pub(super) wind: f32,
}

pub(super) fn drive_rain(
    time: Res<Time<Real>>,
    mut query: Query<(&mut RainDrop, &mut GamePos)>,
    camera: Query<&Transform, With<MainCamera>>,
) {
    let dt = time.delta_secs();
    let mut rng = rand::rng();
    let camera_x = camera
        .single()
        .map(|transform| transform.translation.x)
        .unwrap_or(0.0);

    for (mut drop, mut pos) in &mut query {
        pos.y += drop.speed * dt;
        pos.x += drop.wind * dt;

        if pos.y > drop.area.y + 40.0 {
            // Respawn above the view, spread across the visible width.
            pos.y = -20.0;
            pos.x =
                camera_x - LOGICAL_RESOLUTION.x / 2.0 + rng.random::<f32>() * LOGICAL_RESOLUTION.x;
            drop.speed = 400.0 + rng.random::<f32>() * 80.0;
        }
    }
}
