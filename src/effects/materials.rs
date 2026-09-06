//! `Material2d` wrappers around the ported WGSL shaders.
//!
//! Flutter's `FragmentProgram` took a flat list of floats set by index, which is
//! why the Dart is full of `_shader.setFloat(4, ...)` with a comment naming the
//! uniform. Here each shader gets a named uniform struct, so a reordered field
//! is a compile error instead of a silently wrong effect.
//!
//! All three blend with straight alpha. The Dart drew the shockwave with
//! `BlendMode.plus`; reproducing additive blending would mean overriding the
//! pipeline's blend state in `specialize`, and on this game's dark backdrop the
//! visible difference is a slightly softer ring, so straight alpha is used.

use bevy::prelude::*;
use bevy::render::render_resource::{AsBindGroup, ShaderType};
use bevy::shader::ShaderRef;
use bevy::sprite_render::{AlphaMode2d, Material2d};

#[derive(Clone, Copy, ShaderType)]
pub struct ShockwaveParams {
    /// Quad size in pixels; used only for aspect correction.
    pub size: Vec2,
    /// Ring centre in UV space.
    pub center: Vec2,
    pub time: f32,
    pub progress: f32,
    /// Radius in UV units.
    pub max_radius: f32,
    /// Ring thickness in UV units.
    pub width: f32,
}

#[derive(Asset, TypePath, AsBindGroup, Clone)]
pub struct ShockwaveMaterial {
    #[uniform(0)]
    pub params: ShockwaveParams,
}

impl Material2d for ShockwaveMaterial {
    fn fragment_shader() -> ShaderRef {
        "shaders/shockwave.wgsl".into()
    }

    fn alpha_mode(&self) -> AlphaMode2d {
        AlphaMode2d::Blend
    }
}

#[derive(Clone, Copy, ShaderType)]
pub struct ExplosionParams {
    pub size: Vec2,
    pub time: f32,
    pub progress: f32,
}

#[derive(Asset, TypePath, AsBindGroup, Clone)]
pub struct ExplosionMaterial {
    #[uniform(0)]
    pub params: ExplosionParams,
}

impl Material2d for ExplosionMaterial {
    fn fragment_shader() -> ShaderRef {
        "shaders/bomb_explosion.wgsl".into()
    }

    fn alpha_mode(&self) -> AlphaMode2d {
        AlphaMode2d::Blend
    }
}

#[derive(Clone, Copy, ShaderType)]
pub struct FogParams {
    pub size: Vec2,
    pub ground_pos: f32,
    pub ground_add: f32,
    pub fade: f32,
    pub time: f32,
}

#[derive(Asset, TypePath, AsBindGroup, Clone)]
pub struct FogMaterial {
    #[uniform(0)]
    pub params: FogParams,
}

impl Material2d for FogMaterial {
    fn fragment_shader() -> ShaderRef {
        "shaders/fog.wgsl".into()
    }

    fn alpha_mode(&self) -> AlphaMode2d {
        AlphaMode2d::Blend
    }
}
