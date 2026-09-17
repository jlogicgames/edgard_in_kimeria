//! Enemy types: bats, and yellow and red mobs.
//!
//! The shared parts are components (`Enemy`, `MoveRange`, `Patrol`) and each
//! behaviour is its own system, so a bat simply has no [`Gravity`].

use bevy::prelude::*;
use bevy::sprite::Anchor;

use crate::animation::{AnimationPlayer, AnimationSet};
use crate::assets::GameAssets;
use crate::audio::PlaySound;
use crate::core::collision::{self, ActorBody};
use crate::core::{
    ActorState, BoxSize, ContactState, Facing, GamePos, Gravity, Grounded, Hitbox, PhysicsSet,
    Velocity, ZLayer, z,
};
use crate::level::{LevelEntity, ObjectPlacement};
use crate::player::{
    EnemyStomped, Player, PlayerKilled, PlayerRoutine, PlayerStatus, mirrored_pos,
};
use crate::{AppState, GameSettings};

const TILE_SIZE: f32 = 16.0;
const BAT_SPEED: f32 = 50.0;
const MOB_RUN_SPEED: f32 = 80.0;
/// Upward kick given to the player when they stomp a mob.
const BOUNCE_HEIGHT: f32 = 260.0;
/// `_playerInAttackRange` half-width for the red mob.
const RED_MOB_ATTACK_RANGE: f32 = 65.0;

/// Anything the player's sword can hit.
#[derive(Component)]
pub struct Enemy;

/// Flying patroller. Ignores gravity and terrain entirely.
#[derive(Component)]
pub struct Bat {
    pub vertical: bool,
}

/// Ground mob variant. The two share all movement logic and differ only in
/// whether they can attack.
#[derive(Component, Debug, Clone, Copy, PartialEq, Eq)]
pub enum MobKind {
    Yellow,
    Red,
}

#[derive(Component)]
pub struct GroundMob {
    pub kind: MobKind,
    pub target_direction: f32,
    /// Smoothed direction used only to decide which way the sprite faces.
    pub move_direction: f32,
    /// Red mob only: mid-swing.
    pub attacking: bool,
}

/// Patrol limits in world coordinates, precomputed from `offNeg`/`offPos`.
#[derive(Component, Debug, Clone, Copy)]
pub struct MoveRange {
    pub neg: f32,
    pub pos: f32,
}

/// Current travel direction for a patroller: -1 or 1.
#[derive(Component, Debug, Clone, Copy, Deref, DerefMut)]
pub struct Patrol(pub f32);

/// Set once an enemy has been killed; it plays its hit animation then despawns.
#[derive(Component)]
pub struct Dying;

pub struct EnemyPlugin;

impl Plugin for EnemyPlugin {
    fn build(&self, app: &mut App) {
        app.add_observer(on_enemy_stomped)
            .add_systems(
                FixedUpdate,
                (bat_movement, ground_mob_ai)
                    .in_set(PhysicsSet::Actors)
                    .run_if(in_state(AppState::Playing)),
            )
            .add_systems(
                Update,
                (touch_damage, red_mob_attack_state, despawn_dead_enemies)
                    .chain()
                    .run_if(in_state(AppState::Playing)),
            );
    }
}

pub fn spawn_bat(
    commands: &mut Commands,
    assets: &GameAssets,
    at: ObjectPlacement,
    vertical: bool,
    off_neg: f32,
    off_pos: f32,
) {
    let axis = if vertical { at.pos.y } else { at.pos.x };
    commands.spawn((
        Enemy,
        Bat { vertical },
        LevelEntity,
        Name::new("Bat"),
        (GamePos(at.pos), BoxSize(at.size), ZLayer(z::ACTOR)),
        (
            MoveRange {
                neg: axis - off_neg * TILE_SIZE,
                pos: axis + off_pos * TILE_SIZE,
            },
            Patrol(1.0),
            Facing::default(),
            Hitbox::circle(8.0),
        ),
        (
            ActorState::Idle,
            assets.bat.clone(),
            AnimationPlayer::default(),
            Sprite::default(),
            Anchor::TOP_LEFT,
        ),
    ));
}

pub fn spawn_yellow_mob(
    commands: &mut Commands,
    assets: &GameAssets,
    at: ObjectPlacement,
    off_neg: f32,
    off_pos: f32,
) {
    spawn_ground_mob(
        commands,
        assets.yellow_mob.clone(),
        MobKind::Yellow,
        at,
        off_neg,
        off_pos,
        "YellowMob",
    );
}

pub fn spawn_red_mob(
    commands: &mut Commands,
    assets: &GameAssets,
    at: ObjectPlacement,
    off_neg: f32,
    off_pos: f32,
) {
    spawn_ground_mob(
        commands,
        assets.red_mob.clone(),
        MobKind::Red,
        at,
        off_neg,
        off_pos,
        "RedMob",
    );
}

