package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;

/**
 * A sprite that flies from A to B in screen space (logical 640x360, Y down), the one mechanism behind a pickup flying to its
 * HUD slot (#557), the checklist items flying to the healer and the elixir to Edgard (#541), D132.
 * Cosmetic: it reads no game state, takes no input and has no collision; nothing waits for it. Fixed storage, no allocation after
 * the constructor: a flight is one row of float arrays plus a cached {@link TextureRegion}; its warm trail and landing twinkle are
 * {@link KimeriaParticleSystem#KIND_FLAME} sparks in one shared pool, drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>The path is a quadratic Bezier from A to B whose control point is the midpoint raised by {@code arc} px. World Y grows
 * downward, so "raised" is a smaller y: every flight bows up on screen. The eased progress places the head and interpolates
 * its size; alpha stays 1 until {@code fadeStart} (a fraction of the flight) and then falls to 0 at B. Position and size are
 * rounded to whole pixels when drawn (the 16 px world art is nearest filtered; a fractional size shimmers).
 *
 * <p>Time advances only in {@link #update}, so a paused owner freezes its flights; dt is clamped to {@link #MAX_STEP} so a hitch
 * never teleports one, and the assist's time scale is not applied (the owner passes real time). A flight waits {@code delay}
 * seconds before it shows. When a flight reaches B its slot is freed; {@link #landed} stays true until the next update.
 */
public final class FlightFx {
    public static final int CAPACITY = 12, TRAIL_CAPACITY = 48;
    public static final byte EASE_IN = 0, EASE_OUT = 1, EASE_IN_OUT = 2;
    /** dt is clamped to this (1/20 s). */
    public static final float MAX_STEP = 1f / 20f;
    /** The trail: a 2.5 px spark every 0.03 s behind the head, living 0.2 s. */
    private static final float TRAIL_EVERY = .03f, TRAIL_LIFE = .2f, TRAIL_SIZE = 2.5f;
    /** The landing twinkle: four sparks 5 to 10 px out on the diagonals, 0.15 s. */
    private static final int TWINKLE_SPARKS = 4;
    private static final float TWINKLE_NEAR = 5, TWINKLE_FAR = 10, TWINKLE_LIFE = .15f, TWINKLE_SIZE = 3, QUARTER_TURN = 90, DIAGONAL = 45;
    private static final float WARM_R = 1f, WARM_G = .957f, WARM_B = .667f;
    private static final long SEED_MIX = 0xF11641FL;

    private final TextureRegion[] region = new TextureRegion[CAPACITY];
    private final float[] fromX = new float[CAPACITY], fromY = new float[CAPACITY], toX = new float[CAPACITY], toY = new float[CAPACITY],
        arc = new float[CAPACITY], sizeFrom = new float[CAPACITY], sizeTo = new float[CAPACITY], delay = new float[CAPACITY],
        seconds = new float[CAPACITY], age = new float[CAPACITY], fadeStart = new float[CAPACITY], red = new float[CAPACITY],
        green = new float[CAPACITY], blue = new float[CAPACITY], trailDue = new float[CAPACITY];
    private final byte[] ease = new byte[CAPACITY];
    private final boolean[] live = new boolean[CAPACITY], landedFlag = new boolean[CAPACITY], twinkle = new boolean[CAPACITY], trailOn = new boolean[CAPACITY];
    private final KimeriaParticleSystem trail;
    private int count;
    private float headX, headY;

    public FlightFx(long seed) { trail = new KimeriaParticleSystem(TRAIL_CAPACITY, seed ^ SEED_MIX); }

    /** The trail pool, for tests. */
    public KimeriaParticleSystem trail() { return trail; }

    /** Free slots: a caller that needs two (a pop and its flight) starts both or neither. */
    public int freeSlots() { return CAPACITY - count; }

    /** Live or waiting flights. */
    public int count() { return count; }

    /** True when the flight in {@code slot} reached B during the last {@link #update}. */
    public boolean landed(int slot) { return slot >= 0 && slot < CAPACITY && landedFlag[slot]; }

    /** True while {@code slot} holds a flight that has not started yet (its delay is running). */
    public boolean waiting(int slot) { return slot >= 0 && slot < CAPACITY && live[slot] && age[slot] < 0; }

    /** True while {@code slot} holds a flight (waiting or flying). */
    public boolean active(int slot) { return slot >= 0 && slot < CAPACITY && live[slot]; }

