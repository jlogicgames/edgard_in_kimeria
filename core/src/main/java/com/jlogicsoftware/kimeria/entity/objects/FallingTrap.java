package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Shared rules of the position-triggered falling traps (D45, #232): the hero's body entering an authored
 * zone starts a warning of {@link TrapWarnings#DEFAULT_WARNING_SECONDS} (shared D7 value, longer with the
 * assist); then the body falls straight down with constant gravity until it lands on the first solid below;
 * while it falls its swept box kills on contact; once landed it is harmless scenery until the room is
 * rebuilt (retry, Restart Level, room re-entry). It is one-shot, never a pickup or a blocker, and it ticks
 * only inside {@code Level.update}, so pause stops it.
 *
 * <p>Kill rule: {@link #sparesAirborneHero()} traps (the branch) hurt a hero who stands in the column while
 * they fall and everyone overlapping the landed box at impact, but let a hero who is airborne during the
 * fall pass until impact; the others (the stone) hurt on any contact. Death goes through the hero's own
 * {@code collidedWithActor(false)}, the same entry the bomb blast uses, with the same guards.
 */
public abstract class FallingTrap extends Entity {
    public enum State { ARMED, WARNING, FALLING, SPENT }

    /** Fall acceleration in px/s^2: a 80 px drop takes 0.42 s, a 288 px drop 0.8 s. */
    public static final float GRAVITY = 900f;
    /** The hero's body box as {@code Player} defines it (offset in its 48x48 frame, then size); a test pins these. */
    public static final float HERO_BODY_OFFSET_X = 18, HERO_BODY_OFFSET_Y = 26, HERO_BODY_WIDTH = 11, HERO_BODY_HEIGHT = 22;
    /** Shortest flight a trap may be authored with (s); a shorter drop could not be run or jumped away from. */
    public static final float MIN_FLIGHT_SECONDS = 0.3f;

    private final TrapWarnings warnings;
    private final Rectangle zone;
    private final Supplier<Rectangle> hero;
    private final Runnable kill;
    private final BooleanSupplier heroGrounded;
    private final float landingBottom;
    private State state = State.ARMED;
    private float warningTotal, warningElapsed, speed;

    /**
     * @param body          the authored rectangle: where the trap hangs and its lethal footprint
     * @param zone          the authored trigger rectangle (world pixels)
     * @param landingBottom the y (Y-down) the body's bottom edge stops at: the top of the first solid below
     * @param hero          the hero's body box, read every tick (the hero object changes on retry)
     * @param kill          what contact does: the usual instant death
     */
    protected FallingTrap(Rectangle body, Rectangle zone, float landingBottom, TrapWarnings warnings,
                          Supplier<Rectangle> hero, BooleanSupplier heroGrounded, Runnable kill) {
        super(body.x, body.y, body.width, body.height);
        this.zone = new Rectangle(zone);
        this.landingBottom = landingBottom;
        this.warnings = warnings;
        this.hero = hero;
        this.kill = kill;
        this.heroGrounded = heroGrounded;
    }

    /** True when a hero who is in the air during the fall is not hurt until the impact itself. */
    protected boolean sparesAirborneHero() { return false; }

    /** Flight time in seconds from rest to landing for a drop of {@code distance} px. */
    public static float flightSeconds(float distance) {
        return (float) Math.sqrt(2f * distance / GRAVITY);
    }

    public State state() { return state; }
    public Rectangle zone() { return new Rectangle(zone); }
    public float landingBottom() { return landingBottom; }
    public float warningTotalSeconds() { return warningTotal; }
    public float warningElapsedSeconds() { return warningElapsed; }
    /** 0..1 progress through the warning (0 before it, 1 once it is over). */
    public float warningProgress() {
        return state == State.ARMED ? 0f : state == State.WARNING ? Math.min(1f, warningElapsed / warningTotal) : 1f;
    }
    public boolean isSpent() { return state == State.SPENT; }

    /** Where the body can kill right now, or null when harmless (not falling). */
    public Rectangle lethalBounds() {
        return state == State.FALLING ? new Rectangle(position.x, position.y, size.x, size.y) : null;
    }

    /** The body's box as drawn, whatever the state. */
    public Rectangle bodyBounds() { return new Rectangle(position.x, position.y, size.x, size.y); }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        switch (state) {
            case ARMED -> {
                if (hero.get().overlaps(zone)) {
                    state = State.WARNING;
                    warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
                    warningElapsed = 0f;
                }
            }
            case WARNING -> {
                warningElapsed += dt;
                if (warningElapsed >= warningTotal) {
                    state = State.FALLING;
                    speed = 0f;
                }
            }
            case FALLING -> fall(dt);
            case SPENT -> { }
        }
    }

    private void fall(float dt) {
        float top = position.y;
        speed += GRAVITY * dt;
        position.y += speed * dt;
        boolean lands = position.y + size.y >= landingBottom;
        if (lands) position.y = landingBottom - size.y;
        // Swept box: a long frame cannot tunnel through a hero who can be hurt this frame.
        Rectangle swept = new Rectangle(position.x, top, size.x, position.y + size.y - top);
        Rectangle landed = new Rectangle(position.x, position.y, size.x, size.y);
        boolean spare = sparesAirborneHero();
        boolean canHurt = !spare || lands || heroGrounded.getAsBoolean();
        if (canHurt && hero.get().overlaps(lands && spare ? landed : swept)) kill.run();
        if (lands) {
            state = State.SPENT;
            landed();
        }
    }

    /** Called once on impact, after the contact check. */
    protected void landed() { }
}
