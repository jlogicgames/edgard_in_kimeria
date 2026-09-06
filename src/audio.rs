//! One-shot sound playback.
//!
//! Flame needed `AudioPool`s (pre-warmed players, `maxPlayers: 3`) and a silent
//! priming play at startup to dodge first-play latency. Bevy decodes on a mixer
//! thread and an `AudioPlayer` entity is cheap, so a sound is just an entity
//! that despawns when it finishes — no pooling, no warm-up.

use bevy::audio::Volume;
use bevy::prelude::*;

use crate::GameSettings;
use crate::assets::GameAssets;

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

pub struct AudioPlugin;

impl Plugin for AudioPlugin {
    fn build(&self, app: &mut App) {
        app.add_message::<PlaySound>()
            .add_systems(Update, play_sounds);
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
