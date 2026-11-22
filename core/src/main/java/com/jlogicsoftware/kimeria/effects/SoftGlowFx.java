package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;

/**
 * A soft animated light on an object (#544, part of #396), the replacement for the two sprite glows: the pickup's warm halo
 * ({@code pickup-glow.png}) and the fairy's aqua glow ({@code fairy-glow.png}, D82). It takes the behaviour of patch-gg/pixelfx's
 * orb and halo lights (MIT; see {@code THIRD_PARTY_NOTICES.md}): a pool of light that breathes, dots of light hugging the rim, and
 * a few specks of light drifting round it, all on {@link KimeriaParticleSystem#KIND_ORBIT}. No code of theirs is used.
 *
 * <p>One instance lights one object (an oval of radii {@code rx}, {@code ry} round a centre); each owns two fixed pools,
 * {@link #light()} ({@link SoftDot}) and {@link #specks()} ({@link Shaders#whiteQuad()}), so nothing is allocated after the
 * constructor. {@link #update} spawns while the instance is {@link #place placed}; {@link #render} takes a strength that dims the
 * whole effect at once (the fairy's selected, passive and just-changed marks). Cosmetic and additive: it only brightens, so it can
 * never hide a warning, and the contrast gate measures its peak ({@code tools/check_contrast.py}).
 */
public final class SoftGlowFx {
    /** What one instance looks like: its light colour, how bright the pool and the rim dots are at full strength, the pool's size as a multiple of the larger radius, the rim dot's size in px, the life of a light, how many rim dots and how many specks a second. */
    public record Look(float red, float green, float blue, float poolLight, float rimLight, float poolScale, float rimSize,
                       float lifeSeconds, int rimDots, float speckRate) { }

    /** The pickup's warm light (item radius 8: a pool of about 21 px, the old halo's 24 px, at under half its brightness at the edge). */
    public static final Look PICKUP = new Look(1f, .957f, .667f, .30f, .20f, 2.8f, 10, 1.6f, 5, 4);
    /** The aqua mark on a target of the fairy's (the old nine-slice glow): rim dots hug the target's oval, a pool fills it. */
    public static final Look FAIRY_TARGET = new Look(.30f, .85f, .80f, .30f, .50f, 2f, 11, 1.2f, 8, 5);
    /** The fairy's own light (D76), a breathing pool under her frame, no rim dots, no specks. */
    public static final Look FAIRY_SELF = new Look(.30f, .85f, .80f, .6f, 0f, 7f, 0, 1.6f, 0, 0);

    /**
     * The pool is two lights half a life apart, each ramping in and out over a quarter of its life ({@code ORBIT_EDGE}), so their alphas
     * sum to between {@code POOL_DIM} and {@code POOL_BRIGHT} at every moment ({@code SoftGlowFxTest} pins both against the real particles).
     * {@code tools/check_contrast.py} measures the effect at these two bounds (D76).
     */
    public static final float POOL_DIM = 1f, POOL_BRIGHT = 2f;

    /**
     * Measured live peaks ({@code SoftGlowFxTest}: three looks, 10 seeds, 30 to 240 fps): 11 lights (the fairy target: eight rim dots, two pool lights and one just
     * spawned) and 6 specks; capacity keeps at least a quarter free, because a full pool silently drops its oldest light.
     */
    public static final int LIGHT_CAPACITY = 16, SPECK_CAPACITY = 12;
    private static final long SPECK_SEED_MIX = 0x9E3779B97F4A7C15L;
    /** Each rim dot starts this far past an even spacing, so the ring does not repeat its own pattern. */
    private static final float RIM_TURN_OFFSET = .0618f;
    /** The primed pool lights are a quarter and three quarters into their life, where each is at full alpha. */
    private static final float PRIME_EARLY = .25f, PRIME_LATE = .75f, HALF = .5f;
    private static final float POOL_PHASES = 2f, SPECK_LIFE = 1.2f, SPECK_SWEEP = .5f, SPECK_RHO_FROM = .8f, SPECK_RHO_TO = 1.25f;
    /** A speck is the light's colour mixed this far towards white. */
    private static final float SPECK_WHITE = .6f;

    private final Look look;
    private final KimeriaParticleSystem light, specks;
    private float cx, cy, rx, ry, poolDue, rimDue, speckDue, rimTurn;
    private boolean placed;

    public SoftGlowFx(Look look, long seed) {
        this.look = look;
        light = new KimeriaParticleSystem(LIGHT_CAPACITY, seed);
        specks = new KimeriaParticleSystem(SPECK_CAPACITY, seed ^ SPECK_SEED_MIX);
    }

