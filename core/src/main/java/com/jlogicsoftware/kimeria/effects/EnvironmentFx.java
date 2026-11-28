package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.function.BooleanSupplier;

/**
 * The environment effects of one {@code Level} (#239, #285 step 5): the screen shake, a dust puff under the hero on a
 * ground jump and a smaller one when it lands, the grit that falls from the ceiling when a lever moves a wall and the chips
 * and splinters of the traps. Dust and grit are two
 * {@link KimeriaParticleSystem}s with their own seeded random streams. Everything here is cosmetic: no collision,
 * timing or route reads it. Particles advance only in {@link #update}, so pause freezes them. World Y grows downward.
 */
public final class EnvironmentFx {
    public static final int DUST_CAPACITY = 32, GRIT_CAPACITY = 48;
    public static final int DUST_PER_PUFF = 6, GRIT_PER_BURST = 24;
    /** A landing kicks up fewer puffs than a jump: a smaller puff of the same dust. */
    public static final int DUST_PER_LANDING = 4;
    /** Seconds over which one grit burst is emitted. */
    public static final float GRIT_EMIT_SECONDS = .6f;
    private static final long GRIT_SEED_MIX = 0x9E3779B97F4A7C15L;

    private static final float DUST_START_SPREAD_PX = 8, DUST_SIZE_PX = 3;
    private static final float DUST_LIFE_MIN = .25f, DUST_LIFE_MAX = .4f;
    private static final float DUST_RISE_MIN = 10, DUST_RISE_MAX = 22, DUST_DRIFT_SPEED = 25;
    private static final float[] DUST_COLORS = {.357f, .345f, .439f, .604f, .592f, .678f, .847f, .839f, .910f};

    /** A falling stone's landing: dust puffs along its width, flying chips and debris that lingers on the floor, then fades. */
    public static final int STONE_DUST = 10, STONE_CHIPS = 8, STONE_DEBRIS = 6;
    private static final float STONE_DUST_LIFE_MIN = .35f, STONE_DUST_LIFE_MAX = .6f, STONE_DUST_SIZE_PX = 4;
    private static final float STONE_DUST_RISE_MIN = 14, STONE_DUST_RISE_MAX = 30, STONE_DUST_DRIFT_SPEED = 40;
    private static final float STONE_CHIP_LIFE_MIN = .25f, STONE_CHIP_LIFE_MAX = .45f, STONE_CHIP_SPEED = 90;
    private static final float STONE_CHIP_START_RISE_PX = 10, STONE_CHIP_SIZE_MIN = 1.5f, STONE_CHIP_SIZE_MAX = 2.5f;
    private static final float STONE_DEBRIS_LIFE_MIN = 2f, STONE_DEBRIS_LIFE_MAX = 3f;
    private static final float STONE_DEBRIS_SIZE_MIN = 1, STONE_DEBRIS_SIZE_MAX = 2;
    /** Stone greys of the trap sprite: dark, mid, light. */
    private static final float[] CHIP_COLORS = {.357f, .345f, .439f, .478f, .467f, .553f, .604f, .592f, .678f};

