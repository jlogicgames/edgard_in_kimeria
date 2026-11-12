package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.function.BooleanSupplier;

/**
 * The environment effects of one {@code Level} (#239, #285 step 5): the screen shake, a dust puff under the hero on a
 * ground jump and the grit that falls from the ceiling when a lever moves a wall. Dust and grit are two
 * {@link KimeriaParticleSystem}s with their own seeded random streams. Everything here is cosmetic: no collision,
 * timing or route reads it. Particles advance only in {@link #update}, so pause freezes them. World Y grows downward.
 */
public final class EnvironmentFx {
    public static final int DUST_CAPACITY = 24, GRIT_CAPACITY = 32;
    public static final int DUST_PER_PUFF = 6, GRIT_PER_BURST = 24;
    /** Seconds over which one grit burst is emitted. */
    public static final float GRIT_EMIT_SECONDS = .6f;
    private static final long GRIT_SEED_MIX = 0x9E3779B97F4A7C15L;

    private static final float DUST_START_SPREAD_PX = 8, DUST_SIZE_PX = 3;
    private static final float DUST_LIFE_MIN = .25f, DUST_LIFE_MAX = .4f;
    private static final float DUST_RISE_MIN = 10, DUST_RISE_MAX = 22, DUST_DRIFT_SPEED = 25;
    private static final float[] DUST_COLORS = {.357f, .345f, .439f, .604f, .592f, .678f, .847f, .839f, .910f};

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
    public void dustPuff(float feetX, float feetY) {
        RandomXS128 random = dust.random();
        for (int n = 0; n < DUST_PER_PUFF; n++) {
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