    public KimeriaParticleSystem light() { return light; }
    public KimeriaParticleSystem specks() { return specks; }
    public boolean placed() { return placed; }
    public float centreX() { return cx; }
    public float centreY() { return cy; }

    /** Lights the oval of radii {@code radiusX}, {@code radiusY} round ({@code centreX}, {@code centreY}); cheap, may be called every frame for an object that moves. */
    public void place(float centreX, float centreY, float radiusX, float radiusY) {
        cx = centreX; cy = centreY; rx = radiusX; ry = radiusY;
        if (!placed) { placed = true; prime(); }
    }

    /**
     * A light that has just appeared shows at once as if it had been running (D82: "full strength from its first frame"; D76: her own light is what
     * keeps her contour above the floor): the two pool lights half a life apart, both in the full middle of their life (sum {@link #POOL_BRIGHT}),
     * and every rim dot at its own age. The next spawns are scheduled where the stream would have put them.
     */
    private void prime() {
        light.spawnOrbit(cx, cy, 0, 0, 0, 0, 0, 0, look.lifeSeconds, look.poolScale * Math.max(rx, ry), look.red * look.poolLight, look.green * look.poolLight, look.blue * look.poolLight);
        light.ageLast(look.lifeSeconds * PRIME_EARLY);
        light.spawnOrbit(cx, cy, 0, 0, 0, 0, 0, 0, look.lifeSeconds, look.poolScale * Math.max(rx, ry), look.red * look.poolLight, look.green * look.poolLight, look.blue * look.poolLight);
        light.ageLast(look.lifeSeconds * PRIME_LATE);
        poolDue = look.lifeSeconds * (PRIME_LATE - PRIME_EARLY) / 2f;
        for (int n = 0; n < look.rimDots; n++) {
            rimTurn = (rimTurn + 1f / look.rimDots + RIM_TURN_OFFSET) % 1f;
            light.spawnOrbit(cx, cy, rx, ry, rimTurn, 0, 1, 1, look.lifeSeconds, look.rimSize, look.red * look.rimLight, look.green * look.rimLight, look.blue * look.rimLight);
            light.ageLast(look.lifeSeconds * (n + HALF) / look.rimDots);
        }
        rimDue = look.lifeSeconds / Math.max(1, look.rimDots) * HALF;
    }

    public void clear() {
        light.clear();
        specks.clear();
        poolDue = rimDue = speckDue = rimTurn = 0;
        placed = false;
    }

    public void update(float dt) {
        if (!placed) return;
        RandomXS128 random = specks.random();
        float poolEvery = look.lifeSeconds / POOL_PHASES, rimEvery = look.lifeSeconds / Math.max(1, look.rimDots);
        for (poolDue += dt; poolDue >= poolEvery; poolDue -= poolEvery)
            light.spawnOrbit(cx, cy, 0, 0, 0, 0, 0, 0, look.lifeSeconds, look.poolScale * Math.max(rx, ry), look.red * look.poolLight, look.green * look.poolLight, look.blue * look.poolLight);
        for (rimDue += look.rimDots > 0 ? dt : 0; rimDue >= rimEvery; rimDue -= rimEvery) {
            rimTurn = (rimTurn + 1f / look.rimDots + RIM_TURN_OFFSET) % 1f;
            light.spawnOrbit(cx, cy, rx, ry, rimTurn, 0, 1, 1, look.lifeSeconds, look.rimSize, look.red * look.rimLight, look.green * look.rimLight, look.blue * look.rimLight);
        }
        for (speckDue += look.speckRate * dt; speckDue >= 1; speckDue--) {
            specks.spawnOrbit(cx, cy, rx, ry, random.nextFloat(), random.nextBoolean() ? SPECK_SWEEP : -SPECK_SWEEP, SPECK_RHO_FROM, SPECK_RHO_TO, SPECK_LIFE, 1f,
                look.red + (1f - look.red) * SPECK_WHITE, look.green + (1f - look.green) * SPECK_WHITE, look.blue + (1f - look.blue) * SPECK_WHITE);
        }
        light.update(dt);
        specks.update(dt);
    }

    /** The light, then the specks over it, both scaled by {@code strength}. */
    public void render(Batch batch, float strength) {
        if (!placed || strength <= 0f) return;
        light.render(batch, SoftDot.region(), strength);
        specks.render(batch, Shaders.whiteQuad(), strength);
    }
}
