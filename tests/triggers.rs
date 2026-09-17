//! Headless tests for the trigger -> actionable wiring.
//!
//! A `TriggerActivated` message is handled by one handler per kind of
//! actionable, so this checks the two ends still meet: only matching ids react,
//! and each kind performs its expected action.
//!
//! This path is awkward to confirm by playing — it needs the player to stand in
//! a 16px trigger and press a key on exactly the right frame.

use bevy::prelude::*;
use bevy::state::app::StatesPlugin;
use edgard_in_kimeria::core::{ActorState, BlockKind, BoxSize, CollisionBlock, GamePos};
use edgard_in_kimeria::effects::{Torch, toggle_torches};
use edgard_in_kimeria::items::{Actionable, ActionableKind, ItemsPlugin};
use edgard_in_kimeria::objects::{Escalator, ObjectsPlugin};
use edgard_in_kimeria::player::TriggerActivated;
use edgard_in_kimeria::{AppState, GameProgress, GameSettings};

/// A headless app with just the plugins under test, already in `Playing`.
fn test_app() -> App {
    let mut app = App::new();
    app.add_plugins((MinimalPlugins, StatesPlugin))
        .init_state::<AppState>()
        .init_resource::<GameSettings>()
        .init_resource::<GameProgress>()
        .add_message::<edgard_in_kimeria::audio::PlaySound>()
        .add_message::<TriggerActivated>()
        .add_plugins((ItemsPlugin, ObjectsPlugin))
        // The whole `EffectsPlugin` would drag in the render app; the torch
        // handler is the only part of it this file exercises.
        .add_systems(Update, toggle_torches);
    app.insert_resource(NextState::Pending(AppState::Playing));
    app.update();
    app
}

fn spawn_wall(app: &mut App, target_id: &str) -> Entity {
    app.world_mut()
        .spawn((
            GamePos(Vec2::new(480.0, 224.0)),
            BoxSize(Vec2::new(48.0, 96.0)),
            CollisionBlock {
                kind: BlockKind::Wall,
                active: true,
            },
            Actionable {
                target_id: target_id.to_string(),
            },
            ActionableKind::Wall,
        ))
        .id()
}

fn fire(app: &mut App, target_id: &str) {
    app.world_mut()
        .write_message(TriggerActivated {
            target_id: target_id.to_string(),
        })
        .unwrap();
    app.update();
}

#[test]
fn a_matching_trigger_removes_the_wall() {
    let mut app = test_app();
    let wall = spawn_wall(&mut app, "Wall1");

    fire(&mut app, "Wall1");

    assert!(
        app.world().get_entity(wall).is_err(),
        "Wall.performAction deactivates and removes the block"
    );
}

#[test]
fn a_non_matching_trigger_leaves_the_wall_alone() {
    let mut app = test_app();
    let wall = spawn_wall(&mut app, "Wall1");

    fire(&mut app, "SomeOtherDoor");

    assert!(app.world().get_entity(wall).is_ok());
    assert!(
        app.world().get::<CollisionBlock>(wall).unwrap().active,
        "an unrelated trigger must not deactivate collision"
    );
}

#[test]
fn a_trigger_toggles_an_escalator_between_running_and_idle() {
    let mut app = test_app();
    let escalator = app
        .world_mut()
        .spawn((
            Escalator {
                vertical: false,
                range_neg: 0.0,
                range_pos: 100.0,
                direction: 1.0,
                running: true,
            },
            Actionable {
                target_id: "Lift".to_string(),
            },
            ActionableKind::Escalator,
            ActorState::Running,
        ))
        .id();

    fire(&mut app, "Lift");
    assert!(!app.world().get::<Escalator>(escalator).unwrap().running);
    assert_eq!(
        *app.world().get::<ActorState>(escalator).unwrap(),
        ActorState::Idle,
        "a stopped escalator shows its 'off' animation"
    );

    fire(&mut app, "Lift");
    assert!(app.world().get::<Escalator>(escalator).unwrap().running);
    assert_eq!(
        *app.world().get::<ActorState>(escalator).unwrap(),
        ActorState::Running
    );
}

#[test]
fn a_trigger_toggles_a_torch_and_relights_it_at_full_intensity() {
    let mut app = test_app();
    // The `Torch1` actionable in forest-1.tmx carries Intensity = 100.
    let torch = app
        .world_mut()
        .spawn(Torch::new(100, "Torch1".to_string()))
        .id();
    assert!(app.world().get::<Torch>(torch).unwrap().is_lit());

    fire(&mut app, "Torch1");
    assert!(!app.world().get::<Torch>(torch).unwrap().is_lit());
    assert_eq!(app.world().get::<Torch>(torch).unwrap().intensity, 0);

    fire(&mut app, "Torch1");
    let relit = app.world().get::<Torch>(torch).unwrap();
    assert!(relit.is_lit());
    assert_eq!(
        relit.intensity, 200,
        "toggleFire(true) hard-coded 200, regardless of the authored intensity"
    );
}

#[test]
fn a_trigger_only_reaches_its_own_torch() {
    let mut app = test_app();
    let mine = app
        .world_mut()
        .spawn(Torch::new(100, "Torch1".to_string()))
        .id();
    let theirs = app
        .world_mut()
        .spawn(Torch::new(100, "Torch2".to_string()))
        .id();

    fire(&mut app, "Torch1");

    assert!(!app.world().get::<Torch>(mine).unwrap().is_lit());
    assert!(app.world().get::<Torch>(theirs).unwrap().is_lit());
}
