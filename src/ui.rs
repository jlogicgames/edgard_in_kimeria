//! HUD and menus.
//!
//! The original drew these as Flutter widgets floating above the game canvas,
//! toggled by name through Flame's `overlays` map — a second UI framework,
//! composited separately, with lifetimes managed by hand.
//!
//! The port uses Bevy's own `bevy_ui`: the menus are four small trees of nodes,
//! nothing here wants immediate-mode or a docking/inspector toolkit, and staying
//! native means one render path and no extra dependency. Each menu is tagged
//! `DespawnOnExit(state)`, so the state machine cleans it up — the class of bug
//! the Dart risked every time it paired an `overlays.add` with a matching
//! `overlays.remove` in a different file.

use bevy::prelude::*;

use crate::assets::GameAssets;
use crate::level::LoadLevel;
use crate::{AppState, GameProgress};

const PANEL_BG: Color = Color::srgb(0.0, 0.0, 0.0);
const TEXT_COLOR: Color = Color::WHITE;
const BUTTON_BG: Color = Color::WHITE;
const BUTTON_BG_HOVER: Color = Color::srgb(0.85, 0.85, 0.85);
const BUTTON_TEXT: Color = Color::BLACK;

const CONTROLS_HELP: &str = "Use WASD or Arrow Keys for movement.\n\
J to jump. K to attack. L to interact.\n\
Collect as many stars as you can and avoid enemies!";

/// Which menu button an entity is, so one handler can serve every menu.
#[derive(Component, Debug, Clone, Copy, PartialEq, Eq)]
enum MenuAction {
    Play,
    Resume,
    PlayAgain,
}

#[derive(Component)]
struct CoinCounter;

pub struct UiPlugin;

impl Plugin for UiPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(OnEnter(AppState::MainMenu), spawn_main_menu)
            .add_systems(OnEnter(AppState::Paused), spawn_pause_menu)
            .add_systems(OnEnter(AppState::GameOver), spawn_game_over)
            .add_systems(OnEnter(AppState::Playing), spawn_hud)
            .add_systems(
                Update,
                (handle_buttons, update_coin_counter, resume_on_escape),
            );
    }
}

// --- shared building blocks -------------------------------------------------

fn panel(full_screen: bool) -> impl Bundle {
    (
        Node {
            width: if full_screen {
                Val::Percent(100.0)
            } else {
                Val::Px(400.0)
            },
            height: if full_screen {
                Val::Percent(100.0)
            } else {
                Val::Px(300.0)
            },
            flex_direction: FlexDirection::Column,
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            padding: UiRect::all(Val::Px(10.0)),
            row_gap: Val::Px(20.0),
            border_radius: BorderRadius::all(Val::Px(20.0)),
            ..default()
        },
        BackgroundColor(PANEL_BG),
    )
}

/// Full-viewport centring wrapper, so a menu sits in the middle of the window.
fn overlay_root(name: &'static str) -> impl Bundle {
    (
        Node {
            position_type: PositionType::Absolute,
            width: Val::Percent(100.0),
            height: Val::Percent(100.0),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            ..default()
        },
        Name::new(name),
    )
}

fn button(action: MenuAction, label: &str, font_size: f32) -> impl Bundle {
    (
        Button,
        action,
        Node {
            width: Val::Px(200.0),
            height: Val::Px(75.0),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            border_radius: BorderRadius::all(Val::Px(4.0)),
            ..default()
        },
        BackgroundColor(BUTTON_BG),
        children![(
            Text::new(label.to_string()),
            TextFont::from_font_size(font_size),
            TextColor(BUTTON_TEXT),
        )],
    )
}

fn heading(text: &str) -> impl Bundle {
    (
        Text::new(text.to_string()),
        TextFont::from_font_size(24.0),
        TextColor(TEXT_COLOR),
    )
}

fn help_text() -> impl Bundle {
    (
        Text::new(CONTROLS_HELP),
        TextFont::from_font_size(14.0),
        TextColor(TEXT_COLOR),
        TextLayout::justify(Justify::Center),
    )
}

