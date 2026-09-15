//! Visual effects: shader quads (shockwave, explosion, fog) and CPU particles
//! (torch, fireflies, rain).
//!
//! # Positioning
//!
//! Sprites elsewhere in the game are anchored top-left, but a shader quad is a
//! centred mesh, so effects put their **centre** in [`GamePos`]. `sync_transforms`
//! projects it the same way either case.
//!
//! # Scheduling
//!
//! The Dart drove every particle with nested `Future.delayed` chains that kept
//! firing after their component was removed (each guarded by an `isMounted`
//! check bolted on afterwards). Here emitters are timers on components, so a
//! despawned emitter simply stops existing.

use bevy::prelude::*;
use bevy::sprite::Anchor;
use bevy::sprite_render::{Material2dPlugin, MeshMaterial2d};
use rand::Rng;

pub mod materials;
pub mod postprocess;

pub use postprocess::spawn_ripple;

use crate::camera::MainCamera;
use crate::core::{GamePos, ZLayer, z};
use crate::level::{LevelEntity, ObjectPlacement};
use crate::{AppState, LOGICAL_RESOLUTION};
use materials::{
    ExplosionMaterial, ExplosionParams, FogMaterial, FogParams, ShockwaveMaterial, ShockwaveParams,
};

/// `ShockwaveEffect` defaults from `collectable.dart`.
const SHOCKWAVE_DURATION: f32 = 0.6;
const SHOCKWAVE_MAX_RADIUS: f32 = 64.0;
const SHOCKWAVE_RING_WIDTH: f32 = 8.0;
/// `BombExplosionEffect(size: 64, duration: 0.7)`.
const EXPLOSION_SIZE: f32 = 64.0;
const EXPLOSION_DURATION: f32 = 0.7;

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
                (hydrate_shockwaves, hydrate_explosions, hydrate_fog),
                (advance_shockwaves, advance_explosions, advance_fog),
            )
                .chain(),
        )
        .add_systems(
            Update,
            (advance_particles, drive_torches, drive_rain, toggle_torches)
                .run_if(in_state(AppState::Playing)),
        )
        .add_systems(
            Update,
            // Also drives the main menu/About screen's ambient fireflies, so
            // this can't be limited to `Playing` like the rest of this set.
            drive_fireflies.run_if(|state: Res<State<AppState>>| {
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

// ---------------------------------------------------------------------------
// Shader effects
// ---------------------------------------------------------------------------

/// Expanding ring, spawned when a heart is collected.
#[derive(Component, Default)]
pub struct ShockwaveEffect {
    elapsed: f32,
}

/// Spawned when a bomb goes off.
#[derive(Component, Default)]
pub struct ExplosionEffect {
    elapsed: f32,
}

/// Full-view fog. Only spawned for the main menu/About screen — see
/// `MenuFog` in `ui.rs` — not any level, despite the shader being a port of
/// the `forest` level's original effect.
#[derive(Component, Default)]
pub struct FogEffect {
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

pub fn spawn_explosion(commands: &mut Commands, centre: Vec2) {
    commands.spawn((
        ExplosionEffect::default(),
        GamePos(centre),
        ZLayer(z::EXPLOSION),
        LevelEntity,
        Name::new("ExplosionEffect"),
    ));
}

/// Attaches the mesh and material once, so spawn helpers need only `Commands`.
fn hydrate_shockwaves(
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
                    // The Dart converted pixels to UV by dividing by the larger
                    // quad dimension; the quad is square, so both use `extent`.
                    max_radius: (SHOCKWAVE_MAX_RADIUS / extent).clamp(0.0, 1.0),
                    width: (SHOCKWAVE_RING_WIDTH / extent).clamp(0.001, 1.0),
                },
            })),
        ));
    }
}

