package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * Allocation-free blood: crimson droplets, fast streaks and short-lived floor splashes (#285, docs/DEVELOPMENT.md
 * "Particle system plan", step 3). Owned by the {@code Level}; a sword or fireball hit calls {@link #spray}, so no
 * object is created per hit. It follows the rules of {@link KimeriaParticleSystem} (struct-of-arrays, capacity fixed at
 * construction, a full pool drops the oldest drop, one seeded {@link RandomXS128}, floats only, live drops dense and in
 * spawn order) but keeps its own arrays: a drop is ballistic (velocity, gravity, a floor to land on), not a straight
 * lerp between two points, and carries its own red and green, so it does not fit {@code KimeriaParticleSystem}'s model.
 *
 * <p>World Y grows downward: a drop launched upward has a negative {@code vy}, gravity adds to {@code vy}, and the
 * floor is at {@code floorY}, the largest Y a drop may reach.
 */
public final class KimeriaBloodSystem {
    public static final int DROPLETS = 32, EMPOWERED_DROPLETS = 48;
    /** Longest drop life in seconds; the shortest is {@link #LIFE_MIN}. */
    public static final float DURATION = .9f;
    static final float LIFE_MIN = .6f;
    private static final float GRAVITY = 420f;
    /** A drop fades out over this many seconds before its life ends. */
    private static final float FADE_OUT_SECONDS = .2f;
    private static final float HALF = .5f;

    private static final float SPAWN_SPREAD_X = 2f, SPAWN_SPREAD_Y = 3f;
    private static final float SPEED_X_MIN = 65f, SPEED_X_MAX = 190f;
    private static final float LAUNCH_UP_MIN = -175f, LAUNCH_UP_MAX = -35f;
    /** Every fifth drop sprays back toward the sword at this fraction of the speed. */
    private static final int BACK_SPRAY_EVERY = 5;
    private static final float BACK_SPRAY_FACTOR = -.4f;
    /** The first {@code LARGE_DROPS} of each {@link #DROPLETS} are the thick ones. */
    private static final int LARGE_DROPS = 8;
    private static final float LARGE_SIZE_MIN = 1f, LARGE_SIZE_MAX = 1.5f, SMALL_SIZE_MIN = .4f, SMALL_SIZE_MAX = .9f;
    private static final float EMPOWERED_SIZE_SCALE = 1.8f, EMPOWERED_TRAIL_SCALE = 4f, EMPOWERED_RED_SCALE = 1.2f;
    private static final float TRAIL_MAX = 4.5f, TRAIL_BASE = 1f, TRAIL_PER_SPEED = .0175f;
    private static final float RED_MIN = .5f, RED_MAX = .68f, GREEN_MIN = .1f, GREEN_MAX = .15f;
    private static final float BLUE = .12f;
    private static final float HEAD_R = .7f, HEAD_G = .19f, HEAD_B = .18f;
    private static final float SPLASH_SPREAD = 3f, EMPOWERED_SPLASH_SPREAD = 6f;
    private static final float SPLASH_LIFT = .5f, EMPOWERED_SPLASH_LIFT = 1f;
    private static final float SPLASH_HEIGHT = .75f, EMPOWERED_SPLASH_HEIGHT = 1.5f;
    /** The streak trails behind the drop, so its quad points opposite to the velocity. */
    private static final float TRAIL_FLIP_DEGREES = 180f;
    private static final float ONE = 1f, NORMAL = 1f;

    private final int capacity;
    private final RandomXS128 random;
    private final float[] x, y, vx, vy, size, trailLength, life, age, red, green, floorY;
    private final boolean[] landed, empowered;
    private int count;

    public KimeriaBloodSystem(int capacity, long seed) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacity = capacity;
        this.random = new RandomXS128(seed);
        x = new float[capacity]; y = new float[capacity]; vx = new float[capacity]; vy = new float[capacity];
        size = new float[capacity]; trailLength = new float[capacity];
        life = new float[capacity]; age = new float[capacity];
        red = new float[capacity]; green = new float[capacity]; floorY = new float[capacity];
        landed = new boolean[capacity]; empowered = new boolean[capacity];
    }

    public int capacity() { return capacity; }
    /** Number of live drops. */
    public int count() { return count; }

    private float between(float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * Sprays {@link #DROPLETS} drops ({@link #EMPOWERED_DROPLETS} when empowered, thicker, with longer trails) from
     * ({@code originX}, {@code originY}) in {@code direction} (+1 right, -1 left); they land at {@code floorY}.
     * The random-call order per drop is the one of the old {@code BloodBurst} constructor.
     */
    public void spray(float originX, float originY, float direction, float floor, boolean empoweredHit) {
        int drops = empoweredHit ? EMPOWERED_DROPLETS : DROPLETS;
        for (int i = 0; i < drops; i++) {
            if (count == capacity) removeOldest();
            int d = count++;
            x[d] = originX + between(-SPAWN_SPREAD_X, SPAWN_SPREAD_X);
            y[d] = originY + between(-SPAWN_SPREAD_Y, SPAWN_SPREAD_Y);
            // A strong fan away from the sword, with a few drops spraying back toward it.
            vx[d] = direction * between(SPEED_X_MIN, SPEED_X_MAX) * (i % BACK_SPRAY_EVERY == 0 ? BACK_SPRAY_FACTOR : NORMAL);
            vy[d] = between(LAUNCH_UP_MIN, LAUNCH_UP_MAX);
            size[d] = i % DROPLETS < LARGE_DROPS ? between(LARGE_SIZE_MIN, LARGE_SIZE_MAX) : between(SMALL_SIZE_MIN, SMALL_SIZE_MAX);
            size[d] *= empoweredHit ? EMPOWERED_SIZE_SCALE : NORMAL;
            trailLength[d] = Math.min(TRAIL_MAX, TRAIL_BASE + Math.abs(vx[d]) * TRAIL_PER_SPEED) * (empoweredHit ? EMPOWERED_TRAIL_SCALE : NORMAL);
            life[d] = between(LIFE_MIN, DURATION);
            red[d] = between(RED_MIN, RED_MAX) * (empoweredHit ? EMPOWERED_RED_SCALE : NORMAL);
            green[d] = between(GREEN_MIN, GREEN_MAX);
            age[d] = 0;
            floorY[d] = floor;
            landed[d] = false;
            empowered[d] = empoweredHit;
        }
    }

    private void removeOldest() {
        for (int i = 1; i < count; i++) copy(i, i - 1);
        count--;
    }

    private void copy(int from, int to) {
        x[to] = x[from]; y[to] = y[from]; vx[to] = vx[from]; vy[to] = vy[from];
        size[to] = size[from]; trailLength[to] = trailLength[from];
        life[to] = life[from]; age[to] = age[from];
        red[to] = red[from]; green[to] = green[from]; floorY[to] = floorY[from];
        landed[to] = landed[from]; empowered[to] = empowered[from];
    }

    /** Ages every drop by {@code dt}, moves the airborne ones, lands those that reach the floor and drops the dead. */
    public void update(float dt) {
        int live = 0;
        for (int i = 0; i < count; i++) {
            float aged = age[i] + dt;
            if (aged >= life[i]) continue;
            if (live != i) copy(i, live);
            age[live] = aged;
            if (!landed[live]) {
                x[live] += vx[live] * dt;
                y[live] += vy[live] * dt + HALF * GRAVITY * dt * dt;
                vy[live] += GRAVITY * dt;
                if (y[live] >= floorY[live]) {
                    y[live] = floorY[live];
                    landed[live] = true;
                }
            }
            live++;
        }
        count = live;
    }

    public void clear() { count = 0; }

    /** State as {@link #render} uses it; for tests. */
    public float x(int i) { return x[i]; }
    public float y(int i) { return y[i]; }
    public float vx(int i) { return vx[i]; }
    public float vy(int i) { return vy[i]; }
    public float size(int i) { return size[i]; }
    public float trailLength(int i) { return trailLength[i]; }
    public float life(int i) { return life[i]; }
    public float age(int i) { return age[i]; }
    public boolean landed(int i) { return landed[i]; }
    public boolean empowered(int i) { return empowered[i]; }
    /** 1 until the last {@link #FADE_OUT_SECONDS} of the drop's life, then falling to 0. */
    public float fade(int i) { return Math.min(ONE, (life[i] - age[i]) / FADE_OUT_SECONDS); }

    /** Draws every live drop with the cached white quad and restores the batch colour (floats, no allocation). */
    public void render(Batch batch) {
        if (count == 0) return;
        float beforeR = batch.getColor().r, beforeG = batch.getColor().g, beforeB = batch.getColor().b, beforeAlpha = batch.getColor().a;
        TextureRegion dot = Shaders.whiteQuad();
        for (int i = 0; i < count; i++) {
            float alpha = fade(i) * beforeAlpha;
            batch.setColor(red[i], green[i], BLUE, alpha);
            if (landed[i]) {
                float spread = size[i] * (empowered[i] ? EMPOWERED_SPLASH_SPREAD : SPLASH_SPREAD);
                batch.draw(dot, x[i] - spread / 2, y[i] - (empowered[i] ? EMPOWERED_SPLASH_LIFT : SPLASH_LIFT),
                    spread, empowered[i] ? EMPOWERED_SPLASH_HEIGHT : SPLASH_HEIGHT);
            } else {
                float angle = MathUtils.atan2(vy[i], vx[i]) * MathUtils.radiansToDegrees + TRAIL_FLIP_DEGREES;
                batch.draw(dot, x[i], y[i] - size[i] / 2, 0, size[i] / 2,
                    trailLength[i], size[i], 1, 1, angle);
                batch.setColor(HEAD_R, HEAD_G, HEAD_B, alpha);
                batch.draw(dot, x[i] - size[i] / 2, y[i] - size[i] / 2, size[i], size[i]);
            }
        }
        batch.setColor(beforeR, beforeG, beforeB, beforeAlpha);
    }
}
