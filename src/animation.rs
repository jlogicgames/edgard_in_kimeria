//! Sprite-sheet animation, ported from Flame's `SpriteAnimationGroupComponent`.
//!
//! Flame let a component hold a `Map<State, SpriteAnimation>` and swap `current`.
//! The equivalent here is an [`ActorState`] component that systems write freely
//! plus [`AnimationSet`]/[`AnimationPlayer`], which notice the change and drive
//! the atlas index. Gameplay code never touches frames or timers.

use bevy::platform::collections::HashMap;
use bevy::prelude::*;

use crate::core::ActorState;

/// One animation: a private atlas layout whose indices are exactly its frames.
///
/// Per-clip layouts rather than one grid over the sheet, because Flame's
/// `amountPerRow` lets a clip wrap at a width that need not match the sheet's
/// real column count — the 7-frame player attack wraps at 4 on a 5-wide sheet.
#[derive(Debug, Clone)]
pub struct AnimationClip {
    pub image: Handle<Image>,
    pub layout: Handle<TextureAtlasLayout>,
    pub frames: usize,
    pub step_time: f32,
    pub looping: bool,
}

/// Declarative description of a clip, resolved into an [`AnimationClip`] once
/// the source image's dimensions are known.
#[derive(Debug, Clone, Copy)]
pub struct ClipSpec {
    /// Size of one frame in the sheet.
    pub frame: UVec2,
    /// Top-left of the first frame.
    pub origin: UVec2,
    pub frames: usize,
    /// Frames per row before wrapping; Flame's `amountPerRow`.
    pub per_row: usize,
    pub step_time: f32,
    pub looping: bool,
}

impl ClipSpec {
    pub fn new(frame: UVec2, origin: UVec2, frames: usize, step_time: f32) -> Self {
        Self {
            frame,
            origin,
            frames,
            per_row: frames.max(1),
            step_time,
            looping: true,
        }
    }

    pub fn per_row(mut self, per_row: usize) -> Self {
        self.per_row = per_row.max(1);
        self
    }

    pub fn once(mut self) -> Self {
        self.looping = false;
        self
    }

    /// Builds the clip, clamping each frame rect to the image.
    ///
    /// Two sheets (`FallingOn.png`, `Grey On (32x8).png`) declare a frame taller
    /// than the file actually is; Flame padded the overflow with transparency.
    /// Clamping reproduces the visible result without asking wgpu for pixels
    /// that do not exist, which would be a hard error rather than empty space.
    pub fn build(
        &self,
        image: Handle<Image>,
        image_size: UVec2,
        layouts: &mut Assets<TextureAtlasLayout>,
    ) -> AnimationClip {
        let mut layout = TextureAtlasLayout::new_empty(image_size);
        for index in 0..self.frames {
            let col = (index % self.per_row) as u32;
            let row = (index / self.per_row) as u32;
            let min = self.origin + UVec2::new(col * self.frame.x, row * self.frame.y);
            let max = (min + self.frame).min(image_size);
            layout.add_texture(URect { min, max });
        }
        AnimationClip {
            image,
            layout: layouts.add(layout),
            frames: self.frames,
            step_time: self.step_time,
            looping: self.looping,
        }
    }
}

/// Every clip an entity can play, keyed by the state that selects it.
#[derive(Component, Debug, Clone, Default)]
pub struct AnimationSet {
    clips: HashMap<ActorState, AnimationClip>,
}

impl AnimationSet {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with(mut self, state: ActorState, clip: AnimationClip) -> Self {
        self.clips.insert(state, clip);
        self
    }

    pub fn get(&self, state: ActorState) -> Option<&AnimationClip> {
        self.clips.get(&state)
    }
}

/// Playback cursor. Replaces Flame's `animationTicker`.
#[derive(Component, Debug, Default)]
pub struct AnimationPlayer {
    elapsed: f32,
    frame: usize,
    /// State the cursor was last synced to, so a change resets playback.
    current: Option<ActorState>,
    /// True once a non-looping clip has shown its last frame.
    ///
    /// Gameplay code polls this where the Dart wrote
    /// `await animationTicker?.completed`. Awaiting a future mid-update meant
    /// the rest of that method ran an arbitrary number of frames later, with no
    /// guarantee the entity still existed; a polled flag makes the wait explicit
    /// and keeps every state transition inside the schedule.
    pub finished: bool,
}

impl AnimationPlayer {
    pub fn frame(&self) -> usize {
        self.frame
    }

    /// Restarts the current clip. Flame's `animationTicker.reset()`.
    pub fn reset(&mut self) {
        self.elapsed = 0.0;
        self.frame = 0;
        self.finished = false;
    }
}

pub struct AnimationPlugin;

impl Plugin for AnimationPlugin {
    fn build(&self, app: &mut App) {
        app.add_systems(Update, advance_animations);
    }
}

/// Advances every animation on **real** time, not the bullet-time-scaled clock.
///
/// This matches the original, which deliberately passed unscaled `dt` to
/// `super.update` so animation stayed smooth while physics slowed near a bat.
fn advance_animations(
    time: Res<Time<Real>>,
    mut query: Query<(
        &AnimationSet,
        &ActorState,
        &mut AnimationPlayer,
        &mut Sprite,
    )>,
) {
    let dt = time.delta_secs();
    for (set, state, mut player, mut sprite) in &mut query {
        let Some(clip) = set.get(*state) else {
            continue;
        };

        if player.current != Some(*state) {
            player.current = Some(*state);
            player.reset();
            sprite.image = clip.image.clone();
            sprite.texture_atlas = Some(TextureAtlas {
                layout: clip.layout.clone(),
                index: 0,
            });
        }

        if clip.frames > 1 && !(player.finished && !clip.looping) {
            player.elapsed += dt;
            while player.elapsed >= clip.step_time {
                player.elapsed -= clip.step_time;
                if player.frame + 1 >= clip.frames {
                    if clip.looping {
                        player.frame = 0;
                    } else {
                        player.frame = clip.frames - 1;
                        player.finished = true;
                        break;
                    }
                } else {
                    player.frame += 1;
                }
            }
        } else if clip.frames <= 1 {
            player.finished = true;
        }

        if let Some(atlas) = sprite.texture_atlas.as_mut() {
            atlas.index = player.frame;
        }
    }
}
