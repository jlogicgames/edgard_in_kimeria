package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.MathUtils;

/**
 * The rocking of a small monster's body when the hero passes (D101), shared by every small monster's body: a damped swing of the
 * same frame about the bottom of the body. A push starts it the way the hero moves; it settles on its own. Reactive, not an idle
 * animation (D76), and silent. Plain fields, no allocation.
 */
public final class RemainsSwing {
    /** A push: the swing's first peak, in degrees, for a hero passing; the swing never exceeds the second. */
    public static final float KICK_DEGREES = 10, MAX_DEGREES = 24;
    /**
     * About 2.5 swings a second, under 5 % of the push in 1.2 s; it stops once the swing is under {@code REST_DEGREES} (0.3
     * degrees moves a body's tips by under 0.1 px), about 1.4 s after a walking push.
     */
    private static final float HZ = 2.5f, DECAY = 2.5f, REST_DEGREES = .3f;
    private static final float OMEGA = MathUtils.PI2 * HZ, REST_SPEED = REST_DEGREES * OMEGA;
    private static final int TWO = 2;

    private float angle, speed;

    /** The swing now, in degrees (0 at rest; positive: the body's top leans right). */
    public float angle() { return angle; }

    /** A push the way {@code direction} (+1 right, -1 left) points. */
    public void push(float direction) { speed += direction * KICK_DEGREES * OMEGA; }

    /** One step of the damped swing (semi-implicit Euler, stable at every frame rate the game runs). */
    public void update(float dt) {
        speed += (-OMEGA * OMEGA * angle - TWO * DECAY * speed) * dt;
        angle += speed * dt;
        if (angle > MAX_DEGREES) { angle = MAX_DEGREES; speed = 0; }
        else if (angle < -MAX_DEGREES) { angle = -MAX_DEGREES; speed = 0; }
        if (Math.abs(angle) < REST_DEGREES && Math.abs(speed) < REST_SPEED) angle = speed = 0;
    }

    public void reset() { angle = speed = 0; }
}
