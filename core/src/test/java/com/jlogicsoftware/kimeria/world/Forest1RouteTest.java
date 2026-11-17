package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest-1 route room (#187, D67): one lever drives a ladder and a lift in opposite states. The lift is the
 * mandatory way to the checkpoint ledge and runs from the start; pulling the lever stops it and lowers the ladder to
 * the optional ledge that holds a fireball refill. Ordinary keyboard input drives the hero through the real shipped
 * map. Times are author (mastered) measurements of a scripted hero, not novice playtests. Recipe: docs/ROOMS.md.
 *
 * <p>Y grows downward: the floor top is y 320, the slab 112 px high has its top at y 208, the one-shot step y 168, the refill pad y 128 and the lift runs between
 * y 304 (bottom) and y 176 (top); the exit ledge's top is y 144, 32 px above the lift's top. Everything below is a map coordinate (room 2 starts at x 1280).
 */
class Forest1RouteTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 1280, ROOM_END = 2112, START_X = 1344, FLOOR = 320;
    private static final float LEVER_X = 1440, LADDER_X = 1536, SLAB_X = 1552, SLAB_END = 1632, SLAB_TOP = 208;
    /** The one-shot step (a falling platform, y 168) and the refill pad above it (x 1696-1744, top y 128, refill at x 1712). */
    private static final float STEP_X = 1648, STEP_END = 1680, STEP_TOP = 168, PAD_X = 1696, PAD_END = 1744, PAD_TOP = 128, REFILL_X = 1712;
    private static final float LIFT_X = 1936, LIFT_RIGHT = 1968, BOTTOM = 304, TOP = 176, LEDGE_X = 1968, LEDGE_TOP = 144, CHECKPOINT_X = 2048;
    /** The bat's beat (x of the body; the part that hurts is x + 3 .. x + 13): 1856 to 1904. */
    private static final float BAT_X = 1856, BAT_END = 1904;
    /** The budget for a mastered route (D19, ROOMS.md "Replay budget"): about 20 s. */
    private static final float BUDGET = 20f;
    /** The scripted rider hops onto the exit ledge once the lift is this high (the ledge is 32 px above its top, a hop is about 58 px). */
    private static final float RIDE_JUMP_Y = 195;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    private RangedStock stock;
    /** True while the scripted rider spends a fireball on the bat when it has one (the optional shortcut); false means sword or hop only. */
    private boolean shootTheBat;
    private boolean actionWasDown;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }

    /** The whole level from a restart, with no charges (the required route needs none), standing at the route room's entrance. */
    private Level room() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        level.enterNextRoom(player()); // forest-1-mob (#375) -> forest-1-traps
        level.enterNextRoom(player());
        theBat = level.objects.stream().filter(Bat.class::isInstance).map(Bat.class::cast).findFirst().orElseThrow();
        return level;
    }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private Ladder ladder(Level level) { return level.ladders.get(0); }
    private Escalator lift(Level level) { return level.escalators.get(0); }
    private Lever lever(Level level) { return level.objects.stream().filter(Lever.class::isInstance).map(Lever.class::cast).findFirst().orElseThrow(); }
    private float feet(Level level) { Rectangle b = level.player.hitboxBounds(); return b.y + b.height; }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }
    private boolean riding(Level level) { return level.player.currentEscalator() == lift(level); }
    /** Since #104 the route room ends at a RoomExit (the checkpoint moved to the guard room), at the x the checkpoint used to stand at. */
    private boolean won(Level level) { return level.atRoomExit(); }
    private void alive(Level level) { assertFalse(level.player.isGotHit(), "died at " + level.player.position); }
    private boolean refillGone(Level level) { return level.objects.stream().noneMatch(o -> o instanceof FireballRefill && !o.isRemoved()); }
    private void jump() { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    private Bat theBat;
    /** The room's bat, remembered at load because a slain bat leaves the object list. */
    private Bat bat(Level level) { return level.objects.stream().filter(Bat.class::isInstance).map(Bat.class::cast).findFirst().orElse(theBat); }
    /** Geometry sweeps teleport the hero all over the floor; the bat has its own tests. */
    private void withoutTheBat(Level level) { level.objects.removeIf(Bat.class::isInstance); }
    private Level roomWithoutTheBat() { Level level = room(); withoutTheBat(level); return level; }

    /** A live enemy on the hero's row, ahead of him and closer than {@code within} px (body to body). */
    private boolean enemyAhead(Level level, float within) {
        Rectangle me = level.player.hitboxBounds();
        for (var o : level.objects) if (o instanceof Enemy<?> e && !e.isDefeated()) {
            Rectangle en = e.hitboxBounds();
            if (level.player.isOnGround() && en.x >= me.x && en.x - (me.x + me.width) < within && en.y < me.y + me.height && en.y + en.height > me.y) return true;
        }
        return false;
    }

    /** Up + Action, as the lesson rooms teach: Action is a fresh press while Up is held. */
    private int operate(Level level) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        return 3;
    }

    private int walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(centerX(level) > LEVER_X + 8 ? Input.Keys.A : Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 30, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** From the lever (or anywhere on the floor): to the exit ledge by the lift, with ordinary keys; the lever is not touched. */
    private int rideToCheckpoint(Level level) {
        int frames = 0, lastShot = -99;
        actionWasDown = false;
        while (!won(level)) {
            held.clear(); held.add(Input.Keys.D);
            boolean shooting = shootTheBat && stock.count() > 0 && enemyAhead(level, 160);
            if (shooting) {
                held.remove(Input.Keys.D); // stand and shoot; the fireball flies on the hero's row
                if (frames - lastShot > 45) { pressed.add(Input.Keys.L); lastShot = frames; }
            }
            boolean melee = !shooting && enemyAhead(level, 18);
            if (melee && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = melee;
            boolean overLift = centerX(level) >= LIFT_X + 4;
            if (!riding(level) && feet(level) > 300 && overLift && lift(level).getY() >= 292) jump();
            if (riding(level) && lift(level).getY() <= RIDE_JUMP_Y && level.player.isOnGround()) jump(); // up onto the ledge, 16 px above the lift's top
            frame(level); alive(level);
            assertTrue(++frames < 60 * 40, "checkpoint unreachable from " + level.player.position + " lift y=" + lift(level).getY());
        }
        held.clear();
        return frames;
    }

    /** The mandatory route from a standing start at the entrance: walk, ride, walk. */
    private int mandatory(Level level) { return rideToCheckpoint(level); }

    /** Walks Right until the body is over the ladder's rungs. */
    private int walkToLadder(Level level) {
        int frames = 0;
        while (centerX(level) < LADDER_X + 2) {
            held.clear(); held.add(Input.Keys.D);
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "ladder unreachable from " + level.player.position);
        }
        held.clear();
        return frames;
    }

    /** Holds Up until the hero stands on the ledge slab. */
    private int climb(Level level) {
        int frames = 0;
        boolean grabbed = false;
        while (!(grabbed && !level.player.onLadder() && feet(level) <= SLAB_TOP + 1)) {
            held.clear(); held.add(Input.Keys.W);
            frame(level);
            grabbed |= level.player.onLadder();
            assertTrue(++frames < 60 * 20, "climb stuck at " + level.player.position + " ladderY=" + ladder(level).getY());
        }
        held.clear(); frame(level);
        return frames;
    }

    /**
     * From the slab: walk right, hop onto the one-shot step at the slab's end and from its end onto the pad, take the refill, walk off the
     * pad's right end and drop to the floor. Returns the frames used.
     */
    private int takeTheRefillAndDrop(Level level) {
        int frames = 0;
        while (!(refillGone(level) && feet(level) > FLOOR - 2)) {
            held.clear(); held.add(refillGone(level) ? Input.Keys.A : Input.Keys.D); // after the refill the hero leaves the pad on its left side, away from the bat's beat
            boolean ground = level.player.isOnGround();
            if (ground && Math.abs(feet(level) - SLAB_TOP) < 2 && bodyRight(level) >= SLAB_END - 1) jump();
            if (ground && Math.abs(feet(level) - STEP_TOP) < 2 && bodyRight(level) >= STEP_END - 1) jump();
            frame(level); alive(level);
            assertTrue(++frames < 60 * 20, "refill route stuck at " + level.player.position);
        }
        held.clear(); frame(level);
        return frames;
    }

    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private FallingPlatform step(Level level) { return level.fallingPlatforms.get(0); }

    /** The optional detour from a standing start: lever, ladder, refill, back to the lever, lever again; the hero ends by the lever with the lift running. */
    private int detour(Level level) {
        int frames = walkToLever(level);
        frames += operate(level);
        assertTrue(ladder(level).isLowered() && !lift(level).isRunning());
        frames += walkToLadder(level);
        frames += climb(level);
        frames += takeTheRefillAndDrop(level);
        frames += walkToLever(level);
        frames += operate(level);
        assertTrue(!ladder(level).isLowered() && lift(level).isRunning());
        return frames;
    }

    /** From wherever the hero stands (floor, slab or ladder foot) to the checkpoint, flipping the lever back if the lift is stopped. */
    private int recover(Level level) {
        int frames = 0;
        if (level.player.onLadder() || feet(level) < FLOOR - 2) {
            // On the ledge (or its ladder): walk right off the end of the slab.
            while (feet(level) < FLOOR - 2 || level.player.onLadder()) {
                held.clear(); held.add(level.player.onLadder() ? Input.Keys.S : centerX(level) > PAD_X ? Input.Keys.A : Input.Keys.D);
                frame(level); alive(level);
                assertTrue(++frames < 60 * 20, "cannot leave the ledge from " + level.player.position);
            }
            held.clear(); settle(level, 5);
        }
        frames += walkToLever(level);
        if (!lift(level).isRunning()) frames += operate(level);
        assertTrue(lift(level).isRunning());
        return frames + rideToCheckpoint(level);
    }

    // ---- census and links ----

    @Test void theRoomOffersOneLeverOneRaisedLadderOneRunningLiftAndOneRefillAndNothingElse() {
        Level level = room();
        assertEquals("forest-1-route", level.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, 832, 368), level.currentRoomBounds());
        assertEquals(1, level.triggers.size());
        assertEquals(1, level.objects.stream().filter(Lever.class::isInstance).count());
        assertEquals(1, level.ladders.size());
        assertEquals(1, level.escalators.size());
        assertEquals(1, level.objects.stream().filter(FireballRefill.class::isInstance).count());
        assertEquals(1, level.fallingPlatforms.size(), "exactly one one-shot object: the step to the refill pad");
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Wall));
        assertEquals(1, level.objects.stream().filter(o -> o instanceof Enemy<?>).count(), "one monster, the bat");
        assertEquals(1, level.objects.stream().filter(Bat.class::isInstance).count());
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.objects.LethalHazard), "no hazards in the route room");
        assertEquals(0, level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.environment.Checkpoint.class::isInstance).count(), "the level checkpoint moved to the guard room (#104)");
        // Authored state: the ladder is raised, the lift runs from the bottom, the lever shows "down".
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertTrue(lift(level).isVertical);
        assertEquals(TOP, lift(level).getY(), 0.01f);
        assertEquals(LIFT_X, lift(level).getX(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(LADDER_X, ladder(level).getX(), 0.01f);
        assertEquals(96, ladder(level).topY(), 0.01f);
        assertFalse(step(level).isFalling());
        assertEquals(STEP_X, step(level).getX(), 0.01f);
        assertEquals(STEP_TOP, step(level).getY(), 0.01f);
        assertEquals(FallingPlatform.Skin.PLATFORM, step(level).skin(), "the same look as the four falling platforms of room 1");
        assertEquals(208, ladder(level).bottomY(), 0.01f, "raised: its foot hangs 112 px above the floor");
        FireballRefill refill = level.objects.stream().filter(FireballRefill.class::isInstance).map(FireballRefill.class::cast).findFirst().orElseThrow();
        assertEquals(REFILL_X, refill.getX(), 0.01f);
        assertEquals(PAD_TOP, refill.getY() + refill.getHeight(), 0.01f, "the refill sits on the pad");
        assertEquals(START_X, level.player.position.x, 0.01f);
    }

    @Test void theMapKeepsNoHintsAndTheRoomExitStandsOnTheExitLedgeAtTheOldCheckpointsX() {
        Level level = room();
        // The RoomExit is the rectangle x 2048-2096, y 112-144 (D67: the old checkpoint's x, now the way into the guard room, #104).
        level.player.position.set(CHECKPOINT_X - 24 - 12, LEDGE_TOP - 48);
        settle(level, 10);
        assertFalse(level.atRoomExit(), "12 px short of the exit rectangle's left edge");
        assertTrue(feet(level) <= LEDGE_TOP + 1, "standing on the ledge top: " + level.player.position);
        level.player.position.set(CHECKPOINT_X - 24 + 4, LEDGE_TOP - 48);
        settle(level, 10);
        assertTrue(level.atRoomExit(), "inside the exit rectangle on the ledge");
        assertTrue(CHECKPOINT_X - LEDGE_X >= 64, "the exit is well in from the ledge edge");
        assertEquals("", level.player.zoneHint());
    }

    @Test void oneLeverDrivesTheLadderAndTheLiftInOppositeStatesAndShowsIt() {
        Level level = room();
        walkToLever(level);
        for (int pull = 1; pull <= 5; pull++) {
            operate(level);
            boolean odd = pull % 2 == 1;
            assertEquals(odd, ladder(level).isLowered(), "pull " + pull + ": the ladder follows the lever");
            assertEquals(!odd, lift(level).isRunning(), "pull " + pull + ": the lift is the opposite");
            assertEquals(odd, lever(level).isUp(), "the lever shows the ladder");
            settle(level, 60);
        }
        // The lowered ladder reaches the floor from the ledge's top (about 0.7 s at 160 px/s).
        assertTrue(ladder(level).isLowered());
        settle(level, 60);
        assertEquals(SLAB_TOP, ladder(level).topY(), 0.01f);
        assertEquals(FLOOR, ladder(level).bottomY(), 0.01f);
    }

    @Test void theLeverIsOnlyEverInReachOnTheFloorFarFromTheLadderAndTheLift() {
        for (boolean lowered : new boolean[]{false, true}) {
            Level level = roomWithoutTheBat();
            if (lowered) { walkToLever(level); operate(level); settle(level, 60); }
            assertEquals(lowered, ladder(level).isLowered());
            for (float x = ROOM_X + 24; x < CHECKPOINT_X - 24; x += 4) {
                for (float y : new float[]{272, SLAB_TOP - 48, LEDGE_TOP - 48}) {
                    level.player.position.set(x - 24, y);
                    settle(level, 2);
                    if (level.player.canInteract()) {
                        assertTrue(feet(level) > FLOOR - 4, "the lever is only in reach standing on the floor: x=" + x + " y=" + y);
                        assertTrue(Math.abs(centerX(level) - LEVER_X) < 40 && Math.abs(centerX(level) - LADDER_X) > 48, "x=" + x);
                    }
                }
            }
        }
    }

    // ---- the optional route is closed without the lever ----

    @Test void withTheLadderRaisedNeitherWalkingNorJumpingNorTheLiftReachesTheRefillLedge() {
        Level level = roomWithoutTheBat();
        for (float x = ROOM_X + 24; x <= ROOM_END - 32; x += 8) {
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60 * 2; i++) {
                held.clear(); held.add(Input.Keys.D); held.add(Input.Keys.W);
                if (i % 30 == 0) jump();
                frame(level);
                if (level.player.getState() == Player.State.DISAPPEARING) break;
                assertFalse(refillGone(level), "refill collected without the lever from x=" + x);
                assertFalse(centerX(level) > SLAB_X && centerX(level) < PAD_END && feet(level) <= SLAB_TOP + 4,
                    "hero got onto the refill ledge unaided from x=" + x + " at " + level.player.position);
            }
        }
    }

    @Test void theLiftNeverCarriesTheHeroToTheRefillLedgeEitherWayRound() {
        // From the lift's top the ledge is 192 px away and 32 px lower; from the exit ledge 224 px away at the same height.
        for (float fromX : new float[]{LIFT_X + 4, LIFT_RIGHT + 8, LEDGE_X + 16}) {
            for (float topY : new float[]{TOP, LEDGE_TOP}) {
                Level level = roomWithoutTheBat();
                level.player.position.set(fromX - 24, topY - 48);
                settle(level, 3);
                for (int i = 0; i < 60 * 3; i++) {
                    held.clear(); held.add(Input.Keys.A);
                    if (i % 20 == 0) jump();
                    frame(level);
                    assertFalse(centerX(level) > SLAB_X - 16 && centerX(level) < PAD_END + 16 && feet(level) <= SLAB_TOP + 4,
                        "the hero crossed to the refill ledge from " + fromX + "," + topY);
                    assertFalse(refillGone(level));
                }
            }
        }
    }

    @Test void withTheLiftStoppedAtAnyHeightTheExitLedgeIsNotReachableWithoutTheLever() {
        for (int step = 0; step <= 52; step++) {
            float delay = step * 0.1f;
            Level level = roomWithoutTheBat();
            walkToLever(level);
            settle(level, Math.round(delay * 60));
            operate(level); // stops the lift wherever it is
            assertFalse(lift(level).isRunning());
            settle(level, 60);
            float stopped = lift(level).getY();
            assertTrue(stopped >= TOP - 1 && stopped <= BOTTOM + 1);
            for (float x = ROOM_X + 24; x <= LEDGE_X - 8; x += 12) {
                level.player.position.set(x - 24, 272);
                for (int i = 0; i < 60 * 2; i++) {
                    held.clear(); held.add(Input.Keys.D);
                    if (i % 30 == 0) jump();
                    frame(level);
                    assertFalse(won(level), "stopped lift at y=" + stopped + ": the exit was reached without the lever from x=" + x);
                    assertFalse(centerX(level) >= LEDGE_X && feet(level) <= LEDGE_TOP + 4, "up on the exit ledge unaided, lift y=" + stopped + " x=" + x);
                }
            }
        }
    }

    // ---- the required route ----

    @Test void theRequiredRouteReachesTheCheckpointWithZeroChargesInUnderTwentySecondsAndNeverTouchesTheLever() {
        Level level = room();
        int frames = mandatory(level);
        float seconds = frames * STEP;
        System.out.printf(Locale.ROOT, "forest-1 route room required (sword only): %.2f s%n", seconds);
        assertTrue(won(level));
        assertEquals(0, stock.count(), "no charges used or needed");
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance));
        assertTrue(lift(level).isRunning() && !ladder(level).isLowered(), "the lever was never touched");
        assertTrue(bat(level).isDefeated(), "the scripted hero beat the bat with the sword, no fireball");
        assertTrue(seconds <= 12f, "required route " + seconds + " s: the measured value plus the lift's schedule, well inside the 20 s budget");
    }

    @Test void theRequiredRouteTakesTheSameTimeOnThreeAttemptsAfterRetriesAndTheRefillIsLeftBehind() {
        Level level = room();
        float first = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) { level.resetCurrentRoom(player()); settle(level, 0); }
            held.clear(); pressed.clear();
            float seconds = mandatory(level) * STEP;
            if (attempt == 0) first = seconds; else assertEquals(first, seconds, STEP, "a retry reproduces the route time");
            assertEquals(0, stock.count());
            assertFalse(refillGone(level), "the refill is optional: the required route leaves it where it is");
        }
        System.out.printf(Locale.ROOT, "forest-1 route room required, three identical attempts: %.2f s%n", first);
    }

    // ---- the optional route ----

    @Test void theOptionalDetourCollectsTheRefillThenStillReachesTheCheckpointWithinTheBudget() {
        Level level = room();
        int detourFrames = detour(level);
        assertEquals(1, stock.count(), "the refill grants exactly one charge (D27)");
        assertTrue(refillGone(level));
        int rest = rideToCheckpoint(level);
        float detour = detourFrames * STEP, total = (detourFrames + rest) * STEP;
        System.out.printf(Locale.ROOT, "forest-1 route room with the refill: detour (lever, ladder, refill, back, lever) %.2f s, whole room %.2f s%n", detour, total);
        assertTrue(won(level));
        assertEquals(1, stock.count(), "the charge is kept to the checkpoint");
        assertTrue(total <= BUDGET, "even with the refill detour the whole room fits the mastered-route budget: " + total);
    }

    @Test void theRefillCanOnlyBeHadWithTheLeverAndALowerLadderWhileTheLiftStandsStill() {
        Level level = room();
        walkToLever(level);
        operate(level);
        assertFalse(lift(level).isRunning(), "while the hero is on the ledge the lift is stopped: the way on is closed until the lever is pulled again");
        walkToLadder(level); climb(level);
        assertFalse(lift(level).isRunning());
        assertTrue(ladder(level).isLowered());
    }

    // ---- D26: every state still reaches the exit ----

    @Test void everyLiftStopHeightAndLeverStateStillReachesTheCheckpointWithoutRestartLevel() {
        // The lever is pulled once at 0.1 s steps from 0 to 5.2 s (a bit over one lift cycle, 5.12 s): the lift stands still anywhere
        // between its ends. From each stopped state the hero flips the lever back and finishes; from the ledge likewise.
        int states = 0;
        float lowestStop = Float.MAX_VALUE, highestStop = -Float.MAX_VALUE;
        for (int step = 0; step <= 52; step++) {
            float delay = step * 0.1f;
            for (boolean onLedge : new boolean[]{false, true}) {
                Level level = room();
                walkToLever(level);
                settle(level, Math.round(delay * 60));
                operate(level);
                settle(level, 60);
                float stopped = lift(level).getY();
                lowestStop = Math.min(lowestStop, stopped); highestStop = Math.max(highestStop, stopped);
                if (onLedge) { walkToLadder(level); climb(level); }
                recover(level);
                assertTrue(won(level), "delay " + delay + " ledge=" + onLedge + " stopped at " + stopped);
                states++;
            }
        }
        assertEquals(106, states);
        assertTrue(highestStop - lowestStop >= 90, "stop heights span the lift's whole travel: " + (highestStop - lowestStop));
    }

    @Test void pullingTheLeverAnyNumberOfTimesAtAnyRhythmNeverTrapsTheHero() {
        for (int delay = 0; delay <= 150; delay += 15) {
            Level level = room();
            walkToLever(level);
            for (int i = 0; i < 5; i++) { operate(level); settle(level, delay); }
            recover(level);
            assertTrue(won(level), "delay " + delay);
        }
    }

    @Test void droppingOffTheLedgeEitherWayOrOffTheLadderLeavesTheLeverWithinWalkingReach() {
        for (boolean toTheRight : new boolean[]{true, false}) {
            Level level = room();
            walkToLever(level); operate(level);
            walkToLadder(level); climb(level);
            // Walk off the slab's right end (toTheRight) or back over the ladder's side (left): both drops land on the floor.
            for (int i = 0; i < 60 * 6 && (feet(level) < FLOOR - 2 || level.player.onLadder()); i++) {
                held.clear(); held.add(toTheRight ? Input.Keys.D : Input.Keys.A);
                frame(level); alive(level);
            }
            held.clear(); settle(level, 30);
            assertTrue(feet(level) > FLOOR - 2, "back on the floor: " + level.player.position);
            walkToLever(level);
            assertTrue(level.player.canInteract());
            if (!lift(level).isRunning()) operate(level);
            rideToCheckpoint(level);
            assertTrue(won(level));
        }
    }

    @Test void theHeroNeverCanBeOnTheLiftOrTheLadderWhenTheLeverIsPulled() {
        // The lever's reach never includes any support the lever moves, so neither can be taken from under the hero.
        Level level = roomWithoutTheBat();
        for (float y = 96; y <= 288; y += 16)
            for (float x = LADDER_X - 8; x <= LADDER_X + 8; x += 8) {
                level.player.position.set(x - 8, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the ladder x=" + x + " y=" + y);
            }
        for (float y = TOP - 48; y <= BOTTOM - 48; y += 16)
            for (float x = LIFT_X; x <= LIFT_RIGHT; x += 8) {
                level.player.position.set(x - 24, y);
                settle(level, 2);
                assertFalse(level.player.canInteract(), "at the lift x=" + x + " y=" + y);
            }
    }

    // ---- retry and restart ----

    @Test void deathAndRetryRestartAtTheRoomStartWithTheAuthoredStateAndRollBackTheCharge() {
        Level level = room();
        detour(level);
        assertEquals(1, stock.count());
        withoutTheBat(level); // this test is about the lift and the room state; the bat has its own retry test
        // Part way through the second ride: the hero is on the lift, the lever up again; then he dies.
        for (int i = 0; i < 60 * 40 && !(riding(level) && lift(level).getY() < 250); i++) {
            held.clear(); held.add(Input.Keys.D);
            boolean overLift = centerX(level) >= LIFT_X + 4;
            if (!riding(level) && feet(level) > 300 && overLift && lift(level).getY() >= 292) jump();
            frame(level);
        }
        held.clear();
        assertTrue(riding(level));
        level.resetCurrentRoom(player());
        assertEquals(0, stock.count(), "death rolls the unsecured charge back");
        assertFalse(refillGone(level), "and puts the refill back");
        assertFalse(ladder(level).isLowered());
        assertTrue(lift(level).isRunning());
        assertEquals(TOP, lift(level).getY(), 0.01f);
        assertFalse(lever(level).isUp());
        assertEquals(START_X, level.player.position.x, 0.01f, "the hero restarts at the room's entrance");
        assertEquals("forest-1-route", level.currentRoomName(), "not in the trap course");
        mandatory(level);
        assertTrue(won(level));

        // Retry in the lowered-ladder state (lift stopped): the authored state comes back, the route still works.
        Level lowered = room();
        walkToLever(lowered); operate(lowered); settle(lowered, 100);
        assertTrue(ladder(lowered).isLowered());
        lowered.resetCurrentRoom(player());
        assertFalse(ladder(lowered).isLowered());
        assertTrue(lift(lowered).isRunning());
        mandatory(lowered);
        assertTrue(won(lowered));
    }

    @Test void restartLevelRebuildsRoomOneAndTheRouteRoomFromTheMap() {
        Level level = room();
        detour(level);
        // A restart is a fresh Level: both rooms authored, the stock at the level-entry value.
        Level again = room();
        assertEquals("forest-1-route", again.currentRoomName());
        assertFalse(ladder(again).isLowered());
        assertTrue(lift(again).isRunning());
        assertFalse(refillGone(again));
        assertEquals(0, stock.count());
        mandatory(again);
        assertTrue(won(again));
    }

    @Test void theRouteRoomIsEnteredFromTheTrapCourseExitAndRetriesStayInTheRouteRoom() {
        stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
        assertEquals("forest-1-mob", level.currentRoomName());
        level.enterNextRoom(player());
        assertEquals("forest-1-traps", level.currentRoomName());
        assertTrue(level.hasNextRoom());
        level.enterNextRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertTrue(level.hasNextRoom(), "the guard room (#104) follows the route room; its checkpoint ends the level");
        level.resetCurrentRoom(player());
        assertEquals("forest-1-route", level.currentRoomName());
        assertEquals(START_X, level.player.position.x, 0.01f);
        assertEquals(272, level.player.position.y, 0.01f);
    }

    @Test void theEntranceIsSafeAndFarFromEverythingTheLeverMovesAndFromTheCheckpoint() {
        Level level = room();
        // A safe 48 x 48 area with floor under it, nothing to hurt and nothing in reach to trip over.
        assertEquals(272, level.player.position.y, 0.01f);
        assertFalse(level.player.canInteract());
        assertTrue(LEVER_X - (START_X + 48) >= 40);
        assertTrue(LADDER_X - LEVER_X >= 90, "the lever is well clear of the ladder");
        assertTrue(LIFT_X - PAD_END >= 120, "the refill pad and the lift are far apart");
        assertTrue(ROOM_END - 16 - CHECKPOINT_X >= 16, "the exit rectangle ends at the right wall");
        settle(level, 120);
        alive(level);
    }

    // ---- the one-shot step (D67): spending it loses the refill until Restart Level, never the exit ----

    private void reachTheSlab(Level level) { walkToLever(level); operate(level); walkToLadder(level); climb(level); }

    /** On the slab, walks right and hops onto the step so that it starts to fall, then lets everything settle until it is gone (the hero ends wherever he lands). */
    private void spendTheStep(Level level) {
        FallingPlatform step = step(level);
        for (int i = 0; i < 60 * 10 && !step.isFalling(); i++) {
            held.clear(); held.add(Input.Keys.D);
            if (level.player.isOnGround() && Math.abs(feet(level) - SLAB_TOP) < 2 && bodyRight(level) >= SLAB_END - 1) jump();
            frame(level); alive(level);
        }
        assertTrue(step.isFalling(), "the hero stood on the step");
        held.clear();
        for (int i = 0; i < 60 * 6 && !step.isRemoved(); i++) { frame(level); alive(level); }
        assertTrue(step.isRemoved(), "the step is gone after its warning and fall");
        settle(level, 90);
    }

    /** From the slab, with every kind of key spam, the hero never gets up to the pad while the step is gone. */
    private void assertPadClosed(Level level) {
        for (float x = SLAB_X; x <= SLAB_END + 8; x += 4) {
            for (boolean jumping : new boolean[]{false, true}) {
                level.player.position.set(x - 24, SLAB_TOP - 48);
                settle(level, 3);
                for (int i = 0; i < 90; i++) {
                    held.clear(); held.add(Input.Keys.D);
                    if (jumping && i % 12 == 0) jump();
                    frame(level); alive(level);
                    assertFalse(feet(level) <= PAD_TOP + 4 && centerX(level) > PAD_X, "reached the pad without the step from x=" + x + " at " + level.player.position);
                }
            }
        }
        assertFalse(refillGone(level), "the refill is still there, out of reach");
    }

    @Test void theStepFallsOnceAfterTheSharedWarningAndStaysGone() {
        Level level = room();
        reachTheSlab(level);
        FallingPlatform step = step(level);
        assertFalse(step.isFalling());
        spendTheStep(level);
        assertTrue(step.isRemoved());
        assertTrue(level.fallingPlatforms.isEmpty() || level.fallingPlatforms.stream().allMatch(FallingPlatform::isRemoved));
        settle(level, 60 * 5);
        assertTrue(step.isRemoved(), "a one-shot: nothing brings it back");
    }

    @Test void withTheStepIntactTheSlabReachesThePadAndWithItGoneNothingDoes() {
        // Intact: the detour route is exercised by the optional-route test. Gone: every takeoff from the slab falls short.
        Level level = room();
        withoutTheBat(level);
        reachTheSlab(level);
        spendTheStep(level);
        level.player.position.set(SLAB_X, SLAB_TOP - 48);
        settle(level, 10);
        assertPadClosed(level);
        // ... and the step is not walkable from the ladder foot or the floor either (the pad is 192 px up).
        for (float x = ROOM_X + 24; x < LIFT_X - 8; x += 8) {
            level.player.position.set(x - 24, 272);
            for (int i = 0; i < 60; i++) {
                held.clear(); held.add(Input.Keys.D);
                if (i % 20 == 0) jump();
                frame(level);
                assertTrue(feet(level) > PAD_TOP + 60 || level.player.onLadder(), "reached the pad level from the floor at x=" + x);
            }
        }
    }

    @Test void theRefillLostStateStillReachesTheCheckpointAndRestartLevelBringsTheRefillBack() {
        Level level = room();
        reachTheSlab(level);
        spendTheStep(level);
        assertFalse(refillGone(level));
        assertEquals(0, stock.count());
        recover(level);
        assertTrue(won(level), "with the step spent and the refill lost the exit is still reached without Restart Level");
        assertEquals(0, stock.count());
        // Restart Level rebuilds the level from the map: the step is intact and the refill is there to be taken.
        Level again = room();
        assertFalse(step(again).isFalling() || step(again).isRemoved());
        assertFalse(refillGone(again));
        detour(again);
        assertEquals(1, stock.count(), "the authored opportunity is back");
    }

    @Test void deathAndRetryBeforeSecuringRestoreTheStepAndTheRefill() {
        Level level = room();
        reachTheSlab(level);
        spendTheStep(level);
        level.resetCurrentRoom(player());
        assertFalse(step(level).isFalling() || step(level).isRemoved(), "an unsecured one-shot is authored again after a death");
        assertFalse(refillGone(level));
        assertFalse(ladder(level).isLowered());
        detour(level);
        assertEquals(1, stock.count());
    }

    @Test void aSecuredSpentStepStaysSpentOnARevisitAndTheExitStillWorks() {
        // The route room's forward exit (since #104, D68) secures it; this secures it with the same method (secureVisit) and then takes the reset
        // path (resetCurrentRoom): the snapshot is applied there exactly as on a revisit; only the stock bookkeeping of activateRoom(revisit = true)
        // is not exercised, since the test stays in this room.
        Level level = room();
        reachTheSlab(level);
        spendTheStep(level);
        float liftStopped = lift(level).getY();
        level.secureVisit();
        level.resetCurrentRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "the secured room keeps the step spent");
        assertFalse(refillGone(level), "the refill is still there, out of reach");
        assertTrue(ladder(level).isLowered(), "the ladder stays as left (D26)");
        assertFalse(lift(level).isRunning(), "the lift stays stopped where it was");
        assertEquals(liftStopped, lift(level).getY(), 1f);
        assertFalse(bat(level).isDefeated(), "the bat was never touched");
        level.player.position.set(SLAB_X, SLAB_TOP - 48);
        settle(level, 10);
        withoutTheBat(level);
        assertPadClosed(level);
        level.resetCurrentRoom(player());
        recover(level);
        assertTrue(won(level));
    }

    @Test void everyLeverStateLiftStopAndStepStateStillReachesTheCheckpoint() {
        // States: step intact / step spent, lever pulled once at several moments (lift stopped anywhere), hero on the floor or on the slab.
        int states = 0;
        for (boolean spent : new boolean[]{false, true}) {
            for (int step = 0; step <= 13; step++) {
                for (boolean onSlab : new boolean[]{false, true}) {
                    Level level = room();
                    walkToLever(level);
                    settle(level, Math.round(step * 0.4f * 60));
                    operate(level);
                    settle(level, 60);
                    if (onSlab || spent) { walkToLadder(level); climb(level); }
                    if (spent) spendTheStep(level);
                    recover(level);
                    assertTrue(won(level), "step " + step + " spent=" + spent + " onSlab=" + onSlab);
                    assertEquals(0, stock.count());
                    states++;
                }
            }
        }
        assertEquals(56, states);
    }

    // ---- the bat (author's request, 2026-11-15): a flying patroller on the way to the lift ----

    @Test void theBatPatrolsTheFloorLevelStretchBeforeTheLiftAndNothingElse() {
        Level level = room();
        Bat bat = bat(level);
        assertFalse(bat.isVertical);
        assertEquals(BAT_X, bat.getX(), 0.01f);
        assertEquals(296, bat.getY(), 0.01f, "it hangs at the hero's own height, over the floor");
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < 60 * 30; i++) { frame(level); min = Math.min(min, bat.getX()); max = Math.max(max, bat.getX()); }
        assertEquals(BAT_X, min, 1.2f); assertEquals(BAT_END, max, 1.2f);
        // Clearances (body 0..16, the part that hurts is x + 3 .. x + 13): the lever's reach, the ladder, the slab, the step, the pad's drop and the lift boarding.
        float hurtLeft = min + 3, hurtRight = max + 13;
        assertTrue(hurtLeft - (LEVER_X + 16 + 40) >= 200, "the lever's reach (about 40 px each side) is far from the bat: " + (hurtLeft - LEVER_X));
        assertTrue(hurtLeft - (LADDER_X + 16) >= 100, "the ladder foot is clear");
        assertTrue(hurtLeft - STEP_END >= 80, "the step and slab are clear");
        assertTrue(hurtLeft - PAD_END >= 90, "a hero who drops off the pad's right edge drifts about 90 px (swept below): " + (hurtLeft - PAD_END));
        assertTrue(hurtLeft - (PAD_X) >= 60, "a drop off the pad's left side lands clear of the bat");
        assertTrue((LIFT_X + 4 - 9) - hurtRight >= 12, "the lift is boarded 12 px past the bat's reach: " + ((LIFT_X + 4 - 9) - hurtRight));
        assertTrue(Math.abs(bat.getY() - FLOOR) < 30);
    }

    @Test void theBatNeverReachesTheLeverOrTheSlabHeroAndNeverChases() {
        Level level = room();
        walkToLever(level); // standing at the lever for a long time: the bat never comes
        for (int i = 0; i < 60 * 30; i++) { frame(level); alive(level); }
        reachTheSlab(level);
        for (int i = 0; i < 60 * 15; i++) { frame(level); alive(level); }
    }

    @Test void theRequiredRouteNeedsNoFireballTheBatFallsToTheSwordAndAHopOverItAlsoWorks() {
        Level level = room();
        mandatory(level);
        assertEquals(0, stock.count());
        assertTrue(bat(level).isDefeated());
        // A hero who never attacks hops over it: some takeoff gaps clear it and carry him to the checkpoint alive.
        int cleared = 0;
        for (float gap = 8; gap <= 90; gap += 2) {
            Level hop = room();
            boolean ok = false, dead = false;
            for (int i = 0; i < 60 * 25 && !dead; i++) {
                held.clear(); held.add(Input.Keys.D);
                if (enemyAhead(hop, gap) && hop.player.isOnGround()) jump();
                boolean overLift = centerX(hop) >= LIFT_X + 4;
                if (!riding(hop) && feet(hop) > 300 && overLift && lift(hop).getY() >= 292) jump();
                if (riding(hop) && lift(hop).getY() <= RIDE_JUMP_Y && hop.player.isOnGround()) jump();
                frame(hop);
                dead = hop.player.isGotHit();
                if (won(hop)) { ok = true; break; }
            }
            if (ok) cleared++;
        }
        assertTrue(cleared >= 30, "hopping the bat works from several takeoff gaps: " + cleared);
        System.out.println("forest-1 route room: hopping the bat clears from " + cleared + " of 42 takeoff gaps");
    }

    @Test void aFireballShotFromAfarKillsTheBatAndTheRouteStillReachesTheCheckpoint() {
        Level level = room();
        stock.restore(1);
        shootTheBat = true;
        int frames = rideToCheckpoint(level);
        shootTheBat = false;
        assertTrue(bat(level).isDefeated(), "the fireball killed the bat");
        assertEquals(0, stock.count(), "one charge spent");
        System.out.printf(Locale.ROOT, "forest-1 route room, one fireball on the bat: %.2f s%n", frames * STEP);
        assertTrue(frames * STEP <= BUDGET);
    }

    @Test void theRefillPaysForTheFireballTheBatCostsAndAFullStockCanBeSpentHere() {
        // Stock 2: shoot the bat (1 left), then the refill detour brings it back up by one. The refill is worth a charge once one is spent.
        Level level = room();
        stock.restore(2);
        shootTheBat = true;
        // Shoot first, from the entrance side of the bat's beat, then do the detour and finish.
        float x0 = 0;
        for (int i = 0; i < 60 * 20 && !bat(level).isDefeated(); i++) {
            held.clear(); held.add(Input.Keys.D);
            boolean shooting = enemyAhead(level, 160);
            if (shooting) { held.remove(Input.Keys.D); if (i % 45 == 0) pressed.add(Input.Keys.L); }
            frame(level); alive(level);
        }
        shootTheBat = false;
        assertTrue(bat(level).isDefeated());
        assertEquals(1, stock.count());
        detour(level);
        assertEquals(2, stock.count(), "the refill returned the spent charge");
        rideToCheckpoint(level);
        assertTrue(won(level));
        // Stock 3 at the start: the refill is wasted unless a charge was spent first, which is exactly why the bat is here.
        Level full = room();
        stock.restore(3);
        full.player.position.set(REFILL_X - 11, PAD_TOP - 48);
        settle(full, 30);
        assertEquals(3, stock.count(), "a full stock gains nothing from the refill");
        assertFalse(refillGone(full), "and the pickup stays where it is (D27)");
    }

    @Test void deathAndRetryBringTheBatBackAndASecuredKillStaysAKill() throws Exception {
        Level level = room();
        mandatory(level);
        assertTrue(bat(level).isDefeated());
        level.resetCurrentRoom(player());
        assertFalse(bat(level).isDefeated(), "an unsecured kill is undone by a death, like every enemy on a first visit");
        assertEquals(BAT_X, bat(level).getX(), 0.01f);
        // Secured (the forward exit into the guard room, #104, or a healer return): the defeated bat stays defeated.
        Level secured = room();
        mandatory(secured);
        var secure = Level.class.getDeclaredMethod("secureVisit");
        secure.setAccessible(true);
        secure.invoke(secured);
        secured.resetCurrentRoom(player());
        assertTrue(secured.objects.stream().noneMatch(o -> o instanceof Bat b && !b.isDefeated()), "no living bat after a secured kill");
    }

    @Test void everyD26StateStillReachesTheCheckpointWithTheBatAndNoFireball() {
        // The recover() sweeps above run with the bat alive and zero charges; this adds the states where the hero left the slab late (step spent) at once.
        for (int delay : new int[]{0, 90, 200, 330}) {
            Level level = room();
            walkToLever(level);
            settle(level, delay);
            operate(level); settle(level, 40);
            walkToLadder(level); climb(level);
            spendTheStep(level);
            recover(level);
            assertTrue(won(level), "delay " + delay);
            assertEquals(0, stock.count());
        }
    }

    @Test void droppingOffEitherPadEdgeWhateverTheBatsPhaseNeverLandsInItsBeat() {
        // A hero who has taken the refill walks off the pad holding Right (or walks or jumps off holding Left) and drifts about 90 px before he lands; the bat's beat starts 99 px past the pad's right edge. A running jump to the right is excluded on purpose: it is a deliberate act.
        int drops = 0;
        float farthest = 0;
        for (int phase = 0; phase < 160; phase += 3) {
            for (boolean right : new boolean[]{true, false}) {
                for (boolean jumping : new boolean[]{false, true}) {
                    if (jumping && right) continue; // a deliberate running jump off the pad towards the lift is an act, not a drift: it can land in the beat
                    Level level = room();
                    settle(level, phase);
                    float edgeX = right ? PAD_END : PAD_X;
                    level.player.position.set(edgeX - (right ? 14 : 6) - 20, PAD_TOP - 48);
                    for (int i = 0; i < 150 && feet(level) < FLOOR - 1 || i < 3; i++) {
                        held.clear(); held.add(right ? Input.Keys.D : Input.Keys.A);
                        if (jumping && i == 2) jump();
                        frame(level);
                        assertFalse(level.player.isGotHit(), "hit by the bat dropping " + (right ? "right" : "left") + (jumping ? " with a jump" : "") + " at phase " + phase + " at " + level.player.position);
                    }
                    assertTrue(feet(level) > FLOOR - 2, "landed");
                    farthest = Math.max(farthest, centerX(level));
                    drops++;
                }
            }
        }
        assertEquals(3 * 54, drops);
        assertTrue(farthest < BAT_X + 3, "the farthest landing, centre x " + farthest + ", is left of the bat's reach at " + (BAT_X + 3));
        System.out.println("forest-1 route room: farthest landing of a drop off the pad: x " + farthest + " (bat reach starts at " + (BAT_X + 3) + ")");
    }

    @Test void withTheBatAliveAndTheLiftStoppedAtAnyHeightNeitherAStompNorAJumpGetsTheHeroUpTheExitLedge() {
        // The bat as a springboard: stomping it (or being bounced by it) must not add the missing height. Sweeps lift stops, bat phases and takeoff points.
        float highest = Float.MAX_VALUE;
        for (int step = 0; step <= 52; step += 4) {
            Level level = room();
            walkToLever(level);
            settle(level, Math.round(step * 0.1f * 60));
            operate(level);
            settle(level, 60);
            for (float x = BAT_X - 40; x <= LEDGE_X - 8; x += 12) {
                if (bat(level).isDefeated()) break;
                level.player.position.set(x - 24, 272);
                for (int i = 0; i < 60 * 2; i++) {
                    held.clear(); held.add(Input.Keys.D);
                    if (i % 25 == 0) jump();
                    if (i % 25 == 10 && enemyAhead(level, 18)) pressed.add(Input.Keys.K);
                    frame(level);
                    assertFalse(won(level), "exit reached without the lever, lift y=" + lift(level).getY());
                    if (centerX(level) >= LEDGE_X) highest = Math.min(highest, feet(level));
                    assertFalse(centerX(level) >= LEDGE_X && feet(level) <= LEDGE_TOP + 4, "up on the exit ledge unaided with the bat alive, lift y=" + lift(level).getY() + " x=" + x);
                }
            }
        }
        System.out.println("forest-1 route room: lowest feet over the exit ledge's side with the bat alive: y " + highest + " (ledge top " + LEDGE_TOP + ")");
    }
}
