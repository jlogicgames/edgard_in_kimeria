//! Actor primitives shared by the player and every enemy.
//!
//! The Flame original expressed these as class inheritance (`Actor`) plus two
//! mixins (`GravityMixin`, `CollideMixin`) that reached into the owning
//! component's mutable state. Here they are plain data components; the behaviour
//! that used to live in the mixins lives in [`collision`] as free functions that
//! the player and enemy systems call in the same order the Dart did.

use bevy::prelude::*;

pub mod collision;

pub use collision::{BlockKind, CollisionBlock, CollisionWorld};

/// Position of an entity's **top-left corner** in Tiled space (y grows downward).
///
/// See the crate docs for why gameplay does not use [`Transform`] directly.
#[derive(Component, Debug, Default, Clone, Copy, Deref, DerefMut)]
pub struct GamePos(pub Vec2);

/// Width/height of the entity's sprite box, in Tiled pixels.
#[derive(Component, Debug, Default, Clone, Copy, Deref, DerefMut)]
pub struct BoxSize(pub Vec2);

/// Render depth. Mirrors Flame's integer `priority`.
#[derive(Component, Debug, Default, Clone, Copy, Deref, DerefMut)]
pub struct ZLayer(pub f32);

/// Z values, kept in one place so layering is auditable.
///
/// `bevy_ecs_tiled` stacks a map's layers *downward* from the map entity, one
/// `TiledMapLayerZOffset` step each, so a two-layer map spawned at `TILEMAP`
/// renders as low as -200. The backdrop therefore sits far below that rather
/// than just behind it, while staying inside the orthographic near plane
/// (-1000).
pub mod z {
    pub const BACKGROUND: f32 = -900.0;
    pub const TILEMAP: f32 = 0.0;
    pub const RIPPLE: f32 = 0.0;
    pub const ACTOR: f32 = 1.0;
    pub const TORCH: f32 = 2.0;
    pub const EXPLOSION: f32 = 100.0;
    pub const FOG: f32 = 1000.0;
}

#[derive(Component, Debug, Default, Clone, Copy, Deref, DerefMut)]
pub struct Velocity(pub Vec2);

/// Which way the actor faces. Replaces Flame's `scale.x` sign trick, which the
/// original collision math had to compensate for; [`collision::overlaps`] keeps
/// that compensation, now driven by this flag instead of a render transform.
#[derive(Component, Debug, Clone, Copy)]
pub struct Facing {
    pub right: bool,
}

impl Default for Facing {
    fn default() -> Self {
        Self { right: true }
    }
}

/// Collision box relative to [`GamePos`]. A non-zero `radius` selects a circle,
/// matching `CustomHitbox`'s "radius or rect" discriminator.
#[derive(Component, Debug, Default, Clone, Copy)]
pub struct Hitbox {
    pub offset: Vec2,
    pub size: Vec2,
    pub radius: f32,
}

impl Hitbox {
    pub fn rect(offset_x: f32, offset_y: f32, width: f32, height: f32) -> Self {
        Self {
            offset: Vec2::new(offset_x, offset_y),
            size: Vec2::new(width, height),
            radius: 0.0,
        }
    }

    pub fn circle(radius: f32) -> Self {
        Self {
            offset: Vec2::ZERO,
            size: Vec2::splat(radius * 2.0),
            radius,
        }
    }

    /// World-space AABB of the hitbox for an actor at `pos`.
    pub fn world_rect(&self, pos: Vec2) -> Rect {
        let min = pos + self.offset;
        Rect::from_corners(min, min + self.size)
    }
}

/// Gravity tuning. The Dart added `gravityAcceleration` to velocity once per
/// *fixed step* rather than scaling by dt, so these are per-step values, not
/// per-second ones. Preserved verbatim to keep jump arcs identical.
#[derive(Component, Debug, Clone, Copy)]
pub struct Gravity {
    pub acceleration: f32,
    pub terminal_velocity: f32,
    pub jump_force: f32,
}

impl Default for Gravity {
    fn default() -> Self {
        Self {
            acceleration: 9.8,
            terminal_velocity: 300.0,
            jump_force: 260.0,
        }
    }
}

/// Set by vertical collision resolution each fixed step.
#[derive(Component, Debug, Default, Clone, Copy, Deref, DerefMut)]
pub struct Grounded(pub bool);

/// Contact flags the Dart kept as loose `bool` fields on `CollideMixin`.
#[derive(Component, Debug, Default, Clone, Copy)]
pub struct ContactState {
    pub in_quicksand: bool,
    /// Pressed against a wall while airborne — enables the wall jump.
    pub clambering: bool,
    /// Brief lockout after a wall jump so input cannot cancel the push-off.
    pub wall_jumping: bool,
    pub wall_jump_timer: f32,
    /// Escalator the actor is standing on this step, if any.
    pub escalator: Option<Entity>,
}

/// The animation states an actor can be in. Union of the Dart `ActorState` and
/// the per-enemy `State` enums, which were three separate, partly-overlapping
/// enums that could not be handled generically.
#[derive(Component, Debug, Default, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ActorState {
    #[default]
    Idle,
    Running,
    Jumping,
    Falling,
    Hit,
    Attacking,
    Appearing,
    Disappearing,
    Climbing,
}

/// Fixed physics rate. Flame ran an explicit accumulator at this step.
pub const FIXED_TIMESTEP_HZ: f64 = 60.0;

/// Ordering for the fixed-step physics pipeline.
#[derive(SystemSet, Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum PhysicsSet {
    /// Snapshot every collidable into [`CollisionWorld`].
    Collect,
    /// Integrate and resolve actors against that snapshot.
    Actors,
}

pub struct CorePlugin;

impl Plugin for CorePlugin {
    fn build(&self, app: &mut App) {
        app.init_resource::<CollisionWorld>()
            .insert_resource(Time::<Fixed>::from_hz(FIXED_TIMESTEP_HZ))
            .configure_sets(
                FixedUpdate,
                (PhysicsSet::Collect, PhysicsSet::Actors).chain(),
            )
            .add_systems(
                FixedUpdate,
                collision::collect_collision_world.in_set(PhysicsSet::Collect),
            )
            // Runs in PostUpdate so it sees every gameplay write from this frame.
            .add_systems(PostUpdate, sync_transforms);
    }
}

/// Projects Tiled-space gameplay position onto Bevy's y-up [`Transform`].
///
/// The single place where the two coordinate systems meet.
pub fn sync_transforms(mut query: Query<(&GamePos, &ZLayer, &mut Transform)>) {
    for (pos, z, mut transform) in &mut query {
        transform.translation = Vec3::new(pos.x, -pos.y, **z);
    }
}
