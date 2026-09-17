//! Per-level ambience: spawns a fixed set of atmospheric emitters keyed by
//! level name.

use bevy::prelude::*;
use rand::Rng;

use crate::level::LevelEntity;

use super::firefly::Firefly;

/// Spawns the ambient effects for the given level name.
pub fn spawn_ambient_effects(commands: &mut Commands, level_name: &str, level_size: Vec2) {
    let mut rng = rand::rng();
    match level_name {
        "forest" => {
            for _ in 0..24 {
                commands.spawn((
                    Firefly::new(level_size, rng.random::<f32>() * 2.0),
                    LevelEntity,
                    Name::new("FireflyEmitter"),
                ));
            }
            // Fog is menu-only now — see `MenuFog` in `ui.rs`.
        }
        _ => {}
    }
}
