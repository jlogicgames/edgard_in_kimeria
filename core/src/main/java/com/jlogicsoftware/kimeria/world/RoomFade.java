package com.jlogicsoftware.kimeria.world;

/** Real-time fade. Crossing black invokes the room switch exactly once. */
public final class RoomFade {
    public static final float HALF_SECONDS = 0.25f;
    private float elapsed, hold;
    private boolean active, switched;
    public boolean active() { return active; }
    /** True from the black midpoint (the switch has run) until the fade-in ends. */
    public boolean dark() { return active && switched; }
    public void start() { start(0); }
    /** Starts with a clear screen held for {@code holdSeconds} first (#553: the leaving ripple plays in it), then the fade as before. */
    public void start(float holdSeconds) { if (!active) { active = true; switched = false; elapsed = 0; hold = holdSeconds; } }
    /** True while the screen is held clear before the fade-out begins. */
    public boolean holding() { return active && hold > 0; }
    public void cancel() { active = false; elapsed = 0; switched = false; hold = 0; }
    public void update(float dt, Runnable switchRoom) {
        if (!active) return;
        if (hold > 0) { hold = Math.max(0, hold - dt); return; }
        // Keep the midpoint for one rendered frame, even after a long frame.
        if (!switched && elapsed + dt >= HALF_SECONDS) {
            elapsed = HALF_SECONDS;
            switched = true;
            switchRoom.run();
            return;
        }
        elapsed += dt;
        if (elapsed >= HALF_SECONDS * 2) active = false;
    }
    public float alpha() {
        if (!active) return 0;
        return Math.max(0, Math.min(1, switched ? 2 - elapsed / HALF_SECONDS : elapsed / HALF_SECONDS));
    }
}
