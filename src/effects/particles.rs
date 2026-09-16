//! The generic short-lived sprite particle shared by every CPU emitter
//! (currently just the torch — fireflies and rain drive their own sprite
//! directly, since neither needs a lifespan/shrink/fade curve).

use bevy::prelude::*;
use bevy::sprite::Anchor;

use crate::core::{GamePos, ZLayer};
use crate::level::LevelEntity;

/// A short-lived sprite that moves, shrinks and fades.
#[derive(Component)]
pub struct Particle {
    pub age: f32,
    pub lifespan: f32,
    pub from: Vec2,
    pub to: Vec2,
    pub start_size: f32,
    pub end_size: f32,
    pub color: Color,
    pub start_alpha: f32,
}

/// Positions the particle along its path and fades it out; despawns at the end.
pub(super) fn advance_particles(
    mut commands: Commands,
    time: Res<Time<Real>>,
    mut query: Query<(Entity, &mut Particle, &mut GamePos, &mut Sprite)>,
) {
    let dt = time.delta_secs();
    for (entity, mut particle, mut pos, mut sprite) in &mut query {
        particle.age += dt;
        let t = (particle.age / particle.lifespan).clamp(0.0, 1.0);
        if t >= 1.0 {
            commands.entity(entity).try_despawn();
            continue;
        }
        **pos = particle.from.lerp(particle.to, t);
        let size = particle.start_size.lerp(particle.end_size, t);
        sprite.custom_size = Some(Vec2::splat(size));
        sprite.color = particle.color.with_alpha(particle.start_alpha * (1.0 - t));
    }
}

pub(super) fn spawn_particle(
    commands: &mut Commands,
    texture: Handle<Image>,
    particle: Particle,
    layer: f32,
) {
    let start = particle.from;
    let size = particle.start_size;
    commands.spawn((
        Sprite {
            image: texture,
            custom_size: Some(Vec2::splat(size)),
            color: particle.color.with_alpha(particle.start_alpha),
            ..default()
        },
        Anchor::CENTER,
        GamePos(start),
        ZLayer(layer),
        particle,
        LevelEntity,
    ));
}
