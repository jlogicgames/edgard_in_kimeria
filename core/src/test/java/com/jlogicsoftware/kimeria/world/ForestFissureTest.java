package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.StaticPlatform;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teaching room of the ember fissure, {@code forest-fissure} (#366, D91, D72): room 2 of forest-2, after
 * {@code forest-walls}. A fissure on open floor met alone, then a second whose jet pierces a one-way branch on the step up to
 * the exit ledge. Scripted heroes with the ordinary controls measure the mastered route (D19) over every start offset with
 * and without the assist (D26: timing alone), a careless walker, and the retry. World Y grows downward.
 */
class ForestFissureTest {
    private static final float STEP = 1f / 60f;
    private static final int FISSURE_ROOM = 2;
    private static final float FLOOR = 320f, OFFSET_STEP = 0.05f, CYCLE = 3.5f;
    private static final int ROUTE_LIMIT_FRAMES = 60 * 30;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() throws Exception { h.setUp(); setLonger(false); }

    @AfterEach void restoreGlobals() throws Exception { setLonger(false); h.restoreGlobals(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /** entry, walls, then the fissure room (#376, #366). */
    private Level room() {
        Level level = h.roomOf("forest-2", FISSURE_ROOM);
        assertEquals("forest-fissure", level.currentRoomName());
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }

    // -- the room --

    @Test void theRoomHoldsTwoFissuresTheSecondPiercingTheBranchAndBothInViewFromTheEntrance() {
        Level level = room();
        List<EmberFissure> fissures = ForestFissureRoute.fissures(level);
        assertEquals(2, fissures.size());
        EmberFissure floor = fissures.get(0), branch = fissures.get(1);
        Rectangle column = new Rectangle();
        for (EmberFissure f : fissures) {
            assertEquals(FLOOR, f.floorY(), 0.01f, "each stands on the floor line (the SpawnPoints layer is drawn 16 px up)");
            assertEquals(EmberFissure.DEFAULT_HEIGHT, f.columnHeight(), 0.01f, "nothing solid cuts either column");
            assertEquals(EmberFissure.DEFAULT_PERIOD_SECONDS, f.period(), 0.001f);
        }
        branch.columnInto(column);
        assertTrue(column.y < ForestFissureRoute.BRANCH_TOP, "the branch fissure's jet passes up through the branch");
        assertEquals(ForestFissureRoute.LEDGE_TOP, column.y, 0.01f, "and reaches the ledge's top");
        StaticPlatform over = level.staticPlatforms().stream().filter(p -> p.getX() <= branch.centerX() && branch.centerX() <= p.getX() + p.getWidth())
            .findFirst().orElseThrow();
        assertEquals(ForestFissureRoute.BRANCH_TOP, over.getY(), 0.01f, "a one-way branch stands over the second fissure");
        assertTrue(level.staticPlatforms().stream().noneMatch(p -> p.getX() <= floor.centerX() && floor.centerX() <= p.getX() + p.getWidth()),
            "the first fissure is met alone, on open floor");
        Rectangle room = level.currentRoomBounds();
        assertEquals(640f, room.width, 0.01f, "one screen: both fissures are in view from the entrance");
        assertEquals(2, level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.objects.LethalHazard).count(), "nothing else in the room can kill");
    }

    // -- the route --

    /** The mastered route after {@code delay} s of standing at the entrance: seconds to the exit after the delay, or -1 on a death. */
    private float route(Level level, float delay) {
        held.clear();
        settle(level, Math.round(delay * 60));
        ForestFissureRoute route = new ForestFissureRoute(level);
        int frames = 0;
        while (!level.atRoomExit()) {
            held.clear();
            route.drive(level, held, pressed);
            frame(level);
            if (!alive(level)) { held.clear(); return -1; }
            assertTrue(++frames < ROUTE_LIMIT_FRAMES, "stuck at " + level.player.position);
        }
        held.clear();
        return frames * STEP;
    }

    @Test void theMasteredRouteIsMeasuredAndEveryStartOffsetReachesTheExitWithAndWithoutTheAssist() throws Exception {
        float first = route(room(), 0f);
        assertTrue(first > 0, "the mastered route from the entrance");
        float slowest = 0, fastest = Float.MAX_VALUE;
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < CYCLE; delay += OFFSET_STEP) {
                float t = route(room(), delay);
                assertTrue(t > 0, "longer=" + longer + " delay " + delay + ": died");
                slowest = Math.max(slowest, t); fastest = Math.min(fastest, t);
            }
        }
        System.out.println("forest-fissure route: from the entrance " + first + " s; over start offsets 0-3.5 s " + fastest + " to " + slowest + " s");
        assertTrue(slowest < 20f, "D19: the mastered route stays about 20 s at worst (" + slowest + ")");
    }

    @Test void aHeroWhoIgnoresTheFissuresDiesSomewhereAndTheRetryCostsAtMostTheRoom() {
        int deaths = 0, passes = 0;
        float latest = 0;
        for (float delay = 0f; delay < CYCLE; delay += OFFSET_STEP) {
            Level level = room();
            float floorColumn = ForestFissureRoute.fissures(level).get(0).centerX() + EmberFissure.JET_WIDTH;
            held.clear();
            settle(level, Math.round(delay * 60));
            int frames = 0;
            while (alive(level) && level.player.hitboxBounds().x < floorColumn && frames < 60 * 10) {
                held.clear(); held.add(Input.Keys.D);
                frame(level); frames++;
            }
            held.clear();
            if (alive(level)) passes++;
            else { deaths++; latest = Math.max(latest, delay + frames * STEP); }
        }
        System.out.println("forest-fissure careless walk past the floor fissure: " + passes + " passed, " + deaths + " died, latest death "
            + latest + " s after entering");
        assertTrue(deaths > 0, "the jet kills a careless walker");
        assertTrue(passes > deaths, "the open window (2.5 s of 3.5) is most of the cycle");
        assertTrue(latest < 20f, "a death replays at most the room");
    }

    @Test void aRetryRebuildsBothFissuresAtTheirAuthoredPhase() {
        Level level = room();
        List<EmberFissure> before = ForestFissureRoute.fissures(level);
        float[] fresh = {before.get(0).cycleTime(), before.get(1).cycleTime()};
        assertNotEquals(fresh[0], fresh[1], 0.01f, "the two fissures are out of step");
        settle(level, 100);
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        List<EmberFissure> after = ForestFissureRoute.fissures(level);
        assertEquals(fresh[0], after.get(0).cycleTime(), 0.001f);
        assertEquals(fresh[1], after.get(1).cycleTime(), 0.001f);
    }
}
