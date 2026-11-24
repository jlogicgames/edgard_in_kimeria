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

    /**
     * D120 (#236): the leaves fly just in front of the play layer, so they take the near palette's dark real leaf greens, not grey
     * or teal ghosts. Every colour a leaf is given lies in OKLab L 0.30 to 0.36, C 0.024 to 0.036, hue 130 to 150; the band was
     * measured over the composited sky, far, grove and mid layers of every leaf room (mean distance 0.090 to 0.172, decor body
     * 0.08 to 0.20, ART_DIRECTION.md "Falling leaves").
     */
    @Test void leafColoursAreDarkRealLeafGreens() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            LeafFx fx = new LeafFx(seed);
            fx.start(ROOM, LEAVES);
            KimeriaParticleSystem p = fx.particles();
            for (int i = 0; i < p.count(); i++) {
                float r = p.red(i), g = p.green(i), b = p.blue(i);
                double[] lab = oklab(r, g, b);
                double chroma = Math.hypot(lab[1], lab[2]), hue = (Math.toDegrees(Math.atan2(lab[2], lab[1])) + 360) % 360;
                String at = String.format("leaf (%.3f, %.3f, %.3f): L %.3f C %.4f hue %.1f", r, g, b, lab[0], chroma, hue);
                assertTrue(lab[0] >= .30 && lab[0] <= .36, at);
                assertTrue(chroma >= .024 && chroma <= .036, at);
                assertTrue(hue >= 130 && hue <= 150, at);
                seen.add(Math.round(r * 1000) * 1_000_000 + Math.round(g * 1000) * 1000 + Math.round(b * 1000));
            }
        }
        assertEquals(3, seen.size(), "three leaf greens are in use");
    }

    private static double[] oklab(float red, float green, float blue) {
        double r = linear(red), g = linear(green), b = linear(blue);
        double l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b);
        double m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b);
        double s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b);
        return new double[]{.2104542553 * l + .7936177850 * m - .0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + .4505937099 * s, .0259040371 * l + .7827717662 * m - .8086757660 * s};
    }

    private static double linear(double c) { return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
}
