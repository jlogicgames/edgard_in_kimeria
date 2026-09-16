//! The player controller, ported from `player.dart`.
//!
//! The Dart class mixed input, physics, animation, damage and camera work into
//! one 570-line component whose async methods (`await animationTicker.completed`)
//! suspended mid-update and resumed frames later. Here the same behaviour is
//! split into systems, and every wait that used to be an `await` is an explicit
//! [`PlayerRoutine`] the schedule can see.

use bevy::prelude::*;
use bevy::sprite::Anchor;

use crate::animation::AnimationPlayer;
use crate::assets::GameAssets;
use crate::audio::PlaySound;
use crate::core::collision::{self, ActorBody};
use crate::core::{
    ActorState, BoxSize, ContactState, Facing, GamePos, Gravity, Grounded, Hitbox, PhysicsSet,
    Velocity, ZLayer, z,
};
use crate::level::{AdvanceLevel, LevelEntity, ObjectPlacement};
use crate::{AppState, GameSettings};

const MOVE_SPEED: f32 = 100.0;
const GRAVITY: f32 = 9.8;
const JUMP_FORCE: f32 = 260.0;
const TERMINAL_VELOCITY: f32 = 300.0;
/// `kNumberOfTries`. Note the Dart decremented *before* testing for zero, so
/// this allows four deaths, not three; kept as-is.
const NUMBER_OF_TRIES: i32 = 3;
/// Below this y the player has fallen out of the level.
const DEATH_PLANE_Y: f32 = 380.0;
/// Bullet time engages within this distance of a bat.
const BAT_SLOWDOWN_RANGE: f32 = 50.0;
const SLOW_TIME_SCALE: f32 = 0.5;
/// How long the wall-jump push-off ignores steering input.
const WALL_JUMP_LOCKOUT: f32 = 0.1;

#[derive(Component)]
pub struct Player;

/// Where a respawn puts the player back. Set from the level's `Player` object.
#[derive(Component, Debug, Clone, Copy, Deref)]
pub struct StartPosition(pub Vec2);

/// Per-frame intent, written by [`read_input`] and consumed by the fixed step.
#[derive(Component, Debug, Default)]
pub struct PlayerInput {
    /// -1, 0 or 1.
    pub horizontal: f32,
    pub jump_held: bool,
    /// True on the frame the attack key went down.
    pub attack_pressed: bool,
    pub interact_pressed: bool,
}

/// Player state that is neither physics nor animation.
#[derive(Component, Debug)]
pub struct PlayerStatus {
    pub lives: i32,
    pub attacking: bool,
    /// Id of the `Trigger` currently overlapped, or empty. Mirrors
    /// `collideWithTriggerId`.
    pub trigger_id: String,
}

impl Default for PlayerStatus {
    fn default() -> Self {
        Self {
            lives: NUMBER_OF_TRIES,
            attacking: false,
            trigger_id: String::new(),
        }
    }
}

/// A multi-step sequence that used to be written with `await`.
///
/// Encoding it as data means the player cannot be halfway through a respawn and
/// simultaneously accepting input, which the Dart guarded with ad-hoc
/// `isGotHit` / `isReachedCheckpoint` flags checked in three places.
#[derive(Component, Debug, Default, PartialEq)]
pub enum PlayerRoutine {
    #[default]
    Active,
    /// Playing the hit animation before being moved back to the start.
    Dying,
    /// Playing the appear animation at the start position.
    Reappearing,
    /// Reached a checkpoint; counts down before the next level loads.
    LeavingLevel { timer: f32 },
}

impl PlayerRoutine {
    /// The Dart skipped the whole physics block while either flag was set.
    fn blocks_control(&self) -> bool {
        !matches!(self, PlayerRoutine::Active)
    }
}

/// Fired when the player's attack connects, or when the player lands on an enemy.
#[derive(EntityEvent, Debug)]
pub struct EnemyStomped {
    /// The enemy that was hit.
    pub entity: Entity,
    /// True when it came from a sword swing rather than a stomp.
    pub by_attack: bool,
}

/// Fired when anything kills the player.
#[derive(Event, Debug)]
pub struct PlayerKilled;

/// Fired when the player touches a checkpoint.
#[derive(Event, Debug)]
pub struct CheckpointReached;

/// Fired when the interact key is pressed while standing in a trigger.
#[derive(Message, Debug)]
pub struct TriggerActivated {
    pub target_id: String,
}

pub struct PlayerPlugin;

