package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The puffball's spores as particles (#558, D5, D46): every quad of every particle stays inside the lethal rectangle on every frame, the
 * spores fill it from the first lethal frame, they live exactly as long as the hitbox (the leak of the warning excepted, and it is cleared
 * with the cloud), the pools never overflow at any frame rate, the same puffball gets the same cloud and a rebuild starts with none.
 * World Y grows downward: the rectangle stands on the floor line, so its bottom edge is the object's bottom edge minus the inset.
 */
class PuffballFxTest {
    private static final float STEP = HeadlessLevelHarness.STEP, EDGE_EPS = 1e-3f;
    private static final float X = 480, Y = 304, SIZE = 16, SECONDS = 20f;
    private static final float[] FRAME_RATES = {30f, 60f, 144f};
    private static final int SEEDS = 40;
    /** The pools must keep at least this many free slots at the highest live count measured (a full pool silently drops the oldest particle). */
    private static final int HEADROOM = 8;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { Shaders.dispose(); SoftDot.dispose(); harness.restoreGlobals(); }

    private Puffball puffball(float x, float phase) {
        return new Puffball(harness.assets(), x, Y, SIZE, SIZE, harness.game().trapWarnings(), Puffball.DEFAULT_PERIOD_SECONDS, phase, Puffball.DEFAULT_CLOUD_SECONDS);
    }

    private static void assertInside(KimeriaParticleSystem p, Rectangle lethal, String what) {
        for (int i = 0; i < p.count(); i++) {
            float half = p.drawSize(i) / 2f;
            assertTrue(p.x(i) - half >= lethal.x - EDGE_EPS, what + " left edge, particle " + i);
            assertTrue(p.x(i) + half <= lethal.x + lethal.width + EDGE_EPS, what + " right edge, particle " + i);
            assertTrue(p.y(i) - half >= lethal.y - EDGE_EPS, what + " top edge (smaller Y is higher), particle " + i);
            assertTrue(p.y(i) + half <= lethal.y + lethal.height + EDGE_EPS, what + " bottom edge, particle " + i);
        }
    }

    @Test void everyParticleQuadStaysInsideTheLethalRectangleEveryFrame() {
        for (float rate : FRAME_RATES) {
            Puffball p = puffball(X, 0);
            Rectangle lethal = p.cloudBounds();
            float dt = 1f / rate;
            int checked = 0;
            for (int i = 0, n = Math.round(SECONDS * rate); i < n; i++) {
                p.update(dt);
                assertInside(p.fx().spores(), lethal, "spore at " + rate + " fps");
                assertInside(p.fx().haze(), lethal, "haze at " + rate + " fps");
                checked += p.fx().count();
            }
            assertTrue(checked > 1000, "the check really looked at particles: " + checked);
        }
    }

    @Test void theWholeRectangleIsSpeckledOnTheFirstLethalFrameAndNothingOutlivesTheHitbox() {
        Puffball p = puffball(X, 0);
        Rectangle lethal = p.cloudBounds();
        PuffballFx fx = p.fx();
        assertEquals(0, fx.count(), "calm at the start: nothing drawn");
        int frames = 0;
        while (p.currentPhase() != Puffball.Phase.CLOUD && frames++ < 1000) p.update(STEP);
        assertEquals(Puffball.Phase.CLOUD, p.currentPhase());
        assertTrue(fx.spores().count() >= PuffballFx.SPORE_BURST, "the release is one burst: " + fx.spores().count());
        boolean left = false, right = false, top = false, bottom = false; // a spore in each quarter of the rectangle
        for (int i = 0; i < fx.spores().count(); i++) {
            left |= fx.spores().x(i) < lethal.x + lethal.width / 2f;
            right |= fx.spores().x(i) >= lethal.x + lethal.width / 2f;
            top |= fx.spores().y(i) < lethal.y + lethal.height / 2f;
            bottom |= fx.spores().y(i) >= lethal.y + lethal.height / 2f;
        }
        assertTrue(left && right && top && bottom, "spores over the whole rectangle, not only at the cap");
        for (int i = 0; i < fx.spores().count(); i++) assertEquals(KimeriaParticleSystem.KIND_GRIT, fx.spores().kind(i), "crisp spores");
        boolean flash = false;
        for (int i = 0; i < fx.haze().count(); i++) flash |= fx.haze().kind(i) == KimeriaParticleSystem.KIND_GLOW;
        assertTrue(flash, "the release flash");
        int liveFrames = 0;
        while (p.currentPhase() == Puffball.Phase.CLOUD) {
            assertTrue(fx.count() > 0, "particles while the hitbox is live, frame " + liveFrames);
            p.update(STEP);
            liveFrames++;
        }
        assertEquals(0, fx.count(), "no particle outlives the hitbox");
        assertEquals(Puffball.DEFAULT_CLOUD_SECONDS / STEP, liveFrames, 2);
    }

