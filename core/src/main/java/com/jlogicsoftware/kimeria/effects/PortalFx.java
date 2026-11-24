package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The portal that marks a room exit (#552, part of #396): an upright violet oval whose rim sparkles, with spiral arms of motes
 * winding in to its middle, motes pulled in from round about, and a soft light on and inside the rim. It is the behaviour of
 * patch-gg/pixelfx's {@code arcane/portal} (MIT, with Sprite Fusion's black hole under it; see {@code THIRD_PARTY_NOTICES.md}),
 * rebuilt on {@link KimeriaParticleSystem#KIND_ORBIT} with this game's own numbers: no code of theirs is used, and the per-pixel
 * dark well of the original is not drawn, so the rift is light only and never darkens what is behind it.
 *
 * <p>One portal per {@code Level}, placed on the current room's exit by {@link #place}; it owns two fixed pools, {@link #light()}
 * (soft additive dots, {@link SoftDot}) and {@link #motes()} (1 and 2 px additive squares, {@link Shaders#whiteQuad()}), each with
 * its own seeded stream, so nothing is allocated after the constructor. Streams spawn at fixed rates from {@link #update}, so pause
 * freezes it. World Y grows downward; the oval is symmetric, so no sign matters here. Cosmetic: no collision, timing or route
 * reads it, and it never shakes or flashes the screen.
 */
public final class PortalFx {
    /**
     * Measured live peaks ({@code PortalFxTest}: 30, 60 and 144 fps): 184 motes and 27 lights; the capacities keep at least a quarter free, because a full pool
     * silently drops its oldest particle.
     */
    public static final int LIGHT_CAPACITY = 48, MOTE_CAPACITY = 256;
    /** The oval's half height is the exit's half height but at least this much and at most the cap; its half width is {@link #WIDTH_RATIO} of it (pixelfx: 17 by 29). */
    public static final float MIN_HALF_HEIGHT = 20, MAX_HALF_HEIGHT = 28, WIDTH_RATIO = .6f;
    private static final long SEED = 0xA1CA7EL, MOTE_SEED_MIX = 0x9E3779B97F4A7C15L;

    /** pixelfx's arcane ramp ({@code 481c88 6b2cc0 9a48ec c684ff ecc8ff}) as floats, index 0 to 4: deep, mid, bright, pale, near white; the deep step is not used. */
    private static final float[] VIOLET = {.282f, .110f, .533f, .420f, .173f, .753f, .604f, .282f, .925f, .776f, .518f, 1f, .925f, .784f, 1f};
    /** The level-end ramp (#605): deep amber, leaning orange and redder than the honey fungus and the gold of fire, ending near white; same steps as {@link #VIOLET}. */
    private static final float[] AMBER = {.478f, .227f, .063f, .722f, .380f, .110f, .910f, .537f, .180f, 1f, .761f, .467f, 1f, .941f, .839f};
    private static final int MID = 1, BRIGHT = 2, PALE = 3, WHITE = 4, STRIDE = 3, GREEN = 1, BLUE = 2;

    /** Streams: spawns per second, life range, ring of the oval (rho) it runs between, turns it sweeps, and its size. */
    private static final float RIM_RATE = 100, RIM_LIFE_MIN = .6f, RIM_LIFE_MAX = 1f, RIM_SWEEP = .12f, RIM_RHO = 1f;
    private static final float ARM_RATE = 70, ARM_LIFE_MIN = .9f, ARM_LIFE_MAX = 1.3f, ARM_SWEEP = .75f, ARM_RHO_FROM = .95f, ARM_RHO_TO = .12f;
    private static final int ARMS = 3;
    private static final float ARM_JITTER = .03f;
    private static final float INFALL_RATE = 26, INFALL_LIFE_MIN = .5f, INFALL_LIFE_MAX = .8f, INFALL_SWEEP = .3f, INFALL_FROM = 2f, INFALL_TO = 1.05f;
    /** Light: the rift's own soft pool (two staggered, so the sum breathes) and a ring of soft dots hugging the rim. */
    private static final float POOL_LIFE = 2.4f, POOL_EVERY = 1.2f, POOL_REACH = 2.2f, POOL_LIGHT = .34f;
    private static final float HALO_RATE = 14, HALO_LIFE = 1.6f, HALO_RHO = 1f, HALO_SIZE = 13, HALO_LIGHT = .30f, HALO_TURN = .1618f;
    /** The ring of soft dots round the rim also holds a hot spot that runs round with the spin. */
    /** Rim sparks pick one of three colours; one in {@code RIM_BIG_ONE_IN} is {@code RIM_BIG_SIZE} px; one arm mote in {@code ARM_PALE_ONE_IN} is pale, one infall mote in {@code INFALL_WHITE_ONE_IN} white. */
    private static final int RIM_COLOURS = 3, RIM_BIG_ONE_IN = 3, RIM_BIG_SIZE = 2, ARM_PALE_ONE_IN = 3, INFALL_WHITE_ONE_IN = 4;
    private static final float HOT_SWEEP = .5f;
    private static final float HOT_EVERY = 1.2f, HOT_SIZE = 16, HOT_LIGHT = .5f;

    private final KimeriaParticleSystem light = new KimeriaParticleSystem(LIGHT_CAPACITY, SEED);
    private final KimeriaParticleSystem motes = new KimeriaParticleSystem(MOTE_CAPACITY, SEED ^ MOTE_SEED_MIX);
    private float cx, cy, rx, ry, clock;
    private float rimDue, armDue, infallDue, haloDue, poolDue, hotDue;
    private float haloTurn;
    private boolean placed, levelEnd;
    private float[] ramp = VIOLET;

    /** The soft light, for tests and the preview. */
    public KimeriaParticleSystem light() { return light; }
    /** The motes, for tests and the preview. */
    public KimeriaParticleSystem motes() { return motes; }
    public boolean placed() { return placed; }
    /** True when the portal marks the end of the level (amber) and not a room exit (violet). */
    public boolean levelEnd() { return levelEnd; }
    public float centreX() { return cx; }
    public float centreY() { return cy; }
    public float radiusX() { return rx; }
    public float radiusY() { return ry; }

    /** The oval a rectangle of {@code w} by {@code h} gets: half height {@code h / 2} kept between the two limits, half width a fixed share of it. */
    public static float halfHeight(float h) { return Math.max(MIN_HALF_HEIGHT, Math.min(MAX_HALF_HEIGHT, h / 2f)); }

    /** Puts the portal on the exit rectangle (x, y its top left, w by h) and drops what the last room left. */
    public void place(float x, float y, float w, float h) { place(x, y, w, h, false); }

    /** Puts the amber portal that marks where the level ends (#605): the same door as a room exit with another colour; cosmetic. */
    public void placeLevelEnd(float x, float y, float w, float h) { place(x, y, w, h, true); }

    private void place(float x, float y, float w, float h, boolean end) {
        clear();
        levelEnd = end;
        ramp = end ? AMBER : VIOLET;
        cx = x + w / 2f;
        ry = halfHeight(h);
        rx = ry * WIDTH_RATIO;
        cy = y + h / 2f; // centred on the rectangle: a short one (32 px) lets the oval reach past it by a few pixels
        placed = true;
    }

    public void clear() {
        light.clear();
        motes.clear();
        clock = rimDue = armDue = infallDue = haloDue = poolDue = hotDue = haloTurn = 0;
        placed = levelEnd = false;
        ramp = VIOLET;
    }

    public void update(float dt) {
        if (!placed) return;
        clock += dt;
        RandomXS128 m = motes.random(), l = light.random();
        for (rimDue += RIM_RATE * dt; rimDue >= 1; rimDue--) {
            float life = RIM_LIFE_MIN + m.nextFloat() * (RIM_LIFE_MAX - RIM_LIFE_MIN);
            int c = m.nextInt(RIM_COLOURS);
            orbit(m, m.nextFloat(), RIM_SWEEP, RIM_RHO, RIM_RHO, life, c == 0 ? WHITE : c == 1 ? PALE : BRIGHT, m.nextInt(RIM_BIG_ONE_IN) == 0 ? RIM_BIG_SIZE : 1);
        }
        for (armDue += ARM_RATE * dt; armDue >= 1; armDue--) {
            float arm = (m.nextInt(ARMS) + (m.nextFloat() * 2 - 1) * ARM_JITTER * ARMS) / ARMS;
            float life = ARM_LIFE_MIN + m.nextFloat() * (ARM_LIFE_MAX - ARM_LIFE_MIN);
            orbit(m, arm, ARM_SWEEP, ARM_RHO_FROM, ARM_RHO_TO, life, m.nextInt(ARM_PALE_ONE_IN) == 0 ? PALE : BRIGHT, 1);
        }
        for (infallDue += INFALL_RATE * dt; infallDue >= 1; infallDue--) {
            float life = INFALL_LIFE_MIN + m.nextFloat() * (INFALL_LIFE_MAX - INFALL_LIFE_MIN);
            orbit(m, m.nextFloat(), INFALL_SWEEP, INFALL_FROM, INFALL_TO, life, m.nextInt(INFALL_WHITE_ONE_IN) == 0 ? WHITE : PALE, 1);
        }
        for (poolDue += dt; poolDue >= POOL_EVERY; poolDue -= POOL_EVERY)
            glow(POOL_REACH * ry, 0, 0, 0, 0, POOL_LIFE, BRIGHT, POOL_LIGHT);
        for (haloDue += HALO_RATE * dt; haloDue >= 1; haloDue--) {
            haloTurn = (haloTurn + HALO_TURN) % 1f;
            glow(HALO_SIZE, haloTurn, 0, HALO_RHO, HALO_RHO, HALO_LIFE, MID, HALO_LIGHT);
        }
        for (hotDue += dt; hotDue >= HOT_EVERY; hotDue -= HOT_EVERY) glow(HOT_SIZE, l.nextFloat(), HOT_SWEEP, HALO_RHO, HALO_RHO, HALO_LIFE, PALE, HOT_LIGHT);
        light.update(dt);
        motes.update(dt);
    }

    private void orbit(RandomXS128 random, float startTurn, float sweep, float rhoFrom, float rhoTo, float life, int colour, float size) {
        motes.spawnOrbit(cx, cy, rx, ry, startTurn, sweep, rhoFrom, rhoTo, life, size,
            ramp[colour * STRIDE], ramp[colour * STRIDE + GREEN], ramp[colour * STRIDE + BLUE]);
    }

    private void glow(float size, float startTurn, float sweep, float rhoFrom, float rhoTo, float life, int colour, float strength) {
        light.spawnOrbit(cx, cy, rx, ry, startTurn, sweep, rhoFrom, rhoTo, life, size,
            ramp[colour * STRIDE] * strength, ramp[colour * STRIDE + GREEN] * strength, ramp[colour * STRIDE + BLUE] * strength);
    }

    /** Soft light first, then the sparks over it; each pool restores white and normal blending. */
    public void render(Batch batch) {
        if (!placed) return;
        if (light.count() > 0) light.render(batch, SoftDot.region());
        if (motes.count() > 0) motes.render(batch, Shaders.whiteQuad());
    }
}