impl Plugin for PlayerPlugin {
    fn build(&self, app: &mut App) {
        app.add_message::<TriggerActivated>()
            .add_observer(on_player_killed)
            .add_observer(on_checkpoint_reached)
            .add_systems(
                Update,
                // Chained: `resolve_attack` reads the just-pressed flag that
                // `read_input` writes this frame, and `apply_facing` must see
                // the facing those two settled on.
                (
                    read_input,
                    update_bullet_time,
                    advance_routines,
                    resolve_attack,
                    // Not player-specific: mirrors every actor's sprite.
                    apply_facing,
                )
                    .chain()
                    .run_if(in_state(AppState::Playing)),
            )
            .add_systems(
                FixedUpdate,
                player_physics
                    .in_set(PhysicsSet::Actors)
                    .run_if(in_state(AppState::Playing)),
            )
            // Leaving Playing must not leave the virtual clock slowed.
            .add_systems(OnExit(AppState::Playing), restore_time_scale);
    }
}

pub fn spawn_player(commands: &mut Commands, assets: &GameAssets, at: ObjectPlacement) {
    // The Dart hard-coded 48x48 and ignored the Tiled object's size, which in
    // `forest.tmx` is a non-integer 48.88. Keeping the hard-coded value.
    let size = Vec2::splat(48.0);
    // Grouped into sub-bundles by concern; a flat tuple would also exceed the
    // 15-element limit `Bundle` is implemented up to.
    commands.spawn((
        Player,
        LevelEntity,
        Name::new("Player"),
        // Placement
        (
            GamePos(at.pos),
            StartPosition(at.pos),
            BoxSize(size),
            ZLayer(z::ACTOR),
        ),
        // Physics
        (
            Velocity::default(),
            Facing::default(),
            Grounded::default(),
            ContactState::default(),
            Hitbox::rect(18.0, 26.0, 11.0, 22.0),
            Gravity {
                acceleration: GRAVITY,
                terminal_velocity: TERMINAL_VELOCITY,
                jump_force: JUMP_FORCE,
            },
        ),
        // Presentation
        (
            ActorState::Idle,
            assets.player.clone(),
            AnimationPlayer::default(),
            Sprite::default(),
            Anchor::TOP_LEFT,
        ),
        // Control
        (
            PlayerInput::default(),
            PlayerStatus::default(),
            PlayerRoutine::Active,
        ),
    ));
}

/// Port of `onKeyEvent`. WASD/arrows move, J jumps, K attacks, L interacts.
fn read_input(
    keys: Res<ButtonInput<KeyCode>>,
    gamepads: Query<&Gamepad>,
    mut next_state: ResMut<NextState<AppState>>,
    mut triggers: MessageWriter<TriggerActivated>,
    mut query: Query<(&mut PlayerInput, &PlayerStatus, &PlayerRoutine), With<Player>>,
) {
    let pause = keys.just_pressed(KeyCode::Escape)
        || gamepads
            .iter()
            .any(|g| g.just_pressed(GamepadButton::Start));
    if pause {
        next_state.set(AppState::Paused);
        return;
    }

    for (mut input, status, routine) in &mut query {
        let left = keys.any_pressed([KeyCode::KeyA, KeyCode::ArrowLeft]);
        let right = keys.any_pressed([KeyCode::KeyD, KeyCode::ArrowRight]);

        // The Dart froze steering during an attack but kept tracking the keys so
        // movement could resume when the swing finished.
        input.horizontal = if status.attacking {
            0.0
        } else {
            (right as i32 - left as i32) as f32
        };
        input.jump_held = keys.pressed(KeyCode::KeyJ) && !status.attacking;
        input.attack_pressed = keys.just_pressed(KeyCode::KeyK);
        input.interact_pressed = keys.just_pressed(KeyCode::KeyL);

        if input.interact_pressed && !routine.blocks_control() && !status.trigger_id.is_empty() {
            triggers.write(TriggerActivated {
                target_id: status.trigger_id.clone(),
            });
        }
    }
}

