//! One-shot sound playback.
//!
//! Flame needed `AudioPool`s (pre-warmed players, `maxPlayers: 3`) and a silent
//! priming play at startup to dodge first-play latency. Bevy decodes on a mixer
//! thread and an `AudioPlayer` entity is cheap, so a sound is just an entity
//! that despawns when it finishes — no pooling, no warm-up.

use bevy::audio::{AudioSinkPlayback, Volume};
use bevy::prelude::*;

use crate::assets::GameAssets;
use crate::{AppState, GameSettings};

/// Request to play a sound by file name, e.g. `"jump.wav"`.
#[derive(Message, Debug, Clone)]
pub struct PlaySound {
    pub name: String,
    /// Multiplied by [`GameSettings::sound_volume`].
    pub volume: f32,
}

impl PlaySound {
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            volume: 1.0,
        }
    }
}

/// Tags the entity playing the looping main-menu track.
#[derive(Component)]
struct MainMenuMusic;

/// Linear volume ramp applied to a music entity's [`AudioSink`] each frame.
///
/// The sink is attached a frame or two after the [`AudioPlayer`] spawns, so the
/// ramp only advances once it exists — a fade-in always starts from silence, not
/// from part-way through.
#[derive(Component)]
struct MusicFade {
    elapsed: f32,
    duration: f32,
    from: f32,
    to: f32,
    /// Despawn the entity once the ramp reaches `to` (used for the fade-out).
    despawn_on_end: bool,
}

/// Seconds to ramp from silence to full when the menu music starts.
const MUSIC_FADE_IN: f32 = 2.0;
/// Seconds to ramp back to silence when leaving the menu for gameplay.
const MUSIC_FADE_OUT: f32 = 1.0;

pub struct AudioPlugin;

impl Plugin for AudioPlugin {
    fn build(&self, app: &mut App) {
        app.add_message::<PlaySound>()
            .add_systems(Update, (play_sounds, advance_music_fades))
            .add_systems(OnEnter(AppState::MainMenu), start_menu_music)
            .add_systems(OnExit(AppState::MainMenu), fade_out_menu_music);
    }
}

/// Loops `assets/audio/main_menu.mp3` while the main menu is up, fading it in
/// from silence.
fn start_menu_music(mut commands: Commands, settings: Res<GameSettings>, assets: Res<GameAssets>) {
    if !settings.play_sounds {
        return;
    }
    commands.spawn((
        AudioPlayer(assets.music_main_menu.clone()),
        PlaybackSettings::LOOP.with_volume(Volume::Linear(0.0)),
        MusicFade {
            elapsed: 0.0,
            duration: MUSIC_FADE_IN,
            from: 0.0,
            to: settings.sound_volume,
            despawn_on_end: false,
        },
        MainMenuMusic,
        Name::new("Music:main_menu"),
    ));
}

/// Ramp the menu music down to silence, then despawn it once it lands there.
fn fade_out_menu_music(
    mut commands: Commands,
    music: Query<(Entity, Option<&AudioSink>), With<MainMenuMusic>>,
) {
    for (entity, sink) in &music {
        // No sink yet means the track has not actually started; nothing to fade.
        let Some(sink) = sink else {
            commands.entity(entity).despawn();
            continue;
        };
        let from = sink.volume().to_linear();
        commands.entity(entity).insert(MusicFade {
            elapsed: 0.0,
            duration: MUSIC_FADE_OUT,
            from,
            to: 0.0,
            despawn_on_end: true,
        });
    }
}

fn advance_music_fades(
    mut commands: Commands,
    time: Res<Time>,
    mut fades: Query<(Entity, &mut MusicFade, &mut AudioSink)>,
) {
    for (entity, mut fade, mut sink) in &mut fades {
        fade.elapsed += time.delta_secs();
        let t = (fade.elapsed / fade.duration).clamp(0.0, 1.0);
        let volume = fade.from + (fade.to - fade.from) * t;
        sink.set_volume(Volume::Linear(volume));
        if t >= 1.0 {
            if fade.despawn_on_end {
                commands.entity(entity).despawn();
            } else {
                commands.entity(entity).remove::<MusicFade>();
            }
        }
    }
}

fn play_sounds(
    mut commands: Commands,
    mut requests: MessageReader<PlaySound>,
    settings: Res<GameSettings>,
    assets: Option<Res<GameAssets>>,
) {
    let Some(assets) = assets else {
        requests.clear();
        return;
    };
    for request in requests.read() {
        if !settings.play_sounds {
            continue;
        }
        commands.spawn((
            AudioPlayer(assets.sound(&request.name)),
            PlaybackSettings::DESPAWN
                .with_volume(Volume::Linear(request.volume * settings.sound_volume)),
            Name::new(format!("Sound:{}", request.name)),
        ));
    }
}
