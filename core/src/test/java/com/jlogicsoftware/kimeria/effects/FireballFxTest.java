package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The fireball's trail and hit particles ({@link FireballFx}, #397). World Y grows downward: rising is a smaller Y. */
class FireballFxTest {
    private static final float STEP = 1f / 60f, X = 200, Y = 150;
    /** The fireball's speed (Fireball.SPEED). */
    private static final float BALL_SPEED = 280;
    private static final int BALLS = 3, SHOT_GAP = 6, FLIGHT = 30, CYCLE = 90, SEEDS = 40, FRAMES = 900;
    /** The pool must be at least this many times the measured peak. */
    private static final float HEADROOM = 1.25f;
    /** The author's limit for a flame or spark square (px). */
    private static final float MAX_SQUARE = 3;
    /** FireballFx.FIZZLE_FLAMES. */
    private static final int FIZZLE_FLAMES = 4;
    /** Trail flames live at most 0.17 s, embers at least 0.2 s: anything on the hard pool after trail steps living longer than this is an ember. */
    private static final float EMBER_LIFE = .18f;
    private static final float WARM_RED = .6f, WARM_GAP = .3f;
    /** 18 frames (0.3 s): past the light ring's 0.26 s, short of the boss ring's 0.36 s. */
    private static final int LIGHT_RING_GONE_FRAMES = 18;
    /** About half the first version (its red flames lived up to 0.3 s, 84 px of flight). */
    private static final float SHORT_TRAIL_PX = 50;

    /** Warm only: a bright red channel that is the largest, well above blue (no grey, olive, green or blue). */
    private static void assertWarm(KimeriaParticleSystem p) {
        for (int i = 0; i < p.count(); i++) {
            assertTrue(p.red(i) >= p.green(i) && p.red(i) >= p.blue(i) && p.red(i) > WARM_RED && p.red(i) - p.blue(i) > WARM_GAP,
                "warm colour: " + p.red(i) + ", " + p.green(i) + ", " + p.blue(i));
        }
    }

    /** Every hard square is opaque, shrinks out and is at most 3 px; nothing is smoke or grit. */
    private static void assertHardSquares(KimeriaParticleSystem h) {
        for (int i = 0; i < h.count(); i++) {
            assertEquals(KimeriaParticleSystem.KIND_FLAME, h.kind(i));
            assertTrue(h.drawSize(i) <= MAX_SQUARE, "at most 3 px: " + h.drawSize(i));
        }
    }

    @Test void aTrailStepIsAWarmFlameSquareThatRisesFollowsTheBallAndShrinksOut() {
        FireballFx fx = new FireballFx(1);
        fx.trail(X, Y, 1);
        KimeriaParticleSystem h = fx.hard();
        assertTrue(h.count() >= 1);
        assertEquals(0, fx.soft().count(), "no halo per trail step: the ball carries its own");
        assertHardSquares(h);
        assertWarm(h);
        assertEquals(X, h.x(0), 0f);
        float startY = h.y(0), startSize = h.drawSize(0);
        fx.update(.02f);
        assertTrue(h.y(0) < startY, "rises: smaller Y is higher");
        assertTrue(h.x(0) > X, "drifts after a ball flying right");
        assertTrue(h.drawSize(0) < startSize, "shrinks");
        assertEquals(1f, h.alpha(0), 0f, "does not fade");
    }

    @Test void theTrailIsShortAndCoolsFromTheBallPaleDiesFirstRedLast() {
        FireballFx fx = new FireballFx(2);
        for (int i = 0; i < 300; i++) fx.trail(X, Y, 1);
        KimeriaParticleSystem h = fx.hard();
        float paleMax = 0, redMin = Float.MAX_VALUE, longest = 0;
        for (int i = 0; i < h.count(); i++) {
            if (h.maxLife(i) > EMBER_LIFE) continue;
            longest = Math.max(longest, h.maxLife(i));
            if (h.blue(i) > .5f) paleMax = Math.max(paleMax, h.maxLife(i));
            if (h.green(i) < .1f) redMin = Math.min(redMin, h.maxLife(i));
        }
        assertTrue(paleMax > 0 && redMin < Float.MAX_VALUE, "both ends of the ramp are shed");
        assertTrue(paleMax < redMin, "every pale flame dies before any red one");
        assertTrue(longest * BALL_SPEED < SHORT_TRAIL_PX, "a short trail: the longest flame outlives about " + longest * BALL_SPEED + " px of flight");
    }

