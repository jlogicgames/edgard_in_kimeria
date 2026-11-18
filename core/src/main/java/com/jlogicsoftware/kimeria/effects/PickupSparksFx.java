package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * What the hero sees at the moment he picks up a {@code Collectable} (#402, part of #396): a few 1 to 3 px squares fly out of
 * the item's centre and shrink to nothing, in the warm white and pale yellow of the pickup sparkle
 * ({@code art/objects/pickup/sparkle.grid}), and a small warm ring expands from it (the existing {@code shockwave.frag} through
 * {@link RingPool}, as the fireball's impact ring). It goes with the {@code collect} sound and the pickup cue, and is the same for
 * the coin, the Bat wing jar and the Mushroom. The screen-distorting {@code RippleEffect} no longer plays on a pickup: its lens
 * hid the burst, and the class is kept for the F3 debug hotkey. One per {@code Level}, like {@link FireballFx}: a fixed pool
 * of {@link KimeriaParticleSystem} ({@code KIND_FLAME}: opaque, shrink-out, so nothing fades into the sky), drawn with
 * {@link Shaders#whiteQuad()}; nothing is created per pickup. patch-gg/pixelfx's {@code pickups/collect-burst} was a
 * reference for the idea only, no code or constant was copied. Cosmetic: no collision, timing or route reads it, and
 * nothing shakes or flashes the screen. Particles advance only in {@link #update}, so pause freezes them. World Y
 * grows downward: a rising spark ends at a smaller Y.
 */
public final class PickupSparksFx {
    /**
     * Six bursts at once (a burst lives at most 0.6 s); a hero running through a row of coins (one every ~0.15 s) keeps about three alive. A full pool
     * only drops the oldest spark.
     */
    public static final int SPARKS = 12, CAPACITY = SPARKS * 6;
    /** The sparkle's colours: white {@code ffffff} and warm white {@code fff4aa}. */
    private static final float[] PALETTE = {1f, 1f, 1f, 1f, .957f, .667f};
    private static final int STRIDE = 3, GREEN = 1, BLUE = 2, COLORS = 2;
    private static final float DIST_MIN = 10, DIST_MAX = 26, SIZE_MIN = 1.5f, SIZE_MAX = 3, LIFE_MIN = .4f, LIFE_MAX = .6f;
    /** Angle jitter as a fraction of the gap between two sparks, and a small lift so the burst floats up (world Y grows down). */
    private static final float ANGLE_JITTER = .35f, LIFT = 3;

    /** The ring: it is a faint quick accent behind the sparks: half their shortest life ({@link #LIFE_MIN}), about two thirds of the item's halo, two pixels thick. */
    public static final int RING_CAPACITY = 4;
    private static final float RING_LIFE = .2f, RING_RADIUS = 16, RING_WIDTH = 2;
    /** An intensity ({@code shockwave.frag} fades it and adds it to the sky), the sparkle's warm white, well under the fireball ring's so the sparks lead. */
    private static final float[] RING_COLOR = {1.7f, 1.3f, .4f};

    private final KimeriaParticleSystem sparks;
    private final RingPool rings = new RingPool(RING_CAPACITY, RING_COLOR);

    public PickupSparksFx(long seed) { sparks = new KimeriaParticleSystem(CAPACITY, seed); }

    /** The sparks, for tests. */
    public KimeriaParticleSystem sparks() { return sparks; }

    /** Live rings, for tests. */
    public int ringCount() { return rings.count(); }
    /** The radius the {@code i}-th live ring grows to, oldest first, for tests. */
    public float ringRadius(int i) { return rings.radius(i); }

    /** One burst of {@value #SPARKS} sparks and a ring from ({@code x}, {@code y}), the item's centre. */
    public void burst(float x, float y) {
        rings.start(x, y, RING_LIFE, RING_RADIUS, RING_WIDTH);
        RandomXS128 random = sparks.random();
        float gap = MathUtils.PI2 / SPARKS, phase = random.nextFloat() * gap;
        for (int n = 0; n < SPARKS; n++) {
            float a = phase + gap * n + (random.nextFloat() * 2 - 1) * gap * ANGLE_JITTER;
            float d = DIST_MIN + random.nextFloat() * (DIST_MAX - DIST_MIN);
            int c = random.nextInt(COLORS) * STRIDE;
            sparks.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, x + MathUtils.cos(a) * d, y + MathUtils.sin(a) * d - LIFT,
                LIFE_MIN + random.nextFloat() * (LIFE_MAX - LIFE_MIN), SIZE_MIN + random.nextFloat() * (SIZE_MAX - SIZE_MIN),
                PALETTE[c], PALETTE[c + GREEN], PALETTE[c + BLUE]);
        }
    }

    public void update(float dt) {
        sparks.update(dt);
        rings.update(dt);
    }

    /** The sparks, then the ring; the shared quad is fetched only when something is alive. */
    public void render(Batch batch) {
        if (sparks.count() > 0) sparks.render(batch, Shaders.whiteQuad());
        rings.render(batch);
    }

    /** Room change, retry and level restart: nothing carries over. */
    public void clear() {
        sparks.clear();
        rings.clear();
    }
}
