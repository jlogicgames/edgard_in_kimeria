package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The particles of the forest boss's special attacks (#625, D157): the blow's wind-up specks and its burst, and the soil and
 * bark of its roots. One per {@code Level}, like {@link BeetleFx}. The blow kills in the beetle's dome (D149), the half ellipse
 * in a {@value BeetleFx#BLAST_WIDTH}x{@value BeetleFx#BLAST_HEIGHT} box on the boss's floor line: its specks are born on that
 * rim and drawn in, and every particle of its burst is born and ends inside it. The burst is a white flash and one hue, the
 * yellow-green {@code b8bc00} (at least 0.09 in OKLab from the spore and firefly limes, and farther from every other reserved
 * hue). Cosmetic: nothing reads it back. World Y grows downward: a rising particle ends at a smaller Y.
 */
public final class BossFx {
    /**
     * Measured peaks (BossFxTest, 40 seeds at 30/60/144 fps, both warning lengths): 2 soft and 123 hard, from the blow's specks
     * and burst or from five roots hopping through the warning and shedding their chips. At least 25 % headroom. A full pool
     * only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 8, HARD_CAPACITY = 160;
    private static final long HARD_SEED_MIX = 0x632BE59BD9B4E019L;
    private static final float HALF = .5f, WHITE = 1f, TWO = 2f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    /** The blow's one hue, {@code b8bc00}. */
    public static final float BLOW_R = .722f, BLOW_G = .737f, BLOW_B = 0f;
    private static final float[] PALE = {.847f, .839f, .910f};
    private static final float RADIUS_X = BeetleFx.BLAST_WIDTH * HALF, RADIUS_Y = BeetleFx.BLAST_HEIGHT;
    /** Clamped particles end this far inside the dome's rim (D5). */
    private static final float SHAPE_INSET = 2;

    private static final float SPECK_SIZE = 2, SPECK_LIFE_MIN = .3f, SPECK_LIFE_MAX = .4f, SPECK_WHITE_CHANCE = .35f;

    private static final float FLASH_SIZE = 56, FLASH_LIFE = .12f, CORE_SIZE = 28, CORE_LIFE = .07f;
    private static final int DOME_DOTS = 32, SPRAY = 30, CHIPS = 10;
    private static final float DOME_LIFE = .16f, DOME_SIZE = 4;
    /** Everything of the burst starts this far over the floor and flies up and out. */
    private static final float LIFT = 2;
    private static final float SPRAY_START_JITTER = 6, SPRAY_REACH_MIN = 12, SPRAY_REACH_MAX = 38, SPRAY_LOW = 3, SPRAY_HIGH = 30;
    private static final float SPRAY_LIFE_MIN = .18f, SPRAY_LIFE_MAX = .32f, SPRAY_SIZE_MIN = 1, SPRAY_SIZE_MAX = 3;
    private static final float CHIP_REACH_MIN = 14, CHIP_REACH_MAX = 36, CHIP_LIFE_MIN = .3f, CHIP_LIFE_MAX = .45f;
    private static final float CHIP_SIZE_MIN = 2, CHIP_SIZE_MAX = 3;

    // The roots' soil and bark: the lance root's colours (its grid's 8a5a30, 4a2e1c and efe6c4), in turn.
    private static final float[][] BARK = {{.541f, .353f, .188f}, {.290f, .180f, .110f}, {.937f, .902f, .769f}};
    private static final float SOIL_LIFE = .35f, SOIL_MIN = 4, SOIL_MAX = 10, SOIL_SPREAD = 5;
    private static final float BARK_LIFE = .5f, BARK_DROP = 20, BARK_SPREAD = 10, BARK_FROM = .5f, BARK_PX = 1;

    private final KimeriaParticleSystem soft, hard;
    private int emitted;

    public BossFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** The flash ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Specks, halo, spray, chips, soil and bark ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /** One wind-up speck: born on the dome's rim over the floor line and drawn in to the boss's middle. */
    public void speck(float centreX, float centreY, float floorY) {
        RandomXS128 random = hard.random();
        float angle = random.nextFloat() * MathUtils.PI;
        boolean white = random.nextFloat() < SPECK_WHITE_CHANCE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX + MathUtils.cos(angle) * RADIUS_X, floorY - MathUtils.sin(angle) * RADIUS_Y,
            centreX, centreY, range(random, SPECK_LIFE_MIN, SPECK_LIFE_MAX), SPECK_SIZE,
            white ? WHITE : PALE[RED], white ? WHITE : PALE[GREEN], white ? WHITE : PALE[BLUE]);
    }

    /** The blow going off: the flash and its core lying on the floor, the halo to the rim, the spray and the flung chips. */
    public void burst(float centreX, float floorY) {
        float from = floorY - LIFT;
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, FLASH_LIFE, FLASH_SIZE, WHITE, WHITE, WHITE);
        soft.spawn(KimeriaParticleSystem.KIND_DOME, centreX, floorY, centreX, floorY, CORE_LIFE, CORE_SIZE, WHITE, WHITE, WHITE);
        for (int i = 0; i < DOME_DOTS; i++) {
            float angle = MathUtils.PI * (i + HALF) / DOME_DOTS;
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, centreX, from, centreX + MathUtils.cos(angle) * RADIUS_X,
                Math.min(from, floorY - MathUtils.sin(angle) * RADIUS_Y), DOME_LIFE, DOME_SIZE, BLOW_R, BLOW_G, BLOW_B);
        }
        RandomXS128 random = hard.random();
        for (int i = 0; i < SPRAY; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float startX = centreX + range(random, -SPRAY_START_JITTER, SPRAY_START_JITTER);
            float reach = range(random, SPRAY_REACH_MIN, SPRAY_REACH_MAX);
            float endY = Math.min(from, floorY - Math.min(range(random, SPRAY_LOW, SPRAY_HIGH), inside(reach)));
            hard.spawn(KimeriaParticleSystem.KIND_FLAME, startX, from, centreX + side * reach, endY,
                range(random, SPRAY_LIFE_MIN, SPRAY_LIFE_MAX), range(random, SPRAY_SIZE_MIN, SPRAY_SIZE_MAX), BLOW_R, BLOW_G, BLOW_B);
        }
        for (int i = 0; i < CHIPS; i++) {
            float side = (i & 1) == 0 ? 1 : -1;
            float reach = range(random, CHIP_REACH_MIN, CHIP_REACH_MAX);
            float endY = Math.min(from, floorY - Math.min(range(random, SPRAY_LOW, SPRAY_HIGH), inside(reach)));
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, centreX, from, centreX + side * reach, endY,
                range(random, CHIP_LIFE_MIN, CHIP_LIFE_MAX), range(random, CHIP_SIZE_MIN, CHIP_SIZE_MAX), BLOW_R, BLOW_G, BLOW_B);
        }
    }

    /** How far above the floor a particle may end, {@code across} px beside the boss's middle. */
    private static float inside(float across) {
        float share = across / RADIUS_X;
        return Math.max(0, RADIUS_Y * (float) Math.sqrt(Math.max(0, 1 - share * share)) - SHAPE_INSET);
    }

    /** One soil speck hopping off a root's lips during the warning. */
    public void soil(float centreX, float lipY) {
        RandomXS128 random = hard.random();
        chip(centreX, lipY, centreX + (random.nextFloat() * TWO - 1f) * SOIL_SPREAD, lipY - range(random, SOIL_MIN, SOIL_MAX), SOIL_LIFE);
    }

    /** Bark chips falling off the upper half of a root's column as it sinks. */
    public void bark(float centreX, float floorY, float height, float width, int count) {
        RandomXS128 random = hard.random();
        for (int i = 0; i < count; i++) {
            float y = floorY - height * (BARK_FROM + random.nextFloat() * (1f - BARK_FROM)), side = random.nextFloat() * TWO - 1f;
            chip(centreX + side * width * HALF, y, centreX + side * BARK_SPREAD, y + BARK_DROP, BARK_LIFE);
        }
    }

    private void chip(float x0, float y0, float x1, float y1, float life) {
        float[] c = BARK[emitted];
        emitted = (emitted + 1) % BARK.length;
        hard.spawn(KimeriaParticleSystem.KIND_GRIT, x0, y0, x1, y1, life, BARK_PX, c[RED], c[GREEN], c[BLUE]);
    }

    public void update(float dt) {
        soft.update(dt);
        hard.update(dt);
    }

    /** The flash, then the specks and chips on top; the shared regions are fetched only when something is alive. */
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
