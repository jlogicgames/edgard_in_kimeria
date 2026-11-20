package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest beetle's effects (#327, D100): the specks of its wind-up, its burst and its death. The end of the body it leaves
 * (D101: crumbled or burnt) is shared by every monster and lives in {@link RemainsFx} (#478). One per {@code Level}, like
 * {@link FireballFx}; two {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds the burst's
 * white flash ({@code KIND_GLOW}, additive) and the grey steam and death poof ({@code KIND_SMOKE}), drawn with
 * {@link SoftDot}; {@link #hard()} holds the wind-up specks and the burst's spray ({@code KIND_FLAME}, opaque squares that
 * shrink out) and the death's shell chips ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>Colours: white, the smoke greys {@code d8d6e8}, {@code 9a97ad}, {@code 5b5870} and, for the death's chips, the live shell's
 * crimson {@code 86101f} and its lit {@code 9c2050} (the danger family, #236); the body's crumble uses the body's muted palette
 * ({@link #SHELL_CHIPS}). No reserved hue. The specks are born on the edge of the 80x32 blast box
 * and drawn in to the body, so the burst's reach is read before it happens; the spray's end points stay inside the box, so
 * what kills is what is seen (D5). Cosmetic: nothing reads it back. World Y grows downward: a rising particle ends at a
 * smaller Y. Particles advance only in {@link #update}, so pause freezes them.
 */
public final class BeetleFx {
    /**
     * Measured peaks (BeetleFxTest: three beetles winding up 0.1 s apart at the assist's 1.2 s warning and bursting while three
     * others die, 40 seeds, 30/60/144 fps): 67 soft, 192 hard, with at least 25 % headroom (108 and 296 while the bodies'
     * crumble and burn lived here; they are {@link RemainsFx} since #328). A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 84, HARD_CAPACITY = 240;
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
    private static final float[] SHELL = {.525f, .063f, .122f}, SHELL_LIT = {.612f, .125f, .314f};
    private static final float[] BODY_EDGE = {.122f, .039f, 0f}, BODY_SHELL = {.153f, .071f, .008f};
    private static final float[] BODY_LIT = {.169f, .086f, .020f}, BODY_GLINT = {.184f, .102f, .031f};
    /** The beetle body's crumble chips (D101, {@link RemainsFx#crumble}): beetle-body.grid's contour, shell, lit shell and glint. */
    public static final float[][] SHELL_CHIPS = {BODY_SHELL, BODY_EDGE, BODY_LIT, BODY_GLINT};
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
            float[] c = (i & 1) == 0 ? SHELL_LIT : SHELL;
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, centreY, x + range(chipRandom, -CHIP_DRIFT, CHIP_DRIFT), floorY - CHIP_FLOOR_INSET,
                CHIP_LIFE, range(chipRandom, CHIP_SIZE_MIN, CHIP_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
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