    /**
     * A falling branch's landing (#443, D155), lighter than the stone's: dust puffs along its width, bark chips thrown out and
     * down, violet leaves that flutter to the floor and a few specks of debris that lie there, then fade.
     */
    public static final int BRANCH_DUST = 6, BRANCH_CHIPS = 6, BRANCH_LEAVES = 5, BRANCH_DEBRIS = 4;
    private static final float BRANCH_DUST_LIFE_MIN = .3f, BRANCH_DUST_LIFE_MAX = .5f;
    private static final float BRANCH_DUST_RISE_MIN = 10, BRANCH_DUST_RISE_MAX = 22, BRANCH_DUST_DRIFT_SPEED = 30;
    private static final float BRANCH_CHIP_LIFE_MIN = .25f, BRANCH_CHIP_LIFE_MAX = .45f, BRANCH_CHIP_SPEED = 80;
    private static final float BRANCH_CHIP_START_RISE_PX = 9, BRANCH_CHIP_SIZE_MIN = 1, BRANCH_CHIP_SIZE_MAX = 2;
    private static final float BRANCH_LEAF_LIFE_MIN = .5f, BRANCH_LEAF_LIFE_MAX = .9f, BRANCH_LEAF_RISE_MIN = 12, BRANCH_LEAF_RISE_MAX = 22;
    private static final float BRANCH_LEAF_SIZE_MIN = 3, BRANCH_LEAF_SIZE_MAX = 4, BRANCH_LEAF_DRIFT_PX = 8;
    private static final float BRANCH_LEAF_SWAY_MIN = 2, BRANCH_LEAF_SWAY_MAX = 4;
    /** A leaf ends with its centre this fraction of its long side above the floor, so no tilt of it reaches below. */
    private static final float BRANCH_LEAF_REST_FRACTION = .6f;
    private static final float BRANCH_DEBRIS_LIFE_MIN = 1.5f, BRANCH_DEBRIS_LIFE_MAX = 2.5f, BRANCH_DEBRIS_OVERHANG_PX = 4;
    private static final float BRANCH_DEBRIS_SIZE_MIN = 1, BRANCH_DEBRIS_SIZE_MAX = 2;
    /** The falling branch's lighter barks (branch.grid {@code w a8703a}, {@code 5 937049}): its dark bark vanishes on the forest's `2c3f66`. */
    private static final float[] BRANCH_BARK_COLORS = {.659f, .439f, .227f, .576f, .439f, .286f};
    /** Its leaves (branch.grid {@code V a98ae0}, {@code v 3b2a5c}), taken in turn so most read on the dark backdrop. */
    private static final float[] BRANCH_LEAF_COLORS = {.663f, .541f, .878f, .231f, .165f, .361f};

    /** The deadfall's ropes hit the snag (#363, D75): bark chips knocked off it fall a short way and fade. */
    public static final int BARK_CHIPS = 6;
    private static final float BARK_SPREAD_PX = 5, BARK_FALL_MIN = 20, BARK_FALL_MAX = 40, BARK_DRIFT_PX = 10;
    private static final float BARK_LIFE_MIN = .35f, BARK_LIFE_MAX = .6f, BARK_SIZE_MIN = 1, BARK_SIZE_MAX = 2;
    /** Bark browns of the deadfall's bough: dark, mid, light (`4a2e1c`, `8a5a30`, `c08a4c`). */
    private static final float[] BARK_COLORS = {.290f, .180f, .110f, .541f, .353f, .188f, .753f, .541f, .298f};

    /** The thorn root breaks through its mound (#446): a few dark soil specks thrown up and out, then fading. */
    public static final int THORN_SPECKS = 3;
    private static final int THORN_SPECK_SIDES = 2;
    private static final float THORN_SPECK_SPREAD_PX = 4, THORN_SPECK_DRIFT_PX = 14, THORN_SPECK_START_RISE_PX = 8;
    private static final float THORN_SPECK_LIFE_MIN = .25f, THORN_SPECK_LIFE_MAX = .4f, THORN_SPECK_SIZE_MIN = 1.5f, THORN_SPECK_SIZE_MAX = 2, THORN_SPECK_DRIFT_MIN_FRACTION = .5f;
    /** The root's mound colours (`8a5a30` earth, `a89c78` pale tip): lighter than the first dark soil, which sat at zero lightness distance (OKLab L 0.003) from the forest's `2c3f66`; these are 0.14 and 0.32 above it. */
    private static final float[] SOIL_COLORS = {.541f, .353f, .188f, .659f, .612f, .471f};

