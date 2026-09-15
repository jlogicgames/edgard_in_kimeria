//! A flame: a flickering glow plus three particle emitters.
//!
//! The Dart's `_weightedSparkles` feedback loop (each sparkle nudged the glow by
//! a distance-weighted amount, decremented by a delayed callback) is replaced by
//! modulating the glow from the live particle count directly — the same visual
//! pulse without a hand-maintained running total that could drift.

use bevy::prelude::*;
use bevy::sprite::Anchor;
use rand::Rng;

use crate::core::{GamePos, ZLayer, z};
use crate::level::{LevelEntity, ObjectPlacement};

use super::ParticleTextures;
use super::particles::{Particle, spawn_particle};

#[derive(Component)]
pub struct Torch {
    pub intensity: i32,
    lit: bool,
    base_radius: f32,
    base_alpha: f32,
    flicker: f32,
    core_timer: f32,
    ember_timer: f32,
    sparkle_timer: f32,
    smoke_timer: f32,
    pub target_id: String,
}

impl Torch {
    pub fn new(intensity: i32, target_id: String) -> Self {
        // `_baseRadius` / `_baseAlpha` from `Torch.onLoad`.
        let base_radius = (4.0 + intensity as f32 * 0.06).clamp(3.0, 40.0);
        let base_alpha = (18.0 + intensity as f32 * 0.22).clamp(8.0, 220.0) / 255.0;
        Self {
            intensity,
            lit: intensity > 0,
            base_radius,
            base_alpha,
            flicker: 1.0,
            core_timer: 0.0,
            ember_timer: 0.0,
            sparkle_timer: 0.0,
            smoke_timer: 0.0,
            target_id,
        }
    }

    /// `toggleFire`. Relighting restores the Dart's fixed intensity of 200.
    pub fn set_lit(&mut self, lit: bool) {
        self.lit = lit;
        self.intensity = if lit { 200 } else { 0 };
        if lit {
            self.base_radius = (4.0 + 200.0 * 0.06_f32).clamp(3.0, 40.0);
            self.base_alpha = (18.0 + 200.0 * 0.22_f32).clamp(8.0, 220.0) / 255.0;
        }
    }

    pub fn is_lit(&self) -> bool {
        self.lit
    }
}

/// The glow disc, a child of the torch.
#[derive(Component)]
pub(super) struct TorchGlow;

/// `position` is the flame's centre, as the Dart placed torches with
/// `Anchor.center` at the middle of their Tiled object.
pub fn spawn_torch(
    commands: &mut Commands,
    at: ObjectPlacement,
    intensity: i32,
    target_id: String,
) -> Entity {
    // The level spawner passes the object's top-left; the Dart added half the
    // size to centre the flame.
    let centre = at.pos + at.size / 2.0;
    commands
        .spawn((
            Torch::new(intensity, target_id),
            GamePos(centre),
            ZLayer(z::TORCH),
            // The torch draws nothing itself, but its glow is a child sprite, so
            // it needs a transform and visibility to parent that child to.
            Transform::default(),
            Visibility::default(),
            LevelEntity,
            Name::new("Torch"),
        ))
        .id()
}

