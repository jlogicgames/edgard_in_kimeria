package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The forest snail's effects (#329, D106): the specks its wind-up draws in along its firing line, the gob swelling at its
 * mouth, the drips the flying gob sheds, the gob's splat (hit or miss) and its steam when fire takes it, and the snail's death.
 * The gob itself is an authored sprite drawn by the snail; the end of the body it leaves (D101) is {@link RemainsFx}. One per
 * {@code Level}, like {@link SpiderFx}; two {@link KimeriaParticleSystem}s with their own seeded streams. {@link #soft()} holds
 * the swelling glow ({@code KIND_GLOW}, additive), the steam and the death poof ({@code KIND_SMOKE}), drawn with {@link SoftDot};
 * {@link #hard()} holds the specks ({@code KIND_FLAME}, opaque squares that shrink out), the drips, droplets, smear and shell
 * chips ({@code KIND_GRIT}), drawn with {@link Shaders#whiteQuad()}.
 *
 * <p>The specks are born along the firing line, at the gob's height, and run into the mouth, so the reach and the height of
 * the shot are read before it is fired (D7, D106), as the spider's glints show its landing spot. Colours: white, the gob's pale
 * rose and crimson ({@code f4d4de}, {@code c87896}, {@code 86101f}), the smoke's pale and grey, the live shell and foot for the
 * death's chips (the danger family, #236) and the empty shell's live palette for the body's crumble (D117) ({@link #SNAIL_CHIPS}). No reserved hue. Cosmetic: nothing reads it back. World Y grows downward: a falling particle ends
 * at a larger Y. Particles advance only in {@link #update}, so pause freezes them.
 */
public final class SnailFx {
    /**
     * Measured peaks (SnailFxTest: three snails winding up 0.1 s apart at the assist's 1.2 s warning, their gobs flying and
     * splatting, a fourth gob steamed and three others dying, 40 seeds, 30/60/144 fps): 51 soft, 84 hard; with at least 25 %
     * headroom. A full pool only drops the oldest.
     */
    public static final int SOFT_CAPACITY = 64, HARD_CAPACITY = 112;
    private static final long HARD_SEED_MIX = 0x9E3779B97F4A7C15L;

    private static final float WHITE = 1f;
    private static final int RED = 0, GREEN = 1, BLUE = 2;
    private static final float[] WHITE_RGB = {WHITE, WHITE, WHITE};
    private static final float[] GOB_PALE = {.957f, .831f, .871f}, GOB_MID = {.784f, .471f, .588f}, GOB_DARK = {.525f, .063f, .122f};
    private static final float[] PALE = {.847f, .839f, .910f}, GREY = {.604f, .592f, .678f};
    private static final float[] SHELL = {.525f, .063f, .122f}, SHELL_LIGHT = {.612f, .125f, .314f}, ROSE = {.784f, .471f, .588f};
    private static final float[] FOOT = {.604f, .376f, .439f};
    /** The death's shell chips: snail.grid's crimson whorls, rose highlight and foot. */
    private static final float[][] DEATH_CHIPS = {SHELL, ROSE, SHELL_LIGHT, FOOT};
    private static final float[] BODY_SHELL = {.525f, .063f, .122f}, BODY_WHORL = {.612f, .125f, .314f};
    private static final float[] BODY_CREAM = {.784f, .471f, .588f}, BODY_LIP = {.722f, .353f, .502f};
    /** The snail body's crumble chips (D101, {@link RemainsFx#crumble}): snail-body.grid's shell, cream whorl, whorl and lip. */
    public static final float[][] SNAIL_CHIPS = {BODY_SHELL, BODY_CREAM, BODY_WHORL, BODY_LIP};

    // Wind-up specks: 1 px, born on the firing line between the mouth and its far end, drawn in to the mouth.
    private static final float SPECK_SIZE = 1, SPECK_LIFE_MIN = .25f, SPECK_LIFE_MAX = .4f, SPECK_WHITE_CHANCE = .5f;
    /** The specks arrive within this many px of the mouth, above or below the line's middle. */
    private static final float SPECK_MOUTH_JITTER = 1;

    // The gob swelling at the mouth: a short glow each call, from SWELL_MIN to SWELL_MAX px over the wind-up.
    private static final float SWELL_LIFE = .06f, SWELL_MIN = 1, SWELL_MAX = 3;

    // Drips shed by the gob in flight: they fall to the floor and fade.
    private static final float DRIP_SIZE = 1, DRIP_LIFE = .3f, DRIP_DRIFT = 2;

    // The splat (hit or miss, D93): droplets thrown back from where it ended and a smear that fades.
    private static final int DROPLETS = 7, SMEAR = 4;
    private static final float DROPLET_SIZE_MIN = 1, DROPLET_SIZE_MAX = 2, DROPLET_DIST_MIN = 4, DROPLET_DIST_MAX = 9;
    private static final float DROPLET_LIFE_MIN = .2f, DROPLET_LIFE_MAX = .3f, DROPLET_SPREAD_DEGREES = 70, DROPLET_UP_DEGREES = 30;
    private static final float SMEAR_LIFE = .4f, SMEAR_SIZE = 1, SMEAR_STEP = 1.5f, SMEAR_INSET = 1;
    /** Of the droplets, one in this many is olive, the rest pale or mid. */
    private static final int DROPLET_DARK_EVERY = 3;

    // Fire takes the gob: a puff of steam rising, then the droplets of a splat.
    private static final int STEAM = 5;
    private static final float STEAM_RISE_MIN = 6, STEAM_RISE_MAX = 12, STEAM_DRIFT = 4, STEAM_LIFE_MIN = .3f, STEAM_LIFE_MAX = .45f;
    private static final float STEAM_SIZE_MIN = 3, STEAM_SIZE_MAX = 5;

    // Death (any weapon): a pale poof in a ring and a few shell chips to the floor.
    private static final int POOF = 12, CHIPS = 4;
    /** One puff in this many is grey, the rest pale: the poof reads lighter than the fireball's ring behind it. */
    private static final int POOF_GREY_EVERY = 3;
    private static final float POOF_DIST_MIN = 10, POOF_DIST_MAX = 16, POOF_LIFE_MIN = .4f, POOF_LIFE_MAX = .55f;
    private static final float POOF_SIZE_MIN = 5, POOF_SIZE_MAX = 8, POOF_ANGLE_JITTER = 15, CIRCLE = 360, HALF_TURN = 180;
    private static final float CHIP_SPREAD = 7, CHIP_DRIFT = 10, CHIP_LIFE = .45f, CHIP_SIZE = 1, CHIP_FLOOR_INSET = 1;

    private final KimeriaParticleSystem soft, hard;

    public SnailFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** The swelling glow, the steam and the poof ({@link SoftDot}). */
    public KimeriaParticleSystem soft() { return soft; }
    /** Specks, drips, droplets, smear and chips ({@link Shaders#whiteQuad()}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }

    /**
     * One wind-up speck for a snail whose mouth is at {@code mouthX}, its line running to {@code farX}, at height {@code lineY}:
     * born on the line between the two and drawn in to the mouth.
     */
    public void speck(float mouthX, float farX, float lineY) {
        RandomXS128 random = hard.random();
        float x = mouthX + random.nextFloat() * (farX - mouthX);
        float[] c = random.nextFloat() < SPECK_WHITE_CHANCE ? WHITE_RGB : GOB_MID;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, lineY, mouthX, lineY + range(random, -SPECK_MOUTH_JITTER, SPECK_MOUTH_JITTER),
            range(random, SPECK_LIFE_MIN, SPECK_LIFE_MAX), SPECK_SIZE, c[RED], c[GREEN], c[BLUE]);
    }

    /** The gob swelling at the mouth ({@code x}, {@code y}), {@code progress} 0 to 1 through the wind-up: one short glow. */
    public void swell(float x, float y, float progress) {
        float size = SWELL_MIN + (SWELL_MAX - SWELL_MIN) * Math.min(1f, Math.max(0f, progress));
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, x, y, x, y, SWELL_LIFE, size, GOB_PALE[RED], GOB_PALE[GREEN], GOB_PALE[BLUE]);
    }

    /** One drip the flying gob sheds at ({@code x}, {@code y}): it falls to {@code floorY} and fades. */
    public void drip(float x, float y, float floorY) {
        RandomXS128 random = hard.random();
        hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + range(random, -DRIP_DRIFT, DRIP_DRIFT), floorY - SMEAR_INSET, DRIP_LIFE,
            DRIP_SIZE, GOB_MID[RED], GOB_MID[GREEN], GOB_MID[BLUE]);
    }

    /**
     * The gob ends at ({@code x}, {@code y}), hit or miss: droplets thrown back the way {@code away} points (+1 right, -1 left)
     * and a smear that fades, along the floor ({@code onWall} false, at {@code y}) or down a wall face ({@code onWall} true).
     */
    public void splat(float x, float y, float away, boolean onWall) {
        RandomXS128 random = hard.random();
        float base = away > 0 ? 0 : HALF_TURN;
        for (int i = 0; i < DROPLETS; i++) {
            float angle = base + (away > 0 ? 1 : -1) * range(random, -DROPLET_SPREAD_DEGREES / 2, DROPLET_SPREAD_DEGREES / 2 + DROPLET_UP_DEGREES);
            float dist = range(random, DROPLET_DIST_MIN, DROPLET_DIST_MAX);
            float[] c = i % DROPLET_DARK_EVERY == 0 ? GOB_DARK : (i & 1) == 0 ? GOB_PALE : GOB_MID;
            // World Y grows downward: a droplet thrown up has a negative sine here, so the angle is mirrored about the line.
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, y, x + MathUtils.cosDeg(angle) * dist, y - MathUtils.sinDeg(angle) * dist,
                range(random, DROPLET_LIFE_MIN, DROPLET_LIFE_MAX), range(random, DROPLET_SIZE_MIN, DROPLET_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        for (int i = 0; i < SMEAR; i++) {
            float sx = onWall ? x : x + (i - SMEAR / 2f) * SMEAR_STEP, sy = onWall ? y + i * SMEAR_STEP : y;
            float[] c = (i & 1) == 0 ? GOB_MID : GOB_PALE;
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, sx, sy, sx, sy, SMEAR_LIFE, SMEAR_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
    }

    /** Fire takes the gob at ({@code x}, {@code y}): a puff of steam rises, and it splats where it was. */
    public void steam(float x, float y, float away) {
        RandomXS128 random = soft.random();
        for (int i = 0; i < STEAM; i++) {
            float[] c = (i & 1) == 0 ? PALE : GREY;
            soft.spawn(KimeriaParticleSystem.KIND_SMOKE, x, y, x + range(random, -STEAM_DRIFT, STEAM_DRIFT), y - range(random, STEAM_RISE_MIN, STEAM_RISE_MAX),
                range(random, STEAM_LIFE_MIN, STEAM_LIFE_MAX), range(random, STEAM_SIZE_MIN, STEAM_SIZE_MAX), c[RED], c[GREEN], c[BLUE]);
        }
        splat(x, y, away, false);
    }

    /** The death of a snail centred on ({@code centreX}, {@code centreY}) standing on {@code floorY}: a pale poof and shell chips. */
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
            float[] c = DEATH_CHIPS[i % DEATH_CHIPS.length];
            hard.spawn(KimeriaParticleSystem.KIND_GRIT, x, centreY, x + range(chipRandom, -CHIP_DRIFT, CHIP_DRIFT), floorY - CHIP_FLOOR_INSET,
                CHIP_LIFE, CHIP_SIZE, c[RED], c[GREEN], c[BLUE]);
        }
    }

    public void update(float dt) {
        soft.update(dt);
        hard.update(dt);
    }

    /** Glow, steam and poof, then the specks, drips, droplets and chips on top; the shared regions are fetched only when something is alive. */
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
