package com.jlogicsoftware.kimeria.entity.player;

/** Stationary inspection: immediate pose, 0.3-second hold, then a one-second eased pan. */
final class LookAround {
    private static final float DELAY = .3f, INTERACTION_DELAY = 1f, PAN_SECONDS = 1f, RETURN_SECONDS = .2f, UP_DISTANCE = 100f, DOWN_DISTANCE = 150f;
    private int direction;
    private boolean suppressed = true;
    private float held, elapsed, from, to, offset;
    private float duration = RETURN_SECONDS;
    private float holdDelay = DELAY;

    void update(float dt, int requested) {
        update(dt, requested, requested != 0);
    }

    void update(float dt, int requested, boolean verticalHeld) {
        update(dt, requested, verticalHeld, false);
    }

    void update(float dt, int requested, boolean verticalHeld, boolean interactionAvailable) {
        if (suppressed) {
            if (!verticalHeld) suppressed = false;
            requested = 0;
        }
        if (requested != direction) {
            direction = requested;
            held = 0;
            holdDelay = interactionAvailable ? INTERACTION_DELAY : DELAY;
            startPan(0);
        }
        if (direction != 0) {
            float previous = held;
            held += dt;
            if (previous < holdDelay && held >= holdDelay) {
                startPan(direction < 0 ? -UP_DISTANCE : DOWN_DISTANCE);
                dt = held - holdDelay;
            }
        }
        elapsed = Math.min(duration, elapsed + dt);
        float t = elapsed / duration;
        offset = from + (to - from) * t * t * (3 - 2 * t);
    }

    private void startPan(float target) {
        from = offset;
        to = target;
        duration = target == 0 ? RETURN_SECONDS : PAN_SECONDS;
        elapsed = 0;
    }

    /** A UI boundary requires vertical release; the old offset returns smoothly on resume. */
    void suppress() {
        direction = 0;
        held = 0;
        suppressed = true;
        startPan(0);
    }

    void clear() {
        offset = 0;
        suppress();
    }

    /** The chase must keep up with the eased return, including at its maximum speed. */
    float cameraSpeed(float followSpeed) {
        return followSpeed + (elapsed < duration ? Math.abs(to - from) * 1.5f / duration : 0);
    }

    int direction() { return direction; }
    float offset() { return offset; }
}
