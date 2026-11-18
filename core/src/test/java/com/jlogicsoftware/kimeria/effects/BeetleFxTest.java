package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link BeetleFx} (#327): the worst case the game can make is a few beetles winding up at once at the
 * assist's longest warning and bursting together, while others die under fireballs, and then three bodies crumbling and
 * three burning at once (D101). The peak over many seeds and frame
 * rates must leave at least 25 % headroom, since a full pool silently drops the oldest particle.
 */
class BeetleFxTest {
    private static final int BEETLES = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float SPECKS_START = Beetle.SPECKS_START, SPECKS_END = Beetle.SPECKS_END, STAGGER = .1f, TAIL = 1f, HEADROOM = 1.25f;
    private static final float BURN_FLAMES_PER_SECOND = 70;

    private static int[] peaks(long seed, float fps) {
        BeetleFx fx = new BeetleFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        float[] due = new float[BEETLES];
        boolean[] burst = new boolean[BEETLES];
        boolean bodiesDone = false;
        float burning = 0, flameDue = 0;
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + BEETLES * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < BEETLES; k++) {
                float local = t - k * STAGGER;
                if (local < 0 || burst[k]) continue;
                if (local >= warning) {
                    burst[k] = true;
                    fx.burst(100 + k * 30, 200);
                    fx.poof(400 + k * 30, 192, 200);   // other beetles dying under fireballs at the same moment
                    continue;
                }
                due[k] += (SPECKS_START + (SPECKS_END - SPECKS_START) * local / warning) * dt;
                while (due[k] >= 1) { due[k] -= 1; fx.speck(100 + k * 30, 200); }
            }
            if (!bodiesDone && burst[BEETLES - 1]) {  // the dead beetles' bodies: three crumble, three start to burn
                bodiesDone = true;
                for (int k = 0; k < BEETLES; k++) { fx.crumble(500 + k * 30, 200); fx.burnGlow(600 + k * 30, 200, Beetle.BURN_SECONDS); }
                burning = Beetle.BURN_SECONDS;
            }
            if (burning > 0) {
                burning -= dt;
                flameDue += BURN_FLAMES_PER_SECOND * dt;
                while (flameDue >= 1) { flameDue -= 1; for (int k = 0; k < BEETLES; k++) fx.burnFlame(600 + k * 30, 200); }
                if (burning <= 0) for (int k = 0; k < BEETLES; k++) fx.burnOut(600 + k * 30, 200);
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
        System.out.println("BeetleFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= BeetleFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= BeetleFx.HARD_CAPACITY, "hard peak " + hard);
    }
}
