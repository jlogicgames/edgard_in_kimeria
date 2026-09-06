//! Edgard in Kimeria — a Bevy port of the Flutter/Flame original.
//!
//! # Coordinate convention
//!
//! Gameplay runs in **Tiled space**: origin at the map's top-left, `+y` pointing
//! *down*, entity positions anchored at their top-left corner. This is what the
//! Flame original used, and keeping it means the ported physics is a literal
//! translation of the Dart rather than a sign-flipped rewrite that has to be
//! re-derived (and re-debugged) from scratch.
//!
//! Bevy renders y-up, so [`core::GamePos`] is the source of truth and
//! [`core::sync_transforms`] projects it onto [`Transform`] once per frame:
//! `translation = (pos.x, -pos.y, z)`, with sprites anchored `TOP_LEFT`.
//! Nothing outside that one system should touch `Transform.translation` for
//! gameplay entities.

// Bevy system signatures are wide by construction: a system that needs six
// resources and a filtered query is idiomatic, not a smell. Both lints fire on
// almost every system here and neither points at anything worth changing.
#![allow(clippy::type_complexity)]
#![allow(clippy::too_many_arguments)]

use bevy::prelude::*;

pub mod animation;
pub mod assets;
pub mod audio;
pub mod camera;
pub mod core;
pub mod dev;
pub mod effects;
pub mod enemy;
pub mod items;
pub mod level;
pub mod objects;
pub mod player;
pub mod ui;

/// Logical resolution the game is authored against; the window letterboxes to it.
pub const LOGICAL_RESOLUTION: Vec2 = Vec2::new(640.0, 360.0);

/// Top-level app state. Replaces Flame's `overlays` + `isGameStarted` flag.
///
/// Menu entities are tagged `DespawnOnExit(state)`, so leaving a state cleans up
/// its UI without the manual `overlays.remove(...)` bookkeeping the Dart needed.
#[derive(States, Default, Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum AppState {
    /// Waits for every sprite sheet and sound before anything can spawn.
    /// Flame's synchronous image cache made this step implicit.
    #[default]
    Loading,
    MainMenu,
    Playing,
    Paused,
    GameOver,
}

/// Runtime toggles that were `bool` fields on the Dart `EdgardInKimeria` class.
#[derive(Resource, Debug, Clone)]
pub struct GameSettings {
    pub play_sounds: bool,
    pub sound_volume: f32,
    /// Draw hitbox/collision-block gizmos. `debugMode = true` in the Dart source.
    pub debug_draw: bool,
    /// Enables the chromatic-aberration glitch. Off by default: its Dart
    /// manager was never constructed, so the effect never ran in the original.
    pub chroma_glitch: bool,
    /// Debug aid with no counterpart in the original: ignores lethal damage so
    /// a level can be walked end to end. Toggled with F2.
    pub invulnerable: bool,
}

impl Default for GameSettings {
    fn default() -> Self {
        Self {
            play_sounds: true,
            sound_volume: 1.0,
            debug_draw: false,
            chroma_glitch: false,
            invulnerable: false,
        }
    }
}

/// Score and progression carried across level loads.
#[derive(Resource, Debug, Default)]
pub struct GameProgress {
    pub coins_collected: u32,
    pub current_level: usize,
}

/// The level rotation, in the order the Dart `levelNames` list had them.
pub const LEVEL_NAMES: [&str; 2] = ["forest-1", "forest"];

/// Everything the game needs, as one plugin so `main.rs` stays a launcher.
pub struct GamePlugin;

impl Plugin for GamePlugin {
    fn build(&self, app: &mut App) {
        app.init_state::<AppState>()
            .init_resource::<GameSettings>()
            .init_resource::<GameProgress>()
            .add_plugins((
                assets::AssetsPlugin,
                core::CorePlugin,
                animation::AnimationPlugin,
                camera::CameraPlugin,
                level::LevelPlugin,
                player::PlayerPlugin,
                enemy::EnemyPlugin,
                items::ItemsPlugin,
                objects::ObjectsPlugin,
                effects::EffectsPlugin,
                ui::UiPlugin,
                audio::AudioPlugin,
                dev::DevPlugin,
            ));
    }
}
