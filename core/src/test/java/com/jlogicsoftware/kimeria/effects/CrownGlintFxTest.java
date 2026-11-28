package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.FloatArray;
import com.jlogicsoftware.kimeria.world.Wind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jlogicsoftware.kimeria.effects.CrownGlintFx.*;
import static org.junit.jupiter.api.Assertions.*;

/** The glint rules on spots set by hand (#593, D163); the shipped rooms are {@code world.WindRoomsTest}. */
class CrownGlintFxTest {
    private static final float STEP = 1f / 60f, ROOM = 640, EPSILON = 1e-3f;
    private static final int SEED = 1234, SEEDS = 200, FRAMES_PER_GUSTS = 60 * 60;
    private static final Rectangle VIEW = new Rectangle(0, 0, 640, 360);

    /** The test patch of every hand-set spot: two rows, 4 px wide. */
    private static final float[] PATCH = {0, 0, 4, 1, 1, 2};

    /** One glint as it appeared; {@code fleck} when it takes a fleck colour. */
    private record Lit(int gust, int depth, float x, float y, float time, boolean fleck, float peak, int rgb, float life) { }

    private final Wind wind = new Wind(CrownGlintFx.FOREST_WIND);
    private final CrownGlintFx glints = new CrownGlintFx();

    private static CrownGlintFx.Spots[] spots() {
        return new CrownGlintFx.Spots[]{new CrownGlintFx.Spots(), new CrownGlintFx.Spots(), new CrownGlintFx.Spots()};
    }

    /** {@code count} spots per depth spread over the room, every depth at the same places. */
    private static CrownGlintFx.Spots[] row(int count) {
        CrownGlintFx.Spots[] spots = spots();
        for (int d = 0; d < DEPTHS; d++) for (int i = 0; i < count; i++) spots[d].add(20 + i * (ROOM - 40) / count, 40 + d * 10, PATCH);
        return spots;
    }

    private void start(int seed, CrownGlintFx.Spots[] spots, float[] columns, FloatArray boxes) {
        wind.reset(seed, 0, ROOM);
        glints.start(seed, spots, columns, boxes);
    }

    private void start(int seed, CrownGlintFx.Spots[] spots) { start(seed, spots, new float[0], new FloatArray()); }

