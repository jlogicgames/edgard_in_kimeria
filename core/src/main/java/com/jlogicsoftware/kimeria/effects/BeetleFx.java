package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest beetle's effects (#327, D100, D101): the specks of its wind-up, its burst, its death, and the end of the body it
 * leaves (crumbled by the sword or a stone, burnt by a fireball). One per {@code Level}, like
 * {@link FireballFx}; two {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds the burst's
 * white flash ({@code KIND_GLOW}, additive) and the grey steam and death poof ({@code KIND_SMOKE}), drawn with
 * {@link SoftDot}; {@link #hard()} holds the wind-up specks and the burst's spray ({@code KIND_FLAME}, opaque squares that
 * shrink out) and the death's shell chips ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>Colours: white, the smoke greys {@code d8d6e8}, {@code 9a97ad}, {@code 5b5870} and, for the chips, the shell's
 * {@code 34482a} and bronze {@code 9a8458}; the crumble adds two browns, {@code 6b5a3a} and {@code 4a3d28}; the burning body
 * uses the fireball's own ramp ({@link FireballFx#ramp}), the one fire palette. No reserved hue. The specks are born on the edge of the 80x32 blast box
 * and drawn in to the body, so the burst's reach is read before it happens; the spray's end points stay inside the box, so
 * what kills is what is seen (D5). Cosmetic: nothing reads it back. World Y grows downward: a rising particle ends at a
 * smaller Y. Particles advance only in {@link #update}, so pause freezes them.
 */
public final class BeetleFx {
    /**
     * Measured peaks (BeetleFxTest: three beetles winding up 0.1 s apart at the assist's 1.2 s warning and bursting while three
     * others die, then three bodies crumbled and three burnt together, 40 seeds, 30/60/144 fps): 85 soft, 236
     * hard; with at least 25 % headroom. A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 108, HARD_CAPACITY = 296;
    private static final long HARD_SEED_MIX = 0x632BE59BD9B4E019L;

    /** The blast box the specks outline and the spray fills: 80 wide, 32 tall, standing on the floor line (D100). */
    public static final float BLAST_WIDTH = 80, BLAST_HEIGHT = 32;
    private static final float HALF = .5f;
    /** The blast box's two side edges, counted into the perimeter the specks are born on. */
    private static final int SIDES = 2;
    /** Where the specks gather and the burst starts: this far above the floor line, the middle of the body. */
    public static final float BODY_CENTRE_ABOVE_FLOOR = 8;

    private static final float WHITE = 1f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] PALE = {.847f, .839f, .910f}, GREY = {.604f, .592f, .678f}, DARK = {.357f, .345f, .439f};
    private static final float[] SHELL = {.204f, .282f, .165f}, BRONZE = {.604f, .518f, .345f};
    private static final float[] BROWN = {.420f, .353f, .227f}, DARK_BROWN = {.290f, .239f, .157f};
    private static final float[] WHITE_RGB = {WHITE, WHITE, WHITE};

    // Wind-up specks: 2 px squares born on the blast box's edges (the two sides and the top), drawn in to the body. Brighter
    // since the look check of 2026-11-18: a third white, most of the rest pale, few grey.
    private static final float SPECK_SIZE = 2, SPECK_LIFE_MIN = .3f, SPECK_LIFE_MAX = .4f, SPECK_WHITE_CHANCE = .35f, SPECK_PALE_CHANCE = .85f;

    // Burst: a white flash, a flat spray inside the box, grey steam rising.
    private static final float FLASH_SIZE = 40, FLASH_LIFE = .1f;
    private static final int SPRAY = 26, STEAM = 10;
    private static final float SPRAY_START_JITTER = 4, SPRAY_REACH_MIN = 10, SPRAY_REACH_MAX = 38, SPRAY_LOW = 2, SPRAY_HIGH = 30;
    private static final float SPRAY_LIFE_MIN = .16f, SPRAY_LIFE_MAX = .28f, SPRAY_SIZE_MIN = 1, SPRAY_SIZE_MAX = 3, SPRAY_WHITE_CHANCE = .4f;
    private static final float STEAM_HALF_WIDTH = 30, STEAM_LOW = 4, STEAM_HIGH = 24, STEAM_RISE_MIN = 8, STEAM_RISE_MAX = 16;
    private static final float STEAM_LIFE_MIN = .35f, STEAM_LIFE_MAX = .55f, STEAM_SIZE_MIN = 4, STEAM_SIZE_MAX = 7;
    private static final float STEAM_PALE = .4f, STEAM_GREY = .8f;

    // Death (a fireball or a stone): a pale poof in a ring, larger and longer since the look check of 2026-11-18 so it outlasts the
    // fireball's ring (the beetle starts it a moment after the hit), and five shell chips knocked out to the floor.
    private static final int POOF = 12, CHIPS = 5;
    /** One puff in this many is grey, the rest pale: the poof reads lighter than the fireball's ring behind it. */
    private static final int POOF_GREY_EVERY = 3;
    private static final float POOF_DIST_MIN = 10, POOF_DIST_MAX = 16, POOF_LIFE_MIN = .4f, POOF_LIFE_MAX = .55f;
    private static final float POOF_SIZE_MIN = 5, POOF_SIZE_MAX = 8, POOF_ANGLE_JITTER = 15, CIRCLE = 360;
    private static final float CHIP_SPREAD = 6, CHIP_DRIFT = 12, CHIP_LIFE = .45f, CHIP_SIZE_MIN = 1, CHIP_SIZE_MAX = 2, CHIP_FLOOR_INSET = 1;

    // The body crumbles (D101, the sword or a stone): brown and shell chips from inside the body fall to the floor, a little dust.
    private static final int CRUMBLE_CHIPS = 16, CRUMBLE_DUST = 5;
    private static final float[][] CRUMBLE_COLOURS = {BROWN, DARK_BROWN, SHELL, BRONZE};
    private static final float CRUMBLE_HALF_WIDTH = 10, CRUMBLE_LOW = 2, CRUMBLE_HIGH = 12, CRUMBLE_DRIFT = 6;
    private static final float CRUMBLE_LIFE_MIN = .35f, CRUMBLE_LIFE_MAX = .6f, CRUMBLE_SIZE_MIN = 1, CRUMBLE_SIZE_MAX = 2.5f;
    private static final float DUST_LOW = 2, DUST_HIGH = 8, DUST_RISE_MIN = 4, DUST_RISE_MAX = 8, DUST_LIFE_MIN = .3f, DUST_LIFE_MAX = .45f;
    private static final float DUST_SIZE_MIN = 3, DUST_SIZE_MAX = 5;

    // The body burns (D101, a fireball): flames in the fireball's ramp rise off the body, under one warm glow; a dark wisp at the end.
    private static final float BURN_HALF_WIDTH = 10, BURN_LOW = 1, BURN_HIGH = 8, BURN_RISE_MIN = 6, BURN_RISE_MAX = 14;
    private static final float BURN_LIFE_MIN = .18f, BURN_LIFE_MAX = .3f, BURN_SIZE_MIN = 1.5f, BURN_SIZE_MAX = 3;
    private static final float BURN_GLOW_SIZE = 28;
    private static final int WISP = 4;
    private static final float WISP_HALF_WIDTH = 6, WISP_RISE_MIN = 10, WISP_RISE_MAX = 18, WISP_LIFE_MIN = .5f, WISP_LIFE_MAX = .7f;
    private static final float WISP_SIZE_MIN = 3, WISP_SIZE_MAX = 5;

    private final KimeriaParticleSystem soft, hard;

    public BeetleFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** The flash, steam and poof ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Specks, spray and chips ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * One wind-up speck for a beetle whose body is centred on {@code centreX} and stands on {@code floorY}: born on the blast
     * box's edge (a side or the top, chosen by length) and drawn in to the middle of the body.
     */
    public void speck(float centreX, float floorY) {
        RandomXS128 random = hard.random();
        float half = BLAST_WIDTH * HALF, perimeter = BLAST_WIDTH + SIDES * BLAST_HEIGHT, pick = random.nextFloat() * perimeter;
        float x, y;
        if (pick < BLAST_WIDTH) { x = centreX - half + pick; y = floorY - BLAST_HEIGHT; }
        else if (pick < BLAST_WIDTH + BLAST_HEIGHT) { x = centreX - half; y = floorY - (pick - BLAST_WIDTH); }
        else { x = centreX + half; y = floorY - (pick - BLAST_WIDTH - BLAST_HEIGHT); }
        float shade = random.nextFloat();
        float[] c = shade < SPECK_WHITE_CHANCE ? WHITE_RGB : shade < SPECK_PALE_CHANCE ? PALE : GREY;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, centreX, floorY - BODY_CENTRE_ABOVE_FLOOR,
            range(random, SPECK_LIFE_MIN, SPECK_LIFE_MAX), SPECK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /** The burst of a beetle centred on {@code centreX}, standing on {@code floorY}: the flash, the spray and the steam. */
    public void burst(float centreX, float floorY) {
        float cy = floorY - BODY_CENTRE_ABOVE_FLOOR;
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, centreX, cy, centreX, cy, FLASH_LIFE, FLASH_SIZE, WHITE, WHITE, WHITE);
        RandomXS128 random = hard.random();
        for (int i = 0; i < SPRAY; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float startX = centreX + range(random, -SPRAY_START_JITTER, SPRAY_START_JITTER);
            float endX = centreX + side * range(random, SPRAY_REACH_MIN, SPRAY_REACH_MAX);
            float endY = floorY - range(random, SPRAY_LOW, SPRAY_HIGH);
            boolean white = random.nextFloat() < SPRAY_WHITE_CHANCE;
            float r = white ? WHITE : PALE[RED], g = white ? WHITE : PALE[GREEN], b = white ? WHITE : PALE[BLUE];
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, cy, endX, endY, range(random, SPRAY_LIFE_MIN, SPRAY_LIFE_MAX),
                range(random, SPRAY_SIZE_MIN, SPRAY_SIZE_MAX), r, g, b);
        }
        RandomXS128 steamRandom = soft.random();
        for (int i = 0; i < STEAM; i++) {
            float x = centreX + range(steamRandom, -STEAM_HALF_WIDTH, STEAM_HALF_WIDTH);
            float y = floorY - range(steamRandom, STEAM_LOW, STEAM_HIGH);
            float pick = steamRandom.nextFloat();
            float[] c = pick < STEAM_PALE ? PALE : pick < STEAM_GREY ? GREY : DARK;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, y - range(steamRandom, STEAM_RISE_MIN, STEAM_RISE_MAX),
                range(steamRandom, STEAM_LIFE_MIN, STEAM_LIFE_MAX), range(steamRandom, STEAM_SIZE_MIN, STEAM_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The death of a beetle centred on ({@code centreX}, {@code centreY}) standing on {@code floorY}: a grey poof and chips. */
    public void poof(float centreX, float centreY, float floorY) {
        RandomXS128 random = soft.random();
        for (int i = 0; i < POOF; i++) {
            float angle = i * (CIRCLE / (float) POOF) + range(random, -POOF_ANGLE_JITTER, POOF_ANGLE_JITTER);
            float dist = range(random, POOF_DIST_MIN, POOF_DIST_MAX);
            float[] c = i % POOF_GREY_EVERY == 0 ? GREY : PALE;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, centreX, centreY, centreX + MathUtils.cosDeg(angle) * dist,
                centreY + MathUtils.sinDeg(angle) * dist, range(random, POOF_LIFE_MIN, POOF_LIFE_MAX),
                range(random, POOF_SIZE_MIN, POOF_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 chipRandom = hard.random();
        for (int i = 0; i < CHIPS; i++) {
            float x = centreX + range(chipRandom, -CHIP_SPREAD, CHIP_SPREAD);
            float[] c = (i & 1) == 0 ? BRONZE : SHELL;
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, centreY, x + range(chipRandom, -CHIP_DRIFT, CHIP_DRIFT), floorY - CHIP_FLOOR_INSET,
                CHIP_LIFE, range(chipRandom, CHIP_SIZE_MIN, CHIP_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The body of a beetle centred on {@code centreX}, lying on {@code floorY}, crumbles (D101): chips fall, a little dust. */
    public void crumble(float centreX, float floorY) {
        RandomXS128 random = hard.random();
        for (int i = 0; i < CRUMBLE_CHIPS; i++) {
            float x = centreX + range(random, -CRUMBLE_HALF_WIDTH, CRUMBLE_HALF_WIDTH), y = floorY - range(random, CRUMBLE_LOW, CRUMBLE_HIGH);
            float[] c = CRUMBLE_COLOURS[i % CRUMBLE_COLOURS.length];
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + range(random, -CRUMBLE_DRIFT, CRUMBLE_DRIFT), floorY - CHIP_FLOOR_INSET,
                range(random, CRUMBLE_LIFE_MIN, CRUMBLE_LIFE_MAX), range(random, CRUMBLE_SIZE_MIN, CRUMBLE_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 dustRandom = soft.random();
        for (int i = 0; i < CRUMBLE_DUST; i++) {
            float x = centreX + range(dustRandom, -CRUMBLE_HALF_WIDTH, CRUMBLE_HALF_WIDTH), y = floorY - range(dustRandom, DUST_LOW, DUST_HIGH);
            float[] c = (i & 1) == 0 ? BROWN : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, y - range(dustRandom, DUST_RISE_MIN, DUST_RISE_MAX),
                range(dustRandom, DUST_LIFE_MIN, DUST_LIFE_MAX), range(dustRandom, DUST_SIZE_MIN, DUST_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** The start of a burning body (D101): one warm glow over it for {@code seconds}, in the fireball's halo colour. */
    public void burnGlow(float centreX, float floorY, float seconds) {
        float cy = floorY - BODY_CENTRE_ABOVE_FLOOR;
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, centreX, cy, centreX, cy, seconds, BURN_GLOW_SIZE, FireballFx.halo(RED), FireballFx.halo(GREEN), FireballFx.halo(BLUE));
    }

    /** One flame off a burning body (D101), in the fireball's ramp; the body calls it at a steady rate while it burns. */
    public void burnFlame(float centreX, float floorY) {
        RandomXS128 random = hard.random();
        float x = centreX + range(random, -BURN_HALF_WIDTH, BURN_HALF_WIDTH), y = floorY - range(random, BURN_LOW, BURN_HIGH);
        int c = random.nextInt(FireballFx.RAMP_COLOURS) * FireballFx.RAMP_STRIDE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, x, y - range(random, BURN_RISE_MIN, BURN_RISE_MAX),
            range(random, BURN_LIFE_MIN, BURN_LIFE_MAX), range(random, BURN_SIZE_MIN, BURN_SIZE_MAX), FireballFx.ramp(c), FireballFx.ramp(c + GREEN), FireballFx.ramp(c + BLUE));
    }

    /** The end of a burnt body (D101): a dark wisp of smoke rising where it lay. */
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

    /** Flash, steam and poof, then the specks, spray and chips on top; the shared regions are fetched only when something is alive. */
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
