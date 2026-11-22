package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.RandomXS128;

/**
 * The spore puffball's spores as particles (#558, D46): they replace the old sprite cloud. Two {@link KimeriaParticleSystem}s with
 * existing kinds only, so nothing here is a new kind:
 * <ul>
 * <li>{@link #spores()}: {@link KimeriaParticleSystem#KIND_GRIT}, drawn with {@code Shaders.whiteQuad()}, so each spore is a crisp 1 to
 * 2 px square of the lethal lime;</li>
 * <li>{@link #haze()}: {@link KimeriaParticleSystem#KIND_SMOKE} puffs rolling out of the chimney and one {@link KimeriaParticleSystem#KIND_GLOW}
 * flash at the release, drawn with {@code SoftDot.region()}.</li>
 * </ul>
 * Timeline: while the sac swells in its second half a few spores leak from the chimney (a warning, inside the lethal rectangle's
 * footprint); at the release a flash, a jet out of the chimney, {@link #SPORE_BURST} spores over the whole rectangle and a haze; while the
 * cloud is lethal more spores and puffs are kept up; the frame the hitbox ends everything is cleared with it (D5: what is drawn is what
 * kills, for its whole life). Every particle's start and end are kept half its largest drawn size inside the rectangle and positions are the
 * system's straight lerp, so no quad ever leaves it. World Y grows downward: a rising spore has an end Y smaller than its start Y.
 * <p>
 * Nothing allocates after construction; {@link #update} runs only while the game runs (pause freezes it); a room rebuild makes a new one.
 */
public final class PuffballFx {
    /** Pool sizes from measured peaks (PuffballFxTest, 40 seeds at 30, 60 and 144 fps, 20 s each): at most 71 live spores and 14 haze puffs, so 96 and 24 keep the test's headroom of 8. */
    public static final int SPORE_CAPACITY = 96, HAZE_CAPACITY = 24;
    public static final int SPORE_BURST = 40, JET_COUNT = 6, HAZE_BURST = 8, SUSTAIN_SPORES = 3;
    /** Seconds between the leak's specks, the sustained spores and the sustained puffs. */
    public static final float LEAK_STEP = 0.06f, SUSTAIN_STEP = 0.05f, HAZE_STEP = 0.10f;
    private static final float LEAK_LIFE = 0.3f, LEAK_SIZE = 1f, LEAK_RISE_MIN = 3f, LEAK_RISE_SPAN = 3f, LEAK_SIDE = 4f, LEAK_MOUTH_SPREAD = 3f;
    private static final float SPORE_SIZE_MIN = 1f, SPORE_SIZE_MAX = 2f, SPORE_LIFE_MIN = 0.5f, SPORE_LIFE_MAX = 0.9f;
    private static final float SUSTAIN_LIFE_MIN = 0.4f, SUSTAIN_LIFE_MAX = 0.6f;
    private static final float SPORE_OUTWARD = 0.25f, SPORE_JITTER = 3f, SPORE_RISE_MIN = 2f, SPORE_RISE_SPAN = 4f;
    private static final float LIGHT_SHARE = 0.35f, MID_SHARE = 0.80f;
    private static final float JET_SIZE = 2f, JET_LIFE_MIN = 0.18f, JET_LIFE_MAX = 0.30f, JET_SPREAD = 6f, JET_TOP_MIN = 1f, JET_TOP_SPAN = 3f;
    private static final float HAZE_SIZE_MIN = 5f, HAZE_SIZE_MAX = 6f, HAZE_LIFE_MIN = 0.5f, HAZE_LIFE_MAX = 0.8f, HAZE_MOUTH_SPREAD = 4f;
    private static final float FLASH_SIZE = 18f, FLASH_LIFE = 0.2f;
    private static final float HALF = 0.5f, TWO = 2f;
    /** Palette of the lethal lime (the old {@code trap-spores}, D63): 5fa02a, a6e04a, e6f26a. */
    private static final float DARK_R = 0.373f, DARK_G = 0.627f, DARK_B = 0.165f;
    private static final float MID_R = 0.651f, MID_G = 0.878f, MID_B = 0.290f;
    private static final float LIGHT_R = 0.902f, LIGHT_G = 0.949f, LIGHT_B = 0.416f;

    private final KimeriaParticleSystem spores, haze;
    private final float left, top, width, height, mouthX, mouthY;
    private float leakClock, sporeClock, hazeClock;
    private boolean wasLethal;

