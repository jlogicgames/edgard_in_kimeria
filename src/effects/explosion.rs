//! Spawned when a bomb goes off.

use bevy::prelude::*;
use bevy::sprite_render::MeshMaterial2d;

use crate::core::{GamePos, ZLayer, z};
use crate::level::LevelEntity;

use super::materials::{ExplosionMaterial, ExplosionParams};

/// `BombExplosionEffect(size: 64, duration: 0.7)`.
const EXPLOSION_SIZE: f32 = 64.0;
const EXPLOSION_DURATION: f32 = 0.7;

#[derive(Component, Default)]
pub struct ExplosionEffect {
    elapsed: f32,
}

pub fn spawn_explosion(commands: &mut Commands, centre: Vec2) {
    commands.spawn((
        ExplosionEffect::default(),
        GamePos(centre),
        ZLayer(z::EXPLOSION),
        LevelEntity,
        Name::new("ExplosionEffect"),
    ));
}

pub(super) fn hydrate_explosions(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<ExplosionMaterial>>,
    query: Query<Entity, (With<ExplosionEffect>, Without<Mesh2d>)>,
) {
    for entity in &query {
        commands.entity(entity).insert((
            Mesh2d(meshes.add(Rectangle::new(EXPLOSION_SIZE, EXPLOSION_SIZE))),
            MeshMaterial2d(materials.add(ExplosionMaterial {
                params: ExplosionParams {
                    size: Vec2::splat(EXPLOSION_SIZE),
                    time: 0.0,
                    progress: 0.0,
                },
            })),
        ));
    }
}

pub(super) fn advance_explosions(
    mut commands: Commands,
    time: Res<Time<Real>>,
    mut materials: ResMut<Assets<ExplosionMaterial>>,
    mut query: Query<(
        Entity,
        &mut ExplosionEffect,
        &MeshMaterial2d<ExplosionMaterial>,
    )>,
) {
    for (entity, mut effect, handle) in &mut query {
        effect.elapsed += time.delta_secs();
        let progress = (effect.elapsed / EXPLOSION_DURATION).clamp(0.0, 1.0);
        if let Some(mut material) = materials.get_mut(&handle.0) {
            material.params.time = effect.elapsed;
            material.params.progress = progress;
        }
        if progress >= 1.0 {
            commands.entity(entity).try_despawn();
        }
    }
}
