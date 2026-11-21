package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code forest-walls} after the wall jump (#545, D116), through the real map and real Level updates with ordinary controls: the
 * way over the two walls is the tree in front of the first wall and a leap from its top branch, and the way out of the gap
 * between the walls is the second tree. World Y grows downward: the floor top is y 320, wall 1 (x 912-928) top y 208, wall 2
 * (x 992-1008) top y 224. Positions are the body's centre x and feet y (the body is 11 x 22 px).
 */
class ForestWallsTest {
    private static final float STEP = HeadlessLevelHarness.STEP, FLOOR = 320, WALL2_TOP = 224, A3_TOP = 176;
    /** The strip of A3's top (body centre x) from which the leap lands on the second wall's top, measured (the card). */
    private static final float LEAP_FROM_MIN = 882, LEAP_FROM_MAX = 908;
    private float redMobAt;
    /** How each mob died in the last drive: its class name to {@code stomp} (the bounce, the hero in the air) or {@code sword}. */
    private final java.util.Map<String, String> killedBy = new java.util.HashMap<>();
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level level() { return h.room("forest-2", "forest-walls"); }

    private static float cx(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private static float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }

    /** Stands the hero with the body centre at {@code x} on a surface whose top is {@code top}, still, facing right. */
    private void place(Level level, float x, float top) {
        level.player.position.set(x - 23.5f, top - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        h.held.clear();
    }

    private static Collectable bat(Level level) {
        for (int i = 0; i < level.objects.size(); i++)
            if (level.objects.get(i) instanceof Collectable c && c.type() == com.jlogicsoftware.kimeria.entity.items.ItemType.BAT) return c;
        throw new AssertionError("the room has its Bat");
    }

    /** Drives the whole room; returns the seconds to the exit, and records the Bat and the wall grabs. */
    private float drive(ForestWallsRoute route, Level level, boolean[] out) {
        boolean sword = false;
        redMobAt = -1;
        killedBy.clear();
        java.util.Set<Enemy<?>> dead = new java.util.HashSet<>();
        Collectable bat = bat(level);
        for (int f = 0; f < 60 * 30; f++) {
            h.held.clear(); h.pressed.clear();
            route.drive(level, h.held, h.pressed);
            assertTrue(!h.pressed.contains(Input.Keys.J) || level.player.isOnGround(), "J is pressed only while grounded, so a buffered press can never consume a grab, at frame " + f);
            Rectangle me = level.player.hitboxBounds();
            boolean action = false;
            for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Enemy<?> e) {
                Rectangle r = e.hitboxBounds();
                if (level.player.isOnGround() && r.x >= me.x && r.x - (me.x + me.width) < 18 && r.y < me.y + me.height && r.y + r.height > me.y) action = true;
            }
            if (action && !sword) h.pressed.add(Input.Keys.K);
            sword = action;
            level.update(STEP);
            for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Enemy<?> e && e.isDefeated() && dead.add(e))
                killedBy.put(e.getClass().getSimpleName(), !level.player.isOnGround() && level.player.velocity().y < 0 ? "stomp" : "sword");
            if (level.player.isClambering()) out[0] = true;
            if (redMobAt < 0 && cx(level) >= 1136) redMobAt = (f + 1) * STEP;
            if (level.player.isGotHit()) return -(f + 1) * STEP;
            if (level.atRoomExit()) { out[1] = bat.isCollected(); return (f + 1) * STEP; }
        }
        fail("never reached the exit at " + level.player.position + " step " + route.step());
        return 0;
    }

    @Test void theMasteredRouteClimbsTheTreeLeapsOnToTheSecondWallAndReachesTheExitWithoutAWallGrab() {
        Level level = level();
        boolean[] out = new boolean[2];
        float seconds = drive(new ForestWallsRoute(false), level, out);
        System.out.printf("walls mastered route: %.2f s%n", seconds);
        assertTrue(seconds > 0 && seconds <= 20, "reached the exit alive within D19's 20 s: " + seconds);
        assertEquals(5.55f, seconds, .1f, "the card's beat time (RoomReplayTest pins the same)");
        assertEquals(4.75f, redMobAt, .1f, "the RedMob's place (x 1136) is passed this far into the route");
        assertEquals(java.util.Map.of("YellowMob", "stomp", "RedMob", "stomp"), killedBy, "what kills each mob on the route");
        assertFalse(out[0], "the route never grabs a wall (#546)");
        assertTrue(out[1], "the leap lands on the second wall's top, on the Bat");
    }

    /** Leaps from A3's top branch at body centre x {@code x} (settled first, D held, one jump); returns the first landing "x/feet", or "hit". */
    private boolean grabbed;

    private String leap(float x) {
        grabbed = false;
        Level level = level();
        place(level, x, A3_TOP);
        for (int f = 0; f < 10; f++) level.update(STEP);
        assertTrue(level.player.isOnGround(), "settled on A3 at " + level.player.position);
        boolean up = false;
        for (int f = 0; f < 150; f++) {
            h.held.clear(); h.pressed.clear();
            h.held.add(Input.Keys.D);
            if (f == 0) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }
            level.update(STEP);
            if (level.player.isClambering()) grabbed = true;
            if (level.player.isGotHit()) return "hit";
            if (!level.player.isOnGround()) up = true;
            if (up && level.player.isOnGround()) return String.format("%.0f/%.0f", cx(level), feet(level));
        }
        return "none";
    }

    @Test void theLeapFromTheTopBranchLandsOnTheSecondWallFromA27PxStripOfTheBranch() {
        for (float x = 876; x <= 912; x++) {
            String r = leap(x);
            System.out.printf("leap from %.0f: %s%n", x, r);
            boolean onWall2 = r.endsWith("/" + (int) WALL2_TOP);
            if (onWall2 || x >= 909) assertFalse(grabbed, "a leap that lands or overshoots never touches a wall face, take-off " + x);
            assertEquals(x >= LEAP_FROM_MIN && x <= LEAP_FROM_MAX, onWall2, "take-off at " + x + " lands " + r);
        }
        // The mastered take-off (ForestWallsRoute.LEAP, x 896) is inside the strip, 14 px from its low end and 12 px from its high end.
        assertEquals(14f, ForestWallsRoute.LEAP - LEAP_FROM_MIN, 0f);
        assertEquals(12f, LEAP_FROM_MAX - ForestWallsRoute.LEAP, 0f);
    }

    @Test void aWalkerOffTheTopBranchDropsOnToTheSecondTreesBranchAndTheLeapOvershootMeetsTheYellowMob() {
        Level level = level();
        place(level, 905, A3_TOP);
        for (int f = 0; f < 10; f++) level.update(STEP);
        for (int f = 0; f < 150; f++) { h.held.clear(); h.held.add(Input.Keys.D); level.update(STEP); assertFalse(level.player.isClambering(), "the walker never grabs a wall"); if (feet(level) > 200 && level.player.isOnGround()) break; }
        System.out.printf("walk off A3 at 905: lands x %.1f feet %.1f%n", cx(level), feet(level));
        assertEquals(272f, feet(level), 0.6f, "on B1, 48 px above the gap's floor");
        assertTrue(cx(level) >= 944 && cx(level) <= 976, "on B1's span");
        assertEquals("hit", leap(912), "a leap from the end of the branch overshoots the wall into the YellowMob's range");
    }

    @Test void aLateLeapOvershootsTheSecondWallAndIsBittenByTheYellowMobWithinFourSeconds() {
        Level level = level();
        boolean[] out = new boolean[2];
        float seconds = drive(new ForestWallsRoute(false, 910), level, out);
        assertFalse(out[0], "the overshoot never grabs a wall");
        System.out.printf("late leap death: %.2f s%n", seconds);
        assertTrue(seconds < 0, "hit by the YellowMob");
        assertEquals(3.73f, -seconds, .1f, "the card's cheapest death: the climb, the late leap and the bite");
    }

    @Test void aHeroWhoDropsIntoTheGapClimbsOutByTheSecondTreeToTheTopOfTheSecondWall() {
        Level level = level();
        place(level, 950, FLOOR);
        ForestWallsRoute route = new ForestWallsRoute(true);
        int frames = 0;
        for (int f = 0; f < 60 * 10 && !route.onWall2(); f++) {
            h.held.clear(); h.pressed.clear();
            route.drive(level, h.held, h.pressed);
            assertTrue(!h.pressed.contains(Input.Keys.J) || level.player.isOnGround(), "J is pressed only while grounded");
            level.update(STEP);
            frames = f + 1;
            assertFalse(level.player.isClambering(), "no wall grab at frame " + f + " at " + level.player.position);
            assertFalse(level.player.isGotHit());
        }
        System.out.printf("gap climb: %.2f s%n", frames * STEP);
        assertTrue(route.onWall2(), "out of the gap by branch B1 at " + level.player.position + " step " + route.step());
        assertEquals(WALL2_TOP, feet(level), 0.6f);
    }
}
