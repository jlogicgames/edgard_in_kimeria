package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forest-1-spider (#484, D72, D102): the spider's first room. One floor spider (a D102 box, x 7184, offNeg and offPos 3 tiles)
 * alone on the flat floor of a one-screen room appended at the map's end (x 6880-7520), fifth in the Rooms list. Y grows
 * downward: floor top y 320. Scripted hero at 60 frames per second: these are author (mastered) measurements, not playtests.
 */
class Forest1SpiderTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    static final String ROOM = "forest-1-spider";
    private static final float ROOM_X = 6880, ROOM_END = 7520, START_X = 6912, SPIDER_X = 7184, EXIT_X = 7456, VIEW_W = 640;
    private static final float RANGE_LEFT = 7136, RANGE_RIGHT = 7232;
    /** Gap from the hero's body to the spider's at which the scripted hero swings once (the blade reaches it from closer than 17 px). */
    private static final float SWORD_GAP = 8;
    /** Pinned (scripted, 60 fps): the bait-and-punish route, a fireball from afar, walking straight past, and the walk-in death. */
    static final float BAIT_S = 6.90f, FIREBALL_S = 5.17f, RUSH_S = 5.85f, DEATH_S = 3.58f;
    private static final float TOLERANCE = 6 * STEP;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() { return h.room("forest-1", ROOM); }
    static Spider spider(Level l) {
        for (int i = 0; i < l.objects.size(); i++) if (l.objects.get(i) instanceof Spider s) return s;
        return null;
    }
    private void frame(Level l) { l.update(STEP); pressed.clear(); }
    private static boolean inSwordReach(Level l, Spider s) {
        Rectangle e = s.hitboxBounds(), me = l.player.hitboxBounds();
        return e.x >= me.x && e.x - (me.x + me.width) < SWORD_GAP;
    }

    @Test void theRoomHoldsOneFloorSpiderAndTheExitAndNothingElse() {
        Level l = room();
        assertEquals("forest-1-spider", l.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_END - ROOM_X, 368), l.currentRoomBounds());
        assertEquals(START_X, l.player.position.x, 0.01f);
        assertEquals(1, l.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "one monster, the spider");
        Spider s = spider(l);
        assertNotNull(s);
        assertEquals(SPIDER_X, s.position.x, 0.01f);
        assertEquals(RANGE_LEFT, s.rangeNeg(), 0.01f);
        assertEquals(RANGE_RIGHT, s.rangePos(), 0.01f);
        assertEquals(1, s.route().legs(), "a floor-only spider (D102): no wall, no thread");
        assertFalse(s.route().hasThread(0) || s.route().hasThread(1));
        assertTrue(l.ladders.isEmpty() && l.escalators.isEmpty() && l.fallingPlatforms.isEmpty() && l.triggers.isEmpty(), "nothing else");
        assertEquals("", l.player.zoneHint(), "no hint for a monster (D100)");
        l.enterNextRoom(new Player(h.assets(), h.game(), 0, 0));
        assertEquals("forest-1-bramble", l.currentRoomName(), "the bramble room follows");
    }

    /** It is in view, skittering, from the entrance, and it has not heard the hero there: the player watches it first. */
    @Test void itIsSeenFromTheEntranceBeforeItHearsTheHero() {
        Level l = room();
        Vector2 cam = l.player.roomCameraTarget();
        assertTrue(cam.x <= RANGE_LEFT && RANGE_RIGHT + Spider.FRAME_WIDTH <= cam.x + VIEW_W, "its whole range is on screen from the entrance");
        assertTrue(cam.x <= EXIT_X && EXIT_X + 32 <= cam.x + VIEW_W, "and the exit");
        float x0 = spider(l).position.x;
        boolean moved = false;
        for (int f = 0; f < 120; f++) {
            frame(l);
            assertEquals(Spider.State.CRAWL, spider(l).state(), "a hero at the entrance is not heard");
            moved |= spider(l).position.x != x0;
        }
        assertTrue(moved, "it skitters");
    }

    /** Walk in until it commits, step back out of the locked bite, walk into its recovery and strike; then on to the exit. */
    static int baitAndStrike(Level l, Set<Integer> held, Set<Integer> pressed) {
        Spider s = spider(l);
        int f = 0, back = -1;
        boolean wasCommitted = false, swung = false;
        while (!l.atRoomExit()) {
            assertTrue(++f < 60 * 30, "the route never reached the exit: " + s.state());
            held.clear(); pressed.clear();
            boolean alive = !s.isDefeated();
            if (alive && s.state() == Spider.State.WINDUP && !wasCommitted) { wasCommitted = true; back = 12; }
            if (back > 0) { held.add(Input.Keys.A); back--; }
            else if (alive && (s.state() == Spider.State.WINDUP || s.state() == Spider.State.LEAP || s.state() == Spider.State.BITE)) { /* wait out the miss */ }
            else {
                held.add(Input.Keys.D);
                if (alive && wasCommitted && s.state() == Spider.State.RECOVER && inSwordReach(l, s) && !swung) { pressed.add(Input.Keys.K); swung = true; }
            }
            l.update(STEP);
            assertFalse(l.player.isGotHit(), "the mastered route is never bitten (" + s.state() + ")");
        }
        assertTrue(s.isDefeated(), "struck in its recovery");
        return f;
    }

    @Test void baitThePounceStepBackAndStrikeItWhileItRecovers() {
        Level l = room();
        int f = baitAndStrike(l, held, pressed);
        assertTrue(spider(l).isRemains(), "a quiet kill leaves its body (D101)");
        assertEquals(BAIT_S, f * STEP, TOLERANCE, "mastered route, bait and strike");
        Level again = room();
        assertEquals(f, baitAndStrike(again, held, pressed), "a retry plays the same (D7)");
    }

    @Test void aFireballFromAfarKillsItBeforeItHears() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        boolean shot = false;
        while (!l.atRoomExit()) {
            assertTrue(++f < 60 * 30);
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            if (!shot && l.player.hitboxBounds().x >= 7000) { pressed.add(Input.Keys.L); shot = true; }
            l.update(STEP);
            assertFalse(l.player.isGotHit());
        }
        assertTrue(s.isDefeated(), "the fireball reached it");
        assertEquals(1, 3 - l.rangedStock.count(), "one charge spent");
        assertEquals(FIREBALL_S, f * STEP, TOLERANCE);
    }

    /**
     * Its range ends where the hero comes in, so a pounce at a hero walking on lands on him: walking past is no answer here, as it
     * is for the beetle. Rushing in with the sword during the wind-up is (D102: 31 px of the 48 to cover in 0.5 s).
     */
    @Test void rushingInWithTheSwordDuringTheWindUpWins() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        boolean swung = false;
        while (!l.atRoomExit()) {
            assertTrue(++f < 60 * 30, "never reached the exit: " + s.state());
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            if (!swung && s.isCommitted() && inSwordReach(l, s)) { pressed.add(Input.Keys.K); swung = true; }
            l.update(STEP);
            assertFalse(l.player.isGotHit(), "bitten in " + s.state());
        }
        assertTrue(swung && s.isDefeated(), "struck during its wind-up");
        assertEquals(RUSH_S, f * STEP, TOLERANCE);
    }

    /** The retry cost: a hero who walks in and stops when it commits is bitten, and the room resets the spider calm at its spawn. */
    @Test void stoppingInItsReachIsTheOneDeath() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 10, "never bitten");
            held.clear(); pressed.clear();
            if (s.state() != Spider.State.WINDUP && s.state() != Spider.State.LEAP && s.state() != Spider.State.BITE && !s.isCommitted()) held.add(Input.Keys.D);
            l.update(STEP);
        }
        assertEquals(DEATH_S, f * STEP, TOLERANCE, "retry cost");
        l.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        Spider again = spider(l);
        assertEquals(Spider.State.CRAWL, again.state(), "calm after a death (D3)");
        assertEquals(SPIDER_X, again.position.x, 0.01f);
    }
}
