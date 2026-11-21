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
 * The wolf's first room, {@code forest-glade} (#331, D115), through the real map and real Level updates with ordinary controls.
 * World Y grows downward: the floor top is y 320, the trunk (x 3920-3952) and the one-way branch (x 3952-4016) tops y 288, the
 * exit (x 4448-4464) stands on the floor against the right wall. The wolf spawns at x 4144 (the right end of its range x 3984-4144) facing left; its
 * muzzle is at x 4143 and the hero stands to its left unless a test says otherwise. Hero positions are the frame's x (the 11 px
 * body starts 18 px right of it).
 */
class ForestGladeTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, BRANCH_TOP = 288, TRUNK_LEFT = 3920, TRUNK_RIGHT = 3952;
    private static final int SECOND = 60;
    /** Measured, pinned within 0.1 s: the death at the second thorn root on the mastered pace (the card's retry cost). */
    private static final float SECOND_ROOT_DEATH_S = 8.15f;
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

    /**
     * Drives the route; returns the seconds to the exit, or the negative seconds to the hit when the hero was hit. {@code hopAt}: a
     * careless extra jump from the trunk or the branch once the hero's frame x passes it (NaN: none), which lands him on the floor in
     * front of the wolf.
     */
    private float drive(boolean patient, float hopAt) { return drive(patient, hopAt, true); }

    private float drive(boolean patient, float hopAt, boolean secondRoot) {
        Level level = level();
        ForestGladeRoute route = new ForestGladeRoute(level, patient, secondRoot);
        boolean hopped = false;
        for (int f = 0; f < SECOND * 30; f++) {
            h.held.clear(); h.pressed.clear();
            route.drive(level, h.held, h.pressed);
            float x = level.player.position.x;
            if (!hopped && x >= hopAt && x > 3925 && level.player.isOnGround() && level.player.position.y + 48 <= BRANCH_TOP + 1) {
                h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); hopped = true;
            }
            level.update(STEP);
            if (!alive(level)) return -(f + 1) * STEP;
            if (level.atRoomExit()) return (f + 1) * STEP;
        }
        fail("the route never reached the exit");
        return 0;
    }

    @Test void theMasteredRouteStepsOffTheBranchOnToTheBayingWolfAndReachesTheExitWithinTwentySeconds() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            float seconds = drive(true, Float.NaN);
            assertTrue(seconds > 0 && seconds <= 20, "reached the exit alive in D19's 20 s (assist " + longer + "): " + seconds);
            assertEquals(8.85f, seconds, .1f, "the card's mastered time, with and without the assist (only the wind-up is longer, and the route meets none)");
        }
    }

    @Test void theStompKillsItWithTheBounceAndLeavesItsBody() {
        Level level = level();
        Wolf w = wolf(level);
        ForestGladeRoute route = new ForestGladeRoute(level);
        boolean bounced = false;
        for (int f = 0; f < SECOND * 12 && !w.isDefeated(); f++) {
            h.held.clear(); h.pressed.clear();
            route.drive(level, h.held, h.pressed);
            level.update(STEP);
            assertTrue(alive(level), "never hit before the stomp");
        }
        assertTrue(w.isDefeated(), "the stomp killed it");
        assertEquals(Wolf.State.DEAD, w.state());
        for (int f = 0; f < 3; f++) { level.update(STEP); bounced |= level.player.velocity().y < 0; }
        assertTrue(bounced, "with the normal bounce");
    }

    /**
     * The most expensive wolf death (the second thorn root after it costs more, 8.15 s): a hero who hops down off the trunk or the branch's near end lands on the floor in
     * front of the wolf and is bitten at 4.27 s; from x 3966 on, the far part of the branch, the same hop lands on it or beyond.
     */
    @Test void aHeroWhoHopsDownInFrontOfTheWolfIsBittenWithinSixSeconds() {
        for (float hopAt : new float[]{3930, 3948, 3960}) {
            float seconds = drive(false, hopAt);
            assertTrue(seconds < 0, "he is bitten (hop at x " + hopAt + ")");
            assertEquals(4.27f, -seconds, .15f, "the retry costs the walk to the trunk and the bite (hop at x " + hopAt + ")");
            assertTrue(-seconds <= 6f, "D105: a first meeting's retry is at most 6 s");
        }
    }

    /** A hero who hurries over the branch without waiting steps off its end into the wolf as it comes under him: the same stomp. */
    @Test void aHeroWhoHurriesOverTheBranchStompsItAsItSprintsUnderHim() {
        assertTrue(drive(false, Float.NaN) > 0, "the wolf is under the branch's end by the time he is: no bite, no wait");
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
        float seconds = drive(true, Float.NaN, false);
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

    @Test void aSwingAsItComesKillsItAndNothingBitesHim() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = level();
            Wolf w = committed(level, AT_BOX_EDGE - 1);
            for (int f = 0; f < SECOND / 5; f++) frame(level);
            h.held.add(Input.Keys.K); h.pressed.add(Input.Keys.K); frame(level); h.held.remove(Input.Keys.K);
            for (int f = 0; f < SECOND * 2; f++) frame(level);
            assertTrue(alive(level), "the sword was live when the jaws arrived (assist " + longer + ")");
            assertTrue(w.isDefeated(), "it died to the sword");
        }
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
        assertTrue(bayed && gaveUp, "it bays under him, gives up and trots off; he may drop to the floor whenever he likes");
    }
}
