package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Settings;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.Bramble;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creeping bramble (D89, #364) on {@code bramble-test.tmx}: room 0 is the teaching recipe (a static ladder up to a
 * shelf, the bramble rooted in the right wall over the ladder top), room 1 an open lane with the bramble rooted in a
 * left wall. World Y grows downward: the floor line is the object's bottom edge and the band runs from
 * {@code floor - 18} to {@code floor - 2}.
 */
class BrambleTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int LADDER_ROOM = 0, LANE_ROOM = 1;
    private static final float SHELF_TOP = 208, LANE_FLOOR = 320, LADDER_X = 592, RIGHT_WALL = 624, LANE_ROOT_X = 656, REACH = 64;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    @TempDir Path directory;

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level room(int index) { return harness.roomOf("bramble-test", index); }
    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void run(Level level, float seconds) { for (int i = 0, n = Math.round(seconds / STEP); i < n; i++) frame(level); }
    private void retry(Level level) { level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0)); }
    private static Bramble bramble(Level level) {
        return level.objects.stream().filter(Bramble.class::isInstance).map(Bramble.class::cast).findFirst().orElseThrow();
    }
    private static float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private void hold(int... keys) { harness.held.clear(); for (int k : keys) harness.held.add(k); }

    private void assist(boolean on) {
        Preferences prefs = new HeadlessPreferences(new FileHandle(directory.resolve("settings" + on + ".xml").toFile()));
        prefs.putBoolean(Settings.KEY_LONGER_TRAP_WARNINGS, on);
        harness.game().trapWarnings().load(new Settings(prefs));
    }

    // ---- rhythm ----

    @Test void theFixtureHoldsTheAuthoredDefaults() {
        Bramble b = bramble(room(LADDER_ROOM));
        assertEquals(4f, b.period(), 0f);
        assertEquals(1f, b.hold(), 0f);
        assertTrue(b.rootRight());
        assertEquals(0f, b.cycleTime(), 0f, "a room starts at the authored phase");
        assertEquals(Bramble.Phase.REST, b.currentPhase());
        assertFalse(bramble(room(LANE_ROOM)).rootRight());
    }

    @Test void theCycleIsRestWarningGrowHoldRetractWithTheExactDurations() {
        for (boolean on : new boolean[]{false, true}) {
            assist(on);
            Bramble b = bramble(room(LANE_ROOM));
            float warning = ExpectedWarnings.standard(on);
            assertEquals(warning, b.warningSeconds(), 1e-6f, "the warning is the shared one");
            float retract = 4f - Bramble.RETRACT_SECONDS, hold = retract - 1f, grow = hold - Bramble.GROW_SECONDS, warn = grow - warning;
            assertEquals(on ? 1.05f : 1.45f, warn, 1e-5f, "rest");
            assertEquals(2.25f, grow, 1e-5f, "rest plus warning, the open window, is the same with the assist");
            float e = 0.001f;
            assertEquals(Bramble.Phase.REST, b.phaseAt(warn - e));
            assertEquals(Bramble.Phase.WARNING, b.phaseAt(warn + e));
            assertEquals(Bramble.Phase.WARNING, b.phaseAt(grow - e));
            assertEquals(Bramble.Phase.GROW, b.phaseAt(grow + e));
            assertEquals(Bramble.Phase.GROW, b.phaseAt(hold - e));
            assertEquals(Bramble.Phase.HOLD, b.phaseAt(hold + e));
            assertEquals(Bramble.Phase.HOLD, b.phaseAt(retract - e));
            assertEquals(Bramble.Phase.RETRACT, b.phaseAt(retract + e));
            assertEquals(Bramble.Phase.RETRACT, b.phaseAt(4f - e));
            assertEquals(Bramble.Phase.REST, b.phaseAt(0f));
        }
        assist(false);
    }

    @Test void theWarningLastsTheSharedWarningInPlayBothWays() {
        for (boolean on : new boolean[]{false, true}) {
            assist(on);
            Level level = room(LANE_ROOM);
            Bramble b = bramble(level);
            int warningFrames = 0;
            for (int i = 0; i < 4 * 60; i++) { frame(level); if (b.currentPhase() == Bramble.Phase.WARNING) warningFrames++; }
            assertEquals(ExpectedWarnings.standard(on), warningFrames * STEP, 2 * STEP, "assist=" + on);
        }
        assist(false);
    }

    @Test void theCuesFireOncePerCycleAtTheWarningAndTheGrow() {
        Level level = room(LANE_ROOM);
        Bramble b = bramble(level);
        int[] warnings = new int[1], grows = new int[1];
        b.onCues(() -> warnings[0]++, () -> grows[0]++);
        run(level, 3 * 4f);
        assertEquals(3, warnings[0]);
        assertEquals(3, grows[0]);
    }

    @Test void theTipRunsOutInTheGrowAndBackInTheRetract() {
        Bramble b = bramble(room(LANE_ROOM));
        float grow = 2.25f, hold = 2.5f, retract = 3.5f;
        assertEquals(Bramble.CLUMP_REACH, b.extentAt(1f), 0f, "rest: the clump only");
        assertEquals(Bramble.CLUMP_REACH, b.extentAt(2f), 0f, "warning: the clump only");
        assertEquals(Bramble.CLUMP_REACH + (REACH - Bramble.CLUMP_REACH) / 2f, b.extentAt(grow + 0.125f), 1e-3f, "half way through the grow");
        assertEquals(REACH, b.extentAt(hold + 0.5f), 0f);
        assertEquals(Bramble.CLUMP_REACH + (REACH - Bramble.CLUMP_REACH) / 2f, b.extentAt(retract + 0.25f), 1e-3f, "half way back");
        assertTrue((REACH - Bramble.CLUMP_REACH) / Bramble.GROW_SECONDS > 200f, "the tip outruns the 100 px/s hero");
    }

    // ---- hitbox (world Y grows downward) ----

    @Test void theLethalBandSitsOnTheFloorLineAndGrowsFromTheRoot() {
        Level level = room(LANE_ROOM);
        Bramble b = bramble(level);
        Rectangle band = b.lethalBoundsInto(new Rectangle());
        assertEquals(LANE_FLOOR - 18f, band.y, 0f, "the band's top is 18 px above the floor line (a smaller y is higher)");
        assertEquals(LANE_FLOOR - 2f, band.y + band.height, 0f, "its bottom 2 px above the floor line");
        assertEquals(LANE_ROOT_X, band.x, 0f, "a left root: the band starts at the wall face");
        assertEquals(Bramble.CLUMP_REACH, band.width, 0f);
        run(level, 3f); // hold
        band = b.lethalBoundsInto(band);
        assertEquals(LANE_ROOT_X, band.x, 0f);
        assertEquals(REACH, band.width, 0f);
        Bramble right = bramble(room(LADDER_ROOM));
        Rectangle full = right.fullBounds();
        assertEquals(RIGHT_WALL - REACH, full.x, 0f, "a right root grows leftward from the right wall face at x 624");
        assertEquals(SHELF_TOP - 18f, full.y, 0f);
        assertEquals(SHELF_TOP - 2f, full.y + full.height, 0f);
    }

    @Test void aStandingHeroIsHitOnlyWhereTheThornsAreNow() {
        Level level = room(LANE_ROOM);
        Bramble b = bramble(level);
        Rectangle standingIn = new Rectangle(LANE_ROOT_X + 40, LANE_FLOOR - HeroBody.H, HeroBody.W, HeroBody.H);
        Rectangle atClump = new Rectangle(LANE_ROOT_X + 4, LANE_FLOOR - HeroBody.H, HeroBody.W, HeroBody.H);
        Rectangle beyond = new Rectangle(LANE_ROOT_X + REACH + 0.5f, LANE_FLOOR - HeroBody.H, HeroBody.W, HeroBody.H);
        Rectangle overTheTips = new Rectangle(LANE_ROOT_X + 40, LANE_FLOOR - 18 - HeroBody.H - 0.5f, HeroBody.W, HeroBody.H);
        for (float t = 0f; t < 4f; t += 0.05f) {
            Bramble.Phase p = b.currentPhase();
            assertFalse(b.lethalTo(beyond), "never beyond the reach");
            assertFalse(b.lethalTo(overTheTips), "a body above the band's top is safe");
            if (p == Bramble.Phase.REST || p == Bramble.Phase.WARNING) assertFalse(b.lethalTo(standingIn), "open at t=" + t);
            if (p == Bramble.Phase.HOLD) assertTrue(b.lethalTo(standingIn), "closed at t=" + t);
            assertTrue(b.lethalTo(atClump), "the clump is lethal at t=" + t);
            run(level, 0.05f);
        }
    }

    @Test void aHeroStandingInTheLaneDiesAtTheGrowAndTheRetryRestoresThePhase() {
        Level level = room(LANE_ROOM);
        level.player.position.set(LANE_ROOT_X + 40 - level.player.hitbox.offsetX, LANE_FLOOR - 48);
        run(level, 2.2f);
        assertFalse(level.player.isGotHit(), "rest and warning are safe");
        run(level, 0.3f);
        assertTrue(level.player.isGotHit(), "the grown arm kills");
        retry(level);
        assertFalse(level.player.isGotHit());
        assertEquals(0f, bramble(level).cycleTime(), 0f, "the retry restarts at the authored phase");
        Level again = room(LANE_ROOM);
        assertEquals(0f, bramble(again).cycleTime(), 0f, "Restart Level rebuilds it at the authored phase");
    }

    @Test void pauseFreezesTheClock() {
        // The game stops calling Level.update while paused; the bramble reads no clock but the dt it is given.
        Level level = room(LANE_ROOM);
        run(level, 1.7f);
        float before = bramble(level).cycleTime();
        assertEquals(before, bramble(level).cycleTime(), 0f);
        run(level, 0.1f);
        assertEquals(before + 0.1f, bramble(level).cycleTime(), 2e-3f, "it continues from where it stopped");
    }

    @Test void theBrokenFixturesFailTheLoadWithTheReason() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> harness.roomOf("bramble-bad-period-test", 0)).getMessage().contains("shorter than"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> harness.roomOf("bramble-no-face-test", 0)).getMessage().contains("solid face"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> harness.roomOf("bramble-covers-exit-test", 0)).getMessage().contains("room exit"));
    }

    @Test void thePlacementMergesSplitFacesAndFloorsWithinTheTolerance() {
        Rectangle box = new Rectangle(16, 296, 64, 24);
        java.util.List<Rectangle> splitFace = java.util.List.of(new Rectangle(0, 0, 16, 306), new Rectangle(0, 306, 16, 14));
        java.util.List<Rectangle> splitFloor = java.util.List.of(new Rectangle(0, 320, 72, 48), new Rectangle(72, 320, 568, 48));
        assertNull(Bramble.placementProblem(box, false, splitFace, splitFloor), "a face and a floor in two pieces still count");
        assertNull(Bramble.placementProblem(box, false, java.util.List.of(new Rectangle(0.4f, 0, 16, 320)),
            java.util.List.of(new Rectangle(0, 320.3f, 640, 48))), "a sub-pixel Tiled offset still counts");
        assertTrue(Bramble.placementProblem(box, false, splitFace,
            java.util.List.of(new Rectangle(0, 320, 72, 48), new Rectangle(80, 320, 560, 48))).contains("needs floor"), "an 8 px gap does not");
        assertTrue(Bramble.placementProblem(box, false, java.util.List.of(new Rectangle(0, 0, 16, 306)), splitFloor).contains("root edge"),
            "a face that stops above the band's bottom does not");
    }

    @Test void theRuleNeedsRestWithTheAssistedWarning() {
        assertEquals(3.75f, Bramble.minimumPeriod(1f), 1e-5f);
        assertNull(Bramble.validate(64, 24, 3.75f, 1f));
        assertNotNull(Bramble.validate(64, 24, 3.7f, 1f));
        assertNotNull(Bramble.validate(64, 24, 4f, 1.6f));
        assertNotNull(Bramble.validate(112, 24, 4f, 1f));
        assertNotNull(Bramble.validate(64, 16, 4f, 1f));
    }

    // ---- the ladder exit: wait on the ladder, climb out in the window ----

    private Ladder ladder(Level level) { return level.ladders.get(0); }

    private void walkToLadder(Level level) {
        for (int i = 0; level.player.hitboxBounds().x + level.player.hitboxBounds().width / 2 < LADDER_X + 1; i++) {
            hold(Input.Keys.D); frame(level);
            assertFalse(level.player.isGotHit(), "died walking at " + level.player.position);
            assertTrue(i < 60 * 20, "ladder unreachable from " + level.player.position);
        }
    }

    /** Holds Up until the hero's head is just under the band's bottom: the waiting place on the ladder. */
    private void climbToWait(Level level) {
        for (int i = 0; level.player.hitboxBounds().y > SHELF_TOP - 2 + 2; i++) {
            hold(Input.Keys.W); frame(level);
            assertTrue(level.player.onLadder() || i < 30, "never grabbed the ladder at " + level.player.position);
            assertFalse(level.player.isGotHit(), "died on the ladder at " + level.player.position);
            assertTrue(i < 60 * 10, "climb stuck at " + level.player.position);
        }
        hold();
    }

    /** Climbs out and runs left to the exit; false if the bramble kills on the way. */
    private boolean climbOutAndRun(Level level) {
        for (int i = 0; !level.atRoomExit(); i++) {
            boolean up = level.player.onLadder() || feet(level) > SHELF_TOP + 1;
            hold(up ? Input.Keys.W : Input.Keys.A);
            frame(level);
            if (level.player.isGotHit()) { hold(); return false; }
            assertTrue(i < 60 * 15, "exit unreachable from " + level.player.position);
        }
        hold();
        return true;
    }

    @Test void aStaticLadderStandsWhereItIsAuthoredAndCarriesTheHeroUp() {
        Level level = room(LADDER_ROOM);
        assertEquals(SHELF_TOP, ladder(level).topY(), 0f, "no lever and no drop: the ladder is down from the start");
        walkToLadder(level);
        climbToWait(level);
        assertTrue(level.player.onLadder(), "the hero waits on the ladder");
        Rectangle body = level.player.hitboxBounds();
        assertFalse(bramble(level).fullBounds().overlaps(body), "the waiting place is under the band, safe at every phase");
        run(level, 8f);
        assertFalse(level.player.isGotHit(), "two whole cycles waited on the ladder");
        assertTrue(level.player.onLadder(), "the hero stays on the ladder while waiting");
    }

    @Test void aHeroWhoWaitsForTheRetractAlwaysGetsOutAtEveryPhaseOffset() {
        Level level = room(LADDER_ROOM);
        for (float offset = 0f; offset < 4f; offset += 0.05f) {
            retry(level);
            hold(); run(level, offset);
            walkToLadder(level);
            climbToWait(level);
            Bramble b = bramble(level);
            // Watch: wait for the arm to come back (the retract ends), then go.
            for (int i = 0; b.currentPhase() != Bramble.Phase.RETRACT; i++) { frame(level); assertTrue(i < 5 * 60); }
            for (int i = 0; b.currentPhase() == Bramble.Phase.RETRACT; i++) { frame(level); assertTrue(i < 60); }
            assertTrue(climbOutAndRun(level), "offset " + offset + " died at " + level.player.position);
        }
    }

    @Test void climbingOutBlindIsADeathForSomeOffsetsAndTheWindowIsWide() {
        Level level = room(LADDER_ROOM);
        int n = Math.round(4f / 0.05f), safe = 0, best = 0;
        boolean[] ok = new boolean[n];
        for (int k = 0; k < n; k++) {
            retry(level);
            walkToLadder(level);
            climbToWait(level);
            hold(); run(level, k * 0.05f);
            ok[k] = climbOutAndRun(level);
            if (ok[k]) safe++;
        }
        for (int start = 0; start < n; start++) {
            int len = 0;
            while (len < n && ok[(start + len) % n]) len++;
            best = Math.max(best, len);
        }
        System.out.printf("BRAMBLE blind ladder exit: %d of %d offsets survive, longest safe run %.2f s%n", safe, n, best * 0.05f);
        assertTrue(safe < n, "the bramble must matter: some blind exits die");
        assertTrue(best * 0.05f >= 1.0f, "a safe window of at least 1 s for a blind exit; measured " + best * 0.05f + " s");
    }

    @Test void theWarningIsTheSharedConstant() {
        assertEquals(0.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f);
    }
}
