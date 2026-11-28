package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The room that develops the wolf, {@code forest-ridge} (#525, D158): its route leaps a step and a trench, through the real map
 * and real Level updates with ordinary controls. The room is x 5952-6816; the floor top is y 304, the mounds (x 6352-6416 and
 * 6448-6512) y 288, the ledge (x 6224-6288) y 256.
 */
class ForestRidgeTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SECOND = 60;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level level() { return h.room("forest-2", "forest-ridge"); }

    private static Wolf wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    private float drive(boolean patient, List<String> states) { return drive(patient, 0, Float.NaN, states); }

    /**
     * The seconds from the start to the exit, or the negative seconds to the hit. He sets off after {@code delay} frames; from the
     * room-relative x {@code noJumpFrom} on he jumps no more (NaN: always); {@code states} collects what the wolf showed.
     */
    private float drive(boolean patient, int delay, float noJumpFrom, List<String> states) {
        Level level = level();
        Wolf w = wolf(level);
        ForestRidgeRoute route = new ForestRidgeRoute(level, patient);
        for (int f = 0; f < SECOND * 40; f++) {
            h.held.clear(); h.pressed.clear();
            if (f >= delay) route.drive(level, h.held, h.pressed);
            if (level.player.position.x - ForestRidgeRoute.ROOM_X >= noJumpFrom) { h.held.remove(Input.Keys.J); h.pressed.remove(Input.Keys.J); }
            level.update(STEP);
            if (states != null) states.add(w.state() + " " + w.leg());
            if (level.player.isGotHit()) return -(f + 1 - delay) * STEP;
            if (level.atRoomExit()) return (f + 1 - delay) * STEP;
        }
        fail("the route never reached the exit");
        return 0;
    }

    @Test void theMasteredRouteStompsTheBayingWolfAndReachesTheExitWithinTwentySecondsWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            List<String> states = new ArrayList<>();
            float seconds = drive(true, states);
            assertTrue(seconds > 0 && seconds <= 20, "reached the exit alive (assist " + longer + "): " + seconds);
            assertEquals(12.55f, seconds, .1f, "the card's mastered time");
            assertTrue(states.stream().anyMatch(s -> s.startsWith("DEAD")), "the stomp from the ledge killed it");
            for (String seen : new String[]{"LEAP_COIL 0", "LEAP 0", "LEAP 1", "LEAP 2", "BAY 0"}) assertTrue(states.contains(seen), "shown: " + seen);
        }
    }

    private static final int START_STEP = 6, LAST_START = 720;

    @Test void theMasteredRoutePassesFromEveryStartMomentThroughTwoPatrols() {
        for (int delay = 0; delay <= LAST_START; delay += START_STEP)
            assertTrue(drive(true, delay, Float.NaN, null) > 0, "the watched route is safe whenever he starts: " + delay / 60f + " s");
    }

    /**
     * The same take-offs without the wait: the wolf pounces, lunges and reads, and a hero who keeps jumping can still get past it.
     * Pinned so the card states it: from 105 of 121 start moments he is out in at most 7.8 s, from the other 16 he is bitten.
     */
    @Test void aHeroWhoDoesNotWaitIsSometimesBittenAndOtherwiseOutSooner() {
        int out = 0, bitten = 0;
        for (int delay = 0; delay <= LAST_START; delay += START_STEP) {
            float seconds = drive(false, delay, Float.NaN, null);
            if (seconds > 0) { out++; assertTrue(seconds <= 7.85f, "out in " + seconds); }
            else bitten++;
        }
        assertEquals(105, out);
        assertEquals(16, bitten);
    }

    /** The retry figures of the card: no jump over the first root, over the tuft, or over the second root. */
    @Test void aHeroWhoMissesAThornJumpIsHitAtTheCardsTimes() {
        assertEquals(1.02f, -drive(true, 0, 0f, null), .1f, "the first root, the cheapest death");
        assertEquals(10.67f, -drive(true, 0, 560f, null), .1f, "the tuft");
        assertEquals(11.87f, -drive(true, 0, 680f, null), .1f, "the second root, the most expensive: the whole room, under D105's 15 s");
    }

    /** A mound a hero could once wait on is no refuge: the wolf leaps up to him and bites. */
    @Test void aHeroWhoStopsOnAMoundIsReachedAndBitten() {
        Level level = level();
        Wolf w = wolf(level);
        boolean reached = false;
        for (int f = 0; f < SECOND * 20 && !level.player.isGotHit(); f++) {
            level.player.position.set(6384 - 23.5f, ForestRidgeRoute.MOUND_TOP - 48);
            level.player.velocity().setZero();
            level.player.setOnGround(true);
            level.update(STEP);
            reached |= w.leg() == 1 && w.state() == Wolf.State.WINDUP;
        }
        assertTrue(reached, "it came up on to the mound");
        assertTrue(level.player.isGotHit());
    }
}