/// The fixed-step physics block, in the exact order `Player.update` ran it.
fn player_physics(
    time: Res<Time<Fixed>>,
    world: Res<collision::CollisionWorld>,
    settings: Res<GameSettings>,
    mut sounds: MessageWriter<PlaySound>,
    mut commands: Commands,
    escalators: Query<&collision::EscalatorSurface>,
    mut platforms: Query<&mut crate::objects::FallingPlatform>,
    mut query: Query<
        (
            &mut GamePos,
            &mut Velocity,
            &mut Grounded,
            &mut ContactState,
            &mut Facing,
            &mut ActorState,
            &mut PlayerInput,
            &PlayerStatus,
            &PlayerRoutine,
            &Hitbox,
            &BoxSize,
            &Gravity,
        ),
        With<Player>,
    >,
) {
    let dt = time.delta_secs();
    for (
        mut pos,
        mut velocity,
        mut grounded,
        mut contact,
        mut facing,
        mut state,
        mut input,
        status,
        routine,
        hitbox,
        box_size,
        gravity,
    ) in &mut query
    {
        if contact.wall_jumping {
            contact.wall_jump_timer -= dt;
            if contact.wall_jump_timer <= 0.0 {
                contact.wall_jumping = false;
            }
        }

        if routine.blocks_control() {
            continue;
        }

        if status.attacking {
            input.horizontal = 0.0;
            velocity.x = 0.0;
        }

        update_state(&mut state, &mut facing, &input, &velocity, &contact, status);

        // --- _updatePlayerMovement ---
        if input.jump_held && (**grounded || contact.clambering) {
            if settings.play_sounds {
                sounds.write(PlaySound::new("jump.wav"));
            }
            if contact.clambering {
                velocity.y = -JUMP_FORCE * 0.7;
                // Push off away from the held direction, or away from the wall
                // the player faces when there is no input. Two of these branches
                // coincide by arithmetic, not by accident: holding *into* a wall
                // on the right and merely facing it both push left.
                let push_left = if input.horizontal != 0.0 {
                    input.horizontal > 0.0
                } else {
                    facing.right
                };
                velocity.x = if push_left {
                    -JUMP_FORCE * 0.5
                } else {
                    JUMP_FORCE * 0.5
                };
                contact.clambering = false;
                contact.wall_jumping = true;
                contact.wall_jump_timer = WALL_JUMP_LOCKOUT;
            } else {
                velocity.y = -JUMP_FORCE;
                if contact.in_quicksand {
                    velocity.y *= 0.1;
                }
            }
            pos.y += velocity.y * dt;
            **grounded = false;
        }

        // Coyote time: once falling fast enough, stop counting as grounded.
        if velocity.y > GRAVITY * 15.0 {
            **grounded = false;
        }

        if !contact.wall_jumping {
            velocity.x = input.horizontal * MOVE_SPEED;
            if contact.in_quicksand {
                velocity.x *= 0.1;
            }
            if let Some(escalator) = contact.escalator
                && let Ok(surface) = escalators.get(escalator)
            {
                velocity.x += surface.velocity.x;
            }
        }
        pos.x += velocity.x * dt;

        if contact.clambering {
            velocity.y *= 0.1;
        }

        if pos.y > DEATH_PLANE_Y {
            commands.trigger(PlayerKilled);
            continue;
        }

        // --- collision resolution ---
        let mut body = ActorBody {
            pos: &mut pos,
            velocity: &mut velocity,
            grounded: &mut grounded,
            contact: &mut contact,
            hitbox: *hitbox,
            box_width: box_size.x,
            facing: *facing,
        };
        collision::resolve_horizontal(&mut body, &world);
        collision::apply_gravity(body.velocity, body.pos, gravity, dt);
        let outcome = collision::resolve_vertical(&mut body, &world);

        if let Some(entity) = outcome.trigger_fall
            && let Ok(mut platform) = platforms.get_mut(entity)
        {
            platform.trigger_fall();
        }
        if outcome.hit_platform_from_below {
            commands.trigger(PlayerKilled);
        }
    }
}

/// Port of `_updatePlayerState`.
fn update_state(
    state: &mut ActorState,
    facing: &mut Facing,
    input: &PlayerInput,
    velocity: &Velocity,
    contact: &ContactState,
    status: &PlayerStatus,
) {
    // Facing follows input only, so riding an escalator does not spin the sprite.
    if input.horizontal < 0.0 {
        facing.right = false;
    } else if input.horizontal > 0.0 {
        facing.right = true;
    }

    let mut next = ActorState::Idle;
    if input.horizontal != 0.0 {
        next = ActorState::Running;
    }
    if velocity.y > 0.0 {
        next = ActorState::Falling;
    }
    if velocity.y < 0.0 {
        next = ActorState::Jumping;
    }
    if contact.clambering {
        next = ActorState::Climbing;
    }
    if status.attacking {
        next = ActorState::Attacking;
    }
    *state = next;
}

