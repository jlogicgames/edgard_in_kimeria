package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest spider (#328, D102), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixture spider-test.tmx: room 640 wide, floor y 320, hero enters at x 32 (body x 50..61); the spider spawns at x 320
 * (body x 322..341, centre 332) with offNeg 4 and offPos 4, so its {@code position.x} stays in 256..384; a resting stone at
 * x 96. Facing left, its pounce box runs from its body's middle 58 px to the left, and its fangs' middle is at
 * {@code position.x + 4}. A hero standing with its body's right edge at {@link #NEAR} (centre 294.5) is inside the pounce box
 * of the spider at its spawn, and the locked target puts the fangs on that centre.
 */
class SpiderTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, LEFT_END = 256, RIGHT_END = 384, SPAWN_X = 320;
    /** Hero body right edges: inside the pounce box of the spider at its spawn; heard but out of reach; far, unheard. */
    private static final float NEAR = 300, HEARD = 230, FAR = 150;
    private static final float HERO_HALF = 5.5f, FANG_LEFT = 4;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level() { return harness.roomOf("spider-test", 0); }

    private static Spider spider(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Spider s) return s;
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

    /** The hero stands at {@link #NEAR}; the spider hears, notices and commits on the spot. */
    private Spider committed(Level level) {
        place(level, NEAR);
        Spider s = spider(level);
        until(level, () -> s.state() == Spider.State.WINDUP, 60 * 3);
        return s;
    }

    @Test void skittersInsideItsRangeStopAndGoAndIsSilentWhenCalm() {
        Level level = level();
        Spider s = spider(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, last = s.position.x;
        int still = 0, frames = 60 * 20;
        for (int f = 0; f < frames; f++) {
            frame(level);
            min = Math.min(min, s.position.x); max = Math.max(max, s.position.x);
            if (s.position.x == last) still++;
            last = s.position.x;
            assertEquals(Spider.State.CRAWL, s.state());
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertEquals(Spider.SKITTER_STILL / (Spider.SKITTER_MOVE + Spider.SKITTER_STILL), still / (float) frames, .05f, "still half the time");
        assertTrue(harness.sounds.isEmpty(), "a calm spider is silent");
    }

    @Test void noticesInBodyLanguageThenChases() {
        Level level = level();
        Spider s = spider(level);
        place(level, HEARD);   // hero centre 224.5: 107.5 px from the spider's centre 332, inside the 112 px hearing
        frame(level);
        assertEquals(Spider.State.NOTICE, s.state());
        assertEquals(java.util.List.of("spider_notice"), harness.sounds, "its own call, no shared alert sound");
        assertFalse(s.facingRight, "it turns to the hero");
        float x = s.position.x;
        int frames = until(level, () -> s.state() == Spider.State.CHASE, 60);
        assertEquals(Spider.NOTICE_TOTAL, frames * STEP, STEP * 1.5f, "the notice lasts 0.5 s");
        assertEquals(x, s.position.x, .01f, "it does not move while it notices");
        float before = s.position.x;
        frame(level);
        assertEquals(Spider.CHASE_SPEED * STEP, before - s.position.x, .01f, "chases at 85 px/s");
        assertTrue(alive(level));
    }

    @Test void touchIsHarmless() {
        Level level = level();
        Spider s = spider(level);
        place(level, s.hitboxBounds().x + 6);   // the hero's body overlaps the calm spider's body
        settle(level, 10);
        assertTrue(alive(level), "touching a spider never kills: only the fangs do");
    }

    @Test void itLocksOnWhereTheHeroStoodWindsUpForTheBiteWarningAndBitesThere() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            Spider s = committed(level);
            assertEquals(ExpectedWarnings.bite(longer), s.warningTotal(), 1e-6f);
            assertTrue(harness.sounds.contains(longer ? "spider_windup_long" : "spider_windup"), harness.sounds.toString());
            assertEquals(NEAR - HERO_HALF, s.leapTarget() + FANG_LEFT, .01f, "the fangs will land on the hero's centre");
            float x = s.position.x;
            int toLeap = until(level, () -> s.state() == Spider.State.LEAP, 120);
            assertEquals(ExpectedWarnings.bite(longer), toLeap * STEP, STEP * 1.5f, "it leaps when the warning ends");
            assertEquals(x, s.position.x, .01f, "committed on the spot");
            assertTrue(alive(level), "nothing kills before the leap");
            assertTrue(harness.sounds.contains("spider_leap"));
            until(level, () -> !alive(level), (int) ((Spider.LEAP_SECONDS + Spider.BITE_SECONDS) / STEP) + 3);
            until(level, () -> s.state() == Spider.State.RECOVER, 30);
            assertEquals(s.leapTarget(), s.position.x, .01f, "it lands on the locked spot");
            assertEquals(FLOOR, s.floorY(), .01f, "back on the floor");
            assertTrue(harness.sounds.contains("spider_bite"), "the bite is heard, hit or miss");
        }
        setLonger(false);
    }

    @Test void theWindUpShowsTheReachWithSilkOnTheFloor() {
        Level level = level();
        Spider s = committed(level);
        settle(level, 10);
        KimeriaParticleSystem hard = level.spiderFx().hard();
        int silk = 0;
        float spot = s.leapTarget() + FANG_LEFT;
        for (int i = 0; i < hard.count(); i++) {
            if (hard.kind(i) != KimeriaParticleSystem.KIND_FLAME) continue;
            silk++;
            assertEquals(FLOOR - 1, hard.y(i), .01f, "on the floor line");
            assertTrue(hard.x(i) >= spot - 2.01f && hard.x(i) <= s.fangX() + .01f, "between the fangs and the spot: " + hard.x(i));
        }
        assertTrue(silk > 0, "silk glints during the wind-up");
    }

    @Test void steppingBackDuringTheWindUpMakesItMissAndItsRecoveryIsHarmless() {
        Level level = level();
        Spider s = committed(level);
        harness.held.add(Input.Keys.A);   // a few steps back with ordinary controls
        settle(level, 12);
        harness.held.clear();
        until(level, () -> s.state() == Spider.State.RECOVER, 120);
        assertTrue(alive(level), "out of the locked bite: a miss");
        assertTrue(harness.sounds.contains("spider_bite"), "a miss is heard too (D93)");
        place(level, s.hitboxBounds().x + 4);   // the hero walks into the recovering spider
        until(level, () -> s.state() != Spider.State.RECOVER, (int) (Spider.RECOVER_SECONDS / STEP) + 2);
        assertTrue(alive(level), "the recovery is harmless");
        until(level, () -> s.state() == Spider.State.WINDUP, 5);   // then it chases and, the hero in reach, winds up anew
        assertEquals(s.warningTotal(), s.warningRemaining() + STEP, STEP * 1.5f, "a full new warning");
    }

    @Test void runningAwayAlwaysWorks() {
        Level level = level();
        Spider s = committed(level);
        harness.held.add(Input.Keys.A);
        settle(level, 60 * 3);
        harness.held.clear();
        assertTrue(alive(level));
        assertNotEquals(Spider.State.WINDUP, s.state());
    }

    @Test void theBladeMeetsTheLeap() {
        Level level = level();
        Spider s = committed(level);
        until(level, () -> s.warningRemaining() <= .15f, 120);
        press(level, Input.Keys.K);
        settle(level, 40);
        assertTrue(s.isDefeated(), "a live swing meets it in the air");
        assertTrue(alive(level), "the blade wins");
        assertTrue(s.isRemains(), "a quiet kill: the body stays (D101)");
        assertTrue(level.bloodParticles().count() > 0, "the sword's light spray (D65)");
    }

    @Test void rushingInDuringTheWindUpWins() {
        Level level = level();
        Spider s = committed(level);
        harness.held.add(Input.Keys.D);
        settle(level, 6);
        harness.held.clear();
        press(level, Input.Keys.K);
        settle(level, 10);
        assertTrue(s.isDefeated(), "in reach before the leap");
        assertTrue(alive(level));
    }

    @Test void aKillOnTheFrameTheFangsArriveBeatsTheBite() {
        Level level = level();
        Spider s = committed(level);
        Rectangle fangs = new Rectangle();
        until(level, () -> s.fangBoxInto(fangs) != null && fangs.overlaps(level.player.hitboxBounds()), 120);
        assertTrue(alive(level), "the bite lands on the spider's next frame");
        s.collidedWithActor(true);   // a fireball or a stone hitting it on that same frame
        settle(level, 30);
        assertTrue(alive(level), "the hit wins a same-frame tie (D102)");
        assertTrue(s.isRemains());
    }

    /**
     * The frame, counted from the commit, on which the fangs first reach a hero standing at {@link #NEAR} who does nothing; on
     * that frame the bite is due, and it lands on the next one.
     */
    private int biteFrame() {
        Level level = level();
        Spider s = committed(level);
        Rectangle fangs = new Rectangle();
        for (int f = 1; f < 120; f++) {
            frame(level);
            if (s.fangBoxInto(fangs) != null && fangs.overlaps(level.player.hitboxBounds())) {
                assertTrue(alive(level), "the bite is due, not landed");
                frame(level);
                assertFalse(alive(level), "it lands on the next frame");
                return f;
            }
        }
        fail("the fangs never reached the hero");
        return -1;
    }

    @Test void aSwingStartedOnTheFrameTheFangsArriveWinsThroughTheRealUpdate() {
        int due = biteFrame();
        Level level = level();
        Spider s = committed(level);
        for (int f = 1; f < due; f++) frame(level);
        press(level, Input.Keys.K);   // the swing starts on the very frame the fangs reach the hero (Level.update, no shortcut)
        settle(level, 30);
        assertTrue(alive(level), "the blade wins the same-frame tie (D102)");
        assertTrue(s.isDefeated());
        assertTrue(s.isRemains(), "and the swing that killed it leaves its body");
    }

    /** A spider that misses (the hero stepped back) and has just landed; the hero is clear of its fangs. */
    private Spider landedAfterAMiss(Level level) {
        Spider s = committed(level);
        harness.held.add(Input.Keys.A);
        settle(level, 12);
        harness.held.clear();
        until(level, () -> s.state() == Spider.State.BITE, 120);
        return s;
    }

    @Test void aBiteOnTheLastBiteFrameLandsInsideTheBiteWindow() {
        Level dry = level();
        Spider d = landedAfterAMiss(dry);
        int biteFrames = 0;
        while (d.state() == Spider.State.BITE) { frame(dry); biteFrames++; }   // updates that leave it biting, the last one included
        assertTrue(alive(dry));
        Level level = level();
        Spider s = landedAfterAMiss(level);
        settle(level, biteFrames - 1);
        assertEquals(Spider.State.BITE, s.state(), "the last update of the bite is next");
        Rectangle fangs = s.fangBoxInto(new Rectangle());
        level.player.position.set(fangs.x + fangs.width / 2 - 23.5f, FLOOR - 48);   // the hero steps into the fangs right then
        level.player.velocity().setZero();
        frame(level);
        assertTrue(alive(level), "due, not landed");
        assertEquals(Spider.State.BITE, s.state(), "a bite due on its last frame keeps it biting until the bite lands");
        frame(level);
        assertFalse(alive(level), "it lands inside the bite window, never in the harmless recovery");
    }

    /**
     * A hero the bite cannot kill (the developer's invulnerability, F2; the victory walk ignores it the same way): the spider
     * bites once per leap, recovers and chases again, instead of biting every frame and staying in its bite.
     */
    @Test void aBiteThatCannotKillStillEndsInTheRecovery() {
        harness.invulnerable = true;
        Level level = level();
        Spider s = committed(level);
        until(level, () -> s.state() == Spider.State.BITE, 120);
        int bite = until(level, () -> s.state() == Spider.State.RECOVER, 30);
        assertTrue(bite * STEP <= Spider.BITE_SECONDS + 2 * STEP, "the bite ends on time: " + bite + " frames");
        assertTrue(alive(level));
        until(level, () -> s.state() == Spider.State.CHASE || s.state() == Spider.State.WINDUP || s.state() == Spider.State.CRAWL,
            (int) (Spider.RECOVER_SECONDS / STEP) + 3);
        until(level, () -> s.state() == Spider.State.WINDUP, 60);   // the hero still in reach: a new pounce
        until(level, () -> s.state() == Spider.State.RECOVER, 120);   // and that leap's bite ends in the recovery too
        harness.invulnerable = false;
    }

    @Test void aSolidInTheWayStopsTheLeap() {
        Level level = harness.roomOf("spider-wall", 0);   // a low wall x 280..288 between the hero and the spider
        Spider s = spider(level);
        place(level, 276);
        until(level, () -> s.state() == Spider.State.WINDUP, 60 * 3);
        assertTrue(s.leapTarget() < 280, "it locked onto the hero, behind the wall");
        until(level, () -> s.state() == Spider.State.RECOVER, 120);
        assertTrue(s.hitboxBounds().x >= 288 - .01f, "it stopped against the wall, not through it: " + s.hitboxBounds());
        assertEquals(FLOOR, s.floorY(), .01f, "and landed on its floor");
        assertTrue(alive(level), "the wall kept the fangs off the hero");
        assertTrue(harness.sounds.contains("spider_bite"), "it still bites, against the wall (D93)");
    }

    @Test void aFireballKillsItInEveryStateLeavingItsBodyWithoutBlood() {
        for (boolean windingUp : new boolean[]{false, true}) {
            harness.sounds.clear();
            Level level = level();
            Spider s = spider(level);
            if (windingUp) committed(level);
            else { place(level, FAR); settle(level, 30); }   // the hero has to stand a moment after entering before it can fire
            press(level, Input.Keys.L);
            until(level, s::isDefeated, 90);
            assertEquals(Spider.State.DEAD, s.state());
            assertTrue(harness.sounds.contains("bounce"), "the kill sound");
            settle(level, 60);
            assertTrue(alive(level), "windingUp " + windingUp);
            assertTrue(s.isRemains(), "its body stays (D101)");
            assertEquals(0, level.bloodParticles().count(), "no blood from fire (D99)");
        }
    }

    @Test void killedInTheAirItsBodyFallsToTheFloor() {
        Level level = level();
        Spider s = committed(level);
        until(level, () -> s.state() == Spider.State.LEAP && s.floorY() < FLOOR - 3, 120);
        s.collidedWithActor(true);
        settle(level, 60);
        assertEquals(FLOOR, s.floorY(), .5f, "the body lies on the floor");
        assertTrue(alive(level));
    }

    @Test void aStompKillsItWithTheNormalBounce() {
        Level level = level();
        Spider s = spider(level);
        Rectangle body = s.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - 23.5f, body.y - 48 - 20);
        level.player.velocity().setZero();
        boolean bounced = false;
        for (int f = 0; f < 60 && !s.isDefeated(); f++) { frame(level); bounced |= level.player.velocity.y < 0; }
        assertTrue(s.isDefeated() && bounced, "stomped, with the bounce");
        assertTrue(s.isRemains());
        assertTrue(alive(level));
    }

    @Test void theSwordCrumblesItsBodyAndAFireballBurnsIt() {
        Level level = level();
        Spider s = spider(level);
        place(level, FAR);
        s.collidedWithActor(true);
        settle(level, 60);
        place(level, s.hitboxBounds().x - 4);
        harness.sounds.clear();
        press(level, Input.Keys.K);
        until(level, s::isRemoved, 30);
        assertTrue(harness.sounds.contains(Spider.CRUMBLE_SOUND));
        assertTrue(level.remainsFx().hard().count() >= 10, "chips fall");

        Level burnt = level();
        Spider b = spider(burnt);
        place(burnt, FAR);
        b.collidedWithActor(true);
        settle(burnt, 60);
        harness.sounds.clear();
        press(burnt, Input.Keys.L);
        until(burnt, () -> b.state() == Spider.State.BURN, 60);
        assertTrue(harness.sounds.contains(Spider.BURN_SOUND));
        until(burnt, b::isRemoved, (int) Math.ceil(Spider.BURN_SECONDS / STEP) + 2);
    }

    /** D103 changed the loss from "at once beyond 160 px or 1.0 s out of the band" to "1.0 s beyond 192 px". */
    @Test void itLosesAHeroWhoStaysFarForASecond() {
        Level level = level();
        Spider s = spider(level);
        place(level, HEARD);
        until(level, () -> s.state() == Spider.State.CHASE, 60);
        place(level, 61);   // hero centre 55.5: over 192 px from the spider even at its left end (centre 268)
        frame(level);
        assertEquals(Spider.State.CHASE, s.state(), "not at once");
        int frames = until(level, () -> s.state() == Spider.State.CRAWL, 120);
        assertEquals(Spider.LOSE_SECONDS, (frames + 1) * STEP, STEP * 1.5f, "after a second beyond 192 px");
    }

    /** D103: hearing is a circle, so a hero above the spider's floor (in the air here) is heard; one farther than 144 px is not. */
    @Test void itHearsAHeroAboveItInACircle() {
        Level level = level();
        Spider s = spider(level);
        level.player.position.set(SPAWN_X + 12 - 29 + HERO_HALF + 6, FLOOR - 48 - 150);   // straight above, 150 px up
        level.player.velocity().setZero();
        frame(level);
        assertEquals(Spider.State.CRAWL, s.state(), "150 px away: outside the circle");
        Level other = level();
        Spider t = spider(other);
        other.player.position.set(SPAWN_X + 12 - 29 + HERO_HALF + 6, FLOOR - 48 - 100);   // 100 px up: no band in the way
        other.player.velocity().setZero();
        frame(other);
        assertEquals(Spider.State.NOTICE, t.state(), "a hero above it is heard");
    }

    @Test void aDeathResetsTheRoomAndItsSpiderAndOneLeftMidPounceReturnsCalm() {
        Level level = level();
        Spider s = committed(level);
        until(level, () -> !alive(level), 120);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        Spider fresh = spider(level);
        assertNotNull(fresh);
        assertNotSame(s, fresh);
        assertEquals(SPAWN_X, fresh.position.x, .01f);
        assertEquals(Spider.State.CRAWL, fresh.state());
        assertEquals(0, level.spiderFx().soft().count() + level.spiderFx().hard().count(), "effects cleared with the room");
        Level other = level();
        Spider left = committed(other);
        left.revisit(other.player);
        assertEquals(Spider.State.CRAWL, left.state());
        assertEquals(SPAWN_X, left.position.x, .01f);
        assertEquals(0, left.warningRemaining(), 0);
    }

    @Test void loadTimeChecksNameTheProblem() {
        Rectangle room = new Rectangle(0, 0, 640, 368);
        java.util.List<Rectangle> floor = java.util.List.of(new Rectangle(0, 320, 640, 48));
        Map<String, Rectangle> standing = Map.of("entrance", new Rectangle(50, 298, 11, 22));
        assertNull(Spider.problem(320, 304, 32, 16, 4, 4, floor, room, standing));
        assertNotNull(Spider.problem(96, 304, 32, 16, 1, 1, floor, room, standing), "it could pounce at the entrance");
        assertNotNull(Spider.problem(600, 304, 32, 16, 0, 1, floor, room, Map.of()), "the range leaves the room");
        assertNotNull(Spider.problem(320, 304, 32, 16, 4, 4, java.util.List.of(new Rectangle(0, 320, 300, 48),
            new Rectangle(316, 320, 324, 48)), room, Map.of()), "a gap in its floor");
        assertNotNull(Spider.problem(320, 304, 32, 16, Float.NaN, 4, floor, room, Map.of()), "a broken range");
        // A narrow Tiled object: the game still builds the 32x16 spider, whose pounce box reaches position.x + 70 (390 here).
        Map<String, Rectangle> checkpoint = Map.of("checkpoint", new Rectangle(386, 288, 16, 32));
        assertNotNull(Spider.problem(320, 304, 16, 16, 0, 0, floor, room, checkpoint), "the full reach, not the authored width");
        assertNull(Spider.problem(320, 304, 16, 16, 0, 0, floor, room, Map.of("checkpoint", new Rectangle(392, 288, 16, 32))),
            "just past the reach is fine");
        IllegalArgumentException entrance = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("spider-entrance", 0));
        assertTrue(entrance.getMessage().contains("entrance"), entrance.getMessage());
        IllegalArgumentException gap = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("spider-gap", 0));
        assertTrue(gap.getMessage().contains("floor"), gap.getMessage());
    }

    // ---- D103: the route, the wall and the silk drop -------------------------------------------------------------------

    /**
     * Fixture spider-climb.tmx: floor y 320; a block x 400..624, top y 224. The spider's route (a polyline from (320, 320)): the
     * floor to the block's foot (position.x 318..378), up its left face (the wall frame's top 224..288, the solid on the right),
     * and along its top (position.x 398..506, floor line 224).
     */
    private Level climb() { return harness.roomOf("spider-climb", 0); }
    private static final float LEDGE = 224, FACE = 400;

    /**
     * Fixture spider-drop.tmx: floor y 320; a shelf x 320..448, y 208..224. The route (from (384, 208)): the shelf's top to its
     * right lip (position.x 382..426), then a thread at x 454 (6 px out from the lip) down to the floor: its spinnerets stop at
     * y 300, the hanging frame's bottom 4 px over the floor. The drop box is x 451..459, y 208..316.
     */
    private Level dropRoom() { return harness.roomOf("spider-drop", 0); }
    private static final float SHELF = 208, THREAD_X = 454, THREAD_END = 300;
    /** Hero body right edges on the floor: inside the drop column; just out of it. */
    private static final float UNDER = 460, ASIDE = 480;

    /** Stands the hero (body right edge at {@code right}) on the floor line {@code floor}, still, facing right. */
    private void placeOn(Level level, float right, float floor) {
        level.player.position.set(right - 29, floor - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    @Test void aPolylineTracesFloorsWallsAndThreads() {
        Spider climber = spider(climb()), dropper = spider(dropRoom());
        Spider.Route r = climber.route();
        assertEquals(3, r.legs());
        assertFalse(r.isWall(0)); assertTrue(r.isWall(1)); assertFalse(r.isWall(2));
        assertEquals(318, r.lo(0), 0); assertEquals(378, r.hi(0), 0);
        assertEquals(LEDGE, r.lo(1), 0); assertEquals(FLOOR - Spider.WALL_HEIGHT, r.hi(1), 0); assertEquals(FACE, r.line(1), 0);
        assertEquals(LEDGE, r.line(2), 0);
        assertFalse(r.hasThread(0)); assertFalse(r.hasThread(1));
        assertEquals(318, climber.position.x, 0, "it spawns at the route's first point");
        Spider.Route d = dropper.route();
        assertEquals(1, d.legs());
        assertTrue(d.hasThread(1));
        assertEquals(THREAD_X, d.threadX(1), 0);
        assertEquals(SHELF, d.anchorY(1), 0);
        assertEquals(THREAD_END, d.endY(1), 0);
        Rectangle box = dropper.dropBoxInto(1, new Rectangle());
        assertEquals(new Rectangle(451, SHELF, 8, THREAD_END + 16 - SHELF), box);
    }

    @Test void itPatrolsItsWholeRouteUpTheWallAndBackSilently() {
        Level level = climb();
        Spider s = spider(level);
        boolean wall = false, ledge = false, back = false, up = false, down = false;
        for (int f = 0; f < 60 * 45; f++) {
            frame(level);
            assertEquals(Spider.State.CRAWL, s.state());
            if (s.onWall()) {
                wall = true;
                if (s.headUp()) up = true; else down = true;
                Rectangle body = s.hitboxBounds();
                assertTrue(body.x + body.width <= FACE && body.x + body.width >= FACE - 2, "its belly against the face: " + body);
            }
            if (s.leg() == 2) { ledge = true; assertEquals(LEDGE, s.floorY(), .5f, "on the ledge's top"); }
            if (ledge && s.leg() == 0) back = true;
        }
        assertTrue(wall && ledge && back, "floor, wall, ledge and back");
        assertTrue(up && down, "head up climbing, head down coming back");
        assertTrue(harness.sounds.isEmpty(), "a calm spider is silent");
    }

    @Test void itHearsAHeroOnTheLedgeAboveClimbsTheWallAndNeverStrikesFromIt() {
        Level level = climb();
        Spider s = spider(level);
        placeOn(level, 430, LEDGE);   // hero centre (424.5, 213): 137 px from the spider's centre (330, 313.5)
        frame(level);
        assertEquals(Spider.State.NOTICE, s.state());
        assertEquals(java.util.List.of("spider_notice"), harness.sounds);
        boolean climbed = false;
        for (int f = 0; f < 60 * 4 && s.leg() != 2; f++) {
            frame(level);
            if (s.onWall()) { climbed = true; assertEquals(Spider.State.CHASE, s.state(), "on the wall it only travels"); assertTrue(s.headUp()); }
            assertFalse(s.isCommitted(), "it never strikes from a wall");
        }
        assertTrue(climbed, "it went up the wall");
        assertEquals(2, s.leg(), "over the top onto the ledge");
        until(level, () -> s.state() == Spider.State.WINDUP, 60 * 2);   // on the ledge's floor: the D102 pounce
        assertTrue(alive(level));
    }

    @Test void itClimbsDownHeadFirstToAHeroBelowAndWaitsAtTheHeightNearestHim() {
        Level level = climb();
        Spider s = spider(level);
        placeOn(level, 430, LEDGE);
        until(level, () -> s.onWall() && s.position.y < 250, 60 * 4);
        placeOn(level, 250, FLOOR);   // back on the floor, out of the pounce's reach from the wall's foot
        until(level, () -> s.onWall() && !s.headUp(), 60);
        until(level, () -> s.leg() == 0, 60 * 2);
        settle(level, 60);   // 60 px of floor at 85 px/s
        assertEquals(318, s.position.x, .01f, "it waits at its floor's end nearest the hero");
        assertTrue(alive(level));
    }

    @Test void killedOnTheWallItsBodyFallsToTheFootOnTheOpenSide() {
        Level level = climb();
        Spider s = spider(level);
        placeOn(level, 430, LEDGE);
        until(level, () -> s.onWall() && s.position.y < 260, 60 * 4);
        s.collidedWithActor(true);
        assertEquals(Spider.State.DEAD, s.state());
        assertFalse(s.threadSnapping(), "no thread on a wall");
        settle(level, 60);
        assertTrue(s.isRemains());
        assertEquals(FLOOR, s.floorY(), .5f, "it lies on the floor below");
        Rectangle body = s.hitboxBounds();
        assertTrue(body.x + body.width <= FACE, "on the open side of the wall: " + body);
    }

    @Test void theDropWindsUpAtTheLipThenFallsAndBitesAtTheThreadsEnd() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = dropRoom();
            Spider s = spider(level);
            placeOn(level, UNDER, FLOOR);
            until(level, () -> s.state() == Spider.State.SET, 60 * 3);
            float expected = TrapWarnings.BITE_WARNING_SECONDS * (longer ? 1.5f : 1f);
            assertEquals(expected, s.warningTotal(), 1e-4f, "the shared bite warning (D7)");
            assertTrue(harness.sounds.contains(longer ? "spider_windup_long" : "spider_windup"));
            assertEquals(THREAD_X - 15, s.position.x, 0, "hanging on its thread at the lip");
            assertEquals(SHELF - 1, s.position.y, 0, "drawn back up a pixel");
            assertFalse(s.isRemoved());
            frame(level);
            assertTrue(level.spiderFx().hard().count() > 0, "silk glints run down the column");
            int frames = until(level, () -> s.state() == Spider.State.DROP, 120);
            assertEquals(expected, (frames + 1) * STEP, STEP * 1.5f, "the wind-up lasts the warning");
            assertTrue(alive(level), "the wind-up is harmless");
            assertTrue(harness.sounds.contains("spider_drop"));
            until(level, () -> !alive(level), 30);
            assertTrue(s.state() == Spider.State.DROP || s.state() == Spider.State.DANGLE, "the fangs kill on the way down");
        }
        setLonger(false);
    }

    @Test void steppingOutOfTheColumnMakesItMissThenItHangsHarmlessAndReelsBackUp() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        placeOn(level, ASIDE, FLOOR);   // 11 px to the side: out of the column
        harness.sounds.clear();
        until(level, () -> s.state() == Spider.State.DANGLE, 60);
        assertEquals(THREAD_END, s.spinneretY(), 0, "the thread's end, 4 px over the floor");
        assertTrue(harness.sounds.contains("spider_bite"), "the bite is heard, hit or miss (D93)");
        int hang = until(level, () -> s.state() == Spider.State.HANG, 30);
        assertEquals(Spider.BITE_SECONDS, (hang + 1) * STEP, STEP * 1.5f);
        int reel = until(level, () -> s.state() == Spider.State.REEL, 60);
        assertEquals(Spider.RECOVER_SECONDS, (reel + 1) * STEP, STEP * 1.5f, "a recovery as long as the pounce's");
        assertTrue(harness.sounds.contains("spider_reel"));
        float y = s.spinneretY();
        frame(level);
        assertEquals(Spider.REEL_SPEED * STEP, y - s.spinneretY(), .01f, "it reels up at 64 px/s");
        until(level, () -> s.state() == Spider.State.CHASE, 60 * 3);
        assertEquals(SHELF - 16, s.position.y, .01f, "back on its shelf");
        assertEquals(426, s.position.x, .01f, "at the lip");
        assertTrue(alive(level), "it never bit the hero beside the column");
    }

    @Test void aHeroWhoStaysInTheColumnGetsAFullWindUpAgain() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        placeOn(level, ASIDE, FLOOR);
        until(level, () -> s.state() == Spider.State.REEL, 60 * 2);
        placeOn(level, UNDER, FLOOR);   // back under it while it climbs: it does not drop from the thread
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        assertEquals(TrapWarnings.BITE_WARNING_SECONDS, s.warningRemaining(), STEP * 1.5f, "a new full wind-up from the lip");
        assertTrue(alive(level));
    }

    @Test void theSwordKillsItHangingLowAndTheThreadSnaps() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        placeOn(level, 478, FLOOR);
        level.player.facingRight = false;
        until(level, () -> s.state() == Spider.State.HANG, 60);
        harness.sounds.clear();
        press(level, Input.Keys.K);
        until(level, s::isDefeated, 30);
        assertTrue(harness.sounds.contains("spider_snap"), "the thread is heard snapping");
        assertTrue(s.threadSnapping(), "the stub fades");
        assertTrue(level.bloodParticles().count() > 0, "the sword's light spray (D65)");
        settle(level, 60);
        assertFalse(s.threadSnapping(), "gone after 0.4 s");
        assertTrue(s.isRemains(), "its body stays (D101)");
        assertEquals(FLOOR, s.floorY(), .5f, "on the floor below the thread");
        assertTrue(s.hitboxBounds().x >= 448, "clear of the shelf's lip");
        assertTrue(alive(level));
    }

    @Test void killedInTheDropItsBodyFallsToTheFloor() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.DROP, 60 * 4);
        placeOn(level, ASIDE, FLOOR);
        frame(level);
        s.collidedWithActor(true);
        assertEquals(Spider.State.DEAD, s.state());
        assertTrue(s.threadSnapping());
        settle(level, 60);
        assertEquals(FLOOR, s.floorY(), .5f);
        assertTrue(alive(level));
    }

    @Test void aKillOnTheFrameTheDropFangsArriveBeatsTheBite() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.DROP, 60 * 4);
        Rectangle fangs = new Rectangle();
        until(level, () -> s.fangBoxInto(fangs) != null && fangs.overlaps(level.player.hitboxBounds()), 30);
        s.collidedWithActor(true);   // a stone or a fireball on the frame the fangs arrive
        settle(level, 30);
        assertTrue(alive(level), "the hit wins a same-frame tie");
    }

    @Test void aDeathOnTheThreadResetsItCalmOnItsShelf() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> !alive(level), 60 * 4);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        Spider fresh = spider(level);
        assertNotSame(s, fresh);
        assertEquals(Spider.State.CRAWL, fresh.state());
        assertEquals(382, fresh.position.x, .01f);
        assertEquals(SHELF - 16, fresh.position.y, .01f);
        Level other = dropRoom();
        Spider left = spider(other);
        placeOn(other, UNDER, FLOOR);
        until(other, () -> left.state() == Spider.State.DROP, 60 * 4);
        left.revisit(other.player);
        assertEquals(Spider.State.CRAWL, left.state());
        assertEquals(SHELF - 16, left.position.y, .01f, "one left on its thread comes back calm on its floor");
    }

    @Test void routeLoadChecksNameTheProblem() {
        Rectangle room = new Rectangle(0, 0, 640, 368);
        java.util.List<Rectangle> floor = java.util.List.of(new Rectangle(0, 320, 640, 48), new Rectangle(320, 208, 128, 16));
        java.util.List<Rectangle> solids = floor, none = java.util.List.of();
        Map<String, Rectangle> standing = Map.of("entrance", new Rectangle(50, 298, 11, 22));
        float[] drop = {384, 208, 448, 208, 448, 320};
        assertNull(Spider.routeProblem(drop, floor, solids, none, none, room, 8, standing));
        assertNotNull(Spider.routeProblem(drop, floor, solids, none, none, room, 8, Map.of("checkpoint", new Rectangle(448, 288, 16, 32))),
            "a drop onto a standing place");
        assertNotNull(Spider.routeProblem(drop, floor, solids, java.util.List.of(new Rectangle(440, 260, 32, 16)), none, room, 8, standing),
            "a platform in the column");
        assertNotNull(Spider.routeProblem(drop, floor, solids, none, java.util.List.of(new Rectangle(440, 304, 48, 16)), room, 8, standing),
            "a drop onto quicksand or a climbable");
        java.util.List<Rectangle> narrow = java.util.List.of(new Rectangle(0, 320, 460, 48), new Rectangle(320, 208, 128, 16));
        assertNotNull(Spider.routeProblem(drop, narrow, narrow, none, none, room, 8, standing), "no floor to step aside onto");
        assertNotNull(Spider.routeProblem(new float[]{384, 192, 448, 192, 448, 320}, floor,
            java.util.List.of(new Rectangle(0, 320, 640, 48), new Rectangle(320, 192, 128, 16)), none, none, room, 8, standing),
            "a thread longer than 112 px");
        assertNotNull(Spider.routeProblem(drop, floor, solids, none, none, room, 220, standing), "above the camera's view");
        assertNotNull(Spider.routeProblem(new float[]{320, 320, 400, 300}, floor, solids, none, none, room, 8, standing), "a slanted piece");
        java.util.List<Rectangle> block = java.util.List.of(new Rectangle(0, 320, 640, 48), new Rectangle(400, 224, 224, 96));
        float[] climb = {320, 320, 400, 320, 400, 224, 528, 224};
        assertNull(Spider.routeProblem(climb, block, block, none, none, room, 8, standing));
        assertNotNull(Spider.routeProblem(new float[]{320, 320, 400, 320, 400, 300}, block, block, none, none, room, 8, standing),
            "a wall shorter than the spider");
        assertNotNull(Spider.routeProblem(new float[]{320, 320, 400, 320, 400, 224, 300, 224}, block, block, none, none, room, 8, standing),
            "a floor that leaves the wall's top on the open side, in the air");
        assertNotNull(Spider.routeProblem(new float[]{300, 320, 350, 320, 400, 320}, block, block, none, none, room, 8, standing),
            "two floors in a row");
        assertNotNull(Spider.routeProblem(new float[]{320, 320, 400, 320, 400, 224, 400, 200}, block, block, none, none, room, 8, standing),
            "an upright piece in the open that is not a thread");
        IllegalArgumentException checkpoint = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("spider-drop-checkpoint", 0));
        assertTrue(checkpoint.getMessage().contains("checkpoint"), checkpoint.getMessage());
    }

    @Test void aFloorOnlyRouteIsTheD102Spider() {
        Spider s = spider(level());
        assertEquals(1, s.route().legs());
        assertFalse(s.route().hasThread(0) || s.route().hasThread(1));
        assertEquals(SPAWN_X, s.position.x, 0);
        assertFalse(s.facingRight, "it spawns facing left, as before");
    }

    // ---- D103 review: the mirrored paths, the snap in the wind-up and the shared route fixture -----------------------------

    /** spider-left-drop.tmx: the shelf x 320..448 at y 208; the route runs right to left (aLow false) and ends in a thread down from the shelf's LEFT lip at x 320. */
    private static final float LEFT_LIP = 320, LEFT_THREAD_X = 312, UNDER_LEFT = 318, ASIDE_LEFT = 298;

    @Test void aThreadLeftOfItsLipClearsTheLedgeAndDrops() {
        Level level = harness.roomOf("spider-left-drop", 0);
        Spider s = spider(level);
        assertEquals(LEFT_THREAD_X, s.route().threadX(1), 0, "8 px out on the left: the body clears the face by 1 px");
        assertEquals(362, s.position.x, 0, "it spawns at the route's start, the right end of a right-to-left floor");
        placeOn(level, UNDER_LEFT, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        Rectangle body = s.hitboxBounds();
        assertEquals(LEFT_LIP - Spider.HANG_CLEARANCE, body.x + body.width, 0, "the hanging body clears the shelf's face");
        until(level, () -> !alive(level), 60);
        assertTrue(s.state() == Spider.State.DROP || s.state() == Spider.State.DANGLE);
    }

    @Test void aThreadAtTheRoutesStartDropsMissesAndReelsBackOntoItsFloor() {
        Level level = harness.roomOf("spider-start-drop", 0);
        Spider s = spider(level);
        assertTrue(s.route().hasThread(0));
        assertFalse(s.route().hasThread(1));
        placeOn(level, UNDER_LEFT, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        Rectangle body = s.hitboxBounds();
        assertTrue(body.x + body.width <= LEFT_LIP - Spider.HANG_CLEARANCE, "clear of the ledge: " + body);
        placeOn(level, ASIDE_LEFT, FLOOR);
        until(level, () -> s.state() == Spider.State.REEL, 60 * 2);
        until(level, () -> s.state() == Spider.State.CHASE || s.state() == Spider.State.CRAWL, 60 * 3);
        assertEquals(318, s.position.x, .01f, "back at the lip, the start of its floor");
        assertEquals(SHELF - 16, s.position.y, .01f);
        assertFalse(s.facingRight, "facing over the lip");
        assertTrue(alive(level));
    }

    @Test void aWallWithItsSolidOnTheLeftIsClimbedOnItsRightFace() {
        Level level = harness.roomOf("spider-climb-left", 0);
        Spider s = spider(level);
        assertTrue(s.route().isWall(1));
        assertFalse(s.route().solidRight(1));
        placeOn(level, 230, LEDGE);   // hero centre (224.5, 213), 132 px from the spider's centre (310, 313.5)
        boolean climbed = false;
        for (int f = 0; f < 60 * 5 && s.leg() != 2; f++) {
            frame(level);
            if (s.onWall()) {
                climbed = true;
                Rectangle body = s.hitboxBounds();
                assertTrue(body.x >= 240 && body.x <= 242, "its belly against the face at x 240: " + body);
                assertTrue(s.headUp());
            }
        }
        assertTrue(climbed);
        assertEquals(2, s.leg(), "over the top onto the ledge, which runs right to left");
        settle(level, 10);
        assertEquals(LEDGE, s.floorY(), .5f);
        assertTrue(alive(level) || s.isCommitted(), "it never struck from the wall");
    }

    @Test void aKillDuringTheDropsWindUpSnapsNoThread() {
        Level level = dropRoom();
        Spider s = spider(level);
        placeOn(level, UNDER, FLOOR);
        until(level, () -> s.state() == Spider.State.SET, 60 * 3);
        harness.sounds.clear();
        int hard = level.spiderFx().hard().count();
        s.collidedWithActor(true);
        assertFalse(harness.sounds.contains("spider_snap"), "no thread is drawn yet, so none snaps");
        assertFalse(s.threadSnapping());
        assertTrue(level.spiderFx().hard().count() <= hard, "no snap specks");
        placeOn(level, ASIDE, FLOOR);
        settle(level, 60);
        assertTrue(s.isRemains());
        assertEquals(FLOOR, s.floorY(), .5f, "its body falls to the floor below the lip");
        assertTrue(s.hitboxBounds().x >= 448, "out past the lip");
    }

    // ---- the hold (#488): at its target it stands still ------------------------------------------------------------------

    /** Keeps the hero hovering with its body's right edge at {@code right}, {@code up} px over the floor: out of the pounce's reach. */
    private void hover(Level level, float right, float floor, float up) {
        level.player.position.set(right - 29, floor - 48 - up);
        level.player.velocity().setZero();
    }

    @Test void aChasingSpiderHoldsStillUnderAHeroItCannotReach() {
        Level level = level();
        Spider s = spider(level);
        for (float right : new float[] {HEARD + 60.3f, HEARD}) {   // under the hero (inside its range), then past its left end
            hover(level, right, FLOOR, 100);
            until(level, () -> s.state() == Spider.State.CHASE, 60);
            for (int f = 0; f < 60 * 4; f++) { hover(level, right, FLOOR, 100); frame(level); }
            float x = s.position.x;
            boolean facing = s.facingRight;
            for (int f = 0; f < 60 * 2; f++) {
                hover(level, right, FLOOR, 100);
                frame(level);
                assertEquals(Spider.State.CHASE, s.state());
                assertEquals(x, s.position.x, 0f, "it holds, no dither (frame " + f + ")");
                assertEquals(facing, s.facingRight, "its facing does not flip while it holds");
            }
        }
    }

    @Test void aChasingSpiderHoldsStillOnTheWallAtTheHeightNearestTheHero() {
        Level level = climb();
        Spider s = spider(level);
        placeOn(level, 430, LEDGE);
        until(level, () -> s.onWall() && s.position.y < 250, 60 * 4);
        for (int f = 0; f < 60 * 4; f++) { hover(level, 392, 280, 0); frame(level); }
        assertTrue(s.onWall(), "it waits on the wall");
        float y = s.position.y;
        boolean headUp = s.headUp();
        for (int f = 0; f < 60 * 2; f++) {
            hover(level, 392, 280, 0);
            frame(level);
            assertTrue(s.onWall());
            assertEquals(y, s.position.y, 0f, "it holds, no dither (frame " + f + ")");
            assertEquals(headUp, s.headUp(), "its head does not turn while it holds");
        }
    }

    /** #483 review: Spider.Route.trace and check_contrast.spider_route are pinned to one fixture, so they cannot drift. */
    @Test void theJavaRouteMatchesTheSharedFixture() throws Exception {
        java.nio.file.Path file = java.nio.file.Path.of("src/test/resources/tiles/spider-routes.json");
        if (!java.nio.file.Files.exists(file)) file = java.nio.file.Path.of("core/src/test/resources/tiles/spider-routes.json");
        com.badlogic.gdx.utils.JsonValue fixture = new com.badlogic.gdx.utils.JsonReader().parse(java.nio.file.Files.readString(file));
        int maps = 0;
        for (com.badlogic.gdx.utils.JsonValue map = fixture.child; map != null; map = map.next) {
            if (map.name.startsWith("_")) continue;
            maps++;
            Spider.Route r = spider(harness.roomOf(map.name, 0)).route();
            com.badlogic.gdx.utils.JsonValue legs = map.get("legs"), threads = map.get("threads");
            assertEquals(legs.size, r.legs(), map.name);
            for (int i = 0; i < legs.size; i++) {
                com.badlogic.gdx.utils.JsonValue leg = legs.get(i);
                String where = map.name + " leg " + i;
                assertEquals(leg.getString("kind").equals("wall"), r.isWall(i), where);
                assertEquals(leg.getFloat("lo"), r.lo(i), 0, where);
                assertEquals(leg.getFloat("hi"), r.hi(i), 0, where);
                assertEquals(leg.getFloat("line"), r.line(i), 0, where);
                if (r.isWall(i)) assertEquals(leg.getBoolean("solid_right"), r.solidRight(i), where);
            }
            int found = 0;
            for (int side = 0; side < 2; side++) if (r.hasThread(side)) found++;
            assertEquals(threads.size, found, map.name);
            for (com.badlogic.gdx.utils.JsonValue th = threads.child; th != null; th = th.next) {
                int side = th.getInt("side");
                assertTrue(r.hasThread(side), map.name);
                assertEquals(th.getFloat("thread_x"), r.threadX(side), 0, map.name);
                assertEquals(th.getFloat("anchor_y"), r.anchorY(side), 0, map.name);
                assertEquals(th.getFloat("end_y"), r.endY(side), 0, map.name);
            }
        }
        assertTrue(maps >= 5);
    }
}
