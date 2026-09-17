//! Tests for AABB resolution.
//!
//! This is code that is easy to get subtly wrong: collision checking has
//! three coordinate fixups whose reasons are not obvious from the
//! arithmetic, and resolution depends on list order and early exit.

use bevy::prelude::*;
use edgard_in_kimeria::core::collision::{
    self, ActorBody, BlockKind, BlockSnapshot, CollisionWorld, SurfaceSnapshot,
};
use edgard_in_kimeria::core::{ContactState, Facing, GamePos, Gravity, Grounded, Hitbox, Velocity};

/// The player's hitbox, from `Player.onLoad`.
fn player_hitbox() -> Hitbox {
    Hitbox::rect(18.0, 26.0, 11.0, 22.0)
}

/// The player's sprite box (`Player::spawn_player`'s hard-coded 48x48).
const PLAYER_BOX_WIDTH: f32 = 48.0;

fn block(kind: BlockKind, pos: Vec2, size: Vec2) -> BlockSnapshot {
    BlockSnapshot {
        entity: Entity::PLACEHOLDER,
        pos,
        size,
        kind,
    }
}

struct Actor {
    pos: GamePos,
    velocity: Velocity,
    grounded: Grounded,
    contact: ContactState,
    hitbox: Hitbox,
    facing: Facing,
}

impl Actor {
    fn new(pos: Vec2, velocity: Vec2) -> Self {
        Self {
            pos: GamePos(pos),
            velocity: Velocity(velocity),
            grounded: Grounded(false),
            contact: ContactState::default(),
            hitbox: player_hitbox(),
            facing: Facing { right: true },
        }
    }

    fn body(&mut self) -> ActorBody<'_> {
        ActorBody {
            pos: &mut self.pos,
            velocity: &mut self.velocity,
            grounded: &mut self.grounded,
            contact: &mut self.contact,
            hitbox: self.hitbox,
            box_width: PLAYER_BOX_WIDTH,
            facing: self.facing,
        }
    }
}