    /** A falling platform, rotten log or rotten branch gives way (#444, D93): splinters fly from the break and fade. */
    public static final int SPLINTERS = 6;
    /** How far under the platform's resting bottom edge a splinter may drift (the closest shipped floor is 16 px under one). */
    public static final float SPLINTER_REACH_PX = 12;
    private static final int SPLINTER_SIDES = 2;
    private static final float SPLINTER_SPREAD_PX = 5, SPLINTER_START_SPREAD_PX = 2, SPLINTER_DRIFT_MIN = 8, SPLINTER_DRIFT_MAX = 22;
    private static final float SPLINTER_LIFE_MIN = .35f, SPLINTER_LIFE_MAX = .6f, SPLINTER_SIZE_MIN = 1.5f, SPLINTER_SIZE_MAX = 2.5f;
    /** The log's wood and its pale splinter (`8a5a30`, `c08a4c`, `efe6c4`). */
    private static final float[] LOG_SPLINTER_COLORS = {.541f, .353f, .188f, .753f, .541f, .298f, .937f, .902f, .769f};
    /** The rotten branch's dead grey wood, pale top and splinter (`7a6e6c`, `b8ad9c`, `efe6c4`). */
    private static final float[] BRANCH_SPLINTER_COLORS = {.478f, .431f, .424f, .722f, .678f, .612f, .937f, .902f, .769f};

    private static final float GRIT_EXTRA_SPREAD_PX = 24, GRIT_MAX_FALL_PX = 64;
    private static final float GRIT_SPEED_MIN = 70, GRIT_SPEED_MAX = 110;
    private static final float GRIT_LIFE_MIN = .5f, GRIT_LIFE_MAX = .9f;
    private static final float GRIT_SIZE_MIN = 1, GRIT_SIZE_MAX = 2;
    private static final float[] GRIT_COLORS = {.357f, .345f, .439f, .604f, .592f, .678f};
    private static final int COLOR_STRIDE = 3, GREEN = 1, BLUE = 2;

    private final ScreenShake shake;
    private final KimeriaParticleSystem dust, grit;
    private float gritX, gritHalfWidth, gritY, gritElapsed;
    private int gritEmitted = GRIT_PER_BURST;

    /** @param shakeEnabled false while the player's Screen shake option is off */
    public EnvironmentFx(BooleanSupplier shakeEnabled) { this(shakeEnabled, TimeUtils.nanoTime()); }

    /** @param seed the dust stream's seed; the grit stream's is derived from it, so a seed reproduces a run */
    public EnvironmentFx(BooleanSupplier shakeEnabled, long seed) {
        shake = new ScreenShake(shakeEnabled);
        dust = new KimeriaParticleSystem(DUST_CAPACITY, seed);
        grit = new KimeriaParticleSystem(GRIT_CAPACITY, seed ^ GRIT_SEED_MIX);
    }

