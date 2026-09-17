//! Level loading: the `.tmx` maps are rendered by `bevy_ecs_tiled` and their
//! object layers are read directly to spawn gameplay entities.
//!
//! `bevy_ecs_tiled` does spawn an entity per Tiled object, but those carry the
//! plugin's own transforms and no gameplay meaning. Rather than adopt and patch
//! them, this module reads the raw [`tiled::Map`] out of the loaded asset and
//! spawns its own entities in Tiled space.
//!
//! Layer offsets are deliberately ignored when reading object coordinates. The
//! `SpawnPoints` layer of `forest.tmx` carries `offsety="-16"`, and honouring
//! that offset here would shift every entity in that level 16px from its
//! intended position.

use bevy::prelude::*;
use bevy_ecs_tiled::prelude::*;

use crate::assets::GameAssets;
use crate::core::{BlockKind, BoxSize, CollisionBlock, GamePos, ZLayer, z};
use crate::{AppState, GameProgress, LEVEL_NAMES};

/// Marks everything belonging to the currently loaded level, so a reload is one
/// query.
///
/// Systems that despawn their own level-scoped entities (a finished explosion,
/// a stomped enemy, a collected coin) must use `try_despawn`: a level unload can
/// land in the same frame and remove the entity first, and a plain `despawn` on
/// an already-freed entity is a hard error.
#[derive(Component)]
pub struct LevelEntity;

/// The `bevy_ecs_tiled` map entity for the current level.
#[derive(Component)]
pub struct LevelMap;

#[derive(Resource, Debug, Default)]
pub struct CurrentLevel {
    pub index: usize,
    /// Map extent in pixels, used to seed ambient effect spawn areas.
    pub size: Vec2,
}

impl CurrentLevel {
    pub fn name(&self) -> &'static str {
        LEVEL_NAMES[self.index % LEVEL_NAMES.len()]
    }
}

/// Load a specific level, replacing whatever is loaded.
#[derive(Message, Debug)]
pub struct LoadLevel(pub usize);

/// Advance to the next level, wrapping at the end like `loadNextLevel`.
#[derive(Message, Debug)]
pub struct AdvanceLevel;

pub struct LevelPlugin;

impl Plugin for LevelPlugin {
    fn build(&self, app: &mut App) {
        app.add_plugins(TiledPlugin::default())
            .init_resource::<CurrentLevel>()
            .add_message::<LoadLevel>()
            .add_message::<AdvanceLevel>()
            // Unloading runs in PreUpdate so the despawns are applied before any
            // Update system touches the old level. Doing it inside Update races
            // with every system that queues a command against a level entity
            // that same frame — an insert or despawn applied after the unload is
            // a hard error, not a no-op.
            .add_systems(
                PreUpdate,
                (handle_advance_level, handle_load_level)
                    .chain()
                    .run_if(resource_exists::<GameAssets>),
            )
            // Spawning must stay in Update: it reacts to the map-loaded message
            // that `bevy_ecs_tiled` publishes from its own Update systems.
            .add_systems(
                Update,
                spawn_level_contents.run_if(resource_exists::<GameAssets>),
            );
    }
}

fn handle_advance_level(
    mut requests: MessageReader<AdvanceLevel>,
    mut loads: MessageWriter<LoadLevel>,
    current: Res<CurrentLevel>,
) {
    if requests.read().next().is_none() {
        return;
    }
    requests.clear();
    // `loadNextLevel` wraps back to the first level rather than ending the game.
    loads.write(LoadLevel((current.index + 1) % LEVEL_NAMES.len()));
}

fn handle_load_level(
    mut commands: Commands,
    mut requests: MessageReader<LoadLevel>,
    asset_server: Res<AssetServer>,
    mut current: ResMut<CurrentLevel>,
    existing: Query<Entity, Or<(With<LevelEntity>, With<LevelMap>)>>,
    mut camera: Query<&mut crate::camera::CameraPlaced>,
) {
    let Some(request) = requests.read().last() else {
        return;
    };
    let index = request.0 % LEVEL_NAMES.len();

    for entity in &existing {
        commands.entity(entity).try_despawn();
    }
    for mut placed in &mut camera {
        placed.0 = false;
    }

    current.index = index;
    let path = format!("tiles/{}.tmx", LEVEL_NAMES[index]);
    commands.spawn((
        TiledMap(asset_server.load(path)),
        // Puts the map's top-left at the entity origin, so Tiled (x, y) maps to
        // Bevy (x, -y) — the same projection `sync_transforms` uses.
        TilemapAnchor::TopLeft,
        Transform::from_xyz(0.0, 0.0, z::TILEMAP),
        LevelMap,
        Name::new(format!("Level:{}", LEVEL_NAMES[index])),
    ));
}

/// Reads the object layers once the map asset is available and spawns the level.
fn spawn_level_contents(
    mut commands: Commands,
    mut events: MessageReader<TiledEvent<MapCreated>>,
    assets: Res<Assets<TiledMapAsset>>,
    game_assets: Res<GameAssets>,
    mut current: ResMut<CurrentLevel>,
    mut progress: ResMut<GameProgress>,
) {
    for event in events.read() {
        let Some(map) = event.get_map(&assets) else {
            continue;
        };

        current.size = Vec2::new(
            (map.width * map.tile_width) as f32,
            (map.height * map.tile_height) as f32,
        );
        progress.current_level = current.index;

        for layer in map.layers() {
            let tiled::LayerType::Objects(objects) = layer.layer_type() else {
                continue;
            };
            match layer.name.as_str() {
                "SpawnPoints" => {
                    for object in objects.objects() {
                        spawn_gameplay_object(&mut commands, &game_assets, &object);
                    }
                }
                "Collisions" => {
                    for object in objects.objects() {
                        spawn_collision_block(&mut commands, &object);
                    }
                }
                _ => {}
            }
        }

        crate::effects::spawn_ambient_effects(&mut commands, current.name(), current.size);
    }
}

