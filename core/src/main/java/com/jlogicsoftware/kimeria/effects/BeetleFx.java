package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest beetles' effects (#327, D100; the flying beetle's, #330, D145): the specks of a wind-up, the burst and the death. The end of the body it leaves
 * (D101: crumbled or burnt) is shared by every monster and lives in {@link RemainsFx} (#478). One per {@code Level}, like
 * {@link FireballFx}; two {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds the burst's
 * white flash ({@code KIND_GLOW}, additive) and the grey steam and death poof ({@code KIND_SMOKE}), drawn with
 * {@link SoftDot}; {@link #hard()} holds the wind-up specks and the burst's spray ({@code KIND_FLAME}, opaque squares that
 * shrink out) and the death's shell chips ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>Colours: white, the smoke greys {@code d8d6e8}, {@code 9a97ad}, {@code 5b5870} and, for the death's chips, the live shell's
 * crimson {@code 86101f} and its lit {@code 9c2050} (the danger family, #236); the body's crumble uses the body's live palette (D117)
 * ({@link #SHELL_CHIPS}). No reserved hue. The ground beetle's wind-up specks are born on the rim
 * of its blast's dome (D149) and drawn in to the body, so the burst's reach is read before it happens; every particle of its
 * burst is born and ends inside the dome, so what kills is what is seen (D5). Cosmetic: nothing reads it back. World Y
 * grows downward: a rising particle ends at a smaller Y. Particles advance only in {@link #update}, so pause freezes them.
 *
 * <p>The flying beetle shares the pools (D147): {@link #gather} draws specks in to it from a ring round its body,
 * {@link #lineSpeck} runs down its dive line, and {@link #burstAt} is a half dome off the surface that cuts its blast, every
 * particle flying up and away from it, or an oval one in the air.
 */
public final class BeetleFx {
    /**
     * Measured peaks (BeetleFxTest: three beetles winding up 0.1 s apart at the assist's 1.2 s warning and bursting while three
     * others die, 40 seeds, 30/60/144 fps): 69 soft, 318 hard since the burst of #559 (the halo, embers and chips; 67 and 192 before); three
     * flying beetles, with their dive lines (#330, D145): 69 soft and 347 hard. At least
     * 25 % headroom (108 and 296 while the bodies' crumble and burn lived here; they are {@link RemainsFx} since #328). A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 92, HARD_CAPACITY = 440;
    private static final long HARD_SEED_MIX = 0x632BE59BD9B4E019L;

    /** The bounds of a blast on a surface: the half ellipse inscribed in them is what kills, 80 wide and 32 tall (D148, D149). */
    public static final float BLAST_WIDTH = 80, BLAST_HEIGHT = 32;
    private static final float HALF = .5f;
    /** Where the specks gather and the burst starts: this far above the floor line, the middle of the body. */
    public static final float BODY_CENTRE_ABOVE_FLOOR = 8;
    /** The flying beetle's blast in the air (D145, D148): this far above and below the body's middle, cut at the surface under it. */
    public static final float BLAST_AIR_HALF_HEIGHT = 24;
    /** A surface this far (a rounding margin) inside the air blast's lower edge cuts the burst: it is then the half dome off it. */
    private static final float CUT_MARGIN = .5f;
    /** The flying beetle's wind-up specks are born this far from its body's middle, just outside its wings (D147). */
    public static final float GATHER_RADIUS_MIN = 28, GATHER_RADIUS_MAX = 32;
    /** Clamped particles end this far inside their blast's edge, so what kills is what is seen (D5, D148, D149). */
    private static final float SHAPE_INSET = 2;
    // The half dome off a surface (D147): everything starts this far above it and flies up; chips are thrown this high.
    private static final float DOME_LIFT = 2, DOME_CHIP_LOW = 8, DOME_CHIP_HIGH = 28, DOME_STEAM_LOW = 8;
    // The dive line's specks (D145): born on the first part of the line, flying on to its end.
    private static final float LINE_START_SHARE = .6f, LINE_LIFE_MIN = .22f, LINE_LIFE_MAX = .3f, LINE_JITTER = 1;
    /** An oval burst keeps its particles this far inside the blast's upper and lower edges. */
    private static final float AIR_INSET = 2;

    private static final float WHITE = 1f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] PALE = {.847f, .839f, .910f}, GREY = {.604f, .592f, .678f}, DARK = {.357f, .345f, .439f};
    private static final float[] SHELL = {.525f, .063f, .122f}, SHELL_LIT = {.612f, .125f, .314f};
    private static final float[] BODY_EDGE = {.043f, .031f, .078f}, BODY_SHELL = {.525f, .063f, .122f};
    private static final float[] BODY_LIT = {.612f, .125f, .314f}, BODY_GLINT = {.722f, .353f, .502f};
    /** The beetle body's crumble chips (D101, {@link RemainsFx#crumble}): beetle-body.grid's contour, shell, lit shell and glint. */
    public static final float[][] SHELL_CHIPS = {BODY_SHELL, BODY_EDGE, BODY_LIT, BODY_GLINT};
    private static final float[] WHITE_RGB = {WHITE, WHITE, WHITE};

    // Wind-up specks: 2 px squares, the ground beetle's born on the rim of its dome (D149), drawn in to the body. Brighter
    // since the look check of 2026-11-18: a third white, most of the rest pale, few grey.
    private static final float SPECK_SIZE = 2, SPECK_LIFE_MIN = .3f, SPECK_LIFE_MAX = .4f, SPECK_WHITE_CHANCE = .35f, SPECK_PALE_CHANCE = .85f;

    // Burst (#559): a white flash with a small hot core, a halo, a flat spray and embers, shell chips, grey steam rising.
    private static final float FLASH_SIZE = 44, FLASH_LIFE = .12f, CORE_SIZE = 20, CORE_LIFE = .07f;
    private static final int SPRAY = 26, STEAM = 10;
    /**
     * The halo: dots fly from the body to the rim of the dome, the half-ellipse inscribed in the blast's bounds (rx half their
     * width, ry their height, centred on the floor line), so it draws the edge of what kills and never leaves it (D5, D149). A
     * ring would not fit those bounds. All dots spawn at the instant: no emitter.
     */
    private static final int DOME_DOTS = 32;
    private static final float DOME_LIFE = .16f, DOME_SIZE = 4, DOME_RX = BLAST_WIDTH * HALF, DOME_RY = BLAST_HEIGHT;
    /** Embers in the eyes' colour (two in three, the third white), thrown wide and high inside the blast's shape. */
    private static final int SPARKS = 14, SPARK_EYE_EVERY = 3, CHIPS_BURST = 8;
    private static final float SPARK_LIFE_MIN = .22f, SPARK_LIFE_MAX = .34f, SPARK_SIZE = 2, SPARK_START_JITTER = 3;
    private static final float SPARK_REACH_MIN = 14, SPARK_REACH_MAX = 39, SPARK_LOW = 10, SPARK_HIGH = 31;
    private static final float[] EYE = {1f, .42f, .341f};
    /** Its own shell flung out and down to the floor: the burst's debris, gone by the end (D101: a burst leaves no body). */
    private static final float BURST_CHIP_REACH_MIN = 12, BURST_CHIP_REACH_MAX = 36, BURST_CHIP_START_JITTER = 4;
    private static final float BURST_CHIP_LIFE_MIN = .30f, BURST_CHIP_LIFE_MAX = .42f, BURST_CHIP_SIZE_MIN = 2, BURST_CHIP_SIZE_MAX = 3;
    private static final float SPRAY_START_JITTER = 4, SPRAY_REACH_MIN = 10, SPRAY_REACH_MAX = 38, SPRAY_LOW = 2, SPRAY_HIGH = 30;
    private static final float SPRAY_LIFE_MIN = .16f, SPRAY_LIFE_MAX = .28f, SPRAY_SIZE_MIN = 1, SPRAY_SIZE_MAX = 3, SPRAY_WHITE_CHANCE = .4f;
    private static final float STEAM_HALF_WIDTH = 30, STEAM_LOW = 4, STEAM_HIGH = 24, STEAM_RISE_MIN = 8, STEAM_RISE_MAX = 16;
    private static final float STEAM_LIFE_MIN = .40f, STEAM_LIFE_MAX = .60f, STEAM_SIZE_MIN = 4, STEAM_SIZE_MAX = 7;
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
     * One wind-up speck for a beetle whose body is centred on {@code centreX} and stands on {@code floorY}: born on the rim of
     * its blast's dome (D149), the curve over the floor line, and drawn in to the middle of the body.
     */
    public void speck(float centreX, float floorY) {
        RandomXS128 random = hard.random();
        float angle = random.nextFloat() * MathUtils.PI;
        float x = centreX + MathUtils.cos(angle) * DOME_RX, y = floorY - MathUtils.sin(angle) * DOME_RY;
        float shade = random.nextFloat();
        float[] c = shade < SPECK_WHITE_CHANCE ? WHITE_RGB : shade < SPECK_PALE_CHANCE ? PALE : GREY;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, centreX, floorY - BODY_CENTRE_ABOVE_FLOOR,
            range(random, SPECK_LIFE_MIN, SPECK_LIFE_MAX), SPECK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /**
     * One wind-up speck of the flying beetle (D147), its body's middle at ({@code centreX}, {@code centreY}): born on a ring
     * round it and drawn in to that middle.
     */
    public void gather(float centreX, float centreY) {
        RandomXS128 random = hard.random();
        float angle = random.nextFloat() * MathUtils.PI2, radius = range(random, GATHER_RADIUS_MIN, GATHER_RADIUS_MAX);
        float shade = random.nextFloat();
        float[] c = shade < SPECK_WHITE_CHANCE ? WHITE_RGB : shade < SPECK_PALE_CHANCE ? PALE : GREY;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX + MathUtils.cos(angle) * radius, centreY + MathUtils.sin(angle) * radius,
            centreX, centreY, range(random, SPECK_LIFE_MIN, SPECK_LIFE_MAX), SPECK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /**
     * The ground beetle's burst, its body centred on {@code centreX} and standing on {@code floorY}: the flash and its core as
     * half glows lying on the floor, the halo, spray, embers, shell chips and steam, all inside the dome that kills (D149).
     */
    public void burst(float centreX, float floorY) {
        float cy = floorY - BODY_CENTRE_ABOVE_FLOOR;
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, FLASH_LIFE, FLASH_SIZE, WHITE, WHITE, WHITE);
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, CORE_LIFE, CORE_SIZE, WHITE, WHITE, WHITE);
        RandomXS128 random = hard.random();
        for (int i = 0; i < DOME_DOTS; i++) {
            float angle = MathUtils.PI * (i + HALF) / DOME_DOTS;
            float[] c = (i & 1) == 0 ? WHITE_RGB : PALE;
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX, cy, centreX + MathUtils.cos(angle) * DOME_RX,
                floorY - MathUtils.sin(angle) * DOME_RY, DOME_LIFE, DOME_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < SPRAY; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float startX = centreX + range(random, -SPRAY_START_JITTER, SPRAY_START_JITTER);
            float reach = range(random, SPRAY_REACH_MIN, SPRAY_REACH_MAX), endX = centreX + side * reach;
            float endY = floorY - Math.min(range(random, SPRAY_LOW, SPRAY_HIGH), inside(reach, DOME_RY));
            boolean white = random.nextFloat() < SPRAY_WHITE_CHANCE;
            float r = white ? WHITE : PALE[RED], g = white ? WHITE : PALE[GREEN], b = white ? WHITE : PALE[BLUE];
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, cy, endX, endY, range(random, SPRAY_LIFE_MIN, SPRAY_LIFE_MAX),
                range(random, SPRAY_SIZE_MIN, SPRAY_SIZE_MAX), r, g, b);
        }
        for (int i = 0; i < SPARKS; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = i % SPARK_EYE_EVERY != 0 ? EYE : WHITE_RGB;
            float startX = centreX + range(random, -SPARK_START_JITTER, SPARK_START_JITTER);
            float reach = range(random, SPARK_REACH_MIN, SPARK_REACH_MAX);
            float endY = floorY - Math.min(range(random, SPARK_LOW, SPARK_HIGH), inside(reach, DOME_RY));
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, cy, centreX + side * reach, endY,
                range(random, SPARK_LIFE_MIN, SPARK_LIFE_MAX), SPARK_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < CHIPS_BURST; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = (i & 1) == 0 ? SHELL : SHELL_LIT;
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, centreX + range(random, -BURST_CHIP_START_JITTER, BURST_CHIP_START_JITTER), cy,
                centreX + side * range(random, BURST_CHIP_REACH_MIN, BURST_CHIP_REACH_MAX), floorY - BURST_CHIP_SIZE_MAX / 2f,
                range(random, BURST_CHIP_LIFE_MIN, BURST_CHIP_LIFE_MAX), range(random, BURST_CHIP_SIZE_MIN, BURST_CHIP_SIZE_MAX),
                c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 steamRandom = soft.random();
        for (int i = 0; i < STEAM; i++) {
            float across = range(steamRandom, -STEAM_HALF_WIDTH, STEAM_HALF_WIDTH), x = centreX + across;
            float top = inside(across, DOME_RY), up = Math.min(range(steamRandom, STEAM_LOW, STEAM_HIGH), top);
            float pick = steamRandom.nextFloat();
            float[] c = pick < STEAM_PALE ? PALE : pick < STEAM_GREY ? GREY : DARK;
            float risen = Math.min(up + range(steamRandom, STEAM_RISE_MIN, STEAM_RISE_MAX), top);
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, floorY - up, x, floorY - risen,
                range(steamRandom, STEAM_LIFE_MIN, STEAM_LIFE_MAX), range(steamRandom, STEAM_SIZE_MIN, STEAM_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** One speck of a dive line (D145): born on its first part, from ({@code fromX}, {@code fromY}), flying on to its end. */
    public void lineSpeck(float fromX, float fromY, float toX, float toY) {
        RandomXS128 random = hard.random();
        float share = random.nextFloat() * LINE_START_SHARE;
        float x = fromX + (toX - fromX) * share + range(random, -LINE_JITTER, LINE_JITTER);
        float y = fromY + (toY - fromY) * share + range(random, -LINE_JITTER, LINE_JITTER);
        float[] c = random.nextFloat() < SPECK_WHITE_CHANCE ? WHITE_RGB : PALE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, toX, toY, range(random, LINE_LIFE_MIN, LINE_LIFE_MAX), SPECK_SIZE,
            c[RED], c[GREEN], c[BLUE]);
    }

    /**
     * The flying beetle's burst (D145, D147), its body's middle at ({@code centreX}, {@code centreY}) over a surface or the
     * blast's lower edge at {@code bottom}. Where a surface cuts the blast (the end of a dive), a half dome off that surface;
     * in the air, the flash, a whole oval halo (rx half {@link #BLAST_WIDTH}, ry {@link #BLAST_AIR_HALF_HEIGHT}), the spray,
     * embers, shell chips and steam, kept above that lower edge, so what kills is what is seen (D5).
     */
    public void burstAt(float centreX, float centreY, float bottom) {
        if (onSurface(centreY, bottom)) { domeBurst(centreX, bottom); return; }
        float high = centreY - BLAST_AIR_HALF_HEIGHT + AIR_INSET, low = bottom - AIR_INSET;
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, centreX, centreY, centreX, centreY, FLASH_LIFE, FLASH_SIZE, WHITE, WHITE, WHITE);
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, centreX, centreY, centreX, centreY, CORE_LIFE, CORE_SIZE, WHITE, WHITE, WHITE);
        RandomXS128 random = hard.random();
        for (int i = 0; i < DOME_DOTS; i++) {
            float angle = MathUtils.PI2 * (i + HALF) / DOME_DOTS;
            float[] c = (i & 1) == 0 ? WHITE_RGB : PALE;
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX, centreY, centreX + MathUtils.cos(angle) * DOME_RX,
                Math.min(bottom, centreY - MathUtils.sin(angle) * BLAST_AIR_HALF_HEIGHT), DOME_LIFE, DOME_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < SPRAY; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float startX = centreX + range(random, -SPRAY_START_JITTER, SPRAY_START_JITTER);
            float reach = range(random, SPRAY_REACH_MIN, SPRAY_REACH_MAX), endX = centreX + side * reach;
            boolean white = random.nextFloat() < SPRAY_WHITE_CHANCE;
            float r = white ? WHITE : PALE[RED], g = white ? WHITE : PALE[GREEN], b = white ? WHITE : PALE[BLUE];
            float half = inside(reach, BLAST_AIR_HALF_HEIGHT);
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, centreY, endX,
                MathUtils.clamp(range(random, high, low), centreY - half, centreY + half),
                range(random, SPRAY_LIFE_MIN, SPRAY_LIFE_MAX), range(random, SPRAY_SIZE_MIN, SPRAY_SIZE_MAX), r, g, b);
        }
        for (int i = 0; i < SPARKS; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = i % SPARK_EYE_EVERY != 0 ? EYE : WHITE_RGB;
            float startX = centreX + range(random, -SPARK_START_JITTER, SPARK_START_JITTER);
            float reach = range(random, SPARK_REACH_MIN, SPARK_REACH_MAX), half = inside(reach, BLAST_AIR_HALF_HEIGHT);
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, centreY, centreX + side * reach,
                MathUtils.clamp(range(random, high, low), centreY - half, centreY + half),
                range(random, SPARK_LIFE_MIN, SPARK_LIFE_MAX), SPARK_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < CHIPS_BURST; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = (i & 1) == 0 ? SHELL : SHELL_LIT;
            float startX = centreX + range(random, -BURST_CHIP_START_JITTER, BURST_CHIP_START_JITTER);
            float reach = range(random, BURST_CHIP_REACH_MIN, BURST_CHIP_REACH_MAX);
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, startX, centreY, centreX + side * reach,
                Math.min(bottom - BURST_CHIP_SIZE_MAX / 2f, centreY + inside(reach, BLAST_AIR_HALF_HEIGHT)),
                range(random, BURST_CHIP_LIFE_MIN, BURST_CHIP_LIFE_MAX), range(random, BURST_CHIP_SIZE_MIN, BURST_CHIP_SIZE_MAX),
                c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 steamRandom = soft.random();
        for (int i = 0; i < STEAM; i++) {
            float x = centreX + range(steamRandom, -STEAM_HALF_WIDTH, STEAM_HALF_WIDTH);
            float y = range(steamRandom, centreY, low);
            float pick = steamRandom.nextFloat();
            float[] c = pick < STEAM_PALE ? PALE : pick < STEAM_GREY ? GREY : DARK;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, Math.max(high, y - range(steamRandom, STEAM_RISE_MIN, STEAM_RISE_MAX)),
                range(steamRandom, STEAM_LIFE_MIN, STEAM_LIFE_MAX), range(steamRandom, STEAM_SIZE_MIN, STEAM_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /**
     * A burst on the surface at {@code floorY} (D147): the flash and its core as half glows lying on it, the half-ellipse halo,
     * the spray, embers, shell chips and steam. Every particle starts just above the surface and flies up and away from it:
     * none ends lower than it began, and none is drawn under the surface.
     */
    private void domeBurst(float centreX, float floorY) {
        float from = floorY - DOME_LIFT;
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, FLASH_LIFE, FLASH_SIZE, WHITE, WHITE, WHITE);
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, CORE_LIFE, CORE_SIZE, WHITE, WHITE, WHITE);
        RandomXS128 random = hard.random();
        for (int i = 0; i < DOME_DOTS; i++) {
            float angle = MathUtils.PI * (i + HALF) / DOME_DOTS;
            float[] c = (i & 1) == 0 ? WHITE_RGB : PALE;
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX, from, centreX + MathUtils.cos(angle) * DOME_RX,
                Math.min(from, floorY - MathUtils.sin(angle) * DOME_RY), DOME_LIFE, DOME_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < SPRAY; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float startX = centreX + range(random, -SPRAY_START_JITTER, SPRAY_START_JITTER);
            float reach = range(random, SPRAY_REACH_MIN, SPRAY_REACH_MAX), endX = centreX + side * reach;
            float endY = Math.min(from, floorY - Math.min(range(random, SPRAY_LOW, SPRAY_HIGH), inside(reach, DOME_RY)));
            boolean white = random.nextFloat() < SPRAY_WHITE_CHANCE;
            float r = white ? WHITE : PALE[RED], g = white ? WHITE : PALE[GREEN], b = white ? WHITE : PALE[BLUE];
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, from, endX, endY, range(random, SPRAY_LIFE_MIN, SPRAY_LIFE_MAX),
                range(random, SPRAY_SIZE_MIN, SPRAY_SIZE_MAX), r, g, b);
        }
        for (int i = 0; i < SPARKS; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = i % SPARK_EYE_EVERY != 0 ? EYE : WHITE_RGB;
            float startX = centreX + range(random, -SPARK_START_JITTER, SPARK_START_JITTER);
            float reach = range(random, SPARK_REACH_MIN, SPARK_REACH_MAX);
            float endY = Math.min(from, floorY - Math.min(range(random, SPARK_LOW, SPARK_HIGH), inside(reach, DOME_RY)));
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, from, centreX + side * reach, endY,
                range(random, SPARK_LIFE_MIN, SPARK_LIFE_MAX), SPARK_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < CHIPS_BURST; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float[] c = (i & 1) == 0 ? SHELL : SHELL_LIT;
            float startX = centreX + range(random, -BURST_CHIP_START_JITTER, BURST_CHIP_START_JITTER);
            float reach = range(random, BURST_CHIP_REACH_MIN, BURST_CHIP_REACH_MAX);
            float endY = Math.min(from, floorY - Math.min(range(random, DOME_CHIP_LOW, DOME_CHIP_HIGH), inside(reach, DOME_RY)));
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, startX, from, centreX + side * reach, endY,
                range(random, BURST_CHIP_LIFE_MIN, BURST_CHIP_LIFE_MAX), range(random, BURST_CHIP_SIZE_MIN, BURST_CHIP_SIZE_MAX),
                c[RED], c[GREEN], c[BLUE]);
        }
        RandomXS128 steamRandom = soft.random();
        for (int i = 0; i < STEAM; i++) {
            float x = centreX + range(steamRandom, -STEAM_HALF_WIDTH, STEAM_HALF_WIDTH);
            float y = floorY - range(steamRandom, DOME_STEAM_LOW, STEAM_HIGH);
            float pick = steamRandom.nextFloat();
            float[] c = pick < STEAM_PALE ? PALE : pick < STEAM_GREY ? GREY : DARK;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x, y - range(steamRandom, STEAM_RISE_MIN, STEAM_RISE_MAX),
                range(steamRandom, STEAM_LIFE_MIN, STEAM_LIFE_MAX), range(steamRandom, STEAM_SIZE_MIN, STEAM_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
    }

    /**
     * Whether a surface at {@code bottom} cuts the burst of a flying beetle whose middle is at {@code centreY}: it is then a
     * half dome off that surface (D147), and so is what it kills (D148); else it is a whole oval in the air.
     */
    public static boolean onSurface(float centreY, float bottom) { return bottom - centreY < BLAST_AIR_HALF_HEIGHT - CUT_MARGIN; }

    /** How far above or below its origin a particle may be, {@code across} px beside it, in a shape {@code radius} high. */
    private static float inside(float across, float radius) {
        float share = across / DOME_RX;
        return Math.max(0, radius * (float) Math.sqrt(Math.max(0, 1 - share * share)) - SHAPE_INSET);
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