    /**
     * Starts a flight and returns its slot, or -1 when every slot is taken (the request is dropped, never a live flight).
     * {@code arc} is how far the path bows up; sizes are in px (the sprite is drawn square); {@code fadeStart} is the fraction of
     * the flight after which it fades; the colour is the trail's (none when {@code leavesTrail} is false); {@code landingTwinkle} adds the landing sparks at B.
     */
    public int start(TextureRegion sprite, float ax, float ay, float bx, float by, float bow, float startSize, float endSize,
                     float delaySeconds, float flightSeconds, byte easing, float fade, float r, float g, float b, boolean leavesTrail, boolean landingTwinkle) {
        int i = 0;
        while (i < CAPACITY && live[i]) i++;
        if (i == CAPACITY) return -1;
        region[i] = sprite;
        fromX[i] = ax; fromY[i] = ay; toX[i] = bx; toY[i] = by; arc[i] = bow;
        sizeFrom[i] = startSize; sizeTo[i] = endSize;
        delay[i] = delaySeconds; seconds[i] = flightSeconds; age[i] = -delaySeconds;
        fadeStart[i] = fade; ease[i] = easing; red[i] = r; green[i] = g; blue[i] = b;
        trailDue[i] = 0; trailOn[i] = leavesTrail; live[i] = true; landedFlag[i] = false; twinkle[i] = landingTwinkle;
        count++;
        return i;
    }

    /** Progress 0..1 of the flight in {@code slot} (0 while it waits), for tests. */
    public float progress(int slot) { return live[slot] ? MathUtils.clamp(age[slot] / seconds[slot], 0f, 1f) : 0f; }

    public void update(float dt) {
        float step = Math.min(Math.max(dt, 0f), MAX_STEP);
        for (int i = 0; i < CAPACITY; i++) landedFlag[i] = false;
        for (int i = 0; i < CAPACITY; i++) {
            if (!live[i]) continue;
            age[i] += step;
            if (age[i] < 0) continue;
            if (age[i] >= seconds[i]) { land(i); continue; }
            trailDue[i] -= step;
            if (trailOn[i] && trailDue[i] <= 0) {
                trailDue[i] = TRAIL_EVERY;
                place(i, MathUtils.clamp(age[i] / seconds[i], 0f, 1f));
                trail.spawn(KimeriaParticleSystem.KIND_FLAME, headX, headY, headX, headY, TRAIL_LIFE, TRAIL_SIZE, red[i], green[i], blue[i]);
            }
        }
        trail.update(step);
    }

    private void land(int i) {
        live[i] = false;
        landedFlag[i] = true;
        count--;
        if (!twinkle[i]) return;
        for (int n = 0; n < TWINKLE_SPARKS; n++) {
            float a = MathUtils.degRad * (DIAGONAL + QUARTER_TURN * n), d = TWINKLE_NEAR + trail.random().nextFloat() * (TWINKLE_FAR - TWINKLE_NEAR);
            trail.spawn(KimeriaParticleSystem.KIND_FLAME, toX[i], toY[i], toX[i] + MathUtils.cos(a) * d, toY[i] + MathUtils.sin(a) * d,
                TWINKLE_LIFE, TWINKLE_SIZE, WARM_R, WARM_G, WARM_B);
        }
    }

    private static float eased(byte kind, float t) {
        return switch (kind) {
            case EASE_IN -> Interpolation.pow3In.apply(t);
            case EASE_OUT -> Interpolation.pow2Out.apply(t);
            default -> Interpolation.smooth.apply(t);
        };
    }

    /** Puts the head of flight {@code i} at linear progress {@code t} into {@link #headX} and {@link #headY}. */
    private void place(int i, float t) {
        float e = eased(ease[i], t), u = 1f - e;
        float cx = (fromX[i] + toX[i]) / 2f, cy = (fromY[i] + toY[i]) / 2f - arc[i]; // Y down: the bow subtracts
        headX = u * u * fromX[i] + 2 * u * e * cx + e * e * toX[i];
        headY = u * u * fromY[i] + 2 * u * e * cy + e * e * toY[i];
    }

    /** Where the head of the flight in {@code slot} is, x then y into the two array cells; for tests. */
    public void headOf(int slot, float t, float[] out) {
        place(slot, t);
        out[0] = headX; out[1] = headY;
    }

    public void render(Batch batch) {
        for (int i = 0; i < CAPACITY; i++) {
            if (!live[i] || age[i] < 0) continue;
            float t = MathUtils.clamp(age[i] / seconds[i], 0f, 1f), e = eased(ease[i], t);
            place(i, t);
            float size = Math.max(1f, Math.round(sizeFrom[i] + (sizeTo[i] - sizeFrom[i]) * e));
            float alpha = t <= fadeStart[i] ? 1f : Math.max(0f, 1f - (t - fadeStart[i]) / (1f - fadeStart[i]));
            batch.setColor(1f, 1f, 1f, alpha);
            batch.draw(region[i], Math.round(headX - size / 2f), Math.round(headY - size / 2f), size, size);
        }
        batch.setColor(1f, 1f, 1f, 1f);
        if (trail.count() > 0) trail.render(batch, Shaders.whiteQuad());
    }

    /** Drops every flight and spark: death, retry, restart, a room change, a closed screen. */
    public void clear() {
        for (int i = 0; i < CAPACITY; i++) { live[i] = false; landedFlag[i] = false; region[i] = null; }
        count = 0;
        trail.clear();
    }
}