/// Position and extent of a Tiled object, in Tiled space.
#[derive(Debug, Clone, Copy)]
pub struct ObjectPlacement {
    pub pos: Vec2,
    pub size: Vec2,
}

fn placement(object: &tiled::Object) -> ObjectPlacement {
    let size = match object.shape {
        tiled::ObjectShape::Rect { width, height }
        | tiled::ObjectShape::Ellipse { width, height }
        | tiled::ObjectShape::Capsule { width, height } => Vec2::new(width, height),
        _ => Vec2::ZERO,
    };
    ObjectPlacement {
        pos: Vec2::new(object.x, object.y),
        size,
    }
}

/// Reads a numeric property, accepting either the `int` or `float` Tiled type.
///
/// The maps use both for the same conceptual field (`Intensity` is `int`,
/// `offNeg` is `float`).
pub fn prop_f32(object: &tiled::Object, key: &str) -> Option<f32> {
    match object.properties.get(key)? {
        tiled::PropertyValue::FloatValue(v) => Some(*v),
        tiled::PropertyValue::IntValue(v) => Some(*v as f32),
        _ => None,
    }
}

pub fn prop_bool(object: &tiled::Object, key: &str) -> Option<bool> {
    match object.properties.get(key)? {
        tiled::PropertyValue::BoolValue(v) => Some(*v),
        _ => None,
    }
}

pub fn prop_str<'a>(object: &'a tiled::Object, key: &str) -> Option<&'a str> {
    match object.properties.get(key)? {
        tiled::PropertyValue::StringValue(v) => Some(v.as_str()),
        _ => None,
    }
}

fn spawn_gameplay_object(commands: &mut Commands, assets: &GameAssets, object: &tiled::Object) {
    let at = placement(object);
    let off_neg = prop_f32(object, "offNeg").unwrap_or(0.0);
    let off_pos = prop_f32(object, "offPos").unwrap_or(0.0);
    let is_vertical = prop_bool(object, "isVertical").unwrap_or(false);

    match object.user_type.as_str() {
        "Player" => crate::player::spawn_player(commands, assets, at),
        "Collectable" => crate::items::spawn_collectable(commands, assets, at, &object.name),
        "Bat" => crate::enemy::spawn_bat(commands, assets, at, is_vertical, off_neg, off_pos),
        "YellowMob" => crate::enemy::spawn_yellow_mob(commands, assets, at, off_neg, off_pos),
        "RedMob" => crate::enemy::spawn_red_mob(commands, assets, at, off_neg, off_pos),
        "Checkpoint" => crate::items::spawn_checkpoint(commands, at),
        "Bomb" => crate::items::spawn_bomb(commands, assets, at),
        "Torch" => {
            let intensity = prop_f32(object, "Intensity").unwrap_or(80.0) as i32;
            crate::effects::spawn_torch(commands, at, intensity, String::new());
        }
        "Trigger" => crate::items::spawn_trigger(commands, at, object.name.clone()),
        "Escalator" => crate::objects::spawn_escalator(
            commands,
            assets,
            at,
            is_vertical,
            off_neg,
            off_pos,
            object.name.clone(),
        ),
        "FallingPlatform" => crate::objects::spawn_falling_platform(commands, assets, at),
        "Actionable" => spawn_actionable(commands, object, at),
        other => {
            if !other.is_empty() {
                warn!("unhandled SpawnPoints object class: {other}");
            }
        }
    }
}

/// Port of `_spawnActionable`: a `type` property selects what is actually built,
/// and the object's *name* is the id a `Trigger` matches against.
fn spawn_actionable(commands: &mut Commands, object: &tiled::Object, at: ObjectPlacement) {
    let target_id = object.name.clone();
    match prop_str(object, "type") {
        Some("Torch") => {
            let intensity = prop_f32(object, "Intensity").unwrap_or(0.0) as i32;
            crate::effects::spawn_torch(commands, at, intensity, target_id);
        }
        Some("Wall") => {
            commands.spawn((
                GamePos(at.pos),
                BoxSize(at.size),
                CollisionBlock {
                    kind: BlockKind::Wall,
                    active: true,
                },
                crate::items::Actionable { target_id },
                crate::items::ActionableKind::Wall,
                LevelEntity,
                Name::new("Wall"),
            ));
        }
        other => warn!("unhandled Actionable type: {other:?}"),
    }
}

fn spawn_collision_block(commands: &mut Commands, object: &tiled::Object) {
    let at = placement(object);
    let kind = match object.user_type.as_str() {
        "Platform" => BlockKind::Platform,
        "QuickSand" => BlockKind::QuickSand,
        "Wall" => BlockKind::Wall,
        _ => BlockKind::Solid,
    };
    commands.spawn((
        GamePos(at.pos),
        BoxSize(at.size),
        ZLayer(z::TILEMAP),
        CollisionBlock { kind, active: true },
        LevelEntity,
        Name::new(format!("CollisionBlock:{kind:?}")),
    ));
}

/// Starts a fresh run: level 0, coins cleared.
pub fn reset_game(
    mut progress: ResMut<GameProgress>,
    mut loads: MessageWriter<LoadLevel>,
    mut next_state: ResMut<NextState<AppState>>,
) {
    progress.coins_collected = 0;
    loads.write(LoadLevel(0));
    next_state.set(AppState::Playing);
}
