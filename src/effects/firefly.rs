//! One firefly: alternates between hiding and drifting along a curve.
//!
//! Two variants share this one emitter type: a level's firefly is a plain
//! black silhouette (faithful to the original), while the main menu/About
//! screen's is a warm, pulsing point of light with its own glow halo — see
//! the `is_menu` branches throughout `drive_fireflies`.

use bevy::prelude::*;
use bevy::sprite::Anchor;
use rand::Rng;

use crate::camera::MainCamera;
use crate::core::{GamePos, ZLayer, z};
use crate::level::LevelEntity;

use super::ParticleTextures;

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

pub(super) fn drive_fireflies(
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
                    // explicitly via `flight.glow` each frame instead.
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
