//! Screen-space post processing: the ripple and the chromatic-aberration glitch.
//!
//! # Approach
//!
//! Both effects share one render-graph node in `Core2d`'s `PostProcess` set
//! that reads the view target and writes a distorted copy. No per-frame image
//! allocation, and both effects share the single pass.
//!
//! # Turning them on
//!
//! The ripple fires on coin pickup. The glitch is driven by [`ChromaGlitch`],
//! which is left disabled by default; set `GameSettings::chroma_glitch` to see
//! it.

use bevy::core_pipeline::{Core2dSystems, FullscreenShader, schedule::Core2d};
use bevy::prelude::*;
use bevy::render::extract_component::{
    ComponentUniforms, DynamicUniformIndex, ExtractComponent, ExtractComponentPlugin,
    UniformComponentPlugin,
};
use bevy::render::render_resource::binding_types::{sampler, texture_2d, uniform_buffer};
use bevy::render::render_resource::*;
use bevy::render::renderer::{RenderContext, RenderDevice, ViewQuery};
use bevy::render::view::ViewTarget;
use bevy::render::{RenderApp, RenderStartup};
use rand::Rng;

use crate::camera::MainCamera;
use crate::core::GamePos;
use crate::level::LevelEntity;
use crate::{AppState, GameSettings};

const SHADER_PATH: &str = "shaders/screen_effects.wgsl";

/// Uniform block shared by both effects. Field order must match the WGSL struct.
#[derive(Component, Debug, Default, Clone, Copy, ExtractComponent, ShaderType)]
pub struct ScreenEffects {
    pub ripple_center: Vec2,
    pub ripple_progress: f32,
    pub ripple_max_radius: f32,
    pub ripple_strength: f32,
    pub ripple_frequency: f32,
    pub ripple_decay: f32,
    pub ripple_active: f32,
    pub chroma_intensity: f32,
    pub chroma_shift: f32,
    pub time: f32,
    /// Viewport width / height, for the ripple's aspect correction. 12 scalar
    /// fields after the leading `Vec2` already total 48 bytes — a multiple of
    /// 16 — so no trailing padding is needed to satisfy WebGL2's uniform
    /// buffer alignment; adding one here previously broke it (52 bytes).
    pub aspect: f32,
}

/// A live ripple, spawned on coin pickup.
#[derive(Component, Debug)]
pub struct RippleEffect {
    /// World-space centre, projected to UV each frame as the camera moves.
    pub centre_world: Vec2,
    pub elapsed: f32,
    pub duration: f32,
    /// Pixels; converted to UV against the viewport width.
    pub max_radius: f32,
    pub strength: f32,
    pub frequency: f32,
    pub decay: f32,
}

/// Port of `ChromaGlitchManager`: a poison that flares more often and harder
/// the longer it runs.
#[derive(Resource, Debug)]
pub struct ChromaGlitch {
    pub min_interval: f32,
    pub max_interval: f32,
    pub min_duration: f32,
    pub max_duration: f32,
    pub initial_shift: f32,
    pub max_shift: f32,
    /// How fast the poison builds, per second.
    pub progression_rate: f32,
    pub max_poison_duration: f32,

    time: f32,
    next_trigger: f32,
    effect_end: f32,
    active: bool,
    current_shift: f32,
}

impl Default for ChromaGlitch {
    fn default() -> Self {
        Self {
            min_interval: 1.0,
            max_interval: 3.0,
            min_duration: 0.2,
            max_duration: 0.5,
            initial_shift: 0.002,
            max_shift: 0.010,
            progression_rate: 0.0005,
            max_poison_duration: 2.0,
            time: 0.0,
            next_trigger: 0.0,
            effect_end: 0.0,
            active: false,
            current_shift: 0.002,
        }
    }
}

impl ChromaGlitch {
    /// 0..1 across the poison's range.
    pub fn poison_level(&self) -> f32 {
        ((self.current_shift - self.initial_shift) / (self.max_shift - self.initial_shift))
            .clamp(0.0, 1.0)
    }

    fn reset(&mut self) {
        self.time = 0.0;
        self.next_trigger = 0.0;
        self.effect_end = 0.0;
        self.active = false;
        self.current_shift = self.initial_shift;
    }
}

pub struct PostProcessPlugin;

