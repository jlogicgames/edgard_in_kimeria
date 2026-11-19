package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link SnailFx} (#329, D106): the worst case the game can make is a few snails winding up at once at the
 * assist's longest warning, their gobs flying their whole range shedding drips and splatting together, while others die. The
 * peak over many seeds and frame rates must leave at least 25 % headroom, since a full pool silently drops the oldest particle.
 * The test also pins that the specks run into the mouth along the firing line.
 */
class SnailFxTest {
    private static final int SNAILS = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float STAGGER = .1f, TAIL = 1f, HEADROOM = 1.25f, DRIP_SPACING = 12, LINE_Y = 193, FLOOR = 200;

    private static int[] peaks(long seed, float fps) {
        SnailFx fx = new SnailFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        float flight = Snail.GOB_RANGE / Snail.GOB_SPEED;
        float[] speckDue = new float[SNAILS], swellDue = new float[SNAILS], dripDue = new float[SNAILS];
        boolean[] ended = new boolean[SNAILS];
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + flight + SNAILS * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < SNAILS; k++) {
                float local = t - k * STAGGER, mouth = 300 + k * 60;
                if (local < 0 || ended[k]) continue;
                if (local < warning) {
                    speckDue[k] += Snail.SPECKS_PER_SECOND * dt;
                    while (speckDue[k] >= 1) { speckDue[k] -= 1; fx.speck(mouth, mouth - Snail.GOB_RANGE, LINE_Y); }
                    swellDue[k] += Snail.SWELLS_PER_SECOND * dt;
                    while (swellDue[k] >= 1) { swellDue[k] -= 1; fx.swell(mouth, LINE_Y, local / warning); }
                } else if (local < warning + flight) {
                    dripDue[k] += Snail.GOB_SPEED * dt;
                    while (dripDue[k] >= DRIP_SPACING) { dripDue[k] -= DRIP_SPACING; fx.drip(mouth - (local - warning) * Snail.GOB_SPEED, LINE_Y, FLOOR); }
                } else {
                    ended[k] = true;
                    fx.splat(mouth - Snail.GOB_RANGE, LINE_Y, 1, false);
                    fx.steam(mouth - 40, LINE_Y, 1);       // another gob burnt by a fireball at the same moment
                    fx.poof(500 + k * 30, 194, FLOOR);   // and other snails dying
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
        System.out.println("SnailFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= SnailFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= SnailFx.HARD_CAPACITY, "hard peak " + hard);
    }

    @Test void theSpecksRunIntoTheMouthAlongTheLine() {
        SnailFx fx = new SnailFx(7);
        for (int i = 0; i < 40; i++) fx.speck(300, 124, LINE_Y);
        KimeriaParticleSystem hard = fx.hard();
        assertEquals(40, hard.count());
        for (int i = 0; i < hard.count(); i++) {
            assertTrue(hard.fromX(i) >= 124 && hard.fromX(i) <= 300, "born on the line");
            assertEquals(LINE_Y, hard.y(i), .01f, "at the gob's height");
        }
        fx.update(.24f);   // under the shortest life: every speck is still alive, most of the way in
        for (int i = 0; i < hard.count(); i++) {
            assertTrue(hard.x(i) >= hard.fromX(i) - .01f, "drawn toward the mouth, never away");
            assertTrue(300 - hard.x(i) <= (300 - hard.fromX(i)) * .05f + 1e-3f || hard.x(i) > hard.fromX(i), "it moves in");
            assertEquals(LINE_Y, hard.y(i), 1.01f, "along the line");
        }
    }
}
