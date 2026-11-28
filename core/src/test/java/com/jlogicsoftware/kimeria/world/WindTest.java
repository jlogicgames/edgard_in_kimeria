package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.effects.CrownGlintFx;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shared gust model (#593, D163) and how a level drives it. */
class WindTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final Wind.Profile FOREST = CrownGlintFx.FOREST_WIND;
    private static final float ROOM = 640, LONGEST_FOREST_ROOM = 1120, TOLERANCE_PX = .05f, EPSILON = 1e-4f;
    private static final int SEED = "forest-1-mob".hashCode(), SEEDS = 50, GUSTS = 12;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static Wind wind(int seed, float left, float right) {
        Wind wind = new Wind(FOREST);
        wind.reset(seed, left, right);
        return wind;
    }

    private static void run(Wind wind, float seconds, int fps) {
        for (int i = 0, n = Math.round(seconds * fps); i < n; i++) wind.update(1f / fps);
    }

    /** Updates in 60 fps steps until gust {@code gust} has just started. */
    private static void until(Wind wind, int gust) {
        while (wind.gust() < gust) wind.update(STEP);
    }

    private static void assertSame(Wind a, Wind b, float tolerance) {
        assertEquals(a.gust(), b.gust());
        assertEquals(a.blowing(), b.blowing());
        assertEquals(a.width(), b.width());
        assertEquals(a.period(), b.period());
        assertEquals(a.frontX(0), b.frontX(0), tolerance);
        assertEquals(a.strength(CrownGlintFx.MID_LAG), b.strength(CrownGlintFx.MID_LAG), tolerance);
    }

    @Test void theSameSeedAndTimesGiveTheSameFrontsAndAResetRepeatsThem() {
        Wind a = wind(SEED, 0, ROOM), b = wind(SEED, 0, ROOM);
        for (int i = 0; i < 4000; i++) { a.update(STEP); b.update(STEP); assertSame(a, b, 0); }
        a.reset(SEED, 0, ROOM);
        b.reset(SEED, 0, ROOM);
        assertEquals(-1, a.gust());
        run(a, 33, 60);
        run(b, 33, 60);
        assertSame(a, b, 0);
    }

    @Test void anotherSeedGivesOtherGusts() {
        Wind a = wind(SEED, 0, ROOM), b = wind(SEED + 1, 0, ROOM);
        until(a, 0);
        until(b, 0);
        assertTrue(a.width() != b.width() || a.period() != b.period());
    }

    @Test void nothingMovesWithoutAnUpdate() {
        Wind wind = wind(SEED, 0, ROOM);
        run(wind, 3, 60);
        float front = wind.frontX(0), age = wind.gustAge();
        for (int i = 0; i < 100; i++) { wind.strength(0); wind.intensityAt(100, 0); wind.blowing(); }
        assertEquals(front, wind.frontX(0));
        assertEquals(age, wind.gustAge());
    }

    @Test void theFrameRateDoesNotChangeTheSchedule() {
        for (float seconds : new float[]{2, 5, 12.5f, 31, 62}) {
            Wind slow = wind(SEED, 0, ROOM), normal = wind(SEED, 0, ROOM), fast = wind(SEED, 0, ROOM);
            run(slow, seconds, 30);
            run(normal, seconds, 60);
            run(fast, seconds, 144);
            assertSame(normal, slow, TOLERANCE_PX);
            assertSame(normal, fast, TOLERANCE_PX);
        }
    }

    @Test void theFirstGustStartsAtTheLeftEdgeAfterTheCardsDelayAndMovesRight() {
        float left = 480;
        Wind wind = wind(SEED, left, left + ROOM);
        run(wind, 1.4f, 60);
        assertEquals(-1, wind.gust());
        assertFalse(wind.blowing());
        assertEquals(0f, wind.strength(0));
        int frames = 0;
        for (; wind.gust() < 0; frames++) wind.update(STEP);
        assertEquals(1.5f, 1.4f + frames * STEP - wind.gustAge(), 2 * STEP);
        assertEquals(left + 180 * wind.gustAge(), wind.frontX(0), TOLERANCE_PX);
        assertEquals(1, wind.direction());
        assertEquals(180f, wind.speed());
        assertEquals(wind.frontX(0) - 180 * CrownGlintFx.GROVE_LAG, wind.frontX(CrownGlintFx.GROVE_LAG), TOLERANCE_PX);
    }

    @Test void widthPeriodAndEnvelopeStayInTheCardsBounds() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Wind wind = wind(seed, 0, LONGEST_FOREST_ROOM);
            float least = 1, most = 0;
            while (wind.gust() < GUSTS) {
                wind.update(STEP);
                if (wind.gust() < 0) continue;
                assertTrue(wind.width() >= 72 && wind.width() <= 96, "width " + wind.width());
                assertTrue(wind.period() >= 8 && wind.period() <= 12, "period " + wind.period());
                if (!wind.blowing()) {
                    assertEquals(0f, wind.strength(0));
                    assertEquals(0f, wind.intensityAt(wind.frontX(0) - 1, 0));
                    continue;
                }
                float s = wind.strength(0);
                assertTrue(s >= .35f - EPSILON && s <= 1 + EPSILON, "strength " + s);
                least = Math.min(least, s);
                most = Math.max(most, s);
            }
            assertEquals(.35f, least, .01f, "the gust enters and leaves faint");
            assertEquals(1f, most, .01f, "and is brightest mid-room");
        }
    }

    @Test void theBandIsSoftAndEmptyOutsideItself() {
        Wind wind = wind(SEED, 0, ROOM);
        until(wind, 0);
        run(wind, 2, 60);
        float front = wind.frontX(0), width = wind.width();
        assertEquals(wind.strength(0), wind.intensityAt(front - width / 2, 0), EPSILON);
        assertTrue(wind.intensityAt(front - width / 8, 0) < wind.intensityAt(front - width / 2, 0));
        assertEquals(0f, wind.intensityAt(front + 1, 0));
        assertEquals(0f, wind.intensityAt(front - width - 1, 0));
    }

    @Test void aWindFromTheRightStartsAtTheRightEdge() {
        Wind wind = new Wind(new Wind.Profile(-1, 100, 50, 50, 20, 20, 0, .5f, 0));
        wind.reset(1, 100, 500);
        wind.update(1);
        assertEquals(400f, wind.frontX(0), TOLERANCE_PX);
        assertTrue(wind.intensityAt(420, 0) > 0);
        assertEquals(0f, wind.intensityAt(380, 0));
    }

    /** The card: a long room stretches the period and never overlaps two gusts; every shipped room keeps the drawn 8 to 12 s. */
    @Test void thereIsAlwaysACalmAndNeverTwoGusts() {
        for (float width : new float[]{448, ROOM, LONGEST_FOREST_ROOM, 1200, 3000}) {
            Wind wind = wind(SEED, 0, width);
            int previous = -1;
            boolean calm = true;
            while (wind.gust() < GUSTS) {
                boolean wasBlowing = wind.blowing();
                wind.update(STEP);
                if (wind.gust() != previous) {
                    assertTrue(calm, "gust " + wind.gust() + " started in a " + width + " px room while another was still crossing");
                    assertTrue(wind.period() >= wind.sweepSeconds() + Wind.MIN_CALM_SECONDS - EPSILON);
                    previous = wind.gust();
                    calm = false;
                } else if (wasBlowing && !wind.blowing()) calm = true;
            }
        }
    }

    @Test void everyShippedRoomHasOneGustAtATimeAndTheCardsPeriod() {
        for (String map : new String[]{"forest-0", "forest-1", "forest-2"}) {
            Level level = harness.roomOf(map, 0);
            for (RoomLayout.Room room : level.roomLayout.rooms) {
                Rectangle b = room.bounds();
                Wind wind = wind(room.id().hashCode(), b.x, b.x + b.width);
                while (wind.gust() < GUSTS) {
                    wind.update(STEP);
                    if (wind.gust() < 0) continue;
                    assertTrue(wind.period() <= 12 && wind.period() >= wind.sweepSeconds() + Wind.MIN_CALM_SECONDS - EPSILON, room.id() + " " + wind.period());
                }
            }
        }
    }

    private Player player() { return new Player(harness.assets(), harness.game(), 0, 0); }

    private Level windy(String map, int room) {
        Level level = harness.roomOf(map, room);
        assertTrue(level.map.propBool("wind", false), map + " carries the map property wind");
        return level;
    }

    @Test void aMapWithoutTheWindPropertyHasNoGustsAndNoGlints() {
        Level level = harness.roomOf("forest-1", 0);
        level.map.properties.remove("wind");
        level.resetCurrentRoom(player());
        for (int i = 0; i < 600; i++) level.update(STEP);
        assertEquals(-1, level.wind().gust());
        for (int d = 0; d < CrownGlintFx.DEPTHS; d++) assertEquals(0, level.glints().spotCount(d));
    }

    /** The pause menu, the healer scene and a room change never call {@code Level.update}; what they do call leaves the wind alone. */
    @Test void onlyTheWorldsUpdateMovesTheWind() {
        Level level = windy("forest-1", 0);
        for (int i = 0; i < 180; i++) level.update(STEP);
        float front = level.wind().frontX(0), age = level.wind().gustAge();
        assertTrue(level.wind().blowing());
        NoopBatch batch = new NoopBatch();
        level.beginLeaving();
        for (int i = 0; i < 600; i++) {
            level.updateRoomChange(STEP);
            level.updatePresentation(STEP);
            level.render(batch, new Rectangle(0, 0, 640, 360));
        }
        assertEquals(front, level.wind().frontX(0));
        assertEquals(age, level.wind().gustAge());
        level.cancelLeaving();
        level.update(STEP / 2); // the death slow motion passes a smaller dt: the gust slows with it
        assertEquals(age + STEP / 2, level.wind().gustAge(), EPSILON);
    }

    @Test void aRetryAndARevisitStartTheRoomsGustsAgain() {
        Level level = windy("forest-1", 0);
        for (int i = 0; i < 1000; i++) level.update(STEP);
        int gust = level.wind().gust();
        float front = level.wind().frontX(0), width = level.wind().width();
        level.resetCurrentRoom(player());
        assertEquals(-1, level.wind().gust(), "from the beginning");
        for (int d = 0; d < CrownGlintFx.DEPTHS; d++) assertEquals(0, level.glints().particles(d).count());
        for (int i = 0; i < 1000; i++) level.update(STEP);
        assertEquals(gust, level.wind().gust());
        assertEquals(front, level.wind().frontX(0), TOLERANCE_PX);
        assertEquals(width, level.wind().width());
        level.enterNextRoom(player());
        assertEquals(-1, level.wind().gust(), "the next room starts its own");
        Rectangle next = level.currentRoomBounds();
        assertEquals(next.x, level.wind().frontX(0), "at its own left edge");
    }
}
