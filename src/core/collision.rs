//! AABB collision resolution.
//!
//! Resolution walks the world's three lists (collision blocks, then
//! escalators, then falling platforms) in that order and breaks on the first
//! resolved contact. Order and early-exit are load-bearing — resolving against a
//! different block first moves the actor somewhere else — so [`CollisionWorld`]
//! snapshots the world once per step, keeping both exact and letting the
//! resolution functions stay plain and callable in the same sequence.

use bevy::prelude::*;

use super::{ContactState, Facing, GamePos, Grounded, Hitbox, Velocity};

/// What a collision block does on contact. Modeled as an enum so the states
/// the resolver actually branches on are mutually exclusive by construction,
/// rather than independent `bool` fields that could contradict each other.
#[derive(Component, Debug, Default, Clone, Copy, PartialEq, Eq)]
pub enum BlockKind {
    /// Blocks from every side.
    #[default]
    Solid,
    /// One-way: only stops a downward-moving actor.
    Platform,
    /// Passable, but slows movement to 10%.
    QuickSand,
    /// Solid, and grants the wall-jump/clamber state when hit airborne.
    Wall,
}

/// A static collidable from the map's `Collisions` object layer, or a `Wall`
/// actionable that a trigger can switch off.
#[derive(Component, Debug, Clone, Copy)]
pub struct CollisionBlock {
    pub kind: BlockKind,
    /// `Wall` actionables set this false when triggered, removing them from
    /// collision without despawning.
    pub active: bool,
}

impl Default for CollisionBlock {
    fn default() -> Self {
        Self {
            kind: BlockKind::default(),
            active: true,
        }
    }
}

/// Marks a moving platform that carries the actor along its x velocity.
#[derive(Component, Debug, Clone, Copy)]
pub struct EscalatorSurface {
    pub active: bool,
    /// Direction * speed, in Tiled space. Read by the player to inherit motion.
    pub velocity: Vec2,
}

/// Marks a platform that falls away shortly after being stepped on.
#[derive(Component, Debug, Clone, Copy)]
pub struct FallingPlatformSurface {
    pub is_falling: bool,
}

#[derive(Debug, Clone, Copy)]
pub struct BlockSnapshot {
    pub entity: Entity,
    pub pos: Vec2,
    pub size: Vec2,
    pub kind: BlockKind,
}

#[derive(Debug, Clone, Copy)]
pub struct SurfaceSnapshot {
    pub entity: Entity,
    pub pos: Vec2,
    pub size: Vec2,
    /// Escalator carry velocity; zero for falling platforms.
    pub velocity: Vec2,
    /// Falling platforms only: already triggered, so do not re-trigger.
    pub is_falling: bool,
}

/// Per-step snapshot of everything an actor can stand on or bump into.
///
/// Rebuilt once per fixed step rather than queried per actor, so that every
/// actor resolves against the same world and in the same list order.
#[derive(Resource, Debug, Default)]
pub struct CollisionWorld {
    pub blocks: Vec<BlockSnapshot>,
    pub escalators: Vec<SurfaceSnapshot>,
    pub falling_platforms: Vec<SurfaceSnapshot>,
}

pub fn collect_collision_world(
    mut world: ResMut<CollisionWorld>,
    blocks: Query<(Entity, &GamePos, &super::BoxSize, &CollisionBlock)>,
    escalators: Query<(Entity, &GamePos, &super::BoxSize, &EscalatorSurface)>,
    platforms: Query<(Entity, &GamePos, &super::BoxSize, &FallingPlatformSurface)>,
) {
    world.blocks.clear();
    world.escalators.clear();
    world.falling_platforms.clear();

    for (entity, pos, size, block) in &blocks {
        if !block.active {
            continue;
        }
        world.blocks.push(BlockSnapshot {
            entity,
            pos: **pos,
            size: **size,
            kind: block.kind,
        });
    }
    for (entity, pos, size, escalator) in &escalators {
        if !escalator.active {
            continue;
        }
        world.escalators.push(SurfaceSnapshot {
            entity,
            pos: **pos,
            size: **size,
            velocity: escalator.velocity,
            is_falling: false,
        });
    }
    for (entity, pos, size, platform) in &platforms {
        world.falling_platforms.push(SurfaceSnapshot {
            entity,
            pos: **pos,
            size: **size,
            velocity: Vec2::ZERO,
            is_falling: platform.is_falling,
        });
    }
}

