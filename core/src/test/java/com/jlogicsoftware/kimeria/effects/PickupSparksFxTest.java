package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pickup spark burst ({@link PickupSparksFx}, #402). World Y grows downward: rising is a smaller Y. */
class PickupSparksFxTest {
    private static final float STEP = 1f / 60f, X = 200, Y = 150;
    /** The ripple a pickup also spawns lives 0.75 s (Collectable); the sparks must not outlive it. */
    private static final float RIPPLE_LIFE = .75f, MAX_SQUARE = 3, MAX_REACH = 28;
    private static final int SEEDS = 40;

    @Test void aBurstIsTwelveWarmSquaresOfOneToThreePixelsThatStartAtTheItemCentre() {
        for (long seed = 0; seed < SEEDS; seed++) {
            PickupSparksFx fx = new PickupSparksFx(seed);
            fx.burst(X, Y);
            KimeriaParticleSystem p = fx.sparks();
            assertEquals(PickupSparksFx.SPARKS, p.count());
            for (int i = 0; i < p.count(); i++) {
                assertEquals(KimeriaParticleSystem.KIND_FLAME, p.kind(i), "opaque, shrinks out");
                assertEquals(X, p.x(i), 0f);
                assertEquals(Y, p.y(i), 0f);
                assertTrue(p.drawSize(i) >= 1.5f && p.drawSize(i) <= MAX_SQUARE, "1 to 3 px: " + p.drawSize(i));
                assertTrue(p.red(i) == 1f && p.green(i) >= .9f && p.blue(i) >= .6f, "the sparkle's white or warm white");
                assertTrue(p.maxLife(i) <= RIPPLE_LIFE, "no longer than the ripple");
            }
        }
    }

    @Test void theSparksRadiateAllRoundShrinkOutAndAreGoneBeforeTheRipple() {
        PickupSparksFx fx = new PickupSparksFx(7);
        fx.burst(X, Y);
        KimeriaParticleSystem p = fx.sparks();
        float[] size = new float[p.count()];
        for (int i = 0; i < size.length; i++) size[i] = p.drawSize(i);
        fx.update(.1f);
        boolean left = false, right = false, up = false, down = false;
        for (int i = 0; i < p.count(); i++) {
            assertTrue(p.drawSize(i) < size[i], "shrinks");
            assertEquals(1f, p.alpha(i), 0f, "does not fade");
            assertTrue(Math.hypot(p.x(i) - X, p.y(i) - Y) <= MAX_REACH);
            left |= p.x(i) < X - 1; right |= p.x(i) > X + 1; up |= p.y(i) < Y - 1; down |= p.y(i) > Y + 1;
        }
        assertTrue(left && right && up && down, "a burst goes every way");
        for (float t = .1f; t < RIPPLE_LIFE; t += STEP) fx.update(STEP);
        assertEquals(0, p.count());
    }

    @Test void aBurstAlsoStartsOneRingThatEndsAboutWithTheSparksAndIsClearedWithThem() {
        PickupSparksFx fx = new PickupSparksFx(2);
        assertEquals(0, fx.ringCount());
        fx.burst(X, Y);
        assertEquals(1, fx.ringCount());
        assertEquals(16, fx.ringRadius(0), 0f, "smaller than the item's halo");
        for (int i = 0; i < 13; i++) fx.update(STEP); // 0.22 s: past the ring's 0.2 s, the sparks (0.4 to 0.6 s) still fly
        assertEquals(0, fx.ringCount(), "the ring ends well before the sparks");
        assertTrue(fx.sparks().count() > 0);
        fx.burst(X, Y); fx.burst(X + 8, Y);
        assertEquals(2, fx.ringCount());
        fx.clear();
        assertEquals(0, fx.ringCount());
    }

    @Test void aRowOfPickupsNeverOutgrowsTheRingPool() {
        PickupSparksFx fx = new PickupSparksFx(4);
        for (int i = 0; i < PickupSparksFx.RING_CAPACITY + 3; i++) fx.burst(X + i, Y);
        assertEquals(PickupSparksFx.RING_CAPACITY, fx.ringCount(), "a full pool reuses the oldest ring");
    }

    @Test void theSameSeedGivesTheSameBurstAndNothingIsSpawnedOnItsOwn() {
        PickupSparksFx a = new PickupSparksFx(3), b = new PickupSparksFx(3);
        a.update(1); assertEquals(0, a.sparks().count());
        a.burst(X, Y); b.burst(X, Y);
        for (int i = 0; i < PickupSparksFx.SPARKS; i++) { assertEquals(a.sparks().x(i), b.sparks().x(i)); assertEquals(a.sparks().drawSize(i), b.sparks().drawSize(i)); }
    }

    @Test void aFullPoolDropsTheOldestAndClearEmptiesIt() {
        PickupSparksFx fx = new PickupSparksFx(1);
        for (int n = 0; n < PickupSparksFx.CAPACITY / PickupSparksFx.SPARKS + 3; n++) fx.burst(X, Y);
        assertEquals(PickupSparksFx.CAPACITY, fx.sparks().count());
        fx.clear();
        assertEquals(0, fx.sparks().count());
    }

    @Test void theBurstOfARunThroughARowOfCoinsFitsThePool() {
        PickupSparksFx fx = new PickupSparksFx(5);
        int peak = 0;
        for (int frame = 0; frame < 120; frame++) {
            if (frame % 9 == 0) fx.burst(X, Y); // a coin every 0.15 s
            fx.update(STEP);
            peak = Math.max(peak, fx.sparks().count());
        }
        assertTrue(peak * 1.25f <= PickupSparksFx.CAPACITY, "peak " + peak);
    }
}
