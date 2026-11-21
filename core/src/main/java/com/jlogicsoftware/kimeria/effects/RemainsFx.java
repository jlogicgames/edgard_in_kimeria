package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The end of a monster's body (D101), shared by every monster that leaves one (#478): it crumbles under the sword, a stone or
 * a stomp, and burns under a fireball or a torch. One per {@code Level}, like {@link FireballFx}; two
 * {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds the crumble's dust, the burn's warm glow
 * ({@code KIND_GLOW}, additive) and the burnt body's wisp ({@code KIND_SMOKE}), drawn with {@link SoftDot}; {@link #hard()}
 * holds the chips ({@code KIND_GRIT}) and the flames ({@code KIND_FLAME}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>Colours: the chips are the monster's own, passed in by the body: its live palette, as the body keeps it (D117, #571);
 * the dust is a brown and the smoke greys {@code 9a97ad}, {@code 5b5870}; the flames use the
 * fireball's own ramp ({@link FireballFx#ramp}), the one fire palette. No reserved hue. Cosmetic: nothing reads it back. World
 * Y grows downward: a rising particle ends at a smaller Y. Particles advance only in {@link #update}, so pause freezes them.
 */
public final class RemainsFx {
    /**
     * Measured peaks (RemainsFxTest: three bodies crumbled and three burnt together, then three more of each 0.2 s later,
     * 40 seeds, 30/60/144 fps): 45 soft, 174 hard; with at least 25 % headroom. A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 57, HARD_CAPACITY = 218;
    private static final long HARD_SEED_MIX = 0x5DEECE66DL;
    /** Where the glow and the wisp sit: this far above the floor line, about the middle of a lying body. */
    public static final float BODY_CENTRE_ABOVE_FLOOR = 8;

    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] GREY = {.604f, .592f, .678f}, DARK = {.357f, .345f, .439f}, DUST = {.420f, .353f, .227f};

    // The body crumbles (the sword, a stone or a stomp): chips from inside the body fall to the floor, a little dust rises.
    private static final int CRUMBLE_CHIPS = 16, CRUMBLE_DUST = 5;
    private static final float CRUMBLE_HALF_WIDTH = 10, CRUMBLE_LOW = 2, CRUMBLE_HIGH = 12, CRUMBLE_DRIFT = 6;
    private static final float CRUMBLE_LIFE_MIN = .35f, CRUMBLE_LIFE_MAX = .6f, CRUMBLE_SIZE_MIN = 1, CRUMBLE_SIZE_MAX = 2.5f;
    private static final float CHIP_FLOOR_INSET = 1;
    private static final float DUST_LOW = 2, DUST_HIGH = 8, DUST_RISE_MIN = 4, DUST_RISE_MAX = 8, DUST_LIFE_MIN = .3f, DUST_LIFE_MAX = .45f;
    private static final float DUST_SIZE_MIN = 3, DUST_SIZE_MAX = 5;

    // The body burns (a fireball or a torch): flames in the fireball's ramp rise off the body under one warm glow; a dark wisp.
    private static final float BURN_HALF_WIDTH = 10, BURN_LOW = 1, BURN_HIGH = 8, BURN_RISE_MIN = 6, BURN_RISE_MAX = 14;
    private static final float BURN_LIFE_MIN = .18f, BURN_LIFE_MAX = .3f, BURN_SIZE_MIN = 1.5f, BURN_SIZE_MAX = 3;
    private static final float BURN_GLOW_SIZE = 28;
    private static final int WISP = 4;
    private static final float WISP_HALF_WIDTH = 6, WISP_RISE_MIN = 10, WISP_RISE_MAX = 18, WISP_LIFE_MIN = .5f, WISP_LIFE_MAX = .7f;
    private static final float WISP_SIZE_MIN = 3, WISP_SIZE_MAX = 5;

    private final KimeriaParticleSystem soft, hard;

    public RemainsFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** The dust, the glow and the wisp ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Chips and flames ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * A body centred on {@code centreX}, lying on {@code floorY}, crumbles: chips in the monster's own {@code colours} (rgb
     * triples, used in turn; the body's static palette, never built per call) fall to the floor, a little dust rises.
     */
    public void crumble(float centreX, float floorY, float[][] colours) {
        RandomXS128 random = hard.random();
        for (int i = 0; i < CRUMBLE_CHIPS; i++) {
            float x = centreX + range(random, -CRUMBLE_HALF_WIDTH, CRUMBLE_HALF_WIDTH), y = floorY - range(random, CRUMBLE_LOW, CRUMBLE_HIGH);
            float[] c = colours[i % colours.length];
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + range(random, -CRUMBLE_DRIFT, CRUMBLE_DRIFT), floorY - CHIP_FLOOR_INSET,
                range(random, CRUMBLE_LIFE_MIN, CRUMBLE_LIFE_MAX), range(random, CRUMBLE_SIZE_MIN, CRUMBLE_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 dustRandom = soft.random();
        for (int i = 0; i < CRUMBLE_DUST; i++) {
            float x = centreX + range(dustRandom, -CRUMBLE_HALF_WIDTH, CRUMBLE_HALF_WIDTH), y = floorY - range(dustRandom, DUST_LOW, DUST_HIGH);
            float[] c = (i & 1) == 0 ? DUST : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, y - range(dustRandom, DUST_RISE_MIN, DUST_RISE_MAX),
                range(dustRandom, DUST_LIFE_MIN, DUST_LIFE_MAX), range(dustRandom, DUST_SIZE_MIN, DUST_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The start of a burning body: one warm glow over it for {@code seconds}, in the fireball's halo colour. */
    public void burnGlow(float centreX, float floorY, float seconds) {
        float cy = floorY - BODY_CENTRE_ABOVE_FLOOR;
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, centreX, cy, centreX, cy, seconds, BURN_GLOW_SIZE, FireballFx.halo(RED), FireballFx.halo(GREEN), FireballFx.halo(BLUE));
    }

    /** One flame off a burning body, in the fireball's ramp; the body calls it at a steady rate while it burns. */
    public void burnFlame(float centreX, float floorY) {
        RandomXS128 random = hard.random();
        float x = centreX + range(random, -BURN_HALF_WIDTH, BURN_HALF_WIDTH), y = floorY - range(random, BURN_LOW, BURN_HIGH);
        int c = random.nextInt(FireballFx.RAMP_COLOURS) * FireballFx.RAMP_STRIDE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, x, y - range(random, BURN_RISE_MIN, BURN_RISE_MAX),
            range(random, BURN_LIFE_MIN, BURN_LIFE_MAX), range(random, BURN_SIZE_MIN, BURN_SIZE_MAX), FireballFx.ramp(c), FireballFx.ramp(c + GREEN), FireballFx.ramp(c + BLUE));
    }

    /** The end of a burnt body: a dark wisp of smoke rising where it lay. */
    public void burnOut(float centreX, float floorY) {
        RandomXS128 random = soft.random();
        for (int i = 0; i < WISP; i++) {
            float x = centreX + range(random, -WISP_HALF_WIDTH, WISP_HALF_WIDTH), y = floorY - BODY_CENTRE_ABOVE_FLOOR;
            float[] c = (i & 1) == 0 ? DARK : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, y - range(random, WISP_RISE_MIN, WISP_RISE_MAX),
                range(random, WISP_LIFE_MIN, WISP_LIFE_MAX), range(random, WISP_SIZE_MIN, WISP_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    public void update(float dt) {
        soft.update(dt);
        hard.update(dt);
    }

    /** Dust, glow and wisp, then the chips and flames on top; the shared regions are fetched only when something is alive. */
    public void render(Batch batch) {
        if (soft.count() > 0) soft.render(batch, SoftDot.region());
        if (hard.count() > 0) hard.render(batch, Shaders.whiteQuad());
    }

    /** Room change, retry and level restart: nothing carries over. */
    public void clear() {
        soft.clear();
        hard.clear();
    }
}
