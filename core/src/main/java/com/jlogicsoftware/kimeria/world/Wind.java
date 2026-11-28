package com.jlogicsoftware.kimeria.world;

/**
 * The shared gust model (#593, D163): a soft band that crosses a room in the wind's direction, one gust at a time, with calm
 * between. It holds the direction, the gust cycle and each gust's front position, width, speed and strength envelope, and knows
 * nothing about drawing or about the forest: a stage gives its own {@link Profile}. The forest reads it for looks only (the gust
 * glints, {@code effects.CrownGlintFx}). The Steppe wind (#108, #109, #400) is to use its own profile and read
 * {@link #intensityAt} with lag 0, so the cue the player reads and the force the player feels come from one front; shelter, vertical
 * force and authored schedules are not part of this class.
 *
 * <p>World X grows to the right: direction +1 blows from the room's left edge to its right edge. Everything is a function of
 * (seed, accumulated time): a gust's width and period come from the stateless {@link #hash}, so what the camera sees never
 * changes the schedule and a retry repeats it. Time moves only through {@link #update}: a paused world freezes the wind. A "lag"
 * is how many seconds behind the leading edge a reader runs (a deeper layer, a leaf's own delay).
 *
 * <p>One gust is alive at a time: its sweep lasts until the front, run by the largest lag, has carried the band out of the room
 * (and {@link #END_MARGIN_SECONDS} longer), and the period is never shorter than that sweep plus {@link #MIN_CALM_SECONDS}, so a long room stretches the period instead of
 * overlapping gusts. Primitive state only: nothing is allocated after the constructor.
 */
public final class Wind {
    /**
     * A stage's wind: {@code direction} +1 or -1 on x, {@code speed} of the front in px/s, the band's width range in px, the range of
     * the period from one gust's start to the next in s, the delay of the first gust after a reset in s, the envelope's value at the
     * room's edges (0 to 1; it is 1 mid-room) and {@code maxLag}, the longest lag any reader uses, in s.
     */
    public record Profile(int direction, float speed, float minWidth, float maxWidth, float minPeriod, float maxPeriod,
                          float firstDelay, float edgeStrength, float maxLag) {
        public Profile {
            if (direction != 1 && direction != -1) throw new IllegalArgumentException("direction must be 1 or -1: " + direction);
            if (!(speed > 0 && minWidth > 0 && maxWidth >= minWidth && minPeriod > 0 && maxPeriod >= minPeriod && firstDelay >= 0 && maxLag >= 0))
                throw new IllegalArgumentException("wind profile out of range");
        }
    }

    /** The shortest calm between the end of one gust's sweep and the next gust's start, s. */
    public static final float MIN_CALM_SECONDS = .3f;
    /**
     * The sweep runs this long past the moment the slowest reader's band leaves the room, s: a reader that finds a crossing up to
     * this late (a long frame, 10 a second) still ends what it starts within the sweep, so the calm stays empty.
     */
    public static final float END_MARGIN_SECONDS = .1f;
    /** The salts of the wind's own draws; negative, so a reader's salts (0 and up) never collide with them. */
    private static final int WIDTH_SALT = -1, PERIOD_SALT = -2;
    /** MurmurHash3's finaliser and two odd multipliers that spread the gust and salt over the word. */
    private static final int GUST_SPREAD = 0x9E3779B1, SALT_SPREAD = 0x85EBCA77, MIX_A = 0x85EBCA6B, MIX_B = 0xC2B2AE35;
    private static final int SHIFT_A = 16, SHIFT_B = 13, UNIT_SHIFT = 8;
    private static final float UNIT = 1f / (1 << 24);
    private static final double HALF_TURN = Math.PI;

    private final Profile profile;
    private int seed, gust = -1;
    private float left, right;
    private double time, start, nextStart;
    private float width, period, sweep;

    public Wind(Profile profile) {
        this.profile = profile;
        reset(0, 0, 0);
    }