    @Test void embersFallAsTheyCool() {
        FireballFx fx = new FireballFx(3);
        for (int i = 0; i < 60; i++) fx.trail(X, Y, -1);
        KimeriaParticleSystem h = fx.hard();
        float[] start = new float[h.count()];
        boolean[] ember = new boolean[h.count()];
        int embers = 0;
        for (int i = 0; i < h.count(); i++) { start[i] = h.y(i); if (ember[i] = h.maxLife(i) > EMBER_LIFE) embers++; }
        assertTrue(embers > 0, "some trail steps shed an ember");
        fx.update(.01f); // shorter than any life, so no particle dies and the indices hold
        assertEquals(start.length, h.count());
        for (int i = 0; i < h.count(); i++) if (ember[i]) assertTrue(h.y(i) > start[i], "falls: larger Y is lower");
    }

    @Test void aHitIsOneWarmHaloAndASplashOfFlameAlongTheSprayThatDiesOut() {
        FireballFx fx = new FireballFx(5);
        fx.hit(X, Y, -1);
        KimeriaParticleSystem s = fx.soft(), h = fx.hard();
        assertEquals(1, s.count(), "one halo, not a blob of particles");
        assertEquals(KimeriaParticleSystem.KIND_GLOW, s.kind(0));
        assertEquals(X, s.x(0), 0f);
        assertEquals(Y, s.y(0), 0f);
        assertWarm(s);
        assertHardSquares(h);
        assertWarm(h);
        fx.update(.1f);
        int left = 0, right = 0;
        for (int i = 0; i < h.count(); i++) { if (h.x(i) < X) left++; else if (h.x(i) > X) right++; }
        assertTrue(left > right, "a spray aimed left sends the fire left");
        for (int i = 0; i < 30; i++) fx.update(STEP);
        assertEquals(0, h.count(), "it dies out within 0.6 s: no debris stays");
    }

    @Test void theBossHitIsHeavierThanALightHit() {
        FireballFx light = new FireballFx(9), heavy = new FireballFx(9);
        light.hit(X, Y, 1);
        heavy.heavyHit(X, Y, 1);
        assertTrue(heavy.hard().count() > light.hard().count());
        assertTrue(heavy.soft().drawSize(0) > light.soft().drawSize(0), "a bigger halo");
        assertHardSquares(heavy.hard());
        assertWarm(heavy.hard());
    }

    @Test void aBurnOutIsAFewSmallFlamesThatRise() {
        FireballFx fx = new FireballFx(11);
        fx.fizzle(X, Y);
        KimeriaParticleSystem h = fx.hard();
        assertEquals(1, fx.soft().count(), "one small halo");
        assertEquals(FIZZLE_FLAMES, h.count(), "no sparks");
        assertHardSquares(h);
        assertWarm(h);
        float[] start = new float[h.count()];
        for (int i = 0; i < h.count(); i++) start[i] = h.y(i);
        fx.update(.05f);
        for (int i = 0; i < h.count(); i++) assertTrue(h.y(i) < start[i], "rises");
    }

    @Test void eachHitStartsOneRingTheBossBiggerABurnOutNone() {
        FireballFx fx = new FireballFx(15);
        fx.fizzle(X, Y);
        assertEquals(0, fx.ringCount(), "no ring on a burn-out");
        fx.hit(X, Y, 1);
        assertEquals(1, fx.ringCount());
        float light = fx.ringRadius(0);
        fx.heavyHit(X + 50, Y, 1);
        assertEquals(2, fx.ringCount(), "one ring per stop");
        assertTrue(fx.ringRadius(1) > light, "the boss ring is larger");
        for (int i = 0; i < 60; i++) fx.update(STEP);
        assertEquals(0, fx.ringCount(), "rings end within a second");
    }

    @Test void eachRingIsFreedWhenItsOwnLifeEnds() {
        FireballFx fx = new FireballFx(19);
        fx.heavyHit(X, Y, 1);
        float heavy = fx.ringRadius(0);
        fx.hit(X + 50, Y, 1);
        assertEquals(2, fx.ringCount());
        // The light ring (0.26 s) started after the boss ring (0.36 s) ends first; it must not stay counted behind it.
        for (int i = 0; i < LIGHT_RING_GONE_FRAMES; i++) fx.update(STEP);
        assertEquals(1, fx.ringCount(), "the light ring is gone, the boss ring still runs");
        assertEquals(heavy, fx.ringRadius(0), 0f, "the one left is the boss ring");
    }

