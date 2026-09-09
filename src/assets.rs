//! Asset loading and clip construction.
//!
//! Flame could call `images.loadAllImages()` and then read sizes synchronously
//! from a cache. Bevy's asset pipeline is async, so the port adds an explicit
//! [`AppState::Loading`] step: it waits for every sheet, then builds the atlas
//! layouts (which need real image dimensions) once, up front. That also removes
//! the `Future.delayed(Duration(seconds: 1))` the Dart used to paper over the
//! same race when swapping levels.

use bevy::asset::LoadState;
use bevy::platform::collections::HashMap;
use bevy::prelude::*;

use crate::AppState;
use crate::animation::{AnimationClip, AnimationSet, ClipSpec};
use crate::core::ActorState;

/// Sprite sheets, by asset path.
const IMAGE_PATHS: &[&str] = &[
    "images/hero/Player.png",
    "images/Items.png",
    "images/enemy/Bat.png",
    "images/enemy/Mobs.png",
    "images/enemy/yellow_mob.png",
    "images/objects/FallingOn.png",
    "images/objects/Grey Off.png",
    "images/objects/Grey On (32x8).png",
    "images/background/sky.png",
];

/// One-shot sounds, by asset path. Names match the Dart `FlameAudio.play` calls.
const SOUND_PATHS: &[&str] = &[
    "audio/jump.wav",
    "audio/hit.wav",
    "audio/collect.wav",
    "audio/bounce.wav",
    "audio/disappear.wav",
];

/// Looping background music, by asset path.
const MUSIC_MAIN_MENU: &str = "audio/main_menu.mp3";

/// Handles held during [`AppState::Loading`] so nothing is dropped mid-load.
#[derive(Resource, Default)]
struct LoadingHandles {
    images: Vec<Handle<Image>>,
    sounds: Vec<Handle<AudioSource>>,
    music_main_menu: Handle<AudioSource>,
}

/// Everything spawn code needs, resolved once at startup.
#[derive(Resource)]
pub struct GameAssets {
    pub images: HashMap<String, Handle<Image>>,
    pub sounds: HashMap<String, Handle<AudioSource>>,
    /// Looping track for [`AppState::MainMenu`].
    pub music_main_menu: Handle<AudioSource>,
    pub player: AnimationSet,
    pub bat: AnimationSet,
    pub yellow_mob: AnimationSet,
    pub red_mob: AnimationSet,
    pub bomb: AnimationSet,
    pub coin: AnimationSet,
    pub heart: AnimationSet,
    pub escalator: AnimationSet,
    pub falling_platform: AnimationSet,
}

impl GameAssets {
    pub fn image(&self, path: &str) -> Handle<Image> {
        self.images
            .get(path)
            .unwrap_or_else(|| panic!("image not preloaded: {path}"))
            .clone()
    }

    pub fn sound(&self, name: &str) -> Handle<AudioSource> {
        self.sounds
            .get(name)
            .unwrap_or_else(|| panic!("sound not preloaded: {name}"))
            .clone()
    }
}

pub struct AssetsPlugin;

impl Plugin for AssetsPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnEnter(AppState::Loading), start_loading)
            .add_systems(Update, finish_loading.run_if(in_state(AppState::Loading)));
    }
}

fn start_loading(mut commands: Commands, asset_server: Res<AssetServer>) {
    commands.insert_resource(LoadingHandles {
        images: IMAGE_PATHS.iter().map(|p| asset_server.load(*p)).collect(),
        sounds: SOUND_PATHS.iter().map(|p| asset_server.load(*p)).collect(),
        music_main_menu: asset_server.load(MUSIC_MAIN_MENU),
    });
}

fn finish_loading(
    mut commands: Commands,
    asset_server: Res<AssetServer>,
    handles: Res<LoadingHandles>,
    images: Res<Assets<Image>>,
    mut layouts: ResMut<Assets<TextureAtlasLayout>>,
    mut next_state: ResMut<NextState<AppState>>,
) {
    let pending = handles
        .images
        .iter()
        .map(|h| h.id().untyped())
        .chain(handles.sounds.iter().map(|h| h.id().untyped()))
        .chain(std::iter::once(handles.music_main_menu.id().untyped()));
    for id in pending {
        match asset_server.get_load_state(id) {
            Some(LoadState::Loaded) => {}
            Some(LoadState::Failed(err)) => {
                panic!("failed to load a required asset: {err}");
            }
            _ => return,
        }
    }

    let image_map: HashMap<String, Handle<Image>> = IMAGE_PATHS
        .iter()
        .zip(handles.images.iter())
        .map(|(path, handle)| (path.to_string(), handle.clone()))
        .collect();
    let sound_map: HashMap<String, Handle<AudioSource>> = SOUND_PATHS
        .iter()
        .zip(handles.sounds.iter())
        .map(|(path, handle)| {
            let name = path.rsplit('/').next().unwrap_or(path).to_string();
            (name, handle.clone())
        })
        .collect();

    let size_of = |path: &str| -> UVec2 {
        let handle = &image_map[path];
        images
            .get(handle)
            .unwrap_or_else(|| panic!("image reported loaded but missing: {path}"))
            .size()
    };

    let assets = build_game_assets(
        &image_map,
        &size_of,
        &mut layouts,
        sound_map,
        handles.music_main_menu.clone(),
    );
    commands.insert_resource(assets);
    commands.remove_resource::<LoadingHandles>();
    next_state.set(AppState::MainMenu);
}

