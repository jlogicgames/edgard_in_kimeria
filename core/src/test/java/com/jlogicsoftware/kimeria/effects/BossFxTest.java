package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.BossAttacks;
import com.jlogicsoftware.kimeria.entity.objects.LanceRoot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The forest boss's particles (#625, D157): the pools hold the measured peaks, and the blow stays inside the dome that kills. */
class BossFxTest {
    private static final float RADIUS_X = BeetleFx.BLAST_WIDTH / 2, RADIUS_Y = BeetleFx.BLAST_HEIGHT;
    private static final float FLOOR = 304, CENTRE = 2860, MIDDLE = FLOOR - 14;
    private static final int SEEDS = 40;

    /** The blow's specks at their quickening rate through {@code warning} seconds, then the burst; returns the peaks. */
    private static int[] blow(BossFx fx, int fps, float warning) {
        float dt = 1f / fps, due = 0;
        int soft = 0, hard = 0;
        for (float clock = 0; clock < warning; clock += dt) {
            due += (50 + 100 * clock / warning) * dt;
            while (due >= 1) { due -= 1; fx.speck(CENTRE, MIDDLE, FLOOR); }
            hard = Math.max(hard, fx.hard().count());
            fx.update(dt);
        }
        fx.burst(CENTRE, FLOOR);
        return new int[]{Math.max(soft, fx.soft().count()), Math.max(hard, fx.hard().count())};
    }

    /** Five roots hopping soil through the warning, then shedding their chips; returns the peak. */
    private static int roots(BossFx fx, int fps, float warning) {
        float dt = 1f / fps, due = 0;
        int hard = 0;
        for (float clock = 0; clock < warning + .4f; clock += dt) {
            due += dt;
            while (clock < warning && due >= LanceRoot.HOP_PERIOD) {
                due -= LanceRoot.HOP_PERIOD;
                for (int i = 0; i < BossAttacks.ROOTS; i++) fx.soil(CENTRE + i * BossAttacks.ROOT_SPACING, FLOOR - 2);
            }
            hard = Math.max(hard, fx.hard().count());
            fx.update(dt);
        }
        for (int i = 0; i < BossAttacks.ROOTS; i++)
            fx.bark(CENTRE + i * BossAttacks.ROOT_SPACING, FLOOR, BossAttacks.ROOT_HEIGHT, 8, LanceRoot.CHIPS);
        return Math.max(hard, fx.hard().count());
    }

    @Test void thePoolsHoldTheMeasuredPeaks() {
        int soft = 0, hard = 0;
        for (long seed = 1; seed <= SEEDS; seed++)
            for (int fps : new int[]{30, 60, 144})
                for (float assist : new float[]{1f, 1.5f}) {
                    int[] peaks = blow(new BossFx(seed), fps, TrapWarnings.BOSS_BLOW_WARNING_SECONDS * assist);
                    soft = Math.max(soft, peaks[0]);
                    hard = Math.max(hard, peaks[1]);
                    hard = Math.max(hard, roots(new BossFx(seed), fps, TrapWarnings.BOSS_ROOTS_WARNING_SECONDS * assist));
                }
        System.out.println("BossFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * 1.25f <= BossFx.SOFT_CAPACITY, "soft " + soft);
        assertTrue(hard * 1.25f <= BossFx.HARD_CAPACITY, "hard " + hard);
    }

    @Test void everySpeckIsBornOnTheDomeRimAndDrawnIn() {
        BossFx fx = new BossFx(7);
        for (int i = 0; i < 100; i++) {
            fx.speck(CENTRE, MIDDLE, FLOOR);
            int last = fx.hard().count() - 1;
            float x = (fx.hard().x(last) - CENTRE) / RADIUS_X, y = (fx.hard().y(last) - FLOOR) / RADIUS_Y;
            assertEquals(1, x * x + y * y, .001f);
            assertTrue(fx.hard().y(last) <= FLOOR, "over the floor line");
        }
    }

    @Test void everyParticleOfTheBurstStaysInsideTheDomeAndIsYellowGreen() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            BossFx fx = new BossFx(seed);
            fx.burst(CENTRE, FLOOR);
            for (int frame = 0; frame < 60; frame++) {
                for (int i = 0; i < fx.hard().count(); i++) {
                    float x = (fx.hard().x(i) - CENTRE) / RADIUS_X, y = (fx.hard().y(i) - FLOOR) / RADIUS_Y;
                    assertTrue(x * x + y * y <= 1.001f, "seed " + seed + " frame " + frame + ": " + fx.hard().x(i) + " " + fx.hard().y(i));
                    assertTrue(fx.hard().y(i) <= FLOOR);
                    assertEquals(BossFx.BLOW_R, fx.hard().red(i), .001f);
                    assertEquals(BossFx.BLOW_G, fx.hard().green(i), .001f);
                    assertEquals(BossFx.BLOW_B, fx.hard().blue(i), .001f);
                }
                fx.update(1f / 60f);
            }
            assertEquals(0, fx.hard().count(), "nothing outlives a second");
        }
    }

    @Test void clearEmptiesBothPools() {
        BossFx fx = new BossFx(3);
        fx.burst(CENTRE, FLOOR);
        fx.clear();
        assertEquals(0, fx.soft().count() + fx.hard().count());
    }
}
