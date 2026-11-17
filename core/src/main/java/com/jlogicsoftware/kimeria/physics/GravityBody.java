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

    /**
     * Adds gravity to vertical velocity, clamps it, and integrates position.y over {@code dt}, independent of the frame
     * rate (D98). Gravity was added per frame, so a 120 Hz browser doubled it and halved the jump. The position step
     * is exact for constant acceleration and reproduces the 60 Hz semi-implicit Euler arc it replaces: that arc is the
     * continuous one with the velocity raised by {@code g * REFERENCE_STEP / 2}, hence the {@code (REFERENCE_STEP - dt)}
     * term, which is zero at 60 Hz. A step that hits the velocity clamp moves at the clamped velocity, as before.
     */
    default void applyGravity(float dt) {
        Vector2 v = velocity();
        float g = gravityAcceleration() / REFERENCE_STEP;
        float free = v.y + g * dt;
        v.y = MathUtils.clamp(free, -jumpForce(), terminalVelocity());
        position().y += v.y == free ? free * dt + g * dt * (REFERENCE_STEP - dt) / 2f : v.y * dt;
    }

    default void resetVerticalVelocity() {
        velocity().y = 0;
    }
}
