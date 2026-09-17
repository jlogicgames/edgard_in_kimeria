//! Full-view fog. Only spawned for the main menu/About screen — see
//! `MenuFog` in `ui.rs` — not any level, despite the shader being a port of
//! the `forest` level's original effect.

use bevy::prelude::*;
use bevy::sprite_render::MeshMaterial2d;

use crate::LOGICAL_RESOLUTION;
use crate::camera::MainCamera;
use crate::core::z;

use super::materials::{FogMaterial, FogParams};

#[derive(Component, Default)]
pub struct FogEffect {
    elapsed: f32,
}

/// Fog is parented to the camera so it always covers the view.
pub(super) fn hydrate_fog(
    mut commands: Commands,
    mut meshes: ResMut<Assets<Mesh>>,
    mut materials: ResMut<Assets<FogMaterial>>,
    camera: Query<Entity, With<MainCamera>>,
    query: Query<Entity, (With<FogEffect>, Without<Mesh2d>)>,
) {
    let Ok(camera) = camera.single() else {
        return;
    };
    for entity in &query {
        commands
            .entity(entity)
            .insert((
                Mesh2d(meshes.add(Rectangle::new(LOGICAL_RESOLUTION.x, LOGICAL_RESOLUTION.y))),
                MeshMaterial2d(materials.add(FogMaterial {
                    params: FogParams {
                        size: LOGICAL_RESOLUTION,
                        // Literal uniform values.
                        ground_pos: 0.0,
                        ground_add: 0.0,
                        fade: 1.0,
                        time: 0.0,
                        _padding: Vec2::ZERO,
                    },
                })),
                Transform::from_xyz(0.0, 0.0, z::FOG),
            ))
            .insert(ChildOf(camera));
    }
}

/// Rescales the fog quad to the camera's *actual* visible area every frame,
/// instead of trusting the fixed `LOGICAL_RESOLUTION` mesh it was built
/// with. `ScalingMode::AutoMin` only guarantees that much is visible — on a
/// window whose aspect ratio isn't 16:9 (a tall phone-shaped one, say) it
/// shows *more* along one axis, and a fog quad that doesn't grow to match
/// left a band of bare screen on that axis instead of covering it.
pub(super) fn advance_fog(
    time: Res<Time<Real>>,
    camera: Query<&Projection, With<MainCamera>>,
    mut materials: ResMut<Assets<FogMaterial>>,
    mut query: Query<(&mut FogEffect, &MeshMaterial2d<FogMaterial>, &mut Transform)>,
) {
    let visible_size = match camera.single() {
        Ok(Projection::Orthographic(ortho)) => Some(ortho.area.size()),
        _ => None,
    };
    for (mut effect, handle, mut transform) in &mut query {
        effect.elapsed += time.delta_secs();
        if let Some(mut material) = materials.get_mut(&handle.0) {
            material.params.time = effect.elapsed;
            // The shader corrects for aspect ratio using `params.size`; if
            // it stayed fixed at `LOGICAL_RESOLUTION` while the mesh itself
            // stretched to a different aspect ratio below, the noise would
            // stretch un-evenly right along with it.
            if let Some(size) = visible_size {
                material.params.size = size;
            }
        }
        if let Some(size) = visible_size {
            transform.scale = (size / LOGICAL_RESOLUTION).extend(1.0);
        }
    }
}