fn spawn_ground_mob(
    commands: &mut Commands,
    animations: AnimationSet,
    kind: MobKind,
    at: ObjectPlacement,
    off_neg: f32,
    off_pos: f32,
    name: &'static str,
) {
    commands.spawn((
        Enemy,
        GroundMob {
            kind,
            target_direction: -1.0,
            move_direction: 0.0,
            attacking: false,
        },
        LevelEntity,
        Name::new(name),
        (
            GamePos(at.pos),
            BoxSize(at.size),
            ZLayer(z::ACTOR),
            MoveRange {
                neg: at.pos.x - off_neg * TILE_SIZE,
                pos: at.pos.x + off_pos * TILE_SIZE,
            },
        ),
        (
            Velocity::default(),
            Facing::default(),
            Grounded::default(),
            ContactState::default(),
            Gravity::default(),
            Hitbox::rect(10.0, 6.0, 14.0, 26.0),
        ),
        (
            ActorState::Idle,
            animations,
            AnimationPlayer::default(),
            Sprite::default(),
            Anchor::TOP_LEFT,
        ),
    ));
}

/// Bats bounce between their two range limits on one axis.
fn bat_movement(
    time: Res<Time<Fixed>>,
    mut query: Query<(&Bat, &MoveRange, &mut Patrol, &mut GamePos), Without<Dying>>,
) {
    let dt = time.delta_secs();
    for (bat, range, mut patrol, mut pos) in &mut query {
        let axis = if bat.vertical { pos.y } else { pos.x };
        if axis >= range.pos {
            **patrol = -1.0;
        } else if axis <= range.neg {
            **patrol = 1.0;
        }
        let step = **patrol * BAT_SPEED * dt;
        if bat.vertical {
            pos.y += step;
        } else {
            pos.x += step;
        }
    }
}

/// Chase behaviour plus terrain collision, shared by both ground mobs.
fn ground_mob_ai(
    time: Res<Time<Fixed>>,
    world: Res<collision::CollisionWorld>,
    mut commands: Commands,
    player: Query<(&GamePos, &BoxSize, &Facing, &PlayerRoutine), With<Player>>,
    mut query: Query<
        (
            &mut GroundMob,
            &mut GamePos,
            &BoxSize,
            &mut Velocity,
            &mut Grounded,
            &mut ContactState,
            &mut Facing,
            &mut ActorState,
            &mut AnimationPlayer,
            &MoveRange,
            &Hitbox,
            &Gravity,
        ),
        (Without<Player>, Without<Dying>),
    >,
) {
    let dt = time.delta_secs();
    let Ok((player_pos, player_size, _player_facing, player_routine)) = player.single() else {
        return;
    };
    // Bevy flips with `Sprite::flip_x` and never moves `GamePos`, so no extra
    // offset should be applied here: `GamePos` is already the sprite's
    // world-space left edge in both facings.
    let player_x = player_pos.x;

    for (
        mut mob,
        mut pos,
        size,
        mut velocity,
        mut grounded,
        mut contact,
        mut facing,
        mut state,
        mut animation,
        range,
        hitbox,
        gravity,
    ) in &mut query
    {
        let in_range = player_x >= range.neg
            && player_x <= range.pos
            && player_pos.y + player_size.y > pos.y
            && player_pos.y < pos.y + size.y;

        let in_attack_range = {
            let player_left = player_x;
            let player_right = player_left + player_size.x;
            // This window is centred on the mob's visual centre, i.e. its
            // sprite centre when it faces the player. Bevy keeps `GamePos` at
            // the left edge, so add that shift back.
            let mob_ref_x = pos.x + if facing.right { 0.0 } else { size.x };
            player_left >= mob_ref_x - RED_MOB_ATTACK_RANGE
                && player_right <= mob_ref_x + RED_MOB_ATTACK_RANGE
                && player_pos.y + player_size.y > pos.y
                && player_pos.y < pos.y + size.y
        };

        // The red mob freezes while swinging; the yellow one never attacks.
        let can_move = !(mob.kind == MobKind::Red && mob.attacking);

        if can_move {
            velocity.x = 0.0;

            if mob.kind == MobKind::Red
                && in_attack_range
                && *player_routine == PlayerRoutine::Active
            {
                mob.attacking = true;
                *state = ActorState::Attacking;
                animation.reset();
            } else {
                if in_range {
                    // `pos.x` is already the sprite's world-space left edge
                    // in both facings (see `player_x` above).
                    let mob_x = pos.x;
                    mob.target_direction = if player_x < mob_x { -1.0 } else { 1.0 };
                    velocity.x = mob.target_direction * MOB_RUN_SPEED;
                }
                mob.move_direction = mob.move_direction.lerp(mob.target_direction, 0.1);

                *state = if velocity.x != 0.0 {
                    ActorState::Running
                } else {
                    ActorState::Idle
                };
                if (mob.move_direction < 0.0 && facing.right)
                    || (mob.move_direction > 0.0 && !facing.right)
                {
                    facing.right = !facing.right;
                }

                pos.x += velocity.x * dt;
            }
        } else if in_attack_range && *player_routine == PlayerRoutine::Active {
            // `_checkAttackCollision`: the swing itself is what damages.
            commands.trigger(PlayerKilled);
        }

        // The yellow mob kept resolving terrain even while stomped; the red mob
        // skipped it. Both are stopped here by the `Without<Dying>` filter, and
        // resolution runs for both, which only matters for a frame either way.
        let mut body = ActorBody {
            pos: &mut pos,
            velocity: &mut velocity,
            grounded: &mut grounded,
            contact: &mut contact,
            hitbox: *hitbox,
            box_width: size.x,
            facing: *facing,
        };
        collision::resolve_horizontal(&mut body, &world);
        collision::apply_gravity(body.velocity, body.pos, gravity, dt);
        collision::resolve_vertical(&mut body, &world);
    }
}

