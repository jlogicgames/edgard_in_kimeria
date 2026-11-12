package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Behaviour of the blood system that replaced the per-hit {@code BloodBurst} (#285 step 3); the look is pinned by ParticleGoldenTest. */
class BloodSystemTest {
    private static final float STEP = 1f / 60f;
    private static final int CAPACITY = 96;
    private static final long SEED = 7264;
    private static final float X = 100, Y = 90, FLOOR = 110;

    @Test void sprayFliesInTheHitDirectionFallsAndExpires() {
        for (boolean empowered : new boolean[]{false, true}) for (float direction : new float[]{-1, 1}) {
            BloodSystem blood = new BloodSystem(CAPACITY, SEED);
            blood.spray(X, Y, direction, FLOOR, empowered);
            assertEquals(empowered ? 48 : 32, blood.count());
            float initialX = blood.x(1), initialY = blood.y(1);
            blood.update(.1f);
            assertTrue((blood.x(1) - initialX) * direction > 6);
            assertTrue(blood.y(1) < initialY, "the drop first rises (world Y grows downward)");
            for (int i = 0; i < 40; i++) {
                blood.update(STEP);
                for (int d = 0; d < blood.count(); d++) {
                    assertTrue(Float.isFinite(blood.x(d)));
                    assertTrue(blood.y(d) <= FLOOR, "Droplets must not fall through the floor");
                }
            }
            assertTrue(blood.count() > 0, "the spray is still alive before its longest life ends");
            blood.update(.2f);
            assertEquals(0, blood.count(), "every drop is gone once the longest life has passed");
            blood.update(1);
            assertEquals(0, blood.count(), "an expired spray stays gone and spawns nothing");
        }
    }

    @Test void empoweredSprayHasFortyEightThickerDropsAndLongerTrails() {
        BloodSystem normal = new BloodSystem(CAPACITY, SEED), strong = new BloodSystem(CAPACITY, SEED);
        normal.spray(X, Y, 1, FLOOR, false);
        strong.spray(X, Y, 1, FLOOR, true);
        assertEquals(32, normal.count());
        assertEquals(48, strong.count());
        for (int i = 0; i < normal.count(); i++) {
            assertEquals(normal.size(i) * 1.8f, strong.size(i), .0001f);
            assertEquals(normal.trailLength(i) * 4, strong.trailLength(i), .0001f);
            assertEquals(normal.vx(i), strong.vx(i));
            assertEquals(normal.vy(i), strong.vy(i));
            assertTrue(strong.empowered(i));
            assertFalse(normal.empowered(i));
        }
    }

    @Test void dropsLandOnTheFloorAndStayThereAsASplashUntilTheyFade() {
        BloodSystem blood = new BloodSystem(CAPACITY, SEED);
        blood.spray(X, Y, 1, FLOOR, false);
        for (int i = 0; i < 30; i++) blood.update(STEP);
        int landed = 0;
        float[] landedX = new float[blood.count()];
        for (int d = 0; d < blood.count(); d++) {
            landedX[d] = blood.landed(d) ? blood.x(d) : Float.NaN;
            if (blood.landed(d)) { landed++; assertEquals(FLOOR, blood.y(d), 0f); }
        }
        assertTrue(landed > 0, "some drops have reached the floor by half a second");
        int before = blood.count();
        blood.update(STEP / 4);
        assertEquals(before, blood.count(), "no drop expires in this quarter frame");
        for (int d = 0; d < blood.count(); d++) if (!Float.isNaN(landedX[d])) {
            assertTrue(blood.landed(d));
            assertEquals(landedX[d], blood.x(d), 0f, "a landed drop no longer moves");
            assertEquals(FLOOR, blood.y(d), 0f);
        }
    }

    @Test void dropsFadeOutOverTheirLastMoments() {
        BloodSystem blood = new BloodSystem(CAPACITY, SEED);
        blood.spray(X, Y, 1, FLOOR, false);
        for (int d = 0; d < blood.count(); d++) assertEquals(1f, blood.fade(d), 0f);
        for (int i = 0; i < 40; i++) blood.update(STEP);
        for (int d = 0; d < blood.count(); d++) {
            float remaining = blood.life(d) - blood.age(d);
            assertTrue(remaining > 0);
            assertEquals(Math.min(1f, remaining / .2f), blood.fade(d), 1e-6f);
        }
    }

    @Test void neverExceedsCapacityAndAFullPoolDropsTheOldest() {
        BloodSystem blood = new BloodSystem(40, SEED);
        blood.spray(0, Y, 1, FLOOR, false);   // 32 drops, origin x 0
        blood.spray(500, Y, 1, FLOOR, false); // 32 more: the 24 oldest make way
        assertEquals(40, blood.count());
        for (int d = 0; d < 8; d++) assertTrue(blood.x(d) < 100, "the newest of the first spray remain at the front");
        for (int d = 8; d < 40; d++) assertTrue(blood.x(d) > 400, "the second spray is intact and last");
    }

    @Test void sprayAgesIndependentlyAndClearEmpties() {
        BloodSystem blood = new BloodSystem(CAPACITY, SEED);
        blood.spray(X, Y, 1, FLOOR, false);
        for (int i = 0; i < 30; i++) blood.update(STEP);
        float firstAge = blood.age(0);
        blood.spray(X, Y, -1, FLOOR, false);
        assertEquals(0f, blood.age(blood.count() - 1), 0f, "a new spray starts at age 0");
        assertEquals(firstAge, blood.age(0), 0f);
        blood.clear();
        assertEquals(0, blood.count());
    }

    @Test void sameSeedGivesTheSameStateAndAnotherSeedADifferentOne() {
        float[] a = state(1), b = state(1), c = state(2);
        assertArrayEquals(a, b, 0f);
        assertFalse(java.util.Arrays.equals(a, c));
    }

    private static float[] state(long seed) {
        BloodSystem blood = new BloodSystem(CAPACITY, seed);
        blood.spray(X, Y, 1, FLOOR, true);
        for (int i = 0; i < 20; i++) blood.update(STEP);
        float[] values = new float[blood.count() * 2];
        for (int d = 0; d < blood.count(); d++) { values[2 * d] = blood.x(d); values[2 * d + 1] = blood.y(d); }
        return values;
    }
}
