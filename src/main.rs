use bevy::prelude::*;
use bevy::window::WindowResolution;

use edgard_in_kimeria::{GamePlugin, LOGICAL_RESOLUTION};

fn main() {
    App::new()
        .add_plugins(
            DefaultPlugins
                // Pixel art: never filter the tileset or sprite sheets.
                .set(ImagePlugin::default_nearest())
                .set(WindowPlugin {
                    primary_window: Some(Window {
                        title: "Edgard in Kimeria".to_string(),
                        resolution: WindowResolution::new(
                            LOGICAL_RESOLUTION.x as u32 * 2,
                            LOGICAL_RESOLUTION.y as u32 * 2,
                        ),
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_plugins(GamePlugin)
        .run();
}
