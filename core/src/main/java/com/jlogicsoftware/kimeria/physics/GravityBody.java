package com.jlogicsoftware.kimeria.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** Port of Dart's {@code GravityMixin}. */
public interface GravityBody {
    /**
     * The frame time the movement was designed at (the desktop's 60 fps). Gravity and the jump launch are tuned per
     * reference step; every other frame time (a browser at 120 or 144 Hz, uneven frames) gives the same arc (D98).
     */
    static final float REFERENCE_STEP = 1f / 60f;
    /** The vertical speed (px/s) under which a body counts as resting, unless it says otherwise. */
    static final float RESTING_SPEED = 20f;

    Vector2 position();

    Vector2 velocity();

    boolean isOnGround();

    void setOnGround(boolean value);

    /** Velocity gained per {@link #REFERENCE_STEP} of fall, px/s: 9.8 per step is 588 px/s². */
    default float gravityAcceleration() {
        return 9.8f;
    }

    default float terminalVelocity() {
        return 300f;
    }

    default float jumpForce() {
        return 260f;
    }

    /** Below this vertical speed (px/s) a body counts as resting, for the one-way platform catch-up margin. */
    default float restingSpeed() {
        return RESTING_SPEED;
    }

    /**
     * Adds gravity to vertical velocity, clamps it, and integrates position.y over {@code dt}, independent of the frame
     * rate (D98). Gravity was added per frame, so a 120 Hz browser doubled it and halved the jump. The position step
     * is exact for constant acceleration and reproduces the 60 Hz semi-implicit Euler arc it replaces: that arc is the
     * continuous one with the velocity raised by {@code g * REFERENCE_STEP / 2}, hence the {@code (REFERENCE_STEP - dt)}
     * term, which is zero at 60 Hz. A step that hits the velocity clamp moves at the clamped velocity, as before.
     */
    default void applyGravity(float dt) {
        Vector2 v = velocity();
        float g = gravityPerSecond();
        float free = v.y + g * dt;
        v.y = MathUtils.clamp(free, -jumpForce(), terminalVelocity());
        position().y += v.y == free ? free * dt + g * dt * (REFERENCE_STEP - dt) / 2f : v.y * dt;
    }

    /** Gravity in px/s²: {@link #gravityAcceleration()} per {@link #REFERENCE_STEP}. */
    default float gravityPerSecond() {
        return gravityAcceleration() / REFERENCE_STEP;
    }

    /** True when this frame's {@link #applyGravity} started from rest: the velocity is exactly one step of gravity. */
    default boolean fellFromRest(float dt) {
        return velocity().y == gravityPerSecond() * dt;
    }

    /** How far {@link #applyGravity} moves a body that starts the step at rest: {@code gravityAcceleration()} px per second at 60 Hz. */
    default float restingFallStep(float dt) {
        return gravityPerSecond() * dt * (dt + REFERENCE_STEP) / 2f;
    }

    default void resetVerticalVelocity() {
        velocity().y = 0;
    }
}
