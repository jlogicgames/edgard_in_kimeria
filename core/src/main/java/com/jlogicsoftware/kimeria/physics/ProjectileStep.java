package com.jlogicsoftware.kimeria.physics;

/** Shared time limits for thrown objects and fireballs crossing thin collision geometry. */
public final class ProjectileStep {
    private static final float MAX_FRAME_SECONDS = .05f;
    private static final float MAX_SUBSTEP_SECONDS = .008f;

    private ProjectileStep() { }

    public static float frameSeconds(float dt) { return Math.min(dt, MAX_FRAME_SECONDS); }
    public static int count(float dt) {
        return Math.max(1, (int) Math.ceil(frameSeconds(dt) / MAX_SUBSTEP_SECONDS));
    }
}