    @Test void aNewRingNeverOverwritesALiveRingWhileASlotIsFree() {
        FireballFx fx = new FireballFx(23);
        fx.heavyHit(X, Y, 1); // the boss ring, 0.36 s, in the first slot
        for (int i = 1; i < FireballFx.RING_CAPACITY; i++) fx.hit(X + i, Y, 1); // three light rings, 0.26 s, fill the rest
        float heavy = fx.ringRadius(0);
        for (int i = 0; i < LIGHT_RING_GONE_FRAMES; i++) fx.update(STEP); // the light rings end, the boss ring runs on
        assertEquals(1, fx.ringCount());
        fx.hit(X, Y, 1); // the next ring after the first slot's turn has come round again
        assertEquals(2, fx.ringCount(), "it takes a free slot");
        assertEquals(heavy, fx.ringRadius(0), 0f, "the boss ring is still there, oldest");
    }

    @Test void aFullRingPoolReusesTheOldestSlot() {
        FireballFx fx = new FireballFx(17);
        for (int i = 0; i < FireballFx.RING_CAPACITY + 2; i++) { fx.hit(X + i, Y, 1); assertTrue(fx.ringCount() <= FireballFx.RING_CAPACITY); }
        assertEquals(FireballFx.RING_CAPACITY, fx.ringCount());
    }

    @Test void everythingDiesWithinASecondAndClearEmptiesBoth() {
        FireballFx fx = new FireballFx(13);
        fx.trail(X, Y, 1); fx.heavyHit(X, Y, 1);
        for (int i = 0; i < 60; i++) fx.update(STEP);
        assertEquals(0, fx.soft().count());
        assertEquals(0, fx.hard().count());
        fx.hit(X, Y, 1);
        fx.clear();
        assertEquals(0, fx.soft().count());
        assertEquals(0, fx.hard().count());
        assertEquals(0, fx.ringCount());
    }

    @Test void sameSeedGivesTheSameParticles() {
        assertArrayEquals(run(21), run(21), 0f);
        assertFalse(java.util.Arrays.equals(run(21), run(22)));
    }

    /**
     * Capacity, the worst case the game allows: the hero carries at most {@value #BALLS} charges
     * ({@code RangedStock.CAPACITY}), so {@value #BALLS} balls are shot {@value #SHOT_GAP} frames apart, each flies
     * {@value #FLIGHT} frames trailing and bursts on the boss (the heavy burst, the biggest); the cycle repeats every
     * {@value #CYCLE} frames over {@value #SEEDS} seeds. The pools must hold the peak with headroom, or a full pool would
     * thin the effect unseen.
     */
    @Test void thePoolsHoldThePeakWithHeadroom() {
        int softPeak = 0, hardPeak = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            FireballFx fx = new FireballFx(seed);
            float[] travelled = new float[BALLS];
            for (int frame = 0; frame < FRAMES; frame++) {
                int t = frame % CYCLE;
                for (int b = 0; b < BALLS; b++) {
                    int age = t - b * SHOT_GAP;
                    if (age < 0 || age > FLIGHT) continue;
                    if (age == FLIGHT) { fx.heavyHit(X, Y, 1); continue; }
                    travelled[b] += BALL_SPEED * STEP;
                    for (; travelled[b] >= FireballFx.TRAIL_SPACING_PX; travelled[b] -= FireballFx.TRAIL_SPACING_PX) fx.trail(X, Y, 1);
                }
                fx.update(STEP);
                softPeak = Math.max(softPeak, fx.soft().count());
                hardPeak = Math.max(hardPeak, fx.hard().count());
            }
        }
        System.out.printf("FireballFx peaks: soft %d of %d, hard %d of %d%n", softPeak, FireballFx.SOFT_CAPACITY, hardPeak, FireballFx.HARD_CAPACITY);
        assertTrue(softPeak * HEADROOM <= FireballFx.SOFT_CAPACITY, "soft peak " + softPeak);
        assertTrue(hardPeak * HEADROOM <= FireballFx.HARD_CAPACITY, "hard peak " + hardPeak);
    }

    private static float[] run(long seed) {
        FireballFx fx = new FireballFx(seed);
        for (int i = 0; i < 10; i++) fx.trail(X + i * 2, Y, 1);
        fx.hit(X + 30, Y, -1);
        fx.update(.02f);
        KimeriaParticleSystem f = fx.hard();
        float[] out = new float[f.count() * 2];
        for (int i = 0; i < f.count(); i++) { out[2 * i] = f.x(i); out[2 * i + 1] = f.y(i); }
        return out;
    }
}
