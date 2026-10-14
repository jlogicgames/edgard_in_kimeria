package com.jlogicsoftware.kimeria.world;

/** Real-time fade. Crossing black invokes the room switch exactly once. */
public final class RoomFade {
    public static final float HALF_SECONDS = 0.25f;
    private float elapsed;
    private boolean active, switched;
    public boolean active() { return active; }
    public void start() { if (!active) { active = true; switched = false; elapsed = 0; } }
    public void cancel() { active = false; elapsed = 0; switched = false; }
    public void update(float dt, Runnable switchRoom) {
        if (!active) return;
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
