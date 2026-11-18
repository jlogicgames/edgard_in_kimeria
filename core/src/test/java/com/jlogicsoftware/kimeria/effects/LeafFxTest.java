package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Falling leaves (#473): a quiet ambience that never starts above a trap and is cleared with the room. World Y grows downward. */
class LeafFxTest {
    private static final float STEP = 1f / 60f;
    private static final Rectangle ROOM = new Rectangle(1000, 0, 800, 368);
    private static final int LEAVES = 10, SEEDS = 20, FRAMES = 60 * 120;

    @Test void aRoomStartsPartWayThroughItsLeavesAndNeverExceedsTheCount() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LeafFx fx = new LeafFx(seed);
            fx.start(ROOM, LEAVES);
            assertEquals(LEAVES, fx.particles().count());
            long total = 0;
            for (int f = 0; f < FRAMES; f++) { fx.update(STEP); total += fx.particles().count(); assertTrue(fx.particles().count() <= LEAVES); }
            double mean = (double) total / FRAMES;
            assertTrue(mean >= LEAVES - 1.5, "the room holds close to its count on average: " + mean);
        }
    }

    @Test void noLeafStartsInsideATrapColumn() {
        float[] bands = {1200, 1300, 1500, 1560};
        for (long seed = 0; seed < SEEDS; seed++) {
            LeafFx fx = new LeafFx(seed);
            fx.start(ROOM, LEAVES, bands);
            KimeriaParticleSystem p = fx.particles();
            for (int i = 0; i < p.count(); i++) assertTrue(p.kind(i) == KimeriaParticleSystem.KIND_LEAF);
            int spawned = 0;
            for (int f = 0; f < FRAMES; f++) {
                fx.update(STEP);
                for (int i = 0; i < p.count(); i++) {
                    if (p.life(i) <= p.maxLife(i) - 1.5f * STEP) continue; // only a leaf in its first frame
                    spawned++;
                    for (int b = 0; b < bands.length; b += 2)
                        assertFalse(p.fromX(i) >= bands[b] && p.fromX(i) <= bands[b + 1],
                            "a leaf appeared at x " + p.fromX(i) + " over the trap column " + bands[b] + " to " + bands[b + 1]);
                }
            }
            assertTrue(spawned > 50, "the scenario must spawn leaves: " + spawned);
        }
    }

    @Test void leavesStartInTheTopOfTheRoomAndFallSlowly() {
        LeafFx fx = new LeafFx(3);
        fx.start(ROOM, LEAVES);
        KimeriaParticleSystem p = fx.particles();
        for (int f = 0; f < FRAMES; f++) {
            fx.update(STEP);
            for (int i = 0; i < p.count(); i++) {
                assertTrue(p.y(i) >= ROOM.y && p.y(i) <= ROOM.y + ROOM.height, "inside the room: " + p.y(i));
                assertTrue(p.drawWidth(i) <= 6.01f && p.drawSize(i) <= 6.01f, "5 to 6 px on the long side");
            }
        }
    }

    @Test void noCountMeansNoLeavesAndClearEmptiesTheRoom() {
        LeafFx fx = new LeafFx(1);
        fx.start(ROOM, 0);
        for (int f = 0; f < 600; f++) fx.update(STEP);
        assertEquals(0, fx.particles().count());
        fx.start(ROOM, LEAVES);
        assertEquals(LEAVES, fx.particles().count());
        fx.clear();
        assertEquals(0, fx.particles().count());
        for (int f = 0; f < 600; f++) fx.update(STEP);
        assertEquals(0, fx.particles().count(), "a cleared effect stays empty until the next start");
    }

    @Test void theSameSeedGivesTheSameLeaves() {
        LeafFx a = new LeafFx(9), b = new LeafFx(9);
        a.start(ROOM, LEAVES); b.start(ROOM, LEAVES);
        for (int f = 0; f < 600; f++) { a.update(STEP); b.update(STEP); }
        for (int i = 0; i < a.particles().count(); i++) assertEquals(a.particles().x(i), b.particles().x(i), 0f);
    }

    @Test void aRoomFullyCoveredByTrapBandsSpawnsNothing() {
        LeafFx fx = new LeafFx(5);
        fx.start(ROOM, LEAVES, new float[]{ROOM.x - 10, ROOM.x + ROOM.width + 10});
        assertEquals(0, fx.particles().count(), "no leaf is seeded over a trap");
        for (int f = 0; f < FRAMES; f++) { fx.update(STEP); assertEquals(0, fx.particles().count()); }
    }
}