/// Left edge of the hitbox in world space, accounting for facing.
///
/// Bevy's sprite renders as a fixed box anchored at `actor_pos` — `flip_x`
/// only mirrors the texture inside it, it never moves the box — so facing
/// left must mirror the hitbox *in place* within that same `box_width`,
/// rather than reflecting it out past the box the way a naive anchor-relative
/// flip (`scale.x = -1`) would — that reflection leaves the hitbox sitting
/// entirely outside the drawn sprite.
fn hitbox_left_x(actor_pos_x: f32, hitbox: &Hitbox, box_width: f32, facing_right: bool) -> f32 {
    if facing_right {
        actor_pos_x + hitbox.offset.x
    } else {
        actor_pos_x + box_width - hitbox.offset.x - hitbox.size.x
    }
}

/// One adjustment looks arbitrary but is deliberate: quicksand and one-way
/// platforms shift the tested edge so the actor sinks into the former and
/// lands only on top of the latter.
pub fn overlaps(
    actor_pos: Vec2,
    hitbox: &Hitbox,
    box_width: f32,
    facing_right: bool,
    block: &BlockSnapshot,
) -> bool {
    let actor_x = actor_pos.x + hitbox.offset.x;
    let actor_y = actor_pos.y + hitbox.offset.y;
    let width = hitbox.size.x;
    let height = hitbox.size.y;

    let mut fixed_x = hitbox_left_x(actor_pos.x, hitbox, box_width, facing_right);
    if block.kind == BlockKind::QuickSand {
        fixed_x = actor_x;
    }

    let fixed_y = if block.kind == BlockKind::Platform {
        actor_y + height
    } else {
        actor_y
    };

    fixed_y < block.pos.y + block.size.y
        && actor_y + height > block.pos.y
        && fixed_x < block.pos.x + block.size.x
        && fixed_x + width > block.pos.x
}

/// Convenience for surfaces, tested the same way as non-platform blocks.
fn overlaps_surface(
    actor_pos: Vec2,
    hitbox: &Hitbox,
    box_width: f32,
    facing_right: bool,
    surface: &SurfaceSnapshot,
) -> bool {
    overlaps(
        actor_pos,
        hitbox,
        box_width,
        facing_right,
        &BlockSnapshot {
            entity: surface.entity,
            pos: surface.pos,
            size: surface.size,
            kind: BlockKind::Solid,
        },
    )
}

/// Mutable view of the actor state the resolvers touch, so both the player and
/// the two ground-based mobs can share one implementation without a trait.
pub struct ActorBody<'a> {
    pub pos: &'a mut GamePos,
    pub velocity: &'a mut Velocity,
    pub grounded: &'a mut Grounded,
    pub contact: &'a mut ContactState,
    pub hitbox: Hitbox,
    /// Width of the rendered sprite box, for mirroring the hitbox in place
    /// when facing left — see [`hitbox_left_x`].
    pub box_width: f32,
    pub facing: Facing,
}

/// Port of `checkHorizontalCollisions`.
pub fn resolve_horizontal(body: &mut ActorBody, world: &CollisionWorld) {
    for block in &world.blocks {
        match block.kind {
            BlockKind::QuickSand => {
                body.contact.in_quicksand = overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                );
            }
            BlockKind::Wall => {
                if overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                ) {
                    if body.velocity.x > 0.0 {
                        body.velocity.x = 0.0;
                        body.pos.x = block.pos.x - body.hitbox.offset.x - body.hitbox.size.x;
                        if !**body.grounded {
                            body.contact.clambering = true;
                        }
                        break;
                    }
                    if body.velocity.x < 0.0 {
                        body.velocity.x = 0.0;
                        body.pos.x = block.pos.x + block.size.x - body.box_width
                            + body.hitbox.offset.x
                            + body.hitbox.size.x;
                        if !**body.grounded {
                            body.contact.clambering = true;
                        }
                        break;
                    }
                } else {
                    body.contact.clambering = false;
                }
            }
            BlockKind::Solid | BlockKind::Platform => {
                if overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                ) {
                    if body.velocity.x > 0.0 {
                        body.velocity.x = 0.0;
                        body.pos.x = block.pos.x - body.hitbox.offset.x - body.hitbox.size.x;
                        break;
                    }
                    if body.velocity.x < 0.0 {
                        body.velocity.x = 0.0;
                        body.pos.x = block.pos.x + block.size.x - body.box_width
                            + body.hitbox.offset.x
                            + body.hitbox.size.x;
                        break;
                    }
                }
            }
        }
    }
}

