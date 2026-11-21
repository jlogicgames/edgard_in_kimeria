package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest wolf (#331, D113), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixture wolf-test.tmx: room 1280 wide, floor y 320, hero enters at x 32; the wolf spawns at x 640 facing left (frame
 * x 640..688, body x 646..680, muzzle at x 639), offNeg 6 and offPos 6, so its {@code position.x} stays in 544..736. Facing
 * left its sight runs from x 639 to x 383, from y 272 to the floor; its lunge box from x 567 to its back. The hero's body is
 * 11x22, its right edge at {@code place}'s argument; it stands to the wolf's left, so every gap below is measured from the
 * muzzle to the hero's near (right) edge.
 */
class WolfTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, LEFT_END = 544, RIGHT_END = 736, SPAWN = 640;
    /** Gaps (px, muzzle to the hero's near edge): inside the lunge box; at its edge. */
    private static final float IN_BOX = 70, AT_BOX_EDGE = 71;
    private static final int SECOND = 60;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level() { return harness.roomOf("wolf-test", 0); }

    private static Wolf wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    /** Stands the hero (body right edge at {@code right}) on the floor, still, facing right. */
    private void place(Level level, float right) {
        level.player.position.set(right - 29, FLOOR - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    /** Stands the hero {@code gap} px from the muzzle of the wolf (which faces left), on its left, still. */
    private void placeAt(Level level, Wolf w, float gap) { place(level, w.jawFrontX() - gap); }

    private void press(Level level, int key) { harness.held.add(key); harness.pressed.add(key); frame(level); harness.held.remove(key); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(harness.game().trapWarnings(), longer);
    }

    private int until(Level level, java.util.function.BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    /** The hero stands {@code gap} px ahead, still; the wolf notices, chases and commits: it is winding up. */
    private Wolf committed(Level level, float gap) {
        Wolf w = wolf(level);
        placeAt(level, w, gap);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        return w;
    }

    // ---- patrol, senses, notice ------------------------------------------------------------------------------------------

    @Test void itTrotsAtFortyPixelsASecondSniffsAtEachEndAndIsSilentWhenCalm() {
        Level level = level();
        Wolf w = wolf(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        boolean sniffed = false;
        float before = w.position.x;
        frame(level);
        assertEquals(Wolf.PATROL_SPEED * STEP, before - w.position.x, .01f, "it trots at 40 px/s");
        for (int f = 0; f < SECOND * 40; f++) {
            frame(level);
            min = Math.min(min, w.position.x); max = Math.max(max, w.position.x);
            sniffed |= w.state() == Wolf.State.SNIFF;
            assertTrue(w.state() == Wolf.State.TROT || w.state() == Wolf.State.SNIFF, w.state().toString());
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertTrue(sniffed, "it stops at an end before it turns");
        assertTrue(harness.sounds.isEmpty(), "a calm wolf is silent");
    }

    @Test void itSniffsForPointSixSecondsAtAnEndThenTurns() {
        Level level = level();
        Wolf w = wolf(level);
        until(level, () -> w.state() == Wolf.State.SNIFF, SECOND * 6);
        assertFalse(w.facingRight);
        int frames = until(level, () -> w.state() == Wolf.State.TROT, SECOND);
        assertEquals(Wolf.SNIFF_SECONDS, frames * STEP, STEP * 1.5f);
        assertTrue(w.facingRight, "it turns round");
    }

    @Test void itSeesTwoHundredFiftySixPixelsAheadAndNotBeyond() {
        Level level = level();
        Wolf w = wolf(level);
        place(level, w.jawFrontX() - Wolf.SIGHT_RANGE + 11 + 1);   // the hero's body 12..1 px inside the end of its sight
        frame(level);
        assertEquals(Wolf.State.NOTICE, w.state());
        Level far = level();
        Wolf f = wolf(far);
        place(far, f.jawFrontX() - Wolf.SIGHT_RANGE - 1);   // a pixel beyond it
        settle(far, 2);
        assertEquals(Wolf.State.TROT, f.state(), "beyond 256 px it does not see him");
    }

    @Test void itSeesOnlyAheadAndHearsACloseCircle() {
        Level level = level();
        Wolf w = wolf(level);
        float behind = w.position.x + 23 + Wolf.HEAR_RADIUS + 20;   // centre 20 px outside the circle, behind it
        place(level, behind + 5.5f);
        settle(level, 30);
        assertEquals(Wolf.State.TROT, w.state(), "a hero behind it, beyond its hearing, is not noticed");
        assertTrue(harness.sounds.isEmpty());

        Level heard = level();
        Wolf h = wolf(heard);
        place(heard, h.position.x + 23 + Wolf.HEAR_RADIUS - 4 + 5.5f);   // centre 4 px inside the circle, behind it
        frame(heard);
        assertEquals(Wolf.State.NOTICE, h.state(), "a hero within 64 px is heard, whichever side he is on");
        assertTrue(h.facingRight, "it turns to face him");
    }

    @Test void aSolidAtItsEyeHeightCutsItsSight() {
        Level open = level();
        Wolf o = wolf(open);
        place(open, 460);   // 179 px ahead
        frame(open);
        assertEquals(Wolf.State.NOTICE, o.state(), "seen across open floor");
        Level covered = harness.roomOf("wolf-cover", 0);   // a block at x 480..496, 32 px high
        Wolf c = wolf(covered);
        place(covered, 460);
        settle(covered, 30);
        assertEquals(Wolf.State.TROT, c.state(), "the block cuts its sight");
    }

    @Test void itNoticesInBodyLanguageForHalfASecondNotScaledByTheAssistWithItsOwnCall() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            Wolf w = wolf(level);
            place(level, 460);
            frame(level);
            assertEquals(Wolf.State.NOTICE, w.state());
            assertEquals(List.of("wolf_notice"), harness.sounds, "its own call, no shared alert sound");
            float x = w.position.x;
            int frames = until(level, () -> w.state() != Wolf.State.NOTICE, SECOND);
            assertEquals(Wolf.NOTICE_TOTAL, frames * STEP, STEP * 1.5f, "the notice lasts 0.5 s");
            assertEquals(.5f, Wolf.NOTICE_TOTAL, 1e-6f);
            assertEquals(x, w.position.x, .01f, "it does not move while it notices");
            assertEquals(Wolf.State.CHASE, w.state());
        }
    }

    // ---- the chase -------------------------------------------------------------------------------------------------------

    @Test void itSprintsAtOneHundredSixtyReachingItInAQuarterSecondBoundToItsRange() {
        Level level = level();
        Wolf w = wolf(level);
        place(level, 460);
        until(level, () -> w.state() == Wolf.State.CHASE, SECOND);
        float max = 0;
        int toFull = -1;
        float last = w.position.x;
        for (int f = 1; f < SECOND * 3 && w.state() == Wolf.State.CHASE; f++) {
            frame(level);
            float v = (last - w.position.x) / STEP;
            max = Math.max(max, v);
            if (toFull < 0 && w.speed() >= Wolf.CHASE_SPEED - .01f) toFull = f;
            last = w.position.x;
        }
        assertEquals(Wolf.CHASE_SPEED, max, 1.5f, "the sprint tops out at 160 px/s");
        assertEquals(.25f, toFull * STEP, STEP * 2, "full speed in a quarter of a second (640 px/s^2)");
        settle(level, SECOND * 2);
        assertTrue(w.position.x >= LEFT_END - .01f, "it never leaves its range on the chase: " + w.position.x);
    }

    @Test void itCommitsWhenTheHeroTouchesTheLungeBoxSeventyTwoPixelsFromItsMuzzle() {
        Level level = level();
        Wolf w = wolf(level);
        placeAt(level, w, 120);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        float gap = (w.jawFrontX() - 0) - (level.player.hitboxBounds().x + level.player.hitboxBounds().width);
        assertTrue(gap <= Wolf.COMMIT_REACH + .01f && gap > Wolf.COMMIT_REACH - 4f, "it commits as the gap closes to 72 px: " + gap);
        float x = w.position.x;
        settle(level, 10);
        assertEquals(x, w.position.x, .001f, "it commits on the spot");
    }

    @Test void aHeroAboveTheLungeBoxDoesNotMakeItCommit() {
        Level level = level();
        Wolf w = wolf(level);
        placeAt(level, w, 100);
        level.player.position.y -= Wolf.COMMIT_HEIGHT + 4;   // feet 28 px up: above the box
        for (int f = 0; f < SECOND * 2; f++) {
            level.player.velocity().setZero();
            level.player.position.y = FLOOR - 48 - Wolf.COMMIT_HEIGHT - 4;
            frame(level);
            assertNotEquals(Wolf.State.WINDUP, w.state(), "an airborne or perched hero above 24 px is out of its reach");
        }
    }

    // ---- the wind-up and the lunge -----------------------------------------------------------------------------------------

    @Test void itWindsUpForTheSharedBiteWarningSampledOnceAndDrawsTheLine() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            Wolf w = committed(level, IN_BOX);
            assertEquals(ExpectedWarnings.bite(longer), w.warningTotal(), 1e-6f);
            assertEquals(longer ? .75f : .5f, w.warningTotal(), 1e-6f);
            assertTrue(harness.sounds.contains(longer ? "wolf_windup_long" : "wolf_windup"), harness.sounds.toString());
            assertFalse(w.facingRight, "it faces the hero");
            float from = w.position.x;
            assertEquals(from - Wolf.LUNGE_REACH, w.lungeTargetX(), .01f, "the line it draws is its whole reach");
            int toLunge = until(level, () -> w.state() == Wolf.State.LUNGE, SECOND * 2);
            assertEquals(w.warningTotal(), toLunge * STEP, STEP * 1.5f, "the lunge starts when the warning ends");
            assertTrue(level.wolfFx().hard().count() > 0, "breath streaks were drawn along the line");
            assertEquals(from, w.position.x, STEP * Wolf.LUNGE_SPEED + .01f, "it did not move during the wind-up");
        }
    }

    @Test void theLungeIsOneHundredSixtyPixelsInAQuarterSecondAlongTheLockedLineAndTheBiteIsSeenAndHeardHitOrMiss() {
        Level level = level();
        Wolf w = committed(level, IN_BOX);
        float from = w.position.x;
        until(level, () -> w.state() == Wolf.State.LUNGE, SECOND * 2);
        assertTrue(harness.sounds.contains("wolf_lunge"));
        // The hero walks behind it during the lunge: the line does not change (direction, not a spot).
        level.player.position.set(900, FLOOR - 48);
        int frames = until(level, () -> w.state() == Wolf.State.BITE, SECOND);
        assertEquals(Wolf.LUNGE_SECONDS, frames * STEP, STEP * 1.5f, "the lunge takes 0.25 s");
        assertEquals(Wolf.LUNGE_REACH, from - w.position.x, .5f, "it covers 160 px, left, along the line the wind-up drew");
        assertEquals(FLOOR, w.floorY(), .01f, "low over its floor");
        assertTrue(harness.sounds.contains("wolf_bite"), "heard hit or miss (D93)");
        assertTrue(level.wolfFx().soft().count() > 0, "and seen: dust and two jaw glints");
        assertTrue(alive(level), "a miss");
    }

    @Test void theJawsAreSweptEveryFrameSoNoFrameSkipsOverTheHero() {
        for (float dt : new float[]{1f / 30f, 1f / 60f, 1f / 144f}) {
            Level level = level();
            Wolf w = wolf(level);
            placeAt(level, w, 40);
            int guard = 0;
            while (alive(level) && guard++ < 400) level.update(dt);
            assertFalse(alive(level), "the lunge reaches a hero standing 40 px from the muzzle at dt " + dt);
        }
    }

    @Test void contactIsHarmlessOnlyTheJawsKill() {
        Level level = level();
        Wolf w = wolf(level);
        Rectangle body = w.hitboxBounds();
        place(level, body.x + body.width - 4);   // the hero's body overlaps the wolf's rear, behind the jaws
        for (int f = 0; f < SECOND * 4; f++) {
            level.player.velocity().setZero();
            frame(level);
            assertTrue(alive(level), "touching a wolf never kills: only the jaws do (frame " + f + ", " + w.state() + ")");
        }
    }

    // ---- what saves the hero, and what does not ----------------------------------------------------------------------------

    /** Wound up at a hero {@code gap} px away, who runs away (left) from the commit on, as fast as he can. True when it bit him. */
    private boolean runnerIsBitten(boolean longer, float gap) throws Exception {
        setLonger(longer);
        Level level = level();
        Wolf w = committed(level, gap);
        harness.held.add(Input.Keys.A);
        for (int f = 0; f < SECOND * 3; f++) {
            frame(level);
            if (!alive(level)) return true;
        }
        return false;
    }

    @Test void aHeroWhoRunsFromTheCommitIsBittenWithoutTheAssistAndMissedWithIt() throws Exception {
        assertTrue(runnerIsBitten(false, AT_BOX_EDGE), "outrunning is not an answer: it commits at 72 px and the jaws outreach him");
        assertFalse(runnerIsBitten(true, AT_BOX_EDGE), "with the 0.75 s warning, running works again");
        assertTrue(runnerIsBitten(false, 40), "nearer is no safer without the assist");
    }

    @Test void aHeroWhoStandsStillIsBittenWhateverTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = level();
            committed(level, IN_BOX);
            settle(level, SECOND * 2);
            assertFalse(alive(level), "standing still is always a bite (assist " + longer + ")");
        }
    }

    /** A long range (wolf-long.tmx: x 480..896) so a runner stays inside it: the wolf spawns at x 800 facing left. */
    @Test void aRunnerWhoSurvivesTheLungeThroughTheAssistIsStruckAgainAfterTheRecovery() throws Exception {
        setLonger(true);
        Level level = harness.roomOf("wolf-long", 0);
        Wolf w = wolf(level);
        place(level, w.jawFrontX() - AT_BOX_EDGE);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        harness.held.add(Input.Keys.A);   // from the commit on, he runs
        until(level, () -> w.state() == Wolf.State.LUNGE, SECOND * 2);
        int sinceLunge = 0;
        until(level, () -> w.state() == Wolf.State.RECOVER, SECOND * 2);
        assertTrue(alive(level), "the first lunge missed him");
        for (; w.state() != Wolf.State.LUNGE && sinceLunge < SECOND * 6; sinceLunge++) frame(level);
        assertEquals(Wolf.State.LUNGE, w.state(), "it struck again while he was still running");
        float seconds = Wolf.LUNGE_SECONDS + Wolf.BITE_SECONDS + sinceLunge * STEP;
        assertEquals(2.0f, seconds, .4f, "the second lunge begins about 2 s after the first began: the 0.5 s recovery leaves him 72 px ahead, it closes the last px and winds up 0.75 s");
    }

    // ---- recovery ---------------------------------------------------------------------------------------------------------

    @Test void afterTheBiteItRecoversHarmlessForHalfASecondNotScaledByTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = level();
            Wolf w = committed(level, IN_BOX);
            level.player.position.set(900, FLOOR - 48);
            until(level, () -> w.state() == Wolf.State.RECOVER, SECOND * 2);
            assertFalse(w.isCommitted());
            // The hero stands in front of it, in its jaws' old place, while it shakes its head: harmless.
            placeAt(level, w, 0);
            int frames = until(level, () -> w.state() != Wolf.State.RECOVER, SECOND);
            assertEquals(Wolf.RECOVER_SECONDS, frames * STEP, STEP * 2, "the recovery lasts 0.5 s whatever the assist");
            assertTrue(alive(level), "harmless");
        }
    }

    // ---- the jump always answers the lunge --------------------------------------------------------------------------------

    /**
     * Wound up at a hero {@code gap} px away, who jumps ({@code J}) {@code takeoff} frames after the commit and otherwise stands
     * still. True when the first lunge and bite were over without touching him.
     */
    private boolean jumpClears(boolean longer, float gap, int takeoff) throws Exception {
        setLonger(longer);
        Level level = level();
        Wolf w = committed(level, gap);
        for (int f = 0; f < SECOND * 3; f++) {
            if (f == takeoff) { harness.held.add(Input.Keys.J); harness.pressed.add(Input.Keys.J); }
            frame(level);
            harness.held.remove(Input.Keys.J);
            if (!alive(level)) return false;
            if (w.state() == Wolf.State.RECOVER) return true;
        }
        return alive(level);
    }

    /** The first and the last take-off frames (counted from the commit) at which the jump clears the jaws: the window is contiguous. */
    private int[] clearingWindow(boolean longer, float gap) throws Exception {
        int first = -1, last = -1;
        for (int takeoff = 0; takeoff < SECOND * 2; takeoff++) {
            boolean clear = jumpClears(longer, gap, takeoff);
            if (clear && first < 0) first = takeoff;
            if (clear) last = takeoff;
            else if (first >= 0) break;
        }
        assertTrue(first >= 0, "some jump clears it");
        return new int[]{first, last};
    }

    @Test void aJumpTimedUpToHalfASecondAfterTheCommitClearsTheJaws() throws Exception {
        int edge = clearingWindow(false, AT_BOX_EDGE)[1], muzzle = clearingWindow(false, 1)[1];
        // On paper (D113): the jaws reach a hero G px away G/640 s into the lunge and pass him 18 px later; the feet stay above the
        // jaws (15 px) from 0.064 s to 0.84 s after take-off: the latest take-off is 0.55 s after the commit at 71 px, 0.44 s at the muzzle.
        assertEquals(.55f, edge * STEP, .08f, "at the edge of its box the latest clearing take-off is about 0.55 s after the commit: " + edge);
        assertEquals(.44f, muzzle * STEP, .08f, "at its muzzle it is about 0.44 s: " + muzzle);
        assertEquals(0, clearingWindow(false, AT_BOX_EDGE)[0], "a jump at the commit itself is still in the air when the jaws pass");
        assertFalse(jumpClears(false, AT_BOX_EDGE, edge + 2), "a late jump is bitten");
    }

    @Test void withTheAssistTheWindowOpensAndClosesAQuarterSecondLater() throws Exception {
        int[] plain = clearingWindow(false, AT_BOX_EDGE), longer = clearingWindow(true, AT_BOX_EDGE);
        assertEquals(.25f, (longer[1] - plain[1]) * STEP, STEP * 2, "the longer warning adds its quarter second to the latest take-off");
        assertTrue(longer[0] > 0, "and a jump made at the very commit has landed before the jaws arrive: the jump is timed, not spammed");
    }

    @Test void aJumpDuringItsSprintIsNeverCommittedToAndItRunsUnderneathTheHero() {
        Level level = harness.roomOf("wolf-long", 0);   // range x 480..896, so it can pass under a hero at x 653
        Wolf w = wolf(level);
        place(level, w.jawFrontX() - 140);
        until(level, () -> w.state() == Wolf.State.CHASE, SECOND * 2);
        boolean under = false;
        for (int f = 0; f < SECOND * 2; f++) {
            if (f == 24) { harness.held.add(Input.Keys.J); harness.pressed.add(Input.Keys.J); }   // the sprint has begun: he jumps
            frame(level);
            harness.held.remove(Input.Keys.J);
            Rectangle hero = level.player.hitboxBounds();
            float lift = FLOOR - (hero.y + hero.height);
            if (w.state() == Wolf.State.WINDUP && lift > Wolf.COMMIT_HEIGHT) fail("it committed to a hero more than 24 px up");
            under |= lift > Wolf.COMMIT_HEIGHT && Math.abs(w.centreX() - (hero.x + hero.width / 2)) < 6;
        }
        assertTrue(under, "it ran underneath him, in the air above it, without committing");
        assertTrue(alive(level));
    }

    // ---- the sword, a fireball and a stone ---------------------------------------------------------------------------------

    @Test void aSwingLiveWhenTheJawsArriveKillsItAndTheHeroLives() {
        Level level = level();
        Wolf w = committed(level, AT_BOX_EDGE);
        settle(level, 12);
        press(level, Input.Keys.K);   // 0.2 s after the commit: the swing (0.7 s) is live when the jaws arrive
        settle(level, SECOND * 2);
        assertTrue(alive(level));
        assertTrue(w.isDefeated());
        assertTrue(w.isRemains(), "and the swing that killed it leaves its body (D101)");
    }

    @Test void aSwingStartedTooEarlyEndsBeforeTheJawsAndIsABite() {
        Level level = level();
        Wolf w = wolf(level);
        placeAt(level, w, 110);
        press(level, Input.Keys.K);   // in the notice: the swing is over long before the lunge reaches him
        settle(level, SECOND * 4);
        assertFalse(alive(level), "a swing that ends before the jaws arrive is a bite");
        assertFalse(w.isDefeated());
    }

    /** The frame on which the jaws reach a hero standing 70 px away, counted from the commit. */
    private int biteFrame() {
        Level level = level();
        Wolf w = committed(level, IN_BOX);
        Rectangle jaws = new Rectangle();
        for (int f = 1; f < SECOND * 2; f++) {
            frame(level);
            if ((w.state() == Wolf.State.LUNGE || w.state() == Wolf.State.BITE) && w.jawBoxInto(jaws).overlaps(level.player.hitboxBounds())) {
                assertTrue(alive(level), "the bite is due, not landed");
                frame(level);
                assertFalse(alive(level), "it lands on the next frame");
                return f;
            }
        }
        fail("the jaws never reached the hero");
        return -1;
    }

    /** A fireball cast on the frame the jaws arrive flies its first step on that frame, so it wins the tie (#503). */
    @Test void aFireballCastOnTheFrameTheJawsArriveWinsTheSameFrameTie() {
        int due = biteFrame();
        Level level = level();
        Wolf w = committed(level, IN_BOX);
        for (int f = 1; f < due; f++) frame(level);
        press(level, Input.Keys.L);
        assertTrue(w.isDefeated(), "the ball's first step lands on its cast frame, before the bite");
        settle(level, 30);
        assertTrue(alive(level), "the fireball wins the same-frame tie");
        assertTrue(w.isRemains(), "a quiet kill: the body stays (D101)");
    }

    private void carryTheStone(Level level) {
        place(level, 108);
        harness.held.add(Input.Keys.W); frame(level);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level);
        harness.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked up the stone");
    }

    /** A stone thrown on the frame the jaws arrive flies its first step on that frame, so it wins the tie like a fireball (#505). */
    @Test void aStoneThrownOnTheFrameTheJawsArriveWinsTheSameFrameTie() {
        Level probe = level();
        carryTheStone(probe);
        Wolf p = wolf(probe);
        placeAt(probe, p, IN_BOX);
        until(probe, () -> p.state() == Wolf.State.WINDUP, SECOND * 3);
        Rectangle jaws = new Rectangle();
        int due = until(probe, () -> (p.state() == Wolf.State.LUNGE || p.state() == Wolf.State.BITE) && p.jawBoxInto(jaws).overlaps(probe.player.hitboxBounds()), SECOND * 2);
        frame(probe);
        assertFalse(alive(probe), "with no throw the bite lands on the next frame");
        Level level = level();
        carryTheStone(level);
        Wolf w = wolf(level);
        placeAt(level, w, IN_BOX);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        for (int f = 0; f < due - 1; f++) frame(level);   // the press below is the frame the jaws arrive
        assertFalse(w.isDefeated());
        press(level, Input.Keys.L);
        assertTrue(w.isDefeated(), "the stone's first step lands on its release frame, before the bite");
        settle(level, 30);
        assertTrue(alive(level), "the stone wins the same-frame tie");
        assertTrue(w.isRemains());
    }

    @Test void aStompKillsItWithTheNormalBounce() {
        Level level = level();
        Wolf w = wolf(level);
        harness.held.clear();
        level.player.position.set(w.centreX() - 5.5f - 18, 222);   // feet 32 px above its body's top, falling onto it
        level.player.velocity().setZero();
        boolean bounced = false;
        for (int f = 0; f < SECOND && !w.isDefeated(); f++) frame(level);
        assertTrue(w.isDefeated(), "a stomp kills it");
        assertTrue(w.isRemains());
        frame(level);
        bounced = level.player.velocity().y < 0;
        assertTrue(bounced, "with the normal bounce");
        assertTrue(alive(level));
        assertTrue(harness.sounds.contains("bounce"), "the shared kill sound");
    }

    // ---- killed in any state -----------------------------------------------------------------------------------------------

    private Wolf inState(Level level, Wolf.State wanted) {
        Wolf w = wolf(level);
        switch (wanted) {
            case TROT -> { }
            case NOTICE -> { placeAt(level, w, 120); frame(level); }
            case CHASE -> { placeAt(level, w, 160); until(level, () -> w.state() == Wolf.State.CHASE, SECOND * 2); }
            case WINDUP -> committed(level, IN_BOX);
            case LUNGE, BITE, RECOVER -> {
                committed(level, IN_BOX);
                level.player.position.set(900, FLOOR - 48);
                until(level, () -> w.state() == wanted, SECOND * 2);
            }
            default -> fail("not a state of this helper: " + wanted);
        }
        assertEquals(wanted, w.state());
        return w;
    }

    @Test void everyWeaponKillsItInEveryStateAndItLeavesItsBody() {
        for (Wolf.State state : new Wolf.State[]{Wolf.State.TROT, Wolf.State.NOTICE, Wolf.State.CHASE, Wolf.State.WINDUP, Wolf.State.LUNGE,
                Wolf.State.BITE, Wolf.State.RECOVER}) {
            for (int weapon = 0; weapon < 2; weapon++) {
                harness.sounds.clear();
                Level level = level();
                Wolf w = inState(level, state);
                if (weapon == 0) w.collidedWithActor(true);   // a fireball, a thrown stone or a torch
                else w.hitBySword(level);                      // the sword
                settle(level, SECOND);
                assertTrue(w.isRemains(), state + " " + weapon + ": it leaves its body");
                assertTrue(w.isDefeated());
                assertTrue(harness.sounds.contains("bounce"), "the shared kill sound");
                if (state == Wolf.State.WINDUP || state == Wolf.State.NOTICE || state == Wolf.State.CHASE || state == Wolf.State.TROT)
                    assertFalse(harness.sounds.contains("wolf_lunge"), "killed before it lunged, it never lunges");
            }
        }
    }

    @Test void killedInTheLungeTheBodySlidesOnToTheLinesEnd() {
        Level level = level();
        Wolf w = inState(level, Wolf.State.LUNGE);
        settle(level, 3);
        float end = w.lungeTargetX();
        assertTrue(w.position.x > end + 10, "still on its way");
        w.collidedWithActor(true);
        settle(level, SECOND);
        assertTrue(w.isRemains());
        assertEquals(end, w.position.x, 1f, "the body slid on to where the lunge would have ended");
        assertEquals(FLOOR, w.floorY(), .5f, "over the floor");
    }

    @Test void aKilledWolfsBodyIsHarmlessTwitchesWhenTheHeroComesNearAndNeverRocks() {
        Level level = level();
        Wolf w = wolf(level);
        w.collidedWithActor(true);
        settle(level, SECOND);
        assertTrue(w.isRemains());
        assertEquals(0, w.twitch(), 0f, "at rest");
        Rectangle body = w.hitboxBounds();
        place(level, body.x - 3 - 6 + 11);   // the hero's body 3 px from its box: within the 6 px
        float max = 0;
        for (int f = 0; f < 30; f++) { level.player.velocity().setZero(); frame(level); max = Math.max(max, Math.abs(w.twitch())); }
        assertEquals(1f, max, 0f, "a 1 px shudder");
        assertTrue(alive(level), "harmless");
        float x = w.position.x;
        settle(level, 30);
        assertEquals(0, w.twitch(), 0f, "it stops after 0.3 s");
        assertEquals(x, w.position.x, .001f, "and it stays where it lay");
    }

    // ---- refuge, bay, give-up ----------------------------------------------------------------------------------------------

    private Level ledge() { return harness.roomOf("wolf-ledge", 0); }

    /** The hero stands on the 32 px ledge (a one-way platform at y 288, x 448..512), his body centred at x 480. */
    private void onLedge(Level level) {
        level.player.position.set(480 - 5.5f - 18, 288 - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    @Test void aHeroMoreThanTwentyFourPixelsUpIsOutOfReachItBaysGivesUpAndIgnoresHimUntilHeLeavesItsSight() {
        Level level = ledge();
        Wolf w = wolf(level);
        onLedge(level);
        until(level, () -> w.state() == Wolf.State.BAY, SECOND * 4);
        assertEquals(LEFT_END, w.position.x, .6f, "it ran to the nearest point it can reach: its range end");
        assertTrue(harness.sounds.contains("wolf_bay"), harness.sounds.toString());
        assertEquals(1, java.util.Collections.frequency(harness.sounds, "wolf_bay"), "once");
        assertTrue(alive(level), "it never lunges at a hero above its reach");
        int frames = until(level, () -> w.state() == Wolf.State.TROT, SECOND * 3);
        assertEquals(Wolf.BAY_SECONDS, frames * STEP, STEP * 3, "it gives up after 1.5 s");
        assertTrue(w.isCooling());
        harness.sounds.clear();
        for (int f = 0; f < (int) (Wolf.COOL_SECONDS * SECOND) - 6; f++) {
            onLedge(level);
            frame(level);
            assertTrue(w.state() == Wolf.State.TROT || w.state() == Wolf.State.SNIFF, "it ignores a hero still in sight for 1 s: " + w.state());
        }
        assertTrue(harness.sounds.isEmpty(), "no second notice at once: " + harness.sounds);
        // Its sight is ahead only and it trots away, so he soon is out of its sight: the cool-down ends, and the next time it turns and
        // sees him on the ledge is a new notice (a patrol, not a loop under the ledge).
        for (int f = 0; f < SECOND * 40 && w.state() != Wolf.State.NOTICE; f++) { onLedge(level); frame(level); }
        assertEquals(Wolf.State.NOTICE, w.state(), "the next sight of him is a notice");
        // And a hero who leaves its sight for 1.0 s while it is cooling resets the cool-down as the card says.
        Level other = ledge();
        Wolf o = wolf(other);
        onLedge(other);
        until(other, () -> o.state() == Wolf.State.TROT && o.isCooling(), SECOND * 8);
        for (int f = 0; f < (int) (Wolf.COOL_SECONDS * SECOND) + 6; f++) { other.player.position.set(100, FLOOR - 48); other.player.velocity().setZero(); frame(other); }
        assertFalse(o.isCooling(), "out of its sight for 1.0 s: the cool-down is over");
    }

    @Test void aHeroWhoStepsDownIntoItsReachWhileItBaysIsCommittedTo() {
        Level level = ledge();
        Wolf w = wolf(level);
        onLedge(level);
        until(level, () -> w.state() == Wolf.State.BAY, SECOND * 4);
        placeAt(level, w, 50);
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND);
    }

    @Test void aHeroOutOfItsSightAndHearingForOneAndAHalfSecondsIsGivenUp() {
        Level level = level();
        Wolf w = wolf(level);
        place(level, 460);
        until(level, () -> w.state() == Wolf.State.CHASE, SECOND);
        for (int f = 0; f < SECOND * 6 && w.state() != Wolf.State.TROT; f++) { level.player.position.set(32, FLOOR - 48); level.player.velocity().setZero(); frame(level); }
        assertEquals(Wolf.State.TROT, w.state(), "it gave up and trots on");
    }

    // ---- it never leaves its floor or goes through a wall ----------------------------------------------------------------

    @Test void theLungeStopsAtTheEndOfItsFloorAndNeverReachesAHeroAcrossTheGap() {
        Level level = harness.roomOf("wolf-gap", 0);   // floor to x 800, a gap to x 832
        Wolf w = wolf(level);
        place(level, 851);   // the hero's body x 840..851, past the gap, right of the wolf
        float lowest = 0;
        boolean lunged = false;
        for (int f = 0; f < SECOND * 6; f++) {
            level.player.position.set(851 - 29, FLOOR - 48);
            level.player.velocity().setZero();
            frame(level);
            lowest = Math.max(lowest, w.position.y);
            if (w.state() == Wolf.State.WINDUP) assertEquals(760, w.lungeTargetX(), 2.1f, "its line ends where the floor ends");
            lunged |= w.state() == Wolf.State.LUNGE;
            assertTrue(alive(level), "it cannot strike over a gap");
        }
        assertTrue(lunged, "it did lunge");
        assertEquals(296, lowest, .01f, "it never left its floor");
    }

    @Test void theLungeStopsAgainstASolidAndNeverStrikesThroughAWall() {
        Level level = harness.roomOf("wolf-wall", 0);   // a wall at x 800..816
        Wolf w = wolf(level);
        boolean lunged = false;
        for (int f = 0; f < SECOND * 6; f++) {
            level.player.position.set(831 - 29, FLOOR - 48);   // his body x 820..831, just past the wall
            level.player.velocity().setZero();
            frame(level);
            if (w.state() == Wolf.State.WINDUP) assertEquals(760, w.lungeTargetX(), 2.1f, "its line ends against the wall");
            lunged |= w.state() == Wolf.State.LUNGE;
            assertTrue(alive(level), "no strike through a wall");
        }
        assertTrue(lunged);
    }

    // ---- reset -----------------------------------------------------------------------------------------------------------

    @Test void aWolfLeftMidLungeComesBackCalmAtItsSpawnAndBitesNobody() {
        Level level = level();
        Wolf w = committed(level, IN_BOX);
        until(level, () -> w.state() == Wolf.State.LUNGE, SECOND * 2);
        settle(level, 3);
        w.revisit(level.player);
        assertEquals(Wolf.State.TROT, w.state());
        assertEquals(SPAWN, w.position.x, .001f);
        assertFalse(w.facingRight, "facing its authored way");
        placeAt(level, w, 160);
        level.player.position.set(900, FLOOR - 48);
        settle(level, 20);
        assertTrue(alive(level), "a bite pending from the lunge did not follow it");
        assertFalse(w.isCooling());
    }

    // ---- the paw patter: heard only in view ------------------------------------------------------------------------------

    @Test void thePawPatterIsHeardOnceAGallopCycleWhileItSprintsAndOnlyInView() {
        Rectangle view = new Rectangle(0, 0, 1280, 368);
        Level seen = harness.roomOf("wolf-test", 0, () -> view);
        Wolf w = wolf(seen);
        place(seen, 460);
        until(seen, () -> w.state() == Wolf.State.CHASE, SECOND);
        harness.sounds.clear();
        settle(seen, SECOND);
        long patter = harness.sounds.stream().filter("wolf_run"::equals).count();
        assertTrue(patter >= 2, "one soft patter a gallop cycle while it sprints: " + patter);
        harness.sounds.clear();
        Level unseen = level();   // the harness's empty view: nothing is on the camera
        Wolf u = wolf(unseen);
        place(unseen, 460);
        until(unseen, () -> u.state() == Wolf.State.CHASE, SECOND);
        harness.sounds.clear();
        settle(unseen, SECOND);
        assertFalse(harness.sounds.contains("wolf_run"), "off screen it is silent");
    }

    // ---- load-time checks (D113) ------------------------------------------------------------------------------------------

    @Test void theLoadChecksRejectAWolfThatCouldCatchTheHeroAtTheEntranceOrCannotBeJumped() {
        IllegalArgumentException entrance = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("wolf-entrance", 0));
        assertTrue(entrance.getMessage().contains("sight or reach"), entrance.getMessage());
        IllegalArgumentException shortRange = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("wolf-short", 0));
        assertTrue(shortRange.getMessage().contains("range of at least 160"), shortRange.getMessage());
        IllegalArgumentException low = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("wolf-low", 0));
        assertTrue(low.getMessage().contains("headroom"), low.getMessage());
        IllegalArgumentException pit = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("wolf-pit", 0));
        assertTrue(pit.getMessage().contains("without gaps"), pit.getMessage());
        assertNotNull(harness.roomOf("wolf-gap", 0), "a gap beyond the range is fine: the lunge stops at it");
        assertNotNull(harness.roomOf("wolf-wall", 0));
        assertNotNull(harness.roomOf("wolf-ledge", 0));
    }

    /** A floor that vanishes under its line (a lever-driven wall, a block set inactive) during the wind-up stops the lunge at the last floor. */
    @Test void aFloorThatVanishesDuringTheWindUpStopsTheLungeAtTheLastGoodPlaceAndItStillBites() {
        Level level = harness.roomOf("wolf-split", 0);   // two floor blocks: x 0..800 and x 800..1280
        Wolf w = wolf(level);
        w.position.x = RIGHT_END;
        w.facingRight = true;
        place(level, 797);   // the hero's body x 786..797 on the left block, ahead of the muzzle (783): in its lunge box
        until(level, () -> w.state() == Wolf.State.WINDUP, SECOND * 3);
        assertEquals(RIGHT_END + Wolf.LUNGE_REACH, w.lungeTargetX(), .01f, "traced over both blocks: the whole 160 px");
        CollisionBlockHolder.deactivateFrom(level, 800);
        harness.sounds.clear();
        until(level, () -> w.state() == Wolf.State.BITE, SECOND * 2);
        Rectangle body = w.hitboxBounds();
        assertTrue(body.x + body.width <= 800 + .01f, "its body never reached over the gone floor: right edge " + (body.x + body.width));
        assertTrue(body.x + body.width > 800 - Wolf.LUNGE_SPEED * STEP, "it stopped at the last frame's good place, within one lunge step of the edge");
        assertEquals(296, w.position.y, .01f, "its box never left the floor line");
        assertEquals(FLOOR, w.floorY(), .01f);
        assertTrue(harness.sounds.contains("wolf_bite"), "heard hit or miss (D93)");
        assertTrue(level.wolfFx().soft().count() > 0, "and seen");
    }

    /** Switches off every collision block whose left edge is at or past {@code x}, as a lever-driven wall does. */
    private static final class CollisionBlockHolder {
        static void deactivateFrom(Level level, float x) {
            for (var block : level.collisionBlocks) if (block.getX() >= x) block.setActive(false);
        }
    }
}