    public ScreenShake shake() { return shake; }
    public KimeriaParticleSystem dust() { return dust; }
    public KimeriaParticleSystem grit() { return grit; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /** Six smoke puffs rising from the hero's feet ({@code feetY} is the floor line, larger Y is lower). */
    public void dustPuff(float feetX, float feetY) { puff(feetX, feetY, DUST_PER_PUFF); }

    /** {@value #DUST_PER_LANDING} puffs of the same dust where the hero lands (same floor-line rule as {@link #dustPuff}). */
    public void landingPuff(float feetX, float feetY) { puff(feetX, feetY, DUST_PER_LANDING); }

    private void puff(float feetX, float feetY, int count) {
        RandomXS128 random = dust.random();
        for (int n = 0; n < count; n++) {
            float startX = feetX + range(random, -DUST_START_SPREAD_PX, DUST_START_SPREAD_PX);
            float life = range(random, DUST_LIFE_MIN, DUST_LIFE_MAX);
            float away = startX < feetX ? -1 : 1;
            float endX = startX + away * DUST_DRIFT_SPEED * life;
            float endY = feetY - range(random, DUST_RISE_MIN, DUST_RISE_MAX) * life;
            int c = random.nextInt(DUST_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            dust.spawn(KimeriaParticleSystem.KIND_SMOKE, startX, feetY, endX, endY, life, DUST_SIZE_PX,
                DUST_COLORS[c], DUST_COLORS[c + GREEN], DUST_COLORS[c + BLUE]);
        }
    }

    /**
     * A falling stone lands: dust rises along its width, stone chips fly out and drop, and a few specks stay on the floor
     * and fade. {@code floorY} is the floor line (the stone's bottom edge; larger Y is lower), so nothing is drawn under it.
     */
    public void stoneImpact(float centerX, float floorY, float width) {
        float half = width / 2f;
        RandomXS128 random = dust.random();
        for (int n = 0; n < STONE_DUST; n++) {
            float startX = centerX + range(random, -half, half);
            float life = range(random, STONE_DUST_LIFE_MIN, STONE_DUST_LIFE_MAX);
            float away = startX < centerX ? -1 : 1;
            float endX = startX + away * STONE_DUST_DRIFT_SPEED * life;
            float endY = floorY - range(random, STONE_DUST_RISE_MIN, STONE_DUST_RISE_MAX) * life;
            int c = random.nextInt(DUST_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            dust.spawn(KimeriaParticleSystem.KIND_SMOKE, startX, floorY - STONE_DUST_SIZE_PX / 2f, endX, endY, life,
                STONE_DUST_SIZE_PX, DUST_COLORS[c], DUST_COLORS[c + GREEN], DUST_COLORS[c + BLUE]);
        }
        random = grit.random();
        for (int n = 0; n < STONE_CHIPS; n++) {
            float startX = centerX + range(random, -half, half);
            float away = startX < centerX ? -1 : 1;
            float life = range(random, STONE_CHIP_LIFE_MIN, STONE_CHIP_LIFE_MAX);
            float size = range(random, STONE_CHIP_SIZE_MIN, STONE_CHIP_SIZE_MAX);
            int c = random.nextInt(CHIP_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, floorY - STONE_CHIP_START_RISE_PX,
                startX + away * STONE_CHIP_SPEED * life * random.nextFloat(), floorY - size / 2f, life, size,
                CHIP_COLORS[c], CHIP_COLORS[c + GREEN], CHIP_COLORS[c + BLUE]);
        }
        for (int n = 0; n < STONE_DEBRIS; n++) {
            float x = centerX + range(random, -half - 6, half + 6);
            float size = range(random, STONE_DEBRIS_SIZE_MIN, STONE_DEBRIS_SIZE_MAX);
            int c = random.nextInt(CHIP_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, x, floorY - size / 2f, x, floorY - size / 2f,
                range(random, STONE_DEBRIS_LIFE_MIN, STONE_DEBRIS_LIFE_MAX), size,
                CHIP_COLORS[c], CHIP_COLORS[c + GREEN], CHIP_COLORS[c + BLUE]);
        }
    }

    /**
     * A falling branch lands (D155). {@code floorY} is the floor line (the branch's bottom edge; larger Y is lower), so
     * nothing is drawn under it. It shakes nothing.
     */
    public void branchImpact(float centerX, float floorY, float width) {
        float half = width / 2f;
        RandomXS128 random = dust.random();
        for (int n = 0; n < BRANCH_DUST; n++) {
            float startX = centerX + range(random, -half, half);
            float life = range(random, BRANCH_DUST_LIFE_MIN, BRANCH_DUST_LIFE_MAX);
            float away = startX < centerX ? -1 : 1;
            float endX = startX + away * BRANCH_DUST_DRIFT_SPEED * life;
            float endY = floorY - range(random, BRANCH_DUST_RISE_MIN, BRANCH_DUST_RISE_MAX) * life;
            int c = random.nextInt(DUST_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            dust.spawn(KimeriaParticleSystem.KIND_SMOKE, startX, floorY - DUST_SIZE_PX / 2f, endX, endY, life,
                DUST_SIZE_PX, DUST_COLORS[c], DUST_COLORS[c + GREEN], DUST_COLORS[c + BLUE]);
        }
        random = grit.random();
        for (int n = 0; n < BRANCH_CHIPS; n++) {
            float startX = centerX + range(random, -half, half);
            float away = startX < centerX ? -1 : 1;
            float life = range(random, BRANCH_CHIP_LIFE_MIN, BRANCH_CHIP_LIFE_MAX);
            float size = range(random, BRANCH_CHIP_SIZE_MIN, BRANCH_CHIP_SIZE_MAX);
            int c = random.nextInt(BRANCH_BARK_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, floorY - BRANCH_CHIP_START_RISE_PX,
                startX + away * BRANCH_CHIP_SPEED * life * random.nextFloat(), floorY - size / 2f, life, size,
                BRANCH_BARK_COLORS[c], BRANCH_BARK_COLORS[c + GREEN], BRANCH_BARK_COLORS[c + BLUE]);
        }
        for (int n = 0; n < BRANCH_LEAVES; n++) {
            float startX = centerX + range(random, -half, half);
            float away = startX < centerX ? -1 : 1;
            float size = range(random, BRANCH_LEAF_SIZE_MIN, BRANCH_LEAF_SIZE_MAX);
            int c = n % (BRANCH_LEAF_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawnLeaf(startX, floorY - range(random, BRANCH_LEAF_RISE_MIN, BRANCH_LEAF_RISE_MAX),
                startX + away * BRANCH_LEAF_DRIFT_PX * random.nextFloat(), floorY - size * BRANCH_LEAF_REST_FRACTION,
                range(random, BRANCH_LEAF_LIFE_MIN, BRANCH_LEAF_LIFE_MAX), size,
                range(random, BRANCH_LEAF_SWAY_MIN, BRANCH_LEAF_SWAY_MAX), random.nextFloat(),
                BRANCH_LEAF_COLORS[c], BRANCH_LEAF_COLORS[c + GREEN], BRANCH_LEAF_COLORS[c + BLUE]);
        }
        for (int n = 0; n < BRANCH_DEBRIS; n++) {
            float x = centerX + range(random, -half - BRANCH_DEBRIS_OVERHANG_PX, half + BRANCH_DEBRIS_OVERHANG_PX);
            float size = range(random, BRANCH_DEBRIS_SIZE_MIN, BRANCH_DEBRIS_SIZE_MAX);
            int c = random.nextInt(BRANCH_BARK_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, x, floorY - size / 2f, x, floorY - size / 2f,
                range(random, BRANCH_DEBRIS_LIFE_MIN, BRANCH_DEBRIS_LIFE_MAX), size,
                BRANCH_BARK_COLORS[c], BRANCH_BARK_COLORS[c + GREEN], BRANCH_BARK_COLORS[c + BLUE]);
        }
    }

    /** {@value #BARK_CHIPS} bark chips from the snag at ({@code x}, {@code y}), falling (larger Y is lower) and drifting out. */
    public void barkChips(float x, float y) {
        RandomXS128 random = grit.random();
        for (int n = 0; n < BARK_CHIPS; n++) {
            float startX = x + range(random, -BARK_SPREAD_PX, BARK_SPREAD_PX);
            float away = startX < x ? -1 : 1;
            float life = range(random, BARK_LIFE_MIN, BARK_LIFE_MAX);
            int c = random.nextInt(BARK_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, y, startX + away * BARK_DRIFT_PX * random.nextFloat(),
                y + range(random, BARK_FALL_MIN, BARK_FALL_MAX), life, range(random, BARK_SIZE_MIN, BARK_SIZE_MAX),
                BARK_COLORS[c], BARK_COLORS[c + GREEN], BARK_COLORS[c + BLUE]);
        }
    }

    /** {@value #THORN_SPECKS} soil specks from the thorn root's mound at ({@code x}, {@code floorY}); the first goes left, the next right, and so on. */
    public void thornSpecks(float x, float floorY) {
        RandomXS128 random = grit.random();
        for (int n = 0; n < THORN_SPECKS; n++) {
            float startX = x + range(random, -THORN_SPECK_SPREAD_PX, THORN_SPECK_SPREAD_PX);
            float away = n % THORN_SPECK_SIDES == 0 ? -1 : 1;
            float life = range(random, THORN_SPECK_LIFE_MIN, THORN_SPECK_LIFE_MAX);
            float size = range(random, THORN_SPECK_SIZE_MIN, THORN_SPECK_SIZE_MAX);
            int c = random.nextInt(SOIL_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, floorY - THORN_SPECK_START_RISE_PX,
                startX + away * THORN_SPECK_DRIFT_PX * (THORN_SPECK_DRIFT_MIN_FRACTION + random.nextFloat() * (1 - THORN_SPECK_DRIFT_MIN_FRACTION)), floorY - size / 2f, life, size,
                SOIL_COLORS[c], SOIL_COLORS[c + GREEN], SOIL_COLORS[c + BLUE]);
        }
    }

    /**
     * {@value #SPLINTERS} splinters from the break at ({@code x}, {@code crackY}), flying out to alternate sides and falling
     * (larger Y is lower) to between the platform's resting bottom edge {@code bottomY} and {@value #SPLINTER_REACH_PX} px
     * under it; {@code deadWood} gives the rotten branch's greys instead of the log's browns.
     */
    public void splinters(float x, float crackY, float bottomY, boolean deadWood) {
        float[] colors = deadWood ? BRANCH_SPLINTER_COLORS : LOG_SPLINTER_COLORS;
        RandomXS128 random = grit.random();
        for (int n = 0; n < SPLINTERS; n++) {
            float startX = x + range(random, -SPLINTER_SPREAD_PX, SPLINTER_SPREAD_PX);
            float startY = crackY + range(random, -SPLINTER_START_SPREAD_PX, SPLINTER_START_SPREAD_PX);
            float away = n % SPLINTER_SIDES == 0 ? -1 : 1;
            float size = range(random, SPLINTER_SIZE_MIN, SPLINTER_SIZE_MAX);
            int c = random.nextInt(colors.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, startY,
                startX + away * range(random, SPLINTER_DRIFT_MIN, SPLINTER_DRIFT_MAX),
                bottomY + range(random, 0, SPLINTER_REACH_PX - size / 2f),
                range(random, SPLINTER_LIFE_MIN, SPLINTER_LIFE_MAX), size, colors[c], colors[c + GREEN], colors[c + BLUE]);
        }
    }

    /**
     * Starts a grit burst: {@value #GRIT_PER_BURST} particles over {@value #GRIT_EMIT_SECONDS} s from the room's top edge
     * ({@code roomTop}), spread over the wall's width plus 24 px each side. A burst already running is replaced.
     */
    public void ceilingGrit(float wallCenterX, float wallWidth, float roomTop) {
        gritX = wallCenterX;
        gritHalfWidth = wallWidth / 2f + GRIT_EXTRA_SPREAD_PX;
        gritY = roomTop;
        gritElapsed = 0;
        gritEmitted = 0;
        emitGrit();
    }

    /** Emits the grit that is due at {@code gritElapsed}: an even rate, the first particle at once. */
    private void emitGrit() {
        int due = Math.min(GRIT_PER_BURST, (int) (gritElapsed / GRIT_EMIT_SECONDS * GRIT_PER_BURST) + 1);
        RandomXS128 random = grit.random();
        while (gritEmitted < due) {
            gritEmitted++;
            float startX = gritX + range(random, -gritHalfWidth, gritHalfWidth);
            float life = range(random, GRIT_LIFE_MIN, GRIT_LIFE_MAX);
            float fall = Math.min(range(random, GRIT_SPEED_MIN, GRIT_SPEED_MAX) * life, GRIT_MAX_FALL_PX);
            int c = random.nextInt(GRIT_COLORS.length / COLOR_STRIDE) * COLOR_STRIDE;
            grit.spawn(KimeriaParticleSystem.KIND_GRIT, startX, gritY, startX, gritY + fall, life,
                range(random, GRIT_SIZE_MIN, GRIT_SIZE_MAX), GRIT_COLORS[c], GRIT_COLORS[c + GREEN], GRIT_COLORS[c + BLUE]);
        }
    }

    public void update(float dt) {
        shake.update(dt);
        if (gritEmitted < GRIT_PER_BURST) { gritElapsed += dt; emitGrit(); }
        dust.update(dt);
        grit.update(dt);
    }

    /** Draws the live particles; the shared regions are fetched only when something is alive. */
    public void render(Batch batch) {
        if (dust.count() > 0) dust.render(batch, SoftDot.region());
        if (grit.count() > 0) grit.render(batch, Shaders.whiteQuad());
    }

    /** Death, retry and room change. */
    public void clear() {
        shake.clear();
        dust.clear();
        grit.clear();
        gritEmitted = GRIT_PER_BURST;
    }
}
