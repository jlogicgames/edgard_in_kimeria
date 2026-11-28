package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
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
 * The room that develops the wolf, {@code forest-ridge} (#525, D158, D165): its route leaps a step and a trench, through the real map
 * and real Level updates with ordinary controls. The room is x 5952-6816; the floor top is y 304, the mounds (x 6384-6448 and
 * 6480-6528) y 288, the ledge (x 6144-6208) y 272.
 */
class ForestRidgeTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SECOND = 60;
    /** Measured, pinned (D165): the mastered route without and with the assist, and the slowest of its start moments. */
    private static final float MASTERED_S = 10.68f, ASSIST_MASTERED_S = 10.12f, SLOWEST_START_S = 14.7f;

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

    /**
     * The seconds from the start to the exit, or the negative seconds to the hit, on the mastered route; from the room-relative x
     * {@code noJumpFrom} on he jumps no more (NaN: always); {@code states} collects what the wolf showed.
     */
    private float drive(float noJumpFrom, List<String> states) {
        Level level = level();
        Wolf w = wolf(level);
        ForestRidgeRoute route = new ForestRidgeRoute(level);
        for (int f = 0; f < SECOND * 40; f++) {
            h.held.clear(); h.pressed.clear();
            route.drive(level, h.held, h.pressed);
            if (level.player.position.x - ForestRidgeRoute.ROOM_X >= noJumpFrom) { h.held.remove(Input.Keys.J); h.pressed.remove(Input.Keys.J); }
            level.update(STEP);
            if (states != null) states.add(w.state() + " " + w.leg());
            if (level.player.isGotHit()) return -(f + 1) * STEP;
            if (level.atRoomExit()) return (f + 1) * STEP;
        }
        fail("the route never reached the exit");
        return 0;
    }

    private WolfRoomRuns.Run run(ForestRidgeRoute route, Level level, int delay, int afterBay) {
        return WolfRoomRuns.run(h, level, route, delay, afterBay);
    }

    @Test void theMasteredRouteDodgesTheBayedWolfsBiteAndReachesTheExitWithinTwentySecondsWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            List<String> states = new ArrayList<>();
            float seconds = drive(Float.NaN, states);
            assertTrue(seconds > 0 && seconds <= 20, "reached the exit alive (assist " + longer + "): " + seconds);
            assertEquals(longer ? ASSIST_MASTERED_S : MASTERED_S, seconds, .1f, "the card's mastered time (assist " + longer + ")");
            assertTrue(states.contains("DEAD 0"), "the sword killed it on its lower leg");
            assertEquals(!longer, states.contains("POUNCE_COIL 0"), "without the assist it leaps at him as he comes down; with it, never");
            for (String seen : new String[]{"LEAP_COIL 2", "LEAP 2", "LEAP_COIL 1", "LEAP 1", "BAY 0"}) assertTrue(states.contains(seen), "shown: " + seen);
        }
    }

    private static final int START_STEP = 6, LAST_START = 720;

    @Test void theMasteredRoutePassesFromEveryStartMomentThroughTwoPatrolsWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int delay = 0; delay <= LAST_START; delay += START_STEP) {
                Level level = level();
                WolfRoomRuns.Run run = run(new ForestRidgeRoute(level), level, delay, -1);
                assertTrue(run.out() && run.seconds() <= SLOWEST_START_S, "safe whenever he starts: " + delay / 60f + " s, assist " + longer + ": " + run);
                assertNotNull(run.killedIn(), "he finishes it");
            }
        }
    }

    /**
     * A hero who only walks forward and stops on the ledge for 0 to 8 s. Without the assist 29 of the 41 pauses up to 4 s are
     * bitten, but only 2 of the 40 longer ones: it gives up and walks off past him. With the assist 7 and none.
     */
    @Test void aHeroWhoWalksForwardAndPausesOnTheLedgeStompsItOnlyInItsRecoveryAndIsMostlySafeAfterALongWait() throws Exception {
        int[][] pins = {{29, 12, 2, 38}, {7, 34, 0, 40}};   // {bitten, out} up to 4 s, then {bitten, out} past it: without the assist, with it
        for (int assist = 0; assist < 2; assist++) {
            setLonger(assist == 1);
            assertArrayEquals(pins[assist], WolfRoomRuns.sweepWaits(h, "forest-ridge", ForestRidgeRoute::careless, -1), "assist " + (assist == 1));
        }
    }

    /**
     * The ledge's back (left) end, walked off after 0 to 8 s on it: forward at once ({@code backWay(pause, 0)}), or off at once and
     * a stand on the floor for 0 to 8 s ({@code backWay(0, wait)}).
     */
    @Test void aHeroWhoLeavesTheLedgeByItsBackEndIsBittenAsMeasured() throws Exception {
        int[][] walks = {{25, 16, 11, 29}, {12, 29, 8, 32}}, stands = {{17, 24, 11, 29}, {12, 29, 8, 32}};
        for (int assist = 0; assist < 2; assist++) {
            setLonger(assist == 1);
            assertArrayEquals(walks[assist], WolfRoomRuns.sweepWaits(h, "forest-ridge", (level, wait) -> ForestRidgeRoute.backWay(level, wait, 0), -1),
                "walks on at once, assist " + (assist == 1));
            assertArrayEquals(stands[assist], WolfRoomRuns.sweepWaits(h, "forest-ridge", (level, wait) -> ForestRidgeRoute.backWay(level, 0, wait), -1),
                "stands on the floor first, assist " + (assist == 1));
        }
    }

    /**
     * He waits on the ledge until it bays, leaves by the back end, lets go as he clears it, then walks forward after 0 to 8 s.
     * Without the assist its leap takes him there in all 81 runs; with it 20 of the 40 waits past 4 s end bitten, none of the 41 before.
     */
    @Test void aHeroWhoLeavesTheBackEndAsItBaysAndWaitsOnTheFloorIsBittenAsMeasured() throws Exception {
        int[][] pins = {{41, 0, 40, 0}, {0, 41, 20, 20}};
        for (int assist = 0; assist < 2; assist++) {
            setLonger(assist == 1);
            assertArrayEquals(pins[assist], WolfRoomRuns.sweepWaits(h, "forest-ridge", (level, wait) -> ForestRidgeRoute.backWay(level, 0, wait), 0),
                "assist " + (assist == 1));
        }
    }

    /** The same walk begun at any moment, without a stop: 83 of 121 are bitten without the assist (7 in 10), 19 with it. */
    @Test void aHeroWhoDoesNotWaitIsMostlyBittenAndNeverStompsItOutsideItsRecovery() throws Exception {
        int[][] pins = {{83, 38}, {19, 102}};   // {bitten, out}: without the assist, with it
        for (int assist = 0; assist < 2; assist++) {
            setLonger(assist == 1);
            int bitten = 0, out = 0;
            for (int delay = 0; delay <= LAST_START; delay += START_STEP) {
                Level level = level();
                WolfRoomRuns.Run run = run(ForestRidgeRoute.careless(level, 0), level, delay, -1);
                assertTrue(run.killedIn() == null || run.killedIn() == Wolf.State.RECOVER, "no stomp outside its recovery: " + run);
                if (run.out()) { out++; assertTrue(run.seconds() <= 8f, "out in " + run.seconds()); } else bitten++;
            }
            assertArrayEquals(pins[assist], new int[]{bitten, out}, "assist " + (assist == 1));
        }
    }

    /**
     * He watches until it bays under the ledge, then walks on within a second. Without the assist 585 of 610 such walks over two
     * patrols are bitten; with it, whose wolf does not leap at him, none.
     */
    @Test void aHeroWhoWalksOnAsSoonAsItBaysIsMostlyBitten() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            int bitten = 0;
            for (int delay = 0; delay <= LAST_START; delay += 12)
                for (int after = 0; after < 60; after += 6) {
                    Level level = level();
                    WolfRoomRuns.Run run = run(ForestRidgeRoute.careless(level, 0), level, delay, after);
                    if (!run.out()) bitten++;
                    assertNull(run.killedIn(), "walking forward never kills it");
                }
            assertEquals(longer ? 0 : 585, bitten, "assist " + longer);
        }
    }

    private enum Answer { STAY, WALK_AWAY, JUMP }

    /**
     * Off the back end after {@code ledgeWait} frames on the ledge (negative: as soon as it bays), letting go as he clears it; from
     * {@code delay} frames after it gathers he answers. Null when it never gathers, else whether he is unhurt when it has landed.
     */
    private Boolean escapesTheBackEndLeap(int ledgeWait, int delay, Answer answer) {
        Level level = level();
        Wolf w = wolf(level);
        ForestRidgeRoute route = ForestRidgeRoute.backWay(level, Math.max(0, ledgeWait), SECOND * 30);
        boolean bayed = false;
        int gathered = -1;
        for (int f = 0; f < SECOND * 20; f++) {
            h.held.clear(); h.pressed.clear();
            bayed |= route.onPerch(level) && w.state() == Wolf.State.BAY;
            if (ledgeWait >= 0 || bayed || !route.onPerch(level)) route.drive(level, h.held, h.pressed);
            if (gathered >= 0 && f > gathered + delay && answer != Answer.STAY) {
                h.held.clear();
                if (answer == Answer.WALK_AWAY) h.held.add(Input.Keys.A);
                else if (f == gathered + delay + 1) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
            }
            level.update(STEP);
            if (gathered < 0 && w.state() == Wolf.State.POUNCE_COIL) { gathered = f; assertTheBackEndGather(level, w); }
            if (level.player.isGotHit()) return gathered < 0 ? null : false;
            if (gathered >= 0 && w.state() == Wolf.State.RECOVER) return true;
        }
        assertEquals(-1, gathered, "a gathered leap lands");
        return null;
    }

    /** Where the two are when it gathers off the back end (D165): the hero just past the ledge, the wolf whole on the screen. */
    private void assertTheBackEndGather(Level level, Wolf w) {
        Rectangle hero = level.player.hitboxBounds(), body = w.hitboxBounds();
        assertEquals(6133, hero.x, 1f);
        assertTrue(hero.y + hero.height >= 276 && hero.y + hero.height <= 295, "his feet: " + (hero.y + hero.height));
        assertEquals(6288, body.x, 1f);
        assertTrue(body.x >= h.camera.x && body.x + body.width <= h.camera.x + 640 && body.y >= h.camera.y && body.y + body.height <= h.camera.y + 360,
            "the wolf is on the screen: " + body + " in the view at " + h.camera);
    }

    /**
     * The back end's answers (D165): the hero who has let go is the spot the leap locks, so staying there is bitten; walking on away
     * from it or jumping, begun 0 to 0.25 s after it gathers, gets him out of every such leap.
     */
    @Test void offTheBackEndWalkingOnOrJumpingEscapesTheBaysBiteAndStayingDoesNot() {
        int gathers = 0;
        for (int ledgeWait = -1; ledgeWait <= WolfRoomRuns.WAIT_CAP; ledgeWait = ledgeWait < 0 ? 0 : ledgeWait + WolfRoomRuns.WAIT_STEP) {
            if (escapesTheBackEndLeap(ledgeWait, 0, Answer.STAY) == null) continue;
            gathers++;
            for (int delay = 0; delay <= SECOND / 4; delay += 3) {
                assertEquals(Boolean.FALSE, escapesTheBackEndLeap(ledgeWait, delay, Answer.STAY), "stays: wait " + ledgeWait);
                assertEquals(Boolean.TRUE, escapesTheBackEndLeap(ledgeWait, delay, Answer.WALK_AWAY), "walks on: wait " + ledgeWait + ", delay " + delay);
                assertEquals(Boolean.TRUE, escapesTheBackEndLeap(ledgeWait, delay, Answer.JUMP), "jumps: wait " + ledgeWait + ", delay " + delay);
            }
        }
        assertEquals(18, gathers, "as it bays, and the ledge waits from which it gathers");
    }

    @Test void aHeroWhoJumpsOnTheLedgeAndLandsBackOnItIsNeverBitten() {
        WolfRoomRuns.assertAJumpingPerchedHeroIsNeverBitten(h, "forest-ridge", ForestRidgeRoute.LEDGE_LEFT, ForestRidgeRoute.LEDGE_RIGHT,
            ForestRidgeRoute.LEDGE_TOP);
    }

    /** The retry figures of the card: no jump over the first root, over the tuft, or over the second root. */
    @Test void aHeroWhoMissesAThornJumpIsHitAtTheCardsTimes() {
        assertEquals(.85f, -drive(0f, null), .1f, "the first root, the cheapest death");
        assertEquals(9.03f, -drive(575f, null), .1f, "the tuft");
        assertEquals(10.22f, -drive(650f, null), .1f, "the second root, the most expensive: the whole room, under D105's 15 s");
    }

    @Test void everyOpenTopOfItsSolidsIsDrawnWithAGroundTopTile() {
        WolfRoomRuns.assertOpenGroundHasTopTiles(h, "forest-ridge", 40);
    }

    /** A mound a hero could once wait on is no refuge: the wolf leaps to him and bites. */
    @Test void aHeroWhoStopsOnAMoundIsReachedAndBitten() {
        Level level = level();
        Wolf w = wolf(level);
        boolean reached = false;
        for (int f = 0; f < SECOND * 20 && !level.player.isGotHit(); f++) {
            level.player.position.set(ForestRidgeRoute.MOUND_LEFT + 32 - 23.5f, ForestRidgeRoute.MOUND_TOP - 48);
            level.player.velocity().setZero();
            level.player.setOnGround(true);
            level.update(STEP);
            reached |= w.leg() == 1 && w.state() == Wolf.State.WINDUP;
        }
        assertTrue(reached, "it came on to his mound");
        assertTrue(level.player.isGotHit());
    }
}