    /** Runs the frames and returns every glint in the frame it appeared. */
    private List<Lit> run(int frames, float dt) {
        List<Lit> lit = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            wind.update(dt);
            int before = glints.spawned();
            glints.update(dt, wind, VIEW);
            int fresh = glints.spawned() - before;
            for (int d = DEPTHS - 1; d >= 0 && fresh > 0; d--) {
                KimeriaParticleSystem p = glints.particles(d);
                for (int i = p.count() - 1; i >= 0 && p.life(i) == p.maxLife(i); i--, fresh--) {
                    int rgb = Math.round(p.red(i) * 255) << 16 | Math.round(p.green(i) * 255) << 8 | Math.round(p.blue(i) * 255);
                    assertSame(PATCH, p.glintRuns(i), "the spot's whole patch");
                    boolean fleck = rgb == CROWN_FLECK || rgb == MID_FLECK;
                    lit.add(new Lit(wind.gust(), d, p.x(i), p.y(i), (f + 1) * dt, fleck, p.glintPeak(i), rgb, p.maxLife(i)));
                }
            }
            assertEquals(0, fresh, "every new glint found");
        }
        return lit;
    }

    @Test void everyGlintStandsOnASpotOfItsDepthWithTheCardsColourPeakAndLife() {
        CrownGlintFx.Spots[] spots = row(12);
        start(SEED, spots);
        List<Lit> lit = run(FRAMES_PER_GUSTS, STEP);
        assertTrue(lit.size() > 20);
        Set<Integer>[] colours = new Set[]{new HashSet<>(), new HashSet<>(), new HashSet<>()};
        for (Lit g : lit) {
            boolean listed = false;
            for (int i = 0; i < spots[g.depth].size(); i++) listed |= spots[g.depth].x(i) == g.x && spots[g.depth].y(i) == g.y;
            assertTrue(listed, g.toString());
            colours[g.depth].add(g.rgb);
            assertTrue(g.life >= 72f / 180 - EPSILON && g.life <= 96f / 180 + EPSILON, "life is the band's passing time: " + g.life);
            assertTrue(g.peak >= .5f + .5f * .35f - EPSILON && g.peak <= 1 + EPSILON, "peak is half of full plus half the strength: " + g.peak);
        }
        assertTrue(Set.of(CROWN_LEAF, CROWN_FLECK).containsAll(colours[CROWN]));
        assertTrue(Set.of(MID_LEAF, MID_FLECK).containsAll(colours[MID]));
        assertEquals(Set.of(GROVE_LEAF), colours[GROVE], "no fleck on the grove");
    }

    @Test void theShareAndTheFleckFollowTheCardOverManySeeds() {
        int[] lit = new int[DEPTHS], flecks = new int[DEPTHS];
        int chances = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            start(seed, row(6)); // six per depth never fill a pool
            for (Lit g : run(60 * 10, STEP)) {
                if (g.gust != 0) continue;
                lit[g.depth]++;
                if (g.fleck) flecks[g.depth]++;
            }
            chances += 6;
        }
        for (int d = 0; d < DEPTHS; d++) {
            float share = lit[d] / (float) chances;
            // 45 + 30 * strength: from 55.5 % at the room's ends to 75 % mid-room
            assertTrue(share > .555f && share < .75f, "depth " + d + " share " + share);
        }
        assertEquals(1f / 8, flecks[CROWN] / (float) lit[CROWN], .04f);
        assertEquals(1f / 8, flecks[MID] / (float) lit[MID], .04f);
        assertEquals(0, flecks[GROVE]);
    }

    @Test void glintsFollowTheFrontLeftToRightAndDeeperLayersRunBehind() {
        CrownGlintFx.Spots[] spots = row(16);
        float[] first = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        for (int seed = 0; seed < 20; seed++) {
            start(seed, spots);
            for (Lit g : run(60 * 9, STEP)) {
                int i = 0;
                while (glints.spotX(g.depth, i) != g.x) i++;
                float lag = new float[]{0, MID_LAG, GROVE_LAG}[g.depth] + glints.spotDelay(g.depth, i);
                assertTrue(glints.spotDelay(g.depth, i) >= 0 && glints.spotDelay(g.depth, i) <= MAX_SPOT_DELAY);
                float due = 1.5f + lag + g.x / 180f;
                assertEquals(due, g.time, STEP + EPSILON, "lights as its front arrives: " + g);
                first[g.depth] = Math.min(first[g.depth], g.time - g.x / 180f);
            }
        }
        assertTrue(first[CROWN] < first[MID] && first[MID] < first[GROVE]);
        assertEquals(MID_LAG, first[MID] - first[CROWN], MAX_SPOT_DELAY);
        assertEquals(GROVE_LAG, first[GROVE] - first[CROWN], MAX_SPOT_DELAY);
    }

    @Test void theCalmHoldsNoGlintAtAll() {
        start(SEED, row(16));
        int calmFrames = 0;
        for (int f = 0; f < FRAMES_PER_GUSTS; f++) {
            wind.update(STEP);
            glints.update(STEP, wind, VIEW);
            if (wind.blowing()) continue;
            for (int d = 0; d < DEPTHS; d++) assertEquals(0, glints.particles(d).count(), "calm at frame " + f);
            calmFrames++;
        }
        assertTrue(calmFrames > FRAMES_PER_GUSTS / 5, "calm frames " + calmFrames);
    }

    /** A view wider than the room shows leaves beyond its edges: they stay dark, so none comes before the gust or outlives it. */
    @Test void onlySpotsWithinTheRoomLightSoNothingComesEarlyOrOutlivesTheGust() {
        CrownGlintFx.Spots[] spots = spots();
        for (int d = 0; d < DEPTHS; d++) for (int x = -90; x <= ROOM + 90; x += 6) spots[d].add(x, 40, PATCH);
        Rectangle wide = new Rectangle(-100, 0, ROOM + 200, 360);
        int total = 0;
        for (int seed = 0; seed < 40; seed++) {
            start(seed, spots);
            while (wind.gust() < 1) {
                wind.update(STEP);
                int before = glints.spawned();
                glints.update(STEP, wind, wide);
                if (wind.gust() == 0 && wind.gustAge() <= STEP) assertEquals(before, glints.spawned(), "on the gust's first frame");
                for (int d = 0; d < DEPTHS; d++) {
                    KimeriaParticleSystem p = glints.particles(d);
                    for (int i = 0; i < p.count(); i++) assertTrue(p.x(i) >= 0 && p.x(i) <= ROOM, "outside the room at " + p.x(i));
                    if (!wind.blowing() && wind.gust() == 0) assertEquals(0, p.count(), "after the gust");
                }
            }
            total += glints.spawned();
        }
        assertTrue(total > 0);
    }

    /**
     * The calm is absolute at the worst place: a spot on the room's far edge, in the deepest layer, with a delay at the top of its
     * range, found up to a long frame after the front reached it, never leaves a glint alive once the gust stops blowing.
     */
    @Test void noGlintOutlivesTheSweepAtTheFarEdgeWithTheLatestDelayAndALongFrame() {
        CrownGlintFx.Spots[] spots = spots();
        spots[GROVE].add(ROOM, 40, PATCH);
        Rectangle wide = new Rectangle(-100, 0, ROOM + 200, 360);
        float[] steps = {1f / 144, 1f / 60, 1f / 30, 1f / 15, .25f};
        int[] lit = new int[steps.length];
        int seeds = 0;
        for (int seed = 0; seeds < 60; seed++) {
            start(seed, spots);
            if (glints.spotDelay(GROVE, 0) < MAX_SPOT_DELAY - .01f) continue;
            seeds++;
            for (int s = 0; s < steps.length; s++) {
                start(seed, spots);
                while (wind.gust() < 1) {
                    wind.update(steps[s]);
                    glints.update(steps[s], wind, wide);
                    if (wind.gust() == 0 && !wind.blowing())
                        assertEquals(0, glints.particles(GROVE).count(), "a glint in the calm, seed " + seed + " step " + steps[s]);
                }
                lit[s] += glints.spawned();
            }
        }
        for (int s = 0; steps[s] <= 1f / 15; s++) assertTrue(lit[s] > 0, "the edge spot still lights at ordinary frame steps: " + steps[s]);
    }

    @Test void aFullPoolSkipsTheNewGlintAndCutsNoneShort() {
        CrownGlintFx.Spots[] crowded = spots();
        // each depth's spots stand its lag further downwind of the next, so the three light at the same moments
        float[] left = {300, 300 - 180 * MID_LAG, 300 - 180 * GROVE_LAG};
        for (int d = 0; d < DEPTHS; d++) for (int i = 0; i < 300; i++) crowded[d].add(left[d] + i % 60, i, PATCH);
        start(SEED, crowded);
        Map<Float, Float>[] living = new Map[]{new HashMap<>(), new HashMap<>(), new HashMap<>()};
        int peak = 0;
        for (int f = 0; f < 60 * 12; f++) {
            wind.update(STEP);
            glints.update(STEP, wind, VIEW);
            int total = 0;
            for (int d = 0; d < DEPTHS; d++) {
                KimeriaParticleSystem p = glints.particles(d);
                assertTrue(p.count() <= capacity(d));
                total += p.count();
                Map<Float, Float> now = new HashMap<>();
                for (int i = 0; i < p.count(); i++) now.put(p.x(i) * 1000 + p.y(i), p.life(i));
                for (Map.Entry<Float, Float> old : living[d].entrySet())
                    if (old.getValue() > STEP + EPSILON) assertTrue(now.containsKey(old.getKey()), "a live glint was cut short");
                living[d] = now;
            }
            peak = Math.max(peak, total);
        }
        assertEquals(TOTAL_CAPACITY, peak);
        assertEquals(104, TOTAL_CAPACITY);
        assertEquals(64, capacity(CROWN));
        assertEquals(24, capacity(MID));
        assertEquals(16, capacity(GROVE));
        assertTrue(glints.skipped() > 0);
    }

    @Test void trapColumnsStayDarkAndAreWiderOnTheDriftingLayers() {
        float min = 300, max = 340;
        float[] drift = {0, 32, 40};
        boolean[] nearColumn = new boolean[DEPTHS];
        for (int seed = 0; seed < SEEDS; seed++) {
            CrownGlintFx.Spots[] spots = spots();
            for (int d = 0; d < DEPTHS; d++) for (int i = 0; i < 40; i++) spots[d].add(220 + i * 5, 50, PATCH);
            start(seed, spots, new float[]{min, max}, new FloatArray());
            for (Lit g : run(60 * 6, STEP)) {
                float right = g.x + 4; // the patch is 4 px wide
                assertTrue(right < min - drift[g.depth] || g.x > max + drift[g.depth], g.toString());
                nearColumn[g.depth] |= right >= min - drift[g.depth] - 12 && g.x <= max + drift[g.depth] + 12;
            }
        }
        for (boolean near : nearColumn) assertTrue(near, "the column is no wider than the card's");
    }

    @Test void onlyCrownGlintsKeepSixteenPixelsFromProtectedBoxes() {
        FloatArray box = new FloatArray(new float[]{300, 60, 16, 16});
        int deeperInside = 0, crownClose = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            CrownGlintFx.Spots[] spots = spots();
            for (int d = 0; d < DEPTHS; d++) for (int i = 0; i < 40; i++) spots[d].add(250 + i * 3, 20 + i * 2, PATCH);
            start(seed, spots, new float[0], box);
            for (Lit g : run(60 * 6, STEP)) {
                boolean inside = g.x + 4 > 284 && g.x < 332 && g.y + 2 > 44 && g.y < 92; // the patch is 4x2 px
                if (g.depth == CROWN) {
                    assertFalse(inside, g.toString());
                    crownClose++;
                } else if (inside) deeperInside++;
            }
        }
        assertTrue(deeperInside > 0 && crownClose > 0);
    }

    @Test void nothingStartsOffScreenAndASpotThatComesBehindTheFrontStaysDark() {
        CrownGlintFx.Spots[] spots = spots();
        for (int i = 0; i < 30; i++) spots[MID].add(2000 + i, 50, PATCH);
        start(SEED, spots);
        glints.setLayerBase(MID, -1900, 0); // the spots stand at world x 100 to 129
        for (int f = 0; f < 60 * 4; f++) { wind.update(STEP); glints.update(STEP, wind, new Rectangle(400, 0, 200, 360)); }
        assertEquals(0, glints.spawned(), "off screen");
        start(SEED, spots);
        glints.setLayerBase(MID, 0, 0); // ahead of the front at world x 2000, far outside the room
        while (!(wind.blowing() && wind.frontX(0) > 500)) { wind.update(STEP); glints.update(STEP, wind, VIEW); }
        glints.setLayerBase(MID, -1900, 0); // the camera brings them in behind the front
        for (int f = 0; f < 120; f++) { wind.update(STEP); glints.update(STEP, wind, VIEW); }
        assertEquals(0, glints.spawned(), "behind the front");
        for (int f = 0; f < 60 * 12; f++) { wind.update(STEP); glints.update(STEP, wind, VIEW); }
        assertTrue(glints.spawned() > 0, "the next gust crosses them");
    }

    @Test void aRetryShowsTheSameGustsAndTwoGustsDiffer() {
        CrownGlintFx.Spots[] spots = row(12);
        start(SEED, spots);
        List<Lit> first = run(60 * 30, STEP);
        start(SEED, spots);
        assertEquals(first, run(60 * 30, STEP));
        Set<String> gust0 = new HashSet<>(), gust1 = new HashSet<>();
        for (Lit g : first) if (g.gust < 2) (g.gust == 0 ? gust0 : gust1).add(g.depth + ":" + g.x);
        assertNotEquals(gust0, gust1);
    }

    @Test void theFrameRateDoesNotChangeWhichSpotsLight() {
        CrownGlintFx.Spots[] spots = row(6);
        List<Set<String>> seen = new ArrayList<>();
        for (int fps : new int[]{30, 60, 144}) {
            start(SEED, spots);
            Set<String> set = new HashSet<>();
            for (Lit g : run(fps * 28, 1f / fps)) set.add(g.gust + ":" + g.depth + ":" + g.x + ":" + g.fleck + ":" + g.rgb);
            seen.add(set);
        }
        assertFalse(seen.get(0).isEmpty());
        assertEquals(seen.get(1), seen.get(0));
        assertEquals(seen.get(1), seen.get(2));
    }

    @Test void aRoomWithoutSpotsAndAClearedEffectShowNothing() {
        start(SEED, spots());
        run(600, STEP);
        assertEquals(0, glints.spawned());
        start(SEED, row(12));
        run(60 * 4, STEP);
        glints.clear();
        for (int d = 0; d < DEPTHS; d++) { assertEquals(0, glints.particles(d).count()); assertEquals(0, glints.spotCount(d)); }
    }
}
