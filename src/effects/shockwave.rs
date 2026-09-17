//! Expanding ring, spawned when a heart is collected.

use bevy::prelude::*;
use bevy::sprite_render::MeshMaterial2d;

use crate::core::{GamePos, ZLayer, z};
use crate::level::LevelEntity;

use super::materials::{ShockwaveMaterial, ShockwaveParams};

const SHOCKWAVE_DURATION: f32 = 0.6;
const SHOCKWAVE_MAX_RADIUS: f32 = 64.0;
const SHOCKWAVE_RING_WIDTH: f32 = 8.0;

#[derive(Component, Default)]
pub struct ShockwaveEffect {
    elapsed: f32,
}

/// `centre` is the world-space middle of the effect.
pub fn spawn_shockwave(commands: &mut Commands, centre: Vec2) {
    commands.spawn((
        ShockwaveEffect::default(),
        GamePos(centre),
        ZLayer(z::EXPLOSION),
        LevelEntity,
        Name::new("ShockwaveEffect"),
    ));
}

/// Attaches the mesh and material once, so spawn helpers need only `Commands`.
pub(super) fn hydrate_shockwaves(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<ShockwaveMaterial>>,
    query: Query<Entity, (With<ShockwaveEffect>, Without<Mesh2d>)>,
) {
    for entity in &query {
        let extent = SHOCKWAVE_MAX_RADIUS * 2.0;
        commands.entity(entity).insert((
            Mesh2d(meshes.add(Rectangle::new(extent, extent))),
            MeshMaterial2d(materials.add(ShockwaveMaterial {
                params: ShockwaveParams {
                    size: Vec2::splat(extent),
                    center: Vec2::splat(0.5),
                    time: 0.0,
                    progress: 0.0,
                    // Pixels convert to UV by dividing by the larger quad
                    // dimension; the quad is square, so both use `extent`.
                    max_radius: (SHOCKWAVE_MAX_RADIUS / extent).clamp(0.0, 1.0),
                    width: (SHOCKWAVE_RING_WIDTH / extent).clamp(0.001, 1.0),
                },
            })),
        ));
    }
}

pub(super) fn advance_shockwaves(
    mut commands: Commands,
    time: Res<Time<Real>>,
    mut materials: ResMut<Assets<ShockwaveMaterial>>,
    mut query: Query<(
        Entity,
        &mut ShockwaveEffect,
        &MeshMaterial2d<ShockwaveMaterial>,
    )>,
) {
    for (entity, mut effect, handle) in &mut query {
        effect.elapsed += time.delta_secs();
        let progress = (effect.elapsed / SHOCKWAVE_DURATION).clamp(0.0, 1.0);
        if let Some(mut material) = materials.get_mut(&handle.0) {
            material.params.time = effect.elapsed;
            material.params.progress = progress;
        }
        if progress >= 1.0 {
            commands.entity(entity).try_despawn();
        }
    }
}