pub(super) fn drive_torches(
    mut commands: Commands,
    time: Res<Time<Real>>,
    textures: Res<ParticleTextures>,
    mut glows: Query<&mut Sprite, With<TorchGlow>>,
    mut query: Query<(Entity, &mut Torch, &GamePos, Option<&Children>)>,
) {
    let dt = time.delta_secs();
    let mut rng = rand::rng();

    for (entity, mut torch, pos, children) in &mut query {
        // Lazily attach the glow sprite the first time the torch is seen, then
        // only ever mutate it.
        let glow = children
            .into_iter()
            .flatten()
            .copied()
            .find(|child| glows.contains(*child));
        let Some(glow) = glow else {
            commands.entity(entity).with_child((
                TorchGlow,
                Sprite {
                    image: textures.glow.clone(),
                    color: Color::NONE,
                    custom_size: Some(Vec2::ZERO),
                    ..default()
                },
                Anchor::CENTER,
                Transform::default(),
            ));
            continue;
        };
        let Ok(mut glow_sprite) = glows.get_mut(glow) else {
            continue;
        };

        if !torch.lit {
            glow_sprite.color = Color::NONE;
            glow_sprite.custom_size = Some(Vec2::ZERO);
            continue;
        }

        // Flicker: ease toward a new random multiplier a few times a second.
        torch.flicker = torch
            .flicker
            .lerp(0.75 + rng.random::<f32>() * 0.6, (dt * 8.0).min(1.0));
        let radius = torch.base_radius * torch.flicker;
        // The Dart's glow was `Colors.greenAccent` behind a blur mask.
        glow_sprite.color = Color::srgb(0.4, 1.0, 0.6).with_alpha(torch.base_alpha * torch.flicker);
        glow_sprite.custom_size = Some(Vec2::splat(radius * 4.0));

        let centre = **pos;

        // Core flame: short-lived rising blobs.
        torch.core_timer -= dt;
        if torch.core_timer <= 0.0 {
            torch.core_timer = 0.04 + rng.random::<f32>() * 0.12;
            let life = 0.14 + rng.random::<f32>() * 0.18;
            spawn_particle(
                &mut commands,
                textures.glow.clone(),
                Particle {
                    age: 0.0,
                    lifespan: life,
                    from: centre,
                    to: centre + Vec2::new(0.0, -12.0),
                    start_size: 12.0,
                    end_size: 2.0,
                    color: Color::srgb(0.7, 1.0, 0.3),
                    start_alpha: 0.8,
                },
                z::TORCH,
            );
        }

        // Embers: small sparks thrown upward and outward.
        torch.ember_timer -= dt;
        if torch.ember_timer <= 0.0 {
            torch.ember_timer = 0.02 + rng.random::<f32>() * 0.12;
            for _ in 0..(3 + rng.random_range(0..5)) {
                let from = centre
                    + Vec2::new(
                        (rng.random::<f32>() - 0.5) * 8.0,
                        (rng.random::<f32>() - 0.5) * 6.0,
                    );
                let to = from
                    + Vec2::new(
                        (rng.random::<f32>() - 0.5) * 28.0,
                        -40.0 - rng.random::<f32>() * 30.0,
                    );
                spawn_particle(
                    &mut commands,
                    textures.dot.clone(),
                    Particle {
                        age: 0.0,
                        lifespan: 0.35 + rng.random::<f32>() * 0.45,
                        from,
                        to,
                        start_size: 2.2,
                        end_size: 0.6,
                        color: Color::srgb(0.6, 1.0, 0.4),
                        start_alpha: 0.94,
                    },
                    z::TORCH,
                );
            }
        }

        // Mid sparkles: the dense green burst, scaled by intensity as
        // `midSparkleBurst` was.
        torch.sparkle_timer -= dt;
        if torch.sparkle_timer <= 0.0 {
            torch.sparkle_timer = 0.08 + rng.random::<f32>() * 0.18;
            let burst = ((torch.intensity as f32 * (0.8 + rng.random::<f32>() * 0.4)) as i32)
                .clamp(10, 300)
                // The Dart spawned every spark as its own component; capping the
                // burst keeps a 300-intensity torch from adding 300 entities
                // several times a second.
                .min(40);
            for _ in 0..burst {
                let from = centre
                    + Vec2::new(
                        (rng.random::<f32>() - 0.5) * 10.0,
                        (rng.random::<f32>() - 0.5) * 8.0,
                    );
                let to = from
                    + Vec2::new(
                        (rng.random::<f32>() - 0.5) * 18.0,
                        -30.0 - rng.random::<f32>() * 60.0,
                    );
                spawn_particle(
                    &mut commands,
                    textures.dot.clone(),
                    Particle {
                        age: 0.0,
                        lifespan: 0.4 + rng.random::<f32>() * 0.8,
                        from,
                        to,
                        start_size: 1.8,
                        end_size: 0.4,
                        color: Color::srgb(0.55, 1.0, 0.35),
                        start_alpha: 0.7,
                    },
                    z::TORCH,
                );
            }
        }

        // Smoke: slow, large, grey.
        torch.smoke_timer -= dt;
        if torch.smoke_timer <= 0.0 {
            torch.smoke_timer = 0.22 + rng.random::<f32>() * 0.5;
            let from = centre + Vec2::new((rng.random::<f32>() - 0.5) * 6.0, -2.0);
            let to = from
                + Vec2::new(
                    (rng.random::<f32>() - 0.5) * 8.0,
                    -50.0 - rng.random::<f32>() * 40.0,
                );
            spawn_particle(
                &mut commands,
                textures.glow.clone(),
                Particle {
                    age: 0.0,
                    lifespan: 2.0 + rng.random::<f32>() * 2.0,
                    from,
                    to,
                    start_size: 10.0,
                    end_size: 34.0,
                    color: Color::srgb(0.32, 0.32, 0.34),
                    start_alpha: 0.78,
                },
                z::TORCH - 0.1,
            );
        }
    }
}

/// Handles the `Actionable` side of a torch: a trigger toggles its flame.
pub fn toggle_torches(
    mut events: MessageReader<crate::player::TriggerActivated>,
    mut query: Query<&mut Torch>,
) {
    for event in events.read() {
        for mut torch in &mut query {
            if torch.target_id == event.target_id {
                let lit = torch.is_lit();
                torch.set_lit(!lit);
            }
        }
    }
}
