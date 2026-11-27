package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The spore burst of the false honey fungus (D152, #608, D63, D5): sparks and one ring out of the cap tuft. The sparks live exactly as
 * long as the hitbox, every quad (heads and streaks) and the ring stay inside the lethal rectangle (world Y grows downward: the
 * rectangle is y 296..320 for a mushroom standing on y 320), the same seed gives the same burst, a paused game freezes it and a retry
 * starts with none.
 */
class SporeCloudTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, WIDTH = 24, HEIGHT = 16, ZONE = 64, HERO_W = 11, HERO_H = 22, EDGE_EPS = 1e-3f;
    private static final float FALSE_X = 480;
    private static final int FRAMES_LIMIT = 600, MUSHROOMS = 40;
    private static final float[][] LIMES = {{0.373f, 0.627f, 0.165f}, {0.651f, 0.878f, 0.290f}, {0.902f, 0.949f, 0.416f}};

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();
    private final Rectangle hero = new Rectangle(FALSE_X, FLOOR - HERO_H, HERO_W, HERO_H);

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { Shaders.dispose(); SoftDot.dispose(); harness.restoreGlobals(); }

    private FalseHoneyFungus fungus(float x) {
        return new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), x, FLOOR - HEIGHT, WIDTH, HEIGHT, ZONE, ZONE, () -> hero);
    }

    private static void step(FalseHoneyFungus f) { f.update(STEP); f.render(new NoopBatch()); }

    /** Steps until the cloud is live (the swell ends). */
    private static void toBurst(FalseHoneyFungus f) {
        int frames = 0;
        while (!f.cloudLive() && frames++ < FRAMES_LIMIT) step(f);
        assertTrue(f.cloudLive(), "the swell must end in a burst");
    }

    @Test void theSparksAndTheRingComeWithTheLethalMomentAndEndWithTheHitbox() {
        FalseHoneyFungus f = fungus(FALSE_X);
        SporeCloud cloud = f.sporeCloud();
        KimeriaParticleSystem p = cloud.particles();
        assertEquals(0, p.count(), "none before the burst");
        assertEquals(0, cloud.ringCount());
        toBurst(f);
        assertEquals(SporeCloud.SPARK_COUNT, p.count(), "the whole burst is released at once");
        assertEquals(1, cloud.ringCount(), "one ring marks the lethal moment (D93)");
        int liveFrames = 0, ringFrames = 0;
        while (f.cloudLive()) {
            assertEquals(SporeCloud.SPARK_COUNT, p.count(), "every spark while the hitbox is live, frame " + liveFrames);
            for (int i = 0; i < p.count(); i++) assertTrue(p.alpha(i) >= 0.45f, "still seen, frame " + liveFrames);
            if (cloud.ringCount() > 0) ringFrames++;
            step(f);
            liveFrames++;
        }
        assertEquals(FungusTrap.State.SPENT, f.state());
        assertEquals(0, p.count(), "no spark outlives the hitbox");
        assertEquals(0, cloud.ringCount(), "and no ring");
        assertEquals(FalseHoneyFungus.CLOUD_SECONDS / STEP, liveFrames, 2);
        assertEquals(SporeCloud.RING_SECONDS / STEP, ringFrames, 2, "the ring is a quick one, over long before the hitbox");
    }

    @Test void everyDrawnSparkQuadStaysInsideTheLethalRectangleEveryFrame() {
        int[] drawn = new int[2]; // heads, streaks
        // Each position seeds its own burst; a map object may be taller or lower than the 16 px sprite, the square keeps its place.
        for (int n = 0; n < MUSHROOMS; n++) assertDrawnInside(fungus(FALSE_X + n * WIDTH), drawn);
        for (float height : new float[]{2, 4, 28, 32, 48})
            assertDrawnInside(new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), FALSE_X, FLOOR - height, WIDTH, height,
                ZONE, ZONE, () -> hero), drawn);
        assertTrue(drawn[0] > SporeCloud.SPARK_COUNT * MUSHROOMS, "the check really looked at heads over many frames: " + drawn[0]);
        assertTrue(drawn[1] > 0, "streaks were checked too");
    }

    private void assertDrawnInside(FalseHoneyFungus f, int[] drawn) {
        hero.x = f.position.x;
        Rectangle lethal = f.cloudBounds();
        toBurst(f);
        QuadRecorder quads = new QuadRecorder();
        while (f.cloudLive()) {
            quads.clear();
            f.sporeCloud().render(quads.batch); // the cloud alone: the mushroom's own sprite is not part of the burst
            for (float[] q : quads.quads) {
                for (int corner = 0; corner < 4; corner++) assertInside(lethal, QuadRecorder.cornerX(q, corner), QuadRecorder.cornerY(q, corner));
                drawn[q.length == QuadRecorder.PLAIN ? 0 : 1]++;
            }
            step(f);
        }
    }

    private static void assertInside(Rectangle lethal, float x, float y) {
        assertTrue(x >= lethal.x - EDGE_EPS && x <= lethal.x + lethal.width + EDGE_EPS, "x " + x + " in " + lethal);
        // top edge is the smaller Y; the bottom edge is the floor line
        assertTrue(y >= lethal.y - EDGE_EPS && y <= lethal.y + lethal.height + EDGE_EPS, "y " + y + " in " + lethal);
    }

    @Test void theRingIsCentredOnTheTuftAndFitsTheRectangle() {
        FalseHoneyFungus f = fungus(FALSE_X);
        Rectangle lethal = f.cloudBounds();
        toBurst(f);
        SporeCloud cloud = f.sporeCloud();
        // The ring's quad is centre +- radius, so with these three it is the lethal square itself (D5).
        assertEquals(lethal.x + lethal.width / 2f, cloud.ringX(), 0f);
        assertEquals(lethal.y + lethal.height / 2f, cloud.ringY(), 0f);
        assertEquals(FalseHoneyFungus.CLOUD_SIZE / 2f, cloud.ringRadius(), 0f);
    }

    @Test void theSparksFlyOutOfTheTuftAllRoundAndReachTheEdges() {
        FalseHoneyFungus f = fungus(FALSE_X);
        Rectangle lethal = f.cloudBounds();
        float tuftX = lethal.x + lethal.width / 2f, tuftY = lethal.y + lethal.height / 2f;
        toBurst(f);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        for (int i = 0; i < p.count(); i++) {
            assertEquals(KimeriaParticleSystem.KIND_SPARK, p.kind(i));
            assertEquals(tuftX, p.fromX(i), 1e-4f, "every spark starts at the tuft");
            assertLime(p.red(i), p.green(i), p.blue(i));
        }
        while (f.cloudLive() && p.life(0) > STEP + SporeCloud.LIFE_MARGIN) step(f);
        boolean[] quadrant = new boolean[4];
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < p.count(); i++) {
            quadrant[(p.x(i) < tuftX ? 0 : 1) + (p.y(i) < tuftY ? 0 : 2)] = true;
            left = Math.min(left, p.x(i)); right = Math.max(right, p.x(i));
            top = Math.min(top, p.y(i)); bottom = Math.max(bottom, p.y(i));
        }
        for (boolean q : quadrant) assertTrue(q, "radial: sparks in every quadrant, not an upward jet");
        float reach = 4f; // the drawn burst covers the lethal square to within this of each side
        assertTrue(left - lethal.x < reach && lethal.x + lethal.width - right < reach, "sides: " + left + " .. " + right);
        assertTrue(top - lethal.y < reach && lethal.y + lethal.height - bottom < reach, "top and floor: " + top + " .. " + bottom);
    }

    private static void assertLime(float r, float g, float b) {
        for (float[] lime : LIMES) if (lime[0] == r && lime[1] == g && lime[2] == b) return;
        fail("not one of the lethal limes: " + r + ", " + g + ", " + b);
    }

    @Test void theSparksFlyFastThenBrake() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        float[] early = new float[p.count()];
        while (p.life(0) > p.maxLife(0) * 0.75f) step(f); // a quarter of the life
        for (int i = 0; i < p.count(); i++) early[i] = p.x(i) - p.fromX(i);
        while (p.life(0) > STEP + SporeCloud.LIFE_MARGIN) step(f);
        for (int i = 0; i < p.count(); i++)
            assertTrue(Math.abs(early[i]) >= Math.abs(p.x(i) - p.fromX(i)) * 0.6f, "most of the way in the first quarter, spark " + i);
    }

    @Test void theSameSeedGivesTheSameBurstAndAnotherSeedAnother() {
        FalseHoneyFungus a = fungus(FALSE_X), b = fungus(FALSE_X), c = fungus(FALSE_X + WIDTH * 2);
        toBurst(a); toBurst(b); toBurst(c);
        KimeriaParticleSystem pa = a.sporeCloud().particles(), pb = b.sporeCloud().particles(), pc = c.sporeCloud().particles();
        boolean differs = false;
        for (int i = 0; i < pa.count(); i++) {
            assertEquals(pa.x(i), pb.x(i), 0f);
            assertEquals(pa.y(i), pb.y(i), 0f);
            assertEquals(pa.drawSize(i), pb.drawSize(i), 0f);
            if (pa.drawSize(i) != pc.drawSize(i)) differs = true;
        }
        assertTrue(differs, "a different mushroom gets a different burst");
    }

    @Test void aPausedGameFreezesTheBurst() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        for (int i = 0; i < 10; i++) step(f);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        float x = p.x(0), y = p.y(0), life = p.life(0);
        float stateTime = f.stateTime();
        for (int i = 0; i < 120; i++) f.render(batch); // paused: Level.update is not called, only drawing continues
        assertEquals(x, p.x(0), 0f);
        assertEquals(y, p.y(0), 0f);
        assertEquals(life, p.life(0), 0f);
        assertEquals(stateTime, f.stateTime(), 0f);
        assertEquals(1, f.sporeCloud().ringCount());
        assertTrue(f.cloudLive());
    }

    @Test void aRetryStartsWithoutAnyCloud() {
        Level level = new Level(harness.assets(), harness.game(), new Player(harness.assets(), harness.game(), 0, 0), "fungus-test", Rectangle::new);
        FalseHoneyFungus before = level.objects.stream().filter(FalseHoneyFungus.class::isInstance).map(FalseHoneyFungus.class::cast).findFirst().orElseThrow();
        before.sporeCloud().burst();
        assertTrue(before.sporeCloud().particles().count() > 0);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        FalseHoneyFungus after = level.objects.stream().filter(FalseHoneyFungus.class::isInstance).map(FalseHoneyFungus.class::cast).findFirst().orElseThrow();
        assertNotSame(before, after);
        assertEquals(FungusTrap.State.IDLE, after.state());
        assertEquals(0, after.sporeCloud().particles().count());
        assertEquals(0, after.sporeCloud().ringCount());
    }

    @Test void withoutAShaderTheSparksStillDrawAndNothingThrows() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        batch.resetCounts();
        f.render(batch);
        assertEquals(SporeCloud.SPARK_COUNT, batch.rotatedCount(), "one streak per spark early in its flight");
    }
}