// --- menus ------------------------------------------------------------------

fn spawn_main_menu(mut commands: Commands) {
    commands.spawn((
        overlay_root("MainMenu"),
        DespawnOnExit(AppState::MainMenu),
        children![(
            panel(true),
            children![
                heading("Edgard in Kimeria"),
                button(MenuAction::Play, "Play", 40.0),
                help_text(),
            ],
        )],
    ));
}

fn spawn_pause_menu(mut commands: Commands) {
    commands.spawn((
        overlay_root("PauseMenu"),
        DespawnOnExit(AppState::Paused),
        children![(
            panel(false),
            children![
                heading("Pause Menu"),
                button(MenuAction::Resume, "Resume", 28.0),
                help_text(),
            ],
        )],
    ));
}

fn spawn_game_over(mut commands: Commands) {
    commands.spawn((
        overlay_root("GameOver"),
        DespawnOnExit(AppState::GameOver),
        children![(
            panel(false),
            children![
                heading("Game Over"),
                button(MenuAction::PlayAgain, "Play Again", 28.0),
            ],
        )],
    ));
}

/// The coin readout. Unlike the menus this must survive a pause, so it is scoped
/// to nothing and torn down when the run ends instead.
fn spawn_hud(
    mut commands: Commands,
    assets: Res<GameAssets>,
    existing: Query<Entity, With<CoinCounter>>,
) {
    if !existing.is_empty() {
        return;
    }
    commands.spawn((
        Node {
            position_type: PositionType::Absolute,
            left: Val::Px(10.0),
            top: Val::Px(10.0),
            align_items: AlignItems::Center,
            column_gap: Val::Px(8.0),
            ..default()
        },
        Name::new("Hud"),
        children![
            (
                ImageNode {
                    image: assets.image("images/Items.png"),
                    rect: Some(Rect::from_corners(Vec2::ZERO, Vec2::splat(16.0))),
                    ..default()
                },
                Node {
                    width: Val::Px(32.0),
                    height: Val::Px(32.0),
                    ..default()
                },
            ),
            (
                CoinCounter,
                Text::new("0"),
                TextFont::from_font_size(24.0),
                TextColor(TEXT_COLOR),
            ),
        ],
    ));
}

fn update_coin_counter(
    progress: Res<GameProgress>,
    mut query: Query<&mut Text, With<CoinCounter>>,
) {
    if !progress.is_changed() {
        return;
    }
    for mut text in &mut query {
        **text = progress.coins_collected.to_string();
    }
}

fn handle_buttons(
    mut interactions: Query<
        (&Interaction, &MenuAction, &mut BackgroundColor),
        Changed<Interaction>,
    >,
    mut next_state: ResMut<NextState<AppState>>,
    mut loads: MessageWriter<LoadLevel>,
    mut progress: ResMut<GameProgress>,
) {
    for (interaction, action, mut background) in &mut interactions {
        match interaction {
            Interaction::Pressed => {
                background.0 = BUTTON_BG_HOVER;
                match action {
                    MenuAction::Play => {
                        loads.write(LoadLevel(0));
                        next_state.set(AppState::Playing);
                    }
                    MenuAction::Resume => next_state.set(AppState::Playing),
                    MenuAction::PlayAgain => {
                        // `game.reset()`: coins and level index both go back.
                        progress.coins_collected = 0;
                        loads.write(LoadLevel(0));
                        next_state.set(AppState::Playing);
                    }
                }
            }
            Interaction::Hovered => background.0 = BUTTON_BG_HOVER,
            Interaction::None => background.0 = BUTTON_BG,
        }
    }
}

/// Escape resumes as well as pauses, matching `game.pause()`'s toggle.
fn resume_on_escape(
    keys: Res<ButtonInput<KeyCode>>,
    state: Res<State<AppState>>,
    mut next_state: ResMut<NextState<AppState>>,
) {
    if *state.get() == AppState::Paused && keys.just_pressed(KeyCode::Escape) {
        next_state.set(AppState::Playing);
    }
}