impl Plugin for PostProcessPlugin {
    fn build(&self, app: &mut App) {
        app.init_resource::<ChromaGlitch>()
            .add_plugins((
                ExtractComponentPlugin::<ScreenEffects>::default(),
                UniformComponentPlugin::<ScreenEffects>::default(),
            ))
            .add_systems(Update, (advance_ripples, drive_screen_effects).chain())
            .add_systems(OnEnter(AppState::Playing), reset_glitch);

        let Some(render_app) = app.get_sub_app_mut(RenderApp) else {
            return;
        };
        render_app
            .add_systems(RenderStartup, init_pipeline)
            .add_systems(Core2d, run_pass.in_set(Core2dSystems::PostProcess));
    }
}

/// Values from `Collectable.collideWithPlayer` for a coin.
pub fn spawn_ripple(commands: &mut Commands, centre_world: Vec2) {
    commands.spawn((
        RippleEffect {
            centre_world,
            elapsed: 0.0,
            duration: 0.75,
            max_radius: 300.0,
            strength: 12.0,
            frequency: 60.0,
            decay: 20.0,
        },
        GamePos(centre_world),
        LevelEntity,
        Name::new("RippleEffect"),
    ));
}

fn advance_ripples(
    mut commands: Commands,
    time: Res<Time<Real>>,
    mut query: Query<(Entity, &mut RippleEffect)>,
) {
    for (entity, mut ripple) in &mut query {
        ripple.elapsed += time.delta_secs();
        if ripple.elapsed >= ripple.duration {
            commands.entity(entity).try_despawn();
        }
    }
}

fn reset_glitch(mut glitch: ResMut<ChromaGlitch>) {
    glitch.reset();
}

/// Folds both effects into the camera's [`ScreenEffects`] uniform.
fn drive_screen_effects(
    time: Res<Time<Real>>,
    settings: Res<GameSettings>,
    mut glitch: ResMut<ChromaGlitch>,
    ripples: Query<&RippleEffect>,
    mut camera: Query<(&mut ScreenEffects, &GlobalTransform, &Projection), With<MainCamera>>,
) {
    let Ok((mut effects, camera_transform, projection)) = camera.single_mut() else {
        return;
    };
    let Projection::Orthographic(ortho) = projection else {
        return;
    };
    let dt = time.delta_secs();

    let view_size = ortho.area.size();
    effects.time += dt;
    effects.aspect = view_size.x / view_size.y;

    // --- ripple ---
    // The orthographic area is already the visible world rect, so projecting
    // world space to UV is just two divides.
    if let Some(ripple) = ripples.iter().next() {
        let camera_pos = camera_transform.translation().truncate();
        let world_min = camera_pos + ortho.area.min;
        // GamePos is y-down; Bevy world y is up.
        let centre_bevy = Vec2::new(ripple.centre_world.x, -ripple.centre_world.y);
        let uv = Vec2::new(
            (centre_bevy.x - world_min.x) / view_size.x,
            // Flip: UV y grows downward from the top of the view.
            1.0 - (centre_bevy.y - world_min.y) / view_size.y,
        );
        let progress = (ripple.elapsed / ripple.duration).clamp(0.0, 1.0);

        effects.ripple_active = 1.0;
        effects.ripple_center = uv;
        effects.ripple_progress = progress;
        effects.ripple_max_radius = ripple.max_radius / view_size.x;
        // Strength fades out over the ripple's life.
        effects.ripple_strength = (ripple.strength * (1.0 - progress)) / view_size.x;
        effects.ripple_frequency = ripple.frequency;
        effects.ripple_decay = ripple.decay;
    } else {
        effects.ripple_active = 0.0;
    }

    // --- chromatic glitch ---
    if !settings.chroma_glitch {
        effects.chroma_intensity = 0.0;
        return;
    }

    glitch.time += dt;
    glitch.current_shift = (glitch.current_shift + glitch.progression_rate * dt)
        .clamp(glitch.initial_shift, glitch.max_shift);

    let mut rng = rand::rng();
    if !glitch.active && glitch.time >= glitch.next_trigger {
        glitch.active = true;
        let base =
            glitch.min_duration + rng.random::<f32>() * (glitch.max_duration - glitch.min_duration);
        let bonus = (glitch.max_poison_duration - glitch.max_duration) * glitch.poison_level();
        glitch.effect_end = glitch.time + base + bonus;
    }
    if glitch.active && glitch.time >= glitch.effect_end {
        glitch.active = false;
        glitch.next_trigger = glitch.time
            + glitch.min_interval
            + rng.random::<f32>() * (glitch.max_interval - glitch.min_interval);
    }

    effects.chroma_intensity = if glitch.active { 1.0 } else { 0.0 };
    effects.chroma_shift = glitch.current_shift;
}

