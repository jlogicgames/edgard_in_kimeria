package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.entity.enemy.Boss;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sizes the level's blood capacity (#285 step 3): the peak live drop count over a long stress run of many seeds at
 * several frame rates, with an empowered spray (48 drops, the larger one) every time the boss can be hit again.
 */
class KimeriaBloodSystemCapacityTest {
    private static final int LEVEL_CAPACITY = Level.BLOOD_CAPACITY;
    private static final int SEEDS = 40, SECONDS = 300;
    private static final float[] STEPS = {1f / 30f, 1f / 60f, 1f / 144f};
    /** After a landed hit the boss is in HURT then WARNING and cannot be hit; this is the shortest gap between two sprays. */
    private static final float MIN_HIT_GAP = Boss.HURT_DURATION + Boss.WARNING_DURATION;

    @Test void aSprayDiesBeforeTheBossCanBeHitAgain() {
        assertTrue(MIN_HIT_GAP > KimeriaBloodSystem.DURATION, "sprays never overlap, so one burst's size bounds the live count");
    }

    @Test void peakLiveCountStaysBelowCapacityWithHeadroom() {
        int peak = 0;
        for (float step : STEPS) {
            for (int seed = 1; seed <= SEEDS; seed++) {
                KimeriaBloodSystem blood = new KimeriaBloodSystem(LEVEL_CAPACITY, seed);
                float sinceHit = MIN_HIT_GAP;
                int frames = (int) (SECONDS / step);
                for (int f = 0; f < frames; f++) {
                    if (sinceHit >= MIN_HIT_GAP) { blood.spray(100, 90, 1, 110, true); sinceHit = 0; }
                    blood.update(step);
                    sinceHit += step;
                    peak = Math.max(peak, blood.count());
                }
            }
        }
        System.out.println("BLOOD PEAK LIVE DROPS: " + peak + " (capacity " + LEVEL_CAPACITY + ")");
        assertEquals(KimeriaBloodSystem.EMPOWERED_DROPLETS, peak, "one empowered spray is the most that is ever alive");
        assertTrue(peak * 2 <= LEVEL_CAPACITY, "peak " + peak + " leaves less than 100% headroom in " + LEVEL_CAPACITY);
    }
}
