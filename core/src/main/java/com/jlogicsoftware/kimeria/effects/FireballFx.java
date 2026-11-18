package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The hero's fireball (D66) in flight and where it stops (#397, part of #396): a short flame trail shed behind the ball,
 * a flame splash where it hits terrain, a monster, a stump or root or a hive, a heavier burst on the boss and a small
 * burn-out when its life ends. One per {@code Level}, like the blood. Two {@link KimeriaParticleSystem}s with their own
 * seeded streams: {@link #soft()} holds one warm additive halo per hit ({@link SoftDot}); {@link #hard()} holds the flame
 * and sparks as 1 to 3 px opaque squares ({@link Shaders#whiteQuad()}, {@code KIND_FLAME}) that shrink out instead of
 * fading, so no fading warm colour ever mixes with the sky into olive or magenta. The palette is warm only, from the
 * fireball sprite ({@code images/objects/fireball.png}): pale core, yellow, orange, red; no smoke, no grey. A flame's colour
 * sets its life (pale dies first, red last), so the trail cools from the ball outward. The ball's own steady halo is
 * {@link #drawBallHalo}. Where the ball hits, an expanding fire-coloured ring (the existing {@code shockwave.frag}, as
 * {@link ShockwaveEffect} draws it), small on a light hit and larger on the boss, from fixed slots owned here: nothing is
 * created per hit, and there is no screen distortion. patch-gg/pixelfx's {@code fire/fireball}, {@code impacts/hit-spark} and {@code impacts/blunt-hit}
 * were a reference for motion and timing only, no code or constant was copied. Cosmetic: no collision, timing or route
 * reads it, and nothing shakes or flashes the screen. Particles advance only in {@link #update}, so pause freezes them.
 * World Y grows downward: a rising particle ends at a smaller Y.
 */
public final class FireballFx {
    /**
     * Measured peaks (FireballFxTest: three balls, three boss bursts 0.1 s apart, 40 seeds): 3 soft, 185 hard; with at least
     * 25 % headroom. A full pool only drops the oldest particle.
     */
    public static final int SOFT_CAPACITY = 8, HARD_CAPACITY = 240;
    /** The trail sheds one flame per this many pixels flown, so it looks the same at any frame rate. */
    public static final float TRAIL_SPACING_PX = 2;
    private static final long HARD_SEED_MIX = 0x9E3779B97F4A7C15L;

    /** The sprite's warm ramp, hot to cool: pale core `fdfaa0`, yellow `fde902`, orange `fc5601`, red `c50e10`. */
    private static final float[] RAMP = {.992f, .980f, .627f, .992f, .914f, .008f, .988f, .337f, .004f, .773f, .055f, .063f};
    private static final int PALE = 0, YELLOW = 1, ORANGE = 2, RED = 3;
    /**
     * The warm halo, added to what is behind it: the sprite's amber at about two thirds, so over the blue sky it reads as
     * warm light (peach), not mauve.
     */
    private static final float[] HALO = {.62f, .38f, .04f};
    private static final int STRIDE = 3, GREEN = 1, BLUE = 2;

    // Trail, per step: one flame square whose size and life follow its colour (pale 2-3 px for .04-.06 s ... red 1-2 px
    // for .12-.17 s), drifting a little after the ball and rising; sometimes an ember that falls as it cools. Half the
    // length of the first version (red lived .2-.3 s).
    private static final float[] TRAIL_LIFE_MIN = {.04f, .06f, .09f, .12f}, TRAIL_LIFE_SPAN = {.02f, .03f, .04f, .05f};
    private static final float[] TRAIL_SIZE_MIN = {2, 2, 1.5f, 1}, TRAIL_SIZE_SPAN = {1, 1, 1, 1};
    /** Cumulative chances of pale, yellow, orange; red is the rest. Short and bright at the ball. */
    private static final float TRAIL_PALE = .2f, TRAIL_YELLOW = .55f, TRAIL_ORANGE = .85f;
    private static final float TRAIL_JITTER_Y = 2, TRAIL_FOLLOW = 50, TRAIL_RISE_MIN = 1, TRAIL_RISE_MAX = 5;
    private static final float EMBER_CHANCE = .12f, EMBER_JITTER_Y = 2, EMBER_LIFE_MIN = .2f, EMBER_LIFE_MAX = .35f;
    private static final float EMBER_SIZE = 1, EMBER_FOLLOW_MAX = 10, EMBER_FALL_MIN = 6, EMBER_FALL_MAX = 12;

    /** The steady halo under the flying ball: size in px and alpha (additive, {@link SoftDot#region()}). */
    private static final float BALL_HALO_SIZE = 30, BALL_HALO_ALPHA = .9f;

    // A light hit (terrain, a monster, a stump or root, a hive): a warm halo, a splash of flame thrown along the spray that
    // rises and dies out, a cone of sparks that fall; together they outlive the ring a little.
    private static final float HIT_HALO_SIZE = 30, HIT_HALO_LIFE = .2f;
    private static final int HIT_FLAMES = 24, HIT_SPARKS = 14;
    private static final float HIT_FLAME_SPREAD = 1.3f, HIT_FLAME_DIST_MIN = 6, HIT_FLAME_DIST_MAX = 18, FLAME_LIFT = 6;
    private static final float HIT_FLAME_SIZE_MIN = 1.5f, HIT_FLAME_SIZE_MAX = 3, HIT_FLAME_LIFE_MIN = .2f, HIT_FLAME_LIFE_MAX = .32f;
    private static final float HIT_SPARK_SPREAD = .6f, SPARK_LIFT = .25f, HIT_SPARK_DIST_MIN = 14, HIT_SPARK_DIST_MAX = 30;
    private static final float SPARK_FALL_MIN = 4, SPARK_FALL_MAX = 10, SPARK_SIZE_MIN = 1, SPARK_SIZE_MAX = 1.5f;
    private static final float SPARK_LIFE_MIN = .22f, SPARK_LIFE_MAX = .32f;

    // The boss: a bigger halo, a squashed ring of flame all round, more sparks.
    private static final float HEAVY_HALO_SIZE = 46, HEAVY_HALO_LIFE = .3f;
    private static final int HEAVY_FLAMES = 30, HEAVY_SPARKS = 22;
    private static final float RING_JITTER = .25f, RING_SQUASH = .5f, HEAVY_FLAME_DIST_MIN = 10, HEAVY_FLAME_DIST_MAX = 22;
    private static final float HEAVY_FLAME_SIZE_MIN = 2, HEAVY_FLAME_SIZE_MAX = 3, HEAVY_FLAME_LIFE_MIN = .3f, HEAVY_FLAME_LIFE_MAX = .44f;
    private static final float HEAVY_SPARK_SPREAD = 1.4f, HEAVY_SPARK_DIST_MIN = 20, HEAVY_SPARK_DIST_MAX = 40;
    private static final float HEAVY_SPARK_LIFE_MIN = .3f, HEAVY_SPARK_LIFE_MAX = .44f;

    // Burn-out at the end of the ball's life: a few small flames that rise and go out over a small halo.
    private static final int FIZZLE_FLAMES = 4;
    private static final float FIZZLE_SIZE_MIN = 1.5f, FIZZLE_SIZE_MAX = 2.5f, FIZZLE_LIFE_MIN = .1f, FIZZLE_LIFE_MAX = .2f;
    private static final float FIZZLE_SPREAD = 3, FIZZLE_RISE = 8, FIZZLE_HALO_SIZE = 16, FIZZLE_HALO_LIFE = .12f;

    // The impact ring, the F3 debug ShockwaveEffect (0.6 s, 64 px, 8 px wide) scaled to a fireball: small on a light hit, larger
    // on the boss, none on a burn-out.
    /** Ring slots: three balls can stop within a few frames (RangedStock.CAPACITY); a full pool reuses the oldest ring. */
    public static final int RING_CAPACITY = 4;
    // The ring ends a little before the burst's particles (light .2-.32 s, boss .3-.44 s), so the two read as one blast.
    private static final float RING_LIFE = .26f, RING_RADIUS = 20, RING_WIDTH = 3;
    private static final float HEAVY_RING_LIFE = .36f, HEAVY_RING_RADIUS = 36, HEAVY_RING_WIDTH = 5;
    /**
     * The ring's colour as an intensity: {@code shockwave.frag} multiplies it by a fade that peaks near 0.25, and the result is
     * added to the sky, so an orange of 1 would read grey over blue. Four times the sprite's orange lands warm at the peak.
     */
    private static final float[] RING_COLOR = {4f, 2.2f, .3f};

    private final KimeriaParticleSystem soft, hard;
    private final RingPool rings = new RingPool(RING_CAPACITY, RING_COLOR);

    public FireballFx(long seed) {
        soft = new KimeriaParticleSystem(SOFT_CAPACITY, seed);
        hard = new KimeriaParticleSystem(HARD_CAPACITY, seed ^ HARD_SEED_MIX);
    }

    /** Live impact rings, for tests. */
    public int ringCount() { return rings.count(); }
    /** The radius the {@code i}-th live ring grows to, oldest first, for tests ({@code i} below {@link #ringCount}). */
    public float ringRadius(int i) { return rings.radius(i); }

    /** The warm halos of the hits, drawn additively with {@link SoftDot}. */
    public KimeriaParticleSystem soft() { return soft; }
    /** Flame and sparks: opaque squares that shrink out ({@code KIND_FLAME}). */
    public KimeriaParticleSystem hard() { return hard; }

    private static float range(RandomXS128 random, float min, float max) { return min + random.nextFloat() * (max - min); }
    private static float jitter(RandomXS128 random, float half) { return range(random, -half, half); }

    /**
     * One trail step at ({@code x}, {@code y}), the back of the ball, flying in {@code direction} (1 right, -1 left). The
     * ball calls it once per {@value #TRAIL_SPACING_PX} px flown.
     */
    public void trail(float x, float y, float direction) {
        RandomXS128 random = hard.random();
        float roll = random.nextFloat();
        int c = roll < TRAIL_PALE ? PALE : roll < TRAIL_YELLOW ? YELLOW : roll < TRAIL_ORANGE ? ORANGE : RED;
        float life = TRAIL_LIFE_MIN[c] + random.nextFloat() * TRAIL_LIFE_SPAN[c], startY = y + jitter(random, TRAIL_JITTER_Y);
        flame(c, x, startY, x + direction * TRAIL_FOLLOW * life, startY - range(random, TRAIL_RISE_MIN, TRAIL_RISE_MAX), life,
            TRAIL_SIZE_MIN[c] + random.nextFloat() * TRAIL_SIZE_SPAN[c]);
        if (random.nextFloat() < EMBER_CHANCE) {
            float emberY = y + jitter(random, EMBER_JITTER_Y);
            flame(ORANGE + random.nextInt(RED - ORANGE + 1), x, emberY, x + direction * random.nextFloat() * EMBER_FOLLOW_MAX,
                emberY + range(random, EMBER_FALL_MIN, EMBER_FALL_MAX), range(random, EMBER_LIFE_MIN, EMBER_LIFE_MAX), EMBER_SIZE);
        }
    }

    /**
     * A light hit at ({@code x}, {@code y}): {@code sprayDirection} is 1 for a splash to the right, -1 to the left (back off a
     * wall, or on along the blow into a creature or an object).
     */
    public void hit(float x, float y, float sprayDirection) {
        halo(x, y, HIT_HALO_SIZE, HIT_HALO_LIFE);
        rings.start(x, y, RING_LIFE, RING_RADIUS, RING_WIDTH);
        float aim = sprayDirection > 0 ? 0 : MathUtils.PI;
        RandomXS128 random = hard.random();
        for (int n = 0; n < HIT_FLAMES; n++) {
            float a = aim + jitter(random, HIT_FLAME_SPREAD), d = range(random, HIT_FLAME_DIST_MIN, HIT_FLAME_DIST_MAX);
            flame(random.nextInt(RED + 1), x, y, x + MathUtils.cos(a) * d, y + MathUtils.sin(a) * d - FLAME_LIFT,
                range(random, HIT_FLAME_LIFE_MIN, HIT_FLAME_LIFE_MAX), range(random, HIT_FLAME_SIZE_MIN, HIT_FLAME_SIZE_MAX));
        }
        // Y grows downward: the sparks are aimed a little upward (a negative angle) and fall as they fly.
        sparkCone(x, y, aim - sprayDirection * SPARK_LIFT, HIT_SPARK_SPREAD, HIT_SPARKS, HIT_SPARK_DIST_MIN, HIT_SPARK_DIST_MAX,
            SPARK_LIFE_MIN, SPARK_LIFE_MAX);
    }

    /** The boss takes the fireball at ({@code x}, {@code y}): a heavier burst all round, see {@link #hit}. */
    public void heavyHit(float x, float y, float sprayDirection) {
        halo(x, y, HEAVY_HALO_SIZE, HEAVY_HALO_LIFE);
        rings.start(x, y, HEAVY_RING_LIFE, HEAVY_RING_RADIUS, HEAVY_RING_WIDTH);
        RandomXS128 random = hard.random();
        for (int n = 0; n < HEAVY_FLAMES; n++) {
            float a = MathUtils.PI2 * n / HEAVY_FLAMES + jitter(random, RING_JITTER), d = range(random, HEAVY_FLAME_DIST_MIN, HEAVY_FLAME_DIST_MAX);
            flame(random.nextInt(RED + 1), x, y, x + MathUtils.cos(a) * d, y + MathUtils.sin(a) * d * RING_SQUASH - FLAME_LIFT,
                range(random, HEAVY_FLAME_LIFE_MIN, HEAVY_FLAME_LIFE_MAX), range(random, HEAVY_FLAME_SIZE_MIN, HEAVY_FLAME_SIZE_MAX));
        }
        float aim = sprayDirection > 0 ? 0 : MathUtils.PI;
        sparkCone(x, y, aim - sprayDirection * SPARK_LIFT, HEAVY_SPARK_SPREAD, HEAVY_SPARKS, HEAVY_SPARK_DIST_MIN, HEAVY_SPARK_DIST_MAX,
            HEAVY_SPARK_LIFE_MIN, HEAVY_SPARK_LIFE_MAX);
    }

    /** The ball burns out at the end of its life at ({@code x}, {@code y}): a few small flames that rise, no sparks. */
    public void fizzle(float x, float y) {
        halo(x, y, FIZZLE_HALO_SIZE, FIZZLE_HALO_LIFE);
        RandomXS128 random = hard.random();
        for (int n = 0; n < FIZZLE_FLAMES; n++) {
            float sx = x + jitter(random, FIZZLE_SPREAD), sy = y + jitter(random, FIZZLE_SPREAD);
            flame(YELLOW + random.nextInt(RED - YELLOW + 1), sx, sy, sx, sy - FIZZLE_RISE,
                range(random, FIZZLE_LIFE_MIN, FIZZLE_LIFE_MAX), range(random, FIZZLE_SIZE_MIN, FIZZLE_SIZE_MAX));
        }
    }

    /** One warm halo that stays in place, shrinks a little and fades ({@code KIND_GLOW}, additive). */
    private void halo(float x, float y, float size, float life) {
        soft.spawn(KimeriaParticleSystem.KIND_GLOW, x, y, x, y, life, size, HALO[0], HALO[GREEN], HALO[BLUE]);
    }

    private void flame(int color, float x, float y, float toX, float toY, float life, float size) {
        int c = color * STRIDE;
        hard.spawn(KimeriaParticleSystem.KIND_FLAME, x, y, toX, toY, life, size, RAMP[c], RAMP[c + GREEN], RAMP[c + BLUE]);
    }

    private void sparkCone(float x, float y, float aim, float spread, int count, float distMin, float distMax, float lifeMin, float lifeMax) {
        RandomXS128 random = hard.random();
        for (int n = 0; n < count; n++) {
            float a = aim + jitter(random, spread), d = range(random, distMin, distMax);
            flame(random.nextInt(YELLOW + 1), x, y, x + MathUtils.cos(a) * d,
                y + MathUtils.sin(a) * d + range(random, SPARK_FALL_MIN, SPARK_FALL_MAX), range(random, lifeMin, lifeMax),
                range(random, SPARK_SIZE_MIN, SPARK_SIZE_MAX));
        }
    }

    /**
     * The flying ball's steady warm halo, centred on ({@code centerX}, {@code centerY}); drawn by the ball under its sprite.
     * Additive, then white and normal blending are restored.
     */
    public static void drawBallHalo(Batch batch, float centerX, float centerY) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(HALO[0], HALO[GREEN], HALO[BLUE], BALL_HALO_ALPHA);
        batch.draw(SoftDot.region(), centerX - BALL_HALO_SIZE / 2f, centerY - BALL_HALO_SIZE / 2f, BALL_HALO_SIZE, BALL_HALO_SIZE);
        batch.setColor(1, 1, 1, 1);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void update(float dt) {
        soft.update(dt);
        hard.update(dt);
        rings.update(dt);
    }

    /** The halos, then the flame and sparks on top; the shared regions are fetched only when something is alive. */
    public void render(Batch batch) {
        if (soft.count() > 0) soft.render(batch, SoftDot.region());
        if (hard.count() > 0) hard.render(batch, Shaders.whiteQuad());
        rings.render(batch);
    }

    /** Room change, retry and level restart: nothing carries over. */
    public void clear() {
        soft.clear();
        hard.clear();
        rings.clear();
    }
}