// ---------------------------------------------------------------------------
// Render world
// ---------------------------------------------------------------------------

#[derive(Resource)]
struct ScreenEffectsPipeline {
    layout: BindGroupLayoutDescriptor,
    sampler: Sampler,
    pipeline_id: CachedRenderPipelineId,
}

fn init_pipeline(
    mut commands: Commands,
    render_device: Res<RenderDevice>,
    asset_server: Res<AssetServer>,
    fullscreen_shader: Res<FullscreenShader>,
    pipeline_cache: Res<PipelineCache>,
) {
    let layout = BindGroupLayoutDescriptor::new(
        "screen_effects_bind_group_layout",
        &BindGroupLayoutEntries::sequential(
            ShaderStages::FRAGMENT,
            (
                texture_2d(TextureSampleType::Float { filterable: true }),
                sampler(SamplerBindingType::Filtering),
                uniform_buffer::<ScreenEffects>(true),
            ),
        ),
    );
    let sampler = render_device.create_sampler(&SamplerDescriptor::default());

    let pipeline_id = pipeline_cache.queue_render_pipeline(RenderPipelineDescriptor {
        label: Some("screen_effects_pipeline".into()),
        layout: vec![layout.clone()],
        vertex: fullscreen_shader.to_vertex_state(),
        fragment: Some(FragmentState {
            shader: asset_server.load(SHADER_PATH),
            targets: vec![Some(ColorTargetState {
                format: TextureFormat::Rgba8UnormSrgb,
                blend: None,
                write_mask: ColorWrites::ALL,
            })],
            ..default()
        }),
        ..default()
    });

    commands.insert_resource(ScreenEffectsPipeline {
        layout,
        sampler,
        pipeline_id,
    });
}

#[derive(Default)]
struct BindGroupCache {
    cached: Option<(TextureViewId, BindGroup)>,
}

fn run_pass(
    view: ViewQuery<(
        &ViewTarget,
        &ScreenEffects,
        &DynamicUniformIndex<ScreenEffects>,
    )>,
    pipeline: Option<Res<ScreenEffectsPipeline>>,
    pipeline_cache: Res<PipelineCache>,
    uniforms: Res<ComponentUniforms<ScreenEffects>>,
    mut cache: Local<BindGroupCache>,
    mut ctx: RenderContext,
) {
    let Some(effects_pipeline) = pipeline else {
        return;
    };
    let (view_target, _settings, settings_index) = view.into_inner();

    let Some(render_pipeline) = pipeline_cache.get_render_pipeline(effects_pipeline.pipeline_id)
    else {
        return;
    };
    let Some(settings_binding) = uniforms.uniforms().binding() else {
        return;
    };

    // Flips the view target's main texture; the destination must be written.
    let post_process = view_target.post_process_write();

    let bind_group = match &mut cache.cached {
        Some((texture_id, bind_group)) if post_process.source.id() == *texture_id => bind_group,
        cached => {
            let bind_group = ctx.render_device().create_bind_group(
                "screen_effects_bind_group",
                &pipeline_cache.get_bind_group_layout(&effects_pipeline.layout),
                &BindGroupEntries::sequential((
                    post_process.source,
                    &effects_pipeline.sampler,
                    settings_binding.clone(),
                )),
            );
            let (_, bind_group) = cached.insert((post_process.source.id(), bind_group));
            bind_group
        }
    };

    let mut render_pass = ctx
        .command_encoder()
        .begin_render_pass(&RenderPassDescriptor {
            label: Some("screen_effects_pass"),
            color_attachments: &[Some(RenderPassColorAttachment {
                view: post_process.destination,
                depth_slice: None,
                resolve_target: None,
                ops: Operations::default(),
            })],
            depth_stencil_attachment: None,
            timestamp_writes: None,
            occlusion_query_set: None,
            multiview_mask: None,
        });

    render_pass.set_pipeline(render_pipeline);
    render_pass.set_bind_group(0, bind_group, &[settings_index.index()]);
    render_pass.draw(0..3, 0..1);
}
