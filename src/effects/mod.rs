//! Visual effects: shader quads (shockwave, explosion, fog) and CPU particles
//! (torch, fireflies, rain).
//!
//! Each effect gets its own submodule; this file only wires them into one
//! `EffectsPlugin` and holds the [`ParticleTextures`] every CPU particle
//! shares.
//!
//! # Positioning
//!
//! Sprites elsewhere in the game are anchored top-left, but a shader quad is a
//! centred mesh, so effects put their **centre** in `GamePos`. `sync_transforms`
//! projects it the same way either case.
//!
//! # Scheduling
//!
//! The Dart drove every particle with nested `Future.delayed` chains that kept
//! firing after their component was removed (each guarded by an `isMounted`
//! check bolted on afterwards). Here emitters are timers on components, so a
//! despawned emitter simply stops existing.

use bevy::prelude::*;
use bevy::sprite_render::Material2dPlugin;

pub mod materials;
pub mod postprocess;

mod ambience;
mod explosion;
mod firefly;
mod fog;
mod particles;
mod rain;
mod shockwave;
mod torch;

pub use ambience::spawn_ambient_effects;
pub use explosion::{ExplosionEffect, spawn_explosion};
pub use firefly::{Firefly, MenuFirefly};
pub use fog::FogEffect;
pub use particles::Particle;
pub use postprocess::spawn_ripple;
pub use rain::RainDrop;
pub use shockwave::{ShockwaveEffect, spawn_shockwave};
pub use torch::{Torch, spawn_torch, toggle_torches};

use crate::AppState;
use materials::{ExplosionMaterial, FogMaterial, ShockwaveMaterial};

/// Soft radial dot used for every glow particle, generated rather than shipped
/// so there is no new binary asset in a port that reuses the originals.
#[derive(Resource)]
pub struct ParticleTextures {
    pub glow: Handle<Image>,
    pub dot: Handle<Image>,
}

pub struct EffectsPlugin;

impl Plugin for EffectsPlugin {
    fn build(&self, app: &mut App) {
        app.add_plugins((
            Material2dPlugin::<ShockwaveMaterial>::default(),
            Material2dPlugin::<ExplosionMaterial>::default(),
            Material2dPlugin::<FogMaterial>::default(),
        ))
        .add_plugins(postprocess::PostProcessPlugin)
        .add_systems(Startup, generate_particle_textures)
        .add_systems(
            Update,
            // Hydration must precede the matching advance, or an effect loses
            // its first frame of animation.
            (
                (
                    shockwave::hydrate_shockwaves,
                    explosion::hydrate_explosions,
                    fog::hydrate_fog,
                ),
                (
                    shockwave::advance_shockwaves,
                    explosion::advance_explosions,
                    fog::advance_fog,
                ),
            )
                .chain(),
        )
        .add_systems(
            Update,
            (
                particles::advance_particles,
                torch::drive_torches,
                rain::drive_rain,
                torch::toggle_torches,
            )
                .run_if(in_state(AppState::Playing)),
        )
        .add_systems(
            Update,
            // Also drives the main menu/About screen's ambient fireflies, so
            // this can't be limited to `Playing` like the rest of this set.
            firefly::drive_fireflies.run_if(|state: Res<State<AppState>>| {
                matches!(
                    state.get(),
                    AppState::Playing | AppState::MainMenu | AppState::About
                )
            }),
        );
    }
}

// ---------------------------------------------------------------------------
// Generated particle textures
// ---------------------------------------------------------------------------

fn generate_particle_textures(mut commands: Commands, mut images: ResMut<Assets<Image>>) {
    commands.insert_resource(ParticleTextures {
        glow: images.add(radial_gradient(32, 2.0)),
        dot: images.add(radial_gradient(8, 8.0)),
    });
}

/// A white disc whose alpha falls off as `(1 - r)^falloff`.
///
/// A high `falloff` gives a hard-edged dot (fireflies, rain); a low one gives
/// the blurred glow the Dart got from `MaskFilter.blur`.
fn radial_gradient(size: u32, falloff: f32) -> Image {
    use bevy::asset::RenderAssetUsages;
    use bevy::image::Image;
    use bevy::render::render_resource::{Extent3d, TextureDimension, TextureFormat};

    let mut data = Vec::with_capacity((size * size * 4) as usize);
    let centre = (size as f32 - 1.0) / 2.0;
    let radius = size as f32 / 2.0;
    for y in 0..size {
        for x in 0..size {
            let d = Vec2::new(x as f32 - centre, y as f32 - centre).length() / radius;
            let alpha = (1.0 - d).clamp(0.0, 1.0).powf(falloff);
            data.extend_from_slice(&[255, 255, 255, (alpha * 255.0) as u8]);
        }
    }
    Image::new(
        Extent3d {
            width: size,
            height: size,
            depth_or_array_layers: 1,
        },
        TextureDimension::D2,
        data,
        TextureFormat::Rgba8UnormSrgb,
        RenderAssetUsages::RENDER_WORLD | RenderAssetUsages::MAIN_WORLD,
    )
}
