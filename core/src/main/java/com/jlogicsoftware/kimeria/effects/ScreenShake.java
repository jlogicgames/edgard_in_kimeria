package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.function.BooleanSupplier;

/**
 * Camera shake: one decaying sine wobble. {@link #start} keeps the stronger of the running and the new shake;
 * the offset is {@code amplitude * remaining/total * sin(age * frequency)} on each axis. The offset is purely visual
 * (added to the camera position only) and zero while the player's Screen shake option is off, for every shake.
 *
 * <p>Allocation free: the result vector is a field, overwritten by the next call.
 */
public final class ScreenShake {
    /** The boss feedback shake (attack landing, empowered hit); unchanged by the move from {@code Level}. */
    public static final float BOSS_AMPLITUDE_PX = 1.25f, BOSS_SECONDS = .18f;
    /** A lever-driven wall in motion: a little heavier than nothing, lighter than a boss attack would be if it lasted. */
    public static final float LEVER_AMPLITUDE_PX = 1.5f;
    private static final float FREQUENCY_X = 110, FREQUENCY_Y = 137;

    private final BooleanSupplier enabled;
    private final Vector2 offset = new Vector2();
    private float amplitude, total, remaining;

    /** @param enabled read at every query; false (the Screen shake option off) makes every offset zero */
    public ScreenShake(BooleanSupplier enabled) { this.enabled = enabled; }

    /** Starts a shake unless a running one is still stronger (its amplitude decayed to now); an equal one restarts. */
    public void start(float amplitudePx, float seconds) {
        if (!(amplitudePx > 0) || !(seconds > 0)) return;
        if (remaining > 0 && amplitudePx < currentAmplitude()) return;
        amplitude = amplitudePx;
        total = remaining = seconds;
    }

    public void update(float dt) { remaining = Math.max(0, remaining - dt); }

    public void clear() { remaining = 0; }

    /** True while the Screen shake option is on; shake-driven scenery (hanging vines) stays still when it is off. */
    public boolean enabled() { return enabled.getAsBoolean(); }

    public boolean running() { return remaining > 0; }

    /** The running shake's amplitude now, 0 when none runs. */
    public float currentAmplitude() { return remaining > 0 ? amplitude * (remaining / total) : 0; }

    /**
     * The camera displacement for a view inside a room. A room no wider (taller) than the view keeps its centred
     * framing, so that axis does not move; otherwise the shaken view is clamped to the room bounds.
     * The returned vector is a field of this object: read it at once and do not store it.
     */
    public Vector2 offset(Rectangle room, Rectangle view) {
        if (remaining <= 0 || !enabled.getAsBoolean()) return offset.set(0, 0);
        float age = total - remaining;
        float strength = currentAmplitude();
        float dx = (float) Math.sin(age * FREQUENCY_X) * strength;
        float dy = (float) Math.sin(age * FREQUENCY_Y) * strength;
        if (room.width <= view.width) dx = 0;
        else dx = Math.max(room.x, Math.min(room.x + room.width - view.width, view.x + dx))
            - Math.max(room.x, Math.min(room.x + room.width - view.width, view.x));
        if (room.height <= view.height) dy = 0;
        else dy = Math.max(room.y, Math.min(room.y + room.height - view.height, view.y + dy))
            - Math.max(room.y, Math.min(room.y + room.height - view.height, view.y));
        return offset.set(dx, dy);
    }
}
