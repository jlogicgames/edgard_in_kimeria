package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forest-1-spider (#484, D72, D102; reworked under D105 with the snail, #500, D106): the first room of the spider and of the snail.
 * x 6880-8000 (1120 px, fifth in the Rooms list), flat floor top y 320 (Y grows downward). Beats in route order: the floor spider
 * (a D102 box, x 7184, offNeg and offPos 3 tiles), the snail (x 7600, range 7584-7616) behind a one-way step (x 7440-7472, top
 * y 288) that is its pocket, a falling stone (column x 7744) and a thorn tuft (x 7872); the exit at x 7920. Scripted hero at 60
 * frames per second: these are author (mastered) measurements, not playtests.
 */
class Forest1SpiderTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    static final String ROOM = "forest-1-spider";
    private static final float ROOM_X = 6880, ROOM_END = 8000, START_X = 6912, SPIDER_X = 7184, EXIT_X = 7920, VIEW_W = 640;
    private static final float SNAIL_X = 7600, SNAIL_LEFT = 7584, SNAIL_RIGHT = 7616, STEP_X = 7440, STEP_TOP = 288, STONE_X = 7744, TUFT_X = 7872;
    private static final float RANGE_LEFT = 7136, RANGE_RIGHT = 7232;
    /** Gap from the hero's body to the spider's at which the scripted hero swings once (the blade reaches it from closer than 17 px). */
    private static final float SWORD_GAP = 8;
    /**
     * Pinned (scripted, 60 fps): the bait-and-punish route, a fireball at the spider from afar, rushing it with the sword (each then
     * answering the snail, the stone and the tuft on foot), and the deaths: the spider's bite, the gob, the stone, the tuft.
     */
    static final float BAIT_S = 11.53f, FIREBALL_S = 9.80f, RUSH_S = 10.48f, DEATH_S = 2.32f, GOB_DEATH_S = 8.18f, STONE_DEATH_S = 11.23f, TUFT_DEATH_S = 11.08f;
    private static final float TOLERANCE = 6 * STEP;
    private static final boolean DEBUG = false;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Level room() { return h.room("forest-1", ROOM); }
    static Spider spider(Level l) {
        for (int i = 0; i < l.objects.size(); i++) if (l.objects.get(i) instanceof Spider s) return s;
        return null;
    }
    static Snail snail(Level l) { return ScriptedAnswers.snail(l, ROOM_X, ROOM_END_X); }
    private static final float ROOM_END_X = 8000;
    static FallingStone stone(Level l) {
        for (int i = 0; i < l.objects.size(); i++) if (l.objects.get(i) instanceof FallingStone s) return s;
        return null;
    }
    private void frame(Level l) { l.update(STEP); pressed.clear(); }
    private static boolean inSwordReach(Level l, Spider s) {
        Rectangle e = s.hitboxBounds(), me = l.player.hitboxBounds();
        return e.x >= me.x && e.x - (me.x + me.width) < SWORD_GAP;
    }

    @Test void theRoomHoldsTheSpiderTheSnailAStoneATuftTheStepAndTheExit() {
        Level l = room();
        assertEquals("forest-1-spider", l.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_END - ROOM_X, 368), l.currentRoomBounds());
        assertEquals(START_X, l.player.position.x, 0.01f);
        assertEquals(2, l.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "two monsters, the spider and the snail");
        Spider s = spider(l);
        assertNotNull(s);
        assertEquals(SPIDER_X, s.position.x, 0.01f);
        assertEquals(RANGE_LEFT, s.rangeNeg(), 0.01f);
        assertEquals(RANGE_RIGHT, s.rangePos(), 0.01f);
        assertEquals(1, s.route().legs(), "a floor-only spider (D102): no wall, no thread");
        assertFalse(s.route().hasThread(0) || s.route().hasThread(1));
        Snail n = snail(l);
        assertNotNull(n);
        assertEquals(SNAIL_X, n.position.x, 0.01f);
        assertEquals(SNAIL_LEFT, n.rangeNeg(), 0.01f);
        assertEquals(SNAIL_RIGHT, n.rangePos(), 0.01f);
        assertEquals(STONE_X, stone(l).getX(), 0.01f);
        assertEquals(TUFT_X, ScriptedAnswers.tuft(l).getX(), 0.01f);
        assertTrue(l.ladders.isEmpty() && l.escalators.isEmpty() && l.fallingPlatforms.isEmpty() && l.triggers.isEmpty(), "nothing else");
        assertEquals("", l.player.zoneHint(), "no hint for a monster (D100)");
        l.enterNextRoom(new Player(h.assets(), h.game(), 0, 0));
        assertEquals("forest-1-bramble", l.currentRoomName(), "the bramble room follows");
    }

    /** The spider is in view, skittering, from the entrance, and it has not heard the hero there; the snail is off screen (D72). */
    @Test void theSpiderIsSeenFromTheEntranceBeforeItHearsTheHeroAndTheSnailIsNot() {
        Level l = room();
        Vector2 cam = l.player.roomCameraTarget();
        assertTrue(cam.x <= RANGE_LEFT && RANGE_RIGHT + Spider.FRAME_WIDTH <= cam.x + VIEW_W, "its whole range is on screen from the entrance");
        assertTrue(cam.x + VIEW_W <= SNAIL_LEFT, "the snail is out of view: one unknown at a time");
        float x0 = spider(l).position.x;
        boolean moved = false;
        for (int f = 0; f < 120; f++) {
            frame(l);
            assertEquals(Spider.State.CRAWL, spider(l).state(), "a hero at the entrance is not heard");
            moved |= spider(l).position.x != x0;
        }
        assertTrue(moved, "it skitters");
    }

    /** While the hero meets the spider (anywhere left of its range's right end) the snail stays out of the camera's view. */
    @Test void theSnailIsOutOfViewWhileTheSpiderIsMet() {
        Level l = room();
        for (float x = START_X; x <= RANGE_RIGHT; x += 8) {
            l.player.position.x = x;
            assertTrue(l.player.roomCameraTarget().x + VIEW_W <= SNAIL_LEFT, "the snail is in view with the hero at x " + x);
        }
    }

    /**
     * On to the exit past the snail, the stone and the tuft, answering each on foot (the spider is dead or never met): the mastered
     * route jumps the snail's gob and walks on past the snail in its reload, without a swing; it is left alive behind him, and its
     * next gob falls short of him. Pinned: the snail spat at him, never hit him, and is alive at the exit.
     */
    static int onToTheExit(Level l, Set<Integer> held, Set<Integer> pressed, int f) {
        Snail n = snail(l);
        ThornTrap tuft = ScriptedAnswers.tuft(l);
        boolean spat = false;
        while (!l.atRoomExit()) {
            assertTrue(++f < 60 * 40, "the route never reached the exit at " + l.player.position);
            held.clear(); pressed.clear();
            walkOn(l, n, tuft, held, pressed);
            l.update(STEP);
            spat |= n.gobActive();
            assertFalse(l.player.isGotHit(), "the mastered route is never hit (snail " + n.state() + ") at " + l.player.position);
            if (DEBUG && f % 6 == 0) System.out.printf(java.util.Locale.ROOT, "f %d hero %.0f,%.0f snail %s x %.0f gob %b %.0f%n", f, l.player.hitboxBounds().x, l.player.hitboxBounds().y, n.state(), n.position.x, n.gobActive(), n.gobX());
        }
        assertTrue(spat, "the snail saw him and spat");
        assertFalse(n.isDefeated(), "the snail is left alive behind him: he jumped its gob and walked on in its reload");
        return f;
    }

    /** Walk in until it commits, step back out of the locked bite, walk into its recovery and strike; then on to the exit. */
    static int baitAndStrike(Level l, Set<Integer> held, Set<Integer> pressed) {
        Spider s = spider(l);
        int f = 0, back = -1;
        boolean wasCommitted = false, swung = false;
        while (!s.isDefeated()) {
            assertTrue(++f < 60 * 30, "the spider was never struck: " + s.state());
            held.clear(); pressed.clear();
            if (s.state() == Spider.State.WINDUP && !wasCommitted) { wasCommitted = true; back = 12; }
            if (back > 0) { held.add(Input.Keys.A); back--; }
            else if (s.state() == Spider.State.WINDUP || s.state() == Spider.State.LEAP || s.state() == Spider.State.BITE) { /* wait out the miss */ }
            else {
                held.add(Input.Keys.D);
                if (wasCommitted && s.state() == Spider.State.RECOVER && inSwordReach(l, s) && !swung) { pressed.add(Input.Keys.K); swung = true; }
            }
            l.update(STEP);
            assertFalse(l.player.isGotHit(), "the mastered route is never bitten (" + s.state() + ")");
        }
        return onToTheExit(l, held, pressed, f);
    }

    @Test void baitThePounceStepBackAndStrikeItWhileItRecoversThenJumpTheGobWalkOnAndJumpTheTuft() {
        Level l = room();
        int f = baitAndStrike(l, held, pressed);
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider bait route: %.2f s%n", f * STEP);
        assertTrue(spider(l).isRemains(), "a quiet kill leaves its body (D101)");
        assertTrue(stone(l).isSpent(), "the stone fell behind the hero");
        assertEquals(BAIT_S, f * STEP, TOLERANCE, "mastered route, bait and strike (D105's beat time)");
        Level again = room();
        assertEquals(f, baitAndStrike(again, held, pressed), "a retry plays the same (D7)");
    }

    @Test void aFireballFromAfarKillsTheSpiderBeforeItHears() {
        Level l = room();
        Spider s = spider(l);
        int f = 0;
        boolean shot = false;
        while (!s.isDefeated()) {
            assertTrue(++f < 60 * 30);
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            if (!shot && l.player.hitboxBounds().x >= 7000) { pressed.add(Input.Keys.L); shot = true; }
            l.update(STEP);
            assertFalse(l.player.isGotHit());
        }
        f = onToTheExit(l, held, pressed, f);
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider fireball route: %.2f s%n", f * STEP);
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
        while (!s.isDefeated()) {
            assertTrue(++f < 60 * 30, "never struck: " + s.state());
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            if (!swung && s.isCommitted() && inSwordReach(l, s)) { pressed.add(Input.Keys.K); swung = true; }
            l.update(STEP);
            assertFalse(l.player.isGotHit(), "bitten in " + s.state());
        }
        f = onToTheExit(l, held, pressed, f);
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider rush route: %.2f s%n", f * STEP);
        assertTrue(swung, "struck during its wind-up");
        assertEquals(RUSH_S, f * STEP, TOLERANCE);
    }

    /** The spider's retry cost: a hero who walks in and stops when it commits is bitten; the room resets it calm at its spawn. */
    @Test void stoppingInItsReachIsTheSpidersDeath() {
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
        assertEquals(SNAIL_X, snail(l).position.x, 0.01f, "the snail too");
    }

    /** Past the spider, a hero who walks on along the floor and never jumps or swings is hit by the gob. */
    @Test void walkingIntoTheGobIsTheSnailsDeath() {
        Level l = room();
        baitOnly(l);
        int f = framesSoFar;
        Snail n = snail(l);
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 20, "never hit");
            held.clear(); pressed.clear(); held.add(Input.Keys.D);
            l.update(STEP);
        }
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider gob death: %.2f s at x %.0f, snail %s%n", f * STEP, l.player.hitboxBounds().x, n.state());
        assertTrue(l.player.hitboxBounds().x < SNAIL_LEFT, "hit in front of the snail, short of the stone: " + l.player.position);
        assertEquals(GOB_DEATH_S, f * STEP, TOLERANCE, "retry cost of the snail");
        l.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        assertEquals(Snail.State.CRAWL, snail(l).state(), "calm after a death, no gob in the air (D106)");
        assertFalse(snail(l).gobActive());
    }

    /** A hero who stops in the stone's column when its shadow grows is struck: the room's most expensive death. */
    @Test void stoppingUnderTheStoneIsItsDeath() {
        Level l = room();
        baitOnly(l);
        int f = framesSoFar;
        Snail n = snail(l);
        FallingStone stone = stone(l);
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 20, "never struck");
            held.clear(); pressed.clear();
            if (stone.state() == com.jlogicsoftware.kimeria.entity.objects.FallingTrap.State.ARMED
                || l.player.hitboxBounds().x + 6 < STONE_X) walkOn(l, n, null, held, pressed);
            l.update(STEP);
        }
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider stone death: %.2f s%n", f * STEP);
        assertEquals(STONE_DEATH_S, f * STEP, TOLERANCE, "retry cost of the stone");
    }

    /** Everything answered but the tuft, which the hero walks into once it has bristled. */
    @Test void walkingIntoTheTuftIsItsDeath() {
        Level l = room();
        baitOnly(l);
        int f = framesSoFar;
        Snail n = snail(l);
        while (!l.player.isGotHit()) {
            assertTrue(++f < 60 * 20, "never hit");
            held.clear(); pressed.clear();
            walkOn(l, n, null, held, pressed);
            l.update(STEP);
        }
        System.out.printf(java.util.Locale.ROOT, "forest-1-spider tuft death: %.2f s at x %.0f%n", f * STEP, l.player.position.x);
        assertTrue(l.player.hitboxBounds().x > STONE_X, "past the stone, at the tuft");
        assertEquals(TUFT_DEATH_S, f * STEP, TOLERANCE, "retry cost of the tuft");
    }

    private int framesSoFar;

    /** One frame of the mastered walk on past the snail: right, jumping a close gob (and the tuft, when given); no swing. */
    private static void walkOn(Level l, Snail n, ThornTrap tuft, Set<Integer> held, Set<Integer> pressed) {
        held.add(Input.Keys.D);
        if (ScriptedAnswers.gobClose(l, n) || ScriptedAnswers.tuftAhead(l, tuft)) ScriptedAnswers.jump(held, pressed);
    }

    /** The spider by bait and strike only, leaving the hero just past it. */
    private void baitOnly(Level l) {
        Spider s = spider(l);
        int f = 0, back = -1;
        boolean wasCommitted = false, swung = false;
        while (!s.isDefeated()) {
            assertTrue(++f < 60 * 30);
            held.clear(); pressed.clear();
            if (s.state() == Spider.State.WINDUP && !wasCommitted) { wasCommitted = true; back = 12; }
            if (back > 0) { held.add(Input.Keys.A); back--; }
            else if (s.state() == Spider.State.WINDUP || s.state() == Spider.State.LEAP || s.state() == Spider.State.BITE) { }
            else {
                held.add(Input.Keys.D);
                if (wasCommitted && s.state() == Spider.State.RECOVER && inSwordReach(l, s) && !swung) { pressed.add(Input.Keys.K); swung = true; }
            }
            l.update(STEP);
        }
        framesSoFar = f;
    }
}