    /**
     * @param left,top,width,height the lethal rectangle
     * @param mouthX,mouthY the chimney mouth, in world coordinates
     */
    public PuffballFx(float left, float top, float width, float height, float mouthX, float mouthY, long seed) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.mouthX = mouthX;
        this.mouthY = mouthY;
        spores = new KimeriaParticleSystem(SPORE_CAPACITY, seed);
        haze = new KimeriaParticleSystem(HAZE_CAPACITY, ~seed);
    }

    public KimeriaParticleSystem spores() { return spores; }
    public KimeriaParticleSystem haze() { return haze; }
    public int count() { return spores.count() + haze.count(); }

    /** The warning's leak: a 1 px spore rises out of the chimney. */
    private void leak() {
        RandomXS128 random = spores.random();
        float inset = LEAK_SIZE / TWO;
        float sx = clampX(mouthX + (random.nextFloat() - HALF) * LEAK_MOUTH_SPREAD, inset);
        float ex = clampX(mouthX + (random.nextFloat() - HALF) * LEAK_SIDE, inset);
        float ey = clampY(mouthY - LEAK_RISE_MIN - random.nextFloat() * LEAK_RISE_SPAN, inset);
        spores.spawn(KimeriaParticleSystem.KIND_GRIT, sx, clampY(mouthY, inset), ex, ey, LEAK_LIFE, LEAK_SIZE, MID_R, MID_G, MID_B);
    }

    /** One spore of the cloud: anywhere inside the rectangle, drifting up and a little outward. */
    private void spore(float lifeMin, float lifeMax) {
        RandomXS128 random = spores.random();
        float size = SPORE_SIZE_MIN + random.nextFloat() * (SPORE_SIZE_MAX - SPORE_SIZE_MIN);
        float inset = size / TWO;
        float sx = left + inset + random.nextFloat() * (width - TWO * inset);
        float sy = top + inset + random.nextFloat() * (height - TWO * inset);
        float centreX = left + width / TWO;
        float ex = clampX(sx + (sx - centreX) * SPORE_OUTWARD + (random.nextFloat() - HALF) * SPORE_JITTER, inset);
        float ey = clampY(sy - SPORE_RISE_MIN - random.nextFloat() * SPORE_RISE_SPAN, inset);
        float shade = random.nextFloat();
        float life = lifeMin + random.nextFloat() * (lifeMax - lifeMin);
        if (shade < LIGHT_SHARE) spores.spawn(KimeriaParticleSystem.KIND_GRIT, sx, sy, ex, ey, life, size, LIGHT_R, LIGHT_G, LIGHT_B);
        else if (shade < MID_SHARE) spores.spawn(KimeriaParticleSystem.KIND_GRIT, sx, sy, ex, ey, life, size, MID_R, MID_G, MID_B);
        else spores.spawn(KimeriaParticleSystem.KIND_GRIT, sx, sy, ex, ey, life, size, DARK_R, DARK_G, DARK_B);
    }

    /** One soft puff rolling out of the chimney to a point of the rectangle. */
    private void puff() {
        RandomXS128 random = haze.random();
        float size = HAZE_SIZE_MIN + random.nextFloat() * (HAZE_SIZE_MAX - HAZE_SIZE_MIN);
        float inset = size; // a puff grows to twice its size, so half of that is its base size
        float sx = clampX(mouthX + (random.nextFloat() - HALF) * HAZE_MOUTH_SPREAD, inset);
        float sy = clampY(mouthY, inset);
        float ex = left + inset + random.nextFloat() * (width - TWO * inset);
        float ey = top + inset + random.nextFloat() * (height - TWO * inset);
        float mix = random.nextFloat();
        float life = HAZE_LIFE_MIN + random.nextFloat() * (HAZE_LIFE_MAX - HAZE_LIFE_MIN);
        haze.spawn(KimeriaParticleSystem.KIND_SMOKE, sx, sy, ex, ey, life, size,
            DARK_R + (MID_R - DARK_R) * mix, DARK_G + (MID_G - DARK_G) * mix, DARK_B + (MID_B - DARK_B) * mix);
    }

    /** The release, on the first lethal frame: a flash, a jet out of the chimney, spores over the whole rectangle and a haze. */
    public void release() {
        float cx = left + width / TWO, cy = top + height / TWO;
        haze.spawn(KimeriaParticleSystem.KIND_GLOW, cx, cy, cx, cy, FLASH_LIFE, FLASH_SIZE, MID_R, MID_G, MID_B);
        for (int n = 0; n < HAZE_BURST; n++) puff();
        for (int n = 0; n < SPORE_BURST; n++) spore(SPORE_LIFE_MIN, SPORE_LIFE_MAX);
        RandomXS128 random = spores.random();
        float inset = JET_SIZE / TWO;
        for (int n = 0; n < JET_COUNT; n++) {
            float ex = clampX(mouthX + (random.nextFloat() - HALF) * TWO * JET_SPREAD, inset);
            float ey = clampY(top + JET_TOP_MIN + random.nextFloat() * JET_TOP_SPAN, inset);
            float life = JET_LIFE_MIN + random.nextFloat() * (JET_LIFE_MAX - JET_LIFE_MIN);
            spores.spawn(KimeriaParticleSystem.KIND_GRIT, clampX(mouthX, inset), clampY(mouthY, inset), ex, ey, life, JET_SIZE, LIGHT_R, LIGHT_G, LIGHT_B);
        }
        wasLethal = true;
    }

    /**
     * Ages and spawns for one frame. {@code leaking}: the sac is in the second half of its swell; {@code lethal}: the cloud is out. The frame
     * after the cloud the particles are cleared with the hitbox.
     */
    public void update(float dt, boolean leaking, boolean lethal) {
        spores.update(dt);
        haze.update(dt);
        if (lethal) {
            sporeClock += dt;
            while (sporeClock >= SUSTAIN_STEP) {
                sporeClock -= SUSTAIN_STEP;
                for (int n = 0; n < SUSTAIN_SPORES; n++) spore(SUSTAIN_LIFE_MIN, SUSTAIN_LIFE_MAX);
            }
            hazeClock += dt;
            while (hazeClock >= HAZE_STEP) {
                hazeClock -= HAZE_STEP;
                puff();
            }
        } else {
            if (wasLethal) clear();
            sporeClock = hazeClock = 0f;
        }
        if (leaking) {
            leakClock += dt;
            while (leakClock >= LEAK_STEP) {
                leakClock -= LEAK_STEP;
                leak();
            }
        } else leakClock = 0f;
        wasLethal = lethal;
    }

    public void clear() {
        spores.clear();
        haze.clear();
    }

    private float clampX(float x, float inset) { return clamp(x, left + inset, left + width - inset); }
    private float clampY(float y, float inset) { return clamp(y, top + inset, top + height - inset); }
    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    /** The soft haze first, then the crisp spores over it. Restores the batch's colour and blending. */
    public void render(Batch batch) {
        if (haze.count() > 0) haze.render(batch, SoftDot.region());
        if (spores.count() > 0) spores.render(batch, Shaders.whiteQuad());
    }
}