/// Mirrors the sprite to match [`Facing`], and drives the flip that the original
/// achieved with a negative scale.
///
/// The sprite's box stays anchored at [`GamePos`] regardless of facing —
/// `flip_x` only mirrors the texture in place — so the rendered character never
/// jumps sideways when it turns around. [`collision::overlaps`] mirrors the
/// hitbox to match this same fixed box instead of swinging it to the other
/// side, which is what a naive port of Flame's anchor-relative scale flip
/// would do.
pub fn apply_facing(mut query: Query<(&Facing, &mut Sprite), Changed<Facing>>) {
    for (facing, mut sprite) in &mut query {
        sprite.flip_x = !facing.right;
    }
}

/// The attack: a rectangle in front of the player, live for the swing animation.
fn resolve_attack(
    mut commands: Commands,
    mut players: Query<
        (
            &GamePos,
            &Hitbox,
            &Facing,
            &Grounded,
            &ContactState,
            &PlayerInput,
            &mut PlayerStatus,
            &mut ActorState,
            &mut AnimationPlayer,
            &PlayerRoutine,
        ),
        With<Player>,
    >,
    enemies: Query<(Entity, &GamePos, &Hitbox, &BoxSize, &Facing), With<crate::enemy::Enemy>>,
) {
    for (
        pos,
        hitbox,
        facing,
        grounded,
        contact,
        input,
        mut status,
        mut state,
        mut animation,
        routine,
    ) in &mut players
    {
        if routine.blocks_control() {
            continue;
        }

        if input.attack_pressed
            && !status.attacking
            && **grounded
            && !input.jump_held
            && !contact.clambering
        {
            status.attacking = true;
            *state = ActorState::Attacking;
            animation.reset();
            continue;
        }

        if !status.attacking {
            continue;
        }

        // The swing ends with its animation; the Dart awaited the same ticker.
        if animation.finished {
            status.attacking = false;
            continue;
        }

        let attack_box = attack_rect(**pos, hitbox, facing.right);
        for (entity, enemy_pos, enemy_hitbox, enemy_size, enemy_facing) in &enemies {
            let enemy_box = enemy_hitbox.world_rect(mirrored_pos(
                **enemy_pos,
                enemy_hitbox,
                enemy_size.x,
                enemy_facing.right,
            ));
            if !attack_box.intersect(enemy_box).is_empty() {
                commands.trigger(EnemyStomped {
                    entity,
                    by_attack: true,
                });
                break;
            }
        }
    }
}

/// Hitbox origin corrected for facing, matching the compensation in
/// [`collision::overlaps`]: the sprite always renders as a fixed `box_width`
/// box anchored at `pos`, so facing left mirrors the hitbox *within* that
/// box instead of reflecting it out past the sprite entirely.
pub fn mirrored_pos(pos: Vec2, hitbox: &Hitbox, box_width: f32, facing_right: bool) -> Vec2 {
    if facing_right {
        pos
    } else {
        Vec2::new(
            pos.x + box_width - hitbox.offset.x * 2.0 - hitbox.size.x,
            pos.y,
        )
    }
}

/// Port of the `attackHitbox` rectangle built in `_checkAttackCollisions`.
fn attack_rect(pos: Vec2, hitbox: &Hitbox, facing_right: bool) -> Rect {
    let offset_x = if facing_right {
        16.0 - hitbox.offset.x + hitbox.size.x
    } else {
        hitbox.offset.x - 20.0 + hitbox.size.x
    };
    let min = pos + Vec2::new(offset_x, hitbox.offset.y - 14.0);
    Rect::from_corners(min, min + Vec2::new(37.0, hitbox.size.y + 14.0))
}

/// Bullet time near a bat: the Dart scaled gameplay `dt` by hand while leaving
/// animation on real time. Bevy already separates those two clocks, so this is
/// just a relative-speed change on the virtual clock, which `FixedUpdate` reads.
fn update_bullet_time(
    mut virtual_time: ResMut<Time<Virtual>>,
    players: Query<(&GamePos, &Hitbox, &BoxSize, &Facing), With<Player>>,
    bats: Query<(&GamePos, &Hitbox, &BoxSize, &Facing), With<crate::enemy::Bat>>,
) {
    let Ok((pos, hitbox, size, facing)) = players.single() else {
        return;
    };
    let player_box = hitbox.world_rect(mirrored_pos(**pos, hitbox, size.x, facing.right));

    let near_bat = bats
        .iter()
        .any(|(bat_pos, bat_hitbox, bat_size, bat_facing)| {
            let bat_box = bat_hitbox.world_rect(mirrored_pos(
                **bat_pos,
                bat_hitbox,
                bat_size.x,
                bat_facing.right,
            ));
            rect_distance(player_box, bat_box) < BAT_SLOWDOWN_RANGE
        });

    let target = if near_bat { SLOW_TIME_SCALE } else { 1.0 };
    if (virtual_time.relative_speed() - target).abs() > f32::EPSILON {
        virtual_time.set_relative_speed(target);
    }
}

