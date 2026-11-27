package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
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
 * The flying beetle's first room, {@code forest-roost} (#330, D145, D146), through the real map and real Level updates with
 * ordinary controls. World Y grows downward: the floor top is y 304, the one-way ledge (x 4800-4864) top y 256, level with the
 * bottom of the beetle's body on its line, which crosses the ledge: the beetle flies over it, 48 px above its top. The thorn
 * root stands at x 4608, the falling stone at x 5096, the exit at x 5184. Hero positions are the frame's x unless a test says
 * "centre".
 */
class ForestRoostTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 304, LEDGE_TOP = 256, HERO_HEIGHT = 48;
    private static final float LEDGE_LEFT = 4800, LEDGE_WIDTH = 64, LEDGE_HEIGHT = 16;
    /** The hero's centres under the ledge that no dive reaches (measured). */
    private static final float SAFE_LEFT = 4800, SAFE_RIGHT = 4848;
    /** A hero leaving a blast's reach runs this far from where he stood. */
    private static final float CLEAR_RUN = 90;
    private static final int SECOND = 60;
    /** The take-off for the thorn root (the hero's frame x), as in {@code RoomReplayTest.ROOST_JUMPS}. */
    private static final float ROOT_JUMP = 4550;
    /** Where a hero on the mastered pace who stops walking dies: in the beetle's box, and under the stone. */
    private static final float STOP_AT_BEETLE = 4870, STOP_AT_STONE = 5076;
    /** Measured, pinned within 0.1 s: the mastered route and the two deaths (the card's times). */
    private static final float ROUTE_S = 6.43f, BEETLE_DEATH_S = 4.42f, STONE_DEATH_S = 7.0f;
    /** Past one whole patrol to and fro (224 px at 40 px/s each way, 11.2 s), so a start delayed this long meets every phase. */
    private static final int ALL_PHASES = 12 * SECOND;

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

    @Test void theBeetleCommitsOnTheMasteredRouteAtEveryPhaseSoTheBoxIsSeen() {
        for (int delay = 0; delay <= ALL_PHASES; delay += 5) {
            Level level = level();
            FlyingBeetle b = beetle(level);
            Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
            boolean jumped = false;
            for (int f = 0; f < SECOND * 20 && !level.atRoomExit(); f++) {
                h.held.clear(); h.pressed.clear();
                if (f >= delay) h.held.add(Input.Keys.D);
                if (!jumped && level.player.position.x >= ROOT_JUMP && level.player.isOnGround()) { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); jumped = true; }
                level.update(STEP);
                seen.add(b.state());
            }
            assertTrue(seen.containsAll(EnumSet.of(FlyingBeetle.State.NOTICE, FlyingBeetle.State.WINDUP, FlyingBeetle.State.DIVE)),
                "the beetle is a beat of the route: it notices, draws its dive and dives behind him, started " + delay + " frames late: " + seen);
        }
    }

    @Test void stoppingInTheBoxOrUnderTheStoneDiesAtTheCardsTimes() {
        assertEquals(-BEETLE_DEATH_S, run(0, STOP_AT_BEETLE), .1f, "stopped under the beetle");
        assertEquals(-STONE_DEATH_S, run(0, STOP_AT_STONE), .1f, "stopped under the stone");
    }

    /** Stands the hero with his centre at {@code centre} on {@code surface} and updates; with {@code leaves} he runs once it winds up. */
    private boolean survivesAt(float centre, float surface, int leaves, Set<FlyingBeetle.State> seen) {
        Level level = level();
        FlyingBeetle b = beetle(level);
        level.player.position.set(centre - level.player.getWidth() / 2, surface - HERO_HEIGHT);
        level.player.velocity().setZero();
        boolean leaving = false;
        for (int f = 0; f < SECOND * 14; f++) {
            h.held.clear();
            leaving |= leaves != 0 && b.state() == FlyingBeetle.State.WINDUP;
            if (leaving && Math.abs(level.player.centerX() - centre) < CLEAR_RUN) h.held.add(leaves < 0 ? Input.Keys.A : Input.Keys.D);
            level.update(STEP);
            seen.add(b.state());
            if (level.player.isGotHit()) return false;
        }
        return true;
    }

    @Test void itFliesOverTheLedgeAndSinksBehindIt() {
        Level level = level();
        FlyingBeetle b = beetle(level);
        float line = b.floorY(), highest = line, left = b.position.x;
        for (int f = 0; f < SECOND * 12; f++) {
            level.update(STEP);
            highest = Math.min(highest, b.floorY());
            left = Math.min(left, b.position.x);
            Rectangle body = b.hitboxBoundsInto(new Rectangle());
            assertFalse(body.overlaps(new Rectangle(LEDGE_LEFT, LEDGE_TOP, LEDGE_WIDTH, LEDGE_HEIGHT)), "never through the ledge: " + body);
            if (body.x < LEDGE_LEFT + LEDGE_WIDTH && body.x + body.width > LEDGE_LEFT)
                assertEquals(LEDGE_TOP - FlyingBeetle.CLIMB_HEIGHT, b.floorY(), .01f, "this high over the ledge");
        }
        assertEquals(LEDGE_TOP - FlyingBeetle.CLIMB_HEIGHT, highest, .01f);
        assertEquals(b.rangeNeg(), left, 1f, "past the ledge to its range end");
        assertTrue(b.routeBottom(left) > highest, "and down again behind it");
    }

    @Test void theFloorUnderTheLedgeIsSafeAndTheFloorOnEitherSideIsNot() {
        for (float centre = SAFE_LEFT; centre <= SAFE_RIGHT; centre += 8) {
            Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
            assertTrue(survivesAt(centre, FLOOR, 0, seen), "safe under the ledge at centre " + centre);
            assertFalse(seen.contains(FlyingBeetle.State.WINDUP), "never committed to at centre " + centre);
        }
        for (float centre : new float[]{4704, 4736, 4772, 4792, 4856, 4888})
            assertFalse(survivesAt(centre, FLOOR, 0, EnumSet.noneOf(FlyingBeetle.State.class)), "beside the ledge it dives at him, centre " + centre);
    }

    @Test void noDiveItCommitsToStopsShortOnTheLedgesCorners() {
        for (float centre = SAFE_LEFT - 32; centre <= SAFE_RIGHT + 32; centre++) {
            Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
            boolean safe = survivesAt(centre, FLOOR, 0, seen);
            assertFalse(safe && seen.contains(FlyingBeetle.State.WINDUP), "a committed dive reaches a hero who stays, centre " + centre);
        }
    }

    @Test void aBlastBesideTheLedgeCanBeLeft() {
        for (float centre : new float[]{4750, 4790, 4880})
            for (int way : new int[]{-1, 1})
                assertTrue(survivesAt(centre, FLOOR, way, EnumSet.noneOf(FlyingBeetle.State.class)), "left from centre " + centre + ", way " + way);
    }

    @Test void onTheLedgeItDivesFromAboveAndTheBoxCanBeLeft() {
        for (float centre = LEDGE_LEFT + 4; centre < LEDGE_LEFT + LEDGE_WIDTH; centre += 8) {
            Set<FlyingBeetle.State> seen = EnumSet.noneOf(FlyingBeetle.State.class);
            assertFalse(survivesAt(centre, LEDGE_TOP, 0, seen), "dived at on the ledge, centre " + centre);
            assertTrue(seen.contains(FlyingBeetle.State.DIVE), "by a dive: " + seen);
            for (int way : new int[]{-1, 1})
                assertTrue(survivesAt(centre, LEDGE_TOP, way, EnumSet.noneOf(FlyingBeetle.State.class)), "left from centre " + centre + ", way " + way);
        }
    }
}
