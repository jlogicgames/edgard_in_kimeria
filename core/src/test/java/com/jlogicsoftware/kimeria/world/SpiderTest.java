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

    @Test void itLosesAHeroWhoGetsFar() {
        Level level = level();
        Spider s = spider(level);
        place(level, HEARD);
        until(level, () -> s.state() == Spider.State.CHASE, 60);
        place(level, 61);
        frame(level);
        assertEquals(Spider.State.CRAWL, s.state());
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
}
