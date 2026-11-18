package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link RemainsFx} (D101, #478): the worst case the game can make is several bodies finished at once, three
 * crumbled and three burnt together, then as many again 0.2 s later (a sword swing, a stomp and fireballs through a room of
 * bodies), beetles' and spiders' alike. The peak over many seeds and frame rates must leave at least 25 % headroom, since a
 * full pool silently drops the oldest particle.
 */
class RemainsFxTest {
    private static final int BODIES = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float SECOND_WAVE = .2f, TAIL = 1f, HEADROOM = 1.25f, BURN_FLAMES_PER_SECOND = 70;
    private static final float BURN_SECONDS = Math.max(Beetle.BURN_SECONDS, Spider.BURN_SECONDS);

    private static int[] peaks(long seed, float fps) {
        RemainsFx fx = new RemainsFx(seed);
        float dt = 1f / fps, flameDue = 0;
        float[] burning = new float[2];
        boolean[] started = new boolean[2];
        int soft = 0, hard = 0;
        for (float t = 0; t < SECOND_WAVE + BURN_SECONDS + TAIL; t += dt) {
            for (int wave = 0; wave < 2; wave++) {
                if (started[wave] || t < wave * SECOND_WAVE) continue;
                started[wave] = true;
                for (int k = 0; k < BODIES; k++) {
                    fx.crumble(100 + k * 30 + wave * 200, 200, (k & 1) == 0 ? BeetleFx.SHELL_CHIPS : SpiderFx.SPIDER_CHIPS);
                    fx.burnGlow(600 + k * 30 + wave * 200, 200, BURN_SECONDS);
                }
                burning[wave] = BURN_SECONDS;
            }
            flameDue += BURN_FLAMES_PER_SECOND * dt;
            int due = (int) flameDue;
            flameDue -= due;
            for (int wave = 0; wave < 2; wave++) {
                if (burning[wave] <= 0) continue;
                for (int f = 0; f < due; f++) for (int k = 0; k < BODIES; k++) fx.burnFlame(600 + k * 30 + wave * 200, 200);
                burning[wave] -= dt;
                if (burning[wave] <= 0) for (int k = 0; k < BODIES; k++) fx.burnOut(600 + k * 30 + wave * 200, 200);
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
        System.out.println("RemainsFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= RemainsFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= RemainsFx.HARD_CAPACITY, "hard peak " + hard);
    }
}
