package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The flying beetle's first room, {@code forest-roost} (#330, D145), through the real map and real Level updates with ordinary
 * controls. World Y grows downward: the floor top is y 304, the one-way ledge (x 4704-4768) top y 256, level with the bottom of
 * the beetle's body, whose line lies beside the ledge's right end. The thorn root stands at x 4608, the falling stone at x 5000,
 * the exit at x 5088. Hero positions are the frame's x unless a test says "centre".
 */
class ForestRoostTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 304, LEDGE_TOP = 256, HERO_HEIGHT = 48;
    private static final int SECOND = 60;
    /** The take-off for the thorn root (the hero's frame x), as in {@code RoomReplayTest.ROOST_JUMPS}. */
    private static final float ROOT_JUMP = 4550;
    /** Where a hero on the mastered pace who stops walking dies: in the beetle's box, and under the stone. */
    private static final float STOP_AT_BEETLE = 4750, STOP_AT_STONE = 4980;
    /** Measured, pinned within 0.1 s: the mastered route and the two deaths (the card's times). */
    private static final float ROUTE_S = 5.48f, BEETLE_DEATH_S = 3.35f, STONE_DEATH_S = 6.02f;
    /** Past one whole patrol to and fro (96 px at 40 px/s each way, 4.8 s), so a start delayed this long meets every phase. */
    private static final int ALL_PHASES = 6 * SECOND;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private Level level() { return h.room("forest-2", "forest-roost"); }

    private static FlyingBeetle beetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof FlyingBeetle b) return b;
        return null;
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    /**
     * Runs right after waiting {@code delay} frames at the entrance, jumps the thorn root, and stops for good at {@code stopX}
     * (never: NaN). Returns the seconds to the exit, the negative seconds to the hit, or 0 when neither came in 20 s.
     */
    private float run(int delay, float stopX) {
        Level level = level();
        boolean jumped = false;
        for (int f = 0; f < SECOND * 20; f++) {
            h.held.clear(); h.pressed.clear();
            float x = level.player.position.x;
            if (f >= delay && !(x >= stopX)) h.held.add(Input.Keys.D);
            if (!jumped && x >= ROOT_JUMP && level.player.isOnGround()) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); jumped = true; }
            level.update(STEP);
            if (level.player.isGotHit()) return -(f + 1) * STEP;
            if (level.atRoomExit()) return (f + 1) * STEP;
        }
        return 0;
    }

    @Test void runningThroughClearsTheRoomAtEveryPhaseOfThePatrolWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            assertEquals(ROUTE_S, run(0, Float.NaN), .1f, "the card's mastered time (assist " + longer + ")");
            for (int delay = 5; delay <= ALL_PHASES; delay += 5)
                assertEquals(ROUTE_S + delay * STEP, run(delay, Float.NaN), .1f, "walked out of every box, started " + delay + " frames late, assist " + longer);
        }
    }

    @Test void theBeetleCommitsOnTheMasteredRouteSoTheBoxIsSeen() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
        boolean jumped = false;
        for (int f = 0; f < SECOND * 8 && !level.atRoomExit(); f++) {
            h.held.clear(); h.pressed.clear();
            h.held.add(Input.Keys.D);
            if (!jumped && level.player.position.x >= ROOT_JUMP && level.player.isOnGround()) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); jumped = true; }
            level.update(STEP);
            seen.add(b.state());
        }
        assertTrue(seen.containsAll(EnumSet.of(FlyingBeetle.State.NOTICE, FlyingBeetle.State.WINDUP, FlyingBeetle.State.DIVE)),
            "the beetle is a beat of the route: it notices, draws its dive and dives behind him, " + seen);
    }

    @Test void stoppingInTheBoxOrUnderTheStoneDiesAtTheCardsTimes() {
        assertEquals(-BEETLE_DEATH_S, run(0, STOP_AT_BEETLE), .1f, "stopped under the beetle");
        assertEquals(-STONE_DEATH_S, run(0, STOP_AT_STONE), .1f, "stopped under the stone");
    }

    /** Stands the hero with his frame at ({@code x}, feet on {@code surface}) and updates; with {@code leaves} he runs left once it winds up. */
    private boolean survivesAt(float x, float surface, boolean leaves, Set<FlyingBeetle.State> seen) {
        Level level = level();
        FlyingBeetle b = beetle(level);
        level.player.position.set(x, surface - HERO_HEIGHT);
        level.player.velocity().setZero();
        boolean leaving = false;
        for (int f = 0; f < SECOND * 12; f++) {
            h.held.clear();
            leaving |= leaves && b.state() == FlyingBeetle.State.WINDUP;
            if (leaving && level.player.position.x > 4660) h.held.add(Input.Keys.A);
            level.update(STEP);
            seen.add(b.state());
            if (level.player.isGotHit()) return false;
        }
        return true;
    }

    @Test void theFloorUnderTheLedgeIsSafeAndTheFloorBesideItIsNot() {
        // The hero's centre is 23.5 px right of the frame's x: 4704 to 4744 under the ledge, 4746 and on in reach of a dive.
        for (float centre = 4704; centre <= 4744; centre += 8) {
            Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
            assertTrue(survivesAt(centre - 23.5f, FLOOR, false, seen), "safe under the ledge at centre " + centre);
            assertFalse(seen.contains(FlyingBeetle.State.WINDUP), "never committed to at centre " + centre);
        }
        assertFalse(survivesAt(4748 - 23.5f, FLOOR, false, EnumSet.noneOf(FlyingBeetle.State.class)), "past the ledge's end it dives at him");
    }

    @Test void theLedgesLeftPartIsOutOfReachAndItsRightEndIsDivedAtLevelAndCanBeLeft() {
        Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
        assertTrue(survivesAt(4728 - 23.5f, LEDGE_TOP, false, seen), "out of its 48 px commit reach");
        assertFalse(seen.contains(FlyingBeetle.State.WINDUP), "never committed to: " + seen);
        assertFalse(survivesAt(4752 - 23.5f, LEDGE_TOP, false, EnumSet.noneOf(FlyingBeetle.State.class)), "level with it and in reach: it dives along the ledge");
        assertTrue(survivesAt(4752 - 23.5f, LEDGE_TOP, true, EnumSet.noneOf(FlyingBeetle.State.class)), "the drawn box can be left by running off the ledge's other end");
    }
}
