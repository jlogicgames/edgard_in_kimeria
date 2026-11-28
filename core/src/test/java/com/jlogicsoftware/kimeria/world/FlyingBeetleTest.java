package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.ScreenShake;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest flying beetle (#330, D145), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixture flying-beetle-test.tmx: room 640 wide, floor y 320, hero enters at x 32; the monster's 40x32 frame is at (316, 248)
 * with offNeg 4 and offPos 4, so its body (22x15) flies with its bottom at 280, 40 px over the floor, its middle at y 272.5,
 * and its middle x between 273 (left end) and 401 (right end), 337 at the spawn; a resting stone at x 96.
 * flying-beetle-roof.tmx adds a one-way platform beside the left end at the flight's height (x 198..262, y 272..288);
 * flying-beetle-pit.tmx has no floor from x 176 to 320. Each other
 * flying-beetle-*.tmx breaks one load rule (flying-beetle-ledge.tmx none).
 */
class FlyingBeetleTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, LEFT_END = 252, RIGHT_END = 380, SPAWN_X = 316, SPAWN_Y = 248;
    private static final float BODY_BOTTOM = 280;
    /** Hero middles: under the left end's commit reach, and heard there but out of it. */
    private static final float UNDER = 230, HEARD_ONLY = 180, SHOOTER = 205;
    /** The hero's body middle from its entity position, and the entity's feet below it. */
    private static final float HERO_MIDDLE = 23.5f, HERO_FEET = 48;
    /** Frames from the commit to the burst: the warning, the dive and the one frame the burst is due. */
    private static final int LONG_LIMIT = 60 * 8;

    private static final FlyingBeetle.State[] LIVE_STATES = {FlyingBeetle.State.PATROL, FlyingBeetle.State.NOTICE,
        FlyingBeetle.State.CHASE, FlyingBeetle.State.WINDUP, FlyingBeetle.State.DIVE};
    /** The fixtures' stone, and the torch the torch tests lay beside it. */
    private static final float STONE_X = 96, TORCH_X = 150, CARRY_SIZE = 12;
    private static final int TORCH_ID = 9001;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level() { return harness.roomOf("flying-beetle-test", 0); }

    private static FlyingBeetle beetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof FlyingBeetle b) return b;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    private void press(int key) { harness.held.add(key); harness.pressed.add(key); }
    private void tap(Level level, int key) { press(key); frame(level); harness.held.remove(key); }

    /** Stands the hero (body middle at {@code middle}) on the floor, still, facing right. */
    private void place(Level level, float middle) {
        level.player.position.set(middle - HERO_MIDDLE, FLOOR - HERO_FEET);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(harness.game().trapWarnings(), longer);
    }

    /** Frames until {@code condition} holds, at most {@code limit}; fails if it never does. */
    private int until(Level level, BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    /** The monster hears the hero standing at {@code UNDER}, follows to its left end's reach and commits. */
    private FlyingBeetle committed(Level level) {
        place(level, UNDER);
        FlyingBeetle b = beetle(level);
        until(level, b::isCommitted, LONG_LIMIT);
        return b;
    }

    /** The monster hears a hero it cannot commit to and waits at its left end; the hero then steps to {@code middle}. */
    private FlyingBeetle waiting(Level level) {
        place(level, HEARD_ONLY);
        FlyingBeetle b = beetle(level);
        until(level, () -> b.state() == FlyingBeetle.State.CHASE && b.position.x <= LEFT_END + .01f, LONG_LIMIT);
        return b;
    }

    @Test void fliesToAndFroOnItsLineAndNeverHearsAFarHero() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int f = 0; f < 60 * 15; f++) {
            frame(level);
            min = Math.min(min, b.position.x); max = Math.max(max, b.position.x);
            assertEquals(FlyingBeetle.State.PATROL, b.state());
            assertEquals(SPAWN_Y, b.position.y, 0, "level flight");
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertEquals(BODY_BOTTOM, b.floorY(), .001f, "40 px over the floor");
        assertTrue(harness.sounds.isEmpty(), "a calm one is silent");
    }

    @Test void noticesInBodyLanguageThenFollowsAndWaitsAtItsLinesEnd() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        place(level, HEARD_ONLY);
        until(level, () -> b.state() == FlyingBeetle.State.NOTICE, LONG_LIMIT);
        assertEquals(java.util.List.of("flying_beetle_notice"), harness.sounds, "its own call, no shared alert sound");
        assertFalse(b.facingRight, "it turns to the hero");
        float x = b.position.x;
        int frames = until(level, () -> b.state() == FlyingBeetle.State.CHASE, 60);
        assertEquals(FlyingBeetle.NOTICE_TOTAL, frames * STEP, STEP * 1.5f, "the notice lasts 0.5 s");
        assertEquals(x, b.position.x, .01f, "it stops while it notices");
        settle(level, 60 * 3);
        assertEquals(LEFT_END, b.position.x, .01f, "waits at its line's end");
        assertEquals(FlyingBeetle.State.CHASE, b.state(), "the hero is out of its commit reach");
        assertTrue(alive(level));
    }

    @Test void aHeroAboveItIsNotHeardAndOneWhoLeavesIsForgotten() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        for (int f = 0; f < 60 * 3; f++) {
            level.player.position.set(b.centreX() - HERO_MIDDLE, BODY_BOTTOM - 60 - HERO_FEET);
            level.player.velocity().setZero();
            frame(level);
        }
        assertEquals(FlyingBeetle.State.PATROL, b.state(), "a hero wholly above the body is out of its band");
        waiting(level);
        place(level, 80);
        until(level, () -> b.state() == FlyingBeetle.State.PATROL, 10);
    }

    @Test void aHeroOutOfItsBandForASecondIsForgotten() {
        Level level = level();
        FlyingBeetle b = waiting(level);
        for (int f = 0; f < 66; f++) {
            // Within its reach sideways, wholly above the body's top.
            level.player.position.set(200 - HERO_MIDDLE, BODY_BOTTOM - 15 - 4 - HERO_FEET);
            level.player.velocity().setZero();
            frame(level);
            if (f == 53) assertEquals(FlyingBeetle.State.CHASE, b.state(), "not yet a second");
        }
        assertEquals(FlyingBeetle.State.PATROL, b.state());
    }

    @Test void theClapOfTheNoticeDropsTheDrawnBodyButNotTheHitbox() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        place(level, HEARD_ONLY);
        until(level, () -> b.state() == FlyingBeetle.State.NOTICE, LONG_LIMIT);
        assertEquals(3, b.drawnDrop(), 0, "the covers clap and it drops");
        assertEquals(BODY_BOTTOM, b.floorY(), .001f);
        settle(level, 12);
        assertEquals(0, b.drawnDrop(), 0, "it opens them and climbs back");
    }

    @Test void touchIsHarmless() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        level.player.position.set(b.centreX() - HERO_MIDDLE, BODY_BOTTOM - 4 - 26);   // the head just inside the body from below
        level.player.velocity().set(0, -100);
        frame(level);
        assertTrue(alive(level), "touching it never kills");
        assertFalse(b.isDefeated());
    }

    @Test void commitsHangsDrawsTheLineDivesAndBurstsOnTheLockedSpot() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = committed(level);
            assertEquals(ExpectedWarnings.standard(longer), b.warningTotal(), 1e-6f);
            assertTrue(harness.sounds.contains(longer ? "flying_beetle_windup_long" : "flying_beetle_windup"), harness.sounds.toString());
            assertEquals(UNDER, b.targetX(), .01f, "locked on the hero's middle");
            assertEquals(FLOOR, b.targetSurface(), 0, "on the floor under him");
            assertTrue(Math.abs(b.targetX() - b.centreX()) <= FlyingBeetle.COMMIT_REACH);
            float x = b.position.x, y = b.position.y;
            int hang = until(level, () -> b.state() == FlyingBeetle.State.DIVE, LONG_LIMIT);
            assertEquals(ExpectedWarnings.standard(longer), hang * STEP, STEP * 1.5f, "hangs for the warning");
            assertEquals(x, b.position.x, .01f, "committed on the spot");
            assertEquals(y, b.position.y, .01f);
            assertTrue(level.beetleFx().hard().count() > 0, "the gathering and the line are drawn");
            assertTrue(harness.sounds.contains("flying_beetle_dive"));
            Rectangle last = new Rectangle();
            int dive = until(level, () -> { if (!b.isRemoved()) b.blastBoundsInto(last); return b.isRemoved(); }, 60);
            assertEquals(FlyingBeetle.DIVE_SECONDS + STEP, dive * STEP, STEP * 1.5f, "a 0.25 s dive, the burst a frame later");
            assertEquals(BeetleFx.BLAST_WIDTH, last.width, .01f);
            assertEquals(BeetleFx.BLAST_HEIGHT, last.height, .01f, "as high as the ground beetle's dome");
            assertEquals(FLOOR, last.y + last.height, .01f);
            assertEquals(UNDER, last.x + last.width / 2, .01f, "where it was drawn");
            assertFalse(alive(level), "a hero who stays is killed");
            assertTrue(harness.sounds.contains("beetle_pop"));
            assertTrue(b.isDefeated(), "a burst one counts as defeated");
        }
        setLonger(false);
    }

    @Test void runningEitherWaySurvives() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            for (int key : new int[]{Input.Keys.A, Input.Keys.D}) {
                setLonger(longer);
                Level level = level();
                FlyingBeetle b = committed(level);
                harness.held.add(key);
                until(level, b::isRemoved, LONG_LIMIT);
                harness.held.clear();
                assertTrue(alive(level), "out of the blast at the burst (key " + key + ", assist " + longer + ")");
            }
        }
        setLonger(false);
    }

    @Test void theRunHasHalfASecondToSpare() {
        // Escape: from the locked spot the hero must run 45.5 px (half the blast's width and half his body); he still escapes when he
        // starts half a second after the commit.
        Level level = level();
        FlyingBeetle b = committed(level);
        settle(level, 30);
        harness.held.add(Input.Keys.A);
        until(level, b::isRemoved, LONG_LIMIT);
        harness.held.clear();
        assertTrue(alive(level));
    }

    @Test void aJumpTimedToTheBurstSurvives() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, () -> b.warningRemaining() <= .1f, LONG_LIMIT);
        press(Input.Keys.J);
        until(level, b::isRemoved, 60);
        harness.held.clear();
        assertTrue(alive(level), "the feet are above the blast at the instant");
    }

    @Test void aPlatformBetweenThemStopsTheCommit() {
        Level level = harness.roomOf("flying-beetle-roof", 0);
        FlyingBeetle b = beetle(level);
        place(level, UNDER);
        for (int f = 0; f < 60 * 8; f++) { frame(level); assertFalse(b.isCommitted(), "frame " + f); }
        assertEquals(FlyingBeetle.State.CHASE, b.state(), "it hears him and waits");
        assertEquals(LEFT_END, b.position.x, .01f, "at the end of its line beside him");
        assertTrue(alive(level));
    }

    @Test void somethingMovingIntoTheLineStopsTheDiveThere() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, () -> b.state() == FlyingBeetle.State.DIVE, LONG_LIMIT);
        level.collisionBlocks.add(CollisionBlock.platform(180, 296, 140, 8));
        float[] lowest = {0};
        until(level, () -> { if (!b.isRemoved()) lowest[0] = Math.max(lowest[0], b.centreY()); return b.isRemoved(); }, 60);
        assertTrue(lowest[0] < 296, "it never passes through: " + lowest[0]);
        assertTrue(alive(level), "cut at the platform, the burst does not reach the hero under it");
        assertTrue(harness.sounds.contains("beetle_pop"), "seen and heard");
    }

    @Test void itNeverCommitsOverAPit() {
        Level level = harness.roomOf("flying-beetle-pit", 0);
        FlyingBeetle b = beetle(level);
        boolean heard = false;
        for (int f = 0; f < 60 * 6; f++) {
            place(level, 250);
            frame(level);
            heard |= b.state() == FlyingBeetle.State.CHASE;
            assertFalse(b.isCommitted(), "no surface under the hero, frame " + f);
        }
        assertTrue(heard, "it hears him all the same");
    }

    @Test void aJumpingFireballKillsItAndAStandingOneFliesUnderIt() {
        assertFalse(killedByShot(false, 0), "a standing ball flies under the line");
        int kills = 0;
        for (int delay = 1; delay <= 55; delay++) if (killedByShot(true, delay)) kills++;
        assertTrue(kills >= 8, "a jumping ball hits it over a usable window: " + kills + " cast frames");
    }

    /** One cast at a waiting monster from {@code SHOOTER}, standing or {@code delay} frames after a jump; true when it kills. */
    private boolean killedByShot(boolean jump, int delay) {
        Level level = level();
        FlyingBeetle b = waiting(level);
        place(level, SHOOTER);
        if (jump) { press(Input.Keys.J); for (int f = 0; f < delay; f++) frame(level); }
        tap(level, Input.Keys.L);
        for (int f = 0; f < 30 && !b.isDefeated(); f++) frame(level);
        harness.held.clear();
        if (b.state() != FlyingBeetle.State.DEAD) return false;
        assertFalse(harness.sounds.contains("beetle_pop"), "a quiet kill, no burst");
        return true;
    }

    @Test void aJumpingStoneKillsItAndAStandingOneFliesUnderIt() {
        assertFalse(killedByStone(false, 0), "a standing throw flies under the line");
        int kills = 0;
        for (int delay = 1; delay <= 55; delay++) if (killedByStone(true, delay)) kills++;
        assertTrue(kills >= 8, "a jumping throw hits it over a usable window: " + kills + " throw frames");
    }

    private void pickUpStone(Level level) { pickUp(level, STONE_X); }

    /** The fixtures hold no torch: one is laid on the floor beside the stone and picked up the same way. */
    private void pickUpTorch(Level level) {
        level.objects.add(new Carryable(level, TORCH_ID, Carryable.Kind.TORCH, TORCH_X, FLOOR - CARRY_SIZE));
        pickUp(level, TORCH_X);
        assertEquals(Carryable.Kind.TORCH.name(), level.player.carriedObject().label().name(), "the torch, not the stone");
    }

    private void pickUp(Level level, float objectX) {
        place(level, objectX + CARRY_SIZE - 29 + HERO_MIDDLE);
        frame(level);
        press(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked it up");
    }

    @Test void aJumpingTorchKillsItAndAStandingOneFliesUnderIt() {
        assertFalse(killedByThrow(true, false, 0), "a standing throw flies under the line");
        int kills = 0;
        for (int delay = 1; delay <= 55; delay++) if (killedByThrow(true, true, delay)) kills++;
        assertTrue(kills >= 8, "a jumping torch hits it over a usable window: " + kills + " throw frames");
    }

    private boolean killedByStone(boolean jump, int delay) { return killedByThrow(false, jump, delay); }

    private boolean killedByThrow(boolean torch, boolean jump, int delay) {
        Level level = level();
        if (torch) pickUpTorch(level); else pickUpStone(level);
        FlyingBeetle b = waiting(level);
        place(level, SHOOTER);
        settle(level, 3);
        if (jump) { press(Input.Keys.J); for (int f = 0; f < delay; f++) frame(level); }
        tap(level, Input.Keys.L);
        for (int f = 0; f < 60 && !b.isDefeated(); f++) frame(level);
        harness.held.clear();
        return b.state() == FlyingBeetle.State.DEAD;
    }

    @Test void aHitKillsItQuietlyInEveryState() {
        for (FlyingBeetle.State state : LIVE_STATES) {
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = reached(level, state);
            b.collidedWithActor(true);
            assertEquals(FlyingBeetle.State.DEAD, b.state(), state.toString());
            place(level, 80);
            settle(level, 60 * 2);
            assertTrue(alive(level), state.toString());
            assertTrue(b.isRemains(), "its body stays (D101)");
            assertEquals(FLOOR, b.floorY(), .5f, "it falls and lies on the floor below");
            assertTrue(harness.sounds.contains("bounce"));
            assertFalse(harness.sounds.contains("beetle_pop"), "no burst");
            assertEquals(0, level.bloodParticles().count(), "no blood");
        }
    }

    @Test void aThrownTorchKillsItQuietlyInEveryState() {
        for (FlyingBeetle.State state : LIVE_STATES) {
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = reached(level, state);
            Rectangle body = b.hitboxBounds();
            Carryable torch = new Carryable(level, TORCH_ID, Carryable.Kind.TORCH, body.x, body.y);
            level.objects.add(torch);
            torch.release(body.x - 2, body.y + 2, true, true);
            frame(level);
            assertEquals(FlyingBeetle.State.DEAD, b.state(), state.toString());
            place(level, 80);
            settle(level, 60 * 2);
            assertTrue(alive(level), state.toString());
            assertTrue(b.isRemains(), "its body stays (D101): " + state);
            assertFalse(harness.sounds.contains("beetle_pop"), "no burst: " + state);
        }
    }

    /** A fresh level's monster brought into {@code state}: calm with the hero at the entrance, else with him at {@code UNDER}. */
    private FlyingBeetle reached(Level level, FlyingBeetle.State state) {
        FlyingBeetle b = beetle(level);
        if (state != FlyingBeetle.State.PATROL) place(level, UNDER);
        until(level, () -> b.state() == state, LONG_LIMIT);
        return b;
    }

    /** D145, D148: the sword bursts a live one in every state, and its owner is caught. */
    @Test void theSwordBurstsItOnItsOwnerInEveryState() {
        for (FlyingBeetle.State state : LIVE_STATES) {
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = reached(level, state);
            b.hitBySword(level);
            assertTrue(b.isRemoved(), state.toString());
            assertFalse(b.isRemains(), "a burst leaves nothing: " + state);
            assertFalse(alive(level), state.toString());
            assertTrue(harness.sounds.contains("beetle_pop"), state.toString());
            assertEquals(0, level.bloodParticles().count(), "no blood: " + state);
        }
    }

    /**
     * A real swing at a monster on its line, while it notices and while it hangs in its wind-up. The sword is swung from the
     * ground only, so the hero stands on a floor 16 px higher.
     */
    @Test void aSwordSwungFromAStepBurstsItOnItsLine() {
        for (FlyingBeetle.State state : new FlyingBeetle.State[]{FlyingBeetle.State.NOTICE, FlyingBeetle.State.WINDUP}) {
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = reached(level, state);
            level.collisionBlocks.add(CollisionBlock.wall(160, FLOOR - 16, 256, 16));
            place(level, b.centreX() - 28);
            level.player.position.y -= 16;
            frame(level); // he stands on the step
            assertEquals(state, b.state());
            tap(level, Input.Keys.K);
            until(level, b::isRemoved, 20);
            assertFalse(alive(level), "melee is punished (D145): " + state);
            assertFalse(b.isRemains(), "a burst, not a quiet kill: " + state);
            assertTrue(harness.sounds.contains("beetle_pop"), state.toString());
        }
    }

    @Test void killedOverAPitItIsGone() {
        Level level = harness.roomOf("flying-beetle-pit", 0);
        FlyingBeetle b = beetle(level);
        until(level, () -> b.position.x <= LEFT_END + .01f, LONG_LIMIT);
        b.collidedWithActor(true);
        until(level, b::isRemoved, 60 * 2);
        assertFalse(b.isRemains());
    }

    @Test void theStandingSwordCannotReachItOnItsLine() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        place(level, b.centreX() - 20);
        tap(level, Input.Keys.K);
        settle(level, 35);
        assertFalse(b.isDefeated(), "36 px of sword under a body 40 px up");
        assertTrue(alive(level));
    }

    @Test void theSwordMeetingTheDiveBurstsItOnTheSwordsman() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, () -> b.warningRemaining() <= .2f, LONG_LIMIT);
        tap(level, Input.Keys.K);
        until(level, b::isRemoved, 60);
        assertFalse(alive(level), "the blast reaches whoever cuts it");
        assertTrue(harness.sounds.contains("beetle_pop"));
        assertEquals(0, level.bloodParticles().count(), "no blood");
    }

    @Test void aStompBeforeItCommitsBurstsItUnderTheHero() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        Rectangle body = b.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - HERO_MIDDLE, body.y - HERO_FEET - 6);
        level.player.velocity().setZero();
        until(level, b::isRemoved, 60);
        assertFalse(alive(level));
    }

    @Test void aStompOnACommittedOneBouncesTheHeroOff() {
        Level level = level();
        FlyingBeetle b = committed(level);
        Rectangle body = b.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - HERO_MIDDLE, body.y - HERO_FEET - 6);
        level.player.velocity().setZero();
        until(level, () -> level.player.velocity.y < 0, 30);
        assertTrue(b.isCommitted(), "a stomp does not kill a committed one");
        assertFalse(b.isDefeated());
        assertTrue(alive(level));
    }

    /** True while the diving body's middle stands on the locked spot. */
    private static boolean arrived(FlyingBeetle b) {
        return b.state() == FlyingBeetle.State.DIVE && Math.abs(b.centreX() - b.targetX()) < .01f && Math.abs(b.centreY() - b.targetY()) < .01f;
    }

    /** Frames from the commit after which the dive has just arrived. */
    private int framesToArrive(Level level, FlyingBeetle b) { return until(level, () -> arrived(b), LONG_LIMIT); }

    @Test void theBurstFallsDueOneFrameAfterTheDiveArrives() {
        Level level = level();
        FlyingBeetle b = committed(level);
        framesToArrive(level, b);
        assertFalse(b.isDefeated(), "no burst on the arrival frame");
        assertTrue(alive(level));
        frame(level);
        assertTrue(b.isRemoved(), "on the next one");
        assertFalse(alive(level));
    }

    /** #503: a fireball cast on the frame the dive arrives wins over the burst that is due on the next. */
    @Test void aFireballCastOnTheFrameTheDiveArrivesWins() {
        Level probe = level();
        int arrival = framesToArrive(probe, committed(probe));
        harness.sounds.clear();
        Level level = level();
        FlyingBeetle b = committed(level);
        settle(level, arrival - 1);
        assertFalse(arrived(b), "one frame short");
        tap(level, Input.Keys.L);
        assertEquals(FlyingBeetle.State.DEAD, b.state(), "hit on the arrival frame");
        settle(level, 30);
        assertTrue(alive(level), "the ball wins the tie");
        assertFalse(harness.sounds.contains("beetle_pop"));
    }

    /** #505: so does a stone thrown on that frame. */
    @Test void aStoneThrownOnTheFrameTheDiveArrivesWins() {
        Level probe = level();
        pickUpStone(probe);
        int arrival = framesToArrive(probe, committed(probe));
        harness.sounds.clear();
        Level level = level();
        pickUpStone(level);
        FlyingBeetle b = committed(level);
        settle(level, arrival - 1);
        assertFalse(arrived(b), "one frame short");
        tap(level, Input.Keys.L);
        assertEquals(FlyingBeetle.State.DEAD, b.state(), "hit on the arrival frame");
        settle(level, 30);
        assertTrue(alive(level), "the stone wins the tie");
        assertFalse(harness.sounds.contains("beetle_pop"));
    }

    /** And a torch thrown on that frame. */
    @Test void aTorchThrownOnTheFrameTheDiveArrivesWins() {
        Level probe = level();
        pickUpTorch(probe);
        int arrival = framesToArrive(probe, committed(probe));
        harness.sounds.clear();
        Level level = level();
        pickUpTorch(level);
        FlyingBeetle b = committed(level);
        settle(level, arrival - 1);
        assertFalse(arrived(b), "one frame short");
        tap(level, Input.Keys.L);
        assertEquals(FlyingBeetle.State.DEAD, b.state(), "hit on the arrival frame");
        settle(level, 30);
        assertTrue(alive(level), "the torch wins the tie");
        assertFalse(harness.sounds.contains("beetle_pop"));
    }

    /** D101: the dive's line is thin, so the body can overlap a step or a ledge beside it; killed there it still falls past. */
    @Test void killedBesideAStepOrALedgeItFallsToTheFloor() {
        for (boolean platform : new boolean[]{false, true}) {
            Level level = level();
            level.collisionBlocks.add(platform ? CollisionBlock.platform(192, 308, 32, 16) : CollisionBlock.wall(208, 272, 16, 48));
            FlyingBeetle b = committed(level);
            framesToArrive(level, b);
            assertTrue(b.hitboxBounds().x < 224, "the arriving body overlaps it sideways (platform " + platform + ")");
            b.collidedWithActor(true);
            place(level, 80);
            settle(level, 60);
            assertTrue(b.isRemains());
            assertEquals(FLOOR, b.floorY(), .5f, "on the floor, not lifted on to the top (platform " + platform + ")");
            assertTrue(b.hitboxBounds().x >= 224 - .01f, "moved off it sideways (platform " + platform + "), body x " + b.hitboxBounds().x);
        }
    }

    /** D5: the burst at the end of a dive stays on the surface it locked on, even when that surface has gone since. */
    @Test void theBurstStaysOnItsLockedSurfaceWhenItGoes() {
        Level level = level();
        FlyingBeetle b = committed(level);
        framesToArrive(level, b);
        level.collisionBlocks.clear();
        Rectangle box = b.blastBoundsInto(new Rectangle());
        assertEquals(b.targetSurface(), box.y + box.height, .001f);
        assertEquals(BeetleFx.BLAST_HEIGHT, box.height, .01f);
    }

    @Test void theBodyIsCrumbledByTheSwordAndBurntByAFireball() {
        for (boolean sword : new boolean[]{true, false}) {
            harness.sounds.clear();
            Level level = level();
            FlyingBeetle b = waiting(level);
            b.collidedWithActor(true);
            place(level, 80);
            settle(level, 60);
            assertTrue(b.isRemains());
            place(level, b.centreX() - 28);
            settle(level, 2);
            tap(level, sword ? Input.Keys.K : Input.Keys.L);
            until(level, b::isRemoved, 90);
            assertTrue(harness.sounds.contains(sword ? "remains_crumble" : "remains_burn"), harness.sounds.toString());
            assertTrue(alive(level));
        }
    }

    @Test void leftBehindItReturnsCalmToItsLine() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, () -> b.state() == FlyingBeetle.State.DIVE, LONG_LIMIT);
        settle(level, 5);
        b.revisit(level.player);
        assertEquals(FlyingBeetle.State.PATROL, b.state());
        assertEquals(SPAWN_X, b.position.x, 0);
        assertEquals(SPAWN_Y, b.position.y, 0);
    }

    @Test void aDeathBringsItBackAsAuthored() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, b::isRemoved, LONG_LIMIT);
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        FlyingBeetle fresh = beetle(level);
        assertNotNull(fresh);
        assertEquals(FlyingBeetle.State.PATROL, fresh.state());
        assertEquals(SPAWN_X, fresh.position.x, 0);
    }

    /** D147: the wind-up draws specks in to the monster from all round and runs others down the dive line; nothing at the target. */
    @Test void theSpecksGatherIntoItAndRunDownTheLine() {
        Level level = level();
        FlyingBeetle b = committed(level);
        float fromX = b.centreX(), fromY = b.centreY(), toX = b.targetX(), toY = b.targetY();
        KimeriaParticleSystem specks = level.beetleFx().hard();
        int onLine = 0, round = 0;
        for (int f = 0; f < 40; f++) {
            frame(level);
            for (int i = 0; i < specks.count(); i++) {
                float x = specks.x(i), y = specks.y(i);
                float fromMiddle = (float) Math.hypot(x - fromX, y - fromY);
                boolean near = fromMiddle <= BeetleFx.GATHER_RADIUS_MAX + .01f;
                float along = Math.max(0, Math.min(1, ((x - fromX) * (toX - fromX) + (y - fromY) * (toY - fromY))
                    / ((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY))));
                float dx = x - fromX - along * (toX - fromX), dy = y - fromY - along * (toY - fromY);
                boolean nearLine = dx * dx + dy * dy <= 2.5f * 2.5f;
                assertTrue(near || nearLine, "a speck neither round it nor on the line at " + x + ", " + y);
                if (nearLine && !near) onLine++;
                if (!nearLine && y < fromY - 12) round++;
            }
        }
        assertTrue(onLine > 0, "specks run down the line to the target");
        assertTrue(round > 0, "and gather into it from all round, above it too");
        assertEquals(0, level.beetleFx().soft().count(), "no flash before the burst");
        assertEquals(specks.count(), kinds(specks, KimeriaParticleSystem.KIND_FLAME));
    }

    /** The burst's shake (#448, D156): the ground beetle's numbers, for a survivor at any distance, none when it kills. */
    @Test void aFarSurvivorFeelsTheBurstShake() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        level.player.position.set(b.centreX() + FAR_FROM_BURST, FLOOR - 48);
        b.burst();
        assertTrue(alive(level));
        assertEquals(ScreenShake.BURST_AMPLITUDE_PX, level.fx().shake().currentAmplitude(), 1e-4f);
    }

    @Test void aLethalBurstStartsNoShake() {
        Level level = level();
        FlyingBeetle b = committed(level);
        until(level, b::isRemoved, LONG_LIMIT);
        assertFalse(alive(level));
        assertFalse(level.fx().shake().running(), "the death beat is the feedback");
    }

    private static int kinds(KimeriaParticleSystem system, int kind) {
        int n = 0;
        for (int i = 0; i < system.count(); i++) if (system.kind(i) == kind) n++;
        return n;
    }

    /** D148: what kills is the shape that is seen: off a surface a half ellipse, in the air an ellipse round the middle. */
    @Test void theBlastKillsInTheShapeItIsSeenIn() {
        float x = 200, floor = 320, low = floor - BeetleFx.BODY_CENTRE_ABOVE_FLOOR;
        assertTrue(reaches(x + 39, floor - 22, 22, x, low, floor), "a standing hero 39 px out");
        assertFalse(reaches(x + 41, floor - 22, 22, x, low, floor), "41 px out");
        assertTrue(reaches(x - 5.5f, floor - 40, 9, x, low, floor), "31 px up over the middle");
        assertFalse(reaches(x - 5.5f, floor - 44, 11, x, low, floor), "33 px up");
        assertFalse(reaches(x + 30, floor - 40, 12, x, low, floor), "the old box's upper corner, ahead");
        assertFalse(reaches(x - 41, floor - 40, 12, x, low, floor), "and behind");
        assertTrue(reaches(x - 36, floor - 24, 12, x, low, floor), "inside the curve on the other side");
        assertFalse(reaches(x - 5.5f, floor, 22, x, low, floor), "nothing under the surface");
        float middle = 200, open = middle + BeetleFx.BLAST_AIR_HALF_HEIGHT;
        assertTrue(reaches(x + 39, middle - 5, 10, x, middle, open), "in the air, 39 px out");
        assertFalse(reaches(x + 41, middle - 5, 10, x, middle, open));
        assertTrue(reaches(x - 5.5f, middle - 33, 10, x, middle, open), "23 px over the middle");
        assertFalse(reaches(x - 5.5f, middle - 35, 10, x, middle, open), "25 px over it");
        assertTrue(reaches(x - 5.5f, middle + 23, 10, x, middle, open), "23 px under it");
        assertFalse(reaches(x + 30, middle - 30, 12, x, middle, open), "the old box's corner in the air");
    }

    /** Whether the blast reaches a hero-wide rect with its left edge at {@code left}. */
    private static boolean reaches(float left, float top, float height, float x, float middle, float bottom) {
        return FlyingBeetle.reaches(new Rectangle(left, top, 11, height), x, middle, bottom);
    }

    @Test void theSwordCatchesItsOwnerEvenWhereTheShapeJustMisses() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        // Its body 36 px over the floor, the hero's near side 28 px from its middle: the tip of a sword's reach.
        place(level, b.centreX() - 28 - 5.5f);
        b.position.y += FLOOR - 36 - b.floorY();
        Rectangle hero = level.player.hitboxBoundsInto(new Rectangle());
        assertFalse(FlyingBeetle.reaches(hero, b.centreX(), b.centreY(), b.centreY() + BeetleFx.BLAST_AIR_HALF_HEIGHT),
            "outside the oval: " + hero + " from " + b.centreX() + ", " + b.centreY());
        b.hitBySword(level);
        assertFalse(alive(level), "melee is punished (D145)");
    }

    private static final float FAR_FROM_BURST = 360;

    /** flying-beetle-climb.tmx: a one-way ledge across a line 10 tiles long to the left; the line's body bottom is y 280. */
    private static final float LEDGE_LEFT = 308, LEDGE_TOP = 272, LEDGE_WIDTH = 64, LINE_BOTTOM = 280;
    private static final float OVER_LEDGE = LEDGE_TOP - FlyingBeetle.CLIMB_HEIGHT;
    private static final float BESIDE_LEDGE = 280, ON_LEDGE = 340;

    @Test void fliesOverALedgeInItsLineAndSinksBehindIt() {
        Level level = harness.roomOf("flying-beetle-climb", 0);
        FlyingBeetle b = beetle(level);
        assertEquals(OVER_LEDGE, b.floorY(), .01f, "placed over the ledge, it starts on the route");
        Rectangle ledge = new Rectangle(LEDGE_LEFT, LEDGE_TOP, LEDGE_WIDTH, 16), body = new Rectangle();
        boolean over = false, behind = false;
        float lastX = b.position.x, lastY = b.position.y;
        for (int f = 0; f < 60 * 14; f++) {
            frame(level);
            b.hitboxBoundsInto(body);
            assertFalse(body.overlaps(ledge), "never through the ledge: " + body);
            assertTrue(Math.abs(b.position.y - lastY) <= Math.abs(b.position.x - lastX) + .01f, "rises and sinks no steeper than it flies");
            lastX = b.position.x; lastY = b.position.y;
            if (body.x < ledge.x + ledge.width && body.x + body.width > ledge.x) { assertEquals(OVER_LEDGE, b.floorY(), .01f); over = true; }
            if (Math.abs(b.position.x - b.rangeNeg()) < .01f) { assertEquals(LINE_BOTTOM, b.floorY(), .01f, "back on its line"); behind = true; }
        }
        assertTrue(over && behind, "the patrol passes over the ledge and behind it");
    }

    @Test void underALedgeItHangsOverTheHeroAndCannotCommit() {
        Level level = harness.roomOf("flying-beetle-climb", 0);
        FlyingBeetle b = beetle(level);
        place(level, ON_LEDGE);
        for (int f = 0; f < LONG_LIMIT; f++) {
            frame(level);
            assertFalse(b.isCommitted(), "the ledge cuts every dive on to him");
        }
        assertEquals(FlyingBeetle.State.CHASE, b.state());
        assertEquals(OVER_LEDGE, b.floorY(), .01f);
        assertEquals(ON_LEDGE, b.centreX(), 1f, "right over him");
    }

    @Test void fromOverALedgeItDivesDownToTheFloorBesideIt() {
        Level level = harness.roomOf("flying-beetle-climb", 0);
        FlyingBeetle b = beetle(level);
        place(level, BESIDE_LEDGE);
        until(level, b::isCommitted, LONG_LIMIT);
        assertTrue(b.floorY() < LINE_BOTTOM, "still raised by the ledge: " + b.floorY());
        assertEquals(FLOOR, b.targetSurface(), .01f);
        until(level, b::isRemoved, LONG_LIMIT);
        assertFalse(alive(level), "the burst reaches the hero who stays");
    }

    @Test void itDivesOnToAHeroOnTheLedgeFromAbove() {
        Level level = harness.roomOf("flying-beetle-climb", 0);
        FlyingBeetle b = beetle(level);
        place(level, ON_LEDGE);
        level.player.position.y = LEDGE_TOP - HERO_FEET;
        until(level, b::isCommitted, LONG_LIMIT);
        assertEquals(LEDGE_TOP, b.targetSurface(), .01f);
        until(level, b::isRemoved, LONG_LIMIT);
        assertFalse(alive(level));
    }

    @Test void leftBehindOverALedgeItReturnsToItsRoute() {
        Level level = harness.roomOf("flying-beetle-climb", 0);
        FlyingBeetle b = beetle(level);
        float x = b.position.x, y = b.position.y;
        until(level, () -> b.position.x <= b.rangeNeg() + .01f, LONG_LIMIT);
        b.revisit(level.player);
        assertEquals(x, b.position.x, .01f);
        assertEquals(y, b.position.y, .01f);
    }

    @Test void theLoadChecksFollowADeepDiveFromOverALedge() {
        // The line runs wholly over a ledge: only a dive from up there, down past the ledge's end, covers the checkpoint.
        assertRejected("flying-beetle-over-ledge", "can dive on the room checkpoint");
    }

    @Test void theLoadChecksRejectABadPlacement() {
        assertRejected("flying-beetle-low", "flies less than 40 px");
        assertRejected("flying-beetle-high", "flies more than 64 px");
        assertRejected("flying-beetle-blocked", "on its flight line");
        assertRejected("flying-beetle-entrance", "can dive on the room entrance");
        assertRejected("flying-beetle-out", "leaving the room");
        assertRejected("flying-beetle-air", "can burst over the room checkpoint");
        assertRejected("flying-beetle-camera", "above the camera's view");
        assertRejected("flying-beetle-vine", "on its flight line");
    }

    /** Moving objects count over everything they pass through, not only where they are authored. */
    @Test void theLoadChecksFollowWhatMoves() {
        assertRejected("flying-beetle-lift", "on its flight line");
        assertRejected("flying-beetle-falling", "flies less than 40 px");
    }

    /** A dive locks on the surface under the hero, which need not be the highest one at that spot. */
    @Test void theLoadChecksFollowADivePastALedgeOnToTheFloorUnderIt() {
        assertRejected("flying-beetle-under-ledge", "can dive on the room checkpoint");
    }

    @Test void aLedgeThatOnlyTouchesTheEndOfTheSweepIsNotUnderTheLine() {
        // flying-beetle-ledge.tmx: a platform at the body's bottom height ending exactly where the sweep starts.
        assertNotNull(beetle(harness.roomOf("flying-beetle-ledge", 0)));
    }

    private void assertRejected(String map, String reason) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> harness.roomOf(map, 0), map);
        assertTrue(e.getMessage().contains(reason), map + ": " + e.getMessage());
    }
}