fn restore_time_scale(mut virtual_time: ResMut<Time<Virtual>>) {
    virtual_time.set_relative_speed(1.0);
}

/// Shortest gap between two axis-aligned rectangles; zero when they overlap.
fn rect_distance(a: Rect, b: Rect) -> f32 {
    let dx = (b.min.x - a.max.x).max(a.min.x - b.max.x).max(0.0);
    let dy = (b.min.y - a.max.y).max(a.min.y - b.max.y).max(0.0);
    (dx * dx + dy * dy).sqrt()
}

/// Drives the death and checkpoint sequences that were `await` chains in Dart.
fn advance_routines(
    time: Res<Time<Real>>,
    mut next_state: ResMut<NextState<AppState>>,
    mut advance: MessageWriter<AdvanceLevel>,
    mut query: Query<
        (
            &mut PlayerRoutine,
            &mut ActorState,
            &mut AnimationPlayer,
            &mut GamePos,
            &mut Velocity,
            &mut Facing,
            &mut PlayerStatus,
            &StartPosition,
        ),
        With<Player>,
    >,
) {
    for (
        mut routine,
        mut state,
        mut animation,
        mut pos,
        mut velocity,
        mut facing,
        mut status,
        start,
    ) in &mut query
    {
        match &mut *routine {
            PlayerRoutine::Active => {}
            PlayerRoutine::Dying => {
                if animation.finished {
                    animation.reset();
                    facing.right = true;
                    **velocity = Vec2::ZERO;
                    **pos = **start;
                    *state = ActorState::Appearing;
                    *routine = PlayerRoutine::Reappearing;
                }
            }
            PlayerRoutine::Reappearing => {
                if animation.finished {
                    animation.reset();
                    *state = ActorState::Idle;
                    *routine = PlayerRoutine::Active;

                    // Faithful to the original: lives are decremented only when
                    // already above zero, so the run ends on the fourth death.
                    if status.lives > 0 {
                        status.lives -= 1;
                    } else {
                        status.lives = NUMBER_OF_TRIES;
                        next_state.set(AppState::GameOver);
                    }
                }
            }
            PlayerRoutine::LeavingLevel { timer } => {
                *timer -= time.delta_secs();
                if *timer <= 0.0 {
                    *routine = PlayerRoutine::Active;
                    *state = ActorState::Idle;
                    advance.write(AdvanceLevel);
                }
            }
        }
    }
}

fn on_player_killed(
    _event: On<PlayerKilled>,
    settings: Res<GameSettings>,
    mut sounds: MessageWriter<PlaySound>,
    mut query: Query<(&mut PlayerRoutine, &mut ActorState, &mut AnimationPlayer), With<Player>>,
) {
    if settings.invulnerable {
        return;
    }
    for (mut routine, mut state, mut animation) in &mut query {
        // `_respawn` returned early when already dying; same guard here.
        if *routine != PlayerRoutine::Active {
            continue;
        }
        if settings.play_sounds {
            sounds.write(PlaySound::new("hit.wav"));
        }
        *routine = PlayerRoutine::Dying;
        *state = ActorState::Hit;
        animation.reset();
    }
}

fn on_checkpoint_reached(
    _event: On<CheckpointReached>,
    settings: Res<GameSettings>,
    mut sounds: MessageWriter<PlaySound>,
    mut query: Query<(&mut PlayerRoutine, &mut ActorState, &mut AnimationPlayer), With<Player>>,
) {
    for (mut routine, mut state, mut animation) in &mut query {
        if *routine != PlayerRoutine::Active {
            continue;
        }
        if settings.play_sounds {
            sounds.write(PlaySound::new("disappear.wav"));
        }
        *state = ActorState::Disappearing;
        animation.reset();
        *routine = PlayerRoutine::LeavingLevel { timer: 3.0 };
    }
}