    @Test void theWarningLeaksSporesOnlyInTheSecondHalfOfTheSwell() {
        Puffball p = puffball(X, 0);
        int frames = 0, first = 0, second = 0;
        while (p.currentPhase() != Puffball.Phase.SWELL && frames++ < 1000) {
            p.update(STEP);
            assertEquals(0, p.fx().count(), "calm: nothing");
        }
        while (p.currentPhase() == Puffball.Phase.SWELL) {
            p.update(STEP);
            if (p.currentPhase() != Puffball.Phase.SWELL) break;
            if (p.swellElapsed() < p.swellSeconds() / 2f) first += p.fx().count(); else second += p.fx().count();
        }
        assertEquals(0, first, "the first half of the swell only swells");
        assertTrue(second > 0, "specks leak from the chimney in the second half");
    }

    @Test void aRoomThatOpensInsideACloudShowsItAtOnce() {
        Puffball p = puffball(X, Puffball.DEFAULT_PERIOD_SECONDS - Puffball.DEFAULT_CLOUD_SECONDS + 0.4f);
        assertEquals(Puffball.Phase.CLOUD, p.currentPhase());
        assertTrue(p.fx().count() >= PuffballFx.SPORE_BURST, "the lethal cloud is never invisible");
    }

    @Test void thePoolsNeverOverflowAtAnyFrameRate() {
        int sporePeak = 0, hazePeak = 0;
        for (float rate : FRAME_RATES) {
            float dt = 1f / rate;
            for (int seed = 0; seed < SEEDS; seed++) {
                Puffball p = puffball(X + seed * 7.3f, seed * 0.13f);
                for (int i = 0, n = Math.round(SECONDS * rate); i < n; i++) {
                    p.update(dt);
                    sporePeak = Math.max(sporePeak, p.fx().spores().count());
                    hazePeak = Math.max(hazePeak, p.fx().haze().count());
                }
            }
        }
        System.out.printf("PUFFBALL PEAK spores %d of %d, haze %d of %d%n", sporePeak, PuffballFx.SPORE_CAPACITY, hazePeak, PuffballFx.HAZE_CAPACITY);
        assertTrue(sporePeak > PuffballFx.SPORE_BURST, "the measurement saw a release: " + sporePeak);
        assertTrue(sporePeak <= PuffballFx.SPORE_CAPACITY - HEADROOM, "spore pool " + PuffballFx.SPORE_CAPACITY + " peaked at " + sporePeak);
        assertTrue(hazePeak <= PuffballFx.HAZE_CAPACITY - HEADROOM, "haze pool " + PuffballFx.HAZE_CAPACITY + " peaked at " + hazePeak);
    }

    @Test void theSamePuffballGetsTheSameCloudAndAnotherOneAnother() {
        Puffball a = puffball(X, 2.9f), b = puffball(X, 2.9f), c = puffball(X + SIZE * 2, 2.9f);
        for (int i = 0; i < 30; i++) { a.update(STEP); b.update(STEP); c.update(STEP); }
        KimeriaParticleSystem pa = a.fx().spores(), pb = b.fx().spores(), pc = c.fx().spores();
        assertTrue(pa.count() > 0);
        assertEquals(pa.count(), pb.count());
        boolean differs = false;
        for (int i = 0; i < pa.count(); i++) {
            assertEquals(pa.x(i), pb.x(i), 0f);
            assertEquals(pa.y(i), pb.y(i), 0f);
            if (pa.life(i) != pc.life(Math.min(i, pc.count() - 1))) differs = true;
        }
        assertTrue(differs, "a different puffball gets a different cloud");
    }

    @Test void aPausedGameFreezesTheCloud() {
        Puffball p = puffball(X, 3.2f);
        for (int i = 0; i < 10; i++) p.update(STEP);
        KimeriaParticleSystem sp = p.fx().spores();
        float x = sp.x(0), y = sp.y(0), life = sp.life(0);
        for (int i = 0; i < 120; i++) p.render(batch); // paused: only drawing continues
        assertEquals(x, sp.x(0), 0f);
        assertEquals(y, sp.y(0), 0f);
        assertEquals(life, sp.life(0), 0f);
    }

    @Test void theSporesAreLimeAndTheHaloIsAdditive() {
        Puffball p = puffball(X, 2.9f);
        for (int i = 0; i < 20; i++) p.update(STEP);
        KimeriaParticleSystem sp = p.fx().spores();
        for (int i = 0; i < sp.count(); i++) {
            assertTrue(sp.green(i) > sp.red(i) && sp.green(i) > sp.blue(i), "lime: green leads, spore " + i);
            assertTrue(sp.green(i) >= 0.6f, "the lethal lime, not a golden or a grey spore " + i);
        }
    }
}