/// Clears the red mob's attack once its swing animation ends.
///
/// The `position.x += 300` teleport is carried over from `_performAttack`'s
/// completion callback ("back to initial position after attack"). It is almost
/// certainly a bug in the original, but it is load-bearing for how the fight
/// plays, so it is preserved rather than quietly fixed.
fn red_mob_attack_state(
    mut query: Query<
        (
            &mut GroundMob,
            &mut GamePos,
            &mut ActorState,
            &AnimationPlayer,
        ),
        Without<Dying>,
    >,
) {
    for (mut mob, mut pos, mut state, animation) in &mut query {
        if mob.kind == MobKind::Red && mob.attacking && animation.finished {
            mob.attacking = false;
            *state = ActorState::Idle;
            pos.x += 300.0;
        }
    }
}

/// Contact damage between enemies and the player.
///
/// Only three cases are handled here: a bat kills on touch, a yellow mob
/// resolves stomp-or-kill, and a red mob damages only mid-swing (handled in
/// [`ground_mob_ai`]).
fn touch_damage(
    mut commands: Commands,
    players: Query<
        (
            &GamePos,
            &Hitbox,
            &BoxSize,
            &Facing,
            &PlayerStatus,
            &PlayerRoutine,
        ),
        With<Player>,
    >,
    bats: Query<(&GamePos, &Hitbox, &BoxSize, &Facing), (With<Bat>, Without<Dying>)>,
    mobs: Query<(Entity, &GamePos, &Hitbox, &BoxSize, &Facing, &GroundMob), Without<Dying>>,
) {
    let Ok((pos, hitbox, size, facing, status, routine)) = players.single() else {
        return;
    };
    if *routine != PlayerRoutine::Active {
        return;
    }
    let player_box = hitbox.world_rect(mirrored_pos(**pos, hitbox, size.x, facing.right));

    for (bat_pos, bat_hitbox, bat_size, bat_facing) in &bats {
        let bat_box = bat_hitbox.world_rect(mirrored_pos(
            **bat_pos,
            bat_hitbox,
            bat_size.x,
            bat_facing.right,
        ));
        if !player_box.intersect(bat_box).is_empty() && !status.attacking {
            commands.trigger(PlayerKilled);
            return;
        }
    }

    for (entity, mob_pos, mob_hitbox, mob_size, mob_facing, mob) in &mobs {
        if mob.kind != MobKind::Yellow {
            continue;
        }
        let mob_box = mob_hitbox.world_rect(mirrored_pos(
            **mob_pos,
            mob_hitbox,
            mob_size.x,
            mob_facing.right,
        ));
        if !player_box.intersect(mob_box).is_empty() && !status.attacking {
            commands.trigger(EnemyStomped {
                entity,
                by_attack: false,
            });
            return;
        }
    }
}

/// Port of `collidedWithActor`: a hit from above (or a sword) kills the enemy,
/// anything else kills the player.
fn on_enemy_stomped(
    event: On<EnemyStomped>,
    mut commands: Commands,
    settings: Res<GameSettings>,
    mut sounds: MessageWriter<PlaySound>,
    mut players: Query<(&GamePos, &BoxSize, &mut Velocity), With<Player>>,
    mut enemies: Query<
        (
            &GamePos,
            &mut ActorState,
            &mut AnimationPlayer,
            Option<&GroundMob>,
        ),
        (With<Enemy>, Without<Player>),
    >,
) {
    let Ok((enemy_pos, mut state, mut animation, mob)) = enemies.get_mut(event.entity) else {
        return;
    };
    let Ok((player_pos, player_size, mut player_velocity)) = players.single_mut() else {
        return;
    };

    let falling_onto = player_velocity.y > 0.0 && player_pos.y + player_size.y > enemy_pos.y;

    if event.by_attack || falling_onto {
        if settings.play_sounds {
            sounds.write(PlaySound::new("bounce.wav"));
        }
        // Only a stomp bounces the player; a sword hit does not.
        if !event.by_attack && mob.is_some() {
            player_velocity.y = -BOUNCE_HEIGHT;
        }
        *state = ActorState::Hit;
        animation.reset();
        commands.entity(event.entity).insert(Dying);
    } else {
        commands.trigger(PlayerKilled);
    }
}

/// Removes an enemy once its death animation has played out.
fn despawn_dead_enemies(
    mut commands: Commands,
    query: Query<(Entity, &AnimationPlayer), With<Dying>>,
) {
    for (entity, animation) in &query {
        if animation.finished {
            commands.entity(entity).try_despawn();
        }
    }
}