fn hydrate_explosions(
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

/// Fog is parented to the camera so it always covers the view, which is what
/// the Dart achieved by copying `camera.visibleWorldRect` into the component
/// every frame.
fn hydrate_fog(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<FogMaterial>>,
    camera: Query<Entity, With<MainCamera>>,
    query: Query<Entity, (With<FogEffect>, Without<Mesh2d>)>,
) {
    let Ok(camera) = camera.single() else {
        return;
    };
    for entity in &query {
        commands
            .entity(entity)
            .insert((
                Mesh2d(meshes.add(Rectangle::new(LOGICAL_RESOLUTION.x, LOGICAL_RESOLUTION.y))),
                MeshMaterial2d(materials.add(FogMaterial {
                    params: FogParams {
                        size: LOGICAL_RESOLUTION,
                        // Uniform values the Dart passed literally.
                        ground_pos: 0.0,
                        ground_add: 0.0,
                        fade: 1.0,
                        time: 0.0,
                    },
                })),
                Transform::from_xyz(0.0, 0.0, z::FOG),
            ))
            .insert(ChildOf(camera));
    }
}

fn advance_shockwaves(
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

fn advance_explosions(
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

/// Rescales the fog quad to the camera's *actual* visible area every frame,
/// instead of trusting the fixed `LOGICAL_RESOLUTION` mesh it was built
/// with. `ScalingMode::AutoMin` only guarantees that much is visible — on a
/// window whose aspect ratio isn't 16:9 (a tall phone-shaped one, say) it
/// shows *more* along one axis, and a fog quad that doesn't grow to match
/// left a band of bare screen on that axis instead of covering it.
fn advance_fog(
    time: Res<Time<Real>>,
    camera: Query<&Projection, With<MainCamera>>,
    mut materials: ResMut<Assets<FogMaterial>>,
    mut query: Query<(&mut FogEffect, &MeshMaterial2d<FogMaterial>, &mut Transform)>,
) {
    let visible_size = match camera.single() {
        Ok(Projection::Orthographic(ortho)) => Some(ortho.area.size()),
        _ => None,
    };
    for (mut effect, handle, mut transform) in &mut query {
        effect.elapsed += time.delta_secs();
        if let Some(mut material) = materials.get_mut(&handle.0) {
            material.params.time = effect.elapsed;
            // The shader corrects for aspect ratio using `params.size`; if
            // it stayed fixed at `LOGICAL_RESOLUTION` while the mesh itself
            // stretched to a different aspect ratio below, the noise would
            // stretch un-evenly right along with it.
            if let Some(size) = visible_size {
                material.params.size = size;
            }
        }
        if let Some(size) = visible_size {
            transform.scale = (size / LOGICAL_RESOLUTION).extend(1.0);
        }
    }
}

// ---------------------------------------------------------------------------
// CPU particles
// ---------------------------------------------------------------------------

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
fn advance_particles(
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

fn spawn_particle(commands: &mut Commands, texture: Handle<Image>, particle: Particle, layer: f32) {
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

// --- torch ------------------------------------------------------------------

/// A flame: a flickering glow plus three particle emitters.
///
/// The Dart's `_weightedSparkles` feedback loop (each sparkle nudged the glow by
/// a distance-weighted amount, decremented by a delayed callback) is replaced by
/// modulating the glow from the live particle count directly — the same visual
/// pulse without a hand-maintained running total that could drift.
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
struct TorchGlow;

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

fn drive_torches(
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

// --- fireflies --------------------------------------------------------------

/// One firefly: alternates between hiding and drifting along a curve.
#[derive(Component)]
pub struct Firefly {
    area: Vec2,
    /// Where the wander rectangle's own corner sits in `GamePos` space.
    /// Zero for a level (whose map is itself anchored at the world origin);
    /// offset for the menu, whose camera is centred on `GamePos::ZERO`
    /// while `area` is a corner-anchored rectangle — without this the
    /// wander box would only overlap one quadrant of the visible screen.
    origin: Vec2,
    timer: f32,
    /// `None` while hidden; otherwise the current flight.
    flight: Option<Flight>,
    particle: Option<Entity>,
}

impl Firefly {
    /// `area` is the rectangle (from the origin) it wanders within; `timer`
    /// is the initial hidden-phase clock, randomized by the caller so a
    /// batch of fireflies doesn't all take flight in lockstep.
    pub fn new(area: Vec2, timer: f32) -> Self {
        Self {
            area,
            origin: Vec2::ZERO,
            timer,
            flight: None,
            particle: None,
        }
    }

    /// Shifts the wander rectangle's corner away from `GamePos::ZERO` — see
    /// the field doc on why the menu needs this and a level doesn't.
    pub fn with_origin(mut self, origin: Vec2) -> Self {
        self.origin = origin;
        self
    }
}

/// Tags a [`Firefly`] emitter (and, copied onto it, its current flight
/// particle) spawned for the main menu/About screen rather than a level, so
/// it can be cleaned up without the `LevelEntity` machinery.
#[derive(Component)]
pub struct MenuFirefly;

struct Flight {
    duration: f32,
    start: Vec2,
    control: Vec2,
    end: Vec2,
    /// The menu variant's halo child, updated alongside the core each frame
    /// since it has no `GamePos` of its own to drive a separate query.
    glow: Option<Entity>,
}

/// Menu firefly core size in pixels, before the pulse scales it. The level
/// variant stays a plain 3px dot (untouched, see `drive_fireflies`).
const MENU_FIREFLY_SIZE: f32 = 7.0;
/// The soft halo child is this many times the core's size.
const MENU_FIREFLY_GLOW_SCALE: f32 = 4.0;
/// Pulse cycles per second.
const MENU_FIREFLY_PULSE_SPEED: f32 = 3.0;

/// Keep-clear zone around the title and buttons, in `GamePos` units centred
/// on the screen (see [`Firefly::with_origin`]) — a firefly whose wander
/// starts here would spend its whole flight invisible behind opaque UI.
/// Sized from the panel's actual on-screen footprint plus a 50px margin
/// (halved, since `GamePos` and screen pixels are a 2:1 ratio at the
/// default window size) and padded further to absorb the ~60px max wander
/// radius on top of that.
const MENU_UI_EXCLUSION: Rect = Rect::new(-120.0, -140.0, 120.0, 170.0);

/// A smooth 0..1 pulse, phase-offset per firefly (from its own flight start
/// position, so a batch doesn't all pulse in lockstep without needing a
/// dedicated random field).
fn menu_firefly_pulse(now: f32, flight_start: Vec2) -> f32 {
    let phase = flight_start.x * 0.7 + flight_start.y * 0.3;
    0.5 + 0.5 * (now * MENU_FIREFLY_PULSE_SPEED + phase).sin()
}

fn drive_fireflies(
    mut commands: Commands,
    time: Res<Time<Real>>,
    textures: Res<ParticleTextures>,
    camera: Query<&Projection, With<MainCamera>>,
    mut query: Query<(&mut Firefly, Has<LevelEntity>, Has<MenuFirefly>)>,
    mut sprites: Query<(&mut GamePos, &mut Sprite), Without<Firefly>>,
    // Disjoint from `sprites`: the halo child has no `GamePos`, so this can
    // never match the same entity and Bevy accepts both `&mut Sprite`s.
    mut glow_sprites: Query<&mut Sprite, Without<GamePos>>,
) {
    let dt = time.delta_secs();
    let mut rng = rand::rng();

    // The menu's wander rectangle has to track the camera's *actual*
    // visible area every frame, the same way `advance_fog` resizes the fog
    // quad — `ScalingMode::AutoMin` shows more than `LOGICAL_RESOLUTION`
    // along whichever axis the window's aspect ratio doesn't constrain (a
    // tall, phone-shaped window shows far more than 360 units vertically),
    // and a firefly area fixed at spawn time to the 640x360 case left the
    // top and bottom thirds of a taller window with no fireflies at all.
    let camera_area = match camera.single() {
        Ok(Projection::Orthographic(ortho)) => Some(ortho.area),
        _ => None,
    };

    for (mut firefly, is_level, is_menu) in &mut query {
        if is_menu && let Some(area) = camera_area {
            firefly.area = area.size();
            firefly.origin = area.min;
        }
        firefly.timer += dt;

        // The level's firefly is a black silhouette, faithful to the
        // original; that reads as nothing against the menu's dark purple
        // fog, so the menu variant gets an actual warm glow colour instead.
        let base_color = if is_menu {
            Color::srgb(1.0, 0.8, 0.2)
        } else {
            Color::BLACK
        };

        match &firefly.flight {
            None => {
                // Hidden for 1-2s, as `_startHide`.
                if firefly.timer > 1.0 + rng.random::<f32>() {
                    let mut sample = || {
                        firefly.origin
                            + Vec2::new(
                                rng.random::<f32>() * firefly.area.x,
                                rng.random::<f32>() * firefly.area.y,
                            )
                    };
                    let mut start = sample();
                    if is_menu {
                        // Reject-sample around the title/buttons: a wander
                        // starting there would spend its whole flight
                        // hidden behind opaque UI. `MENU_UI_EXCLUSION`
                        // already pads well past the 60px max wander
                        // radius, so this converges in a try or two.
                        for _ in 0..20 {
                            if !MENU_UI_EXCLUSION.contains(start) {
                                break;
                            }
                            start = sample();
                        }
                    }
                    let control = start
                        + Vec2::new(
                            rng.random::<f32>() * 60.0 - 30.0,
                            rng.random::<f32>() * 60.0 - 30.0,
                        );
                    let end = start
                        + Vec2::new(
                            rng.random::<f32>() * 40.0 - 20.0,
                            rng.random::<f32>() * 40.0 - 20.0,
                        );
                    // The menu variant uses the blurred `glow` texture (soft
                    // circle, not a hard dot) for its core, so it reads as a
                    // point of light rather than a flat pixel.
                    let (texture, size) = if is_menu {
                        (textures.glow.clone(), MENU_FIREFLY_SIZE)
                    } else {
                        (textures.dot.clone(), 3.0)
                    };
                    let mut particle = commands.spawn((
                        Sprite {
                            image: texture,
                            color: base_color.with_alpha(0.0),
                            custom_size: Some(Vec2::splat(size)),
                            ..default()
                        },
                        Anchor::CENTER,
                        GamePos(start),
                        ZLayer(z::ACTOR),
                        Name::new("Firefly"),
                    ));
                    // Copies whichever scope tag the emitter has, so the
                    // in-flight particle is cleaned up the same way.
                    if is_level {
                        particle.insert(LevelEntity);
                    }
                    if is_menu {
                        particle.insert(MenuFirefly);
                    }
                    let entity = particle.id();

                    // A larger, dimmer halo behind the core. It's the core's
                    // child so it tracks position for free, but it has no
                    // `GamePos` of its own — its alpha/size are driven
                    // explicitly via `glow.glow` each frame instead.
                    let glow = is_menu.then(|| {
                        commands
                            .spawn((
                                Sprite {
                                    image: textures.glow.clone(),
                                    color: base_color.with_alpha(0.0),
                                    custom_size: Some(Vec2::splat(
                                        MENU_FIREFLY_SIZE * MENU_FIREFLY_GLOW_SCALE,
                                    )),
                                    ..default()
                                },
                                Anchor::CENTER,
                                ChildOf(entity),
                                MenuFirefly,
                                Name::new("FireflyGlow"),
                            ))
                            .id()
                    });

                    firefly.timer = 0.0;
                    firefly.particle = Some(entity);
                    firefly.flight = Some(Flight {
                        // 3-7s, the Dart's "2-5x longer" lifespan.
                        duration: 3.0 + rng.random::<f32>() * 4.0,
                        start,
                        control,
                        end,
                        glow,
                    });
                }
            }
            Some(flight) => {
                let t = firefly.timer / flight.duration;
                if t >= 1.0 {
                    if let Some(entity) = firefly.particle.take() {
                        // The dot (and its glow child, despawned with it) is
                        // a separate entity; a level unload may have taken
                        // it already.
                        commands.entity(entity).try_despawn();
                    }
                    firefly.timer = 0.0;
                    firefly.flight = None;
                    continue;
                }
                // Quadratic Bezier, as in the Dart renderer.
                let inv = 1.0 - t;
                let position =
                    flight.start * inv * inv + flight.control * 2.0 * inv * t + flight.end * t * t;
                // Fade in over the first half, out over the second.
                let alpha = if t < 0.5 { t * 2.0 } else { (1.0 - t) * 2.0 };

                // Dynamic pulse for the menu variant: brightness and size
                // both breathe, on top of the flight's own fade envelope.
                let pulse = is_menu.then(|| menu_firefly_pulse(time.elapsed_secs(), flight.start));

                if let Some(entity) = firefly.particle
                    && let Ok((mut pos, mut sprite)) = sprites.get_mut(entity)
                {
                    **pos = position;
                    if let Some(pulse) = pulse {
                        sprite.color = base_color.with_alpha(alpha * (0.6 + 0.4 * pulse));
                        sprite.custom_size =
                            Some(Vec2::splat(MENU_FIREFLY_SIZE * (0.8 + 0.35 * pulse)));
                    } else {
                        sprite.color = base_color.with_alpha(alpha);
                    }
                }

                if let Some(glow_entity) = flight.glow
                    && let Ok(mut glow_sprite) = glow_sprites.get_mut(glow_entity)
                {
                    let pulse = pulse.unwrap_or(0.0);
                    glow_sprite.color = base_color.with_alpha(alpha * 0.35 * (0.6 + 0.4 * pulse));
                    glow_sprite.custom_size = Some(Vec2::splat(
                        MENU_FIREFLY_SIZE * MENU_FIREFLY_GLOW_SCALE * (0.85 + 0.3 * pulse),
                    ));
                }
            }
        }
    }
}

// --- rain -------------------------------------------------------------------

/// A single falling drop, recycled to the top rather than respawned.
///
/// The Dart had each drop schedule a *new* `RainDrop` component on completion
/// and also spawn 60 more on first load, so the population grew without bound.
/// Recycling a fixed pool keeps the same look with a stable entity count.
#[derive(Component)]
pub struct RainDrop {
    area: Vec2,
    speed: f32,
    wind: f32,
}

fn drive_rain(
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

// ---------------------------------------------------------------------------
// Per-level ambience
// ---------------------------------------------------------------------------

/// Spawns the ambient effects the Dart `Level.onLoad` added per level name.
pub fn spawn_ambient_effects(commands: &mut Commands, level_name: &str, level_size: Vec2) {
    let mut rng = rand::rng();
    match level_name {
        "forest" => {
            for _ in 0..24 {
                commands.spawn((
                    Firefly::new(level_size, rng.random::<f32>() * 2.0),
                    LevelEntity,
                    Name::new("FireflyEmitter"),
                ));
            }
            // Fog is menu-only now — see `MenuFog` in `ui.rs`.
        }
        "forest-1" => {
            for _ in 0..48 {
                let wind = (rng.random::<f32>() - 0.5) * 48.0;
                commands.spawn((
                    RainDrop {
                        area: level_size,
                        speed: 400.0 + rng.random::<f32>() * 80.0,
                        wind,
                    },
                    Sprite {
                        color: Color::BLACK,
                        custom_size: Some(Vec2::new(1.2, 14.0)),
                        ..default()
                    },
                    Anchor::CENTER,
                    GamePos(Vec2::new(
                        rng.random::<f32>() * level_size.x,
                        rng.random::<f32>() * level_size.y,
                    )),
                    ZLayer(z::ACTOR),
                    LevelEntity,
                    Name::new("RainDrop"),
                ));
            }
        }
        _ => {}
    }
}