/// Port of `applyGravity`. Note the acceleration is added per *step*, not scaled
/// by dt — see [`super::Gravity`].
pub fn apply_gravity(
    velocity: &mut Velocity,
    pos: &mut GamePos,
    gravity: &super::Gravity,
    dt: f32,
) {
    velocity.y += gravity.acceleration;
    velocity.y = velocity
        .y
        .clamp(-gravity.jump_force, gravity.terminal_velocity);
    pos.y += velocity.y * dt;
}

/// Things vertical resolution discovered that the caller must act on, since
/// resolution itself has no access to the sibling components those actions need.
#[derive(Debug, Default)]
pub struct VerticalOutcome {
    /// Landed on a platform that has not started falling yet: trigger its fall.
    pub trigger_fall: Option<Entity>,
    /// Struck a falling platform while moving upward, which for the player is
    /// a death.
    pub hit_platform_from_below: bool,
}

pub fn resolve_vertical(body: &mut ActorBody, world: &CollisionWorld) -> VerticalOutcome {
    let mut outcome = VerticalOutcome::default();
    body.contact.escalator = None;

    for block in &world.blocks {
        match block.kind {
            BlockKind::Platform => {
                if overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                ) && body.velocity.y > 0.0
                {
                    body.velocity.y = 0.0;
                    body.pos.y = block.pos.y - body.hitbox.size.y - body.hitbox.offset.y;
                    **body.grounded = true;
                    break;
                }
            }
            BlockKind::QuickSand => {
                if overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                ) && body.velocity.y > 0.0
                {
                    body.velocity.y = 0.0;
                    **body.grounded = true;
                    break;
                }
            }
            BlockKind::Solid | BlockKind::Wall => {
                if overlaps(
                    **body.pos,
                    &body.hitbox,
                    body.box_width,
                    body.facing.right,
                    block,
                ) {
                    if body.velocity.y > 0.0 {
                        body.velocity.y = 0.0;
                        body.pos.y = block.pos.y - body.hitbox.size.y - body.hitbox.offset.y;
                        **body.grounded = true;
                        break;
                    }
                    if body.velocity.y < 0.0 {
                        body.velocity.y = 0.0;
                        body.pos.y = block.pos.y + block.size.y - body.hitbox.offset.y;
                    }
                }
            }
        }
    }

    for escalator in &world.escalators {
        if overlaps_surface(
            **body.pos,
            &body.hitbox,
            body.box_width,
            body.facing.right,
            escalator,
        ) && body.velocity.y > 0.0
        {
            body.velocity.y = 0.0;
            body.pos.y = escalator.pos.y - body.hitbox.size.y - body.hitbox.offset.y;
            **body.grounded = true;
            body.contact.escalator = Some(escalator.entity);
            break;
        }
    }

    for platform in &world.falling_platforms {
        if overlaps_surface(
            **body.pos,
            &body.hitbox,
            body.box_width,
            body.facing.right,
            platform,
        ) {
            if body.velocity.y > 0.0 {
                body.velocity.y = 0.0;
                body.pos.y = platform.pos.y - body.hitbox.size.y - body.hitbox.offset.y;
                **body.grounded = true;
                if !platform.is_falling {
                    outcome.trigger_fall = Some(platform.entity);
                }
                break;
            } else {
                outcome.hit_platform_from_below = true;
            }
        }
    }

    outcome
}
