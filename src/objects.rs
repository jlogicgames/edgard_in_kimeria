//! Moving and triggered platforms: escalators and falling platforms.

use bevy::prelude::*;
use bevy::sprite::Anchor;

use crate::AppState;
use crate::animation::AnimationPlayer;
use crate::assets::GameAssets;
use crate::core::collision::{EscalatorSurface, FallingPlatformSurface};
use crate::core::{ActorState, BoxSize, Facing, GamePos, ZLayer, z};
use crate::items::{Actionable, ActionableKind};
use crate::level::{LevelEntity, ObjectPlacement};
use crate::player::TriggerActivated;

/// Escalators measure their patrol range in 32px units, unlike the enemies'
/// 16px.
const ESCALATOR_TILE_SIZE: f32 = 32.0;
const ESCALATOR_SPEED: f32 = 50.0;
/// Warning time between being stepped on and dropping.
const FALL_DELAY: f32 = 1.0;
/// `MoveByEffect(Vector2(0, 200), EffectController(duration: 1.5))`.
const FALL_DISTANCE: f32 = 200.0;
const FALL_DURATION: f32 = 1.5;

#[derive(Component)]
pub struct Escalator {
    pub vertical: bool,
    pub range_neg: f32,
    pub range_pos: f32,
    pub direction: f32,
    /// Toggled by a trigger; a stopped escalator still blocks but stops
    /// carrying and shows its "off" sprite.
    pub running: bool,
}

/// Lifecycle of a falling platform.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum FallPhase {
    Idle,
    /// Stepped on: lit by a torch, about to drop.
    Warning {
        timer: f32,
    },
    Falling {
        elapsed: f32,
        start_y: f32,
    },
}

#[derive(Component)]
pub struct FallingPlatform {
    pub phase: FallPhase,
    /// The warning torch, despawned with the platform.
    pub torch: Option<Entity>,
}

impl FallingPlatform {
    /// `collideWithActor` / `triggerFall`.
    pub fn trigger_fall(&mut self) {
        if self.phase == FallPhase::Idle {
            self.phase = FallPhase::Warning { timer: FALL_DELAY };
        }
    }
}

pub struct ObjectsPlugin;

impl Plugin for ObjectsPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(
            Update,
            (
                move_escalators,
                toggle_escalators,
                advance_falling_platforms,
            )
                .run_if(in_state(AppState::Playing)),
        );
    }
}

#[allow(clippy::too_many_arguments)]
pub fn spawn_escalator(
    commands: &mut Commands,
    assets: &GameAssets,
    at: ObjectPlacement,
    vertical: bool,
    off_neg: f32,
    off_pos: f32,
    target_id: String,
) {
    let axis = if vertical { at.pos.y } else { at.pos.x };
    commands.spawn((
        Escalator {
            vertical,
            range_neg: axis - off_neg * ESCALATOR_TILE_SIZE,
            range_pos: axis + off_pos * ESCALATOR_TILE_SIZE,
            direction: 1.0,
            running: true,
        },
        EscalatorSurface {
            active: true,
            velocity: Vec2::ZERO,
        },
        GamePos(at.pos),
        BoxSize(at.size),
        ZLayer(z::ACTOR),
        Facing::default(),
        // Escalators start in the running animation, as `current = State.run`.
        ActorState::Running,
        assets.escalator.clone(),
        AnimationPlayer::default(),
        Actionable { target_id },
        ActionableKind::Escalator,
        Sprite::default(),
        Anchor::TOP_LEFT,
        LevelEntity,
        Name::new("Escalator"),
    ));
}

pub fn spawn_falling_platform(commands: &mut Commands, assets: &GameAssets, at: ObjectPlacement) {
    commands.spawn((
        FallingPlatform {
            phase: FallPhase::Idle,
            torch: None,
        },
        FallingPlatformSurface { is_falling: false },
        GamePos(at.pos),
        BoxSize(at.size),
        ZLayer(z::ACTOR),
        ActorState::Idle,
        assets.falling_platform.clone(),
        AnimationPlayer::default(),
        Sprite::default(),
        Anchor::TOP_LEFT,
        LevelEntity,
        Name::new("FallingPlatform"),
    ));
}

