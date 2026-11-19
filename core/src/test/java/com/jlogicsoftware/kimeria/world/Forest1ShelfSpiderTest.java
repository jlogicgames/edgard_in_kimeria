package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forest-1-route's spider (#484, D103): the silk drop's first room. The spider lives on the ladder slab (x 1552-1632, top y 208)
 * and hangs its thread from the slab's right lip (x 1632) down over the mandatory floor (y 320). The rest of the room is
 * {@link Forest1RouteTest}'s. Y grows downward. Scripted hero at 60 frames per second.
 */
class Forest1ShelfSpiderTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    static final String ROOM = "forest-1-route";
    private static final float SLAB_X = 1552, LIP_X = 1632, SLAB_TOP = 208, FLOOR = 320, THREAD_X = 1638, THREAD_END = FLOOR - 4 - 16;
    /** The drop box: 8 px of fang column under the thread (D103). */
    private static final float BOX_LEFT = 1635, BOX_RIGHT = 1643;
    /** Pinned (scripted, 60 fps): a hero who stops under the lip is bitten this long after the entrance; the room's other deaths are Forest1RouteTest's. */
    static final float DEATH_S = 3.43f;
    private static final float TOLERANCE = 6 * STEP;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() { return h.room("forest-1", ROOM); }
    private static Spider spider(Level l) { return Forest1SpiderTest.spider(l); }
    private void frame(Level l) { l.update(STEP); pressed.clear(); }
    private float centreX(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width / 2; }

    @Test void itLivesOnTheLadderSlabAndHangsItsThreadFromTheSlabsRightLipOverTheFloor() {
        Level l = room();
        assertEquals("forest-1-route", l.currentRoomName());
        Spider s = spider(l);
        Spider.Route r = s.route();
        assertEquals(1, r.legs(), "one floor, the slab: no wall here (one new ability per room, D103)");
        assertEquals(SLAB_TOP, r.line(0), 0.01f);
        assertEquals(SLAB_X - 2, r.lo(0), 0.01f);
        assertEquals(LIP_X - 22, r.hi(0), 0.01f);
        assertTrue(r.hasThread(0) && !r.hasThread(1), "the thread is at the route's start");
        assertEquals(THREAD_X, r.threadX(0), 0.01f);
        assertEquals(SLAB_TOP, r.anchorY(0), 0.01f);
        assertEquals(THREAD_END, r.endY(0), 0.01f, "4 px over the floor");
        assertEquals(new Rectangle(BOX_LEFT, SLAB_TOP, BOX_RIGHT - BOX_LEFT, THREAD_END + 16 - SLAB_TOP), r.dropBoxInto(0, new Rectangle()));
        assertEquals(LIP_X - 22, s.position.x, 0.01f, "it starts at the lip");
        assertEquals("", l.player.zoneHint(), "no hint for a monster (D100)");
    }

    /**
     * The first drop is shown safely: a hero who keeps walking along the mandatory floor sets it off and is past the column when it
     * falls, so it is seen and heard missing behind him (D93), at every phase of its skitter and with the assist.
     */
    @Test void aHeroWhoKeepsWalkingSetsOffTheDropAndItMissesBehindHim() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (int wait = 0; wait <= 72; wait += 4) {
                Level l = room();
                Spider s = spider(l);
                for (int i = 0; i < wait; i++) frame(l);
                h.sounds.clear();
                boolean dropped = false;
                for (int f = 0; f < 60 * 6 && centreX(l) < 1780; f++) {
                    held.clear(); held.add(Input.Keys.D);
                    frame(l);
                    assertFalse(l.player.isGotHit(), "bitten walking on (wait " + wait + ", assist " + longer + ")");
                    dropped |= s.state() == Spider.State.DROP;
                }
                held.clear();
                for (int f = 0; f < 60 && !dropped; f++) { frame(l); dropped |= s.state() == Spider.State.DROP; }
                assertTrue(dropped, "it dropped behind the walker (wait " + wait + ", assist " + longer + ")");
                assertTrue(h.sounds.contains("spider_drop") && h.sounds.contains("spider_bite"), "the drop and the miss are heard (D93)");
                assertFalse(l.player.isGotHit());
            }
        }
    }

    private void setLonger(boolean longer) throws Exception {
        var field = com.jlogicsoftware.kimeria.TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /** The retry cost: a hero who walks in and stops under the lip is bitten; a retry brings it back calm on its slab, the thread drawn in. */
    @Test void stoppingUnderTheLipIsTheDeathAndARetryResetsItOnItsSlab() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 10, "never bitten: " + s.state());
            held.clear(); pressed.clear();
            if (centreX(l) < (BOX_LEFT + BOX_RIGHT) / 2) held.add(Input.Keys.D);
            l.update(STEP);
        }
        assertEquals(DEATH_S, f * STEP, TOLERANCE, "retry cost");
        l.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        Spider again = spider(l);
        assertEquals(Spider.State.CRAWL, again.state(), "calm after a death (D3)");
        assertEquals(SLAB_TOP - 16, again.position.y, 0.01f, "on its slab");
    }

    /** Step aside from the column when it sets, let it miss, and strike it as it hangs at the hero's height. */
    @Test void stepAsideThenTheSwordKillsItHangingAndTheThreadSnaps() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        while (s.state() != Spider.State.SET) {
            assertTrue(++f < 60 * 10, "it never set: " + s.state());
            held.clear(); pressed.clear();
            if (centreX(l) < (BOX_LEFT + BOX_RIGHT) / 2) held.add(Input.Keys.D);
            l.update(STEP);
        }
        for (int i = 0; i < 12; i++) { held.clear(); held.add(Input.Keys.A); frame(l); }   // a step back out of the column
        held.clear();
        while (s.state() != Spider.State.HANG) { assertTrue(++f < 60 * 12); frame(l); assertFalse(l.player.isGotHit(), "out of the column: a miss"); }
        held.add(Input.Keys.D); frame(l); held.clear();   // face it
        pressed.add(Input.Keys.K); frame(l);
        for (int i = 0; i < 30 && !s.isDefeated(); i++) frame(l);
        assertTrue(s.isDefeated(), "the blade reaches it hanging low");
        assertTrue(h.sounds.contains("spider_snap"), "the thread is heard snapping");
        for (int i = 0; i < 90; i++) frame(l);
        assertTrue(s.isRemains(), "its body lies on the floor (D101)");
        assertEquals(FLOOR, s.floorY(), 0.5f);
        assertFalse(l.player.isGotHit());
    }
}
