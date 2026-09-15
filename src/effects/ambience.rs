//! Per-level ambience: the Dart `Level.onLoad` spawned a fixed set of
//! atmospheric emitters keyed by level name.

use bevy::prelude::*;
use bevy::sprite::Anchor;
use rand::Rng;

use crate::core::{GamePos, ZLayer, z};
use crate::level::LevelEntity;

use super::firefly::Firefly;
use super::rain::RainDrop;

/// Spawns the ambient effects the Dart `Level.onLoad` added per level name.
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
        "forest-1" => {
            for _ in 0..48 {
                let wind = (rng.random::<f32>() - 0.5) * 48.0;
                commands.spawn((
                    RainDrop {
                        area: level_size,
                        speed: 400.0 + rng.random::<f32>() * 80.0,
                        wind,
                    },
                    Sprite {
                        color: Color::BLACK,
                        custom_size: Some(Vec2::new(1.2, 14.0)),
                        ..default()
                    },
                    Anchor::CENTER,
                    GamePos(Vec2::new(
                        rng.random::<f32>() * level_size.x,
                        rng.random::<f32>() * level_size.y,
                    )),
                    ZLayer(z::ACTOR),
                    LevelEntity,
                    Name::new("RainDrop"),
                ));
            }
        }
        _ => {}
    }
}
