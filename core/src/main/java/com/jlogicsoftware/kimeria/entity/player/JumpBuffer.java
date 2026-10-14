package com.jlogicsoftware.kimeria.entity.player;

/** A fresh press on any input source queues one jump for 100 ms of real time. */
final class JumpBuffer {
    static final float WINDOW_SECONDS = 0.1f;

    private boolean keyboardHeld, gamepadHeld, touchHeld;
    private float age = WINDOW_SECONDS;

    void update(float realDt, boolean keyboard, boolean gamepad, boolean touch) {
        age += realDt;
        if ((keyboard && !keyboardHeld) || (gamepad && !gamepadHeld) || (touch && !touchHeld)) {
            age = 0f;
        }
        keyboardHeld = keyboard;
        gamepadHeld = gamepad;
        touchHeld = touch;
    }

    boolean pending() {
        return age < WINDOW_SECONDS;
    }

    boolean consume(boolean canJump) {
        if (!canJump || !pending()) return false;
        age = WINDOW_SECONDS;
        return true;
    }

    /** Discard old presses and require each source to be released before it can queue another. */
    void reset() {
        age = WINDOW_SECONDS;
        keyboardHeld = gamepadHeld = touchHeld = true;
    }
}