/// Patrols the escalator and publishes the velocity riders inherit.
fn move_escalators(
    time: Res<Time<Virtual>>,
    mut query: Query<(
        &mut Escalator,
        &mut EscalatorSurface,
        &mut GamePos,
        &mut Facing,
    )>,
) {
    let dt = time.delta_secs();
    for (mut escalator, mut surface, mut pos, mut facing) in &mut query {
        let axis = if escalator.vertical { pos.y } else { pos.x };
        if axis >= escalator.range_pos {
            escalator.direction = -1.0;
            if !escalator.vertical {
                facing.right = !facing.right;
            }
        } else if axis <= escalator.range_neg {
            escalator.direction = 1.0;
            if !escalator.vertical {
                facing.right = !facing.right;
            }
        }

        let step = escalator.direction * ESCALATOR_SPEED * dt;
        if escalator.vertical {
            pos.y += step;
        } else {
            pos.x += step;
        }

        surface.velocity = if escalator.running {
            if escalator.vertical {
                Vec2::new(0.0, escalator.direction * ESCALATOR_SPEED)
            } else {
                Vec2::new(escalator.direction * ESCALATOR_SPEED, 0.0)
            }
        } else {
            Vec2::ZERO
        };
    }
}

/// `Escalator.performAction`: flip between the idle and running animations.
fn toggle_escalators(
    mut events: MessageReader<TriggerActivated>,
    mut query: Query<(
        &mut Escalator,
        &mut ActorState,
        &Actionable,
        &ActionableKind,
    )>,
) {
    for event in events.read() {
        for (mut escalator, mut state, actionable, kind) in &mut query {
            if *kind != ActionableKind::Escalator || actionable.target_id != event.target_id {
                continue;
            }
            escalator.running = !escalator.running;
            *state = if escalator.running {
                ActorState::Running
            } else {
                ActorState::Idle
            };
        }
    }
}

/// Warning torch, then the drop, then removal.
fn advance_falling_platforms(
    mut commands: Commands,
    time: Res<Time<Virtual>>,
    mut query: Query<(
        Entity,
        &mut FallingPlatform,
        &mut FallingPlatformSurface,
        &mut GamePos,
        &BoxSize,
        &mut ActorState,
    )>,
    mut torches: Query<&mut crate::effects::Torch>,
) {
    let dt = time.delta_secs();
    for (entity, mut platform, mut surface, mut pos, size, mut state) in &mut query {
        match platform.phase {
            FallPhase::Idle => {}
            FallPhase::Warning { timer } => {
                if platform.torch.is_none() {
                    *state = ActorState::Falling;
                    surface.is_falling = true;
                    // `spawn_torch` expects the object's top-left and adds half
                    // the size itself, so pass the platform's own top-left to
                    // centre the flame on the platform.
                    let torch = crate::effects::spawn_torch(
                        &mut commands,
                        ObjectPlacement {
                            pos: **pos,
                            size: **size,
                        },
                        5,
                        String::new(),
                    );
                    platform.torch = Some(torch);
                }

                let remaining = timer - dt;
                if remaining <= 0.0 {
                    if let Some(torch) = platform.torch
                        && let Ok(mut torch) = torches.get_mut(torch)
                    {
                        torch.set_lit(false);
                    }
                    platform.phase = FallPhase::Falling {
                        elapsed: 0.0,
                        start_y: pos.y,
                    };
                } else {
                    platform.phase = FallPhase::Warning { timer: remaining };
                }
            }
            FallPhase::Falling { elapsed, start_y } => {
                let elapsed = elapsed + dt;
                let progress = (elapsed / FALL_DURATION).clamp(0.0, 1.0);
                pos.y = start_y + FALL_DISTANCE * progress;
                if progress >= 1.0 {
                    if let Some(torch) = platform.torch {
                        commands.entity(torch).try_despawn();
                    }
                    commands.entity(entity).try_despawn();
                } else {
                    platform.phase = FallPhase::Falling { elapsed, start_y };
                }
            }
        }
    }
}