    /** Room start, death, retry and revisit: no gust yet, the first one starts {@link Profile#firstDelay} s from now at the upwind edge. */
    public void reset(int seed, float left, float right) {
        this.seed = seed;
        this.left = left;
        this.right = right;
        gust = -1;
        time = start = 0;
        nextStart = profile.firstDelay;
        width = period = sweep = 0;
    }

    public void update(float dt) {
        time += dt;
        while (time >= nextStart) {
            gust++;
            start = nextStart;
            width = between(profile.minWidth, profile.maxWidth, hash(seed, gust, WIDTH_SALT));
            sweep = (right - left + width) / profile.speed + profile.maxLag + END_MARGIN_SECONDS;
            period = Math.max(between(profile.minPeriod, profile.maxPeriod, hash(seed, gust, PERIOD_SALT)), sweep + MIN_CALM_SECONDS);
            nextStart = start + period;
        }
    }

    private static float between(float min, float max, float share) { return min + (max - min) * share; }

    public int direction() { return profile.direction; }
    public float speed() { return profile.speed; }
    /** The latest gust started since the reset, counting from 0; -1 before the first one. */
    public int gust() { return gust; }
    /** True while the latest gust is still crossing; false before the first gust and in the calm. */
    public boolean blowing() { return gust >= 0 && time - start < sweep; }
    /** This gust's band width in px; 0 before the first gust. */
    public float width() { return width; }
    /** This gust's start to the next one's, s; 0 before the first gust. */
    public float period() { return period; }
    /** How long this gust crosses, s; 0 before the first gust. */
    public float sweepSeconds() { return sweep; }
    /** Seconds left of this gust's sweep; 0 in the calm and before the first gust. */
    public float remainingSeconds() { return blowing() ? sweep - (float) (time - start) : 0; }
    /** Seconds since this gust started; negative or 0 only before the first gust. */
    public float gustAge() { return gust < 0 ? 0 : (float) (time - start); }

    /** True when world {@code x} lies in the room of the last reset: a gust lives from the room's one edge to the other. */
    public boolean covers(float x) { return x >= left && x <= right; }

    private float upwindEdge() { return profile.direction > 0 ? left : right; }

    /** The leading edge in world px of the front that runs {@code lagSeconds} behind; the upwind room edge before the first gust. */
    public float frontX(float lagSeconds) {
        if (gust < 0) return upwindEdge();
        return upwindEdge() + profile.direction * profile.speed * (float) (time - start - lagSeconds);
    }

    /** The gust's envelope at that front: {@code edge + (1 - edge) sin(pi p)}, p the front's progress across the room; 0 in the calm. */
    public float strength(float lagSeconds) {
        if (!blowing()) return 0f;
        float span = right - left;
        float progress = span > 0 ? Math.min(1f, Math.max(0f, profile.direction * (frontX(lagSeconds) - upwindEdge()) / span)) : 0f;
        return profile.edgeStrength + (1f - profile.edgeStrength) * (float) Math.sin(HALF_TURN * progress);
    }

    /** {@link #strength} times the band's soft profile (a half sine from the leading edge to the tail) at world {@code x}; 0 outside the band. */
    public float intensityAt(float x, float lagSeconds) {
        if (!blowing()) return 0f;
        float behind = profile.direction * (frontX(lagSeconds) - x);
        if (behind < 0 || behind > width) return 0f;
        return strength(lagSeconds) * (float) Math.sin(HALF_TURN * behind / width);
    }

    /** A number in [0, 1) fixed by (seed, gust, salt): stateless, the same on desktop and web, independent of the order of calls. */
    public static float hash(int seed, int gust, int salt) {
        int h = mix(seed ^ gust * GUST_SPREAD);
        h = mix(h ^ salt * SALT_SPREAD);
        return (h >>> UNIT_SHIFT) * UNIT;
    }

    private static int mix(int h) {
        h ^= h >>> SHIFT_A;
        h *= MIX_A;
        h ^= h >>> SHIFT_B;
        h *= MIX_B;
        h ^= h >>> SHIFT_A;
        return h;
    }
}
