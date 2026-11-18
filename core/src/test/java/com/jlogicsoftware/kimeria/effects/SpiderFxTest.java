package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link SpiderFx} (#328, D102): the worst case the game can make is a few spiders winding up at once at the
 * assist's longest bite warning, leaping their whole reach and biting together, while others die. The peak over many seeds
 * and frame rates must leave at least 25 % headroom, since a full pool silently drops the oldest particle. The test also pins
 * that the silk glints gather on the landing spot and stay on the floor line.
 */
class SpiderFxTest {
    private static final int SPIDERS = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float SILK_PER_SECOND = 60, STAGGER = .1f, TAIL = 1f, HEADROOM = 1.25f, LEAP_LENGTH = 56;

    private static int[] peaks(long seed, float fps) {
        SpiderFx fx = new SpiderFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.BITE_WARNING_SECONDS);
        float[] silkDue = new float[SPIDERS], flown = new float[SPIDERS];
        boolean[] bit = new boolean[SPIDERS];
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + Spider.LEAP_SECONDS + SPIDERS * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < SPIDERS; k++) {
                float local = t - k * STAGGER, fang = 100 + k * 80;
                if (local < 0 || bit[k]) continue;
                if (local < warning) {
                    silkDue[k] += SILK_PER_SECOND * dt;
                    while (silkDue[k] >= 1) { silkDue[k] -= 1; fx.silk(fang, fang + LEAP_LENGTH, 200); }
                } else if (local < warning + Spider.LEAP_SECONDS) {
                    flown[k] += LEAP_LENGTH * dt / Spider.LEAP_SECONDS;
                    while (flown[k] >= Spider.LINE_STEP) { flown[k] -= Spider.LINE_STEP; fx.line(fang + local * 10, 190); }
                } else {
                    bit[k] = true;
                    fx.bite(fang + LEAP_LENGTH, 200);
                    fx.poof(500 + k * 30, 193, 200);   // other spiders dying at the same moment
                }
            }
            fx.update(dt);
            soft = Math.max(soft, fx.soft().count());
            hard = Math.max(hard, fx.hard().count());
        }
        return new int[]{soft, hard};
    }

    @Test void theWorstCaseLeavesHeadroomInBothPools() {
        int soft = 0, hard = 0;
        for (float fps : RATES) for (long seed = 1; seed <= SEEDS; seed++) {
            int[] p = peaks(seed, fps);
            soft = Math.max(soft, p[0]);
            hard = Math.max(hard, p[1]);
        }
        System.out.println("SpiderFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= SpiderFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= SpiderFx.HARD_CAPACITY, "hard peak " + hard);
    }

    @Test void theSilkRunsAlongTheFloorToTheLandingSpot() {
        SpiderFx fx = new SpiderFx(5);
        float fang = 100, spot = 148, floor = 200;
        for (int i = 0; i < 200; i++) fx.silk(fang, spot, floor);
        KimeriaParticleSystem hard = fx.hard();
        for (int i = 0; i < hard.count(); i++) {
            assertTrue(hard.x(i) >= fang - .01f && hard.x(i) <= spot + .01f, "born between the fangs and the spot: " + hard.x(i));
            assertEquals(floor - 1, hard.y(i), .01f, "on the floor line");
        }
        for (int f = 0; f < 60; f++) fx.update(1 / 60f);
        assertEquals(0, hard.count(), "they die out within their life");
        for (int i = 0; i < 200; i++) fx.silk(fang, spot, floor);
        for (int f = 0; f < 60 && hard.count() > 0; f++) {
            fx.update(1 / 60f);
            if (f == 12) for (int i = 0; i < hard.count(); i++) assertTrue(hard.x(i) >= fang + (spot - fang) * .3f, "they move toward the spot");
        }
    }
}
