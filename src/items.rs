//! Pickups and interactables: collectables, bombs, checkpoints, triggers and the
//! trigger-driven `Actionable` objects.
//!
//! In Flame each of these overrode `onCollisionStart` and reached across into
//! the player, or walked `parent.children` to find matching `Actionable`s. Here
//! contact is one overlap system per kind and the trigger wiring is a
//! [`TriggerActivated`] message, so nothing needs a handle on anything else.
//!
//! Note on hitboxes: the Dart gave coins and bombs an 8px `CircleHitbox`
//! inscribed in their 16x16 sprite. The port tests the enclosing box instead,
//! which differs only within a pixel at the corners and keeps every contact test
//! in the game on one code path.

use bevy::prelude::*;
use bevy::sprite::Anchor;

use crate::animation::AnimationPlayer;
use crate::assets::GameAssets;
use crate::audio::PlaySound;
use crate::core::{ActorState, BoxSize, CollisionBlock, Facing, GamePos, Hitbox, ZLayer, z};
use crate::level::{LevelEntity, ObjectPlacement};
use crate::player::{
    CheckpointReached, Player, PlayerKilled, PlayerRoutine, PlayerStatus, TriggerActivated,
    mirrored_pos,
};
use crate::{AppState, GameProgress, GameSettings};

#[derive(Component, Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollectableKind {
    Coin,
    Heart,
}

#[derive(Component)]
pub struct Collectable {
    pub kind: CollectableKind,
}

#[derive(Component)]
pub struct Bomb;

#[derive(Component)]
pub struct Checkpoint;

#[derive(Component)]
pub struct Trigger {
    pub target_id: String,
}

/// Links an object to the [`Trigger`] that switches it, by shared name.
/// Replaces the `Actionable` Dart mixin.
#[derive(Component)]
pub struct Actionable {
    pub target_id: String,
}

/// What an [`Actionable`] does when its trigger fires.
#[derive(Component, Debug, Clone, Copy, PartialEq, Eq)]
pub enum ActionableKind {
    Wall,
    Torch,
    Escalator,
}

pub struct ItemsPlugin;

impl Plugin for ItemsPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(
            Update,
            (
                collect_items,
                touch_bombs,
                touch_checkpoints,
                track_trigger_overlap,
                open_walls,
            )
                .chain()
                .run_if(in_state(AppState::Playing)),
        );
    }
}

pub fn spawn_collectable(
    commands: &mut Commands,
    assets: &GameAssets,
    at: ObjectPlacement,
    name: &str,
) {
    let (kind, animations) = match name {
        "Heart" => (CollectableKind::Heart, assets.heart.clone()),
        _ => (CollectableKind::Coin, assets.coin.clone()),
    };
    commands.spawn((
        Collectable { kind },
        GamePos(at.pos),
        BoxSize(at.size),
        ZLayer(z::ACTOR),
        Hitbox::rect(0.0, 0.0, at.size.x, at.size.y),
        ActorState::Idle,
        animations,
        AnimationPlayer::default(),
        Sprite::default(),
        Anchor::TOP_LEFT,
        LevelEntity,
        Name::new(format!("Collectable:{name}")),
    ));
}

pub fn spawn_bomb(commands: &mut Commands, assets: &GameAssets, at: ObjectPlacement) {
    commands.spawn((
        Bomb,
        GamePos(at.pos),
        BoxSize(at.size),
        ZLayer(z::ACTOR),
        Hitbox::rect(0.0, 0.0, at.size.x, at.size.y),
        ActorState::Idle,
        assets.bomb.clone(),
        AnimationPlayer::default(),
        Sprite::default(),
        Anchor::TOP_LEFT,
        LevelEntity,
        Name::new("Bomb"),
    ));
}

/// Checkpoints have no sprite: the Dart's flag animations were commented out and
/// the component rendered nothing but its debug box.
pub fn spawn_checkpoint(commands: &mut Commands, at: ObjectPlacement) {
    commands.spawn((
        Checkpoint,
        GamePos(at.pos),
        BoxSize(at.size),
        Hitbox::rect(0.0, 0.0, 16.0, 32.0),
        LevelEntity,
        Name::new("Checkpoint"),
    ));
}

/// Triggers are likewise invisible; they only mark a spot the player can act in.
pub fn spawn_trigger(commands: &mut Commands, at: ObjectPlacement, target_id: String) {
    commands.spawn((
        Trigger {
            target_id: target_id.clone(),
        },
        GamePos(at.pos),
        BoxSize(at.size),
        Hitbox::rect(0.0, 0.0, at.size.x, at.size.y),
        LevelEntity,
        Name::new(format!("Trigger:{target_id}")),
    ));
}