#[test]
fn solid_overlap_uses_the_hitbox_not_the_sprite() {
    let hitbox = player_hitbox();
    // Spans y = 20..60, straddling the actor's hitbox band of pos.y+26..+48.
    let wall = block(
        BlockKind::Solid,
        Vec2::new(100.0, 20.0),
        Vec2::new(16.0, 40.0),
    );

    // Hitbox spans x = pos.x + 18 .. + 29. At pos.x = 71 that is 89..100, which
    // touches but does not cross the block's left edge.
    assert!(!collision::overlaps(
        Vec2::new(71.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &wall
    ));
    assert!(collision::overlaps(
        Vec2::new(72.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &wall
    ));
}

#[test]
fn facing_left_mirrors_the_hitbox_in_place() {
    let hitbox = player_hitbox();

    // The sprite renders as a fixed 48-wide box anchored at `pos` regardless of
    // facing (Bevy's `flip_x` only mirrors the texture, it never moves the
    // box), so facing left must mirror the hitbox *within* that same box:
    // x = pos.x + 19 .. + 30, versus pos.x + 18 .. + 29 facing right. Only 1px
    // apart — not an anchor-relative scale flip, which would reflect the
    // hitbox all the way out past the box's left edge.
    let low_wall = block(
        BlockKind::Solid,
        Vec2::new(99.0, 20.0),
        Vec2::new(1.0, 40.0),
    );
    let high_wall = block(
        BlockKind::Solid,
        Vec2::new(110.0, 20.0),
        Vec2::new(1.0, 40.0),
    );

    // Facing right: hitbox is x = 99..110. Touches `low_wall`'s far edge (no
    // overlap) and falls just short of `high_wall`.
    assert!(collision::overlaps(
        Vec2::new(81.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &low_wall
    ));
    assert!(!collision::overlaps(
        Vec2::new(81.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &high_wall
    ));

    // Facing left: hitbox shifts 1px right, to x = 100..111. Now it clears
    // `low_wall` but just reaches `high_wall`.
    assert!(!collision::overlaps(
        Vec2::new(81.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        false,
        &low_wall
    ));
    assert!(collision::overlaps(
        Vec2::new(81.0, 0.0),
        &hitbox,
        PLAYER_BOX_WIDTH,
        false,
        &high_wall
    ));
}

#[test]
fn one_way_platform_only_catches_from_above() {
    let hitbox = player_hitbox();
    let platform = block(
        BlockKind::Platform,
        Vec2::new(0.0, 100.0),
        Vec2::new(64.0, 8.0),
    );

    // A platform compares the hitbox's *bottom* edge, so a body whose feet are
    // above the surface does not register.
    let feet_above = Vec2::new(10.0, 40.0);
    assert!(!collision::overlaps(
        feet_above,
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &platform
    ));

    // pos.y + 26 + 22 = pos.y + 48 must land inside the platform band.
    let feet_in_band = Vec2::new(10.0, 56.0);
    assert!(collision::overlaps(
        feet_in_band,
        &hitbox,
        PLAYER_BOX_WIDTH,
        true,
        &platform
    ));
}

#[test]
fn landing_snaps_to_the_top_of_a_solid_block() {
    let ground = block(
        BlockKind::Solid,
        Vec2::new(0.0, 200.0),
        Vec2::new(64.0, 16.0),
    );
    let world = CollisionWorld {
        blocks: vec![ground],
        escalators: vec![],
        falling_platforms: vec![],
    };

    // Falling, already overlapping the block.
    let mut actor = Actor::new(Vec2::new(10.0, 160.0), Vec2::new(0.0, 120.0));
    collision::resolve_vertical(&mut actor.body(), &world);

    assert!(*actor.grounded);
    assert_eq!(actor.velocity.y, 0.0);
    // block.y - hitbox.height - hitbox.offset.y = 200 - 22 - 26
    assert_eq!(actor.pos.y, 152.0);
}

#[test]
fn a_wall_hit_in_mid_air_starts_a_clamber() {
    let wall = block(
        BlockKind::Wall,
        Vec2::new(100.0, 0.0),
        Vec2::new(16.0, 64.0),
    );
    let world = CollisionWorld {
        blocks: vec![wall],
        escalators: vec![],
        falling_platforms: vec![],
    };

    let mut actor = Actor::new(Vec2::new(80.0, 0.0), Vec2::new(60.0, 0.0));
    collision::resolve_horizontal(&mut actor.body(), &world);

    assert_eq!(actor.velocity.x, 0.0);
    // block.x - hitbox.offset.x - hitbox.width = 100 - 18 - 11
    assert_eq!(actor.pos.x, 71.0);
    assert!(
        actor.contact.clambering,
        "hitting a wall while airborne enables the wall jump"
    );
}

#[test]
fn hitting_a_wall_while_moving_left_snaps_to_its_right_edge() {
    let wall = block(
        BlockKind::Wall,
        Vec2::new(100.0, 0.0),
        Vec2::new(16.0, 64.0),
    );
    let world = CollisionWorld {
        blocks: vec![wall],
        escalators: vec![],
        falling_platforms: vec![],
    };

    let mut actor = Actor::new(Vec2::new(90.0, 0.0), Vec2::new(-60.0, 0.0));
    actor.facing.right = false;
    collision::resolve_horizontal(&mut actor.body(), &world);

    assert_eq!(actor.velocity.x, 0.0);
    // block.right - box_width + hitbox.offset.x + hitbox.width
    // = 116 - 48 + 18 + 11, so the (in-place-mirrored) hitbox's left edge
    // lands exactly on the wall's right edge instead of a whole box-width away.
    assert_eq!(actor.pos.x, 97.0);
}

#[test]
fn the_same_wall_hit_while_grounded_does_not_clamber() {
    let wall = block(
        BlockKind::Wall,
        Vec2::new(100.0, 0.0),
        Vec2::new(16.0, 64.0),
    );
    let world = CollisionWorld {
        blocks: vec![wall],
        escalators: vec![],
        falling_platforms: vec![],
    };

    let mut actor = Actor::new(Vec2::new(80.0, 0.0), Vec2::new(60.0, 0.0));
    *actor.grounded = true;
    collision::resolve_horizontal(&mut actor.body(), &world);

    assert_eq!(actor.pos.x, 71.0);
    assert!(!actor.contact.clambering);
}

#[test]
fn quicksand_is_passable_but_flags_contact() {
    let sand = block(
        BlockKind::QuickSand,
        Vec2::new(100.0, 0.0),
        Vec2::new(64.0, 32.0),
    );
    let world = CollisionWorld {
        blocks: vec![sand],
        escalators: vec![],
        falling_platforms: vec![],
    };

    let mut actor = Actor::new(Vec2::new(100.0, 0.0), Vec2::new(60.0, 0.0));
    let before = actor.pos.x;
    collision::resolve_horizontal(&mut actor.body(), &world);

    assert!(actor.contact.in_quicksand);
    assert_eq!(actor.velocity.x, 60.0, "quicksand never stops movement");
    assert_eq!(actor.pos.x, before, "and never repositions the actor");
}

#[test]
fn standing_on_an_escalator_records_it_for_the_carry() {
    let escalator = SurfaceSnapshot {
        entity: Entity::from_raw_u32(7).unwrap(),
        pos: Vec2::new(0.0, 200.0),
        size: Vec2::new(32.0, 16.0),
        velocity: Vec2::new(50.0, 0.0),
        is_falling: false,
    };
    let world = CollisionWorld {
        blocks: vec![],
        escalators: vec![escalator],
        falling_platforms: vec![],
    };

    let mut actor = Actor::new(Vec2::new(0.0, 160.0), Vec2::new(0.0, 120.0));
    collision::resolve_vertical(&mut actor.body(), &world);

    assert!(*actor.grounded);
    assert_eq!(actor.contact.escalator, Some(escalator.entity));
}

#[test]
fn landing_on_an_untriggered_platform_asks_it_to_fall() {
    let platform = SurfaceSnapshot {
        entity: Entity::from_raw_u32(9).unwrap(),
        pos: Vec2::new(0.0, 200.0),
        size: Vec2::new(32.0, 16.0),
        velocity: Vec2::ZERO,
        is_falling: false,
    };
    let world = CollisionWorld {
        blocks: vec![],
        escalators: vec![],
        falling_platforms: vec![platform],
    };

    let mut actor = Actor::new(Vec2::new(0.0, 160.0), Vec2::new(0.0, 120.0));
    let outcome = collision::resolve_vertical(&mut actor.body(), &world);
    assert_eq!(outcome.trigger_fall, Some(platform.entity));

    // Rising into the same platform is lethal instead.
    let mut riser = Actor::new(Vec2::new(0.0, 160.0), Vec2::new(0.0, -120.0));
    let outcome = collision::resolve_vertical(&mut riser.body(), &world);
    assert!(outcome.hit_platform_from_below);
    assert_eq!(outcome.trigger_fall, None);
}

#[test]
fn gravity_accumulates_per_step_and_clamps_at_terminal_velocity() {
    let gravity = Gravity {
        acceleration: 9.8,
        terminal_velocity: 300.0,
        jump_force: 260.0,
    };
    let mut velocity = Velocity(Vec2::ZERO);
    let mut pos = GamePos(Vec2::ZERO);

    // Acceleration is added once per fixed step without scaling by dt.
    collision::apply_gravity(&mut velocity, &mut pos, &gravity, 1.0 / 60.0);
    assert_eq!(velocity.y, 9.8);

    for _ in 0..200 {
        collision::apply_gravity(&mut velocity, &mut pos, &gravity, 1.0 / 60.0);
    }
    assert_eq!(velocity.y, 300.0);
}
