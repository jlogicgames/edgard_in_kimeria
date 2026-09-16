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
                        // Ignored on native (bevy_winit only reads it under
                        // `cfg(target_arch = "wasm32")`). On web, without it
                        // the canvas stays a fixed 1280x720 regardless of the
                        // browser window, so on any viewport shorter than
                        // that — i.e. almost any real browser window, once
                        // its chrome is subtracted — the bottom of the UI
                        // (menu buttons, panel content) is clipped with no
                        // way to scroll to it. This resizes the canvas to
                        // its parent element (`<body>`, which our index.html
                        // sizes to the viewport), which in turn resizes the
                        // window/camera so both the letterboxed game view and
                        // the menus lay out to whatever size the browser
                        // actually gives them.
                        fit_canvas_to_parent: true,
                        ..default()
                    }),
                    ..default()
                }),
        )
        .add_plugins(GamePlugin)
        .run();
}
