package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wolf's first room, {@code forest-glade} (#331, D115, D158, D165), through the real map and real Level updates with ordinary controls.
 * World Y grows downward: the floor top is y 304, the trunk (x 3920-3952) and the one-way branch (x 3952-4016) tops y 272, the
 * exit (x 4448-4464) stands on the floor against the right wall. The wolf spawns at x 4144 (the right end of its range x 3984-4144) facing left; its
 * muzzle is at x 4143 and the hero stands to its left unless a test says otherwise. Hero positions are the frame's x (the 11 px
 * body starts 18 px right of it).
 */
class ForestGladeTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 304, BRANCH_TOP = 272, TRUNK_LEFT = 3920, TRUNK_RIGHT = 3952;
    private static final int SECOND = 60;
    /** Measured, pinned within 0.1 s: the death at the second thorn root on the mastered pace (the card's retry cost). */
    private static final float SECOND_ROOT_DEATH_S = 8.63f;
    /** Measured, pinned: the bite on a hop down in front of the wolf (D158). */
    private static final float HOP_DEATH_S = 4.62f;
    /** Measured, pinned (D165): the mastered route without and with the assist, and the slowest of its start moments. */
    private static final float MASTERED_S = 9.33f, ASSIST_MASTERED_S = 9.03f, SLOWEST_START_S = 14.2f;
    /** Commit gaps (px, muzzle to the hero's near edge): at the muzzle and at the edge of its box. */
    private static final float AT_MUZZLE = 1, AT_BOX_EDGE = 71;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level level() { return h.room("forest-2", "forest-glade"); }

    private static Wolf wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    private void frame(Level level) { level.update(STEP); h.pressed.clear(); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    private int until(Level level, java.util.function.BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    /** Stands the hero (body right edge at {@code right}) on the floor, still, facing right. */
    private void place(Level level, float right) {
        level.player.position.set(right - 29, FLOOR - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        h.held.clear();
    }

    /** Stands the hero {@code gap} px from the muzzle of the wolf (which faces left), on its left, and runs until it winds up. */
    private Wolf committed(Level level, float gap) {
        Wolf w = wolf(level);
        place(level, w.jawFrontX() - gap);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        return w;
    }

    // ---- the route -------------------------------------------------------------------------------------------------------

    /** The seconds from the start to the exit, or the negative seconds to the hit. */
    private static float signed(WolfRoomRuns.Run run) { return run.out() ? run.seconds() : -run.seconds(); }

    private WolfRoomRuns.Run run(ForestGladeRoute route, Level level, int delay, int afterBay) {
        return WolfRoomRuns.run(h, level, route, delay, afterBay);
    }

    private static final int START_STEP = 6, LAST_START = 720;

    @Test void theMasteredRouteDodgesTheBayedWolfsBiteAndReachesTheExitWithinTwentySeconds() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = level();
            WolfRoomRuns.Run run = run(new ForestGladeRoute(level), level, 0, -1);
            assertTrue(run.out() && run.seconds() <= 20, "reached the exit alive in D19's 20 s (assist " + longer + "): " + run);
            assertEquals(longer ? ASSIST_MASTERED_S : MASTERED_S, run.seconds(), .1f, "the card's mastered time (assist " + longer + ")");
            assertEquals(!longer, run.pounced(), "without the assist it leaps at him as he comes down; with it, never");
            assertEquals(longer ? Wolf.State.LUNGE : Wolf.State.RECOVER, run.killedIn(),
                "the sword: in the recovery from the missed bite, or, with the assist, as the lunge comes");
        }
    }

    @Test void theMasteredRoutePassesFromEveryStartMomentThroughTwoPatrolsWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int delay = 0; delay <= LAST_START; delay += START_STEP) {
                Level level = level();
                WolfRoomRuns.Run run = run(new ForestGladeRoute(level), level, delay, -1);
                assertTrue(run.out() && run.seconds() <= SLOWEST_START_S, "safe whenever he starts: " + delay / 60f + " s, assist " + longer + ": " + run);
                assertNotNull(run.killedIn(), "he finishes it");
            }
        }
    }

    @Test void everyOpenTopOfItsSolidsIsDrawnWithAGroundTopTile() {
        WolfRoomRuns.assertOpenGroundHasTopTiles(h, "forest-glade", 40);
    }

    @Test void theRoutesSwordLeavesItsBody() {
        Level level = level();
        Wolf w = wolf(level);
        run(new ForestGladeRoute(level), level, 0, -1);
        assertEquals(Wolf.State.DEAD, w.state());
        assertTrue(w.isRemains());
    }

    /**
     * A hero who only walks forward and stops on the branch for 0 to 8 s (D165: before it, 17 of the 41 pauses up to 4 s stomped
     * the wolf for free). Without the assist 22 of those 41 and 23 of the 40 longer ones are bitten; with it 7 and 13.
     */
    @Test void aHeroWhoWalksForwardAndPausesOnTheBranchStompsItOnlyInItsRecoveryAndIsBittenAsMeasured() throws Exception {
        int[][] pins = {{22, 19, 23, 17}, {7, 34, 13, 27}};   // {bitten, out} up to 4 s, then {bitten, out} past it: without the assist, with it
        for (int assist = 0; assist < 2; assist++) {
            setLonger(assist == 1);
            assertArrayEquals(pins[assist], WolfRoomRuns.sweepWaits(h, "forest-glade", ForestGladeRoute::careless, -1), "assist " + (assist == 1));
        }
    }

    /** The scene D165 answers: he watches until it bays, then walks on within a second. Without the assist he is always bitten. */
    @Test void aHeroWhoWalksOnAsSoonAsItBaysIsBittenWithoutTheAssistAndPassesWithIt() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int delay = 0; delay <= LAST_START; delay += 48)
                for (int after = 0; after < 60; after += 6) {
                    Level level = level();
                    WolfRoomRuns.Run run = run(ForestGladeRoute.careless(level, 0), level, delay, after);
                    assertEquals(longer, run.out(), "start " + delay + ", walks on " + after + " frames after the bay: " + run);
                    assertNull(run.killedIn(), "walking forward never kills it");
                }
        }
    }

    @Test void aHeroWhoJumpsOnTheBranchAndLandsBackOnItIsNeverBitten() {
        WolfRoomRuns.assertAJumpingPerchedHeroIsNeverBitten(h, "forest-glade", 3952, 4016, BRANCH_TOP);
    }

    /**
     * A wolf death on a careless pace: a hero who hops down off the trunk or the branch's near end has his jump answered (D158),
     * lands on the floor in front of the wolf and is bitten at 4.6 s.
     */
    @Test void aHeroWhoHopsDownInFrontOfTheWolfIsBittenWithinSixSeconds() {
        for (float hopAt : new float[]{3930, 3948, 3960}) {
            Level level = level();
            ForestGladeRoute route = ForestGladeRoute.careless(level, 0);
            boolean hopped = false;
            float seconds = 0;
            for (int f = 0; f < SECOND * 30 && seconds == 0; f++) {
                h.held.clear(); h.pressed.clear();
                route.drive(level, h.held, h.pressed);
                float x = level.player.position.x;
                if (!hopped && x >= hopAt && level.player.isOnGround() && level.player.position.y + 48 <= BRANCH_TOP + 1) {
                    h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); hopped = true;
                }
                level.update(STEP);
                if (!alive(level)) seconds = (f + 1) * STEP;
                assertFalse(level.atRoomExit(), "he is bitten (hop at x " + hopAt + ")");
            }
            assertEquals(HOP_DEATH_S, seconds, .15f, "the retry costs the walk to the trunk and the bite (hop at x " + hopAt + ")");
            assertTrue(seconds <= 6f, "D105: a first meeting's retry is at most 6 s");
        }
    }

    @Test void aHeroWhoWalksStraightIntoTheFirstThornRootIsHitAtTheSoonestWithinSixSeconds() {
        Level level = level();
        for (int f = 0; f < SECOND * 8; f++) {
            h.held.clear();
            h.held.add(Input.Keys.D);
            level.update(STEP);
            if (!alive(level)) {
                assertEquals(1.17f, (f + 1) * STEP, .1f, "the first root is the cheapest death of the room");
                assertTrue((f + 1) * STEP <= 6f);
                return;
            }
        }
        fail("a hero who walks straight on passes the root alive");
    }

    /** The most expensive death of the room: the mastered route past the wolf, then straight into the second thorn root. */
    @Test void aHeroWhoWalksStraightIntoTheSecondThornRootAfterTheWolfIsHit() {
        Level level = level();
        float seconds = signed(run(new ForestGladeRoute(level, false), level, 0, -1));
        assertTrue(seconds < 0, "he is hit by the root");
        assertEquals(SECOND_ROOT_DEATH_S, -seconds, .1f, "the retry costs the whole mastered route up to the second root");
        // D105 asks at most 6 s of a first meeting that is not among the first two beats; the wolf is beat 2, so its pocket needs no retry
        // limit, and the root is a known element (a repeat, not a first meeting): its death replays the whole room, under D105's 15 s split rule.
        assertTrue(-seconds < 15f, "a retry that long would be a reason to split the room (D105)");
    }

    // ---- the wolf's answers on its floor ---------------------------------------------------------------------------------

    /** True when the first lunge and bite were over without touching him; he jumps ({@code J}) {@code takeoff} frames after the commit, or never (-1). */
    private boolean jumpClears(boolean longer, float gap, int takeoff) throws Exception {
        setLonger(longer);
        Level level = level();
        Wolf w = committed(level, gap);
        for (int f = 0; f < SECOND * 3; f++) {
            if (f == takeoff) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
            frame(level);
            h.held.remove(Input.Keys.J);
            if (!alive(level)) return false;
            if (w.state() == Wolf.State.RECOVER) return true;
        }
        return alive(level);
    }

    @Test void aJumpAQuarterSecondAfterTheCommitClearsTheLungeFromEveryCommitGapWithAndWithoutTheAssist() throws Exception {
        int takeoff = SECOND / 4;
        for (boolean longer : new boolean[]{false, true})
            for (float gap = AT_MUZZLE; gap <= AT_BOX_EDGE + 1; gap += 1)
                assertTrue(jumpClears(longer, gap, takeoff), "the jump clears the lunge from a gap of " + gap + " px (assist " + longer + ")");
    }

    @Test void standingStillOrJumpingNeverIsBittenWhateverTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true})
            for (float gap : new float[]{AT_MUZZLE, 40, AT_BOX_EDGE})
                assertFalse(jumpClears(longer, gap, -1), "a hero who stays put is bitten (gap " + gap + ", assist " + longer + ")");
    }

    /**
     * He runs from the commit, left: without the assist the jaws outreach him by 13 px, with it they miss by 12 px (D115). Past the
     * wolf's recoil the trunk's foot is 12 to 19 px away: he jumps on to its top, 32 px up and out of reach, and is never bitten.
     */
    private boolean runnerBitten(boolean longer, float gap) throws Exception {
        setLonger(longer);
        Level level = level();
        Wolf w = committed(level, gap);
        boolean up = false;
        for (int f = 0; f < SECOND * 8; f++) {
            Rectangle b = level.player.hitboxBounds();
            h.held.clear();
            if (!up) {
                h.held.add(Input.Keys.A);
                if (level.player.isOnGround() && b.x <= TRUNK_RIGHT + 9) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
                up = !level.player.isOnGround() && b.y + b.height < FLOOR - 4 && b.x <= TRUNK_RIGHT + 9 && f > SECOND / 2;
            } else h.held.add(Input.Keys.A);
            frame(level);
            if (!alive(level)) return true;
        }
        return false;
    }

    @Test void aRunnerIsBittenWithoutTheAssistAndMissedWithItThenClimbsTheTrunkOutOfReach() throws Exception {
        assertTrue(runnerBitten(false, AT_BOX_EDGE), "outrunning is not an answer without the assist");
        assertFalse(runnerBitten(true, AT_BOX_EDGE), "with the 0.75 s warning, running works again, and the trunk is a refuge");
    }

    private void swing(Level level) { h.held.add(Input.Keys.K); h.pressed.add(Input.Keys.K); frame(level); h.held.remove(Input.Keys.K); }

    /** With the assist there is no jump back (D158): a swing begun in the wind-up is still live when the jaws arrive. */
    @Test void withTheAssistASwingAsItComesKillsItAndNothingBitesHim() throws Exception {
        setLonger(true);
        Level level = level();
        Wolf w = committed(level, AT_BOX_EDGE - 1);
        for (int f = 0; f < SECOND / 2; f++) frame(level);
        swing(level);
        for (int f = 0; f < SECOND * 2; f++) frame(level);
        assertTrue(alive(level), "the sword was live when the jaws arrived");
        assertTrue(w.isDefeated(), "it died to the sword");
    }

    /** Without it, a swing at the wind-up makes it hop back; he steps in while it recovers and the next swing kills it. */
    @Test void aSwingAtItsWindUpMakesItHopBackAndTheNextOneInItsRecoveryKillsIt() {
        Level level = level();
        Wolf w = committed(level, AT_BOX_EDGE - 1);
        swing(level);
        until(level, () -> w.state() == Wolf.State.RECOVER, SECOND);
        until(level, () -> !level.player.isSwinging(), SECOND);
        float front = w.jawFrontX();
        h.held.add(Input.Keys.D);
        until(level, () -> level.player.hitboxBounds().x + level.player.hitboxBounds().width >= front - 8, SECOND * 2);
        h.held.clear();
        swing(level);
        for (int f = 0; f < 3; f++) frame(level);
        assertTrue(w.isDefeated(), "it died to the second swing");
        assertTrue(alive(level));
    }

    // ---- refuges and the wolf's reach ------------------------------------------------------------------------------------

    private boolean everCommitted(Level level, int frames) {
        Wolf w = wolf(level);
        for (int f = 0; f < frames; f++) {
            level.player.velocity().setZero();
            frame(level);
            if (w.isCommitted() || !alive(level)) return true;
        }
        return false;
    }

    @Test void theBranchAndTheTrunkTopAreRefugesTheWolfNeverCommitsTo() {
        float[][] spots = {{3960, BRANCH_TOP}, {3992, BRANCH_TOP}, {4012, BRANCH_TOP}, {(TRUNK_LEFT + TRUNK_RIGHT) / 2, BRANCH_TOP}};
        for (float[] spot : spots) {
            Level level = level();
            level.player.position.set(spot[0] - 23.5f, spot[1] - 48);
            level.player.velocity().setZero();
            assertFalse(everCommitted(level, SECOND * 12), "a hero standing on top at x " + spot[0] + ", y " + spot[1] + " is out of its reach for a whole patrol");
        }
    }

    /** The trunk's face cuts its sight at eye height: a hero on the trunk's top, or on the floor before it, is neither seen nor heard from its patrol. */
    @Test void theTrunkHidesAHeroOnItsTopAndOnTheFloorBeforeItFromTheWolfForAWholePatrol() {
        float[] bodyRights = {(TRUNK_LEFT + TRUNK_RIGHT) / 2 + 5.5f, TRUNK_LEFT - 6};   // on the trunk's top; on the floor at its left face
        for (int spot = 0; spot < bodyRights.length; spot++) {
            Level level = level();
            Wolf w = wolf(level);
            if (spot == 0) level.player.position.set(bodyRights[0] - 29, BRANCH_TOP - 48);
            else place(level, bodyRights[1]);
            level.player.velocity().setZero();
            for (int f = 0; f < SECOND * 12; f++) {
                level.player.velocity().setZero();
                frame(level);
                assertTrue(w.state() == Wolf.State.TROT || w.state() == Wolf.State.SNIFF, "it never noticed him behind the trunk (spot " + spot + "): " + w.state());
            }
        }
    }

    @Test void theFloorBeyondItsReachIsSafeAndTheFloorInItIsNot() {
        Level safe = level();
        place(safe, 4264 + 29 - 18 + 2);   // the body's left edge at 4266, past the commit box of the wolf at its range end (x 4263)
        assertFalse(everCommitted(safe, SECOND * 12), "x 4264 and on is out of its reach");
        Level inside = level();
        place(inside, 4160 + 11);
        assertTrue(everCommitted(inside, SECOND * 12), "the floor from x 4160, past the branch, is in its reach");
    }

    // ---- the exit is always reachable --------------------------------------------------------------------------------------

    @Test void aHeroWhoWaitsOnTheBranchForTheWolfToGiveUpIsNeverStuckAndItTrotsOnItsPatrol() {
        Level level = level();
        level.player.position.set(3976 - 23.5f, BRANCH_TOP - 48);
        level.player.velocity().setZero();
        Wolf w = wolf(level);
        boolean bayed = false, gaveUp = false;
        for (int f = 0; f < SECOND * 12; f++) {
            level.player.velocity().setZero();
            frame(level);
            bayed |= w.state() == Wolf.State.BAY;
            gaveUp |= bayed && w.state() == Wolf.State.TROT;
            assertTrue(alive(level));
        }
        assertTrue(bayed && gaveUp, "it stands back and bays, gives up and trots off; he may drop to the floor whenever he likes");
    }
}
