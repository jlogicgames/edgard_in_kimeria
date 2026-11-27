package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The lethal spore burst of the false honey fungus (D152, #608, D63): spores as small bright sparks that fly out of the cap tuft in all
 * directions at once and brake, and one ring that expands from the tuft. It replaced the swirling shader haze of #296.
 * <ul>
 * <li>{@link #OUTER_COUNT} sparks that reach most of the way to the edge of the lethal rectangle, corners included, and
 * {@link #INNER_COUNT} that stop nearer the tuft, all {@link KimeriaParticleSystem#KIND_SPARK} on a fixed pool, drawn with
 * {@code Shaders.whiteQuad()}; they hang and dim until the hitbox ends, so the danger is seen for its whole life;</li>
 * <li>one ring of {@link RingPool}, as large as fits the rectangle, over in {@link #RING_SECONDS}.</li>
 * </ul>
 * Every spark's start and end are kept half its largest drawn size inside the rectangle, positions stay on the straight line between
 * them and the streak points back towards the start, so no quad leaves the rectangle; the ring's quad is centred on the tuft with a
 * radius of half the square, the tuft being its middle (D5: what is drawn is no wider than what kills). The palette is the
 * lethal lime (the puffball's {@link PuffballFx} uses it too). The two stay apart by motion: radial sparks and a ring here, there an
 * upward jet, grit squares and a haze. World Y grows downward.
 * <p>
 * Nothing allocates after construction. {@link #update} only runs while the game runs (pause freezes it); the caller clears it with
 * the hitbox. A context that cannot build {@code shockwave.frag} shows the sparks without the ring.
 */
public final class SporeCloud {
    public static final int OUTER_COUNT = 24, INNER_COUNT = 12, SPARK_COUNT = OUTER_COUNT + INNER_COUNT;
    /** One burst per mushroom, so the live peak is the burst itself. */
    public static final int PARTICLE_CAPACITY = SPARK_COUNT;
    public static final float RING_SECONDS = 0.4f, RING_WIDTH = 3f;
    /** The sparks outlive the cloud by this much so the pool is never empty while the hitbox is live; it is cleared with it. */
    public static final float LIFE_MARGIN = 0.05f;
    /** How far a spark flies, as fractions of the way from the tuft to the rectangle's edge along its direction. */
    private static final float OUTER_REACH_MIN = 0.75f, OUTER_REACH_MAX = 1f, INNER_REACH_MIN = 0.3f, INNER_REACH_MAX = 0.65f;
    /** The head's side at birth; the streak is narrower. */
    private static final float SPARK_SIZE_MIN = 1.6f, SPARK_SIZE_MAX = 2f;
    /** Angle jitter as a fraction of the gap between two sparks of one group. */
    private static final float ANGLE_JITTER = 0.35f;
    /** Shares of the three limes among the sparks: bright, then mid, the rest dark. */
    private static final float LIGHT_SHARE = 0.5f, MID_SHARE = 0.85f;
    /** The lethal lime: a6e04a, 5fa02a, e6f26a (the former {@code trap-spores} palette). */
    private static final float DARK_R = 0.373f, DARK_G = 0.627f, DARK_B = 0.165f;
    private static final float MID_R = 0.651f, MID_G = 0.878f, MID_B = 0.290f;
    private static final float LIGHT_R = 0.902f, LIGHT_G = 0.949f, LIGHT_B = 0.416f;
    /** An intensity ({@code shockwave.frag} fades it and adds it to what is behind): the mid lime, bright enough to survive the fade. */
    private static final float[] RING_COLOR = {2.6f, 3.5f, 1.2f};
    private static final float HALF = 0.5f, TWO = 2f;

    private final KimeriaParticleSystem particles;
    private final RingPool ring = new RingPool(1, RING_COLOR);
    private final float tuftX, tuftY, radius, lifeSeconds;

    /**
     * @param left,top,size the lethal rectangle (a square); the cap tuft the burst comes from is its middle
     */
    public SporeCloud(float left, float top, float size, float lifeSeconds, long seed) {
        radius = size * HALF;
        tuftX = left + radius;
        tuftY = top + radius;
        this.lifeSeconds = lifeSeconds;
        particles = new KimeriaParticleSystem(PARTICLE_CAPACITY, seed);
    }

    public KimeriaParticleSystem particles() { return particles; }

    /** Live rings (0 or 1), for tests. */
    public int ringCount() { return ring.count(); }
    /** The radius the live ring grows to, for tests. */
    public float ringRadius() { return ring.radius(0); }
    /** The live ring's centre, for tests. */
    public float ringX() { return ring.centreX(0); }
    public float ringY() { return ring.centreY(0); }

    /** Releases the burst: the ring and every spark from the tuft. Called once, when the swell ends. */
    public void burst() {
        ring.start(tuftX, tuftY, RING_SECONDS, radius, RING_WIDTH);
        sparks(OUTER_COUNT, OUTER_REACH_MIN, OUTER_REACH_MAX);
        sparks(INNER_COUNT, INNER_REACH_MIN, INNER_REACH_MAX);
    }

    /** {@code count} sparks spread evenly round the tuft, each flying between {@code reachMin} and {@code reachMax} of the way out. */
    private void sparks(int count, float reachMin, float reachMax) {
        RandomXS128 random = particles.random();
        float gap = MathUtils.PI2 / count, phase = random.nextFloat() * gap;
        for (int n = 0; n < count; n++) {
            float a = phase + gap * n + (random.nextFloat() * TWO - 1f) * gap * ANGLE_JITTER;
            float cos = MathUtils.cos(a), sin = MathUtils.sin(a);
            float s = SPARK_SIZE_MIN + random.nextFloat() * (SPARK_SIZE_MAX - SPARK_SIZE_MIN);
            float reach = reachMin + random.nextFloat() * (reachMax - reachMin);
            // The way to the square's edge (not a circle's) along this direction, short of it by half the largest head.
            float d = reach * (radius - SPARK_SIZE_MAX * HALF) / Math.max(Math.abs(cos), Math.abs(sin));
            float shade = random.nextFloat();
            boolean light = shade < LIGHT_SHARE, mid = shade < MID_SHARE;
            particles.spawn(KimeriaParticleSystem.KIND_SPARK, tuftX, tuftY, tuftX + cos * d, tuftY + sin * d, lifeSeconds + LIFE_MARGIN, s,
                light ? LIGHT_R : mid ? MID_R : DARK_R, light ? LIGHT_G : mid ? MID_G : DARK_G, light ? LIGHT_B : mid ? MID_B : DARK_B);
        }
    }

    public void update(float dt) {
        particles.update(dt);
        ring.update(dt);
    }

    public void clear() {
        particles.clear();
        ring.clear();
    }

    /** Draws the sparks, then the ring over them. Restores the batch's shader, colour and blending. */
    public void render(Batch batch) {
        if (particles.count() > 0) particles.render(batch, Shaders.whiteQuad());
        ring.render(batch);
    }
}