/// Frame layouts transcribed from the Dart `_loadAllAnimations` methods.
///
/// Row offsets are kept in the `48 * n` / `32 * n` form the original used so the
/// two can be diffed line by line.
fn build_game_assets(
    image_map: &HashMap<String, Handle<Image>>,
    size_of: &dyn Fn(&str) -> UVec2,
    layouts: &mut Assets<TextureAtlasLayout>,
    sounds: HashMap<String, Handle<AudioSource>>,
    music_main_menu: Handle<AudioSource>,
) -> GameAssets {
    let mut clip = |path: &str, spec: ClipSpec| -> AnimationClip {
        spec.build(image_map[path].clone(), size_of(path), layouts)
    };

    const PLAYER: &str = "images/hero/Player.png";
    let f48 = UVec2::splat(48);
    let row48 = |n: u32| UVec2::new(0, 48 * n);
    let step = 0.1;

    let player = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(PLAYER, ClipSpec::new(f48, row48(9), 4, step)),
        )
        .with(
            ActorState::Running,
            clip(PLAYER, ClipSpec::new(f48, row48(0), 4, step)),
        )
        .with(
            ActorState::Jumping,
            clip(PLAYER, ClipSpec::new(f48, row48(8), 1, step)),
        )
        .with(
            ActorState::Falling,
            clip(PLAYER, ClipSpec::new(f48, row48(4), 1, step)),
        )
        .with(
            ActorState::Hit,
            clip(PLAYER, ClipSpec::new(f48, row48(4), 2, step).once()),
        )
        .with(
            ActorState::Attacking,
            // 7 frames wrapping at 4 on a 5-column sheet — the one clip that
            // cannot be expressed as a plain grid slice.
            clip(
                PLAYER,
                ClipSpec::new(f48, row48(1), 7, step).per_row(4).once(),
            ),
        )
        .with(
            ActorState::Appearing,
            clip(PLAYER, ClipSpec::new(f48, row48(3), 4, step).once()),
        )
        .with(
            ActorState::Disappearing,
            clip(PLAYER, ClipSpec::new(f48, row48(6), 4, step)),
        )
        .with(
            ActorState::Climbing,
            clip(PLAYER, ClipSpec::new(f48, row48(3), 1, step)),
        );

    const BAT: &str = "images/enemy/Bat.png";
    let f16 = UVec2::splat(16);
    let bat_step = 0.03;
    let bat = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(BAT, ClipSpec::new(f16, UVec2::new(0, 0), 5, bat_step)),
        )
        .with(
            ActorState::Running,
            clip(BAT, ClipSpec::new(f16, UVec2::new(0, 32), 5, bat_step)),
        )
        .with(
            ActorState::Hit,
            clip(
                BAT,
                ClipSpec::new(f16, UVec2::new(0, 64), 4, bat_step).once(),
            ),
        );

    let mob_frame = UVec2::new(48, 32);
    let row32 = |n: u32| UVec2::new(0, 32 * n);

    const YELLOW: &str = "images/enemy/yellow_mob.png";
    let yellow_step = 0.05;
    let yellow_mob = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(YELLOW, ClipSpec::new(mob_frame, row32(5), 4, yellow_step)),
        )
        .with(
            ActorState::Running,
            clip(YELLOW, ClipSpec::new(mob_frame, row32(1), 4, yellow_step)),
        )
        .with(
            ActorState::Hit,
            clip(
                YELLOW,
                ClipSpec::new(mob_frame, row32(4), 4, yellow_step).once(),
            ),
        );

    const MOBS: &str = "images/enemy/Mobs.png";
    let red_step = 0.1;
    let red_mob = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(MOBS, ClipSpec::new(mob_frame, row32(5), 4, red_step)),
        )
        .with(
            ActorState::Running,
            clip(MOBS, ClipSpec::new(mob_frame, row32(1), 4, red_step)),
        )
        .with(
            ActorState::Hit,
            clip(MOBS, ClipSpec::new(mob_frame, row32(4), 4, red_step).once()),
        )
        .with(
            ActorState::Attacking,
            clip(
                MOBS,
                ClipSpec::new(mob_frame, row32(2), 4, red_step * 2.0).once(),
            ),
        );

    const ITEMS: &str = "images/Items.png";
    let bomb = AnimationSet::new().with(
        ActorState::Idle,
        clip(ITEMS, ClipSpec::new(f16, UVec2::new(32, 0), 2, 0.1)),
    );
    let coin = AnimationSet::new().with(
        ActorState::Idle,
        clip(ITEMS, ClipSpec::new(f16, UVec2::new(0, 0), 2, 0.3)),
    );
    let heart = AnimationSet::new().with(
        ActorState::Idle,
        clip(ITEMS, ClipSpec::new(f16, UVec2::new(0, 16), 2, 0.3)),
    );

    // The escalator sheets declare 32x16 frames but are only 8px tall; ClipSpec
    // clamps, so these render as the 32x8 strips they actually are.
    let strip = UVec2::new(32, 16);
    let escalator = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(
                "images/objects/Grey Off.png",
                ClipSpec::new(strip, UVec2::ZERO, 1, 0.05),
            ),
        )
        .with(
            ActorState::Running,
            clip(
                "images/objects/Grey On (32x8).png",
                ClipSpec::new(strip, UVec2::ZERO, 8, 0.05),
            ),
        );

    const FALLING: &str = "images/objects/FallingOn.png";
    let falling_platform = AnimationSet::new()
        .with(
            ActorState::Idle,
            clip(FALLING, ClipSpec::new(strip, UVec2::ZERO, 4, 0.1)),
        )
        .with(
            ActorState::Falling,
            clip(FALLING, ClipSpec::new(strip, UVec2::ZERO, 4, 0.3)),
        );

    GameAssets {
        images: image_map.clone(),
        sounds,
        music_main_menu,
        player,
        bat,
        yellow_mob,
        red_mob,
        bomb,
        coin,
        heart,
        escalator,
        falling_platform,
    }
}
