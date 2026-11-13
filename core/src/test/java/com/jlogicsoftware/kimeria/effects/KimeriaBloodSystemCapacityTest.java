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

    /** The sword animation (7 frames of 0.1 s) must end before the next swing starts, and one swing kills at most one monster (the hit loop breaks at the first enemy). */
    private static final float SWING_SECONDS = .7f;
    /** A kill on the first frame of every swing, with the boss hit as often as it can be: the busiest realistic fight (#319). */
    @Test void bossSprayPlusSwordKillsStayBelowCapacityWithoutEvictingDrops() {
        int peak = 0;
        for (float step : STEPS) {
            for (int seed = 1; seed <= SEEDS; seed++) {
                KimeriaBloodSystem blood = new KimeriaBloodSystem(LEVEL_CAPACITY, seed);
                float sinceHit = MIN_HIT_GAP, sinceKill = SWING_SECONDS;
                int frames = (int) (60 / step);
                for (int f = 0; f < frames; f++) {
                    if (sinceHit >= MIN_HIT_GAP) { blood.spray(100, 90, 1, 110, true); sinceHit = 0; }
                    if (sinceKill >= SWING_SECONDS) { blood.sprayLight(100, 90, 1, 110); sinceKill = 0; }
                    blood.update(step);
                    sinceHit += step; sinceKill += step;
                    peak = Math.max(peak, blood.count());
                }
            }
        }
        System.out.println("BLOOD PEAK WITH MOB KILLS: " + peak + " (capacity " + LEVEL_CAPACITY + ")");
        assertTrue(peak <= KimeriaBloodSystem.EMPOWERED_DROPLETS + 2 * KimeriaBloodSystem.LIGHT_DROPLETS,
            "a drop lives .9 s, a swing .7 s: at most two light sprays are alive beside the boss spray");
        assertTrue(peak < LEVEL_CAPACITY, "the pool never fills, so no boss drop is evicted");
    }

    @Test void threeSimultaneousKillsAndAnEmpoweredBossSprayFit() {
        KimeriaBloodSystem blood = new KimeriaBloodSystem(LEVEL_CAPACITY, 5);
        blood.spray(100, 90, 1, 110, true);
        for (int i = 0; i < 3; i++) blood.sprayLight(100, 90, 1, 110);
        assertEquals(KimeriaBloodSystem.EMPOWERED_DROPLETS + 3 * KimeriaBloodSystem.LIGHT_DROPLETS, blood.count());
        assertTrue(blood.count() < LEVEL_CAPACITY);
    }
}
