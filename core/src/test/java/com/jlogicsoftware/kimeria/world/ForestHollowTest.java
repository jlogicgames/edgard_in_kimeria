package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mosquito swarm's room, {@code forest-hollow} (#645, D151), through the real map and real Level updates with ordinary
 * keyboard controls: the mastered route at every phase of both swarms, with and without the longer-warnings assist; the stone and
 * the fireball; the deaths a careless hero meets, at the card's times; and that the exit stays reachable once the swarms are gone.
 * World Y grows downward.
 */
class ForestHollowTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float POCKET_X = 5350, STONE_X = 5316;
    /** The hero's frame x from its middle, and its frame's y on the floor (top 304). */
    private static final float HERO_MIDDLE = 23.5f, FLOOR_Y = 256;
    private static final int LIMIT = 60 * 25;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level level() { return h.room("forest-2", "forest-hollow"); }

    private static List<MosquitoSwarm> swarms(Level level) {
        List<MosquitoSwarm> found = new ArrayList<>();
        for (var o : level.objects) if (o instanceof MosquitoSwarm s) found.add(s);
        found.sort(java.util.Comparator.comparingDouble(s -> s.position.x));
        return found;
    }

    private void frame(Level level) { level.update(STEP); h.pressed.clear(); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /** Seconds to the exit by the route after waiting {@code delay} frames at the entrance; fails on a hit. */
    private float route(Level level, int delay) {
        ForestHollowRoute route = new ForestHollowRoute(level);
        for (int f = 0; f < LIMIT; f++) {
            h.held.clear(); h.pressed.clear();
            if (f >= delay) route.drive(level, h.held, h.pressed);
            level.update(STEP);
            assertTrue(alive(level), "died at frame " + f + " (delay " + delay + ") at " + level.player.position);
            if (level.atRoomExit()) { h.held.clear(); return (f + 1 - delay) * STEP; }
        }
        fail("never reached the exit, delay " + delay + ", at " + level.player.position);
        return 0;
    }

    @Test void theRoomHoldsItsTwoSwarmsTheThornAndTheStone() {
        Level level = level();
        List<MosquitoSwarm> swarms = swarms(level);
        assertEquals(2, swarms.size());
        assertFalse(swarms.get(0).isVertical);
        assertEquals(new Vector2(5408, 282), swarms.get(0).position, "its bottom 4 px over the floor");
        assertEquals(5488, swarms.get(0).rangePos(), 0);
        assertTrue(swarms.get(1).isVertical);
        assertEquals(234, swarms.get(1).rangeNeg(), 0, "its bottom 52 px up");
        assertEquals(282, swarms.get(1).rangePos(), 0, "and 4 px up");
        assertEquals(new Vector2(5248, 256), level.player.position);
    }

    /** D7, D26: the route works whenever the hero sets out: every 5 frames through 12 s, both swarms' periods covered. */
    @Test void theRouteWorksAtEveryPhaseOfBothSwarmsWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            float shortest = Float.MAX_VALUE, longest = 0;
            for (int delay = 0; delay <= 720; delay += 5) {
                float seconds = route(level(), delay);
                shortest = Math.min(shortest, seconds);
                longest = Math.max(longest, seconds);
            }
            System.out.printf(java.util.Locale.ROOT, "HOLLOW route over start delays (assist %b): %.2f to %.2f s%n", longer, shortest, longest);
            assertEquals(6.43f, shortest, .1f, "the shortest start");
            assertEquals(9.68f, longest, .1f, "the longest wait");
        }
        setLonger(false);
    }

    /**
     * The card's jump in the room (the swarm turns at its line's ends): frames of the swarm's 3.2 s period from which a running
     * jump taken standing with the body's right edge at {@code front} clears it.
     */
    private int takeOffFrames(float front) {
        int clearing = 0;
        for (int wait = 0; wait < 192; wait++) {
            Level level = level();
            MosquitoSwarm low = swarms(level).get(0);
            for (int f = 0; f < wait + 3; f++) {
                level.player.position.set(front - 29, FLOOR_Y);
                level.player.velocity().setZero();
                frame(level);
            }
            assertTrue(alive(level), "the take-off spot is safe at frame " + wait);
            h.held.add(Input.Keys.D); h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J);
            for (int f = 0; f < 54 && alive(level); f++) frame(level);
            h.held.clear();
            if (alive(level) && level.player.hitboxBounds().x > low.position.x + MosquitoSwarm.WIDTH) clearing++;
        }
        return clearing;
    }

    @Test void theJumpOverTheLowSwarmHasAWideWindowCloseToItsLine() {
        int near = takeOffFrames(5390), farther = takeOffFrames(5372);
        System.out.printf("HOLLOW take-off frames of 192: %d at 18 px short of the line, %d at 36 px%n", near, farther);
        assertEquals(76, near, 4);
        assertEquals(32, farther, 4);
    }

    /** The camera at the first meeting's pocket does not show the second swarm: one new thing at a time (D105). */
    @Test void theSecondSwarmIsOutOfViewFromThePocket() {
        Level level = level();
        MosquitoSwarm far = swarms(level).get(1);
        level.player.position.x = POCKET_X - level.player.getWidth() / 2;
        Vector2 camera = level.player.roomCameraTarget();
        Vector2 view = h.game().logicalResolution();
        assertTrue(camera.x + view.x <= far.position.x - MosquitoSwarm.SCATTER_REACH, "view to x " + (camera.x + view.x));
    }

    private void pickUpStone(Level level) {
        level.player.position.set(STONE_X + 12 - 29, FLOOR_Y);
        for (int f = 0; f < 3; f++) frame(level);
        h.held.add(Input.Keys.W); h.pressed.add(Input.Keys.W); frame(level);
        h.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked the stone up in the pocket");
    }

    private void standAtThePocket(Level level) {
        level.player.position.set(POCKET_X - HERO_MIDDLE, FLOOR_Y);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        for (int f = 0; f < 3; f++) frame(level);
    }

    /** One throw or cast from the pocket {@code delay} frames in; true when the low swarm dies. */
    private boolean shotKills(boolean stone, int delay) {
        Level level = level();
        MosquitoSwarm low = swarms(level).get(0);
        if (stone) pickUpStone(level);
        standAtThePocket(level);
        for (int f = 0; f < delay; f++) frame(level);
        h.held.add(Input.Keys.L); h.pressed.add(Input.Keys.L); frame(level);
        h.held.clear();
        for (int f = 0; f < 60 && !low.isDefeated(); f++) frame(level);
        assertTrue(alive(level), "the pocket stays safe");
        return low.isDefeated();
    }

    @Test void theStoneAndAFireballReachTheFirstSwarmFromThePocket() {
        int throwsThatKill = 0, castsThatKill = 0, tries = 0;
        for (int delay = 0; delay < 192; delay += 6) {
            tries++;
            if (shotKills(true, delay)) throwsThatKill++;
            if (shotKills(false, delay)) castsThatKill++;
        }
        System.out.printf("HOLLOW from the pocket: %d of %d throws and %d casts kill the low swarm%n", throwsThatKill, tries, castsThatKill);
        assertTrue(throwsThatKill >= tries / 3, "a throw reaches it often: " + throwsThatKill + " of " + tries);
        assertEquals(tries, castsThatKill, "a ball flies the whole room");
    }

    @Test void aMissedStoneStaysWhereItCanBePickedUpAgain() {
        Level level = level();
        pickUpStone(level);
        Carryable stone = level.player.carriedObject();
        level.player.facingRight = false;
        h.held.add(Input.Keys.L); h.pressed.add(Input.Keys.L); frame(level);
        h.held.clear();
        for (int f = 0; f < 120; f++) frame(level);
        assertFalse(stone.isRemoved());
        assertTrue(stone.available());
        assertTrue(stone.getX() >= 5232 && stone.getX() < 5936, "still in the room");
    }

    /** Seconds until a hero who only holds right from the entrance (jumping at {@code jumps}) dies; fails if he lives. */
    private float blindWalk(boolean withoutLow, boolean withoutThorn, float... jumps) {
        Level level = level();
        if (withoutLow) swarms(level).get(0).collidedWithActor(true);
        int jump = 0;
        for (int f = 0; f < LIMIT; f++) {
            h.held.clear(); h.pressed.clear();
            h.held.add(Input.Keys.D);
            if (jump < jumps.length && level.player.position.x >= jumps[jump] && level.player.isOnGround()) {
                h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); jump++;
            }
            level.update(STEP);
            if (!alive(level)) { h.held.clear(); return (f + 1) * STEP; }
            assertFalse(level.atRoomExit(), "reached the exit alive");
        }
        fail("never died");
        return 0;
    }

    /** The card's retry times: walking into the low swarm, into the thorn root, and blind under the vertical swarm. */
    @Test void carelessWalksDieAtTheCardsTimes() {
        float low = blindWalk(false, false);
        float thorn = blindWalk(true, false);
        float high = blindWalk(true, true, ForestHollowRoute.ROOT_JUMP);
        System.out.printf(java.util.Locale.ROOT, "HOLLOW deaths: low swarm %.2f s, thorn root %.2f s, vertical swarm %.2f s%n", low, thorn, high);
        assertEquals(1.97f, low, .1f);
        assertEquals(3.73f, thorn, .1f);
        assertEquals(5.98f, high, .1f);
    }

    @Test void standingOnTheLineAndSwingingDoesNotClearTheWay() {
        Level level = level();
        MosquitoSwarm low = swarms(level).get(0);
        for (int f = 0; f < 60 * 4 && low.position.x < 5470; f++) frame(level);
        level.player.position.set(5415 - HERO_MIDDLE, FLOOR_Y);
        for (int f = 0; f < 60 * 8 && alive(level); f++) {
            h.held.clear(); h.pressed.clear();
            if (f % 45 == 0) { h.held.add(Input.Keys.K); h.pressed.add(Input.Keys.K); }
            level.update(STEP);
        }
        assertFalse(alive(level), "it comes on through the swings");
        assertFalse(low.isDefeated());
        Rectangle shape = low.shapeInto(new Rectangle());
        assertTrue(shape.width >= MosquitoSwarm.WIDTH);
    }
}
