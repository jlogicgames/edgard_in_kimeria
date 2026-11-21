package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pool sizes of {@link WolfFx} (#331, D115): the worst case the game can make is a few wolves winding up at once at the assist's
 * longest bite warning, lunging their whole reach and biting together, while others skid and die. The peak over many seeds and
 * frame rates must leave at least 25 % headroom, since a full pool silently drops the oldest particle. The test also pins that the
 * breath streaks gather on the end of the line and stay on the floor line.
 */
class WolfFxTest {
    private static final int WOLVES = 3, SEEDS = 40;
    private static final float[] RATES = {30, 60, 144};
    private static final float STAGGER = .1f, TAIL = 1f, HEADROOM = 1.25f, SKID_EVERY = .04f;
    /** A skid of a turn is at most 0.125 s (braking from 160 px/s at 1280 px/s^2): this many puffs. */
    private static final int SKIDS = 3;

    private static int[] peaks(long seed, float fps) {
        WolfFx fx = new WolfFx(seed);
        float dt = 1f / fps, warning = TrapWarnings.longestDuration(TrapWarnings.BITE_WARNING_SECONDS);
        float[] breathDue = new float[WOLVES], skidDue = new float[WOLVES];
        boolean[] bit = new boolean[WOLVES];
        int soft = 0, hard = 0;
        for (float t = 0; t < warning + Wolf.LUNGE_SECONDS + WOLVES * STAGGER + TAIL; t += dt) {
            for (int k = 0; k < WOLVES; k++) {
                float local = t - k * STAGGER, jaw = 400 - k * 90;
                if (local < 0 || bit[k]) continue;
                if (local < warning) {
                    if (local < SKIDS * SKID_EVERY) {   // the brake at the commit, and a skid of a turn just before it: a puff every 0.04 s
                        skidDue[k] += dt;
                        while (skidDue[k] >= SKID_EVERY) { skidDue[k] -= SKID_EVERY; fx.skid(jaw, 200, -1); }
                    }
                    breathDue[k] += Wolf.BREATH_PER_SECOND * dt;
                    while (breathDue[k] >= 1) { breathDue[k] -= 1; fx.breath(jaw, jaw - Wolf.LUNGE_REACH, 200); }
                } else {
                    bit[k] = true;
                    fx.bite(jaw - Wolf.LUNGE_REACH, 200);
                    fx.poof(700 + k * 40, 191, 200);   // other wolves dying at the same moment
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
        System.out.println("WolfFx peaks: soft " + soft + ", hard " + hard);
        assertTrue(soft * HEADROOM <= WolfFx.SOFT_CAPACITY, "soft peak " + soft);
        assertTrue(hard * HEADROOM <= WolfFx.HARD_CAPACITY, "hard peak " + hard);
    }

    @Test void theBreathRunsAlongTheFloorToTheEndOfTheLine() {
        WolfFx fx = new WolfFx(5);
        float jaw = 400, end = jaw - Wolf.LUNGE_REACH, floor = 200;
        for (int i = 0; i < 200; i++) fx.breath(jaw, end, floor);
        KimeriaParticleSystem hard = fx.hard();
        for (int i = 0; i < hard.count(); i++) {
            assertTrue(hard.x(i) <= jaw + .01f && hard.x(i) >= end - .01f, "born between the jaws and the line's end: " + hard.x(i));
            assertEquals(floor - 1, hard.y(i), .01f, "on the floor line");
        }
        for (int f = 0; f < 60; f++) fx.update(1 / 60f);
        assertEquals(0, hard.count(), "they die out within their life");
        for (int i = 0; i < 200; i++) fx.breath(jaw, end, floor);
        for (int f = 0; f < 60 && hard.count() > 0; f++) {
            fx.update(1 / 60f);
            if (f == 12) for (int i = 0; i < hard.count(); i++) assertTrue(hard.x(i) <= jaw - (jaw - end) * .3f, "they move toward the end");
        }
    }

    @Test void theDeathThrowsFurFlecksToTheFloorAndABitePutsTwoGlintsOnTheJaws() {
        WolfFx fx = new WolfFx(9);
        fx.poof(300, 191, 200);
        assertEquals(12, fx.soft().count(), "a poof of twelve puffs");
        assertEquals(4, fx.hard().count(), "and four fur flecks");
        fx.clear();
        assertEquals(0, fx.soft().count() + fx.hard().count(), "nothing carries over a room change");
        fx.bite(300, 200);
        assertTrue(fx.soft().count() >= 2 + 4, "two jaw glints and a puff of dust");
    }

    @Test void theChipsOfTheBodyAreFourPaletteColours() {
        assertEquals(4, WolfFx.WOLF_CHIPS.length);
        for (float[] c : WolfFx.WOLF_CHIPS) {
            assertEquals(3, c.length);
            for (float v : c) assertTrue(v >= 0 && v <= .25f, "a dark, dried umber like the other bodies: " + v);
        }
    }
}