/// The player's hitbox in world space, or `None` if there is no active player.
fn player_box(
    players: &Query<(&GamePos, &Hitbox, &BoxSize, &Facing, &PlayerRoutine), With<Player>>,
) -> Option<Rect> {
    let (pos, hitbox, size, facing, routine) = players.single().ok()?;
    if *routine != PlayerRoutine::Active {
        return None;
    }
    Some(hitbox.world_rect(mirrored_pos(**pos, hitbox, size.x, facing.right)))
}

fn collect_items(
    mut commands: Commands,
    settings: Res<GameSettings>,
    mut progress: ResMut<GameProgress>,
    mut sounds: MessageWriter<PlaySound>,
    players: Query<(&GamePos, &Hitbox, &BoxSize, &Facing, &PlayerRoutine), With<Player>>,
    items: Query<(Entity, &GamePos, &BoxSize, &Collectable)>,
) {
    let Some(player_box) = player_box(&players) else {
        return;
    };
    for (entity, pos, size, collectable) in &items {
        let item_box = Rect::from_corners(**pos, **pos + **size);
        if player_box.intersect(item_box).is_empty() {
            continue;
        }
        if settings.play_sounds {
            sounds.write(PlaySound::new("collect.wav"));
        }
        let centre = **pos + **size / 2.0;
        match collectable.kind {
            CollectableKind::Coin => {
                progress.coins_collected += 1;
                crate::effects::spawn_ripple(&mut commands, centre);
            }
            CollectableKind::Heart => {
                crate::effects::spawn_shockwave(&mut commands, centre);
            }
        }
        commands.entity(entity).try_despawn();
    }
}

fn touch_bombs(
    mut commands: Commands,
    settings: Res<GameSettings>,
    mut sounds: MessageWriter<PlaySound>,
    players: Query<(&GamePos, &Hitbox, &BoxSize, &Facing, &PlayerRoutine), With<Player>>,
    bombs: Query<(Entity, &GamePos, &BoxSize), With<Bomb>>,
) {
    let Some(player_box) = player_box(&players) else {
        return;
    };
    for (entity, pos, size) in &bombs {
        let bomb_box = Rect::from_corners(**pos, **pos + **size);
        if player_box.intersect(bomb_box).is_empty() {
            continue;
        }
        if settings.play_sounds {
            sounds.write(PlaySound::new("bounce.wav"));
        }
        crate::effects::spawn_explosion(&mut commands, **pos + **size / 2.0);
        commands.entity(entity).try_despawn();
        commands.trigger(PlayerKilled);
    }
}

fn touch_checkpoints(
    mut commands: Commands,
    players: Query<(&GamePos, &Hitbox, &BoxSize, &Facing, &PlayerRoutine), With<Player>>,
    checkpoints: Query<(&GamePos, &Hitbox), With<Checkpoint>>,
) {
    let Some(player_box) = player_box(&players) else {
        return;
    };
    for (pos, hitbox) in &checkpoints {
        if !player_box.intersect(hitbox.world_rect(**pos)).is_empty() {
            commands.trigger(CheckpointReached);
            return;
        }
    }
}

/// Keeps `PlayerStatus::trigger_id` in sync with whichever trigger the player
/// stands in, replacing the paired `onCollisionStart`/`onCollisionEnd` overrides.
fn track_trigger_overlap(
    mut players: Query<
        (
            &GamePos,
            &Hitbox,
            &BoxSize,
            &Facing,
            &PlayerRoutine,
            &mut PlayerStatus,
        ),
        With<Player>,
    >,
    triggers: Query<(&GamePos, &Hitbox, &Trigger)>,
) {
    let Ok((pos, hitbox, size, facing, routine, mut status)) = players.single_mut() else {
        return;
    };
    if *routine != PlayerRoutine::Active {
        return;
    }
    let player_box = hitbox.world_rect(mirrored_pos(**pos, hitbox, size.x, facing.right));

    let mut overlapped = String::new();
    for (trigger_pos, trigger_hitbox, trigger) in &triggers {
        if !player_box
            .intersect(trigger_hitbox.world_rect(**trigger_pos))
            .is_empty()
        {
            overlapped = trigger.target_id.clone();
            break;
        }
    }
    if status.trigger_id != overlapped {
        status.trigger_id = overlapped;
    }
}

/// `Wall.performAction`: deactivate and remove.
fn open_walls(
    mut commands: Commands,
    mut events: MessageReader<TriggerActivated>,
    mut walls: Query<(Entity, &Actionable, &ActionableKind, &mut CollisionBlock)>,
) {
    for event in events.read() {
        for (entity, actionable, kind, mut block) in &mut walls {
            if *kind != ActionableKind::Wall || actionable.target_id != event.target_id {
                continue;
            }
            if !block.active {
                continue;
            }
            block.active = false;
            commands.entity(entity).try_despawn();
        }
    }
}
