package com.jlogicsoftware.kimeria.entity.player;

import com.badlogic.gdx.math.MathUtils;

/**
 * The vertical half of the follow camera (D154, #678). Two steps. The focus is the hero's Y with some slack upward, so a jump
 * from the floor does not scroll; downward it has none, so a hero who has come down is framed as the room was authored. The
 * camera top then eases to the target that focus gives, starting and stopping gently, and lands on it exactly.
 * World Y grows downward: a smaller Y is higher.
 */
final class VerticalCameraFollow {

    /** How far (px) the hero may rise above the focus before it follows. A full jump from the floor stays inside it. */
    static final float RISE_SLACK = 24f;
    /** How hard the camera is drawn to its target (1/s) by a critically damped spring. */
    static final float STIFFNESS = 8f;
    /** Closer than this (px) and slower than this (px/s) the camera is on its target. */
    static final float SNAP_DISTANCE = .05f, SNAP_SPEED = 1f;

    private float focusY, top, speed;

    /** Puts the focus on the hero and the camera at rest at {@code cameraTop}: a spawn or a respawn. */
    void settle(float heroY, float cameraTop) {
        focusY = heroY;
        top = cameraTop;
        speed = 0;
    }

    /** Moves the focus the least distance needed to lie from {@code heroY} to {@link #RISE_SLACK} below it, and returns it. */
    float focus(float heroY) {
        focusY = MathUtils.clamp(focusY, heroY, heroY + RISE_SLACK);
        return focusY;
    }

    /**
     * Advances the camera top toward {@code target} by {@code dt} seconds and returns it. The step is the spring's exact
     * solution for a target at rest, so it does not depend on the frame rate (D98); {@code dt} 0 changes nothing. A camera
     * settled at rest never leaves the range of the targets it has had, so targets inside the room keep it inside.
     */
    float ease(float target, float dt) {
        if (dt <= 0) return top;
        float offset = top - target;
        float drive = (speed + STIFFNESS * offset) * dt;
        float decay = (float) Math.exp(-STIFFNESS * dt);
        top = target + (offset + drive) * decay;
        speed = (speed - STIFFNESS * drive) * decay;
        if (Math.abs(top - target) < SNAP_DISTANCE && Math.abs(speed) < SNAP_SPEED) {
            top = target;
            speed = 0;
        }
        return top;
    }
}
