package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #544: the soft light's bounds, which {@code tools/check_contrast.py} measures, and its fixed pools. */
class SoftGlowFxTest {
    private static final float STEP = 1f / 60f;
    /** A pool may be at most this full at its measured peak: a full pool silently drops its oldest light. */
    private static final float HEADROOM = .75f;

    /**
     * The contrast gate takes the pool at {@link SoftGlowFx#POOL_DIM} and {@link SoftGlowFx#POOL_BRIGHT} times its light: the alphas of the pool's
     * lights (the ones at the centre, rho 0) must stay between the two at every frame, and both must be reached.
     */
    @Test void thePoolBreathesBetweenTheTwoBoundsTheGateMeasures() {
        for (SoftGlowFx.Look look : new SoftGlowFx.Look[]{SoftGlowFx.PICKUP, SoftGlowFx.FAIRY_TARGET, SoftGlowFx.FAIRY_SELF}) {
            SoftGlowFx glow = new SoftGlowFx(look, 3);
            glow.place(50, 60, 8, 8);
            float low = 9, high = 0;
            for (int frame = 0; frame < 600; frame++) {
                glow.update(STEP); // from the very first frame: a light that appears is already running (D82)
                float sum = 0;
                KimeriaParticleSystem light = glow.light();
                for (int i = 0; i < light.count(); i++) if (light.x(i) == 50 && light.y(i) == 60) sum += light.alpha(i);
                low = Math.min(low, sum); high = Math.max(high, sum);
            }
            assertEquals(SoftGlowFx.POOL_DIM, low, 0.05f, "dimmest sum of the pool's alphas");
            assertEquals(SoftGlowFx.POOL_BRIGHT, high, 0.05f, "brightest sum of the pool's alphas");
        }
    }

    @Test void thePoolsNeverFillUpAtAnyFrameRate() {
        for (SoftGlowFx.Look look : new SoftGlowFx.Look[]{SoftGlowFx.PICKUP, SoftGlowFx.FAIRY_TARGET, SoftGlowFx.FAIRY_SELF})
            for (float dt : new float[]{1f / 30, 1f / 60, 1f / 144, 1f / 240}) for (long seed = 0; seed < 10; seed++) {
                SoftGlowFx glow = new SoftGlowFx(look, seed);
                glow.place(0, 0, 14, 14);
                int light = 0, specks = 0;
                for (float t = 0; t < 12; t += dt) {
                    glow.update(dt);
                    light = Math.max(light, glow.light().count()); specks = Math.max(specks, glow.specks().count());
                }
                System.out.println("PEAK glow light " + light + " specks " + specks + " dt " + dt);
                assertTrue(light <= SoftGlowFx.LIGHT_CAPACITY * HEADROOM, "light peak " + light + " at dt " + dt);
                assertTrue(specks <= SoftGlowFx.SPECK_CAPACITY * HEADROOM, "speck peak " + specks + " at dt " + dt);
            }
    }

    /** D82, D76: the first frame of a light already shows the pool within its bounds and every rim dot, so the contrast bound holds from frame one. */
    @Test void aLightThatAppearsShowsAtFullStrengthOnItsFirstFrame() {
        for (SoftGlowFx.Look look : new SoftGlowFx.Look[]{SoftGlowFx.PICKUP, SoftGlowFx.FAIRY_TARGET, SoftGlowFx.FAIRY_SELF}) {
            SoftGlowFx glow = new SoftGlowFx(look, 9);
            glow.place(30, 40, 8, 8);
            glow.update(STEP);
            float pool = 0;
            int rim = 0;
            KimeriaParticleSystem light = glow.light();
            for (int i = 0; i < light.count(); i++) {
                if (light.x(i) == 30 && light.y(i) == 40) pool += light.alpha(i); else rim++;
            }
            assertTrue(pool >= SoftGlowFx.POOL_DIM - 0.05f && pool <= SoftGlowFx.POOL_BRIGHT + 0.05f, "pool alpha on the first frame: " + pool);
            assertEquals(look.rimDots(), rim, "every rim dot is there on the first frame");
        }
        SoftGlowFx glow = new SoftGlowFx(SoftGlowFx.FAIRY_SELF, 1);  // she hides and appears again on every room entry and retry
        glow.place(0, 0, 4, 4); glow.update(STEP); glow.clear();
        glow.place(0, 0, 4, 4); glow.update(STEP);
        assertTrue(glow.light().alpha(0) >= 0.99f, "a cleared and placed light is primed again");
    }

    @Test void nothingSpawnsUntilPlacedAndClearEmptiesIt() {
        SoftGlowFx glow = new SoftGlowFx(SoftGlowFx.PICKUP, 1);
        glow.update(1f);
        assertEquals(0, glow.light().count());
        glow.place(0, 0, 8, 8);
        glow.update(0.5f);
        assertTrue(glow.light().count() > 0);
        glow.clear();
        assertEquals(0, glow.light().count() + glow.specks().count());
        assertTrue(!glow.placed());
    }

    @Test void theRimDotsSitOnTheOvalAndTheSpecksCircleItAdditively() {
        SoftGlowFx glow = new SoftGlowFx(SoftGlowFx.FAIRY_TARGET, 5);
        glow.place(0, 0, 14, 10);
        for (int i = 0; i < 90; i++) glow.update(STEP);
        for (int i = 0; i < glow.light().count(); i++) {
            float x = glow.light().x(i), y = glow.light().y(i);
            if (x == 0 && y == 0) continue;
            assertEquals(1f, (x / 14) * (x / 14) + (y / 10) * (y / 10), 1e-3f, "a rim dot is on the oval");
            assertEquals(KimeriaParticleSystem.KIND_ORBIT, glow.light().kind(i));
        }
    }
}
